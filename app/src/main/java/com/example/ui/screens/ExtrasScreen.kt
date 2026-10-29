package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.CyberCard
import com.example.ui.components.SectionTitle
import com.example.ui.screens.extras.ControllerTab
import com.example.ui.screens.extras.PerformanceTab
import com.example.ui.screens.extras.TouchTab
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricCrimson
import com.example.ui.theme.HubBlue
import com.example.ui.theme.LaunchGreen
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.SolarGold
import com.example.util.SystemSettingsHelper
import com.example.viewmodel.GameBoosterViewModel
import java.util.Locale

@Composable
fun ExtrasScreen(
    viewModel: GameBoosterViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val readiness by viewModel.readiness.collectAsState()
    val storageSpeed by viewModel.storageSpeed.collectAsState()
    val isSpeedTesting by viewModel.isSpeedTesting.collectAsState()
    val thermalInfo by viewModel.thermalInfo.collectAsState()
    val hardwareSpecs by viewModel.hardwareSpecs.collectAsState()
    val overlayConfig by viewModel.overlayConfig.collectAsState()
    val efpsTelemetry by viewModel.efpsTelemetry.collectAsState()

    var selectedSubTab by remember { mutableIntStateOf(0) }

    val canDrawOverlay = remember(context) {
        SystemSettingsHelper.canDrawOverlays(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Horizontal Scrollable Tab Navigation for Extras: [ Tweaks ] | [ Performance ] | [ Touch ] | [ Controller ] | [ Specs ] | [ FPS & Diag ]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ExtrasSubTabPill("Tweaks", selectedSubTab == 0, "extras_tab_tweaks") { selectedSubTab = 0 }
            ExtrasSubTabPill("Performance", selectedSubTab == 1, "extras_tab_performance") { selectedSubTab = 1 }
            ExtrasSubTabPill("Touch", selectedSubTab == 2, "extras_tab_touch") { selectedSubTab = 2 }
            ExtrasSubTabPill("Controller", selectedSubTab == 3, "extras_tab_controller") { selectedSubTab = 3 }
            ExtrasSubTabPill("Specs", selectedSubTab == 4, "extras_tab_specs") { selectedSubTab = 4 }
            ExtrasSubTabPill("FPS & Diag", selectedSubTab == 5, "extras_tab_diagnostics") { selectedSubTab = 5 }
        }

        when (selectedSubTab) {
            0 -> {
                OptionsScreen(viewModel = viewModel)
            }
            1 -> {
                PerformanceTab(viewModel = viewModel)
            }
            2 -> {
                TouchTab(viewModel = viewModel)
            }
            3 -> {
                ControllerTab(viewModel = viewModel)
            }
            4 -> {
                // Specs: Phone hardware specs, accurate battery thermals, display refresh rate
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Device Readiness Score Banner
                    item {
                        val scoreColor = when {
                            readiness.score >= 85 -> LaunchGreen
                            readiness.score >= 65 -> SolarGold
                            else -> ElectricCrimson
                        }

                        CyberCard(borderColor = scoreColor.copy(alpha = 0.5f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    CircularProgressIndicator(
                                        progress = { readiness.score / 100f },
                                        color = scoreColor,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        strokeWidth = 7.dp,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Text(
                                        text = "${readiness.score}",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Device Gaming Readiness",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    Text(
                                        text = readiness.status,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = scoreColor,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (readiness.warnings.isNotEmpty()) readiness.warnings.first() else "Hardware headroom is primed for smooth competitive play.",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // 2. Section: Phone Hardware Specifications
                    item {
                        CyberCard(borderColor = HubBlue.copy(alpha = 0.5f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                SectionTitle(
                                    title = "Phone Hardware Specifications",
                                    subtitle = "Genuine detected hardware parameters"
                                )
                                IconButton(onClick = { viewModel.loadHardwareSpecs() }) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh Specs",
                                        tint = HubBlue
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                HardwareSpecRowItem(
                                    icon = Icons.Default.DeveloperBoard,
                                    label = "Processor / SoC",
                                    value = "${hardwareSpecs.socModel} (${hardwareSpecs.cpuCores} Cores • ${hardwareSpecs.cpuAbi})",
                                    accent = HubBlue
                                )

                                HardwareSpecRowItem(
                                    icon = Icons.Default.Speed,
                                    label = "Graphics & 3D Engine",
                                    value = "${hardwareSpecs.gpuRenderer} • ${hardwareSpecs.openGlVersion}",
                                    accent = SolarGold
                                )

                                HardwareSpecRowItem(
                                    icon = Icons.Default.Thermostat,
                                    label = "Hardware Battery Thermals",
                                    value = "${String.format(Locale.US, "%.1f°C", thermalInfo.batteryTempC)} (${thermalInfo.batteryHealth} • ${thermalInfo.batteryLevel}% Level)",
                                    accent = if (thermalInfo.batteryTempC < 38f) LaunchGreen else SolarGold
                                )

                                HardwareSpecRowItem(
                                    icon = Icons.Default.Memory,
                                    label = "System RAM Headroom",
                                    value = "${hardwareSpecs.availRamMb} MB Available of ${hardwareSpecs.totalRamMb} MB",
                                    accent = LaunchGreen
                                )

                                HardwareSpecRowItem(
                                    icon = Icons.Default.Storage,
                                    label = "Internal Flash Storage",
                                    value = "${String.format(Locale.US, "%.1f", hardwareSpecs.freeStorageGb)} GB Free of ${String.format(Locale.US, "%.1f", hardwareSpecs.totalStorageGb)} GB Total",
                                    accent = Color(0xFFA855F7)
                                )

                                HardwareSpecRowItem(
                                    icon = Icons.Default.Tv,
                                    label = "Display & Refresh Rate",
                                    value = "${hardwareSpecs.displayResolution} @ ${hardwareSpecs.refreshRateHz} Hz (${hardwareSpecs.screenDensityDpi} DPI)",
                                    accent = Color(0xFF0EA5E9)
                                )

                                HardwareSpecRowItem(
                                    icon = Icons.Default.Devices,
                                    label = "Device & Operating System",
                                    value = "${hardwareSpecs.deviceName} • Android ${hardwareSpecs.androidVersion} (API ${hardwareSpecs.apiLevel})",
                                    accent = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            5 -> {
                // Diagnostics & FPS: Screen Overlay HUD Controls, Shizuku Setup, Flash Storage Speed
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Screen Overlay HUD Controls Card
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

                            // Master Toggle
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
                                    modifier = Modifier.testTag("fps_diag_overlay_master_switch")
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
                            Spacer(modifier = Modifier.height(8.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ExtrasTelemetryRow(
                                    icon = Icons.Default.Speed,
                                    label = "Ultra-Precision EFPS Counter (96%–98% Accuracy)",
                                    description = "Compact HUD readout powered by 6-way hardware sensor fusion",
                                    isChecked = overlayConfig.showFps,
                                    onCheckedChange = { viewModel.toggleOverlayFps(context, it) },
                                    accentColor = HubBlue
                                )

                                ExtrasTelemetryRow(
                                    icon = Icons.Default.Memory,
                                    label = "RAM Usage Percentage",
                                    description = "Display live system RAM utilization (%) on floating HUD",
                                    isChecked = overlayConfig.showRamPct,
                                    onCheckedChange = { viewModel.toggleOverlayRamPct(context, it) },
                                    accentColor = Color(0xFFA855F7)
                                )

                                ExtrasTelemetryRow(
                                    icon = Icons.Default.Thermostat,
                                    label = "Battery & Thermal Sensor",
                                    description = "Monitor live battery temperature (°C)",
                                    isChecked = overlayConfig.showTemp,
                                    onCheckedChange = { viewModel.toggleOverlayTemp(context, it) },
                                    accentColor = SolarGold
                                )

                                ExtrasTelemetryRow(
                                    icon = Icons.Default.BatteryChargingFull,
                                    label = "Battery Fuel Gauge",
                                    description = "Display live battery percentage",
                                    isChecked = overlayConfig.showBattery,
                                    onCheckedChange = { viewModel.toggleOverlayBattery(context, it) },
                                    accentColor = LaunchGreen
                                )

                                ExtrasTelemetryRow(
                                    icon = Icons.Default.Timer,
                                    label = "Time of Day Clock",
                                    description = "Display live current time of day (e.g. 2:45 PM) on HUD",
                                    isChecked = overlayConfig.showClock,
                                    onCheckedChange = { viewModel.toggleOverlayClock(context, it) },
                                    accentColor = HubBlue
                                )

                                ExtrasTelemetryRow(
                                    icon = Icons.Default.Timer,
                                    label = "Match Session Stopwatch",
                                    description = "On-screen active gaming timer",
                                    isChecked = overlayConfig.showTimer,
                                    onCheckedChange = { viewModel.toggleOverlayTimer(context, it) },
                                    accentColor = Color(0xFF0EA5E9)
                                )

                                ExtrasTelemetryRow(
                                    icon = Icons.Default.Layers,
                                    label = "Auto-Scale for Smaller Phone Screens",
                                    description = "Automatically shrinks HUD text & spacing so all 6 metrics fit on 1 row",
                                    isChecked = overlayConfig.autoScaleSmallScreens,
                                    onCheckedChange = { viewModel.toggleOverlayAutoScale(context, it) },
                                    accentColor = LaunchGreen
                                )
                            }
                        }
                    }

                    // 2. 6-Way Ultra-Precision EFPS Engine (96%–98% Accuracy) & Non-Root Explanation Card
                    item {
                        CyberCard(borderColor = HubBlue.copy(alpha = 0.6f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(LaunchGreen.copy(alpha = 0.16f))
                                            .border(1.dp, LaunchGreen.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Speed,
                                            contentDescription = null,
                                            tint = LaunchGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "6-WAY ULTRA-PRECISION EFPS",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 14.sp
                                            )
                                        )
                                        Text(
                                            text = "VRR + VSYNC + GPU Swap + Sub-Harmonic Cap + CPU/Thermal",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(LaunchGreen.copy(alpha = 0.16f))
                                        .border(1.dp, LaunchGreen.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${efpsTelemetry.confidencePercent}% ACCURACY",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = LaunchGreen,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 9.sp
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Row 1 of 6-Metric Live Diagnostics Strip (Live EFPS, 1% Low EFPS, Frame Pace)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${efpsTelemetry.currentEfps} EFPS",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Black,
                                                color = when {
                                                    efpsTelemetry.currentEfps < 30 -> ElectricCrimson
                                                    efpsTelemetry.currentEfps < (efpsTelemetry.effectiveCapFps * 0.75f).toInt() -> SolarGold
                                                    else -> LaunchGreen
                                                },
                                                fontSize = 15.sp
                                            )
                                        )
                                        Text(
                                            text = "Live Output",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp
                                            )
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${efpsTelemetry.onePercentLowEfps} EFPS",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Black,
                                                color = if (efpsTelemetry.onePercentLowEfps < 30) ElectricCrimson else HubBlue,
                                                fontSize = 15.sp
                                            )
                                        )
                                        Text(
                                            text = "1% Low Floor",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp
                                            )
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = String.format(Locale.US, "%.1f ms", efpsTelemetry.frameTimeMs),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Black,
                                                color = SolarGold,
                                                fontSize = 15.sp
                                            )
                                        )
                                        Text(
                                            text = "Frame Time",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp
                                            )
                                        )
                                    }
                                }

                                // Row 2 of 6-Metric Live Diagnostics Strip (Active Cap / Hz, Frame Stability, SoC CPU/GPU Load)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${efpsTelemetry.effectiveCapFps} / ${efpsTelemetry.displayHz}Hz",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 13.sp
                                            )
                                        )
                                        Text(
                                            text = if (efpsTelemetry.targetCapMode == "AUTO") "Auto Cap / Panel" else "Locked Cap / Panel",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp
                                            )
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${efpsTelemetry.frameStabilityPercent}% (±${String.format(Locale.US, "%.1f", efpsTelemetry.vsyncJitterMs)}ms)",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Black,
                                                color = if (efpsTelemetry.frameStabilityPercent >= 90) LaunchGreen else SolarGold,
                                                fontSize = 13.sp
                                            )
                                        )
                                        Text(
                                            text = "Frame Stability",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp
                                            )
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${efpsTelemetry.cpuLoadPercent}%",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Black,
                                                color = if (efpsTelemetry.cpuLoadPercent < 80) HubBlue else ElectricCrimson,
                                                fontSize = 13.sp
                                            )
                                        )
                                        Text(
                                            text = "SoC Clock Load",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp
                                            )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Target Game FPS Cap Calibration (Auto-Detect via GPU Cadence + Manual Override)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "GAME ENGINE FPS CAP CALIBRATION",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = HubBlue,
                                        fontSize = 11.sp,
                                        letterSpacing = 0.8.sp
                                    )
                                )
                                Text(
                                    text = if (efpsTelemetry.targetCapMode == "AUTO") {
                                        "Auto-Detected: ${efpsTelemetry.autoDetectedCap} FPS"
                                    } else {
                                        "Override: ${efpsTelemetry.effectiveCapFps} FPS"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = LaunchGreen,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Prevents 120Hz panels from over-reporting when a game locks its internal engine to 60 or 90 FPS.",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val activeCapMode = overlayConfig.targetCapMode.uppercase()
                                for (capOption in com.example.util.EfpsEngine.SUPPORTED_CAP_MODES) {
                                    val isSelected = activeCapMode == capOption
                                    val pillLabel = if (capOption == "AUTO") {
                                        "Auto (${efpsTelemetry.autoDetectedCap})"
                                    } else {
                                        "$capOption FPS"
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) LaunchGreen
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) LaunchGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                viewModel.setTargetFpsCap(context, capOption)
                                            }
                                            .padding(vertical = 7.dp, horizontal = 10.dp)
                                            .testTag("efps_cap_pill_${capOption.lowercase()}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = pillLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Honest Non-Root Explanation Box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SolarGold.copy(alpha = 0.12f))
                                    .border(1.dp, SolarGold.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                                    .testTag("efps_non_root_explanation_card")
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = SolarGold,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = com.example.util.EfpsEngine.NON_ROOT_EXPLANATION_TITLE,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = SolarGold,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                    Text(
                                        text = com.example.util.EfpsEngine.NON_ROOT_EXPLANATION_BODY,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 11.sp,
                                            lineHeight = 16.sp
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "HOW 6-WAY SENSOR FUSION REACHES 96%–98% ACCURACY",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = HubBlue,
                                    fontSize = 10.sp,
                                    letterSpacing = 0.8.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = com.example.util.EfpsEngine.SENSOR_FUSION_SUMMARY_BODY,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "EFPS SAMPLING RESPONSE CADENCE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = HubBlue,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.8.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val currentProfile = com.example.util.EfpsSamplingProfile.fromKey(overlayConfig.fpsMode)
                                val profiles = com.example.util.EfpsSamplingProfile.values()

                                for (profile in profiles) {
                                    val isSelected = currentProfile == profile
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) HubBlue
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) HubBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                viewModel.setFpsMode(context, profile.key)
                                            }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = profile.label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Flash Storage Speed Benchmark
                    item {
                        CyberCard {
                            SectionTitle(
                                title = "Internal Flash Storage Speed Benchmark",
                                subtitle = "Measures sequential flash read/write speed (MB/s) for texture loading"
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = { viewModel.runStorageBenchmark() },
                                enabled = !isSpeedTesting,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = HubBlue,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("run_flash_speed_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isSpeedTesting) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("TESTING FLASH DISK SPEED...", color = Color.White, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(imageVector = Icons.Default.Storage, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("RUN FLASH SPEED BENCHMARK (MB/s)", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }

                            storageSpeed?.let { result ->
                                if (result.isSuccess) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Results: Sequential Write: ${result.writeSpeedMBps} MB/s | Read: ${result.readSpeedMBps} MB/s",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = LaunchGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtrasSubTabPill(
    title: String,
    isSelected: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) HubBlue else MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (isSelected) HubBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        )
    }
}

@Composable
fun HardwareSpecRowItem(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp
                )
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ExtrasTelemetryRow(
    icon: ImageVector,
    label: String,
    description: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                )
            }
        }

        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}
