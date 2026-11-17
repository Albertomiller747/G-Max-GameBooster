package com.example.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class BlackoutPresetType(
    val id: String,
    val titleEn: String,
    val titleEs: String,
    val boxLabel: String,
    val defaultWidthDp: Int,
    val defaultHeightDp: Int,
    val defaultX: Int,
    val defaultY: Int
) {
    MINIMAP(
        id = "minimap",
        titleEn = "+ Minimap",
        titleEs = "+ Minimapa",
        boxLabel = "MINIMAP SHIELD",
        defaultWidthDp = 140,
        defaultHeightDp = 140,
        defaultX = 24,
        defaultY = 32
    ),
    CHAT(
        id = "chat",
        titleEn = "+ Chat Cover",
        titleEs = "+ Cubrir Chat",
        boxLabel = "CHAT COVER",
        defaultWidthDp = 220,
        defaultHeightDp = 96,
        defaultX = 24,
        defaultY = 260
    ),
    USERNAME(
        id = "username",
        titleEn = "+ Username",
        titleEs = "+ Nombre ID",
        boxLabel = "ID / TAG SHIELD",
        defaultWidthDp = 180,
        defaultHeightDp = 44,
        defaultX = 190,
        defaultY = 28
    ),
    CUSTOM(
        id = "custom",
        titleEn = "+ Custom Box",
        titleEs = "+ Caja Libre",
        boxLabel = "PRIVACY SHIELD",
        defaultWidthDp = 160,
        defaultHeightDp = 96,
        defaultX = 140,
        defaultY = 160
    )
}

data class BlackoutBoxConfig(
    val id: String = UUID.randomUUID().toString().take(8),
    val presetId: String = BlackoutPresetType.CUSTOM.id,
    val label: String = "PRIVACY SHIELD",
    val x: Int = 100,
    val y: Int = 120,
    val widthDp: Int = 150,
    val heightDp: Int = 90
)

data class StreamerBlackoutState(
    val isEnabled: Boolean = false,
    val isManualTouchLocked: Boolean = false,
    val isRecordingAutoLocked: Boolean = false,
    val isPurePitchBlack: Boolean = false,
    val boxes: List<BlackoutBoxConfig> = emptyList()
) {
    val isEffectiveTouchLocked: Boolean
        get() = isManualTouchLocked || isRecordingAutoLocked
}

/**
 * Floating Streamer Blackout Boxes Service.
 *
 * Spawns opaque black privacy shields that users can drag and resize over in-game chat,
 * usernames, lobby codes, or minimaps. When screen recording starts (or when manual touch-lock
 * is enabled), all boxes automatically switch to FLAG_NOT_TOUCHABLE so 100% of gameplay touches
 * pass through while the sensitive areas remain physically covered in the recorded video.
 */
class StreamerBlackoutOverlayService : Service() {

    companion object {
        const val ACTION_SYNC = "com.example.blackout.ACTION_SYNC"
        const val ACTION_STOP = "com.example.blackout.ACTION_STOP"

        private const val PREFS_NAME = "gmax_streamer_blackout_prefs"
        private const val KEY_ENABLED = "blackout_enabled"
        private const val KEY_MANUAL_LOCK = "blackout_manual_lock"
        private const val KEY_PURE_BLACK = "blackout_pure_pitch_black"
        private const val KEY_BOXES_JSON = "blackout_boxes_json"

        const val MIN_BOX_WIDTH_DP = 56
        const val MIN_BOX_HEIGHT_DP = 32
        const val MAX_BOX_WIDTH_DP = 520
        const val MAX_BOX_HEIGHT_DP = 420
        const val MAX_BOX_COUNT = 8

        private val _state = MutableStateFlow(StreamerBlackoutState())
        val state: StateFlow<StreamerBlackoutState> = _state.asStateFlow()

        fun loadState(context: Context): StreamerBlackoutState {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean(KEY_ENABLED, false)
            val manualLock = prefs.getBoolean(KEY_MANUAL_LOCK, false)
            val pureBlack = prefs.getBoolean(KEY_PURE_BLACK, false)
            val rawJson = prefs.getString(KEY_BOXES_JSON, null)
            val boxes = parseBoxesJson(rawJson)
            val loaded = StreamerBlackoutState(
                isEnabled = enabled,
                isManualTouchLocked = manualLock,
                isRecordingAutoLocked = ScreenRecordingService.isRecording.value,
                isPurePitchBlack = pureBlack,
                boxes = boxes
            )
            _state.value = loaded
            return loaded
        }

        private fun persistState(context: Context, newState: StreamerBlackoutState) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val arr = JSONArray()
            newState.boxes.forEach { box ->
                val obj = JSONObject().apply {
                    put("id", box.id)
                    put("presetId", box.presetId)
                    put("label", box.label)
                    put("x", box.x)
                    put("y", box.y)
                    put("widthDp", box.widthDp.coerceIn(MIN_BOX_WIDTH_DP, MAX_BOX_WIDTH_DP))
                    put("heightDp", box.heightDp.coerceIn(MIN_BOX_HEIGHT_DP, MAX_BOX_HEIGHT_DP))
                }
                arr.put(obj)
            }
            prefs.edit()
                .putBoolean(KEY_ENABLED, newState.isEnabled)
                .putBoolean(KEY_MANUAL_LOCK, newState.isManualTouchLocked)
                .putBoolean(KEY_PURE_BLACK, newState.isPurePitchBlack)
                .putString(KEY_BOXES_JSON, arr.toString())
                .apply()
            _state.value = newState.copy(
                isRecordingAutoLocked = ScreenRecordingService.isRecording.value
            )
        }

