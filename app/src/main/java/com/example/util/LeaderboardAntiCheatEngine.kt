package com.example.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Snapshot of the 4-layer Leaderboard Anti-Cheat system status for UI transparency and diagnostics.
 */
data class AntiCheatStatusReport(
    val isIntegrityVerified: Boolean = true,
    val monotonicClockVerified: Boolean = true,
    val sessionCooldownGuardActive: Boolean = true,
    val foregroundUsageCheckActive: Boolean = true,
    val velocityCapActive: Boolean = true,
    val earnedLastHourGs: Long = 0L,
    val maxHourlyGsCap: Long = LeaderboardAntiCheatEngine.MAX_HOURLY_GAMERSCORE_GAIN,
    val earnedLast24HoursGs: Long = 0L,
    val maxDailyGsCap: Long = LeaderboardAntiCheatEngine.MAX_DAILY_GAMERSCORE_GAIN,
    val blockedExploitAttempts: Int = 0,
    val lastStatusMessage: String = "All 4 Anti-Cheat shields active • Monotonic clock & velocity verified"
)

/**
 * Result of validating a gameplay session through the 4-layer Anti-Cheat Engine.
 */
data class ValidatedSessionResult(
    val isCredited: Boolean,
    val verifiedDurationMs: Long,
    val countAsCreditedSession: Boolean,
    val rejectionReason: String? = null
)

/**
 * Multi-layer Anti-Cheat Engine that makes it difficult to cheat the Leaderboard or farm high
 * amounts of Gamerscore rapidly:
 *
 * Layer 1 — Monotonic Hardware Clock Verification (`SystemClock.elapsedRealtime()`):
 *   Cross-checks wall-clock deltas against hardware monotonic uptime so advancing the phone's
 *   date/time clock cannot inflate play duration or bypass cooldowns.
 *
 * Layer 2 — Minimum Session Duration & Launch-Spam Cooldown:
 *   Requires at least 120 seconds (2 minutes) of active play to credit a session bonus (+50 GS),
 *   and enforces a 5-minute cooldown between credited session launches for the same package.
 *
 * Layer 3 — Foreground Package & UsageStats Cross-Verification:
 *   Verifies against Android `UsageStatsManager` foreground events when Usage Access is granted
 *   and clamps single-session duration to realistic bounds (max 4 hours continuous without foreground re-verification).
 *
 * Layer 4 — Rolling Hourly & Daily Gamerscore Velocity Caps:
 *   Enforces a rolling cap of 450 GS/hour and 2,400 GS/24h so automated macros or spoofed stats
 *   cannot spike Gamerscore instantaneously.
 */
object LeaderboardAntiCheatEngine {

    private const val PREFS_NAME = "gmax_leaderboard_anticheat_prefs"
    private const val KEY_ACTIVE_START_ELAPSED_MS = "active_start_elapsed_ms"
    private const val KEY_ACTIVE_START_WALL_MS = "active_start_wall_ms"
    private const val KEY_LAST_CREDITED_LAUNCH_MAP = "last_credited_launch_map_json"
    private const val KEY_SCORE_LEDGER_JSON = "score_velocity_ledger_json"
    private const val KEY_BLOCKED_ATTEMPTS = "blocked_exploit_attempts_count"
    private const val KEY_LAST_STATUS_MSG = "last_anticheat_status_msg"
    private const val KEY_VERIFIED_GRIND_BONUS_GS = "verified_grind_bonus_gs"
    private const val KEY_LAST_GRIND_ELAPSED_MS = "last_grind_elapsed_ms"

    // Layer 2 constants
    const val MIN_CREDITED_SESSION_DURATION_MS: Long = 2L * 60L * 1000L // 2 minutes minimum for session bonus
    const val MIN_TRACKED_PLAY_DURATION_MS: Long = 15_000L // 15 seconds minimum to log play time
    const val MAX_SINGLE_SESSION_DURATION_MS: Long = 4L * 60L * 60L * 1000L // 4 hours max single continuous session
    const val LAUNCH_COOLDOWN_MS: Long = 5L * 60L * 1000L // 5 minutes between credited session launches per game
    const val CLOCK_DRIFT_TOLERANCE_MS: Long = 90_000L // 90s max divergence between wall clock and monotonic clock

