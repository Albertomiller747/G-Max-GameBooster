package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AdbCommandsRepository
import com.example.data.GameGuidanceDatabase
import com.example.data.repository.UserAccountRepository
import com.example.data.repository.UserCloudProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import com.example.model.AdbCommandTweak
import com.example.model.AppBackgroundTheme
import com.example.model.BenchmarkRun
import com.example.model.BoostMode
import com.example.model.DeviceReadiness
import com.example.model.GameGuidance
import com.example.model.GamepadDevice
import com.example.model.InstalledApp
import com.example.model.MemoryStats
import com.example.model.NetworkPingResult
import com.example.model.MatchRecap
import com.example.model.OverlayHudConfig
import com.example.model.OverlaySettings
import com.example.model.PhoneHardwareSpecs
import com.example.model.ThermalInfo
import com.example.service.GamingOverlayService
import com.example.util.BenchmarkEngine
import com.example.util.ControllerHealthManager
import com.example.util.ControllerHealthState
import com.example.util.GamepadDetector
import com.example.util.MatchRecapManager
import com.example.util.MemoryHelper
import com.example.util.NetworkTester
import com.example.util.PriorityDndHelper
import com.example.util.StorageSpeedTester
import com.example.util.SystemSettingsHelper
import com.example.util.ThermalGuardState
import com.example.util.ThermalMonitor
import com.example.util.ThermalThrottleGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GameBoosterViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val memoryHelper = MemoryHelper(context)
    private val thermalMonitor = ThermalMonitor(context)
    private val networkTester = NetworkTester(context)
    private val storageSpeedTester = StorageSpeedTester(context)
    private val gamepadDetector = GamepadDetector(context)
    private val benchmarkEngine = BenchmarkEngine()
    val boosterSessionManager = com.example.util.BoosterSessionManager(context)

    val matchRecapManager = MatchRecapManager.getInstance(context)
    val matchRecap: StateFlow<MatchRecap> = matchRecapManager.latestRecap
    val isMatchRecording: StateFlow<Boolean> = matchRecapManager.isRecording

    val thermalGuard = ThermalThrottleGuard(context)
    val thermalGuardState: StateFlow<ThermalGuardState> = thermalGuard.state

    val controllerHealthManager = ControllerHealthManager(context)
    val controllerHealthState: StateFlow<ControllerHealthState> = controllerHealthManager.state

    data class BoosterSessionUiState(
        val isActive: Boolean = false,
        val mode: BoostMode? = null,
        val remainingMillis: Long = 0L,
        val formattedCountdown: String = "00:00:00",
        val targetGamePackage: String? = null,
        val targetGameName: String? = null
    )

    private val _sessionUiState = MutableStateFlow(BoosterSessionUiState())
    val sessionUiState: StateFlow<BoosterSessionUiState> = _sessionUiState.asStateFlow()

    private val _isGamePickerVisible = MutableStateFlow(false)
    val isGamePickerVisible: StateFlow<Boolean> = _isGamePickerVisible.asStateFlow()

    private val _selectedTargetGame = MutableStateFlow<InstalledApp?>(null)
    val selectedTargetGame: StateFlow<InstalledApp?> = _selectedTargetGame.asStateFlow()

    private val _focusedGamePackage = MutableStateFlow<String?>(null)
    val focusedGamePackage: StateFlow<String?> = _focusedGamePackage.asStateFlow()

    fun setFocusedGamePackage(packageName: String?) {
        _focusedGamePackage.value = packageName
        if (!packageName.isNullOrBlank()) {
            val match = _libraryGames.value.firstOrNull { it.packageName == packageName }
                ?: _installedApps.value.firstOrNull { it.packageName == packageName }
            if (match != null) {
                _selectedTargetGame.value = match
                boosterSessionManager.setLastTargetGame(match.packageName, match.appName)
                com.example.util.EfpsEngine.setActiveGamePackage(match.packageName)
            }
        }
    }

    private val _memoryStats = MutableStateFlow(MemoryStats())
    val memoryStats: StateFlow<MemoryStats> = _memoryStats.asStateFlow()

    val thermalInfo: StateFlow<ThermalInfo> = thermalMonitor.thermalInfo

    private val _wifiStatus = MutableStateFlow(NetworkTester.WifiStatus())
    val wifiStatus: StateFlow<NetworkTester.WifiStatus> = _wifiStatus.asStateFlow()

    private val _pingResults = MutableStateFlow<List<NetworkPingResult>>(emptyList())
    val pingResults: StateFlow<List<NetworkPingResult>> = _pingResults.asStateFlow()

    private val _isPingTesting = MutableStateFlow(false)
    val isPingTesting: StateFlow<Boolean> = _isPingTesting.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _libraryPackages = MutableStateFlow<Set<String>>(emptySet())
    val libraryPackages: StateFlow<Set<String>> = _libraryPackages.asStateFlow()

    private val _libraryGames = MutableStateFlow<List<InstalledApp>>(emptyList())
    val libraryGames: StateFlow<List<InstalledApp>> = _libraryGames.asStateFlow()

    private val _recentlyPlayedGames = MutableStateFlow<List<com.example.util.GamePlayStats>>(emptyList())
    val recentlyPlayedGames: StateFlow<List<com.example.util.GamePlayStats>> = _recentlyPlayedGames.asStateFlow()

    private val _mostPlayedGames = MutableStateFlow<List<com.example.util.GamePlayStats>>(emptyList())
    val mostPlayedGames: StateFlow<List<com.example.util.GamePlayStats>> = _mostPlayedGames.asStateFlow()

    private val _totalPlayTimeSummary = MutableStateFlow(com.example.util.TotalPlayTimeSummary())
    val totalPlayTimeSummary: StateFlow<com.example.util.TotalPlayTimeSummary> = _totalPlayTimeSummary.asStateFlow()

    private val _isBigScreenModeManual = MutableStateFlow(false)
    val isBigScreenModeManual: StateFlow<Boolean> = _isBigScreenModeManual.asStateFlow()

    private val _suppressAutoLandscapeConsole = MutableStateFlow(false)
    val suppressAutoLandscapeConsole: StateFlow<Boolean> = _suppressAutoLandscapeConsole.asStateFlow()

    private val _showConsoleStartupAnimation = MutableStateFlow(false)
    val showConsoleStartupAnimation: StateFlow<Boolean> = _showConsoleStartupAnimation.asStateFlow()

    fun setBigScreenModeManual(enabled: Boolean) {
        _isBigScreenModeManual.value = enabled
        if (enabled) {
            _suppressAutoLandscapeConsole.value = false
            _showConsoleStartupAnimation.value = true
        } else {
            _suppressAutoLandscapeConsole.value = true
            _showConsoleStartupAnimation.value = false
        }
        com.example.util.ConsoleHapticManager.playSelectClick(context)
    }

    fun exitConsoleModeImmediately() {
        _isBigScreenModeManual.value = false
        _suppressAutoLandscapeConsole.value = true
        _showConsoleStartupAnimation.value = false
        com.example.util.ConsoleHapticManager.playSelectClick(context)
    }

    fun onLandscapeConsoleEnteredAutomatically() {
        // Console startup animation is only triggered when the user explicitly taps the Console Mode button
    }

    fun clearSuppressAutoLandscapeConsole() {
        _suppressAutoLandscapeConsole.value = false
    }

    fun completeConsoleStartupAnimation() {
        _showConsoleStartupAnimation.value = false
    }

    val gameGuidanceList: List<GameGuidance> = GameGuidanceDatabase.gamesList
    val adbTweaks: List<AdbCommandTweak> = AdbCommandsRepository.tweaksList

    private val _gamepads = MutableStateFlow<List<GamepadDevice>>(emptyList())
    val gamepads: StateFlow<List<GamepadDevice>> = _gamepads.asStateFlow()

    private val _controllerAlert = MutableStateFlow<com.example.util.ControllerConnectionAlert?>(null)
    val controllerAlert: StateFlow<com.example.util.ControllerConnectionAlert?> = _controllerAlert.asStateFlow()

    fun dismissControllerAlert() {
        _controllerAlert.value = null
    }

    private val _storageSpeed = MutableStateFlow<StorageSpeedTester.StorageSpeedResult?>(null)
    val storageSpeed: StateFlow<StorageSpeedTester.StorageSpeedResult?> = _storageSpeed.asStateFlow()

    private val _isSpeedTesting = MutableStateFlow(false)
    val isSpeedTesting: StateFlow<Boolean> = _isSpeedTesting.asStateFlow()

    val efpsTelemetry: StateFlow<com.example.util.EfpsTelemetryState> = com.example.util.EfpsEngine.telemetry

    fun refreshEfpsEngine() {
        com.example.util.EfpsEngine.refreshDisplayHz()
    }

    private val _gamingSessionActive = MutableStateFlow(false)
    val gamingSessionActive: StateFlow<Boolean> = _gamingSessionActive.asStateFlow()

    private val _overlaySettings = MutableStateFlow(OverlaySettings())
    val overlaySettings: StateFlow<OverlaySettings> = _overlaySettings.asStateFlow()

    private val _readiness = MutableStateFlow(DeviceReadiness())
    val readiness: StateFlow<DeviceReadiness> = _readiness.asStateFlow()

    private val _isBoosting = MutableStateFlow(false)
    val isBoosting: StateFlow<Boolean> = _isBoosting.asStateFlow()

    private val _boostFeedback = MutableStateFlow<String?>(null)
    val boostFeedback: StateFlow<String?> = _boostFeedback.asStateFlow()

    private val _appliedOptionIds = MutableStateFlow<Set<String>>(setOf("pop_3_ram", "net_wifi_5g"))
    val appliedOptionIds: StateFlow<Set<String>> = _appliedOptionIds.asStateFlow()

    // Default to 1h 30m profile (BoostMode.BEAST)
    private val _selectedBoostMode = MutableStateFlow(BoostMode.BEAST)
    val selectedBoostMode: StateFlow<BoostMode> = _selectedBoostMode.asStateFlow()

    fun setBoostMode(@Suppress("UNUSED_PARAMETER") mode: BoostMode) {
        _selectedBoostMode.value = BoostMode.BEAST
        hubPrefs.edit().putString("hub_selected_boost_mode", BoostMode.BEAST.name).apply()
        scheduleCloudSettingsSync()
    }

    // Phone Hardware Specifications
    private val _hardwareSpecs = MutableStateFlow(PhoneHardwareSpecs())
    val hardwareSpecs: StateFlow<PhoneHardwareSpecs> = _hardwareSpecs.asStateFlow()

    // Real-time Screen Overlay HUD Config (FPS, Temp, Battery, Net Speed KB/s)
    private val _overlayConfig = MutableStateFlow(OverlayHudConfig())
    val overlayConfig: StateFlow<OverlayHudConfig> = _overlayConfig.asStateFlow()

    private val hubPrefs = context.getSharedPreferences("gmax_hub_ui_prefs", Context.MODE_PRIVATE)

    // Background Themes (Defaults to AUTO_SYSTEM)
    private val _appTheme = MutableStateFlow(
        try {
            val saved = hubPrefs.getString("hub_theme_mode", AppBackgroundTheme.AUTO_SYSTEM.name)
            AppBackgroundTheme.valueOf(saved ?: AppBackgroundTheme.AUTO_SYSTEM.name)
        } catch (_: Exception) {
            AppBackgroundTheme.AUTO_SYSTEM
        }
    )
    val appTheme: StateFlow<AppBackgroundTheme> = _appTheme.asStateFlow()

    private val _customBackgroundColor = MutableStateFlow(
        hubPrefs.getLong("hub_custom_bg_color", 0xFF121417L)
    )
    val customBackgroundColor: StateFlow<Long> = _customBackgroundColor.asStateFlow()

    private val _hasCompletedOnboarding = MutableStateFlow(
        com.example.util.PersistentStorageHelper.hasCompletedOnboarding(context)
    )
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    val appLanguageMode: StateFlow<com.example.util.AppLanguage> = com.example.util.AppStrings.selectedLanguageMode

    fun setAppLanguageMode(mode: com.example.util.AppLanguage) {
        com.example.util.AppStrings.setLanguageMode(context, mode)
    }

    // Firebase Auth & Cloud Settings Sync state
    private val userAccountRepository: UserAccountRepository? = try {
        UserAccountRepository(context)
    } catch (_: Exception) {
        null
    }

    private val _currentFirebaseUser = MutableStateFlow<FirebaseUser?>(
        try { Firebase.auth.currentUser } catch (_: Exception) { null }
    )
    val currentFirebaseUser: StateFlow<FirebaseUser?> = _currentFirebaseUser.asStateFlow()
    val currentUser: StateFlow<FirebaseUser?> get() = currentFirebaseUser

    fun getSavedCustomUsername(): String = _userUsername.value

    private val _socialSignedInProvider = MutableStateFlow(
        hubPrefs.getString("hub_social_auth_provider", "") ?: ""
    )
    val socialSignedInProvider: StateFlow<String> = _socialSignedInProvider.asStateFlow()

    // Tracks whether the user has completed the post-splash Sign In / Create Account step
    private val _hasCompletedAuthStep = MutableStateFlow(
        (_currentFirebaseUser.value != null) ||
            _socialSignedInProvider.value.isNotBlank() ||
            hubPrefs.getBoolean("hub_auth_step_completed", false)
    )
    val hasCompletedAuthStep: StateFlow<Boolean> = _hasCompletedAuthStep.asStateFlow()

    private val _cloudProfile = MutableStateFlow<UserCloudProfile?>(null)
    val cloudProfile: StateFlow<UserCloudProfile?> = _cloudProfile.asStateFlow()

    private val _cloudSyncStatus = MutableStateFlow(
        when {
            _currentFirebaseUser.value != null -> "Cloud Sync Active"
            _socialSignedInProvider.value.isNotBlank() -> "Signed in with ${_socialSignedInProvider.value} • Profile Active"
            else -> "Guest Mode (Local Storage Only)"
        }
    )
    val cloudSyncStatus: StateFlow<String> = _cloudSyncStatus.asStateFlow()

    private val _isCloudSyncing = MutableStateFlow(false)
    val isCloudSyncing: StateFlow<Boolean> = _isCloudSyncing.asStateFlow()

    private val _userDateOfBirth = MutableStateFlow(
        hubPrefs.getString("hub_user_dob", "Not Set") ?: "Not Set"
    )
    val userDateOfBirth: StateFlow<String> = _userDateOfBirth.asStateFlow()

    private val _userBio = MutableStateFlow(
        hubPrefs.getString("hub_user_bio", "") ?: ""
    )
    val userBio: StateFlow<String> = _userBio.asStateFlow()

    private val _customDisplayName = MutableStateFlow(
        hubPrefs.getString("hub_user_custom_name", "") ?: ""
    )
    val customDisplayName: StateFlow<String> = _customDisplayName.asStateFlow()

    private val _userUsername = MutableStateFlow(
        hubPrefs.getString("hub_user_username", "") ?: ""
    )
    val userUsername: StateFlow<String> = _userUsername.asStateFlow()

    private val _userCountryCode = MutableStateFlow(
        hubPrefs.getString("hub_user_country_code", null)
            ?.takeIf { it.isNotBlank() }
            ?: com.example.util.CountryCatalog.getDefaultCountry().code
    )
    val userCountryCode: StateFlow<String> = _userCountryCode.asStateFlow()

    private val _userCountryName = MutableStateFlow(
        hubPrefs.getString("hub_user_country_name", null)
            ?.takeIf { it.isNotBlank() }
            ?: com.example.util.CountryCatalog.getDefaultCountry().name
    )
    val userCountryName: StateFlow<String> = _userCountryName.asStateFlow()

    private val _isCheckingUsername = MutableStateFlow(false)
    val isCheckingUsername: StateFlow<Boolean> = _isCheckingUsername.asStateFlow()

    private val _usernameError = MutableStateFlow<String?>(null)
    val usernameError: StateFlow<String?> = _usernameError.asStateFlow()

    private val _customEmail = MutableStateFlow(
        hubPrefs.getString("hub_user_custom_email", "") ?: ""
    )
    val customEmail: StateFlow<String> = _customEmail.asStateFlow()

    private val _selectedProfileTags = MutableStateFlow<List<String>>(
        com.example.util.PremadeTagsCatalog.sanitizeSelectedTags(
            (hubPrefs.getString("hub_user_selected_tags_csv", "") ?: "")
                .split(",")
                .filter { it.isNotBlank() }
        )
    )
    val selectedProfileTags: StateFlow<List<String>> = _selectedProfileTags.asStateFlow()

    private val _isVerifiedDeveloperAccount = MutableStateFlow(
        com.example.util.PremadeTagsCatalog.isOfficialDeveloperEmail(
            _currentFirebaseUser.value?.email ?: (hubPrefs.getString("hub_user_custom_email", "") ?: "")
        )
    )
    val isVerifiedDeveloperAccount: StateFlow<Boolean> = _isVerifiedDeveloperAccount.asStateFlow()

    fun getAccountKey(email: String? = null, uid: String? = null): String {
        val raw = email?.takeIf { it.isNotBlank() }
            ?: uid?.takeIf { it.isNotBlank() }
            ?: _currentFirebaseUser.value?.email
            ?: _currentFirebaseUser.value?.uid
            ?: _customEmail.value.takeIf { it.isNotBlank() }
            ?: "guest"
        return raw.lowercase().trim().replace("[^a-zA-Z0-9_]".toRegex(), "_")
    }

    private val _userAvatarUrl = MutableStateFlow(
        hubPrefs.getString("hub_avatar_${getAccountKey()}", "") ?: ""
    )
    val userAvatarUrl: StateFlow<String> = _userAvatarUrl.asStateFlow()

    private val _userBannerUrl = MutableStateFlow(
        hubPrefs.getString("hub_banner_${getAccountKey()}", "cyber_neon_grid") ?: "cyber_neon_grid"
    )
    val userBannerUrl: StateFlow<String> = _userBannerUrl.asStateFlow()

    private fun reloadAccountMedia(user: FirebaseUser?) {
        val key = getAccountKey(user?.email, user?.uid)
        val savedAvatar = hubPrefs.getString("hub_avatar_$key", "") ?: ""
        val savedBanner = hubPrefs.getString("hub_banner_$key", "cyber_neon_grid") ?: "cyber_neon_grid"
        val savedBio = hubPrefs.getString("hub_bio_$key", "") ?: ""
        val savedUsername = hubPrefs.getString("hub_username_$key", "") ?: ""
        _userAvatarUrl.value = if (savedAvatar.isNotBlank()) savedAvatar else (user?.photoUrl?.toString() ?: "")
        _userBannerUrl.value = savedBanner
        if (savedBio.isNotBlank() || key != "guest") {
            _userBio.value = savedBio
        }
        if (savedUsername.isNotBlank()) {
            _userUsername.value = savedUsername
        }
        try {
            com.example.util.ConfigCodeCodec.setActiveAccount(context, key)
            com.example.service.CrosshairOverlayService.setActiveAccount(context, key)
            _overlaySettings.value = com.example.service.CrosshairOverlayService.getSavedConfig(context, key)
            com.example.util.DailyStreakManager.switchAccount(context, key)
            com.example.util.GamePlayStatsHelper.setActiveAccountKey(context, key)
            refreshPlayStats()
        } catch (_: Exception) {}
    }

    /**
     * Switches the active account context for HUD, Crosshair, and Base64 Config Code isolation.
     */
    fun switchAccountLoadout(accountKey: String, usernameHandle: String = "") {
        val cleanKey = com.example.util.ConfigCodeCodec.sanitizeAccountKey(accountKey)
        com.example.util.ConfigCodeCodec.setActiveAccount(context, cleanKey)
        com.example.service.CrosshairOverlayService.setActiveAccount(context, cleanKey)
        if (usernameHandle.isNotBlank()) {
            _userUsername.value = usernameHandle.removePrefix("@")
            hubPrefs.edit().putString("hub_username_$cleanKey", _userUsername.value).apply()
        }
        _overlaySettings.value = com.example.service.CrosshairOverlayService.getSavedConfig(context, cleanKey)
    }

    private val _customDeletionUrl = MutableStateFlow(
        hubPrefs.getString("hub_custom_deletion_url", "") ?: ""
    )
    val customDeletionUrl: StateFlow<String> = _customDeletionUrl.asStateFlow()

    fun setCustomDeletionUrl(url: String) {
        val trimmed = url.trim()
        hubPrefs.edit().putString("hub_custom_deletion_url", trimmed).apply()
        _customDeletionUrl.value = trimmed
    }

    // 14-Day Season Cycle for Gamerscore halving
    private val _seasonHalvingCount = MutableStateFlow(
        run {
            com.example.util.SeasonLeaderboardEngine.synchronizeSeasonCycle(context)
            com.example.util.SeasonLeaderboardEngine.getHalvingCount(context)
        }
    )
    val seasonHalvingCount: StateFlow<Int> = _seasonHalvingCount.asStateFlow()

    private val _antiCheatRevision = MutableStateFlow(0)
    private val _antiCheatReport = MutableStateFlow(
        com.example.util.LeaderboardAntiCheatEngine.getAntiCheatStatusReport(context)
    )
    val antiCheatReport: StateFlow<com.example.util.AntiCheatStatusReport> = _antiCheatReport.asStateFlow()

    fun refreshAntiCheatReport() {
        _antiCheatReport.value = com.example.util.LeaderboardAntiCheatEngine.getAntiCheatStatusReport(context)
    }

    /**
     * Credits a verified competitive grind session subject to the 4-layer Anti-Cheat Engine
     * (hourly 450 GS cap, daily 2,400 GS cap, anti-macro throttle, and monotonic clock check).
     * Immediately updates Gamerscore in real time so players can overtake competitors on the Leaderboard.
     */
    fun recordVerifiedGrindSession(requestedGsGain: Long = 160L): Pair<Boolean, String> {
        val result = com.example.util.LeaderboardAntiCheatEngine.recordVerifiedGrindSession(
            context = context,
            requestedGsGain = requestedGsGain
        )
        _antiCheatRevision.value += 1
        _antiCheatReport.value = com.example.util.LeaderboardAntiCheatEngine.getAntiCheatStatusReport(context)
        if (result.first) {
            scheduleCloudSettingsSync()
        }
        return result
    }

    val gamerscoreData: StateFlow<com.example.util.GamerscoreData> = combine(
        _totalPlayTimeSummary,
        _cloudProfile,
        _seasonHalvingCount,
        _antiCheatRevision,
        _customEmail
    ) { summary, cloud, halvings, _, localEmail ->
        val rawMins = maxOf(summary.totalMinutes, cloud?.totalPlayTimeMinutes ?: 0L)
        val rawSessions = maxOf(summary.totalSessions.toLong(), cloud?.totalSessionsCount ?: 0L)
        val (verifiedMins, verifiedSessions) = com.example.util.LeaderboardAntiCheatEngine.clampVerifiedPlayStats(
            context = context,
            rawTotalMinutes = rawMins,
            rawTotalSessions = rawSessions
        )
        val computed = com.example.util.GamerscoreEngine.calculateGamerscore(verifiedMins, verifiedSessions, halvings)
        val activeEmail = _currentFirebaseUser.value?.email?.takeIf { it.isNotBlank() }
            ?: cloud?.email?.takeIf { it.isNotBlank() }
            ?: localEmail
        val giftScoreFloor = if (com.example.util.AlbertoGiftManager.isTargetGiftRecipient(activeEmail)) {
            com.example.util.AlbertoGiftManager.GIFT_GAMERSCORE
        } else {
            0L
        }
        val effectiveCloudOrGiftScore = maxOf(cloud?.gamerscore ?: 0L, giftScoreFloor)
        if (effectiveCloudOrGiftScore > computed.totalScore) {
            com.example.util.GamerscoreEngine.calculateGamerscoreFromVerifiedScore(effectiveCloudOrGiftScore)
        } else {
            computed
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        run {
            val (verifiedMins, verifiedSessions) = com.example.util.LeaderboardAntiCheatEngine.clampVerifiedPlayStats(
                context = context,
                rawTotalMinutes = 0L,
                rawTotalSessions = 0L
            )
            com.example.util.GamerscoreEngine.calculateGamerscore(
                verifiedMins,
                verifiedSessions,
                _seasonHalvingCount.value
            )
        }
    )

    fun get7DayGamingHistory(): List<com.example.util.DailyPlayTime> {
        val currentMins = maxOf(_totalPlayTimeSummary.value.totalMinutes, _cloudProfile.value?.totalPlayTimeMinutes ?: 0L)
        return com.example.util.GamerscoreEngine.get7DayGamingHistory(context, currentMins)
    }

    private var profileObserverJob: Job? = null
    private var cloudSyncDebounceJob: Job? = null
    private var isApplyingCloudSettings = false

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        _currentFirebaseUser.value = user
        if (user != null) {
            _hasCompletedAuthStep.value = true
            hubPrefs.edit().putBoolean("hub_auth_step_completed", true).apply()
            reloadAccountMedia(user)
            if (com.example.util.PremadeTagsCatalog.isOfficialDeveloperEmail(user.email)) {
                ensurePrecreatedDeveloperAccountSeeded(user.email ?: com.example.util.PremadeTagsCatalog.OFFICIAL_DEVELOPER_EMAIL)
            } else {
                _isVerifiedDeveloperAccount.value = false
            }
            if (com.example.util.AlbertoGiftManager.isTargetGiftRecipient(user.email)) {
                _appTheme.value = AppBackgroundTheme.DARK_HUB
                hubPrefs.edit().putString("hub_theme_mode", AppBackgroundTheme.DARK_HUB.name).apply()
            }
            startCloudProfileSyncForUser(user)
            try {
                com.example.util.SquadReferralManager.syncAuthenticatedUser(
                    context = context,
                    uid = user.uid,
                    username = _userUsername.value.ifBlank { user.email?.substringBefore("@").orEmpty() },
                    displayName = _customDisplayName.value.ifBlank { user.displayName ?: "G-Max Gamer" }
                )
            } catch (_: Exception) {}
            try {
                com.example.util.RivalsManager.syncAuthenticatedUser(
                    context = context,
                    uid = user.uid
                )
            } catch (_: Exception) {}
        } else {
            profileObserverJob?.cancel()
            profileObserverJob = null
            _cloudProfile.value = null
            _isVerifiedDeveloperAccount.value = com.example.util.PremadeTagsCatalog.isOfficialDeveloperEmail(_customEmail.value)
            _userAvatarUrl.value = ""
            _userBannerUrl.value = "cyber_neon_grid"
            _cloudSyncStatus.value = "Guest Mode (Local Storage Only)"
            try {
                com.example.util.SquadReferralManager.onUserSignedOut(context)
            } catch (_: Exception) {}
            try {
                com.example.util.RivalsManager.onUserSignedOut(context)
            } catch (_: Exception) {}
        }
    }

    // Dual-Sided Squad Referral Loop & Pro Guest Pass State
    val squadPassState: StateFlow<com.example.data.repository.SquadPassSummaryState> =
        com.example.util.SquadReferralManager.state

    // Real-Time Rivals Competition State
    val rivalsState: StateFlow<com.example.data.repository.RivalsUiState> =
        com.example.util.RivalsManager.state

    private val _requestedExtrasSubTab = MutableStateFlow(0)
    val requestedExtrasSubTab: StateFlow<Int> = _requestedExtrasSubTab.asStateFlow()

    fun requestExtrasSubTab(subTab: Int) {
        _requestedExtrasSubTab.value = subTab.coerceIn(0, 6)
    }

    private val _showPermissionsPage = MutableStateFlow(false)
    val showPermissionsPage: StateFlow<Boolean> = _showPermissionsPage.asStateFlow()

    val isLowRamDeviceDetected: Boolean = run {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)
            val totalRamMb = memInfo.totalMem / (1024 * 1024)
            (am?.isLowRamDevice == true) || (totalRamMb in 1..4200)
        } catch (_: Exception) {
            false
        }
    }

    val lastMemoryTrimResult: StateFlow<com.example.util.MemoryTrimResult?> =
        com.example.util.AppMemoryManager.lastTrimResult

    val isBackgroundTelemetryPaused: StateFlow<Boolean> =
        com.example.util.AppMemoryManager.isBackgroundTelemetryPaused

    private val _isCleaningMemory = MutableStateFlow(false)
    val isCleaningMemory: StateFlow<Boolean> = _isCleaningMemory.asStateFlow()

    val showReviewPopup: StateFlow<Boolean> = com.example.util.AppReviewManager.showReviewPopup
    val activeReviewTriggerSource: StateFlow<com.example.util.ReviewTriggerSource> =
        com.example.util.AppReviewManager.activeTriggerSource
    val isVerifiedReviewed: StateFlow<Boolean> = com.example.util.AppReviewManager.isVerifiedReviewedFlow

    // Daily Engagement Streak & G-Tokens Economy State
    val dailyStreakState: StateFlow<com.example.util.DailyStreakState> =
        com.example.util.DailyStreakManager.streakState

    private val _streakFeedbackMessage = MutableStateFlow<String?>(null)
    val streakFeedbackMessage: StateFlow<String?> = _streakFeedbackMessage.asStateFlow()

    fun clearStreakFeedbackMessage() {
        _streakFeedbackMessage.value = null
    }

    fun refreshDailyStreak() {
        try {
            com.example.util.DailyStreakManager.switchAccount(context, getAccountKey())
        } catch (_: Exception) {}
    }

    fun buyStreakFreeze(): Pair<Boolean, String> {
        val result = com.example.util.DailyStreakManager.purchaseStreakFreeze(context)
        val ok = result.isSuccess
        val msg = if (ok) {
            result.getOrNull()?.statusBannerMessage ?: com.example.util.AppStrings.trText(
                "❄️ Streak Freeze purchased!",
                "❄️ ¡Congelador de Racha comprado!"
            )
        } else {
            result.exceptionOrNull()?.message ?: com.example.util.AppStrings.trText(
                "Need 100 G-Tokens to buy a Streak Freeze.",
                "Necesitas 100 G-Tokens para comprar un Congelador de Racha."
            )
        }
        _streakFeedbackMessage.value = msg
        com.example.ui.components.ActionFeedbackController.emitFeedback(
            context = context,
            message = msg,
            accentColorArgb = if (ok) 0xFF38BDF8L else 0xFFFF5252L,
            playHaptic = true
        )
        if (ok) {
            scheduleCloudSettingsSync()
        }
        return ok to msg
    }

    fun selectStreakHudNeonColor(hexColor: String) {
        val res = com.example.util.DailyStreakManager.selectHudNeonColor(context, hexColor)
        if (res.isSuccess) {
            _streakFeedbackMessage.value = com.example.util.AppStrings.trText(
                "7-Day Perk: Live HUD Neon Color updated to $hexColor!",
                "¡Recompensa de 7 Días: Color Neón del HUD actualizado a $hexColor!"
            )
            com.example.ui.components.ActionFeedbackController.emitFeedback(
                context = context,
                message = "HUD Neon Color Updated",
                accentColorArgb = 0xFF00E676L,
                playHaptic = true
            )
            syncOverlayService(context)
            scheduleCloudSettingsSync()
        } else {
            _streakFeedbackMessage.value = res.exceptionOrNull()?.message ?: com.example.util.AppStrings.trText(
                "Reach a 7-Day Streak to unlock Custom HUD Neon Colors.",
                "Alcanza una racha de 7 días para desbloquear Colores Neón del HUD."
            )
        }
    }

    fun repairBrokenStreakAfterRewardedAd(): Boolean {
        val state = com.example.util.DailyStreakManager.repairBrokenStreakViaRewardedAd(context)
        val success = !state.isStreakBrokenRepairable && state.currentStreak > 0
        if (success) {
            val msg = com.example.util.AppStrings.trText(
                "Streak Repaired! Your ${state.currentStreak}-Day Streak and milestones are restored!",
                "¡Racha Reparada! ¡Tu racha de ${state.currentStreak} días y tus recompensas han sido restauradas!"
            )
            _streakFeedbackMessage.value = msg
            com.example.ui.components.ActionFeedbackController.emitFeedback(
                context = context,
                message = msg,
                accentColorArgb = 0xFFFF9100L,
                playHaptic = true
            )
            syncOverlayService(context)
            scheduleCloudSettingsSync()
        }
        return success
    }

    fun resetBrokenStreakToZero() {
        com.example.util.DailyStreakManager.dismissAndResetBrokenStreak(context)
        _streakFeedbackMessage.value = com.example.util.AppStrings.trText(
            "Streak reset to 0 days. Launch a game with Focus Mode or HUD today to start a new streak!",
            "Racha reiniciada a 0 días. ¡Inicia un juego con Modo Enfoque o HUD hoy para comenzar una nueva racha!"
        )
        syncOverlayService(context)
        scheduleCloudSettingsSync()
    }

    // Lite Mode / Reduced Animations (disabled by default for all installations unless manually enabled by the user)
    private val _reducedAnimations = MutableStateFlow(
        hubPrefs.getBoolean("hub_lite_mode_manual", false)
    )
    val reducedAnimations: StateFlow<Boolean> = _reducedAnimations.asStateFlow()

    fun setReducedAnimations(enabled: Boolean) {
        _reducedAnimations.value = enabled
        hubPrefs.edit()
            .putBoolean("hub_lite_mode_manual", enabled)
            .putBoolean("hub_reduced_animations", enabled)
            .apply()
        com.example.ui.components.ActionFeedbackController.emitFeedback(
            context = context,
            message = if (enabled) "Lite Mode Enabled • Instant Transitions" else "Lite Mode Disabled • Full Spring Motion",
            accentColorArgb = if (enabled) 0xFF00E676L else 0xFF00E5FFL
        )
        scheduleCloudSettingsSync()
    }

    // Automatic Big Screen Mode when turning the device to landscape (disabled by default so Console Mode only starts when tapped)
    private val _autoBigScreenLandscape = MutableStateFlow(
        hubPrefs.getBoolean("hub_auto_big_screen_landscape", false)
    )
    val autoBigScreenLandscape: StateFlow<Boolean> = _autoBigScreenLandscape.asStateFlow()

    fun setAutoBigScreenLandscape(enabled: Boolean) {
        _autoBigScreenLandscape.value = enabled
        hubPrefs.edit().putBoolean("hub_auto_big_screen_landscape", enabled).apply()
        if (!enabled) {
            _isBigScreenModeManual.value = false
        }
        com.example.ui.components.ActionFeedbackController.emitFeedback(
            context = context,
            message = if (enabled) "Auto Landscape Big Screen Enabled" else "Auto Landscape Big Screen Disabled"
        )
        scheduleCloudSettingsSync()
    }

    // Show/Hide bottom Controller Shortcut Legend Bar ("A Launch • X Boost • Y Record • L1/R1", enabled by default)
    private val _showControllerLegendBar = MutableStateFlow(
        hubPrefs.getBoolean("hub_show_controller_legend_bar", true)
    )
    val showControllerLegendBar: StateFlow<Boolean> = _showControllerLegendBar.asStateFlow()

    fun setShowControllerLegendBar(enabled: Boolean) {
        _showControllerLegendBar.value = enabled
        hubPrefs.edit().putBoolean("hub_show_controller_legend_bar", enabled).apply()
        com.example.ui.components.ActionFeedbackController.emitFeedback(
            context = context,
            message = if (enabled) "Controller Shortcut Bar Enabled" else "Controller Shortcut Bar Hidden"
        )
        scheduleCloudSettingsSync()
    }

    // Controller Layout Mode (AUTO, PLAYSTATION, XBOX, NINTENDO)
    private val _controllerLayoutMode = MutableStateFlow(
        com.example.service.ControllerOverlayService.loadLayoutMode(context)
    )
    val controllerLayoutMode: StateFlow<com.example.model.ControllerLayoutMode> = _controllerLayoutMode.asStateFlow()

    val activeControllerGlyphs: StateFlow<com.example.model.ControllerGlyphMap> = combine(
        _controllerLayoutMode,
        _gamepads,
        controllerHealthState
    ) { mode, pads, health ->
        com.example.model.ControllerLayoutResolver.getGlyphMap(
            mode = mode,
            gamepads = pads,
            activeControllerName = health.activeControllerName
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        com.example.model.ControllerLayoutResolver.getGlyphMap(
            mode = _controllerLayoutMode.value,
            gamepads = emptyList(),
            activeControllerName = ""
        )
    )

    fun setControllerLayoutMode(mode: com.example.model.ControllerLayoutMode) {
        _controllerLayoutMode.value = mode
        com.example.service.ControllerOverlayService.saveLayoutMode(context, mode)
        hubPrefs.edit().putString("hub_controller_layout_mode", mode.name).apply()
        com.example.service.ControllerOverlayService.syncService(context)
        com.example.ui.components.ActionFeedbackController.emitFeedback(
            context = context,
            message = "Controller Layout: ${mode.title}"
        )
        scheduleCloudSettingsSync()
    }

    // Real-Time Controller Input Screen Overlay Configuration (Auto-Scaling, Size, Opacity, Touch Lock, Color)
    private val _controllerOverlayConfig = MutableStateFlow(
        com.example.service.ControllerOverlayService.loadConfig(context).let { loaded ->
            if (loaded.isEnabled && !com.example.util.ControllerHealthManager.isWiredControllerConnected(context)) {
                val disabled = loaded.copy(isEnabled = false)
                com.example.service.ControllerOverlayService.saveConfig(context, disabled)
                disabled
            } else {
                loaded
            }
        }
    )
    val controllerOverlayConfig: StateFlow<com.example.model.ControllerOverlayConfig> = _controllerOverlayConfig.asStateFlow()

    private val _showWiredControllerRequiredPopup = MutableStateFlow(false)
    val showWiredControllerRequiredPopup: StateFlow<Boolean> = _showWiredControllerRequiredPopup.asStateFlow()

    fun dismissWiredControllerRequiredPopup() {
        _showWiredControllerRequiredPopup.value = false
    }

    fun triggerWiredControllerRequiredPopup() {
        _showWiredControllerRequiredPopup.value = true
    }

    private fun updateAndPersistControllerOverlay(transform: (com.example.model.ControllerOverlayConfig) -> com.example.model.ControllerOverlayConfig) {
        val updated = transform(_controllerOverlayConfig.value)
        _controllerOverlayConfig.value = updated
        com.example.service.ControllerOverlayService.saveConfig(context, updated)
        com.example.service.ControllerOverlayService.syncService(context)
    }

    fun setControllerOverlayEnabled(enabled: Boolean) {
        if (enabled && !com.example.util.ControllerHealthManager.isWiredControllerConnected(context)) {
            updateAndPersistControllerOverlay { it.copy(isEnabled = false) }
            _showWiredControllerRequiredPopup.value = true
            return
        }
        updateAndPersistControllerOverlay { it.copy(isEnabled = enabled) }
        com.example.util.AppAnalyticsManager.logControllerOverlayToggled(context, enabled)
        if (enabled) {
            com.example.util.SmartNotificationManager.onOverlayUsed(
                context = context,
                showClockEnabled = _overlayConfig.value.showClock
            )
            com.example.util.RivalsManager.recordOverlayActivation(context)
        }
        if (enabled && !com.example.service.ControllerOverlayService.canRenderSystemOverlay(context)) {
            com.example.util.SystemSettingsHelper.openOverlayPermissionSettings(context)
        }
    }

    fun setControllerOverlayAutoScale(autoScale: Boolean) {
        updateAndPersistControllerOverlay { it.copy(autoScale = autoScale) }
    }

    fun setControllerOverlayScaleFactor(scaleFactor: Float) {
        updateAndPersistControllerOverlay { it.copy(scaleFactor = scaleFactor.coerceIn(0.55f, 1.65f)) }
    }

    fun setControllerOverlayOpacity(opacity: Float) {
        updateAndPersistControllerOverlay { it.copy(opacity = opacity.coerceIn(0.25f, 1.0f)) }
    }

    fun setControllerOverlayLockTouch(locked: Boolean) {
        updateAndPersistControllerOverlay { it.copy(lockTouchPassThrough = locked) }
    }

    fun setControllerOverlayDisableSpeakerAudio(disable: Boolean) {
        updateAndPersistControllerOverlay { it.copy(disableControllerSpeakerAudio = disable) }
        com.example.util.ControllerHealthManager.applyControllerSpeakerAudioRouting(context, disable)
        com.example.ui.components.ActionFeedbackController.emitFeedback(
            context = context,
            message = if (disable) {
                "Controller Speaker Disabled • Audio Routed to Phone"
            } else {
                "Controller Speaker Audio Allowed"
            },
            accentColorArgb = if (disable) 0xFF00E676L else 0xFF00E5FFL
        )
    }

    fun setControllerOverlayHighlightColor(colorArgb: Long) {
        updateAndPersistControllerOverlay { it.copy(highlightColorArgb = colorArgb) }
    }

    fun setControllerOverlayStickTrackingMode(mode: com.example.model.StickTrackingMode) {
        updateAndPersistControllerOverlay { it.copy(stickTrackingMode = mode) }
    }

    private var demoPulseJob: Job? = null

    fun triggerControllerOverlayDemoPulse() {
        demoPulseJob?.cancel()
        demoPulseJob = viewModelScope.launch {
            val sequence = listOf("A", "B", "X", "Y", "L1", "R1", "L2", "R2", "DPAD_UP", "DPAD_RIGHT", "DPAD_DOWN", "DPAD_LEFT", "MODE")
            for (btn in sequence) {
                controllerHealthManager.setSimulatedButtonState(btn, true)
                delay(120)
                controllerHealthManager.setSimulatedButtonState(btn, false)
            }
            // Sweep thumbsticks in a smooth circle
            val steps = 16
            for (i in 0..steps) {
                val angle = (i.toDouble() / steps) * 2.0 * Math.PI
                val sx = kotlin.math.cos(angle).toFloat() * 0.85f
                val sy = kotlin.math.sin(angle).toFloat() * 0.85f
                controllerHealthManager.setSimulatedSticks(sx, sy, -sx, -sy)
                delay(35)
            }
            controllerHealthManager.setSimulatedSticks(0f, 0f, 0f, 0f)
        }
    }

    // Lock controller stick/D-pad input to Stick Drift & Range tester so UI does not scroll or move focus
    private val _isControllerTestLocked = MutableStateFlow(false)
    val isControllerTestLocked: StateFlow<Boolean> = _isControllerTestLocked.asStateFlow()

    fun setControllerTestLocked(locked: Boolean) {
        _isControllerTestLocked.value = locked
    }

    fun setAppTheme(theme: AppBackgroundTheme) {
        _appTheme.value = theme
        hubPrefs.edit().putString("hub_theme_mode", theme.name).apply()
        scheduleCloudSettingsSync()
    }

    fun setCustomBackgroundColor(colorArgb: Long) {
        _customBackgroundColor.value = colorArgb
        _appTheme.value = AppBackgroundTheme.CUSTOM_COLOR
        hubPrefs.edit()
            .putLong("hub_custom_bg_color", colorArgb)
            .putString("hub_theme_mode", AppBackgroundTheme.CUSTOM_COLOR.name)
            .apply()
        scheduleCloudSettingsSync()
    }

    fun completeOnboarding() {
        _hasCompletedOnboarding.value = true
        _showPermissionsPage.value = false
        com.example.util.PersistentStorageHelper.markOnboardingCompleted(context)
    }

    fun setShowPermissionsPage(show: Boolean) {
        _showPermissionsPage.value = show
    }

    // Pre-Activation Warning suppression
    private val _suppressActivationWarning = MutableStateFlow(
        hubPrefs.getBoolean("hub_suppress_activation_warning", false)
    )
    val suppressActivationWarning: StateFlow<Boolean> = _suppressActivationWarning.asStateFlow()

    // System Status States
    private val _isBatteryOptimizationIgnored = MutableStateFlow(true)
    val isBatteryOptimizationIgnored: StateFlow<Boolean> = _isBatteryOptimizationIgnored.asStateFlow()

    private val _isPowerSaveModeActive = MutableStateFlow(false)
    val isPowerSaveModeActive: StateFlow<Boolean> = _isPowerSaveModeActive.asStateFlow()

    private val _isDndAccessGranted = MutableStateFlow(false)
    val isDndAccessGranted: StateFlow<Boolean> = _isDndAccessGranted.asStateFlow()

    fun setSuppressActivationWarning(suppress: Boolean) {
        _suppressActivationWarning.value = suppress
        hubPrefs.edit().putBoolean("hub_suppress_activation_warning", suppress).apply()
        scheduleCloudSettingsSync()
    }

    fun toggleOptionApplied(optionId: String) {
        val current = _appliedOptionIds.value.toMutableSet()
        if (current.contains(optionId)) {
            current.remove(optionId)
        } else {
            current.add(optionId)
        }
        _appliedOptionIds.value = current
        hubPrefs.edit().putStringSet("hub_applied_option_ids", current).apply()
        updateReadinessScore()
        scheduleCloudSettingsSync()
    }

    private var benchmarkJob: Job? = null

    init {
        try { com.example.util.AppStrings.init(context) } catch (_: Exception) {}
        try { com.example.util.SquadReferralManager.initialize(context) } catch (_: Exception) {}
        try { com.example.util.RivalsManager.initialize(context) } catch (_: Exception) {}
        try { com.example.util.DailyStreakManager.switchAccount(context, getAccountKey()) } catch (_: Exception) {}
        try { com.example.util.EfpsEngine.init(context) } catch (_: Exception) {}
        try { com.example.util.EfpsEngine.start(context) } catch (_: Exception) {}
        try { initPingTargets() } catch (_: Exception) {}

        // Restore HUD EFPS Sampling Profile, Target FPS Cap, RAM %, and Clock preferences immediately (fast memory read)
        try {
            val hudPrefs = context.getSharedPreferences("gmax_hud_prefs", Context.MODE_PRIVATE)
            val rawSaved = hudPrefs.getString("hud_fps_mode", "BALANCED") ?: "BALANCED"
            val normalizedMode = com.example.util.EfpsSamplingProfile.fromKey(rawSaved).key
            val savedCap = hudPrefs.getString("hud_target_cap", "AUTO")?.uppercase() ?: "AUTO"
            val normalizedCap = if (savedCap in com.example.util.EfpsEngine.SUPPORTED_CAP_MODES) savedCap else "AUTO"
            val savedRamPct = hudPrefs.getBoolean("hud_show_ram_pct", true)
            val savedClock = hudPrefs.getBoolean("hud_show_clock", true)
            val savedNetSpeed = hudPrefs.getBoolean(com.example.util.ConfigCodeCodec.KEY_HUD_SHOW_NET_SPEED, true)
            val savedAutoScale = hudPrefs.getBoolean("hud_auto_scale", true)
            val tickerCfg = com.example.service.NotificationTickerOverlayService.loadConfig(context)
            com.example.service.GamingVolumeLockManager.init(context)
            com.example.service.HardcoreCallBlockerManager.init(context)
            val volLockState = com.example.service.GamingVolumeLockManager.state.value
            val hardcoreState = com.example.service.HardcoreCallBlockerManager.state.value
            val speedrunEnabled = com.example.service.SpeedrunTimerOverlayService.loadSavedEnabled(context)
            _overlayConfig.value = _overlayConfig.value.copy(
                fpsMode = normalizedMode,
                targetCapMode = normalizedCap,
                showRamPct = savedRamPct,
                showClock = savedClock,
                showNetSpeed = savedNetSpeed,
                showNotificationTicker = tickerCfg.isEnabled,
                tickerDurationSeconds = tickerCfg.countdownSeconds,
                gamingVolumeLockEnabled = volLockState.isEnabled,
                gamingVolumeLockPercent = volLockState.lockedVolumePercent,
                hardcoreModeEnabled = hardcoreState.isEnabled,
                speedrunTimerEnabled = speedrunEnabled,
                autoScaleSmallScreens = savedAutoScale
            )
            context.getSharedPreferences("gmax_quick_browser_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        } catch (_: Exception) {}

        // Load remembered target game
        val (savedPkg, savedName) = boosterSessionManager.getLastTargetGame()
        if (savedPkg != null && savedName != null) {
            _selectedTargetGame.value = InstalledApp(
                packageName = savedPkg,
                appName = savedName,
                isGame = true
            )
            com.example.util.EfpsEngine.setActiveGamePackage(savedPkg)
        }

        // Load library games
        _libraryPackages.value = boosterSessionManager.getLibraryGamePackages()

        // Ensure 1h 30m profile and applied option IDs are restored
        try {
            _selectedBoostMode.value = BoostMode.BEAST
            val savedApplied = hubPrefs.getStringSet("hub_applied_option_ids", null)
            if (!savedApplied.isNullOrEmpty()) {
                _appliedOptionIds.value = savedApplied.toSet()
            }
        } catch (_: Exception) {}

        // Offload disk I/O, PackageManager queries, hardware probes, and launch cache auto-trim to background coroutine
        viewModelScope.launch(Dispatchers.IO) {
            try { thermalMonitor.start() } catch (_: Exception) {}
            try { refreshMetrics() } catch (_: Exception) {}
            try { updateLibraryGamesList() } catch (_: Exception) {}
            try { loadInstalledApps() } catch (_: Exception) {}
            try { refreshGamepads() } catch (_: Exception) {}
            try { com.example.util.SmartNotificationManager.scheduleBackgroundChecks(context) } catch (_: Exception) {}
            try {
                val launchTrim = com.example.util.AppMemoryManager.performAutoTrim(
                    context = context,
                    reason = "Cold Launch Auto-Trim",
                    cleanBackgroundProcesses = false
                )
                if (launchTrim.totalFreedBytes > 0L) {
                    com.example.util.SmartNotificationManager.recordClearedRamBytes(context, launchTrim.totalFreedBytes)
                }
            } catch (_: Exception) {}

            // Ensure legacy Tactical Crosshair state is permanently disabled
            try {
                _overlaySettings.value = _overlaySettings.value.copy(isEnabled = false)
                val currentApplied = _appliedOptionIds.value.toMutableSet()
                if (currentApplied.remove("tweak_crosshair")) {
                    _appliedOptionIds.value = currentApplied
                }
                com.example.service.CrosshairOverlayService.saveSettingsObject(
                    context = context,
                    settings = _overlaySettings.value.copy(isEnabled = false)
                )
            } catch (_: Exception) {}
        }

        // Register Firebase AuthStateListener for real-time cloud settings restoration & sync
        try {
            Firebase.auth.addAuthStateListener(authStateListener)
        } catch (_: Exception) {}

        // Collect real-time controller connection/disconnection alerts
        viewModelScope.launch {
            gamepadDetector.connectionAlerts.collect { alert ->
                _controllerAlert.value = alert
                if (alert != null) {
                    val connType = if (com.example.util.ControllerHealthManager.isWiredControllerConnected(context)) "USB_WIRED" else "BLUETOOTH"
                    com.example.util.AppAnalyticsManager.logControllerConnection(
                        context = context,
                        connected = alert.isConnected,
                        controllerName = alert.controllerName,
                        connectionType = connType
                    )
                }
                refreshGamepads()
            }
        }

        // Sync connected gamepads list whenever ControllerHealthManager binds/unbinds a controller via live input
        viewModelScope.launch {
            var lastControllerId = -2
            controllerHealthState.collect { state ->
                if (state.controllerId != lastControllerId) {
                    lastControllerId = state.controllerId
                    try {
                        _gamepads.value = gamepadDetector.getConnectedGamepads()
                    } catch (_: Exception) {}
                }
            }
        }

        // Listen for USB controller hot-plug attach/detach broadcasts
        try {
            val usbFilter = android.content.IntentFilter().apply {
                addAction(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            val usbReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    refreshGamepads()
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(usbReceiver, usbFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(usbReceiver, usbFilter)
            }
        } catch (_: Exception) {}

        // Auto-remove non-session overlays if device loses internet connection
        viewModelScope.launch {
            var wasOnline = com.example.util.NetworkConnectivityMonitor.isCurrentlyOnline(context)
            com.example.util.NetworkConnectivityMonitor.observeConnectivity(context).collect { isOnline ->
                if (wasOnline && !isOnline) {
                    val isSessionActive = boosterSessionManager.isSessionActive()

                    var anyRemoved = false
                    if (_controllerOverlayConfig.value.isEnabled) {
                        setControllerOverlayEnabled(false)
                        anyRemoved = true
                    }
                    if (!isSessionActive && (_overlayConfig.value.isEnabled || GamingOverlayService.isServiceRunning)) {
                        _overlayConfig.value = _overlayConfig.value.copy(isEnabled = false)
                        val currentApplied = _appliedOptionIds.value.toMutableSet()
                        currentApplied.remove("ctrl_overlay_hud")
                        _appliedOptionIds.value = currentApplied
                        try {
                            context.stopService(Intent(context, GamingOverlayService::class.java))
                        } catch (_: Exception) {}
                        anyRemoved = true
                    }
                    if (anyRemoved) {
                        _boostFeedback.value = "Internet connection lost: Non-session overlays paused."
                    }
                }
                wasOnline = isOnline
            }
        }

        // Periodic 1-second ticker that auto-pauses when the app is in background and idle
        viewModelScope.launch {
            while (true) {
                val isIdlePaused = com.example.util.AppMemoryManager.isBackgroundTelemetryPaused.value
                val sessionActive = boosterSessionManager.isSessionActive()
                val recapActive = matchRecapManager.isRecording.value
                if (isIdlePaused && !sessionActive && !recapActive) {
                    delay(2500)
                    continue
                }
                updateSessionState()
                if (sessionActive) {
                    com.example.util.AppReviewManager.recordBoostUsageMs(context, 1000L)
                }
                if (recapActive) {
                    val currentFps = efpsTelemetry.value.currentEfps.toFloat().coerceAtLeast(30f)
                    val frameTimeMs = (1000f / currentFps).coerceIn(4f, 60f)
                    matchRecapManager.recordSample(
                        frameTimeMs = frameTimeMs,
                        tempC = thermalGuardState.value.liveTempC,
                        batteryPct = thermalGuardState.value.batteryPct,
                        freeRamMb = _memoryStats.value.availRamMb.toInt()
                    )
                }
                delay(1000)
            }
        }
    }

    private fun updateSessionState() {
        val wasActive = _sessionUiState.value.isActive
        val isActive = boosterSessionManager.isSessionActive()
        val remaining = boosterSessionManager.getRemainingMillis()
        val mode = boosterSessionManager.getActiveMode()
        val (pkg, name) = boosterSessionManager.getLastTargetGame()

        _sessionUiState.value = BoosterSessionUiState(
            isActive = isActive,
            mode = mode,
            remainingMillis = remaining,
            formattedCountdown = boosterSessionManager.formatCountdown(remaining),
            targetGamePackage = pkg,
            targetGameName = name
        )
        _gamingSessionActive.value = isActive
        if (wasActive && !isActive) {
            _overlayConfig.value = _overlayConfig.value.copy(isEnabled = false)
            setOptionAppliedState("ctrl_overlay_hud", false)
            syncOverlayService(context)
            com.example.util.AppReviewManager.onBoostSessionEnded(context)
        }
    }

    fun setGamePickerVisible(visible: Boolean) {
        _isGamePickerVisible.value = visible
    }

    fun selectTargetGame(app: InstalledApp) {
        _selectedTargetGame.value = app
        boosterSessionManager.setLastTargetGame(app.packageName, app.appName)
        com.example.util.EfpsEngine.setActiveGamePackage(app.packageName)
        _isGamePickerVisible.value = false
        _boostFeedback.value = "Target game selected: ${app.appName}."
        com.example.ui.components.ActionFeedbackController.emitFeedback(
            context = context,
            message = "Selected ${app.appName}"
        )
        scheduleCloudSettingsSync()
    }

    fun stopBoosterSession() {
        boosterSessionManager.stopSession()
        networkTester.setLowLatencyWifiLock(false)
        SystemSettingsHelper.setDoNotDisturb(context, false)
        _overlayConfig.value = _overlayConfig.value.copy(isEnabled = false)
        setOptionAppliedState("ctrl_overlay_hud", false)
        updateSessionState()
        syncOverlayService(context)
        com.example.util.SmartNotificationManager.triggerPostSessionWrapUp(context)
        com.example.util.AppReviewManager.onBoostSessionEnded(context)
        _boostFeedback.value = "Overlay and Focus Mode Timer ended. Default system scheduler, DND, and overlay restored."
        com.example.ui.components.ActionFeedbackController.emitFeedback(
            context = context,
            message = "Overlay & Focus Mode Stopped"
        )
    }

    /**
     * Auto-pauses background telemetry, Choreographer callbacks, thermal receivers, and idle wake/Wi-Fi locks
     * whenever the user minimizes the app and no active Boost or HUD overlay session is running.
     */
    fun onAppPausedOrStopped() {
        if (_overlaySettings.value.isEnabled) {
            com.example.service.CrosshairOverlayService.syncService(context)
        }
        val sessionActive = boosterSessionManager.isSessionActive()
        val overlayActive = _overlayConfig.value.isEnabled && GamingOverlayService.isServiceRunning
        if (sessionActive || _overlayConfig.value.gamingVolumeLockEnabled) {
            com.example.service.GamingVolumeLockManager.setGameInForeground(context, true)
        }
        if (!sessionActive && !overlayActive) {
            com.example.util.AppMemoryManager.setBackgroundIdlePaused(true)
            com.example.util.EfpsEngine.pauseForIdle()
            thermalMonitor.stop()
            thermalGuard.pause()
            if (!_appliedOptionIds.value.contains("net_low_latency")) {
                networkTester.setLowLatencyWifiLock(false)
            }
        }
    }

    /**
     * One-tap or automated RAM & Cache cleanup with immediate haptic + visual feedback.
     * Reports the exact measured RAM + cache freed without any hardcoded 12MB minimum clamp.
     */
    fun cleanRamAndCacheNow() {
        if (_isCleaningMemory.value) return
        _isCleaningMemory.value = true
        com.example.ui.components.ActionFeedbackController.emitFeedback(
            context = context,
            message = "Trimming Cache & Background RAM...",
            isProcessStart = true
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = com.example.util.AppMemoryManager.performAutoTrim(
                    context = context,
                    reason = "Manual RAM & Cache Purge",
                    cleanBackgroundProcesses = true,
                    force = true
                )
                if (result.totalFreedBytes > 0L) {
                    com.example.util.SmartNotificationManager.recordClearedRamBytes(context, result.totalFreedBytes)
                }
                refreshMetrics()
                val exactSummary = com.example.util.AppMemoryManager.formatExactTrimSummary(result)
                _boostFeedback.value = "Memory & Cache Optimized: $exactSummary (${result.backgroundAppsTrimmed} background apps trimmed)."
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    com.example.ui.components.ActionFeedbackController.emitFeedback(
                        context = context,
                        message = exactSummary,
                        accentColorArgb = 0xFF00E676L,
                        playHaptic = true
                    )
                }
            } catch (_: Exception) {
            } finally {
                _isCleaningMemory.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            Firebase.auth.removeAuthStateListener(authStateListener)
        } catch (_: Exception) {}
        profileObserverJob?.cancel()
        cloudSyncDebounceJob?.cancel()
        thermalMonitor.stop()
        networkTester.setLowLatencyWifiLock(false)
        PriorityDndHelper.restorePreviousDnd(context)
        thermalGuard.destroy()
        controllerHealthManager.destroy()
    }

    fun refreshMetrics() {
        try {
            _memoryStats.value = memoryHelper.getMemoryStats()
            _wifiStatus.value = networkTester.getWifiStatus()
            _isBatteryOptimizationIgnored.value = SystemSettingsHelper.isIgnoringBatteryOptimizations(context)
            _isPowerSaveModeActive.value = SystemSettingsHelper.isPowerSaveMode(context)
            _isDndAccessGranted.value = SystemSettingsHelper.isNotificationPolicyAccessGranted(context)
            updateReadinessScore()
            loadHardwareSpecs()
        } catch (_: Exception) {}
    }

    fun requestBatteryOptimizationExemption(ctx: Context) {
        SystemSettingsHelper.requestIgnoreBatteryOptimizations(ctx)
    }

    fun openBatterySaverSettings(ctx: Context) {
        SystemSettingsHelper.openBatterySaverSettings(ctx)
    }

    fun openDndAccessSettings(ctx: Context) {
        SystemSettingsHelper.openNotificationPolicyAccess(ctx)
    }

    fun refreshGamepads() {
        try {
            controllerHealthManager.scanActiveController()
            _gamepads.value = gamepadDetector.getConnectedGamepads()
            if (_controllerOverlayConfig.value.isEnabled &&
                !com.example.util.ControllerHealthManager.isWiredControllerConnected(context)
            ) {
                updateAndPersistControllerOverlay { it.copy(isEnabled = false) }
            }
        } catch (_: Exception) {}
    }

    fun updateLibraryGamesList() {
        val pkgs = _libraryPackages.value
        val appsMap = _installedApps.value.associateBy { it.packageName }
        val pm = context.packageManager
        val validList = mutableListOf<InstalledApp>()
        val invalidPkgs = mutableListOf<String>()

        for (pkg in pkgs) {
            val cached = appsMap[pkg]
            if (cached != null) {
                validList.add(cached)
                continue
            }
            val verifiedApp = try {
                if (pkg == context.packageName ||
                    pkg.equals("com.example", ignoreCase = true) ||
                    pkg.startsWith("com.aistudio.gamebooster", ignoreCase = true) ||
                    pkg.contains("gamebooster", ignoreCase = true)
                ) {
                    null
                } else {
                    val launchIntent = pm.getLaunchIntentForPackage(pkg)
                    val resolved = if (launchIntent != null) pm.resolveActivity(launchIntent, 0) else null
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val iconDrawable = if (appInfo.icon != 0) pm.getApplicationIcon(pkg) else null
                    val label = pm.getApplicationLabel(appInfo).toString().trim()
                    val pkgSuffix = pkg.substringAfterLast(".")
                    if (launchIntent != null &&
                        resolved != null &&
                        iconDrawable != null &&
                        label.isNotBlank() &&
                        !label.equals(pkg, ignoreCase = true) &&
                        !label.equals(pkgSuffix, ignoreCase = true) &&
                        !label.equals("game", ignoreCase = true) &&
                        !label.equals("gamebooster", ignoreCase = true) &&
                        !label.endsWith(".apk", ignoreCase = true)
                    ) {
                        InstalledApp(packageName = pkg, appName = label, isGame = true)
                    } else {
                        null
                    }
                }
            } catch (_: Exception) {
                null
            }
            if (verifiedApp != null) {
                validList.add(verifiedApp)
            } else {
                invalidPkgs.add(pkg)
            }
        }

        if (invalidPkgs.isNotEmpty()) {
            invalidPkgs.forEach { boosterSessionManager.removeLibraryGamePackage(it) }
            _libraryPackages.value = boosterSessionManager.getLibraryGamePackages()
        }
        _libraryGames.value = validList
        refreshPlayStats()
    }

    fun refreshPlayStats() {
        try {
            com.example.util.GamePlayStatsHelper.setActiveAccountKey(context, getAccountKey())
            val currentLib = _libraryGames.value
            _recentlyPlayedGames.value = com.example.util.GamePlayStatsHelper.getRecentlyPlayedGames(context, currentLib)
            _mostPlayedGames.value = com.example.util.GamePlayStatsHelper.getMostPlayedGames(context, currentLib)
            _totalPlayTimeSummary.value = com.example.util.GamePlayStatsHelper.getTotalPlayTimeSummary(context, currentLib)
        } catch (_: Exception) {}
    }

    fun onAppResumed() {
        com.example.util.AppMemoryManager.setBackgroundIdlePaused(false)
        com.example.util.EfpsEngine.resumeFromIdle(context)
        com.example.service.GamingVolumeLockManager.setGameInForeground(context, false)
        com.example.service.HardcoreCallBlockerManager.onUserReturnedToHubCheckPostMatchSummary(context)
        if (_overlaySettings.value.isEnabled) {
            com.example.service.CrosshairOverlayService.syncService(context)
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                thermalMonitor.start()
                thermalGuard.start()
                com.example.util.GamePlayStatsHelper.setActiveAccountKey(context, getAccountKey())
                com.example.util.GamePlayStatsHelper.finalizeActiveGameSessionOnReturn(context)
                if (matchRecapManager.isRecording.value && matchRecapManager.getElapsedRecordingSeconds() >= 3L) {
                    matchRecapManager.finishSession()
                    com.example.util.SmartNotificationManager.triggerPostSessionWrapUp(context)
                }
                com.example.util.SmartNotificationManager.evaluateInactivitySequence(context)
                refreshPlayStats()
                syncCurrentAccountToLocalPlayerDirectory()
                scheduleCloudSettingsSync()
            } catch (_: Exception) {}
        }
    }

    fun addGameToLibrary(app: InstalledApp) {
        boosterSessionManager.addLibraryGamePackage(app.packageName)
        _libraryPackages.value = boosterSessionManager.getLibraryGamePackages()
        updateLibraryGamesList()
        _boostFeedback.value = "Added ${app.appName} to Library."
        com.example.ui.components.ActionFeedbackController.emitFeedback(
            context = context,
            message = "Added ${app.appName} to Library",
            accentColorArgb = 0xFF00E676L
        )
        scheduleCloudSettingsSync()
    }

    fun removeGameFromLibrary(packageName: String) {
        boosterSessionManager.removeLibraryGamePackage(packageName)
        _libraryPackages.value = boosterSessionManager.getLibraryGamePackages()
        updateLibraryGamesList()
        _boostFeedback.value = "Removed from Library."
        com.example.ui.components.ActionFeedbackController.emitFeedback(
            context = context,
            message = "Removed from Library"
        )
        scheduleCloudSettingsSync()
    }

    fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apps = memoryHelper.getInstalledUserApps()
                _installedApps.value = apps
                val saved = boosterSessionManager.getLibraryGamePackages()
                if (saved.isEmpty()) {
                    val defaultGames = apps.filter { it.isGame }
                    if (defaultGames.isNotEmpty()) {
                        defaultGames.forEach { boosterSessionManager.addLibraryGamePackage(it.packageName) }
                        _libraryPackages.value = boosterSessionManager.getLibraryGamePackages()
                    }
                }
                updateLibraryGamesList()
            } catch (_: Exception) {}
        }
    }

    private fun initPingTargets() {
        try {
            _pingResults.value = networkTester.serverTargets.map {
                NetworkPingResult(regionName = it.region, host = it.host)
            }
        } catch (_: Exception) {}
    }

    fun loadHardwareSpecs() {
        try {
            val displayMetrics = context.resources.displayMetrics
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            @Suppress("DEPRECATION")
            val display = windowManager?.defaultDisplay

            val refreshRate = try {
                display?.refreshRate?.toInt() ?: 60
            } catch (_: Exception) { 60 }

            val supportedRates = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    display?.supportedModes?.map { it.refreshRate.toInt() }?.distinct()?.sorted() ?: listOf(60)
                } else {
                    listOf(refreshRate)
                }
            } catch (_: Exception) { listOf(60) }

            val soc = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN } ?: Build.HARDWARE
                } else {
                    Build.HARDWARE
                }
            } catch (_: Exception) { Build.HARDWARE }

            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val glVer = try {
                am?.deviceConfigurationInfo?.glEsVersion ?: "3.2"
            } catch (_: Exception) { "3.2" }

            val hasVulkan = try {
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
            } catch (_: Exception) { true }

            val mem = try {
                memoryHelper.getMemoryStats()
            } catch (_: Exception) { MemoryStats() }

            _hardwareSpecs.value = PhoneHardwareSpecs(
                deviceName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
                manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
                model = Build.MODEL,
                board = Build.BOARD,
                androidVersion = "Android ${Build.VERSION.RELEASE}",
                apiLevel = Build.VERSION.SDK_INT,
                securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "N/A",
                socModel = soc,
                cpuCores = Runtime.getRuntime().availableProcessors(),
                cpuAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a",
                gpuRenderer = "Hardware GLES $glVer",
                openGlVersion = "OpenGL ES $glVer",
                vulkanSupported = hasVulkan,
                totalRamMb = mem.totalRamMb,
                availRamMb = mem.availRamMb,
                totalStorageGb = mem.internalStorageTotalGb,
                freeStorageGb = mem.internalStorageFreeGb,
                displayResolution = "${displayMetrics.widthPixels} x ${displayMetrics.heightPixels}",
                refreshRateHz = refreshRate,
                supportedRefreshRates = supportedRates,
                screenDensityDpi = displayMetrics.densityDpi
            )
        } catch (_: Exception) {}
    }

    fun resolveFocusedOrDefaultGame(): InstalledApp? {
        val focusedPkg = _focusedGamePackage.value
        if (!focusedPkg.isNullOrBlank()) {
            _libraryGames.value.firstOrNull { it.packageName == focusedPkg }?.let { return it }
            _installedApps.value.firstOrNull { it.packageName == focusedPkg }?.let { return it }
        }
        _selectedTargetGame.value?.let { return it }
        _libraryGames.value.firstOrNull()?.let { return it }
        return _installedApps.value.firstOrNull { it.isGame } ?: _installedApps.value.firstOrNull()
    }

    fun boostAndLaunchFocusedGame(): InstalledApp? {
        val target = resolveFocusedOrDefaultGame()
        quickBoost(targetApp = target, mode = BoostMode.BEAST)
        return target
    }

    fun stageOverlayConfigForPreLaunch(context: Context, stagedConfig: com.example.model.OverlayHudConfig) {
        if (stagedConfig.isEnabled && !SystemSettingsHelper.canDrawOverlays(context)) {
            SystemSettingsHelper.openOverlayPermissionSettings(context)
        }
        _overlayConfig.value = stagedConfig
        com.example.service.NotificationTickerOverlayService.setEnabled(context, stagedConfig.showNotificationTicker)
        com.example.service.NotificationTickerOverlayService.setCountdownSeconds(context, stagedConfig.tickerDurationSeconds)
        com.example.service.GamingVolumeLockManager.setEnabled(context, stagedConfig.gamingVolumeLockEnabled)
        com.example.service.GamingVolumeLockManager.setLockedVolumePercent(context, stagedConfig.gamingVolumeLockPercent)
        com.example.service.HardcoreCallBlockerManager.setEnabled(context, stagedConfig.hardcoreModeEnabled)
        context.getSharedPreferences("gmax_hud_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("hud_show_ram_pct", stagedConfig.showRamPct)
            .putBoolean("hud_show_clock", stagedConfig.showClock)
            .putBoolean("hud_auto_scale", stagedConfig.autoScaleSmallScreens)
            .apply()
        // Ensure the floating overlay service does NOT appear while the user is still configuring the popup;
        // it will be launched when the user presses "Open Game".
        try {
            val stopIntent = Intent(context, GamingOverlayService::class.java).apply {
                action = GamingOverlayService.ACTION_STOP
            }
            context.stopService(stopIntent)
        } catch (_: Exception) {}
    }

    fun quickBoost(
        targetApp: InstalledApp? = null,
        @Suppress("UNUSED_PARAMETER") mode: BoostMode? = null,
        chosenOverlayConfig: com.example.model.OverlayHudConfig? = null
    ) {
        _selectedBoostMode.value = BoostMode.BEAST
        if (chosenOverlayConfig != null) {
            _overlayConfig.value = chosenOverlayConfig
            com.example.service.NotificationTickerOverlayService.setEnabled(context, chosenOverlayConfig.showNotificationTicker)
            com.example.service.NotificationTickerOverlayService.setCountdownSeconds(context, chosenOverlayConfig.tickerDurationSeconds)
            com.example.service.GamingVolumeLockManager.setEnabled(context, chosenOverlayConfig.gamingVolumeLockEnabled)
            com.example.service.GamingVolumeLockManager.setLockedVolumePercent(context, chosenOverlayConfig.gamingVolumeLockPercent)
            com.example.service.HardcoreCallBlockerManager.setEnabled(context, chosenOverlayConfig.hardcoreModeEnabled)
            com.example.service.SpeedrunTimerOverlayService.setEnabled(
                context = context,
                enabled = chosenOverlayConfig.speedrunTimerEnabled,
                gamePackage = targetApp?.packageName,
                gameName = targetApp?.appName
            )
            context.getSharedPreferences("gmax_hud_prefs", Context.MODE_PRIVATE).edit()
                .putBoolean("hud_show_ram_pct", chosenOverlayConfig.showRamPct)
                .putBoolean("hud_show_clock", chosenOverlayConfig.showClock)
                .putBoolean(com.example.util.ConfigCodeCodec.KEY_HUD_SHOW_NET_SPEED, chosenOverlayConfig.showNetSpeed)
                .putBoolean("hud_auto_scale", chosenOverlayConfig.autoScaleSmallScreens)
                .apply()
        }
        val resolvedTarget = targetApp ?: resolveFocusedOrDefaultGame()
        if (resolvedTarget != null) {
            selectTargetGame(resolvedTarget)
        }

        val target = _selectedTargetGame.value
        if (target == null) {
            _boostFeedback.value = "Add a game to your Launcher library to launch with Focus Mode."
            return
        }

        com.example.ui.components.ActionFeedbackController.emitFeedback(
            context = context,
            message = "Launching ${target.appName} with Focus Mode...",
            accentColorArgb = 0xFF00E676L,
            isProcessStart = true,
            playHaptic = true
        )

        viewModelScope.launch {
            _isBoosting.value = true

            // Complete Daily Engagement Streak requirement immediately when game launches
            try {
                val (streakAfterLaunch, didIncrementStreak) =
                    com.example.util.DailyStreakManager.recordDailyRequirementCompleted(
                        context = context,
                        sourceLabel = "GMAX Focus Mode Launch"
                    )
                if (didIncrementStreak) {
                    val streakMsg = com.example.util.AppStrings.trText(
                        "🔥 Daily Streak +1! You're on a ${streakAfterLaunch.currentStreak}-Day Streak!",
                        "🔥 ¡Racha Diaria +1! ¡Llevas ${streakAfterLaunch.currentStreak} días de racha!"
                    )
                    _streakFeedbackMessage.value = streakMsg
                    scheduleCloudSettingsSync()
                }
            } catch (_: Exception) {}

            // Start Post-Match Performance Recap session automatically on launch
            startManualMatchRecap(target.appName)

            _boostFeedback.value = "Overlay and Focus Mode Timer (1h 30m): Engaging low-latency network & focus profile for ${target.appName}..."
            val trimResult = com.example.util.AppMemoryManager.performAutoTrim(
                context = context,
                reason = "Focus Mode Start",
                cleanBackgroundProcesses = true,
                force = true
            )
            val freedMb = trimResult.ramFreedMb
            val exactSummary = com.example.util.AppMemoryManager.formatExactTrimSummary(trimResult)
            if (trimResult.totalFreedBytes > 0L) {
                com.example.util.SmartNotificationManager.recordClearedRamBytes(context, trimResult.totalFreedBytes)
            }

            // 1. Acquire low-latency Wi-Fi radio lock (WIFI_MODE_FULL_LOW_LATENCY)
            networkTester.setLowLatencyWifiLock(true)

            // 2. Apply low-latency fast-track audio parameters
            try {
                val audioMgr = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                audioMgr?.setParameters("game_mode=1;low_latency=1;fast_track=1")
            } catch (_: Exception) {}

            // 3. Attempt Secure Settings 0.0x animation scale if granted
            if (com.example.util.SecureSettingsManager.canWriteSecureSettings(context)) {
                com.example.util.SecureSettingsManager.setAnimationScales(context, 0.0f)
            }

            // 4. Suppress ALL interruptions (Full Gaming Silence) if granted
            if (SystemSettingsHelper.isNotificationPolicyAccessGranted(context)) {
                SystemSettingsHelper.setDoNotDisturb(context, true, fullSilence = true)
            }

            // 5. Preserve the user's exact selected overlay options without overwriting them
            if (chosenOverlayConfig == null && !SystemSettingsHelper.canDrawOverlays(context)) {
                _overlayConfig.value = _overlayConfig.value.copy(isEnabled = false)
            }

            // 6. Start persistent 1 hour 30 min session
            com.example.util.AppReviewManager.onNewBoostSessionStarted(context)
            boosterSessionManager.startSession(BoostMode.BEAST, target.packageName, target.appName)
            updateSessionState()
            syncOverlayService(context)

            delay(260)
            withContext(Dispatchers.IO) { refreshMetrics() }
            _isBoosting.value = false
            com.example.util.SmartNotificationManager.onBoosterSessionStarted(
                context = context,
                freedRamMb = freedMb
            )
            com.example.util.RivalsManager.recordBoosterActivation(context)
            _boostFeedback.value = "Overlay and Focus Mode Timer Active (1h 30m): 0-Jitter Wi-Fi Lock, Fast-Track Audio, Full Silence DND & HUD engaged ($exactSummary). Launching ${target.appName}..."

            // Record real launch telemetry and trigger console launch haptics
            com.example.util.AppAnalyticsManager.logGameBoost(
                context = context,
                gamePackage = target.packageName,
                gameName = target.appName,
                boostMode = BoostMode.BEAST.name,
                ramFreedMb = com.example.util.AppMemoryManager.lastTrimResult.value?.ramFreedMb ?: 0L
            )
            com.example.util.GamePlayStatsHelper.setActiveAccountKey(context, getAccountKey())
            com.example.util.GamePlayStatsHelper.recordGameLaunch(context, target.packageName, target.appName)
            com.example.util.ConsoleHapticManager.playLaunchPulse(context)
            withContext(Dispatchers.IO) {
                refreshPlayStats()
                syncCurrentAccountToLocalPlayerDirectory()
            }
            scheduleCloudSettingsSync()

            // Gate external game launch until any Post-Action Unity ad has completely finished and closed
            val launchExternalGameNow: () -> Unit = {
                // Engage Gaming Volume Lock and Hardcore Call Blocker for the foreground game
                com.example.service.HardcoreCallBlockerManager.setActiveGameName(context, target.appName)
                com.example.service.GamingVolumeLockManager.setGameInForeground(context, true)
                // Pause GMAX's own internal UI monitors before handing focus to the game so GMAX uses 0% CPU in background
                com.example.util.AppMemoryManager.setBackgroundIdlePaused(true)
                thermalMonitor.stop()
                thermalGuard.pause()

                try {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(target.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                    }
                } catch (_: Exception) {}
            }

            com.example.util.UnityAdsManager.maybeTriggerPostActionAd(
                context = context,
                actionLabelEn = "Focus Mode & Overlay",
                actionLabelEs = "Modo Enfoque y Superposición",
                onAdFinished = launchExternalGameNow
            )
        }
    }

    fun clearFeedback() {
        _boostFeedback.value = null
    }

    fun toggleOverlayEnabled(context: Context, enabled: Boolean) {
        if (enabled && !SystemSettingsHelper.canDrawOverlays(context)) {
            SystemSettingsHelper.openOverlayPermissionSettings(context)
            _overlayConfig.value = _overlayConfig.value.copy(isEnabled = false)
            return
        }
        _overlayConfig.value = _overlayConfig.value.copy(isEnabled = enabled)
        if (enabled) {
            com.example.util.SmartNotificationManager.onOverlayUsed(
                context = context,
                showClockEnabled = _overlayConfig.value.showClock
            )
            com.example.util.RivalsManager.recordOverlayActivation(context)
            try {
                val (streakAfterHud, didIncrementStreak) =
                    com.example.util.DailyStreakManager.recordDailyRequirementCompleted(
                        context = context,
                        sourceLabel = "Live Performance HUD"
                    )
                if (didIncrementStreak) {
                    _streakFeedbackMessage.value = com.example.util.AppStrings.trText(
                        "🔥 Daily Streak +1! Live HUD kept your ${streakAfterHud.currentStreak}-Day Streak alive!",
                        "🔥 ¡Racha Diaria +1! ¡El HUD en vivo mantuvo tu racha de ${streakAfterHud.currentStreak} días!"
                    )
                }
            } catch (_: Exception) {}
        }
        com.example.ui.components.ActionFeedbackController.emitFeedback(
            context = context,
            message = if (enabled) "HUD Overlay Enabled" else "HUD Overlay Disabled",
            accentColorArgb = if (enabled) 0xFF00E676L else 0xFF00E5FFL
        )
        syncOverlayService(context)
        scheduleCloudSettingsSync()
    }

    fun toggleOverlayFps(context: Context, show: Boolean) {
        _overlayConfig.value = _overlayConfig.value.copy(showFps = show)
        com.example.ui.components.ActionFeedbackController.emitFeedback(
            context = context,
            message = if (show) "FPS Readout On" else "FPS Readout Off"
        )
        syncOverlayService(context)
        scheduleCloudSettingsSync()
    }

    fun toggleOverlayTemp(context: Context, show: Boolean) {
        _overlayConfig.value = _overlayConfig.value.copy(showTemp = show)
        com.example.ui.components.ActionFeedbackController.emitFeedback(
            context = context,
            message = if (show) "Thermal Readout On" else "Thermal Readout Off"
        )
        syncOverlayService(context)
        scheduleCloudSettingsSync()
    }

    fun toggleOverlayBattery(context: Context, show: Boolean) {
        _overlayConfig.value = _overlayConfig.value.copy(showBattery = show)
        com.example.ui.components.ActionFeedbackController.emitFeedback(
            context = context,
            message = if (show) "Battery Readout On" else "Battery Readout Off"
        )
        syncOverlayService(context)
        scheduleCloudSettingsSync()
    }

    fun toggleOverlayTimer(context: Context, show: Boolean) {
        _overlayConfig.value = _overlayConfig.value.copy(showTimer = show)
        com.example.ui.components.ActionFeedbackController.emitFeedback(
            context = context,
            message = if (show) "Session Timer On" else "Session Timer Off"
        )
        syncOverlayService(context)
        scheduleCloudSettingsSync()
    }

    fun toggleOverlayRamPct(context: Context, show: Boolean) {
        _overlayConfig.value = _overlayConfig.value.copy(showRamPct = show)
        context.getSharedPreferences("gmax_hud_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("hud_show_ram_pct", show).apply()
        com.example.ui.components.ActionFeedbackController.emitFeedback(
            context = context,
            message = if (show) "RAM % Readout On" else "RAM % Readout Off"
        )
        syncOverlayService(context)
        scheduleCloudSettingsSync()
    }

    fun toggleOverlayClock(context: Context, show: Boolean) {
        _overlayConfig.value = _overlayConfig.value.copy(showClock = show)
        context.getSharedPreferences("gmax_hud_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("hud_show_clock", show).apply()
        com.example.ui.components.ActionFeedbackController.emitFeedback(
            context = context,
            message = if (show) "Clock Overlay On" else "Clock Overlay Off"
        )
        syncOverlayService(context)
        scheduleCloudSettingsSync()
    }

    fun toggleOverlayNetSpeed(context: Context, show: Boolean) {
        _overlayConfig.value = _overlayConfig.value.copy(showNetSpeed = show)
        context.getSharedPreferences("gmax_hud_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean(com.example.util.ConfigCodeCodec.KEY_HUD_SHOW_NET_SPEED, show).apply()
        com.example.ui.components.ActionFeedbackController.emitFeedback(
            context = context,
            message = if (show) "Local KB/s Lag Monitor On" else "Local KB/s Lag Monitor Off"
        )
        syncOverlayService(context)
        scheduleCloudSettingsSync()
    }

    val notificationTickerConfig: StateFlow<com.example.service.NotificationTickerConfig> =
        com.example.service.NotificationTickerOverlayService.config

    fun toggleOverlayNotificationTicker(context: Context, show: Boolean) {
        val updatedTicker = com.example.service.NotificationTickerOverlayService.setEnabled(context, show)
        _overlayConfig.value = _overlayConfig.value.copy(
            showNotificationTicker = updatedTicker.isEnabled
        )
        com.example.ui.components.ActionFeedbackController.emitFeedback(
            context = context,
            message = if (show) "Top-Edge Notification Ticker On" else "Top-Edge Notification Ticker Off"
        )
        scheduleCloudSettingsSync()
    }

    fun setNotificationTickerDurationSeconds(context: Context, seconds: Int) {
        val updatedTicker = com.example.service.NotificationTickerOverlayService.setCountdownSeconds(context, seconds)
        _overlayConfig.value = _overlayConfig.value.copy(
            tickerDurationSeconds = updatedTicker.countdownSeconds
        )
        scheduleCloudSettingsSync()
    }

    fun setNotificationTickerAutoCancelSystem(context: Context, autoCancel: Boolean) {
        com.example.service.NotificationTickerOverlayService.setAutoCancelSystemPopups(context, autoCancel)
    }

    val gamingVolumeLockState: StateFlow<com.example.service.GamingVolumeLockState> =
        com.example.service.GamingVolumeLockManager.state

    val hardcoreModeState: StateFlow<com.example.service.HardcoreModeState> =
        com.example.service.HardcoreCallBlockerManager.state

    fun setGamingVolumeLockEnabled(context: Context, enabled: Boolean) {
        com.example.service.GamingVolumeLockManager.setEnabled(context, enabled)
        _overlayConfig.value = _overlayConfig.value.copy(gamingVolumeLockEnabled = enabled)
        com.example.ui.components.ActionFeedbackController.emitFeedback(
            context = context,
            message = if (enabled) {
                "Gaming Volume Lock Active (${_overlayConfig.value.gamingVolumeLockPercent}%)"
            } else {
                "Gaming Volume Lock Off"
            },
            accentColorArgb = if (enabled) 0xFF00E5FFL else 0xFF64748BL
        )
        scheduleCloudSettingsSync()
    }

    fun setGamingVolumeLockPercent(context: Context, percent: Int) {
        val clamped = percent.coerceIn(10, 100)
        com.example.service.GamingVolumeLockManager.setLockedVolumePercent(context, clamped)
        _overlayConfig.value = _overlayConfig.value.copy(gamingVolumeLockPercent = clamped)
        scheduleCloudSettingsSync()
    }

    fun setHardcoreModeEnabled(context: Context, enabled: Boolean) {
        com.example.service.HardcoreCallBlockerManager.setEnabled(context, enabled)
        _overlayConfig.value = _overlayConfig.value.copy(hardcoreModeEnabled = enabled)
        com.example.ui.components.ActionFeedbackController.emitFeedback(
            context = context,
            message = if (enabled) {
                "Hardcore Mode Active • Incoming Calls Auto-Rejected"
            } else {
                "Hardcore Mode Disabled"
            },
            accentColorArgb = if (enabled) 0xFFEF4444L else 0xFF64748BL
        )
        scheduleCloudSettingsSync()
    }

    fun openPostMatchSummaryModal(context: Context) {
        com.example.service.HardcoreCallBlockerManager.openPostMatchSummaryModal(context)
    }

    fun dismissPostMatchSummaryModal(context: Context) {
        com.example.service.HardcoreCallBlockerManager.dismissPostMatchSummaryModal(context)
    }

    val speedrunTimerState: StateFlow<com.example.service.SpeedrunTimerState> =
        com.example.service.SpeedrunTimerOverlayService.state

    fun setSpeedrunTimerEnabled(
        context: Context,
        enabled: Boolean,
        gamePackage: String? = null,
        gameName: String? = null
    ) {
        com.example.service.SpeedrunTimerOverlayService.setEnabled(
            context = context,
            enabled = enabled,
            gamePackage = gamePackage ?: _selectedTargetGame.value?.packageName,
            gameName = gameName ?: _selectedTargetGame.value?.appName
        )
        _overlayConfig.value = _overlayConfig.value.copy(speedrunTimerEnabled = enabled)
        com.example.ui.components.ActionFeedbackController.emitFeedback(
            context = context,
            message = if (enabled) {
                "Anti-Cheat Speedrun Timer Overlay Active"
            } else {
                "Speedrun Timer Overlay Closed"
            },
            accentColorArgb = if (enabled) 0xFF00E676L else 0xFF64748BL
        )
        scheduleCloudSettingsSync()
    }

    fun toggleOverlayAutoScale(context: Context, enabled: Boolean) {
        _overlayConfig.value = _overlayConfig.value.copy(autoScaleSmallScreens = enabled)
        context.getSharedPreferences("gmax_hud_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("hud_auto_scale", enabled).apply()
        syncOverlayService(context)
        scheduleCloudSettingsSync()
    }

    fun setFpsMode(context: Context, mode: String) {
        val normalized = com.example.util.EfpsSamplingProfile.fromKey(mode).key
        _overlayConfig.value = _overlayConfig.value.copy(fpsMode = normalized)
        com.example.util.EfpsEngine.setSamplingProfile(context, normalized)
        syncOverlayService(context)
        scheduleCloudSettingsSync()
    }

    fun setTargetFpsCap(context: Context, capMode: String) {
        val normalized = capMode.uppercase().let {
            if (it in com.example.util.EfpsEngine.SUPPORTED_CAP_MODES) it else "AUTO"
        }
        _overlayConfig.value = _overlayConfig.value.copy(targetCapMode = normalized)
        com.example.util.EfpsEngine.setTargetCapMode(context, normalized)
        syncOverlayService(context)
        scheduleCloudSettingsSync()
    }

    /**
     * Generates the user's current account-unique v2 HUD + Crosshair + Protection + Recorder + Tweaks JSON string.
     */
    fun generateCurrentConfigJson(
        accountKeyOverride: String? = null,
        creatorHandleOverride: String? = null
    ): String {
        val activeAcct = accountKeyOverride?.takeIf { it.isNotBlank() } ?: getAccountKey()
        val activeHandle = creatorHandleOverride?.takeIf { it.isNotBlank() }
            ?: _userUsername.value.ifBlank { _customDisplayName.value.ifBlank { activeAcct } }
        val (hudX, hudY) = com.example.util.ConfigCodeCodec.getSavedHudPosition(context, activeAcct)
        val recSettings = try {
            com.example.util.ScreenRecordingManager.loadSettings(context)
        } catch (_: Exception) {
            com.example.model.VideoQualitySettings()
        }
        val replayState = com.example.util.InstantReplayBufferManager.state.value
        val volLock = com.example.service.GamingVolumeLockManager.state.value
        val hardcore = com.example.service.HardcoreCallBlockerManager.state.value
        val speedrun = com.example.service.SpeedrunTimerOverlayService.state.value
        val ledHex = hubPrefs.getString("hub_controller_led_hex", "#00E5FF") ?: "#00E5FF"

        val isBoostCurrentlyActive = boosterSessionManager.isSessionActive() || _sessionUiState.value.isActive

        return com.example.util.ConfigCodeCodec.toJsonString(
            crosshair = _overlaySettings.value,
            hud = _overlayConfig.value,
            hudX = hudX,
            hudY = hudY,
            controllerConfig = _controllerOverlayConfig.value,
            controllerLayoutMode = _controllerLayoutMode.value,
            accountKey = activeAcct,
            creatorHandle = activeHandle,
            lightbarColorHex = ledHex,
            volumeLockEnabled = volLock.isEnabled,
            volumeLockPercent = volLock.lockedVolumePercent,
            hardcoreModeEnabled = hardcore.isEnabled,
            recorderResolutionLabel = recSettings.resolutionLabel,
            recorderFps = recSettings.fps,
            recorderBitrateMbps = recSettings.bitrateMbps,
            recorderMicEnabled = recSettings.recordMic,
            recorderBubbleSizeDp = recSettings.bubbleSizeDp,
            instantReplay30sEnabled = replayState.isBufferEnabled,
            boostModeName = _selectedBoostMode.value.name,
            boostTimerActive = isBoostCurrentlyActive,
            appliedOptionIds = _appliedOptionIds.value,
            speedrunTimerCategory = speedrun.targetGameName.ifBlank { "Any%" },
            speedrunTamperLockEnabled = speedrun.noPauseLockActive
        )
    }

    /**
     * Generates a compressed, account-unique `GMAX-...` v2 Base64 Config Code for sharing in WhatsApp, Discord, or TikTok.
     */
    fun generateCurrentConfigCode(
        accountKeyOverride: String? = null,
        creatorHandleOverride: String? = null
    ): String {
        val activeAcct = accountKeyOverride?.takeIf { it.isNotBlank() } ?: getAccountKey()
        val json = generateCurrentConfigJson(
            accountKeyOverride = activeAcct,
            creatorHandleOverride = creatorHandleOverride
        )
        val code = com.example.util.ConfigCodeCodec.encodeJsonToBase64Code(json)
        com.example.util.ConfigCodeCodec.saveAccountConfigCode(context, activeAcct, code)
        return code
    }

    /**
     * Decodes a pasted `GMAX-...` Base64 Config Code (or JSON string) and applies the HUD layout,
     * X/Y coordinates, Controller Overlay, Fine-Tuned Tactical Crosshair, Volume Lock, Hardcore Mode,
     * Screen Recorder, Performance Tweaks, and automatically starts the Boost Timer if the creator
     * was actively running a Boost Timer when they shared the code.
     */
    fun applyDecodedConfigCode(rawInput: String): com.example.util.DecodedConfigPayload? {
        val decoded = com.example.util.ConfigCodeCodec.decodeConfigCode(
            rawInput = rawInput,
            fallbackCrosshair = _overlaySettings.value,
            fallbackHud = _overlayConfig.value,
            fallbackController = _controllerOverlayConfig.value
        ) ?: return null

        val activeAcct = getAccountKey()

        // 1. Ignore legacy crosshair settings on import and keep crosshair disabled
        _overlaySettings.value = _overlaySettings.value.copy(isEnabled = false)
        val currentApplied = (if (decoded.appliedOptionIds.isNotEmpty()) {
            decoded.appliedOptionIds.toMutableSet()
        } else {
            _appliedOptionIds.value.toMutableSet()
        })
        currentApplied.remove("tweak_crosshair")
        _appliedOptionIds.value = currentApplied
        hubPrefs.edit().putStringSet("hub_applied_option_ids", currentApplied).apply()

        // 2. Apply Floating Gaming HUD & X/Y position + Volume Lock & Hardcore Mode
        com.example.util.ConfigCodeCodec.saveHudPosition(context, decoded.hudPositionX, decoded.hudPositionY, activeAcct)
        com.example.service.GamingVolumeLockManager.setEnabled(context, decoded.volumeLockEnabled)
        com.example.service.GamingVolumeLockManager.setLockedVolumePercent(context, decoded.volumeLockPercent)
        com.example.service.HardcoreCallBlockerManager.setEnabled(context, decoded.hardcoreModeEnabled)

        _overlayConfig.value = decoded.hudConfig.copy(
            gamingVolumeLockEnabled = decoded.volumeLockEnabled,
            gamingVolumeLockPercent = decoded.volumeLockPercent,
            hardcoreModeEnabled = decoded.hardcoreModeEnabled
        )
        context.getSharedPreferences("gmax_hud_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("hud_show_ram_pct", decoded.hudConfig.showRamPct)
            .putBoolean("hud_show_clock", decoded.hudConfig.showClock)
            .putBoolean(com.example.util.ConfigCodeCodec.KEY_HUD_SHOW_NET_SPEED, decoded.hudConfig.showNetSpeed)
            .putBoolean("hud_auto_scale", decoded.hudConfig.autoScaleSmallScreens)
            .putString("hud_fps_mode", decoded.hudConfig.fpsMode)
            .putString("hud_target_cap", decoded.hudConfig.targetCapMode)
            .apply()
        syncOverlayService(context)

        // 3. Apply Controller Overlay scale, opacity, X/Y position & RGB Lightbar
        _controllerLayoutMode.value = decoded.controllerLayoutMode
        com.example.service.ControllerOverlayService.saveLayoutMode(context, decoded.controllerLayoutMode)
        _controllerOverlayConfig.value = decoded.controllerConfig
        com.example.service.ControllerOverlayService.saveConfig(context, decoded.controllerConfig)
        com.example.service.ControllerOverlayService.syncService(context)
        if (decoded.lightbarColorHex.isNotBlank()) {
            controllerHealthManager.setLedColor(decoded.lightbarColorHex)
            hubPrefs.edit().putString("hub_controller_led_hex", decoded.lightbarColorHex).apply()
        }

        // 4. Apply Recorder settings
        try {
            val currentRec = com.example.util.ScreenRecordingManager.loadSettings(context)
            val matchedPreset = com.example.util.ScreenRecordingManager.ALL_PRESETS.find {
                it.label.equals(decoded.recorderResolutionLabel, ignoreCase = true)
            }
            val updatedRec = currentRec.copy(
                resolutionLabel = matchedPreset?.label ?: decoded.recorderResolutionLabel,
                width = matchedPreset?.width ?: currentRec.width,
                height = matchedPreset?.height ?: currentRec.height,
                fps = decoded.recorderFps.coerceIn(15, 240),
                bitrateMbps = decoded.recorderBitrateMbps.coerceIn(1, 100),
                recordMic = decoded.recorderMicEnabled,
                bubbleSizeDp = decoded.recorderBubbleSizeDp.coerceIn(28, 64)
            )
            com.example.util.ScreenRecordingManager.saveSettings(context, updatedRec)
        } catch (_: Exception) {}

        // 5. Apply Focus Mode, auto-start Overlay and Focus Mode Timer if creator had it active, & Speedrun preferences
        _selectedBoostMode.value = BoostMode.BEAST
        hubPrefs.edit().putString("hub_selected_boost_mode", BoostMode.BEAST.name).apply()

        if (decoded.boostTimerActive) {
            val targetApp = _selectedTargetGame.value ?: resolveFocusedOrDefaultGame()
            val targetPkg = targetApp?.packageName ?: context.packageName
            val targetName = targetApp?.appName ?: "GMax Hub"
            boosterSessionManager.startSession(BoostMode.BEAST, targetPkg, targetName)
            updateSessionState()
            syncOverlayService(context)
        }

        try {
            if (decoded.speedrunTimerCategory.isNotBlank()) {
                com.example.service.SpeedrunTimerOverlayService.setEnabled(
                    context = context,
                    enabled = com.example.service.SpeedrunTimerOverlayService.state.value.isEnabled,
                    gameName = decoded.speedrunTimerCategory
                )
            }
        } catch (_: Exception) {}

        com.example.ui.components.ActionFeedbackController.emitFeedback(
            context = context,
            message = decoded.summaryLabelEn,
            accentColorArgb = 0xFF00E676L,
            playHaptic = true
        )
        scheduleCloudSettingsSync()
        return decoded
    }

    private fun syncOverlayService(context: Context) {
        val cfg = _overlayConfig.value
        val shouldRun = cfg.isEnabled && SystemSettingsHelper.canDrawOverlays(context)
        val intent = Intent(context, GamingOverlayService::class.java).apply {
            action = if (shouldRun) GamingOverlayService.ACTION_START else GamingOverlayService.ACTION_STOP
            putExtra(GamingOverlayService.EXTRA_SHOW_FPS, cfg.showFps && cfg.isEnabled)
            putExtra(GamingOverlayService.EXTRA_SHOW_TEMP, cfg.showTemp && cfg.isEnabled)
            putExtra(GamingOverlayService.EXTRA_SHOW_BATTERY, cfg.showBattery && cfg.isEnabled)
            putExtra(GamingOverlayService.EXTRA_SHOW_TIMER, cfg.showTimer && cfg.isEnabled)
            putExtra(GamingOverlayService.EXTRA_SHOW_RAM_PCT, cfg.showRamPct && cfg.isEnabled)
            putExtra(GamingOverlayService.EXTRA_SHOW_CLOCK, cfg.showClock && cfg.isEnabled)
            putExtra(GamingOverlayService.EXTRA_SHOW_NET_SPEED, cfg.showNetSpeed && cfg.isEnabled)
            putExtra(GamingOverlayService.EXTRA_AUTO_SCALE, cfg.autoScaleSmallScreens)
            putExtra("EXTRA_FPS_MODE", cfg.fpsMode)
            putExtra("EXTRA_TARGET_CAP", cfg.targetCapMode)
        }
        try {
            if (shouldRun) {
                context.startService(intent)
            } else {
                context.stopService(intent)
            }
        } catch (_: Exception) {}
    }


    fun toggleLowLatencyWifi(enabled: Boolean? = null) {
        val applied = _appliedOptionIds.value.contains("net_low_latency")
        val target = enabled ?: !applied
        networkTester.setLowLatencyWifiLock(target)
        if (target) {
            _appliedOptionIds.value = _appliedOptionIds.value + "net_low_latency"
            _boostFeedback.value = "Radio Low-Latency Lock acquired. Wi-Fi packet prioritization active."
        } else {
            _appliedOptionIds.value = _appliedOptionIds.value - "net_low_latency"
            _boostFeedback.value = "Radio Low-Latency Lock released."
        }
    }

    private fun setOptionAppliedState(optionId: String, applied: Boolean) {
        val current = _appliedOptionIds.value.toMutableSet()
        if (applied) {
            current.add(optionId)
        } else {
            current.remove(optionId)
        }
        _appliedOptionIds.value = current
        hubPrefs.edit().putStringSet("hub_applied_option_ids", current).apply()
        updateReadinessScore()
        scheduleCloudSettingsSync()
    }

    fun executePerformanceOption(
        context: Context,
        option: com.example.model.PerformanceOption,
        onOpenModal: () -> Unit
    ) {
        when (option.id) {
            "pop_3_ram" -> {
                val isCurrentlyApplied = _appliedOptionIds.value.contains(option.id)
                if (isCurrentlyApplied) {
                    setOptionAppliedState(option.id, false)
                    _boostFeedback.value = "Kernel RAM Purge & Memory Compaction reset to standby."
                    com.example.ui.components.ActionFeedbackController.emitFeedback(
                        context = context,
                        message = "RAM Compaction Standby",
                        playHaptic = true
                    )
                } else {
                    setOptionAppliedState(option.id, true)
                    cleanRamAndCacheNow()
                }
            }
            "pop_2_dnd", "feat_dnd_session" -> {
                if (SystemSettingsHelper.isNotificationPolicyAccessGranted(context)) {
                    val currentApplied = _appliedOptionIds.value.contains(option.id)
                    val newTarget = !currentApplied
                    SystemSettingsHelper.setDoNotDisturb(context, newTarget)
                    setOptionAppliedState(option.id, newTarget)
                    val msg = if (newTarget) {
                        "Focus Shield Active: Do Not Disturb enabled. Call banners & popups suppressed."
                    } else {
                        "Focus Shield Disabled: Normal notification filter restored."
                    }
                    _boostFeedback.value = msg
                    com.example.ui.components.ActionFeedbackController.emitFeedback(
                        context = context,
                        message = if (newTarget) "DND Focus Shield Active" else "DND Focus Shield Off",
                        accentColorArgb = if (newTarget) 0xFF00E676L else 0xFF00E5FFL,
                        playHaptic = true
                    )
                } else {
                    _boostFeedback.value = "Notification Policy Access required. Opening system settings..."
                    com.example.ui.components.ActionFeedbackController.emitFeedback(
                        context = context,
                        message = "Grant DND Access in Settings",
                        playHaptic = true
                    )
                    SystemSettingsHelper.openNotificationPolicyAccess(context)
                }
            }
            "net_low_latency" -> {
                val currentApplied = _appliedOptionIds.value.contains(option.id)
                val newTarget = !currentApplied
                networkTester.setLowLatencyWifiLock(newTarget)
                try {
                    if (newTarget) {
                        android.net.TrafficStats.setThreadStatsTag(0x474D4158) // "GMAX" low-jitter tag
                    } else {
                        android.net.TrafficStats.clearThreadStatsTag()
                    }
                } catch (_: Exception) {}
                setOptionAppliedState(option.id, newTarget)
                _boostFeedback.value = if (newTarget) {
                    "Radio Low-Latency Lock (WIFI_MODE_FULL_LOW_LATENCY) acquired. Wi-Fi packet prioritization active."
                } else {
                    "Radio Low-Latency Lock released."
                }
                com.example.ui.components.ActionFeedbackController.emitFeedback(
                    context = context,
                    message = if (newTarget) "Low-Latency Wi-Fi Lock Active" else "Wi-Fi Lock Released",
                    accentColorArgb = if (newTarget) 0xFF00E676L else 0xFF00E5FFL,
                    playHaptic = true
                )
            }
            "perf_sustained_mode" -> {
                val currentApplied = _appliedOptionIds.value.contains(option.id)
                val newTarget = !currentApplied
                try {
                    val activity = context as? android.app.Activity
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        activity?.window?.setSustainedPerformanceMode(newTarget)
                    }
                    val audioMgr = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                    audioMgr?.setParameters(if (newTarget) "game_mode=1;sustained_perf=1" else "game_mode=0;sustained_perf=0")
                } catch (_: Exception) {}
                if (newTarget) {
                    com.example.util.EfpsEngine.refreshDisplayHz()
                    thermalGuard.start()
                }
                setOptionAppliedState(option.id, newTarget)
                _boostFeedback.value = if (newTarget) {
                    "Sustained Hardware Performance Mode engaged: Window sustained clock hint & thermal headroom governor active."
                } else {
                    "Sustained Hardware Performance Mode released: Dynamic governor restored."
                }
                com.example.ui.components.ActionFeedbackController.emitFeedback(
                    context = context,
                    message = if (newTarget) "Sustained Performance Governor On" else "Sustained Governor Off",
                    accentColorArgb = if (newTarget) 0xFF00E676L else 0xFF00E5FFL,
                    playHaptic = true
                )
            }
            "perf_thread_priority" -> {
                val currentApplied = _appliedOptionIds.value.contains(option.id)
                val newTarget = !currentApplied
                try {
                    android.os.Process.setThreadPriority(
                        if (newTarget) android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY
                        else android.os.Process.THREAD_PRIORITY_DEFAULT
                    )
                } catch (_: Exception) {}
                setOptionAppliedState(option.id, newTarget)
                _boostFeedback.value = if (newTarget) {
                    "Thread Scheduler Priority: Foreground render thread elevated to URGENT_DISPLAY (-8)."
                } else {
                    "Thread Scheduler Priority: Restored to default Linux CFS priority (0)."
                }
                com.example.ui.components.ActionFeedbackController.emitFeedback(
                    context = context,
                    message = if (newTarget) "Urgent Display Priority (-8) Active" else "Default Thread Priority Restored",
                    accentColorArgb = if (newTarget) 0xFF00E676L else 0xFF00E5FFL,
                    playHaptic = true
                )
            }
            "ctrl_overlay_hud" -> {
                val targetState = !_overlayConfig.value.isEnabled
                if (targetState && !SystemSettingsHelper.canDrawOverlays(context)) {
                    SystemSettingsHelper.openOverlayPermissionSettings(context)
                    setOptionAppliedState(option.id, false)
                    _boostFeedback.value = "Grant 'Display over other apps' permission to enable Real-Time Screen HUD."
                    return
                }
                toggleOverlayEnabled(context, targetState)
                setOptionAppliedState(option.id, _overlayConfig.value.isEnabled)
                _boostFeedback.value = if (_overlayConfig.value.isEnabled) {
                    "Real-Time Screen HUD activated on screen."
                } else {
                    "Real-Time Screen HUD closed."
                }
            }
            "tweak_crosshair", "ctrl_crosshair" -> {
                toggleCrosshair(context)
                setOptionAppliedState("tweak_crosshair", _overlaySettings.value.isEnabled)
            }
            "bg_animations" -> {
                val currentApplied = _appliedOptionIds.value.contains(option.id)
                val newTarget = !currentApplied
                if (com.example.util.SecureSettingsManager.canWriteSecureSettings(context)) {
                    val scale = if (newTarget) 0.0f else 1.0f
                    val ok = com.example.util.SecureSettingsManager.setAnimationScales(context, scale)
                    if (ok) {
                        setOptionAppliedState(option.id, newTarget)
                        _boostFeedback.value = if (newTarget) {
                            "Zero Animation Latency (0.0x Scales) applied via Secure Settings."
                        } else {
                            "System animation scales restored to 1.0x."
                        }
                        com.example.ui.components.ActionFeedbackController.emitFeedback(
                            context = context,
                            message = if (newTarget) "0.0x Animation Scales Active" else "1.0x Animation Scales Restored",
                            accentColorArgb = if (newTarget) 0xFF00E676L else 0xFF00E5FFL,
                            playHaptic = true
                        )
                    } else {
                        onOpenModal()
                    }
                } else {
                    setReducedAnimations(newTarget)
                    setOptionAppliedState(option.id, newTarget)
                    _boostFeedback.value = if (newTarget) {
                        "Zero-Lag Instant Transitions enabled in GMAX Hub (Use ADB or Developer Options for OS-wide 0.0x scales)."
                    } else {
                        "Standard transition animations restored."
                    }
                }
            }
            "net_private_dns" -> {
                val currentApplied = _appliedOptionIds.value.contains(option.id)
                val newTarget = !currentApplied
                if (com.example.util.SecureSettingsManager.canWriteSecureSettings(context)) {
                    val ok = com.example.util.SecureSettingsManager.setPrivateDns(
                        context,
                        if (newTarget) "hostname" else "opportunistic",
                        if (newTarget) "1dot1dot1dot1.cloudflare-dns.com" else ""
                    )
                    if (ok) {
                        setOptionAppliedState(option.id, newTarget)
                        _boostFeedback.value = if (newTarget) {
                            "Private DNS configured to Cloudflare 1.1.1.1 via Secure Settings."
                        } else {
                            "Private DNS restored to automatic."
                        }
                        com.example.ui.components.ActionFeedbackController.emitFeedback(
                            context = context,
                            message = if (newTarget) "1.1.1.1 Gaming DNS Active" else "Automatic DNS Restored",
                            accentColorArgb = if (newTarget) 0xFF00E676L else 0xFF00E5FFL,
                            playHaptic = true
                        )
                    } else {
                        onOpenModal()
                    }
                } else {
                    try {
                        val dnsIntent = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(dnsIntent)
                        com.example.ui.components.copyToClipboard(
                            context,
                            "1dot1dot1dot1.cloudflare-dns.com",
                            "Copied 1dot1dot1dot1.cloudflare-dns.com! Paste into Private DNS."
                        )
                        setOptionAppliedState(option.id, newTarget)
                        _boostFeedback.value = "Copied '1dot1dot1dot1.cloudflare-dns.com' to clipboard & opened Network Settings."
                    } catch (_: Exception) {
                        onOpenModal()
                    }
                }
            }
            else -> {
                onOpenModal()
            }
        }
    }

    fun toggleGamingSession() {
        val newState = !_gamingSessionActive.value
        _gamingSessionActive.value = newState
        if (newState) {
            SystemSettingsHelper.setDoNotDisturb(context, true)
            networkTester.setLowLatencyWifiLock(true)
            quickBoost()
        } else {
            SystemSettingsHelper.setDoNotDisturb(context, false)
            networkTester.setLowLatencyWifiLock(false)
        }
        refreshMetrics()
    }

    fun runStorageBenchmark() {
        if (_isSpeedTesting.value) return
        viewModelScope.launch {
            _isSpeedTesting.value = true
            _storageSpeed.value = null
            val res = storageSpeedTester.runSpeedTest()
            _storageSpeed.value = res
            _isSpeedTesting.value = false
            refreshMetrics()
        }
    }

    fun toggleOverlay() {
        toggleOverlayEnabled(context, !_overlayConfig.value.isEnabled)
    }

    fun toggleCrosshair(context: Context) {
        val target = !_overlaySettings.value.isEnabled
        setCrosshairEnabled(context, target)
    }

    fun setCrosshairEnabled(context: Context, @Suppress("UNUSED_PARAMETER") enabled: Boolean) {
        _overlaySettings.value = _overlaySettings.value.copy(
            isEnabled = false,
            antiCheatNoticeAcknowledged = true
        )
        val currentApplied = _appliedOptionIds.value.toMutableSet()
        currentApplied.remove("tweak_crosshair")
        _appliedOptionIds.value = currentApplied
        com.example.service.CrosshairOverlayService.saveSettingsObject(
            context = context,
            settings = _overlaySettings.value,
            accountKeyOverride = getAccountKey()
        )
    }

    fun acknowledgeAntiCheatNotice() {
        _overlaySettings.value = _overlaySettings.value.copy(antiCheatNoticeAcknowledged = true)
    }

    fun updateCrosshairStyle(style: Int) {
        _overlaySettings.value = _overlaySettings.value.copy(crosshairStyle = style)
        syncCrosshairService()
    }

    fun updateCrosshairColor(colorLong: Long) {
        _overlaySettings.value = _overlaySettings.value.copy(crosshairColor = colorLong)
        syncCrosshairService()
    }

    fun updateCrosshairSize(sizeDp: Int) {
        _overlaySettings.value = _overlaySettings.value.copy(crosshairSizeDp = sizeDp)
        syncCrosshairService()
    }

    fun updateCrosshairAlpha(alpha: Float) {
        _overlaySettings.value = _overlaySettings.value.copy(crosshairAlpha = alpha.coerceIn(0.2f, 1.0f))
        syncCrosshairService()
    }

    fun updateCrosshairOffset(offsetXdp: Int, offsetYdp: Int) {
        _overlaySettings.value = _overlaySettings.value.copy(
            offsetXdp = offsetXdp.coerceIn(-24, 24),
            offsetYdp = offsetYdp.coerceIn(-24, 24)
        )
        syncCrosshairService()
    }

    fun updateCrosshairStroke(strokeDp: Float) {
        _overlaySettings.value = _overlaySettings.value.copy(
            strokeThicknessDp = strokeDp.coerceIn(1.0f, 6.0f)
        )
        syncCrosshairService()
    }

    fun updateCrosshairGap(gapDp: Float) {
        _overlaySettings.value = _overlaySettings.value.copy(
            centerGapDp = gapDp.coerceIn(0.0f, 16.0f)
        )
        syncCrosshairService()
    }

    fun updateCrosshairOutline(showOutline: Boolean) {
        _overlaySettings.value = _overlaySettings.value.copy(showOutline = showOutline)
        syncCrosshairService()
    }

    fun updateCrosshairCenterDot(showCenterDot: Boolean) {
        _overlaySettings.value = _overlaySettings.value.copy(showCenterDot = showCenterDot)
        syncCrosshairService()
    }

    private fun syncCrosshairService() {
        _overlaySettings.value = _overlaySettings.value.copy(isEnabled = false)
        com.example.service.CrosshairOverlayService.saveSettingsObject(
            context = context,
            settings = _overlaySettings.value,
            accountKeyOverride = getAccountKey()
        )
    }

    fun runAllPingTests() {
        if (_isPingTesting.value) return
        viewModelScope.launch {
            _isPingTesting.value = true
            val targets = networkTester.serverTargets
            val results = mutableListOf<NetworkPingResult>()
            for (target in targets) {
                results.add(networkTester.runPingTest(target))
                _pingResults.value = results.toList()
            }
            _isPingTesting.value = false
            updateReadinessScore()
        }
    }

    private fun updateReadinessScore() {
        var score = 100
        val warnings = mutableListOf<String>()
        val tips = mutableListOf<String>()

        val mem = _memoryStats.value
        val therm = thermalMonitor.thermalInfo.value
        val wifi = _wifiStatus.value

        // RAM penalty
        if (mem.ramUsedPercent > 85) {
            score -= 20
            warnings.add("RAM usage high (${mem.ramUsedPercent}%). High risk of frame spikes.")
        } else if (mem.ramUsedPercent > 70) {
            score -= 10
            tips.add("Close heavy apps or launch with Focus Mode to free cached RAM.")
        }

        // Thermal penalty
        if (therm.isThrottlingRisk || therm.batteryTempC > 40f) {
            score -= 25
            warnings.add("Thermal alert: Device temperature is ${therm.batteryTempC}°C. Throttling active!")
        } else if (therm.batteryTempC > 36f) {
            score -= 10
            tips.add("Remove phone case and lower screen brightness to prevent heating.")
        }

        // Charging warning
        if (therm.isCharging) {
            score -= 15
            warnings.add("Charging while gaming generates high heat. Enable bypass charging if supported.")
        }

        // Network check
        if (wifi.isWifi && !wifi.is5GHzOr6GHz) {
            score -= 10
            tips.add("Connected to 2.4GHz Wi-Fi. Switch to 5GHz or 6GHz band for lower packet jitter.")
        }

        // Storage check (<15% free space rule)
        if (mem.internalStorageTotalGb > 0) {
            val freeRatio = mem.internalStorageFreeGb / mem.internalStorageTotalGb
            if (freeRatio < 0.15) {
                score -= 15
                warnings.add("Low storage space (<15%). Android needs space for 3D shader compilation.")
            }
        }

        val clamped = score.coerceIn(20, 100)
        val status = when {
            clamped >= 85 -> "Optimal Gaming State"
            clamped >= 65 -> "Good (Minor Tweaks Needed)"
            else -> "Throttling or Bottleneck Risk"
        }

        _readiness.value = DeviceReadiness(
            score = clamped,
            status = status,
            warnings = warnings,
            recommendations = tips
        )
    }

    // Match Recap
    fun startManualMatchRecap(gameTitle: String = "Mobile Gaming Session") {
        val temp = thermalGuardState.value.liveTempC
        val batt = thermalGuardState.value.batteryPct
        val currentFps = efpsTelemetry.value.currentEfps.toFloat().coerceAtLeast(60f)
        matchRecapManager.startSession(gameTitle, temp, batt)
        matchRecapManager.recordSample(
            frameTimeMs = (1000f / currentFps).coerceIn(6f, 33f),
            tempC = temp,
            batteryPct = batt,
            freeRamMb = _memoryStats.value.availRamMb.toInt()
        )
    }

    fun finishManualMatchRecap(): MatchRecap {
        return matchRecapManager.finishSession()
    }

    fun shareMatchCard(context: Context, recap: MatchRecap) {
        matchRecapManager.shareMatchCard(context, recap)
    }

    // Priority Do Not Disturb (does not mute game audio)
    fun isPriorityDndGranted(): Boolean {
        return PriorityDndHelper.isPermissionGranted(context)
    }

    fun openDndSettings(context: Context) {
        PriorityDndHelper.openDndPermissionSettings(context)
    }

    fun isPriorityDndActive(): Boolean {
        return PriorityDndHelper.isPriorityDndActive(context)
    }

    fun togglePriorityDnd(enable: Boolean): Boolean {
        return if (enable) {
            PriorityDndHelper.enablePriorityDnd(context)
        } else {
            PriorityDndHelper.restorePreviousDnd(context)
        }
    }

    fun getSavedDndFilterDescription(): String {
        return PriorityDndHelper.getSavedPreviousFilterDescription(context)
    }

    // Controller Health & Diagnostics
    fun testControllerRumble(intensity: Float = 1.0f, durationMs: Long = 300) {
        controllerHealthManager.testRumble(intensity, durationMs)
    }

    fun setControllerLedColor(colorHex: String) {
        controllerHealthManager.setLedColor(colorHex)
        hubPrefs.edit().putString("hub_controller_led_hex", colorHex).apply()
        scheduleCloudSettingsSync()
    }

    fun setControllerDeadzone(deadzone: Float) {
        controllerHealthManager.userDeadzone = deadzone
    }

    fun refreshControllerState() {
        controllerHealthManager.scanActiveController()
        refreshGamepads()
    }

    fun dismissThermalAlert() {
        thermalGuard.dismissAlert()
    }

    // =========================================================================
    // Firebase Authentication & Cloud Settings Persistence
    // =========================================================================

    fun isSignedInWithGoogleOrFacebook(): Boolean {
        return _currentFirebaseUser.value != null ||
            _socialSignedInProvider.value.equals("Google", ignoreCase = true) ||
            _socialSignedInProvider.value.equals("Facebook", ignoreCase = true) ||
            _isVerifiedDeveloperAccount.value
    }

    fun resolveActiveLeaderboardAuthProvider(): String {
        return if (_socialSignedInProvider.value.equals("Facebook", ignoreCase = true)) {
            "Facebook"
        } else {
            "Google"
        }
    }

    fun resolveActiveLeaderboardUserId(): String {
        val fbUser = _currentFirebaseUser.value
        if (fbUser != null && fbUser.uid.isNotBlank()) {
            return fbUser.uid
        }
        if (!isSignedInWithGoogleOrFacebook()) {
            return "guest"
        }
        val providerPrefix = if (_socialSignedInProvider.value.equals("Facebook", ignoreCase = true)) {
            "fb"
        } else {
            "google"
        }
        val rawIdentity = _customEmail.value.takeIf { it.isNotBlank() }
            ?: _userUsername.value.takeIf { it.isNotBlank() }
            ?: _customDisplayName.value.takeIf { it.isNotBlank() }
            ?: "player"
        val cleanSlug = rawIdentity.lowercase(java.util.Locale.US)
            .trim()
            .replace("[^a-z0-9_]".toRegex(), "_")
            .take(64)
            .ifBlank { "player" }
        return "acct_${providerPrefix}_$cleanSlug"
    }

    fun continueAsGuest() {
        _currentFirebaseUser.value = null
        _socialSignedInProvider.value = ""
        _hasCompletedAuthStep.value = true
        hubPrefs.edit()
            .putBoolean("hub_auth_step_completed", true)
            .remove("hub_social_auth_provider")
            .apply()
        _cloudSyncStatus.value = "Guest Mode (Local Storage Only)"
        try {
            com.example.util.DailyStreakManager.switchAccount(context, getAccountKey())
        } catch (_: Exception) {}
    }

    fun onUserSignedInSuccess(providerLabel: String = "Google") {
        val normalizedProvider = if (providerLabel.equals("Facebook", ignoreCase = true)) {
            "Facebook"
        } else {
            "Google"
        }
        val user = try { Firebase.auth.currentUser } catch (_: Exception) { null }
        _currentFirebaseUser.value = user
        _socialSignedInProvider.value = normalizedProvider
        _hasCompletedAuthStep.value = true
        hubPrefs.edit()
            .putBoolean("hub_auth_step_completed", true)
            .putString("hub_social_auth_provider", normalizedProvider)
            .apply()
        reloadAccountMedia(user)
        if (user != null) {
            startCloudProfileSyncForUser(user)
        } else {
            _cloudSyncStatus.value = "Signed in with $normalizedProvider • Profile Active"
        }
    }

    fun onSocialAccountSignedIn(providerLabel: String, fullName: String, email: String) {
        val normalizedProvider = if (providerLabel.equals("Facebook", ignoreCase = true)) {
            "Facebook"
        } else {
            "Google"
        }
        val cleanName = fullName.trim().ifBlank { "$normalizedProvider Gamer" }
        val cleanEmail = email.trim().ifBlank { "gamer@${normalizedProvider.lowercase(java.util.Locale.US)}.com" }
        val newAcctKey = getAccountKey(cleanEmail, null)

        val savedAcctUsername = hubPrefs.getString("hub_username_$newAcctKey", "") ?: ""
        val savedAcctBio = hubPrefs.getString("hub_bio_$newAcctKey", "") ?: ""
        setCustomProfileCredentials(cleanName, cleanEmail)

        val resolvedHandle = if (savedAcctUsername.isNotBlank()) {
            savedAcctUsername
        } else {
            com.example.util.PremadeTagsCatalog.sanitizeUsername(
                cleanName.lowercase(java.util.Locale.US).replace(" ", "_"),
                cleanName,
                _isVerifiedDeveloperAccount.value
            )
        }
        _userUsername.value = resolvedHandle
        _userBio.value = savedAcctBio
        hubPrefs.edit()
            .putString("hub_user_username", resolvedHandle)
            .putString("hub_username_$newAcctKey", resolvedHandle)
            .putString("hub_user_bio", savedAcctBio)
            .apply()

        onUserSignedInSuccess(normalizedProvider)
    }

    fun onUserSignedOut() {
        profileObserverJob?.cancel()
        profileObserverJob = null
        _currentFirebaseUser.value = null
        _socialSignedInProvider.value = ""
        _cloudProfile.value = null
        _customEmail.value = ""
        _customDisplayName.value = ""
        _userAvatarUrl.value = ""
        _userBannerUrl.value = "cyber_neon_grid"
        _userBio.value = ""
        _isVerifiedDeveloperAccount.value = false
        hubPrefs.edit()
            .remove("hub_user_custom_email")
            .remove("hub_user_custom_name")
            .remove("hub_social_auth_provider")
            .putString("hub_user_bio", "")
            .apply()
        _cloudSyncStatus.value = "Signed out, using local device settings"
        try {
            com.example.util.DailyStreakManager.switchAccount(context, "guest")
            com.example.util.GamePlayStatsHelper.setActiveAccountKey(context, "guest")
            refreshPlayStats()
        } catch (_: Exception) {}
    }

    fun deleteCurrentAccount(onComplete: (Boolean, String?) -> Unit) {
        val repo = userAccountRepository
        val key = getAccountKey()
        viewModelScope.launch {
            try {
                if (repo != null && _currentFirebaseUser.value != null) {
                    val result = repo.deleteUserProfileAndAccount()
                    if (result.isFailure) {
                        onComplete(false, result.exceptionOrNull()?.message ?: "Failed to delete remote cloud profile")
                        return@launch
                    }
                }
                // Wipe local account profile prefs
                hubPrefs.edit()
                    .remove("hub_avatar_$key")
                    .remove("hub_banner_$key")
                    .remove("hub_username_$key")
                    .remove("hub_bio_$key")
                    .remove("hub_user_custom_email")
                    .remove("hub_user_custom_name")
                    .remove("hub_user_bio")
                    .remove("hub_user_dob")
                    .remove("hub_user_selected_tags_csv")
                    .apply()

                onUserSignedOut()
                onComplete(true, null)
            } catch (e: Exception) {
                onComplete(false, e.localizedMessage ?: "Account deletion failed")
            }
        }
    }

    fun setUserAvatar(urlOrUri: String) {
        val raw = urlOrUri.trim()
        val key = getAccountKey()
        // If picked from Android Photo Picker (content://), copy to persistent internal storage so it never expires
        var localPersistentUri = raw
        var cloudSafeAvatar = raw
        if (raw.startsWith("content://")) {
            try {
                val parsedUri = android.net.Uri.parse(raw)
                val avatarsDir = java.io.File(context.filesDir, "avatars").apply { mkdirs() }
                val safeFileKey = key.replace(Regex("[^a-zA-Z0-9_]"), "_").take(40).ifBlank { "default" }
                val destFile = java.io.File(avatarsDir, "avatar_$safeFileKey.jpg")
                context.contentResolver.openInputStream(parsedUri)?.use { input ->
                    val bitmap = android.graphics.BitmapFactory.decodeStream(input)
                    if (bitmap != null) {
                        java.io.FileOutputStream(destFile).use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, out)
                        }
                        localPersistentUri = android.net.Uri.fromFile(destFile).toString()

                        // Also create a compact Base64 data URI (< 950 chars) so Firestore /usernames can sync custom avatars across devices
                        val thumb = android.graphics.Bitmap.createScaledBitmap(bitmap, 36, 36, true)
                        val baos = java.io.ByteArrayOutputStream()
                        @Suppress("DEPRECATION")
                        thumb.compress(android.graphics.Bitmap.CompressFormat.WEBP, 55, baos)
                        val b64 = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
                        val dataUri = "data:image/webp;base64,$b64"
                        if (dataUri.length <= 980) {
                            cloudSafeAvatar = dataUri
                        } else {
                            cloudSafeAvatar = localPersistentUri
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        _userAvatarUrl.value = localPersistentUri
        hubPrefs.edit().putString("hub_avatar_$key", localPersistentUri).apply()
        syncCurrentAccountToLocalPlayerDirectory()
        val user = _currentFirebaseUser.value
        if (user != null && userAccountRepository != null) {
            viewModelScope.launch {
                userAccountRepository.updatePhotoUrl(cloudSafeAvatar.take(1000))
            }
        }
        scheduleCloudSettingsSync()
    }

    fun setUserBanner(bannerKeyOrUri: String) {
        val safe = bannerKeyOrUri.trim()
        _userBannerUrl.value = safe
        val key = getAccountKey()
        hubPrefs.edit().putString("hub_banner_$key", safe).apply()
        val user = _currentFirebaseUser.value
        if (user != null && userAccountRepository != null) {
            viewModelScope.launch {
                userAccountRepository.updateBannerUrl(safe)
            }
        }
        scheduleCloudSettingsSync()
    }

    fun setUserDateOfBirth(dob: String) {
        val safeDob = dob.trim().ifEmpty { "Not Set" }.take(40)
        _userDateOfBirth.value = safeDob
        hubPrefs.edit().putString("hub_user_dob", safeDob).apply()
        scheduleCloudSettingsSync()
    }

    fun setUserBio(bio: String) {
        val safeBio = bio.trim().take(300)
        _userBio.value = safeBio
        val key = getAccountKey()
        hubPrefs.edit()
            .putString("hub_user_bio", safeBio)
            .putString("hub_bio_$key", safeBio)
            .apply()
        syncCurrentAccountToLocalPlayerDirectory()
        val user = _currentFirebaseUser.value
        val repo = userAccountRepository
        if (user != null && repo != null) {
            viewModelScope.launch {
                repo.updateBio(safeBio)
            }
        }
        scheduleCloudSettingsSync()
    }

    fun setSelectedProfileTags(tagIds: List<String>) {
        val sanitized = com.example.util.PremadeTagsCatalog.sanitizeSelectedTags(tagIds)
        _selectedProfileTags.value = sanitized
        hubPrefs.edit().putString("hub_user_selected_tags_csv", sanitized.joinToString(",")).apply()
        scheduleCloudSettingsSync()
    }

    fun toggleProfileTag(tagId: String): Boolean {
        val current = _selectedProfileTags.value.toMutableList()
        if (current.contains(tagId)) {
            current.remove(tagId)
            setSelectedProfileTags(current)
            return true
        }
        if (current.size >= com.example.util.PremadeTagsCatalog.MAX_EQUIPPED_TAGS) {
            return false
        }
        if (tagId in com.example.util.PremadeTagsCatalog.VALID_TAG_IDS) {
            current.add(tagId)
            setSelectedProfileTags(current)
            return true
        }
        return false
    }

    fun clearUsernameError() {
        _usernameError.value = null
    }

    fun updateUsername(
        rawNewUsername: String,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val isDev = _isVerifiedDeveloperAccount.value
        val validation = com.example.util.PremadeTagsCatalog.validateUsername(rawNewUsername, isDev)
        if (validation.isFailure) {
            val msg = validation.exceptionOrNull()?.message ?: "Invalid username format"
            _usernameError.value = msg
            onComplete(false, msg)
            return
        }
        val cleanUsername = validation.getOrThrow()
        val prevUsername = _userUsername.value
        val acctKey = getAccountKey()

        val user = _currentFirebaseUser.value
        val repo = userAccountRepository
        if (user != null && repo != null) {
            viewModelScope.launch {
                _isCheckingUsername.value = true
                _usernameError.value = null
                val claimResult = repo.claimAndSaveUsername(cleanUsername, prevUsername)
                _isCheckingUsername.value = false
                if (claimResult.isSuccess) {
                    val claimed = claimResult.getOrNull() ?: cleanUsername
                    _userUsername.value = claimed
                    hubPrefs.edit()
                        .putString("hub_user_username", claimed)
                        .putString("hub_username_$acctKey", claimed)
                        .apply()
                    scheduleCloudSettingsSync()
                    onComplete(true, "Username @$claimed saved!")
                } else {
                    val err = claimResult.exceptionOrNull()?.message ?: "Could not claim username."
                    _usernameError.value = err
                    onComplete(false, err)
                }
            }
        } else {
            _usernameError.value = null
            _userUsername.value = cleanUsername
            hubPrefs.edit()
                .putString("hub_user_username", cleanUsername)
                .putString("hub_username_$acctKey", cleanUsername)
                .apply()
            onComplete(true, "Username @$cleanUsername saved!")
        }
    }

    fun setUserCountry(countryCode: String, countryName: String) {
        val safeCode = countryCode.trim().uppercase().ifEmpty { "US" }
        val safeName = countryName.trim().ifEmpty { "United States" }
        _userCountryCode.value = safeCode
        _userCountryName.value = safeName
        hubPrefs.edit()
            .putString("hub_user_country_code", safeCode)
            .putString("hub_user_country_name", safeName)
            .apply()
        val user = _currentFirebaseUser.value
        val repo = userAccountRepository
        if (user != null && repo != null) {
            viewModelScope.launch {
                repo.updateCountry(safeCode, safeName)
            }
        }
        scheduleCloudSettingsSync()
    }

    private fun syncCurrentAccountToLocalPlayerDirectory() {
        try {
            val localUsername = _userUsername.value.trim().removePrefix("@")
            val localDisplayName = _customDisplayName.value.takeIf { it.isNotBlank() }
                ?: _currentFirebaseUser.value?.displayName?.takeIf { it.isNotBlank() }
                ?: localUsername.replace("_", " ").ifBlank { "G-Max Gamer" }
            val effectiveLocalHandle = com.example.util.PremadeTagsCatalog.sanitizeUsername(
                localUsername,
                localDisplayName,
                _isVerifiedDeveloperAccount.value
            )
            if (effectiveLocalHandle.length < 2) return
            val topPlayed = _mostPlayedGames.value.firstOrNull()
            val mostPlayedName = topPlayed?.game?.appName
                ?: _cloudProfile.value?.mostPlayedGameName
                ?: "No games played yet"
            val mostPlayedMins = if (topPlayed != null) {
                (topPlayed.totalPlayTimeMs / 60_000L).coerceAtLeast(1L)
            } else {
                _cloudProfile.value?.mostPlayedMinutes ?: 0L
            }
            val code = _userCountryCode.value.ifBlank { "US" }
            val cName = _userCountryName.value.ifBlank { "United States" }
            val actualGamerscore = maxOf(
                gamerscoreData.value.totalScore,
                _cloudProfile.value?.gamerscore ?: 0L
            )
            val claim = com.example.data.repository.UsernameClaim(
                id = effectiveLocalHandle.lowercase(),
                userId = resolveActiveLeaderboardUserId(),
                username = effectiveLocalHandle,
                displayName = localDisplayName,
                bio = _userBio.value,
                photoUrl = _userAvatarUrl.value.ifBlank { _cloudProfile.value?.photoUrl.orEmpty() },
                bannerUrl = _userBannerUrl.value,
                countryCode = code,
                countryName = cName,
                gamerscore = actualGamerscore,
                mostPlayedGameName = mostPlayedName,
                mostPlayedMinutes = mostPlayedMins,
                totalPlayTimeMinutes = maxOf(_totalPlayTimeSummary.value.totalMinutes, _cloudProfile.value?.totalPlayTimeMinutes ?: 0L),
                selectedTags = _selectedProfileTags.value,
                isVerifiedDeveloper = _isVerifiedDeveloperAccount.value
            )
            com.example.data.repository.UserAccountRepository.upsertLocalPlayerDirectoryEntry(context, claim)
        } catch (_: Exception) {}
    }

    /**
     * Searches for player profiles by `@username` or shared `displayName` and returns the closest
     * matching accounts ranked by similarity score across all unique user accounts (cloud `/usernames`,
     * multi-account local directory, current account, and creator account) with each user's actual
     * account Gamerscore and profile picture (`photoUrl`).
     */
    suspend fun searchClosestPlayerProfiles(
        rawQuery: String,
        maxResults: Int = 8
    ): Result<List<com.example.util.ExternalPlayerProfileData>> {
        val cleanQuery = rawQuery.trim().removePrefix("@")
        if (cleanQuery.length < 2) {
            return Result.failure(IllegalArgumentException("Enter at least 2 characters to search."))
        }

        syncCurrentAccountToLocalPlayerDirectory()
        val candidatesByHandle = LinkedHashMap<String, com.example.util.ExternalPlayerProfileData>()
        val currentUid = _currentFirebaseUser.value?.uid ?: ""
        val currentActualGamerscore = maxOf(
            gamerscoreData.value.totalScore,
            _cloudProfile.value?.gamerscore ?: 0L
        )
        val currentActualAvatar = _userAvatarUrl.value.ifBlank {
            _cloudProfile.value?.photoUrl.orEmpty().ifBlank {
                _currentFirebaseUser.value?.photoUrl?.toString().orEmpty()
            }
        }

        // 1. Current user profile candidate (always uses real account gamerscore & avatar)
        val localUsername = _userUsername.value.trim().removePrefix("@")
        val localDisplayName = _customDisplayName.value.takeIf { it.isNotBlank() }
            ?: _currentFirebaseUser.value?.displayName?.takeIf { it.isNotBlank() }
            ?: localUsername.replace("_", " ").ifBlank { "G-Max Gamer" }
        val effectiveLocalHandle = com.example.util.PremadeTagsCatalog.sanitizeUsername(
            localUsername,
            localDisplayName,
            _isVerifiedDeveloperAccount.value
        ).ifBlank { "gmax_gamer" }

        val localScoreInfo = com.example.util.GamerscoreEngine.calculateGamerscoreFromVerifiedScore(currentActualGamerscore)
        val code = _userCountryCode.value.ifBlank { "US" }
        val cName = _userCountryName.value.ifBlank { "United States" }
        candidatesByHandle[effectiveLocalHandle.lowercase()] = com.example.util.ExternalPlayerProfileData(
            userId = resolveActiveLeaderboardUserId(),
            displayName = localDisplayName,
            username = effectiveLocalHandle,
            authProvider = resolveActiveLeaderboardAuthProvider(),
            photoUrl = currentActualAvatar,
            countryCode = code,
            countryName = cName,
            flagEmoji = com.example.util.CountryCatalog.codeToFlagEmoji(code),
            bio = _userBio.value.ifBlank { "Verified G-Max player (@$effectiveLocalHandle) from $cName." },
            gamerscore = currentActualGamerscore,
            rankTitle = localScoreInfo.rankTitle,
            rankTier = localScoreInfo.rankTier,
            selectedTags = _selectedProfileTags.value,
            isVerifiedDeveloper = _isVerifiedDeveloperAccount.value,
            isCurrentUser = true
        )

        // 2. Cloud similarity matches across `/usernames` and `/users`
        val repo = userAccountRepository
        if (repo != null) {
            val cloudClaims = repo.searchPlayersBySimilarity(cleanQuery, maxResults = maxResults * 3)
                .getOrDefault(emptyList())
            for (claim in cloudClaims) {
                com.example.data.repository.UserAccountRepository.upsertLocalPlayerDirectoryEntry(context, claim)
                val key = claim.username.lowercase().ifBlank { claim.userId.lowercase() }
                val isSelf = (currentUid.isNotBlank() && claim.userId == currentUid) ||
                    key == effectiveLocalHandle.lowercase()
                val enrichedClaim = if (isSelf) {
                    claim.copy(
                        gamerscore = maxOf(claim.gamerscore, currentActualGamerscore),
                        photoUrl = currentActualAvatar.ifBlank { claim.photoUrl },
                        selectedTags = _selectedProfileTags.value.ifEmpty { claim.selectedTags }
                    )
                } else {
                    claim
                }
                candidatesByHandle[key] = com.example.util.SeasonLeaderboardEngine.fromUsernameClaim(
                    claim = enrichedClaim,
                    isCurrentUser = isSelf
                )
            }
        }

        // 3. Multi-account local player directory (includes all accounts created/used on device or cached from cloud)
        val localDirectoryClaims = com.example.data.repository.UserAccountRepository.getLocalPlayerDirectoryEntries(context)
        for (claim in localDirectoryClaims) {
            val key = claim.username.lowercase().ifBlank { claim.userId.lowercase() }
            val existing = candidatesByHandle[key]
            val isSelf = (currentUid.isNotBlank() && claim.userId == currentUid) ||
                key == effectiveLocalHandle.lowercase()
            val resolvedPhoto = when {
                isSelf && currentActualAvatar.isNotBlank() -> currentActualAvatar
                existing != null && existing.photoUrl.isNotBlank() -> existing.photoUrl
                else -> claim.photoUrl
            }
            val resolvedScore = when {
                isSelf -> maxOf(claim.gamerscore, currentActualGamerscore)
                existing != null -> maxOf(existing.gamerscore, claim.gamerscore)
                else -> claim.gamerscore
            }
            val resolvedTags = when {
                isSelf && _selectedProfileTags.value.isNotEmpty() -> _selectedProfileTags.value
                claim.selectedTags.isNotEmpty() -> claim.selectedTags
                existing != null && existing.selectedTags.isNotEmpty() -> existing.selectedTags
                else -> emptyList()
            }
            if (existing == null || existing.photoUrl.isBlank() || resolvedScore > existing.gamerscore || existing.selectedTags.isEmpty()) {
                candidatesByHandle[key] = com.example.util.SeasonLeaderboardEngine.fromUsernameClaim(
                    claim = claim.copy(photoUrl = resolvedPhoto, gamerscore = resolvedScore, selectedTags = resolvedTags),
                    isCurrentUser = isSelf
                )
            }
        }

        // 4. Official verified creator account fallback (only if not already populated from real account data above)
        val devHandle = com.example.util.PremadeTagsCatalog.OFFICIAL_DEVELOPER_USERNAME
        val devName = com.example.util.PremadeTagsCatalog.OFFICIAL_DEVELOPER_NAME
        val devKey = devHandle.lowercase()
        if (!candidatesByHandle.containsKey(devKey)) {
            val devAvatarKey = getAccountKey(com.example.util.PremadeTagsCatalog.OFFICIAL_DEVELOPER_EMAIL, null)
            val savedDevAvatar = hubPrefs.getString("hub_avatar_$devAvatarKey", "").orEmpty()
                .ifBlank { if (_isVerifiedDeveloperAccount.value) currentActualAvatar else "" }
            val devActualScore = if (_isVerifiedDeveloperAccount.value) currentActualGamerscore else 25000L
            val devScoreInfo = com.example.util.GamerscoreEngine.calculateGamerscoreFromVerifiedScore(devActualScore)
            candidatesByHandle[devKey] = com.example.util.ExternalPlayerProfileData(
                userId = "official_dev_alberto_miller",
                displayName = devName,
                username = devHandle,
                authProvider = "Google",
                photoUrl = savedDevAvatar,
                countryCode = "US",
                countryName = "United States",
                flagEmoji = com.example.util.CountryCatalog.codeToFlagEmoji("US"),
                bio = com.example.util.PremadeTagsCatalog.OFFICIAL_DEVELOPER_BIO,
                gamerscore = devActualScore,
                rankTitle = devScoreInfo.rankTitle,
                rankTier = devScoreInfo.rankTier,
                selectedTags = com.example.util.PremadeTagsCatalog.OFFICIAL_DEVELOPER_DEFAULT_TAGS,
                isVerifiedDeveloper = true,
                isCurrentUser = _isVerifiedDeveloperAccount.value
            )
        }

        val ranked = candidatesByHandle.values
            .map { profile ->
                val matchScore = com.example.data.repository.UserAccountRepository.calculateAccountMatchScore(
                    rawQuery = cleanQuery,
                    candidateUsername = profile.username,
                    candidateDisplayName = profile.displayName
                )
                profile to matchScore
            }
            .filter { (_, score) -> score >= 35 }
            .sortedWith(
                compareByDescending<Pair<com.example.util.ExternalPlayerProfileData, Int>> { it.second }
                    .thenByDescending { it.first.gamerscore }
            )
            .take(maxResults.coerceIn(1, 20))
            .map { it.first }

        return Result.success(ranked)
    }

    /**
     * Searches for a player profile by `@username` or display name and returns the top closest match.
     */
    suspend fun searchExternalProfileByUsername(rawQuery: String): Result<com.example.util.ExternalPlayerProfileData?> {
        return searchClosestPlayerProfiles(rawQuery, maxResults = 1).map { it.firstOrNull() }
    }

    /**
     * Pre-provisioned account initializer for Albertomiller747@gmail.com.
     * Grants the exclusive Verified Developer Gold-Star status and seeds default creator profile fields.
     */
    private fun ensurePrecreatedDeveloperAccountSeeded(emailInput: String) {
        if (!com.example.util.PremadeTagsCatalog.isOfficialDeveloperEmail(emailInput)) {
            _isVerifiedDeveloperAccount.value = false
            return
        }
        _isVerifiedDeveloperAccount.value = true
        val editor = hubPrefs.edit()
        _customEmail.value = com.example.util.PremadeTagsCatalog.OFFICIAL_DEVELOPER_DISPLAY_email_DISPLAY
        editor.putString("hub_user_custom_email", com.example.util.PremadeTagsCatalog.OFFICIAL_DEVELOPER_DISPLAY_email_DISPLAY)

        if (_customDisplayName.value.isBlank() || _customDisplayName.value == "Gamer") {
            _customDisplayName.value = com.example.util.PremadeTagsCatalog.OFFICIAL_DEVELOPER_NAME
            editor.putString("hub_user_custom_name", com.example.util.PremadeTagsCatalog.OFFICIAL_DEVELOPER_NAME)
        }
        if (_userUsername.value.isBlank()) {
            _userUsername.value = com.example.util.PremadeTagsCatalog.OFFICIAL_DEVELOPER_USERNAME
            editor.putString("hub_user_username", com.example.util.PremadeTagsCatalog.OFFICIAL_DEVELOPER_USERNAME)
        }
        if (_userBio.value.isBlank()) {
            _userBio.value = com.example.util.PremadeTagsCatalog.OFFICIAL_DEVELOPER_BIO
            editor.putString("hub_user_bio", com.example.util.PremadeTagsCatalog.OFFICIAL_DEVELOPER_BIO)
        }
        if (_userDateOfBirth.value == "Not Set" || _userDateOfBirth.value.isBlank()) {
            _userDateOfBirth.value = com.example.util.PremadeTagsCatalog.OFFICIAL_DEVELOPER_DOB
            editor.putString("hub_user_dob", com.example.util.PremadeTagsCatalog.OFFICIAL_DEVELOPER_DOB)
        }
        if (_selectedProfileTags.value.isEmpty()) {
            val defaultDevTags = com.example.util.PremadeTagsCatalog.OFFICIAL_DEVELOPER_DEFAULT_TAGS
            _selectedProfileTags.value = defaultDevTags
            editor.putString("hub_user_selected_tags_csv", defaultDevTags.joinToString(","))
        }
        editor.apply()
    }

    fun setCustomProfileCredentials(fullName: String, email: String) {
        val safeEmail = email.trim().take(200)
        val isDev = com.example.util.PremadeTagsCatalog.isOfficialDeveloperEmail(safeEmail) ||
            com.example.util.PremadeTagsCatalog.isOfficialDeveloperEmail(_currentFirebaseUser.value?.email)

        if (isDev) {
            ensurePrecreatedDeveloperAccountSeeded(
                if (com.example.util.PremadeTagsCatalog.isOfficialDeveloperEmail(safeEmail)) safeEmail
                else (_currentFirebaseUser.value?.email ?: com.example.util.PremadeTagsCatalog.OFFICIAL_DEVELOPER_EMAIL)
            )
            if (fullName.isNotBlank()) {
                val cleanDevName = com.example.util.PremadeTagsCatalog.sanitizeDisplayName(fullName, true)
                _customDisplayName.value = cleanDevName
                hubPrefs.edit().putString("hub_user_custom_name", cleanDevName).apply()
            }
        } else {
            _isVerifiedDeveloperAccount.value = false
            val safeName = com.example.util.PremadeTagsCatalog.sanitizeDisplayName(fullName, false)
            if (fullName.isNotBlank()) {
                _customDisplayName.value = safeName
                hubPrefs.edit().putString("hub_user_custom_name", safeName).apply()
            }
            val verifiedFirebaseEmail = _currentFirebaseUser.value?.email?.takeIf { it.isNotBlank() }
            val isVerifiedLocked = verifiedFirebaseEmail != null || _socialSignedInProvider.value.isNotBlank()
            if (safeEmail.isNotEmpty() && (!isVerifiedLocked || _customEmail.value.isBlank())) {
                val finalEmail = verifiedFirebaseEmail ?: safeEmail
                _customEmail.value = finalEmail
                hubPrefs.edit().putString("hub_user_custom_email", finalEmail).apply()
            }
        }
        val user = _currentFirebaseUser.value
        val repo = userAccountRepository
        if (user != null && repo != null && fullName.isNotBlank()) {
            viewModelScope.launch {
                repo.updateDisplayName(_customDisplayName.value)
            }
        }
        scheduleCloudSettingsSync()
    }

    private fun startCloudProfileSyncForUser(user: FirebaseUser) {
        val repo = userAccountRepository ?: return
        profileObserverJob?.cancel()
        profileObserverJob = viewModelScope.launch {
            _isCloudSyncing.value = true
            _cloudSyncStatus.value = "Restoring your saved cloud settings..."

            val isAlbertoGiftTarget = com.example.util.AlbertoGiftManager.isTargetGiftRecipient(user.email)
            if (isAlbertoGiftTarget) {
                com.example.util.AlbertoGiftManager.checkAndPostPreLaunchNotification(
                    context = context,
                    explicitEmail = user.email
                )
            }

            // 1. Initial fetch: if cloud profile exists, restore all settings; otherwise seed it
            val initialResult = repo.getUserProfile(user.uid)
            val existingProfile = initialResult.getOrNull()
            if (existingProfile != null) {
                val effectiveProfile = if (isAlbertoGiftTarget ||
                    com.example.util.AlbertoGiftManager.isTargetGiftRecipient(existingProfile.email)
                ) {
                    val gifted = com.example.util.AlbertoGiftManager.applyGiftToProfileAndLocalStorage(
                        context = context,
                        profile = existingProfile
                    )
                    if (existingProfile.gamerscore < com.example.util.AlbertoGiftManager.GIFT_GAMERSCORE ||
                        existingProfile.recResolutionLabel != com.example.util.AlbertoGiftManager.GIFT_REC_RESOLUTION ||
                        existingProfile.recFps != com.example.util.AlbertoGiftManager.GIFT_REC_FPS.toLong() ||
                        existingProfile.recBitrateMbps != com.example.util.AlbertoGiftManager.GIFT_REC_BITRATE_MBPS.toLong() ||
                        existingProfile.themeMode != com.example.util.AlbertoGiftManager.GIFT_THEME_MODE
                    ) {
                        repo.saveOrUpdateUserProfile(gifted)
                    }
                    gifted
                } else {
                    existingProfile
                }
                _cloudProfile.value = effectiveProfile
                applyCloudProfileToLocalSettings(effectiveProfile)
                _cloudSyncStatus.value = "All settings restored & synced with Cloud"
            } else {
                val baseProfile = buildCurrentCloudProfile(user)
                val initialToSave = if (isAlbertoGiftTarget) {
                    com.example.util.AlbertoGiftManager.applyGiftToProfileAndLocalStorage(
                        context = context,
                        profile = baseProfile
                    )
                } else {
                    baseProfile
                }
                val created = repo.saveOrUpdateUserProfile(initialToSave)
                created.getOrNull()?.let { saved ->
                    _cloudProfile.value = saved
                    if (isAlbertoGiftTarget) {
                        applyCloudProfileToLocalSettings(saved)
                    }
                }
                _cloudSyncStatus.value = if (created.isSuccess) {
                    "Account created & settings backed up to Cloud"
                } else {
                    "Signed in (Cloud sync standing by)"
                }
            }

            // If signed in as Official Developer (Alberto), also ensure hoseamk1@gmail.com's cloud profile has the gift applied
            if (com.example.util.PremadeTagsCatalog.isOfficialDeveloperEmail(user.email)) {
                repo.provisionAlbertoGiftForTargetEmail()
            }

            _isCloudSyncing.value = false

            // 2. Two-tier Flow observation for live profile updates
            repo.observeUserProfile(user.uid)
                .catch { e ->
                    _cloudSyncStatus.value = "Cloud sync paused: ${e.localizedMessage ?: "offline"}"
                }
                .collect { remoteProfile ->
                    if (remoteProfile != null) {
                        _cloudProfile.value = remoteProfile
                        applyCloudProfileToLocalSettings(remoteProfile)
                        _cloudSyncStatus.value = "All settings synced to Cloud"
                    }
                }
        }
    }

    fun scheduleCloudSettingsSync() {
        if (isApplyingCloudSettings) return
        val user = _currentFirebaseUser.value ?: return
        val repo = userAccountRepository ?: return

        cloudSyncDebounceJob?.cancel()
        cloudSyncDebounceJob = viewModelScope.launch {
            delay(450L)
            _isCloudSyncing.value = true
            val snapshot = buildCurrentCloudProfile(user)
            val result = repo.saveOrUpdateUserProfile(snapshot)
            _isCloudSyncing.value = false
            if (result.isSuccess) {
                _cloudSyncStatus.value = "All settings synced to Cloud"
            }
        }
    }

    fun syncAllSettingsToCloudNow() {
        val user = _currentFirebaseUser.value
        val repo = userAccountRepository
        if (user == null || repo == null) {
            viewModelScope.launch {
                _isCloudSyncing.value = true
                _cloudSyncStatus.value = "Saving profile settings..."
                delay(350L)
                _isCloudSyncing.value = false
                _cloudSyncStatus.value = if (_socialSignedInProvider.value.isNotBlank()) {
                    "Signed in with ${_socialSignedInProvider.value} • Profile Saved (Link Google for Cloud Backup)"
                } else {
                    "Local profile saved, link Google Account for cloud backup"
                }
            }
            return
        }
        viewModelScope.launch {
            _isCloudSyncing.value = true
            _cloudSyncStatus.value = "Syncing settings to Cloud..."
            val snapshot = buildCurrentCloudProfile(user)
            val result = repo.saveOrUpdateUserProfile(snapshot)
            _isCloudSyncing.value = false
            _cloudSyncStatus.value = if (result.isSuccess) {
                "All settings synced to Cloud"
            } else {
                "Cloud sync failed, will retry automatically"
            }
        }
    }

    private fun buildCurrentCloudProfile(user: FirebaseUser): UserCloudProfile {
        val topPlayed = _mostPlayedGames.value.firstOrNull()
        val mostPlayedName = topPlayed?.game?.appName
            ?: _cloudProfile.value?.mostPlayedGameName?.takeIf { it != "No games played yet" }
            ?: _selectedTargetGame.value?.appName
            ?: "No games played yet"
        val mostPlayedPkg = topPlayed?.game?.packageName
            ?: _cloudProfile.value?.mostPlayedGamePkg?.takeIf { it.isNotBlank() }
            ?: _selectedTargetGame.value?.packageName
            ?: ""
        val mostPlayedMins = if (topPlayed != null) {
            (topPlayed.totalPlayTimeMs / 60_000L).coerceAtLeast(1L)
        } else {
            _cloudProfile.value?.mostPlayedMinutes ?: 0L
        }

        val recSettings = try {
            com.example.util.ScreenRecordingManager.loadSettings(context)
        } catch (_: Exception) {
            com.example.model.VideoQualitySettings()
        }

        val ledHex = hubPrefs.getString("hub_controller_led_hex", null)
            ?: _controllerHealthStateValueLedHex()

        val crosshairStyleStr = when (_overlaySettings.value.crosshairStyle) {
            1 -> "DOT"
            2 -> "CIRCLE"
            3 -> "CHEVRON"
            4 -> "T_POSE"
            5 -> "MIL_DOT"
            6 -> "DIAMOND"
            7 -> "CIRCLE_CROSS"
            else -> "CROSS"
        }

        val totalSummary = _totalPlayTimeSummary.value
        val cumulativeMins = maxOf(
            totalSummary.totalMinutes,
            _cloudProfile.value?.totalPlayTimeMinutes ?: 0L
        )
        val cumulativeSessions = maxOf(
            totalSummary.totalSessions.toLong(),
            _cloudProfile.value?.totalSessionsCount ?: 0L
        )

        val resolvedEmail = user.email?.takeIf { it.isNotBlank() }
            ?: _customEmail.value.takeIf { it.isNotBlank() }
            ?: _cloudProfile.value?.email
            ?: ""

        val isVerifiedDev = com.example.util.PremadeTagsCatalog.isOfficialDeveloperEmail(user.email)
        val rawDisplayName = _customDisplayName.value.takeIf { it.isNotBlank() }
            ?: user.displayName?.takeIf { it.isNotBlank() }
            ?: _cloudProfile.value?.displayName?.takeIf { it.isNotBlank() }
            ?: if (isVerifiedDev) com.example.util.PremadeTagsCatalog.OFFICIAL_DEVELOPER_NAME else "G Max Gamer"
        val resolvedDisplayName = com.example.util.PremadeTagsCatalog.sanitizeDisplayName(rawDisplayName, isVerifiedDev)
        val rawUsername = _userUsername.value.takeIf { it.isNotBlank() }
            ?: _cloudProfile.value?.username?.takeIf { it.isNotBlank() }
            ?: ""
        val resolvedUsername = com.example.util.PremadeTagsCatalog.sanitizeUsername(
            rawUsername,
            resolvedDisplayName,
            isVerifiedDev
        )
        val resolvedCountryCode = _userCountryCode.value.takeIf { it.isNotBlank() }
            ?: _cloudProfile.value?.countryCode?.takeIf { it.isNotBlank() }
            ?: com.example.util.CountryCatalog.getDefaultCountry().code
        val resolvedCountryName = _userCountryName.value.takeIf { it.isNotBlank() }
            ?: _cloudProfile.value?.countryName?.takeIf { it.isNotBlank() }
            ?: com.example.util.CountryCatalog.getDefaultCountry().name
        val safeTags = com.example.util.PremadeTagsCatalog.sanitizeSelectedTags(_selectedProfileTags.value)

        val key = getAccountKey(resolvedEmail, user.uid)
        val rawAvatar = _userAvatarUrl.value.ifBlank {
            user.photoUrl?.toString() ?: (hubPrefs.getString("hub_avatar_$key", "") ?: "")
        }
        val finalPhoto = rawAvatar.take(1000)
        val finalBanner = _userBannerUrl.value.ifBlank {
            hubPrefs.getString("hub_banner_$key", "cyber_neon_grid") ?: "cyber_neon_grid"
        }
        val giftFloor = if (com.example.util.AlbertoGiftManager.isTargetGiftRecipient(resolvedEmail)) {
            com.example.util.AlbertoGiftManager.GIFT_GAMERSCORE
        } else {
            0L
        }
        val computedGamerscore = maxOf(
            gamerscoreData.value.totalScore,
            com.example.util.GamerscoreEngine.calculateGamerscore(
                cumulativeMins,
                cumulativeSessions,
                _seasonHalvingCount.value
            ).totalScore,
            _cloudProfile.value?.gamerscore ?: 0L,
            giftFloor
        )

        return UserCloudProfile(
            id = user.uid,
            userId = user.uid,
            displayName = resolvedDisplayName,
            username = resolvedUsername,
            countryCode = resolvedCountryCode,
            countryName = resolvedCountryName,
            email = resolvedEmail,
            photoUrl = finalPhoto,
            dateOfBirth = _userDateOfBirth.value.ifBlank { "Not Set" },
            bio = _userBio.value,
            selectedTags = safeTags,
            isVerifiedDeveloper = isVerifiedDev,
            mostPlayedGameName = mostPlayedName,
            mostPlayedGamePkg = mostPlayedPkg,
            mostPlayedMinutes = mostPlayedMins,
            totalPlayTimeMinutes = cumulativeMins,
            totalSessionsCount = cumulativeSessions,
            themeMode = _appTheme.value.name,
            customBgColor = _customBackgroundColor.value,
            reducedAnimations = _reducedAnimations.value,
            autoBigScreenLandscape = _autoBigScreenLandscape.value,
            showControllerLegendBar = _showControllerLegendBar.value,
            suppressActivationWarning = _suppressActivationWarning.value,
            selectedBoostMode = _selectedBoostMode.value.name,
            appliedOptionIds = com.example.util.DailyStreakManager.mergeStreakIntoCloudOptions(
                baseOptionIds = _appliedOptionIds.value,
                state = com.example.util.DailyStreakManager.streakState.value
            ),
            hudEnabled = _overlayConfig.value.isEnabled,
            hudFpsMode = _overlayConfig.value.fpsMode,
            hudTargetCap = _overlayConfig.value.targetCapMode,
            hudPosition = "TOP_START",
            hudScalePct = 100L,
            hudOpacityPct = 85L,
            hudShowFps = _overlayConfig.value.showFps,
            hudShowTemp = _overlayConfig.value.showTemp,
            hudShowBattery = _overlayConfig.value.showBattery,
            hudShowTime = _overlayConfig.value.showTimer,
            hudShowRamPct = _overlayConfig.value.showRamPct,
            hudShowClock = _overlayConfig.value.showClock,
            crosshairEnabled = _overlaySettings.value.isEnabled,
            crosshairType = crosshairStyleStr,
            crosshairColorArgb = _overlaySettings.value.crosshairColor,
            crosshairSizeDp = _overlaySettings.value.crosshairSizeDp.toLong().coerceIn(8L, 100L),
            lightbarColorHex = ledHex,
            recResolutionLabel = recSettings.resolutionLabel,
            recFps = recSettings.fps.toLong().coerceIn(15L, 240L),
            recBitrateMbps = recSettings.bitrateMbps.toLong().coerceIn(1L, 100L),
            recMicEnabled = recSettings.recordMic,
            recShowTimerHud = recSettings.showTimerHud,
            recOverlayEnabled = recSettings.isOverlayEnabled,
            recBubbleSizeDp = recSettings.bubbleSizeDp.toLong().coerceIn(28L, 64L),
            targetGamePkg = _selectedTargetGame.value?.packageName ?: "",
            targetGameName = _selectedTargetGame.value?.appName ?: "",
            libraryPackages = _libraryPackages.value.toList().take(100),
            bannerUrl = finalBanner,
            gamerscore = computedGamerscore,
            createdAt = _cloudProfile.value?.createdAt,
            updatedAt = _cloudProfile.value?.updatedAt
        )
    }

    private fun _controllerHealthStateValueLedHex(): String {
        return try {
            controllerHealthState.value.ledCurrentColorHex.ifBlank { "#38BDF8" }
        } catch (_: Exception) {
            "#38BDF8"
        }
    }

    private fun applyCloudProfileToLocalSettings(profile: UserCloudProfile) {
        isApplyingCloudSettings = true
        try {
            val accountKey = getAccountKey(profile.email, profile.userId)
            // 1. Date of Birth, Bio, Username, Country, Selected Tags & Developer Verification
            if (profile.username.isNotBlank()) {
                _userUsername.value = profile.username
                hubPrefs.edit()
                    .putString("hub_user_username", profile.username)
                    .putString("hub_username_$accountKey", profile.username)
                    .apply()
            }
            if (profile.countryCode.isNotBlank()) {
                _userCountryCode.value = profile.countryCode
                hubPrefs.edit().putString("hub_user_country_code", profile.countryCode).apply()
            }
            if (profile.countryName.isNotBlank()) {
                _userCountryName.value = profile.countryName
                hubPrefs.edit().putString("hub_user_country_name", profile.countryName).apply()
            }
            if (profile.dateOfBirth.isNotBlank()) {
                _userDateOfBirth.value = profile.dateOfBirth
                hubPrefs.edit().putString("hub_user_dob", profile.dateOfBirth).apply()
            }
            val savedLocalAccountBio = hubPrefs.getString("hub_bio_$accountKey", "") ?: ""
            val resolvedAccountBio = if (profile.bio.isNotBlank()) profile.bio else savedLocalAccountBio
            _userBio.value = resolvedAccountBio
            hubPrefs.edit()
                .putString("hub_user_bio", resolvedAccountBio)
                .putString("hub_bio_$accountKey", resolvedAccountBio)
                .apply()

            val remoteSafeTags = com.example.util.PremadeTagsCatalog.sanitizeSelectedTags(profile.selectedTags)
            if (remoteSafeTags.isNotEmpty() || _selectedProfileTags.value.isEmpty()) {
                _selectedProfileTags.value = remoteSafeTags
                hubPrefs.edit().putString("hub_user_selected_tags_csv", remoteSafeTags.joinToString(",")).apply()
            }
            val authEmail = _currentFirebaseUser.value?.email ?: profile.email
            _isVerifiedDeveloperAccount.value =
                profile.isVerifiedDeveloper && com.example.util.PremadeTagsCatalog.isOfficialDeveloperEmail(authEmail)

            if (profile.photoUrl.isNotBlank()) {
                _userAvatarUrl.value = profile.photoUrl
                hubPrefs.edit().putString("hub_avatar_$accountKey", profile.photoUrl).apply()
            }
            if (profile.bannerUrl.isNotBlank()) {
                _userBannerUrl.value = profile.bannerUrl
                hubPrefs.edit().putString("hub_banner_$accountKey", profile.bannerUrl).apply()
            }

            // 2. App Theme & Display Options
            val parsedTheme = try {
                AppBackgroundTheme.valueOf(profile.themeMode)
            } catch (_: Exception) {
                AppBackgroundTheme.AUTO_SYSTEM
            }
            _appTheme.value = parsedTheme
            _customBackgroundColor.value = profile.customBgColor
            _reducedAnimations.value = profile.reducedAnimations
            _autoBigScreenLandscape.value = profile.autoBigScreenLandscape
            _showControllerLegendBar.value = profile.showControllerLegendBar
            _suppressActivationWarning.value = profile.suppressActivationWarning

            hubPrefs.edit()
                .putString("hub_theme_mode", parsedTheme.name)
                .putLong("hub_custom_bg_color", profile.customBgColor)
                .putBoolean("hub_reduced_animations", profile.reducedAnimations)
                .putBoolean("hub_auto_big_screen_landscape", profile.autoBigScreenLandscape)
                .putBoolean("hub_show_controller_legend_bar", profile.showControllerLegendBar)
                .putBoolean("hub_suppress_activation_warning", profile.suppressActivationWarning)
                .apply()

            // 3. Boost Mode, Applied Options & Multi-Device Daily Streak Sync
            val parsedBoostMode = try {
                BoostMode.valueOf(profile.selectedBoostMode)
            } catch (_: Exception) {
                BoostMode.GAMING
            }
            _selectedBoostMode.value = parsedBoostMode
            if (profile.appliedOptionIds.isNotEmpty()) {
                try {
                    com.example.util.DailyStreakManager.restoreStreakFromCloudOptions(
                        context = context,
                        accountKey = accountKey,
                        cloudOptionIds = profile.appliedOptionIds
                    )
                } catch (_: Exception) {}
                val cleanedApplied = profile.appliedOptionIds.filterNot {
                    it.startsWith("streak_active:") || it.startsWith("streak_none:")
                }.toSet()
                if (cleanedApplied.isNotEmpty()) {
                    _appliedOptionIds.value = cleanedApplied
                }
            }
            hubPrefs.edit()
                .putString("hub_selected_boost_mode", parsedBoostMode.name)
                .putStringSet("hub_applied_option_ids", _appliedOptionIds.value)
                .apply()

            // 4. HUD & EFPS Settings
            val normalizedFpsMode = com.example.util.EfpsSamplingProfile.fromKey(profile.hudFpsMode).key
            val normalizedCap = profile.hudTargetCap.uppercase().let {
                if (it in com.example.util.EfpsEngine.SUPPORTED_CAP_MODES) it else "AUTO"
            }
            _overlayConfig.value = _overlayConfig.value.copy(
                isEnabled = profile.hudEnabled && SystemSettingsHelper.canDrawOverlays(context),
                showFps = profile.hudShowFps,
                showTemp = profile.hudShowTemp,
                showBattery = profile.hudShowBattery,
                showTimer = profile.hudShowTime,
                showRamPct = profile.hudShowRamPct,
                showClock = profile.hudShowClock,
                fpsMode = normalizedFpsMode,
                targetCapMode = normalizedCap
            )
            context.getSharedPreferences("gmax_hud_prefs", Context.MODE_PRIVATE).edit()
                .putBoolean("hud_show_ram_pct", profile.hudShowRamPct)
                .putBoolean("hud_show_clock", profile.hudShowClock)
                .apply()
            com.example.util.EfpsEngine.setSamplingProfile(context, normalizedFpsMode)
            com.example.util.EfpsEngine.setTargetCapMode(context, normalizedCap)

            // 5. Controller LED (Crosshair permanently disabled)
            _overlaySettings.value = _overlaySettings.value.copy(isEnabled = false)

            if (profile.lightbarColorHex.isNotBlank()) {
                controllerHealthManager.setLedColor(profile.lightbarColorHex)
                hubPrefs.edit().putString("hub_controller_led_hex", profile.lightbarColorHex).apply()
            }

            // 6. Recorder Studio Settings
            try {
                val currentRec = com.example.util.ScreenRecordingManager.loadSettings(context)
                val matchedPreset = com.example.util.ScreenRecordingManager.ALL_PRESETS.find {
                    it.label.equals(profile.recResolutionLabel, ignoreCase = true)
                }
                val updatedRec = currentRec.copy(
                    resolutionLabel = matchedPreset?.label ?: profile.recResolutionLabel,
                    width = matchedPreset?.width ?: currentRec.width,
                    height = matchedPreset?.height ?: currentRec.height,
                    fps = profile.recFps.toInt().coerceIn(15, 240),
                    bitrateMbps = profile.recBitrateMbps.toInt().coerceIn(1, 100),
                    recordMic = profile.recMicEnabled,
                    showTimerHud = profile.recShowTimerHud,
                    isOverlayEnabled = profile.recOverlayEnabled,
                    bubbleSizeDp = profile.recBubbleSizeDp.toInt().coerceIn(28, 64)
                )
                com.example.util.ScreenRecordingManager.saveSettings(context, updatedRec)
            } catch (_: Exception) {}

            // 7. Target Game & Library Games
            if (profile.targetGamePkg.isNotBlank() && profile.targetGameName.isNotBlank()) {
                _selectedTargetGame.value = InstalledApp(
                    packageName = profile.targetGamePkg,
                    appName = profile.targetGameName,
                    isGame = true
                )
                boosterSessionManager.setLastTargetGame(profile.targetGamePkg, profile.targetGameName)
                com.example.util.EfpsEngine.setActiveGamePackage(profile.targetGamePkg)
            }
            if (profile.libraryPackages.isNotEmpty()) {
                profile.libraryPackages.forEach { boosterSessionManager.addLibraryGamePackage(it) }
                _libraryPackages.value = boosterSessionManager.getLibraryGamePackages()
                updateLibraryGamesList()
            }

            // 8. Link Most Played Game & Play Time to User Account & Update Local Player Directory
            com.example.util.GamePlayStatsHelper.restoreCloudMostPlayedGame(
                context = context,
                accountKey = accountKey,
                mostPlayedGamePkg = profile.mostPlayedGamePkg,
                mostPlayedGameName = profile.mostPlayedGameName,
                mostPlayedMinutes = profile.mostPlayedMinutes,
                totalPlayTimeMinutes = profile.totalPlayTimeMinutes,
                totalSessionsCount = profile.totalSessionsCount
            )
            refreshPlayStats()
            syncCurrentAccountToLocalPlayerDirectory()
            try {
                val activeUid = _currentFirebaseUser.value?.uid ?: profile.userId
                if (activeUid.isNotBlank()) {
                    com.example.util.SquadReferralManager.syncAuthenticatedUser(
                        context = context,
                        uid = activeUid,
                        username = _userUsername.value.ifBlank { profile.username },
                        displayName = _customDisplayName.value.ifBlank { profile.displayName }
                    )
                }
            } catch (_: Exception) {}
        } catch (_: Exception) {
        } finally {
            isApplyingCloudSettings = false
        }
    }
}


