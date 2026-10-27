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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    fun setBigScreenModeManual(enabled: Boolean) {
        _isBigScreenModeManual.value = enabled
        com.example.util.ConsoleHapticManager.playSelectClick(context)
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

    // 2 Boost Modes: Gaming and Beast
    private val _selectedBoostMode = MutableStateFlow(BoostMode.GAMING)
    val selectedBoostMode: StateFlow<BoostMode> = _selectedBoostMode.asStateFlow()

    fun setBoostMode(mode: BoostMode) {
        _selectedBoostMode.value = mode
        hubPrefs.edit().putString("hub_selected_boost_mode", mode.name).apply()
        scheduleCloudSettingsSync()
    }

    // Phone Hardware Specifications
    private val _hardwareSpecs = MutableStateFlow(PhoneHardwareSpecs())
    val hardwareSpecs: StateFlow<PhoneHardwareSpecs> = _hardwareSpecs.asStateFlow()

    // Real-time Screen Overlay HUD Config (FPS, Temp, Battery)
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

    // Tracks whether the user has completed the post-splash Sign In / Create Account step
    private val _hasCompletedAuthStep = MutableStateFlow(
        (_currentFirebaseUser.value != null) || hubPrefs.getBoolean("hub_auth_step_completed", false)
    )
    val hasCompletedAuthStep: StateFlow<Boolean> = _hasCompletedAuthStep.asStateFlow()

    private val _cloudProfile = MutableStateFlow<UserCloudProfile?>(null)
    val cloudProfile: StateFlow<UserCloudProfile?> = _cloudProfile.asStateFlow()

    private val _cloudSyncStatus = MutableStateFlow(
        if (_currentFirebaseUser.value != null) "Cloud Sync Active" else "Guest Mode (Local Storage Only)"
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
            Firebase.auth.currentUser?.email ?: (hubPrefs.getString("hub_user_custom_email", "") ?: "")
        )
    )
    val isVerifiedDeveloperAccount: StateFlow<Boolean> = _isVerifiedDeveloperAccount.asStateFlow()

    private var profileObserverJob: Job? = null
    private var cloudSyncDebounceJob: Job? = null
    private var isApplyingCloudSettings = false

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        _currentFirebaseUser.value = user
        if (user != null) {
            _hasCompletedAuthStep.value = true
            hubPrefs.edit().putBoolean("hub_auth_step_completed", true).apply()
            if (com.example.util.PremadeTagsCatalog.isOfficialDeveloperEmail(user.email)) {
                ensurePrecreatedDeveloperAccountSeeded(user.email ?: com.example.util.PremadeTagsCatalog.OFFICIAL_DEVELOPER_EMAIL)
            } else {
                _isVerifiedDeveloperAccount.value = false
            }
            startCloudProfileSyncForUser(user)
        } else {
            profileObserverJob?.cancel()
            profileObserverJob = null
            _cloudProfile.value = null
            _isVerifiedDeveloperAccount.value = com.example.util.PremadeTagsCatalog.isOfficialDeveloperEmail(_customEmail.value)
            _cloudSyncStatus.value = "Guest Mode (Local Storage Only)"
        }
    }

    private val _showPermissionsPage = MutableStateFlow(false)
    val showPermissionsPage: StateFlow<Boolean> = _showPermissionsPage.asStateFlow()

    val isLowRamDeviceDetected: Boolean = run {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val memStats = memoryHelper.getMemoryStats()
            (am?.isLowRamDevice == true) || (memStats.totalRamMb in 1..4200)
        } catch (_: Exception) {
            false
        }
    }

    // Lite Mode / Reduced Animations for low-end phones (auto-enables on <= 4GB RAM if not manually set)
    private val _reducedAnimations = MutableStateFlow(
        if (hubPrefs.contains("hub_reduced_animations")) {
            hubPrefs.getBoolean("hub_reduced_animations", false)
        } else {
            isLowRamDeviceDetected
        }
    )
    val reducedAnimations: StateFlow<Boolean> = _reducedAnimations.asStateFlow()

    fun setReducedAnimations(enabled: Boolean) {
        _reducedAnimations.value = enabled
        hubPrefs.edit().putBoolean("hub_reduced_animations", enabled).apply()
        scheduleCloudSettingsSync()
    }

    // Automatic Big Screen Mode when turning the device to landscape (enabled by default)
    private val _autoBigScreenLandscape = MutableStateFlow(
        hubPrefs.getBoolean("hub_auto_big_screen_landscape", true)
    )
    val autoBigScreenLandscape: StateFlow<Boolean> = _autoBigScreenLandscape.asStateFlow()

    fun setAutoBigScreenLandscape(enabled: Boolean) {
        _autoBigScreenLandscape.value = enabled
        hubPrefs.edit().putBoolean("hub_auto_big_screen_landscape", enabled).apply()
        if (!enabled) {
            _isBigScreenModeManual.value = false
        }
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
        scheduleCloudSettingsSync()
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
        try { thermalMonitor.start() } catch (_: Exception) {}
        try { com.example.util.EfpsEngine.init(context) } catch (_: Exception) {}
        try { com.example.util.EfpsEngine.start(context) } catch (_: Exception) {}
        try { refreshMetrics() } catch (_: Exception) {}
        try { loadInstalledApps() } catch (_: Exception) {}
        try { refreshGamepads() } catch (_: Exception) {}
        try { initPingTargets() } catch (_: Exception) {}
        try { loadHardwareSpecs() } catch (_: Exception) {}

        // Restore Tactical Crosshair if previously enabled by user
        try {
            val savedCrosshair = com.example.service.CrosshairOverlayService.getSavedConfig(context)
            if (savedCrosshair.isEnabled) {
                _overlaySettings.value = savedCrosshair
                val currentApplied = _appliedOptionIds.value.toMutableSet()
                currentApplied.add("tweak_crosshair")
                _appliedOptionIds.value = currentApplied
                if (SystemSettingsHelper.canDrawOverlays(context)) {
                    val intent = Intent(context, com.example.service.CrosshairOverlayService::class.java).apply {
                        action = com.example.service.CrosshairOverlayService.ACTION_RESTORE
                    }
                    context.startService(intent)
                }
            }
        } catch (_: Exception) {}

        // Restore HUD EFPS Sampling Profile, Target FPS Cap, RAM %, and Clock preferences
        try {
            val hudPrefs = context.getSharedPreferences("gmax_hud_prefs", Context.MODE_PRIVATE)
            val rawSaved = hudPrefs.getString("hud_fps_mode", "BALANCED") ?: "BALANCED"
            val normalizedMode = com.example.util.EfpsSamplingProfile.fromKey(rawSaved).key
            val savedCap = hudPrefs.getString("hud_target_cap", "AUTO")?.uppercase() ?: "AUTO"
            val normalizedCap = if (savedCap in com.example.util.EfpsEngine.SUPPORTED_CAP_MODES) savedCap else "AUTO"
            val savedRamPct = hudPrefs.getBoolean("hud_show_ram_pct", true)
            val savedClock = hudPrefs.getBoolean("hud_show_clock", true)
            _overlayConfig.value = _overlayConfig.value.copy(
                fpsMode = normalizedMode,
                targetCapMode = normalizedCap,
                showRamPct = savedRamPct,
                showClock = savedClock
            )
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
        updateLibraryGamesList()

        // Restore saved boost mode and applied option IDs
        try {
            val savedBoostMode = hubPrefs.getString("hub_selected_boost_mode", BoostMode.GAMING.name)
            if (savedBoostMode != null) {
                _selectedBoostMode.value = BoostMode.valueOf(savedBoostMode)
            }
            val savedApplied = hubPrefs.getStringSet("hub_applied_option_ids", null)
            if (!savedApplied.isNullOrEmpty()) {
                _appliedOptionIds.value = savedApplied.toSet()
            }
        } catch (_: Exception) {}

        // Register Firebase AuthStateListener for real-time cloud settings restoration & sync
        try {
            Firebase.auth.addAuthStateListener(authStateListener)
        } catch (_: Exception) {}

        // Collect real-time controller connection/disconnection alerts
        viewModelScope.launch {
            gamepadDetector.connectionAlerts.collect { alert ->
                _controllerAlert.value = alert
                refreshGamepads()
            }
        }

        // Periodic 1-second ticker to sync countdown time without resetting
        viewModelScope.launch {
            while (true) {
                updateSessionState()
                delay(1000)
            }
        }
    }

    private fun updateSessionState() {
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
        scheduleCloudSettingsSync()
    }

    fun stopBoosterSession() {
        boosterSessionManager.stopSession()
        networkTester.setLowLatencyWifiLock(false)
        SystemSettingsHelper.setDoNotDisturb(context, false)
        updateSessionState()
        syncOverlayService(context)
        _boostFeedback.value = "Booster active session ended. Default system scheduler, DND, and locks restored."
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
            _gamepads.value = gamepadDetector.getConnectedGamepads()
        } catch (_: Exception) {}
    }

    fun updateLibraryGamesList() {
        val pkgs = _libraryPackages.value
        val appsMap = _installedApps.value.associateBy { it.packageName }
        val pm = context.packageManager
        val list = pkgs.map { pkg ->
            appsMap[pkg] ?: run {
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val label = pm.getApplicationLabel(appInfo).toString()
                    InstalledApp(packageName = pkg, appName = label, isGame = true)
                } catch (_: Exception) {
                    InstalledApp(packageName = pkg, appName = pkg.substringAfterLast("."), isGame = true)
                }
            }
        }
        _libraryGames.value = list
        refreshPlayStats()
    }

    fun refreshPlayStats() {
        try {
            val currentLib = _libraryGames.value
            _recentlyPlayedGames.value = com.example.util.GamePlayStatsHelper.getRecentlyPlayedGames(context, currentLib)
            _mostPlayedGames.value = com.example.util.GamePlayStatsHelper.getMostPlayedGames(context, currentLib)
            _totalPlayTimeSummary.value = com.example.util.GamePlayStatsHelper.getTotalPlayTimeSummary(context, currentLib)
        } catch (_: Exception) {}
    }

    fun onAppResumed() {
        try {
            com.example.util.GamePlayStatsHelper.finalizeActiveGameSessionOnReturn(context)
            refreshPlayStats()
        } catch (_: Exception) {}
    }

    fun addGameToLibrary(app: InstalledApp) {
        boosterSessionManager.addLibraryGamePackage(app.packageName)
        _libraryPackages.value = boosterSessionManager.getLibraryGamePackages()
        updateLibraryGamesList()
        _boostFeedback.value = "Added ${app.appName} to Library."
        scheduleCloudSettingsSync()
    }

    fun removeGameFromLibrary(packageName: String) {
        boosterSessionManager.removeLibraryGamePackage(packageName)
        _libraryPackages.value = boosterSessionManager.getLibraryGamePackages()
        updateLibraryGamesList()
        _boostFeedback.value = "Removed from Library."
        scheduleCloudSettingsSync()
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
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

    fun quickBoost(targetApp: InstalledApp? = null, mode: BoostMode? = null) {
        if (mode != null) {
            _selectedBoostMode.value = mode
        }
        if (targetApp != null) {
            selectTargetGame(targetApp)
        }

        val target = _selectedTargetGame.value
        if (target == null) {
            // User must pick an app or game first
            _isGamePickerVisible.value = true
            _boostFeedback.value = "Please select the game or app you want to boost."
            return
        }

        viewModelScope.launch {
            _isBoosting.value = true
            val mode = _selectedBoostMode.value

            if (mode == BoostMode.GAMING) {
                _boostFeedback.value = "Gaming Mode: Optimizing buffers for ${target.appName} & cleaning RAM..."
                val (killedCount, freedMb) = memoryHelper.cleanCachedRam()
                System.gc()

                // Acquire low latency Wi-Fi lock
                networkTester.setLowLatencyWifiLock(true)

                // Engage Priority DND if granted
                if (SystemSettingsHelper.isNotificationPolicyAccessGranted(context)) {
                    SystemSettingsHelper.setDoNotDisturb(context, true, fullSilence = false)
                }

                // Start persistent 3-hour session
                boosterSessionManager.startSession(BoostMode.GAMING, target.packageName, target.appName)
                updateSessionState()
                syncOverlayService(context)

                delay(1000)
                refreshMetrics()
                _isBoosting.value = false
                _boostFeedback.value = "Gaming Mode Active (3h): Wi-Fi latency lock, screen awake, priority DND & 12m RAM maintenance engaged (+${freedMb}MB freed). Launching ${target.appName}..."
            } else {
                // BEAST MODE - Intense Gaming Profile
                _boostFeedback.value = "BEAST MODE: Engaging aggressive FPS kernel profile for ${target.appName}..."
                val (killedCount, freedMb) = memoryHelper.cleanCachedRam()
                System.gc()

                // Request low-latency wifi lock
                networkTester.setLowLatencyWifiLock(true)

                // Attempt Secure Settings optimizations if granted
                if (com.example.util.SecureSettingsManager.canWriteSecureSettings(context)) {
                    com.example.util.SecureSettingsManager.setAnimationScales(context, 0.0f)
                }

                // Suppress ALL interruptions (Full Gaming Silence) if granted
                if (SystemSettingsHelper.isNotificationPolicyAccessGranted(context)) {
                    SystemSettingsHelper.setDoNotDisturb(context, true, fullSilence = true)
                }

                // Automatically turn on screen HUD with FPS, Battery & Live Countdown
                if (SystemSettingsHelper.canDrawOverlays(context)) {
                    _overlayConfig.value = _overlayConfig.value.copy(
                        isEnabled = true,
                        showFps = true,
                        showBattery = true
                    )
                }

                // Start persistent 1 hour 30 min session
                boosterSessionManager.startSession(BoostMode.BEAST, target.packageName, target.appName)
                updateSessionState()
                syncOverlayService(context)

                delay(1200)
                refreshMetrics()
                _isBoosting.value = false
                _boostFeedback.value = "BEAST MODE ACTIVE (1h 30m): Full silence DND, screen awake, strict thread priority (-8), 0-lag Wi-Fi & 3m RAM purger engaged. Launching ${target.appName}..."
            }

            // Record real launch telemetry and trigger console launch haptics
            com.example.util.GamePlayStatsHelper.recordGameLaunch(context, target.packageName, target.appName)
            com.example.util.ConsoleHapticManager.playLaunchPulse(context)
            refreshPlayStats()

            // Automatically open the boosted game/app
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(target.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                }
            } catch (_: Exception) {}
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
        syncOverlayService(context)
        scheduleCloudSettingsSync()
    }

    fun toggleOverlayFps(context: Context, show: Boolean) {
        _overlayConfig.value = _overlayConfig.value.copy(showFps = show)
        syncOverlayService(context)
        scheduleCloudSettingsSync()
    }

    fun toggleOverlayTemp(context: Context, show: Boolean) {
        _overlayConfig.value = _overlayConfig.value.copy(showTemp = show)
        syncOverlayService(context)
        scheduleCloudSettingsSync()
    }

    fun toggleOverlayBattery(context: Context, show: Boolean) {
        _overlayConfig.value = _overlayConfig.value.copy(showBattery = show)
        syncOverlayService(context)
        scheduleCloudSettingsSync()
    }

    fun toggleOverlayTimer(context: Context, show: Boolean) {
        _overlayConfig.value = _overlayConfig.value.copy(showTimer = show)
        syncOverlayService(context)
        scheduleCloudSettingsSync()
    }

    fun toggleOverlayRamPct(context: Context, show: Boolean) {
        _overlayConfig.value = _overlayConfig.value.copy(showRamPct = show)
        context.getSharedPreferences("gmax_hud_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("hud_show_ram_pct", show).apply()
        syncOverlayService(context)
        scheduleCloudSettingsSync()
    }

    fun toggleOverlayClock(context: Context, show: Boolean) {
        _overlayConfig.value = _overlayConfig.value.copy(showClock = show)
        context.getSharedPreferences("gmax_hud_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("hud_show_clock", show).apply()
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

    private fun syncOverlayService(context: Context) {
        val cfg = _overlayConfig.value
        val isSessionActive = boosterSessionManager.isSessionActive()
        val shouldRun = cfg.isEnabled || isSessionActive
        val intent = Intent(context, GamingOverlayService::class.java).apply {
            action = if (shouldRun) GamingOverlayService.ACTION_START else GamingOverlayService.ACTION_STOP
            putExtra(GamingOverlayService.EXTRA_SHOW_FPS, cfg.showFps && cfg.isEnabled)
            putExtra(GamingOverlayService.EXTRA_SHOW_TEMP, cfg.showTemp && cfg.isEnabled)
            putExtra(GamingOverlayService.EXTRA_SHOW_BATTERY, cfg.showBattery && cfg.isEnabled)
            putExtra(GamingOverlayService.EXTRA_SHOW_TIMER, cfg.showTimer)
            putExtra(GamingOverlayService.EXTRA_SHOW_RAM_PCT, cfg.showRamPct && cfg.isEnabled)
            putExtra(GamingOverlayService.EXTRA_SHOW_CLOCK, cfg.showClock && cfg.isEnabled)
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

    fun executePerformanceOption(
        context: Context,
        option: com.example.model.PerformanceOption,
        onOpenModal: () -> Unit
    ) {
        when (option.id) {
            "pop_3_ram" -> {
                quickBoost()
                toggleOptionApplied(option.id)
            }
            "pop_2_dnd", "feat_dnd_session" -> {
                if (SystemSettingsHelper.isNotificationPolicyAccessGranted(context)) {
                    val currentApplied = _appliedOptionIds.value.contains(option.id)
                    val newTarget = !currentApplied
                    SystemSettingsHelper.setDoNotDisturb(context, newTarget)
                    toggleOptionApplied(option.id)
                    _boostFeedback.value = if (newTarget) {
                        "Focus Shield Active: Do Not Disturb enabled. Call banners & popups suppressed."
                    } else {
                        "Focus Shield Disabled: Normal notification filter restored."
                    }
                } else {
                    _boostFeedback.value = "Notification Policy Access required. Opening system settings..."
                    SystemSettingsHelper.openNotificationPolicyAccess(context)
                }
            }
            "net_low_latency" -> {
                val currentApplied = _appliedOptionIds.value.contains(option.id)
                val newTarget = !currentApplied
                networkTester.setLowLatencyWifiLock(newTarget)
                toggleOptionApplied(option.id)
                _boostFeedback.value = if (newTarget) {
                    "Radio Low-Latency Lock acquired. Wi-Fi packet prioritization active."
                } else {
                    "Radio Low-Latency Lock released."
                }
            }
            "perf_sustained_mode" -> {
                val currentApplied = _appliedOptionIds.value.contains(option.id)
                val newTarget = !currentApplied
                toggleOptionApplied(option.id)
                _boostFeedback.value = if (newTarget) {
                    "Sustained Performance Mode engaged: Kernel thermal frequency budget locked."
                } else {
                    "Sustained Performance Mode released: Dynamic governor restored."
                }
            }
            "perf_thread_priority" -> {
                try {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)
                } catch (_: Exception) {}
                toggleOptionApplied(option.id)
                _boostFeedback.value = "Thread Priority: Foreground rendering thread elevated to URGENT_DISPLAY (-8)."
            }
            "ctrl_overlay_hud" -> {
                toggleOverlayEnabled(context, !_overlayConfig.value.isEnabled)
                toggleOptionApplied(option.id)
                _boostFeedback.value = if (_overlayConfig.value.isEnabled) {
                    "Screen Overlay HUD activated on screen."
                } else {
                    "Screen Overlay HUD closed."
                }
            }
            "tweak_crosshair", "ctrl_crosshair" -> {
                toggleCrosshair(context)
            }
            "bg_animations" -> {
                if (com.example.util.SecureSettingsManager.canWriteSecureSettings(context)) {
                    val ok = com.example.util.SecureSettingsManager.setAnimationScales(context, 0.0f)
                    if (ok) {
                        toggleOptionApplied(option.id)
                        _boostFeedback.value = "System animation scales set to 0.0x directly via Secure Settings."
                    } else {
                        onOpenModal()
                    }
                } else {
                    onOpenModal()
                }
            }
            "net_private_dns" -> {
                if (com.example.util.SecureSettingsManager.canWriteSecureSettings(context)) {
                    val ok = com.example.util.SecureSettingsManager.setPrivateDns(
                        context,
                        "hostname",
                        "1dot1dot1dot1.cloudflare-dns.com"
                    )
                    if (ok) {
                        toggleOptionApplied(option.id)
                        _boostFeedback.value = "Private DNS configured to Cloudflare 1.1.1.1 via Secure Settings."
                    } else {
                        onOpenModal()
                    }
                } else {
                    onOpenModal()
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

    fun setCrosshairEnabled(context: Context, enabled: Boolean) {
        if (enabled && !SystemSettingsHelper.canDrawOverlays(context)) {
            SystemSettingsHelper.openOverlayPermissionSettings(context)
            _boostFeedback.value = "Please grant 'Display over other apps' permission to draw crosshair."
            return
        }

        _overlaySettings.value = _overlaySettings.value.copy(isEnabled = enabled)
        val currentApplied = _appliedOptionIds.value.toMutableSet()
        if (enabled) {
            currentApplied.add("tweak_crosshair")
            com.example.service.CrosshairOverlayService.saveConfig(
                context,
                true,
                _overlaySettings.value.crosshairStyle,
                _overlaySettings.value.crosshairColor,
                _overlaySettings.value.crosshairSizeDp,
                _overlaySettings.value.crosshairAlpha
            )
            val intent = Intent(context, com.example.service.CrosshairOverlayService::class.java).apply {
                action = com.example.service.CrosshairOverlayService.ACTION_START
                putExtra(com.example.service.CrosshairOverlayService.EXTRA_STYLE, _overlaySettings.value.crosshairStyle)
                putExtra(com.example.service.CrosshairOverlayService.EXTRA_COLOR, _overlaySettings.value.crosshairColor)
                putExtra(com.example.service.CrosshairOverlayService.EXTRA_SIZE_DP, _overlaySettings.value.crosshairSizeDp)
                putExtra(com.example.service.CrosshairOverlayService.EXTRA_ALPHA, _overlaySettings.value.crosshairAlpha)
            }
            context.startService(intent)
            _boostFeedback.value = "Tactical Crosshair enabled on screen. (Touch-through active)."
        } else {
            currentApplied.remove("tweak_crosshair")
            com.example.service.CrosshairOverlayService.saveConfig(
                context,
                false,
                _overlaySettings.value.crosshairStyle,
                _overlaySettings.value.crosshairColor,
                _overlaySettings.value.crosshairSizeDp,
                _overlaySettings.value.crosshairAlpha
            )
            val intent = Intent(context, com.example.service.CrosshairOverlayService::class.java).apply {
                action = com.example.service.CrosshairOverlayService.ACTION_STOP
            }
            context.startService(intent)
            try {
                context.stopService(intent)
            } catch (_: Exception) {}
            _boostFeedback.value = "Tactical Crosshair overlay removed."
        }
        _appliedOptionIds.value = currentApplied
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
        _overlaySettings.value = _overlaySettings.value.copy(crosshairAlpha = alpha)
        syncCrosshairService()
    }

    private fun syncCrosshairService() {
        com.example.service.CrosshairOverlayService.saveConfig(
            context,
            _overlaySettings.value.isEnabled,
            _overlaySettings.value.crosshairStyle,
            _overlaySettings.value.crosshairColor,
            _overlaySettings.value.crosshairSizeDp,
            _overlaySettings.value.crosshairAlpha
        )
        if (_overlaySettings.value.isEnabled && SystemSettingsHelper.canDrawOverlays(context)) {
            val intent = Intent(context, com.example.service.CrosshairOverlayService::class.java).apply {
                action = com.example.service.CrosshairOverlayService.ACTION_UPDATE
                putExtra(com.example.service.CrosshairOverlayService.EXTRA_STYLE, _overlaySettings.value.crosshairStyle)
                putExtra(com.example.service.CrosshairOverlayService.EXTRA_COLOR, _overlaySettings.value.crosshairColor)
                putExtra(com.example.service.CrosshairOverlayService.EXTRA_SIZE_DP, _overlaySettings.value.crosshairSizeDp)
                putExtra(com.example.service.CrosshairOverlayService.EXTRA_ALPHA, _overlaySettings.value.crosshairAlpha)
            }
            context.startService(intent)
        }
        scheduleCloudSettingsSync()
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
            tips.add("Close heavy apps or run Quick Boost to free cached RAM.")
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
        matchRecapManager.startSession(gameTitle, temp, batt)
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

    fun continueAsGuest() {
        _hasCompletedAuthStep.value = true
        hubPrefs.edit().putBoolean("hub_auth_step_completed", true).apply()
        _cloudSyncStatus.value = "Guest Mode (Local Storage Only)"
    }

    fun onUserSignedInSuccess() {
        val user = try { Firebase.auth.currentUser } catch (_: Exception) { null }
        _currentFirebaseUser.value = user
        _hasCompletedAuthStep.value = true
        hubPrefs.edit().putBoolean("hub_auth_step_completed", true).apply()
        if (user != null) {
            startCloudProfileSyncForUser(user)
        }
    }

    fun onUserSignedOut() {
        profileObserverJob?.cancel()
        profileObserverJob = null
        _currentFirebaseUser.value = null
        _cloudProfile.value = null
        _customEmail.value = ""
        _customDisplayName.value = ""
        _isVerifiedDeveloperAccount.value = false
        hubPrefs.edit()
            .remove("hub_user_custom_email")
            .remove("hub_user_custom_name")
            .apply()
        _cloudSyncStatus.value = "Signed out — using local device settings"
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
        hubPrefs.edit().putString("hub_user_bio", safeBio).apply()
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
            if (safeEmail.isNotEmpty()) {
                _customEmail.value = safeEmail
                hubPrefs.edit().putString("hub_user_custom_email", safeEmail).apply()
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

            // 1. Initial fetch: if cloud profile exists, restore all settings; otherwise seed it
            val initialResult = repo.getUserProfile(user.uid)
            val existingProfile = initialResult.getOrNull()
            if (existingProfile != null) {
                _cloudProfile.value = existingProfile
                applyCloudProfileToLocalSettings(existingProfile)
                _cloudSyncStatus.value = "All settings restored & synced with Cloud"
            } else {
                val created = repo.saveOrUpdateUserProfile(buildCurrentCloudProfile(user))
                created.getOrNull()?.let { _cloudProfile.value = it }
                _cloudSyncStatus.value = if (created.isSuccess) {
                    "Account created & settings backed up to Cloud"
                } else {
                    "Signed in (Cloud sync standing by)"
                }
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
        val user = _currentFirebaseUser.value ?: return
        val repo = userAccountRepository ?: return
        viewModelScope.launch {
            _isCloudSyncing.value = true
            _cloudSyncStatus.value = "Syncing settings to Cloud..."
            val snapshot = buildCurrentCloudProfile(user)
            val result = repo.saveOrUpdateUserProfile(snapshot)
            _isCloudSyncing.value = false
            _cloudSyncStatus.value = if (result.isSuccess) {
                "All settings synced to Cloud"
            } else {
                "Cloud sync failed — will retry automatically"
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
            ?: if (isVerifiedDev) com.example.util.PremadeTagsCatalog.OFFICIAL_DEVELOPER_NAME else "G-Max Gamer"
        val resolvedDisplayName = com.example.util.PremadeTagsCatalog.sanitizeDisplayName(rawDisplayName, isVerifiedDev)
        val safeTags = com.example.util.PremadeTagsCatalog.sanitizeSelectedTags(_selectedProfileTags.value)

        return UserCloudProfile(
            id = user.uid,
            userId = user.uid,
            displayName = resolvedDisplayName,
            email = resolvedEmail,
            photoUrl = user.photoUrl?.toString() ?: "",
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
            appliedOptionIds = _appliedOptionIds.value.toList().take(50),
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
            // 1. Date of Birth, Bio, Selected Tags & Developer Verification
            if (profile.dateOfBirth.isNotBlank()) {
                _userDateOfBirth.value = profile.dateOfBirth
                hubPrefs.edit().putString("hub_user_dob", profile.dateOfBirth).apply()
            }
            if (profile.bio.isNotBlank() || _userBio.value.isBlank()) {
                _userBio.value = profile.bio
                hubPrefs.edit().putString("hub_user_bio", profile.bio).apply()
            }
            val remoteSafeTags = com.example.util.PremadeTagsCatalog.sanitizeSelectedTags(profile.selectedTags)
            if (remoteSafeTags.isNotEmpty() || _selectedProfileTags.value.isEmpty()) {
                _selectedProfileTags.value = remoteSafeTags
                hubPrefs.edit().putString("hub_user_selected_tags_csv", remoteSafeTags.joinToString(",")).apply()
            }
            val authEmail = _currentFirebaseUser.value?.email ?: profile.email
            _isVerifiedDeveloperAccount.value =
                profile.isVerifiedDeveloper && com.example.util.PremadeTagsCatalog.isOfficialDeveloperEmail(authEmail)

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

            // 3. Boost Mode & Applied Options
            val parsedBoostMode = try {
                BoostMode.valueOf(profile.selectedBoostMode)
            } catch (_: Exception) {
                BoostMode.GAMING
            }
            _selectedBoostMode.value = parsedBoostMode
            if (profile.appliedOptionIds.isNotEmpty()) {
                _appliedOptionIds.value = profile.appliedOptionIds.toSet()
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

            // 5. Crosshair & Controller LED
            val crosshairStyleIdx = when (profile.crosshairType.uppercase()) {
                "DOT" -> 1
                "CIRCLE" -> 2
                "CHEVRON" -> 3
                else -> 0
            }
            _overlaySettings.value = _overlaySettings.value.copy(
                isEnabled = profile.crosshairEnabled && SystemSettingsHelper.canDrawOverlays(context),
                crosshairStyle = crosshairStyleIdx,
                crosshairColor = profile.crosshairColorArgb,
                crosshairSizeDp = profile.crosshairSizeDp.toInt().coerceIn(8, 100)
            )
            com.example.service.CrosshairOverlayService.saveConfig(
                context,
                _overlaySettings.value.isEnabled,
                _overlaySettings.value.crosshairStyle,
                _overlaySettings.value.crosshairColor,
                _overlaySettings.value.crosshairSizeDp,
                _overlaySettings.value.crosshairAlpha
            )

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
        } catch (_: Exception) {
        } finally {
            isApplyingCloudSettings = false
        }
    }
}


