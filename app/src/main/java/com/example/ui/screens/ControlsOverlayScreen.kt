package com.example.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.model.GamepadDevice
import com.example.ui.components.AntiCheatNoticeDialog
import com.example.ui.components.CrosshairData
import com.example.ui.components.CrosshairPreview
import com.example.ui.components.CyberCard
import com.example.ui.components.SectionTitle
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCrimson
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.SolarGold
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.SystemSettingsHelper
import com.example.viewmodel.GameBoosterViewModel

@Composable
fun ControlsOverlayScreen(
    viewModel: GameBoosterViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val overlaySettings by viewModel.overlaySettings.collectAsState()
    val overlayConfig by viewModel.overlayConfig.collectAsState()
    val gamepads by viewModel.gamepads.collectAsState()
    var showAntiCheatNotice by remember { mutableStateOf(false) }

    if (showAntiCheatNotice) {
        AntiCheatNoticeDialog(
            onConfirm = {
                viewModel.acknowledgeAntiCheatNotice()
                showAntiCheatNotice = false
                viewModel.setCrosshairEnabled(context, true)
            },
            onDismiss = {
                showAntiCheatNotice = false
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Floating Gaming HUD Master Toggle
        item {
            CyberCard(borderColor = if (overlayConfig.isEnabled) CyberCyan else DarkSurfaceBorder) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Floating Gaming HUD Overlay",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Renders a movable HUD overlay with FPS, battery temperature & gauge over all games.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }

                    Switch(
                        checked = overlayConfig.isEnabled,
                        onCheckedChange = { viewModel.toggleOverlayEnabled(context, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CyberCyan
                        ),
                        modifier = Modifier.testTag("floating_overlay_toggle")
                    )
                }

                if (!SystemSettingsHelper.canDrawOverlays(context)) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { SystemSettingsHelper.openOverlayPermissionSettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Grant 'Display Over Other Apps' Permission", color = SolarGold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Crosshair Customizer & Live Preview Box
        item {
            CyberCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        SectionTitle(
                            title = "Tactical Screen Crosshair",
                            subtitle = "Draws an on-screen reticle overlay (Touch-through active)"
                        )
                    }

                    Switch(
                        checked = overlaySettings.isEnabled,
                        onCheckedChange = { targetState ->
                            if (targetState && !overlaySettings.antiCheatNoticeAcknowledged) {
                                showAntiCheatNotice = true
                            } else {
                                viewModel.setCrosshairEnabled(context, targetState)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CyberCyan
                        ),
                        modifier = Modifier.testTag("crosshair_tweak_toggle")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Live Preview Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF070A12))
                        .border(1.dp, Color(0xFF1E283D), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    CrosshairPreview(
                        style = overlaySettings.crosshairStyle,
                        color = Color(overlaySettings.crosshairColor),
                        sizeDp = overlaySettings.crosshairSizeDp
                    )
                    Text(
                        text = "LIVE RETICLE PREVIEW",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0x66FFFFFF),
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Style Selector Chips (8 distinct reticles)
                Text("Reticle Design (${CrosshairData.styles.size} Styles)", style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(CrosshairData.styles) { index, styleName ->
                        FilterChip(
                            selected = overlaySettings.crosshairStyle == index,
                            onClick = { viewModel.updateCrosshairStyle(index) },
                            label = { Text(styleName, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                                selectedLabelColor = CyberCyan
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Color Picker Row (8 neon colors)
                Text("Reticle Color (${CrosshairData.colors.size} Colors)", style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(CrosshairData.colors.size) { i ->
                        val (name, colorVal) = CrosshairData.colors[i]
                        val isSelected = overlaySettings.crosshairColor == colorVal
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(colorVal))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color.White else DarkSurfaceBorder,
                                    shape = CircleShape
                                )
                                .clickable { viewModel.updateCrosshairColor(colorVal) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Size Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Reticle Size", style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
                    Text("${overlaySettings.crosshairSizeDp} dp", style = MaterialTheme.typography.bodySmall.copy(color = CyberCyan, fontWeight = FontWeight.Bold))
                }
                Slider(
                    value = overlaySettings.crosshairSizeDp.toFloat(),
                    onValueChange = { viewModel.updateCrosshairSize(it.toInt()) },
                    valueRange = 16f..64f,
                    colors = SliderDefaults.colors(
                        thumbColor = CyberCyan,
                        activeTrackColor = CyberCyan,
                        inactiveTrackColor = DarkSurfaceVariant
                    )
                )
            }
        }

        // Gamepad Detection & Controller Mapping Guide (Section 8)
        item {
            CyberCard(borderColor = if (gamepads.isNotEmpty()) NeonGreen.copy(alpha = 0.5f) else DarkSurfaceBorder) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle(
                        title = "Connected Gamepads & Controllers",
                        subtitle = "Hardware gamepad detector (Bluetooth / USB-C / OTG)",
                        badge = if (gamepads.isNotEmpty()) "${gamepads.size} DETECTED" else "SCANNING",
                        badgeColor = if (gamepads.isNotEmpty()) NeonGreen else SolarGold
                    )
                    IconButton(onClick = { viewModel.refreshGamepads() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = CyberCyan)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (gamepads.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "No physical gamepads connected. Connect an Xbox Wireless, PlayStation DualSense, or USB controller via Bluetooth or USB-C OTG.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (gp in gamepads) {
                            GamepadItem(gamepad = gp)
                        }
                    }
                }
            }
        }

        // Audio Latency & Touch Response Guide (Section 8)
        item {
            CyberCard {
                SectionTitle(
                    title = "Audio Latency & Touch Polling Guide",
                    subtitle = "Latency hierarchies for competitive reaction time"
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LatencyTierRow(
                        tier = "Tier S: Wired 3.5mm / Type-C DAC",
                        latency = "0 - 5 ms",
                        quality = "Zero perceived latency. Recommended for competitive shooters (PUBG, COD).",
                        color = NeonGreen
                    )
                    LatencyTierRow(
                        tier = "Tier A: 2.4GHz RF USB Dongle",
                        latency = "15 - 25 ms",
                        quality = "Near-instant wireless audio using dedicated low-loss RF receiver.",
                        color = CyberCyan
                    )
                    LatencyTierRow(
                        tier = "Tier B: Bluetooth aptX Low Latency / LHDC",
                        latency = "40 - 80 ms",
                        quality = "Acceptable latency. Requires matching codec support in Developer Options.",
                        color = SolarGold
                    )
                    LatencyTierRow(
                        tier = "Tier C: Standard Bluetooth SBC / AAC",
                        latency = "150 - 250 ms",
                        quality = "Noticeable delay. Gunshot and footstep sounds arrive ~1/5th second late.",
                        color = ElectricCrimson
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { SystemSettingsHelper.openDeveloperOptions(context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Headphones, contentDescription = null, tint = CyberCyan)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Developer Options: Bluetooth Audio Codec Settings", color = TextPrimary)
                }
            }
        }
    }
}

@Composable
fun GamepadItem(gamepad: GamepadDevice) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceVariant)
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.example.ui.components.GMaxBrandIcon(
                size = 26.dp,
                cornerRadius = 7.dp,
                showGlowBorder = true,
                glowColor = NeonGreen
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = gamepad.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                )
                Text(
                    text = "Vendor ID: 0x${gamepad.vendorId.toString(16)} • Product ID: 0x${gamepad.productId.toString(16)}",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(NeonGreen.copy(alpha = 0.2f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                "CONNECTED",
                style = MaterialTheme.typography.labelSmall.copy(color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 9.sp)
            )
        }
    }
}

@Composable
fun LatencyTierRow(tier: String, latency: String, quality: String, color: Color) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(tier, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
            Text(latency, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = color))
        }
        Text(quality, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
    }
}
