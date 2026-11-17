package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.data.repository.RivalsPendingPassTauntOpportunity
import com.example.data.repository.RivalsRepository
import com.example.data.repository.RivalsSquad
import com.example.data.repository.RivalsSquadInvite
import com.example.data.repository.RivalsSquadMemberRank
import com.example.data.repository.RivalsTaunt
import com.example.data.repository.RivalsUiState
import com.example.data.repository.UserAccountRepository
import com.example.data.repository.UsernameClaim
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object RivalsManager {

    private const val PREFS_NAME = "gmax_rivals_competition_prefs"
    private const val KEY_BOOSTER_COUNT = "rivals_booster_activated_count"
    private const val KEY_RECORDING_COUNT = "rivals_recording_completed_count"
    private const val KEY_OVERLAY_COUNT = "rivals_overlay_activated_count"
    private const val KEY_POPUP_SHOWN_FOR_2X = "rivals_invite_popup_shown_2x"
    private const val KEY_HAS_ACTIVE_SQUAD = "rivals_has_active_squad_cached"
    private const val KEY_LAST_SEASON_MONTH_KEY = "rivals_last_utc_season_month_key"
    private const val KEY_MONTHLY_WRAP_SENT_MONTH_KEY = "rivals_monthly_wrap_12h_sent_month_key"
    private const val KEY_SENT_PASS_TAUNT_KEYS = "rivals_sent_pass_taunt_keys"
    private const val KEY_NOTIFIED_TAUNT_IDS = "rivals_notified_taunt_ids"
    private const val KEY_NOTIFIED_THREAT_KEYS = "rivals_notified_threat_keys"
    private const val KEY_NOTIFIED_ACTIVITY_KEYS = "rivals_notified_activity_keys"

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + AppCrashGuard.coroutineExceptionHandler)

    private var squadJob: Job? = null
    private var leaderboardJob: Job? = null
    private var invitesJob: Job? = null
    private var tauntsJob: Job? = null
    private var activeObservedUid: String? = null
    private var activeObservedSquadKey: String? = null

    private var previousLeaderboardSnapshot: Map<String, RivalsSquadMemberRank> = emptyMap()

    private val _state = MutableStateFlow(RivalsUiState())
    val state: StateFlow<RivalsUiState> = _state.asStateFlow()

    private fun getPrefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun initialize(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val appContext = context.applicationContext
        val prefs = getPrefs(appContext)
        val boosterCount = prefs.getInt(KEY_BOOSTER_COUNT, 0).coerceAtLeast(0)
        val recCount = prefs.getInt(KEY_RECORDING_COUNT, 0).coerceAtLeast(0)
        val overlayCount = prefs.getInt(KEY_OVERLAY_COUNT, 0).coerceAtLeast(0)
        val monthKey = RivalsRepository.currentUtcSeasonMonthKey(nowMs)
        val nextResetMs = RivalsRepository.nextUtcMonthResetEpochMs(nowMs)
        val countdownLabel = RivalsRepository.formatRemainingResetCountdown(nowMs)

        // Ensure stored season month key is initialized and check for monthly rollover
        evaluateMonthlyResetAnd12hWrap(appContext, nowMs)
        SmartNotificationManager.scheduleRivalsMonthlyWrap12hBeforeReset(appContext, nowMs)

        _state.update { prev ->
            prev.copy(
                boosterActivatedCount = boosterCount,
                recordingCompletedCount = recCount,
                overlayActivatedCount = overlayCount,
                currentSeasonMonthKey = monthKey,
                nextResetEpochMs = nextResetMs,
                formattedResetCountdown = countdownLabel
            )
        }
    }

    /**
     * Refreshes the live countdown timer string for the monthly UTC Gamer Score reset.
     */
    fun refreshSeasonResetCountdown(context: Context, nowMs: Long = System.currentTimeMillis()) {
        evaluateMonthlyResetAnd12hWrap(context, nowMs)
        _state.update { prev ->
            prev.copy(
                currentSeasonMonthKey = RivalsRepository.currentUtcSeasonMonthKey(nowMs),
                nextResetEpochMs = RivalsRepository.nextUtcMonthResetEpochMs(nowMs),
                formattedResetCountdown = RivalsRepository.formatRemainingResetCountdown(nowMs)
            )
        }
    }

    /**
     * Evaluates:
     * 1) Monthly Global Gamer Score Reset at `00:00:00 UTC` on the 1st of each month (resets Gamer Score to 0).
     * 2) Monthly Wrap push notification exactly 12 hours before the monthly UTC reset (`12:00:00 UTC` on the last day of the month).
     */
    fun evaluateMonthlyResetAnd12hWrap(
        context: Context,
        nowMs: Long = System.currentTimeMillis(),
        onScoreReset: (() -> Unit)? = null
    ): Boolean {
        val appContext = context.applicationContext
        val prefs = getPrefs(appContext)
        val currentMonthKey = RivalsRepository.currentUtcSeasonMonthKey(nowMs)
        val savedMonthKey = prefs.getString(KEY_LAST_SEASON_MONTH_KEY, null)

        var didReset = false
        if (savedMonthKey == null) {
            prefs.edit().putString(KEY_LAST_SEASON_MONTH_KEY, currentMonthKey).apply()
        } else if (savedMonthKey != currentMonthKey) {
            // Exact monthly UTC reset: reset all users' Gamer Score to 0 for the new month
            prefs.edit()
                .putString(KEY_LAST_SEASON_MONTH_KEY, currentMonthKey)
                .remove(KEY_SENT_PASS_TAUNT_KEYS)
                .remove(KEY_NOTIFIED_THREAT_KEYS)
                .remove(KEY_NOTIFIED_ACTIVITY_KEYS)
                .apply()

            GamePlayStatsHelper.resetAllGamePlayStatsForMonthlySeasonReset(appContext, nowMs)
            val uiPrefs = appContext.getSharedPreferences("gmax_hub_ui_prefs", Context.MODE_PRIVATE)
            uiPrefs.edit().putLong("hub_monthly_gamerscore_override", 0L).apply()

            val currentUser = try { Firebase.auth.currentUser } catch (_: Throwable) { null }
            if (currentUser != null) {
                managerScope.launch {
                    try {
                        UserAccountRepository(appContext).updateGamerscore(0L)
                    } catch (_: Exception) {}
                }
            }
            onScoreReset?.invoke()
            didReset = true
        }

        // Check if we are inside the 12-hour window before the next monthly UTC reset
        val wrapTriggerAtMs = RivalsRepository.nextMonthlyWrap12HoursBeforeResetEpochMs(nowMs)
        val nextResetMs = RivalsRepository.nextUtcMonthResetEpochMs(nowMs)
        if (nowMs in wrapTriggerAtMs until nextResetMs) {
            val lastWrapSentMonth = prefs.getString(KEY_MONTHLY_WRAP_SENT_MONTH_KEY, null)
            if (lastWrapSentMonth != currentMonthKey) {
                val currentUid = try { Firebase.auth.currentUser?.uid } catch (_: Throwable) { null }
                val myRankLabel = _state.value.leaderboard
                    .firstOrNull { it.userId == currentUid }
                    ?.rankLabel
                    ?: "1st"
                val wrapMsg = RivalsRepository.buildMonthlyWrapNotificationText(myRankLabel)
                SmartNotificationManager.postRivalsNotification(
                    context = appContext,
                    trigger = SmartNotificationTrigger.RIVALS_MONTHLY_WRAP,
                    message = wrapMsg
                )
                prefs.edit().putString(KEY_MONTHLY_WRAP_SENT_MONTH_KEY, currentMonthKey).apply()
            }
        }

        return didReset
    }

    /**
     * Explicitly sets the stored season month key (used in unit tests to verify monthly rollover).
     */
    fun setLastRecordedSeasonMonthKeyForTesting(context: Context, monthKey: String) {
        getPrefs(context).edit().putString(KEY_LAST_SEASON_MONTH_KEY, monthKey).apply()
    }

    // =========================================================================
    // "Invite a Squad" In-App Popup (2x Booster OR 2x Recording OR 2x Overlay)
    // =========================================================================

    fun recordBoosterActivation(context: Context): Boolean {
        val prefs = getPrefs(context)
        val nextCount = prefs.getInt(KEY_BOOSTER_COUNT, 0) + 1
        prefs.edit().putInt(KEY_BOOSTER_COUNT, nextCount).apply()
        _state.update { it.copy(boosterActivatedCount = nextCount) }
        return evaluateInviteSquadPopupTrigger(context)
    }

    fun recordRecordingSession(context: Context): Boolean {
        val prefs = getPrefs(context)
        val nextCount = prefs.getInt(KEY_RECORDING_COUNT, 0) + 1
        prefs.edit().putInt(KEY_RECORDING_COUNT, nextCount).apply()
        _state.update { it.copy(recordingCompletedCount = nextCount) }
        return evaluateInviteSquadPopupTrigger(context)
    }

    fun recordOverlayActivation(context: Context): Boolean {
        val prefs = getPrefs(context)
        val nextCount = prefs.getInt(KEY_OVERLAY_COUNT, 0) + 1
        prefs.edit().putInt(KEY_OVERLAY_COUNT, nextCount).apply()
        _state.update { it.copy(overlayActivatedCount = nextCount) }
        return evaluateInviteSquadPopupTrigger(context)
    }

    /**
     * Evaluates whether the "Invite a Squad" in-app popup should appear:
     * Appears if the user activated any Booster Mode 2 times, OR recorded 2 times,
     * OR activated an Overlay 2 times, AND the user does NOT currently have a Squad.
     */
    fun evaluateInviteSquadPopupTrigger(context: Context): Boolean {
        val prefs = getPrefs(context)
        val hasSquad = _state.value.activeSquad != null || prefs.getBoolean(KEY_HAS_ACTIVE_SQUAD, false)
        if (hasSquad) {
            if (_state.value.showInviteSquadPopup) {
                _state.update { it.copy(showInviteSquadPopup = false) }
            }
            return false
        }

        val boosterCount = prefs.getInt(KEY_BOOSTER_COUNT, 0)
        val recCount = prefs.getInt(KEY_RECORDING_COUNT, 0)
        val overlayCount = prefs.getInt(KEY_OVERLAY_COUNT, 0)
        val reached2x = boosterCount >= 2 || recCount >= 2 || overlayCount >= 2

        val alreadyShown = prefs.getBoolean(KEY_POPUP_SHOWN_FOR_2X, false)
        if (reached2x && !alreadyShown) {
            prefs.edit().putBoolean(KEY_POPUP_SHOWN_FOR_2X, true).apply()
            _state.update { it.copy(showInviteSquadPopup = true) }
            return true
        }
        return false
    }

    fun dismissInviteSquadPopup() {
        _state.update { it.copy(showInviteSquadPopup = false) }
    }

    fun resetActionCountersForTesting(context: Context, hasActiveSquad: Boolean = false) {
        getPrefs(context).edit()
            .putInt(KEY_BOOSTER_COUNT, 0)
            .putInt(KEY_RECORDING_COUNT, 0)
            .putInt(KEY_OVERLAY_COUNT, 0)
            .putBoolean(KEY_POPUP_SHOWN_FOR_2X, false)
            .putBoolean(KEY_HAS_ACTIVE_SQUAD, hasActiveSquad)
            .apply()
        _state.update {
            it.copy(
                activeSquad = if (hasActiveSquad) RivalsSquad(id = "test_squad", name = "Test Squad") else null,
                boosterActivatedCount = 0,
                recordingCompletedCount = 0,
                overlayActivatedCount = 0,
                showInviteSquadPopup = false
            )
        }
    }

    // =========================================================================
    // Real-Time Firestore Listeners (Squad, 5-Player Leaderboard, Invites, Taunts)
    // =========================================================================

    fun syncAuthenticatedUser(
        context: Context,
        uid: String,
        repository: RivalsRepository = RivalsRepository(context.applicationContext)
    ) {
        val appContext = context.applicationContext
        evaluateMonthlyResetAnd12hWrap(appContext)
        if (activeObservedUid == uid && squadJob?.isActive == true) return
        stopObserving()
        activeObservedUid = uid

        squadJob = managerScope.launch {
            repository.observeMyActiveSquad(uid)
                .catch { /* Handled via handleFirestoreError in repository */ }
                .collect { squad ->
                    onActiveSquadChanged(appContext, uid, squad, repository)
                }
        }

        invitesJob = managerScope.launch {
            repository.observeIncomingPendingInvites(uid)
                .catch { /* Handled via handleFirestoreError in repository */ }
                .collect { invites ->
                    _state.update { it.copy(incomingInvites = invites) }
                }
        }

        tauntsJob = managerScope.launch {
            repository.observeIncomingTaunts(uid)
                .catch { /* Handled via handleFirestoreError in repository */ }
                .collect { taunts ->
                    onIncomingTauntsSnapshot(appContext, taunts)
                }
        }
    }

    fun stopObserving() {
        squadJob?.cancel()
        squadJob = null
        leaderboardJob?.cancel()
        leaderboardJob = null
        invitesJob?.cancel()
        invitesJob = null
        tauntsJob?.cancel()
        tauntsJob = null
        activeObservedUid = null
        activeObservedSquadKey = null
        previousLeaderboardSnapshot = emptyMap()
    }

    fun onUserSignedOut(context: Context) {
        stopObserving()
        getPrefs(context).edit().putBoolean(KEY_HAS_ACTIVE_SQUAD, false).apply()
        _state.update {
            it.copy(
                activeSquad = null,
                leaderboard = emptyList(),
                incomingInvites = emptyList(),
                recentIncomingTaunts = emptyList(),
                pendingPassTauntOpportunities = emptyList()
            )
        }
    }

    private fun onActiveSquadChanged(
        context: Context,
        currentUid: String,
        squad: RivalsSquad?,
        repository: RivalsRepository
    ) {
        val prefs = getPrefs(context)
        val hasSquad = squad != null
        prefs.edit().putBoolean(KEY_HAS_ACTIVE_SQUAD, hasSquad).apply()

        _state.update { prev ->
            prev.copy(
                activeSquad = squad,
                showInviteSquadPopup = if (hasSquad) false else prev.showInviteSquadPopup,
                leaderboard = if (!hasSquad) emptyList() else prev.leaderboard,
                pendingPassTauntOpportunities = if (!hasSquad) emptyList() else prev.pendingPassTauntOpportunities
            )
        }

        if (squad == null) {
            leaderboardJob?.cancel()
            leaderboardJob = null
            activeObservedSquadKey = null
            previousLeaderboardSnapshot = emptyMap()
            return
        }

        val squadKey = "${squad.id}:${squad.memberUids.sorted().joinToString(",")}:${squad.leaderUid}"
        if (activeObservedSquadKey == squadKey && leaderboardJob?.isActive == true) {
            return
        }
        activeObservedSquadKey = squadKey
        leaderboardJob?.cancel()
        leaderboardJob = managerScope.launch {
            repository.observeSquadMembersLeaderboard(
                memberUids = squad.memberUids,
                leaderUid = squad.leaderUid
            )
                .catch { /* Handled via handleFirestoreError in repository */ }
                .collect { rankedMembers ->
                    onLeaderboardSnapshot(context, currentUid, squad, rankedMembers)
                }
        }
    }

    /**
     * Evaluates real-time leaderboard rank/score shifts to dispatch:
     * - The Threat (within 500 points)
     * - The Dethroning (squadmate passes current user)
     * - The Activity Nudge (+180 minutes / 3h session and pulling ahead)
     * - One-Click Pre-Set Taunt opportunity (current user passes a squadmate)
     */
    internal fun onLeaderboardSnapshot(
        context: Context,
        currentUid: String,
        squad: RivalsSquad,
        rankedMembers: List<RivalsSquadMemberRank>
    ) {
        val prefs = getPrefs(context)
        val prevMap = previousLeaderboardSnapshot
        val currMap = rankedMembers.associateBy { it.userId }
        previousLeaderboardSnapshot = currMap

        val sentPassKeys = prefs.getStringSet(KEY_SENT_PASS_TAUNT_KEYS, emptySet()).orEmpty()
        val notifiedThreatKeys = prefs.getStringSet(KEY_NOTIFIED_THREAT_KEYS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val notifiedActivityKeys = prefs.getStringSet(KEY_NOTIFIED_ACTIVITY_KEYS, emptySet())?.toMutableSet() ?: mutableSetOf()

        val myCurr = currMap[currentUid]
        val myPrev = prevMap[currentUid]

        val newPassOpportunities = _state.value.pendingPassTauntOpportunities
            .filter { !sentPassKeys.contains(it.passEventKey) }
            .toMutableList()

        if (myCurr != null) {
            for (rivalCurr in rankedMembers) {
                if (rivalCurr.userId == currentUid) continue
                val rivalPrev = prevMap[rivalCurr.userId]
                val rivalName = rivalCurr.displayName.ifBlank { rivalCurr.username }.ifBlank { "Friend" }

                // 1. The Threat: Friend is behind current user by 1..500 points
                val scoreGap = myCurr.gamerscore - rivalCurr.gamerscore
                if (scoreGap in 1L..RivalsRepository.THREAT_POINTS_THRESHOLD) {
                    val threatKey = "${squad.id}_${rivalCurr.userId}_threat_${myCurr.rankLabel}_${RivalsRepository.currentUtcSeasonMonthKey()}"
                    if (!notifiedThreatKeys.contains(threatKey)) {
                        notifiedThreatKeys.add(threatKey)
                        prefs.edit().putStringSet(KEY_NOTIFIED_THREAT_KEYS, notifiedThreatKeys).apply()
                        val msg = RivalsRepository.buildThreatNotificationText(
                            friendName = rivalName,
                            pointsAway = scoreGap,
                            rankPlacement = myCurr.rankLabel
                        )
                        SmartNotificationManager.postRivalsNotification(
                            context = context,
                            trigger = SmartNotificationTrigger.RIVALS_THREAT,
                            message = msg
                        )
                    }
                }

                if (myPrev != null && rivalPrev != null) {
                    // 2. The Dethroning: Rival was ranked lower than user (higher rankPosition number) and is now ranked higher (smaller rankPosition number)
                    if (rivalPrev.rankPosition > myPrev.rankPosition && rivalCurr.rankPosition < myCurr.rankPosition) {
                        val dethroneMsg = RivalsRepository.buildDethroningNotificationText(rivalName)
                        SmartNotificationManager.postRivalsNotification(
                            context = context,
                            trigger = SmartNotificationTrigger.RIVALS_DETHRONED,
                            message = dethroneMsg
                        )
                    }

                    // 3. User Passed a Squadmate -> Unlock 1-Click Pre-Set Taunt opportunity
                    if (myPrev.rankPosition > rivalPrev.rankPosition && myCurr.rankPosition < rivalCurr.rankPosition) {
                        val passKey = "${squad.id}_${currentUid}_passed_${rivalCurr.userId}_at_${myCurr.rankPosition}_${rivalCurr.gamerscore}"
                        if (!sentPassKeys.contains(passKey) && newPassOpportunities.none { it.passedMemberUid == rivalCurr.userId }) {
                            newPassOpportunities.add(
                                RivalsPendingPassTauntOpportunity(
                                    passEventKey = passKey,
                                    squadId = squad.id,
                                    passedMemberUid = rivalCurr.userId,
                                    passedMemberUsername = rivalCurr.username,
                                    passedMemberDisplayName = rivalName,
                                    newMyRankLabel = myCurr.rankLabel
                                )
                            )
                        }
                    }

                    // 4. The Activity Nudge: Rival added >= 180 minutes (3 hours) and is ahead or pulling ahead
                    val minutesAdded = rivalCurr.totalPlayTimeMinutes - rivalPrev.totalPlayTimeMinutes
                    if (minutesAdded >= RivalsRepository.THREE_HOUR_SESSION_MINUTES &&
                        rivalCurr.gamerscore >= myCurr.gamerscore
                    ) {
                        val actKey = "${squad.id}_${rivalCurr.userId}_3h_${rivalCurr.totalPlayTimeMinutes / 180L}"
                        if (!notifiedActivityKeys.contains(actKey)) {
                            notifiedActivityKeys.add(actKey)
                            prefs.edit().putStringSet(KEY_NOTIFIED_ACTIVITY_KEYS, notifiedActivityKeys).apply()
                            val nudgeMsg = RivalsRepository.buildActivityNudgeNotificationText(rivalName)
                            SmartNotificationManager.postRivalsNotification(
                                context = context,
                                trigger = SmartNotificationTrigger.RIVALS_ACTIVITY_NUDGE,
                                message = nudgeMsg
                            )
                        }
                    }
                }
            }
        }

        _state.update { prev ->
            prev.copy(
                leaderboard = rankedMembers,
                pendingPassTauntOpportunities = newPassOpportunities
            )
        }
    }

    private fun onIncomingTauntsSnapshot(context: Context, taunts: List<RivalsTaunt>) {
        val prefs = getPrefs(context)
        val notifiedIds = prefs.getStringSet(KEY_NOTIFIED_TAUNT_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        var changed = false

        for (taunt in taunts) {
            if (taunt.id.isNotBlank() && !notifiedIds.contains(taunt.id)) {
                notifiedIds.add(taunt.id)
                changed = true
                val senderLabel = taunt.senderDisplayName.ifBlank { taunt.senderUsername }.ifBlank { "Friend" }
                val msg = RivalsRepository.buildTauntReceivedNotificationText(
                    friendName = senderLabel,
                    preTaunt = taunt.tauntText
                )
                SmartNotificationManager.postRivalsNotification(
                    context = context,
                    trigger = SmartNotificationTrigger.RIVALS_TAUNT_RECEIVED,
                    message = msg
                )
            }
        }
        if (changed) {
            prefs.edit().putStringSet(KEY_NOTIFIED_TAUNT_IDS, notifiedIds).apply()
        }
        _state.update { it.copy(recentIncomingTaunts = taunts) }
    }

    // =========================================================================
    // User Actions (Create with Unity Rewarded Ad, Edit, Delete, Leave, Code Join, Search Invite, 1-Click Taunt)
    // =========================================================================

    fun createSquadAfterRewardedAd(
        context: Context,
        squadName: String,
        bio: String,
        leaderUsername: String,
        leaderDisplayName: String,
        repository: RivalsRepository = RivalsRepository(context.applicationContext),
        onResult: (Result<RivalsSquad>) -> Unit = {}
    ) {
        val appContext = context.applicationContext
        val cleanName = squadName.trim()
        val cleanBio = bio.trim()

        if (cleanName.isBlank() || cleanName.length > RivalsRepository.MAX_SQUAD_NAME_LENGTH) {
            val err = IllegalArgumentException(
                AppStrings.trText(
                    en = "Enter a Squad Name (1–40 characters).",
                    es = "Ingresa un nombre de escuadrón (1 a 40 caracteres)."
                )
            )
            _state.update { it.copy(statusMessage = err.message, isErrorStatus = true) }
            onResult(Result.failure(err))
            return
        }
        if (cleanBio.length > RivalsRepository.MAX_SQUAD_BIO_LENGTH) {
            val err = IllegalArgumentException(
                AppStrings.trText(
                    en = "Squad bio cannot exceed 150 characters.",
                    es = "La biografía del escuadrón no puede superar los 150 caracteres."
                )
            )
            _state.update { it.copy(statusMessage = err.message, isErrorStatus = true) }
            onResult(Result.failure(err))
            return
        }
        if (_state.value.activeSquad != null) {
            val err = IllegalStateException(
                AppStrings.trText(
                    en = "You are already in a Squad. Leave your current Squad first before creating a new one.",
                    es = "Ya estás en un escuadrón. Sal de tu escuadrón actual antes de crear uno nuevo."
                )
            )
            _state.update { it.copy(statusMessage = err.message, isErrorStatus = true) }
            onResult(Result.failure(err))
            return
        }

        // Play Unity Rewarded Ad before creating the squad
        UnityAdsManager.showRewardedAdForRivalsSquadCreation(
            context = context,
            onRewardEarned = {
                executeCreateSquadInFirestore(
                    appContext = appContext,
                    squadName = cleanName,
                    bio = cleanBio,
                    leaderUsername = leaderUsername,
                    leaderDisplayName = leaderDisplayName,
                    repository = repository,
                    onResult = onResult
                )
            }
        )
    }

    fun executeCreateSquadInFirestore(
        appContext: Context,
        squadName: String,
        bio: String,
        leaderUsername: String,
        leaderDisplayName: String,
        repository: RivalsRepository = RivalsRepository(appContext),
        onResult: (Result<RivalsSquad>) -> Unit = {}
    ) {
        _state.update { it.copy(isSubmitting = true, statusMessage = null, isErrorStatus = false) }
        managerScope.launch {
            val res = repository.createSquad(
                squadName = squadName,
                bio = bio,
                leaderUsername = leaderUsername,
                leaderDisplayName = leaderDisplayName
            )
            res.onSuccess { created ->
                getPrefs(appContext).edit().putBoolean(KEY_HAS_ACTIVE_SQUAD, true).apply()
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        activeSquad = created,
                        showInviteSquadPopup = false,
                        statusMessage = AppStrings.trText(
                            en = "Squad '${created.name}' created! Share code ${created.squadCode} with up to 4 friends.",
                            es = "¡Escuadrón '${created.name}' creado! Comparte el código ${created.squadCode} con hasta 4 amigos."
                        ),
                        isErrorStatus = false
                    )
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        statusMessage = err.message ?: AppStrings.trText(
                            en = "Could not create Squad.",
                            es = "No se pudo crear el escuadrón."
                        ),
                        isErrorStatus = true
                    )
                }
            }
            onResult(res)
        }
    }

    fun updateSquadDetailsAsLeader(
        context: Context,
        squadId: String,
        newName: String,
        newBio: String,
        repository: RivalsRepository = RivalsRepository(context.applicationContext),
        onResult: (Result<RivalsSquad>) -> Unit = {}
    ) {
        _state.update { it.copy(isSubmitting = true, statusMessage = null, isErrorStatus = false) }
        managerScope.launch {
            val res = repository.updateSquadDetails(squadId, newName, newBio)
            res.onSuccess { updated ->
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        activeSquad = updated,
                        statusMessage = AppStrings.trText(
                            en = "Squad name and bio updated in real time!",
                            es = "¡Nombre y biografía del escuadrón actualizados en tiempo real!"
                        ),
                        isErrorStatus = false
                    )
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        statusMessage = err.message ?: "Failed to update squad",
                        isErrorStatus = true
                    )
                }
            }
            onResult(res)
        }
    }

    fun joinSquadByCode(
        context: Context,
        rawCode: String,
        repository: RivalsRepository = RivalsRepository(context.applicationContext),
        onResult: (Result<RivalsSquad>) -> Unit = {}
    ) {
        val appContext = context.applicationContext
        _state.update { it.copy(isSubmitting = true, statusMessage = null, isErrorStatus = false) }
        managerScope.launch {
            val res = repository.joinSquadByCode(rawCode)
            res.onSuccess { joined ->
                getPrefs(appContext).edit().putBoolean(KEY_HAS_ACTIVE_SQUAD, true).apply()
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        activeSquad = joined,
                        showInviteSquadPopup = false,
                        statusMessage = AppStrings.trText(
                            en = "Joined Squad '${joined.name}' automatically!",
                            es = "¡Te uniste al escuadrón '${joined.name}' automáticamente!"
                        ),
                        isErrorStatus = false
                    )
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        statusMessage = err.message ?: "Could not join squad by code",
                        isErrorStatus = true
                    )
                }
            }
            onResult(res)
        }
    }

    fun searchPlayersToInvite(
        context: Context,
        query: String,
        accountRepository: UserAccountRepository = UserAccountRepository(context.applicationContext)
    ) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _state.update { it.copy(searchCandidates = emptyList(), isSearchingPlayers = false) }
            return
        }
        _state.update { it.copy(isSearchingPlayers = true) }
        managerScope.launch {
            val res = accountRepository.searchPlayersBySimilarity(trimmed, maxResults = 6)
            val currentMemberUids = _state.value.activeSquad?.memberUids?.toSet().orEmpty()
            val list = res.getOrDefault(emptyList()).filter { it.userId !in currentMemberUids }
            _state.update {
                it.copy(
                    searchCandidates = list,
                    isSearchingPlayers = false
                )
            }
        }
    }

    fun invitePlayerByUsernameAsLeader(
        context: Context,
        squadId: String,
        targetUsername: String,
        inviterUsername: String,
        inviterDisplayName: String,
        repository: RivalsRepository = RivalsRepository(context.applicationContext),
        onResult: (Result<RivalsSquadInvite>) -> Unit = {}
    ) {
        _state.update { it.copy(isSubmitting = true, statusMessage = null, isErrorStatus = false) }
        managerScope.launch {
            val res = repository.invitePlayerByUsername(
                squadId = squadId,
                targetUsernameOrQuery = targetUsername,
                inviterUsername = inviterUsername,
                inviterDisplayName = inviterDisplayName
            )
            res.onSuccess { inv ->
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        statusMessage = AppStrings.trText(
                            en = "Invite sent to @${inv.inviteeUsername}! They will join once they accept.",
                            es = "¡Invitación enviada a @${inv.inviteeUsername}! Se unirá cuando acepte."
                        ),
                        isErrorStatus = false
                    )
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        statusMessage = err.message ?: "Could not invite player",
                        isErrorStatus = true
                    )
                }
            }
            onResult(res)
        }
    }

    fun acceptSquadInvite(
        context: Context,
        inviteId: String,
        repository: RivalsRepository = RivalsRepository(context.applicationContext),
        onResult: (Result<RivalsSquad>) -> Unit = {}
    ) {
        val appContext = context.applicationContext
        _state.update { it.copy(isSubmitting = true, statusMessage = null, isErrorStatus = false) }
        managerScope.launch {
            val res = repository.acceptSquadInvite(inviteId)
            res.onSuccess { joined ->
                getPrefs(appContext).edit().putBoolean(KEY_HAS_ACTIVE_SQUAD, true).apply()
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        activeSquad = joined,
                        showInviteSquadPopup = false,
                        statusMessage = AppStrings.trText(
                            en = "Accepted invite! Welcome to '${joined.name}'.",
                            es = "¡Invitación aceptada! Bienvenido a '${joined.name}'."
                        ),
                        isErrorStatus = false
                    )
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        statusMessage = err.message ?: "Could not accept invite",
                        isErrorStatus = true
                    )
                }
            }
            onResult(res)
        }
    }

    fun declineSquadInvite(
        context: Context,
        inviteId: String,
        repository: RivalsRepository = RivalsRepository(context.applicationContext)
    ) {
        managerScope.launch {
            repository.declineSquadInvite(inviteId)
        }
    }

    fun leaveSquad(
        context: Context,
        squadId: String,
        repository: RivalsRepository = RivalsRepository(context.applicationContext),
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        val appContext = context.applicationContext
        _state.update { it.copy(isSubmitting = true, statusMessage = null, isErrorStatus = false) }
        managerScope.launch {
            val res = repository.leaveSquad(squadId)
            res.onSuccess {
                getPrefs(appContext).edit().putBoolean(KEY_HAS_ACTIVE_SQUAD, false).apply()
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        activeSquad = null,
                        leaderboard = emptyList(),
                        pendingPassTauntOpportunities = emptyList(),
                        statusMessage = AppStrings.trText(
                            en = "You left the Squad. You can now join another Squad or enter a Squad Code.",
                            es = "Saliste del escuadrón. Ahora puedes unirte a otro o ingresar un Código de Escuadrón."
                        ),
                        isErrorStatus = false
                    )
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        statusMessage = err.message ?: "Could not leave squad",
                        isErrorStatus = true
                    )
                }
            }
            onResult(res)
        }
    }

    fun deleteSquadAsLeader(
        context: Context,
        squadId: String,
        repository: RivalsRepository = RivalsRepository(context.applicationContext),
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        val appContext = context.applicationContext
        _state.update { it.copy(isSubmitting = true, statusMessage = null, isErrorStatus = false) }
        managerScope.launch {
            val res = repository.deleteSquad(squadId)
            res.onSuccess {
                getPrefs(appContext).edit().putBoolean(KEY_HAS_ACTIVE_SQUAD, false).apply()
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        activeSquad = null,
                        leaderboard = emptyList(),
                        pendingPassTauntOpportunities = emptyList(),
                        statusMessage = AppStrings.trText(
                            en = "Squad deleted from database.",
                            es = "Escuadrón eliminado de la base de datos."
                        ),
                        isErrorStatus = false
                    )
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        statusMessage = err.message ?: "Could not delete squad",
                        isErrorStatus = true
                    )
                }
            }
            onResult(res)
        }
    }

    /**
     * Sends a One-Click Pre-Set Taunt after passing a squadmate.
     * Strictly enforces 1 taunt per passing event (`passEventKey`).
     */
    fun sendOneClickPresetTaunt(
        context: Context,
        opportunity: RivalsPendingPassTauntOpportunity,
        senderUsername: String,
        senderDisplayName: String,
        presetTaunt: String,
        repository: RivalsRepository = RivalsRepository(context.applicationContext),
        onResult: (Result<RivalsTaunt>) -> Unit = {}
    ) {
        val appContext = context.applicationContext
        val prefs = getPrefs(appContext)
        val sentSet = prefs.getStringSet(KEY_SENT_PASS_TAUNT_KEYS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (sentSet.contains(opportunity.passEventKey)) {
            val err = IllegalStateException(
                AppStrings.trText(
                    en = "You already sent a taunt for passing ${opportunity.passedMemberDisplayName}.",
                    es = "Ya enviaste una burla al superar a ${opportunity.passedMemberDisplayName}."
                )
            )
            _state.update { it.copy(statusMessage = err.message, isErrorStatus = true) }
            onResult(Result.failure(err))
            return
        }

        managerScope.launch {
            val res = repository.sendPresetTaunt(
                squadId = opportunity.squadId,
                senderUsername = senderUsername,
                senderDisplayName = senderDisplayName,
                recipientUid = opportunity.passedMemberUid,
                recipientUsername = opportunity.passedMemberUsername,
                tauntText = presetTaunt,
                passEventKey = opportunity.passEventKey
            )
            res.onSuccess {
                sentSet.add(opportunity.passEventKey)
                prefs.edit().putStringSet(KEY_SENT_PASS_TAUNT_KEYS, sentSet).apply()
                _state.update { prev ->
                    prev.copy(
                        pendingPassTauntOpportunities = prev.pendingPassTauntOpportunities
                            .filterNot { it.passEventKey == opportunity.passEventKey },
                        statusMessage = AppStrings.trText(
                            en = "Sent taunt \"$presetTaunt\" to ${opportunity.passedMemberDisplayName}!",
                            es = "¡Burla \"$presetTaunt\" enviada a ${opportunity.passedMemberDisplayName}!"
                        ),
                        isErrorStatus = false
                    )
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        statusMessage = err.message ?: "Could not send taunt",
                        isErrorStatus = true
                    )
                }
            }
            onResult(res)
        }
    }

    fun clearStatusMessage() {
        _state.update { it.copy(statusMessage = null, isErrorStatus = false) }
    }

    fun copySquadCodeToClipboard(context: Context, squadCode: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Rivals Squad Code", squadCode))
            Toast.makeText(
                context.applicationContext,
                AppStrings.trText(
                    en = "Copied Squad Code: $squadCode",
                    es = "Código de Escuadrón copiado: $squadCode"
                ),
                Toast.LENGTH_SHORT
            ).show()
        } catch (_: Exception) {}
    }

    fun shareSquadCode(context: Context, squadName: String, squadCode: String) {
        copySquadCodeToClipboard(context, squadCode)
        val shareText = AppStrings.trText(
            en = "Join my Rivals Squad '$squadName' on GMAX Gaming Hub! Copy and enter Squad Code: $squadCode to compete for the 1st Place Crown!",
            es = "¡Únete a mi Escuadrón Rivals '$squadName' en GMAX Gaming Hub! Copia e ingresa el Código de Escuadrón: $squadCode para competir por la Corona del 1.º Lugar!"
        )
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(sendIntent, "Share Rivals Squad Code").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {}
    }
}
