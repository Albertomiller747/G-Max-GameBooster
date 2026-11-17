package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telephony.TelephonyManager
import java.util.Collections
import java.util.Locale

/**
 * Intercepts incoming messaging and call notifications (WhatsApp, Telegram, SMS, Discord,
 * Messenger, Instagram, Signal, and Phone/VoIP calls) while the user is gaming:
 * - Prevents continuous call pop-ups by deduplicating incoming call keys, filtering out ongoing/in-call
 *   timer notifications (e.g. CallStyle.CALL_TYPE_ONGOING, missing Answer action, or CALL_STATE_OFFHOOK),
 *   and immediately dismissing the ticker the moment the user answers or ends a call.
 * - Preserves messaging notifications' RemoteInput PendingIntents (never canceling before the user replies)
 *   and extracts RemoteInput from both standard actions and WearableExtender actions so Silent Quick-Reply
 *   works reliably in the background.
 */
class GMaxNotificationTickerListenerService : NotificationListenerService() {

    companion object {
        @Volatile
        private var activeInstance: GMaxNotificationTickerListenerService? = null

        private val seenCallKeys: MutableSet<String> =
            Collections.synchronizedSet(mutableSetOf<String>())
        private val seenCallPackages: MutableSet<String> =
            Collections.synchronizedSet(mutableSetOf<String>())

        @Volatile
        private var isCallOffHookOrAnswered: Boolean = false

        @Volatile
        private var lastMessageKeyAndBody: String = ""

        @Volatile
        private var lastMessageTimestampMs: Long = 0L

        private val DURATION_TIMER_REGEX = Regex("""^\d{1,2}:\d{2}(:\d{2})?$""")

        val SUPPORTED_MESSAGING_And_CALL_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.discord",
            "com.facebook.orca",
            "com.instagram.android",
            "org.thoughtcrime.securesms",
            "com.snapchat.android",
            "com.viber.voip",
            "jp.naver.line.android",
            "com.google.android.dialer",
            "com.samsung.android.incallui",
            "com.android.incallui",
            "com.android.server.telecom"
        )

        fun onCallStateRinging() {
            isCallOffHookOrAnswered = false
        }

        fun onCallAnsweredOrEnded(isOffHook: Boolean) {
            isCallOffHookOrAnswered = isOffHook
            if (!isOffHook) {
                seenCallKeys.clear()
                seenCallPackages.clear()
            }
            activeInstance?.applicationContext?.let { ctx ->
                NotificationTickerOverlayService.dismissActiveTicker(ctx)
            }
        }

        fun markCallHandledAndDismiss(context: Context, notificationKey: String?, packageName: String?) {
            isCallOffHookOrAnswered = true
            if (!notificationKey.isNullOrBlank()) {
                seenCallKeys.add(notificationKey)
            }
            if (!packageName.isNullOrBlank()) {
                seenCallPackages.add(packageName)
            }
            NotificationTickerOverlayService.dismissActiveTicker(context)
        }

        fun cancelActiveByKey(notificationKey: String?) {
            if (notificationKey.isNullOrBlank()) return
            try {
                activeInstance?.cancelNotification(notificationKey)
            } catch (_: Exception) {}
        }

