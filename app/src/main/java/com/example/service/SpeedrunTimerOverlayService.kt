package com.example.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.util.AppStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

enum class SpeedrunIntegrityStatus(
    val badgeEn: String,
    val badgeEs: String,
    val isClean: Boolean
) {
    VERIFIED_CLEAN(
        badgeEn = "🛡️ VERIFIED ANTI-CHEAT",
        badgeEs = "🛡️ VERIFICADO ANTI-TRAMPA",
        isClean = true
    ),
    TAMPERED_CLOCK_SKEW(
        badgeEn = "⚠️ TAMPER LOCK: CLOCK SKEW",
        badgeEs = "⚠️ BLOQUEO: RELOJ ALTERADO",
        isClean = false
    ),
    TAMPERED_DEBUGGER(
        badgeEn = "⚠️ TAMPER LOCK: DEBUGGER",
        badgeEs = "⚠️ BLOQUEO: DEPURADOR",
        isClean = false
    ),
    TAMPERED_PAUSE_ATTEMPT(
        badgeEn = "⚠️ TAMPER LOCK: PAUSE BLOCKED",
        badgeEs = "⚠️ BLOQUEO: PAUSA PROHIBIDA",
        isClean = false
    )
}

enum class SpeedrunOverlaySizePreset(
    val scaleFactor: Float,
    val labelEn: String,
    val labelEs: String,
    val maxVisibleSplits: Int
) {
    COMPACT(0.80f, "Compact", "Compacto", 1),
    NORMAL(1.00f, "Normal", "Normal", 3),
    EXPANDED(1.25f, "Expanded", "Expandido", 5)
}

data class SpeedrunSplit(
    val index: Int,
    val label: String,
    val splitElapsedMs: Long,
    val segmentDeltaMs: Long,
    val formattedSplitTime: String,
    val formattedSegmentDelta: String,
    val splitVerificationHash: String
)

data class SpeedrunPastRunRecord(
    val id: String,
    val gamePackage: String,
    val gameName: String,
    val elapsedMs: Long,
    val formattedTime: String,
    val splitsCount: Int,
    val integrityStatus: SpeedrunIntegrityStatus,
    val verificationHash: String,
    val certificateCode: String,
    val timestampMs: Long,
    val formattedDate: String
)

data class SpeedrunTimerState(
    val isEnabled: Boolean = false,
    val isRunning: Boolean = false,
    val isFinished: Boolean = false,
    val targetGamePackage: String = "",
    val targetGameName: String = "General Speedrun",
    val elapsedMs: Long = 0L,
    val formattedTime: String = "00:00.000",
    val splits: List<SpeedrunSplit> = emptyList(),
    val integrityStatus: SpeedrunIntegrityStatus = SpeedrunIntegrityStatus.VERIFIED_CLEAN,
    val liveVerificationHash: String = "SR-00000000",
    val frameHeartbeatTick: Long = 0L,
    val noPauseLockActive: Boolean = true,
    val runCertificateCode: String? = null,
    val sizePreset: SpeedrunOverlaySizePreset = SpeedrunOverlaySizePreset.NORMAL,
    val overlayScale: Float = 1.0f,
    val pastRuns: List<SpeedrunPastRunRecord> = emptyList()
)

/**
 * SpeedrunTimerOverlayService:
 * Floating millisecond speedrun timer with 4 anti-cheat security layers, interactive overlay sizing
 * (Compact / Normal / Expanded presets + floating resize handle), and persistent per-app Past Speedruns History:
 * 1. Hardware Monotonic Clock (`SystemClock.elapsedRealtimeNanos()`) immune to system time changes.
 * 2. Wall-Clock vs Monotonic Clock-Skew & Debugger Tamper Detector (`CLOCK_SKEW_THRESHOLD_MS = 1500L`).
 * 3. Live Rolling SHA-256 Cryptographic Watermark (`SR-XXXXXXXX`) + Frame Heartbeat Counter (`Tick #...`).
 * 4. Competitive No-Pause Lock, Verified Split/Lap Recorder, and per-app Past Runs History (`GMAX-SR-...`).
 */
class SpeedrunTimerOverlayService : Service() {

