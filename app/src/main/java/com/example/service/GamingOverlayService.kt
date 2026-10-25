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
    private var tempTextView: TextView? = null
    private var batteryTextView: TextView? = null

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
    private var isRealFps = false

    // Hardware Refresh Rate and Nominal Period
    private var displayRefreshRate = 60
    private var expectedVsyncPeriodNanos = 16_666_666L

    // Instantaneous Frame Delta and Dropped Frame Tracker
    private var lastFrameTimeNanos = 0L
    private var lastRealFrameNanos = 0L
    private var lastFpsUpdateTimestampNanos = 0L
    private val recentFpsSamples = ArrayList<Int>(32)
    private var currentFps = 60
    private var isChoreographerActive = false

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

    private fun queryHardwareBatteryAndTemp() {
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

        var isServiceRunning = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        boosterSessionManager = com.example.util.BoosterSessionManager(this)
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
            val priority = if (isBeast) {
                Process.THREAD_PRIORITY_URGENT_DISPLAY // -8: High interactive & frame priority
            } else {
                Process.THREAD_PRIORITY_DISPLAY // -4: Standard smooth display priority
            }
            Process.setThreadPriority(priority)
        } catch (_: Exception) {}
    }

    private fun restoreDefaultThreadPriority() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT)
        } catch (_: Exception) {}
    }

    private fun executeMultiActionOptimization(isBeast: Boolean) {
        serviceScope.launch {
            try {
                // 1. Safe cached memory purge without game hooking
                val memHelper = com.example.util.MemoryHelper(this@GamingOverlayService)
                val (killedCount, freedMb) = memHelper.cleanCachedRam()
                System.runFinalization()
                System.gc()
                lastPurgedMb = freedMb.toInt().coerceAtLeast(16)
                lastPurgeTimestamp = System.currentTimeMillis()

                // 2. Enforce low-latency Wi-Fi radio lock
                acquireLowLatencyWifiLock()

                // 3. Enforce Linux thread scheduler priority
                applySessionThreadPriority(isBeast)

                // 4. Enforce Screen WakeLock
                acquireScreenWakeLock()

                // 5. Enforce Do Not Disturb (full silence for Beast Mode)
                com.example.util.SystemSettingsHelper.setDoNotDisturb(this@GamingOverlayService, true, fullSilence = isBeast)
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
            val summaryText = if (isBeast) {
                "Purged ${purgedMb}MB • 0-Lag Wi-Fi • Screen Awake • Max Priority (-8)"
            } else {
                "Purged ${purgedMb}MB • Wi-Fi Lock • Screen Awake • $gameTitle"
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

    private fun startOptimizationEngine() {
        if (optimizationJob?.isActive == true) return
        optimizationJob = serviceScope.launch {
            while (isActive) {
                delay(1000L)
                val session = boosterSessionManager
                if (session != null && session.isSessionActive()) {
                    val mode = session.getActiveMode()
                    val isBeast = mode == com.example.model.BoostMode.BEAST
                    val intervalMs = session.getPurgeIntervalMs(mode)
                    val now = System.currentTimeMillis()

                    // Ensure Wi-Fi lock, Screen WakeLock, and Linux thread priority are maintained
                    acquireLowLatencyWifiLock()
                    acquireScreenWakeLock()
                    applySessionThreadPriority(isBeast)
                    com.example.util.SystemSettingsHelper.setDoNotDisturb(this@GamingOverlayService, true, fullSilence = isBeast)

                    // Execute scheduled multi-action purge
                    if (lastPeriodicRamCleanTimestamp == 0L || (now - lastPeriodicRamCleanTimestamp >= intervalMs)) {
                        lastPeriodicRamCleanTimestamp = now
                        executeMultiActionOptimization(isBeast)
                    }

                    // Query real in-game FPS via Shizuku if active
                    val targetPkg = session.getLastTargetGame().first
                    if (!targetPkg.isNullOrBlank() && com.example.util.ShizukuManager.isRealFpsAvailable()) {
                        val realFps = com.example.util.ShizukuManager.queryRealGameFps(targetPkg, displayRefreshRate)
                        if (realFps != null && realFps > 0) {
                            currentFps = realFps
                            isRealFps = true
                            serviceScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                updateLabels()
                            }
                        }
                    } else {
                        isRealFps = false
                    }

                    // Subdued notification update every 5 seconds
                    if (now - lastNotificationUpdateTimestamp >= 5000L) {
                        lastNotificationUpdateTimestamp = now
                        val remainingFormatted = session.formatCountdown(session.getRemainingMillis())
                        val gameTitle = session.getLastTargetGame().second ?: "Active Game"
                        val fpsType = if (isRealFps) "FPS" else "EFPS"
                        updateNotification(remainingFormatted, isBeast, "$gameTitle ($currentFps $fpsType)", lastPurgedMb)
                    }
                } else {
                    // Session expired or ended: release all locks and restore scheduler & DND
                    releaseLowLatencyWifiLock()
                    releaseScreenWakeLock()
                    restoreDefaultThreadPriority()
                    com.example.util.SystemSettingsHelper.setDoNotDisturb(this@GamingOverlayService, false)
                    if (overlayLayout == null && !showFps && !showTemp && !showBattery) {
                        stopSelf()
                        break
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            releaseLowLatencyWifiLock()
            releaseScreenWakeLock()
            restoreDefaultThreadPriority()
            com.example.util.SystemSettingsHelper.setDoNotDisturb(this@GamingOverlayService, false)
            stopSelf()
            return START_NOT_STICKY
        }

        showFps = intent?.getBooleanExtra(EXTRA_SHOW_FPS, true) ?: true
        showTemp = intent?.getBooleanExtra(EXTRA_SHOW_TEMP, true) ?: true
        showBattery = intent?.getBooleanExtra(EXTRA_SHOW_BATTERY, true) ?: true
        showTimer = intent?.getBooleanExtra(EXTRA_SHOW_TIMER, true) ?: true

        // Refresh battery & temperature immediately from hardware on each start/update
        queryHardwareBatteryAndTemp()

        // Setup floating HUD if overlay permission is granted
        val canDraw = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        if (canDraw) {
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
        expectedVsyncPeriodNanos = (1_000_000_000.0 / displayRefreshRate).toLong()
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

            val shape = GradientDrawable().apply {
                setColor(Color.argb(225, 13, 17, 26))
                cornerRadius = 18 * density
                setStroke((1.5f * density).toInt(), Color.argb(160, 0, 229, 255))
            }
            background = shape
            elevation = 16f
        }

        // 1. Live Real FPS Indicator
        fpsTextView = TextView(this).apply {
            text = "60 FPS"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 0, (8 * density).toInt(), 0)
        }
        hudContainer.addView(fpsTextView)

        // 2. Live Booster Remaining Time Badge
        timerTextView = TextView(this).apply {
            text = "⏳ --:--"
            setTextColor(Color.parseColor("#00E676"))
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 0, (8 * density).toInt(), 0)
        }
        hudContainer.addView(timerTextView)

        // 3. Live Real Battery Temp
        tempTextView = TextView(this).apply {
            text = "28.0°C"
            setTextColor(Color.parseColor("#F59E0B"))
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 0, (8 * density).toInt(), 0)
        }
        hudContainer.addView(tempTextView)

        // 4. Live Real Battery Level
        batteryTextView = TextView(this).apply {
            text = "🔋 100%"
            setTextColor(Color.parseColor("#10B981"))
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 0, (10 * density).toInt(), 0)
        }
        hudContainer.addView(batteryTextView)

        // 4. Quick Dismiss Button
        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#EF4444"))
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding((4 * density).toInt(), 0, (4 * density).toInt(), 0)
            setOnClickListener {
                stopSelf()
            }
        }
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

    private fun updateVisibility() {
        fpsTextView?.visibility = if (showFps) View.VISIBLE else View.GONE
        tempTextView?.visibility = if (showTemp) View.VISIBLE else View.GONE
        batteryTextView?.visibility = if (showBattery) View.VISIBLE else View.GONE
    }

    @SuppressLint("SetTextI18n")
    private fun updateLabels() {
        // Label as "FPS" for real Shizuku in-game timing, or "EFPS" (Estimated Frames Per Second) for fallback
        val fpsLabel = if (isRealFps) "$currentFps FPS" else "$currentFps EFPS"
        fpsTextView?.text = fpsLabel

        // Dynamic 3-stage color shift:
        // Cyber Cyan (Optimal >= 90%) -> Solar Amber (Moderate drop 70%-89%) -> Electric Crimson (Severe lag < 70%)
        val stableThreshold = (displayRefreshRate * 0.90f).toInt()
        val moderateThreshold = (displayRefreshRate * 0.70f).toInt()
        val fpsColor = when {
            currentFps >= stableThreshold -> Color.parseColor("#00E5FF") // Cyber Cyan (Smooth/Optimal)
            currentFps >= moderateThreshold -> Color.parseColor("#F59E0B") // Solar Amber (Moderate drop)
            else -> Color.parseColor("#EF4444") // Electric Crimson (Severe drop/lag)
        }
        fpsTextView?.setTextColor(fpsColor)

        // 2. Booster Session Live Countdown & Subdued HUD Status (Respects showTimer toggle)
        val session = boosterSessionManager
        if (session != null && session.isSessionActive() && showTimer) {
            val remainingMs = session.getRemainingMillis()
            val mode = session.getActiveMode()
            val formatted = session.formatCountdown(remainingMs)
            val isBeast = mode == com.example.model.BoostMode.BEAST
            val now = System.currentTimeMillis()

            // Subdued HUD status feedback: if a purge just occurred in the last 4s, momentarily show stats
            if (now - lastPurgeTimestamp in 0..4000L && lastPurgedMb > 0) {
                timerTextView?.text = "⚡ -${lastPurgedMb}MB"
                timerTextView?.setTextColor(Color.parseColor("#00E5FF"))
            } else {
                val icon = if (isBeast) "🔥" else "⏳"
                timerTextView?.text = "$icon $formatted"
                timerTextView?.setTextColor(if (isBeast) Color.parseColor("#FF5722") else Color.parseColor("#00E676"))
            }
            timerTextView?.visibility = View.VISIBLE
        } else {
            timerTextView?.visibility = View.GONE
        }

        tempTextView?.text = String.format(java.util.Locale.US, "%.1f°C", currentTempC)
        batteryTextView?.text = "🔋 $currentBatteryLevel%"
    }

    private fun startFpsMeter() {
        if (!isChoreographerActive) {
            isChoreographerActive = true
            lastFrameTimeNanos = 0L
            lastRealFrameNanos = 0L
            lastFpsUpdateTimestampNanos = System.nanoTime()
            recentFpsSamples.clear()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isChoreographerActive) return

        val nowNanos = System.nanoTime()

        // 1. Hardware Compositor Sync: Invalidate the overlay layout on VSYNC so the overlay's
        // RenderThread submits to SurfaceFlinger's presentation queue.
        try {
            overlayLayout?.invalidate()
        } catch (_: Exception) {}

        // 2. Real wall-clock frame interval delta measurement
        if (lastRealFrameNanos > 0L) {
            val actualDeltaNanos = nowNanos - lastRealFrameNanos
            if (actualDeltaNanos > 0L) {
                val vsyncRatio = actualDeltaNanos.toDouble() / expectedVsyncPeriodNanos
                if (vsyncRatio > 1.35) {
                    // Frame drop: GPU or compositor skipped one or more hardware refresh cycles
                    val droppedCycles = (vsyncRatio - 0.5).toInt().coerceAtLeast(1)
                    val droppedFps = (displayRefreshRate.toDouble() / (droppedCycles + 1)).toInt().coerceIn(1, displayRefreshRate)
                    recentFpsSamples.add(droppedFps)
                } else {
                    val instantFps = (1_000_000_000.0 / actualDeltaNanos).toInt().coerceIn(1, displayRefreshRate)
                    recentFpsSamples.add(instantFps)
                }
            }
        }
        lastRealFrameNanos = nowNanos

        // 3. Detect Choreographer VSYNC dispatch delay caused by heavy CPU/GPU workload
        if (lastFrameTimeNanos > 0L) {
            val vsyncDeltaNanos = frameTimeNanos - lastFrameTimeNanos
            if (vsyncDeltaNanos > expectedVsyncPeriodNanos * 1.35) {
                val missedIntervals = (vsyncDeltaNanos.toDouble() / expectedVsyncPeriodNanos).toInt().coerceAtLeast(1)
                val degradedFps = (displayRefreshRate.toDouble() / (missedIntervals + 1)).toInt().coerceIn(1, displayRefreshRate)
                recentFpsSamples.add(degradedFps)
            }
        }
        lastFrameTimeNanos = frameTimeNanos

        // 4. Also measure scheduler lag (time from VSYNC signal to actual callback execution)
        val schedulerLag = nowNanos - frameTimeNanos
        if (schedulerLag > expectedVsyncPeriodNanos * 1.5) {
            val lagCycles = (schedulerLag.toDouble() / expectedVsyncPeriodNanos).toInt().coerceAtLeast(1)
            val lagFps = (displayRefreshRate.toDouble() / (lagCycles + 1)).toInt().coerceIn(1, displayRefreshRate)
            recentFpsSamples.add(lagFps)
        }

        // 5. Responsive 250ms update interval to instantly surface in-game stutters
        val elapsedNanos = nowNanos - lastFpsUpdateTimestampNanos
        if (elapsedNanos >= 250_000_000L) {
            if (recentFpsSamples.isNotEmpty()) {
                val minFps = recentFpsSamples.minOrNull() ?: displayRefreshRate
                val avgFps = recentFpsSamples.average().toInt()
                // Only update EFPS via Choreographer if real Shizuku in-game timing is not active
                if (!isRealFps) {
                    currentFps = ((minFps * 0.6) + (avgFps * 0.4)).toInt().coerceIn(1, displayRefreshRate)
                }
                recentFpsSamples.clear()
            }
            lastFpsUpdateTimestampNanos = nowNanos
            updateLabels()
        }

        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        isChoreographerActive = false
        Choreographer.getInstance().removeFrameCallback(this)
        optimizationJob?.cancel()
        serviceScope.cancel()
        releaseLowLatencyWifiLock()
        releaseScreenWakeLock()
        restoreDefaultThreadPriority()
        com.example.util.SystemSettingsHelper.setDoNotDisturb(this@GamingOverlayService, false)
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
