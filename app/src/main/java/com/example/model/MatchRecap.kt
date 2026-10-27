package com.example.model

data class TelemetryPoint(
    val timestampMs: Long,
    val fps: Int,
    val tempC: Float,
    val batteryPct: Int
)

data class MatchRecap(
    val matchId: String = System.currentTimeMillis().toString(),
    val gameTitle: String = "Mobile Gaming Session",
    val startTimeMs: Long = System.currentTimeMillis() - 15 * 60 * 1000,
    val durationSeconds: Int = 900,
    val averageFps: Float = 58.4f,
    val low1PercentFps: Float = 47.0f,
    val stutterCount: Int = 3,
    val startTempC: Float = 31.0f,
    val maxTempC: Float = 39.2f,
    val endTempC: Float = 37.8f,
    val startBatteryPct: Int = 85,
    val endBatteryPct: Int = 78,
    val telemetryHistory: List<TelemetryPoint> = emptyList(),
    val thermalThrottlingDetected: Boolean = false,
    val throttleImpactPercent: Float = 0f
) {
    val batteryDrained: Int
        get() = (startBatteryPct - endBatteryPct).coerceAtLeast(0)

    val stabilityScore: Int
        get() {
            if (averageFps <= 0f) return 100
            val dropRatio = (averageFps - low1PercentFps) / averageFps
            val raw = 100 - (dropRatio * 100).toInt() - (stutterCount * 2)
            return raw.coerceIn(40, 100)
        }
}
