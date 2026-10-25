package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BenchmarkRun
import com.example.ui.components.CyberCard
import com.example.ui.components.MetricGauge
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
import com.example.ui.theme.ThermalCool
import com.example.ui.theme.ThermalCritical
import com.example.ui.theme.ThermalWarm
import com.example.util.SystemSettingsHelper
import com.example.viewmodel.GameBoosterViewModel

@Composable
fun HardwareScreen(
    viewModel: GameBoosterViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val thermalInfo by viewModel.thermalInfo.collectAsState()

    val tempColor = when {
        thermalInfo.batteryTempC >= 42f -> ThermalCritical
        thermalInfo.batteryTempC >= 36f -> ThermalWarm
        else -> ThermalCool
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Thermal Headroom & Status Card
        item {
            CyberCard(borderColor = tempColor.copy(alpha = 0.5f)) {
                SectionTitle(
                    title = "Thermal Telemetry & Throttling",
                    subtitle = "Real-time PowerManager hardware governor sensor feeds",
                    badge = thermalInfo.thermalStatusName,
                    badgeColor = tempColor
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricGauge(
                        label = "Battery Temp",
                        value = "${thermalInfo.batteryTempC}°C",
                        subValue = if (thermalInfo.batteryTempC > 40f) "Hot! Throttling active" else "Nominal range",
                        percent = (thermalInfo.batteryTempC / 50f).coerceIn(0f, 1f),
                        barColor = tempColor,
                        icon = Icons.Default.Thermostat,
                        modifier = Modifier.weight(1f)
                    )

                    val headroomPercent = (thermalInfo.thermalHeadroom * 100).toInt()
                    val headroomColor = if (thermalInfo.thermalHeadroom > 0.5f) NeonGreen else ElectricCrimson

                    MetricGauge(
                        label = "Thermal Headroom",
                        value = "$headroomPercent%",
                        subValue = "Margin before throttling",
                        percent = thermalInfo.thermalHeadroom.coerceIn(0f, 1f),
                        barColor = headroomColor,
                        icon = Icons.Default.Speed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Cooldown & Heat Mitigation Guidance (Section 6)
        item {
            CyberCard(borderColor = if (thermalInfo.isThrottlingRisk) ElectricCrimson else DarkSurfaceBorder) {
                SectionTitle(
                    title = "Thermal Throttling Protection",
                    subtitle = "Hardware cooling guidelines to sustain max framerates"
                )

                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CoolingAdviceRow(
                        title = "Avoid Charging While Gaming",
                        desc = "Simultaneous fast-charging + 3D rendering causes heat spikes exceeding 45°C, forcing severe CPU clock drops. If device supports bypass charging, enable it."
                    )
                    CoolingAdviceRow(
                        title = "Remove Phone Protective Case",
                        desc = "Silicone and rugged armor cases trap radiant heat, preventing metal/glass frame dissipation."
                    )
                    CoolingAdviceRow(
                        title = "Cap Framerate to 60 FPS",
                        desc = "An unstable 120 FPS causes heat spikes within 8 minutes followed by 30 FPS thermal throttling. A locked 60 FPS remains stable for hours."
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { SystemSettingsHelper.openBatterySaverSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = SolarGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Check Battery Saver & Charging Settings", color = TextPrimary)
                }
            }
        }
    }
}

@Composable
fun CoolingAdviceRow(title: String, desc: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.AcUnit,
                contentDescription = null,
                tint = CyberCyan,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
        }
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
            modifier = Modifier.padding(start = 24.dp)
        )
    }
}

@Composable
fun ThrottlingChart(samples: List<Float>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (samples.size < 2) return@Canvas
        val w = size.width
        val h = size.height

        val stepX = w / (samples.size - 1)
        val path = Path()

        samples.forEachIndexed { i, sample ->
            val clamped = sample.coerceIn(0f, 1f)
            val x = i * stepX
            val y = h - (clamped * (h - 10f)) - 5f

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = Color(0xFF00E5FF),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
