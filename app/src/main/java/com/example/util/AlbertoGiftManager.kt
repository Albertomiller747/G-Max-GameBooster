package com.example.util

import android.content.Context
import android.util.Log
import com.example.data.repository.UserCloudProfile
import com.example.model.AppBackgroundTheme
import com.google.firebase.auth.auth
import java.util.Locale

/**
 * Cloud & Local Gift Fulfillment Engine for "Alberto Gift".
 *
 * Targets `hoseamk1@gmail.com` with:
 * - Gamerscore: 100,000 GS (Apex Legend Lv.1)
 * - Screen Recorder Settings: 720p (1280x720), 60 FPS, 6 Mbps
 * - Theme: Dark Mode (`AppBackgroundTheme.DARK_HUB`)
 * - Immediate System Push Notification: "Alberto Gift has Arrived"
 */
object AlbertoGiftManager {

    private const val TAG = "AlbertoGiftManager"

    const val TARGET_GIFT_EMAIL = "hoseamk1@gmail.com"
    const val GIFT_GAMERSCORE = 100_000L
    const val GIFT_REC_RESOLUTION = "720p"
    const val GIFT_REC_WIDTH = 1280
    const val GIFT_REC_HEIGHT = 720
    const val GIFT_REC_FPS = 60
    const val GIFT_REC_BITRATE_MBPS = 6
    const val GIFT_THEME_MODE = "DARK_HUB"

    const val GIFT_NOTIFICATION_TITLE = "Alberto Gift has Arrived"
    const val GIFT_NOTIFICATION_MESSAGE_EN =
        "Alberto Gift has Arrived! +100,000 Gamerscore (Apex Legend Lv.1), 720p 60FPS 6Mbps Recorder Preset & Dark Mode have been automatically applied."
    const val GIFT_NOTIFICATION_MESSAGE_ES =
        "¡Alberto Gift has Arrived! +100,000 Gamerscore (Apex Legend Nv.1), Grabación 720p 60FPS 6Mbps y Modo Oscuro activados automáticamente."

    private const val PREFS_NAME = "gmax_alberto_gift_prefs"
    private const val KEY_GIFT_APPLIED_FOR_HOSEA = "alberto_gift_v1_applied_hoseamk1"
    private const val KEY_GIFT_NOTIF_SENT_FOR_HOSEA = "alberto_gift_v1_notif_sent_hoseamk1"

    fun isTargetGiftRecipient(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        return email.trim().lowercase(Locale.ROOT) == TARGET_GIFT_EMAIL
    }

    fun hasAppliedLocalGiftPreset(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_GIFT_APPLIED_FOR_HOSEA, false)
    }

    fun markLocalGiftPresetApplied(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_GIFT_APPLIED_FOR_HOSEA, true)
            .apply()
    }

    fun hasSentGiftNotification(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_GIFT_NOTIF_SENT_FOR_HOSEA, false)
    }

    fun markGiftNotificationSent(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_GIFT_NOTIF_SENT_FOR_HOSEA, true)
            .apply()
    }

    /**
     * Checks if the current device has `hoseamk1@gmail.com` signed in (via Firebase Auth or cached hub prefs)
     * and dispatches the immediate system push notification "Alberto Gift has Arrived" before the app session starts.
     */
    fun checkAndPostPreLaunchNotification(
        context: Context,
        explicitEmail: String? = null,
        forcePost: Boolean = false
    ): Boolean {
        val appContext = context.applicationContext
        val hubPrefs = appContext.getSharedPreferences("gmax_hub_prefs", Context.MODE_PRIVATE)
        val cachedEmail = explicitEmail?.takeIf { it.isNotBlank() }
            ?: try {
                com.google.firebase.Firebase.auth.currentUser?.email
            } catch (_: Throwable) {
                null
            }
            ?: hubPrefs.getString("hub_user_email", null)

        if (!isTargetGiftRecipient(cachedEmail)) {
            return false
        }

        if (!forcePost && hasSentGiftNotification(appContext)) {
            return false
        }

        val posted = SmartNotificationManager.postAlbertoGiftNotification(appContext)
        if (posted != null) {
            markGiftNotificationSent(appContext)
            Log.d(TAG, "Dispatched 'Alberto Gift has Arrived' notification for $cachedEmail")
            return true
        }
        return false
    }

    /**
     * Applies the Alberto Gift settings (720p 60fps 6Mbps + Dark Mode) to local device storage
     * and returns an upgraded [UserCloudProfile] with 100,000 Gamerscore, 720p 60fps 6Mbps, and DARK_HUB.
     */
    fun applyGiftToProfileAndLocalStorage(
        context: Context,
        profile: UserCloudProfile
    ): UserCloudProfile {
        val appContext = context.applicationContext
        try {
            val currentRec = ScreenRecordingManager.loadSettings(appContext)
            val giftedRec = currentRec.copy(
                resolutionLabel = GIFT_REC_RESOLUTION,
                width = GIFT_REC_WIDTH,
                height = GIFT_REC_HEIGHT,
                fps = GIFT_REC_FPS,
                bitrateMbps = GIFT_REC_BITRATE_MBPS
            )
            ScreenRecordingManager.saveSettings(appContext, giftedRec)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save gifted recording settings: ${e.message}")
        }

        try {
            appContext.getSharedPreferences("gmax_hub_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("hub_theme_mode", AppBackgroundTheme.DARK_HUB.name)
                .apply()
        } catch (_: Exception) {}

        markLocalGiftPresetApplied(appContext)

        return profile.copy(
            gamerscore = maxOf(profile.gamerscore, GIFT_GAMERSCORE),
            recResolutionLabel = GIFT_REC_RESOLUTION,
            recFps = GIFT_REC_FPS.toLong(),
            recBitrateMbps = GIFT_REC_BITRATE_MBPS.toLong(),
            themeMode = GIFT_THEME_MODE
        )
    }
}
