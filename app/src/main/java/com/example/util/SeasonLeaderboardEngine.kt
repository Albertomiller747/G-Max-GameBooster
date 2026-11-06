package com.example.util

import android.content.Context
import com.example.data.repository.UsernameClaim

/**
 * Represents a seasonal badge on a player profile.
 */
data class TopFiveSeasonBadge(
    val rank: Int,
    val tagLabel: String,
    val seasonNumber: Int,
    val expiresAtMillis: Long,
    val daysRemaining: Int,
    val hoursRemaining: Int,
    val formattedRemaining: String,
    val isGraceShieldActive: Boolean = false,
    val overtakenByCount: Int = 0
)

/**
 * Public-only profile data shown on the External User Profile screen
 * when a user searches for a player by username on the Profile tab.
 */
data class ExternalPlayerProfileData(
    val userId: String,
    val displayName: String,
    val username: String,
    val authProvider: String = "Google",
    val photoUrl: String,
    val countryCode: String,
    val countryName: String,
    val flagEmoji: String,
    val bio: String,
    val gamerscore: Long,
    val rankTitle: String,
    val rankTier: String,
    val currentLeaderboardPosition: Int = 0,
    val topFiveBadge: TopFiveSeasonBadge? = null,
    val isVerifiedDeveloper: Boolean = false,
    val isCurrentUser: Boolean = false
)

/**
 * Represents the 14-day season state, countdown, and halving multiplier for Gamerscore.
 */
data class SeasonLeaderboardState(
    val seasonNumber: Int = 1,
    val seasonStartMillis: Long = 0L,
    val seasonEndMillis: Long = 0L,
    val daysRemaining: Int = 14,
    val hoursRemaining: Int = 0,
    val minutesRemaining: Int = 0,
    val formattedCountdown: String = "14d 0h",
    val seasonProgressFraction: Float = 0f,
    val appliedHalvingCount: Int = 0,
    val activeTopFiveBadge: TopFiveSeasonBadge? = null,
    val isInTopTen: Boolean = false,
    val currentRank: Int = 0
)

object SeasonLeaderboardEngine {

    // Exactly 14 days in milliseconds
    const val SEASON_DURATION_MS: Long = 14L * 24L * 60L * 60L * 1000L
    private const val DAY_MS: Long = 24L * 60L * 60L * 1000L
    private const val HOUR_MS: Long = 60L * 60L * 1000L
    private const val MINUTE_MS: Long = 60L * 1000L

    // Fixed anchor epoch (Jan 1, 2025 00:00:00 UTC) so all 14-day cycles align deterministically
    private const val SEASON_EPOCH_MS: Long = 1735689600000L

    private const val PREFS_NAME = "gmax_season_leaderboard_prefs"
    private const val KEY_LAST_PROCESSED_SEASON = "last_processed_season_index"
    private const val KEY_HALVING_COUNT = "cumulative_season_halving_count"
    private const val KEY_MANUAL_SEASON_OFFSET = "manual_season_cycle_offset"

    /**
     * Computes the active 14-day season index and boundaries.
     */
    fun getSeasonTiming(context: Context, nowMillis: Long = System.currentTimeMillis()): Triple<Int, Long, Long> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val manualOffset = prefs.getInt(KEY_MANUAL_SEASON_OFFSET, 0)
        val elapsedSinceEpoch = (nowMillis - SEASON_EPOCH_MS).coerceAtLeast(0L)
        val rawCycleIndex = (elapsedSinceEpoch / SEASON_DURATION_MS).toInt() + 1
        val effectiveSeasonNumber = (rawCycleIndex + manualOffset).coerceAtLeast(1)

