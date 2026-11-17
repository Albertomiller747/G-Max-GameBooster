package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.data.repository.ReferralActivation
import com.example.data.repository.SquadMilestoneCatalog
import com.example.data.repository.SquadPassSummaryState
import com.example.data.repository.SquadReferralRepository
import com.example.service.CrosshairOverlayService
import com.example.service.GamingOverlayService
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

object SquadReferralManager {

    private const val PREFS_NAME = "gmax_squad_referral_prefs"
    private const val KEY_MY_CODE = "my_referral_code"
    private const val KEY_VALIDATED_COUNT = "validated_squadmates_count"
    private const val KEY_LOCAL_BOOSTED_MINUTES = "local_boosted_minutes_completed"
    private const val KEY_LOCAL_HUD_MATCH_DONE = "local_hud_match_completed"
    private const val KEY_INVITEE_REFERRER_CODE = "invitee_claimed_referrer_code"
    private const val KEY_INVITEE_REFERRER_USER = "invitee_claimed_referrer_username"
    private const val KEY_INVITEE_STATUS = "invitee_activation_status"
    private const val KEY_INVITEE_TRIGGER = "invitee_activation_trigger"
    private const val KEY_NOTIFIED_ACTIVATION_IDS = "notified_activation_ids"
    private const val KEY_EQUIPPED_CROSSHAIR_PACK = "equipped_crosshair_pack"
    private const val KEY_EQUIPPED_ADVANCED_HUD = "equipped_advanced_hud"
    private const val KEY_EQUIPPED_VANGUARD_BADGE = "equipped_vanguard_badge"
    private const val KEY_LAST_MINUTE_TICK_MS = "last_boosted_minute_tick_ms"

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + AppCrashGuard.coroutineExceptionHandler)
    private var referrerListenerJob: Job? = null
    private var inviteeListenerJob: Job? = null
    private var activeObservedUid: String? = null

    private val _state = MutableStateFlow(SquadPassSummaryState())
    val state: StateFlow<SquadPassSummaryState> = _state.asStateFlow()

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uiPrefs = appContext.getSharedPreferences("gmax_hub_ui_prefs", Context.MODE_PRIVATE)

        val currentUser = try {
            Firebase.auth.currentUser
        } catch (_: Throwable) {
            null
        }
        val fallbackUid = currentUser?.uid ?: uiPrefs.getString("hub_user_custom_username", null) ?: "LOCAL01"

        val fallbackUsername = uiPrefs.getString("hub_user_custom_username", null)
            ?.takeIf { it.isNotBlank() }
            ?: currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: "SquadLeader"

        val savedCode = prefs.getString(KEY_MY_CODE, null)
            ?.takeIf { it.isNotBlank() }
            ?: SquadReferralRepository.generateReferralCode(fallbackUid, fallbackUsername).also {
                prefs.edit().putString(KEY_MY_CODE, it).apply()
            }

        val validatedCount = prefs.getInt(KEY_VALIDATED_COUNT, 0).coerceAtLeast(0)
        val localMinutes = prefs.getLong(KEY_LOCAL_BOOSTED_MINUTES, 0L).coerceAtLeast(0L)
        val localHudMatch = prefs.getBoolean(KEY_LOCAL_HUD_MATCH_DONE, false)
        val claimedRefCode = prefs.getString(KEY_INVITEE_REFERRER_CODE, null)
        val claimedRefUser = prefs.getString(KEY_INVITEE_REFERRER_USER, "squad_leader").orEmpty()
        val claimedStatus = prefs.getString(KEY_INVITEE_STATUS, ReferralActivation.STATUS_PENDING)
            ?: ReferralActivation.STATUS_PENDING
        val claimedTrigger = prefs.getString(KEY_INVITEE_TRIGGER, ReferralActivation.TRIGGER_NONE)
            ?: ReferralActivation.TRIGGER_NONE

        // Only restore invitee activation when authenticated with Google
        val persistedInvitee = if (currentUser != null && !claimedRefCode.isNullOrBlank()) {
            ReferralActivation(
                id = currentUser.uid,
                inviteeUid = currentUser.uid,
                inviteeUsername = fallbackUsername,
                inviteeDisplayName = fallbackUsername,
                referrerUid = "referrer",
                referrerUsername = claimedRefUser,
                referrerCode = claimedRefCode,
                deviceFingerprintHash = DeviceFingerprintGenerator.getDeviceFingerprintHash(appContext),
                status = claimedStatus,
                boostedMinutesCompleted = localMinutes,
                hudMatchCompleted = localHudMatch,
                activationTrigger = claimedTrigger
            )
        } else {
            null
        }

        val unlocked = SquadMilestoneCatalog.computeUnlockedModules(validatedCount, persistedInvitee)
        val totalProDays = SquadMilestoneCatalog.computeTotalProDays(validatedCount, persistedInvitee)
        val passesLeft = SquadMilestoneCatalog.computeScarcityPassesRemaining(validatedCount)

        _state.value = SquadPassSummaryState(
            myReferralCode = savedCode,
            guestPassesRemaining = passesLeft,
            guestPassesMaxBatch = 3,
            validatedSquadmatesCount = validatedCount,
            pendingSquadmatesCount = 0,
            squadRoster = emptyList(),
            myInviteeActivation = persistedInvitee,
            inviteeStarterThemeUnlocked = persistedInvitee != null,
            invitee14DayProUnlocked = persistedInvitee?.isActivated == true,
            totalStackingProDaysEarned = totalProDays,
            isLifetimeProUnlocked = validatedCount >= 15,
            unlockedModuleKeys = unlocked,
            equippedCrosshairPack = prefs.getBoolean(KEY_EQUIPPED_CROSSHAIR_PACK, false) &&
                (unlocked.contains(SquadMilestoneCatalog.MODULE_CROSSHAIR_PACK) || unlocked.contains(SquadMilestoneCatalog.MODULE_STARTER_THEME)),
            equippedAdvancedHud = prefs.getBoolean(KEY_EQUIPPED_ADVANCED_HUD, false) &&
                unlocked.contains(SquadMilestoneCatalog.MODULE_ADVANCED_HUD),
            equippedConsoleVanguardBadge = prefs.getBoolean(KEY_EQUIPPED_VANGUARD_BADGE, false) &&
                unlocked.contains(SquadMilestoneCatalog.MODULE_CONSOLE_VANGUARD)
        )
    }

    /**
     * Starts observing Firestore real-time referral activations for an authenticated Google user.
     */
    fun syncAuthenticatedUser(
        context: Context,
        uid: String,
        username: String,
        displayName: String,
        repository: SquadReferralRepository = SquadReferralRepository(context.applicationContext)
    ) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val generatedCode = SquadReferralRepository.generateReferralCode(uid, username)
        val hwHash = DeviceFingerprintGenerator.getDeviceFingerprintHash(appContext)
        prefs.edit().putString(KEY_MY_CODE, generatedCode).apply()

        _state.update {
            it.copy(
                myReferralCode = generatedCode
            )
        }

        // Register/update user's referral code + hardware fingerprint in Firestore
        managerScope.launch {
            repository.registerOrUpdateMyReferralCode(
                username = username,
                displayName = displayName,
                validatedCount = _state.value.validatedSquadmatesCount.toLong(),
                passesRemaining = _state.value.guestPassesRemaining.toLong(),
                referrerDeviceHash = hwHash
            )
        }

        if (activeObservedUid == uid && referrerListenerJob?.isActive == true) return
        stopObserving()
        activeObservedUid = uid

        referrerListenerJob = managerScope.launch {
            repository.observeReferrerSquadActivations(uid)
                .catch { /* Handled via handleFirestoreError in repository */ }
                .collect { activations ->
                    onReferrerActivationsSnapshot(appContext, activations)
                }
        }

        inviteeListenerJob = managerScope.launch {
            repository.observeInviteeActivation(uid)
                .catch { /* Handled via handleFirestoreError in repository */ }
                .collect { activation ->
                    onInviteeActivationSnapshot(appContext, activation)
                }
        }
    }

    fun stopObserving() {
        referrerListenerJob?.cancel()
        referrerListenerJob = null
        inviteeListenerJob?.cancel()
        inviteeListenerJob = null
        activeObservedUid = null
    }

    private fun onReferrerActivationsSnapshot(
        context: Context,
        activations: List<ReferralActivation>
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val notifiedSet = prefs.getStringSet(KEY_NOTIFIED_ACTIVATION_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()

        val validatedList = activations.filter { it.isActivated }
        val pendingList = activations.filter { !it.isActivated }
        val validatedCount = validatedList.size
        prefs.edit().putInt(KEY_VALIDATED_COUNT, validatedCount).apply()

        var newDopamineToast: String? = null
        for (act in validatedList) {
            val notifyKey = "ref_${act.inviteeUid}"
            if (!notifiedSet.contains(notifyKey)) {
                notifiedSet.add(notifyKey)
                val rewardUnlocked = when {
                    validatedCount >= 10 -> AppStrings.trText("1 Year Pro Pass", "Pase Pro de 1 Año")
                    validatedCount >= 5 -> AppStrings.trText("Console Mode Module & Vanguard Badge", "Módulo de Consola e Insignia Vanguard")
                    validatedCount >= 3 -> AppStrings.trText("Advanced Performance HUD + 1 Month Pro", "HUD de Rendimiento Avanzado + 1 Mes Pro")
                    else -> AppStrings.trText("Custom Crosshair Pack + 7 Days Pro", "Paquete de Miras + 7 Días Pro")
                }
                val cleanMate = act.inviteeUsername.removePrefix("@").ifBlank { "Squadmate" }
                newDopamineToast = AppStrings.trText(
                    en = "Squadmate @$cleanMate connected! $rewardUnlocked Unlocked.",
                    es = "¡Compañero @$cleanMate conectado! $rewardUnlocked desbloqueado."
                )
                AppAnalyticsManager.logSquadReferralEvent(
                    context = context,
                    action = "referrer_reward_unlocked",
                    referralCode = act.referrerCode,
                    channelOrTrigger = act.activationTrigger,
                    validatedCount = validatedCount
                )
            }
        }
        prefs.edit().putStringSet(KEY_NOTIFIED_ACTIVATION_IDS, notifiedSet).apply()

        val currentInvitee = _state.value.myInviteeActivation
        val unlockedModules = SquadMilestoneCatalog.computeUnlockedModules(validatedCount, currentInvitee)
        val totalProDays = SquadMilestoneCatalog.computeTotalProDays(validatedCount, currentInvitee)
        val passesLeft = SquadMilestoneCatalog.computeScarcityPassesRemaining(activations.size)

        if (unlockedModules.contains(SquadMilestoneCatalog.MODULE_ADVANCED_HUD)) {
            unlockNeonHudTheme(context)
        }

        _state.update { prev ->
            prev.copy(
                validatedSquadmatesCount = validatedCount,
                pendingSquadmatesCount = pendingList.size,
                squadRoster = activations.sortedByDescending { it.isActivated },
                guestPassesRemaining = passesLeft,
                totalStackingProDaysEarned = totalProDays,
                isLifetimeProUnlocked = validatedCount >= 15,
                unlockedModuleKeys = unlockedModules,
                highDopamineToastMessage = newDopamineToast ?: prev.highDopamineToastMessage
            )
        }

        if (!newDopamineToast.isNullOrBlank()) {
            triggerHighDopamineOverlayAndToast(context, newDopamineToast)
        }
    }

    private fun onInviteeActivationSnapshot(
        context: Context,
        activation: ReferralActivation?
    ) {
        if (activation == null) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val notifiedSet = prefs.getStringSet(KEY_NOTIFIED_ACTIVATION_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()

        prefs.edit()
            .putString(KEY_INVITEE_REFERRER_CODE, activation.referrerCode)
            .putString(KEY_INVITEE_REFERRER_USER, activation.referrerUsername)
            .putString(KEY_INVITEE_STATUS, activation.status)
            .putString(KEY_INVITEE_TRIGGER, activation.activationTrigger)
            .putLong(KEY_LOCAL_BOOSTED_MINUTES, activation.boostedMinutesCompleted)
            .putBoolean(KEY_LOCAL_HUD_MATCH_DONE, activation.hudMatchCompleted)
            .apply()

        var newDopamineToast: String? = null
        val notifyKey = "inv_${activation.inviteeUid}_activated"
        if (activation.isActivated && !notifiedSet.contains(notifyKey)) {
            notifiedSet.add(notifyKey)
            prefs.edit().putStringSet(KEY_NOTIFIED_ACTIVATION_IDS, notifiedSet).apply()
            val cleanLeader = activation.referrerUsername.removePrefix("@").ifBlank { "SquadLeader" }
            newDopamineToast = AppStrings.trText(
                en = "Squadmate @$cleanLeader connected! 14-Day Pro Guest Pass & Custom Crosshair Pack Unlocked.",
                es = "¡Compañero @$cleanLeader conectado! Pase Pro de 14 Días y Paquete de Miras Desbloqueados."
            )
            unlockNeonHudTheme(context)
            AppAnalyticsManager.logSquadReferralEvent(
                context = context,
                action = "invitee_reward_unlocked",
                referralCode = activation.referrerCode,
                channelOrTrigger = activation.activationTrigger,
                validatedCount = _state.value.validatedSquadmatesCount
            )
        }

        val validatedCount = _state.value.validatedSquadmatesCount
        val unlockedModules = SquadMilestoneCatalog.computeUnlockedModules(validatedCount, activation)
        val totalProDays = SquadMilestoneCatalog.computeTotalProDays(validatedCount, activation)

        _state.update { prev ->
            prev.copy(
                myInviteeActivation = activation,
                inviteeStarterThemeUnlocked = true,
                invitee14DayProUnlocked = activation.isActivated,
                totalStackingProDaysEarned = totalProDays,
                unlockedModuleKeys = unlockedModules,
                highDopamineToastMessage = newDopamineToast ?: prev.highDopamineToastMessage
            )
        }

        if (!newDopamineToast.isNullOrBlank()) {
            triggerHighDopamineOverlayAndToast(context, newDopamineToast)
        }
    }

    fun claimGuestPassCode(
        context: Context,
        rawCodeOrLink: String,
        inviteeUsername: String,
        inviteeDisplayName: String,
        repository: SquadReferralRepository = SquadReferralRepository(context.applicationContext),
        onResult: (Result<ReferralActivation>) -> Unit = {}
    ) {
        val appContext = context.applicationContext

        // Anti-Cheat Gate 1: Require authenticated Google Sign-In
        val currentUser = try { Firebase.auth.currentUser } catch (_: Throwable) { null }
        if (currentUser == null) {
            val err = IllegalStateException(
                AppStrings.trText(
                    en = "Anti-Fraud Guard: Sign in with your Google Account first before claiming a Squad Referral Code.",
                    es = "Protección Antifraude: Inicia sesión con tu cuenta de Google antes de reclamar un Código de Escuadrón."
                )
            )
            _state.update { it.copy(statusBannerMessage = err.message) }
            onResult(Result.failure(err))
            return
        }

        // Anti-Cheat Gate 2: Reject URLs and require strict GMAX-USER-XXXX code format
        val normalized = SquadReferralRepository.normalizeReferralCode(rawCodeOrLink)
        if (normalized.isNullOrBlank()) {
            val err = IllegalArgumentException(
                AppStrings.trText(
                    en = "Enter a valid Squad Referral Code (e.g., GMAX-SQUAD-1234). URLs are not accepted.",
                    es = "Ingresa un Código de Escuadrón válido (ej. GMAX-SQUAD-1234). No se aceptan enlaces URL."
                )
            )
            _state.update { it.copy(statusBannerMessage = err.message) }
            onResult(Result.failure(err))
            return
        }

        // Anti-Cheat Gate 3: Block self-referral
        if (normalized.equals(_state.value.myReferralCode, ignoreCase = true)) {
            val err = IllegalArgumentException(
                AppStrings.trText(
                    en = "Anti-Fraud Guard: You cannot claim your own Squad Referral Code.",
                    es = "Protección Antifraude: No puedes reclamar tu propio Código de Escuadrón."
                )
            )
            _state.update { it.copy(statusBannerMessage = err.message) }
            onResult(Result.failure(err))
            return
        }

        val hwHash = DeviceFingerprintGenerator.getDeviceFingerprintHash(appContext)

        managerScope.launch {
            val res = repository.claimGuestPass(
                rawCodeOrLink = normalized,
                inviteeUsername = inviteeUsername,
                inviteeDisplayName = inviteeDisplayName,
                deviceFingerprintHash = hwHash
            )
            res.onSuccess { activation ->
                unlockNeonHudTheme(appContext)
                onInviteeActivationSnapshot(appContext, activation)
                _state.update {
                    it.copy(
                        statusBannerMessage = AppStrings.trText(
                            en = "Squad Code ${activation.referrerCode} linked! Starter Crosshair unlocked. Play 15m boosted or finish 1 HUD match to unlock 14 Days Pro.",
                            es = "¡Código ${activation.referrerCode} vinculado! Mira inicial desbloqueada. Juega 15m con impulso o completa 1 partida con HUD para desbloquear 14 Días Pro."
                        )
                    )
                }
                AppAnalyticsManager.logSquadReferralEvent(
                    context = appContext,
                    action = "guest_pass_claimed_pending",
                    referralCode = activation.referrerCode,
                    channelOrTrigger = "firestore_verified_hw"
                )
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        statusBannerMessage = err.message ?: AppStrings.trText(
                            en = "Could not claim Squad Referral Code.",
                            es = "No se pudo reclamar el Código de Escuadrón."
                        )
                    )
                }
            }
            onResult(res)
        }
    }

    /**
     * Called every 60 seconds during an active boosted game session.
     * Enforces minimum real-time interval between minute ticks to prevent rapid spoofed calls.
     */
    fun onBoostedGameplayMinuteElapsed(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentAct = _state.value.myInviteeActivation ?: return
        if (currentAct.isActivated) return

        val nowMs = System.currentTimeMillis()
        val lastTickMs = prefs.getLong(KEY_LAST_MINUTE_TICK_MS, 0L)
        if (lastTickMs > 0L && nowMs - lastTickMs < 45_000L) {
            return
        }
        prefs.edit().putLong(KEY_LAST_MINUTE_TICK_MS, nowMs).apply()

        val newMinutes = (currentAct.boostedMinutesCompleted + 1L).coerceAtMost(100000L)
        prefs.edit().putLong(KEY_LOCAL_BOOSTED_MINUTES, newMinutes).apply()

        val reached15Min = newMinutes >= 15L
        val updatedLocal = currentAct.copy(
            boostedMinutesCompleted = newMinutes,
            status = if (reached15Min) ReferralActivation.STATUS_ACTIVATED else currentAct.status,
            activationTrigger = if (reached15Min) ReferralActivation.TRIGGER_15M_BOOST else currentAct.activationTrigger
        )
        onInviteeActivationSnapshot(appContext, updatedLocal)

        val currentUser = try { Firebase.auth.currentUser } catch (_: Throwable) { null }
        if (currentUser != null) {
            managerScope.launch {
                SquadReferralRepository(appContext).recordVerifiedCoreActionProgress(
                    inviteeUsername = currentAct.inviteeUsername,
                    inviteeDisplayName = currentAct.inviteeDisplayName,
                    additionalBoostedMinutes = 1L,
                    completedHudMatch = false
                ).onSuccess { remoteUpdated ->
                    if (remoteUpdated != null) {
                        onInviteeActivationSnapshot(appContext, remoteUpdated)
                    }
                }
            }
        }
    }

    /**
     * Called when a player finishes a match in MatchRecapManager with the Floating Performance HUD active.
     */
    fun onHudMatchCompleted(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_LOCAL_HUD_MATCH_DONE, true).apply()

        val currentAct = _state.value.myInviteeActivation ?: return
        if (currentAct.isActivated) return

        val updatedLocal = currentAct.copy(
            hudMatchCompleted = true,
            status = ReferralActivation.STATUS_ACTIVATED,
            activationTrigger = ReferralActivation.TRIGGER_1_HUD_MATCH
        )
        onInviteeActivationSnapshot(appContext, updatedLocal)

        val currentUser = try { Firebase.auth.currentUser } catch (_: Throwable) { null }
        if (currentUser != null) {
            managerScope.launch {
                SquadReferralRepository(appContext).recordVerifiedCoreActionProgress(
                    inviteeUsername = currentAct.inviteeUsername,
                    inviteeDisplayName = currentAct.inviteeDisplayName,
                    additionalBoostedMinutes = 0L,
                    completedHudMatch = true
                ).onSuccess { remoteUpdated ->
                    if (remoteUpdated != null) {
                        onInviteeActivationSnapshot(appContext, remoteUpdated)
                    }
                }
            }
        }
    }

    fun dismissDopamineToast() {
        _state.update { it.copy(highDopamineToastMessage = null) }
    }

    fun onUserSignedOut(context: Context) {
        referrerListenerJob?.cancel()
        referrerListenerJob = null
        inviteeListenerJob?.cancel()
        inviteeListenerJob = null
        activeObservedUid = null
        initialize(context)
    }

    fun clearStatusBanner() {
        _state.update { it.copy(statusBannerMessage = null) }
    }

    fun toggleEquipCrosshairPack(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        val unlocked = _state.value.unlockedModuleKeys
        if (!unlocked.contains(SquadMilestoneCatalog.MODULE_CROSSHAIR_PACK) &&
            !unlocked.contains(SquadMilestoneCatalog.MODULE_STARTER_THEME)
        ) {
            return
        }
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_EQUIPPED_CROSSHAIR_PACK, enabled).apply()
        if (enabled) {
            CrosshairOverlayService.saveConfig(
                context = appContext,
                enabled = true,
                style = 2,
                color = 0xFF00E5FFL,
                sizeDp = 26,
                alpha = 0.95f
            )
        }
        _state.update { it.copy(equippedCrosshairPack = enabled) }
    }

    fun toggleEquipAdvancedHud(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        if (!_state.value.unlockedModuleKeys.contains(SquadMilestoneCatalog.MODULE_ADVANCED_HUD)) return
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_EQUIPPED_ADVANCED_HUD, enabled).apply()
        unlockNeonHudTheme(appContext)
        _state.update { it.copy(equippedAdvancedHud = enabled) }
    }

    fun toggleEquipConsoleVanguardBadge(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        if (!_state.value.unlockedModuleKeys.contains(SquadMilestoneCatalog.MODULE_CONSOLE_VANGUARD)) return
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_EQUIPPED_VANGUARD_BADGE, enabled).apply()
        _state.update { it.copy(equippedConsoleVanguardBadge = enabled) }
    }

    private fun unlockNeonHudTheme(context: Context) {
        try {
            val hudPrefs = context.getSharedPreferences("gmax_hud_prefs", Context.MODE_PRIVATE)
            hudPrefs.edit()
                .putBoolean("hud_streak_neon_unlocked", true)
                .putString("hud_streak_neon_hex", "#00E676")
                .apply()
        } catch (_: Exception) {}
    }

    private fun triggerHighDopamineOverlayAndToast(context: Context, message: String) {
        try {
            GamingOverlayService.showInGameSquadToast(context, message)
        } catch (_: Exception) {}
        try {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext, "⚡ $message", Toast.LENGTH_LONG).show()
            }
        } catch (_: Exception) {}
    }

    /**
     * Shares a code-only Squad Guest Pass directly to Discord, WhatsApp, or the system share sheet.
     */
    fun shareGuestPass(
        context: Context,
        referrerUsername: String,
        targetChannelPackage: String? = null,
        gameTitle: String? = null
    ) {
        val currentState = _state.value
        val code = currentState.myReferralCode.ifBlank { "GMAX-SQUAD-0001" }
        val shareText = SquadReferralRepository.buildGuestPassShareCopy(
            referrerUsername = referrerUsername,
            code = code,
            passesRemaining = currentState.guestPassesRemaining,
            gameTitle = gameTitle
        )

        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("GMax Squad Referral Code", code))
        } catch (_: Exception) {}

        val channelLabel = when (targetChannelPackage) {
            "com.discord" -> "discord"
            "com.whatsapp" -> "whatsapp"
            else -> "system_share"
        }
        AppAnalyticsManager.logSquadReferralEvent(
            context = context,
            action = "guest_pass_shared",
            referralCode = code,
            channelOrTrigger = channelLabel,
            validatedCount = currentState.validatedSquadmatesCount
        )

        try {
            val baseIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    AppStrings.trText(
                        en = "GMAX Gaming Hub 14-Day Pro Squad Code",
                        es = "Código de Escuadrón Pro de 14 Días • GMAX Gaming Hub"
                    )
                )
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (!targetChannelPackage.isNullOrBlank()) {
                val targetedIntent = Intent(baseIntent).apply {
                    setPackage(targetChannelPackage)
                }
                try {
                    context.startActivity(targetedIntent)
                    return
                } catch (_: Exception) {
                    // Target app not installed; fall back to chooser
                }
            }

            val chooser = Intent.createChooser(
                baseIntent,
                AppStrings.trText(
                    en = "Share Squad Referral Code",
                    es = "Compartir Código de Escuadrón"
                )
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {}
    }

    /**
     * Copies strictly the `GMAX-USER-XXXX` referral code to the clipboard (no URLs).
     */
    fun copyGuestPassLinkToClipboard(context: Context, referrerUsername: String): String {
        val currentState = _state.value
        val code = currentState.myReferralCode.ifBlank { "GMAX-SQUAD-0001" }
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("GMax Squad Referral Code", code))
            val toastMsg = AppStrings.trText(
                en = "Copied Squad Referral Code: $code",
                es = "Código de Escuadrón copiado: $code"
            )
            Toast.makeText(
                context.applicationContext,
                toastMsg,
                Toast.LENGTH_SHORT
            ).show()
        } catch (_: Exception) {}

        AppAnalyticsManager.logSquadReferralEvent(
            context = context,
            action = "guest_pass_copied",
            referralCode = code,
            channelOrTrigger = "clipboard",
            validatedCount = currentState.validatedSquadmatesCount
        )
        return code
    }
}
