package com.example.util

import java.util.Locale

enum class ProfileTagCategory(
    val title: String,
    val accentColorHex: Long
) {
    PLAYSTYLE("Playstyle", 0xFF38BDF8L),
    GENRES("Genres", 0xFF00E676L),
    COMPETITIVE("Competitive Tiers", 0xFFC084FCL),
    HARDWARE("Hardware & Tech", 0xFFFBBF24L),
    VIBES("Vibes & Community", 0xFFFB7185L)
}

data class PremadeProfileTag(
    val id: String,
    val label: String,
    val category: ProfileTagCategory
)

object PremadeTagsCatalog {
    const val MAX_EQUIPPED_TAGS = 4

    const val OFFICIAL_DEVELOPER_EMAIL = "albertomiller747@gmail.com"
    const val OFFICIAL_DEVELOPER_DISPLAY_email_DISPLAY = "Albertomiller747@gmail.com"
    const val OFFICIAL_DEVELOPER_NAME = "Alberto Miller"
    const val OFFICIAL_DEVELOPER_BIO = "Official Creator & Lead Developer of G-Max Game Booster ★"
    const val OFFICIAL_DEVELOPER_DOB = "1999-07-07"

    val OFFICIAL_DEVELOPER_DEFAULT_TAGS: List<String> = listOf(
        "max_efps_hunter",
        "120hz_enthusiast",
        "esports_ready",
        "day_one_og"
    )

    /**
     * 45 curated premade profile tags across 5 categories.
     * Note: The Gold-Star Developer tag is strictly excluded from this public selectable list
     * and is granted exclusively to Albertomiller747@gmail.com as a permanent 5th badge.
     */
    val ALL_TAGS: List<PremadeProfileTag> = listOf(
        // 1. PLAYSTYLE (9 tags)
        PremadeProfileTag("aggressive_rusher", "Aggressive Rusher", ProfileTagCategory.PLAYSTYLE),
        PremadeProfileTag("tactical_igl", "Tactical IGL", ProfileTagCategory.PLAYSTYLE),
        PremadeProfileTag("sniper_main", "Sniper Main", ProfileTagCategory.PLAYSTYLE),
        PremadeProfileTag("clutch_specialist", "Clutch Specialist", ProfileTagCategory.PLAYSTYLE),
        PremadeProfileTag("stealth_assassin", "Stealth Assassin", ProfileTagCategory.PLAYSTYLE),
        PremadeProfileTag("support_anchor", "Support Anchor", ProfileTagCategory.PLAYSTYLE),
        PremadeProfileTag("solo_queue_warrior", "Solo Queue Warrior", ProfileTagCategory.PLAYSTYLE),
        PremadeProfileTag("squad_captain", "Squad Captain", ProfileTagCategory.PLAYSTYLE),
        PremadeProfileTag("speedrunner", "Speedrunner", ProfileTagCategory.PLAYSTYLE),

        // 2. GENRES (9 tags)
        PremadeProfileTag("fps_veteran", "FPS Veteran", ProfileTagCategory.GENRES),
        PremadeProfileTag("battle_royale_dropper", "Battle Royale Dropper", ProfileTagCategory.GENRES),
        PremadeProfileTag("moba_strategist", "MOBA Strategist", ProfileTagCategory.GENRES),
        PremadeProfileTag("rpg_completionist", "RPG Completionist", ProfileTagCategory.GENRES),
        PremadeProfileTag("apex_racer", "Apex Racer", ProfileTagCategory.GENRES),
        PremadeProfileTag("fighter_main", "Fighter Main", ProfileTagCategory.GENRES),
        PremadeProfileTag("open_world_explorer", "Open World Explorer", ProfileTagCategory.GENRES),
        PremadeProfileTag("survival_crafter", "Survival Crafter", ProfileTagCategory.GENRES),
        PremadeProfileTag("rhythm_god", "Rhythm God", ProfileTagCategory.GENRES),

        // 3. COMPETITIVE TIERS (9 tags)
        PremadeProfileTag("esports_ready", "Esports Ready", ProfileTagCategory.COMPETITIVE),
        PremadeProfileTag("top_1_percent", "Top 1% Ranked", ProfileTagCategory.COMPETITIVE),
        PremadeProfileTag("grandmaster_tier", "Grandmaster Tier", ProfileTagCategory.COMPETITIVE),
        PremadeProfileTag("conqueror_rank", "Conqueror Rank", ProfileTagCategory.COMPETITIVE),
        PremadeProfileTag("tournament_grinder", "Tournament Grinder", ProfileTagCategory.COMPETITIVE),
        PremadeProfileTag("scrim_regular", "Scrim Regular", ProfileTagCategory.COMPETITIVE),
        PremadeProfileTag("leaderboard_climber", "Leaderboard Climber", ProfileTagCategory.COMPETITIVE),
        PremadeProfileTag("mvp_streak", "MVP Streak", ProfileTagCategory.COMPETITIVE),
        PremadeProfileTag("undefeated_ace", "Undefeated Ace", ProfileTagCategory.COMPETITIVE),

        // 4. HARDWARE & TECH (9 tags)
        PremadeProfileTag("120hz_enthusiast", "120Hz Enthusiast", ProfileTagCategory.HARDWARE),
        PremadeProfileTag("claw_grip_player", "4-Finger Claw", ProfileTagCategory.HARDWARE),
        PremadeProfileTag("gyro_aim_pro", "Gyro Aim Pro", ProfileTagCategory.HARDWARE),
        PremadeProfileTag("controller_pro", "Controller Pro", ProfileTagCategory.HARDWARE),
        PremadeProfileTag("max_efps_hunter", "Max EFPS Hunter", ProfileTagCategory.HARDWARE),
        PremadeProfileTag("low_ping_purist", "Low Ping Purist", ProfileTagCategory.HARDWARE),
        PremadeProfileTag("thermal_optimizer", "Thermal Optimizer", ProfileTagCategory.HARDWARE),
        PremadeProfileTag("adb_power_user", "ADB Power User", ProfileTagCategory.HARDWARE),
        PremadeProfileTag("oled_hdr_gamer", "OLED HDR Gamer", ProfileTagCategory.HARDWARE),

        // 5. VIBES & COMMUNITY (9 tags)
        PremadeProfileTag("night_owl", "Night Owl", ProfileTagCategory.VIBES),
        PremadeProfileTag("chill_vibes", "Chill Vibes", ProfileTagCategory.VIBES),
        PremadeProfileTag("mic_on_comms", "Mic On Comms", ProfileTagCategory.VIBES),
        PremadeProfileTag("silent_carry", "Silent Carry", ProfileTagCategory.VIBES),
        PremadeProfileTag("content_creator", "Content Creator", ProfileTagCategory.VIBES),
        PremadeProfileTag("trophy_hunter", "100% Trophy Hunter", ProfileTagCategory.VIBES),
        PremadeProfileTag("day_one_og", "Day One OG", ProfileTagCategory.VIBES),
        PremadeProfileTag("weekend_grinder", "Weekend Grinder", ProfileTagCategory.VIBES),
        PremadeProfileTag("good_vibes_only", "Good Vibes Only", ProfileTagCategory.VIBES)
    )

