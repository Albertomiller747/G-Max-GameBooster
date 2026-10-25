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

        val pm = context.packageManager
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        var killedCount = 0

        for (app in installed) {
            // Do not attempt to kill own process or critical system services
            if (app.packageName != context.packageName &&
                (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0
            ) {
                try {
                    activityManager.killBackgroundProcesses(app.packageName)
                    killedCount++
                } catch (_: Exception) {}
            }
        }

        val finalMem = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(finalMem)

        val freedBytes = (finalMem.availMem - initialMem.availMem).coerceAtLeast(0L)
        val freedMb = freedBytes / (1024 * 1024)

        Pair(killedCount, freedMb)
    }

    suspend fun getInstalledUserApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val result = mutableListOf<InstalledApp>()

        for (app in apps) {
            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (!isSystem || (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                val label = pm.getApplicationLabel(app).toString()
                val isGame = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    app.category == ApplicationInfo.CATEGORY_GAME || isLikelyGame(label, app.packageName)
                } else {
                    isLikelyGame(label, app.packageName)
                }

                result.add(
                    InstalledApp(
                        packageName = app.packageName,
                        appName = label,
                        isGame = isGame,
                        versionName = try {
                            pm.getPackageInfo(app.packageName, 0).versionName ?: "1.0"
                        } catch (_: Exception) { "1.0" }
                    )
                )
            }
        }

        result.sortedWith(compareByDescending<InstalledApp> { it.isGame }.thenBy { it.appName })
    }

    private fun isLikelyGame(name: String, pkg: String): Boolean {
        val lower = "$name $pkg".lowercase()
        val keywords = listOf(
            "game", "pubg", "genshin", "call of duty", "cod", "roblox", "free fire",
            "minecraft", "legends", "asphalt", "clash", "brawl", "speed", "arena",
            "subway", "candy", "fifa", "ea sports", "moba", "rpg", "fps"
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