        fun snoozeActiveByKey(notificationKey: String?, durationMs: Long = 10_000L) {
            if (notificationKey.isNullOrBlank()) return
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    activeInstance?.snoozeNotification(notificationKey, durationMs)
                }
            } catch (_: Exception) {}
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        activeInstance = this
        HardcoreCallBlockerManager.registerCallStateObserverIfPermitted(applicationContext)
    }

    override fun onListenerDisconnected() {
        if (activeInstance === this) {
            activeInstance = null
        }
        seenCallKeys.clear()
        seenCallPackages.clear()
        super.onListenerDisconnected()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        val key = sbn?.key ?: return
        val pkg = sbn.packageName.orEmpty()
        if (seenCallKeys.remove(key) || seenCallPackages.remove(pkg)) {
            isCallOffHookOrAnswered = false
            NotificationTickerOverlayService.dismissIfMatchingKey(applicationContext, key)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        if (pkg == applicationContext.packageName) return

        // If Hardcore Mode is active, drop any call notification immediately
        if (HardcoreCallBlockerManager.isHardcoreModeActive(applicationContext)) {
            val notif = sbn.notification
            if (notif?.category == Notification.CATEGORY_CALL) {
                HardcoreCallBlockerManager.silenceAndEndIncomingCall(applicationContext)
                try {
                    cancelNotification(sbn.key)
                } catch (_: Exception) {}
                return
            }
        }

        val cfg = NotificationTickerOverlayService.loadConfig(applicationContext)
        if (!cfg.isEnabled) return

        val notification = sbn.notification ?: return
        // Skip group summary headers to avoid duplicate tickers
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val category = notification.category.orEmpty()
        val extras = notification.extras ?: return

        val rawTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: ""
        val rawText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: notification.tickerText?.toString()
            ?: ""

        if (rawTitle.isBlank() && rawText.isBlank()) return

        // Collect all candidate actions from both standard actions and WearableExtender (used by WhatsApp/Telegram/Messages)
        val allActions = mutableListOf<Notification.Action>()
        notification.actions?.let { allActions.addAll(it) }
        try {
            val wearableActions = Notification.WearableExtender(notification).actions
            if (!wearableActions.isNullOrEmpty()) {
                allActions.addAll(wearableActions)
            }
        } catch (_: Exception) {}

        var replyAction: Notification.Action? = null
        var remoteInputs: Array<RemoteInput>? = null
        var answerIntent: PendingIntent? = null
        var declineIntent: PendingIntent? = null

        for (act in allActions) {
            val inputs = act.remoteInputs
            if (!inputs.isNullOrEmpty()) {
                val hasFreeForm = inputs.any { it.allowFreeFormInput }
                if (replyAction == null || hasFreeForm) {
                    replyAction = act
                    remoteInputs = inputs
                }
            }
            val actTitle = act.title?.toString()?.lowercase(Locale.US).orEmpty()
            if (actTitle.contains("answer") || actTitle.contains("accept") ||
                actTitle.contains("responder") || actTitle.contains("contestar") ||
                actTitle.contains("応答") || actTitle.contains("उत्तर")
            ) {
                if (answerIntent == null) answerIntent = act.actionIntent
            } else if (actTitle.contains("decline") || actTitle.contains("reject") ||
                actTitle.contains("hang up") || actTitle.contains("end call") ||
                actTitle.contains("rechazar") || actTitle.contains("colgar") ||
                actTitle.contains("拒否") || actTitle.contains("अस्वीकार")
            ) {
                if (declineIntent == null) declineIntent = act.actionIntent
            }
        }

        val callTypeExtra = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            extras.getInt("android.callType", -1)
        } else -1

        val isCallNotification = category == Notification.CATEGORY_CALL ||
            (answerIntent != null && declineIntent != null) ||
            callTypeExtra >= 0 ||
            pkg.contains("incallui", ignoreCase = true) ||
            pkg.contains("dialer", ignoreCase = true)

        if (isCallNotification) {
            // 1. Check if the device is already in an answered call (OFFHOOK) or if this is an ongoing call update
            val tm = applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val isOffHookNow = try {
                @Suppress("DEPRECATION")
                tm?.callState == TelephonyManager.CALL_STATE_OFFHOOK
            } catch (_: Exception) {
                false
            }
            val lowerBody = rawText.trim().lowercase(Locale.US)
            val isOngoingCallUpdate = isCallOffHookOrAnswered ||
                isOffHookNow ||
                callTypeExtra == 2 /* Notification.CallStyle.CALL_TYPE_ONGOING */ ||
                (answerIntent == null && declineIntent != null) ||
                DURATION_TIMER_REGEX.matches(rawText.trim()) ||
                lowerBody.contains("ongoing call") ||
                lowerBody.contains("call in progress") ||
                lowerBody.contains("llamada en curso")

            if (isOngoingCallUpdate) {
                // User already answered the call! Ensure our top-edge ticker is dismissed and never re-pops
                seenCallKeys.add(sbn.key)
                seenCallPackages.add(pkg)
                NotificationTickerOverlayService.dismissActiveTicker(applicationContext)
                return
            }

            // 2. Deduplicate repeated ring updates for the same incoming call
            if (sbn.key in seenCallKeys || pkg in seenCallPackages) {
                return
            }
            seenCallKeys.add(sbn.key)
            seenCallPackages.add(pkg)
        }

        val isMessagingOrSocial = !isCallNotification && (
            category == Notification.CATEGORY_MESSAGE ||
                category == Notification.CATEGORY_SOCIAL ||
                replyAction != null ||
                pkg in SUPPORTED_MESSAGING_And_CALL_PACKAGES
            )

        if (!isCallNotification && !isMessagingOrSocial) return

        // Deduplicate identical message updates posted within 2.5 seconds
        if (!isCallNotification) {
            val now = System.currentTimeMillis()
            val sig = "${sbn.key}|$rawTitle|$rawText"
            if (sig == lastMessageKeyAndBody && (now - lastMessageTimestampMs) < 2_500L) {
                return
            }
            lastMessageKeyAndBody = sig
            lastMessageTimestampMs = now
        }

        val appLabel = resolveReadableAppName(pkg)

        val event = InterceptedTickerEvent(
            notificationKey = sbn.key,
            packageName = pkg,
            appLabel = appLabel,
            senderOrTitle = rawTitle.ifBlank { appLabel },
            messageBody = rawText.ifBlank { if (isCallNotification) "Incoming Call" else "New message" },
            isCall = isCallNotification,
            countdownSeconds = cfg.countdownSeconds,
            replyAction = replyAction,
            remoteInputs = remoteInputs,
            answerIntent = answerIntent,
            declineIntent = declineIntent,
            contentIntent = notification.contentIntent,
            isDemo = false
        )

        NotificationTickerOverlayService.dispatchInterceptedEvent(applicationContext, event)
    }

    private fun resolveReadableAppName(packageName: String): String {
        return try {
            val pm = applicationContext.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            when {
                packageName.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
                packageName.contains("telegram", ignoreCase = true) -> "Telegram"
                packageName.contains("messaging", ignoreCase = true) || packageName.contains("mms", ignoreCase = true) -> "SMS"
                packageName.contains("discord", ignoreCase = true) -> "Discord"
                packageName.contains("orca", ignoreCase = true) -> "Messenger"
                packageName.contains("instagram", ignoreCase = true) -> "Instagram"
                packageName.contains("dialer", ignoreCase = true) || packageName.contains("incallui", ignoreCase = true) -> "Phone"
                else -> "Alert"
            }
        }
    }
}
