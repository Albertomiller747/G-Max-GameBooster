package com.example.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class HudNeonPreset(
    val id: String,
    val labelEn: String,
    val labelEs: String,
    val hexColor: String,
    val argb: Long
)

enum class StreakDayStatus {
    COMPLETED,
    FROZEN,
    TODAY_PENDING,
    MISSED,
    UPCOMING
}

data class StreakDayEntry(
    val dateKey: String,
    val dayShortLabel: String,
    val status: StreakDayStatus
)

data class DailyStreakState(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastCompletedDate: String = "",
    val completedToday: Boolean = false,
    val gTokensBalance: Int = 0,
    val unclaimedHudSeconds: Int = 0,
    val streakFreezesOwned: Int = 0,
    val lastFreezeConsumedDate: String = "",
    val isStreakBrokenRepairable: Boolean = false,
    val lostStreakCount: Int = 0,
    val brokenAtEpochMs: Long = 0L,
    val repairDeadlineEpochMs: Long = 0L,
    val selectedHudNeonColorHex: String = "#00E5FF",
    val isHudNeonUnlocked: Boolean = false,
    val isVipBadgeUnlocked: Boolean = false,
    val recentDays: List<StreakDayEntry> = emptyList(),
    val statusBannerMessage: String? = null
) {
    val nextTokenProgressFraction: Float
        get() = (unclaimedHudSeconds.coerceIn(0, 300) / 300f).coerceIn(0f, 1f)

    val secondsUntilNextTokenBatch: Int
        get() = (300 - (unclaimedHudSeconds % 300)).coerceIn(1, 300)

    fun remainingRepairMillis(nowMs: Long = System.currentTimeMillis()): Long {
        if (!isStreakBrokenRepairable || repairDeadlineEpochMs <= 0L) return 0L
        return (repairDeadlineEpochMs - nowMs).coerceAtLeast(0L)
    }

    fun formatRepairCountdown(nowMs: Long = System.currentTimeMillis()): String {
        val rem = remainingRepairMillis(nowMs)
        if (rem <= 0L) return "00h 00m 00s"
        val hours = rem / 3_600_000L
        val mins = (rem % 3_600_000L) / 60_000L
        val secs = (rem % 60_000L) / 1_000L
        return String.format(Locale.US, "%02dh %02dm %02ds", hours, mins, secs)
    }
}

/**
 * Duolingo-Inspired Daily Engagement Streak & G-Tokens Economy Manager.
 *
 * Rules enforced:
 * 1. Daily Requirement: Launch a game with a GMAX booster preset (Normal/Beast) OR play with the live
 *    performance HUD active before midnight to increment the streak by +1 for that calendar day.
 * 2. Economy: Earn 10 G-Tokens per 5 minutes (300 seconds) of active HUD gameplay.
 *    Spend 100 G-Tokens to buy a Streak Freeze (maximum 2 Streak Freezes held in inventory).
 * 3. Safety Net: If a day is missed at midnight and the user owns a Streak Freeze, automatically
 *    consume 1 freeze per missed day so the streak does not reset to zero.
 * 4. Break & Repair: If a day is missed with 0 freezes left, the streak breaks and triggers a
 *    "Streak Lost!" screen with a 12-hour grace period to repair it via a Google AdMob Rewarded Video Ad.
 * 5. Milestones:
 *    - 7-Day Streak: Unlocks Custom Neon HUD Colors for the live performance HUD.
 *    - 30-Day Streak: Unlocks the exclusive VIP Badge next to the player's username.
 *    - If the streak breaks completely and is not repaired, perks are immediately revoked until rebuilt.
 * 6. Multi-Device Cloud Account Registry: Syncs streak state per account across devices and removes
 *    accounts from active streak tracking when their streak breaks and resets to 0.
 */
object DailyStreakManager {

    private const val PREFS_NAME = "gmax_daily_streak_prefs"

    const val TOKENS_PER_5_MINS = 10
    const val HUD_SECONDS_PER_REWARD = 300 // 5 minutes
    const val STREAK_FREEZE_COST_TOKENS = 100
    const val MAX_STREAK_FREEZES = 2
    const val REPAIR_GRACE_WINDOW_MS = 12L * 60L * 60L * 1000L // 12 hours
    const val MILESTONE_NEON_HUD_DAYS = 7
    const val MILESTONE_VIP_BADGE_DAYS = 30