        val cycleStart = SEASON_EPOCH_MS + ((rawCycleIndex - 1).toLong() * SEASON_DURATION_MS)
        val cycleEnd = cycleStart + SEASON_DURATION_MS
        return Triple(effectiveSeasonNumber, cycleStart, cycleEnd)
    }

    /**
     * Checks whether a 14-day season boundary has passed since the last recorded check.
     */
    fun synchronizeSeasonCycle(context: Context, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val (currentSeason, _, _) = getSeasonTiming(context, nowMillis)
        val lastProcessed = prefs.getInt(KEY_LAST_PROCESSED_SEASON, -1)

        if (lastProcessed == -1) {
            prefs.edit()
                .putInt(KEY_LAST_PROCESSED_SEASON, currentSeason)
                .apply()
            return false
        }

        if (currentSeason > lastProcessed) {
            val seasonsElapsed = (currentSeason - lastProcessed).coerceAtLeast(1)
            val currentHalvings = prefs.getInt(KEY_HALVING_COUNT, 0)
            val newHalvings = (currentHalvings + seasonsElapsed).coerceAtMost(20)

            prefs.edit()
                .putInt(KEY_LAST_PROCESSED_SEASON, currentSeason)
                .putInt(KEY_HALVING_COUNT, newHalvings)
                .apply()
            return true
        }
        return false
    }

    /**
     * Applies the 50% Gamerscore halving for each 14-day season reset.
     */
    fun applySeasonHalvingToScore(rawScore: Long, halvingCount: Int): Long {
        if (rawScore <= 0L) return 0L
        if (halvingCount <= 0) return rawScore
        var result = rawScore
        repeat(halvingCount.coerceAtMost(16)) {
            result /= 2L
        }
        return result.coerceAtLeast(if (rawScore > 0L) 1L else 0L)
    }

    fun getHalvingCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_HALVING_COUNT, 0)
    }

    fun evaluateSeasonState(
        context: Context,
        currentRank: Int = 0,
        nowMillis: Long = System.currentTimeMillis()
    ): SeasonLeaderboardState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val (seasonNumber, seasonStart, seasonEnd) = getSeasonTiming(context, nowMillis)
        val halvingCount = prefs.getInt(KEY_HALVING_COUNT, 0)

        val remainingMs = (seasonEnd - nowMillis).coerceAtLeast(0L)
        val daysLeft = (remainingMs / DAY_MS).toInt()
        val hoursLeft = ((remainingMs % DAY_MS) / HOUR_MS).toInt()
        val minutesLeft = ((remainingMs % HOUR_MS) / MINUTE_MS).toInt()

        val formattedCountdown = when {
            daysLeft > 0 -> "${daysLeft}d ${hoursLeft}h"
            hoursLeft > 0 -> "${hoursLeft}h ${minutesLeft}m"
            else -> "${minutesLeft.coerceAtLeast(1)}m"
        }

        val elapsedMs = (nowMillis - seasonStart).coerceIn(0L, SEASON_DURATION_MS)
        val progress = (elapsedMs.toFloat() / SEASON_DURATION_MS.toFloat()).coerceIn(0f, 1f)

        return SeasonLeaderboardState(
            seasonNumber = seasonNumber,
            seasonStartMillis = seasonStart,
            seasonEndMillis = seasonEnd,
            daysRemaining = daysLeft,
            hoursRemaining = hoursLeft,
            minutesRemaining = minutesLeft,
            formattedCountdown = formattedCountdown,
            seasonProgressFraction = progress,
            appliedHalvingCount = halvingCount,
            activeTopFiveBadge = null,
            isInTopTen = false,
            currentRank = currentRank
        )
    }

    /**
     * Converts a searched `UsernameClaim` from `/usernames/{usernameLower}` into an
     * `ExternalPlayerProfileData` object for `ExternalUserProfileScreen`.
     */
    fun fromUsernameClaim(
        claim: UsernameClaim,
        isCurrentUser: Boolean = false
    ): ExternalPlayerProfileData {
        val cleanUsername = claim.username.ifBlank { "player" }
        val resolvedName = claim.displayName.ifBlank {
            cleanUsername.replace("_", " ").ifBlank { "G-Max Player" }
        }
        val countryCode = claim.countryCode.ifBlank { "US" }
        val countryName = claim.countryName.ifBlank {
            CountryCatalog.ALL_COUNTRIES.firstOrNull {
                it.code.equals(countryCode, ignoreCase = true)
            }?.name ?: "United States"
        }
        val scoreInfo = GamerscoreEngine.calculateGamerscoreFromVerifiedScore(claim.gamerscore.coerceAtLeast(0L))
        val resolvedBio = claim.bio.ifBlank {
            "Verified G-Max player (@$cleanUsername) from $countryName."
        }
        return ExternalPlayerProfileData(
            userId = claim.userId,
            displayName = resolvedName,
            username = cleanUsername,
            authProvider = "Google",
            photoUrl = claim.photoUrl,
            countryCode = countryCode,
            countryName = countryName,
            flagEmoji = CountryCatalog.codeToFlagEmoji(countryCode),
            bio = resolvedBio,
            gamerscore = claim.gamerscore.coerceAtLeast(0L),
            rankTitle = scoreInfo.rankTitle,
            rankTier = scoreInfo.rankTier,
            currentLeaderboardPosition = 0,
            topFiveBadge = null,
            isVerifiedDeveloper = claim.isVerifiedDeveloper,
            isCurrentUser = isCurrentUser
        )
    }
}
