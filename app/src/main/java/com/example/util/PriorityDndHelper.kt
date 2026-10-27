package com.example.util

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

object PriorityDndHelper {
    private const val TAG = "PriorityDndHelper"
    private const val PREFS_NAME = "gmax_priority_dnd_prefs"
    private const val KEY_PREVIOUS_FILTER = "saved_interruption_filter"
    private const val KEY_IS_AUTO_ENGAGED = "is_auto_engaged"

    fun isPermissionGranted(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        return nm.isNotificationPolicyAccessGranted
    }

    fun openDndPermissionSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch DND permission settings", e)
        }
    }

    /**
     * Checks if Priority Do Not Disturb is currently active.
     */
    fun isPriorityDndActive(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        return nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
    }

    /**
     * Engages PRIORITY DND (NotificationManager.INTERRUPTION_FILTER_PRIORITY).
     * This filter allows game audio and media sound to remain unmuted, while suppressing
     * unwanted calls and notification banners during gameplay.
     * 
     * Saves the user's prior interruption filter to SharedPreferences so it can be restored.
     */
    fun enablePriorityDnd(context: Context, markAutoEngaged: Boolean = false): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false

        if (!nm.isNotificationPolicyAccessGranted) {
            return false
        }

        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentFilter = nm.currentInterruptionFilter

            // Only save previous filter if not already in PRIORITY mode
            if (currentFilter != NotificationManager.INTERRUPTION_FILTER_PRIORITY && !prefs.contains(KEY_PREVIOUS_FILTER)) {
                prefs.edit()
                    .putInt(KEY_PREVIOUS_FILTER, currentFilter)
                    .putBoolean(KEY_IS_AUTO_ENGAGED, markAutoEngaged)
                    .apply()
                Log.d(TAG, "Saved previous interruption filter: $currentFilter")
            }

            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            Log.i(TAG, "Enabled Priority DND (Media & Game audio preserved)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling Priority DND", e)
            return false
        }
    }

    /**
     * Restores the interruption filter that was active before Priority DND was enabled.
     */
    fun restorePreviousDnd(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false

        if (!nm.isNotificationPolicyAccessGranted) {
            return false
        }

        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedFilter = prefs.getInt(
                KEY_PREVIOUS_FILTER,
                NotificationManager.INTERRUPTION_FILTER_ALL
            )

            nm.setInterruptionFilter(savedFilter)
            prefs.edit()
                .remove(KEY_PREVIOUS_FILTER)
                .remove(KEY_IS_AUTO_ENGAGED)
                .apply()

            Log.i(TAG, "Restored previous interruption filter: $savedFilter")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring previous DND filter", e)
            return false
        }
    }

    fun getSavedPreviousFilterDescription(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_PREVIOUS_FILTER)) {
            return "Standard (All Interruptions Allowed)"
        }
        return when (prefs.getInt(KEY_PREVIOUS_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)) {
            NotificationManager.INTERRUPTION_FILTER_ALL -> "All Notifications Allowed"
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "Priority Only"
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> "Alarms Only"
            NotificationManager.INTERRUPTION_FILTER_NONE -> "Total Silence"
            else -> "System Default"
        }
    }

    fun isAutoEngaged(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_AUTO_ENGAGED, false)
    }
}
