package com.example.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.example.model.InstalledApp
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class GamePlayStats(
    val game: InstalledApp,
    val lastPlayedEpochMs: Long,
    val totalPlayTimeMs: Long,
    val launchCount: Int
) {
    val formattedLastPlayed: String
        get() {
            if (lastPlayedEpochMs <= 0L) return "Recently added"
            val diffMs = (System.currentTimeMillis() - lastPlayedEpochMs).coerceAtLeast(0L)
            val minutes = diffMs / (60 * 1000L)
            val hours = diffMs / (60 * 60 * 1000L)
            val days = diffMs / (24 * 60 * 60 * 1000L)

            return when {
                minutes < 2 -> "Just now"
                minutes < 60 -> "${minutes}m ago"
                hours < 24 -> "${hours}h ago"
                days == 1L -> "Yesterday"
                days < 7L -> "${days}d ago"
                else -> SimpleDateFormat("MMM d", Locale.US).format(Date(lastPlayedEpochMs))
            }
        }

    val formattedPlayTime: String
        get() {
            val totalMinutes = (totalPlayTimeMs / (60 * 1000L)).toInt()
            val hours = totalMinutes / 60
            val mins = totalMinutes % 60
            return when {
                hours >= 10 -> String.format(Locale.US, "%.1f hrs", totalPlayTimeMs / (3600_000.0))
                hours >= 1 -> "${hours}h ${mins}m"
                totalMinutes >= 1 -> "${totalMinutes}m played"
                launchCount > 0 -> "$launchCount ${if (launchCount == 1) "session" else "sessions"}"
                else -> "Ready"
            }
        }
}

data class TotalPlayTimeSummary(
    val totalPlayTimeMs: Long = 0L,
    val totalMinutes: Long = 0L,
    val hours: Long = 0L,
    val minutesPart: Long = 0L,
    val totalSessions: Int = 0,
    val gamesPlayedCount: Int = 0
) {
    val formattedHoursAndMinutes: String
        get() = "${hours}h ${minutesPart}m"
}

/**
 * Combines real Android UsageStatsManager telemetry with G-Max Hub launcher session tracking
 * to power the "Recently Played" and "Most Played" rows and automatic per-game media tagging.
 */
object GamePlayStatsHelper {

    private const val PREFS_NAME = "gmax_game_play_stats_prefs"
    private const val KEY_STATS_JSON = "stats_json"
    private const val KEY_ACTIVE_ACCOUNT_KEY = "active_stats_account_key"
    private const val KEY_INSTALL_TIMESTAMP_MS = "gmax_install_timestamp_ms"
    private const val KEY_ACTIVE_LAUNCH_PKG = "active_launch_pkg"
    private const val KEY_ACTIVE_LAUNCH_NAME = "active_launch_name"
    private const val KEY_ACTIVE_LAUNCH_START_MS = "active_launch_start_ms"

    private data class LocalGameRecord(
        val packageName: String,
        var appName: String,
        var lastLaunchedMs: Long = 0L,
        var accumulatedDurationMs: Long = 0L,
        var launchCount: Int = 0
    )

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun resolveAccountStatsKey(context: Context): String {
        val prefs = getPrefs(context)
        val acct = prefs.getString(KEY_ACTIVE_ACCOUNT_KEY, null)?.trim().orEmpty()
        return if (acct.isNotBlank() && acct != "guest") "${KEY_STATS_JSON}_$acct" else KEY_STATS_JSON
    }

    /**
     * Links the Most Played / Recently Played game records strictly to the active user account.
     */
    fun setActiveAccountKey(context: Context, accountKey: String) {
        val clean = accountKey.trim().lowercase(Locale.US).ifBlank { "guest" }
        val prefs = getPrefs(context)
        prefs.edit().putString(KEY_ACTIVE_ACCOUNT_KEY, clean).apply()
    }

