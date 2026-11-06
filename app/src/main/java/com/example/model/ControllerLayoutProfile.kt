package com.example.model

/**
 * User-selectable controller layout mode in Settings and Controls Overlay.
 */
enum class ControllerLayoutMode(
    val title: String,
    val subtitle: String,
    val shortBadge: String
) {
    AUTO(
        title = "Auto-Detect",
        subtitle = "Automatically matches connected PlayStation, Xbox, or Nintendo controller",
        shortBadge = "AUTO"
    ),
    PLAYSTATION(
        title = "PlayStation",
        subtitle = "DualShock 4 / DualSense (✕ ○ □ △ • L1/R1 • L2/R2 • Home)",
        shortBadge = "Home"
    ),
    XBOX(
        title = "Xbox",
        subtitle = "Xbox Wireless / Series X|S (A B X Y • LB/RB • LT/RT • Xbox Guide)",
        shortBadge = "XBOX"
    ),
    NINTENDO(
        title = "Nintendo Switch",
        subtitle = "Switch Pro / Joy-Con (B A Y X • L/R • ZL/ZR • Home)",
        shortBadge = "SWITCH"
    )
}

/**
 * Resolved physical controller brand for button glyphs and overlay rendering.
 */
enum class ResolvedControllerBrand {
    PLAYSTATION,
    XBOX,
    NINTENDO
}

/**
 * Complete button glyph & color mapping for the active controller layout.
 */
data class ControllerGlyphMap(
    val brand: ResolvedControllerBrand,
    val brandTitle: String,
    val southGlyph: String,      // Cross (✕) / A / B
    val eastGlyph: String,       // Circle (○) / B / A
    val westGlyph: String,       // Square (□) / X / Y
    val northGlyph: String,      // Triangle (△) / Y / X
    val southColorArgb: Long,
    val eastColorArgb: Long,
    val westColorArgb: Long,
    val northColorArgb: Long,
    val leftBumper: String,      // L1 / LB / L
    val rightBumper: String,     // R1 / RB / R
    val leftTrigger: String,     // L2 / LT / ZL
    val rightTrigger: String,    // R2 / RT / ZR
    val selectLabel: String,     // SHARE / VIEW / -
    val startLabel: String,      // OPTIONS / MENU / +
    val homeGlyph: String,       // PS / XBOX / HOME
    val homePromptName: String,  // PS / Xbox / Home
    val centerPadLabel: String   // TOUCHPAD / SHARE / CAPTURE
)

/**
 * Controls how the floating Controller Overlay captures D-Pad and analog thumbstick MotionEvents
 * when GMAX Gaming Hub is in the background (on the Home Screen or inside games).
 */
enum class StickTrackingMode(
    val titleEn: String,
    val titleEs: String,
    val subtitleEn: String,
    val subtitleEs: String
) {
    SMART_HYBRID(
        titleEn = "Smart Pass-Through (Game + Overlay)",
        titleEs = "Paso Inteligente (Juego + Superposición)",
        subtitleEn = "120Hz on Home Screen + high-speed pass-through sampling inside games",
        subtitleEs = "120Hz en Inicio + muestreo rápido con paso directo en juegos"
    ),
    FULL_120HZ_LOCK(
        titleEn = "Full 120Hz Stick Lock (Max Overlay)",
        titleEs = "Bloqueo Total 120Hz (Máx Superposición)",
        subtitleEn = "Locks 100% of D-Pad & analog stick axes to the overlay at 120Hz",
        subtitleEs = "Captura el 100% del D-Pad y joysticks en la superposición a 120Hz"
    )
}

/**
 * Configuration for the real-time floating Controller Input Screen Overlay.
 */
data class ControllerOverlayConfig(
    val isEnabled: Boolean = false,
    val autoScale: Boolean = true,
    val scaleFactor: Float = 1.0f,          // 0.55f .. 1.65f
    val opacity: Float = 0.92f,             // 0.25f .. 1.0f
    val lockTouchPassThrough: Boolean = false,
    val highlightColorArgb: Long = 0xFFE5252AL, // Crimson Red matching reference controller HUD
    val positionX: Int = -1,
    val positionY: Int = -1,
    val stickTrackingMode: StickTrackingMode = StickTrackingMode.SMART_HYBRID,
    val disableControllerSpeakerAudio: Boolean = true
)

object ControllerLayoutResolver {