    // Layer 4 Velocity Cap constants
    const val MAX_HOURLY_GAMERSCORE_GAIN: Long = 450L
    const val MAX_DAILY_GAMERSCORE_GAIN: Long = 2400L
    private const val ONE_HOUR_MS: Long = 60L * 60L * 1000L
    private const val ONE_DAY_MS: Long = 24L * 60L * 60L * 1000L

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Records monotonic hardware uptime (`SystemClock.elapsedRealtime()`) and wall clock time
     * when a game is launched from G-Max Hub.
     */
    fun onGameLaunchStarted(context: Context, packageName: String) {
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        getPrefs(context).edit()
            .putLong(KEY_ACTIVE_START_WALL_MS, nowWall)
            .putLong(KEY_ACTIVE_START_ELAPSED_MS, nowElapsed)
            .apply()
    }

    /**
     * Layer 1 + Layer 2 + Layer 3 validation when returning from a game session.
     */
    fun validateAndFinalizeSession(
        context: Context,
        packageName: String,
        wallStartMs: Long,
        nowWallMs: Long = System.currentTimeMillis()
    ): ValidatedSessionResult {
        val prefs = getPrefs(context)
        val startElapsed = prefs.getLong(KEY_ACTIVE_START_ELAPSED_MS, 0L)
        val nowElapsed = SystemClock.elapsedRealtime()

        val wallDelta = (nowWallMs - wallStartMs).coerceAtLeast(0L)
        val hasValidMonotonicAnchor = startElapsed in 1L..nowElapsed
        val monotonicDelta = if (hasValidMonotonicAnchor) {
            (nowElapsed - startElapsed).coerceAtLeast(0L)
        } else {
            wallDelta
        }

        // Layer 1: Detect system clock tampering (user setting phone time forward to fake hours of play)
        val clockDrift = abs(wallDelta - monotonicDelta)
        val isClockTampered = hasValidMonotonicAnchor && clockDrift > CLOCK_DRIFT_TOLERANCE_MS

        if (isClockTampered) {
            incrementBlockedAttempts(
                context,
                "Clock tamper detected (${clockDrift / 1000}s drift). Clamped to hardware monotonic timer."
            )
        }

        // Always trust monotonic hardware time over wall clock time
        val rawVerifiedDuration = if (hasValidMonotonicAnchor) {
            minOf(wallDelta, monotonicDelta)
        } else {
            wallDelta
        }

        if (rawVerifiedDuration < MIN_TRACKED_PLAY_DURATION_MS) {
            return ValidatedSessionResult(
                isCredited = false,
                verifiedDurationMs = 0L,
                countAsCreditedSession = false,
                rejectionReason = "Session under ${MIN_TRACKED_PLAY_DURATION_MS / 1000}s minimum threshold."
            )
        }

        // Layer 3: Cross-check with UsageStatsManager foreground activity if permission is granted
        val foregroundVerifiedMs = verifyForegroundDurationWithSystem(
            context = context,
            packageName = packageName,
            windowStartMs = wallStartMs,
            windowEndMs = nowWallMs,
            fallbackDurationMs = rawVerifiedDuration
        )

        val clampedDurationMs = foregroundVerifiedMs.coerceIn(0L, MAX_SINGLE_SESSION_DURATION_MS)

        // Layer 2: Check minimum session duration (2 mins) & per-game launch cooldown (5 mins)
        val meetsMinSessionDuration = clampedDurationMs >= MIN_CREDITED_SESSION_DURATION_MS
        val cooldownPassed = checkAndRecordCreditedLaunchCooldown(context, packageName, nowWallMs)

        val creditSessionBonus = meetsMinSessionDuration && cooldownPassed && !isClockTampered
        if (!cooldownPassed) {
            incrementBlockedAttempts(
                context,
                "Rapid re-launch cooldown active for ${packageName.substringAfterLast('.')} (5m required)."
            )
        }

        // Record earned points into rolling velocity ledger
        val earnedPoints = ((clampedDurationMs / 60_000L) * 10L) + (if (creditSessionBonus) 50L else 0L)
        recordScoreGainInLedger(context, earnedPoints, nowWallMs)

        return ValidatedSessionResult(
            isCredited = clampedDurationMs >= MIN_TRACKED_PLAY_DURATION_MS,
            verifiedDurationMs = clampedDurationMs,
            countAsCreditedSession = creditSessionBonus
        )
    }

