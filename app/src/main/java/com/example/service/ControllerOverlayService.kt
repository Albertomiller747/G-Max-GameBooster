package com.example.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.example.model.ControllerGlyphMap
import com.example.model.ControllerLayoutMode
import com.example.model.ControllerLayoutResolver
import com.example.model.ControllerOverlayConfig
import com.example.model.StickTrackingMode
import com.example.ui.components.ControllerCanvasRenderer
import com.example.util.ControllerHealthManager
import com.example.util.ControllerHealthState
import com.example.util.GamepadDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * System-wide floating Controller Input Screen Overlay Service.
 *
 * Features:
 * - Renders a mini visual controller on screen over any game and highlights every button,
 *   D-pad direction, L1/R1 bumper, L2/R2 trigger, and analog stick deflection in real time.
 * - Supports PlayStation, Xbox, Nintendo Switch, and Auto-Detect layouts.
 * - Automatic screen-aware scaling (adapts to portrait/landscape orientation and display density)
 *   combined with manual size (55% - 165%) and opacity (25% - 100%) adjustment.
 * - Supports drag-to-reposition when unlocked, and 100% touch pass-through (FLAG_NOT_TOUCHABLE)
 *   when locked for intense mobile/controller gameplay.
 */
class ControllerOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: ControllerOverlayView? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateJob: Job? = null
    private var animationJob: Job? = null
    private var gamepadDetector: GamepadDetector? = null
    private var serviceHealthManager: ControllerHealthManager? = null

    private var currentConfig = ControllerOverlayConfig()
    private var currentLayoutMode = ControllerLayoutMode.AUTO
    private var currentGlyphs: ControllerGlyphMap = ControllerLayoutResolver.playStationGlyphs

    companion object {
        const val ACTION_START_OR_UPDATE = "com.example.service.CONTROLLER_OVERLAY_START_OR_UPDATE"
        const val ACTION_STOP = "com.example.service.CONTROLLER_OVERLAY_STOP"

        private const val PREFS_NAME = "gmax_controller_overlay_prefs"
        private const val KEY_ENABLED = "controller_overlay_enabled"
        private const val KEY_AUTO_SCALE = "controller_overlay_auto_scale"
        private const val KEY_SCALE_FACTOR = "controller_overlay_scale_factor"
        private const val KEY_OPACITY = "controller_overlay_opacity"
        private const val KEY_LOCK_TOUCH = "controller_overlay_lock_touch"
        private const val KEY_HIGHLIGHT_COLOR = "controller_overlay_highlight_color"
        private const val KEY_POS_X = "controller_overlay_pos_x"
        private const val KEY_POS_Y = "controller_overlay_pos_y"
        private const val KEY_LAYOUT_MODE = "controller_layout_mode"
        private const val KEY_STICK_TRACKING_MODE = "controller_overlay_stick_tracking_mode"
        private const val KEY_DISABLE_SPEAKER_AUDIO = "controller_overlay_disable_speaker_audio"

        private const val NOTIFICATION_ID = 2009
        private const val CHANNEL_ID = "gmax_controller_overlay_channel"

        var isRunning: Boolean = false
            private set

        @Volatile
        private var cachedConfig: ControllerOverlayConfig? = null

        @Volatile
        private var cachedLayoutMode: ControllerLayoutMode? = null

        fun canRenderSystemOverlay(context: Context): Boolean {
            return Settings.canDrawOverlays(context)
        }

        fun loadConfig(context: Context): ControllerOverlayConfig {
            cachedConfig?.let { return it }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val rawTrackingMode = prefs.getString(KEY_STICK_TRACKING_MODE, StickTrackingMode.SMART_HYBRID.name)
                ?: StickTrackingMode.SMART_HYBRID.name
            val stickTrackingMode = try {
                StickTrackingMode.valueOf(rawTrackingMode)
            } catch (_: Exception) {
                StickTrackingMode.SMART_HYBRID
            }
            return ControllerOverlayConfig(
                isEnabled = prefs.getBoolean(KEY_ENABLED, false),
                autoScale = prefs.getBoolean(KEY_AUTO_SCALE, true),
                scaleFactor = prefs.getFloat(KEY_SCALE_FACTOR, 1.0f).coerceIn(0.55f, 1.65f),
                opacity = prefs.getFloat(KEY_OPACITY, 0.92f).coerceIn(0.25f, 1.0f),
                lockTouchPassThrough = prefs.getBoolean(KEY_LOCK_TOUCH, false),
                highlightColorArgb = prefs.getLong(KEY_HIGHLIGHT_COLOR, 0xFFE5252AL),
                positionX = prefs.getInt(KEY_POS_X, -1),
                positionY = prefs.getInt(KEY_POS_Y, -1),
                stickTrackingMode = stickTrackingMode,
                disableControllerSpeakerAudio = prefs.getBoolean(KEY_DISABLE_SPEAKER_AUDIO, true)
            ).also { cachedConfig = it }
        }

        fun saveConfig(context: Context, config: ControllerOverlayConfig) {
            val normalized = config.copy(
                scaleFactor = config.scaleFactor.coerceIn(0.55f, 1.65f),
                opacity = config.opacity.coerceIn(0.25f, 1.0f)
            )
            cachedConfig = normalized
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, normalized.isEnabled)
                .putBoolean(KEY_AUTO_SCALE, normalized.autoScale)
                .putFloat(KEY_SCALE_FACTOR, normalized.scaleFactor)
                .putFloat(KEY_OPACITY, normalized.opacity)
                .putBoolean(KEY_LOCK_TOUCH, normalized.lockTouchPassThrough)
                .putLong(KEY_HIGHLIGHT_COLOR, normalized.highlightColorArgb)
                .putInt(KEY_POS_X, normalized.positionX)
                .putInt(KEY_POS_Y, normalized.positionY)
                .putString(KEY_STICK_TRACKING_MODE, normalized.stickTrackingMode.name)
                .putBoolean(KEY_DISABLE_SPEAKER_AUDIO, normalized.disableControllerSpeakerAudio)
                .apply()
            ControllerHealthManager.applyControllerSpeakerAudioRouting(
                context,
                normalized.disableControllerSpeakerAudio
            )
        }

        fun loadLayoutMode(context: Context): ControllerLayoutMode {
            cachedLayoutMode?.let { return it }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_LAYOUT_MODE, ControllerLayoutMode.AUTO.name) ?: ControllerLayoutMode.AUTO.name
            val resolved = try {
                ControllerLayoutMode.valueOf(raw)
            } catch (_: Exception) {
                ControllerLayoutMode.AUTO
            }
            cachedLayoutMode = resolved
            return resolved
        }

        fun saveLayoutMode(context: Context, mode: ControllerLayoutMode) {
            cachedLayoutMode = mode
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_LAYOUT_MODE, mode.name)
                .apply()
        }

        fun disableIfNotWired(context: Context) {
            if (!ControllerHealthManager.isWiredControllerConnected(context)) {
                val cfg = loadConfig(context)
                if (cfg.isEnabled) {
                    saveConfig(context, cfg.copy(isEnabled = false))
                }
                if (isRunning) {
                    val intent = Intent(context, ControllerOverlayService::class.java).apply {
                        action = ACTION_STOP
                    }
                    try {
                        context.startService(intent)
                    } catch (_: Exception) {}
                }
            }
        }

        fun syncService(context: Context) {
            val config = loadConfig(context)
            val isWired = ControllerHealthManager.isWiredControllerConnected(context)
            if (isWired) {
                ControllerHealthManager.applyControllerSpeakerAudioRouting(
                    context,
                    config.disableControllerSpeakerAudio
                )
            }
            if (config.isEnabled && !isWired) {
                saveConfig(context, config.copy(isEnabled = false))
                if (isRunning) {
                    val intent = Intent(context, ControllerOverlayService::class.java).apply {
                        action = ACTION_STOP
                    }
                    try {
                        context.startService(intent)
                    } catch (_: Exception) {}
                }
                return
            }
            if (config.isEnabled && canRenderSystemOverlay(context) && isWired) {
                val intent = Intent(context, ControllerOverlayService::class.java).apply {
                    action = ACTION_START_OR_UPDATE
                }
                try {
                    androidx.core.content.ContextCompat.startForegroundService(context, intent)
                } catch (_: Exception) {
                    try {
                        context.startService(intent)
                    } catch (_: Exception) {}
                }
            } else if (isRunning) {
                val intent = Intent(context, ControllerOverlayService::class.java).apply {
                    action = ACTION_STOP
                }
                try {
                    context.startService(intent)
                } catch (_: Exception) {}
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        gamepadDetector = GamepadDetector(this)
        serviceHealthManager = ControllerHealthManager(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "G MAX Controller Overlay",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the floating controller input overlay active over games"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    private fun promoteToForeground() {
        try {
            val openIntent = Intent(this, com.example.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
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
                .setContentTitle("Controller Input Overlay Active")
                .setContentText("Displaying real-time gamepad inputs over your game")
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            removeOverlayView()
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {}
            stopSelf()
            return START_NOT_STICKY
        }

        if (!canRenderSystemOverlay(this)) {
            removeOverlayView()
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {}
            stopSelf()
            return START_NOT_STICKY
        }

        currentConfig = loadConfig(this)
        currentLayoutMode = loadLayoutMode(this)
        if (!currentConfig.isEnabled || !ControllerHealthManager.isWiredControllerConnected(this)) {
            if (currentConfig.isEnabled) {
                saveConfig(this, currentConfig.copy(isEnabled = false))
            }
            removeOverlayView()
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {}
            stopSelf()
            return START_NOT_STICKY
        }

        ControllerHealthManager.applyControllerSpeakerAudioRouting(
            this,
            currentConfig.disableControllerSpeakerAudio
        )
        promoteToForeground()
        refreshGlyphs(force = true)
        showOrUpdateOverlay()
        observeLiveControllerState()
        GamepadRawEventBridge.ensureStarted(applicationContext)

        return START_STICKY
    }

    private var lastResolvedControllerName: String = ""
    private var lastResolvedLayoutMode: ControllerLayoutMode? = null

    private fun refreshGlyphs(force: Boolean = false) {
        val activeName = ControllerHealthManager.liveInputState.value.activeControllerName
        if (!force && activeName == lastResolvedControllerName && currentLayoutMode == lastResolvedLayoutMode) {
            return
        }
        lastResolvedControllerName = activeName
        lastResolvedLayoutMode = currentLayoutMode
        val connected = gamepadDetector?.getConnectedGamepads() ?: emptyList()
        currentGlyphs = ControllerLayoutResolver.getGlyphMap(
            mode = currentLayoutMode,
            gamepads = connected,
            activeControllerName = activeName
        )
    }

    @Volatile
    private var yieldFocusUntilMs: Long = 0L

    private val autoYieldRunnable = Runnable {
        yieldFocusToGameImmediately()
    }

    private fun yieldFocusToGameImmediately() {
        if (ControllerHealthManager.isAppInForeground) return
        val nowMs = System.currentTimeMillis()
        yieldFocusUntilMs = nowMs + 115L
        val view = overlayView ?: return
        val params = windowParams ?: return
        val nonFocusableFlags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (params.flags != nonFocusableFlags || view.isFocusable) {
            params.flags = nonFocusableFlags
            view.isFocusable = false
            view.isFocusableInTouchMode = false
            try {
                windowManager?.updateViewLayout(view, params)
            } catch (_: Exception) {}
        }
    }

    private fun scheduleAutoYieldIfMicroSampling(view: View) {
        view.removeCallbacks(autoYieldRunnable)
        if (!ControllerHealthManager.isAppInForeground) {
            val sliceDurationMs = if (currentConfig.stickTrackingMode == StickTrackingMode.FULL_120HZ_LOCK) 22L else 14L
            view.postDelayed(autoYieldRunnable, sliceDurationMs)
        }
    }

    private fun observeLiveControllerState() {
        if (stateJob?.isActive != true) {
            stateJob = serviceScope.launch {
                ControllerHealthManager.liveInputState.collect { state ->
                    refreshGlyphs(force = false)
                    overlayView?.updateControllerState(state, currentGlyphs, currentConfig)
                }
            }
        }
        if (animationJob?.isActive != true) {
            animationJob = serviceScope.launch {
                var tickCount = 0
                while (true) {
                    kotlinx.coroutines.delay(16L)
                    tickCount++
                    serviceHealthManager?.stepInGameStickAnimation()
                    var configChanged = false
                    if (tickCount % 20 == 0) {
                        val latestMode = loadLayoutMode(this@ControllerOverlayService)
                        val latestConfig = loadConfig(this@ControllerOverlayService)
                        configChanged = latestMode != currentLayoutMode || latestConfig != currentConfig
                        if (configChanged) {
                            currentLayoutMode = latestMode
                            currentConfig = latestConfig
                            refreshGlyphs(force = true)
                        }
                    }
                    val (desiredFlags, shouldFocus) = computeWindowFlags()
                    val currentParams = windowParams
                    val view = overlayView
                    if (view != null && currentParams != null &&
                        (currentParams.flags != desiredFlags || view.isFocusable != shouldFocus)
                    ) {
                        currentParams.flags = desiredFlags
                        view.isFocusable = shouldFocus
                        view.isFocusableInTouchMode = shouldFocus
                        try {
                            windowManager?.updateViewLayout(view, currentParams)
                            if (shouldFocus && !view.hasFocus()) {
                                view.requestFocus()
                                scheduleAutoYieldIfMicroSampling(view)
                            }
                        } catch (_: Exception) {}
                    }
                    if (configChanged) {
                        overlayView?.updateControllerState(
                            ControllerHealthManager.liveInputState.value,
                            currentGlyphs,
                            currentConfig
                        )
                    }
                }
            }
        }
    }

    /**
      * Computes window flags for the floating overlay using an Ultra-Fast 14ms Micro-Peek
      * over a 120ms pass-through cycle when outside G Max Gaming Hub, combined with instant
      * 115ms pass-through (`yieldFocusToGameImmediately`) so the overlay illuminates pressed
      * buttons and sticks while the Android Home Screen and games receive stutter-free controller input.
      */
    private fun computeWindowFlags(): Pair<Int, Boolean> {
        val inForeground = ControllerHealthManager.isAppInForeground
        ControllerHealthManager.isTimeSlicedSamplingActive = !inForeground

        val nowMs = System.currentTimeMillis()
        val recentlyTouchedOutside = (nowMs - ControllerHealthManager.lastOutsideTouchTimestampMs) < 1200L
        val shouldFocusSampleSlice = if (inForeground || recentlyTouchedOutside || nowMs < yieldFocusUntilMs) {
            false
        } else {
            val cycleMs = if (currentConfig.stickTrackingMode == StickTrackingMode.FULL_120HZ_LOCK) 105L else 120L
            val activeWindowMs = if (currentConfig.stickTrackingMode == StickTrackingMode.FULL_120HZ_LOCK) 22L else 14L
            (nowMs % cycleMs) < activeWindowMs
        }

        val focusFlag = if (shouldFocusSampleSlice) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        val touchFlag = if (currentConfig.lockTouchPassThrough) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0

        val baseFlags = touchFlag or
            focusFlag or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        val finalFlags = baseFlags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
        return finalFlags to shouldFocusSampleSlice
    }

    private fun computeOverlayDimensionsPx(): Pair<Int, Int> {
        val dm = resources.displayMetrics
        val density = dm.density
        val minScreenDp = (minOf(dm.widthPixels, dm.heightPixels) / density).coerceAtLeast(320f)

        // Auto-scaling base width in dp based on screen size and orientation
        val autoBaseWidthDp = if (currentConfig.autoScale) {
            (minScreenDp * 0.44f).coerceIn(145f, 235f)
        } else {
            185f
        }
        val finalWidthDp = autoBaseWidthDp * currentConfig.scaleFactor.coerceIn(0.55f, 1.65f)
        val finalHeightDp = finalWidthDp * 0.68f

        val widthPx = (finalWidthDp * density).toInt().coerceAtLeast(120)
        val heightPx = (finalHeightDp * density).toInt().coerceAtLeast(84)
        return widthPx to heightPx
    }

    @SuppressLint("RtlHardcoded")
    private fun showOrUpdateOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            return
        }
        val targetContext: Context = this
        val wm = targetContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val (widthPx, heightPx) = computeOverlayDimensionsPx()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val (touchFlags, shouldFocus) = computeWindowFlags()

        if (overlayView == null || windowManager !== wm) {
            detachOverlayWindowOnly()
            windowManager = wm
            val view = ControllerOverlayView(targetContext).apply {
                isFocusable = shouldFocus
                isFocusableInTouchMode = shouldFocus
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    defaultFocusHighlightEnabled = false
                }
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                outlineProvider = null
                elevation = 0f
                updateControllerState(
                    ControllerHealthManager.liveInputState.value,
                    currentGlyphs,
                    currentConfig
                )
            }
            val dm = resources.displayMetrics
            val defaultX = if (currentConfig.positionX >= 0) currentConfig.positionX else (dm.widthPixels - widthPx) / 2
            val defaultY = if (currentConfig.positionY >= 0) currentConfig.positionY else (dm.heightPixels - heightPx - (48 * dm.density).toInt()).coerceAtLeast(40)

            val params = WindowManager.LayoutParams(
                widthPx,
                heightPx,
                layoutType,
                touchFlags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                x = defaultX
                y = defaultY
                dimAmount = 0f
                windowAnimations = 0
            }

            windowParams = params
            overlayView = view
            try {
                windowManager?.addView(view, params)
                if (shouldFocus) {
                    view.requestFocus()
                    scheduleAutoYieldIfMicroSampling(view)
                }
                isRunning = true
            } catch (_: Exception) {
                overlayView = null
                isRunning = false
            }
        } else {
            overlayView?.apply {
                isFocusable = shouldFocus
                isFocusableInTouchMode = shouldFocus
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    defaultFocusHighlightEnabled = false
                }
                updateControllerState(
                    ControllerHealthManager.liveInputState.value,
                    currentGlyphs,
                    currentConfig
                )
            }
            windowParams?.let { params ->
                params.width = widthPx
                params.height = heightPx
                params.flags = touchFlags
                params.dimAmount = 0f
                try {
                    windowManager?.updateViewLayout(overlayView, params)
                    val view = overlayView
                    if (shouldFocus && view != null && !view.hasFocus()) {
                        view.requestFocus()
                        scheduleAutoYieldIfMicroSampling(view)
                    }
                } catch (_: Exception) {}
            }
        }
        observeLiveControllerState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (overlayView != null && currentConfig.autoScale) {
            showOrUpdateOverlay()
        }
    }

    private fun detachOverlayWindowOnly() {
        val view = overlayView
        if (view != null && windowManager != null) {
            view.removeCallbacks(autoYieldRunnable)
            try {
                windowManager?.removeViewImmediate(view)
            } catch (_: Exception) {
                try {
                    windowManager?.removeView(view)
                } catch (_: Exception) {}
            }
        }
        overlayView = null
    }

    private fun removeOverlayView() {
        stateJob?.cancel()
        stateJob = null
        animationJob?.cancel()
        animationJob = null
        detachOverlayWindowOnly()
        isRunning = false
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        @Suppress("DEPRECATION")
        overlayView?.destroyDrawingCache()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        @Suppress("DEPRECATION")
        overlayView?.destroyDrawingCache()
    }

    override fun onDestroy() {
        removeOverlayView()
        gamepadDetector?.destroy()
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Custom hardware-accelerated View that renders the controller overlay at 60 FPS with smooth
     * analog stick interpolation and supports drag-to-move.
     */
    inner class ControllerOverlayView(context: Context) : View(context) {

        private var state: ControllerHealthState = ControllerHealthState()
        private var glyphs: ControllerGlyphMap = ControllerLayoutResolver.playStationGlyphs
        private var config: ControllerOverlayConfig = ControllerOverlayConfig()

        // Smooth 60 FPS visual interpolation coordinates for left and right sticks
        private var displayLeftX = 0f
        private var displayLeftY = 0f
        private var displayRightX = 0f
        private var displayRightY = 0f

        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var isDragging = false

        fun updateControllerState(
            newState: ControllerHealthState,
            newGlyphs: ControllerGlyphMap,
            newConfig: ControllerOverlayConfig
        ) {
            if (state == newState && glyphs == newGlyphs && config == newConfig) {
                return
            }
            state = newState
            glyphs = newGlyphs
            config = newConfig
            invalidate()
        }

        init {
            isFocusable = false
            isFocusableInTouchMode = false
            @Suppress("DEPRECATION")
            setWillNotCacheDrawing(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                defaultFocusHighlightEnabled = false
            }
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            outlineProvider = null
            elevation = 0f
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val targetLx = state.leftStick.x
            val targetLy = state.leftStick.y
            val targetRx = state.rightStick.x
            val targetRy = state.rightStick.y

            // In-app (120Hz native stream) uses near-instant 0.78f lerp; in-game 14ms micro-peek mode uses
            // 0.55f per-frame 60 FPS lerp so thumbsticks glide smoothly without stepping.
            val lerpAlpha = if (ControllerHealthManager.isTimeSlicedSamplingActive) 0.55f else 0.78f

            displayLeftX += (targetLx - displayLeftX) * lerpAlpha
            displayLeftY += (targetLy - displayLeftY) * lerpAlpha
            displayRightX += (targetRx - displayRightX) * lerpAlpha
            displayRightY += (targetRy - displayRightY) * lerpAlpha

            val remDelta = maxOf(
                abs(targetLx - displayLeftX),
                abs(targetLy - displayLeftY),
                abs(targetRx - displayRightX),
                abs(targetRy - displayRightY)
            )
            if (remDelta <= 0.005f) {
                displayLeftX = targetLx
                displayLeftY = targetLy
                displayRightX = targetRx
                displayRightY = targetRy
            }

            val renderState = if (
                displayLeftX == targetLx && displayLeftY == targetLy &&
                displayRightX == targetRx && displayRightY == targetRy
            ) {
                state
            } else {
                val lMag = kotlin.math.hypot(displayLeftX, displayLeftY)
                val rMag = kotlin.math.hypot(displayRightX, displayRightY)
                state.copy(
                    leftStick = state.leftStick.copy(
                        x = displayLeftX,
                        y = displayLeftY,
                        magnitude = lMag
                    ),
                    rightStick = state.rightStick.copy(
                        x = displayRightX,
                        y = displayRightY,
                        magnitude = rMag
                    )
                )
            }

            ControllerCanvasRenderer.drawController(
                canvas = canvas,
                width = width.toFloat(),
                height = height.toFloat(),
                state = renderState,
                glyphs = glyphs,
                highlightArgb = config.highlightColorArgb,
                opacity = config.opacity
            )

            if (remDelta > 0.005f) {
                postInvalidateOnAnimation()
            }
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            serviceHealthManager?.onKeyEvent(event)
            yieldFocusToGameImmediately()
            return false
        }

        override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
            serviceHealthManager?.onKeyEvent(event)
            yieldFocusToGameImmediately()
            return false
        }

        override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
            serviceHealthManager?.onKeyEvent(event)
            yieldFocusToGameImmediately()
            return false
        }

        override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
            val handled = serviceHealthManager?.onGenericMotionEvent(event) == true
            yieldFocusToGameImmediately()
            // CRITICAL: Return true when handled so Android's ViewRootImpl.FallbackEventHandler
            // NEVER converts left-stick movement into synthetic KEYCODE_DPAD_UP/DOWN/LEFT/RIGHT KeyEvents!
            return handled
        }

        override fun onGenericMotionEvent(event: MotionEvent): Boolean {
            val handled = serviceHealthManager?.onGenericMotionEvent(event) == true
            yieldFocusToGameImmediately()
            return handled
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                ControllerHealthManager.notifyOutsideTouch()
                showOrUpdateOverlay()
                return false
            }
            if (config.lockTouchPassThrough) return false
            val params = windowParams ?: return false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 6 || abs(dy) > 6) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try {
                            windowManager?.updateViewLayout(this, params)
                        } catch (_: Exception) {}
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        currentConfig = currentConfig.copy(positionX = params.x, positionY = params.y)
                        saveConfig(context, currentConfig)
                    }
                    isDragging = false
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }
}