    companion object {
        const val ACTION_START_OVERLAY = "com.example.service.SpeedrunTimerOverlayService.ACTION_START_OVERLAY"
        const val ACTION_STOP_OVERLAY = "com.example.service.SpeedrunTimerOverlayService.ACTION_STOP_OVERLAY"
        const val ACTION_REFRESH_SIZE = "com.example.service.SpeedrunTimerOverlayService.ACTION_REFRESH_SIZE"
        const val EXTRA_GAME_PKG = "extra_speedrun_game_pkg"
        const val EXTRA_GAME_NAME = "extra_speedrun_game_name"

        private const val PREFS_NAME = "gmax_speedrun_timer_prefs"
        private const val KEY_ENABLED = "speedrun_timer_enabled"
        private const val KEY_POS_X = "speedrun_timer_pos_x"
        private const val KEY_POS_Y = "speedrun_timer_pos_y"
        private const val KEY_SIZE_PRESET = "speedrun_timer_size_preset"
        private const val KEY_OVERLAY_SCALE = "speedrun_timer_overlay_scale"
        private const val KEY_PAST_RUNS_JSON = "speedrun_timer_past_runs_json"
        const val CLOCK_SKEW_THRESHOLD_MS = 1500L

        private val _state = MutableStateFlow(SpeedrunTimerState())
        val state: StateFlow<SpeedrunTimerState> = _state.asStateFlow()

        private var runStartMonoNanos: Long = 0L
        private var runStartWallMs: Long = 0L
        private var runNonceSeed: String = "GMAX_SR_INIT"

        @Volatile
        private var lastContextRef: Context? = null

        fun loadSavedEnabled(context: Context): Boolean {
            val appCtx = context.applicationContext
            lastContextRef = appCtx
            val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean(KEY_ENABLED, false)
            val presetName = prefs.getString(KEY_SIZE_PRESET, SpeedrunOverlaySizePreset.NORMAL.name)
                ?: SpeedrunOverlaySizePreset.NORMAL.name
            val preset = SpeedrunOverlaySizePreset.entries.firstOrNull { it.name == presetName }
                ?: SpeedrunOverlaySizePreset.NORMAL
            val scale = prefs.getFloat(KEY_OVERLAY_SCALE, preset.scaleFactor).coerceIn(0.70f, 1.45f)
            val history = parsePastRunsJson(prefs.getString(KEY_PAST_RUNS_JSON, "[]") ?: "[]")

            _state.value = _state.value.copy(
                isEnabled = enabled,
                sizePreset = preset,
                overlayScale = scale,
                pastRuns = history
            )
            return enabled
        }

        fun setSizePreset(context: Context, preset: SpeedrunOverlaySizePreset): SpeedrunTimerState {
            val appCtx = context.applicationContext
            lastContextRef = appCtx
            appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_SIZE_PRESET, preset.name)
                .putFloat(KEY_OVERLAY_SCALE, preset.scaleFactor)
                .apply()
            _state.value = _state.value.copy(
                sizePreset = preset,
                overlayScale = preset.scaleFactor
            )
            notifyOverlayRefreshSize(appCtx)
            return _state.value
        }