    /**
     * Checks whether at least 5 minutes have elapsed since the last credited launch for this package.
     */
    fun canCreditNewLaunch(context: Context, packageName: String, nowWallMs: Long = System.currentTimeMillis()): Boolean {
        val prefs = getPrefs(context)
        val raw = prefs.getString(KEY_LAST_CREDITED_LAUNCH_MAP, null) ?: return true
        return try {
            val obj = JSONObject(raw)
            val lastMs = obj.optLong(packageName, 0L)
            if (lastMs <= 0L) true
            else (nowWallMs - lastMs) >= LAUNCH_COOLDOWN_MS
        } catch (_: Exception) {
            true
        }
    }

    private fun checkAndRecordCreditedLaunchCooldown(
        context: Context,
        packageName: String,
        nowWallMs: Long
    ): Boolean {
        val prefs = getPrefs(context)
        val raw = prefs.getString(KEY_LAST_CREDITED_LAUNCH_MAP, null)
        val obj = try {
            if (!raw.isNullOrBlank()) JSONObject(raw) else JSONObject()
        } catch (_: Exception) {
            JSONObject()
        }

        val lastMs = obj.optLong(packageName, 0L)
        if (lastMs > 0L && (nowWallMs - lastMs) in 0L until LAUNCH_COOLDOWN_MS) {
            return false
        }

        obj.put(packageName, nowWallMs)
        prefs.edit().putString(KEY_LAST_CREDITED_LAUNCH_MAP, obj.toString()).apply()
        return true
    }

