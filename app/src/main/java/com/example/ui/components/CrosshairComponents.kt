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
 * High-precision Compose Canvas rendering for all 8 crosshair reticle designs.
 */
@Composable
fun CrosshairPreview(
    style: Int,
    color: Color,
    sizeDp: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(sizeDp.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val w = size.width
        val r = w / 2f
        val strokeW = (w * 0.08f).coerceAtLeast(3.dp.toPx())
        val shadowW = strokeW + 2.dp.toPx()
        val shadowColor = Color.Black.copy(alpha = 0.7f)

        when (style) {
            0 -> {
                // Classic Cross (+) with center gap
                val arm = r * 0.90f
                val gap = r * 0.22f
                // Shadows
                drawLine(shadowColor, Offset(cx, cy - arm), Offset(cx, cy - gap), strokeWidth = shadowW, cap = StrokeCap.Round)
                drawLine(shadowColor, Offset(cx, cy + gap), Offset(cx, cy + arm), strokeWidth = shadowW, cap = StrokeCap.Round)
                drawLine(shadowColor, Offset(cx - arm, cy), Offset(cx - gap, cy), strokeWidth = shadowW, cap = StrokeCap.Round)
                drawLine(shadowColor, Offset(cx + gap, cy), Offset(cx + arm, cy), strokeWidth = shadowW, cap = StrokeCap.Round)
                // Core
                drawLine(color, Offset(cx, cy - arm), Offset(cx, cy - gap), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx, cy + gap), Offset(cx, cy + arm), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx - arm, cy), Offset(cx - gap, cy), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx + gap, cy), Offset(cx + arm, cy), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawCircle(color, radius = strokeW * 0.5f, center = Offset(cx, cy))
            }
            1 -> {
                // Precision Dot (•)
                val dotR = (r * 0.35f).coerceAtLeast(4.dp.toPx())
                drawCircle(shadowColor, radius = dotR + 1.5.dp.toPx(), center = Offset(cx, cy))
                drawCircle(color, radius = dotR, center = Offset(cx, cy))
            }
            2 -> {
                // Circle + Dot (⊙)
                val circleR = r * 0.72f
                val dotR = (r * 0.22f).coerceAtLeast(3.dp.toPx())
                drawCircle(shadowColor, radius = circleR, center = Offset(cx, cy), style = Stroke(width = shadowW))
                drawCircle(color, radius = circleR, center = Offset(cx, cy), style = Stroke(width = strokeW))
                drawCircle(color, radius = dotR, center = Offset(cx, cy))
            }
            3 -> {
                // Tactical Chevron (^)
                val path = Path().apply {
                    moveTo(cx - r * 0.65f, cy + r * 0.35f)
                    lineTo(cx, cy - r * 0.55f)
                    lineTo(cx + r * 0.65f, cy + r * 0.35f)
                }
                drawPath(path, shadowColor, style = Stroke(width = shadowW, cap = StrokeCap.Round))
                drawPath(path, color, style = Stroke(width = strokeW, cap = StrokeCap.Round))
                drawLine(shadowColor, Offset(cx, cy + r * 0.15f), Offset(cx, cy + r * 0.70f), strokeWidth = shadowW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx, cy + r * 0.15f), Offset(cx, cy + r * 0.70f), strokeWidth = strokeW, cap = StrokeCap.Round)
            }
            4 -> {
                // T-Pose (⊤)
                val arm = r * 0.85f
                val gap = r * 0.18f
                drawLine(shadowColor, Offset(cx - arm, cy), Offset(cx - gap, cy), strokeWidth = shadowW, cap = StrokeCap.Round)
                drawLine(shadowColor, Offset(cx + gap, cy), Offset(cx + arm, cy), strokeWidth = shadowW, cap = StrokeCap.Round)
                drawLine(shadowColor, Offset(cx, cy + gap), Offset(cx, cy + arm), strokeWidth = shadowW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx - arm, cy), Offset(cx - gap, cy), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx + gap, cy), Offset(cx + arm, cy), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(cx, cy + gap), Offset(cx, cy + arm), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawCircle(color, radius = strokeW * 0.5f, center = Offset(cx, cy))
            }
            5 -> {
                // Sniper Mil-Dot (⌖)
                val outerR = r * 0.85f
                val arm = r * 0.95f
                val gap = r * 0.15f
                drawCircle(shadowColor, radius = outerR, center = Offset(cx, cy), style = Stroke(width = shadowW))
                drawCircle(color, radius = outerR, center = Offset(cx, cy), style = Stroke(width = strokeW))

                drawLine(color, Offset(cx, cy - arm), Offset(cx, cy - gap), strokeWidth = strokeW)
                drawLine(color, Offset(cx, cy + gap), Offset(cx, cy + arm), strokeWidth = strokeW)
                drawLine(color, Offset(cx - arm, cy), Offset(cx - gap, cy), strokeWidth = strokeW)
                drawLine(color, Offset(cx + gap, cy), Offset(cx + arm, cy), strokeWidth = strokeW)

                val dotD = outerR * 0.45f
                drawCircle(color, radius = strokeW * 0.6f, center = Offset(cx - dotD, cy))
                drawCircle(color, radius = strokeW * 0.6f, center = Offset(cx + dotD, cy))
                drawCircle(color, radius = strokeW * 0.6f, center = Offset(cx, cy - dotD))
                drawCircle(color, radius = strokeW * 0.6f, center = Offset(cx, cy + dotD))
                drawCircle(color, radius = strokeW * 0.5f, center = Offset(cx, cy))
            }
            6 -> {
                // Diamond Box (◇)
                val d = r * 0.65f
                val path = Path().apply {
                    moveTo(cx, cy - d)
                    lineTo(cx + d, cy)
                    lineTo(cx, cy + d)
                    lineTo(cx - d, cy)
                    close()
                }
                drawPath(path, shadowColor, style = Stroke(width = shadowW))
                drawPath(path, color, style = Stroke(width = strokeW))
                drawCircle(color, radius = strokeW * 0.6f, center = Offset(cx, cy))
            }
            else -> {
                // Circle + Cross (⊕)
                val circleR = r * 0.70f
                val arm = r * 0.95f
                drawCircle(shadowColor, radius = circleR, center = Offset(cx, cy), style = Stroke(width = shadowW))
                drawCircle(color, radius = circleR, center = Offset(cx, cy), style = Stroke(width = strokeW))
                drawLine(color, Offset(cx, cy - arm), Offset(cx, cy + arm), strokeWidth = strokeW)
                drawLine(color, Offset(cx - arm, cy), Offset(cx + arm, cy), strokeWidth = strokeW)
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
                text = "Anti-Cheat Policy Notice",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
            )
        },
        text = {
            Column {
                Text(
                    text = "Certain online multiplayer games strictly prohibit screen overlay utilities, custom crosshairs, or external reticles under their Fair Play terms.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFD1D5DB))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Enabling this feature in competitive games may lead to account penalties or permanent bans by game publishers. Do you understand and wish to activate it?",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFFDBA74), fontWeight = FontWeight.SemiBold)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("I Understand & Proceed", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF9CA3AF))
            }
        },
        containerColor = Color(0xFF141824)
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
                text = "Activate Android Developer Mode",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Status Pill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDevActive) Color(0xFF064E3B) else Color(0xFF374151))
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
                            text = if (isDevActive) "Developer Mode is ACTIVE on this phone" else "Developer Mode is NOT yet activated",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isDevActive) NeonGreen else Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Follow these 4 quick steps:",
                    style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Step 1
                TutorialStepRow(
                    stepNumber = "1",
                    title = "Open 'About Phone'",
                    description = "Open Android Settings and navigate to About Phone (or About Device)."
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Step 2
                TutorialStepRow(
                    stepNumber = "2",
                    title = "Locate 'Build Number'",
                    description = "Find 'Build Number'. (On Samsung: Software Info > Build Number; on Xiaomi: Tap 'OS / MIUI Version')."
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Step 3
                TutorialStepRow(
                    stepNumber = "3",
                    title = "Tap Build Number 7 Times",
                    description = "Repeatedly tap Build Number 7 times. Enter your lockscreen PIN if prompted until Android toasts: 'You are now a developer!'."
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Step 4
                TutorialStepRow(
                    stepNumber = "4",
                    title = "Access Developer Options",
                    description = "Developer Options will now appear in Settings > System (or Additional Settings)."
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Launch Buttons
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
                        Text("1. Open About", fontSize = 11.sp, color = CyberCyan)
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
                        Text("2. Open Dev Opts", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Got It", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color(0xFF141824)
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
                    color = Color.Black,
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
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF9CA3AF),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            )
        }
    }
}

