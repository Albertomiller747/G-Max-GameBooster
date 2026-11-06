package com.example.util

import android.content.ComponentCallbacks2
import android.content.Context
import coil.Coil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class MemoryTrimResult(
    val reason: String,
    val cacheClearedBytes: Long,
    val cacheClearedMb: Float,
    val backgroundAppsTrimmed: Int,
    val ramFreedMb: Long,
    val availRamMbAfter: Long,
    val totalRamMb: Long,
    val timestampMs: Long = System.currentTimeMillis(),
    val totalFreedBytes: Long = cacheClearedBytes + (ramFreedMb * 1024L * 1024L)
)

/**
 * Centralized Memory, Cache, and Idle Battery Footprint Manager.
 *
 * - Automatically trims internal/external caches and image memory caches on:
 *   1. Cold app launch (asynchronously off the main thread)
 *   2. Android OS `onTrimMemory` / `onLowMemory` signals
 *   3. Game Boost session start (Gaming / Beast mode)
 *   4. Manual one-tap cache & RAM purge
 * - Coordinates background telemetry auto-pause state when the app is minimized and idle.
 */
object AppMemoryManager {

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _lastTrimResult = MutableStateFlow<MemoryTrimResult?>(null)
    val lastTrimResult: StateFlow<MemoryTrimResult?> = _lastTrimResult.asStateFlow()

    private val _isBackgroundTelemetryPaused = MutableStateFlow(false)
    val isBackgroundTelemetryPaused: StateFlow<Boolean> = _isBackgroundTelemetryPaused.asStateFlow()

    @Volatile
    private var lastAutoTrimTimestampMs: Long = 0L

    fun setBackgroundIdlePaused(paused: Boolean) {
        _isBackgroundTelemetryPaused.value = paused
    }

    /**
     * Runs a fast, non-blocking cache and memory trim on [Dispatchers.IO].
     */
    suspend fun performAutoTrim(
        context: Context,
        reason: String,
        cleanBackgroundProcesses: Boolean = false,
        force: Boolean = false
    ): MemoryTrimResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = _lastTrimResult.value
        if (!force && existing != null && (now - lastAutoTrimTimestampMs) < 5_000L && !cleanBackgroundProcesses) {
            return@withContext existing
        }
        lastAutoTrimTimestampMs = now

        val appCtx = context.applicationContext
        val memHelper = MemoryHelper(appCtx)
        val runtime = Runtime.getRuntime()
        val jvmUsedBefore = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
        val statsBefore = try {
            memHelper.getMemoryStats()
        } catch (_: Exception) {
            com.example.model.MemoryStats()
        }

        // 1. Trim Coil in-memory bitmap cache
        trimImageMemoryCache(appCtx)

        // 2. Prune in-memory rolling telemetry windows
        try {
            EfpsEngine.trimHistory()
        } catch (_: Exception) {}

        // 3. Delete stale files in internal cacheDir, codeCacheDir, and externalCacheDir
        val clearedBytes = clearCacheDirectoriesBytes(appCtx)

        // 4. Optionally clean background cached processes (e.g. during Boost start, manual purge, or low memory)
        var killedApps = 0
        var helperFreedMb = 0L
        if (cleanBackgroundProcesses) {
            try {
                val (killed, freed) = memHelper.cleanCachedRam()
                killedApps = killed
                helperFreedMb = freed
            } catch (_: Exception) {}
        }

        try {
            runtime.gc()
        } catch (_: Exception) {}

        val jvmUsedAfter = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
        val jvmFreedBytes = (jvmUsedBefore - jvmUsedAfter).coerceAtLeast(0L)

        val statsAfter = try {
            memHelper.getMemoryStats()
        } catch (_: Exception) {
            statsBefore
        }

        val availDeltaMb = (statsAfter.availRamMb - statsBefore.availRamMb).coerceAtLeast(0L)
        val totalReclaimedBytes = clearedBytes + jvmFreedBytes + (maxOf(helperFreedMb, availDeltaMb) * 1024L * 1024L)
        val exactFreedRamMb = maxOf(
            helperFreedMb,
            availDeltaMb,
            jvmFreedBytes / (1024L * 1024L)
        ).coerceAtLeast(0L)
        val clearedMb = ((clearedBytes + jvmFreedBytes).toDouble() / (1024.0 * 1024.0)).toFloat()

