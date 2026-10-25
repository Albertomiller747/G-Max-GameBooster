package com.example.data

import com.example.model.AdbCommandTweak

object AdbCommandsRepository {

    val tweaksList: List<AdbCommandTweak> = listOf(
        AdbCommandTweak(
            id = "game_mode_perf",
            category = "Game Manager (Android 12+)",
            title = "Force Android 12+ Game Mode: Performance",
            command = "cmd game mode performance <package>",
            revertCommand = "cmd game mode standard <package>",
            description = "Forces the Android system GameManager to prioritize CPU/GPU burst frequencies and thermal headroom for this specific game package.",
            impactRating = "High"
        ),
        AdbCommandTweak(
            id = "game_downscale",
            category = "Game Manager (Android 12+)",
            title = "Internal Render Downscale (0.75x)",
            command = "cmd game downscale 0.75 <package>",
            revertCommand = "cmd game reset <package>",
            description = "Directly commands Android SurfaceFlinger to downscale internal 3D backbuffer to 75%. Massively increases GPU framerate and drops temperatures on 1080p/1440p displays.",
            impactRating = "High"
        ),
        AdbCommandTweak(
            id = "game_fps_cap",
            category = "Game Manager (Android 12+)",
            title = "Per-Game Framerate Lock (60 FPS / 90 FPS)",
            command = "cmd game fps 60 <package>",
            revertCommand = "cmd game reset <package>",
            description = "Enforces a stable frame cap via GameManager to prevent wild jitter between 45 and 90 FPS, ensuring smooth frame pacing.",
            impactRating = "Medium"
        ),
        AdbCommandTweak(
            id = "wm_size_downscale",
            category = "Display & GPU Load",
            title = "Display Downscaler (wm size)",
            command = "wm size 1080x2400",
            revertCommand = "wm size reset",
            description = "Lowers the entire physical framebuffer resolution. Downscaling a 1440p panel to 1080p cuts GPU fillrate demand by over 40% for the highest possible FPS gains.",
            impactRating = "High"
        ),
        AdbCommandTweak(
            id = "wm_density",
            category = "Display & GPU Load",
            title = "Display Density Scale (wm density)",
            command = "wm density 380",
            revertCommand = "wm density reset",
            description = "Matches UI DPI scaling to prevent oversized elements after applying custom 'wm size' resolution changes.",
            impactRating = "Medium"
        ),
        AdbCommandTweak(
            id = "art_compile_speed",
            category = "Stutter Reduction (JIT/AOT)",
            title = "ART Speed-Profile Pre-Compilation",
            command = "cmd package compile -m speed-profile -f <package>",
            revertCommand = "cmd package compile -r first-boot <package>",
            description = "Compiles the game bytecode Ahead-Of-Time (AOT) to native machine code. Eliminates runtime JIT stutter, micro-freezes, and shader compilation hitches.",
            impactRating = "High"
        ),
        AdbCommandTweak(
            id = "art_compile_full",
            category = "Stutter Reduction (JIT/AOT)",
            title = "Full AOT Speed Compilation (cmd package)",
            command = "cmd package compile -m speed -f <package>",
            revertCommand = "cmd package compile -r first-boot <package>",
            description = "Compiles all application classes and DEX methods with maximum optimizations for competitive stability.",
            impactRating = "High"
        ),
        AdbCommandTweak(
            id = "animation_scales",
            category = "Developer Options",
            title = "Snappy System Animation Scales (0.5x)",
            command = "settings put global window_animation_scale 0.5 && settings put global transition_animation_scale 0.5 && settings put global animator_duration_scale 0.5",
            revertCommand = "settings put global window_animation_scale 1.0 && settings put global transition_animation_scale 1.0 && settings put global animator_duration_scale 1.0",
            description = "Halves window transition and animator scale. Makes app switching and returning to home screen feel instantaneous (does not directly change 3D FPS).",
            impactRating = "Safe"
        ),
        AdbCommandTweak(
            id = "animation_scales_off",
            category = "Developer Options",
            title = "Disable System Animations (0.0x / Instant)",
            command = "settings put global window_animation_scale 0.0 && settings put global transition_animation_scale 0.0 && settings put global animator_duration_scale 0.0",
            revertCommand = "settings put global window_animation_scale 1.0 && settings put global transition_animation_scale 1.0 && settings put global animator_duration_scale 1.0",
            description = "Completely removes system window animations for zero delay when toggling floating tools or returning to game.",
            impactRating = "Safe"
        ),
        AdbCommandTweak(
            id = "private_dns_cloudflare",
            category = "Network Optimization",
            title = "Cloudflare 1.1.1.1 Gaming Private DNS",
            command = "settings put global private_dns_mode hostname && settings put global private_dns_specifier 1dot1dot1dot1.cloudflare-dns.com",
            revertCommand = "settings put global private_dns_mode opportunistic",
            description = "Enforces encrypted Cloudflare 1.1.1.1 DNS over TLS. Note: speeds up host domain lookups, but does not alter direct in-match server UDP packet ping.",
            impactRating = "Safe"
        ),
        AdbCommandTweak(
            id = "private_dns_google",
            category = "Network Optimization",
            title = "Google 8.8.8.8 Private DNS",
            command = "settings put global private_dns_mode hostname && settings put global private_dns_specifier dns.google",
            revertCommand = "settings put global private_dns_mode opportunistic",
            description = "Enforces Google DNS over TLS for reliable zero-poisoning game server address resolution.",
            impactRating = "Safe"
        ),
        AdbCommandTweak(
            id = "disable_bloatware",
            category = "Background Process Clean",
            title = "Freeze/Disable Bloatware Package",
            command = "pm disable-user --user 0 <package>",
            revertCommand = "pm enable <package>",
            description = "Disables heavy pre-installed OEM or carrier services without root. Prevents wake locks and background battery drain during gaming.",
            impactRating = "Medium"
        ),
        AdbCommandTweak(
            id = "grant_secure_settings",
            category = "System Grant",
            title = "Grant WRITE_SECURE_SETTINGS to G MAX Booster",
            command = "pm grant com.aistudio.gamebooster.kxmpzq android.permission.WRITE_SECURE_SETTINGS",
            revertCommand = "pm revoke com.aistudio.gamebooster.kxmpzq android.permission.WRITE_SECURE_SETTINGS",
            description = "Permits G MAX Booster to toggle animations, private DNS, and display parameters directly on supported devices.",
            impactRating = "Safe"
        )
    )
}
