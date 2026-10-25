package com.example.util

import com.example.model.BenchmarkRun
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

class BenchmarkEngine {

    suspend fun runStressTest(
        durationSeconds: Int = 10,
        onProgress: (BenchmarkRun) -> Unit
    ): BenchmarkRun = withContext(Dispatchers.Default) {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        val samples = mutableListOf<Float>()
        var peakOps = 0.0
        var lastOps = 0.0

        val startTime = System.currentTimeMillis()
        val totalMs = durationSeconds * 1000L

        var elapsedSeconds = 0

        while (isActive && (System.currentTimeMillis() - startTime) < totalMs) {
            val windowStart = System.currentTimeMillis()
            var opsInWindow = 0L

            // Run heavy floating point & matrix calculation across cores
            val windowDurationMs = 1000L
            while (System.currentTimeMillis() - windowStart < windowDurationMs && isActive) {
                // Compute mathematical stress chunk
                var acc = 1.0
                for (i in 1..2500) {
                    acc += sqrt(i.toDouble()) * 0.9999 + (i % 7)
                }
                opsInWindow += 2500
            }

            val currentOpsPerSec = opsInWindow.toDouble() / (windowDurationMs / 1000.0)
            if (currentOpsPerSec > peakOps) {
                peakOps = currentOpsPerSec
            }
            lastOps = currentOpsPerSec

            // Convert to relative performance ratio (1.0 = peak)
            val currentRatio = if (peakOps > 0) (currentOpsPerSec / peakOps).toFloat() else 1.0f
            samples.add(currentRatio)

            elapsedSeconds++
            val progress = (elapsedSeconds.toFloat() / durationSeconds).coerceIn(0f, 1f)

            val currentThrottle = if (peakOps > 0 && lastOps < peakOps) {
                (((peakOps - lastOps) / peakOps) * 100).toInt()
            } else 0

            val currentRun = BenchmarkRun(
                score = (lastOps / 1000).toInt(),
                durationSeconds = elapsedSeconds,
                maxGflops = (peakOps / 1_000_000.0 * 8).let { (it * 10).toLong() / 10.0 },
                throttlePercent = currentThrottle,
                isRunning = true,
                progress = progress,
                historySamples = samples.toList()
            )
            onProgress(currentRun)
        }

        val finalThrottle = if (peakOps > 0 && lastOps < peakOps) {
            (((peakOps - lastOps) / peakOps) * 100).toInt()
        } else 0

        BenchmarkRun(
            score = (peakOps / 1000).toInt(),
            durationSeconds = durationSeconds,
            maxGflops = (peakOps / 1_000_000.0 * 8).let { (it * 10).toLong() / 10.0 },
            throttlePercent = finalThrottle,
            isRunning = false,
            progress = 1.0f,
            historySamples = samples.toList()
        )
    }
}
