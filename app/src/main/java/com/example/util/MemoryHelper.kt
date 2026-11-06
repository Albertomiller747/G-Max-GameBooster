package com.example.util

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Environment
import android.os.StatFs
import com.example.model.InstalledApp
import com.example.model.MemoryStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MemoryHelper(private val context: Context) {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    fun getMemoryStats(): MemoryStats {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val totalMb = memInfo.totalMem / (1024 * 1024)
        val availMb = memInfo.availMem / (1024 * 1024)
        val usedMb = (memInfo.totalMem - memInfo.availMem) / (1024 * 1024)
        val thresholdMb = memInfo.threshold / (1024 * 1024)

        // Storage stats
        var storageFreeGb = 0.0
        var storageTotalGb = 0.0
        try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availBlocks = stat.availableBlocksLong

            storageFreeGb = (availBlocks * blockSize).toDouble() / (1024 * 1024 * 1024)
            storageTotalGb = (totalBlocks * blockSize).toDouble() / (1024 * 1024 * 1024)
        } catch (_: Exception) {}

        return MemoryStats(
            totalRamMb = totalMb,
            availRamMb = availMb,
            usedRamMb = usedMb,
            thresholdMb = thresholdMb,
            isLowMemory = memInfo.lowMemory,
            internalStorageFreeGb = (storageFreeGb * 10).toLong() / 10.0,
            internalStorageTotalGb = (storageTotalGb * 10).toLong() / 10.0
        )
    }

    suspend fun cleanCachedRam(): Pair<Int, Long> = withContext(Dispatchers.IO) {
        val initialMem = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(initialMem)
        val runtime = Runtime.getRuntime()
        val heapUsedBefore = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)

        val pm = context.packageManager
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        var killedCount = 0

        for (app in installed) {
            // Do not attempt to kill own process, launcher, input methods, or critical system services
            if (app.packageName != context.packageName &&
                (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                (app.flags and ApplicationInfo.FLAG_PERSISTENT) == 0 &&
                !app.packageName.contains("launcher", ignoreCase = true) &&
                !app.packageName.contains("inputmethod", ignoreCase = true) &&
                !app.packageName.contains("keyboard", ignoreCase = true)
            ) {
                try {
                    activityManager.killBackgroundProcesses(app.packageName)
                    killedCount++
                } catch (_: Exception) {}
            }
        }

        try {
            runtime.gc()
        } catch (_: Exception) {}

        val finalMem = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(finalMem)
        val heapUsedAfter = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
        val heapFreedBytes = (heapUsedBefore - heapUsedAfter).coerceAtLeast(0L)
        val sysFreedBytes = (finalMem.availMem - initialMem.availMem).coerceAtLeast(0L)
        val totalFreedBytes = sysFreedBytes + heapFreedBytes
        val freedMb = totalFreedBytes / (1024L * 1024L)

        Pair(killedCount, freedMb)
    }

    suspend fun getInstalledUserApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val result = mutableListOf<InstalledApp>()

        for (app in apps) {
            val pkg = app.packageName
            if (pkg == context.packageName ||
                pkg.equals("com.example", ignoreCase = true) ||
                pkg.startsWith("com.aistudio.gamebooster", ignoreCase = true) ||
                pkg.contains("gamebooster", ignoreCase = true)
            ) {
                continue
            }

            // Must have a real launchable Activity intent that resolves to an enabled Activity
            val launchIntent = try {
                pm.getLaunchIntentForPackage(pkg)
            } catch (_: Exception) {
                null
            }
            if (launchIntent == null) {
                continue
            }
            val resolvedActivity = try {
                pm.resolveActivity(launchIntent, 0)
            } catch (_: Exception) {
                null
            }
            if (resolvedActivity == null) {
                continue
            }

            // Must have an actual application icon resource (filters out headless APKs, split APKs, and data files)
            if (app.icon == 0) {
                continue
            }
            val hasLoadableIcon = try {
                pm.getApplicationIcon(pkg) != null
            } catch (_: Exception) {
                false
            }
            if (!hasLoadableIcon) {
                continue
            }

            // Filter out system/overlay/provider/installer/framework pseudo-packages
            val lowerPkg = pkg.lowercase()
            if (lowerPkg.endsWith(".overlay") ||
                lowerPkg.contains(".auto_generated_rro") ||
                lowerPkg.startsWith("com.android.cts") ||
                lowerPkg.startsWith("com.android.providers") ||
                lowerPkg.startsWith("com.google.android.overlay") ||
                lowerPkg.contains("packageinstaller") ||
                lowerPkg.contains("documentsui")
            ) {
                continue
            }

            val label = try {
                pm.getApplicationLabel(app).toString().trim()
            } catch (_: Exception) {
                ""
            }
            val pkgSuffix = pkg.substringAfterLast(".")
            // Ignore raw package-name labels, raw package suffixes (e.g. "game", "gamebooster"), or file path labels
            if (label.isBlank() ||
                label.equals(pkg, ignoreCase = true) ||
                label.equals(pkgSuffix, ignoreCase = true) ||
                label.equals("game", ignoreCase = true) ||
                label.equals("gamebooster", ignoreCase = true) ||
                label.endsWith(".apk", ignoreCase = true) ||
                label.endsWith(".obb", ignoreCase = true) ||
                label.endsWith(".zip", ignoreCase = true) ||
                label.startsWith("/")
            ) {
                continue
            }

            if (GamePlayStatsHelper.isSelfHubOrHudApp(context, pkg, label)) {
                continue
            }

            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val isGame = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                app.category == ApplicationInfo.CATEGORY_GAME || (!isSystem && isLikelyGame(label, pkg))
            } else {
                !isSystem && isLikelyGame(label, pkg)
            }

            if (!isSystem || isUpdatedSystem || isGame) {
                result.add(
                    InstalledApp(
                        packageName = pkg,
                        appName = label,
                        isGame = isGame,
                        versionName = try {
                            pm.getPackageInfo(pkg, 0).versionName ?: "1.0"
                        } catch (_: Exception) { "1.0" }
                    )
                )
            }
        }

        result.sortedWith(compareByDescending<InstalledApp> { it.isGame }.thenBy { it.appName })
    }

    private fun isLikelyGame(name: String, pkg: String): Boolean {
        val lower = "$name $pkg".lowercase()
        if (lower.contains("booster") ||
            lower.contains("launcher") ||
            lower.contains("installer") ||
            lower.contains("file") ||
            lower.contains("manager") ||
            lower.contains("settings")
        ) {
            return false
        }
        val keywords = listOf(
            "pubg", "genshin", "call of duty", "cod", "roblox", "free fire",
            "minecraft", "mobile legends", "asphalt", "clash", "brawl", "need for speed",
            "subway surf", "candy crush", "fifa", "ea sports", "moba", "blood strike",
            "standoff", "valorant", "fortnite", "apex", "efootball", "honkai", "wuthering"
        )
        return keywords.any { lower.contains(it) }
    }

    suspend fun clearAppInternalCache(): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        try {
            val cacheDir = context.cacheDir
            freed = deleteDirContents(cacheDir)
            context.externalCacheDir?.let {
                freed += deleteDirContents(it)
            }
        } catch (_: Exception) {}
        freed / (1024 * 1024) // in MB
    }

    private fun deleteDirContents(dir: File?): Long {
        if (dir == null || !dir.exists() || !dir.isDirectory) return 0L
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (f in files) {
            if (f.isDirectory) {
                size += deleteDirContents(f)
                f.delete()
            } else {
                size += f.length()
                f.delete()
            }
        }
        return size
    }
}
