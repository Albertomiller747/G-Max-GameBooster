package com.example.util

import android.content.Context
import android.os.Bundle
import com.example.data.repository.TopGearAnalyticsManager
import com.google.firebase.analytics.FirebaseAnalytics
import java.util.concurrent.CopyOnWriteArrayList

data class LoggedAnalyticsEvent(
    val name: String,
    val params: Map<String, String>,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Centralized Google Analytics (Firebase Analytics) Manager for G Max Gaming Hub.
 *
 * Syncs collection state with the user-controllable Analytics toggle in Settings and Top Gear
 * ([TopGearAnalyticsManager.isAnalyticsEnabled]) and logs key app events:
 * - Screen & Tab Views (`screen_view`)
 * - App Open (`gmax_app_open`)
 * - Game Boost Sessions (`gmax_game_boost`)
 * - Screen Recording & Screenshots (`gmax_screen_recording`, `gmax_screenshot_captured`)
 * - Controller Connections & Overlay Toggles (`gmax_controller_connection`, `gmax_controller_overlay_toggled`)
 * - Top Gear Affiliate Taps (`gmax_top_gear_tap`)
 */
object AppAnalyticsManager {

    @Volatile
    private var firebaseAnalytics: FirebaseAnalytics? = null

    @Volatile
    private var isInitialized = false

    private val recentEvents = CopyOnWriteArrayList<LoggedAnalyticsEvent>()

    private fun obtainAnalytics(context: Context): FirebaseAnalytics? {
        val existing = firebaseAnalytics
        if (existing != null) return existing
        return try {
            val instance = FirebaseAnalytics.getInstance(context.applicationContext)
            firebaseAnalytics = instance
            instance
        } catch (_: Throwable) {
            null
        }
    }

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        val enabled = TopGearAnalyticsManager.isAnalyticsEnabled(appContext)
        try {
            obtainAnalytics(appContext)?.setAnalyticsCollectionEnabled(enabled)
        } catch (_: Throwable) {}

        if (!isInitialized) {
            isInitialized = true
            logEvent(
                context = appContext,
                eventName = "gmax_app_open",
                params = mapOf("version_name" to com.example.BuildConfig.VERSION_NAME)
            )
        }
    }

    fun isAnalyticsCollectionEnabled(context: Context): Boolean {
        return TopGearAnalyticsManager.isAnalyticsEnabled(context)
    }

    fun setAnalyticsCollectionEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        TopGearAnalyticsManager.setAnalyticsEnabled(appContext, enabled, syncToFirebase = false)
        try {
            obtainAnalytics(appContext)?.setAnalyticsCollectionEnabled(enabled)
        } catch (_: Throwable) {}
    }

    fun logScreenView(
        context: Context,
        screenName: String,
        screenClass: String = "MainActivity"
    ) {
        logEvent(
            context = context,
            eventName = FirebaseAnalytics.Event.SCREEN_VIEW,
            params = mapOf(
                FirebaseAnalytics.Param.SCREEN_NAME to screenName,
                FirebaseAnalytics.Param.SCREEN_CLASS to screenClass
            )
        )
    }

    fun logGameBoost(
        context: Context,
        gamePackage: String,
        gameName: String,
        boostMode: String,
        ramFreedMb: Long
    ) {
        logEvent(
            context = context,
            eventName = "gmax_game_boost",
            params = mapOf(
                "game_package" to gamePackage.take(80),
                "game_name" to gameName.take(80),
                "boost_mode" to boostMode,
                "ram_freed_mb" to ramFreedMb.toString()
            )
        )
    }

    fun logScreenRecording(
        context: Context,
        action: String,
        resolution: String,
        fps: Int,
        audioMode: String
    ) {
        logEvent(
            context = context,
            eventName = "gmax_screen_recording",
            params = mapOf(
                "action" to action,
                "resolution" to resolution,
                "fps" to fps.toString(),
                "audio_mode" to audioMode
            )
        )
    }

    fun logScreenshotCaptured(context: Context) {
        logEvent(
            context = context,
            eventName = "gmax_screenshot_captured",
            params = mapOf("source" to "screen_recorder")
        )
    }

    fun logControllerConnection(
        context: Context,
        connected: Boolean,
        controllerName: String,
        connectionType: String
    ) {
        logEvent(
            context = context,
            eventName = "gmax_controller_connection",
            params = mapOf(
                "status" to if (connected) "connected" else "disconnected",
                "controller_name" to controllerName.ifBlank { "Gamepad" }.take(80),
                "connection_type" to connectionType
            )
        )
    }

    fun logControllerOverlayToggled(context: Context, enabled: Boolean) {
        logEvent(
            context = context,
            eventName = "gmax_controller_overlay_toggled",
            params = mapOf(
                "enabled" to enabled.toString()
            )
        )
    }

    fun logTopGearTap(
        context: Context,
        productId: String,
        tier: String,
        action: String
    ) {
        logEvent(
            context = context,
            eventName = "gmax_top_gear_tap",
            params = mapOf(
                "product_id" to productId.take(80),
                "tier" to tier.lowercase(),
                "action" to action
            )
        )
    }

    fun logSquadReferralEvent(
        context: Context,
        action: String,
        referralCode: String = "",
        channelOrTrigger: String = "",
        validatedCount: Int = 0
    ) {
        logEvent(
            context = context,
            eventName = "gmax_squad_referral",
            params = mapOf(
                "action" to action.take(40),
                "referral_code" to referralCode.take(30),
                "channel_or_trigger" to channelOrTrigger.take(40),
                "validated_count" to validatedCount.toString()
            )
        )
    }

    fun logEvent(
        context: Context,
        eventName: String,
        params: Map<String, String> = emptyMap()
    ) {
        val appContext = context.applicationContext
        if (!TopGearAnalyticsManager.isAnalyticsEnabled(appContext)) {
            return
        }

        if (recentEvents.size >= 100) {
            recentEvents.removeAt(0)
        }
        recentEvents.add(
            LoggedAnalyticsEvent(
                name = eventName,
                params = params
            )
        )

        try {
            val bundle = Bundle().apply {
                params.forEach { (key, value) ->
                    putString(key.take(40), value.take(100))
                }
            }
            obtainAnalytics(appContext)?.logEvent(eventName.take(40), bundle)
        } catch (_: Throwable) {
            // Safe fallback in local JVM unit test environment
        }
    }

    fun getRecentEventsForTesting(): List<LoggedAnalyticsEvent> = recentEvents.toList()

    fun clearRecentEventsForTesting() {
        recentEvents.clear()
        isInitialized = false
    }
}