        val result = MemoryTrimResult(
            reason = reason,
            cacheClearedBytes = clearedBytes + jvmFreedBytes,
            cacheClearedMb = ((clearedMb * 100f).toInt() / 100f).coerceAtLeast(0f),
            backgroundAppsTrimmed = killedApps,
            ramFreedMb = exactFreedRamMb,
            availRamMbAfter = statsAfter.availRamMb,
            totalRamMb = statsAfter.totalRamMb,
            timestampMs = now
        )
        _lastTrimResult.value = result
        result
    }

    /**
     * Formats the exact measured RAM & Cache freed into a real, non-fabricated human string.
     */
    fun formatExactTrimSummary(result: MemoryTrimResult): String {
        val totalBytes = (result.ramFreedMb * 1024L * 1024L) + result.cacheClearedBytes
        return when {
            totalBytes >= 1024L * 1024L * 1024L -> {
                val gb = totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                String.format(java.util.Locale.US, "%.2f GB", gb)
            }
            totalBytes >= 1024L * 1024L -> {
                val mb = totalBytes.toDouble() / (1024.0 * 1024.0)
                if (mb >= 10.0) {
                    String.format(java.util.Locale.US, "%.0f MB", mb)
                } else {
                    String.format(java.util.Locale.US, "%.1f MB", mb)
                }
            }
            totalBytes >= 1024L -> {
                val kb = totalBytes / 1024L
                "$kb KB"
            }
            else -> "${totalBytes.coerceAtLeast(0L)} B"
        }
    }

    /**
     * Triggered by [ComponentCallbacks2.onTrimMemory] in `MainActivity`.
     */
    fun onTrimMemory(context: Context, level: Int) {
        val appCtx = context.applicationContext
        trimImageMemoryCache(appCtx)
        try {
            EfpsEngine.trimHistory()
        } catch (_: Exception) {}

        val isCritical = level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        if (isCritical) {
            managerScope.launch {
                performAutoTrim(
                    context = appCtx,
                    reason = "System Memory Trim ($level)",
                    cleanBackgroundProcesses = level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
                    force = true
                )
            }
        }
    }

    /**
     * Triggered by [ComponentCallbacks2.onLowMemory] in `MainActivity`.
     */
    fun onLowMemory(context: Context) {
        val appCtx = context.applicationContext
        trimImageMemoryCache(appCtx)
        try {
            EfpsEngine.trimHistory()
        } catch (_: Exception) {}
        managerScope.launch {
            performAutoTrim(
                context = appCtx,
                reason = "Low Memory Alert",
                cleanBackgroundProcesses = true,
                force = true
            )
        }
    }

    private fun trimImageMemoryCache(context: Context) {
        try {
            val loader = Coil.imageLoader(context)
            loader.memoryCache?.clear()
        } catch (_: Exception) {}
    }

    fun clearCacheDirectoriesBytes(context: Context): Long {
        var freedBytes = 0L
        try {
            freedBytes += deleteSafeCacheContents(context.cacheDir)
            context.codeCacheDir?.let { codeCache ->
                freedBytes += deleteSafeCacheContents(codeCache)
            }
            context.externalCacheDir?.let { extCache ->
                freedBytes += deleteSafeCacheContents(extCache)
            }
        } catch (_: Exception) {}
        return freedBytes.coerceAtLeast(0L)
    }

    private fun deleteSafeCacheContents(dir: File?): Long {
        if (dir == null || !dir.exists() || !dir.isDirectory) return 0L
        var freed = 0L
        val children = dir.listFiles() ?: return 0L
        for (child in children) {
            try {
                if (child.isDirectory) {
                    freed += deleteSafeCacheContents(child)
                    child.delete()
                } else {
                    val len = child.length()
                    if (child.delete()) {
                        freed += len
                    }
                }
            } catch (_: Exception) {}
        }
        return freed
    }
}
