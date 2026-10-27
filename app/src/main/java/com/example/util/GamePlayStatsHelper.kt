package com.example.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.example.model.InstalledApp
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class GamePlayStats(
    val game: InstalledApp,
    val lastPlayedEpochMs: Long,
    val totalPlayTimeMs: Long,
    val launchCount: Int
) {
    val formattedLastPlayed: String
        get() {
            if (lastPlayedEpochMs <= 0L) return "Recently added"
            val diffMs = (System.currentTimeMillis() - lastPlayedEpochMs).coerceAtLeast(0L)
            val minutes = diffMs / (60 * 1000L)
            val hours = diffMs / (60 * 60 * 1000L)
            val days = diffMs / (24 * 60 * 60 * 1000L)

            return when {
                minutes < 2 -> "Just now"
                minutes < 60 -> "${minutes}m ago"
                hours < 24 -> "${hours}h ago"
                days == 1L -> "Yesterday"
                days < 7L -> "${days}d ago"
                else -> SimpleDateFormat("MMM d", Locale.US).format(Date(lastPlayedEpochMs))
            }
        }

    val formattedPlayTime: String
        get() {
            val totalMinutes = (totalPlayTimeMs / (60 * 1000L)).toInt()
            val hours = totalMinutes / 60
            val mins = totalMinutes % 60
            return when {
                hours >= 10 -> String.format(Locale.US, "%.1f hrs", totalPlayTimeMs / (3600_000.0))
                hours >= 1 -> "${hours}h ${mins}m"
                totalMinutes >= 1 -> "${totalMinutes}m played"
                launchCount > 0 -> "$launchCount ${if (launchCount == 1) "session" else "sessions"}"
                else -> "Ready"
            }
        }
}

data class TotalPlayTimeSummary(
    val totalPlayTimeMs: Long = 0L,
    val totalMinutes: Long = 0L,
    val hours: Long = 0L,
    val minutesPart: Long = 0L,
    val totalSessions: Int = 0,
    val gamesPlayedCount: Int = 0
) {
    val formattedHoursAndMinutes: String
        get() = "${hours}h ${minutesPart}m"
}

/**
 * Combines real Android UsageStatsManager telemetry with G-Max Hub launcher session tracking
 * to power the "Recently Played" and "Most Played" rows and automatic per-game media tagging.
 */
object GamePlayStatsHelper {

    private const val PREFS_NAME = "gmax_game_play_stats_prefs"
    private const val KEY_STATS_JSON = "stats_json"
    private const val KEY_INSTALL_TIMESTAMP_MS = "gmax_install_timestamp_ms"
    private const val KEY_ACTIVE_LAUNCH_PKG = "active_launch_pkg"
    private const val KEY_ACTIVE_LAUNCH_NAME = "active_launch_name"
    private const val KEY_ACTIVE_LAUNCH_START_MS = "active_launch_start_ms"

