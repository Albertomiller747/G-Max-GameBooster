package com.example.data

import android.content.Context
import com.example.model.OptionActionType
import com.example.model.OptionIconType
import com.example.model.PerformanceOption
import com.example.util.DeviceCapabilityChecker

object PerformanceOptionsRepository {

    fun getFeaturedOptions(context: Context): List<PerformanceOption> {
        val hasAndroid12 = DeviceCapabilityChecker.supportsAndroid12GameMode()
        val hasGamepad = DeviceCapabilityChecker.hasConnectedGamepad()

        return listOf(
            PerformanceOption(
                id = "pop_3_ram",
                title = "Kernel RAM Purge & Memory Compaction",
                category = "Memory & Kernel",
                tags = "RAM Clean | Cache Trim | GC",
                description = "Directly terminates cached background process memory and forces JVM garbage collection to guarantee texture buffer headroom.",
                iconType = OptionIconType.RAM_CLEAN,
                actionType = OptionActionType.DIRECT_RAM_CLEAN,
                isDirectAction = true,
                isSupported = true
            ),
            PerformanceOption(
                id = "net_low_latency",
                title = "Wi-Fi Chip Low-Latency Radio Lock",
                category = "Network Subsystem",
                tags = "Wi-Fi Lock | Hardware Polling | Low Jitter",
                description = "Directly acquires the Android Wi-Fi low-latency radio lock (WIFI_MODE_FULL_LOW_LATENCY) to eliminate packet jitter in multiplayer matches.",
                iconType = OptionIconType.WIFI_LOW_LATENCY,
                actionType = OptionActionType.DIRECT_WIFI_LOCK,
                isDirectAction = true,
                isSupported = true
            ),
            PerformanceOption(
                id = "perf_sustained_mode",
                title = "Sustained Hardware Performance Mode",
                category = "CPU & GPU Governor",
                tags = "PowerManager | Sustained Clocks | Anti-Throttle",
                description = "Signals the Android window manager to engage sustained performance governor mode, preventing sudden CPU frequency throttling.",
                iconType = OptionIconType.SUSTAINED_PERFORMANCE,
                actionType = OptionActionType.DIRECT_SUSTAINED_PERFORMANCE,
                isDirectAction = true,
                isSupported = true
            ),
            PerformanceOption(
                id = "ctrl_overlay_hud",
                title = "Real-Time Screen HUD (FPS, Temp, Battery)",
                category = "Screen Telemetry",
                tags = "Choreographer FPS | Real Temps | Overlay",
                description = "Renders an always-on-screen, hardware-synchronized FPS meter, live thermal sensor readouts, and battery gauge over all games.",
                iconType = OptionIconType.CROSSHAIR_HUD,
                actionType = OptionActionType.DIRECT_OVERLAY_HUD,
                isDirectAction = true,
                isSupported = true
            )
        )
    }

    fun getMostPopularTweaks(context: Context): List<PerformanceOption> {
        val hasGamepad = DeviceCapabilityChecker.hasConnectedGamepad()

        return listOf(
            PerformanceOption(
                id = "pop_3_ram",
                rank = 1,
                title = "Kernel RAM Purge & Memory Compaction",
                category = "Memory",
                tags = "RAM Clean | Cache Trim | GC",
                description = "Directly terminates cached background process memory and forces JVM garbage collection to guarantee texture buffer headroom.",
                iconType = OptionIconType.RAM_CLEAN,
                actionType = OptionActionType.DIRECT_RAM_CLEAN,
                isDirectAction = true,
                isSupported = true
            ),
            PerformanceOption(
                id = "net_low_latency",
                rank = 2,
                title = "Wi-Fi Chip Low-Latency Radio Lock",
                category = "Network",
                tags = "Wi-Fi Lock | Hardware Polling | Low Jitter",
                description = "Directly acquires the Android Wi-Fi low-latency radio lock to eliminate packet jitter in multiplayer matches.",
                iconType = OptionIconType.WIFI_LOW_LATENCY,
                actionType = OptionActionType.DIRECT_WIFI_LOCK,
                isDirectAction = true,
                isSupported = true
            ),
            PerformanceOption(
                id = "pop_2_dnd",
                rank = 3,
                title = "Do Not Disturb Gaming Focus Shield",
                category = "Focus",
                tags = "Priority Mute | No Popups | Quiet",
                description = "Directly activates Do Not Disturb priority mode to suppress phone calls, banner alerts, and popups while playing.",
                iconType = OptionIconType.DND_FOCUS,
                actionType = OptionActionType.DIRECT_DND_LOCK,
                isDirectAction = true,
                isSupported = true
            ),
            PerformanceOption(
                id = "ctrl_overlay_hud",
                rank = 4,
                title = "Real-Time Screen HUD (FPS, Temp, Battery)",
                category = "Screen Telemetry",
                tags = "Choreographer FPS | Real Temps | Overlay",
                description = "Renders an always-on-screen, hardware-synchronized FPS meter, live thermal sensor readouts, and battery gauge over all games.",
                iconType = OptionIconType.CROSSHAIR_HUD,
                actionType = OptionActionType.DIRECT_OVERLAY_HUD,
                isDirectAction = true,
                isSupported = true
            )
        )
    }

