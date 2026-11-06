package com.example.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.os.Process
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GamingOverlayService : Service(), Choreographer.FrameCallback {

    private var windowManager: WindowManager? = null
    private var overlayLayout: View? = null
    private var windowParams: WindowManager.LayoutParams? = null

    private var fpsTextView: TextView? = null
    private var timerTextView: TextView? = null
    private var ramTextView: TextView? = null
    private var tempTextView: TextView? = null
    private var batteryTextView: TextView? = null
    private var clockTextView: TextView? = null

    private var boosterSessionManager: com.example.util.BoosterSessionManager? = null
    private var lastPeriodicRamCleanTimestamp = 0L
    private var lastNotificationUpdateTimestamp = 0L
    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default + kotlinx.coroutines.SupervisorJob())
    private var optimizationJob: Job? = null

    private var wifiLock: WifiManager.WifiLock? = null
    private var screenWakeLock: PowerManager.WakeLock? = null
    private var lastPurgedMb = 0
    private var lastPurgeTimestamp = 0L

    private val NOTIFICATION_ID = 2001
    private val CHANNEL_ID = "gmax_booster_status_channel"

    private var showFps = true
    private var showTemp = true
    private var showBattery = true
    private var showTimer = true
    private var showRamPct = true
    private var showClock = true
    private var autoScaleSmallScreens = true
    private var closeBtnView: TextView? = null

    // Hardware Refresh Rate, Effective Cap, and Live 6-Way EFPS State
    private var displayRefreshRate = 60
    private var effectiveCapFps = 60
    private var currentFps = 60
    private var efpsCollectorJob: Job? = null

    // Live RAM Usage Percentage & Time of Day (pre-allocated objects for zero-GC telemetry ticks)
    private var currentRamPct = 48
    private var currentTimeOfDayStr = ""
    private val clockFormatter = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)
    private val reusableMemInfo = android.app.ActivityManager.MemoryInfo()
    private val reusableClockDate = java.util.Date()
    private var cachedHudPrefs: android.content.SharedPreferences? = null

    // Real Battery & Temp Receiver (100% genuine Android hardware broadcast)
    private var currentTempC = 28.0f
    private var currentBatteryLevel = 100
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val rawTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                if (rawTemp > 0) {
                    currentTempC = rawTemp / 10f
                }

                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    currentBatteryLevel = ((level.toFloat() / scale) * 100).toInt()
                }

                updateLabels()
            }
        }
    }

    private fun queryRamAndClock() {
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            if (am != null) {
                am.getMemoryInfo(reusableMemInfo)
                if (reusableMemInfo.totalMem > 0L) {
                    val used = (reusableMemInfo.totalMem - reusableMemInfo.availMem).coerceAtLeast(0L)
                    currentRamPct = ((used * 100L) / reusableMemInfo.totalMem).toInt().coerceIn(1, 99)
                }
            }
        } catch (_: Exception) {}

        try {
            reusableClockDate.time = System.currentTimeMillis()
            currentTimeOfDayStr = clockFormatter.format(reusableClockDate)
        } catch (_: Exception) {}
    }

    private fun queryHardwareBatteryAndTemp() {
        queryRamAndClock()
        // 1. Direct hardware register query via BatteryManager (zero latency)
        try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val hwCap = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (hwCap in 1..100) {
                currentBatteryLevel = hwCap
            }
        } catch (_: Exception) {}

        // 2. Synchronous sticky broadcast probe for 100% genuine hardware battery temperature
        try {
            val stickyIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            stickyIntent?.let { intent ->
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    currentBatteryLevel = ((level.toFloat() / scale) * 100).toInt()
                }
                val rawTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                if (rawTemp > 0) {
                    currentTempC = rawTemp / 10f
                }
            }
        } catch (_: Exception) {}
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_UPDATE = "ACTION_UPDATE"

        const val EXTRA_SHOW_FPS = "EXTRA_SHOW_FPS"
        const val EXTRA_SHOW_TEMP = "EXTRA_SHOW_TEMP"
        const val EXTRA_SHOW_BATTERY = "EXTRA_SHOW_BATTERY"
        const val EXTRA_SHOW_TIMER = "EXTRA_SHOW_TIMER"
        const val EXTRA_SHOW_RAM_PCT = "EXTRA_SHOW_RAM_PCT"
        const val EXTRA_SHOW_CLOCK = "EXTRA_SHOW_CLOCK"
        const val EXTRA_AUTO_SCALE = "EXTRA_AUTO_SCALE"

        var isServiceRunning = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        boosterSessionManager = com.example.util.BoosterSessionManager(this)
        com.example.util.EfpsEngine.init(this)
        createNotificationChannel()
        queryHardwareBatteryAndTemp()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            }
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "G MAX Booster Live Telemetry",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live booster active status and remaining time"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    private fun acquireLowLatencyWifiLock() {
        if (wifiLock?.isHeld == true) return
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wm != null) {
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wm.createWifiLock(mode, "GMaxBooster:GamingOverlayService").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (_: Exception) {}
    }

    private fun releaseLowLatencyWifiLock() {
        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (_: Exception) {}
        wifiLock = null
    }

    private fun acquireScreenWakeLock() {
        if (screenWakeLock?.isHeld == true) return
        try {
            val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm != null) {
                @Suppress("DEPRECATION")
                val flags = PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP
                screenWakeLock = pm.newWakeLock(flags, "GMaxBooster:ScreenAwakeLock").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (_: Exception) {
            try {
                val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                screenWakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GMaxBooster:FallbackWakeLock")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            } catch (_: Exception) {}
        }
    }

    private fun releaseScreenWakeLock() {
        try {
            if (screenWakeLock?.isHeld == true) {
                screenWakeLock?.release()
            }
        } catch (_: Exception) {}
        screenWakeLock = null
    }

    private fun applySessionThreadPriority(isBeast: Boolean) {
        try {
            // Keep the background telemetry/overlay worker strictly at BACKGROUND priority
            // so it stays on efficiency cores and NEVER steals CPU cycles from the active game.
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        } catch (_: Exception) {}
    }

    private fun restoreDefaultThreadPriority() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT)
        } catch (_: Exception) {}
    }

    private fun applyLowLatencyAudioAndNetworkHints(enable: Boolean, isBeast: Boolean) {
        try {
            val audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            if (audioManager != null) {
                if (enable) {
                    audioManager.setParameters(
                        if (isBeast) "game_mode=1;low_latency=1;fast_track=1"
                        else "game_mode=1;low_latency=1"
                    )
                } else {
                    audioManager.setParameters("game_mode=0;low_latency=0")
                }
            }
        } catch (_: Exception) {}
    }

    private fun executeMultiActionOptimization(isBeast: Boolean, isInitialStart: Boolean = false) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                applySessionThreadPriority(isBeast)

                if (isInitialStart) {
                    // 1. Acquire low-latency Wi-Fi radio lock (WIFI_MODE_FULL_LOW_LATENCY) for minimum ping jitter
                    acquireLowLatencyWifiLock()

                    // 2. Configure low-latency hardware audio mixer hints
                    applyLowLatencyAudioAndNetworkHints(enable = true, isBeast = isBeast)

                    // 3. Enforce Screen WakeLock
                    acquireScreenWakeLock()

                    // 4. Enforce Priority Do Not Disturb once on session start
                    com.example.util.PriorityDndHelper.enablePriorityDnd(this@GamingOverlayService, markAutoEngaged = true)

                    // Read exact RAM freed from the pre-launch trim
                    val lastTrim = com.example.util.AppMemoryManager.lastTrimResult.value
                    lastPurgedMb = (lastTrim?.ramFreedMb ?: 0L).toInt().coerceAtLeast(0)
                    lastPurgeTimestamp = System.currentTimeMillis()
                } else {
                    // During active gameplay: DO NOT run killBackgroundProcesses loops or System.gc()
                    // because killing background apps mid-match triggers Zygote service respawns and frame drops.
                    // Only trim GMAX's own lightweight app cache if the OS signals critical low-memory.
                    val am = getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                    val memInfo = android.app.ActivityManager.MemoryInfo()
                    am?.getMemoryInfo(memInfo)
                    if (memInfo.lowMemory) {
                        val trimRes = com.example.util.AppMemoryManager.performAutoTrim(
                            context = this@GamingOverlayService,
                            reason = "Critical Low-Memory Guard",
                            cleanBackgroundProcesses = false,
                            force = false
                        )
                        lastPurgedMb = trimRes.ramFreedMb.toInt()
                        lastPurgeTimestamp = System.currentTimeMillis()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun updateNotification(remainingFormatted: String, isBeast: Boolean, gameTitle: String, purgedMb: Int) {
        try {
            val intent = Intent(this, com.example.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                this,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) android.app.PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val modeTitle = if (isBeast) "⚡ BEAST ACTIVE" else "🛡️ GAMING BOOST"
            val purgedLabel = if (purgedMb > 0) "Freed ${purgedMb}MB • " else ""
            val summaryText = if (isBeast) {
                "${purgedLabel}Low-Latency Wi-Fi • Zero-Lag Audio • $gameTitle"
            } else {
                "${purgedLabel}Wi-Fi Lock • Priority DND • $gameTitle"
            }

            val builder = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("$modeTitle • ⏳ $remainingFormatted")
                .setContentText(summaryText)
                .setSubText(gameTitle)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)

            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            nm?.notify(NOTIFICATION_ID, builder.build())
        } catch (_: Exception) {}
    }

    private var connectivityMonitorJob: Job? = null
    private var wasOnlinePreviously: Boolean = true

    private fun startOptimizationEngine() {
        if (connectivityMonitorJob?.isActive != true) {
            wasOnlinePreviously = com.example.util.NetworkConnectivityMonitor.isCurrentlyOnline(this)
            connectivityMonitorJob = serviceScope.launch {
                com.example.util.NetworkConnectivityMonitor.observeConnectivity(this@GamingOverlayService).collect { isOnline ->
                    if (wasOnlinePreviously && !isOnline) {
                        val session = boosterSessionManager
                        val isNormalBoostActive = session != null &&
                            session.isSessionActive() &&
                            session.getActiveMode() == com.example.model.BoostMode.GAMING

                        // Always stop Crosshair & Controller overlays on internet loss
                        try {
                            com.example.service.CrosshairOverlayService.saveConfig(
                                this@GamingOverlayService,
                                false,
                                0,
                                0xFF00E676L,
                                24,
                                0.9f
                            )
                            stopService(Intent(this@GamingOverlayService, CrosshairOverlayService::class.java))
                        } catch (_: Exception) {}

                        try {
                            val ctrlCfg = ControllerOverlayService.loadConfig(this@GamingOverlayService)
                            if (ctrlCfg.isEnabled) {
                                ControllerOverlayService.saveConfig(this@GamingOverlayService, ctrlCfg.copy(isEnabled = false))
                                stopService(Intent(this@GamingOverlayService, ControllerOverlayService::class.java))
                            }
                        } catch (_: Exception) {}

                        // Remove HUD overlay when internet drops UNLESS it is a Normal Boost (BoostMode.GAMING) session
                        if (!isNormalBoostActive) {
                            stopSelf()
                        }
                    }
                    wasOnlinePreviously = isOnline
                }
            }
        }

        if (optimizationJob?.isActive == true) return
        optimizationJob = serviceScope.launch {
            applySessionThreadPriority(false)
            var tickCount = 0
            while (isActive) {
                delay(1000L)
                tickCount++
                val session = boosterSessionManager
                if (session != null && session.isSessionActive()) {
                    val mode = session.getActiveMode()
                    val isBeast = mode == com.example.model.BoostMode.BEAST
                    val now = System.currentTimeMillis()

                    if (lastPeriodicRamCleanTimestamp == 0L) {
                        lastPeriodicRamCleanTimestamp = now
                        executeMultiActionOptimization(isBeast, isInitialStart = true)
                    } else if (now - lastPeriodicRamCleanTimestamp >= 30_000L) {
                        lastPeriodicRamCleanTimestamp = now
                        executeMultiActionOptimization(isBeast, isInitialStart = false)
                    }

                    // Subdued notification update every 10 seconds to minimize NotificationManager binder overhead
                    if (now - lastNotificationUpdateTimestamp >= 10_000L) {
                        lastNotificationUpdateTimestamp = now
                        val remainingFormatted = session.formatCountdown(session.getRemainingMillis())
                        val gameTitle = session.getLastTargetGame().second ?: "Active Game"
                        updateNotification(remainingFormatted, isBeast, "$gameTitle ($currentFps EFPS)", lastPurgedMb)
                    }
                } else {
                    // Session expired or ended: release all locks and restore scheduler & DND
                    releaseLowLatencyWifiLock()
                    releaseScreenWakeLock()
                    applyLowLatencyAudioAndNetworkHints(enable = false, isBeast = false)
                    restoreDefaultThreadPriority()
                    com.example.util.PriorityDndHelper.restorePreviousDnd(this@GamingOverlayService)
                    if (overlayLayout == null && !showFps && !showTemp && !showBattery && !showRamPct && !showClock) {
                        if (com.example.util.MatchRecapManager.getInstance(this@GamingOverlayService).isRecording.value) {
                            com.example.util.MatchRecapManager.getInstance(this@GamingOverlayService).finishSession()
                        }
                        stopSelf()
                        break
                    }
                }
                // Refresh RAM usage % and Time of Day every 3 seconds instead of every 1 second to reduce IPC overhead
                if (tickCount % 3 == 0) {
                    queryRamAndClock()
                }
                // Passively accrue G-Tokens (10 G-Tokens per 5 minutes = 300s) while the live performance HUD is active
                if (tickCount % 15 == 0 && overlayLayout != null) {
                    try {
                        com.example.util.DailyStreakManager.accrueHudActiveSeconds(
                            context = this@GamingOverlayService,
                            secondsElapsed = 15
                        )
                    } catch (_: Exception) {}
                }
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    updateLabels()
                }
                // Record telemetry sample every 2 seconds during active overlay
                if (tickCount % 2 == 0) {
                    com.example.util.MatchRecapManager.getInstance(this@GamingOverlayService).recordSample(
                        fps = currentFps,
                        tempC = currentTempC,
                        batteryPct = currentBatteryLevel
                    )
                }
            }
        }
    }

    private var currentFpsMode: String = "BALANCED"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            releaseLowLatencyWifiLock()
            releaseScreenWakeLock()
            restoreDefaultThreadPriority()
            com.example.util.PriorityDndHelper.restorePreviousDnd(this@GamingOverlayService)
            if (com.example.util.MatchRecapManager.getInstance(this).isRecording.value) {
                com.example.util.MatchRecapManager.getInstance(this).finishSession()
            }
            stopSelf()
            return START_NOT_STICKY
        }

        val hudPrefs = getSharedPreferences("gmax_hud_prefs", Context.MODE_PRIVATE)
        showFps = intent?.getBooleanExtra(EXTRA_SHOW_FPS, true) ?: true
        showTemp = intent?.getBooleanExtra(EXTRA_SHOW_TEMP, true) ?: true
        showBattery = intent?.getBooleanExtra(EXTRA_SHOW_BATTERY, true) ?: true
        showTimer = intent?.getBooleanExtra(EXTRA_SHOW_TIMER, true) ?: true
        showRamPct = intent?.getBooleanExtra(
            EXTRA_SHOW_RAM_PCT,
            hudPrefs.getBoolean("hud_show_ram_pct", true)
        ) ?: true
        showClock = intent?.getBooleanExtra(
            EXTRA_SHOW_CLOCK,
            hudPrefs.getBoolean("hud_show_clock", true)
        ) ?: true
        autoScaleSmallScreens = intent?.getBooleanExtra(
            EXTRA_AUTO_SCALE,
            hudPrefs.getBoolean("hud_auto_scale", true)
        ) ?: true
        currentFpsMode = intent?.getStringExtra("EXTRA_FPS_MODE") ?: hudPrefs.getString("hud_fps_mode", "BALANCED") ?: "BALANCED"
        val targetCapMode = intent?.getStringExtra("EXTRA_TARGET_CAP") ?: hudPrefs.getString("hud_target_cap", "AUTO") ?: "AUTO"
        com.example.util.EfpsEngine.setSamplingProfile(this, currentFpsMode)
        com.example.util.EfpsEngine.setTargetCapMode(this, targetCapMode)
        com.example.util.EfpsEngine.setActiveGamePackage(boosterSessionManager?.getLastTargetGame()?.first)

        // Ensure Match Recap auto-recording is initiated
        val targetGameName = boosterSessionManager?.getLastTargetGame()?.second ?: "Game Session"
        if (!com.example.util.MatchRecapManager.getInstance(this).isRecording.value) {
            com.example.util.MatchRecapManager.getInstance(this).startSession(
                gameTitle = targetGameName,
                initialTemp = currentTempC,
                initialBattery = currentBatteryLevel
            )
        }

        // Refresh battery, temperature, RAM %, and clock immediately on each start/update
        queryHardwareBatteryAndTemp()

        // Promote to foreground service so Android never kills the overlay when leaving the app
        try {
            val openIntent = Intent(this, com.example.MainActivity::class.java).apply {
                this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                this,
                0,
                openIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) android.app.PendingIntent.FLAG_IMMUTABLE else 0)
            )
            val notification = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("G MAX Live Telemetry Active")
                .setContentText("$targetGameName • $currentFps EFPS • ${currentTempC}°C")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {}

        // Setup floating HUD if overlay permission is granted
        val canDraw = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        if (canDraw) {
            try {
                com.example.util.DailyStreakManager.recordDailyRequirementCompleted(
                    context = this,
                    sourceLabel = "Live Performance HUD"
                )
            } catch (_: Exception) {}
            if (windowManager == null || overlayLayout == null) {
                setupOverlay()
            } else {
                updateVisibility()
                updateLabels()
            }
            startFpsMeter()
        }

        startOptimizationEngine()
        isServiceRunning = true
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun setupOverlay() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        @Suppress("DEPRECATION")
        val defaultDisplay = wm.defaultDisplay

        // Detect screen's real hardware maximum refresh rate (up to 90Hz, 120Hz, 144Hz, 165Hz)
        val supportedModes = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                defaultDisplay?.supportedModes ?: emptyArray()
            } else emptyArray()
        } catch (_: Exception) { emptyArray() }

        val maxHardwareHz = supportedModes.map { it.refreshRate.toInt() }.maxOrNull() ?: 60
        val currentDisplayHz = try {
            kotlin.math.round(defaultDisplay?.refreshRate ?: 60f).toInt()
        } catch (_: Exception) { 60 }

        displayRefreshRate = maxOf(maxHardwareHz, currentDisplayHz, 60).coerceIn(30, 240)
        currentFps = displayRefreshRate
        queryHardwareBatteryAndTemp()

        val density = resources.displayMetrics.density

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val hudContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * density).toInt(), (6 * density).toInt(), (10 * density).toInt(), (6 * density).toInt())
            @Suppress("DEPRECATION")
            setWillNotCacheDrawing(true)

            val shape = GradientDrawable().apply {
                setColor(Color.argb(225, 13, 17, 26))
                cornerRadius = 18 * density
                setStroke((1.5f * density).toInt(), Color.argb(160, 0, 229, 255))
            }
            background = shape
            elevation = 0f
        }

        // 1. Live Multi-Sensor EFPS Indicator (strictly single-line so it never wraps into 2 rows)
        fpsTextView = TextView(this).apply {
            text = "60 EFPS"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 11.5f
            typeface = android.graphics.Typeface.MONOSPACE
            isSingleLine = true
            maxLines = 1
            ellipsize = null
            setPadding(0, 0, (7 * density).toInt(), 0)
        }
        hudContainer.addView(fpsTextView)

        // 2. Live Booster Remaining Time Badge
        timerTextView = TextView(this).apply {
            text = "⏳ --:--"
            setTextColor(Color.parseColor("#00E676"))
            textSize = 11.5f
            typeface = android.graphics.Typeface.MONOSPACE
            isSingleLine = true
            maxLines = 1
            ellipsize = null
            setPadding(0, 0, (7 * density).toInt(), 0)
        }
        hudContainer.addView(timerTextView)

        // 3. Live System RAM Usage Percentage
        ramTextView = TextView(this).apply {
            text = "RAM $currentRamPct%"
            setTextColor(Color.parseColor("#A855F7"))
            textSize = 11.5f
            typeface = android.graphics.Typeface.MONOSPACE
            isSingleLine = true
            maxLines = 1
            ellipsize = null
            setPadding(0, 0, (7 * density).toInt(), 0)
        }
        hudContainer.addView(ramTextView)

        // 4. Live Real Battery Temp
        tempTextView = TextView(this).apply {
            text = "28.0°C"
            setTextColor(Color.parseColor("#F59E0B"))
            textSize = 11.5f
            typeface = android.graphics.Typeface.MONOSPACE
            isSingleLine = true
            maxLines = 1
            ellipsize = null
            setPadding(0, 0, (7 * density).toInt(), 0)
        }
        hudContainer.addView(tempTextView)

        // 5. Live Real Battery Level
        batteryTextView = TextView(this).apply {
            text = "🔋100%"
            setTextColor(Color.parseColor("#10B981"))
            textSize = 11.5f
            typeface = android.graphics.Typeface.MONOSPACE
            isSingleLine = true
            maxLines = 1
            ellipsize = null
            setPadding(0, 0, (7 * density).toInt(), 0)
        }
        hudContainer.addView(batteryTextView)

        // 6. Live Time of Day Clock
        clockTextView = TextView(this).apply {
            text = currentTimeOfDayStr.ifBlank { "12:00 PM" }
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 11.5f
            typeface = android.graphics.Typeface.MONOSPACE
            isSingleLine = true
            maxLines = 1
            ellipsize = null
            setPadding(0, 0, (8 * density).toInt(), 0)
        }
        hudContainer.addView(clockTextView)

        // 7. Quick Dismiss Button
        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#EF4444"))
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isSingleLine = true
            maxLines = 1
            setPadding((3 * density).toInt(), 0, (3 * density).toInt(), 0)
            setOnClickListener {
                stopSelf()
            }
        }
        closeBtnView = closeBtn
        hudContainer.addView(closeBtn)

        overlayLayout = hudContainer
        updateVisibility()
        updateLabels()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (20 * density).toInt()
            y = (60 * density).toInt()

            // Unlock screen's hardware high refresh rate (90Hz, 120Hz, 144Hz, 165Hz)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bestMode = supportedModes.maxByOrNull { it.refreshRate }
                if (bestMode != null) {
                    preferredDisplayModeId = bestMode.modeId
                }
                preferredRefreshRate = displayRefreshRate.toFloat()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                preferredRefreshRate = displayRefreshRate.toFloat()
            }
        }
        windowParams = params

        // Smooth Drag Handling
        hudContainer.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null) return false
                val curParams = windowParams ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = curParams.x
                        initialY = curParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        curParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        curParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        try {
                            windowManager?.updateViewLayout(hudContainer, curParams)
                        } catch (_: Exception) {}
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager?.addView(hudContainer, params)
        } catch (_: Exception) {}
    }

    private var lastAppliedScaleSignature: Int = 0
    private var lastFpsText: String = ""
    private var lastFpsColor: Int = 0
    private var lastTimerText: String = ""
    private var lastTimerColor: Int = 0
    private var lastTimerVisible: Boolean? = null
    private var lastRamText: String = ""
    private var lastRamColor: Int = 0
    private var lastTempText: String = ""
    private var lastBatText: String = ""
    private var lastClockText: String = ""

    private fun updateVisibility() {
        fpsTextView?.visibility = if (showFps) View.VISIBLE else View.GONE
        ramTextView?.visibility = if (showRamPct) View.VISIBLE else View.GONE
        tempTextView?.visibility = if (showTemp) View.VISIBLE else View.GONE
        batteryTextView?.visibility = if (showBattery) View.VISIBLE else View.GONE
        clockTextView?.visibility = if (showClock) View.VISIBLE else View.GONE
        applyResponsiveOverlayScale(force = true)
    }

    /**
     * Auto-scales font size, item padding, and container padding based on phone screen width
     * and the number of enabled HUD metrics so all 6 items stay strictly on 1 single row.
     * Uses signature dirty-checking so layout padding/textSize is not recalculated on every frame.
     */
    private fun applyResponsiveOverlayScale(force: Boolean = false) {
        val density = resources.displayMetrics.density
        val screenWidthDp = resources.configuration.screenWidthDp.coerceAtLeast(320)
        val isTimerVisible = boosterSessionManager?.isSessionActive() == true && showTimer
        val activeCount = listOf(showFps, isTimerVisible, showRamPct, showTemp, showBattery, showClock).count { it }.coerceAtLeast(1)

        val signature = (screenWidthDp shl 8) or (activeCount shl 2) or (if (autoScaleSmallScreens) 1 else 0)
        if (!force && signature == lastAppliedScaleSignature) return
        lastAppliedScaleSignature = signature

        val widthFactor = if (autoScaleSmallScreens) {
            (screenWidthDp / 412f).coerceIn(0.76f, 1.04f)
        } else {
            0.94f
        }
        val densityFactor = when {
            activeCount >= 6 -> if (autoScaleSmallScreens) 0.78f else 0.85f
            activeCount == 5 -> if (autoScaleSmallScreens) 0.84f else 0.90f
            activeCount == 4 -> 0.92f
            else -> 1.0f
        }

        val resolvedTextSp = (11.8f * widthFactor * densityFactor).coerceIn(8.5f, 12.0f)
        val itemGapPx = ((if (activeCount >= 5) 4.5f else 7.0f) * widthFactor * density).toInt().coerceAtLeast(3)
        val padHPx = ((if (activeCount >= 5) 8.5f else 11.5f) * widthFactor * density).toInt().coerceAtLeast(6)
        val padVPx = ((if (activeCount >= 5) 4.5f else 6.0f) * widthFactor * density).toInt().coerceAtLeast(4)

        (overlayLayout as? LinearLayout)?.setPadding(padHPx, padVPx, padHPx, padVPx)

        listOf(fpsTextView, timerTextView, ramTextView, tempTextView, batteryTextView, clockTextView).forEach { tv ->
            tv?.textSize = resolvedTextSp
            tv?.isSingleLine = true
            tv?.maxLines = 1
            tv?.setPadding(0, 0, itemGapPx, 0)
        }
        closeBtnView?.textSize = (resolvedTextSp + 0.5f).coerceIn(9.0f, 12.5f)
    }

    @SuppressLint("SetTextI18n")
    private fun updateLabels() {
        val isTimerVisible = boosterSessionManager?.isSessionActive() == true && showTimer
        val activeCount = listOf(showFps, isTimerVisible, showRamPct, showTemp, showBattery, showClock).count { it }
        val isCompactHud = autoScaleSmallScreens && activeCount >= 5 && resources.configuration.screenWidthDp < 430

        // 1. Compact 96% to 98% Accuracy 6-Way Multi-Sensor EFPS Readout
        val newFpsText = if (isCompactHud) "$currentFps FPS" else "$currentFps EFPS"
        if (newFpsText != lastFpsText) {
            lastFpsText = newFpsText
            fpsTextView?.text = newFpsText
        }

        val hudPrefs = cachedHudPrefs ?: getSharedPreferences("gmax_hud_prefs", Context.MODE_PRIVATE).also {
            cachedHudPrefs = it
        }
        val isNeonHudUnlocked = hudPrefs.getBoolean("hud_streak_neon_unlocked", false)
        val customNeonHex = if (isNeonHudUnlocked) {
            hudPrefs.getString("hud_streak_neon_hex", "#00E5FF").orEmpty().ifBlank { "#00E5FF" }
        } else {
            "#00E5FF"
        }
        val primaryNeonColor = try {
            Color.parseColor(customNeonHex)
        } catch (_: Exception) {
            Color.parseColor("#00E5FF")
        }

        // Apply 7-Day Streak Custom Neon HUD border accent if unlocked (or revert to default if streak broke)
        if (primaryNeonColor != lastFpsColor) {
            (overlayLayout?.background as? GradientDrawable)?.let { bgShape ->
                val density = resources.displayMetrics.density
                val r = Color.red(primaryNeonColor)
                val g = Color.green(primaryNeonColor)
                val b = Color.blue(primaryNeonColor)
                bgShape.setStroke((1.5f * density).toInt(), Color.argb(185, r, g, b))
            }
        }

        val ceiling = effectiveCapFps.coerceAtLeast(30)
        val stableThreshold = (ceiling * 0.90f).toInt()
        val moderateThreshold = (ceiling * 0.70f).toInt().coerceAtLeast(30)
        val fpsColor = when {
            currentFps < 30 -> Color.parseColor("#FF5252")
            currentFps >= stableThreshold -> primaryNeonColor
            currentFps >= moderateThreshold -> Color.parseColor("#F59E0B")
            else -> Color.parseColor("#EF4444")
        }
        if (fpsColor != lastFpsColor) {
            lastFpsColor = fpsColor
            fpsTextView?.setTextColor(fpsColor)
        }

        // 2. Booster Session Live Countdown & Subdued HUD Status (Respects showTimer toggle)
        val session = boosterSessionManager
        if (session != null && session.isSessionActive() && showTimer) {
            val remainingMs = session.getRemainingMillis()
            val mode = session.getActiveMode()
            val formatted = session.formatCountdown(remainingMs)
            val isBeast = mode == com.example.model.BoostMode.BEAST
            val now = System.currentTimeMillis()

            val (newTimerStr, newTimerClr) = if (now - lastPurgeTimestamp in 0..4000L && lastPurgedMb > 0) {
                "⚡-${lastPurgedMb}M" to Color.parseColor("#00E5FF")
            } else {
                val icon = if (isBeast) "🔥" else "⏳"
                val str = if (isCompactHud) "$icon$formatted" else "$icon $formatted"
                val clr = if (isBeast) Color.parseColor("#FF5722") else Color.parseColor("#00E676")
                str to clr
            }
            if (newTimerStr != lastTimerText) {
                lastTimerText = newTimerStr
                timerTextView?.text = newTimerStr
            }
            if (newTimerClr != lastTimerColor) {
                lastTimerColor = newTimerClr
                timerTextView?.setTextColor(newTimerClr)
            }
            if (lastTimerVisible != true) {
                lastTimerVisible = true
                timerTextView?.visibility = View.VISIBLE
            }
        } else {
            if (lastTimerVisible != false) {
                lastTimerVisible = false
                timerTextView?.visibility = View.GONE
            }
        }

        // 3. RAM Usage Percentage
        val newRamText = if (isCompactHud) "R:$currentRamPct%" else "RAM $currentRamPct%"
        if (newRamText != lastRamText) {
            lastRamText = newRamText
            ramTextView?.text = newRamText
        }
        val ramColor = when {
            currentRamPct >= 85 -> Color.parseColor("#EF4444")
            currentRamPct >= 72 -> Color.parseColor("#F59E0B")
            else -> Color.parseColor("#A855F7")
        }
        if (ramColor != lastRamColor) {
            lastRamColor = ramColor
            ramTextView?.setTextColor(ramColor)
        }

        val newTempText = String.format(java.util.Locale.US, "%.1f°C", currentTempC)
        if (newTempText != lastTempText) {
            lastTempText = newTempText
            tempTextView?.text = newTempText
        }

        val newBatText = if (isCompactHud) "🔋$currentBatteryLevel%" else "🔋 $currentBatteryLevel%"
        if (newBatText != lastBatText) {
            lastBatText = newBatText
            batteryTextView?.text = newBatText
        }

        if (currentTimeOfDayStr.isBlank()) {
            queryRamAndClock()
        }
        if (currentTimeOfDayStr != lastClockText) {
            lastClockText = currentTimeOfDayStr
            clockTextView?.text = currentTimeOfDayStr
        }
        applyResponsiveOverlayScale(force = false)
    }

    private fun startFpsMeter() {
        com.example.util.EfpsEngine.start(this, overlayLayout)
        if (efpsCollectorJob?.isActive != true) {
            efpsCollectorJob = serviceScope.launch(Dispatchers.Main) {
                com.example.util.EfpsEngine.telemetry.collect { state ->
                    displayRefreshRate = state.displayHz.coerceIn(30, 240)
                    effectiveCapFps = state.effectiveCapFps.coerceIn(15, 240)
                    currentFps = state.currentEfps.coerceAtLeast(1)
                    updateLabels()
                }
            }
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        // Handled by EfpsEngine multi-sensor fusion pipeline
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        @Suppress("DEPRECATION")
        overlayLayout?.destroyDrawingCache()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        @Suppress("DEPRECATION")
        overlayLayout?.destroyDrawingCache()
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        com.example.util.EfpsEngine.stop()
        efpsCollectorJob?.cancel()
        optimizationJob?.cancel()
        serviceScope.cancel()
        releaseLowLatencyWifiLock()
        releaseScreenWakeLock()
        com.example.util.PriorityDndHelper.restorePreviousDnd(this@GamingOverlayService)
        if (com.example.util.MatchRecapManager.getInstance(this).isRecording.value) {
            com.example.util.MatchRecapManager.getInstance(this).finishSession()
        }
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}

        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            nm?.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}

        try {
            overlayLayout?.let {
                windowManager?.removeView(it)
            }
        } catch (_: Exception) {}
        overlayLayout = null
        windowManager = null
    }
}
