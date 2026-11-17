package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import com.example.util.SystemSettingsHelper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.OverlaySettings
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.SolarGold
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

object CrosshairData {
    val styles = listOf(
        "Classic Cross",
        "Precision Dot",
        "Circle + Dot",
        "Tactical Chevron",
        "T-Pose",
        "Sniper Mil-Dot",
        "Diamond Box",
        "Circle + Cross"
    )

    val colors = listOf(
        Pair("Cyan", 0xFF00E5FFL),
        Pair("Green", 0xFF00FF66L),
        Pair("Crimson", 0xFFFF2A4DL),
        Pair("Gold", 0xFFFFD700L),
        Pair("White", 0xFFFFFFFFL),
        Pair("Purple", 0xFFA855F7L),
        Pair("Hot Pink", 0xFFFF1493L),
        Pair("Orange", 0xFFFF6F00L)
    )
}

/**
 * Crisp miniature vector reticle icon for style selector chips and header badges.
 * Every style's aiming point is anchored at the exact geometric center of the icon.
 */
@Composable
fun CrosshairStyleMiniIcon(
    style: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = (size.minDimension / 2f) * 0.85f
        val strokeW = 1.6.dp.toPx()
        val haloW = strokeW + 1.4.dp.toPx()
        val haloColor = Color.Black.copy(alpha = 0.75f)

        when (style) {
            0 -> {
                // Classic Cross (+)
                val arm = r * 0.92f
                val gap = r * 0.26f
                drawLine(haloColor, Offset(cx, cy - arm), Offset(cx, cy - gap), strokeWidth = haloW, cap = StrokeCap.Round)
                drawLine(haloColor, Offset(cx, cy + gap), Offset(cx, cy + arm), strokeWidth = haloW, cap = StrokeCap.Round)
                drawLine(haloColor, Offset(cx - arm, cy), Offset(cx - gap, cy), strokeWidth = haloW, cap = StrokeCap.Round)
                drawLine(haloColor, Offset(cx + gap, cy), Offset(cx + arm, cy), strokeWidth = haloW, cap = StrokeCap.Round)

                drawLine(color, Offset(cx, cy - arm), Offset(cx, cy - gap), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx, cy + gap), Offset(cx, cy + arm), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx - arm, cy), Offset(cx - gap, cy), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx + gap, cy), Offset(cx + arm, cy), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawCircle(color, radius = strokeW * 0.55f, center = Offset(cx, cy))
            }
            1 -> {
                // Precision Dot (•)
                val dotR = r * 0.42f
                drawCircle(haloColor, radius = dotR + 1.dp.toPx(), center = Offset(cx, cy))
                drawCircle(color, radius = dotR, center = Offset(cx, cy))
            }
            2 -> {
                // Circle + Dot (⊙)
                val circleR = r * 0.76f
                drawCircle(haloColor, radius = circleR, center = Offset(cx, cy), style = Stroke(width = haloW))
                drawCircle(color, radius = circleR, center = Offset(cx, cy), style = Stroke(width = strokeW))
                drawCircle(haloColor, radius = strokeW * 0.95f, center = Offset(cx, cy))
                drawCircle(color, radius = strokeW * 0.7f, center = Offset(cx, cy))
            }
            3 -> {
                // Tactical Chevron (^) — apex tip anchored at (cx, cy)
                val wingSpanX = r * 0.68f
                val wingDropY = r * 0.62f
                val path = Path().apply {
                    moveTo(cx - wingSpanX, cy + wingDropY)
                    lineTo(cx, cy)
                    lineTo(cx + wingSpanX, cy + wingDropY)
                }
                drawPath(path, haloColor, style = Stroke(width = haloW, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, color, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(color, radius = strokeW * 0.5f, center = Offset(cx, cy))
            }
            4 -> {
                // T-Pose (⊤) — open upper field, arms converge on (cx, cy)
                val arm = r * 0.90f
                val gap = r * 0.24f
                drawLine(haloColor, Offset(cx - arm, cy), Offset(cx - gap, cy), strokeWidth = haloW, cap = StrokeCap.Round)
                drawLine(haloColor, Offset(cx + gap, cy), Offset(cx + arm, cy), strokeWidth = haloW, cap = StrokeCap.Round)
                drawLine(haloColor, Offset(cx, cy + gap), Offset(cx, cy + arm), strokeWidth = haloW, cap = StrokeCap.Round)

                drawLine(color, Offset(cx - arm, cy), Offset(cx - gap, cy), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx + gap, cy), Offset(cx + arm, cy), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx, cy + gap), Offset(cx, cy + arm), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawCircle(color, radius = strokeW * 0.55f, center = Offset(cx, cy))
            }
            5 -> {
                // Sniper Mil-Dot (⌖)
                val outerR = r * 0.78f
                val arm = r * 0.96f
                val gap = r * 0.22f
                drawCircle(haloColor, radius = outerR, center = Offset(cx, cy), style = Stroke(width = haloW * 0.85f))
                drawCircle(color, radius = outerR, center = Offset(cx, cy), style = Stroke(width = strokeW * 0.85f))
                drawLine(color, Offset(cx, cy - arm), Offset(cx, cy - gap), strokeWidth = strokeW * 0.85f, cap = StrokeCap.Round)
                drawLine(color, Offset(cx, cy + gap), Offset(cx, cy + arm), strokeWidth = strokeW * 0.85f, cap = StrokeCap.Round)
                drawLine(color, Offset(cx - arm, cy), Offset(cx - gap, cy), strokeWidth = strokeW * 0.85f, cap = StrokeCap.Round)
                drawLine(color, Offset(cx + gap, cy), Offset(cx + arm, cy), strokeWidth = strokeW * 0.85f, cap = StrokeCap.Round)
                drawCircle(color, radius = strokeW * 0.55f, center = Offset(cx, cy))
            }
            6 -> {
                // Diamond Box (◇)
                val d = r * 0.78f
                val path = Path().apply {
                    moveTo(cx, cy - d)
                    lineTo(cx + d, cy)
                    lineTo(cx, cy + d)
                    lineTo(cx - d, cy)
                    close()
                }
                drawPath(path, haloColor, style = Stroke(width = haloW, join = StrokeJoin.Round))
                drawPath(path, color, style = Stroke(width = strokeW, join = StrokeJoin.Round))
                drawCircle(color, radius = strokeW * 0.55f, center = Offset(cx, cy))
            }
            else -> {
                // Circle + Cross (⊕)
                val circleR = r * 0.68f
                val arm = r * 0.95f
                val gap = r * 0.22f
                drawCircle(haloColor, radius = circleR, center = Offset(cx, cy), style = Stroke(width = haloW))
                drawCircle(color, radius = circleR, center = Offset(cx, cy), style = Stroke(width = strokeW))
                drawLine(color, Offset(cx, cy - arm), Offset(cx, cy - gap), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx, cy + gap), Offset(cx, cy + arm), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx - arm, cy), Offset(cx - gap, cy), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx + gap, cy), Offset(cx + arm, cy), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawCircle(color, radius = strokeW * 0.5f, center = Offset(cx, cy))
            }
        }
    }
}

