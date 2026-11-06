package com.example.ui.components

import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.example.model.ControllerGlyphMap
import com.example.model.ControllerOverlayConfig
import com.example.model.ResolvedControllerBrand
import com.example.util.ControllerHealthState
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Shared hardware-accelerated Android Canvas renderer for the real-time Controller Input HUD.
 * Used by both Jetpack Compose (in-app HUD & settings preview) and WindowManager (system-wide overlay over games).
 *
 * Closely replicates the reference DualShock/Gamepad input visualizer:
 * - Sculpted white/light-silver body shell with crimson/accent lower grip caps
 * - Top L2/R2 triggers (with analog fill) and L1/R1 bumpers
 * - Center touchpad, Share/Options pills, speaker grille dots, and Home emblem
 * - Left circular D-Pad well with 4 directional wedges
 * - Right circular Face Button well with PlayStation shapes (△ ○ ✕ □) or Xbox/Nintendo letters
 * - Dual analog thumbsticks that physically translate with live (X, Y) deflection
 */
object ControllerCanvasRenderer {

    private const val COLOR_BODY_SHELL = 0xFFE6E8EC.toInt()
    private const val COLOR_BODY_SHADOW = 0xFFC7CCD4.toInt()
    private const val COLOR_DARK_WELL = 0xFF0D1017.toInt()
    private const val COLOR_BUTTON_IDLE = 0xFF252A34.toInt()
    private const val COLOR_TRIGGER_IDLE = 0xFF7A2028.toInt()
    private const val COLOR_TRIGGER_NEON = 0xFF00F5FF.toInt()
    private const val COLOR_TRIGGER_TEXT_DARK = 0xFF081018.toInt()
    private const val COLOR_PS_LABEL = 0xFF222630.toInt()
    private const val COLOR_XBOX_SHELL = 0xFFECEFF4.toInt()
    private const val COLOR_XBOX_GREEN = 0xFF107C10.toInt()
    private const val COLOR_XBOX_GRIP = 0xFF232832.toInt()
    private const val COLOR_XBOX_GUIDE_STROKE = 0xFF22C55E.toInt()
    private const val COLOR_XBOX_DISH_RING = 0xFF3B4252.toInt()
    private const val COLOR_SWITCH_SHELL = 0xFFD8DCE4.toInt()
    private const val COLOR_SWITCH_STROKE = 0xFFEF4444.toInt()
    private const val COLOR_SWITCH_GRIP = 0xFF1B1F29.toInt()

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val wellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val reusableShellPath = Path()
    private val reusableLeftGripPath = Path()
    private val reusableRightGripPath = Path()
    private val reusableTrianglePath = Path()
    private val l2Rect = RectF()
    private val r2Rect = RectF()
    private val triggerFillRect = RectF()
    private val l1Rect = RectF()
    private val r1Rect = RectF()
    private val auxRect1 = RectF()
    private val auxRect2 = RectF()
    private val auxRect3 = RectF()
    private val dpadRect = RectF()

    private fun withAlpha(color: Int, alphaInt: Int, factor: Float = 1f): Int {
        val a = ((AndroidColor.alpha(color) / 255f) * alphaInt * factor).toInt().coerceIn(0, 255)
        return (a shl 24) or (color and 0x00FFFFFF)
    }