    const val CLOUD_ACTIVE_PREFIX = "streak_active:"
    const val CLOUD_NONE_PREFIX = "streak_none:"
    const val VIP_STREAK_TAG_ID = "vip_30d_streak"

    val NEON_HUD_PRESETS: List<HudNeonPreset> = listOf(
        HudNeonPreset("neon_cyan", "Ice Cyan", "Cian Hielo", "#00E5FF", 0xFF00E5FFL),
        HudNeonPreset("neon_magenta", "Cyber Magenta", "Magenta Cyber", "#FF007F", 0xFFFF007FL),
        HudNeonPreset("neon_violet", "Plasma Violet", "Violeta Plasma", "#D500F9", 0xFFD500F9L),
        HudNeonPreset("neon_gold", "Electric Gold", "Oro Eléctrico", "#FFD600", 0xFFFFD600L),
        HudNeonPreset("neon_lime", "Acid Lime", "Lima Ácida", "#76FF03", 0xFF76FF03L),
        HudNeonPreset("neon_crimson", "Crimson Laser", "Láser Carmesí", "#FF1744", 0xFFFF1744L)
    )

    private val _streakState = MutableStateFlow(DailyStreakState())
    val streakState: StateFlow<DailyStreakState> = _streakState.asStateFlow()

    @Volatile
    private var activeAccountKey: String = "default_account"

    private fun getPrefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun scopedKey(base: String, accountKey: String = activeAccountKey): String =
        "${base}_${accountKey.ifBlank { "default_account" }}"

