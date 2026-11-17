package com.example.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.model.InstalledApp
import com.example.model.RecordedVideo
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class GameAppDossier(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val formattedVersion: String,
    val apkSizeBytes: Long,
    val formattedAppSize: String,
    val launchCount: Int,
    val totalPlayTimeMs: Long,
    val formattedPlayTime: String,
    val recordedClipsCount: Int,
    val recordedVideoDurationMs: Long,
    val recordedVideoBytes: Long,
    val formattedVideoDuration: String,
    val formattedVideoSize: String,
    val recordedClips: List<RecordedVideo> = emptyList()
)

/**
 * GameAppDossierHelper:
 * Resolves the 4 core metrics for the `(i)` App Info tab on Launcher apps:
 * 1. App Size (base APK + split APKs + external OBB/media footprint)
 * 2. How many times the user launched the app (`launchCount`)
 * 3. How much video was recorded for the app (`recordedClipsCount`, duration, and storage size)
 * 4. App Version Number (`versionName` + build code)
 */
object GameAppDossierHelper {

    private const val PREFS_NAME = "gmax_app_video_ledger_prefs"
    private const val KEY_LEDGER_JSON = "per_app_video_ledger_json"

    data class RecordedAppVideoStats(
        val clipsCount: Int = 0,
        val totalDurationMs: Long = 0L,
        val totalBytes: Long = 0L
    )

    /**
     * Records a completed screen recording clip against a specific game package in the persistent ledger
     * so per-app video totals remain accurate even if clips are later moved or trimmed.
     */
    fun recordVideoForGamePackage(
        context: Context,
        packageName: String,
        durationMs: Long,
        sizeBytes: Long
    ) {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LEDGER_JSON, "{}") ?: "{}"
        try {
            val root = JSONObject(raw)
            val existing = root.optJSONObject(cleanPkg) ?: JSONObject()
            val nextClips = existing.optInt("clipsCount", 0) + 1
            val nextDuration = existing.optLong("totalDurationMs", 0L) + durationMs.coerceAtLeast(0L)
            val nextBytes = existing.optLong("totalBytes", 0L) + sizeBytes.coerceAtLeast(0L)
            existing.put("clipsCount", nextClips)
            existing.put("totalDurationMs", nextDuration)
            existing.put("totalBytes", nextBytes)
            root.put(cleanPkg, existing)
            prefs.edit().putString(KEY_LEDGER_JSON, root.toString()).apply()
        } catch (_: Exception) {}
    }

    fun getLedgerStatsForPackage(context: Context, packageName: String): RecordedAppVideoStats {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isBlank()) return RecordedAppVideoStats()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LEDGER_JSON, "{}") ?: "{}"
        return try {
            val root = JSONObject(raw)
            val obj = root.optJSONObject(cleanPkg) ?: return RecordedAppVideoStats()
            RecordedAppVideoStats(
                clipsCount = obj.optInt("clipsCount", 0),
                totalDurationMs = obj.optLong("totalDurationMs", 0L),
                totalBytes = obj.optLong("totalBytes", 0L)
            )
        } catch (_: Exception) {
            RecordedAppVideoStats()
        }
    }

    fun buildAppDossier(
        context: Context,
        game: InstalledApp,
        cachedVideos: List<RecordedVideo>? = null
    ): GameAppDossier {
        val pm = context.packageManager
        var versionName = game.versionName.ifBlank { "1.0.0" }
        var versionCode = 1L
        var totalApkBytes = 0L

        try {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(game.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(game.packageName, 0)
            }
            versionName = pkgInfo.versionName?.takeIf { it.isNotBlank() } ?: versionName
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }

            val appInfo = pkgInfo.applicationInfo
            if (appInfo != null) {
                appInfo.sourceDir?.let { path ->
                    val f = File(path)
                    if (f.exists()) totalApkBytes += f.length()
                }
                appInfo.splitSourceDirs?.forEach { splitPath ->
                    val sf = File(splitPath)
                    if (sf.exists()) totalApkBytes += sf.length()
                }
            }
        } catch (_: Exception) {}

        if (totalApkBytes <= 0L && game.memoryUsageMb > 0L) {
            totalApkBytes = game.memoryUsageMb * 1024L * 1024L
        }

        // Launch count & play time from GamePlayStatsHelper
        val playStats = try {
            GamePlayStatsHelper.getMergedGameStats(context, listOf(game)).firstOrNull()
        } catch (_: Exception) {
            null
        }
        val launchCount = playStats?.launchCount ?: 0
        val totalPlayTimeMs = playStats?.totalPlayTimeMs ?: 0L
        val formattedPlayTime = playStats?.formattedPlayTime ?: "0m played"

        // Recorded videos for this app (combining live MediaStore/folder clips + persistent ledger)
        val allVideos = cachedVideos ?: try {
            ScreenRecordingManager.queryRecordedVideos(context)
        } catch (_: Exception) {
            emptyList()
        }

        val matchingClips = allVideos.filter { vid ->
            vid.gamePackage.equals(game.packageName, ignoreCase = true) ||
                vid.gameName.equals(game.appName, ignoreCase = true)
        }

        val ledger = getLedgerStatsForPackage(context, game.packageName)
        val clipsCount = maxOf(matchingClips.size, ledger.clipsCount)
        val clipsDurationMs = maxOf(
            matchingClips.sumOf { it.durationMs.coerceAtLeast(0L) },
            ledger.totalDurationMs
        )
        val clipsSizeBytes = maxOf(
            matchingClips.sumOf { it.sizeBytes.coerceAtLeast(0L) },
            ledger.totalBytes
        )

        return GameAppDossier(
            packageName = game.packageName,
            appName = game.appName,
            versionName = versionName,
            versionCode = versionCode,
            formattedVersion = "v$versionName ($versionCode)",
            apkSizeBytes = totalApkBytes,
            formattedAppSize = formatByteSize(totalApkBytes),
            launchCount = launchCount,
            totalPlayTimeMs = totalPlayTimeMs,
            formattedPlayTime = formattedPlayTime,
            recordedClipsCount = clipsCount,
            recordedVideoDurationMs = clipsDurationMs,
            recordedVideoBytes = clipsSizeBytes,
            formattedVideoDuration = formatVideoDuration(clipsDurationMs, clipsCount),
            formattedVideoSize = formatByteSize(clipsSizeBytes),
            recordedClips = matchingClips
        )
    }

    fun formatByteSize(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        } else if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            val kb = bytes.toDouble() / 1024.0
            String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
        }
    }

    fun formatVideoDuration(durationMs: Long, clipsCount: Int = 0): String {
        if (durationMs <= 0L) {
            return if (clipsCount > 0) "$clipsCount ${if (clipsCount == 1) "clip" else "clips"}" else "0m 00s"
        }
        val totalSec = durationMs / 1000L
        val hours = totalSec / 3600L
        val mins = (totalSec % 3600L) / 60L
        val secs = totalSec % 60L
        return when {
            hours > 0L -> String.format(Locale.US, "%dh %02dm %02ds", hours, mins, secs)
            mins > 0L -> String.format(Locale.US, "%dm %02ds", mins, secs)
            else -> String.format(Locale.US, "%ds", secs.coerceAtLeast(1L))
        }
    }
}