    fun drawController(
        canvas: AndroidCanvas,
        width: Float,
        height: Float,
        state: ControllerHealthState,
        glyphs: ControllerGlyphMap,
        highlightArgb: Long = 0xFFE5252AL,
        opacity: Float = 0.94f
    ) {
        if (width <= 10f || height <= 10f) return

        val alphaInt = (opacity.coerceIn(0.2f, 1.0f) * 255).toInt()

        val highlightColor = withAlpha(highlightArgb.toInt(), alphaInt)
        val dimHighlightColor = withAlpha(highlightArgb.toInt(), alphaInt, 0.72f)
        val bodyShellColor = withAlpha(COLOR_BODY_SHELL, alphaInt)
        val bodyShadowColor = withAlpha(COLOR_BODY_SHADOW, alphaInt)
        val darkWellColor = withAlpha(COLOR_DARK_WELL, alphaInt)
        val buttonIdleColor = withAlpha(COLOR_BUTTON_IDLE, alphaInt)
        val triggerIdleColor = withAlpha(COLOR_TRIGGER_IDLE, alphaInt)

        val pressed = state.pressedButtons
        val isL1 = pressed.contains("L1")
        val isR1 = pressed.contains("R1")
        val ltVal = state.triggers.leftTrigger.coerceAtLeast(if (pressed.contains("L2")) 1f else 0f)
        val rtVal = state.triggers.rightTrigger.coerceAtLeast(if (pressed.contains("R2")) 1f else 0f)
        val isL2 = ltVal > 0.12f || pressed.contains("L2")
        val isR2 = rtVal > 0.12f || pressed.contains("R2")

        val isDpadUp = pressed.contains("DPAD_UP")
        val isDpadDown = pressed.contains("DPAD_DOWN")
        val isDpadLeft = pressed.contains("DPAD_LEFT")
        val isDpadRight = pressed.contains("DPAD_RIGHT")

        val isSouth = pressed.contains("A") || pressed.contains("DPAD_CENTER")
        val isEast = pressed.contains("B")
        val isWest = pressed.contains("X")
        val isNorth = pressed.contains("Y")

        val isSelect = pressed.contains("SELECT") || pressed.contains("BACK")
        val isStart = pressed.contains("START")
        val isHome = pressed.contains("MODE") || pressed.contains("HOME")
        val isL3 = pressed.contains("THUMBL")
        val isR3 = pressed.contains("THUMBR")

        val w = width
        val h = height

        // 1. Draw L2 / R2 Analog Triggers at top with ultra-bright active glow
        l2Rect.set(w * 0.21f, h * 0.03f, w * 0.34f, h * 0.18f)
        r2Rect.set(w * 0.66f, h * 0.03f, w * 0.79f, h * 0.18f)
        val triggerActiveNeon = withAlpha(COLOR_TRIGGER_NEON, alphaInt, 1.0f)

        // L2 Trigger Base & Active Illumination
        accentPaint.color = if (isL2) highlightColor else triggerIdleColor
        canvas.drawRoundRect(l2Rect, w * 0.045f, w * 0.045f, accentPaint)
        if (isL2 || ltVal > 0.05f) {
            val intensity = ltVal.coerceAtLeast(if (isL2) 1f else 0f).coerceIn(0.25f, 1f)
            val fillTop = l2Rect.bottom - (l2Rect.height() * intensity)
            triggerFillRect.set(l2Rect.left, fillTop, l2Rect.right, l2Rect.bottom)
            accentPaint.color = triggerActiveNeon
            canvas.drawRoundRect(triggerFillRect, w * 0.045f, w * 0.045f, accentPaint)
            accentPaint.color = withAlpha(AndroidColor.WHITE, alphaInt, 0.62f * intensity)
            canvas.drawRoundRect(l2Rect, w * 0.045f, w * 0.045f, accentPaint)
            strokePaint.strokeWidth = (w * 0.014f).coerceAtLeast(2.5f)
            strokePaint.color = withAlpha(AndroidColor.WHITE, alphaInt, 0.98f)
            canvas.drawRoundRect(l2Rect, w * 0.045f, w * 0.045f, strokePaint)
        }

        // R2 Trigger Base & Active Illumination
        accentPaint.color = if (isR2) highlightColor else triggerIdleColor
        canvas.drawRoundRect(r2Rect, w * 0.045f, w * 0.045f, accentPaint)
        if (isR2 || rtVal > 0.05f) {
            val intensity = rtVal.coerceAtLeast(if (isR2) 1f else 0f).coerceIn(0.25f, 1f)
            val fillTop = r2Rect.bottom - (r2Rect.height() * intensity)
            triggerFillRect.set(r2Rect.left, fillTop, r2Rect.right, r2Rect.bottom)
            accentPaint.color = triggerActiveNeon
            canvas.drawRoundRect(triggerFillRect, w * 0.045f, w * 0.045f, accentPaint)
            accentPaint.color = withAlpha(AndroidColor.WHITE, alphaInt, 0.62f * intensity)
            canvas.drawRoundRect(r2Rect, w * 0.045f, w * 0.045f, accentPaint)
            strokePaint.strokeWidth = (w * 0.014f).coerceAtLeast(2.5f)
            strokePaint.color = withAlpha(AndroidColor.WHITE, alphaInt, 0.98f)
            canvas.drawRoundRect(r2Rect, w * 0.045f, w * 0.045f, strokePaint)
        }

        textPaint.textSize = h * 0.050f
        textPaint.color = if (isL2) withAlpha(COLOR_TRIGGER_TEXT_DARK, alphaInt, 1f) else withAlpha(AndroidColor.WHITE, alphaInt, 0.78f)
        canvas.drawText(glyphs.leftTrigger, l2Rect.centerX(), l2Rect.centerY() + h * 0.01f, textPaint)
        textPaint.color = if (isR2) withAlpha(COLOR_TRIGGER_TEXT_DARK, alphaInt, 1f) else withAlpha(AndroidColor.WHITE, alphaInt, 0.78f)
        canvas.drawText(glyphs.rightTrigger, r2Rect.centerX(), r2Rect.centerY() + h * 0.01f, textPaint)

        // 2. Draw L1 / R1 Shoulder Bumpers right below L2 / R2
        l1Rect.set(w * 0.20f, h * 0.175f, w * 0.35f, h * 0.235f)
        r1Rect.set(w * 0.65f, h * 0.175f, w * 0.80f, h * 0.235f)

        accentPaint.color = if (isL1) highlightColor else buttonIdleColor
        canvas.drawRoundRect(l1Rect, w * 0.025f, w * 0.025f, accentPaint)
        if (isL1) {
            accentPaint.color = withAlpha(AndroidColor.WHITE, alphaInt, 0.45f)
            canvas.drawRoundRect(l1Rect, w * 0.025f, w * 0.025f, accentPaint)
            strokePaint.strokeWidth = (w * 0.011f).coerceAtLeast(2f)
            strokePaint.color = withAlpha(AndroidColor.WHITE, alphaInt, 0.95f)
            canvas.drawRoundRect(l1Rect, w * 0.025f, w * 0.025f, strokePaint)
        }
        accentPaint.color = if (isR1) highlightColor else buttonIdleColor
        canvas.drawRoundRect(r1Rect, w * 0.025f, w * 0.025f, accentPaint)
        if (isR1) {
            accentPaint.color = withAlpha(AndroidColor.WHITE, alphaInt, 0.45f)
            canvas.drawRoundRect(r1Rect, w * 0.025f, w * 0.025f, accentPaint)
            strokePaint.strokeWidth = (w * 0.011f).coerceAtLeast(2f)
            strokePaint.color = withAlpha(AndroidColor.WHITE, alphaInt, 0.95f)
            canvas.drawRoundRect(r1Rect, w * 0.025f, w * 0.025f, strokePaint)
        }

        textPaint.textSize = h * 0.038f
        textPaint.color = withAlpha(AndroidColor.WHITE, alphaInt, if (isL1) 1f else 0.85f)
        canvas.drawText(glyphs.leftBumper, l1Rect.centerX(), l1Rect.centerY() + h * 0.013f, textPaint)
        textPaint.color = withAlpha(AndroidColor.WHITE, alphaInt, if (isR1) 1f else 0.85f)
        canvas.drawText(glyphs.rightBumper, r1Rect.centerX(), r1Rect.centerY() + h * 0.013f, textPaint)

        // 3. Brand-Specific Controller Body Shell, Grip Handles & Center Bridge
        when (glyphs.brand) {
            ResolvedControllerBrand.PLAYSTATION -> {
                reusableShellPath.reset()
                reusableShellPath.apply {
                    moveTo(w * 0.22f, h * 0.22f)
                    lineTo(w * 0.78f, h * 0.22f)
                    quadTo(w * 0.88f, h * 0.24f, w * 0.90f, h * 0.45f)
                    quadTo(w * 0.92f, h * 0.74f, w * 0.84f, h * 0.91f)
                    quadTo(w * 0.76f, h * 0.98f, w * 0.68f, h * 0.88f)
                    quadTo(w * 0.63f, h * 0.75f, w * 0.60f, h * 0.68f)
                    lineTo(w * 0.40f, h * 0.68f)
                    quadTo(w * 0.37f, h * 0.75f, w * 0.32f, h * 0.88f)
                    quadTo(w * 0.24f, h * 0.98f, w * 0.16f, h * 0.91f)
                    quadTo(w * 0.08f, h * 0.74f, w * 0.10f, h * 0.45f)
                    quadTo(w * 0.12f, h * 0.24f, w * 0.22f, h * 0.22f)
                    close()
                }
                bodyPaint.color = bodyShellColor
                canvas.drawPath(reusableShellPath, bodyPaint)
                strokePaint.color = bodyShadowColor
                strokePaint.strokeWidth = w * 0.008f
                canvas.drawPath(reusableShellPath, strokePaint)

                // Lower Accent Grip Caps
                reusableLeftGripPath.reset()
                reusableLeftGripPath.apply {
                    moveTo(w * 0.105f, h * 0.72f)
                    quadTo(w * 0.20f, h * 0.78f, w * 0.31f, h * 0.86f)
                    quadTo(w * 0.24f, h * 0.97f, w * 0.16f, h * 0.91f)
                    quadTo(w * 0.10f, h * 0.83f, w * 0.105f, h * 0.72f)
                    close()
                }
                reusableRightGripPath.reset()
                reusableRightGripPath.apply {
                    moveTo(w * 0.895f, h * 0.72f)
                    quadTo(w * 0.80f, h * 0.78f, w * 0.69f, h * 0.86f)
                    quadTo(w * 0.76f, h * 0.97f, w * 0.84f, h * 0.91f)
                    quadTo(w * 0.90f, h * 0.83f, w * 0.895f, h * 0.72f)
                    close()
                }
                accentPaint.color = dimHighlightColor
                canvas.drawPath(reusableLeftGripPath, accentPaint)
                canvas.drawPath(reusableRightGripPath, accentPaint)

                // Central Rectangular Touchpad
                auxRect1.set(w * 0.375f, h * 0.23f, w * 0.625f, h * 0.455f)
                accentPaint.color = if (isSelect || isStart) highlightColor else dimHighlightColor
                canvas.drawRoundRect(auxRect1, w * 0.02f, w * 0.02f, accentPaint)
                strokePaint.color = withAlpha(AndroidColor.BLACK, alphaInt, 0.35f)
                strokePaint.strokeWidth = w * 0.005f
                canvas.drawRoundRect(auxRect1, w * 0.02f, w * 0.02f, strokePaint)

                // SHARE & OPTIONS Oval Pills
                auxRect2.set(w * 0.335f, h * 0.265f, w * 0.362f, h * 0.335f)
                auxRect3.set(w * 0.638f, h * 0.265f, w * 0.665f, h * 0.335f)
                accentPaint.color = if (isSelect) highlightColor else buttonIdleColor
                canvas.drawRoundRect(auxRect2, w * 0.015f, w * 0.015f, accentPaint)
                accentPaint.color = if (isStart) highlightColor else buttonIdleColor
                canvas.drawRoundRect(auxRect3, w * 0.015f, w * 0.015f, accentPaint)

                textPaint.color = withAlpha(COLOR_PS_LABEL, alphaInt)
                textPaint.textSize = h * 0.024f
                canvas.drawText(glyphs.selectLabel, auxRect2.centerX(), auxRect2.top - h * 0.008f, textPaint)
                canvas.drawText(glyphs.startLabel, auxRect3.centerX(), auxRect3.top - h * 0.008f, textPaint)

                // Speaker Grille Dots
                wellPaint.color = darkWellColor
                val dotRadius = w * 0.0055f
                for (row in 0..1) {
                    val count = if (row == 0) 5 else 4
                    val startX = w * 0.50f - ((count - 1) * w * 0.018f) / 2f
                    val y = h * (0.485f + row * 0.022f)
                    for (c in 0 until count) {
                        canvas.drawCircle(startX + c * w * 0.018f, y, dotRadius, wellPaint)
                    }
                }

                // PlayStation Home Button at bottom-center
                val homeCx = w * 0.50f
                val homeCy = h * 0.575f
                val homeRadius = w * 0.028f
                wellPaint.color = if (isHome) highlightColor else darkWellColor
                canvas.drawCircle(homeCx, homeCy, homeRadius, wellPaint)
                textPaint.color = withAlpha(AndroidColor.WHITE, alphaInt)
                textPaint.textSize = h * 0.022f
                canvas.drawText("PS", homeCx, homeCy + h * 0.008f, textPaint)
            }

            ResolvedControllerBrand.XBOX -> {
                reusableShellPath.reset()
                reusableShellPath.apply {
                    moveTo(w * 0.19f, h * 0.21f)
                    quadTo(w * 0.50f, h * 0.185f, w * 0.81f, h * 0.21f)
                    quadTo(w * 0.91f, h * 0.25f, w * 0.92f, h * 0.48f)
                    quadTo(w * 0.93f, h * 0.77f, w * 0.82f, h * 0.93f)
                    quadTo(w * 0.73f, h * 0.97f, w * 0.65f, h * 0.84f)
                    quadTo(w * 0.59f, h * 0.72f, w * 0.56f, h * 0.70f)
                    lineTo(w * 0.44f, h * 0.70f)
                    quadTo(w * 0.41f, h * 0.72f, w * 0.35f, h * 0.84f)
                    quadTo(w * 0.27f, h * 0.97f, w * 0.18f, h * 0.93f)
                    quadTo(w * 0.07f, h * 0.77f, w * 0.08f, h * 0.48f)
                    quadTo(w * 0.09f, h * 0.25f, w * 0.19f, h * 0.21f)
                    close()
                }
                bodyPaint.color = withAlpha(COLOR_XBOX_SHELL, alphaInt)
                canvas.drawPath(reusableShellPath, bodyPaint)
                strokePaint.color = withAlpha(COLOR_XBOX_GREEN, alphaInt, 0.65f)
                strokePaint.strokeWidth = w * 0.009f
                canvas.drawPath(reusableShellPath, strokePaint)

                // Xbox Textured Side Grip Panels
                reusableLeftGripPath.reset()
                reusableLeftGripPath.apply {
                    moveTo(w * 0.085f, h * 0.56f)
                    quadTo(w * 0.16f, h * 0.66f, w * 0.28f, h * 0.88f)
                    quadTo(w * 0.22f, h * 0.95f, w * 0.18f, h * 0.93f)
                    quadTo(w * 0.075f, h * 0.77f, w * 0.085f, h * 0.56f)
                    close()
                }
                reusableRightGripPath.reset()
                reusableRightGripPath.apply {
                    moveTo(w * 0.915f, h * 0.56f)
                    quadTo(w * 0.84f, h * 0.66f, w * 0.72f, h * 0.88f)
                    quadTo(w * 0.78f, h * 0.95f, w * 0.82f, h * 0.93f)
                    quadTo(w * 0.925f, h * 0.77f, w * 0.915f, h * 0.56f)
                    close()
                }
                accentPaint.color = withAlpha(COLOR_XBOX_GRIP, alphaInt)
                canvas.drawPath(reusableLeftGripPath, accentPaint)
                canvas.drawPath(reusableRightGripPath, accentPaint)

                // Top-Center Glowing Xbox Guide Button (XB)
                val guideCx = w * 0.50f
                val guideCy = h * 0.305f
                val guideR = w * 0.036f
                wellPaint.color = if (isHome) highlightColor else darkWellColor
                canvas.drawCircle(guideCx, guideCy, guideR, wellPaint)
                strokePaint.color = withAlpha(COLOR_XBOX_GUIDE_STROKE, alphaInt, 0.9f)
                strokePaint.strokeWidth = w * 0.006f
                canvas.drawCircle(guideCx, guideCy, guideR, strokePaint)
                textPaint.color = withAlpha(AndroidColor.WHITE, alphaInt)
                textPaint.textSize = h * 0.025f
                canvas.drawText("XB", guideCx, guideCy + h * 0.009f, textPaint)

                // Xbox VIEW (left) & MENU (right) circular buttons + center Share pill
                val viewCx = w * 0.435f
                val menuCx = w * 0.565f
                val vmCy = h * 0.415f
                val vmR = w * 0.020f
                accentPaint.color = if (isSelect) highlightColor else buttonIdleColor
                canvas.drawCircle(viewCx, vmCy, vmR, accentPaint)
                accentPaint.color = if (isStart) highlightColor else buttonIdleColor
                canvas.drawCircle(menuCx, vmCy, vmR, accentPaint)
                textPaint.color = withAlpha(AndroidColor.WHITE, alphaInt, 0.9f)
                textPaint.textSize = h * 0.020f
                canvas.drawText("⧉", viewCx, vmCy + h * 0.007f, textPaint)
                canvas.drawText("≡", menuCx, vmCy + h * 0.007f, textPaint)

                auxRect1.set(w * 0.475f, h * 0.465f, w * 0.525f, h * 0.495f)
                accentPaint.color = if (isSelect && isStart) highlightColor else buttonIdleColor
                canvas.drawRoundRect(auxRect1, w * 0.01f, w * 0.01f, accentPaint)
            }

            ResolvedControllerBrand.NINTENDO -> {
                reusableShellPath.reset()
                reusableShellPath.apply {
                    moveTo(w * 0.20f, h * 0.21f)
                    quadTo(w * 0.50f, h * 0.195f, w * 0.80f, h * 0.21f)
                    quadTo(w * 0.90f, h * 0.24f, w * 0.91f, h * 0.46f)
                    quadTo(w * 0.91f, h * 0.76f, w * 0.83f, h * 0.93f)
                    quadTo(w * 0.75f, h * 0.98f, w * 0.67f, h * 0.85f)
                    quadTo(w * 0.62f, h * 0.70f, w * 0.58f, h * 0.67f)
                    lineTo(w * 0.42f, h * 0.67f)
                    quadTo(w * 0.38f, h * 0.70f, w * 0.33f, h * 0.85f)
                    quadTo(w * 0.25f, h * 0.98f, w * 0.17f, h * 0.93f)
                    quadTo(w * 0.09f, h * 0.76f, w * 0.09f, h * 0.46f)
                    quadTo(w * 0.10f, h * 0.24f, w * 0.20f, h * 0.21f)
                    close()
                }
                bodyPaint.color = withAlpha(COLOR_SWITCH_SHELL, alphaInt)
                canvas.drawPath(reusableShellPath, bodyPaint)
                strokePaint.color = withAlpha(COLOR_SWITCH_STROKE, alphaInt, 0.7f)
                strokePaint.strokeWidth = w * 0.008f
                canvas.drawPath(reusableShellPath, strokePaint)

                // Switch Pro Distinct Dark Matte Grip Handles
                reusableLeftGripPath.reset()
                reusableLeftGripPath.apply {
                    moveTo(w * 0.09f, h * 0.50f)
                    quadTo(w * 0.20f, h * 0.58f, w * 0.33f, h * 0.85f)
                    quadTo(w * 0.25f, h * 0.98f, w * 0.17f, h * 0.93f)
                    quadTo(w * 0.09f, h * 0.76f, w * 0.09f, h * 0.50f)
                    close()
                }
                reusableRightGripPath.reset()
                reusableRightGripPath.apply {
                    moveTo(w * 0.91f, h * 0.50f)
                    quadTo(w * 0.80f, h * 0.58f, w * 0.67f, h * 0.85f)
                    quadTo(w * 0.75f, h * 0.98f, w * 0.83f, h * 0.93f)
                    quadTo(w * 0.91f, h * 0.76f, w * 0.91f, h * 0.50f)
                    close()
                }
                accentPaint.color = withAlpha(COLOR_SWITCH_GRIP, alphaInt)
                canvas.drawPath(reusableLeftGripPath, accentPaint)
                canvas.drawPath(reusableRightGripPath, accentPaint)

                // Switch Minus (-), Plus (+), Capture (Square), and Home (Circle) Buttons
                val minusCx = w * 0.41f
                val plusCx = w * 0.59f
                val mpCy = h * 0.30f
                val mpR = w * 0.019f
                accentPaint.color = if (isSelect) highlightColor else buttonIdleColor
                canvas.drawCircle(minusCx, mpCy, mpR, accentPaint)
                accentPaint.color = if (isStart) highlightColor else buttonIdleColor
                canvas.drawCircle(plusCx, mpCy, mpR, accentPaint)
                textPaint.color = withAlpha(AndroidColor.WHITE, alphaInt)
                textPaint.textSize = h * 0.026f
                canvas.drawText("−", minusCx, mpCy + h * 0.009f, textPaint)
                canvas.drawText("+", plusCx, mpCy + h * 0.009f, textPaint)

                // Capture Square & Home Circle
                auxRect1.set(w * 0.43f, h * 0.39f, w * 0.466f, h * 0.44f)
                accentPaint.color = buttonIdleColor
                canvas.drawRoundRect(auxRect1, w * 0.006f, w * 0.006f, accentPaint)

                val swHomeCx = w * 0.55f
                val swHomeCy = h * 0.415f
                wellPaint.color = if (isHome) highlightColor else darkWellColor
                canvas.drawCircle(swHomeCx, swHomeCy, w * 0.021f, wellPaint)
                textPaint.color = withAlpha(AndroidColor.WHITE, alphaInt)
                textPaint.textSize = h * 0.020f
                canvas.drawText("⌂", swHomeCx, swHomeCy + h * 0.007f, textPaint)
            }
        }

        // 4. Determine Brand-Specific Coordinates for Left Stick & D-Pad
        val isAsymmetricLayout = glyphs.brand == ResolvedControllerBrand.XBOX ||
            glyphs.brand == ResolvedControllerBrand.NINTENDO

        val dpadCx = if (isAsymmetricLayout) w * 0.39f else w * 0.255f
        val dpadCy = if (isAsymmetricLayout) h * 0.585f else h * 0.405f
        val dpadWellRadius = if (isAsymmetricLayout) w * 0.082f else w * 0.102f

        val lStickBaseX = if (isAsymmetricLayout) w * 0.255f else w * 0.39f
        val lStickBaseY = if (isAsymmetricLayout) h * 0.405f else h * 0.585f
        val rStickBaseX = w * 0.61f
        val rStickBaseY = h * 0.585f

        // 5. Render D-Pad Well & High-Contrast Directional Wedges (with physical shift when pressed)
        wellPaint.color = darkWellColor
        canvas.drawCircle(dpadCx, dpadCy, dpadWellRadius, wellPaint)
        if (glyphs.brand == ResolvedControllerBrand.XBOX) {
            strokePaint.color = withAlpha(COLOR_XBOX_DISH_RING, alphaInt, 0.85f)
            strokePaint.strokeWidth = w * 0.005f
            canvas.drawCircle(dpadCx, dpadCy, dpadWellRadius * 0.88f, strokePaint)
        }

        val armW = dpadWellRadius * 0.36f
        val armL = dpadWellRadius * 0.72f
        val pressShift = dpadWellRadius * 0.10f

        if (glyphs.brand == ResolvedControllerBrand.NINTENDO || glyphs.brand == ResolvedControllerBrand.XBOX) {
            dpadRect.set(dpadCx - armW * 0.5f, dpadCy - armW * 0.5f, dpadCx + armW * 0.5f, dpadCy + armW * 0.5f)
            accentPaint.color = buttonIdleColor
            canvas.drawRect(dpadRect, accentPaint)
        }

        val whiteGlowColor = withAlpha(AndroidColor.WHITE, alphaInt)

        // Up D-Pad
        val upShiftY = if (isDpadUp) -pressShift else 0f
        dpadRect.set(
            dpadCx - armW / 2f,
            dpadCy - armL + upShiftY,
            dpadCx + armW / 2f,
            dpadCy - armW * 0.28f + upShiftY
        )
        accentPaint.color = if (isDpadUp) highlightColor else buttonIdleColor
        canvas.drawRoundRect(dpadRect, armW * 0.22f, armW * 0.22f, accentPaint)
        if (isDpadUp) drawGlowOutline(canvas, dpadRect, armW * 0.22f, whiteGlowColor)

        // Down D-Pad
        val downShiftY = if (isDpadDown) pressShift else 0f
        dpadRect.set(
            dpadCx - armW / 2f,
            dpadCy + armW * 0.28f + downShiftY,
            dpadCx + armW / 2f,
            dpadCy + armL + downShiftY
        )
        accentPaint.color = if (isDpadDown) highlightColor else buttonIdleColor
        canvas.drawRoundRect(dpadRect, armW * 0.22f, armW * 0.22f, accentPaint)
        if (isDpadDown) drawGlowOutline(canvas, dpadRect, armW * 0.22f, whiteGlowColor)

        // Left D-Pad
        val leftShiftX = if (isDpadLeft) -pressShift else 0f
        dpadRect.set(
            dpadCx - armL + leftShiftX,
            dpadCy - armW / 2f,
            dpadCx - armW * 0.28f + leftShiftX,
            dpadCy + armW / 2f
        )
        accentPaint.color = if (isDpadLeft) highlightColor else buttonIdleColor
        canvas.drawRoundRect(dpadRect, armW * 0.22f, armW * 0.22f, accentPaint)
        if (isDpadLeft) drawGlowOutline(canvas, dpadRect, armW * 0.22f, whiteGlowColor)

        // Right D-Pad
        val rightShiftX = if (isDpadRight) pressShift else 0f
        dpadRect.set(
            dpadCx + armW * 0.28f + rightShiftX,
            dpadCy - armW / 2f,
            dpadCx + armL + rightShiftX,
            dpadCy + armW / 2f
        )
        accentPaint.color = if (isDpadRight) highlightColor else buttonIdleColor
        canvas.drawRoundRect(dpadRect, armW * 0.22f, armW * 0.22f, accentPaint)
        if (isDpadRight) drawGlowOutline(canvas, dpadRect, armW * 0.22f, whiteGlowColor)

        // 6. Right Circular Well: Face Buttons (PlayStation Shapes or Xbox / Nintendo Letters)
        val faceCx = w * 0.745f
        val faceCy = h * 0.405f
        val wellRadius = w * 0.105f
        wellPaint.color = darkWellColor
        canvas.drawCircle(faceCx, faceCy, wellRadius, wellPaint)

        val btnDist = wellRadius * 0.56f
        val btnRadius = wellRadius * 0.25f

        // North (Triangle / Y / X)
        drawFaceButton(
            canvas = canvas,
            cx = faceCx,
            cy = faceCy - btnDist,
            radius = btnRadius,
            isPressed = isNorth,
            glyph = glyphs.northGlyph,
            glyphColor = withAlpha(glyphs.northColorArgb.toInt(), alphaInt),
            idleBg = buttonIdleColor,
            activeBg = highlightColor,
            brand = glyphs.brand,
            slot = "NORTH",
            alphaInt = alphaInt
        )
        // East (Circle / B / A)
        drawFaceButton(
            canvas = canvas,
            cx = faceCx + btnDist,
            cy = faceCy,
            radius = btnRadius,
            isPressed = isEast,
            glyph = glyphs.eastGlyph,
            glyphColor = withAlpha(glyphs.eastColorArgb.toInt(), alphaInt),
            idleBg = buttonIdleColor,
            activeBg = highlightColor,
            brand = glyphs.brand,
            slot = "EAST",
            alphaInt = alphaInt
        )
        // South (Cross / A / B)
        drawFaceButton(
            canvas = canvas,
            cx = faceCx,
            cy = faceCy + btnDist,
            radius = btnRadius,
            isPressed = isSouth,
            glyph = glyphs.southGlyph,
            glyphColor = withAlpha(glyphs.southColorArgb.toInt(), alphaInt),
            idleBg = buttonIdleColor,
            activeBg = highlightColor,
            brand = glyphs.brand,
            slot = "SOUTH",
            alphaInt = alphaInt
        )
        // West (Square / X / Y)
        drawFaceButton(
            canvas = canvas,
            cx = faceCx - btnDist,
            cy = faceCy,
            radius = btnRadius,
            isPressed = isWest,
            glyph = glyphs.westGlyph,
            glyphColor = withAlpha(glyphs.westColorArgb.toInt(), alphaInt),
            idleBg = buttonIdleColor,
            activeBg = highlightColor,
            brand = glyphs.brand,
            slot = "WEST",
            alphaInt = alphaInt
        )

        // 7. Left & Right Analog Thumbsticks (with real-time 2D stick deflection + stem indicator)
        val stickWellR = w * 0.068f
        val stickCapR = w * 0.052f
        val maxDeflect = stickWellR * 0.56f

        // Left Stick Base Well
        bodyPaint.color = bodyShadowColor
        canvas.drawCircle(lStickBaseX, lStickBaseY, stickWellR * 1.15f, bodyPaint)
        wellPaint.color = darkWellColor
        canvas.drawCircle(lStickBaseX, lStickBaseY, stickWellR, wellPaint)

        val lxOffset = (state.leftStick.x.coerceIn(-1f, 1f)) * maxDeflect
        val lyOffset = (state.leftStick.y.coerceIn(-1f, 1f)) * maxDeflect
        val lActive = isL3 || state.leftStick.magnitude > 0.08f
        if (lActive) {
            strokePaint.color = highlightColor
            strokePaint.strokeWidth = w * 0.012f
            canvas.drawLine(lStickBaseX, lStickBaseY, lStickBaseX + lxOffset, lStickBaseY + lyOffset, strokePaint)
        }
        accentPaint.color = if (lActive) highlightColor else buttonIdleColor
        canvas.drawCircle(lStickBaseX + lxOffset, lStickBaseY + lyOffset, stickCapR, accentPaint)
        strokePaint.color = if (lActive) whiteGlowColor else withAlpha(AndroidColor.WHITE, alphaInt, 0.35f)
        strokePaint.strokeWidth = w * 0.006f
        canvas.drawCircle(lStickBaseX + lxOffset, lStickBaseY + lyOffset, stickCapR * 0.72f, strokePaint)

        // Right Stick Base Well
        bodyPaint.color = bodyShadowColor
        canvas.drawCircle(rStickBaseX, rStickBaseY, stickWellR * 1.15f, bodyPaint)
        wellPaint.color = darkWellColor
        canvas.drawCircle(rStickBaseX, rStickBaseY, stickWellR, wellPaint)

        val rxOffset = (state.rightStick.x.coerceIn(-1f, 1f)) * maxDeflect
        val ryOffset = (state.rightStick.y.coerceIn(-1f, 1f)) * maxDeflect
        val rActive = isR3 || state.rightStick.magnitude > 0.08f
        if (rActive) {
            strokePaint.color = highlightColor
            strokePaint.strokeWidth = w * 0.012f
            canvas.drawLine(rStickBaseX, rStickBaseY, rStickBaseX + rxOffset, rStickBaseY + ryOffset, strokePaint)
        }
        accentPaint.color = if (rActive) highlightColor else buttonIdleColor
        canvas.drawCircle(rStickBaseX + rxOffset, rStickBaseY + ryOffset, stickCapR, accentPaint)
        strokePaint.color = if (rActive) whiteGlowColor else withAlpha(AndroidColor.WHITE, alphaInt, 0.35f)
        strokePaint.strokeWidth = w * 0.006f
        canvas.drawCircle(rStickBaseX + rxOffset, rStickBaseY + ryOffset, stickCapR * 0.72f, strokePaint)
    }

