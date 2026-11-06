package com.example.util

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.ui.components.ActionFeedbackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ReviewTriggerSource(val key: String) {
    BOOST_30_MIN_MILESTONE("boost_30_min_milestone"),
    BOOST_SESSION_COMPLETED("boost_session_completed"),
    SCREEN_RECORDING_STOPPED("screen_recording_stopped"),
    NOTIFICATION_DEEP_LINK("notification_deep_link")
}

/**
 * Centralized App Review & Verification Engine for GMAX Gaming Hub.
 *
 * Trigger rules:
 * 1. After a boost session completes OR after 30 minutes (`1,800,000 ms`) of active boost usage:
 *    - Displays the in-app star review popup (or queues it when returning to the app)
 *    - Dispatches a push notification directing the user to review the app.
 * 2. When a screen recording stops:
 *    - Dispatches a push notification (and queues the in-app review prompt if opened) directing the user to review the app.
 * 3. Permanent Verification Suppression:
 *    - When the user taps "Rate on Google Play" in the in-app star prompt to open the Google Play Store review page
 *      and then returns to the app (`onAppResumedCheckVerification`), the user is marked `isVerifiedReviewed = true`.
 *    - Once verified to have reviewed the app, neither the in-app review popup nor review push notifications will EVER be shown again.
 * 4. 3-Day Dismissal Cooldown:
 *    - If the user dismisses the review prompt without reviewing, waits 3 days (`259,200,000 ms`) before showing again.
 */
object AppReviewManager {

    private const val PREFS_NAME = "gmax_app_review_prefs"
    private const val KEY_IS_VERIFIED_REVIEWED = "is_verified_reviewed"
    private const val KEY_VERIFIED_AT_MS = "verified_reviewed_at_ms"
    private const val KEY_PENDING_STORE_VISIT_MS = "pending_play_store_visit_ms"
    private const val KEY_SELECTED_STARS = "last_selected_review_stars"
    private const val KEY_LAST_DISMISSED_MS = "last_review_dismissed_ms"
    private const val KEY_ACCUMULATED_BOOST_MS = "accumulated_boost_usage_ms"
    private const val KEY_BOOST_30M_TRIGGERED_IN_SESSION = "boost_30m_triggered_in_session"
    private const val KEY_PENDING_IN_APP_POPUP = "pending_in_app_review_popup"
    private const val KEY_LAST_TRIGGER_SOURCE = "last_review_trigger_source"

    const val BOOST_REVIEW_THRESHOLD_MS = 30L * 60L * 1000L // 30 minutes (1,800,000 ms)
    const val DISMISS_COOLDOWN_MS = 3L * 24L * 60L * 60L * 1000L // 3 days (259,200,000 ms)

    private val _showReviewPopup = MutableStateFlow(false)
    val showReviewPopup: StateFlow<Boolean> = _showReviewPopup.asStateFlow()

    private val _activeTriggerSource = MutableStateFlow(ReviewTriggerSource.BOOST_SESSION_COMPLETED)
    val activeTriggerSource: StateFlow<ReviewTriggerSource> = _activeTriggerSource.asStateFlow()

    private val _isVerifiedReviewedFlow = MutableStateFlow(false)
    val isVerifiedReviewedFlow: StateFlow<Boolean> = _isVerifiedReviewedFlow.asStateFlow()

    private fun getPrefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun syncStateFromPrefs(context: Context) {
        val prefs = getPrefs(context)
        val verified = prefs.getBoolean(KEY_IS_VERIFIED_REVIEWED, false)
        _isVerifiedReviewedFlow.value = verified
        if (verified) {
            _showReviewPopup.value = false
            return
        }
        val sourceKey = prefs.getString(KEY_LAST_TRIGGER_SOURCE, null)
        if (sourceKey != null) {
            ReviewTriggerSource.entries.firstOrNull { it.key == sourceKey }?.let {
                _activeTriggerSource.value = it
            }
        }
        if (prefs.getBoolean(KEY_PENDING_IN_APP_POPUP, false) && isEligibleToShowReview(context)) {
            _showReviewPopup.value = true
        }
    }

    /**
     * Returns true if the user has been verified to have reviewed the app.
     */
    fun isVerifiedReviewed(context: Context): Boolean {
        val verified = getPrefs(context).getBoolean(KEY_IS_VERIFIED_REVIEWED, false)
        _isVerifiedReviewedFlow.value = verified
        return verified
    }

