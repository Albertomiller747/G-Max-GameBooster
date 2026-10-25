package com.example.model

data class PerformanceOption(
    val id: String,
    val rank: Int? = null,
    val title: String,
    val category: String,
    val tags: String,
    val description: String,
    val iconType: OptionIconType,
    val actionType: OptionActionType,
    val targetSettingIntent: String? = null,
    val adbCommand: String? = null,
    val isDirectAction: Boolean = false,
    val defaultApplied: Boolean = false,
    val isSupported: Boolean = true,
    val unsupportedReason: String? = null
)

enum class OptionIconType {
    REFRESH_RATE,
    GAME_MODE,
    DND_FOCUS,
    RAM_CLEAN,
    RESOLUTION_DOWNSCALE,
    AUTO_BRIGHTNESS,
    SCREEN_PINNING,
    ART_COMPILE,
    HEAVY_APPS,
    BLOATWARE,
    ANIMATION_SCALE,
    WIFI_5G,
    WIFI_LOW_LATENCY,
    WIFI_ASSIST,
    PRIVATE_DNS,
    THERMAL_HEADROOM,
    UNRESTRICTED_BATTERY,
    BYPASS_CHARGING,
    CROSSHAIR_HUD,
    LOW_LATENCY_AUDIO,
    GAMEPAD_CONTROLLER,
    SUSTAINED_PERFORMANCE,
    THREAD_PRIORITY
}

enum class OptionActionType {
    DIRECT_RAM_CLEAN,
    DIRECT_DND_LOCK,
    DIRECT_WIFI_LOCK,
    DIRECT_OVERLAY_HUD,
    DIRECT_SUSTAINED_PERFORMANCE,
    DIRECT_CONTROLLER_OVERCLOCK,
    DIRECT_THREAD_PRIORITY,
    DIRECT_SECURE_SETTINGS_ANIMATIONS,
    DIRECT_SECURE_SETTINGS_DNS,
    DIRECT_CROSSHAIR_OVERLAY,
    ADB_ONLY_COMMAND
}