        fun setCustomOverlayScale(context: Context, scale: Float): SpeedrunTimerState {
            val appCtx = context.applicationContext
            lastContextRef = appCtx
            val clamped = scale.coerceIn(0.70f, 1.45f)
            val nearestPreset = when {
                clamped <= 0.88f -> SpeedrunOverlaySizePreset.COMPACT
                clamped >= 1.14f -> SpeedrunOverlaySizePreset.EXPANDED
                else -> SpeedrunOverlaySizePreset.NORMAL
            }
            appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_SIZE_PRESET, nearestPreset.name)
                .putFloat(KEY_OVERLAY_SCALE, clamped)
                .apply()
            _state.value = _state.value.copy(
                sizePreset = nearestPreset,
                overlayScale = clamped
            )
            notifyOverlayRefreshSize(appCtx)
            return _state.value
        }

        fun cycleSizePreset(context: Context): SpeedrunTimerState {
            val current = _state.value.sizePreset
            val next = when (current) {
                SpeedrunOverlaySizePreset.COMPACT -> SpeedrunOverlaySizePreset.NORMAL
                SpeedrunOverlaySizePreset.NORMAL -> SpeedrunOverlaySizePreset.EXPANDED
                SpeedrunOverlaySizePreset.EXPANDED -> SpeedrunOverlaySizePreset.COMPACT
            }
            return setSizePreset(context, next)
        }

        private fun notifyOverlayRefreshSize(context: Context) {
            if (!_state.value.isEnabled) return
            try {
                val intent = Intent(context, SpeedrunTimerOverlayService::class.java).apply {
                    action = ACTION_REFRESH_SIZE
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }

        fun setEnabled(
            context: Context,
            enabled: Boolean,
            gamePackage: String? = null,
            gameName: String? = null
        ) {
            val appCtx = context.applicationContext
            lastContextRef = appCtx
            appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, enabled)
                .apply()

            val updatedPkg = gamePackage ?: _state.value.targetGamePackage
            val updatedName = gameName?.takeIf { it.isNotBlank() } ?: _state.value.targetGameName
            _state.value = _state.value.copy(
                isEnabled = enabled,
                targetGamePackage = updatedPkg,
                targetGameName = updatedName
            )

            if (enabled && Settings.canDrawOverlays(appCtx)) {
                val intent = Intent(appCtx, SpeedrunTimerOverlayService::class.java).apply {
                    action = ACTION_START_OVERLAY
                    putExtra(EXTRA_GAME_PKG, updatedPkg)
                    putExtra(EXTRA_GAME_NAME, updatedName)
                }
                try {
                    appCtx.startService(intent)
                } catch (_: Exception) {}
            } else if (!enabled) {
                val intent = Intent(appCtx, SpeedrunTimerOverlayService::class.java).apply {
                    action = ACTION_STOP_OVERLAY
                }
                try {
                    appCtx.startService(intent)
                } catch (_: Exception) {
                    try {
                        appCtx.stopService(Intent(appCtx, SpeedrunTimerOverlayService::class.java))
                    } catch (_: Exception) {}
                }
            }
        }

        fun startNewVerifiedRun(
            gamePackage: String = _state.value.targetGamePackage,
            gameName: String = _state.value.targetGameName,
            customStartMonoNanos: Long = SystemClock.elapsedRealtimeNanos(),
            customStartWallMs: Long = System.currentTimeMillis()
        ): SpeedrunTimerState {
            runStartMonoNanos = customStartMonoNanos
            runStartWallMs = customStartWallMs
            runNonceSeed = "GMAX-${customStartMonoNanos}-${gamePackage.ifBlank { "global" }}"
            val initialIntegrity = if (Debug.isDebuggerConnected()) {
                SpeedrunIntegrityStatus.TAMPERED_DEBUGGER
            } else {
                SpeedrunIntegrityStatus.VERIFIED_CLEAN
            }
            val initialHash = computeVerificationHash(
                seed = runNonceSeed,
                elapsedSecondsBucket = 0L,
                splitsCount = 0,
                lastSplitMs = 0L,
                integrity = initialIntegrity
            )
            val newState = _state.value.copy(
                isRunning = true,
                isFinished = false,
                targetGamePackage = gamePackage,
                targetGameName = gameName.ifBlank { "General Speedrun" },
                elapsedMs = 0L,
                formattedTime = formatSpeedrunDuration(0L),
                splits = emptyList(),
                integrityStatus = initialIntegrity,
                liveVerificationHash = initialHash,
                frameHeartbeatTick = 1L,
                noPauseLockActive = true,
                runCertificateCode = null
            )
            _state.value = newState
            return newState
        }

        fun tickTimer(
            nowMonoNanos: Long = SystemClock.elapsedRealtimeNanos(),
            nowWallMs: Long = System.currentTimeMillis()
        ): SpeedrunTimerState {
            val current = _state.value
            if (!current.isRunning || current.isFinished) return current

            val monoElapsedMs = ((nowMonoNanos - runStartMonoNanos) / 1_000_000L).coerceAtLeast(0L)
            val wallElapsedMs = nowWallMs - runStartWallMs
            val clockDriftMs = abs(wallElapsedMs - monoElapsedMs)

            val updatedIntegrity = when {
                !current.integrityStatus.isClean -> current.integrityStatus
                clockDriftMs > CLOCK_SKEW_THRESHOLD_MS -> SpeedrunIntegrityStatus.TAMPERED_CLOCK_SKEW
                Debug.isDebuggerConnected() -> SpeedrunIntegrityStatus.TAMPERED_DEBUGGER
                else -> SpeedrunIntegrityStatus.VERIFIED_CLEAN
            }

            val nextTick = current.frameHeartbeatTick + 1L
            val secBucket = monoElapsedMs / 1000L
            val lastSplitMs = current.splits.lastOrNull()?.splitElapsedMs ?: 0L
            val rollingHash = computeVerificationHash(
                seed = runNonceSeed,
                elapsedSecondsBucket = secBucket,
                splitsCount = current.splits.size,
                lastSplitMs = lastSplitMs,
                integrity = updatedIntegrity
            )

            val nextState = current.copy(
                elapsedMs = monoElapsedMs,
                formattedTime = formatSpeedrunDuration(monoElapsedMs),
                integrityStatus = updatedIntegrity,
                liveVerificationHash = rollingHash,
                frameHeartbeatTick = nextTick
            )
            _state.value = nextState
            return nextState
        }

        fun recordSplit(
            customLabel: String? = null,
            nowMonoNanos: Long = SystemClock.elapsedRealtimeNanos(),
            nowWallMs: Long = System.currentTimeMillis()
        ): SpeedrunSplit? {
            val ticked = tickTimer(nowMonoNanos, nowWallMs)
            if (!ticked.isRunning || ticked.isFinished) return null

            val splitIndex = ticked.splits.size + 1
            val prevSplitMs = ticked.splits.lastOrNull()?.splitElapsedMs ?: 0L
            val segmentMs = (ticked.elapsedMs - prevSplitMs).coerceAtLeast(0L)
            val splitHash = computeVerificationHash(
                seed = runNonceSeed,
                elapsedSecondsBucket = ticked.elapsedMs,
                splitsCount = splitIndex,
                lastSplitMs = ticked.elapsedMs,
                integrity = ticked.integrityStatus
            )
            val split = SpeedrunSplit(
                index = splitIndex,
                label = customLabel?.takeIf { it.isNotBlank() } ?: "Split #$splitIndex",
                splitElapsedMs = ticked.elapsedMs,
                segmentDeltaMs = segmentMs,
                formattedSplitTime = formatSpeedrunDuration(ticked.elapsedMs),
                formattedSegmentDelta = "+${formatSpeedrunDuration(segmentMs)}",
                splitVerificationHash = splitHash
            )
            val updatedSplits = ticked.splits + split
            _state.value = ticked.copy(
                splits = updatedSplits,
                liveVerificationHash = splitHash
            )
            return split
        }

        fun finishRun(
            nowMonoNanos: Long = SystemClock.elapsedRealtimeNanos(),
            nowWallMs: Long = System.currentTimeMillis()
        ): SpeedrunTimerState {
            val ticked = tickTimer(nowMonoNanos, nowWallMs)
            if (!ticked.isRunning) return ticked
            val finalHash = computeVerificationHash(
                seed = runNonceSeed,
                elapsedSecondsBucket = ticked.elapsedMs,
                splitsCount = ticked.splits.size,
                lastSplitMs = ticked.elapsedMs,
                integrity = ticked.integrityStatus
            )
            val statusTag = if (ticked.integrityStatus.isClean) "VERIFIED" else "TAMPERED"
            val resolvedGameName = ticked.targetGameName.ifBlank { "General Speedrun" }
            val resolvedGamePkg = ticked.targetGamePackage.ifBlank { "global.speedrun" }
            val cert = "GMAX-SR-$statusTag-${resolvedGameName.replace(" ", "").take(10).uppercase(Locale.US)}-${ticked.formattedTime}-${finalHash}"

            val dateStr = SimpleDateFormat("MMM d • h:mm a", Locale.getDefault()).format(Date(nowWallMs))
            val newRecord = SpeedrunPastRunRecord(
                id = "sr_${nowWallMs}_${(100..999).random()}",
                gamePackage = resolvedGamePkg,
                gameName = resolvedGameName,
                elapsedMs = ticked.elapsedMs,
                formattedTime = ticked.formattedTime,
                splitsCount = ticked.splits.size,
                integrityStatus = ticked.integrityStatus,
                verificationHash = finalHash,
                certificateCode = cert,
                timestampMs = nowWallMs,
                formattedDate = dateStr
            )

            val updatedHistory = (listOf(newRecord) + ticked.pastRuns).take(40)
            lastContextRef?.let { ctx ->
                persistPastRuns(ctx, updatedHistory)
            }

            val finishedState = ticked.copy(
                isRunning = false,
                isFinished = true,
                liveVerificationHash = finalHash,
                runCertificateCode = cert,
                pastRuns = updatedHistory
            )
            _state.value = finishedState
            return finishedState
        }

        fun clearPastRunsHistory(context: Context? = lastContextRef): SpeedrunTimerState {
            val ctx = context?.applicationContext ?: lastContextRef
            if (ctx != null) {
                persistPastRuns(ctx, emptyList())
            }
            _state.value = _state.value.copy(pastRuns = emptyList())
            return _state.value
        }

        fun deletePastRunById(runId: String, context: Context? = lastContextRef): SpeedrunTimerState {
            val updated = _state.value.pastRuns.filterNot { it.id == runId }
            val ctx = context?.applicationContext ?: lastContextRef
            if (ctx != null) {
                persistPastRuns(ctx, updated)
            }
            _state.value = _state.value.copy(pastRuns = updated)
            return _state.value
        }

        private fun persistPastRuns(context: Context, runs: List<SpeedrunPastRunRecord>) {
            val arr = JSONArray()
            runs.forEach { r ->
                arr.put(
                    JSONObject().apply {
                        put("id", r.id)
                        put("gamePackage", r.gamePackage)
                        put("gameName", r.gameName)
                        put("elapsedMs", r.elapsedMs)
                        put("formattedTime", r.formattedTime)
                        put("splitsCount", r.splitsCount)
                        put("integrityStatus", r.integrityStatus.name)
                        put("verificationHash", r.verificationHash)
                        put("certificateCode", r.certificateCode)
                        put("timestampMs", r.timestampMs)
                        put("formattedDate", r.formattedDate)
                    }
                )
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_PAST_RUNS_JSON, arr.toString())
                .apply()
        }

        private fun parsePastRunsJson(raw: String): List<SpeedrunPastRunRecord> {
            return try {
                val arr = JSONArray(raw)
                val list = mutableListOf<SpeedrunPastRunRecord>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val statusName = obj.optString("integrityStatus", SpeedrunIntegrityStatus.VERIFIED_CLEAN.name)
                    val integrity = SpeedrunIntegrityStatus.entries.firstOrNull { it.name == statusName }
                        ?: SpeedrunIntegrityStatus.VERIFIED_CLEAN
                    list.add(
                        SpeedrunPastRunRecord(
                            id = obj.optString("id", "sr_$i"),
                            gamePackage = obj.optString("gamePackage", "global.speedrun"),
                            gameName = obj.optString("gameName", "General Speedrun"),
                            elapsedMs = obj.optLong("elapsedMs", 0L),
                            formattedTime = obj.optString("formattedTime", "00:00.000"),
                            splitsCount = obj.optInt("splitsCount", 0),
                            integrityStatus = integrity,
                            verificationHash = obj.optString("verificationHash", "SR-00000000"),
                            certificateCode = obj.optString("certificateCode", ""),
                            timestampMs = obj.optLong("timestampMs", 0L),
                            formattedDate = obj.optString("formattedDate", "")
                        )
                    )
                }
                list
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun resetRun(): SpeedrunTimerState {
            val current = _state.value
            val resetState = current.copy(
                isRunning = false,
                isFinished = false,
                elapsedMs = 0L,
                formattedTime = "00:00.000",
                splits = emptyList(),
                integrityStatus = SpeedrunIntegrityStatus.VERIFIED_CLEAN,
                liveVerificationHash = "SR-READY000",
                frameHeartbeatTick = 0L,
                runCertificateCode = null
            )
            _state.value = resetState
            return resetState
        }

        fun formatSpeedrunDuration(elapsedMs: Long): String {
            val safeMs = elapsedMs.coerceAtLeast(0L)
            val totalSeconds = safeMs / 1000L
            val millis = safeMs % 1000L
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L
            return if (hours > 0L) {
                String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
            } else {
                String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, millis)
            }
        }

        fun computeVerificationHash(
            seed: String,
            elapsedSecondsBucket: Long,
            splitsCount: Int,
            lastSplitMs: Long,
            integrity: SpeedrunIntegrityStatus
        ): String {
            return try {
                val payload = "$seed|$elapsedSecondsBucket|$splitsCount|$lastSplitMs|${integrity.name}"
                val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
                val hex = digest.take(4).joinToString("") { String.format(Locale.US, "%02X", it) }
                "SR-$hex"
            } catch (_: Exception) {
                "SR-8F4A2C1E"
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var rootView: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var badgeTextView: TextView? = null
    private var gameTagTextView: TextView? = null
    private var sizeCycleBtnView: TextView? = null
    private var watermarkTextView: TextView? = null
    private var timerTextView: TextView? = null
    private var splitsContainer: LinearLayout? = null
    private var startOrFinishBtn: TextView? = null
    private var splitBtn: TextView? = null
    private var resetBtn: TextView? = null

    private val frameRunnable = object : Runnable {
        override fun run() {
            val s = tickTimer()
            updateOverlayUi(s)
            if (_state.value.isEnabled && rootView != null) {
                mainHandler.postDelayed(this, 33L)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastContextRef = applicationContext
        when (intent?.action) {
            ACTION_STOP_OVERLAY -> {
                removeOverlayView()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH_SIZE -> {
                applyOverlayScaleToViews(_state.value)
                updateOverlayUi(_state.value)
            }
            ACTION_START_OVERLAY, null -> {
                val pkg = intent?.getStringExtra(EXTRA_GAME_PKG).orEmpty()
                val name = intent?.getStringExtra(EXTRA_GAME_NAME).orEmpty()
                if (pkg.isNotBlank() || name.isNotBlank()) {
                    _state.value = _state.value.copy(
                        targetGamePackage = pkg.ifBlank { _state.value.targetGamePackage },
                        targetGameName = name.ifBlank { _state.value.targetGameName }
                    )
                }
                if (Settings.canDrawOverlays(this)) {
                    showOrUpdateOverlayView()
                } else {
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        removeOverlayView()
        super.onDestroy()
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun showOrUpdateOverlayView() {
        if (rootView != null) {
            applyOverlayScaleToViews(_state.value)
            updateOverlayUi(_state.value)
            return
        }
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm
        val density = resources.displayMetrics.density
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val hPad = (12 * density).toInt()
            val vPad = (8 * density).toInt()
            setPadding(hPad, vPad, hPad, vPad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f * density
                setColor(Color.parseColor("#EB0A101D"))
                setStroke((1.5f * density).toInt().coerceAtLeast(1), Color.parseColor("#FF00E676"))
            }
        }

        // Top Security Header Row: Anti-Cheat Status + Resize Button (⤢) + Close Button
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val securityTv = TextView(this).apply {
            text = "🛡️ VERIFIED ANTI-CHEAT"
            setTextColor(Color.parseColor("#FF00E676"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        badgeTextView = securityTv

        val sizeBtn = TextView(this).apply {
            text = " ⤢ ${_state.value.sizePreset.labelEn.uppercase()} "
            setTextColor(Color.parseColor("#FF38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener {
                val updated = cycleSizePreset(applicationContext)
                applyOverlayScaleToViews(updated)
                updateOverlayUi(updated)
            }
        }
        sizeCycleBtnView = sizeBtn

        val closeTv = TextView(this).apply {
            text = " ✕ "
            setTextColor(Color.parseColor("#FF94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener {
                setEnabled(applicationContext, false)
            }
        }

        topRow.addView(securityTv)
        topRow.addView(sizeBtn)
        topRow.addView(closeTv)
        container.addView(topRow)

        // Game Name Row
        val gameTv = TextView(this).apply {
            text = "🎮 ${_state.value.targetGameName}"
            setTextColor(Color.parseColor("#FFE2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.DEFAULT_BOLD
        }
        gameTagTextView = gameTv
        container.addView(gameTv)

        // Rolling SHA-256 Watermark + Monotonic Frame Heartbeat Row
        val watermarkTv = TextView(this).apply {
            text = "SR-READY000 • Tick #00000 • NO-PAUSE LOCK"
            setTextColor(Color.parseColor("#FF38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f)
            typeface = Typeface.MONOSPACE
        }
        watermarkTextView = watermarkTv
        container.addView(watermarkTv)

        // Main Millisecond Timer Display
        val mainTimerTv = TextView(this).apply {
            text = "00:00.000"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(0, (2 * density).toInt(), 0, (2 * density).toInt())
        }
        timerTextView = mainTimerTv
        container.addView(mainTimerTv)

        // Splits list container
        val splitsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        splitsContainer = splitsLayout
        container.addView(splitsLayout)

        // Controls Row: START/FINISH • SPLIT • RESET • Corner Resize Handle (◢)
        val controlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (4 * density).toInt(), 0, 0)
        }

        fun styleActionChip(tv: TextView, bgHex: String, textHex: String = "#FFFFFFFF") {
            val padH = (8 * density).toInt()
            val padV = (4 * density).toInt()
            tv.setPadding(padH, padV, padH, padV)
            tv.setTextColor(Color.parseColor(textHex))
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            tv.typeface = Typeface.DEFAULT_BOLD
            tv.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10f * density
                setColor(Color.parseColor(bgHex))
            }
        }

        val actionStartFinish = TextView(this).apply {
            text = AppStrings.trText("START RUN", "INICIAR")
            styleActionChip(this, "#FF00E676", "#FF04141F")
            setOnClickListener {
                val cur = _state.value
                if (!cur.isRunning) {
                    startNewVerifiedRun()
                } else {
                    val done = finishRun()
                    done.runCertificateCode?.let { cert ->
                        try {
                            val cb = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cb?.setPrimaryClip(ClipData.newPlainText("Speedrun Certificate", cert))
                            Toast.makeText(
                                applicationContext,
                                AppStrings.trText(
                                    "Run Verified & Saved! Certificate copied: $cert",
                                    "¡Carrera verificada y guardada! Certificado: $cert"
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (_: Exception) {}
                    }
                }
                updateOverlayUi(_state.value)
            }
        }
        startOrFinishBtn = actionStartFinish

        val actionSplit = TextView(this).apply {
            text = AppStrings.trText("SPLIT", "SPLIT")
            styleActionChip(this, "#FF0EA5E9", "#FF04141F")
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = (6 * density).toInt()
            }
            this.layoutParams = lp
            setOnClickListener {
                if (_state.value.isRunning) {
                    recordSplit()
                    updateOverlayUi(_state.value)
                }
            }
        }
        splitBtn = actionSplit

        val actionReset = TextView(this).apply {
            text = AppStrings.trText("RESET", "REINICIAR")
            styleActionChip(this, "#FF334155", "#FFE2E8F0")
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = (6 * density).toInt()
            }
            this.layoutParams = lp
            setOnClickListener {
                resetRun()
                updateOverlayUi(_state.value)
            }
        }
        resetBtn = actionReset

        // Bottom-right drag-to-resize corner handle (◢)
        val resizeCornerHandle = TextView(this).apply {
            text = " ◢"
            setTextColor(Color.parseColor("#FF38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding((6 * density).toInt(), (2 * density).toInt(), (2 * density).toInt(), (2 * density).toInt())
            var startScale = 1.0f
            var downRawX = 0f
            setOnTouchListener { _, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startScale = _state.value.overlayScale
                        downRawX = ev.rawX
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaDp = (ev.rawX - downRawX) / density
                        val nextScale = (startScale + (deltaDp / 180f)).coerceIn(0.70f, 1.45f)
                        val st = setCustomOverlayScale(applicationContext, nextScale)
                        applyOverlayScaleToViews(st)
                        true
                    }
                    else -> true
                }
            }
        }

        controlsRow.addView(actionStartFinish)
        controlsRow.addView(actionSplit)
        controlsRow.addView(actionReset)
        controlsRow.addView(resizeCornerHandle)
        container.addView(controlsRow)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val savedX = prefs.getInt(KEY_POS_X, (16 * density).toInt())
        val savedY = prefs.getInt(KEY_POS_Y, (96 * density).toInt())

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }

        var dragInitialX = 0
        var dragInitialY = 0
        var touchStartX = 0f
        var touchStartY = 0f

        container.setOnTouchListener { _, event ->
            val lp = layoutParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragInitialX = lp.x
                    dragInitialY = lp.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    lp.x = (dragInitialX + dx).coerceAtLeast(0)
                    lp.y = (dragInitialY + dy).coerceAtLeast(0)
                    try {
                        wm.updateViewLayout(container, lp)
                    } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    prefs.edit()
                        .putInt(KEY_POS_X, lp.x)
                        .putInt(KEY_POS_Y, lp.y)
                        .apply()
                    true
                }
                else -> false
            }
        }

        try {
            wm.addView(container, params)
            rootView = container
            layoutParams = params
            applyOverlayScaleToViews(_state.value)
            updateOverlayUi(_state.value)
            mainHandler.removeCallbacks(frameRunnable)
            mainHandler.post(frameRunnable)
        } catch (_: Exception) {}
    }

    @SuppressLint("SetTextI18n")
    private fun applyOverlayScaleToViews(state: SpeedrunTimerState) {
        val density = resources.displayMetrics.density
        val scale = state.overlayScale.coerceIn(0.70f, 1.45f)
        val container = rootView ?: return
        val hPad = (12 * density * scale).toInt()
        val vPad = (8 * density * scale).toInt()
        container.setPadding(hPad, vPad, hPad, vPad)

        badgeTextView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f * scale)
        sizeCycleBtnView?.text = " ⤢ ${(scale * 100).toInt()}% "
        sizeCycleBtnView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f * scale)
        gameTagTextView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f * scale)
        watermarkTextView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f * scale)
        timerTextView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f * scale)
        startOrFinishBtn?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f * scale)
        splitBtn?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f * scale)
        resetBtn?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f * scale)

        layoutParams?.let { lp ->
            try {
                windowManager?.updateViewLayout(container, lp)
            } catch (_: Exception) {}
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateOverlayUi(state: SpeedrunTimerState) {
        val density = resources.displayMetrics.density
        val scale = state.overlayScale.coerceIn(0.70f, 1.45f)
        timerTextView?.text = state.formattedTime
        gameTagTextView?.text = "🎮 ${state.targetGameName.ifBlank { "General Speedrun" }}"
        val isClean = state.integrityStatus.isClean
        badgeTextView?.text = AppStrings.trText(
            state.integrityStatus.badgeEn,
            state.integrityStatus.badgeEs
        )
        badgeTextView?.setTextColor(
            if (isClean) Color.parseColor("#FF00E676") else Color.parseColor("#FFEF4444")
        )
        watermarkTextView?.text = String.format(
            Locale.US,
            "%s • Tick #%05d • %s",
            state.liveVerificationHash,
            state.frameHeartbeatTick % 100000L,
            if (state.isFinished) "FINAL LOCKED" else "NO-PAUSE"
        )
        startOrFinishBtn?.text = when {
            state.isRunning -> AppStrings.trText("FINISH", "FINALIZAR")
            state.isFinished -> AppStrings.trText("NEW RUN", "NUEVA")
            else -> AppStrings.trText("START RUN", "INICIAR")
        }

        splitsContainer?.let { container ->
            container.removeAllViews()
            state.splits.takeLast(state.sizePreset.maxVisibleSplits).forEach { sp ->
                val rowTv = TextView(this).apply {
                    text = "${sp.label}: ${sp.formattedSplitTime} (${sp.formattedSegmentDelta}) [${sp.splitVerificationHash.takeLast(4)}]"
                    setTextColor(Color.parseColor("#FFCBD5E1"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f * scale)
                    typeface = Typeface.MONOSPACE
                    setPadding(0, (1 * density).toInt(), 0, (1 * density).toInt())
                }
                container.addView(rowTv)
            }
        }
    }

    private fun removeOverlayView() {
        mainHandler.removeCallbacks(frameRunnable)
        val view = rootView
        val wm = windowManager
        rootView = null
        windowManager = null
        if (view != null && wm != null) {
            try {
                wm.removeView(view)
            } catch (_: Exception) {}
        }
    }
}
