package com.example.ui.screens.extras

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.model.MatchRecap
import com.example.ui.components.CyberCard
import com.example.ui.components.SectionTitle
import com.example.ui.screens.ExtrasTelemetryRow
import com.example.ui.theme.ElectricCrimson
import com.example.ui.theme.HubBlue
import com.example.ui.theme.LaunchGreen
import com.example.ui.theme.SolarGold
import com.example.util.SystemSettingsHelper
import com.example.viewmodel.GameBoosterViewModel
import java.util.Locale

@Composable
fun PerformanceTab(
    viewModel: GameBoosterViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val overlayConfig by viewModel.overlayConfig.collectAsState()
    val matchRecap by viewModel.matchRecap.collectAsState()
    val isRecording by viewModel.isMatchRecording.collectAsState()
    val thermalGuardState by viewModel.thermalGuardState.collectAsState()
    val canDrawOverlay = remember(context) { SystemSettingsHelper.canDrawOverlays(context) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("performance_tab_container"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Thermal Throttle Guard Card
        item {
            CyberCard(
                borderColor = if (thermalGuardState.isThrottling) ElectricCrimson else HubBlue.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        SectionTitle(
                            title = "Thermal Throttle Guard",
                            subtitle = "Real kernel thermal state & SoC throttle defense"
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (thermalGuardState.isThrottling) ElectricCrimson.copy(alpha = 0.16f)
                                else LaunchGreen.copy(alpha = 0.15f)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (thermalGuardState.isThrottling) "THROTTLING DETECTED" else "OPTIMAL COOLING",
                            color = if (thermalGuardState.isThrottling) ElectricCrimson else LaunchGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                thermalGuardState.activeThrottleAlert?.let { alertMsg ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ElectricCrimson.copy(alpha = 0.14f))
                            .border(1.dp, ElectricCrimson, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                            .testTag("thermal_throttle_alert_banner")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = ElectricCrimson,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = alertMsg,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.dismissThermalAlert() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThermalMetricBox(
                        modifier = Modifier.weight(1f),
                        label = "HARDWARE TEMP",
                        value = String.format(Locale.US, "%.1f°C", thermalGuardState.liveTempC),
                        accent = if (thermalGuardState.liveTempC > 40f) ElectricCrimson else SolarGold
                    )
                    ThermalMetricBox(
                        modifier = Modifier.weight(1.2f),
                        label = "OS STATUS",
                        value = thermalGuardState.thermalStatusName,
                        accent = if (thermalGuardState.isThrottling) ElectricCrimson else HubBlue
                    )
                    ThermalMetricBox(
                        modifier = Modifier.weight(1f),
                        label = "HEADROOM",
                        value = String.format(Locale.US, "%.2f", thermalGuardState.thermalHeadroom),
                        accent = if (thermalGuardState.thermalHeadroom < 0.6f) ElectricCrimson else LaunchGreen
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Monitors PowerManager kernel thermal listeners (API 29+) and correlates thermal events with in-game frame pacing to protect SoC silicon from sustained heat degradation.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                )
            }
        }

        // 2. Post-Match Performance Recap Card
        item {
            CyberCard(borderColor = HubBlue.copy(alpha = 0.5f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        SectionTitle(
                            title = "Post-Match Performance Recap",
                            subtitle = "Real FPS, 1% lows, stutters, and thermal curves"
                        )
                    }
                    if (isRecording) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(LaunchGreen.copy(alpha = 0.16f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "● REC ACTIVE",
                                color = LaunchGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MatchStatBox(
                        modifier = Modifier.weight(1f),
                        label = "AVG FPS",
                        value = "${matchRecap.averageFps}",
                        accent = HubBlue
                    )
                    MatchStatBox(
                        modifier = Modifier.weight(1f),
                        label = "1% LOWS",
                        value = "${matchRecap.low1PercentFps}",
                        accent = if (matchRecap.low1PercentFps >= 45f) LaunchGreen else SolarGold
                    )
                    MatchStatBox(
                        modifier = Modifier.weight(1f),
                        label = "STABILITY",
                        value = "${matchRecap.stabilityScore}%",
                        accent = Color(0xFFA855F7)
                    )
                    MatchStatBox(
                        modifier = Modifier.weight(1f),
                        label = "PEAK TEMP",
                        value = String.format(Locale.US, "%.1f°C", matchRecap.maxTempC),
                        accent = if (matchRecap.maxTempC > 41f) ElectricCrimson else SolarGold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Performance Dynamics (${matchRecap.gameTitle}):",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    MatchRecapCurvesCanvas(
                        recap = matchRecap,
                        gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Battery Drain: -${matchRecap.batteryDrained}% (${matchRecap.startBatteryPct}% → ${matchRecap.endBatteryPct}%)",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    )
                    Text(
                        text = "Stutters: ${matchRecap.stutterCount} drops",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (matchRecap.stutterCount > 5) ElectricCrimson else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isRecording) {
                        Button(
                            onClick = { viewModel.finishManualMatchRecap() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("finish_match_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCrimson, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("FINISH MATCH", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.startManualMatchRecap() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("start_match_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = HubBlue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("START RECAP", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = { viewModel.shareMatchCard(context, matchRecap) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("share_match_card_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = HubBlue, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SHARE CARD", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 3. Screen Overlay HUD Controls
        item {
            CyberCard(borderColor = if (overlayConfig.isEnabled) LaunchGreen else MaterialTheme.colorScheme.outline) {
                SectionTitle(
                    title = "Screen Overlay HUD Controls",
                    subtitle = "Real-time floating metrics for FPS, battery, and thermals"
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (!canDrawOverlay) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SolarGold.copy(alpha = 0.14f))
                            .border(1.dp, SolarGold.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = SolarGold, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Requires 'Display over other apps' permission to stay visible over games.",
                                style = MaterialTheme.typography.bodySmall.copy(color = SolarGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = if (overlayConfig.isEnabled) LaunchGreen else HubBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Floating Screen Overlay",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = if (overlayConfig.isEnabled) "HUD is active and draggable on screen" else "Currently inactive",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (overlayConfig.isEnabled) LaunchGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    Switch(
                        checked = overlayConfig.isEnabled,
                        onCheckedChange = { viewModel.toggleOverlayEnabled(context, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = LaunchGreen,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.testTag("performance_overlay_master_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Overlay Telemetry Metrics:",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExtrasTelemetryRow(
                        icon = Icons.Default.Speed,
                        label = "In-Game Frame Rate (96%–98% EFPS)",
                        description = "6-Way Hardware Telemetry Fusion (VRR + VSYNC + GPU Swap + Game Cap + CPU/Thermal)",
                        isChecked = overlayConfig.showFps,
                        onCheckedChange = { viewModel.toggleOverlayFps(context, it) },
                        accentColor = HubBlue
                    )

                    ExtrasTelemetryRow(
                        icon = Icons.Default.Thermostat,
                        label = "Hardware Battery Thermals",
                        description = "Genuine Android battery broadcast temperature",
                        isChecked = overlayConfig.showTemp,
                        onCheckedChange = { viewModel.toggleOverlayTemp(context, it) },
                        accentColor = SolarGold
                    )

                    ExtrasTelemetryRow(
                        icon = Icons.Default.BatteryChargingFull,
                        label = "Battery Percentage & Health",
                        description = "Genuine battery capacity and charge state",
                        isChecked = overlayConfig.showBattery,
                        onCheckedChange = { viewModel.toggleOverlayBattery(context, it) },
                        accentColor = LaunchGreen
                    )

                    ExtrasTelemetryRow(
                        icon = Icons.Default.Timer,
                        label = "Booster Session Countdown",
                        description = "Live session timer and RAM maintenance feedback",
                        isChecked = overlayConfig.showTimer,
                        onCheckedChange = { viewModel.toggleOverlayTimer(context, it) },
                        accentColor = Color(0xFFA855F7)
                    )
                }
            }
        }
    }
}

@Composable
private fun ThermalMetricBox(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accent: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MatchStatBox(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accent: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            )
        }
    }
}

@Composable
private fun MatchRecapCurvesCanvas(recap: MatchRecap, gridColor: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val history = recap.telemetryHistory
        if (history.isEmpty()) {
            drawLine(
                color = gridColor,
                start = Offset(0f, height / 2f),
                end = Offset(width, height / 2f),
                strokeWidth = 2f
            )
            return@Canvas
        }

        val fpsPoints = history.map { it.fps }
        val maxFps = (fpsPoints.maxOrNull() ?: 60).coerceAtLeast(60).toFloat()
        val minFps = (fpsPoints.minOrNull() ?: 30).coerceAtMost(30).toFloat()

        val maxTemp = 48f
        val minTemp = 28f

        val fpsPath = Path()
        val tempPath = Path()

        history.forEachIndexed { index, point ->
            val x = (index.toFloat() / (history.size - 1).coerceAtLeast(1)) * width

            val normFps = ((point.fps - minFps) / (maxFps - minFps)).coerceIn(0f, 1f)
            val yFps = height - (normFps * (height - 16f)) - 8f
            if (index == 0) fpsPath.moveTo(x, yFps) else fpsPath.lineTo(x, yFps)

            val normTemp = ((point.tempC - minTemp) / (maxTemp - minTemp)).coerceIn(0f, 1f)
            val yTemp = height - (normTemp * (height - 16f)) - 8f
            if (index == 0) tempPath.moveTo(x, yTemp) else tempPath.lineTo(x, yTemp)
        }

        drawLine(
            color = gridColor,
            start = Offset(0f, height * 0.25f),
            end = Offset(width, height * 0.25f),
            strokeWidth = 1f
        )
        drawLine(
            color = gridColor,
            start = Offset(0f, height * 0.75f),
            end = Offset(width, height * 0.75f),
            strokeWidth = 1f
        )

        drawPath(
            path = fpsPath,
            color = HubBlue,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        drawPath(
            path = tempPath,
            color = Color(0xFFFF9100),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