    /**
     * Returns true if the user is eligible to see the review popup or receive a review push notification:
     * - Must NOT be verified reviewed (`isVerifiedReviewed == false`)
     * - Must NOT be within the 3-day dismissal cooldown window
     */
    fun isEligibleToShowReview(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (isVerifiedReviewed(context)) return false
        val lastDismissed = getPrefs(context).getLong(KEY_LAST_DISMISSED_MS, 0L)
        if (lastDismissed > 0L && (nowMs - lastDismissed) in 0 until DISMISS_COOLDOWN_MS) {
            return false
        }
        return true
    }

    fun getAccumulatedBoostUsageMs(context: Context): Long {
        return getPrefs(context).getLong(KEY_ACCUMULATED_BOOST_MS, 0L)
    }

    /**
     * Called when a new Boost session starts to reset the per-session 30-minute trigger guard.
     */
    fun onNewBoostSessionStarted(context: Context) {
        getPrefs(context).edit()
            .putBoolean(KEY_BOOST_30M_TRIGGERED_IN_SESSION, false)
            .apply()
    }

    /**
     * Records active boost usage time (in milliseconds).
     * Once accumulated boost time reaches 30 minutes (`1,800,000 ms`), triggers the in-app review popup
     * and dispatches the review push notification if eligible.
     */
    fun recordBoostUsageMs(
        context: Context,
        deltaMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (deltaMs <= 0L) return false
        if (isVerifiedReviewed(context)) return false

        val prefs = getPrefs(context)
        val newTotal = prefs.getLong(KEY_ACCUMULATED_BOOST_MS, 0L) + deltaMs
        val alreadyTriggeredInSession = prefs.getBoolean(KEY_BOOST_30M_TRIGGERED_IN_SESSION, false)
        prefs.edit().putLong(KEY_ACCUMULATED_BOOST_MS, newTotal).apply()

        if (newTotal >= BOOST_REVIEW_THRESHOLD_MS && !alreadyTriggeredInSession) {
            prefs.edit().putBoolean(KEY_BOOST_30M_TRIGGERED_IN_SESSION, true).apply()
            return triggerReviewFlow(
                context = context,
                source = ReviewTriggerSource.BOOST_30_MIN_MILESTONE,
                sendPushNotification = true,
                showInAppModal = true,
                nowMs = nowMs
            )
        }
        return false
    }

    /**
     * Triggered when a Boost session finishes or is stopped by the user.
     * Shows the in-app review popup and dispatches a review push notification if eligible.
     */
    fun onBoostSessionEnded(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        return triggerReviewFlow(
            context = context,
            source = ReviewTriggerSource.BOOST_SESSION_COMPLETED,
            sendPushNotification = true,
            showInAppModal = true,
            nowMs = nowMs
        )
    }

    /**
     * Triggered when a Screen Recording stops and saves.
     * Dispatches a push notification directing the user to review the app and queues the in-app popup.
     */
    fun onScreenRecordingStopped(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        return triggerReviewFlow(
            context = context,
            source = ReviewTriggerSource.SCREEN_RECORDING_STOPPED,
            sendPushNotification = true,
            showInAppModal = true,
            nowMs = nowMs
        )
    }

    /**
     * Triggered when the user taps a Review Push Notification to open the app's review prompt.
     */
    fun onReviewNotificationTapped(context: Context) {
        if (isVerifiedReviewed(context)) {
            _showReviewPopup.value = false
            return
        }
        _activeTriggerSource.value = ReviewTriggerSource.NOTIFICATION_DEEP_LINK
        getPrefs(context).edit()
            .putBoolean(KEY_PENDING_IN_APP_POPUP, true)
            .putString(KEY_LAST_TRIGGER_SOURCE, ReviewTriggerSource.NOTIFICATION_DEEP_LINK.key)
            .apply()
        _showReviewPopup.value = true
    }

    private fun triggerReviewFlow(
        context: Context,
        source: ReviewTriggerSource,
        sendPushNotification: Boolean,
        showInAppModal: Boolean,
        nowMs: Long
    ): Boolean {
        if (!isEligibleToShowReview(context, nowMs)) {
            _showReviewPopup.value = false
            return false
        }

        _activeTriggerSource.value = source
        val prefs = getPrefs(context)
        prefs.edit()
            .putString(KEY_LAST_TRIGGER_SOURCE, source.key)
            .putBoolean(KEY_PENDING_IN_APP_POPUP, showInAppModal)
            .apply()

        if (showInAppModal) {
            _showReviewPopup.value = true
        }

        if (sendPushNotification) {
            when (source) {
                ReviewTriggerSource.SCREEN_RECORDING_STOPPED -> {
                    SmartNotificationManager.triggerReviewNotificationAfterRecording(context, nowMs)
                }
                else -> {
                    SmartNotificationManager.triggerReviewNotificationAfterBoost(context, nowMs)
                }
            }
        }
        return true
    }