/**
 * Dedicated, complete interactive Tactical Crosshair Customization Card.
 * Includes live canvas preview, 8 reticle design chips, 8 neon colors, size slider,
 * anti-cheat ban warning banner, and master overlay activation toggle.
 */
@Composable
fun TacticalCrosshairCard(
    overlaySettings: OverlaySettings,
    onToggle: (Boolean) -> Unit,
    onSelectStyle: (Int) -> Unit,
    onSelectColor: (Long) -> Unit,
    onSelectSize: (Int) -> Unit,
    onShowAntiCheatNotice: () -> Unit,
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
                if (overlaySettings.isEnabled) Color(activeColorLong).copy(alpha = 0.7f) else DarkSurfaceBorder,
                RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131826)),
        shape = RoundedCornerShape(16.dp)
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
                                if (overlaySettings.isEnabled) Color(activeColorLong).copy(alpha = 0.2f) else Color(0xFF1E2433)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Adjust,
                            contentDescription = null,
                            tint = if (overlaySettings.isEnabled) Color(activeColorLong) else CyberCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Tactical Screen Crosshair",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 16.sp
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF0F3B4A))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "TWEAK",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = CyberCyan
                                )
                            }
                        }
                        Text(
                            text = if (overlaySettings.isEnabled) "ACTIVE • Touch-Through Pass Active" else "Off • 8 Reticle Designs & 8 Colors",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (overlaySettings.isEnabled) NeonGreen else TextSecondary,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }

                Switch(
                    checked = overlaySettings.isEnabled,
                    onCheckedChange = { targetState ->
                        if (targetState && !overlaySettings.antiCheatNoticeAcknowledged) {
                            onShowAntiCheatNotice()
                        } else {
                            onToggle(targetState)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(activeColorLong).copy(alpha = 0.8f),
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color(0xFF1E2638)
                    ),
                    modifier = Modifier.testTag("crosshair_tweak_card_toggle")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reticle Live Preview Box with subtle crosshair grid
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF070A12))
                    .border(1.dp, Color(0xFF1B2436), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Subtle Center Grid Lines
                Canvas(modifier = Modifier.matchParentSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    drawLine(
                        color = Color(0x22FFFFFF),
                        start = Offset(0f, cy),
                        end = Offset(size.width, cy),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = Color(0x22FFFFFF),
                        start = Offset(cx, 0f),
                        end = Offset(cx, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Render Active Crosshair
                CrosshairPreview(
                    style = overlaySettings.crosshairStyle,
                    color = Color(activeColorLong).copy(alpha = overlaySettings.crosshairAlpha),
                    sizeDp = overlaySettings.crosshairSizeDp
                )

                // Reticle Info Pill
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$activeStyleName • $activeColorName (${overlaySettings.crosshairSizeDp}dp)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Reticle Style Selector Chips
            Text(
                text = "Reticle Design (${CrosshairData.styles.size} Styles)",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = TextSecondary,
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
                        label = {
                            Text(
                                text = styleName,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberCyan.copy(alpha = 0.25f),
                            selectedLabelColor = CyberCyan,
                            containerColor = Color(0xFF161E2E),
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = Color(0xFF263248),
                            selectedBorderColor = CyberCyan,
                            borderWidth = 1.dp,
                            selectedBorderWidth = 1.5.dp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Reticle Neon Color Selector Row
            Text(
                text = "Reticle Neon Color (${CrosshairData.colors.size} Colors)",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = TextSecondary,
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
                                color = if (isSelected) Color.White else Color(0x44FFFFFF),
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
                    text = "Reticle Size",
                    style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.Bold)
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
                    inactiveTrackColor = DarkSurfaceVariant
                )
            )

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
                    Text("Grant 'Display Over Other Apps' Permission", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            .background(Color(0xFF1A2338))
            .border(1.dp, Color(0xFF2E3E66), RoundedCornerShape(12.dp))
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
                text = "Developer Mode Required for System Tweaks",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Tap for step-by-step 4-step tutorial with direct settings shortcuts.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF9CA3AF),
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

