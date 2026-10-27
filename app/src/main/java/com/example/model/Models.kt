package com.example.model

import androidx.compose.ui.graphics.Color

data class MemoryStats(
    val totalRamMb: Long = 0,
    val availRamMb: Long = 0,
    val usedRamMb: Long = 0,
    val thresholdMb: Long = 0,
    val isLowMemory: Boolean = false,
    val internalStorageFreeGb: Double = 0.0,
    val internalStorageTotalGb: Double = 0.0
) {
    val ramUsedPercent: Int
        get() = if (totalRamMb > 0) ((usedRamMb.toDouble() / totalRamMb) * 100).toInt() else 0

    val storageUsedPercent: Int
        get() = if (internalStorageTotalGb > 0) {
            (((internalStorageTotalGb - internalStorageFreeGb) / internalStorageTotalGb) * 100).toInt()
        } else 0
}

data class ThermalInfo(
    val thermalStatusInt: Int = 0,
    val thermalStatusName: String = "Normal",
    val thermalHeadroom: Float = 1.0f, // 0.0 (throttled) to 1.0+ (ample cooling)
    val batteryTempC: Float = 28.0f,
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val batteryVoltageMv: Int = 4000,
    val batteryHealth: String = "Good",
    val isThrottlingRisk: Boolean = false
)

data class NetworkPingResult(
    val regionName: String,
    val host: String,
    val pingMs: Long = 0,
    val jitterMs: Long = 0,
    val packetLossPercent: Int = 0,
    val isTesting: Boolean = false,
    val isCompleted: Boolean = false,
    val error: String? = null
)

data class GamepadDevice(
    val id: Int,
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val hasVibrator: Boolean = false,
    val isConnected: Boolean = true
)

data class GameGuidance(
    val title: String,
    val packageName: String,
    val genre: String,
    val recommendedFpsCap: String,
    val recommendedResolution: String,
    val recommendedGraphics: String,
    val antiAliasingAdvice: String,
    val shadowsAdvice: String,
    val lowLatencyTip: String,
    val thermalRisk: String, // Low, Medium, High
    val suggestedAdbCommand: String
)

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val isGame: Boolean,
    val versionName: String = "1.0",
    val memoryUsageMb: Long = 0
)

data class AdbCommandTweak(
    val id: String,
    val category: String,
    val title: String,
    val command: String,
    val revertCommand: String,
    val description: String,
    val requiresRootOrShizuku: Boolean = true,
    val impactRating: String // "High", "Medium", "Safe"
)

data class OverlaySettings(
    val isEnabled: Boolean = false,
    val crosshairStyle: Int = 0, // 0: Classic Cross, 1: Dot, 2: Circle+Dot, 3: Chevron, 4: T-Pose, 5: Sniper, 6: Diamond, 7: Circle+Cross
    val crosshairColor: Long = 0xFF00E5FF,
    val crosshairSizeDp: Int = 32,
    val crosshairAlpha: Float = 0.95f,
    val showTimer: Boolean = true,
    val timerSeconds: Int = 0,
    val isTimerRunning: Boolean = false,
    val antiCheatNoticeAcknowledged: Boolean = false
)

data class DeviceReadiness(
    val score: Int = 85,
    val status: String = "Game Ready",
    val warnings: List<String> = emptyList(),
    val recommendations: List<String> = emptyList()
)

data class BenchmarkRun(
    val score: Int = 0,
    val durationSeconds: Int = 0,
    val maxGflops: Double = 0.0,
    val throttlePercent: Int = 0,
    val isRunning: Boolean = false,
    val progress: Float = 0f,
    val historySamples: List<Float> = emptyList()
)

enum class AppBackgroundTheme(
    val title: String,
    val description: String,
    val backgroundColor: Color,
    val surfaceColor: Color,
    val borderColor: Color
) {
    AUTO_SYSTEM(
        title = "Auto (System Light / Dark)",
        description = "Automatically switches between Light and Dark to match your device display",
        backgroundColor = Color(0xFF121417),
        surfaceColor = Color(0xFF1C1F24),
        borderColor = Color(0xFF2E333D)
    ),
    LIGHT_HUB(
        title = "Light Mode",
        description = "Clean, high-contrast alabaster and pure white consumer theme",
        backgroundColor = Color(0xFFF8FAFC),
        surfaceColor = Color(0xFFFFFFFF),
        borderColor = Color(0xFFE2E8F0)
    ),
    DARK_HUB(
        title = "Dark Mode",
        description = "Console charcoal background with elevated carbon surfaces",
        backgroundColor = Color(0xFF121417),
        surfaceColor = Color(0xFF1C1F24),
        borderColor = Color(0xFF2E333D)
    ),
    CUSTOM_COLOR(
        title = "Custom Background",
        description = "Personalize the hub with your own custom background color or preset",
        backgroundColor = Color(0xFF181C24),
        surfaceColor = Color(0xFF222733),
        borderColor = Color(0xFF333B4D)
    );

    companion object {
        val DARK_SLATE = AUTO_SYSTEM
    }
}

data class CustomBackgroundPreset(
    val name: String,
    val bgHex: Long,
    val surfaceHex: Long,
    val borderHex: Long,
    val isLight: Boolean = false
)

enum class BoostMode(
    val title: String,
    val subtitle: String,
    val description: String,
    val accentColor: Color
) {
    GAMING(
        title = "Gaming Mode",
        subtitle = "Normal Boost",
        description = "Balanced optimization for smooth casual to competitive gaming. Flushes app caches, trims memory, and sets high thread priority.",
        accentColor = Color(0xFF3B82F6)
    ),
    BEAST(
        title = "Beast Mode",
        subtitle = "Intense FPS Boost",
        description = "Maximum performance profile for heavy games. Aggressive RAM compaction, sustained performance mode, maximum refresh rate, and auto-activates screen HUD.",
        accentColor = Color(0xFFEF4444)
    )
}

data class PhoneHardwareSpecs(
    val deviceName: String = "Unknown Device",
    val manufacturer: String = "Unknown",
    val model: String = "Unknown",
    val board: String = "Unknown",
    val androidVersion: String = "Unknown",
    val apiLevel: Int = 0,
    val securityPatch: String = "Unknown",
    val socModel: String = "Unknown",
    val cpuCores: Int = 8,
    val cpuAbi: String = "arm64-v8a",
    val gpuRenderer: String = "Adreno / Mali GPU",
    val openGlVersion: String = "OpenGL ES 3.2",
    val vulkanSupported: Boolean = true,
    val totalRamMb: Long = 0,
    val availRamMb: Long = 0,
    val totalStorageGb: Double = 0.0,
    val freeStorageGb: Double = 0.0,
    val displayResolution: String = "1080 x 2400",
    val refreshRateHz: Int = 60,
    val supportedRefreshRates: List<Int> = listOf(60),
    val screenDensityDpi: Int = 420
)

data class OverlayHudConfig(
    val isEnabled: Boolean = false,
    val showFps: Boolean = true,
    val showTemp: Boolean = true,
    val showBattery: Boolean = true,
    val showTimer: Boolean = true,
    val showRamPct: Boolean = true,
    val showClock: Boolean = true,
    val fpsMode: String = "BALANCED", // "FAST", "BALANCED", "SMOOTH"
    val targetCapMode: String = "AUTO" // "AUTO", "30", "45", "60", "90", "120", "144", "165"
)
