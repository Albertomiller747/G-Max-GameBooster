package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.ClipData
import android.content.ClipDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.min
import kotlin.math.roundToInt

data class NotificationTickerConfig(
    val isEnabled: Boolean = true,
    val countdownSeconds: Int = 6,
    val autoCancelSystemPopups: Boolean = true
)

data class InterceptedTickerEvent(
    val id: String = UUID.randomUUID().toString().take(8),
    val notificationKey: String? = null,
    val packageName: String = "",
    val appLabel: String = "Message",
    val senderOrTitle: String = "",
    val messageBody: String = "",
    val isCall: Boolean = false,
    val countdownSeconds: Int = 6,
    val replyAction: Notification.Action? = null,
    val remoteInputs: Array<RemoteInput>? = null,
    val answerIntent: PendingIntent? = null,
    val declineIntent: PendingIntent? = null,
    val contentIntent: PendingIntent? = null,
    val isDemo: Boolean = false
) {
    val hasQuickReplyCapability: Boolean
        get() = !isCall && ((replyAction != null && !remoteInputs.isNullOrEmpty()) || isDemo)
}

/**
 * Top-Edge Floating Notification Ticker & Silent Pre-Canned Quick-Reply Overlay Service.
 *
 * - For Calls: Shows scrolling caller line + compact Answer and Decline buttons + countdown.
 *   When answered or declined, immediately marks the call handled via [GMaxNotificationTickerListenerService]
 *   so ongoing call timer notifications never re-trigger the popup.
 * - For Messages: Shows scrolling sender/message line + compact [Reply ▾] button + countdown.
 *   Tapping [Reply ▾] expands 5 pre-canned buttons ("In a ranked match", "Need 5 mins",
 *   "Yes", "No", "Coming") that fire Android's RemoteInput PendingIntent silently in the
 *   background (preserving the notification token until after the reply is sent) without ever
 *   opening the soft keyboard.
 */
class NotificationTickerOverlayService : Service() {