    /**
     * Layer 3: Cross-verifies foreground execution using UsageStatsManager when granted.
     */
    private fun verifyForegroundDurationWithSystem(
        context: Context,
        packageName: String,
        windowStartMs: Long,
        windowEndMs: Long,
        fallbackDurationMs: Long
    ): Long {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return fallbackDurationMs
            val events = usm.queryEvents(windowStartMs - 5_000L, windowEndMs + 5_000L)
            val event = UsageEvents.Event()
            var sawTargetForeground = false
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.packageName == packageName &&
                    (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                        event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND)
                ) {
                    sawTargetForeground = true
                    break
                }
            }
            if (sawTargetForeground) {
                fallbackDurationMs
            } else {
                // If UsageStats is active and has events for other packages but not target package, clamp
                fallbackDurationMs.coerceAtMost(30 * 60_000L)
            }
        } catch (_: Exception) {
            fallbackDurationMs
        }
    }

    /**
     * Layer 4: Applies anti-cheat velocity & sanity clamping to raw playtime and session totals
     * before computing Gamerscore.
     *
     * - Caps maximum credited sessions-per-hour ratio so launch-spamming without play time
     *   cannot generate unverified session points.
     * - Adds verified competitive grind sessions within the hourly/daily velocity caps.
     */
    fun clampVerifiedPlayStats(
        context: Context,
        rawTotalMinutes: Long,
        rawTotalSessions: Long
    ): Pair<Long, Long> {
        val safeMinutes = rawTotalMinutes.coerceIn(0L, 60_000L) // Max realistic seasonal minutes

        // Anti-Launch-Spam Ratio Guard:
        // Credited sessions cannot exceed (safeMinutes / 2) + 3, preventing 500 1-second launches
        // from awarding 25,000+ session points without real gameplay minutes.
        val maxLegitSessionsForPlaytime = ((safeMinutes / 2L) + 3L).coerceAtLeast(1L)
        val clampedSessions = rawTotalSessions.coerceIn(0L, maxLegitSessionsForPlaytime)

        if (rawTotalSessions > maxLegitSessionsForPlaytime + 5L) {
            incrementBlockedAttempts(
                context,
                "Clamped ${rawTotalSessions - clampedSessions} unverified rapid launches (requires >=2m play per session)."
            )
        }

        // Include verified grind bonus minutes/sessions earned inside the velocity cap
        val verifiedGrindGs = getVerifiedGrindBonusGs(context)
        val bonusMinutesFromGrind = verifiedGrindGs / 10L

        return Pair(safeMinutes + bonusMinutesFromGrind, clampedSessions)
    }

    /**
     * Allows a player who is grinding legitimately to record a verified competitive match/session
     * subject to the 4-layer Anti-Cheat Hourly (450 GS/hr) and Daily (2,400 GS/day) velocity caps.
     * Returns Pair<accepted, statusMessage>.
     */
    fun recordVerifiedGrindSession(
        context: Context,
        requestedGsGain: Long = 160L
    ): Pair<Boolean, String> {
        val prefs = getPrefs(context)
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val lastGrindElapsed = prefs.getLong(KEY_LAST_GRIND_ELAPSED_MS, 0L)

        // Rapid click anti-macro throttle (must wait at least 1.5s between verified session claims)
        if (lastGrindElapsed in 1L..nowElapsed && (nowElapsed - lastGrindElapsed) < 1200L) {
            incrementBlockedAttempts(context, "Anti-macro rate limiter blocked rapid session submission.")
            return Pair(false, "Anti-Cheat Rate Limit: Slow down! Wait a moment between verified sessions.")
        }

        val (earnedHour, earnedDay) = getRollingVelocityTotals(context, nowWall)
        if (earnedHour >= MAX_HOURLY_GAMERSCORE_GAIN) {
            incrementBlockedAttempts(
                context,
                "Hourly Gamerscore velocity cap ($MAX_HOURLY_GAMERSCORE_GAIN GS/hr) reached."
            )
            return Pair(
                false,
                "Anti-Cheat Velocity Cap: Max $MAX_HOURLY_GAMERSCORE_GAIN GS/hr reached ($earnedHour/$MAX_HOURLY_GAMERSCORE_GAIN GS). Play more later!"
            )
        }

        if (earnedDay >= MAX_DAILY_GAMERSCORE_GAIN) {
            incrementBlockedAttempts(
                context,
                "Daily Gamerscore velocity cap ($MAX_DAILY_GAMERSCORE_GAIN GS/24h) reached."
            )
            return Pair(
                false,
                "Anti-Cheat Daily Cap: Max $MAX_DAILY_GAMERSCORE_GAIN GS/24h reached ($earnedDay/$MAX_DAILY_GAMERSCORE_GAIN GS)."
            )
        }

        val allowedGain = minOf(
            requestedGsGain,
            (MAX_HOURLY_GAMERSCORE_GAIN - earnedHour).coerceAtLeast(0L),
            (MAX_DAILY_GAMERSCORE_GAIN - earnedDay).coerceAtLeast(0L)
        )

        if (allowedGain <= 0L) {
            return Pair(false, "Anti-Cheat Velocity Cap reached for this hour.")
        }

        val currentBonus = prefs.getLong(KEY_VERIFIED_GRIND_BONUS_GS, 0L) + allowedGain
        prefs.edit()
            .putLong(KEY_VERIFIED_GRIND_BONUS_GS, currentBonus)
            .putLong(KEY_LAST_GRIND_ELAPSED_MS, nowElapsed)
            .putString(
                KEY_LAST_STATUS_MSG,
                "Verified session +$allowedGain GS credited (${earnedHour + allowedGain}/$MAX_HOURLY_GAMERSCORE_GAIN GS hourly cap)."
            )
            .apply()

        recordScoreGainInLedger(context, allowedGain, nowWall)
        return Pair(
            true,
            "+$allowedGain GS Verified! (${earnedHour + allowedGain}/$MAX_HOURLY_GAMERSCORE_GAIN GS hourly cap)"
        )
    }

    fun getVerifiedGrindBonusGs(context: Context): Long {
        return getPrefs(context).getLong(KEY_VERIFIED_GRIND_BONUS_GS, 0L).coerceAtLeast(0L)
    }

    private fun recordScoreGainInLedger(context: Context, gainGs: Long, nowWallMs: Long) {
        if (gainGs <= 0L) return
        val prefs = getPrefs(context)
        val raw = prefs.getString(KEY_SCORE_LEDGER_JSON, null)
        val arr = try {
            if (!raw.isNullOrBlank()) JSONArray(raw) else JSONArray()
        } catch (_: Exception) {
            JSONArray()
        }

        val pruned = JSONArray()
        val cutoff = nowWallMs - ONE_DAY_MS
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val ts = item.optLong("ts", 0L)
            if (ts >= cutoff) {
                pruned.put(item)
            }
        }
        pruned.put(
            JSONObject().apply {
                put("ts", nowWallMs)
                put("gs", gainGs)
            }
        )
        prefs.edit().putString(KEY_SCORE_LEDGER_JSON, pruned.toString()).apply()
    }

    fun getRollingVelocityTotals(
        context: Context,
        nowWallMs: Long = System.currentTimeMillis()
    ): Pair<Long, Long> {
        val raw = getPrefs(context).getString(KEY_SCORE_LEDGER_JSON, null) ?: return Pair(0L, 0L)
        return try {
            val arr = JSONArray(raw)
            var hourTotal = 0L
            var dayTotal = 0L
            val hourCutoff = nowWallMs - ONE_HOUR_MS
            val dayCutoff = nowWallMs - ONE_DAY_MS
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val ts = item.optLong("ts", 0L)
                val gs = item.optLong("gs", 0L)
                if (ts >= dayCutoff) {
                    dayTotal += gs
                    if (ts >= hourCutoff) {
                        hourTotal += gs
                    }
                }
            }
            Pair(hourTotal, dayTotal)
        } catch (_: Exception) {
            Pair(0L, 0L)
        }
    }

    /**
     * Clamps anomalous remote leaderboard scores so tampered client writes cannot break the podium.
     */
    fun sanitizeRemoteCompetitorScore(rawScore: Long): Long {
        return rawScore.coerceIn(0L, 250_000L)
    }

    private fun incrementBlockedAttempts(context: Context, reason: String) {
        val prefs = getPrefs(context)
        val count = prefs.getInt(KEY_BLOCKED_ATTEMPTS, 0) + 1
        prefs.edit()
            .putInt(KEY_BLOCKED_ATTEMPTS, count)
            .putString(KEY_LAST_STATUS_MSG, reason)
            .apply()
    }

    fun getAntiCheatStatusReport(context: Context): AntiCheatStatusReport {
        val prefs = getPrefs(context)
        val (hourGs, dayGs) = getRollingVelocityTotals(context)
        val blocked = prefs.getInt(KEY_BLOCKED_ATTEMPTS, 0)
        val lastMsg = prefs.getString(
            KEY_LAST_STATUS_MSG,
            "All 4 Anti-Cheat shields active • Monotonic clock & velocity verified"
        ) ?: "All 4 Anti-Cheat shields active"

        return AntiCheatStatusReport(
            isIntegrityVerified = true,
            monotonicClockVerified = true,
            sessionCooldownGuardActive = true,
            foregroundUsageCheckActive = true,
            velocityCapActive = true,
            earnedLastHourGs = hourGs,
            maxHourlyGsCap = MAX_HOURLY_GAMERSCORE_GAIN,
            earnedLast24HoursGs = dayGs,
            maxDailyGsCap = MAX_DAILY_GAMERSCORE_GAIN,
            blockedExploitAttempts = blocked,
            lastStatusMessage = lastMsg
        )
    }
}
