package com.example.util

import android.app.usage.UsageStatsManager
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class GamerscoreData(
    val totalScore: Long,
    val rankTitle: String,
    val rankTier: String,
    val rankBadgeText: String,
    val primaryColorHex: String,
    val currentTierMinScore: Long,
    val nextTierMinScore: Long,
    val progressRatio: Float,
    val playTimePoints: Long,
    val sessionsPoints: Long,
    val milestoneBonusPoints: Long
)

data class DailyPlayTime(
    val dayLabel: String,
    val fullDateLabel: String,
    val totalMinutes: Long,
    val hours: Long,
    val minutesPart: Long,
    val isToday: Boolean
)

data class CyberBannerPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val startColorArgb: Long,
    val endColorArgb: Long,
    val accentColorArgb: Long
)

object GamerscoreEngine {

    val CYBER_BANNER_PRESETS: List<CyberBannerPreset> = listOf(
        CyberBannerPreset(
            id = "cyber_neon_grid",
            title = "Neon Synthwave",
            subtitle = "Hyperdrive Grid • Cyber Magenta",
            startColorArgb = 0xFF0B0F19L,
            endColorArgb = 0xFF4A0E4EL,
            accentColorArgb = 0xFF00E5FFL
        ),
        CyberBannerPreset(
            id = "obsidian_beast",
            title = "Obsidian Beast",
            subtitle = "Titan Overclock • Blood Crimson",
            startColorArgb = 0xFF140707L,
            endColorArgb = 0xFF5C0E14L,
            accentColorArgb = 0xFFFF2A4BL
        ),
        CyberBannerPreset(
            id = "quantum_core",
            title = "Quantum Matrix",
            subtitle = "Zero-Latency • Emerald Flux",
            startColorArgb = 0xFF051811L,
            endColorArgb = 0xFF0E4E30L,
            accentColorArgb = 0xFF00E676L
        ),
        CyberBannerPreset(
            id = "solar_flare",
            title = "Solar Vanguard",
            subtitle = "Ray Tracing • Cyber Gold",
            startColorArgb = 0xFF1A1305L,
            endColorArgb = 0xFF5C4308L,
            accentColorArgb = 0xFFFFB300L
        ),
        CyberBannerPreset(
            id = "void_horizon",
            title = "Void Horizon",
            subtitle = "Deep Space • Cosmic Violet",
            startColorArgb = 0xFF0C071FL,
            endColorArgb = 0xFF311568L,
            accentColorArgb = 0xFFB388FFL
        )
    )

    /**
     * Calculates an uncapped Gamerscore based on cumulative play minutes, sessions, and milestones.
     * There is NO maximum score cap.
     */
    fun calculateGamerscore(totalMinutes: Long, totalSessions: Long): GamerscoreData {
        val safeMinutes = totalMinutes.coerceAtLeast(0L)
        val safeSessions = totalSessions.coerceAtLeast(0L)

        val playTimePoints = safeMinutes * 10L
        val sessionsPoints = safeSessions * 50L
        val hourMilestones = (safeMinutes / 600L) * 500L // 500 pts per 10 hours
        val sessionMilestones = (safeSessions / 10L) * 250L // 250 pts per 10 sessions
        val milestoneBonusPoints = hourMilestones + sessionMilestones

        val totalScore = playTimePoints + sessionsPoints + milestoneBonusPoints

        val (rankTitle, rankTier, rankBadgeText, colorHex, minScore, nextScore) = when {
            totalScore < 500L -> RankDef("Recruit", "Tier I", "RCT", "#9E9E9E", 0L, 500L)
            totalScore < 1500L -> RankDef("Bronze Striker", "Tier II", "BRZ", "#CD7F32", 500L, 1500L)
            totalScore < 3500L -> RankDef("Silver Vanguard", "Tier III", "SLV", "#C0C0C0", 1500L, 3500L)
            totalScore < 7500L -> RankDef("Gold Warlord", "Tier IV", "GLD", "#FFD700", 3500L, 7500L)
            totalScore < 15000L -> RankDef("Platinum Overlord", "Tier V", "PLT", "#00E5FF", 7500L, 15000L)
            totalScore < 30000L -> RankDef("Diamond Apex", "Tier VI", "DIA", "#7C4DFF", 15000L, 30000L)
            totalScore < 60000L -> RankDef("Master Sentinel", "Tier VII", "MST", "#FF1744", 30000L, 60000L)
            totalScore < 100000L -> RankDef("Grandmaster Sovereign", "Tier VIII", "GMS", "#00E676", 60000L, 100000L)
            else -> {
                // Uncapped Apex Legend levels: progresses every 50,000 points
                val level = ((totalScore - 100000L) / 50000L) + 1L
                val tierMin = 100000L + (level - 1L) * 50000L
                val tierNext = tierMin + 50000L
                RankDef("Apex Legend Lv.$level", "Prestige $level", "APX", "#FFD700", tierMin, tierNext)
            }
        }

        val range = (nextScore - minScore).coerceAtLeast(1L)
        val progress = ((totalScore - minScore).toFloat() / range.toFloat()).coerceIn(0f, 1f)

        return GamerscoreData(
            totalScore = totalScore,
            rankTitle = rankTitle,
            rankTier = rankTier,
            rankBadgeText = rankBadgeText,
            primaryColorHex = colorHex,
            currentTierMinScore = minScore,
            nextTierMinScore = nextScore,
            progressRatio = progress,
            playTimePoints = playTimePoints,
            sessionsPoints = sessionsPoints,
            milestoneBonusPoints = milestoneBonusPoints
        )
    }

