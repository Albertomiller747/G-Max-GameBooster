package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.util.AppStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BlockedCallRecord(
    val id: String,
    val phoneNumber: String,
    val callerLabel: String,
    val gameName: String,
    val timestampMs: Long,
    val formattedTime: String
)

data class HardcoreModeState(
    val isEnabled: Boolean = false,
    val activeGameName: String = "Active Match",
    val blockedCalls: List<BlockedCallRecord> = emptyList(),
    val unseenBlockedCount: Int = 0,
    val showPostMatchSummaryModal: Boolean = false,
    val isCallScreeningRoleHeld: Boolean = false,
    val isTelecomPermissionGranted: Boolean = false,
    val isDndAccessGranted: Boolean = false
)

/**
 * HardcoreCallBlockerManager:
 * Coordinates real Android Telecom CallScreeningService + TelephonyManager + TelecomManager auto-hangup
 * and Do Not Disturb silence, persistent Post-Match Summary missed call logs (purging any legacy fake entries),
 * automatic Post-Match Summary popup on return to GMax, and one-tap Call Back (ACTION_DIAL).
 */
object HardcoreCallBlockerManager {

    private const val PREFS_NAME = "gmax_hardcore_call_blocker_prefs"
    private const val KEY_ENABLED = "hardcore_mode_enabled"
    private const val KEY_ACTIVE_GAME = "hardcore_active_game_name"
    private const val KEY_BLOCKED_CALLS_JSON = "hardcore_blocked_calls_json"
    private const val KEY_UNSEEN_COUNT = "hardcore_unseen_blocked_count"
    private const val KEY_PREV_DND_FILTER = "hardcore_prev_dnd_filter"

    // Legacy simulated numbers to purge automatically so only real blocked calls are ever displayed
    private val LEGACY_FAKE_NUMBERS = setOf(
        "+1 (555) 234-8910",
        "+1 (555) 876-5432",
        "+1 (555) 390-1122"
    )

    private val _state = MutableStateFlow(HardcoreModeState())
    val state: StateFlow<HardcoreModeState> = _state.asStateFlow()

    @Volatile
    private var isInitialized = false

    @Volatile
    private var lastRecordedNumber: String = ""

    @Volatile
    private var lastRecordedEpochMs: Long = 0L

    @Volatile
    private var isTelephonyListenerRegistered = false

    private var telephonyCallbackApi31: Any? = null

    @Suppress("DEPRECATION")
    private var legacyPhoneStateListener: PhoneStateListener? = null

