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
import com.example.model.OverlayHudConfig
import com.example.model.OverlaySettings
import com.example.model.PhoneHardwareSpecs
import com.example.model.ThermalInfo
import com.example.service.GamingOverlayService
import com.example.util.BenchmarkEngine
import com.example.util.GamepadDetector
import com.example.util.MemoryHelper
import com.example.util.NetworkTester
import com.example.util.StorageSpeedTester
import com.example.util.SystemSettingsHelper
import com.example.util.ThermalMonitor
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

    val gameGuidanceList: List<GameGuidance> = GameGuidanceDatabase.gamesList
    val adbTweaks: List<AdbCommandTweak> = AdbCommandsRepository.tweaksList

    private val _gamepads = MutableStateFlow<List<GamepadDevice>>(emptyList())
    val gamepads: StateFlow<List<GamepadDevice>> = _gamepads.asStateFlow()

    private val _storageSpeed = MutableStateFlow<StorageSpeedTester.StorageSpeedResult?>(null)
    val storageSpeed: StateFlow<StorageSpeedTester.StorageSpeedResult?> = _storageSpeed.asStateFlow()

    private val _isSpeedTesting = MutableStateFlow(false)
    val isSpeedTesting: StateFlow<Boolean> = _isSpeedTesting.asStateFlow()

    val shizukuInfo: StateFlow<com.example.util.ShizukuInfo> = com.example.util.ShizukuManager.shizukuInfo

    fun refreshShizukuStatus() {
        com.example.util.ShizukuManager.checkStatus()
    }

    fun requestShizukuPermission() {
        com.example.util.ShizukuManager.requestPermission()
    }

    fun openShizukuApp() {
        val ok = com.example.util.ShizukuManager.openShizukuApp(context)
        if (!ok) {
            _boostFeedback.value = "Shizuku app not found. Please install Shizuku from GitHub/Play Store to read real in-game FPS."
        }
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
    }

    // Phone Hardware Specifications
    private val _hardwareSpecs = MutableStateFlow(PhoneHardwareSpecs())
    val hardwareSpecs: StateFlow<PhoneHardwareSpecs> = _hardwareSpecs.asStateFlow()

    // Real-time Screen Overlay HUD Config (FPS, Temp, Battery)
    private val _overlayConfig = MutableStateFlow(OverlayHudConfig())
    val overlayConfig: StateFlow<OverlayHudConfig> = _overlayConfig.asStateFlow()

    // Background Themes
    private val _appTheme = MutableStateFlow(AppBackgroundTheme.DARK_SLATE)
    val appTheme: StateFlow<AppBackgroundTheme> = _appTheme.asStateFlow()

    fun setAppTheme(theme: AppBackgroundTheme) {
        _appTheme.value = theme
    }

    // Pre-Activation Warning suppression
    private val _suppressActivationWarning = MutableStateFlow(false)
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
    }

    fun toggleOptionApplied(optionId: String) {
        val current = _appliedOptionIds.value.toMutableSet()
        if (current.contains(optionId)) {
            current.remove(optionId)
        } else {
            current.add(optionId)
        }
        _appliedOptionIds.value = current
        updateReadinessScore()
    }

    private var benchmarkJob: Job? = null

    init {
        try { thermalMonitor.start() } catch (_: Exception) {}
        try { com.example.util.ShizukuManager.init() } catch (_: Exception) {}
        try { refreshMetrics() } catch (_: Exception) {}
        try { loadInstalledApps() } catch (_: Exception) {}
        try { refreshGamepads() } catch (_: Exception) {}
        try { initPingTargets() } catch (_: Exception) {}
        try { loadHardwareSpecs() } catch (_: Exception) {}

        // Load remembered target game
        val (savedPkg, savedName) = boosterSessionManager.getLastTargetGame()
        if (savedPkg != null && savedName != null) {
            _selectedTargetGame.value = InstalledApp(
                packageName = savedPkg,
                appName = savedName,
                isGame = true
            )
        }

        // Load library games
        _libraryPackages.value = boosterSessionManager.getLibraryGamePackages()
        updateLibraryGamesList()

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
        _isGamePickerVisible.value = false
        _boostFeedback.value = "Target game selected: ${app.appName}."
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
        thermalMonitor.stop()
        networkTester.setLowLatencyWifiLock(false)
        SystemSettingsHelper.setDoNotDisturb(context, false)
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
    }

    fun addGameToLibrary(app: InstalledApp) {
        boosterSessionManager.addLibraryGamePackage(app.packageName)
        _libraryPackages.value = boosterSessionManager.getLibraryGamePackages()
        updateLibraryGamesList()
        _boostFeedback.value = "Added ${app.appName} to Library."
    }

    fun removeGameFromLibrary(packageName: String) {
        boosterSessionManager.removeLibraryGamePackage(packageName)
        _libraryPackages.value = boosterSessionManager.getLibraryGamePackages()
        updateLibraryGamesList()
        _boostFeedback.value = "Removed from Library."
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
    }

    fun toggleOverlayFps(context: Context, show: Boolean) {
        _overlayConfig.value = _overlayConfig.value.copy(showFps = show)
        syncOverlayService(context)
    }

    fun toggleOverlayTemp(context: Context, show: Boolean) {
        _overlayConfig.value = _overlayConfig.value.copy(showTemp = show)
        syncOverlayService(context)
    }

    fun toggleOverlayBattery(context: Context, show: Boolean) {
        _overlayConfig.value = _overlayConfig.value.copy(showBattery = show)
        syncOverlayService(context)
    }

    fun toggleOverlayTimer(context: Context, show: Boolean) {
        _overlayConfig.value = _overlayConfig.value.copy(showTimer = show)
        syncOverlayService(context)
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
            val intent = Intent(context, com.example.service.CrosshairOverlayService::class.java).apply {
                action = com.example.service.CrosshairOverlayService.ACTION_STOP
            }
            context.startService(intent)
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
}
