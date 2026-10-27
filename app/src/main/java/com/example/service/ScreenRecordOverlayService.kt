package com.example.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.MainActivity
import com.example.R
import com.example.util.ScreenRecordingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ScreenRecordOverlayService : Service() {

    companion object {
        const val ACTION_START = "com.example.overlay.START"
        const val ACTION_STOP = "com.example.overlay.STOP"
        const val ACTION_UPDATE_SIZE = "com.example.overlay.UPDATE_SIZE"
        private const val READY_NOTIFICATION_ID = 4202
        private const val CHANNEL_ID = "screen_recording_channel"
    }

    private var windowManager: WindowManager? = null
    private var rootOverlayLayout: FrameLayout? = null
    private var windowParams: WindowManager.LayoutParams? = null

    // UI elements
    private var floatingCircle: FrameLayout? = null
    private var floatingCamIcon: ImageView? = null
    private var floatingMiniDot: View? = null
    private var radialFanContainer: FrameLayout? = null
    private var recordingTimerPill: LinearLayout? = null
    private var timerTextView: TextView? = null
    private var stopLabelView: TextView? = null
    private var recordSatelliteIcon: ImageView? = null

    private var isExpanded = false
    private var isDragging = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        ensureNotificationChannel()
        postReadyNotification()
        initOverlay()
        observeRecordingState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        if (action == ACTION_STOP) {
            cancelReadyNotification()
            stopSelf()
            return START_NOT_STICKY
        }
        if (action == ACTION_UPDATE_SIZE) {
            applyBubbleSizeFromSettings()
            updateDisplayState()
            return START_NOT_STICKY
        }
        applyBubbleSizeFromSettings()
        updateDisplayState()
        postReadyNotification()
        return START_NOT_STICKY
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Gameplay Screen Recorder",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Quick recorder controls and active recording indicator"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun postReadyNotification() {
        try {
            if (!ScreenRecordingService.isRecording.value) {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val notif = ScreenRecordingService.buildRecorderNotification(
                    context = this,
                    isRecordingActive = false,
                    elapsedSeconds = 0
                )
                nm.notify(READY_NOTIFICATION_ID, notif)
            }
        } catch (_: Exception) {}
    }

    private fun cancelReadyNotification() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(READY_NOTIFICATION_ID)
        } catch (_: Exception) {}
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initOverlay() {
        if (!Settings.canDrawOverlays(this)) return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val screenWidth = resources.displayMetrics.widthPixels
        val density = resources.displayMetrics.density

        windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - (64 * density)).toInt().coerceAtLeast(16)
            y = (260 * density).toInt()
        }

        rootOverlayLayout = FrameLayout(this)

        // 1. Docked Coral-Red Camera Bubble (Collapsed State)
        floatingCircle = createFloatingCircle()

        // 2. Curved Radial Fan Container (Matches reference image #1)
        radialFanContainer = createRadialFanMenu()
        radialFanContainer?.visibility = View.GONE

        // 3. Compact Recording Timer Pill (Shows during active gameplay when enabled)
        recordingTimerPill = createRecordingTimerPill()
        recordingTimerPill?.visibility = View.GONE

        rootOverlayLayout?.addView(radialFanContainer)
        rootOverlayLayout?.addView(recordingTimerPill)
        rootOverlayLayout?.addView(floatingCircle)

        setupDragListener(floatingCircle!!)
        setupDragListener(recordingTimerPill!!)

        try {
            windowManager?.addView(rootOverlayLayout, windowParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Creates the coral-red circular camera bubble matching the reference screenshot
     */
    private fun createFloatingCircle(): FrameLayout {
        val density = resources.displayMetrics.density
        val bubbleDp = ScreenRecordingManager.loadSettings(this).bubbleSizeDp.coerceIn(28, 64)
        val sizePx = (bubbleDp * density).toInt()

        return FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx, Gravity.CENTER_VERTICAL or Gravity.END)

            // Pure circular drawable with zero elevation to eliminate any gray hardware shadow box
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1D4ED8")) // Cyber Sapphire Blue
                setStroke((2f * density).toInt().coerceAtLeast(1), Color.parseColor("#38BDF8")) // Glowing Cyan accent
            }
            elevation = 0f

            val camIcon = ImageView(context).apply {
                val iconSize = (sizePx * 0.50f).toInt()
                layoutParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
                setImageResource(R.drawable.ic_notif_record)
                setColorFilter(Color.WHITE)
            }
            floatingCamIcon = camIcon
            addView(camIcon)

            // Small glowing indicator dot at top-left of camera icon
            val miniDot = View(context).apply {
                val dotSize = (sizePx * 0.13f).toInt().coerceAtLeast((4 * density).toInt())
                val inset = (sizePx * 0.20f).toInt()
                layoutParams = FrameLayout.LayoutParams(dotSize, dotSize, Gravity.TOP or Gravity.START).apply {
                    topMargin = inset
                    marginStart = inset
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#38BDF8")) // Glowing cyan dot
                }
            }
            floatingMiniDot = miniDot
            addView(miniDot)

            setOnClickListener {
                if (!isDragging) {
                    toggleExpandedMenu()
                }
            }
        }
    }

    private fun applyBubbleSizeFromSettings() {
        val circle = floatingCircle ?: return
        val density = resources.displayMetrics.density
        val bubbleDp = ScreenRecordingManager.loadSettings(this).bubbleSizeDp.coerceIn(28, 64)
        val sizePx = (bubbleDp * density).toInt()

        circle.layoutParams = (circle.layoutParams as? FrameLayout.LayoutParams)?.apply {
            width = sizePx
            height = sizePx
        } ?: FrameLayout.LayoutParams(sizePx, sizePx, Gravity.CENTER_VERTICAL or Gravity.END)

        floatingCamIcon?.let { icon ->
            val iconSize = (sizePx * 0.50f).toInt()
            icon.layoutParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
        }
        floatingMiniDot?.let { dot ->
            val dotSize = (sizePx * 0.13f).toInt().coerceAtLeast((4 * density).toInt())
            val inset = (sizePx * 0.20f).toInt()
            dot.layoutParams = FrameLayout.LayoutParams(dotSize, dotSize, Gravity.TOP or Gravity.START).apply {
                topMargin = inset
                marginStart = inset
            }
        }
        circle.requestLayout()
        try {
            if (rootOverlayLayout != null && windowParams != null) {
                windowManager?.updateViewLayout(rootOverlayLayout, windowParams)
            }
        } catch (_: Exception) {}
    }

    /**
     * Creates the curved radial fan menu matching screenshot #1:
     * Central coral camera bubble on the right with 4 white circular buttons arranged in a curved arc:
     * 1. Top: Record / Stop (Coral camera/stop icon)
     * 2. Upper-Middle: Videos Gallery (4-square coral/amber grid icon)
     * 3. Lower-Middle: Screenshot Capture (Coral screenshot icon - replacing utility wrench)
     * 4. Bottom: Exit / Close Overlay (Coral X icon)
     */
    private fun createRadialFanMenu(): FrameLayout {
        val density = resources.displayMetrics.density
        val containerWidth = (176 * density).toInt()
        val containerHeight = (264 * density).toInt()

        return FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(containerWidth, containerHeight)

            // Dismiss scrim tap area
            setOnClickListener {
                toggleExpandedMenu(forceClose = true)
            }

            // Central anchor Cyber Sapphire button on the right side of the arc
            val centerHubSize = (54 * density).toInt()
            val centerHub = FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(centerHubSize, centerHubSize).apply {
                    gravity = Gravity.TOP or Gravity.START
                    leftMargin = (114 * density).toInt()
                    topMargin = (105 * density).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#1E3A8A")) // Dark Sapphire
                    setStroke((2f * density).toInt(), Color.parseColor("#38BDF8")) // Cyan Glow
                }
                elevation = 0f

                val hubIcon = ImageView(context).apply {
                    val s = (26 * density).toInt()
                    layoutParams = FrameLayout.LayoutParams(s, s, Gravity.CENTER)
                    setImageResource(R.drawable.ic_notif_record)
                    setColorFilter(Color.parseColor("#38BDF8"))
                }
                addView(hubIcon)

                setOnClickListener {
                    toggleExpandedMenu(forceClose = true)
                }
            }
            addView(centerHub)

            // Satellite 1 (Top of arc): Record / Stop
            val isRec = ScreenRecordingService.isRecording.value
            val sat1 = createRadialSatelliteButton(
                leftDp = 68,
                topDp = 12,
                iconRes = if (isRec) R.drawable.ic_notif_stop else R.drawable.ic_notif_record,
                iconTint = if (isRec) Color.parseColor("#EF4444") else Color.parseColor("#2563EB"),
                isRecordBtn = true
            ) {
                handleRecordActionClick()
            }
            addView(sat1)

            // Satellite 2 (Upper-middle of arc): Videos Gallery (4-grid icon)
            val sat2 = createRadialSatelliteButton(
                leftDp = 18,
                topDp = 68,
                iconRes = R.drawable.ic_notif_videos,
                iconTint = null
            ) {
                toggleExpandedMenu(forceClose = true)
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("extra_open_tab", "RECORD")
                    putExtra("extra_recorder_subtab", 0)
                }
                startActivity(intent)
            }
            addView(sat2)

            // Satellite 3 (Lower-middle of arc): Instant Screenshot Capture
            val sat3 = createRadialSatelliteButton(
                leftDp = 18,
                topDp = 138,
                iconRes = R.drawable.ic_notif_screenshots,
                iconTint = null
            ) {
                takeQuickScreenshot()
            }
            addView(sat3)

            // Satellite 4 (Bottom of arc): Exit Overlay
            val sat4 = createRadialSatelliteButton(
                leftDp = 68,
                topDp = 194,
                iconRes = R.drawable.ic_notif_exit,
                iconTint = null
            ) {
                toggleExpandedMenu(forceClose = true)
                ScreenRecordingManager.setOverlayEnabled(context, false)
                cancelReadyNotification()
                stopSelf()
            }
            addView(sat4)
        }
    }

    private fun createRadialSatelliteButton(
        leftDp: Int,
        topDp: Int,
        iconRes: Int,
        iconTint: Int?,
        isRecordBtn: Boolean = false,
        onClick: () -> Unit
    ): FrameLayout {
        val density = resources.displayMetrics.density
        val sizePx = (52 * density).toInt()

        return FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = (leftDp * density).toInt()
                topMargin = (topDp * density).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke((1.5f * density).toInt(), Color.parseColor("#38BDF8")) // Glowing Cyan Border
            }
            elevation = 0f

            val icon = ImageView(context).apply {
                val iconPx = (26 * density).toInt()
                layoutParams = FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER)
                setImageResource(iconRes)
                if (iconTint != null) {
                    setColorFilter(iconTint)
                } else {
                    setColorFilter(Color.parseColor("#2563EB")) // Sapphire Blue default tint
                }
            }
            if (isRecordBtn) {
                recordSatelliteIcon = icon
            }
            addView(icon)

            setOnClickListener { onClick() }
        }
    }

    /**
     * Compact live elapsed timer & 1-tap Stop HUD pill so the user can always stop recording in-game.
     */
    private fun createRecordingTimerPill(): LinearLayout {
        val density = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((10 * density).toInt(), (5 * density).toInt(), (10 * density).toInt(), (5 * density).toInt())

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18 * density
                setColor(Color.parseColor("#CC0F172A")) // Translucent cyber dark
                setStroke((1.5f * density).toInt(), Color.parseColor("#EF4444")) // Red stop accent border
            }
            elevation = 0f

            // Stop square icon inside the pill
            val stopSquare = View(context).apply {
                val sqSize = (9 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(sqSize, sqSize).apply {
                    marginEnd = (6 * density).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 2 * density
                    setColor(Color.parseColor("#EF4444"))
                }
            }
            addView(stopSquare)

            timerTextView = TextView(context).apply {
                text = "00:00"
                setTextColor(Color.WHITE)
                textSize = 11f
                typeface = android.graphics.Typeface.MONOSPACE
                setPadding(0, 0, (6 * density).toInt(), 0)
            }
            addView(timerTextView)

            stopLabelView = TextView(context).apply {
                text = "STOP"
                setTextColor(Color.parseColor("#FCA5A5"))
                textSize = 10f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            addView(stopLabelView)

            setOnClickListener {
                if (!isDragging) {
                    // Direct 1-tap stop when recording is active so user never gets stuck in-game
                    handleRecordActionClick()
                }
            }
        }
    }

    private fun toggleExpandedMenu(forceClose: Boolean = false) {
        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels

        if (isExpanded || forceClose) {
            isExpanded = false
            radialFanContainer?.visibility = View.GONE
            // Snap back near right edge if it was shifted for radial fan
            windowParams?.x = (screenWidth - (60 * density)).toInt().coerceAtLeast(16)
            try {
                windowManager?.updateViewLayout(rootOverlayLayout, windowParams)
            } catch (_: Exception) {}
            updateDisplayState()
        } else {
            isExpanded = true
            val recording = ScreenRecordingService.isRecording.value
            recordSatelliteIcon?.setImageResource(
                if (recording) R.drawable.ic_notif_stop else R.drawable.ic_notif_record
            )
            // Shift left enough so the radial arc fits nicely on screen
            windowParams?.x = (screenWidth - (180 * density)).toInt().coerceAtLeast(8)
            radialFanContainer?.visibility = View.VISIBLE
            floatingCircle?.visibility = View.GONE
            recordingTimerPill?.visibility = View.GONE
            try {
                windowManager?.updateViewLayout(rootOverlayLayout, windowParams)
            } catch (_: Exception) {}
        }
    }

    private fun handleRecordActionClick() {
        if (ScreenRecordingService.isRecording.value) {
            val intent = Intent(this, ScreenRecordingService::class.java).apply {
                action = ScreenRecordingService.ACTION_STOP
            }
            startService(intent)
            Toast.makeText(this, "Recording saved to Videos!", Toast.LENGTH_SHORT).show()
            toggleExpandedMenu(forceClose = true)
            mainHandler.postDelayed({ postReadyNotification() }, 500)
        } else {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("extra_trigger_recording", true)
            }
            startActivity(intent)
            toggleExpandedMenu(forceClose = true)
        }
    }

    private fun takeQuickScreenshot() {
        toggleExpandedMenu(forceClose = true)
        mainHandler.postDelayed({
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("extra_trigger_screenshot", true)
            }
            startActivity(intent)
        }, 220)
    }

    private fun observeRecordingState() {
        serviceScope.launch {
            ScreenRecordingService.isRecording.collectLatest { recording ->
                if (!recording) {
                    postReadyNotification()
                }
                updateDisplayState()
            }
        }

        serviceScope.launch {
            ScreenRecordingService.recordingSeconds.collectLatest { secs ->
                val mins = secs / 60
                val remSecs = secs % 60
                timerTextView?.text = String.format(java.util.Locale.US, "%02d:%02d", mins, remSecs)
            }
        }
    }

    private fun updateDisplayState() {
        val recording = ScreenRecordingService.isRecording.value
        val showTimer = ScreenRecordingManager.loadSettings(this).showTimerHud

        if (isExpanded) {
            recordSatelliteIcon?.setImageResource(
                if (recording) R.drawable.ic_notif_stop else R.drawable.ic_notif_record
            )
            return
        }

        if (recording) {
            // Always show the compact stop pill during active recording so the user can stop in-game;
            // only show the elapsed timer digits if showTimerHud is enabled.
            recordingTimerPill?.visibility = View.VISIBLE
            timerTextView?.visibility = if (showTimer) View.VISIBLE else View.GONE
            floatingCircle?.visibility = View.GONE
        } else {
            recordingTimerPill?.visibility = View.GONE
            floatingCircle?.visibility = View.VISIBLE
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragListener(view: View) {
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = windowParams?.x ?: 0
                    initialY = windowParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                        isDragging = true
                        windowParams?.x = initialX + deltaX
                        windowParams?.y = initialY + deltaY
                        try {
                            windowManager?.updateViewLayout(rootOverlayLayout, windowParams)
                        } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelReadyNotification()
        serviceScope.cancel()
        try {
            if (rootOverlayLayout != null) {
                windowManager?.removeView(rootOverlayLayout)
            }
        } catch (_: Exception) {}
    }
}
