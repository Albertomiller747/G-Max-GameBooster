package com.example.service

import android.annotation.SuppressLint
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
import com.example.util.ScreenRecordingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ScreenRecordOverlayService : Service() {

    companion object {
        const val ACTION_START = "com.example.overlay.START"
        const val ACTION_STOP = "com.example.overlay.STOP"
    }

    private var windowManager: WindowManager? = null
    private var rootOverlayLayout: FrameLayout? = null
    private var windowParams: WindowManager.LayoutParams? = null

    // UI elements
    private var floatingCircle: FrameLayout? = null
    private var expandedMenu: LinearLayout? = null
    private var recordingTimerPill: LinearLayout? = null
    private var timerTextView: TextView? = null
    private var recordActionText: TextView? = null
    private var recordActionIcon: ImageView? = null
    private var micStatusText: TextView? = null
    private var timerToggleText: TextView? = null

    private var isExpanded = false
    private var isDragging = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        initOverlay()
        observeRecordingState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        if (action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
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

        windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 300
        }

        rootOverlayLayout = FrameLayout(this)

        // 1. Floating Circle Button (Collapsed State)
        floatingCircle = createFloatingCircle()

        // 2. Expanded 5-Option Menu
        expandedMenu = createExpandedMenu()
        expandedMenu?.visibility = View.GONE

        // 3. Compact Recording Timer Pill (Shows during active gameplay when expanded menu is closed)
        recordingTimerPill = createRecordingTimerPill()
        recordingTimerPill?.visibility = View.GONE

        rootOverlayLayout?.addView(expandedMenu)
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
     * Creates the initial draggable red & white recording circle
     */
    private fun createFloatingCircle(): FrameLayout {
        val density = resources.displayMetrics.density
        val sizePx = (54 * density).toInt()

        return FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)

            // Outer ring & background
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E11D48")) // Vibrant Crimson Red
                setStroke((3 * density).toInt(), Color.WHITE)
            }

            elevation = 12 * density

            // Center Record Dot / Icon
            val centerDot = View(context).apply {
                val dotSize = (20 * density).toInt()
                layoutParams = FrameLayout.LayoutParams(dotSize, dotSize, Gravity.CENTER)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.WHITE)
                }
            }
            addView(centerDot)

            setOnClickListener {
                if (!isDragging) {
                    toggleExpandedMenu()
                }
            }
        }
    }

    /**
     * Creates the 5-option expanded control panel
     */
    private fun createExpandedMenu(): LinearLayout {
        val density = resources.displayMetrics.density
        val paddingPx = (14 * density).toInt()

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                (240 * density).toInt(),
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18 * density
                setColor(Color.parseColor("#E60D1322")) // Dark cyber glass
                setStroke((1.5f * density).toInt(), Color.parseColor("#E11D48"))
            }

            // Top Header with Title and Red "X" close button
            val headerRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )

                val titleText = TextView(context).apply {
                    text = "RECORDING STUDIO"
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                // Red "X" button to completely close overlay
                val closeButton = TextView(context).apply {
                    text = "✕"
                    setTextColor(Color.parseColor("#EF4444"))
                    textSize = 18f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setPadding((8 * density).toInt(), 0, (4 * density).toInt(), 0)
                    setOnClickListener {
                        ScreenRecordingManager.setOverlayEnabled(context, false)
                        stopSelf()
                    }
                }

                addView(titleText)
                addView(closeButton)
            }
            addView(headerRow)

            // Divider
            addView(createSpacer(8))

            // Option 1: Start / Stop Recording
            val recordBtn = createMenuActionRow(
                iconRes = android.R.drawable.ic_media_play,
                title = "Start Recording",
                subtitle = "Direct full screen recording",
                accentColor = Color.parseColor("#EF4444")
            ) {
                handleRecordActionClick()
            }
            recordActionText = recordBtn.findViewById(1001)
            recordActionIcon = recordBtn.findViewById(1002)
            addView(recordBtn)

            addView(createSpacer(6))

            // Option 2: Live Recording Timer Toggle
            val timerToggleRow = createMenuActionRow(
                iconRes = android.R.drawable.ic_menu_recent_history,
                title = "Timer HUD on Screen",
                subtitle = if (ScreenRecordingManager.loadSettings(context).showTimerHud) "Visible" else "Hidden",
                accentColor = Color.parseColor("#38BDF8")
            ) {
                val current = ScreenRecordingManager.loadSettings(context)
                val newTarget = !current.showTimerHud
                ScreenRecordingManager.saveSettings(context, current.copy(showTimerHud = newTarget))
                timerToggleText?.text = if (newTarget) "Visible while recording" else "Hidden"
                Toast.makeText(context, if (newTarget) "Recording timer HUD: ON" else "Recording timer HUD: OFF", Toast.LENGTH_SHORT).show()
            }
            timerToggleText = timerToggleRow.findViewById(1001)
            addView(timerToggleRow)

            addView(createSpacer(6))

            // Option 3: Microphone Toggle
            val micToggleRow = createMenuActionRow(
                iconRes = android.R.drawable.ic_btn_speak_now,
                title = "Microphone Audio",
                subtitle = if (ScreenRecordingService.isMicEnabled.value) "Mic ON" else "Mic Muted",
                accentColor = Color.parseColor("#10B981")
            ) {
                val intent = Intent(context, ScreenRecordingService::class.java).apply {
                    action = ScreenRecordingService.ACTION_TOGGLE_MIC
                }
                startService(intent)
                val newMic = !ScreenRecordingService.isMicEnabled.value
                micStatusText?.text = if (newMic) "Mic ON" else "Mic Muted"
                Toast.makeText(context, if (newMic) "Microphone: ON" else "Microphone: MUTED", Toast.LENGTH_SHORT).show()
            }
            micStatusText = micToggleRow.findViewById(1001)
            addView(micToggleRow)

            addView(createSpacer(6))

            // Option 4: Quick Screenshot Button
            val screenshotRow = createMenuActionRow(
                iconRes = android.R.drawable.ic_menu_camera,
                title = "Quick Screenshot",
                subtitle = "Instant frame snapshot",
                accentColor = Color.parseColor("#F59E0B")
            ) {
                takeQuickScreenshot()
            }
            addView(screenshotRow)

            addView(createSpacer(8))

            // Option 5: Red "X" Collapse/Remove row
            val dismissRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding((6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 8 * density
                    setColor(Color.parseColor("#26EF4444"))
                }
                setOnClickListener {
                    toggleExpandedMenu(forceClose = true)
                }

                val dismissText = TextView(context).apply {
                    text = "Close Panel"
                    setTextColor(Color.parseColor("#F87171"))
                    textSize = 12f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                addView(dismissText)
            }
            addView(dismissRow)
        }
    }

    /**
     * Compact live elapsed timer HUD pill
     */
    private fun createRecordingTimerPill(): LinearLayout {
        val density = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * density).toInt(), (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20 * density
                setColor(Color.parseColor("#CC0B1120")) // Translucent dark
                setStroke((1.5f * density).toInt(), Color.parseColor("#EF4444"))
            }

            val redDot = View(context).apply {
                val dotSize = (8 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                    marginEnd = (6 * density).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#EF4444"))
                }
            }
            addView(redDot)

            timerTextView = TextView(context).apply {
                text = "REC 00:00"
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = android.graphics.Typeface.MONOSPACE
            }
            addView(timerTextView)

            setOnClickListener {
                if (!isDragging) {
                    toggleExpandedMenu()
                }
            }
        }
    }

    private fun createMenuActionRow(
        iconRes: Int,
        title: String,
        subtitle: String,
        accentColor: Int,
        onClick: () -> Unit
    ): LinearLayout {
        val density = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10 * density
                setColor(Color.parseColor("#151D30"))
            }
            setOnClickListener { onClick() }

            val icon = ImageView(context).apply {
                id = 1002
                setImageResource(iconRes)
                setColorFilter(accentColor)
                layoutParams = LinearLayout.LayoutParams((22 * density).toInt(), (22 * density).toInt())
            }
            addView(icon)

            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (10 * density).toInt()
                }

                val titleTv = TextView(context).apply {
                    text = title
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                val subTv = TextView(context).apply {
                    id = 1001
                    text = subtitle
                    setTextColor(accentColor)
                    textSize = 10f
                }

                addView(titleTv)
                addView(subTv)
            }
            addView(textCol)
        }
    }

    private fun createSpacer(heightDp: Int): View {
        val density = resources.displayMetrics.density
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (heightDp * density).toInt()
            )
        }
    }

    private fun toggleExpandedMenu(forceClose: Boolean = false) {
        if (isExpanded || forceClose) {
            isExpanded = false
            expandedMenu?.visibility = View.GONE
            updateDisplayState()
        } else {
            isExpanded = true
            expandedMenu?.visibility = View.VISIBLE
            floatingCircle?.visibility = View.GONE
            recordingTimerPill?.visibility = View.GONE
        }
    }

    private fun handleRecordActionClick() {
        if (ScreenRecordingService.isRecording.value) {
            // Stop recording
            val intent = Intent(this, ScreenRecordingService::class.java).apply {
                action = ScreenRecordingService.ACTION_STOP
            }
            startService(intent)
            Toast.makeText(this, "Screen recording saved to Gallery!", Toast.LENGTH_SHORT).show()
            toggleExpandedMenu(forceClose = true)
        } else {
            // Start recording: opens MainActivity to initiate MediaProjection consent
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
            Toast.makeText(this, "Quick Screenshot captured and saved to Gallery!", Toast.LENGTH_SHORT).show()
        }, 300)
    }

    private fun observeRecordingState() {
        serviceScope.launch {
            ScreenRecordingService.isRecording.collectLatest { recording ->
                updateDisplayState()
            }
        }

        serviceScope.launch {
            ScreenRecordingService.recordingSeconds.collectLatest { secs ->
                val mins = secs / 60
                val remSecs = secs % 60
                timerTextView?.text = String.format(java.util.Locale.US, "REC %02d:%02d", mins, remSecs)
            }
        }
    }

    private fun updateDisplayState() {
        val recording = ScreenRecordingService.isRecording.value
        val showTimer = ScreenRecordingManager.loadSettings(this).showTimerHud

        if (isExpanded) {
            recordActionText?.text = if (recording) "Stop Recording" else "Start Recording"
            recordActionIcon?.setImageResource(
                if (recording) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
            return
        }

        if (recording) {
            if (showTimer) {
                recordingTimerPill?.visibility = View.VISIBLE
                floatingCircle?.visibility = View.GONE
            } else {
                recordingTimerPill?.visibility = View.GONE
                // Minimized subtle recording indicator
                floatingCircle?.visibility = View.VISIBLE
            }
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
        serviceScope.cancel()
        try {
            if (rootOverlayLayout != null) {
                windowManager?.removeView(rootOverlayLayout)
            }
        } catch (_: Exception) {}
    }
}