    fun getDirectHardwareTweaks(context: Context): List<PerformanceOption> {
        return listOf(
            PerformanceOption(
                id = "perf_sustained_mode",
                title = "Sustained Hardware Performance Mode",
                category = "Governor",
                tags = "Window | Sustained Clocks | Anti-Throttle",
                description = "Requests Android sustained performance mode to prevent CPU/GPU downclocking during lengthy gaming sessions.",
                iconType = OptionIconType.SUSTAINED_PERFORMANCE,
                actionType = OptionActionType.DIRECT_SUSTAINED_PERFORMANCE,
                isDirectAction = true,
                isSupported = true
            ),
            PerformanceOption(
                id = "perf_thread_priority",
                title = "Real-Time Thread Scheduler Priority",
                category = "CPU Scheduling",
                tags = "Thread Priority | Urgent Display | Fast Render",
                description = "Elevates process and rendering thread priority directly in the Linux scheduler to THREAD_PRIORITY_URGENT_DISPLAY.",
                iconType = OptionIconType.THREAD_PRIORITY,
                actionType = OptionActionType.DIRECT_THREAD_PRIORITY,
                isDirectAction = true,
                isSupported = true
            ),
            PerformanceOption(
                id = "ctrl_overlay_hud",
                title = "Real-Time Screen HUD (FPS, Temp, Battery)",
                category = "Overlay",
                tags = "Floating Pill | Live FPS | Hardware Sensors",
                description = "Draws real-time FPS frame intervals, battery temperature, and battery percentage over all games.",
                iconType = OptionIconType.CROSSHAIR_HUD,
                actionType = OptionActionType.DIRECT_OVERLAY_HUD,
                isDirectAction = true,
                isSupported = true
            ),
            PerformanceOption(
                id = "tweak_crosshair",
                title = "Tactical Screen Crosshair (8 Reticles & Neon Colors)",
                category = "Overlay",
                tags = "8 Reticle Designs | Neon Colors | Touch-Through",
                description = "Draws a centered precision crosshair reticle over games. Includes 8 distinct designs and customizable colors. (Notice: Check online anti-cheat rules before use).",
                iconType = OptionIconType.CROSSHAIR_HUD,
                actionType = OptionActionType.DIRECT_CROSSHAIR_OVERLAY,
                isDirectAction = true,
                isSupported = true
            )
        )
    }

    fun getSecureSettingsTweaks(context: Context): List<PerformanceOption> {
        val canWrite = com.example.util.SecureSettingsManager.canWriteSecureSettings(context)

        return listOf(
            PerformanceOption(
                id = "bg_animations",
                title = "Zero Animation Latency (0.0x Scales)",
                category = "System Latency",
                tags = if (canWrite) "Secure Settings | 0.0x Instant" else "Requires Developer Options",
                description = "Instantly sets window, transition, and animator scales to 0.0x for zero animation delays across the entire system.",
                iconType = OptionIconType.ANIMATION_SCALE,
                actionType = OptionActionType.DIRECT_SECURE_SETTINGS_ANIMATIONS,
                isDirectAction = true,
                isSupported = true
            ),
            PerformanceOption(
                id = "net_private_dns",
                title = "Cloudflare 1.1.1.1 Gaming DNS-over-TLS",
                category = "Network",
                tags = if (canWrite) "Secure Settings | 1.1.1.1 Active" else "Requires Developer Options",
                description = "Applies encrypted 1.1.1.1 Cloudflare DNS directly to eliminate ISP routing delays on game domain queries.",
                iconType = OptionIconType.PRIVATE_DNS,
                actionType = OptionActionType.DIRECT_SECURE_SETTINGS_DNS,
                isDirectAction = true,
                isSupported = true
            )
        )
    }

    fun getAllOptions(context: Context): List<PerformanceOption> {
        return (getMostPopularTweaks(context) +
                getDirectHardwareTweaks(context) +
                getSecureSettingsTweaks(context)).distinctBy { it.id }
    }
}
