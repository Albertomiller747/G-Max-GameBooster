package com.example.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * High-performance hardware overlay service that draws a centered tactical gaming crosshair.
 *
 * CRITICAL GAMING ARCHITECTURE:
 * - Uses FLAG_NOT_TOUCHABLE so it NEVER intercepts touch events, ensuring 100% full touch
 *   pass-through for high-intensity mobile gaming (PUBG, COD, Free Fire, etc.).
 * - Positioned with Gravity.CENTER for exact screen-center reticle alignment.
 * - Hardware Canvas rendering with dual-pass (contrast outline + neon core) for crisp visibility
 *   on both ultra-bright (desert/snow maps) and pitch-black night environments.
 * - Persists across app switches, portrait/landscape game transitions, and Recents task removal.
 */
class CrosshairOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var crosshairView: CrosshairView? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + com.example.util.AppCrashGuard.coroutineExceptionHandler)
    private var watchdogJob: Job? = null
    private var stoppingIntentionally = false

    private var displayListener: android.hardware.display.DisplayManager.DisplayListener? = null

    private fun registerDisplayChangeListener() {
        if (displayListener != null) return
        val dm = applicationContext.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager ?: return
        val listener = object : android.hardware.display.DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                val saved = getSavedConfig(this@CrosshairOverlayService)
                val canDraw = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this@CrosshairOverlayService)
                if (saved.isEnabled && canDraw) {
                    showOrUpdateCrosshair(
                        saved.crosshairStyle,
                        saved.crosshairColor,
                        saved.crosshairSizeDp,
                        saved.crosshairAlpha,
                        saved.offsetXdp,
                        saved.offsetYdp,
                        saved.strokeThicknessDp,
                        saved.centerGapDp,
                        saved.showOutline,
                        saved.showCenterDot
                    )
                }
            }
        }
        try {
            dm.registerDisplayListener(listener, android.os.Handler( android.os.Looper.getMainLooper()))
            displayListener = listener
        } catch (_: Exception) {}
    }

    private fun unregisterDisplayChangeListener() {
        val listener = displayListener ?: return
        displayListener = null
        try {
            val dm = applicationContext.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
            dm?.unregisterDisplayListener(listener)
        } catch (_: Exception) {}
    }

    companion object {
        const val ACTION_START = "com.example.service.CROSSHAIR_START"
        const val ACTION_STOP = "com.example.service.CROSSHAIR_STOP"
        const val ACTION_UPDATE = "com.example.service.CROSSHAIR_UPDATE"
        const val ACTION_RESTORE = "com.example.service.CROSSHAIR_RESTORE"

        const val EXTRA_STYLE = "EXTRA_STYLE"
        const val EXTRA_COLOR = "EXTRA_COLOR"
        const val EXTRA_SIZE_DP = "EXTRA_SIZE_DP"
        const val EXTRA_ALPHA = "EXTRA_ALPHA"
        const val EXTRA_OFFSET_X_DP = "EXTRA_OFFSET_X_DP"
        const val EXTRA_OFFSET_Y_DP = "EXTRA_OFFSET_Y_DP"
        const val EXTRA_STROKE_DP = "EXTRA_STROKE_DP"
        const val EXTRA_GAP_DP = "EXTRA_GAP_DP"
        const val EXTRA_SHOW_OUTLINE = "EXTRA_SHOW_OUTLINE"
        const val EXTRA_SHOW_CENTER_DOT = "EXTRA_SHOW_CENTER_DOT"

        private const val PREFS_NAME = "gmax_crosshair_prefs"
        private const val KEY_ACTIVE_ACCOUNT = "active_account_key"
        private const val KEY_IS_ENABLED = "crosshair_enabled"
        private const val KEY_STYLE = "crosshair_style"
        private const val KEY_COLOR = "crosshair_color"
        private const val KEY_SIZE_DP = "crosshair_size_dp"
        private const val KEY_ALPHA = "crosshair_alpha"
        private const val KEY_OFFSET_X_DP = "crosshair_offset_x_dp"
        private const val KEY_OFFSET_Y_DP = "crosshair_offset_y_dp"
        private const val KEY_STROKE_DP = "crosshair_stroke_dp"
        private const val KEY_GAP_DP = "crosshair_gap_dp"
        private const val KEY_SHOW_OUTLINE = "crosshair_show_outline"
        private const val KEY_SHOW_CENTER_DOT = "crosshair_show_center_dot"

        private var activeViewInstance: CrosshairView? = null

        @Volatile
        var isRunning: Boolean = false
            private set

        /**
         * Resolves the true physical full-screen pixel dimensions of the phone display,
         * ignoring status bars, navigation bars, and camera punch-hole cutouts so the
         * crosshair aligns with the exact pixel center of full-screen 3D games.
         */
        fun resolvePhysicalScreenDimensionsPx(
            context: Context,
            wm: WindowManager? = null
        ): Pair<Int, Int> {
            val effectiveWm = wm ?: (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
            if (effectiveWm != null) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val bounds = effectiveWm.maximumWindowMetrics.bounds
                        if (bounds.width() > 0 && bounds.height() > 0) {
                            return Pair(bounds.width(), bounds.height())
                        }
                    }
                } catch (_: Throwable) {}

                try {
                    val dm = android.util.DisplayMetrics()
                    @Suppress("DEPRECATION")
                    effectiveWm.defaultDisplay?.getRealMetrics(dm)
                    if (dm.widthPixels > 0 && dm.heightPixels > 0) {
                        return Pair(dm.widthPixels, dm.heightPixels)
                    }
                } catch (_: Throwable) {}
            }
            val resDm = context.resources.displayMetrics
            return Pair(resDm.widthPixels.coerceAtLeast(1), resDm.heightPixels.coerceAtLeast(1))
        }

        /**
         * Computes the exact top-left window coordinates `(x, y)` for a square overlay window of
         * [windowSizePx] so its geometric center `(windowSizePx / 2f, windowSizePx / 2f)` lands
         * on the exact physical screen center `(screenWidthPx / 2f + offsetXpx, screenHeightPx / 2f + offsetYpx)`.
         */
        fun computeCenteredWindowPositionPx(
            screenWidthPx: Int,
            screenHeightPx: Int,
            windowSizePx: Int,
            offsetXpx: Int,
            offsetYpx: Int
        ): Pair<Int, Int> {
            val exactLeft = kotlin.math.round((screenWidthPx - windowSizePx) / 2.0f).toInt() + offsetXpx
            val exactTop = kotlin.math.round((screenHeightPx - windowSizePx) / 2.0f).toInt() + offsetYpx
            return Pair(exactLeft, exactTop)
        }

        fun resolveAccountSuffix(context: Context, accountKeyOverride: String? = null): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = (accountKeyOverride ?: prefs.getString(KEY_ACTIVE_ACCOUNT, "guest_local")).orEmpty().trim()
            val sanitized = raw.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "guest_local" }
            return "_$sanitized"
        }

        fun setActiveAccount(context: Context, accountKey: String) {
            val sanitized = accountKey.trim().replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "guest_local" }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_ACTIVE_ACCOUNT, sanitized)
                .apply()
        }

        fun isSavedEnabled(context: Context, accountKeyOverride: String? = null): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val sfx = resolveAccountSuffix(context, accountKeyOverride)
            return if (prefs.contains("$KEY_IS_ENABLED$sfx")) {
                prefs.getBoolean("$KEY_IS_ENABLED$sfx", false)
            } else {
                prefs.getBoolean(KEY_IS_ENABLED, false)
            }
        }

        fun getSavedConfig(context: Context, accountKeyOverride: String? = null): com.example.model.OverlaySettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val sfx = resolveAccountSuffix(context, accountKeyOverride)
            val hasAccountScoped = prefs.contains("$KEY_STYLE$sfx") || prefs.contains("$KEY_IS_ENABLED$sfx")
            return if (hasAccountScoped) {
                com.example.model.OverlaySettings(
                    isEnabled = prefs.getBoolean("$KEY_IS_ENABLED$sfx", prefs.getBoolean(KEY_IS_ENABLED, false)),
                    crosshairStyle = prefs.getInt("$KEY_STYLE$sfx", prefs.getInt(KEY_STYLE, 0)),
                    crosshairColor = prefs.getLong("$KEY_COLOR$sfx", prefs.getLong(KEY_COLOR, 0xFF00E5FFL)),
                    crosshairSizeDp = prefs.getInt("$KEY_SIZE_DP$sfx", prefs.getInt(KEY_SIZE_DP, 32)),
                    crosshairAlpha = prefs.getFloat("$KEY_ALPHA$sfx", prefs.getFloat(KEY_ALPHA, 0.95f)),
                    offsetXdp = prefs.getInt("$KEY_OFFSET_X_DP$sfx", prefs.getInt(KEY_OFFSET_X_DP, 0)),
                    offsetYdp = prefs.getInt("$KEY_OFFSET_Y_DP$sfx", prefs.getInt(KEY_OFFSET_Y_DP, 0)),
                    strokeThicknessDp = prefs.getFloat("$KEY_STROKE_DP$sfx", prefs.getFloat(KEY_STROKE_DP, 2.5f)),
                    centerGapDp = prefs.getFloat("$KEY_GAP_DP$sfx", prefs.getFloat(KEY_GAP_DP, 4.0f)),
                    showOutline = prefs.getBoolean("$KEY_SHOW_OUTLINE$sfx", prefs.getBoolean(KEY_SHOW_OUTLINE, true)),
                    showCenterDot = prefs.getBoolean("$KEY_SHOW_CENTER_DOT$sfx", prefs.getBoolean(KEY_SHOW_CENTER_DOT, true))
                )
            } else {
                com.example.model.OverlaySettings(
                    isEnabled = prefs.getBoolean(KEY_IS_ENABLED, false),
                    crosshairStyle = prefs.getInt(KEY_STYLE, 0),
                    crosshairColor = prefs.getLong(KEY_COLOR, 0xFF00E5FFL),
                    crosshairSizeDp = prefs.getInt(KEY_SIZE_DP, 32),
                    crosshairAlpha = prefs.getFloat(KEY_ALPHA, 0.95f),
                    offsetXdp = prefs.getInt(KEY_OFFSET_X_DP, 0),
                    offsetYdp = prefs.getInt(KEY_OFFSET_Y_DP, 0),
                    strokeThicknessDp = prefs.getFloat(KEY_STROKE_DP, 2.5f),
                    centerGapDp = prefs.getFloat(KEY_GAP_DP, 4.0f),
                    showOutline = prefs.getBoolean(KEY_SHOW_OUTLINE, true),
                    showCenterDot = prefs.getBoolean(KEY_SHOW_CENTER_DOT, true)
                )
            }
        }

        fun saveConfig(
            context: Context,
            enabled: Boolean,
            style: Int,
            color: Long,
            sizeDp: Int,
            alpha: Float,
            offsetXdp: Int? = null,
            offsetYdp: Int? = null,
            strokeThicknessDp: Float? = null,
            centerGapDp: Float? = null,
            showOutline: Boolean? = null,
            showCenterDot: Boolean? = null,
            accountKeyOverride: String? = null
        ) {
            val current = getSavedConfig(context, accountKeyOverride)
            val sfx = resolveAccountSuffix(context, accountKeyOverride)
            val resOffsetX = (offsetXdp ?: current.offsetXdp).coerceIn(-24, 24)
            val resOffsetY = (offsetYdp ?: current.offsetYdp).coerceIn(-24, 24)
            val resStroke = (strokeThicknessDp ?: current.strokeThicknessDp).coerceIn(1.0f, 6.0f)
            val resGap = (centerGapDp ?: current.centerGapDp).coerceIn(0.0f, 16.0f)
            val resOutline = showOutline ?: current.showOutline
            val resDot = showCenterDot ?: current.showCenterDot

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_IS_ENABLED, enabled)
                .putInt(KEY_STYLE, style)
                .putLong(KEY_COLOR, color)
                .putInt(KEY_SIZE_DP, sizeDp)
                .putFloat(KEY_ALPHA, alpha)
                .putInt(KEY_OFFSET_X_DP, resOffsetX)
                .putInt(KEY_OFFSET_Y_DP, resOffsetY)
                .putFloat(KEY_STROKE_DP, resStroke)
                .putFloat(KEY_GAP_DP, resGap)
                .putBoolean(KEY_SHOW_OUTLINE, resOutline)
                .putBoolean(KEY_SHOW_CENTER_DOT, resDot)
                // Account-isolated keys
                .putBoolean("$KEY_IS_ENABLED$sfx", enabled)
                .putInt("$KEY_STYLE$sfx", style)
                .putLong("$KEY_COLOR$sfx", color)
                .putInt("$KEY_SIZE_DP$sfx", sizeDp)
                .putFloat("$KEY_ALPHA$sfx", alpha)
                .putInt("$KEY_OFFSET_X_DP$sfx", resOffsetX)
                .putInt("$KEY_OFFSET_Y_DP$sfx", resOffsetY)
                .putFloat("$KEY_STROKE_DP$sfx", resStroke)
                .putFloat("$KEY_GAP_DP$sfx", resGap)
                .putBoolean("$KEY_SHOW_OUTLINE$sfx", resOutline)
                .putBoolean("$KEY_SHOW_CENTER_DOT$sfx", resDot)
                .apply()
        }

        fun saveSettingsObject(
            context: Context,
            settings: com.example.model.OverlaySettings,
            accountKeyOverride: String? = null
        ) {
            saveConfig(
                context = context,
                enabled = settings.isEnabled,
                style = settings.crosshairStyle,
                color = settings.crosshairColor,
                sizeDp = settings.crosshairSizeDp,
                alpha = settings.crosshairAlpha,
                offsetXdp = settings.offsetXdp,
                offsetYdp = settings.offsetYdp,
                strokeThicknessDp = settings.strokeThicknessDp,
                centerGapDp = settings.centerGapDp,
                showOutline = settings.showOutline,
                showCenterDot = settings.showCenterDot,
                accountKeyOverride = accountKeyOverride
            )
        }

        /**
         * Synchronizes the system-wide CrosshairOverlayService with the persisted configuration.
         * Ensures the floating hardware reticle starts or stays attached when entering/leaving the app
         * or returning from the Android "Display over other apps" permission screen.
         */
        fun syncService(context: Context) {
            val appCtx = context.applicationContext
            val saved = getSavedConfig(appCtx)
            val canDraw = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(appCtx)
            if (saved.isEnabled && canDraw) {
                val intent = Intent(appCtx, CrosshairOverlayService::class.java).apply {
                    action = if (isRunning) ACTION_UPDATE else ACTION_START
                    putExtra(EXTRA_STYLE, saved.crosshairStyle)
                    putExtra(EXTRA_COLOR, saved.crosshairColor)
                    putExtra(EXTRA_SIZE_DP, saved.crosshairSizeDp)
                    putExtra(EXTRA_ALPHA, saved.crosshairAlpha)
                    putExtra(EXTRA_OFFSET_X_DP, saved.offsetXdp)
                    putExtra(EXTRA_OFFSET_Y_DP, saved.offsetYdp)
                    putExtra(EXTRA_STROKE_DP, saved.strokeThicknessDp)
                    putExtra(EXTRA_GAP_DP, saved.centerGapDp)
                    putExtra(EXTRA_SHOW_OUTLINE, saved.showOutline)
                    putExtra(EXTRA_SHOW_CENTER_DOT, saved.showCenterDot)
                }
                try {
                    appCtx.startService(intent)
                } catch (_: Exception) {}
            } else if (!saved.isEnabled && isRunning) {
                val intent = Intent(appCtx, CrosshairOverlayService::class.java).apply {
                    action = ACTION_STOP
                }
                try {
                    appCtx.startService(intent)
                } catch (_: Exception) {
                    try {
                        appCtx.stopService(Intent(appCtx, CrosshairOverlayService::class.java))
                    } catch (_: Exception) {}
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stoppingIntentionally = true
            watchdogJob?.cancel()
            unregisterDisplayChangeListener()
            stopCrosshair()
            val saved = getSavedConfig(this)
            saveSettingsObject(this, saved.copy(isEnabled = false))
            stopSelf()
            return START_NOT_STICKY
        }

        stoppingIntentionally = false
        val canDraw = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        if (!canDraw) {
            watchdogJob?.cancel()
            unregisterDisplayChangeListener()
            stopCrosshair()
            stopSelf()
            return START_NOT_STICKY
        }

        registerDisplayChangeListener()
        val saved = getSavedConfig(this)

        if (intent == null || intent.action == ACTION_RESTORE) {
            if (saved.isEnabled) {
                showOrUpdateCrosshair(
                    saved.crosshairStyle,
                    saved.crosshairColor,
                    saved.crosshairSizeDp,
                    saved.crosshairAlpha,
                    saved.offsetXdp,
                    saved.offsetYdp,
                    saved.strokeThicknessDp,
                    saved.centerGapDp,
                    saved.showOutline,
                    saved.showCenterDot
                )
                ensureWatchdogRunning()
                return START_STICKY
            } else {
                watchdogJob?.cancel()
                unregisterDisplayChangeListener()
                stopCrosshair()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val style = intent.getIntExtra(EXTRA_STYLE, saved.crosshairStyle)
        val colorLong = intent.getLongExtra(EXTRA_COLOR, saved.crosshairColor)
        val sizeDp = intent.getIntExtra(EXTRA_SIZE_DP, saved.crosshairSizeDp)
        val alpha = intent.getFloatExtra(EXTRA_ALPHA, saved.crosshairAlpha)
        val offsetXdp = intent.getIntExtra(EXTRA_OFFSET_X_DP, saved.offsetXdp)
        val offsetYdp = intent.getIntExtra(EXTRA_OFFSET_Y_DP, saved.offsetYdp)
        val strokeDp = intent.getFloatExtra(EXTRA_STROKE_DP, saved.strokeThicknessDp)
        val gapDp = intent.getFloatExtra(EXTRA_GAP_DP, saved.centerGapDp)
        val showOutline = intent.getBooleanExtra(EXTRA_SHOW_OUTLINE, saved.showOutline)
        val showCenterDot = intent.getBooleanExtra(EXTRA_SHOW_CENTER_DOT, saved.showCenterDot)

        saveConfig(
            this,
            true,
            style,
            colorLong,
            sizeDp,
            alpha,
            offsetXdp,
            offsetYdp,
            strokeDp,
            gapDp,
            showOutline,
            showCenterDot
        )
        showOrUpdateCrosshair(
            style,
            colorLong,
            sizeDp,
            alpha,
            offsetXdp,
            offsetYdp,
            strokeDp,
            gapDp,
            showOutline,
            showCenterDot
        )
        ensureWatchdogRunning()

        return START_STICKY
    }

    private fun ensureWatchdogRunning() {
        if (watchdogJob?.isActive == true) return
        watchdogJob = serviceScope.launch {
            while (isActive) {
                delay(2000L)
                val saved = getSavedConfig(this@CrosshairOverlayService)
                val canDraw = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                    Settings.canDrawOverlays(this@CrosshairOverlayService)
                if (!saved.isEnabled || !canDraw) {
                    stopCrosshair()
                    stopSelf()
                    break
                }
                val currentView = crosshairView ?: activeViewInstance
                if (currentView == null || !currentView.isAttachedToWindow) {
                    showOrUpdateCrosshair(
                        saved.crosshairStyle,
                        saved.crosshairColor,
                        saved.crosshairSizeDp,
                        saved.crosshairAlpha,
                        saved.offsetXdp,
                        saved.offsetYdp,
                        saved.strokeThicknessDp,
                        saved.centerGapDp,
                        saved.showOutline,
                        saved.showCenterDot
                    )
                }
            }
        }
    }

    private fun showOrUpdateCrosshair(
        style: Int,
        colorLong: Long,
        sizeDp: Int,
        alpha: Float,
        offsetXdp: Int = 0,
        offsetYdp: Int = 0,
        strokeDp: Float = 2.5f,
        gapDp: Float = 4.0f,
        showOutline: Boolean = true,
        showCenterDot: Boolean = true
    ) {
        val existingView = crosshairView ?: activeViewInstance
        if (existingView != null && existingView.isAttachedToWindow && windowManager != null) {
            updateCrosshair(style, colorLong, sizeDp, alpha, offsetXdp, offsetYdp, strokeDp, gapDp, showOutline, showCenterDot)
        } else {
            showCrosshair(style, colorLong, sizeDp, alpha, offsetXdp, offsetYdp, strokeDp, gapDp, showOutline, showCenterDot)
        }
    }

    @SuppressLint("RtlHardcoded")
    private fun showCrosshair(
        style: Int,
        colorLong: Long,
        sizeDp: Int,
        alpha: Float,
        offsetXdp: Int = 0,
        offsetYdp: Int = 0,
        strokeDp: Float = 2.5f,
        gapDp: Float = 4.0f,
        showOutline: Boolean = true,
        showCenterDot: Boolean = true
    ) {
        val appCtx = applicationContext
        windowManager = appCtx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return

        // Clean up any stale view before creating a new one
        stopCrosshair()

        val density = appCtx.resources.displayMetrics.density
        val reticleSizePx = (sizeDp * density).toInt().coerceAtLeast(24)
        // Add generous unclipped canvas margin around reticleSizePx so thick strokes, outer contrast
        // shadows, and BDC chevron ticks never clip against the window boundary.
        val canvasPaddingPx = (14f * density).toInt().coerceAtLeast(12)
        val windowSizePx = reticleSizePx + (canvasPaddingPx * 2)
        val offsetXpx = kotlin.math.round(offsetXdp * density).toInt()
        val offsetYpx = kotlin.math.round(offsetYdp * density).toInt()

        val (screenW, screenH) = resolvePhysicalScreenDimensionsPx(appCtx, windowManager)
        val (exactLeftPx, exactTopPx) = computeCenteredWindowPositionPx(
            screenWidthPx = screenW,
            screenHeightPx = screenH,
            windowSizePx = windowSizePx,
            offsetXpx = offsetXpx,
            offsetYpx = offsetYpx
        )

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val newView = CrosshairView(appCtx).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
            @Suppress("DEPRECATION")
            setWillNotCacheDrawing(true)
            updateConfig(
                newStyle = style,
                colorLong = colorLong,
                newSizePx = reticleSizePx.toFloat(),
                newAlpha = alpha,
                newStrokePx = (strokeDp * density).coerceAtLeast(1.5f),
                newGapPx = (gapDp * density).coerceAtLeast(0f),
                newShowOutline = showOutline,
                newShowCenterDot = showCenterDot
            )
        }
        crosshairView = newView
        activeViewInstance = newView

        val params = WindowManager.LayoutParams(
            windowSizePx,
            windowSizePx,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                    WindowManager.LayoutParams.FLAG_SPLIT_TOUCH or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            // Anchor with TOP | LEFT using true full-screen physical pixel coordinates so camera
            // cutouts, status bars, and navigation bars never shift the reticle off screen center.
            gravity = Gravity.TOP or Gravity.LEFT
            x = exactLeftPx
            y = exactTopPx
            dimAmount = 0f
            windowAnimations = 0
            packageName = appCtx.packageName
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                fitInsetsTypes = 0
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        windowParams = params

        try {
            windowManager?.addView(newView, params)
            isRunning = true
        } catch (_: Exception) {
            crosshairView = null
            activeViewInstance = null
            isRunning = false
        }
    }

    @SuppressLint("RtlHardcoded")
    private fun updateCrosshair(
        style: Int,
        colorLong: Long,
        sizeDp: Int,
        alpha: Float,
        offsetXdp: Int = 0,
        offsetYdp: Int = 0,
        strokeDp: Float = 2.5f,
        gapDp: Float = 4.0f,
        showOutline: Boolean = true,
        showCenterDot: Boolean = true
    ) {
        val appCtx = applicationContext
        val density = appCtx.resources.displayMetrics.density
        val reticleSizePx = (sizeDp * density).toInt().coerceAtLeast(24)
        val canvasPaddingPx = (14f * density).toInt().coerceAtLeast(12)
        val windowSizePx = reticleSizePx + (canvasPaddingPx * 2)
        val offsetXpx = kotlin.math.round(offsetXdp * density).toInt()
        val offsetYpx = kotlin.math.round(offsetYdp * density).toInt()

        val (screenW, screenH) = resolvePhysicalScreenDimensionsPx(appCtx, windowManager)
        val (exactLeftPx, exactTopPx) = computeCenteredWindowPositionPx(
            screenWidthPx = screenW,
            screenHeightPx = screenH,
            windowSizePx = windowSizePx,
            offsetXpx = offsetXpx,
            offsetYpx = offsetYpx
        )

        val view = crosshairView ?: activeViewInstance
        if (view == null) {
            showCrosshair(style, colorLong, sizeDp, alpha, offsetXdp, offsetYdp, strokeDp, gapDp, showOutline, showCenterDot)
            return
        }
        view.updateConfig(
            newStyle = style,
            colorLong = colorLong,
            newSizePx = reticleSizePx.toFloat(),
            newAlpha = alpha,
            newStrokePx = (strokeDp * density).coerceAtLeast(1.5f),
            newGapPx = (gapDp * density).coerceAtLeast(0f),
            newShowOutline = showOutline,
            newShowCenterDot = showCenterDot
        )

        windowParams?.let { params ->
            params.width = windowSizePx
            params.height = windowSizePx
            params.gravity = Gravity.TOP or Gravity.LEFT
            params.x = exactLeftPx
            params.y = exactTopPx
            try {
                windowManager?.updateViewLayout(view, params)
                isRunning = true
            } catch (_: Exception) {
                showCrosshair(style, colorLong, sizeDp, alpha, offsetXdp, offsetYdp, strokeDp, gapDp, showOutline, showCenterDot)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val saved = getSavedConfig(this)
        val canDraw = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        if (saved.isEnabled && canDraw) {
            showOrUpdateCrosshair(
                saved.crosshairStyle,
                saved.crosshairColor,
                saved.crosshairSizeDp,
                saved.crosshairAlpha,
                saved.offsetXdp,
                saved.offsetYdp,
                saved.strokeThicknessDp,
                saved.centerGapDp,
                saved.showOutline,
                saved.showCenterDot
            )
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val saved = getSavedConfig(this)
        val canDraw = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        if (saved.isEnabled && canDraw && !stoppingIntentionally) {
            showOrUpdateCrosshair(
                saved.crosshairStyle,
                saved.crosshairColor,
                saved.crosshairSizeDp,
                saved.crosshairAlpha,
                saved.offsetXdp,
                saved.offsetYdp,
                saved.strokeThicknessDp,
                saved.centerGapDp,
                saved.showOutline,
                saved.showCenterDot
            )
            scheduleRestoreAlarm()
        }
    }

    private fun scheduleRestoreAlarm() {
        try {
            val appCtx = applicationContext
            val restoreIntent = Intent(appCtx, CrosshairOverlayService::class.java).apply {
                action = ACTION_RESTORE
            }
            try {
                appCtx.startService(restoreIntent)
            } catch (_: Exception) {}

            val flags = PendingIntent.FLAG_ONE_SHOT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            val pendingIntent = PendingIntent.getService(
                appCtx,
                4091,
                restoreIntent,
                flags
            )
            val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 300L,
                pendingIntent
            )
        } catch (_: Exception) {}
    }

    private fun stopCrosshair() {
        if (windowManager == null) {
            windowManager = applicationContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        }
        val view = crosshairView ?: activeViewInstance
        if (view != null && windowManager != null) {
            try {
                windowManager?.removeViewImmediate(view)
            } catch (_: Exception) {
                try {
                    windowManager?.removeView(view)
                } catch (_: Exception) {}
            }
            crosshairView = null
            activeViewInstance = null
        }
        isRunning = false
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        @Suppress("DEPRECATION")
        crosshairView?.destroyDrawingCache()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        @Suppress("DEPRECATION")
        crosshairView?.destroyDrawingCache()
    }

    override fun onDestroy() {
        watchdogJob?.cancel()
        unregisterDisplayChangeListener()
        serviceScope.cancel()
        val shouldKeepAlive = !stoppingIntentionally &&
            isSavedEnabled(this) &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this))
        stopCrosshair()
        if (shouldKeepAlive) {
            scheduleRestoreAlarm()
        }
        super.onDestroy()
    }

    /**
     * High-Precision Hardware Canvas view with HD multi-layer vector rendering.
     * Every reticle style is mathematically anchored so its exact aiming point sits on (cx, cy).
     */
    class CrosshairView(context: Context) : View(context) {

        private var style: Int = 0
        private var coreColor: Int = Color.parseColor("#00E5FF")
        private var reticleSizePx: Float = 64f
        private var opacity: Float = 1.0f
        private var customStrokePx: Float = 5f
        private var customGapPx: Float = 8f
        private var showOutline: Boolean = true
        private var showCenterDot: Boolean = true

        private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            style = Paint.Style.FILL
        }

        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(190, 0, 0, 0)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val shadowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(190, 0, 0, 0)
        }

        private val reusablePath = Path()

        fun updateConfig(
            newStyle: Int,
            colorLong: Long,
            newSizePx: Float,
            newAlpha: Float,
            newStrokePx: Float = 5f,
            newGapPx: Float = 8f,
            newShowOutline: Boolean = true,
            newShowCenterDot: Boolean = true
        ) {
            style = newStyle
            coreColor = colorLong.toInt()
            reticleSizePx = newSizePx
            opacity = newAlpha.coerceIn(0.2f, 1.0f)
            customStrokePx = newStrokePx.coerceAtLeast(1.5f)
            customGapPx = newGapPx.coerceAtLeast(0f)
            showOutline = newShowOutline
            showCenterDot = newShowCenterDot
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            // Exact center of the unclipped padded view (which is aligned to exact physical screen center)
            val cx = width / 2f
            val cy = height / 2f
            val r = reticleSizePx / 2f

            val alphaInt = (opacity * 255).toInt().coerceIn(0, 255)
            val strokeW = customStrokePx.coerceIn(1.5f, r * 0.35f)
            val shadowStrokeW = strokeW + 2.8f

            corePaint.strokeWidth = strokeW
            corePaint.color = Color.argb(
                alphaInt,
                Color.red(coreColor),
                Color.green(coreColor),
                Color.blue(coreColor)
            )

            fillPaint.color = corePaint.color
            shadowPaint.strokeWidth = shadowStrokeW
            shadowPaint.color = if (showOutline) Color.argb((alphaInt * 0.78f).toInt(), 0, 0, 0) else Color.TRANSPARENT
            shadowFillPaint.color = shadowPaint.color

            when (style) {
                0 -> drawClassicCross(canvas, cx, cy, r, strokeW)
                1 -> drawPrecisionDot(canvas, cx, cy, r, strokeW)
                2 -> drawCircleDot(canvas, cx, cy, r, strokeW)
                3 -> drawTacticalChevron(canvas, cx, cy, r, strokeW)
                4 -> drawTPoseReticle(canvas, cx, cy, r, strokeW)
                5 -> drawSniperMilDot(canvas, cx, cy, r, strokeW)
                6 -> drawDiamondBox(canvas, cx, cy, r, strokeW)
                7 -> drawCircleCross(canvas, cx, cy, r, strokeW)
                else -> drawClassicCross(canvas, cx, cy, r, strokeW)
            }
        }

        private fun drawAimCenterDot(canvas: Canvas, cx: Float, cy: Float, dotRadius: Float) {
            if (showOutline) {
                canvas.drawCircle(cx, cy, dotRadius + 1.4f, shadowFillPaint)
            }
            canvas.drawCircle(cx, cy, dotRadius, fillPaint)
        }

        // Style 0: Classic Cross (+) with center gap — exact intersection at (cx, cy)
        private fun drawClassicCross(canvas: Canvas, cx: Float, cy: Float, r: Float, sw: Float) {
            val arm = r * 0.88f
            val gap = customGapPx.coerceIn(0f, arm * 0.72f)

            if (showOutline) {
                canvas.drawLine(cx, cy - arm, cx, cy - gap, shadowPaint)
                canvas.drawLine(cx, cy + gap, cx, cy + arm, shadowPaint)
                canvas.drawLine(cx - arm, cy, cx - gap, cy, shadowPaint)
                canvas.drawLine(cx + gap, cy, cx + arm, cy, shadowPaint)
            }

            canvas.drawLine(cx, cy - arm, cx, cy - gap, corePaint)
            canvas.drawLine(cx, cy + gap, cx, cy + arm, corePaint)
            canvas.drawLine(cx - arm, cy, cx - gap, cy, corePaint)
            canvas.drawLine(cx + gap, cy, cx + arm, cy, corePaint)

            if (showCenterDot) {
                drawAimCenterDot(canvas, cx, cy, (sw * 0.55f).coerceAtLeast(1.8f))
            }
        }

        // Style 1: Precision Dot (•) — concentric optical dot centered at (cx, cy)
        private fun drawPrecisionDot(canvas: Canvas, cx: Float, cy: Float, r: Float, sw: Float) {
            val dotRadius = (r * 0.28f + sw * 0.35f).coerceAtLeast(3.5f)
            if (showOutline) {
                canvas.drawCircle(cx, cy, dotRadius + 2.0f, shadowFillPaint)
            }
            canvas.drawCircle(cx, cy, dotRadius, fillPaint)
        }

        // Style 2: Circle + Dot (⊙) — optical ring + 4 cardinal micro-ticks + exact center dot at (cx, cy)
        private fun drawCircleDot(canvas: Canvas, cx: Float, cy: Float, r: Float, sw: Float) {
            val circleR = r * 0.72f
            val tickInner = circleR - sw * 0.9f
            val tickOuter = circleR + sw * 0.9f
            val dotR = (r * 0.18f + sw * 0.25f).coerceAtLeast(2.4f)

            if (showOutline) {
                canvas.drawCircle(cx, cy, circleR, shadowPaint)
                canvas.drawLine(cx, cy - tickOuter, cx, cy - tickInner, shadowPaint)
                canvas.drawLine(cx, cy + tickInner, cx, cy + tickOuter, shadowPaint)
                canvas.drawLine(cx - tickOuter, cy, cx - tickInner, cy, shadowPaint)
                canvas.drawLine(cx + tickInner, cy, cx + tickOuter, cy, shadowPaint)
            }
            canvas.drawCircle(cx, cy, circleR, corePaint)
            canvas.drawLine(cx, cy - tickOuter, cx, cy - tickInner, corePaint)
            canvas.drawLine(cx, cy + tickInner, cx, cy + tickOuter, corePaint)
            canvas.drawLine(cx - tickOuter, cy, cx - tickInner, cy, corePaint)
            canvas.drawLine(cx + tickInner, cy, cx + tickOuter, cy, corePaint)

            if (showCenterDot) {
                drawAimCenterDot(canvas, cx, cy, dotR)
            }
        }

        // Style 3: Tactical Chevron (^) — sharp aiming apex tip anchored at exact center (cx, cy)!
        private fun drawTacticalChevron(canvas: Canvas, cx: Float, cy: Float, r: Float, sw: Float) {
            val wingSpanX = r * 0.62f
            val wingDropY = r * 0.56f
            val bdcStart = (wingDropY * 0.52f).coerceAtLeast(customGapPx * 0.6f + sw)
            val bdcEnd = r * 0.86f

            reusablePath.reset()
            reusablePath.moveTo(cx - wingSpanX, cy + wingDropY)
            // Exact apex tip at (cx, cy) for true ACOG / holographic chevron point-of-aim accuracy
            reusablePath.lineTo(cx, cy)
            reusablePath.lineTo(cx + wingSpanX, cy + wingDropY)

            if (showOutline) {
                canvas.drawPath(reusablePath, shadowPaint)
                canvas.drawLine(cx, cy + bdcStart, cx, cy + bdcEnd, shadowPaint)
            }
            canvas.drawPath(reusablePath, corePaint)
            canvas.drawLine(cx, cy + bdcStart, cx, cy + bdcEnd, corePaint)

            if (showCenterDot) {
                drawAimCenterDot(canvas, cx, cy, (sw * 0.48f).coerceAtLeast(1.6f))
            }
        }

        // Style 4: T-Pose Reticle (⊤) — open upper hemisphere, arms converge on exact (cx, cy)
        private fun drawTPoseReticle(canvas: Canvas, cx: Float, cy: Float, r: Float, sw: Float) {
            val arm = r * 0.86f
            val gap = customGapPx.coerceIn(0f, arm * 0.70f)

            if (showOutline) {
                canvas.drawLine(cx - arm, cy, cx - gap, cy, shadowPaint)
                canvas.drawLine(cx + gap, cy, cx + arm, cy, shadowPaint)
                canvas.drawLine(cx, cy + gap, cx, cy + arm, shadowPaint)
            }
            canvas.drawLine(cx - arm, cy, cx - gap, cy, corePaint)
            canvas.drawLine(cx + gap, cy, cx + arm, cy, corePaint)
            canvas.drawLine(cx, cy + gap, cx, cy + arm, corePaint)

            if (showCenterDot) {
                drawAimCenterDot(canvas, cx, cy, (sw * 0.52f).coerceAtLeast(1.8f))
            }
        }

        // Style 5: Sniper Mil-Dot (⌖) — scope ring, gap-aware crosshairs, outlined mil-dots, center at (cx, cy)
        private fun drawSniperMilDot(canvas: Canvas, cx: Float, cy: Float, r: Float, sw: Float) {
            val outerR = r * 0.78f
            val arm = r * 0.94f
            val gap = customGapPx.coerceIn(0f, outerR * 0.65f)

            if (showOutline) {
                canvas.drawCircle(cx, cy, outerR, shadowPaint)
                canvas.drawLine(cx, cy - arm, cx, cy - gap, shadowPaint)
                canvas.drawLine(cx, cy + gap, cx, cy + arm, shadowPaint)
                canvas.drawLine(cx - arm, cy, cx - gap, cy, shadowPaint)
                canvas.drawLine(cx + gap, cy, cx + arm, cy, shadowPaint)
            }

            canvas.drawCircle(cx, cy, outerR, corePaint)
            canvas.drawLine(cx, cy - arm, cx, cy - gap, corePaint)
            canvas.drawLine(cx, cy + gap, cx, cy + arm, corePaint)
            canvas.drawLine(cx - arm, cy, cx - gap, cy, corePaint)
            canvas.drawLine(cx + gap, cy, cx + arm, cy, corePaint)

            // Symmetrical Mil-Dots with dark contrast halo
            val dotDist = ( gap + (outerR - gap) * 0.5f ).coerceIn(outerR * 0.38f, outerR * 0.68f)
            val milDotR = (sw * 0.58f).coerceAtLeast(1.8f)
            val milPositions = arrayOf(
                floatArrayOf(cx - dotDist, cy),
                floatArrayOf(cx + dotDist, cy),
                floatArrayOf(cx, cy - dotDist),
                floatArrayOf(cx, cy + dotDist)
            )
            for (pt in milPositions) {
                if (showOutline) {
                    canvas.drawCircle(pt[0], pt[1], milDotR + 1.2f, shadowFillPaint)
                }
                canvas.drawCircle(pt[0], pt[1], milDotR, fillPaint)
            }

            if (showCenterDot) {
                drawAimCenterDot(canvas, cx, cy, (sw * 0.55f).coerceAtLeast(1.8f))
            }
        }

        // Style 6: Diamond Box (◇) — symmetrical 45° diamond + cardinal corner ticks centered at (cx, cy)
        private fun drawDiamondBox(canvas: Canvas, cx: Float, cy: Float, r: Float, sw: Float) {
            val d = r * 0.66f
            val tickOuter = (d + sw * 1.4f).coerceAtMost(r * 0.94f)
            reusablePath.reset()
            reusablePath.moveTo(cx, cy - d)
            reusablePath.lineTo(cx + d, cy)
            reusablePath.lineTo(cx, cy + d)
            reusablePath.lineTo(cx - d, cy)
            reusablePath.close()

            if (showOutline) {
                canvas.drawPath(reusablePath, shadowPaint)
                canvas.drawLine(cx, cy - tickOuter, cx, cy - d, shadowPaint)
                canvas.drawLine(cx, cy + d, cx, cy + tickOuter, shadowPaint)
                canvas.drawLine(cx - tickOuter, cy, cx - d, cy, shadowPaint)
                canvas.drawLine(cx + d, cy, cx + tickOuter, cy, shadowPaint)
            }
            canvas.drawPath(reusablePath, corePaint)
            canvas.drawLine(cx, cy - tickOuter, cx, cy - d, corePaint)
            canvas.drawLine(cx, cy + d, cx, cy + tickOuter, corePaint)
            canvas.drawLine(cx - tickOuter, cy, cx - d, cy, corePaint)
            canvas.drawLine(cx + d, cy, cx + tickOuter, cy, corePaint)

            if (showCenterDot) {
                drawAimCenterDot(canvas, cx, cy, (sw * 0.55f).coerceAtLeast(1.8f))
            }
        }

        // Style 7: Circle + Cross (⊕) — optical ring + gap-aware crosshairs + center dot at (cx, cy)
        private fun drawCircleCross(canvas: Canvas, cx: Float, cy: Float, r: Float, sw: Float) {
            val circleR = r * 0.68f
            val arm = r * 0.92f
            val gap = customGapPx.coerceIn(0f, circleR * 0.65f)

            if (showOutline) {
                canvas.drawCircle(cx, cy, circleR, shadowPaint)
                canvas.drawLine(cx, cy - arm, cx, cy - gap, shadowPaint)
                canvas.drawLine(cx, cy + gap, cx, cy + arm, shadowPaint)
                canvas.drawLine(cx - arm, cy, cx - gap, cy, shadowPaint)
                canvas.drawLine(cx + gap, cy, cx + arm, cy, shadowPaint)
            }

            canvas.drawCircle(cx, cy, circleR, corePaint)
            canvas.drawLine(cx, cy - arm, cx, cy - gap, corePaint)
            canvas.drawLine(cx, cy + gap, cx, cy + arm, corePaint)
            canvas.drawLine(cx - arm, cy, cx - gap, cy, corePaint)
            canvas.drawLine(cx + gap, cy, cx + arm, cy, corePaint)

            if (showCenterDot) {
                drawAimCenterDot(canvas, cx, cy, (sw * 0.55f).coerceAtLeast(1.8f))
            }
        }
    }
}