    companion object {
        const val ACTION_SHOW_TICKER = "com.example.ticker.ACTION_SHOW_TICKER"
        const val ACTION_DISMISS = "com.example.ticker.ACTION_DISMISS"

        private const val PREFS_NAME = "gmax_notification_ticker_prefs"
        private const val KEY_TICKER_ENABLED = "ticker_overlay_enabled"
        private const val KEY_COUNTDOWN_SECONDS = "ticker_countdown_seconds"
        private const val KEY_AUTO_CANCEL_SYSTEM = "ticker_auto_cancel_system_popups"

        const val DEFAULT_COUNTDOWN_SECONDS = 6
        const val MIN_COUNTDOWN_SECONDS = 3
        const val MAX_COUNTDOWN_SECONDS = 15

        val QUICK_REPLIES = listOf(
            "In a ranked match",
            "Need 5 mins",
            "Yes",
            "No",
            "Coming"
        )

        private val _config = MutableStateFlow(NotificationTickerConfig())
        val config: StateFlow<NotificationTickerConfig> = _config.asStateFlow()

        private val _latestEvent = MutableStateFlow<InterceptedTickerEvent?>(null)
        val latestEvent: StateFlow<InterceptedTickerEvent?> = _latestEvent.asStateFlow()

        private val _lastSilentReplySent = MutableStateFlow<String?>(null)
        val lastSilentReplySent: StateFlow<String?> = _lastSilentReplySent.asStateFlow()

        fun loadConfig(context: Context): NotificationTickerConfig {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean(KEY_TICKER_ENABLED, true)
            val secs = prefs.getInt(KEY_COUNTDOWN_SECONDS, DEFAULT_COUNTDOWN_SECONDS)
                .coerceIn(MIN_COUNTDOWN_SECONDS, MAX_COUNTDOWN_SECONDS)
            val autoCancel = prefs.getBoolean(KEY_AUTO_CANCEL_SYSTEM, true)
            val loaded = NotificationTickerConfig(
                isEnabled = enabled,
                countdownSeconds = secs,
                autoCancelSystemPopups = autoCancel
            )
            _config.value = loaded
            return loaded
        }

        fun setEnabled(context: Context, enabled: Boolean): NotificationTickerConfig {
            val current = loadConfig(context)
            val updated = current.copy(isEnabled = enabled)
            saveConfig(context, updated)
            if (!enabled) {
                dismissActiveTicker(context)
            }
            return updated
        }

        fun setCountdownSeconds(context: Context, seconds: Int): NotificationTickerConfig {
            val clamped = seconds.coerceIn(MIN_COUNTDOWN_SECONDS, MAX_COUNTDOWN_SECONDS)
            val current = loadConfig(context)
            val updated = current.copy(countdownSeconds = clamped)
            saveConfig(context, updated)
            return updated
        }

        fun setAutoCancelSystemPopups(context: Context, autoCancel: Boolean): NotificationTickerConfig {
            val current = loadConfig(context)
            val updated = current.copy(autoCancelSystemPopups = autoCancel)
            saveConfig(context, updated)
            return updated
        }

        private fun saveConfig(context: Context, updated: NotificationTickerConfig) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_TICKER_ENABLED, updated.isEnabled)
                .putInt(KEY_COUNTDOWN_SECONDS, updated.countdownSeconds.coerceIn(MIN_COUNTDOWN_SECONDS, MAX_COUNTDOWN_SECONDS))
                .putBoolean(KEY_AUTO_CANCEL_SYSTEM, updated.autoCancelSystemPopups)
                .apply()
            _config.value = updated
        }

        fun isNotificationListenerGranted(context: Context): Boolean {
            return try {
                val flat = Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners"
                ).orEmpty()
                val cn = ComponentName(context, GMaxNotificationTickerListenerService::class.java)
                flat.contains(cn.flattenToString()) || flat.contains(cn.flattenToShortString())
            } catch (_: Exception) {
                false
            }
        }

        fun openNotificationListenerSettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallback)
                } catch (_: Exception) {}
            }
        }

        fun dispatchInterceptedEvent(context: Context, event: InterceptedTickerEvent) {
            val cfg = loadConfig(context)
            if (!cfg.isEnabled) return
            val effectiveEvent = event.copy(
                countdownSeconds = cfg.countdownSeconds.coerceIn(MIN_COUNTDOWN_SECONDS, MAX_COUNTDOWN_SECONDS)
            )
            _latestEvent.value = effectiveEvent
            if (!Settings.canDrawOverlays(context)) return
            try {
                val intent = Intent(context, NotificationTickerOverlayService::class.java).apply {
                    action = ACTION_SHOW_TICKER
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }

        fun triggerDemoTicker(context: Context, isCall: Boolean) {
            val cfg = loadConfig(context)
            val demoEvent = if (isCall) {
                InterceptedTickerEvent(
                    packageName = "com.whatsapp",
                    appLabel = "WhatsApp Call",
                    senderOrTitle = "Squad Leader Alex",
                    messageBody = "Incoming Voice Call • Ranked Trio Lobby Ready",
                    isCall = true,
                    countdownSeconds = cfg.countdownSeconds,
                    isDemo = true
                )
            } else {
                InterceptedTickerEvent(
                    packageName = "com.whatsapp",
                    appLabel = "WhatsApp",
                    senderOrTitle = "Squad Leader Alex",
                    messageBody = "Yo we need a 5th for Ranked right now! Are you hopping on?",
                    isCall = false,
                    countdownSeconds = cfg.countdownSeconds,
                    isDemo = true
                )
            }
            _latestEvent.value = demoEvent
            if (!Settings.canDrawOverlays(context)) return
            try {
                val intent = Intent(context, NotificationTickerOverlayService::class.java).apply {
                    action = ACTION_SHOW_TICKER
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }

        fun dismissIfMatchingKey(context: Context, notificationKey: String) {
            val current = _latestEvent.value ?: return
            if (current.notificationKey == notificationKey) {
                dismissActiveTicker(context)
            }
        }

        fun dismissActiveTicker(context: Context) {
            _latestEvent.value = null
            try {
                val intent = Intent(context, NotificationTickerOverlayService::class.java).apply {
                    action = ACTION_DISMISS
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }

        /**
         * Fires the notification's RemoteInput PendingIntent silently in the background with [replyText]
         * so the Android soft keyboard never pops up on screen.
         * Preserves the notification token until AFTER the PendingIntent is dispatched.
         */
        fun sendSilentRemoteInputReply(
            context: Context,
            event: InterceptedTickerEvent,
            replyText: String
        ): Boolean {
            if (event.isDemo) {
                _lastSilentReplySent.value = replyText
                return true
            }
            val action = event.replyAction ?: return false
            val remoteInputs = event.remoteInputs ?: action.remoteInputs ?: return false
            if (remoteInputs.isEmpty()) return false
            val pendingIntent = action.actionIntent ?: return false

            return try {
                val fillInIntent = Intent().apply {
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                }
                val resultsBundle = Bundle()
                for (remoteInput in remoteInputs) {
                    resultsBundle.putCharSequence(remoteInput.resultKey, replyText)
                }
                RemoteInput.addResultsToIntent(remoteInputs, fillInIntent, resultsBundle)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    RemoteInput.setResultsSource(fillInIntent, RemoteInput.SOURCE_FREE_FORM_INPUT)
                }
                // Ensure ClipData carries the RemoteInput bundle for apps that inspect ClipData directly
                if (fillInIntent.clipData == null) {
                    val clipIntent = Intent()
                    clipIntent.putExtra(RemoteInput.EXTRA_RESULTS_DATA, resultsBundle)
                    fillInIntent.clipData = ClipData(
                        RemoteInput.RESULTS_CLIP_LABEL,
                        arrayOf(ClipDescription.MIMETYPE_TEXT_INTENT),
                        ClipData.Item(clipIntent)
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val options = android.app.ActivityOptions.makeBasic().apply {
                        setPendingIntentBackgroundActivityStartMode(
                            android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        )
                    }
                    pendingIntent.send(
                        context.applicationContext,
                        0,
                        fillInIntent,
                        null,
                        null,
                        null,
                        options.toBundle()
                    )
                } else {
                    pendingIntent.send(context.applicationContext, 0, fillInIntent)
                }
                _lastSilentReplySent.value = replyText
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private var windowManager: WindowManager? = null
    private var rootTickerContainer: LinearLayout? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var scrollingTickerText: TextView? = null
    private var countdownBadgeText: TextView? = null
    private var quickReplyRowContainer: HorizontalScrollView? = null
    private var replyExpandButton: TextView? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + com.example.util.AppCrashGuard.coroutineExceptionHandler)
    private var countdownJob: Job? = null
    private var isQuickReplyExpanded = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        loadConfig(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_SHOW_TICKER
        if (action == ACTION_DISMISS || !Settings.canDrawOverlays(this)) {
            removeTickerWindow()
            stopSelf()
            return START_NOT_STICKY
        }

        val event = _latestEvent.value
        if (event == null) {
            removeTickerWindow()
            stopSelf()
            return START_NOT_STICKY
        }

        showOrUpdateTickerWindow(event)
        return START_NOT_STICKY
    }

    private fun dp(value: Int): Int {
        val density = resources.displayMetrics.density
        return (value * density).roundToInt()
    }

    private fun computeMediumTickerWidthPx(): Int {
        val dm = resources.displayMetrics
        val maxScreenWidth = (dm.widthPixels * 0.86f).roundToInt()
        return min(dp(364), maxScreenWidth).coerceAtLeast(dp(260))
    }

    @SuppressLint("SetTextI18n")
    private fun showOrUpdateTickerWindow(event: InterceptedTickerEvent) {
        removeTickerWindow()
        val wm = windowManager ?: return
        if (!Settings.canDrawOverlays(this)) return

        isQuickReplyExpanded = false

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val lp = WindowManager.LayoutParams(
            computeMediumTickerWidthPx(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(6)
        }
        windowParams = lp

        val accentBorderColor = if (event.isCall) Color.parseColor("#00E676") else Color.parseColor("#00D2FF")
        val pillBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(22).toFloat()
            setColor(Color.parseColor("#D90A0F1A"))
            setStroke(dp(1), accentBorderColor)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = pillBackground
            setPadding(dp(10), dp(5), dp(8), dp(5))
        }

        val mainRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(26)
            )
        }

        val tagPill = TextView(this).apply {
            text = if (event.isCall) "📞 CALL" else "💬 ${event.appLabel.take(9).uppercase()}"
            setTextColor(if (event.isCall) Color.parseColor("#05130D") else Color.parseColor("#04141F"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(6), dp(2), dp(6), dp(2))
            background = GradientDrawable().apply {
                cornerRadius = dp(50).toFloat()
                setColor(accentBorderColor)
            }
        }
        mainRow.addView(tagPill)

        val tickerLine = TextView(this).apply {
            val cleanSender = event.senderOrTitle.trim()
            val cleanBody = event.messageBody.trim().replace("\n", " ")
            text = if (cleanSender.isNotBlank()) {
                "$cleanSender: $cleanBody   •   $cleanSender: $cleanBody"
            } else {
                "$cleanBody   •   $cleanBody"
            }
            setTextColor(Color.parseColor("#F8FAFC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
            isHorizontalFadingEdgeEnabled = true
            setPadding(dp(8), 0, dp(6), 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        scrollingTickerText = tickerLine
        mainRow.addView(tickerLine)

        if (event.isCall) {
            val answerBtn = buildCompactActionPill(
                label = "Answer",
                bgColorHex = "#00E676",
                textColorHex = "#04160C"
            ) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        val options = android.app.ActivityOptions.makeBasic().apply {
                            setPendingIntentBackgroundActivityStartMode(
                                android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                            )
                        }
                        (event.answerIntent ?: event.contentIntent)?.send(
                            this@NotificationTickerOverlayService,
                            0,
                            null,
                            null,
                            null,
                            null,
                            options.toBundle()
                        )
                    } else {
                        (event.answerIntent ?: event.contentIntent)?.send()
                    }
                } catch (_: Exception) {}
                // Immediately mark call answered so ongoing call timer updates never re-pop the ticker
                GMaxNotificationTickerListenerService.markCallHandledAndDismiss(
                    context = this@NotificationTickerOverlayService,
                    notificationKey = event.notificationKey,
                    packageName = event.packageName
                )
            }
            mainRow.addView(answerBtn)

            val declineBtn = buildCompactActionPill(
                label = "Decline",
                bgColorHex = "#FF4D6D",
                textColorHex = "#FFFFFF",
                leftMarginDp = 4
            ) {
                try {
                    event.declineIntent?.send()
                } catch (_: Exception) {}
                HardcoreCallBlockerManager.silenceAndEndIncomingCall(this@NotificationTickerOverlayService)
                GMaxNotificationTickerListenerService.cancelActiveByKey(event.notificationKey)
                GMaxNotificationTickerListenerService.markCallHandledAndDismiss(
                    context = this@NotificationTickerOverlayService,
                    notificationKey = event.notificationKey,
                    packageName = event.packageName
                )
            }
            mainRow.addView(declineBtn)
        } else if (event.hasQuickReplyCapability) {
            val replyBtn = buildCompactActionPill(
                label = "Reply ▾",
                bgColorHex = "#1E293B",
                textColorHex = "#38BDF8",
                borderColorHex = "#38BDF8"
            ) {
                toggleQuickReplyBar(event)
            }
            replyExpandButton = replyBtn
            mainRow.addView(replyBtn)
        }

        val countdownBadge = TextView(this).apply {
            text = "${event.countdownSeconds}s"
            setTextColor(Color.parseColor("#FBBF24"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.MONOSPACE
            setPadding(dp(6), dp(2), dp(6), dp(2))
            background = GradientDrawable().apply {
                cornerRadius = dp(50).toFloat()
                setColor(Color.parseColor("#26FBBF24"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(5)
            }
        }
        countdownBadgeText = countdownBadge
        mainRow.addView(countdownBadge)

        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(5), dp(1), dp(3), dp(1))
            setOnClickListener {
                if (event.isCall) {
                    GMaxNotificationTickerListenerService.markCallHandledAndDismiss(
                        context = this@NotificationTickerOverlayService,
                        notificationKey = event.notificationKey,
                        packageName = event.packageName
                    )
                } else {
                    removeTickerWindow()
                    stopSelf()
                }
            }
        }
        mainRow.addView(closeBtn)

        root.addView(mainRow)

        if (event.hasQuickReplyCapability) {
            val scrollRow = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(5)
                }
            }

            val chipRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            QUICK_REPLIES.forEachIndexed { idx, cannedText ->
                val chip = buildCompactActionPill(
                    label = cannedText,
                    bgColorHex = "#132238",
                    textColorHex = "#E2E8F0",
                    borderColorHex = "#00D2FF",
                    leftMarginDp = if (idx == 0) 0 else 5
                ) {
                    val sentOk = sendSilentRemoteInputReply(
                        context = this@NotificationTickerOverlayService,
                        event = event,
                        replyText = cannedText
                    )
                    if (sentOk) {
                        scrollingTickerText?.text = "✓ Sent silently: \"$cannedText\""
                        scrollingTickerText?.setTextColor(Color.parseColor("#00E676"))
                        scrollRow.visibility = View.GONE
                        // Cancel notification only AFTER a brief delay so the target app finishes handling the RemoteInput
                        serviceScope.launch {
                            delay(450L)
                            GMaxNotificationTickerListenerService.cancelActiveByKey(event.notificationKey)
                        }
                        dismissAfterBriefFeedback()
                    } else {
                        scrollingTickerText?.text = "⚠️ App notification does not support background reply"
                        scrollingTickerText?.setTextColor(Color.parseColor("#FBBF24"))
                        dismissAfterBriefFeedback()
                    }
                }
                chipRow.addView(chip)
            }

            scrollRow.addView(chipRow)
            quickReplyRowContainer = scrollRow
            root.addView(scrollRow)
        }

        rootTickerContainer = root
        try {
            wm.addView(root, lp)
        } catch (_: Exception) {}

        startCountdownTimer(event)
    }

    @SuppressLint("SetTextI18n")
    private fun toggleQuickReplyBar(event: InterceptedTickerEvent) {
        val row = quickReplyRowContainer ?: return
        isQuickReplyExpanded = !isQuickReplyExpanded
        row.visibility = if (isQuickReplyExpanded) View.VISIBLE else View.GONE
        replyExpandButton?.text = if (isQuickReplyExpanded) "Reply ▴" else "Reply ▾"
        if (isQuickReplyExpanded) {
            startCountdownTimer(event, initialSecondsOverride = event.countdownSeconds.coerceAtLeast(10))
        }
    }

    private fun buildCompactActionPill(
        label: String,
        bgColorHex: String,
        textColorHex: String,
        borderColorHex: String? = null,
        leftMarginDp: Int = 5,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.parseColor(textColorHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
            setSingleLine(true)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = GradientDrawable().apply {
                cornerRadius = dp(50).toFloat()
                setColor(Color.parseColor(bgColorHex))
                if (borderColorHex != null) {
                    setStroke(dp(1), Color.parseColor(borderColorHex))
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(leftMarginDp)
            }
            setOnClickListener { onClick() }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun startCountdownTimer(event: InterceptedTickerEvent, initialSecondsOverride: Int? = null) {
        countdownJob?.cancel()
        countdownJob = serviceScope.launch {
            var remaining = (initialSecondsOverride ?: event.countdownSeconds).coerceIn(1, 30)
            while (remaining > 0) {
                countdownBadgeText?.text = "${remaining}s"
                delay(1_000L)
                remaining--
            }
            countdownBadgeText?.text = "0s"
            // If auto-cancel is enabled for messages and user didn't reply, snooze/dismiss after countdown finishes
            if (!event.isCall && !event.isDemo && _config.value.autoCancelSystemPopups) {
                GMaxNotificationTickerListenerService.snoozeActiveByKey(event.notificationKey, 15_000L)
            }
            removeTickerWindow()
            stopSelf()
        }
    }

    private fun dismissAfterBriefFeedback() {
        countdownJob?.cancel()
        countdownJob = serviceScope.launch {
            delay(850L)
            removeTickerWindow()
            stopSelf()
        }
    }

    private fun removeTickerWindow() {
        countdownJob?.cancel()
        val wm = windowManager
        val view = rootTickerContainer
        if (wm != null && view != null) {
            try {
                wm.removeView(view)
            } catch (_: Exception) {}
        }
        rootTickerContainer = null
        scrollingTickerText = null
        countdownBadgeText = null
        quickReplyRowContainer = null
        replyExpandButton = null
    }

    override fun onDestroy() {
        removeTickerWindow()
        serviceScope.cancel()
        super.onDestroy()
    }
}