    private data class LocalGameRecord(
        val packageName: String,
        var appName: String,
        var lastLaunchedMs: Long = 0L,
        var accumulatedDurationMs: Long = 0L,
        var launchCount: Int = 0
    )

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Returns the timestamp when G-Max Hub was installed/first initialized on this device.
     * Ensures pre-install Android UsageStats history does not populate Recently/Most Played on a fresh install.
     */
    fun getInstallTimestampMs(context: Context): Long {
        val prefs = getPrefs(context)
        val saved = prefs.getLong(KEY_INSTALL_TIMESTAMP_MS, 0L)
        if (saved > 0L) return saved

        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_INSTALL_TIMESTAMP_MS, now).apply()
        return now
    }

    private fun loadLocalRecords(context: Context): MutableMap<String, LocalGameRecord> {
        val prefs = getPrefs(context)
        // Ensure install timestamp is initialized on first check
        getInstallTimestampMs(context)
        val rawJson = prefs.getString(KEY_STATS_JSON, null)

        val map = mutableMapOf<String, LocalGameRecord>()
        if (rawJson.isNullOrBlank()) return map

        try {
            val root = JSONObject(rawJson)
            val keys = root.keys()
            while (keys.hasNext()) {
                val pkg = keys.next()
                val obj = root.optJSONObject(pkg) ?: continue
                map[pkg] = LocalGameRecord(
                    packageName = pkg,
                    appName = obj.optString("appName", pkg.substringAfterLast(".")),
                    lastLaunchedMs = obj.optLong("lastLaunchedMs", 0L),
                    accumulatedDurationMs = obj.optLong("accumulatedDurationMs", 0L),
                    launchCount = obj.optInt("launchCount", 0)
                )
            }
        } catch (_: Exception) {}
        return map
    }

    private fun saveLocalRecords(context: Context, records: Map<String, LocalGameRecord>) {
        try {
            val root = JSONObject()
            for ((pkg, rec) in records) {
                val obj = JSONObject().apply {
                    put("appName", rec.appName)
                    put("lastLaunchedMs", rec.lastLaunchedMs)
                    put("accumulatedDurationMs", rec.accumulatedDurationMs)
                    put("launchCount", rec.launchCount)
                }
                root.put(pkg, obj)
            }
            val jsonString = root.toString()
            getPrefs(context).edit().putString(KEY_STATS_JSON, jsonString).apply()
        } catch (_: Exception) {}
    }

    /**
     * Records a game launch initiated from G-Max Hub and sets it as the active capture target.
     */
    fun recordGameLaunch(context: Context, packageName: String, appName: String) {
        val now = System.currentTimeMillis()
        getInstallTimestampMs(context)
        val records = loadLocalRecords(context)
        val existing = records[packageName] ?: LocalGameRecord(packageName = packageName, appName = appName)
        existing.appName = appName
        existing.lastLaunchedMs = now
        existing.launchCount += 1
        records[packageName] = existing
        saveLocalRecords(context, records)

        getPrefs(context).edit()
            .putString(KEY_ACTIVE_LAUNCH_PKG, packageName)
            .putString(KEY_ACTIVE_LAUNCH_NAME, appName)
            .putLong(KEY_ACTIVE_LAUNCH_START_MS, now)
            .apply()

        ScreenRecordingManager.setActiveGameContext(context, packageName, appName)
    }

    /**
     * Called when returning to G-Max Hub (e.g. MainActivity.onResume) to attribute elapsed
     * session play time if a game was launched from the hub.
     */
    fun finalizeActiveGameSessionOnReturn(context: Context) {
        val prefs = getPrefs(context)
        val activePkg = prefs.getString(KEY_ACTIVE_LAUNCH_PKG, null) ?: return
        val activeName = prefs.getString(KEY_ACTIVE_LAUNCH_NAME, activePkg) ?: activePkg
        val startMs = prefs.getLong(KEY_ACTIVE_LAUNCH_START_MS, 0L)
        if (startMs <= 0L) return

        val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L)
        // Attribute realistic session duration (between 5 seconds and 6 hours)
        if (elapsedMs in 5_000L..(6 * 3600_000L)) {
            val records = loadLocalRecords(context)
            val rec = records[activePkg] ?: LocalGameRecord(packageName = activePkg, appName = activeName)
            rec.accumulatedDurationMs += elapsedMs
            rec.lastLaunchedMs = System.currentTimeMillis()
            records[activePkg] = rec
            saveLocalRecords(context, records)
        }

        prefs.edit()
            .remove(KEY_ACTIVE_LAUNCH_StartKey())
            .apply()
    }

    private fun KEY_ACTIVE_LAUNCH_StartKey() = KEY_ACTIVE_LAUNCH_START_MS

    /**
     * Queries Android UsageStatsManager for real foreground usage strictly AFTER G-Max was installed.
     */
    private fun queryPostInstallSystemUsageStats(context: Context, installTimeMs: Long): Map<String, Pair<Long, Long>> {
        // Pair<lastTimeUsedMs, totalTimeInForegroundMs>
        val result = mutableMapOf<String, Pair<Long, Long>>()
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return result
            val endTime = System.currentTimeMillis()
            if (endTime <= installTimeMs) return result

            val aggregated = usm.queryAndAggregateUsageStats(installTimeMs, endTime)
            for ((pkg, stats) in aggregated) {
                val lastUsed = stats.lastTimeUsed
                val totalFg = stats.totalTimeInForeground
                if (lastUsed >= installTimeMs && (lastUsed > 0L || totalFg > 0L)) {
                    result[pkg] = Pair(lastUsed, totalFg)
                }
            }
        } catch (_: Exception) {}
        return result
    }

    /**
     * Computes merged play stats for all games in the user's library.
     * Only counts play sessions that occurred after G-Max Hub was installed and launched by the user.
     */
    fun getMergedGameStats(
        context: Context,
        libraryGames: List<InstalledApp>
    ): List<GamePlayStats> {
        val installTimeMs = getInstallTimestampMs(context)
        val localRecords = loadLocalRecords(context)
        val systemUsage = queryPostInstallSystemUsageStats(context, installTimeMs)

        return libraryGames.map { game ->
            val local = localRecords[game.packageName]
            val hasLocalLaunch = (local?.launchCount ?: 0) > 0 && (local?.lastLaunchedMs ?: 0L) >= installTimeMs

            if (!hasLocalLaunch) {
                // Fresh install or never launched in G-Max Hub -> 0 stats so it won't appear in Recently/Most Played
                GamePlayStats(
                    game = game,
                    lastPlayedEpochMs = 0L,
                    totalPlayTimeMs = 0L,
                    launchCount = 0
                )
            } else {
                val sys = systemUsage[game.packageName]
                val lastPlayedMs = maxOf(local?.lastLaunchedMs ?: 0L, sys?.first ?: 0L)
                val totalPlayMs = maxOf(local?.accumulatedDurationMs ?: 0L, sys?.second ?: 0L)
                val launches = local?.launchCount ?: 1

                GamePlayStats(
                    game = game,
                    lastPlayedEpochMs = lastPlayedMs,
                    totalPlayTimeMs = totalPlayMs,
                    launchCount = launches
                )
            }
        }
    }

    /**
     * Returns games ordered by most recently played.
     * Returns an empty list if no games have been played since installing G-Max.
     */
    fun getRecentlyPlayedGames(
        context: Context,
        libraryGames: List<InstalledApp>
    ): List<GamePlayStats> {
        val allStats = getMergedGameStats(context, libraryGames)
        return allStats
            .filter { it.launchCount > 0 && it.lastPlayedEpochMs > 0L }
            .sortedByDescending { it.lastPlayedEpochMs }
            .take(10)
    }

    /**
     * Returns games ordered by highest total play time and session count.
     * Returns an empty list if no games have been played since installing G-Max.
     */
    fun getMostPlayedGames(
        context: Context,
        libraryGames: List<InstalledApp>
    ): List<GamePlayStats> {
        val allStats = getMergedGameStats(context, libraryGames)
        return allStats
            .filter { it.launchCount > 0 && (it.totalPlayTimeMs > 0L || it.lastPlayedEpochMs > 0L) }
            .sortedWith(
                compareByDescending<GamePlayStats> { it.totalPlayTimeMs }
                    .thenByDescending { it.launchCount }
                    .thenByDescending { it.lastPlayedEpochMs }
            )
            .take(10)
    }

    /**
     * Calculates every game session together across all tracked and library games,
     * returning cumulative hours, minutes, total play time in ms, and total session count.
     */
    fun getTotalPlayTimeSummary(
        context: Context,
        libraryGames: List<InstalledApp>
    ): TotalPlayTimeSummary {
        val localRecords = loadLocalRecords(context)
        val combinedGamesByPkg = linkedMapOf<String, InstalledApp>()
        for (game in libraryGames) {
            combinedGamesByPkg[game.packageName] = game
        }
        for ((pkg, record) in localRecords) {
            if (!combinedGamesByPkg.containsKey(pkg)) {
                combinedGamesByPkg[pkg] = InstalledApp(
                    packageName = pkg,
                    appName = record.appName.ifBlank { pkg.substringAfterLast(".") },
                    isGame = true
                )
            }
        }
        val mergedStats = getMergedGameStats(context, combinedGamesByPkg.values.toList())
        val playedStats = mergedStats.filter { it.totalPlayTimeMs > 0L || it.launchCount > 0 }
        val totalMs = playedStats.sumOf { it.totalPlayTimeMs }
        val totalSessions = playedStats.sumOf { it.launchCount }
        val totalMins = totalMs / 60_000L
        val hrs = totalMins / 60L
        val minsPart = totalMins % 60L
        return TotalPlayTimeSummary(
            totalPlayTimeMs = totalMs,
            totalMinutes = totalMins,
            hours = hrs,
            minutesPart = minsPart,
            totalSessions = totalSessions,
            gamesPlayedCount = playedStats.size
        )
    }

    /**
     * Detects the currently active or most recently foregrounded game for tagging recordings and screenshots.
     */
    fun resolveCurrentOrRecentGame(context: Context): Pair<String, String>? {
        val prefs = getPrefs(context)
        val activePkg = prefs.getString(KEY_ACTIVE_LAUNCH_PKG, null)
        val activeName = prefs.getString(KEY_ACTIVE_LAUNCH_NAME, null)

        // 1. Check UsageEvents in the last 15 minutes if permission is granted
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usm != null) {
                val now = System.currentTimeMillis()
                val events = usm.queryEvents(now - 15 * 60_000L, now)
                val event = UsageEvents.Event()
                var lastFgPkg: String? = null
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND &&
                        event.packageName != context.packageName &&
                        !event.packageName.contains("launcher", ignoreCase = true) &&
                        !event.packageName.contains("systemui", ignoreCase = true)
                    ) {
                        lastFgPkg = event.packageName
                    }
                }
                if (lastFgPkg != null) {
                    val pm = context.packageManager
                    val label = try {
                        val info = pm.getApplicationInfo(lastFgPkg, 0)
                        pm.getApplicationLabel(info).toString()
                    } catch (_: Exception) {
                        lastFgPkg.substringAfterLast(".")
                    }
                    return Pair(lastFgPkg, label)
                }
            }
        } catch (_: Exception) {}

        // 2. Fallback to active boost / launched game in G-Max Hub
        if (!activePkg.isNullOrBlank() && !activeName.isNullOrBlank()) {
            return Pair(activePkg, activeName)
        }

        val sessionMgr = BoosterSessionManager(context)
        val (lastBoostPkg, lastBoostName) = sessionMgr.getLastTargetGame()
        if (!lastBoostPkg.isNullOrBlank() && !lastBoostName.isNullOrBlank()) {
            return Pair(lastBoostPkg, lastBoostName)
        }

        return null
    }
}
