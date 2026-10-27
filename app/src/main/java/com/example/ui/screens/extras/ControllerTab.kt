package com.example.ui.screens.extras

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.CyberCard
import com.example.ui.components.SectionTitle
import com.example.ui.theme.ElectricCrimson
import com.example.ui.theme.HubBlue
import com.example.ui.theme.LaunchGreen
import com.example.ui.theme.SolarGold
import com.example.util.StickState
import com.example.viewmodel.GameBoosterViewModel
import java.util.Locale

private val LightbarPresets = listOf(
    "#00E5FF" to "Cyan",
    "#EF4444" to "Crimson",
    "#10B981" to "Emerald",
    "#F59E0B" to "Gold",
    "#8B5CF6" to "Violet",
    "#2563EB" to "Sapphire",
    "#EC4899" to "Hot Pink",
    "#F97316" to "Orange",
    "#F8FAFC" to "Ice White",
    "#84CC16" to "Toxic Lime",
    "#D946EF" to "Magenta",
    "#14B8A6" to "Teal"
)

@Composable
fun ControllerTab(
    viewModel: GameBoosterViewModel,
    modifier: Modifier = Modifier
) {
    val healthState by viewModel.controllerHealthState.collectAsState()
    val isTestLocked by viewModel.isControllerTestLocked.collectAsState()
    var deadzoneSlider by remember { mutableFloatStateOf(0.05f) }

    var selectedLedHex by remember { mutableStateOf("#00E5FF") }
    var customR by remember { mutableIntStateOf(0) }
    var customG by remember { mutableIntStateOf(229) }
    var customB by remember { mutableIntStateOf(255) }

    // Ensure controller input lock is automatically released when leaving ControllerTab
    DisposableEffect(Unit) {
        onDispose {
            viewModel.setControllerTestLocked(false)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("controller_tab_container"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Controller Connection & Polling Rate Monitor Card
        item {
            CyberCard(borderColor = HubBlue.copy(alpha = 0.5f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        SectionTitle(
                            title = "Controller Polling Rate Monitor",
                            subtitle = "Real kernel HID event frequency (Hz)"
                        )
                    }
                    IconButtonCustom(
                        onClick = { viewModel.refreshControllerState() },
                        icon = Icons.Default.Refresh
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Gamepad,
                            contentDescription = null,
                            tint = if (healthState.controllerId >= 0) LaunchGreen else SolarGold,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = healthState.activeControllerName,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = if (healthState.controllerId >= 0) "Controller ID #${healthState.controllerId} • Connected" else "Connect via Bluetooth or USB-OTG",
                                color = if (healthState.controllerId >= 0) LaunchGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ControllerMetricBox(
                        modifier = Modifier.weight(1f),
                        label = "CURRENT POLLING RATE",
                        value = if (healthState.pollingRateHz > 0) "${healthState.pollingRateHz} Hz" else "Standby (0 Hz)",
                        accent = if (healthState.pollingRateHz >= 250) LaunchGreen else if (healthState.pollingRateHz >= 125) HubBlue else SolarGold
                    )
                    ControllerMetricBox(
                        modifier = Modifier.weight(1f),
                        label = "PEAK DETECTED",
                        value = if (healthState.peakPollingRateHz > 0) "${healthState.peakPollingRateHz} Hz" else "--",
                        accent = LaunchGreen
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "High polling rates (250Hz - 1000Hz) deliver sub-4ms response times. Lower polling rates (under 125Hz) can introduce aim smoothing or perceptible delay in fast-paced competitive games.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                )
            }
        }

        // 2. Controller Health Check: Stick Drift, Deadzones & Stick Range (with Start/Stop Check Input Lock)
        item {
            val borderCol = when {
                isTestLocked -> HubBlue
                healthState.leftStick.isDrifting || healthState.rightStick.isDrifting -> ElectricCrimson
                else -> LaunchGreen.copy(alpha = 0.6f)
            }
            CyberCard(borderColor = borderCol) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        SectionTitle(
                            title = "Controller Health Check: Stick Drift & Range",
                            subtitle = "Isolate joysticks so testing never scrolls or moves UI"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Start Check / Stop Check Input Isolation Banner + Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isTestLocked) HubBlue.copy(alpha = 0.14f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        )
                        .border(
                            1.dp,
                            if (isTestLocked) HubBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isTestLocked) Icons.Default.Lock else Icons.Default.Gamepad,
                                contentDescription = null,
                                tint = if (isTestLocked) HubBlue else LaunchGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isTestLocked) "Check Active • UI Navigation Locked" else "Diagnostic Input Isolation",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = if (isTestLocked) "Sticks & D-Pad now only affect the drift reticles below." else "Tap Start Check so moving joysticks won't scroll the screen.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.5.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Button(
                            onClick = { viewModel.setControllerTestLocked(!isTestLocked) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTestLocked) ElectricCrimson else LaunchGreen,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("controller_stick_check_lock_button")
                        ) {
                            Icon(
                                imageVector = if (isTestLocked) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isTestLocked) "Stop Check" else "Start Check",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (healthState.leftStick.isDrifting || healthState.rightStick.isDrifting) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ElectricCrimson.copy(alpha = 0.14f))
                            .border(1.dp, ElectricCrimson, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = ElectricCrimson, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Stick Drift Warning: Analog pot resting outside neutral deadzone. Increase deadzone below or clean stick sensor module.",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Dual Reticles: Left Stick & Right Stick
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "LEFT THUMBSTICK",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        StickRadarReticle(stick = healthState.leftStick, deadzone = deadzoneSlider)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "X: ${healthState.leftStick.x}  Y: ${healthState.leftStick.y}",
                            color = HubBlue,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Circularity: ${healthState.leftStick.circularityScore}%",
                            color = if (healthState.leftStick.circularityScore >= 90) LaunchGreen else SolarGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "RIGHT THUMBSTICK",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        StickRadarReticle(stick = healthState.rightStick, deadzone = deadzoneSlider)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "X: ${healthState.rightStick.x}  Y: ${healthState.rightStick.y}",
                            color = HubBlue,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Circularity: ${healthState.rightStick.circularityScore}%",
                            color = if (healthState.rightStick.circularityScore >= 90) LaunchGreen else SolarGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Deadzone Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Test Deadzone Radius:",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${(deadzoneSlider * 100).toInt()}%",
                            color = LaunchGreen,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = deadzoneSlider,
                        onValueChange = {
                            deadzoneSlider = it
                            viewModel.setControllerDeadzone(it)
                        },
                        valueRange = 0.0f..0.25f,
                        colors = SliderDefaults.colors(
                            thumbColor = LaunchGreen,
                            activeTrackColor = LaunchGreen,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Trigger Travel Gauges (L2/LT & R2/RT)
                Text(
                    "Analog Trigger Travel (0.00 to 1.00):",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("LT / L2 Depth", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                            Text(
                                String.format(Locale.US, "%.2f", healthState.triggers.leftTrigger),
                                color = HubBlue,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { healthState.triggers.leftTrigger },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = HubBlue,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("RT / R2 Depth", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                            Text(
                                String.format(Locale.US, "%.2f", healthState.triggers.rightTrigger),
                                color = HubBlue,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { healthState.triggers.rightTrigger },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = HubBlue,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "Mechanical Button Chatter Detection:",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (healthState.buttonChatterEvents.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "✓ Zero contact bounce chatter detected. Switches in pristine condition.",
                            color = LaunchGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        healthState.buttonChatterEvents.take(3).forEach { chatter ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ElectricCrimson.copy(alpha = 0.14f))
                                    .border(1.dp, ElectricCrimson.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Switch Chatter: ${chatter.buttonName}", color = ElectricCrimson, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("Bounce: ${chatter.bounceIntervalMs}ms", color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Controller Battery Level & Low-Battery Alert Card
        item {
            CyberCard(borderColor = if (healthState.batteryInfo.isLowBattery) ElectricCrimson else SolarGold.copy(alpha = 0.55f)) {
                SectionTitle(
                    title = "Controller Battery Level",
                    subtitle = "Real-time gamepad charge & low-battery alert"
                )

                Spacer(modifier = Modifier.height(10.dp))

                healthState.batteryInfo.lowBatteryAlert?.let { alert ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ElectricCrimson.copy(alpha = 0.16f))
                            .border(1.dp, ElectricCrimson, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BatteryAlert, contentDescription = null, tint = ElectricCrimson, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(alert, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (healthState.batteryInfo.isLowBattery) Icons.Default.BatteryAlert else Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        tint = if (healthState.batteryInfo.isLowBattery) ElectricCrimson else LaunchGreen,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Battery Status: ${healthState.batteryInfo.batteryStatus}",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Queried directly via Android 12+ InputDevice.batteryState API" else "Direct HID battery query requires Android 12+ (API 31+)",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // 4. Real Rumble Tester Card
        item {
            CyberCard(borderColor = Color(0xFFA855F7).copy(alpha = 0.55f)) {
                SectionTitle(
                    title = "Real Rumble Tester",
                    subtitle = "Actuates gamepad haptic vibration motors"
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Status: ${healthState.rumbleStatus}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.testControllerRumble(0.4f, 150) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HubBlue.copy(alpha = 0.14f),
                            contentColor = HubBlue
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, HubBlue.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("CLICK (150ms)", fontSize = 10.sp, color = HubBlue, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.testControllerRumble(0.75f, 300) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LaunchGreen.copy(alpha = 0.14f),
                            contentColor = LaunchGreen
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LaunchGreen.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("PULSE (300ms)", fontSize = 10.sp, color = LaunchGreen, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.testControllerRumble(1.0f, 600) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCrimson.copy(alpha = 0.14f),
                            contentColor = ElectricCrimson
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCrimson.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("HEAVY (600ms)", fontSize = 10.sp, color = ElectricCrimson, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 5. Expanded LED Lightbar Studio (12 Presets + Custom RGB Sliders) & Gyro Tools
        item {
            CyberCard(borderColor = HubBlue.copy(alpha = 0.55f)) {
                SectionTitle(
                    title = "LED Lightbar & Gyroscope Studio (Android 12+)",
                    subtitle = "DualSense, DualShock 4, Xbox, and pro gamepad hardware interfaces"
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Active Lightbar Header & Swatch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Gamepad Lightbar / LED Color (12 Presets + Custom RGB):",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(
                                    try {
                                        Color(android.graphics.Color.parseColor(selectedLedHex))
                                    } catch (_: Exception) {
                                        HubBlue
                                    }
                                )
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = selectedLedHex,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 12 Lightbar Color Presets in 2 Rows of 6 (horizontally scrollable for compact phones)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LightbarPresets.chunked(6).forEach { rowPresets ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPresets.forEach { (hex, label) ->
                                LedColorButton(
                                    hex = hex,
                                    label = label,
                                    isSelected = selectedLedHex.equals(hex, ignoreCase = true),
                                    onSelect = {
                                        selectedLedHex = hex
                                        val parsed = android.graphics.Color.parseColor(hex)
                                        customR = android.graphics.Color.red(parsed)
                                        customG = android.graphics.Color.green(parsed)
                                        customB = android.graphics.Color.blue(parsed)
                                        viewModel.setControllerLedColor(hex)
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Custom RGB Sliders Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Custom RGB Lightbar Mixer",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )

                        val updateCustomHex = { r: Int, g: Int, b: Int ->
                            val hex = String.format(Locale.US, "#%02X%02X%02X", r, g, b)
                            selectedLedHex = hex
                            viewModel.setControllerLedColor(hex)
                        }

                        RgbChannelSliderRow(
                            label = "R",
                            value = customR,
                            color = Color(0xFFEF4444),
                            onValueChange = {
                                customR = it
                                updateCustomHex(customR, customG, customB)
                            }
                        )
                        RgbChannelSliderRow(
                            label = "G",
                            value = customG,
                            color = LaunchGreen,
                            onValueChange = {
                                customG = it
                                updateCustomHex(customR, customG, customB)
                            }
                        )
                        RgbChannelSliderRow(
                            label = "B",
                            value = customB,
                            color = HubBlue,
                            onValueChange = {
                                customB = it
                                updateCustomHex(customR, customG, customB)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Gyro Section
                Text(
                    "Controller Gyroscope Telemetry:",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (healthState.gyroReading.isSupported) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GyroMetricBox(modifier = Modifier.weight(1f), label = "PITCH (X)", value = "${healthState.gyroReading.pitch} rad/s")
                        GyroMetricBox(modifier = Modifier.weight(1f), label = "ROLL (Y)", value = "${healthState.gyroReading.roll} rad/s")
                        GyroMetricBox(modifier = Modifier.weight(1f), label = "YAW (Z)", value = "${healthState.gyroReading.yaw} rad/s")
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Gamepad gyroscope inactive or not exposed over current Bluetooth profile. DualSense and Switch Pro gyro supported on Android 12+.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RgbChannelSliderRow(
    label: String,
    value: Int,
    color: Color,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = color,
            fontWeight = FontWeight.Black,
            fontSize = 11.sp,
            modifier = Modifier.width(18.dp)
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            modifier = Modifier
                .weight(1f)
                .height(28.dp),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value.toString().padStart(3, ' '),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(30.dp)
        )
    }
}

@Composable
private fun StickRadarReticle(stick: StickState, deadzone: Float) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    Box(
        modifier = Modifier
            .size(110.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = size.width / 2f - 4.dp.toPx()
            val deadzoneRadius = outerRadius * deadzone

            drawCircle(
                color = gridColor,
                radius = outerRadius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            drawCircle(
                color = SolarGold.copy(alpha = 0.45f),
                radius = deadzoneRadius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )

            drawLine(color = gridColor, start = Offset(center.x, 4f), end = Offset(center.x, size.height - 4f))
            drawLine(color = gridColor, start = Offset(4f, center.y), end = Offset(size.width - 4f, center.y))

            val dotX = center.x + (stick.x * outerRadius)
            val dotY = center.y + (stick.y * outerRadius)

            drawCircle(
                color = if (stick.isDrifting) ElectricCrimson else LaunchGreen,
                radius = 6.dp.toPx(),
                center = Offset(dotX, dotY)
            )
        }
    }
}

@Composable
private fun LedColorButton(
    hex: String,
    label: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val color = Color(android.graphics.Color.parseColor(hex))
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) color.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(0.5.dp, Color.Black.copy(alpha = 0.25f), CircleShape)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 10.5.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun GyroMetricBox(modifier: Modifier = Modifier, label: String, value: String) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Column {
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                value,
                color = HubBlue,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ControllerMetricBox(
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
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace
                )
            )
        }
    }
}

@Composable
private fun IconButtonCustom(onClick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = HubBlue, modifier = Modifier.size(18.dp))
    }
}