    private fun drawGlowOutline(canvas: AndroidCanvas, rect: RectF, radius: Float, color: Int) {
        strokePaint.color = color
        strokePaint.strokeWidth = 3f
        canvas.drawRoundRect(rect, radius, radius, strokePaint)
    }

    private fun drawFaceButton(
        canvas: AndroidCanvas,
        cx: Float,
        cy: Float,
        radius: Float,
        isPressed: Boolean,
        glyph: String,
        glyphColor: Int,
        idleBg: Int,
        activeBg: Int,
        brand: ResolvedControllerBrand,
        slot: String,
        alphaInt: Int
    ) {
        accentPaint.color = if (isPressed) activeBg else idleBg
        canvas.drawCircle(cx, cy, radius, accentPaint)

        if (isPressed) {
            strokePaint.color = withAlpha(AndroidColor.WHITE, alphaInt, 0.95f)
            strokePaint.strokeWidth = radius * 0.18f
            canvas.drawCircle(cx, cy, radius, strokePaint)
        }

        val symbolColor = if (isPressed) withAlpha(AndroidColor.WHITE, alphaInt, 1f) else glyphColor
        strokePaint.color = symbolColor
        strokePaint.strokeWidth = radius * 0.18f

        if (brand == ResolvedControllerBrand.PLAYSTATION) {
            when (slot) {
                "NORTH" -> {
                    val r = radius * 0.52f
                    reusableTrianglePath.reset()
                    reusableTrianglePath.moveTo(cx, cy - r)
                    reusableTrianglePath.lineTo(cx + r * 0.9f, cy + r * 0.72f)
                    reusableTrianglePath.lineTo(cx - r * 0.9f, cy + r * 0.72f)
                    reusableTrianglePath.close()
                    canvas.drawPath(reusableTrianglePath, strokePaint)
                }
                "EAST" -> {
                    // Circle
                    canvas.drawCircle(cx, cy, radius * 0.48f, strokePaint)
                }
                "SOUTH" -> {
                    // Cross (X)
                    val r = radius * 0.42f
                    canvas.drawLine(cx - r, cy - r, cx + r, cy + r, strokePaint)
                    canvas.drawLine(cx + r, cy - r, cx - r, cy + r, strokePaint)
                }
                "WEST" -> {
                    // Square
                    val r = radius * 0.42f
                    canvas.drawRect(cx - r, cy - r, cx + r, cy + r, strokePaint)
                }
            }
        } else {
            textPaint.color = symbolColor
            textPaint.textSize = radius * 1.15f
            canvas.drawText(glyph, cx, cy + radius * 0.40f, textPaint)
        }
    }
}