    fun init(context: Context) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        val activeGame = prefs.getString(KEY_ACTIVE_GAME, "Active Match") ?: "Active Match"
        val rawCalls = parseBlockedCallsJson(prefs.getString(KEY_BLOCKED_CALLS_JSON, "[]") ?: "[]")
        // Purge any legacy simulated numbers so only authentic calls remain
        val realCalls = rawCalls.filterNot { it.phoneNumber in LEGACY_FAKE_NUMBERS }
        val unseen = if (realCalls.isEmpty()) 0 else prefs.getInt(KEY_UNSEEN_COUNT, 0).coerceAtMost(realCalls.size)
        if (realCalls.size != rawCalls.size) {
            persistBlockedCalls(appCtx, realCalls, unseen)
        }
        _state.value = _state.value.copy(
            isEnabled = enabled,
            activeGameName = activeGame,
            blockedCalls = realCalls,
            unseenBlockedCount = unseen,
            isCallScreeningRoleHeld = isCallScreeningRoleHeld(appCtx),
            isTelecomPermissionGranted = hasTelecomPermissions(appCtx),
            isDndAccessGranted = isDndPolicyAccessGranted(appCtx)
        )
        isInitialized = true
        registerCallStateObserverIfPermitted(appCtx)
    }

    fun refreshPermissionStates(context: Context) {
        val appCtx = context.applicationContext
        if (!isInitialized) init(appCtx)
        _state.value = _state.value.copy(
            isCallScreeningRoleHeld = isCallScreeningRoleHeld(appCtx),
            isTelecomPermissionGranted = hasTelecomPermissions(appCtx),
            isDndAccessGranted = isDndPolicyAccessGranted(appCtx)
        )
        registerCallStateObserverIfPermitted(appCtx)
    }

    fun isHardcoreModeActive(context: Context): Boolean {
        if (!isInitialized) init(context)
        return _state.value.isEnabled
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val appCtx = context.applicationContext
        if (!isInitialized) init(appCtx)
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        _state.value = _state.value.copy(
            isEnabled = enabled,
            isCallScreeningRoleHeld = isCallScreeningRoleHeld(appCtx),
            isTelecomPermissionGranted = hasTelecomPermissions(appCtx),
            isDndAccessGranted = isDndPolicyAccessGranted(appCtx)
        )
        applyHardcoreDndFilter(appCtx, enabled)
        registerCallStateObserverIfPermitted(appCtx)
    }

    fun setActiveGameName(context: Context, gameName: String) {
        val appCtx = context.applicationContext
        if (!isInitialized) init(appCtx)
        val cleanName = gameName.ifBlank { "Active Match" }
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_GAME, cleanName)
            .apply()
        _state.value = _state.value.copy(activeGameName = cleanName)
    }

    /**
     * Records a real auto-rejected incoming phone call into the persistent Post-Match Summary log.
     * Deduplicates within a 5-second window for the same number so CallScreeningService and TelecomManager
     * never double-log the same call.
     */
    fun recordBlockedCall(
        context: Context,
        phoneNumber: String,
        callerLabel: String = "",
        gameNameOverride: String? = null
    ): BlockedCallRecord {
        val appCtx = context.applicationContext
        if (!isInitialized) init(appCtx)
        val now = System.currentTimeMillis()
        val cleanNumber = phoneNumber.trim().ifBlank { "Unknown Number" }

        if (cleanNumber == lastRecordedNumber && (now - lastRecordedEpochMs) < 5_000L) {
            return _state.value.blockedCalls.firstOrNull() ?: BlockedCallRecord(
                id = "call_$now",
                phoneNumber = cleanNumber,
                callerLabel = callerLabel.ifBlank { "Incoming Caller" },
                gameName = _state.value.activeGameName,
                timestampMs = now,
                formattedTime = ""
            )
        }
        lastRecordedNumber = cleanNumber
        lastRecordedEpochMs = now

        val timeFmt = SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date(now))
        val resolvedLabel = callerLabel.trim().ifBlank {
            if (cleanNumber == "Unknown Number") {
                AppStrings.trText("Hidden / Private Caller", "Número Oculto / Privado")
            } else {
                AppStrings.trText("Incoming Call Rejected", "Llamada Entrante Rechazada")
            }
        }
        val gameName = (gameNameOverride ?: _state.value.activeGameName).ifBlank { "Active Match" }

        val record = BlockedCallRecord(
            id = "call_${now}_${(100..999).random()}",
            phoneNumber = cleanNumber,
            callerLabel = resolvedLabel,
            gameName = gameName,
            timestampMs = now,
            formattedTime = timeFmt
        )

        val updatedList = listOf(record) + _state.value.blockedCalls.take(49)
        val updatedUnseen = _state.value.unseenBlockedCount + 1
        persistBlockedCalls(appCtx, updatedList, updatedUnseen)
        _state.value = _state.value.copy(
            blockedCalls = updatedList,
            unseenBlockedCount = updatedUnseen
        )
        return record
    }

    /**
     * Attempt to silence ringer and terminate an active incoming ringing call via TelecomManager.
     */
    @SuppressLint("MissingPermission")
    fun silenceAndEndIncomingCall(context: Context): Boolean {
        val appCtx = context.applicationContext
        // 1. Silence Ringer Stream immediately
        try {
            val audioManager = appCtx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.adjustStreamVolume(
                AudioManager.STREAM_RING,
                AudioManager.ADJUST_MUTE,
                0
            )
        } catch (_: Exception) {}

        // 2. Cut call via TelecomManager if ANSWER_PHONE_CALLS permission is granted
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(appCtx, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
            ) {
                val telecomManager = appCtx.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                @Suppress("DEPRECATION")
                telecomManager?.endCall() ?: false
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun applyHardcoreDndFilter(context: Context, hardcoreEnabled: Boolean) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (!nm.isNotificationPolicyAccessGranted) return
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (hardcoreEnabled) {
                val currentFilter = nm.currentInterruptionFilter
                if (currentFilter != NotificationManager.INTERRUPTION_FILTER_ALARMS &&
                    currentFilter != NotificationManager.INTERRUPTION_FILTER_NONE
                ) {
                    prefs.edit().putInt(KEY_PREV_DND_FILTER, currentFilter).apply()
                }
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
            } else {
                val savedFilter = prefs.getInt(KEY_PREV_DND_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
                nm.setInterruptionFilter(savedFilter)
            }
        } catch (_: Exception) {}
    }

    /**
     * Registers a real TelephonyManager call state listener so:
     * 1) When a user answers a call (`CALL_STATE_OFFHOOK`) or hangs up (`CALL_STATE_IDLE`),
     *    the Top-Edge Notification Ticker immediately stops and never re-triggers during the call.
     * 2) When Hardcore Mode is active and `CALL_STATE_RINGING` is detected, `TelecomManager.endCall()`
     *    silences and drops the call if `CallScreeningService` did not already intercept it.
     */
    @SuppressLint("MissingPermission")
    fun registerCallStateObserverIfPermitted(context: Context) {
        val appCtx = context.applicationContext
        if (isTelephonyListenerRegistered) return
        val hasReadPhoneState = ContextCompat.checkSelfPermission(
            appCtx,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasReadPhoneState) return

        val tm = appCtx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleTelephonyCallStateChanged(appCtx, state, null)
                    }
                }
                tm.registerTelephonyCallback(ContextCompat.getMainExecutor(appCtx), cb)
                telephonyCallbackApi31 = cb
                isTelephonyListenerRegistered = true
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleTelephonyCallStateChanged(appCtx, state, phoneNumber)
                    }
                }
                @Suppress("DEPRECATION")
                tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                legacyPhoneStateListener = listener
                isTelephonyListenerRegistered = true
            }
        } catch (_: Exception) {}
    }

    internal fun handleTelephonyCallStateChanged(context: Context, state: Int, incomingNumber: String?) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                GMaxNotificationTickerListenerService.onCallStateRinging()
                if (isHardcoreModeActive(context)) {
                    val ended = silenceAndEndIncomingCall(context)
                    if (ended || !incomingNumber.isNullOrBlank()) {
                        recordBlockedCall(
                            context = context,
                            phoneNumber = incomingNumber?.takeIf { it.isNotBlank() } ?: "Incoming Caller",
                            callerLabel = AppStrings.trText("Auto-Rejected via Telecom", "Rechazada por Telecom")
                        )
                    }
                    NotificationTickerOverlayService.dismissActiveTicker(context)
                }
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // User answered the call! Immediately dismiss our Top-Edge Ticker and block ongoing call pop-ups
                GMaxNotificationTickerListenerService.onCallAnsweredOrEnded(isOffHook = true)
                NotificationTickerOverlayService.dismissActiveTicker(context)
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                // Call ended / idle
                GMaxNotificationTickerListenerService.onCallAnsweredOrEnded(isOffHook = false)
                NotificationTickerOverlayService.dismissActiveTicker(context)
            }
        }
    }

    fun onUserReturnedToHubCheckPostMatchSummary(context: Context): Boolean {
        val appCtx = context.applicationContext
        if (!isInitialized) init(appCtx)
        if (_state.value.unseenBlockedCount > 0 && _state.value.blockedCalls.isNotEmpty()) {
            _state.value = _state.value.copy(showPostMatchSummaryModal = true)
            return true
        }
        return false
    }

    fun openPostMatchSummaryModal(context: Context) {
        if (!isInitialized) init(context)
        _state.value = _state.value.copy(showPostMatchSummaryModal = true)
    }

    fun dismissPostMatchSummaryModal(context: Context) {
        val appCtx = context.applicationContext
        if (!isInitialized) init(appCtx)
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_UNSEEN_COUNT, 0)
            .apply()
        _state.value = _state.value.copy(
            unseenBlockedCount = 0,
            showPostMatchSummaryModal = false
        )
    }

    fun clearBlockedCallsLog(context: Context) {
        val appCtx = context.applicationContext
        if (!isInitialized) init(appCtx)
        persistBlockedCalls(appCtx, emptyList(), 0)
        _state.value = _state.value.copy(
            blockedCalls = emptyList(),
            unseenBlockedCount = 0
        )
    }

    fun dialCallback(context: Context, phoneNumber: String) {
        val sanitized = phoneNumber.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        val uri = if (sanitized.isNotBlank()) {
            Uri.parse("tel:$sanitized")
        } else {
            Uri.parse("tel:")
        }
        try {
            val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
        } catch (_: Exception) {}
    }

    fun hasTelecomPermissions(context: Context): Boolean {
        val readState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        val answerCalls = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ANSWER_PHONE_CALLS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
        return readState && answerCalls
    }

    fun isDndPolicyAccessGranted(context: Context): Boolean {
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.isNotificationPolicyAccessGranted == true
        } catch (_: Exception) {
            false
        }
    }

    fun openDndPolicyAccessSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun isCallScreeningRoleHeld(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
            } else {
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun createCallScreeningRoleIntent(context: Context): Intent? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                } else {
                    Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                }
            } else {
                Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun openCallScreeningSettingsFallback(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val appSettings = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(appSettings)
            } catch (_: Exception) {}
        }
    }

    private fun persistBlockedCalls(
        context: Context,
        records: List<BlockedCallRecord>,
        unseenCount: Int
    ) {
        val arr = JSONArray()
        records.forEach { rec ->
            val obj = JSONObject().apply {
                put("id", rec.id)
                put("phoneNumber", rec.phoneNumber)
                put("callerLabel", rec.callerLabel)
                put("gameName", rec.gameName)
                put("timestampMs", rec.timestampMs)
                put("formattedTime", rec.formattedTime)
            }
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BLOCKED_CALLS_JSON, arr.toString())
            .putInt(KEY_UNSEEN_COUNT, unseenCount)
            .apply()
    }

    private fun parseBlockedCallsJson(raw: String): List<BlockedCallRecord> {
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<BlockedCallRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(
                    BlockedCallRecord(
                        id = obj.optString("id", "call_$i"),
                        phoneNumber = obj.optString("phoneNumber", "Unknown Number"),
                        callerLabel = obj.optString("callerLabel", "Incoming Caller"),
                        gameName = obj.optString("gameName", "Active Match"),
                        timestampMs = obj.optLong("timestampMs", 0L),
                        formattedTime = obj.optString("formattedTime", "")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/**
 * GMaxCallScreeningService:
 * Uses Android's Telecom CallScreeningService API + TelecomManager auto-hangup + full ringer silence.
 * When "Hardcore Mode" is enabled, any incoming phone call is immediately silenced, disallowed,
 * and rejected before the system incoming-call UI can appear, and logged into the Post-Match Summary.
 */
class GMaxCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val isIncoming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            callDetails.callDirection == Call.Details.DIRECTION_INCOMING
        } else {
            true
        }

        if (!isIncoming) {
            respondToCall(
                callDetails,
                CallResponse.Builder()
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build()
            )
            return
        }

        val handleNumber = callDetails.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() }
            ?: "Unknown Number"
        val callerDisplayName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            callDetails.callerDisplayName?.takeIf { it.isNotBlank() }
                ?: callDetails.contactDisplayName?.takeIf { it.isNotBlank() }
                ?: ""
        } else {
            callDetails.callerDisplayName?.takeIf { it.isNotBlank() } ?: ""
        }

        // 1. If Hardcore Call-Blocker Mode is enabled: Reject + Silence + Skip Notification + End Call
        if (HardcoreCallBlockerManager.isHardcoreModeActive(applicationContext)) {
            HardcoreCallBlockerManager.silenceAndEndIncomingCall(applicationContext)
            HardcoreCallBlockerManager.recordBlockedCall(
                context = applicationContext,
                phoneNumber = handleNumber,
                callerLabel = callerDisplayName
            )
            NotificationTickerOverlayService.dismissActiveTicker(applicationContext)

            val responseBuilder = CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                responseBuilder.setSilenceCall(true)
            }
            respondToCall(callDetails, responseBuilder.build())
            return
        }

        // 2. If Hardcore Mode is OFF, check if Top-Edge Notification Ticker is active and configured to replace call pop-ups
        val tickerCfg = NotificationTickerOverlayService.loadConfig(applicationContext)
        val silenceSystemRingtoneBanner = tickerCfg.isEnabled && tickerCfg.autoCancelSystemPopups
        val responseBuilder = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && silenceSystemRingtoneBanner) {
            responseBuilder.setSilenceCall(true)
        }
        respondToCall(callDetails, responseBuilder.build())
    }
}