    /**
     * Restores the user's Most Played game and play time from their cloud account (`UserCloudProfile`)
     * so switching devices or reinstalling preserves their Most Played game on their account.
     */
    fun restoreCloudMostPlayedGame(
        context: Context,
        accountKey: String,
        mostPlayedGamePkg: String,
        mostPlayedGameName: String,
        mostPlayedMinutes: Long,
        totalPlayTimeMinutes: Long,
        totalSessionsCount: Long
    ) {
        setActiveAccountKey(context, accountKey)
        val pkg = mostPlayedGamePkg.trim()
        val name = mostPlayedGameName.trim()
        if (pkg.isBlank() && (name.isBlank() || name.equals("No games played yet", ignoreCase = true))) {
            return
        }
        val effectivePkg = pkg.ifBlank {
            "cloud.game.${name.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_")}"
        }
        val effectiveName = name.ifBlank { effectivePkg.substringAfterLast(".") }
        if (isSelfHubOrHudApp(context, effectivePkg, effectiveName)) return

        val records = loadLocalRecords(context)
        val existing = records[effectivePkg] ?: LocalGameRecord(
            packageName = effectivePkg,
            appName = effectiveName
        )
        val cloudDurationMs = (mostPlayedMinutes.coerceAtLeast(1L)) * 60_000L
        val cloudLaunches = totalSessionsCount.toInt().coerceAtLeast(1)
        if (cloudDurationMs > existing.accumulatedDurationMs) {
            existing.accumulatedDurationMs = cloudDurationMs
        }
        if (cloudLaunches > existing.launchCount) {
            existing.launchCount = cloudLaunches
        }
        if (existing.lastLaunchedMs <= 0L) {
            existing.lastLaunchedMs = System.currentTimeMillis() - 60_000L
        }
        existing.appName = effectiveName
        records[effectivePkg] = existing
        saveLocalRecords(context, records)
    }

    /**
     * Returns the timestamp when G-Max Hub was installed/first initialized on this device.
     * Ensures pre-install Android UsageStats history does not populate Recently/Most Played on a fresh install.
     */
    fun getInstallTimestampMs(context: Context): Long {
        val prefs = getPrefs(context)
        val saved = prefs.getLong(KEY_INSTALL_TIMESTAMP_MS, 0L)
        if (saved > 0L) return saved

        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_INSTALL_TIMESTAMP_MS, now).apply()
        return now
    }

    fun isSelfHubOrHudApp(context: Context, packageName: String?, appName: String? = null): Boolean {
        val pkg = packageName?.trim().orEmpty()
        val name = appName?.trim().orEmpty()
        if (pkg.isEmpty() && name.isEmpty()) return true
        if (pkg.equals(context.packageName, ignoreCase = true)) return true
        if (pkg.equals("com.example", ignoreCase = true)) return true
        if (pkg.startsWith("com.aistudio.gamebooster", ignoreCase = true)) return true
        val lowerName = name.lowercase(Locale.US)
        if (lowerName.contains("g max gaming") ||
            lowerName.contains("gmax gaming") ||
            lowerName == "g max" ||
            lowerName == "gmax"
        ) {
            return true
        }
        return false
    }

    private fun loadLocalRecords(context: Context): MutableMap<String, LocalGameRecord> {
        val prefs = getPrefs(context)
        // Ensure install timestamp is initialized on first check
        getInstallTimestampMs(context)
        val activePrefKey = resolveAccountStatsKey(context)
        val rawJson = prefs.getString(activePrefKey, null)

        val map = mutableMapOf<String, LocalGameRecord>()
        if (rawJson.isNullOrBlank()) return map

        var removedSelf = false
        try {
            val root = JSONObject(rawJson)
            val keys = root.keys()
            while (keys.hasNext()) {
                val pkg = keys.next()
                val obj = root.optJSONObject(pkg) ?: continue
                val appName = obj.optString("appName", pkg.substringAfterLast("."))
                if (isSelfHubOrHudApp(context, pkg, appName)) {
                    removedSelf = true
                    continue
                }
                map[pkg] = LocalGameRecord(
                    packageName = pkg,
                    appName = appName,
                    lastLaunchedMs = obj.optLong("lastLaunchedMs", 0L),
                    accumulatedDurationMs = obj.optLong("accumulatedDurationMs", 0L),
                    launchCount = obj.optInt("launchCount", 0)
                )
            }
        } catch (_: Exception) {}
        if (removedSelf) {
            saveLocalRecords(context, map)
        }
        return map
    }

    private fun saveLocalRecords(context: Context, records: Map<String, LocalGameRecord>) {
        try {
            val root = JSONObject()
            for ((pkg, rec) in records) {
                if (isSelfHubOrHudApp(context, pkg, rec.appName)) continue
                val obj = JSONObject().apply {
                    put("appName", rec.appName)
                    put("lastLaunchedMs", rec.lastLaunchedMs)
                    put("accumulatedDurationMs", rec.accumulatedDurationMs)
                    put("launchCount", rec.launchCount)
                }
                root.put(pkg, obj)
            }
            val jsonString = root.toString()
            val activePrefKey = resolveAccountStatsKey(context)
            getPrefs(context).edit()
                .putString(activePrefKey, jsonString)
                .apply()
        } catch (_: Exception) {}
    }

    /**
     * Records a game launch initiated from G Max Gaming Hub and sets it as the active capture target.
     * Credits a baseline 1-minute play block on launch if the game had 0ms so Most Played Games,
     * Total Gaming Time, and Gamerscore immediately reflect the launched session.
     * Explicitly ignores G Max Gaming Hub / HUD itself.
     */
    fun recordGameLaunch(context: Context, packageName: String, appName: String) {
        if (isSelfHubOrHudApp(context, packageName, appName)) return
        val now = System.currentTimeMillis()
        getInstallTimestampMs(context)
        LeaderboardAntiCheatEngine.onGameLaunchStarted(context, packageName)

        val canCreditLaunch = LeaderboardAntiCheatEngine.canCreditNewLaunch(context, packageName, now)
        val records = loadLocalRecords(context)
        val existing = records[packageName] ?: LocalGameRecord(packageName = packageName, appName = appName)
        existing.appName = appName
        existing.lastLaunchedMs = now
        if (canCreditLaunch || existing.launchCount == 0) {
            existing.launchCount += 1
            // Ensure every credited session contributes at least 1 minute of baseline play time
            // before finalizeActiveGameSessionOnReturn adds the rest of the elapsed session duration.
            existing.accumulatedDurationMs += 60_000L
        } else if (existing.accumulatedDurationMs <= 0L) {
            existing.accumulatedDurationMs = 60_000L
        }
        records[packageName] = existing
        saveLocalRecords(context, records)

        getPrefs(context).edit()
            .putString(KEY_ACTIVE_LAUNCH_PKG, packageName)
            .putString(KEY_ACTIVE_LAUNCH_NAME, appName)
            .putLong(KEY_ACTIVE_LAUNCH_START_MS, now)
            .apply()

        ScreenRecordingManager.setActiveGameContext(context, packageName, appName)
    }

    /**
     * Called when returning to G Max Gaming Hub (e.g. MainActivity.onResume) to attribute elapsed
     * session play time if a game was launched from the hub.
     */
    fun finalizeActiveGameSessionOnReturn(context: Context) {
        val prefs = getPrefs(context)
        val activePkg = prefs.getString(KEY_ACTIVE_LAUNCH_PKG, null) ?: return
        val activeName = prefs.getString(KEY_ACTIVE_LAUNCH_NAME, activePkg) ?: activePkg
        if (isSelfHubOrHudApp(context, activePkg, activeName)) {
            prefs.edit()
                .remove(KEY_ACTIVE_LAUNCH_PKG)
                .remove(KEY_ACTIVE_LAUNCH_NAME)
                .remove(KEY_ACTIVE_LAUNCH_START_MS)
                .apply()
            return
        }
        val startMs = prefs.getLong(KEY_ACTIVE_LAUNCH_START_MS, 0L)
        if (startMs <= 0L) return

        val nowWall = System.currentTimeMillis()
        val rawElapsedMs = (nowWall - startMs).coerceIn(0L, 4 * 3600_000L)
        val validation = LeaderboardAntiCheatEngine.validateAndFinalizeSession(
            context = context,
            packageName = activePkg,
            wallStartMs = startMs,
            nowWallMs = nowWall
        )

        val records = loadLocalRecords(context)
        val rec = records[activePkg] ?: LocalGameRecord(packageName = activePkg, appName = activeName)
        // Subtract the 60s baseline already credited at launch when adding the full session duration
        val additionalMs = when {
            validation.isCredited && validation.verifiedDurationMs > 60_000L ->
                validation.verifiedDurationMs - 60_000L
            rawElapsedMs > 60_000L ->
                rawElapsedMs - 60_000L
            else -> 0L
        }
        if (additionalMs > 0L) {
            rec.accumulatedDurationMs += additionalMs
        }
        if (rec.accumulatedDurationMs <= 0L) {
            rec.accumulatedDurationMs = 60_000L
        }
        if (rec.launchCount <= 0) {
            rec.launchCount = 1
        }
        rec.lastLaunchedMs = nowWall
        records[activePkg] = rec
        saveLocalRecords(context, records)

        prefs.edit()
            .remove(KEY_ACTIVE_LAUNCH_StartKey())
            .apply()
    }

    private fun KEY_ACTIVE_LAUNCH_StartKey() = KEY_ACTIVE_LAUNCH_START_MS

    /**
     * Queries Android UsageStatsManager for real foreground usage strictly AFTER G Max was installed.
     */
    private fun queryPostInstallSystemUsageStats(context: Context, installTimeMs: Long): Map<String, Pair<Long, Long>> {
        // Pair<lastTimeUsedMs, totalTimeInForegroundMs>
        val result = mutableMapOf<String, Pair<Long, Long>>()
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return result
            val endTime = System.currentTimeMillis()
            if (endTime <= installTimeMs) return result

            val aggregated = usm.queryAndAggregateUsageStats(installTimeMs, endTime)
            for ((pkg, stats) in aggregated) {
                if (isSelfHubOrHudApp(context, pkg)) continue
                val lastUsed = stats.lastTimeUsed
                val totalFg = stats.totalTimeInForeground
                if (lastUsed >= installTimeMs && (lastUsed > 0L || totalFg > 0L)) {
                    result[pkg] = Pair(lastUsed, totalFg)
                }
            }
        } catch (_: Exception) {}
        return result
    }

    /**
     * Computes merged play stats for all games in the user's library.
     * Combines G Max Gaming Hub launches with post-install Android UsageStatsManager foreground time.
     * Strictly excludes G Max Gaming Hub / HUD itself.
     */
    fun getMergedGameStats(
        context: Context,
        libraryGames: List<InstalledApp>
    ): List<GamePlayStats> {
        val installTimeMs = getInstallTimestampMs(context)
        val localRecords = loadLocalRecords(context)
        val systemUsage = queryPostInstallSystemUsageStats(context, installTimeMs)

        return libraryGames
            .filterNot { isSelfHubOrHudApp(context, it.packageName, it.appName) }
            .map { game ->
                val local = localRecords[game.packageName]
                val sys = systemUsage[game.packageName]

                val hasLocalLaunch = (local?.launchCount ?: 0) > 0
                val isSignedInAccount = resolveAccountStatsKey(context) != KEY_STATS_JSON
                val hasPostInstallSystemPlay = !isSignedInAccount &&
                    sys != null && sys.first >= installTimeMs && sys.second >= 30_000L

                if (!hasLocalLaunch && !hasPostInstallSystemPlay) {
                    GamePlayStats(
                        game = game,
                        lastPlayedEpochMs = 0L,
                        totalPlayTimeMs = 0L,
                        launchCount = 0
                    )
                } else {
                    val lastPlayedMs = maxOf(local?.lastLaunchedMs ?: 0L, if (hasLocalLaunch) (sys?.first ?: 0L) else 0L)
                    val launches = maxOf(local?.launchCount ?: 0, if (hasPostInstallSystemPlay) 1 else 0)
                    val minExpectedMs = launches * 60_000L
                    val totalPlayMs = maxOf(
                        local?.accumulatedDurationMs ?: 0L,
                        if (hasLocalLaunch) (sys?.second ?: 0L) else 0L,
                        minExpectedMs
                    )

                    GamePlayStats(
                        game = game,
                        lastPlayedEpochMs = lastPlayedMs,
                        totalPlayTimeMs = totalPlayMs,
                        launchCount = launches
                    )
                }
            }
    }

    /**
     * Returns games ordered by most recently played.
     */
    fun getRecentlyPlayedGames(
        context: Context,
        libraryGames: List<InstalledApp>
    ): List<GamePlayStats> {
        val allStats = getMergedGameStats(context, libraryGames)
        return allStats
            .filter { !isSelfHubOrHudApp(context, it.game.packageName, it.game.appName) && it.launchCount > 0 && it.lastPlayedEpochMs > 0L }
            .sortedByDescending { it.lastPlayedEpochMs }
            .take(10)
    }

    /**
     * Returns games ordered by highest total play time and session count, including any cloud-synced
     * Most Played games for the active user account even if not currently in libraryGames.
     */
    fun getMostPlayedGames(
        context: Context,
        libraryGames: List<InstalledApp>
    ): List<GamePlayStats> {
        val localRecords = loadLocalRecords(context)
        val combinedGamesByPkg = linkedMapOf<String, InstalledApp>()
        for (g in libraryGames) {
            if (!isSelfHubOrHudApp(context, g.packageName, g.appName)) {
                combinedGamesByPkg[g.packageName] = g
            }
        }
        for ((pkg, rec) in localRecords) {
            if (!isSelfHubOrHudApp(context, pkg, rec.appName) && !combinedGamesByPkg.containsKey(pkg)) {
                combinedGamesByPkg[pkg] = InstalledApp(
                    packageName = pkg,
                    appName = rec.appName.ifBlank { pkg.substringAfterLast(".") },
                    isGame = true
                )
            }
        }
        val allStats = getMergedGameStats(context, combinedGamesByPkg.values.toList())
        return allStats
            .filter { !isSelfHubOrHudApp(context, it.game.packageName, it.game.appName) && it.launchCount > 0 && (it.totalPlayTimeMs > 0L || it.lastPlayedEpochMs > 0L) }
            .sortedWith(
                compareByDescending<GamePlayStats> { it.totalPlayTimeMs }
                    .thenByDescending { it.launchCount }
                    .thenByDescending { it.lastPlayedEpochMs }
            )
            .take(10)
    }

    /**
     * Calculates every game session together across all tracked and library games,
     * returning cumulative hours, minutes, total play time in ms, and total session count.
     * Explicitly excludes G Max Gaming Hub / HUD.
     */
    fun getTotalPlayTimeSummary(
        context: Context,
        libraryGames: List<InstalledApp>
    ): TotalPlayTimeSummary {
        val localRecords = loadLocalRecords(context)
        val combinedGamesByPkg = linkedMapOf<String, InstalledApp>()
        for (game in libraryGames) {
            if (isSelfHubOrHudApp(context, game.packageName, game.appName)) continue
            combinedGamesByPkg[game.packageName] = game
        }
        for ((pkg, record) in localRecords) {
            if (isSelfHubOrHudApp(context, pkg, record.appName)) continue
            if (!combinedGamesByPkg.containsKey(pkg)) {
                combinedGamesByPkg[pkg] = InstalledApp(
                    packageName = pkg,
                    appName = record.appName.ifBlank { pkg.substringAfterLast(".") },
                    isGame = true
                )
            }
        }
        val mergedStats = getMergedGameStats(context, combinedGamesByPkg.values.toList())
        val playedStats = mergedStats.filter {
            !isSelfHubOrHudApp(context, it.game.packageName, it.game.appName) &&
                (it.totalPlayTimeMs > 0L || it.launchCount > 0)
        }
        val totalMs = playedStats.sumOf { it.totalPlayTimeMs }
        val totalSessions = playedStats.sumOf { it.launchCount }
        val totalMins = if (totalMs > 0L) ((totalMs + 59_999L) / 60_000L).coerceAtLeast(totalSessions.toLong()) else 0L
        val hrs = totalMins / 60L
        val minsPart = totalMins % 60L
        return TotalPlayTimeSummary(
            totalPlayTimeMs = totalMs,
            totalMinutes = totalMins,
            hours = hrs,
            minutesPart = minsPart,
            totalSessions = totalSessions,
            gamesPlayedCount = playedStats.size
        )
    }

    /**
     * Detects the currently active or most recently foregrounded game for tagging recordings and screenshots.
     */
    fun resolveCurrentOrRecentGame(context: Context): Pair<String, String>? {
        val prefs = getPrefs(context)
        val activePkg = prefs.getString(KEY_ACTIVE_LAUNCH_PKG, null)
        val activeName = prefs.getString(KEY_ACTIVE_LAUNCH_NAME, null)

        // 1. Check UsageEvents in the last 15 minutes if permission is granted
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usm != null) {
                val now = System.currentTimeMillis()
                val events = usm.queryEvents(now - 15 * 60_000L, now)
                val event = UsageEvents.Event()
                var lastFgPkg: String? = null
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND &&
                        event.packageName != context.packageName &&
                        !event.packageName.contains("launcher", ignoreCase = true) &&
                        !event.packageName.contains("systemui", ignoreCase = true)
                    ) {
                        lastFgPkg = event.packageName
                    }
                }
                if (lastFgPkg != null) {
                    val pm = context.packageManager
                    val label = try {
                        val info = pm.getApplicationInfo(lastFgPkg, 0)
                        pm.getApplicationLabel(info).toString()
                    } catch (_: Exception) {
                        lastFgPkg.substringAfterLast(".")
                    }
                    return Pair(lastFgPkg, label)
                }
            }
        } catch (_: Exception) {}

        // 2. Fallback to active boost / launched game in G-Max Hub
        if (!activePkg.isNullOrBlank() && !activeName.isNullOrBlank()) {
            return Pair(activePkg, activeName)
        }

        val sessionMgr = BoosterSessionManager(context)
        val (lastBoostPkg, lastBoostName) = sessionMgr.getLastTargetGame()
        if (!lastBoostPkg.isNullOrBlank() && !lastBoostName.isNullOrBlank()) {
            return Pair(lastBoostPkg, lastBoostName)
        }

        return null
    }
}