/**
 * Jetpack Compose wrapper for the real-time Controller Input Visualizer.
 */
@Composable
fun ControllerInputVisualizerCanvas(
    state: ControllerHealthState,
    glyphs: ControllerGlyphMap,
    highlightColorArgb: Long = 0xFFE5252AL,
    opacity: Float = 0.95f,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        ControllerCanvasRenderer.drawController(
            canvas = drawContext.canvas.nativeCanvas,
            width = size.width,
            height = size.height,
            state = state,
            glyphs = glyphs,
            highlightArgb = highlightColorArgb,
            opacity = opacity
        )
    }
}

/**
 * In-App Draggable & Auto-Scaling Floating Controller Screen Overlay HUD.
 * Renders directly over the G-Max Hub when enabled, and works alongside the system-wide
 * WindowManager overlay service when playing external games.
 */
@Composable
fun FloatingInAppControllerOverlay(
    state: ControllerHealthState,
    glyphs: ControllerGlyphMap,
    config: ControllerOverlayConfig,
    onToggleLock: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!config.isEnabled) return

    val screenConfig = LocalConfiguration.current
    val screenW = screenConfig.screenWidthDp.coerceAtLeast(320)
    val screenH = screenConfig.screenHeightDp.coerceAtLeast(320)
    val minDim = minOf(screenW, screenH)

    // Auto-scaling base size based on screen orientation & smallest width, multiplied by user scaleFactor
    val autoBaseWidthDp = if (config.autoScale) {
        (minDim * 0.44f).coerceIn(145f, 230f)
    } else {
        185f
    }
    val finalWidthDp = (autoBaseWidthDp * config.scaleFactor.coerceIn(0.55f, 1.65f)).dp
    val finalHeightDp = finalWidthDp * 0.68f

    var offsetX by remember { mutableFloatStateOf(24f) }
    var offsetY by remember { mutableFloatStateOf(160f) }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .then(
                if (!config.lockTouchPassThrough) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    }
                } else {
                    Modifier
                }
            )
            .testTag("in_app_controller_overlay")
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Mini top control pill (visible when unlocked)
            if (!config.lockTouchPassThrough) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xCC0A1220), RoundedCornerShape(50))
                        .border(
                            1.dp,
                            Color(config.highlightColorArgb).copy(alpha = 0.65f),
                            RoundedCornerShape(50)
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${glyphs.brandTitle} HUD",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f))
                                .clickable { onToggleLock() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (config.lockTouchPassThrough) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = "Lock touch pass-through",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE5252A).copy(alpha = 0.25f))
                                .clickable { onClose() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Hide controller overlay",
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
            }

            ControllerInputVisualizerCanvas(
                state = state,
                glyphs = glyphs,
                highlightColorArgb = config.highlightColorArgb,
                opacity = config.opacity,
                modifier = Modifier
                    .width(finalWidthDp)
                    .height(finalHeightDp)
            )
        }
    }
}