    val playStationGlyphs = ControllerGlyphMap(
        brand = ResolvedControllerBrand.PLAYSTATION,
        brandTitle = "PlayStation",
        southGlyph = "✕",
        eastGlyph = "○",
        westGlyph = "□",
        northGlyph = "△",
        southColorArgb = 0xFF5C9DFFL, // PlayStation Cross Blue
        eastColorArgb = 0xFFFF5252L,  // PlayStation Circle Red
        westColorArgb = 0xFFE082FFL,  // PlayStation Square Pink
        northColorArgb = 0xFF26E6A6L, // PlayStation Triangle Green
        leftBumper = "L1",
        rightBumper = "R1",
        leftTrigger = "L2",
        rightTrigger = "R2",
        selectLabel = "SHARE",
        startLabel = "OPTIONS",
        homeGlyph = "Home",
        homePromptName = "Home",
        centerPadLabel = "TOUCHPAD"
    )

    val xboxGlyphs = ControllerGlyphMap(
        brand = ResolvedControllerBrand.XBOX,
        brandTitle = "Xbox",
        southGlyph = "A",
        eastGlyph = "B",
        westGlyph = "X",
        northGlyph = "Y",
        southColorArgb = 0xFF10B981L, // Xbox A Green
        eastColorArgb = 0xFFEF4444L,  // Xbox B Red
        westColorArgb = 0xFF3B82F6L,  // Xbox X Blue
        northColorArgb = 0xFFF59E0BL, // Xbox Y Yellow
        leftBumper = "LB",
        rightBumper = "RB",
        leftTrigger = "LT",
        rightTrigger = "RT",
        selectLabel = "VIEW",
        startLabel = "MENU",
        homeGlyph = "XBOX",
        homePromptName = "Xbox Guide",
        centerPadLabel = "SHARE"
    )

    val nintendoGlyphs = ControllerGlyphMap(
        brand = ResolvedControllerBrand.NINTENDO,
        brandTitle = "Nintendo Switch",
        southGlyph = "B",
        eastGlyph = "A",
        westGlyph = "Y",
        northGlyph = "X",
        southColorArgb = 0xFFF59E0BL, // Switch B
        eastColorArgb = 0xFFEF4444L,  // Switch A Red
        westColorArgb = 0xFF10B981L,  // Switch Y
        northColorArgb = 0xFF3B82F6L, // Switch X
        leftBumper = "L",
        rightBumper = "R",
        leftTrigger = "ZL",
        rightTrigger = "ZR",
        selectLabel = "−",
        startLabel = "+",
        homeGlyph = "HOME",
        homePromptName = "Home",
        centerPadLabel = "CAPTURE"
    )

    fun resolveBrand(
        mode: ControllerLayoutMode,
        gamepads: List<GamepadDevice> = emptyList(),
        activeControllerName: String = ""
    ): ResolvedControllerBrand {
        return when (mode) {
            ControllerLayoutMode.PLAYSTATION -> ResolvedControllerBrand.PLAYSTATION
            ControllerLayoutMode.XBOX -> ResolvedControllerBrand.XBOX
            ControllerLayoutMode.NINTENDO -> ResolvedControllerBrand.NINTENDO
            ControllerLayoutMode.AUTO -> {
                val primaryGamepad = gamepads.firstOrNull()
                val vendorId = primaryGamepad?.vendorId ?: 0
                val combinedName = "${primaryGamepad?.name ?: ""} $activeControllerName".lowercase()

                when {
                    // Sony Vendor ID (0x054C = 1356) or PlayStation keywords
                    vendorId == 0x054C ||
                        combinedName.contains("playstation") ||
                        combinedName.contains("dualsense") ||
                        combinedName.contains("dualshock") ||
                        combinedName.contains("ps4") ||
                        combinedName.contains("ps5") ||
                        combinedName.contains("sony") ||
                        combinedName.contains("wireless controller") -> ResolvedControllerBrand.PLAYSTATION

                    // Nintendo Vendor ID (0x057E = 1406) or Switch keywords
                    vendorId == 0x057E ||
                        combinedName.contains("nintendo") ||
                        combinedName.contains("switch") ||
                        combinedName.contains("joy-con") ||
                        combinedName.contains("pro controller") -> ResolvedControllerBrand.NINTENDO

                    // Microsoft Vendor ID (0x045E = 1118) or Xbox keywords
                    vendorId == 0x045E ||
                        combinedName.contains("xbox") ||
                        combinedName.contains("x-box") ||
                        combinedName.contains("microsoft") -> ResolvedControllerBrand.XBOX

                    // Default to PlayStation console style when no specific vendor matches
                    else -> ResolvedControllerBrand.PLAYSTATION
                }
            }
        }
    }

    fun getGlyphMap(
        mode: ControllerLayoutMode,
        gamepads: List<GamepadDevice> = emptyList(),
        activeControllerName: String = ""
    ): ControllerGlyphMap {
        return when (resolveBrand(mode, gamepads, activeControllerName)) {
            ResolvedControllerBrand.PLAYSTATION -> playStationGlyphs
            ResolvedControllerBrand.XBOX -> xboxGlyphs
            ResolvedControllerBrand.NINTENDO -> nintendoGlyphs
        }
    }
}
