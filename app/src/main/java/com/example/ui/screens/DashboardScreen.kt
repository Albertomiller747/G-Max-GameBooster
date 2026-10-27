package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.SolarGold
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BoostMode
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
import com.example.util.OEMGameBoosterHelper
import com.example.util.SystemSettingsHelper
import com.example.viewmodel.GameBoosterViewModel
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: GameBoosterViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val memoryStats by viewModel.memoryStats.collectAsState()
    val thermalInfo by viewModel.thermalInfo.collectAsState()
    val readiness by viewModel.readiness.collectAsState()
    val isBoosting by viewModel.isBoosting.collectAsState()
    val boostFeedback by viewModel.boostFeedback.collectAsState()
    val gamingSessionActive by viewModel.gamingSessionActive.collectAsState()
    val selectedBoostMode by viewModel.selectedBoostMode.collectAsState()
    val overlayConfig by viewModel.overlayConfig.collectAsState()
    val currentTheme by viewModel.appTheme.collectAsState()
    val hardwareSpecs by viewModel.hardwareSpecs.collectAsState()
    val appliedOptionIds by viewModel.appliedOptionIds.collectAsState()
    val sessionState by viewModel.sessionUiState.collectAsState()
    val selectedTargetGame by viewModel.selectedTargetGame.collectAsState()
    val isBatteryOptimizationIgnored by viewModel.isBatteryOptimizationIgnored.collectAsState()
    val isPowerSaveModeActive by viewModel.isPowerSaveModeActive.collectAsState()
    val isDndAccessGranted by viewModel.isDndAccessGranted.collectAsState()
    val efpsTelemetry by viewModel.efpsTelemetry.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isBoosting) 1.08f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val tempFormatted = String.format(Locale.US, "%.1f°C", thermalInfo.batteryTempC)
    val freeRamGb = if (memoryStats.availRamMb > 1024) {
        String.format(Locale.US, "%.1f GB", memoryStats.availRamMb / 1024.0)
    } else {
        "${memoryStats.availRamMb} MB"
    }

    val isBeastMode = selectedBoostMode == BoostMode.BEAST

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(currentTheme.backgroundColor)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = "Hardware Readiness & Boost Engine",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select your profile and trigger hardware acceleration",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF8B94A6),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }

        // 2. Mode Selector: Gaming vs Beast
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF141A28))
                    .border(1.dp, Color(0xFF202A3E), RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Gaming Mode (Normal Boost)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (!isBeastMode) Color(0xFF2563EB) else Color.Transparent)
                        .clickable { viewModel.setBoostMode(BoostMode.GAMING) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = if (!isBeastMode) Color.White else Color(0xFF9CA3AF),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Gaming Mode",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (!isBeastMode) Color.White else Color(0xFF9CA3AF),
                                    fontSize = 13.sp
                                )
                            )
                        }
                        Text(
                            text = "3h • 12m Auto-Trim",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (!isBeastMode) Color(0xFFBFDBFE) else Color(0xFF6B7280),
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                // Beast Mode (Intense FPS Boost)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isBeastMode) Color(0xFFDC2626) else Color.Transparent)
                        .clickable { viewModel.setBoostMode(BoostMode.BEAST) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = null,
                                tint = if (isBeastMode) Color.White else Color(0xFF9CA3AF),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Beast Mode",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isBeastMode) Color.White else Color(0xFF9CA3AF),
                                    fontSize = 13.sp
                                )
                            )
                        }
                        Text(
                            text = "90m • 3m Aggressive Purge",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isBeastMode) Color(0xFFFECACA) else Color(0xFF6B7280),
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }

        // 2.5 Target Game Selection & Auto-Launch Card
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = currentTheme.surfaceColor,
                border = BorderStroke(1.dp, currentTheme.borderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setGamePickerVisible(true) }
                    .testTag("target_game_selector_card")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (isBeastMode) Color(0xFFDC2626).copy(alpha = 0.2f) else CyberCyan.copy(alpha = 0.15f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = if (isBeastMode) Color(0xFFEF4444) else CyberCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (selectedTargetGame != null) selectedTargetGame!!.appName else "NO GAME SELECTED",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (selectedTargetGame?.isGame == true) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF00E676).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "GAME",
                                        color = Color(0xFF00E676),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (selectedTargetGame != null) "Auto-opens automatically after boost completes" else "Tap to choose which game or app to boost & launch",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    OutlinedButton(
                        onClick = { viewModel.setGamePickerVisible(true) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("change_target_game_button")
                    ) {
                        Text(
                            text = if (selectedTargetGame != null) "Change" else "Select",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // 2.6 Battery Saver Active Alert (if enabled by OS, throttles CPU/GPU)
        if (isPowerSaveModeActive) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF3B1219),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.openBatterySaverSettings(context) }
                        .testTag("battery_saver_alert_card")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚠️",
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Battery Saver is Active (CPU Throttled)",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 12.sp
                                )
                            )
                            Text(
                                text = "Android limits SoC clock frequencies in battery saver. Tap to disable.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFFFECACA).copy(alpha = 0.8f),
                                    fontSize = 10.sp
                                )
                            )
                        }
                        Text(
                            text = "DISABLE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }

        // 2.7 Multi-Action Subsystem Readiness Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // DND Subsystem Badge
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDndAccessGranted) Color(0xFF0F261C) else Color(0xFF2E2211))
                        .border(1.dp, if (isDndAccessGranted) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFFF59E0B).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable { if (!isDndAccessGranted) viewModel.openDndAccessSettings(context) }
                        .padding(vertical = 6.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isDndAccessGranted) "🛡️ DND: Active" else "🔔 DND: Grant",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isDndAccessGranted) Color(0xFF34D399) else Color(0xFFFBBF24),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                // Battery Exemption Subsystem Badge
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isBatteryOptimizationIgnored) Color(0xFF0F261C) else Color(0xFF2E2211))
                        .border(1.dp, if (isBatteryOptimizationIgnored) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFFF59E0B).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable { if (!isBatteryOptimizationIgnored) viewModel.requestBatteryOptimizationExemption(context) }
                        .padding(vertical = 6.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isBatteryOptimizationIgnored) "⚡ Battery: Exempt" else "⚡ Battery: Exempt",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isBatteryOptimizationIgnored) Color(0xFF34D399) else Color(0xFFFBBF24),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                // Screen Awake Subsystem Badge
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0A2234))
                        .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(vertical = 6.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "👁️ Screen: Awake",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF67E8F9),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }

        // 2.8 Multi-Sensor EFPS Pipeline Status Card
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("efps_pipeline_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "⚡",
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "MULTI-SENSOR EFPS ENGINE (${efpsTelemetry.currentEfps} EFPS @ ${efpsTelemetry.displayHz}Hz)",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(NeonGreen.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${efpsTelemetry.confidencePercent}% ACCURACY",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = NeonGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = com.example.util.EfpsEngine.NON_ROOT_EXPLANATION_BODY,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }

        // 3. Center Dial Button (Color and animations adapt based on Gaming vs Beast mode)
        item {
            val primaryRingColor = if (isBeastMode) Color(0xFFEF4444) else Color(0xFF00E5FF)
            val secondaryRingColor = if (isBeastMode) Color(0xFFF97316) else Color(0xFF2563EB)
            val buttonGradient = if (isBeastMode) {
                listOf(Color(0xFFDC2626), Color(0xFFB91C1C), Color(0xFFEA580C))
            } else {
                listOf(Color(0xFF2563EB), Color(0xFF1D4ED8), Color(0xFF00B0FF))
            }

            Spacer(modifier = Modifier.height(4.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(220.dp)
                    .scale(pulseScale)
            ) {
                // Outer Glow Ring 1
                Box(
                    modifier = Modifier
                        .size(216.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, primaryRingColor.copy(alpha = 0.35f), CircleShape)
                )

                // Outer Glow Ring 2
                Box(
                    modifier = Modifier
                        .size(196.dp)
                        .clip(CircleShape)
                        .border(3.5.dp, primaryRingColor, CircleShape)
                )

                // Dark spacing ring
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0A101C))
                )

                // Inner Gradient Pulsing Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(164.dp)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(colors = buttonGradient))
                        .clickable(enabled = !isBoosting) { viewModel.quickBoost() }
                        .testTag("boost_center_button")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (isBoosting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = Color.White,
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isBeastMode) "UNLEASHING..." else "FLUSHING...",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = Color.White
                                )
                            )
                        } else if (sessionState.isActive) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Active Timer",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = sessionState.formattedCountdown,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 16.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (sessionState.mode == BoostMode.BEAST) "BEAST ACTIVE" else "BOOST ACTIVE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isBeastMode) Color(0xFFFFD5D5) else Color(0xFF80F0FF),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        } else {
                            Icon(
                                imageVector = if (isBeastMode) Icons.Default.FlashOn else Icons.Default.Bolt,
                                contentDescription = "Boost",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isBeastMode) "BEAST BOOST" else "GAMING BOOST",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.5.sp,
                                    color = Color.White,
                                    fontSize = 16.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (selectedTargetGame != null) "Launch ${selectedTargetGame!!.appName}" else if (isBeastMode) "1h 30m • 5m Purge" else "3 Hours Active",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isBeastMode) Color(0xFFFFD5D5) else Color(0xFF80F0FF),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Active Booster Session Countdown Card (if session is active)
        if (sessionState.isActive) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (sessionState.mode == BoostMode.BEAST) Color(0xFF261010) else Color(0xFF0F2236)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (sessionState.mode == BoostMode.BEAST) Color(0xFFDC2626) else CyberCyan
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (sessionState.mode == BoostMode.BEAST) Color(0xFFEF4444).copy(alpha = 0.2f) else CyberCyan.copy(alpha = 0.2f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = if (sessionState.mode == BoostMode.BEAST) Color(0xFFEF4444) else CyberCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (sessionState.mode == BoostMode.BEAST) "BEAST MODE (1h 30m)" else "NORMAL BOOST (3h)",
                                    color = if (sessionState.mode == BoostMode.BEAST) Color(0xFFFF8A80) else Color(0xFF80D8FF),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "${sessionState.formattedCountdown} remaining",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { viewModel.stopBoosterSession() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Stop", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }


        // Live Boost Feedback banner if present
        if (boostFeedback != null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isBeastMode) Color(0xFF3B1515) else Color(0xFF142436))
                        .border(
                            1.dp,
                            if (isBeastMode) Color(0xFFEF4444).copy(alpha = 0.5f) else Color(0xFF00E5FF).copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = boostFeedback ?: "",
                        color = if (isBeastMode) Color(0xFFFCA5A5) else Color(0xFF00E5FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Quick HUD Overlay banner (especially useful during Beast Mode)
        item {
            CyberCard(borderColor = if (overlayConfig.isEnabled) NeonGreen.copy(alpha = 0.6f) else DarkSurfaceBorder) {
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
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = if (overlayConfig.isEnabled) NeonGreen else CyberCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Screen Overlay HUD",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = if (overlayConfig.isEnabled) "Showing live FPS & Battery on screen" else "Off (Auto-enables in Beast Mode)",
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
                        modifier = Modifier.testTag("dashboard_overlay_toggle")
                    )
                }

                if (overlayConfig.isEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.toggleOverlayFps(context, !overlayConfig.showFps) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (overlayConfig.showFps) Color(0xFF142436) else Color.Transparent
                            )
                        ) {
                            Text(
                                text = if (overlayConfig.showFps) "FPS: ON" else "FPS: OFF",
                                fontSize = 11.sp,
                                color = if (overlayConfig.showFps) CyberCyan else Color.Gray
                            )
                        }

                        OutlinedButton(
                            onClick = { viewModel.toggleOverlayTemp(context, !overlayConfig.showTemp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (overlayConfig.showTemp) Color(0xFF332312) else Color.Transparent
                            )
                        ) {
                            Text(
                                text = if (overlayConfig.showTemp) "Temp: ON" else "Temp: OFF",
                                fontSize = 11.sp,
                                color = if (overlayConfig.showTemp) SolarGold else Color.Gray
                            )
                        }

                        OutlinedButton(
                            onClick = { viewModel.toggleOverlayBattery(context, !overlayConfig.showBattery) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (overlayConfig.showBattery) Color(0xFF122E22) else Color.Transparent
                            )
                        ) {
                            Text(
                                text = if (overlayConfig.showBattery) "Batt: ON" else "Batt: OFF",
                                fontSize = 11.sp,
                                color = if (overlayConfig.showBattery) NeonGreen else Color.Gray
                            )
                        }
                    }
                }
            }
        }

        // 4. 2x2 Telemetry Grid
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Row 1: Thermals & RAM Headroom
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TelemetryMockupCard(
                        icon = Icons.Default.Thermostat,
                        iconTint = SolarGold,
                        label = "Thermals",
                        value = tempFormatted,
                        valueColor = Color(0xFF00E5FF),
                        subtitle = if (thermalInfo.batteryTempC > 40f) "Throttling Risk" else "Nominal (Cold State)",
                        modifier = Modifier.weight(1f)
                    )

                    TelemetryMockupCard(
                        icon = Icons.Default.Memory,
                        iconTint = Color(0xFFA855F7),
                        label = "RAM Headroom",
                        value = "$freeRamGb Free",
                        valueColor = NeonGreen,
                        subtitle = "LMK Threshold Safe",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 2: Refresh Rate & Focus Shield
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TelemetryMockupCard(
                        icon = Icons.Default.Timeline,
                        iconTint = Color(0xFF00E5FF),
                        label = "Refresh Rate",
                        value = "${hardwareSpecs.refreshRateHz} Hz",
                        valueColor = if (hardwareSpecs.refreshRateHz >= 90) NeonGreen else Color.White,
                        subtitle = if (hardwareSpecs.refreshRateHz >= 90) "High Refresh Active" else "Native Display Rate",
                        modifier = Modifier.weight(1f),
                        onClick = null
                    )

                    TelemetryMockupCard(
                        icon = Icons.Default.Security,
                        iconTint = NeonGreen,
                        label = "Focus Shield",
                        value = if (gamingSessionActive) "Active" else "Ready",
                        valueColor = NeonGreen,
                        subtitle = "Call Banners Suppressed",
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.toggleGamingSession() }
                    )
                }
            }
        }


    }
}

@Composable
fun TelemetryMockupCard(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    valueColor: Color,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFF1C2436), RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131826)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF9CA3AF),
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = valueColor,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White,
                    fontSize = 11.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