    private val TAG_MAP: Map<String, PremadeProfileTag> = ALL_TAGS.associateBy { it.id }
    val VALID_TAG_IDS: Set<String> = TAG_MAP.keys

    fun getTagById(id: String): PremadeProfileTag? = TAG_MAP[id]

    fun resolveTags(ids: List<String>): List<PremadeProfileTag> {
        return sanitizeSelectedTags(ids).mapNotNull { TAG_MAP[it] }
    }

    /**
     * Filters any list of tag IDs strictly against the 45 premade tags and caps at 4.
     * Prevents any custom/injected "developer" tag ID from ever entering selectedTags.
     */
    fun sanitizeSelectedTags(rawIds: Collection<String>): List<String> {
        return rawIds
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it in VALID_TAG_IDS }
            .distinct()
            .take(MAX_EQUIPPED_TAGS)
    }

    /**
     * Returns true ONLY if the provided email matches Albertomiller747@gmail.com (case-insensitive).
     */
    fun isOfficialDeveloperEmail(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        return email.trim().lowercase(Locale.ROOT) == OFFICIAL_DEVELOPER_EMAIL
    }

    /**
     * Prevents random non-developer users from spoofing the developer badge or gold star in their display name.
     */
    fun sanitizeDisplayName(rawName: String, isVerifiedDeveloper: Boolean): String {
        val trimmed = rawName.trim().take(100)
        if (trimmed.isEmpty()) return if (isVerifiedDeveloper) OFFICIAL_DEVELOPER_NAME else "Gamer"
        if (isVerifiedDeveloper) return trimmed
        // Strip gold stars and developer impersonation strings from non-developer display names
        val cleaned = trimmed
            .replace("★", "")
            .replace("⭐", "")
            .replace(Regex("(?i)\\b(verified\\s*developer|official\\s*developer|g-max\\s*developer|app\\s*developer)\\b"), "")
            .trim()
        return cleaned.ifEmpty { "Gamer" }
    }
}