    fun formatDateKey(epochMs: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date(epochMs))
    }

    private fun parseDateKeyToMidnightCalendar(dateKey: String): Calendar? {
        if (dateKey.isBlank()) return null
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val parsed = sdf.parse(dateKey) ?: return null
            Calendar.getInstance().apply {
                time = parsed
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun daysBetweenDateKeys(startDateKey: String, endDateKey: String): Int {
        val startCal = parseDateKeyToMidnightCalendar(startDateKey) ?: return -1
        val endCal = parseDateKeyToMidnightCalendar(endDateKey) ?: return -1
        val diffMs = endCal.timeInMillis - startCal.timeInMillis
        return TimeUnit.MILLISECONDS.toDays(diffMs).toInt()
    }

    fun switchAccount(context: Context, accountKey: String, nowMs: Long = System.currentTimeMillis()): DailyStreakState {
        activeAccountKey = accountKey.ifBlank { "default_account" }
        return evaluateMidnightRollover(context, nowMs)
    }

    /**
     * Evaluates the user's streak against the current calendar day (midnight boundary),
     * automatically consuming Streak Freezes if a day was missed or transitioning into the
     * 12-hour "Streak Lost!" repair window if 0 freezes remain.
     */
    @Synchronized
    fun evaluateMidnightRollover(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ): DailyStreakState {
        val prefs = getPrefs(context)
        val acct = activeAccountKey
        val todayKey = formatDateKey(nowMs)
        val yesterdayCal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val yesterdayKey = formatDateKey(yesterdayCal.timeInMillis)

        var currentStreak = prefs.getInt(scopedKey("current_streak", acct), 0).coerceAtLeast(0)
        var longestStreak = prefs.getInt(scopedKey("longest_streak", acct), 0).coerceAtLeast(currentStreak)
        var lastDate = prefs.getString(scopedKey("last_completed_date", acct), "").orEmpty()
        var gTokens = prefs.getInt(scopedKey("g_tokens_balance", acct), 0).coerceAtLeast(0)
        val unclaimedSec = prefs.getInt(scopedKey("unclaimed_hud_sec", acct), 0).coerceIn(0, 299)
        var freezesOwned = prefs.getInt(scopedKey("freezes_owned", acct), 0).coerceIn(0, MAX_STREAK_FREEZES)
        var lastFreezeDate = prefs.getString(scopedKey("last_freeze_date", acct), "").orEmpty()
        var isBrokenRepairable = prefs.getBoolean(scopedKey("is_broken_repairable", acct), false)
        var lostStreakCount = prefs.getInt(scopedKey("lost_streak_count", acct), 0).coerceAtLeast(0)
        var brokenAtMs = prefs.getLong(scopedKey("broken_at_ms", acct), 0L)
        var repairDeadlineMs = prefs.getLong(scopedKey("repair_deadline_ms", acct), 0L)
        val neonHex = prefs.getString(scopedKey("hud_neon_hex", acct), "#00E5FF").orEmpty().ifBlank { "#00E5FF" }
        var statusBanner: String? = prefs.getString(scopedKey("status_banner", acct), null)

        // 1. Check if an active 12-hour repair grace window has expired
        if (isBrokenRepairable) {
            if (repairDeadlineMs <= 0L || nowMs >= repairDeadlineMs) {
                isBrokenRepairable = false
                lostStreakCount = 0
                brokenAtMs = 0L
                repairDeadlineMs = 0L
                currentStreak = 0
                lastDate = ""
                statusBanner = AppStrings.trText(
                    "Streak repair window expired. Launch a game with Boost to start a new streak!",
                    "El tiempo para reparar tu racha expiró. ¡Inicia un juego con Boost para empezar una nueva racha!"
                )
            }
        }

        // 2. Check midnight rollover if user had an active streak
        if (!isBrokenRepairable && currentStreak > 0 && lastDate.isNotBlank()) {
            val daysDiff = daysBetweenDateKeys(lastDate, todayKey)
            if (daysDiff > 1) {
                val missedDays = daysDiff - 1
                if (freezesOwned >= missedDays) {
                    // Safety Net: Automatically consume Streak Freeze(s) to protect the streak!
                    freezesOwned = (freezesOwned - missedDays).coerceAtLeast(0)
                    lastDate = yesterdayKey
                    lastFreezeDate = yesterdayKey
                    statusBanner = AppStrings.trText(
                        "❄️ Streak Freeze used! Your $currentStreak-day streak was saved overnight.",
                        "❄️ ¡Congelador de racha usado! Tu racha de $currentStreak días fue protegida."
                    )
                } else {
                    // Consume any partial freezes, then break the streak and start 12-hour repair window
                    freezesOwned = 0
                    lostStreakCount = currentStreak
                    currentStreak = 0
                    lastDate = ""
                    isBrokenRepairable = true
                    brokenAtMs = nowMs
                    repairDeadlineMs = nowMs + REPAIR_GRACE_WINDOW_MS
                    statusBanner = AppStrings.trText(
                        "⚠️ $lostStreakCount-Day Streak Lost! Repair it within 12 hours by watching a Rewarded Video Ad.",
                        "⚠️ ¡Perdiste tu racha de $lostStreakCount días! Repárala en 12 horas viendo un anuncio de video."
                    )
                }
            }
        }

        val completedToday = (lastDate == todayKey && currentStreak > 0 && !isBrokenRepairable)
        val isHudNeonUnlocked = currentStreak >= MILESTONE_NEON_HUD_DAYS && !isBrokenRepairable
        val isVipBadgeUnlocked = currentStreak >= MILESTONE_VIP_BADGE_DAYS && !isBrokenRepairable

        // Persist updated state
        prefs.edit()
            .putInt(scopedKey("current_streak", acct), currentStreak)
            .putInt(scopedKey("longest_streak", acct), longestStreak)
            .putString(scopedKey("last_completed_date", acct), lastDate)
            .putInt(scopedKey("g_tokens_balance", acct), gTokens)
            .putInt(scopedKey("unclaimed_hud_sec", acct), unclaimedSec)
            .putInt(scopedKey("freezes_owned", acct), freezesOwned)
            .putString(scopedKey("last_freeze_date", acct), lastFreezeDate)
            .putBoolean(scopedKey("is_broken_repairable", acct), isBrokenRepairable)
            .putInt(scopedKey("lost_streak_count", acct), lostStreakCount)
            .putLong(scopedKey("broken_at_ms", acct), brokenAtMs)
            .putLong(scopedKey("repair_deadline_ms", acct), repairDeadlineMs)
            .putString(scopedKey("hud_neon_hex", acct), neonHex)
            .putString(scopedKey("status_banner", acct), statusBanner)
            .apply()

        syncActiveAccountsRegistry(context, acct, currentStreak)
        syncHudCustomColorPrefs(context, isHudNeonUnlocked, neonHex)

        val recentDays = buildSevenDayStrip(
            nowMs = nowMs,
            currentStreak = currentStreak,
            completedToday = completedToday,
            lastFreezeConsumedDate = lastFreezeDate
        )

        val newState = DailyStreakState(
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            lastCompletedDate = lastDate,
            completedToday = completedToday,
            gTokensBalance = gTokens,
            unclaimedHudSeconds = unclaimedSec,
            streakFreezesOwned = freezesOwned,
            lastFreezeConsumedDate = lastFreezeDate,
            isStreakBrokenRepairable = isBrokenRepairable,
            lostStreakCount = lostStreakCount,
            brokenAtEpochMs = brokenAtMs,
            repairDeadlineEpochMs = repairDeadlineMs,
            selectedHudNeonColorHex = neonHex,
            isHudNeonUnlocked = isHudNeonUnlocked,
            isVipBadgeUnlocked = isVipBadgeUnlocked,
            recentDays = recentDays,
            statusBannerMessage = statusBanner
        )
        _streakState.value = newState
        return newState
    }

    /**
     * Completes today's daily requirement when the user launches a game with a GMAX booster preset
     * or plays a match with the live performance HUD active.
     *
     * @return Pair(updatedState, didIncrementToday)
     */
    @Synchronized
    fun recordDailyRequirementCompleted(
        context: Context,
        sourceLabel: String = "Boost Launch",
        nowMs: Long = System.currentTimeMillis()
    ): Pair<DailyStreakState, Boolean> {
        val current = evaluateMidnightRollover(context, nowMs)
        val todayKey = formatDateKey(nowMs)
        if (current.completedToday && current.lastCompletedDate == todayKey && !current.isStreakBrokenRepairable) {
            return current to false
        }

        val prefs = getPrefs(context)
        val acct = activeAccountKey
        val nextStreak = if (current.isStreakBrokenRepairable) {
            // Starting fresh Day 1 if they launched without repairing
            1
        } else {
            current.currentStreak + 1
        }
        val nextLongest = maxOf(current.longestStreak, nextStreak)
        val banner = when (nextStreak) {
            MILESTONE_NEON_HUD_DAYS -> AppStrings.trText(
                "🔥 $nextStreak-Day Streak! Unlocked Custom Neon HUD Colors!",
                "🔥 ¡Racha de $nextStreak días! ¡Desbloqueaste Colores Neón para el HUD!"
            )
            MILESTONE_VIP_BADGE_DAYS -> AppStrings.trText(
                "⚡ $nextStreak-Day Streak! Unlocked Exclusive VIP Username Badge!",
                "⚡ ¡Racha de $nextStreak días! ¡Desbloqueaste la Insignia VIP junto a tu usuario!"
            )
            else -> AppStrings.trText(
                "🔥 Daily Streak Extended! Day $nextStreak ($sourceLabel)",
                "🔥 ¡Racha Diaria Extendida! Día $nextStreak ($sourceLabel)"
            )
        }

        prefs.edit()
            .putInt(scopedKey("current_streak", acct), nextStreak)
            .putInt(scopedKey("longest_streak", acct), nextLongest)
            .putString(scopedKey("last_completed_date", acct), todayKey)
            .putBoolean(scopedKey("is_broken_repairable", acct), false)
            .putInt(scopedKey("lost_streak_count", acct), 0)
            .putLong(scopedKey("broken_at_ms", acct), 0L)
            .putLong(scopedKey("repair_deadline_ms", acct), 0L)
            .putString(scopedKey("status_banner", acct), banner)
            .apply()

        val updated = evaluateMidnightRollover(context, nowMs)
        return updated to true
    }

    /**
     * Passively accrues active HUD gameplay time and awards 10 G-Tokens for every 5 minutes (300s).
     * Also marks the daily streak requirement complete when playing with the live HUD active.
     *
     * @return Pair(updatedState, tokensAwardedThisTick)
     */
    @Synchronized
    fun accrueHudActiveSeconds(
        context: Context,
        secondsElapsed: Int,
        nowMs: Long = System.currentTimeMillis()
    ): Pair<DailyStreakState, Int> {
        if (secondsElapsed <= 0) return evaluateMidnightRollover(context, nowMs) to 0

        // Playing with the live HUD active also satisfies the Daily Requirement
        recordDailyRequirementCompleted(context, "Live Performance HUD", nowMs)

        val prefs = getPrefs(context)
        val acct = activeAccountKey
        val prevSec = prefs.getInt(scopedKey("unclaimed_hud_sec", acct), 0).coerceAtLeast(0)
        val prevTokens = prefs.getInt(scopedKey("g_tokens_balance", acct), 0).coerceAtLeast(0)

        val totalSec = prevSec + secondsElapsed
        val batchesEarned = totalSec / HUD_SECONDS_PER_REWARD
        val remainderSec = totalSec % HUD_SECONDS_PER_REWARD
        val tokensEarned = batchesEarned * TOKENS_PER_5_MINS
        val newTokens = prevTokens + tokensEarned

        val editor = prefs.edit()
            .putInt(scopedKey("unclaimed_hud_sec", acct), remainderSec)
            .putInt(scopedKey("g_tokens_balance", acct), newTokens)

        if (tokensEarned > 0) {
            editor.putString(
                scopedKey("status_banner", acct),
                AppStrings.trText(
                    "🪙 +$tokensEarned G-Tokens earned from active HUD play! (Balance: $newTokens)",
                    "🪙 ¡+$tokensEarned G-Tokens ganados jugando con el HUD activo! (Saldo: $newTokens)"
                )
            )
        }
        editor.apply()

        return evaluateMidnightRollover(context, nowMs) to tokensEarned
    }

    /**
     * Spends 100 G-Tokens to buy 1 Streak Freeze (maximum 2 freezes held at any time).
     */
    @Synchronized
    fun purchaseStreakFreeze(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ): Result<DailyStreakState> {
        val current = evaluateMidnightRollover(context, nowMs)
        if (current.streakFreezesOwned >= MAX_STREAK_FREEZES) {
            return Result.failure(
                IllegalStateException(
                    AppStrings.trText(
                        "You already hold the maximum of $MAX_STREAK_FREEZES Streak Freezes.",
                        "Ya tienes el máximo de $MAX_STREAK_FREEZES Congeladores de Racha."
                    )
                )
            )
        }
        if (current.gTokensBalance < STREAK_FREEZE_COST_TOKENS) {
            val needed = STREAK_FREEZE_COST_TOKENS - current.gTokensBalance
            return Result.failure(
                IllegalStateException(
                    AppStrings.trText(
                        "Need $needed more G-Tokens (Play with the live HUD active to earn 10 G-Tokens every 5 mins).",
                        "Necesitas $needed G-Tokens más (Juega con el HUD activo para ganar 10 G-Tokens cada 5 min)."
                    )
                )
            )
        }

        val prefs = getPrefs(context)
        val acct = activeAccountKey
        val newTokens = (current.gTokensBalance - STREAK_FREEZE_COST_TOKENS).coerceAtLeast(0)
        val newFreezes = (current.streakFreezesOwned + 1).coerceIn(0, MAX_STREAK_FREEZES)

        prefs.edit()
            .putInt(scopedKey("g_tokens_balance", acct), newTokens)
            .putInt(scopedKey("freezes_owned", acct), newFreezes)
            .putString(
                scopedKey("status_banner", acct),
                AppStrings.trText(
                    "❄️ Streak Freeze purchased! Inventory: $newFreezes/$MAX_STREAK_FREEZES",
                    "❄️ ¡Congelador de Racha comprado! Inventario: $newFreezes/$MAX_STREAK_FREEZES"
                )
            )
            .apply()

        return Result.success(evaluateMidnightRollover(context, nowMs))
    }

    /**
     * Repairs a broken streak within the 12-hour grace period after the user finishes watching
     * a Google AdMob Rewarded Video Ad.
     */
    @Synchronized
    fun repairBrokenStreakViaRewardedAd(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ): DailyStreakState {
        val current = evaluateMidnightRollover(context, nowMs)
        val restoredCount = current.lostStreakCount.coerceAtLeast(current.currentStreak).coerceAtLeast(1)
        val todayKey = formatDateKey(nowMs)
        val prefs = getPrefs(context)
        val acct = activeAccountKey

        prefs.edit()
            .putInt(scopedKey("current_streak", acct), restoredCount)
            .putInt(scopedKey("longest_streak", acct), maxOf(current.longestStreak, restoredCount))
            .putString(scopedKey("last_completed_date", acct), todayKey)
            .putBoolean(scopedKey("is_broken_repairable", acct), false)
            .putInt(scopedKey("lost_streak_count", acct), 0)
            .putLong(scopedKey("broken_at_ms", acct), 0L)
            .putLong(scopedKey("repair_deadline_ms", acct), 0L)
            .putString(
                scopedKey("status_banner", acct),
                AppStrings.trText(
                    "🔥 $restoredCount-Day Streak Repaired! Your milestone perks are restored.",
                    "🔥 ¡Racha de $restoredCount días reparada! Tus recompensas han sido restauradas."
                )
            )
            .apply()

        return evaluateMidnightRollover(context, nowMs)
    }

    /**
     * Permanently clears a broken streak to 0 when the user declines repair or the 12h timer expires.
     * Removes the account from the active streak registry and revokes 7D/30D perks.
     */
    @Synchronized
    fun dismissAndResetBrokenStreak(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ): DailyStreakState {
        val prefs = getPrefs(context)
        val acct = activeAccountKey
        prefs.edit()
            .putInt(scopedKey("current_streak", acct), 0)
            .putString(scopedKey("last_completed_date", acct), "")
            .putBoolean(scopedKey("is_broken_repairable", acct), false)
            .putInt(scopedKey("lost_streak_count", acct), 0)
            .putLong(scopedKey("broken_at_ms", acct), 0L)
            .putLong(scopedKey("repair_deadline_ms", acct), 0L)
            .putString(
                scopedKey("status_banner", acct),
                AppStrings.trText(
                    "Streak reset to 0. Launch a game with Boost today to start Day 1!",
                    "Racha reiniciada a 0. ¡Inicia un juego con Boost hoy para empezar el Día 1!"
                )
            )
            .apply()
        return evaluateMidnightRollover(context, nowMs)
    }

    /**
     * Selects a custom neon HUD color (requires 7-Day Streak milestone).
     */
    @Synchronized
    fun selectHudNeonColor(
        context: Context,
        hexColor: String,
        nowMs: Long = System.currentTimeMillis()
    ): Result<DailyStreakState> {
        val current = evaluateMidnightRollover(context, nowMs)
        if (!current.isHudNeonUnlocked) {
            return Result.failure(
                IllegalStateException(
                    AppStrings.trText(
                        "Reach a 7-Day Streak to unlock Custom Neon HUD Colors!",
                        "¡Alcanza una racha de 7 días para desbloquear los Colores Neón del HUD!"
                    )
                )
            )
        }
        val cleanHex = if (hexColor.startsWith("#")) hexColor.uppercase(Locale.US) else "#${hexColor.uppercase(Locale.US)}"
        val prefs = getPrefs(context)
        prefs.edit()
            .putString(scopedKey("hud_neon_hex", activeAccountKey), cleanHex)
            .apply()
        return Result.success(evaluateMidnightRollover(context, nowMs))
    }

    private fun syncHudCustomColorPrefs(context: Context, isUnlocked: Boolean, neonHex: String) {
        try {
            val hudPrefs = context.applicationContext.getSharedPreferences("gmax_hud_prefs", Context.MODE_PRIVATE)
            hudPrefs.edit()
                .putBoolean("hud_streak_neon_unlocked", isUnlocked)
                .putString("hud_streak_neon_hex", if (isUnlocked) neonHex else "#00E5FF")
                .apply()
        } catch (_: Exception) {}
    }

    /**
     * Tracks which accounts currently hold an active streak (`>= 1`) and removes any account
     * whose streak breaks or resets to 0.
     */
    private fun syncActiveAccountsRegistry(context: Context, accountKey: String, streakCount: Int) {
        val prefs = getPrefs(context)
        val rawSet = prefs.getStringSet("active_streak_accounts_registry", emptySet()) ?: emptySet()
        val updated = rawSet.filterNot { it.startsWith("$accountKey:") }.toMutableSet()
        if (streakCount > 0) {
            updated.add("$accountKey:$streakCount")
        }
        prefs.edit().putStringSet("active_streak_accounts_registry", updated).apply()
    }

    fun getActiveStreakAccountsMap(context: Context): Map<String, Int> {
        val rawSet = getPrefs(context).getStringSet("active_streak_accounts_registry", emptySet()) ?: emptySet()
        val map = LinkedHashMap<String, Int>()
        for (entry in rawSet) {
            val idx = entry.lastIndexOf(':')
            if (idx > 0) {
                val acct = entry.substring(0, idx)
                val count = entry.substring(idx + 1).toIntOrNull() ?: 0
                if (count > 0) {
                    map[acct] = count
                }
            }
        }
        return map
    }

    /**
     * Encodes the current account's streak and G-Token state into `UserCloudProfile.appliedOptionIds`
     * for multi-device synchronization. When a streak breaks and resets to 0, removes `streak_active:`
     * from the cloud profile so all devices remove the active streak for that account.
     */
    fun mergeStreakIntoCloudOptions(baseOptionIds: Set<String>, state: DailyStreakState): List<String> {
        val cleaned = baseOptionIds.filterNot {
            it.startsWith(CLOUD_ACTIVE_PREFIX) || it.startsWith(CLOUD_NONE_PREFIX)
        }.toMutableList()

        val neonClean = state.selectedHudNeonColorHex.removePrefix("#").take(8).ifBlank { "00E5FF" }
        val tokenEntry = if (state.currentStreak > 0 || state.isStreakBrokenRepairable) {
            val brokenSec = (state.brokenAtEpochMs / 1000L).coerceAtLeast(0L)
            "$CLOUD_ACTIVE_PREFIX${state.currentStreak}:${state.longestStreak}:${state.lastCompletedDate}:${state.gTokensBalance}:${state.streakFreezesOwned}:$brokenSec:${state.lostStreakCount}:$neonClean"
        } else {
            // Account has no active streak -> remove streak_active and record 0
            "$CLOUD_NONE_PREFIX${state.longestStreak}:${state.gTokensBalance}:${state.streakFreezesOwned}:$neonClean"
        }
        cleaned.add(tokenEntry.take(80))
        return cleaned.take(50)
    }

    /**
     * Restores multi-device streak & G-Tokens state from `UserCloudProfile.appliedOptionIds`.
     */
    @Synchronized
    fun restoreStreakFromCloudOptions(
        context: Context,
        accountKey: String,
        cloudOptionIds: List<String>,
        nowMs: Long = System.currentTimeMillis()
    ): DailyStreakState {
        activeAccountKey = accountKey.ifBlank { "default_account" }
        val prefs = getPrefs(context)
        val activeToken = cloudOptionIds.firstOrNull { it.startsWith(CLOUD_ACTIVE_PREFIX) }
        val noneToken = cloudOptionIds.firstOrNull { it.startsWith(CLOUD_NONE_PREFIX) }

        if (activeToken != null) {
            val parts = activeToken.removePrefix(CLOUD_ACTIVE_PREFIX).split(":")
            if (parts.size >= 8) {
                val remoteStreak = parts[0].toIntOrNull()?.coerceAtLeast(0) ?: 0
                val remoteLongest = parts[1].toIntOrNull()?.coerceAtLeast(remoteStreak) ?: remoteStreak
                val remoteLastDate = parts[2]
                val remoteTokens = parts[3].toIntOrNull()?.coerceAtLeast(0) ?: 0
                val remoteFreezes = parts[4].toIntOrNull()?.coerceIn(0, MAX_STREAK_FREEZES) ?: 0
                val remoteBrokenSec = parts[5].toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                val remoteLostCount = parts[6].toIntOrNull()?.coerceAtLeast(0) ?: 0
                val remoteNeon = "#" + parts[7].removePrefix("#").ifBlank { "00E5FF" }

                val localStreak = prefs.getInt(scopedKey("current_streak", activeAccountKey), 0)
                val localTokens = prefs.getInt(scopedKey("g_tokens_balance", activeAccountKey), 0)
                val localLongest = prefs.getInt(scopedKey("longest_streak", activeAccountKey), 0)

                val mergedStreak = maxOf(remoteStreak, localStreak)
                val mergedLongest = maxOf(remoteLongest, localLongest, mergedStreak)
                val mergedTokens = maxOf(remoteTokens, localTokens)
                val localLastDate = prefs.getString(scopedKey("last_completed_date", activeAccountKey), "").orEmpty()
                val mergedLastDate = if (remoteLastDate >= localLastDate) remoteLastDate else localLastDate
                val isBroken = remoteBrokenSec > 0L && mergedStreak == 0 && remoteLostCount > 0
                val brokenMs = remoteBrokenSec * 1000L

                prefs.edit()
                    .putInt(scopedKey("current_streak", activeAccountKey), mergedStreak)
                    .putInt(scopedKey("longest_streak", activeAccountKey), mergedLongest)
                    .putString(scopedKey("last_completed_date", activeAccountKey), mergedLastDate)
                    .putInt(scopedKey("g_tokens_balance", activeAccountKey), mergedTokens)
                    .putInt(scopedKey("freezes_owned", activeAccountKey), remoteFreezes)
                    .putBoolean(scopedKey("is_broken_repairable", activeAccountKey), isBroken)
                    .putInt(scopedKey("lost_streak_count", activeAccountKey), if (isBroken) remoteLostCount else 0)
                    .putLong(scopedKey("broken_at_ms", activeAccountKey), if (isBroken) brokenMs else 0L)
                    .putLong(
                        scopedKey("repair_deadline_ms", activeAccountKey),
                        if (isBroken) brokenMs + REPAIR_GRACE_WINDOW_MS else 0L
                    )
                    .putString(scopedKey("hud_neon_hex", activeAccountKey), remoteNeon)
                    .apply()
            }
        } else if (noneToken != null) {
            // Remote device explicitly recorded that this account's streak broke/reset to 0
            val parts = noneToken.removePrefix(CLOUD_NONE_PREFIX).split(":")
            val remoteLongest = parts.getOrNull(0)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val remoteTokens = parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val remoteFreezes = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(0, MAX_STREAK_FREEZES) ?: 0
            val remoteNeon = "#" + (parts.getOrNull(3)?.removePrefix("#") ?: "00E5FF")

            val localLastDate = prefs.getString(scopedKey("last_completed_date", activeAccountKey), "").orEmpty()
            val todayKey = formatDateKey(nowMs)
            // Only overwrite to 0 if this device hasn't already completed today's streak
            if (localLastDate != todayKey) {
                prefs.edit()
                    .putInt(scopedKey("current_streak", activeAccountKey), 0)
                    .putInt(scopedKey("longest_streak", activeAccountKey), remoteLongest)
                    .putString(scopedKey("last_completed_date", activeAccountKey), "")
                    .putInt(scopedKey("g_tokens_balance", activeAccountKey), remoteTokens)
                    .putInt(scopedKey("freezes_owned", activeAccountKey), remoteFreezes)
                    .putBoolean(scopedKey("is_broken_repairable", activeAccountKey), false)
                    .putInt(scopedKey("lost_streak_count", activeAccountKey), 0)
                    .putString(scopedKey("hud_neon_hex", activeAccountKey), remoteNeon)
                    .apply()
            }
        }

        return evaluateMidnightRollover(context, nowMs)
    }

    private fun buildSevenDayStrip(
        nowMs: Long,
        currentStreak: Int,
        completedToday: Boolean,
        lastFreezeConsumedDate: String
    ): List<StreakDayEntry> {
        val dayLabels = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val list = ArrayList<StreakDayEntry>(7)
        val activeDaysBeforeToday = if (completedToday) (currentStreak - 1).coerceAtLeast(0) else currentStreak

        for (offset in -5..1) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMs
                add(Calendar.DAY_OF_YEAR, offset)
            }
            val key = formatDateKey(cal.timeInMillis)
            val dow = cal.get(Calendar.DAY_OF_WEEK) // 1..7
            val label = dayLabels[(dow - 1).coerceIn(0, 6)]
            val status = when {
                offset > 0 -> StreakDayStatus.UPCOMING
                offset == 0 -> if (completedToday) StreakDayStatus.COMPLETED else StreakDayStatus.TODAY_PENDING
                key == lastFreezeConsumedDate -> StreakDayStatus.FROZEN
                -offset <= activeDaysBeforeToday -> StreakDayStatus.COMPLETED
                else -> StreakDayStatus.MISSED
            }
            list.add(StreakDayEntry(dateKey = key, dayShortLabel = label, status = status))
        }
        return list
    }
}