/**
 * High-precision Compose Canvas rendering for all 8 crosshair reticle designs.
 * Matches CrosshairOverlayService.CrosshairView 1:1 with exact aim-point centering at (cx, cy).
 */
@Composable
fun CrosshairPreview(
    style: Int,
    color: Color,
    sizeDp: Int,
    strokeThicknessDp: Float = 2.5f,
    centerGapDp: Float = 4.0f,
    showOutline: Boolean = true,
    showCenterDot: Boolean = true,
    offsetXdp: Int = 0,
    offsetYdp: Int = 0,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(sizeDp.dp)) {
        val cx = (size.width / 2f) + offsetXdp.dp.toPx()
        val cy = (size.height / 2f) + offsetYdp.dp.toPx()
        val r = (size.minDimension / 2f)
        val strokeW = strokeThicknessDp.dp.toPx().coerceIn(1.5f, r * 0.32f)
        val shadowW = strokeW + 2.2.dp.toPx()
        val shadowColor = if (showOutline) Color.Black.copy(alpha = 0.85f) else Color.Transparent

        fun drawAimCenterDot(radiusPx: Float) {
            if (showOutline) {
                drawCircle(
                    color = shadowColor,
                    radius = radiusPx + 1.2.dp.toPx(),
                    center = Offset(cx, cy)
                )
            }
            drawCircle(
                color = color,
                radius = radiusPx,
                center = Offset(cx, cy)
            )
        }

        when (style) {
            0 -> {
                // Classic Cross (+) with center gap — exact intersection at (cx, cy)
                val arm = r * 0.90f
                val gap = centerGapDp.dp.toPx().coerceIn(0f, arm * 0.72f)
                if (showOutline) {
                    drawLine(shadowColor, Offset(cx, cy - arm), Offset(cx, cy - gap), strokeWidth = shadowW, cap = StrokeCap.Round)
                    drawLine(shadowColor, Offset(cx, cy + gap), Offset(cx, cy + arm), strokeWidth = shadowW, cap = StrokeCap.Round)
                    drawLine(shadowColor, Offset(cx - arm, cy), Offset(cx - gap, cy), strokeWidth = shadowW, cap = StrokeCap.Round)
                    drawLine(shadowColor, Offset(cx + gap, cy), Offset(cx + arm, cy), strokeWidth = shadowW, cap = StrokeCap.Round)
                }
                drawLine(color, Offset(cx, cy - arm), Offset(cx, cy - gap), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx, cy + gap), Offset(cx, cy + arm), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx - arm, cy), Offset(cx - gap, cy), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx + gap, cy), Offset(cx + arm, cy), strokeWidth = strokeW, cap = StrokeCap.Round)
                if (showCenterDot) {
                    drawAimCenterDot((strokeW * 0.58f).coerceAtLeast(1.8.dp.toPx()))
                }
            }
            1 -> {
                // Precision Dot (•) — concentric high-contrast dot centered at (cx, cy)
                val dotR = (r * 0.28f).coerceAtLeast(strokeW * 1.15f)
                if (showOutline) {
                    drawCircle(shadowColor, radius = dotR + 1.8.dp.toPx(), center = Offset(cx, cy))
                }
                drawCircle(color, radius = dotR, center = Offset(cx, cy))
            }
            2 -> {
                // Circle + Dot (⊙) — optical ring + 4 cardinal micro-ticks + center dot at (cx, cy)
                val circleR = r * 0.72f
                val tickOuter = circleR + (strokeW * 0.85f)
                val tickInner = (circleR - (strokeW * 0.85f)).coerceAtLeast(0f)
                val dotR = (r * 0.18f).coerceAtLeast(strokeW * 0.75f)
                if (showOutline) {
                    drawCircle(shadowColor, radius = circleR, center = Offset(cx, cy), style = Stroke(width = shadowW))
                    drawLine(shadowColor, Offset(cx, cy - tickOuter), Offset(cx, cy - tickInner), strokeWidth = shadowW, cap = StrokeCap.Round)
                    drawLine(shadowColor, Offset(cx, cy + tickInner), Offset(cx, cy + tickOuter), strokeWidth = shadowW, cap = StrokeCap.Round)
                    drawLine(shadowColor, Offset(cx - tickOuter, cy), Offset(cx - tickInner, cy), strokeWidth = shadowW, cap = StrokeCap.Round)
                    drawLine(shadowColor, Offset(cx + tickInner, cy), Offset(cx + tickOuter, cy), strokeWidth = shadowW, cap = StrokeCap.Round)
                }
                drawCircle(color, radius = circleR, center = Offset(cx, cy), style = Stroke(width = strokeW))
                drawLine(color, Offset(cx, cy - tickOuter), Offset(cx, cy - tickInner), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx, cy + tickInner), Offset(cx, cy + tickOuter), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx - tickOuter, cy), Offset(cx - tickInner, cy), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx + tickInner, cy), Offset(cx + tickOuter, cy), strokeWidth = strokeW, cap = StrokeCap.Round)
                if (showCenterDot) {
                    drawAimCenterDot(dotR)
                }
            }
            3 -> {
                // Tactical Chevron (^) — exact apex tip anchored at (cx, cy)
                val wingSpanX = r * 0.62f
                val wingDropY = r * 0.56f
                val bdcTop = (wingDropY * 0.58f).coerceAtLeast(centerGapDp.dp.toPx() * 0.6f + strokeW)
                val bdcBottom = r * 0.86f
                val path = Path().apply {
                    moveTo(cx - wingSpanX, cy + wingDropY)
                    lineTo(cx, cy)
                    lineTo(cx + wingSpanX, cy + wingDropY)
                }
                if (showOutline) {
                    drawPath(path, shadowColor, style = Stroke(width = shadowW, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawLine(shadowColor, Offset(cx, cy + bdcTop), Offset(cx, cy + bdcBottom), strokeWidth = shadowW, cap = StrokeCap.Round)
                }
                drawPath(path, color, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawLine(color, Offset(cx, cy + bdcTop), Offset(cx, cy + bdcBottom), strokeWidth = strokeW, cap = StrokeCap.Round)
                if (showCenterDot) {
                    drawAimCenterDot((strokeW * 0.50f).coerceAtLeast(1.5.dp.toPx()))
                }
            }
            4 -> {
                // T-Pose (⊤) — left, right, and bottom arms converge on (cx, cy)
                val arm = r * 0.88f
                val gap = centerGapDp.dp.toPx().coerceIn(0f, arm * 0.70f)
                if (showOutline) {
                    drawLine(shadowColor, Offset(cx - arm, cy), Offset(cx - gap, cy), strokeWidth = shadowW, cap = StrokeCap.Round)
                    drawLine(shadowColor, Offset(cx + gap, cy), Offset(cx + arm, cy), strokeWidth = shadowW, cap = StrokeCap.Round)
                    drawLine(shadowColor, Offset(cx, cy + gap), Offset(cx, cy + arm), strokeWidth = shadowW, cap = StrokeCap.Round)
                }
                drawLine(color, Offset(cx - arm, cy), Offset(cx - gap, cy), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx + gap, cy), Offset(cx + arm, cy), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx, cy + gap), Offset(cx, cy + arm), strokeWidth = strokeW, cap = StrokeCap.Round)
                if (showCenterDot) {
                    drawAimCenterDot((strokeW * 0.58f).coerceAtLeast(1.8.dp.toPx()))
                }
            }
            5 -> {
                // Sniper Mil-Dot (⌖) — scope ring, gap-aware crosshairs, 4 outlined mil-dots & center dot at (cx, cy)
                val outerR = r * 0.78f
                val arm = r * 0.94f
                val gap = centerGapDp.dp.toPx().coerceIn(0f, arm * 0.62f)
                val dotD = outerR * 0.50f
                val milDotR = (strokeW * 0.62f).coerceAtLeast(1.8.dp.toPx())
                if (showOutline) {
                    drawCircle(shadowColor, radius = outerR, center = Offset(cx, cy), style = Stroke(width = shadowW))
                    drawLine(shadowColor, Offset(cx, cy - arm), Offset(cx, cy - gap), strokeWidth = shadowW, cap = StrokeCap.Round)
                    drawLine(shadowColor, Offset(cx, cy + gap), Offset(cx, cy + arm), strokeWidth = shadowW, cap = StrokeCap.Round)
                    drawLine(shadowColor, Offset(cx - arm, cy), Offset(cx - gap, cy), strokeWidth = shadowW, cap = StrokeCap.Round)
                    drawLine(shadowColor, Offset(cx + gap, cy), Offset(cx + arm, cy), strokeWidth = shadowW, cap = StrokeCap.Round)
                    drawCircle(shadowColor, radius = milDotR + 1.dp.toPx(), center = Offset(cx - dotD, cy))
                    drawCircle(shadowColor, radius = milDotR + 1.dp.toPx(), center = Offset(cx + dotD, cy))
                    drawCircle(shadowColor, radius = milDotR + 1.dp.toPx(), center = Offset(cx, cy - dotD))
                    drawCircle(shadowColor, radius = milDotR + 1.dp.toPx(), center = Offset(cx, cy + dotD))
                }
                drawCircle(color, radius = outerR, center = Offset(cx, cy), style = Stroke(width = strokeW))
                drawLine(color, Offset(cx, cy - arm), Offset(cx, cy - gap), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx, cy + gap), Offset(cx, cy + arm), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx - arm, cy), Offset(cx - gap, cy), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx + gap, cy), Offset(cx + arm, cy), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawCircle(color, radius = milDotR, center = Offset(cx - dotD, cy))
                drawCircle(color, radius = milDotR, center = Offset(cx + dotD, cy))
                drawCircle(color, radius = milDotR, center = Offset(cx, cy - dotD))
                drawCircle(color, radius = milDotR, center = Offset(cx, cy + dotD))
                if (showCenterDot) {
                    drawAimCenterDot((strokeW * 0.58f).coerceAtLeast(1.8.dp.toPx()))
                }
            }
            6 -> {
                // Diamond Box (◇) — symmetrical 45° diamond + 4 cardinal corner ticks + center dot at (cx, cy)
                val d = r * 0.68f
                val tickOuter = (d + strokeW * 1.3f).coerceAtMost(r * 0.94f)
                val path = Path().apply {
                    moveTo(cx, cy - d)
                    lineTo(cx + d, cy)
                    lineTo(cx, cy + d)
                    lineTo(cx - d, cy)
                    close()
                }
                if (showOutline) {
                    drawPath(path, shadowColor, style = Stroke(width = shadowW, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawLine(shadowColor, Offset(cx, cy - tickOuter), Offset(cx, cy - d), strokeWidth = shadowW, cap = StrokeCap.Round)
                    drawLine(shadowColor, Offset(cx, cy + d), Offset(cx, cy + tickOuter), strokeWidth = shadowW, cap = StrokeCap.Round)
                    drawLine(shadowColor, Offset(cx - tickOuter, cy), Offset(cx - d, cy), strokeWidth = shadowW, cap = StrokeCap.Round)
                    drawLine(shadowColor, Offset(cx + d, cy), Offset(cx + tickOuter, cy), strokeWidth = shadowW, cap = StrokeCap.Round)
                }
                drawPath(path, color, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawLine(color, Offset(cx, cy - tickOuter), Offset(cx, cy - d), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx, cy + d), Offset(cx, cy + tickOuter), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx - tickOuter, cy), Offset(cx - d, cy), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx + d, cy), Offset(cx + tickOuter, cy), strokeWidth = strokeW, cap = StrokeCap.Round)
                if (showCenterDot) {
                    drawAimCenterDot((strokeW * 0.62f).coerceAtLeast(1.8.dp.toPx()))
                }
            }
            else -> {
                // Circle + Cross (⊕) — optical ring + gap-aware 4 crosshair arms + center dot at (cx, cy)
                val circleR = r * 0.66f
                val arm = r * 0.92f
                val gap = centerGapDp.dp.toPx().coerceIn(0f, arm * 0.65f)
                if (showOutline) {
                    drawCircle(shadowColor, radius = circleR, center = Offset(cx, cy), style = Stroke(width = shadowW))
                    drawLine(shadowColor, Offset(cx, cy - arm), Offset(cx, cy - gap), strokeWidth = shadowW, cap = StrokeCap.Round)
                    drawLine(shadowColor, Offset(cx, cy + gap), Offset(cx, cy + arm), strokeWidth = shadowW, cap = StrokeCap.Round)
                    drawLine(shadowColor, Offset(cx - arm, cy), Offset(cx - gap, cy), strokeWidth = shadowW, cap = StrokeCap.Round)
                    drawLine(shadowColor, Offset(cx + gap, cy), Offset(cx + arm, cy), strokeWidth = shadowW, cap = StrokeCap.Round)
                }
                drawCircle(color, radius = circleR, center = Offset(cx, cy), style = Stroke(width = strokeW))
                drawLine(color, Offset(cx, cy - arm), Offset(cx, cy - gap), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx, cy + gap), Offset(cx, cy + arm), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx - arm, cy), Offset(cx - gap, cy), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx + gap, cy), Offset(cx + arm, cy), strokeWidth = strokeW, cap = StrokeCap.Round)
                if (showCenterDot) {
                    drawAimCenterDot((strokeW * 0.56f).coerceAtLeast(1.8.dp.toPx()))
                }
            }
        }
    }
}

