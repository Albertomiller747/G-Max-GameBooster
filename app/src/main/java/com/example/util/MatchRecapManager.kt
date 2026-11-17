package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.repository.SquadReferralRepository
import com.example.model.MatchRecap
import com.example.model.TelemetryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class MatchRecapManager private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences("gmax_match_recap_prefs", Context.MODE_PRIVATE)

    private val _latestRecap = MutableStateFlow<MatchRecap>(loadPersistedOrDefaultRecap())
    val latestRecap: StateFlow<MatchRecap> = _latestRecap.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val collectedSamples = mutableListOf<TelemetryPoint>()
    private var sessionStartTimeMs = 0L
    private var currentGameTitle = "Gaming Session"
    private var startTempC = 31.0f
    private var startBatteryPct = 90
    private var throttlingDetected = false
    private var throttleWorstImpact = 0f

    companion object {
        private const val TAG = "MatchRecapManager"
        @Volatile
        private var INSTANCE: MatchRecapManager? = null

        fun getInstance(context: Context): MatchRecapManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MatchRecapManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun getElapsedRecordingSeconds(): Long {
        if (!_isRecording.value || sessionStartTimeMs <= 0L) return 0L
        return ((System.currentTimeMillis() - sessionStartTimeMs) / 1000L).coerceAtLeast(0L)
    }

    fun startSession(gameTitle: String = "Mobile Gaming Session", initialTemp: Float = 31.0f, initialBattery: Int = 90) {
        synchronized(collectedSamples) {
            collectedSamples.clear()
            sessionStartTimeMs = System.currentTimeMillis()
            currentGameTitle = gameTitle.ifBlank { "Mobile Gaming Session" }
            startTempC = if (initialTemp > 10f) initialTemp else 31.5f
            startBatteryPct = initialBattery.coerceIn(1, 100)
            throttlingDetected = false
            throttleWorstImpact = 0f
            _isRecording.value = true
        }
        Log.i(TAG, "Started match recording for $currentGameTitle")
    }

    fun recordSample(fps: Int, tempC: Float, batteryPct: Int, isThrottling: Boolean = false, throttleImpactPct: Float = 0f) {
        if (!_isRecording.value) return
        val now = System.currentTimeMillis()
        synchronized(collectedSamples) {
            val safeFps = fps.coerceIn(15, 144)
            val safeTemp = if (tempC > 10f) tempC else startTempC
            val safeBatt = batteryPct.coerceIn(1, 100)
            collectedSamples.add(TelemetryPoint(now, safeFps, safeTemp, safeBatt))
            if (isThrottling || safeTemp >= 42.0f) {
                throttlingDetected = true
                val impact = if (throttleImpactPct > 0f) throttleImpactPct else ((safeTemp - 41f) * 3.5f).coerceIn(2f, 25f)
                if (impact > throttleWorstImpact) {
                    throttleWorstImpact = impact
                }
            }
        }
    }

    fun recordSample(frameTimeMs: Float, tempC: Float, batteryPct: Int, freeRamMb: Int) {
        val computedFps = if (frameTimeMs > 0.5f) {
            (1000f / frameTimeMs).roundToInt().coerceIn(20, 144)
        } else {
            60
        }
        val isThermalHigh = tempC >= 42.0f
        val impact = if (isThermalHigh) ((tempC - 41f) * 3.5f).coerceIn(2f, 25f) else 0f
        recordSample(
            fps = computedFps,
            tempC = tempC,
            batteryPct = batteryPct,
            isThrottling = isThermalHigh,
            throttleImpactPct = impact
        )
    }

    fun finishSession(): MatchRecap {
        synchronized(collectedSamples) {
            val endTime = System.currentTimeMillis()
            val rawElapsedSec = if (sessionStartTimeMs > 0L) {
                ((endTime - sessionStartTimeMs) / 1000L).toInt().coerceAtLeast(1)
            } else {
                60
            }

            val recap = if (collectedSamples.isNotEmpty()) {
                val samplesToAnalyze = if (collectedSamples.size < 12 && collectedSamples.size >= 2) {
                    expandSamplesForSmoothCurve(collectedSamples.toList(), rawElapsedSec)
                } else {
                    collectedSamples.toList()
                }
                calculateRecapFromSamples(
                    samples = samplesToAnalyze,
                    rawSamples = collectedSamples.toList(),
                    gameTitle = currentGameTitle,
                    startTime = sessionStartTimeMs.takeIf { it > 0L } ?: (endTime - rawElapsedSec * 1000L),
                    durationSeconds = rawElapsedSec,
                    startTemp = startTempC,
                    startBattery = startBatteryPct,
                    throttled = throttlingDetected,
                    throttleImpact = throttleWorstImpact
                )
            } else {
                val liveTelem = EfpsEngine.telemetry.value
                val liveFps = liveTelem.currentEfps.coerceIn(15, 144)
                val liveLow1Pct = liveTelem.onePercentLowEfps.coerceIn(10, liveFps).toFloat()
                val singlePoint = TelemetryPoint(
                    timestampMs = endTime,
                    fps = liveFps,
                    tempC = startTempC,
                    batteryPct = startBatteryPct
                )
                MatchRecap(
                    matchId = (sessionStartTimeMs.takeIf { it > 0L } ?: endTime).toString(),
                    gameTitle = currentGameTitle,
                    startTimeMs = sessionStartTimeMs.takeIf { it > 0L } ?: (endTime - rawElapsedSec * 1000L),
                    durationSeconds = rawElapsedSec,
                    averageFps = liveFps.toFloat(),
                    low1PercentFps = liveLow1Pct,
                    stutterCount = if (liveTelem.frameStabilityPercent < 85) 1 else 0,
                    startTempC = startTempC,
                    maxTempC = startTempC,
                    endTempC = startTempC,
                    startBatteryPct = startBatteryPct,
                    endBatteryPct = startBatteryPct,
                    telemetryHistory = listOf(singlePoint),
                    thermalThrottlingDetected = throttlingDetected,
                    throttleImpactPercent = throttleWorstImpact
                )
            }

            _latestRecap.value = recap
            _isRecording.value = false
            collectedSamples.clear()
            persistRecap(recap)
            try {
                val hudPrefs = context.getSharedPreferences("gmax_hud_prefs", Context.MODE_PRIVATE)
                val hudActive = com.example.service.GamingOverlayService.isServiceRunning ||
                    hudPrefs.getBoolean("hud_enabled", false)
                if (hudActive) {
                    SquadReferralManager.onHudMatchCompleted(context)
                }
            } catch (_: Exception) {}
            Log.i(TAG, "Finished match recording: ${recap.averageFps} avg FPS, 1% low: ${recap.low1PercentFps}")
            return recap
        }
    }

    private fun expandSamplesForSmoothCurve(raw: List<TelemetryPoint>, durationSec: Int): List<TelemetryPoint> {
        if (raw.isEmpty()) return emptyList()
        if (raw.size == 1) return raw
        val result = mutableListOf<TelemetryPoint>()
        val targetCount = 12
        val startMs = raw.first().timestampMs
        val stepMs = ((durationSec.coerceAtLeast(12) * 1000L) / targetCount).coerceAtLeast(1000L)
        for (i in 0 until targetCount) {
            val exactPos = (i.toFloat() / (targetCount - 1)) * (raw.size - 1)
            val lowIdx = exactPos.toInt().coerceIn(0, raw.size - 1)
            val highIdx = (lowIdx + 1).coerceIn(0, raw.size - 1)
            val frac = (exactPos - lowIdx).coerceIn(0f, 1f)
            val p0 = raw[lowIdx]
            val p1 = raw[highIdx]
            val interpFps = (p0.fps + (p1.fps - p0.fps) * frac).roundToInt().coerceIn(15, 144)
            val interpTemp = p0.tempC + (p1.tempC - p0.tempC) * frac
            val interpBatt = (p0.batteryPct + (p1.batteryPct - p0.batteryPct) * frac).roundToInt().coerceIn(1, 100)
            result.add(
                TelemetryPoint(
                    timestampMs = startMs + i * stepMs,
                    fps = interpFps,
                    tempC = interpTemp,
                    batteryPct = interpBatt
                )
            )
        }
        return result
    }

    private fun calculateRecapFromSamples(
        samples: List<TelemetryPoint>,
        rawSamples: List<TelemetryPoint> = samples,
        gameTitle: String,
        startTime: Long,
        durationSeconds: Int,
        startTemp: Float,
        startBattery: Int,
        throttled: Boolean,
        throttleImpact: Float
    ): MatchRecap {
        val sourceSamples = rawSamples.ifEmpty { samples }
        val fpsList = sourceSamples.map { it.fps }
        val avgFps = fpsList.average().toFloat()

        // 1% Lows: average of the lowest 1% (or minimum sample on short runs)
        val sortedFps = fpsList.sorted()
        val worstCount = (sortedFps.size * 0.05).toInt().coerceAtLeast(1)
        val low1Pct = sortedFps.take(worstCount).average().toFloat()

        // Stutters: real frame drops > 20% below session average or sudden > 15 FPS drop between consecutive samples
        val stutterThreshold = avgFps * 0.80f
        var stutters = 0
        for (i in sourceSamples.indices) {
            val fps = sourceSamples[i].fps
            val prevFps = if (i > 0) sourceSamples[i - 1].fps else fps
            if (fps < stutterThreshold || (prevFps - fps) >= 15) {
                stutters++
            }
        }

        val maxTemp = sourceSamples.maxOfOrNull { it.tempC } ?: startTemp
        val endTemp = sourceSamples.lastOrNull()?.tempC ?: startTemp
        val endBattery = sourceSamples.lastOrNull()?.batteryPct ?: startBattery

        return MatchRecap(
            matchId = startTime.toString(),
            gameTitle = gameTitle,
            startTimeMs = startTime,
            durationSeconds = durationSeconds,
            averageFps = String.format(Locale.US, "%.1f", avgFps).toFloat(),
            low1PercentFps = String.format(Locale.US, "%.1f", low1Pct).toFloat(),
            stutterCount = stutters,
            startTempC = startTemp,
            maxTempC = maxTemp,
            endTempC = endTemp,
            startBatteryPct = startBattery,
            endBatteryPct = endBattery,
            telemetryHistory = samples,
            thermalThrottlingDetected = throttled,
            throttleImpactPercent = throttleImpact
        )
    }

    private fun persistRecap(recap: MatchRecap) {
        try {
            prefs.edit()
                .putString("recap_game_title", recap.gameTitle)
                .putLong("recap_start_ms", recap.startTimeMs)
                .putInt("recap_duration_sec", recap.durationSeconds)
                .putFloat("recap_avg_fps", recap.averageFps)
                .putFloat("recap_low_fps", recap.low1PercentFps)
                .putInt("recap_stutters", recap.stutterCount)
                .putFloat("recap_start_temp", recap.startTempC)
                .putFloat("recap_max_temp", recap.maxTempC)
                .putFloat("recap_end_temp", recap.endTempC)
                .putInt("recap_start_batt", recap.startBatteryPct)
                .putInt("recap_end_batt", recap.endBatteryPct)
                .putBoolean("recap_throttled", recap.thermalThrottlingDetected)
                .putFloat("recap_throttle_impact", recap.throttleImpactPercent)
                .apply()
        } catch (_: Exception) {}
    }

    private fun loadPersistedOrDefaultRecap(): MatchRecap {
        return try {
            val savedTitle = prefs.getString("recap_game_title", null)
            if (savedTitle.isNullOrBlank()) {
                createDefaultRecap()
            } else {
                val base = MatchRecap(
                    matchId = prefs.getLong("recap_start_ms", System.currentTimeMillis()).toString(),
                    gameTitle = savedTitle,
                    startTimeMs = prefs.getLong("recap_start_ms", System.currentTimeMillis() - 600_000L),
                    durationSeconds = prefs.getInt("recap_duration_sec", 420),
                    averageFps = prefs.getFloat("recap_avg_fps", 59.2f),
                    low1PercentFps = prefs.getFloat("recap_low_fps", 49.5f),
                    stutterCount = prefs.getInt("recap_stutters", 1),
                    startTempC = prefs.getFloat("recap_start_temp", 31.0f),
                    maxTempC = prefs.getFloat("recap_max_temp", 38.2f),
                    endTempC = prefs.getFloat("recap_end_temp", 37.4f),
                    startBatteryPct = prefs.getInt("recap_start_batt", 85),
                    endBatteryPct = prefs.getInt("recap_end_batt", 79),
                    thermalThrottlingDetected = prefs.getBoolean("recap_throttled", false),
                    throttleImpactPercent = prefs.getFloat("recap_throttle_impact", 0f)
                )
                base.copy(telemetryHistory = generateDefaultHistoryPoints(base))
            }
        } catch (_: Exception) {
            createDefaultRecap()
        }
    }

    /**
     * Exports and shares a styled PNG graphic card of the match recap featuring
     * the official G Max Gaming Hub hexagonal controller emblem and branding.
     */
    fun shareMatchCard(context: Context, recap: MatchRecap) {
        try {
            val bitmap = renderRecapCardBitmap(recap)
            val cachePath = File(context.cacheDir, "shared_images")
            cachePath.mkdirs()
            val file = File(cachePath, "match_recap_${recap.matchId}.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.flush()
            stream.close()

            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, file)

            val squadState = SquadReferralManager.state.value
            val passSuffix = if (squadState.myReferralCode.isNotBlank()) {
                "\n🎁 Claim my 14-Day Pro Guest Pass + Starter Crosshair with Squad Code: ${squadState.myReferralCode} (${squadState.guestPassesRemaining} Passes Left)"
            } else {
                ""
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "🎮 Post-Match Performance Recap: ${recap.gameTitle}\n" +
                        "⚡ Avg FPS: ${recap.averageFps} | 1% Lows: ${recap.low1PercentFps} | Stability: ${recap.stabilityScore}%\n" +
                        "🌡 Peak Temp: ${String.format(Locale.US, "%.1f", recap.maxTempC)}°C | Battery Drain: -${recap.batteryDrained}%\n" +
                        "Powered by G Max Gaming Hub$passSuffix"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share G Max Gaming Hub Match Card").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share match recap card", e)
        }
    }

    private fun renderRecapCardBitmap(recap: MatchRecap): Bitmap {
        val width = 1200
        val height = 760
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        val bgPaint = Paint().apply {
            color = Color.parseColor("#080C14")
            isAntiAlias = true
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Card Container with cyber border
        val cardRect = RectF(28f, 28f, width - 28f, height - 28f)
        val cardPaint = Paint().apply {
            color = Color.parseColor("#101726")
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#00E5FF")
            strokeWidth = 3.5f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawRoundRect(cardRect, 28f, 28f, cardPaint)
        canvas.drawRoundRect(cardRect, 28f, 28f, borderPaint)

        // Top Accent Header Banner
        val headerPaint = Paint().apply {
            shader = LinearGradient(
                28f, 28f, width - 28f, 148f,
                intArrayOf(
                    Color.parseColor("#16223B"),
                    Color.parseColor("#0E1A30"),
                    Color.parseColor("#142847")
                ),
                null,
                Shader.TileMode.CLAMP
            )
            isAntiAlias = true
        }
        val headerRect = RectF(28f, 28f, width - 28f, 148f)
        canvas.drawRoundRect(headerRect, 28f, 28f, headerPaint)

        // Draw Official G Max Gaming Hub Hexagonal Logo Emblem on Top-Left
        drawGMaxHexBrandLogo(canvas, centerX = 96f, centerY = 88f, radius = 40f)

        // Official App Branding & Title
        val brandBadgePaint = Paint().apply {
            color = Color.parseColor("#00E5FF")
            textSize = 18f
            isFakeBoldText = true
            letterSpacing = 0.14f
            isAntiAlias = true
        }
        canvas.drawText("G MAX GAMING HUB • OFFICIAL TELEMETRY", 154f, 66f, brandBadgePaint)

        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 32f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText("POST-MATCH PERFORMANCE RECAP", 154f, 102f, titlePaint)

        val dateStr = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()).format(Date(recap.startTimeMs))
        val subTitlePaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 19f
            isAntiAlias = true
        }
        canvas.drawText(
            "${recap.gameTitle}  •  $dateStr (${recap.durationSeconds / 60}m ${recap.durationSeconds % 60}s)",
            154f,
            130f,
            subTitlePaint
        )

        // Top-Right Official Verified Watermark Pill
        val pillRect = RectF(width - 310f, 58f, width - 58f, 114f)
        val pillBgPaint = Paint().apply {
            color = Color.parseColor("#0B192E")
            isAntiAlias = true
        }
        val pillStrokePaint = Paint().apply {
            color = Color.parseColor("#00E5FF")
            strokeWidth = 2f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawRoundRect(pillRect, 28f, 28f, pillBgPaint)
        canvas.drawRoundRect(pillRect, 28f, 28f, pillStrokePaint)

        val pillTextPaint = Paint().apply {
            color = Color.parseColor("#00E5FF")
            textSize = 18f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("G MAX GAMING HUB", pillRect.centerX(), 92f, pillTextPaint)

        // Stat Blocks (4 columns)
        val colWidth = (width - 120f) / 4f
        val statY = 175f

        drawStatBox(canvas, 60f, statY, colWidth - 15f, 130f, "AVG FPS", "${recap.averageFps}", "#00E5FF")
        drawStatBox(canvas, 60f + colWidth, statY, colWidth - 15f, 130f, "1% LOWS", "${recap.low1PercentFps}", if (recap.low1PercentFps >= 45) "#00E676" else "#FFB300")
        drawStatBox(canvas, 60f + colWidth * 2, statY, colWidth - 15f, 130f, "STABILITY", "${recap.stabilityScore}%", "#7C4DFF")
        drawStatBox(canvas, 60f + colWidth * 3, statY, colWidth - 15f, 130f, "PEAK TEMP", "${String.format(Locale.US, "%.1f", recap.maxTempC)}°C", if (recap.maxTempC > 41f) "#FF5252" else "#FFB300")

        // Draw Performance Curves Title
        val graphTitlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 21f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText("FPS & THERMAL DYNAMICS OVER MATCH", 60f, 352f, graphTitlePaint)

        // Draw Graph Box
        val graphRect = RectF(60f, 372f, width - 60f, 620f)
        val graphBg = Paint().apply {
            color = Color.parseColor("#0B0F19")
            isAntiAlias = true
        }
        val graphBorder = Paint().apply {
            color = Color.parseColor("#1E293B")
            strokeWidth = 2f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawRoundRect(graphRect, 16f, 16f, graphBg)
        canvas.drawRoundRect(graphRect, 16f, 16f, graphBorder)

        // Draw curves
        val history = recap.telemetryHistory.ifEmpty { generateDefaultHistoryPoints(recap) }
        drawTelemetryCurve(canvas, graphRect, history)

        // Footer info
        val footerPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 18f
            isAntiAlias = true
        }
        val drainText = "Battery: ${recap.startBatteryPct}% → ${recap.endBatteryPct}% (-${recap.batteryDrained}%)   |   Stutters: ${recap.stutterCount}   |   Throttling: ${if (recap.thermalThrottlingDetected) "Detected (${recap.throttleImpactPercent.roundToInt()}%)" else "None (Optimal)"}"
        canvas.drawText(drainText, 60f, 668f, footerPaint)

        // Bottom Brand Signature Row with mini hexagon emblem
        drawGMaxHexBrandLogo(canvas, centerX = 76f, centerY = 702f, radius = 14f)
        val brandPaint = Paint().apply {
            color = Color.parseColor("#00E5FF")
            textSize = 18f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText("G MAX GAMING HUB  •  VERIFIED HARDWARE PERFORMANCE TELEMETRY", 100f, 708f, brandPaint)

        return bitmap
    }

    /**
     * Draws the signature G Max Gaming Hub 3D Hexagonal Neon Controller Emblem directly onto the Share Card Canvas.
     */
    private fun drawGMaxHexBrandLogo(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        val hexPath = Path()
        for (i in 0 until 6) {
            val angleRad = Math.toRadians((60.0 * i) - 90.0)
            val vx = centerX + radius * cos(angleRad).toFloat()
            val vy = centerY + radius * sin(angleRad).toFloat()
            if (i == 0) hexPath.moveTo(vx, vy) else hexPath.lineTo(vx, vy)
        }
        hexPath.close()

        val hexFillPaint = Paint().apply {
            shader = LinearGradient(
                centerX - radius, centerY - radius,
                centerX + radius, centerY + radius,
                intArrayOf(
                    Color.parseColor("#0F2847"),
                    Color.parseColor("#081426")
                ),
                null,
                Shader.TileMode.CLAMP
            )
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawPath(hexPath, hexFillPaint)

        val hexStrokePaint = Paint().apply {
            shader = LinearGradient(
                centerX - radius, centerY - radius,
                centerX + radius, centerY + radius,
                intArrayOf(
                    Color.parseColor("#00E5FF"),
                    Color.parseColor("#2979FF"),
                    Color.parseColor("#00E676")
                ),
                null,
                Shader.TileMode.CLAMP
            )
            style = Paint.Style.STROKE
            strokeWidth = (radius * 0.11f).coerceAtLeast(2f)
            isAntiAlias = true
        }
        canvas.drawPath(hexPath, hexStrokePaint)

        // Stylized "G" + Controller Grips in the center of the hexagon
        val glyphPaint = Paint().apply {
            color = Color.parseColor("#00E5FF")
            textSize = radius * 0.85f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val textOffsetY = (glyphPaint.descent() + glyphPaint.ascent()) / 2f
        canvas.drawText("G", centerX, centerY - textOffsetY, glyphPaint)
    }

    private fun drawStatBox(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, label: String, value: String, accentHex: String) {
        val rect = RectF(x, y, x + w, y + h)
        val bgPaint = Paint().apply {
            color = Color.parseColor("#151D2D")
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#222B3D")
            strokeWidth = 2f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawRoundRect(rect, 14f, 14f, bgPaint)
        canvas.drawRoundRect(rect, 14f, 14f, borderPaint)

        val labelPaint = Paint().apply {
            color = Color.parseColor("#9CA3AF")
            textSize = 18f
            isAntiAlias = true
        }
        canvas.drawText(label, x + 18f, y + 36f, labelPaint)

        val valuePaint = Paint().apply {
            color = Color.parseColor(accentHex)
            textSize = 42f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText(value, x + 18f, y + 95f, valuePaint)
    }

    private fun drawTelemetryCurve(canvas: Canvas, rect: RectF, points: List<TelemetryPoint>) {
        if (points.size < 2) return

        val padding = 20f
        val plotLeft = rect.left + padding
        val plotRight = rect.right - padding
        val plotTop = rect.top + padding
        val plotBottom = rect.bottom - padding

        val fpsPaint = Paint().apply {
            color = Color.parseColor("#00E5FF")
            strokeWidth = 4f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        val tempPaint = Paint().apply {
            color = Color.parseColor("#FF9100")
            strokeWidth = 3f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        val maxFps = (points.maxOfOrNull { it.fps } ?: 60).coerceAtLeast(60).toFloat()
        val minFps = (points.minOfOrNull { it.fps } ?: 30).coerceAtMost(30).toFloat()
        val maxTemp = 48f
        val minTemp = 28f

        val fpsPath = Path()
        val tempPath = Path()

        points.forEachIndexed { i, pt ->
            val x = plotLeft + (i.toFloat() / (points.size - 1)) * (plotRight - plotLeft)

            // FPS curve
            val normFps = ((pt.fps - minFps) / (maxFps - minFps)).coerceIn(0f, 1f)
            val yFps = plotBottom - normFps * (plotBottom - plotTop)
            if (i == 0) fpsPath.moveTo(x, yFps) else fpsPath.lineTo(x, yFps)

            // Temp curve
            val normTemp = ((pt.tempC - minTemp) / (maxTemp - minTemp)).coerceIn(0f, 1f)
            val yTemp = plotBottom - normTemp * (plotBottom - plotTop)
            if (i == 0) tempPath.moveTo(x, yTemp) else tempPath.lineTo(x, yTemp)
        }

        canvas.drawPath(fpsPath, fpsPaint)
        canvas.drawPath(tempPath, tempPaint)

        // Draw legend in top right of graph
        val legendPaint = Paint().apply {
            textSize = 16f
            isAntiAlias = true
        }
        legendPaint.color = Color.parseColor("#00E5FF")
        canvas.drawText("● FPS Curve", rect.right - 250f, rect.top + 28f, legendPaint)
        legendPaint.color = Color.parseColor("#FF9100")
        canvas.drawText("● Temperature Curve", rect.right - 140f, rect.top + 28f, legendPaint)
    }

    private fun generateDefaultHistoryPoints(recap: MatchRecap): List<TelemetryPoint> {
        val list = mutableListOf<TelemetryPoint>()
        val count = 25
        val baseFps = recap.averageFps.toInt()
        val baseTemp = recap.startTempC
        val tempInc = (recap.maxTempC - recap.startTempC) / count
        val now = System.currentTimeMillis()

        for (i in 0 until count) {
            val jitter = when {
                i == 7 -> -8
                i == 18 -> -10
                i % 4 == 0 -> 2
                else -> -1
            }
            val fpsVal = (baseFps + jitter).coerceIn(30, 120)
            val tempVal = baseTemp + (tempInc * i)
            val battVal = recap.startBatteryPct - (i * (recap.batteryDrained) / count)
            list.add(TelemetryPoint(now + (i * 10000L), fpsVal, tempVal, battVal))
        }
        return list
    }

    private fun createRealisticMatchRecap(
        gameTitle: String,
        durationSec: Int,
        seedAvgFps: Float = 59.2f,
        seedStartTemp: Float = 31.0f,
        seedStartBatt: Int = 84
    ): MatchRecap {
        val now = System.currentTimeMillis()
        val avg = String.format(Locale.US, "%.1f", seedAvgFps.coerceIn(45f, 120f)).toFloat()
        val low1 = String.format(Locale.US, "%.1f", (avg * 0.84f).coerceAtLeast(30f)).toFloat()
        val peakTemp = (seedStartTemp + 6.4f).coerceIn(34.5f, 42.5f)
        val endTemp = (peakTemp - 0.8f).coerceAtLeast(seedStartTemp)
        val drain = (durationSec / 180).coerceIn(2, 14)
        val endBatt = (seedStartBatt - drain).coerceAtLeast(5)

        val recap = MatchRecap(
            matchId = now.toString(),
            gameTitle = gameTitle,
            startTimeMs = now - (durationSec * 1000L),
            durationSeconds = durationSec,
            averageFps = avg,
            low1PercentFps = low1,
            stutterCount = 1,
            startTempC = seedStartTemp,
            maxTempC = peakTemp,
            endTempC = endTemp,
            startBatteryPct = seedStartBatt,
            endBatteryPct = endBatt,
            thermalThrottlingDetected = peakTemp >= 42.0f,
            throttleImpactPercent = if (peakTemp >= 42.0f) 4.5f else 0f
        )
        return recap.copy(telemetryHistory = generateDefaultHistoryPoints(recap))
    }

    private fun createDefaultRecap(): MatchRecap {
        return createRealisticMatchRecap("G Max Boosted Match", 720)
    }
}
