package com.example.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * High-performance hardware overlay service that draws a centered tactical gaming crosshair.
 *
 * CRITICAL GAMING ARCHITECTURE:
 * - Uses FLAG_NOT_TOUCHABLE so it NEVER intercepts touch events, ensuring 100% full touch
 *   pass-through for high-intensity mobile gaming (PUBG, COD, Free Fire, etc.).
 * - Positioned with Gravity.CENTER for exact screen-center reticle alignment.
 * - Hardware Canvas rendering with dual-pass (contrast outline + neon core) for crisp visibility
 *   on both ultra-bright (desert/snow maps) and pitch-black night environments.
 */
class CrosshairOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var crosshairView: CrosshairView? = null
    private var windowParams: WindowManager.LayoutParams? = null

    companion object {
        const val ACTION_START = "com.example.service.CROSSHAIR_START"
        const val ACTION_STOP = "com.example.service.CROSSHAIR_STOP"
        const val ACTION_UPDATE = "com.example.service.CROSSHAIR_UPDATE"
        const val ACTION_RESTORE = "com.example.service.CROSSHAIR_RESTORE"

        const val EXTRA_STYLE = "EXTRA_STYLE"
        const val EXTRA_COLOR = "EXTRA_COLOR"
        const val EXTRA_SIZE_DP = "EXTRA_SIZE_DP"
        const val EXTRA_ALPHA = "EXTRA_ALPHA"

        private const val PREFS_NAME = "gmax_crosshair_prefs"
        private const val KEY_IS_ENABLED = "crosshair_enabled"
        private const val KEY_STYLE = "crosshair_style"
        private const val KEY_COLOR = "crosshair_color"
        private const val KEY_SIZE_DP = "crosshair_size_dp"
        private const val KEY_ALPHA = "crosshair_alpha"

        private var activeViewInstance: CrosshairView? = null

        var isRunning: Boolean = false
            private set

        fun isSavedEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_IS_ENABLED, false)
        }

        fun getSavedConfig(context: Context): com.example.model.OverlaySettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return com.example.model.OverlaySettings(
                isEnabled = prefs.getBoolean(KEY_IS_ENABLED, false),
                crosshairStyle = prefs.getInt(KEY_STYLE, 0),
                crosshairColor = prefs.getLong(KEY_COLOR, 0xFF00E5FFL),
                crosshairSizeDp = prefs.getInt(KEY_SIZE_DP, 32),
                crosshairAlpha = prefs.getFloat(KEY_ALPHA, 0.95f)
            )
        }

        fun saveConfig(context: Context, enabled: Boolean, style: Int, color: Long, sizeDp: Int, alpha: Float) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_IS_ENABLED, enabled)
                .putInt(KEY_STYLE, style)
                .putLong(KEY_COLOR, color)
                .putInt(KEY_SIZE_DP, sizeDp)
                .putFloat(KEY_ALPHA, alpha)
                .apply()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCrosshair()
            val saved = getSavedConfig(this)
            saveConfig(this, false, saved.crosshairStyle, saved.crosshairColor, saved.crosshairSizeDp, saved.crosshairAlpha)
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_RESTORE) {
            val saved = getSavedConfig(this)
            if (saved.isEnabled && Settings.canDrawOverlays(this)) {
                showCrosshair(saved.crosshairStyle, saved.crosshairColor, saved.crosshairSizeDp, saved.crosshairAlpha)
                return START_STICKY
            } else {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val style = intent?.getIntExtra(EXTRA_STYLE, 0) ?: 0
        val colorLong = intent?.getLongExtra(EXTRA_COLOR, 0xFF00E5FFL) ?: 0xFF00E5FFL
        val sizeDp = intent?.getIntExtra(EXTRA_SIZE_DP, 32) ?: 32
        val alpha = intent?.getFloatExtra(EXTRA_ALPHA, 1.0f) ?: 1.0f

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        saveConfig(this, true, style, colorLong, sizeDp, alpha)

        if (crosshairView == null && activeViewInstance == null) {
            showCrosshair(style, colorLong, sizeDp, alpha)
        } else {
            updateCrosshair(style, colorLong, sizeDp, alpha)
        }

        return START_STICKY
    }

    @SuppressLint("RtlHardcoded")
    private fun showCrosshair(style: Int, colorLong: Long, sizeDp: Int, alpha: Float) {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return

        // Clean up any stale view before creating new one
        stopCrosshair()

        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val newView = CrosshairView(this).apply {
            updateConfig(style, colorLong, sizePx.toFloat(), alpha)
        }
        crosshairView = newView
        activeViewInstance = newView

        val params = WindowManager.LayoutParams(
            sizePx * 2, // Allow ample canvas bounds for tick marks and rings
            sizePx * 2,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            x = 0
            y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        windowParams = params

        try {
            windowManager?.addView(newView, params)
            isRunning = true
        } catch (_: Exception) {
            crosshairView = null
            activeViewInstance = null
            stopSelf()
        }
    }

    private fun updateCrosshair(style: Int, colorLong: Long, sizeDp: Int, alpha: Float) {
        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt()

        (crosshairView ?: activeViewInstance)?.updateConfig(style, colorLong, sizePx.toFloat(), alpha)

        windowParams?.let { params ->
            params.width = sizePx * 2
            params.height = sizePx * 2
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            try {
                windowManager?.updateViewLayout(crosshairView ?: activeViewInstance, params)
            } catch (_: Exception) {}
        }
    }

    private fun stopCrosshair() {
        if (windowManager == null) {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
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

    override fun onDestroy() {
        stopCrosshair()
        super.onDestroy()
    }

    /**
     * Custom Hardware Canvas view with vector rendering for multiple reticle designs.
     */
    class CrosshairView(context: Context) : View(context) {

        private var style: Int = 0
        private var coreColor: Int = Color.parseColor("#00E5FF")
        private var reticleSizePx: Float = 64f
        private var opacity: Float = 1.0f

        private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(160, 0, 0, 0)
            strokeCap = Paint.Cap.ROUND
        }

        private val shadowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(160, 0, 0, 0)
        }

        fun updateConfig(newStyle: Int, colorLong: Long, newSizePx: Float, newAlpha: Float) {
            style = newStyle
            coreColor = colorLong.toInt()
            reticleSizePx = newSizePx
            opacity = newAlpha.coerceIn(0.2f, 1.0f)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val cx = width / 2f
            val cy = height / 2f
            val r = reticleSizePx / 2f

            val alphaInt = (opacity * 255).toInt().coerceIn(0, 255)
            val strokeW = (reticleSizePx * 0.08f).coerceAtLeast(3f)
            val shadowStrokeW = strokeW + 2.5f

            corePaint.strokeWidth = strokeW
            corePaint.color = Color.argb(
                alphaInt,
                Color.red(coreColor),
                Color.green(coreColor),
                Color.blue(coreColor)
            )

            fillPaint.color = corePaint.color
            shadowPaint.strokeWidth = shadowStrokeW
            shadowPaint.color = Color.argb((alphaInt * 0.65f).toInt(), 0, 0, 0)
            shadowFillPaint.color = shadowPaint.color

            when (style) {
                0 -> drawClassicCross(canvas, cx, cy, r, strokeW)
                1 -> drawPrecisionDot(canvas, cx, cy, r)
                2 -> drawCircleDot(canvas, cx, cy, r, strokeW)
                3 -> drawTacticalChevron(canvas, cx, cy, r, strokeW)
                4 -> drawTPoseReticle(canvas, cx, cy, r, strokeW)
                5 -> drawSniperMilDot(canvas, cx, cy, r, strokeW)
                6 -> drawDiamondBox(canvas, cx, cy, r, strokeW)
                7 -> drawCircleCross(canvas, cx, cy, r, strokeW)
                else -> drawClassicCross(canvas, cx, cy, r, strokeW)
            }
        }

        // Style 0: Classic Cross (+) with center gap
        private fun drawClassicCross(canvas: Canvas, cx: Float, cy: Float, r: Float, sw: Float) {
            val arm = r * 0.90f
            val gap = r * 0.22f

            // Shadow / contrast outline
            canvas.drawLine(cx, cy - arm, cx, cy - gap, shadowPaint)
            canvas.drawLine(cx, cy + gap, cx, cy + arm, shadowPaint)
            canvas.drawLine(cx - arm, cy, cx - gap, cy, shadowPaint)
            canvas.drawLine(cx + gap, cy, cx + arm, cy, shadowPaint)

            // Neon Core
            canvas.drawLine(cx, cy - arm, cx, cy - gap, corePaint)
            canvas.drawLine(cx, cy + gap, cx, cy + arm, corePaint)
            canvas.drawLine(cx - arm, cy, cx - gap, cy, corePaint)
            canvas.drawLine(cx + gap, cy, cx + arm, cy, corePaint)

            // Micro Center Dot
            canvas.drawCircle(cx, cy, sw * 0.8f, shadowFillPaint)
            canvas.drawCircle(cx, cy, sw * 0.5f, fillPaint)
        }

        // Style 1: Precision Dot (•)
        private fun drawPrecisionDot(canvas: Canvas, cx: Float, cy: Float, r: Float) {
            val dotRadius = (r * 0.35f).coerceAtLeast(4f)
            canvas.drawCircle(cx, cy, dotRadius + 2f, shadowFillPaint)
            canvas.drawCircle(cx, cy, dotRadius, fillPaint)
        }

        // Style 2: Circle + Dot (⊙)
        private fun drawCircleDot(canvas: Canvas, cx: Float, cy: Float, r: Float, sw: Float) {
            val circleR = r * 0.75f
            val dotR = (r * 0.22f).coerceAtLeast(3f)

            canvas.drawCircle(cx, cy, circleR, shadowPaint)
            canvas.drawCircle(cx, cy, circleR, corePaint)

            canvas.drawCircle(cx, cy, dotR + 1.5f, shadowFillPaint)
            canvas.drawCircle(cx, cy, dotR, fillPaint)
        }

        // Style 3: Tactical Chevron (^)
        private fun drawTacticalChevron(canvas: Canvas, cx: Float, cy: Float, r: Float, sw: Float) {
            val path = Path().apply {
                moveTo(cx - r * 0.65f, cy + r * 0.40f)
                lineTo(cx, cy - r * 0.55f)
                lineTo(cx + r * 0.65f, cy + r * 0.40f)
            }

            canvas.drawPath(path, shadowPaint)
            canvas.drawPath(path, corePaint)

            // Small vertical drop post
            canvas.drawLine(cx, cy + r * 0.15f, cx, cy + r * 0.70f, shadowPaint)
            canvas.drawLine(cx, cy + r * 0.15f, cx, cy + r * 0.70f, corePaint)
        }

        // Style 4: T-Pose Reticle (⊤) (open top for recoil tracking)
        private fun drawTPoseReticle(canvas: Canvas, cx: Float, cy: Float, r: Float, sw: Float) {
            val arm = r * 0.85f
            val gap = r * 0.18f

            // Left
            canvas.drawLine(cx - arm, cy, cx - gap, cy, shadowPaint)
            canvas.drawLine(cx - arm, cy, cx - gap, cy, corePaint)

            // Right
            canvas.drawLine(cx + gap, cy, cx + arm, cy, shadowPaint)
            canvas.drawLine(cx + gap, cy, cx + arm, cy, corePaint)

            // Bottom
            canvas.drawLine(cx, cy + gap, cx, cy + arm, shadowPaint)
            canvas.drawLine(cx, cy + gap, cx, cy + arm, corePaint)

            // Center Pip
            canvas.drawCircle(cx, cy, sw * 0.7f, shadowFillPaint)
            canvas.drawCircle(cx, cy, sw * 0.45f, fillPaint)
        }

        // Style 5: Sniper Mil-Dot (⌖)
        private fun drawSniperMilDot(canvas: Canvas, cx: Float, cy: Float, r: Float, sw: Float) {
            val outerR = r * 0.85f
            val arm = r * 0.95f
            val gap = r * 0.15f

            // Outer Scope Ring
            canvas.drawCircle(cx, cy, outerR, shadowPaint)
            canvas.drawCircle(cx, cy, outerR, corePaint)

            // Cross lines
            canvas.drawLine(cx, cy - arm, cx, cy - gap, shadowPaint)
            canvas.drawLine(cx, cy + gap, cx, cy + arm, shadowPaint)
            canvas.drawLine(cx - arm, cy, cx - gap, cy, shadowPaint)
            canvas.drawLine(cx + gap, cy, cx + arm, cy, shadowPaint)

            canvas.drawLine(cx, cy - arm, cx, cy - gap, corePaint)
            canvas.drawLine(cx, cy + gap, cx, cy + arm, corePaint)
            canvas.drawLine(cx - arm, cy, cx - gap, cy, corePaint)
            canvas.drawLine(cx + gap, cy, cx + arm, cy, corePaint)

            // Mil-dots
            val dotD = outerR * 0.45f
            canvas.drawCircle(cx - dotD, cy, sw * 0.6f, fillPaint)
            canvas.drawCircle(cx + dotD, cy, sw * 0.6f, fillPaint)
            canvas.drawCircle(cx, cy - dotD, sw * 0.6f, fillPaint)
            canvas.drawCircle(cx, cy + dotD, sw * 0.6f, fillPaint)

            canvas.drawCircle(cx, cy, sw * 0.6f, fillPaint)
        }

        // Style 6: Diamond Box (◇)
        private fun drawDiamondBox(canvas: Canvas, cx: Float, cy: Float, r: Float, sw: Float) {
            val d = r * 0.65f
            val path = Path().apply {
                moveTo(cx, cy - d)
                lineTo(cx + d, cy)
                lineTo(cx, cy + d)
                lineTo(cx - d, cy)
                close()
            }

            canvas.drawPath(path, shadowPaint)
            canvas.drawPath(path, corePaint)

            // Micro Center Dot
            canvas.drawCircle(cx, cy, sw * 0.8f, shadowFillPaint)
            canvas.drawCircle(cx, cy, sw * 0.5f, fillPaint)
        }

        // Style 7: Circle + Cross (⊕)
        private fun drawCircleCross(canvas: Canvas, cx: Float, cy: Float, r: Float, sw: Float) {
            val circleR = r * 0.70f
            val arm = r * 0.95f

            canvas.drawCircle(cx, cy, circleR, shadowPaint)
            canvas.drawCircle(cx, cy, circleR, corePaint)

            canvas.drawLine(cx, cy - arm, cx, cy + arm, shadowPaint)
            canvas.drawLine(cx - arm, cy, cx + arm, cy, shadowPaint)

            canvas.drawLine(cx, cy - arm, cx, cy + arm, corePaint)
            canvas.drawLine(cx - arm, cy, cx + arm, cy, corePaint)
        }
    }
}
