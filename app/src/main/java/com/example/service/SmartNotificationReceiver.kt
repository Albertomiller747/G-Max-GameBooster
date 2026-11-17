package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.util.SmartNotificationManager

class SmartNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SEND_TIME_MODELING = "com.example.action.SMART_NOTIFY_SEND_TIME_MODELING"
        const val ACTION_EVALUATE_RETENTION = "com.example.action.SMART_NOTIFY_EVALUATE_RETENTION"
        const val ACTION_RIVALS_MONTHLY_WRAP_12H = "com.example.action.SMART_NOTIFY_RIVALS_MONTHLY_WRAP_12H"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context ?: return
        when (intent?.action) {
            ACTION_SEND_TIME_MODELING -> {
                SmartNotificationManager.triggerSendTimeModelingPrompt(ctx)
                SmartNotificationManager.scheduleBackgroundChecks(ctx)
            }
            ACTION_EVALUATE_RETENTION -> {
                SmartNotificationManager.evaluateInactivitySequence(ctx)
                com.example.util.RivalsManager.evaluateMonthlyResetAnd12hWrap(ctx)
            }
            ACTION_RIVALS_MONTHLY_WRAP_12H -> {
                com.example.util.RivalsManager.evaluateMonthlyResetAnd12hWrap(ctx)
                SmartNotificationManager.scheduleRivalsMonthlyWrap12hBeforeReset(ctx)
            }
        }
    }
}
