package com.example.ui.screens

import android.provider.Settings
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PhoneHardwareSpecs
import com.example.ui.components.CyberCard
import com.example.ui.components.SectionTitle
import com.example.ui.theme.CyberCyan
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
    val gamepads by viewModel.gamepads.collectAsState()
    val memoryStats by viewModel.memoryStats.collectAsState()
    val thermalInfo by viewModel.thermalInfo.collectAsState()
    val hardwareSpecs by viewModel.hardwareSpecs.collectAsState()
    val overlayConfig by viewModel.overlayConfig.collectAsState()
    val currentTheme by viewModel.appTheme.collectAsState()
    val shizukuInfo by viewModel.shizukuInfo.collectAsState()
    val pingResults by viewModel.pingResults.collectAsState()
    val isPingTesting by viewModel.isPingTesting.collectAsState()

    // Sub-tab 0 = Tweaks (All performance options), 1 = Hardware Specs & HUD, 2 = Diagnostics & Tools
    var selectedSubTab by remember { mutableIntStateOf(0) }

    val canDrawOverlay = remember(context) {
        SystemSettingsHelper.canDrawOverlays(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(currentTheme.backgroundColor)
    ) {
        // Top Segmented Tab Navigation for Extras: [ Tweaks ] | [ Specs & HUD ] | [ Diagnostics ]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF141824))
                .border(1.dp, Color(0xFF222B3D), RoundedCornerShape(14.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Tab 0: Tweaks
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selectedSubTab == 0) Color(0xFF2563EB) else Color.Transparent)
                    .clickable { selectedSubTab = 0 }
                    .padding(vertical = 10.dp)
                    .testTag("extras_tab_tweaks"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tweaks",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (selectedSubTab == 0) Color.White else Color(0xFF9CA3AF),
                        fontSize = 12.sp
                    )
                )
            }

            // Tab 1: Specs & HUD
            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selectedSubTab == 1) Color(0xFF2563EB) else Color.Transparent)
                    .clickable { selectedSubTab = 1 }
                    .padding(vertical = 10.dp)
                    .testTag("extras_tab_specs"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Specs & HUD",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (selectedSubTab == 1) Color.White else Color(0xFF9CA3AF),
                        fontSize = 12.sp
                    )
                )
            }

            // Tab 2: Diagnostics & Tools
            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selectedSubTab == 2) Color(0xFF2563EB) else Color.Transparent)
                    .clickable { selectedSubTab = 2 }
                    .padding(vertical = 10.dp)
                    .testTag("extras_tab_diagnostics"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Diagnostics & FPS",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (selectedSubTab == 2) Color.White else Color(0xFF9CA3AF),
                        fontSize = 12.sp
                    )
                )
            }
        }

        when (selectedSubTab) {
            0 -> {
                // Tweaks inside Extras!
                OptionsScreen(viewModel = viewModel)
            }
            1 -> {
                // Specs & HUD (Phone hardware specs, accurate battery thermals, display refresh rate, overlay HUD controls)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Device Readiness Score Banner
                    item {
                        val scoreColor = when {
                            readiness.score >= 85 -> NeonGreen
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
                                        trackColor = Color(0xFF1E283D),
                                        strokeWidth = 7.dp,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Text(
                                        text = "${readiness.score}",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Black,
                                            color = Color.White
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Device Gaming Readiness",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    )
                                    Text(
                                        text = readiness.status,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = scoreColor,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (readiness.warnings.isNotEmpty()) readiness.warnings.first() else "Hardware headroom is primed for smooth competitive play.",
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF9CA3AF))
                                    )
                                }
                            }
                        }
                    }

                    // 2. Section: Phone Hardware Specifications
                    item {
                        CyberCard(borderColor = CyberCyan.copy(alpha = 0.5f)) {
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
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh Specs", tint = CyberCyan)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                HardwareSpecRowItem(
                                    icon = Icons.Default.DeveloperBoard,
                                    label = "Processor / SoC",
                                    value = "${hardwareSpecs.socModel} (${hardwareSpecs.cpuCores} Cores • ${hardwareSpecs.cpuAbi})",
                                    accent = CyberCyan
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
                                    accent = if (thermalInfo.batteryTempC < 38f) NeonGreen else SolarGold
                                )

                                HardwareSpecRowItem(
                                    icon = Icons.Default.Memory,
                                    label = "System RAM Headroom",
                                    value = "${hardwareSpecs.availRamMb} MB Available of ${hardwareSpecs.totalRamMb} MB",
                                    accent = NeonGreen
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
                                    accent = Color(0xFF38BDF8)
                                )

                                HardwareSpecRowItem(
                                    icon = Icons.Default.Devices,
                                    label = "Device & Operating System",
                                    value = "${hardwareSpecs.deviceName} • Android ${hardwareSpecs.androidVersion} (API ${hardwareSpecs.apiLevel})",
                                    accent = TextSecondary
                                )
                            }
                        }
                    }

                    // 3. Section: Real-time Screen Overlay HUD (FPS, Temp, Battery)
                    item {
                        CyberCard(borderColor = if (overlayConfig.isEnabled) NeonGreen else DarkSurfaceBorder) {
                            SectionTitle(
                                title = "Screen Overlay HUD (Real-time)",
                                subtitle = "Displays on your screen over any game or app"
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Permission warning if not granted
                            if (!canDrawOverlay) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF291B12))
                                        .border(1.dp, SolarGold.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                        .padding(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = SolarGold, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Requires 'Display over other apps' permission to stay visible over games.",
                                            style = MaterialTheme.typography.bodySmall.copy(color = SolarGold, fontSize = 11.sp)
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
                                    .background(DarkSurfaceVariant)
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.Layers,
                                        contentDescription = null,
                                        tint = if (overlayConfig.isEnabled) NeonGreen else CyberCyan,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Floating Screen Overlay",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                        )
                                        Text(
                                            text = if (overlayConfig.isEnabled) "HUD is active and draggable on screen" else "Currently inactive",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = if (overlayConfig.isEnabled) NeonGreen else TextSecondary,
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
                                        checkedTrackColor = NeonGreen,
                                        uncheckedThumbColor = Color.Gray,
                                        uncheckedTrackColor = Color(0xFF1E2638)
                                    ),
                                    modifier = Modifier.testTag("screen_overlay_master_switch")
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Overlay Telemetry Metrics:",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF9CA3AF), fontWeight = FontWeight.Bold)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                ExtrasTelemetryRow(
                                    icon = Icons.Default.Speed,
                                    label = "In-Game Frame Rate",
                                    description = "Real Shizuku frame timing (Android 11+) with EFPS fallback",
                                    isChecked = overlayConfig.showFps,
                                    onCheckedChange = { viewModel.toggleOverlayFps(context, it) },
                                    accentColor = CyberCyan
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
                                    description = "Live battery percentage and charging status",
                                    isChecked = overlayConfig.showBattery,
                                    onCheckedChange = { viewModel.toggleOverlayBattery(context, it) },
                                    accentColor = NeonGreen
                                )

                                ExtrasTelemetryRow(
                                    icon = Icons.Default.Timer,
                                    label = "Booster Mode Countdown Timer",
                                    description = "Shows remaining active boost time in HUD overlay (toggle off for cleaner UI)",
                                    isChecked = overlayConfig.showTimer,
                                    onCheckedChange = { viewModel.toggleOverlayTimer(context, it) },
                                    accentColor = Color(0xFF38BDF8)
                                )
                            }
                        }
                    }

                    // 4. Connected Gaming Peripherals
                    item {
                        CyberCard {
                            SectionTitle(
                                title = "Connected Gaming Peripherals",
                                subtitle = "Hardware gamepads, Bluetooth controllers, and USB HID sticks"
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            if (gamepads.isEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Gamepad, contentDescription = null, tint = TextMuted)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Native Touch Engine active. Connect any Bluetooth or USB-C gamepad for hardware input.",
                                        color = TextMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    for (pad in gamepads) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(DarkSurfaceVariant)
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(imageVector = Icons.Default.Gamepad, contentDescription = null, tint = NeonGreen)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(text = pad.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }
                                            Text(
                                                text = if (pad.hasVibrator) "Connected • Haptics" else "Connected",
                                                color = NeonGreen,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = { viewModel.refreshGamepads() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = CyberCyan)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SCAN FOR BLUETOOTH / USB GAMEPADS", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            2 -> {
                // Diagnostics & Tools: Flash Storage Speed, Ping Latency, Shizuku Setup
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Shizuku Real FPS Integration Card
                    item {
                        val isShizukuActive = shizukuInfo.state == com.example.util.ShizukuState.ACTIVE
                        val isPermNeeded = shizukuInfo.state == com.example.util.ShizukuState.PERMISSION_NEEDED
                        val isUnsupported = shizukuInfo.state == com.example.util.ShizukuState.UNSUPPORTED

                        val cardBorder = when {
                            isShizukuActive -> NeonGreen.copy(alpha = 0.5f)
                            isPermNeeded -> SolarGold.copy(alpha = 0.6f)
                            else -> DarkSurfaceBorder
                        }

                        CyberCard(borderColor = cardBorder) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isShizukuActive) NeonGreen.copy(alpha = 0.2f) else Color(0xFF1E283D)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isShizukuActive) "⚡" else "📊",
                                            fontSize = 16.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = if (isShizukuActive) "REAL FPS ENGINE (SHIZUKU)" else "FRAME TIMING PIPELINE",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Black,
                                                color = if (isShizukuActive) NeonGreen else Color.White,
                                                fontSize = 14.sp
                                            )
                                        )
                                        Text(
                                            text = if (isShizukuActive) "Direct system SurfaceFlinger frame timing active" else "Real in-game frame measurement via Shizuku",
                                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp)
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            when {
                                                isShizukuActive -> NeonGreen.copy(alpha = 0.15f)
                                                isPermNeeded -> SolarGold.copy(alpha = 0.15f)
                                                else -> Color(0xFF1E2638)
                                            }
                                        )
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = when {
                                            isShizukuActive -> "REAL FPS ACTIVE"
                                            isPermNeeded -> "NEEDS PERMISSION"
                                            isUnsupported -> "EFPS FALLBACK"
                                            else -> "NOT RUNNING"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = when {
                                                isShizukuActive -> NeonGreen
                                                isPermNeeded -> SolarGold
                                                isUnsupported -> Color(0xFFF87171)
                                                else -> Color(0xFF9CA3AF)
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = shizukuInfo.explanation,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFFD1D5DB),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!isShizukuActive && !isUnsupported) {
                                    if (isPermNeeded) {
                                        Button(
                                            onClick = { viewModel.requestShizukuPermission() },
                                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Grant Permission", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { viewModel.openShizukuApp() },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Launch Shizuku App", fontSize = 12.sp)
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = { viewModel.refreshShizukuStatus() },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = if (isShizukuActive || isUnsupported) Modifier.fillMaxWidth() else Modifier.weight(0.7f)
                                ) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh Shizuku", tint = CyberCyan, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Re-Check", fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }
                    }

                    // Flash Storage Speed Benchmark
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
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2638)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("run_flash_speed_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isSpeedTesting) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = CyberCyan)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("TESTING FLASH DISK SPEED...", color = CyberCyan)
                                } else {
                                    Icon(imageVector = Icons.Default.Storage, contentDescription = null, tint = CyberCyan)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("RUN FLASH SPEED BENCHMARK (MB/s)", color = CyberCyan, fontWeight = FontWeight.Bold)
                                }
                            }

                            storageSpeed?.let { result ->
                                if (result.isSuccess) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Results: Sequential Write: ${result.writeSpeedMBps} MB/s | Read: ${result.readSpeedMBps} MB/s",
                                        style = MaterialTheme.typography.bodySmall.copy(color = NeonGreen, fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }

                    // Server Ping Latency Tester
                    item {
                        CyberCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SectionTitle(
                                    title = "Multiplayer Game Server Ping Tester",
                                    subtitle = "Tests real TCP/ICMP packet latency across global gaming regions"
                                )
                                IconButton(
                                    onClick = { viewModel.runAllPingTests() },
                                    enabled = !isPingTesting
                                ) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Run Ping Test", tint = CyberCyan)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (ping in pingResults) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(DarkSurfaceVariant)
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(text = ping.regionName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(text = ping.host, color = TextMuted, fontSize = 11.sp)
                                        }

                                        val pingColor = when {
                                            ping.pingMs <= 0 -> TextMuted
                                            ping.pingMs < 45 -> NeonGreen
                                            ping.pingMs < 85 -> SolarGold
                                            else -> ElectricCrimson
                                        }

                                        Text(
                                            text = if (ping.isTesting) "Pinging..." else if (ping.pingMs > 0) "${ping.pingMs} ms" else "Idle",
                                            color = pingColor,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp
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
            .background(Color(0xFF131826))
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
                    color = Color(0xFF9CA3AF),
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp
                )
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White,
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
            .background(Color(0xFF141926))
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
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF9CA3AF),
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
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color(0xFF1E2638)
            )
        )
    }
}