/**
 * Deprecated banner: replaced with AntiCheatNoticeDialog modal popup per user request.
 */
@Deprecated("Use AntiCheatNoticeDialog modal popup instead")
@Composable
fun CrosshairAntiCheatBanner(modifier: Modifier = Modifier) {
    // Banner removed per user request: static warning removed, popup warning kept.
}

/**
 * Confirmation dialog before turning on the crosshair reticle.
 */
@Composable
fun AntiCheatNoticeDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = Color(0xFFF97316))
        },
        title = {
            Text(
                text = com.example.util.tr("Fair Play Policy Notice", "Aviso de Política de Juego Limpio"),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        text = {
            Column {
                Text(
                    text = com.example.util.tr(
                        "Certain online multiplayer games strictly prohibit screen overlay utilities, custom crosshairs, or external reticles under their Fair Play terms.",
                        "Ciertos juegos multijugador en línea prohíben estrictamente utilidades de superposición, miras personalizadas o retículas externas bajo sus términos de Juego Limpio."
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = com.example.util.tr(
                        "Enabling this feature in competitive games may lead to account penalties or permanent bans by game publishers. Do you understand and wish to activate it?",
                        "Activar esta función en juegos competitivos puede causar sanciones o suspensiones permanentes por los editores del juego. ¿Comprendes y deseas activarla?"
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFF97316), fontWeight = FontWeight.SemiBold)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(com.example.util.tr("I Understand & Proceed", "Entiendo y Continuar"), fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(com.example.util.tr("Cancel", "Cancelar"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

/**
 * Step-by-step interactive tutorial for activating Developer Mode on Android.
 */
@Composable
fun DeveloperModeTutorialDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isDevActive = remember(context) {
        try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
        } catch (_: Exception) {
            false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(imageVector = Icons.Default.DeveloperMode, contentDescription = null, tint = CyberCyan)
        },
        title = {
            Text(
                text = com.example.util.tr("Activate Android Developer Mode", "Activar Modo Desarrollador de Android"),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Status Pill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isDevActive) NeonGreen.copy(alpha = 0.16f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isDevActive) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isDevActive) NeonGreen else SolarGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isDevActive) {
                                com.example.util.tr("Developer Mode is ACTIVE on this phone", "El Modo Desarrollador está ACTIVO en este teléfono")
                            } else {
                                com.example.util.tr("Developer Mode is NOT yet activated", "El Modo Desarrollador AÚN NO está activado")
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isDevActive) NeonGreen else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = com.example.util.tr("Follow these 4 quick steps:", "Sigue estos 4 pasos rápidos:"),
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                TutorialStepRow(
                    stepNumber = "1",
                    title = com.example.util.tr("Open 'About Phone'", "Abrir 'Acerca del teléfono'"),
                    description = com.example.util.tr(
                        "Open Android Settings and navigate to About Phone (or About Device).",
                        "Abre Ajustes de Android y ve a Acerca del teléfono (o Acerca del dispositivo)."
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                TutorialStepRow(
                    stepNumber = "2",
                    title = com.example.util.tr("Locate 'Build Number'", "Buscar 'Número de compilación'"),
                    description = com.example.util.tr(
                        "Find 'Build Number'. (On Samsung: Software Info > Build Number; on Xiaomi: Tap 'OS / MIUI Version').",
                        "Busca 'Número de compilación'. (En Samsung: Info de software > Número de compilación; en Xiaomi: Toca 'Versión de MIUI / SO')."
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                TutorialStepRow(
                    stepNumber = "3",
                    title = com.example.util.tr("Tap Build Number 7 Times", "Toca Número de compilación 7 veces"),
                    description = com.example.util.tr(
                        "Repeatedly tap Build Number 7 times. Enter your lockscreen PIN if prompted until Android toasts: 'You are now a developer!'.",
                        "Toca Número de compilación 7 veces seguidas. Ingresa tu PIN si se solicita hasta que Android muestre: '¡Ya eres desarrollador!'."
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                TutorialStepRow(
                    stepNumber = "4",
                    title = com.example.util.tr("Access Developer Options", "Acceder a Opciones de desarrollador"),
                    description = com.example.util.tr(
                        "Developer Options will now appear in Settings > System (or Additional Settings).",
                        "Las Opciones de desarrollador aparecerán en Ajustes > Sistema (o Ajustes adicionales)."
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(com.example.util.tr("1. Open About", "1. Abrir Acerca"), fontSize = 11.sp, color = CyberCyan)
                    }

                    Button(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (_: Exception) {}
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(com.example.util.tr("2. Open Dev Opts", "2. Opciones Des."), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(com.example.util.tr("Got It", "Entendido"), fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun TutorialStepRow(
    stepNumber: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(CyberCyan)
                .padding(top = 1.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp
                )
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            )
        }
    }
}

/**
 * Dedicated, complete interactive Tactical Crosshair Customization Card.
 */
@Composable
fun TacticalCrosshairCard(
    overlaySettings: OverlaySettings,
    onToggle: (Boolean) -> Unit,
    onSelectStyle: (Int) -> Unit,
    onSelectColor: (Long) -> Unit,
    onSelectSize: (Int) -> Unit,
    onShowAntiCheatNotice: () -> Unit,
    onSelectAlpha: (Float) -> Unit = {},
    onSelectStroke: (Float) -> Unit = {},
    onSelectGap: (Float) -> Unit = {},
    onSelectOffset: (Int, Int) -> Unit = { _, _ -> },
    onToggleOutline: (Boolean) -> Unit = {},
    onToggleCenterDot: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val canDraw = remember(context) { SystemSettingsHelper.canDrawOverlays(context) }

    val activeColorLong = overlaySettings.crosshairColor
    val activeColorName = remember(activeColorLong) {
        CrosshairData.colors.find { it.second == activeColorLong }?.first ?: "Custom"
    }
    val activeStyleName = CrosshairData.styles.getOrElse(overlaySettings.crosshairStyle) { "Crosshair" }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                1.dp,
                if (overlaySettings.isEnabled) Color(activeColorLong).copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (overlaySettings.isEnabled) Color(activeColorLong).copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        CrosshairStyleMiniIcon(
                            style = overlaySettings.crosshairStyle,
                            color = if (overlaySettings.isEnabled) Color(activeColorLong) else CyberCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = com.example.util.tr("Tactical Screen Crosshair", "Mira Táctica en Pantalla"),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp
                                )
                            )
                        }
                        Text(
                            text = if (overlaySettings.isEnabled) {
                                com.example.util.tr("ACTIVE • True Full-Screen Pixel Center", "ACTIVO • Centro Físico de Pantalla Completa")
                            } else {
                                com.example.util.tr("Off • 8 Reticle Designs & 8 Colors", "Inactivo • 8 Diseños de Retícula y 8 Colores")
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (overlaySettings.isEnabled) NeonGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }

                Switch(
                    checked = overlaySettings.isEnabled,
                    onCheckedChange = { targetState ->
                        onToggle(targetState)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(activeColorLong).copy(alpha = 0.85f),
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.testTag("crosshair_tweak_card_toggle")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reticle Live Preview Box (dark viewport with true-center target calibration grid)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0A0F1D))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    // True-center horizontal & vertical hairlines
                    drawLine(
                        color = Color(0x28FFFFFF),
                        start = Offset(0f, cy),
                        end = Offset(size.width, cy),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = Color(0x28FFFFFF),
                        start = Offset(cx, 0f),
                        end = Offset(cx, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                    // Concentric target calibration rings around (cx, cy)
                    drawCircle(
                        color = Color(0x1E00E5FF),
                        radius = 22.dp.toPx(),
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0x14FFFFFF),
                        radius = 44.dp.toPx(),
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                CrosshairPreview(
                    style = overlaySettings.crosshairStyle,
                    color = Color(activeColorLong).copy(alpha = overlaySettings.crosshairAlpha),
                    sizeDp = overlaySettings.crosshairSizeDp,
                    strokeThicknessDp = overlaySettings.strokeThicknessDp,
                    centerGapDp = overlaySettings.centerGapDp,
                    showOutline = overlaySettings.showOutline,
                    showCenterDot = overlaySettings.showCenterDot,
                    offsetXdp = overlaySettings.offsetXdp,
                    offsetYdp = overlaySettings.offsetYdp
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = com.example.util.AppStrings.autoTranslateDynamic(
                            "$activeStyleName • $activeColorName (${overlaySettings.crosshairSizeDp}dp • X:${overlaySettings.offsetXdp} Y:${overlaySettings.offsetYdp})"
                        ),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Reticle Style Selector Chips with Live Vector Reticle Icons
            Text(
                text = com.example.util.tr("Reticle Design (${CrosshairData.styles.size} Styles)", "Diseño de Retícula (${CrosshairData.styles.size} Estilos)"),
                style = MaterialTheme.typography.labelMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(CrosshairData.styles) { index, styleName ->
                    val isSelected = overlaySettings.crosshairStyle == index
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectStyle(index) },
                        leadingIcon = {
                            CrosshairStyleMiniIcon(
                                style = index,
                                color = if (isSelected) Color(activeColorLong) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = {
                            Text(
                                text = com.example.util.AppStrings.autoTranslateDynamic(styleName),
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                            selectedLabelColor = CyberCyan,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                            selectedBorderColor = CyberCyan,
                            borderWidth = 1.dp,
                            selectedBorderWidth = 1.5.dp
                        ),
                        modifier = Modifier.testTag("crosshair_style_chip_$index")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Reticle Neon Color Selector Row
            Text(
                text = com.example.util.tr("Reticle Neon Color (${CrosshairData.colors.size} Colors)", "Color Neón de Retícula (${CrosshairData.colors.size} Colores)"),
                style = MaterialTheme.typography.labelMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(CrosshairData.colors.size) { i ->
                    val (colorName, colorVal) = CrosshairData.colors[i]
                    val isSelected = overlaySettings.crosshairColor == colorVal
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(colorVal))
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                            .clickable { onSelectColor(colorVal) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = colorName,
                                tint = if (colorName == "White" || colorName == "Gold" || colorName == "Cyan" || colorName == "Green") Color.Black else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Size Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = com.example.util.tr("Reticle Size", "Tamaño de Retícula"),
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "${overlaySettings.crosshairSizeDp} dp",
                    style = MaterialTheme.typography.bodySmall.copy(color = CyberCyan, fontWeight = FontWeight.Bold)
                )
            }
            Slider(
                value = overlaySettings.crosshairSizeDp.toFloat(),
                onValueChange = { onSelectSize(it.toInt()) },
                valueRange = 16f..64f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(activeColorLong),
                    activeTrackColor = Color(activeColorLong),
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 3b. Stroke Thickness & Center Gap Sliders
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = com.example.util.tr("Stroke Width", "Grosor"),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = String.format(java.util.Locale.US, "%.1fdp", overlaySettings.strokeThicknessDp),
                            style = MaterialTheme.typography.labelSmall.copy(color = CyberCyan, fontWeight = FontWeight.Bold)
                        )
                    }
                    Slider(
                        value = overlaySettings.strokeThicknessDp,
                        onValueChange = { onSelectStroke(it) },
                        valueRange = 1.0f..6.0f,
                        modifier = Modifier.testTag("crosshair_stroke_slider")
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = com.example.util.tr("Center Gap", "Espacio Central"),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "${overlaySettings.centerGapDp.toInt()}dp",
                            style = MaterialTheme.typography.labelSmall.copy(color = CyberCyan, fontWeight = FontWeight.Bold)
                        )
                    }
                    Slider(
                        value = overlaySettings.centerGapDp,
                        onValueChange = { onSelectGap(it) },
                        valueRange = 0f..16f,
                        modifier = Modifier.testTag("crosshair_gap_slider")
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 3c. Fine X/Y Offset Calibration with 1px Nudge & Center Reset (0, 0)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = com.example.util.tr("Center Calibration & 1px Nudge", "Calibración Central y Ajuste de 1px"),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                )
                TextButton(
                    onClick = { onSelectOffset(0, 0) },
                    enabled = overlaySettings.offsetXdp != 0 || overlaySettings.offsetYdp != 0,
                    modifier = Modifier.testTag("crosshair_offset_reset_btn")
                ) {
                    Text(
                        text = com.example.util.tr("Reset Center (0, 0)", "Restablecer Centro (0, 0)"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (overlaySettings.offsetXdp != 0 || overlaySettings.offsetYdp != 0) CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = com.example.util.tr("X Offset", "Offset X"),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clickable {
                                        onSelectOffset((overlaySettings.offsetXdp - 1).coerceIn(-24, 24), overlaySettings.offsetYdp)
                                    }
                                    .testTag("crosshair_nudge_x_minus")
                            ) {
                                Text(
                                    text = "-1",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "${overlaySettings.offsetXdp}dp",
                                style = MaterialTheme.typography.labelSmall.copy(color = SolarGold, fontWeight = FontWeight.Bold)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clickable {
                                        onSelectOffset((overlaySettings.offsetXdp + 1).coerceIn(-24, 24), overlaySettings.offsetYdp)
                                    }
                                    .testTag("crosshair_nudge_x_plus")
                            ) {
                                Text(
                                    text = "+1",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Slider(
                        value = overlaySettings.offsetXdp.toFloat(),
                        onValueChange = { onSelectOffset(it.toInt(), overlaySettings.offsetYdp) },
                        valueRange = -24f..24f
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = com.example.util.tr("Y Offset", "Offset Y"),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clickable {
                                        onSelectOffset(overlaySettings.offsetXdp, (overlaySettings.offsetYdp - 1).coerceIn(-24, 24))
                                    }
                                    .testTag("crosshair_nudge_y_minus")
                            ) {
                                Text(
                                    text = "-1",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "${overlaySettings.offsetYdp}dp",
                                style = MaterialTheme.typography.labelSmall.copy(color = SolarGold, fontWeight = FontWeight.Bold)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clickable {
                                        onSelectOffset(overlaySettings.offsetXdp, (overlaySettings.offsetYdp + 1).coerceIn(-24, 24))
                                    }
                                    .testTag("crosshair_nudge_y_plus")
                            ) {
                                Text(
                                    text = "+1",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Slider(
                        value = overlaySettings.offsetYdp.toFloat(),
                        onValueChange = { onSelectOffset(overlaySettings.offsetXdp, it.toInt()) },
                        valueRange = -24f..24f
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = overlaySettings.showOutline,
                    onClick = { onToggleOutline(!overlaySettings.showOutline) },
                    label = {
                        Text(
                            text = com.example.util.tr("Dark Outline", "Borde Oscuro"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
                FilterChip(
                    selected = overlaySettings.showCenterDot,
                    onClick = { onToggleCenterDot(!overlaySettings.showCenterDot) },
                    label = {
                        Text(
                            text = com.example.util.tr("Center Dot", "Punto Central"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }

            // 4. Permission grant prompt if overlay permission missing
            if (!canDraw) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { SystemSettingsHelper.openOverlayPermissionSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SolarGold)
                ) {
                    Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(com.example.util.tr("Grant 'Display Over Other Apps' Permission", "Conceder Permiso de 'Mostrar sobre otras apps'"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Quick access banner for users needing to enable Android Developer Mode for advanced tweaks.
 */
@Composable
fun DeveloperModeHelperBanner(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, CyberCyan.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(CyberCyan.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DeveloperMode,
                contentDescription = null,
                tint = CyberCyan,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = com.example.util.tr("Developer Mode Required for System Tweaks", "Modo Desarrollador Requerido para Ajustes del Sistema"),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = com.example.util.tr("Tap for 4 step tutorial with direct settings shortcuts.", "Toca para el tutorial de 4 pasos con accesos directos a ajustes."),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            )
        }
        Icon(
            imageVector = Icons.Default.OpenInNew,
            contentDescription = "Open",
            tint = CyberCyan,
            modifier = Modifier.size(16.dp)
        )
    }
}

