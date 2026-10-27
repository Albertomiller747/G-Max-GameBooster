package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.util.ScreenRecordingManager

/**
 * Persistent Notification Drawer Widget for G MAX Screen Recording.
 * Provides quick actions right in the Android notification shade:
 * - Enable/Disable Overlay
 * - Record Direct
 * - Open App
 * - Exit (removes widget)
 */
class ScreenRecordNotificationWidgetService : Service() {

    companion object {
        const val ACTION_START_WIDGET = "com.example.action.START_REC_WIDGET"
        const val ACTION_STOP_WIDGET = "com.example.action.STOP_REC_WIDGET"
        const val ACTION_TOGGLE_OVERLAY = "com.example.action.WIDGET_TOGGLE_OVERLAY"
        const val ACTION_EXIT_WIDGET = "com.example.action.WIDGET_EXIT"

        private const val NOTIFICATION_ID = 5001
        private const val CHANNEL_ID = "gmax_rec_widget_channel"
        private const val PREFS_NAME = "gmax_rec_widget_prefs"
        private const val KEY_WIDGET_ENABLED = "rec_widget_enabled"

        fun isWidgetActive(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_WIDGET_ENABLED, false)
        }

        fun setWidgetActive(context: Context, active: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_WIDGET_ENABLED, active)
                .apply()
        }

        fun startWidget(context: Context) {
            setWidgetActive(context, true)
            val intent = Intent(context, ScreenRecordNotificationWidgetService::class.java).apply {
                action = ACTION_START_WIDGET
            }
            context.startService(intent)
        }

        fun stopWidget(context: Context) {
            setWidgetActive(context, false)
            val intent = Intent(context, ScreenRecordNotificationWidgetService::class.java).apply {
                action = ACTION_STOP_WIDGET
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_WIDGET

        when (action) {
            ACTION_STOP_WIDGET, ACTION_EXIT_WIDGET -> {
                setWidgetActive(this, false)
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(NOTIFICATION_ID)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_OVERLAY -> {
                val settings = ScreenRecordingManager.loadSettings(this)
                val newTarget = !settings.isOverlayEnabled
                ScreenRecordingManager.setOverlayEnabled(this, newTarget)
                ScreenRecordingManager.saveSettings(this, settings.copy(isOverlayEnabled = newTarget))

                val overlayIntent = Intent(this, ScreenRecordOverlayService::class.java).apply {
                    setAction(if (newTarget) ScreenRecordOverlayService.ACTION_START else ScreenRecordOverlayService.ACTION_STOP)
                }
                if (newTarget) {
                    startService(overlayIntent)
                } else {
                    stopService(overlayIntent)
                }
                showWidgetNotification()
            }
            else -> {
                setWidgetActive(this, true)
                showWidgetNotification()
            }
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Recording Quick Widget",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Quick actions drawer for G MAX Game Recorder"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    private fun showWidgetNotification() {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)

        // 1. Open App Action
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("extra_open_tab", "record")
        }
        val openAppPendingIntent = PendingIntent.getActivity(this, 101, openAppIntent, flags)

        // 2. Toggle Overlay Action
        val toggleOverlayIntent = Intent(this, ScreenRecordNotificationWidgetService::class.java).apply {
            action = ACTION_TOGGLE_OVERLAY
        }
        val toggleOverlayPendingIntent = PendingIntent.getService(this, 102, toggleOverlayIntent, flags)

        // 3. Direct Record Action (Opens MainActivity to prompt MediaProjection)
        val recordDirectIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("extra_trigger_recording", true)
        }
        val recordDirectPendingIntent = PendingIntent.getActivity(this, 103, recordDirectIntent, flags)

        // 4. Exit Action
        val exitIntent = Intent(this, ScreenRecordNotificationWidgetService::class.java).apply {
            action = ACTION_EXIT_WIDGET
        }
        val exitPendingIntent = PendingIntent.getService(this, 104, exitIntent, flags)

        val settings = ScreenRecordingManager.loadSettings(this)
        val overlayLabel = if (settings.isOverlayEnabled) "Disable Overlay" else "Enable Overlay"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("🎬 G MAX Screen Recorder")
            .setContentText("${settings.resolutionLabel} • ${settings.fps} FPS • Overlay: ${if (settings.isOverlayEnabled) "ON" else "OFF"}")
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_slideshow, overlayLabel, toggleOverlayPendingIntent)
            .addAction(android.R.drawable.ic_media_play, "Record Direct", recordDirectPendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "Open App", openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Exit", exitPendingIntent)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(NOTIFICATION_ID)
    }
}