    private data class RankDef(
        val title: String,
        val tier: String,
        val badge: String,
        val colorHex: String,
        val minScore: Long,
        val nextScore: Long
    )

    /**
     * Resolves 7-day gaming time breakdown for the profile bar chart.
     * Combines system usage events (if permission granted) and G-Max hub tracking.
     */
    fun get7DayGamingHistory(context: Context, totalPlayTimeMinutes: Long): List<DailyPlayTime> {
        val daysList = mutableListOf<DailyPlayTime>()
        val dayFormat = SimpleDateFormat("EEE", Locale.US)
        val fullDateFormat = SimpleDateFormat("MMM d", Locale.US)

        val now = System.currentTimeMillis()

        // Gather real usage stats if possible
        val dailyUsageMap = mutableMapOf<String, Long>()
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usm != null) {
                val startOfWindow = now - (7L * 24L * 60L * 60L * 1000L)
                val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfWindow, now)
                for (stat in stats) {
                    val dStr = fullDateFormat.format(Date(stat.firstTimeStamp))
                    val mins = stat.totalTimeInForeground / 60_000L
                    if (mins > 0) {
                        dailyUsageMap[dStr] = (dailyUsageMap[dStr] ?: 0L) + mins
                    }
                }
            }
        } catch (_: Exception) {}

        // Build list for past 6 days + today
        for (i in 6 downTo 0) {
            val targetCal = Calendar.getInstance().apply {
                timeInMillis = now - (i * 24L * 60L * 60L * 1000L)
            }
            val dayLabel = dayFormat.format(targetCal.time).uppercase(Locale.US)
            val fullDate = fullDateFormat.format(targetCal.time)
            val isToday = (i == 0)

            var dayMins = dailyUsageMap[fullDate] ?: 0L

            // If system usage was empty, distribute user's actual total play time reasonably across recent days
            if (dailyUsageMap.isEmpty() && totalPlayTimeMinutes > 0L) {
                // Distribute proportional sample pattern based on day of week
                val weights = listOf(0.18f, 0.12f, 0.14f, 0.10f, 0.16f, 0.20f, 0.10f)
                val w = weights[targetCal.get(Calendar.DAY_OF_WEEK) % weights.size]
                dayMins = (totalPlayTimeMinutes * w).toLong()
            }

            val hrs = dayMins / 60L
            val minsPart = dayMins % 60L

            daysList.add(
                DailyPlayTime(
                    dayLabel = dayLabel,
                    fullDateLabel = fullDate,
                    totalMinutes = dayMins,
                    hours = hrs,
                    minutesPart = minsPart,
                    isToday = isToday
                )
            )
        }

        return daysList
    }
}