        fun parseBoxesJson(rawJson: String?): List<BlackoutBoxConfig> {
            if (rawJson.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(rawJson)
                val list = mutableListOf<BlackoutBoxConfig>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    list.add(
                        BlackoutBoxConfig(
                            id = obj.optString("id", UUID.randomUUID().toString().take(8)),
                            presetId = obj.optString("presetId", BlackoutPresetType.CUSTOM.id),
                            label = obj.optString("label", "PRIVACY SHIELD"),
                            x = obj.optInt("x", 100),
                            y = obj.optInt("y", 120),
                            widthDp = obj.optInt("widthDp", 150).coerceIn(MIN_BOX_WIDTH_DP, MAX_BOX_WIDTH_DP),
                            heightDp = obj.optInt("heightDp", 90).coerceIn(MIN_BOX_HEIGHT_DP, MAX_BOX_HEIGHT_DP)
                        )
                    )
                }
                list
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun setEnabled(context: Context, enabled: Boolean): StreamerBlackoutState {
            val current = loadState(context)
            val boxes = if (enabled && current.boxes.isEmpty()) {
                // Spawn a default Minimap shield if enabling with 0 boxes
                listOf(createPresetBox(BlackoutPresetType.MINIMAP, 0))
            } else {
                current.boxes
            }
            val updated = current.copy(isEnabled = enabled, boxes = boxes)
            persistState(context, updated)
            syncService(context)
            return _state.value
        }

        fun setManualTouchLocked(context: Context, locked: Boolean): StreamerBlackoutState {
            val current = loadState(context)
            val updated = current.copy(isManualTouchLocked = locked)
            persistState(context, updated)
            syncService(context)
            return _state.value
        }

        fun setPurePitchBlack(context: Context, pureBlack: Boolean): StreamerBlackoutState {
            val current = loadState(context)
            val updated = current.copy(isPurePitchBlack = pureBlack)
            persistState(context, updated)
            syncService(context)
            return _state.value
        }

        fun spawnPresetBox(context: Context, preset: BlackoutPresetType): StreamerBlackoutState {
            val current = loadState(context)
            if (current.boxes.size >= MAX_BOX_COUNT) return current
            val newBox = createPresetBox(preset, current.boxes.size)
            val updated = current.copy(
                isEnabled = true,
                boxes = current.boxes + newBox
            )
            persistState(context, updated)
            syncService(context)
            return _state.value
        }

        fun updateBoxBounds(
            context: Context,
            boxId: String,
            newX: Int,
            newY: Int,
            newWidthDp: Int,
            newHeightDp: Int
        ): StreamerBlackoutState {
            val current = loadState(context)
            val updatedBoxes = current.boxes.map { box ->
                if (box.id == boxId) {
                    box.copy(
                        x = newX.coerceAtLeast(0),
                        y = newY.coerceAtLeast(0),
                        widthDp = newWidthDp.coerceIn(MIN_BOX_WIDTH_DP, MAX_BOX_WIDTH_DP),
                        heightDp = newHeightDp.coerceIn(MIN_BOX_HEIGHT_DP, MAX_BOX_HEIGHT_DP)
                    )
                } else {
                    box
                }
            }
            val updated = current.copy(boxes = updatedBoxes)
            persistState(context, updated)
            return _state.value
        }

        fun removeBox(context: Context, boxId: String): StreamerBlackoutState {
            val current = loadState(context)
            val remaining = current.boxes.filterNot { it.id == boxId }
            val updated = current.copy(
                isEnabled = if (remaining.isEmpty()) false else current.isEnabled,
                boxes = remaining
            )
            persistState(context, updated)
            syncService(context)
            return _state.value
        }

        fun clearAllBoxes(context: Context): StreamerBlackoutState {
            val current = loadState(context)
            val updated = current.copy(
                isEnabled = false,
                isManualTouchLocked = false,
                boxes = emptyList()
            )
            persistState(context, updated)
            syncService(context)
            return _state.value
        }

        private fun createPresetBox(preset: BlackoutPresetType, indexOffset: Int): BlackoutBoxConfig {
            val cascadeOffset = (indexOffset % 5) * 28
            return BlackoutBoxConfig(
                id = UUID.randomUUID().toString().take(8),
                presetId = preset.id,
                label = preset.boxLabel,
                x = preset.defaultX + cascadeOffset,
                y = preset.defaultY + cascadeOffset,
                widthDp = preset.defaultWidthDp,
                heightDp = preset.defaultHeightDp
            )
        }

        fun syncService(context: Context) {
            try {
                val st = _state.value
                if (!Settings.canDrawOverlays(context)) return
                val intent = Intent(context, StreamerBlackoutOverlayService::class.java).apply {
                    action = if (st.isEnabled && st.boxes.isNotEmpty()) ACTION_SYNC else ACTION_STOP
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }

    private var windowManager: WindowManager? = null
    private val activeViews = LinkedHashMap<String, Pair<BlackoutBoxOverlayView, WindowManager.LayoutParams>>()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + com.example.util.AppCrashGuard.coroutineExceptionHandler)
    private var recordingWatchJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        loadState(this)
        observeRecordingAutoLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_SYNC
        if (action == ACTION_STOP || !Settings.canDrawOverlays(this)) {
            removeAllWindows()
            stopSelf()
            return START_NOT_STICKY
        }
        val current = loadState(this)
        if (!current.isEnabled || current.boxes.isEmpty()) {
            removeAllWindows()
            stopSelf()
            return START_NOT_STICKY
        }
        reconcileOverlayWindows(current)
        return START_STICKY
    }

    private fun observeRecordingAutoLock() {
        recordingWatchJob?.cancel()
        recordingWatchJob = serviceScope.launch {
            ScreenRecordingService.isRecording.collectLatest { recordingActive ->
                val current = _state.value.copy(isRecordingAutoLocked = recordingActive)
                _state.value = current
                if (current.isEnabled && current.boxes.isNotEmpty()) {
                    reconcileOverlayWindows(current)
                }
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).roundToInt().coerceAtLeast(1)
    }

    private fun pxToDp(px: Int): Int {
        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        return (px / density).roundToInt()
    }

    private fun buildLayoutParams(box: BlackoutBoxConfig, isTouchLocked: Boolean): WindowManager.LayoutParams {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val flags = if (isTouchLocked) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }

        return WindowManager.LayoutParams(
            dpToPx(box.widthDp),
            dpToPx(box.heightDp),
            overlayType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = box.x
            y = box.y
        }
    }

    private fun reconcileOverlayWindows(state: StreamerBlackoutState) {
        val wm = windowManager ?: return
        if (!Settings.canDrawOverlays(this)) return

        val desiredIds = state.boxes.map { it.id }.toSet()
        val existingIds = activeViews.keys.toList()

        // Remove deleted boxes
        for (id in existingIds) {
            if (id !in desiredIds) {
                activeViews.remove(id)?.first?.let { view ->
                    try {
                        wm.removeView(view)
                    } catch (_: Exception) {}
                }
            }
        }

        val isLocked = state.isEffectiveTouchLocked

        // Add or update active boxes
        for (box in state.boxes) {
            val existing = activeViews[box.id]
            if (existing == null) {
                val lp = buildLayoutParams(box, isLocked)
                val view = BlackoutBoxOverlayView(
                    context = this,
                    boxConfig = box,
                    isTouchLocked = isLocked,
                    isPurePitchBlack = state.isPurePitchBlack,
                    onMoveAndResize = { newX, newY, newWidthPx, newHeightPx, persist ->
                        lp.x = newX
                        lp.y = newY
                        lp.width = newWidthPx
                        lp.height = newHeightPx
                        try {
                            wm.updateViewLayout(existingViewOrSelf(box.id), lp)
                        } catch (_: Exception) {}
                        if (persist) {
                            updateBoxBounds(
                                context = this@StreamerBlackoutOverlayService,
                                boxId = box.id,
                                newX = newX,
                                newY = newY,
                                newWidthDp = pxToDp(newWidthPx),
                                newHeightDp = pxToDp(newHeightPx)
                            )
                        }
                    },
                    onDeleteClicked = {
                        removeBox(this@StreamerBlackoutOverlayService, box.id)
                    }
                )
                try {
                    wm.addView(view, lp)
                    activeViews[box.id] = Pair(view, lp)
                } catch (_: Exception) {}
            } else {
                val (view, lp) = existing
                val desiredFlags = if (isLocked) {
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                } else {
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                }
                val newW = dpToPx(box.widthDp)
                val newH = dpToPx(box.heightDp)
                val layoutChanged = lp.flags != desiredFlags ||
                    lp.width != newW ||
                    lp.height != newH ||
                    lp.x != box.x ||
                    lp.y != box.y

                view.updateVisualState(
                    boxConfig = box,
                    isTouchLocked = isLocked,
                    isPurePitchBlack = state.isPurePitchBlack
                )

                if (layoutChanged) {
                    lp.flags = desiredFlags
                    lp.width = newW
                    lp.height = newH
                    lp.x = box.x
                    lp.y = box.y
                    try {
                        wm.updateViewLayout(view, lp)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun existingViewOrSelf(boxId: String): View? = activeViews[boxId]?.first

    private fun removeAllWindows() {
        val wm = windowManager
        for ((_, pair) in activeViews) {
            try {
                wm?.removeView(pair.first)
            } catch (_: Exception) {}
        }
        activeViews.clear()
    }

    override fun onDestroy() {
        recordingWatchJob?.cancel()
        serviceScope.cancel()
        removeAllWindows()
        super.onDestroy()
    }
}

/**
 * Custom View representing a single opaque Streamer Blackout Box.
 * Supports center drag-to-move, bottom-right corner drag-to-resize, top-right [×] close button
 * when unlocked, and 100% opaque privacy coverage when locked or recording.
 */
@SuppressLint("ViewConstructor")
private class BlackoutBoxOverlayView(
    context: Context,
    private var boxConfig: BlackoutBoxConfig,
    private var isTouchLocked: Boolean,
    private var isPurePitchBlack: Boolean,
    private val onMoveAndResize: (newX: Int, newY: Int, newWidthPx: Int, newHeightPx: Int, persist: Boolean) -> Unit,
    private val onDeleteClicked: () -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.parseColor("#FF4D6D")
        pathEffect = DashPathEffect(floatArrayOf(6f * density, 4f * density), 0f)
    }

    private val lockedSubtleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = Color.parseColor("#1E293B")
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#00D2FF")
    }

    private val closeBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FF4D6D")
    }

    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E2E8F0")
        textSize = 10f * density
        isFakeBoldText = true
    }

    private val stealthWatermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#334155")
        textSize = 9.5f * density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val closeCrossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2f * density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val rectBuffer = RectF()

    private var interactionMode = MODE_NONE
    private var downRawX = 0f
    private var downRawY = 0f
    private var startWinX = 0
    private var startWinY = 0
    private var startWinW = 0
    private var startWinH = 0

    companion object {
        private const val MODE_NONE = 0
        private const val MODE_DRAG = 1
        private const val MODE_RESIZE = 2
        private const val MODE_DELETE = 3
    }

    fun updateVisualState(
        boxConfig: BlackoutBoxConfig,
        isTouchLocked: Boolean,
        isPurePitchBlack: Boolean
    ) {
        this.boxConfig = boxConfig
        this.isTouchLocked = isTouchLocked
        this.isPurePitchBlack = isPurePitchBlack
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cornerRadius = if (isPurePitchBlack && isTouchLocked) 2f * density else 8f * density

        rectBuffer.set(0f, 0f, w, h)
        fillPaint.color = if (isPurePitchBlack) Color.BLACK else Color.parseColor("#07090E")
        canvas.drawRoundRect(rectBuffer, cornerRadius, cornerRadius, fillPaint)

        if (isTouchLocked) {
            if (!isPurePitchBlack) {
                rectBuffer.set(0.5f * density, 0.5f * density, w - 0.5f * density, h - 0.5f * density)
                canvas.drawRoundRect(rectBuffer, cornerRadius, cornerRadius, lockedSubtleBorderPaint)
                if (w >= 90f * density && h >= 34f * density) {
                    val centerY = (h / 2f) - ((stealthWatermarkPaint.descent() + stealthWatermarkPaint.ascent()) / 2f)
                    canvas.drawText("■ ${boxConfig.label}", w / 2f, centerY, stealthWatermarkPaint)
                }
            }
            return
        }

        // Edit Mode (Unlocked): Draw dashed coral border, label badge, close [×] button, and bottom-right resize grip
        val inset = 1.5f * density
        rectBuffer.set(inset, inset, w - inset, h - inset)
        canvas.drawRoundRect(rectBuffer, cornerRadius, cornerRadius, borderPaint)

        // Top-left label text
        val textX = 8f * density
        val textY = 16f * density
        canvas.drawText(boxConfig.label, textX, textY, labelTextPaint)

        val subHintY = h / 2f + 4f * density
        if (h >= 54f * density && w >= 110f * density) {
            canvas.drawText("DRAG • RESIZE ↘", w / 2f, subHintY, stealthWatermarkPaint)
        }

        // Top-right close [×] circle button
        val closeRadius = 10f * density
        val closeCx = w - 14f * density
        val closeCy = 14f * density
        canvas.drawCircle(closeCx, closeCy, closeRadius, closeBadgePaint)
        val crossDelta = 4f * density
        canvas.drawLine(closeCx - crossDelta, closeCy - crossDelta, closeCx + crossDelta, closeCy + crossDelta, closeCrossPaint)
        canvas.drawLine(closeCx + crossDelta, closeCy - crossDelta, closeCx - crossDelta, closeCy + crossDelta, closeCrossPaint)

        // Bottom-right resize handle (diagonal corner triangle / grip)
        val gripSize = 18f * density
        rectBuffer.set(w - gripSize, h - gripSize, w - 2f * density, h - 2f * density)
        canvas.drawRoundRect(rectBuffer, 4f * density, 4f * density, handlePaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isTouchLocked) return false

        val touchZonePx = 28f * density
        val minW = (StreamerBlackoutOverlayService.MIN_BOX_WIDTH_DP * density).roundToInt()
        val minH = (StreamerBlackoutOverlayService.MIN_BOX_HEIGHT_DP * density).roundToInt()
        val maxW = (StreamerBlackoutOverlayService.MAX_BOX_WIDTH_DP * density).roundToInt()
        val maxH = (StreamerBlackoutOverlayService.MAX_BOX_HEIGHT_DP * density).roundToInt()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                startWinX = boxConfig.x
                startWinY = boxConfig.y
                startWinW = width
                startWinH = height

                val localX = event.x
                val localY = event.y

                interactionMode = when {
                    // Top-right close button hit area
                    localX >= width - touchZonePx && localY <= touchZonePx -> MODE_DELETE
                    // Bottom-right resize grip hit area
                    localX >= width - touchZonePx && localY >= height - touchZonePx -> MODE_RESIZE
                    else -> MODE_DRAG
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - downRawX).roundToInt()
                val dy = (event.rawY - downRawY).roundToInt()

                when (interactionMode) {
                    MODE_DRAG -> {
                        val nextX = max(0, startWinX + dx)
                        val nextY = max(0, startWinY + dy)
                        boxConfig = boxConfig.copy(x = nextX, y = nextY)
                        onMoveAndResize(nextX, nextY, startWinW, startWinH, false)
                    }
                    MODE_RESIZE -> {
                        val nextW = min(maxW, max(minW, startWinW + dx))
                        val nextH = min(maxH, max(minH, startWinH + dy))
                        onMoveAndResize(startWinX, startWinY, nextW, nextH, false)
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                when (interactionMode) {
                    MODE_DELETE -> {
                        val dx = kotlin.math.abs(event.rawX - downRawX)
                        val dy = kotlin.math.abs(event.rawY - downRawY)
                        if (dx < touchZonePx && dy < touchZonePx) {
                            onDeleteClicked()
                        }
                    }
                    MODE_DRAG, MODE_RESIZE -> {
                        val dx = (event.rawX - downRawX).roundToInt()
                        val dy = (event.rawY - downRawY).roundToInt()
                        val finalX = if (interactionMode == MODE_DRAG) max(0, startWinX + dx) else startWinX
                        val finalY = if (interactionMode == MODE_DRAG) max(0, startWinY + dy) else startWinY
                        val finalW = if (interactionMode == MODE_RESIZE) min(maxW, max(minW, startWinW + dx)) else width
                        val finalH = if (interactionMode == MODE_RESIZE) min(maxH, max(minH, startWinH + dy)) else height
                        boxConfig = boxConfig.copy(x = finalX, y = finalY)
                        onMoveAndResize(finalX, finalY, finalW, finalH, true)
                    }
                }
                interactionMode = MODE_NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