    /**
     * Called when the user selects stars and taps "Rate on Google Play" inside the in-app review popup.
     * Records a pending Play Store review visit timestamp and launches the Google Play Store review page.
     * When the user returns to the app (`onAppResumedCheckVerification`), they are marked verified reviewed.
     */
    fun launchPlayStoreReviewPage(
        context: Context,
        selectedStars: Int = 5,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val clampedStars = selectedStars.coerceIn(1, 5)
        getPrefs(context).edit()
            .putLong(KEY_PENDING_STORE_VISIT_MS, nowMs)
            .putInt(KEY_SELECTED_STARS, clampedStars)
            .putBoolean(KEY_PENDING_IN_APP_POPUP, false)
            .apply()
        _showReviewPopup.value = false

        val pkg = context.packageName
        try {
            val marketIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$pkg&showAllReviews=true")
            ).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_HISTORY or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                )
            }
            context.startActivity(marketIntent)
        } catch (_: Exception) {
            try {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$pkg&showAllReviews=true")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            } catch (_: Exception) {}
        }
    }

    /**
     * Called in `MainActivity.onResume()`.
     * If the user previously opened the Google Play Store review page (`KEY_PENDING_STORE_VISIT_MS > 0L`)
     * and has now returned to the app, marks the user as verified reviewed (`KEY_IS_VERIFIED_REVIEWED = true`),
     * cancels any active review notifications, and permanently suppresses all future review prompts and notifications.
     */
    fun onAppResumedCheckVerification(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val prefs = getPrefs(context)
        val pendingVisitMs = prefs.getLong(KEY_PENDING_STORE_VISIT_MS, 0L)
        if (pendingVisitMs > 0L) {
            markUserVerifiedReviewed(context, nowMs)
            ActionFeedbackController.emitFeedback(
                context = context,
                message = AppStrings.trText(
                    en = "Thank you for reviewing GMAX! Review verified.",
                    es = "¡Gracias por calificar GMAX! Reseña verificada.",
                    ptBr = "Obrigado por avaliar o GMAX! Avaliação verificada.",
                    id = "Terima kasih telah mengulas GMAX! Ulasan terverifikasi.",
                    vi = "Cảm ơn bạn đã đánh giá GMAX! Đã xác minh đánh giá.",
                    tl = "Salamat sa pag-review sa GMAX! Na-verify na ang review."
                ),
                accentColorArgb = 0xFF00E676L,
                playHaptic = true
            )
            return true
        }
        syncStateFromPrefs(context)
        return false
    }

    /**
     * Explicitly marks the user as verified reviewed and permanently disables all review prompts & review notifications.
     */
    fun markUserVerifiedReviewed(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ) {
        getPrefs(context).edit()
            .putBoolean(KEY_IS_VERIFIED_REVIEWED, true)
            .putLong(KEY_VERIFIED_AT_MS, nowMs)
            .putLong(KEY_PENDING_STORE_VISIT_MS, 0L)
            .putBoolean(KEY_PENDING_IN_APP_POPUP, false)
            .apply()
        _isVerifiedReviewedFlow.value = true
        _showReviewPopup.value = false

        // Cancel any lingering review notifications in the system tray
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(SmartNotificationTrigger.APP_REVIEW_AFTER_BOOST.id)
            nm?.cancel(SmartNotificationTrigger.APP_REVIEW_AFTER_RECORDING.id)
        } catch (_: Exception) {}
    }

    /**
     * Called when the user dismisses the review popup or taps "Maybe Later".
     * Enforces a 3-day cooldown before the popup or review push notification can appear again.
     */
    fun dismissReviewPromptForCooldown(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ) {
        getPrefs(context).edit()
            .putLong(KEY_LAST_DISMISSED_MS, nowMs)
            .putBoolean(KEY_PENDING_IN_APP_POPUP, false)
            .apply()
        _showReviewPopup.value = false
    }

    fun resetForTesting(context: Context) {
        getPrefs(context).edit().clear().apply()
        _showReviewPopup.value = false
        _isVerifiedReviewedFlow.value = false
        _activeTriggerSource.value = ReviewTriggerSource.BOOST_SESSION_COMPLETED
    }
}
