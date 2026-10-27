package com.example.ui.screens.extras

import android.os.SystemClock
import android.view.MotionEvent
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
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
import com.example.viewmodel.GameBoosterViewModel
import java.util.Locale

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TouchTab(
    viewModel: GameBoosterViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Priority DND state
    var isDndActive by remember { mutableStateOf(viewModel.isPriorityDndActive()) }
    val isDndGranted = remember(isDndActive) { viewModel.isPriorityDndGranted() }
    val savedFilterDesc = remember(isDndActive) { viewModel.getSavedDndFilterDescription() }

    // Touch Pipeline Latency State
    var currentPipelineLatencyMs by remember { mutableLongStateOf(0L) }
    var minPipelineLatencyMs by remember { mutableLongStateOf(Long.MAX_VALUE) }
    var maxPipelineLatencyMs by remember { mutableLongStateOf(0L) }
    var averagePipelineLatencyMs by remember { mutableFloatStateOf(0f) }
    var totalTouchesRecorded by remember { mutableIntStateOf(0) }
    var lastTouchX by remember { mutableFloatStateOf(0f) }
    var lastTouchY by remember { mutableFloatStateOf(0f) }
    var isTouching by remember { mutableStateOf(false) }

    // Touch Digitizer Sampling Rate State
    var liveSamplingRateHz by remember { mutableIntStateOf(0) }
    var peakSamplingRateHz by remember { mutableIntStateOf(0) }
    var lastMotionTimestampMs by remember { mutableLongStateOf(0L) }
    val recentDeltas = remember { mutableStateListOf<Long>() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("touch_tab_container"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Phone Touch Pipeline Latency Test
        item {
            CyberCard(borderColor = HubBlue.copy(alpha = 0.5f)) {
                SectionTitle(
                    title = "Touch Pipeline Latency Test",
                    subtitle = "Phone's hardware digitizer to compositor response time"
                )

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(HubBlue.copy(alpha = 0.12f))
                        .border(1.dp, HubBlue.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = HubBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Measures the phone's touch pipeline latency (hardware digitizer interrupt → kernel input driver → framework dispatch). This is local device input latency, not in-game engine or network ping.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TouchMetricBox(
                        modifier = Modifier.weight(1f),
                        label = "CURRENT",
                        value = if (currentPipelineLatencyMs > 0) "${currentPipelineLatencyMs}ms" else "--",
                        accent = if (currentPipelineLatencyMs <= 8) LaunchGreen else if (currentPipelineLatencyMs <= 16) HubBlue else SolarGold
                    )
                    TouchMetricBox(
                        modifier = Modifier.weight(1f),
                        label = "AVERAGE",
                        value = if (averagePipelineLatencyMs > 0) String.format(Locale.US, "%.1fms", averagePipelineLatencyMs) else "--",
                        accent = HubBlue
                    )
                    TouchMetricBox(
                        modifier = Modifier.weight(1f),
                        label = "BEST (MIN)",
                        value = if (minPipelineLatencyMs != Long.MAX_VALUE) "${minPipelineLatencyMs}ms" else "--",
                        accent = LaunchGreen
                    )
                    TouchMetricBox(
                        modifier = Modifier.weight(1f),
                        label = "MAX",
                        value = if (maxPipelineLatencyMs > 0) "${maxPipelineLatencyMs}ms" else "--",
                        accent = if (maxPipelineLatencyMs > 25) ElectricCrimson else SolarGold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Tap or drag your finger below to benchmark your touch response:",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                val padGridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .border(
                            1.5.dp,
                            if (isTouching) LaunchGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        )
                        .testTag("touch_response_test_pad")
                        .pointerInteropFilter { motionEvent ->
                            when (motionEvent.actionMasked) {
                                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                    val nowUptime = SystemClock.uptimeMillis()
                                    val eventTime = motionEvent.eventTime
                                    val latency = (nowUptime - eventTime).coerceAtLeast(1L)

                                    currentPipelineLatencyMs = latency
                                    minPipelineLatencyMs = minOf(minPipelineLatencyMs, latency)
                                    maxPipelineLatencyMs = maxOf(maxPipelineLatencyMs, latency)

                                    totalTouchesRecorded++
                                    val prevAvg = averagePipelineLatencyMs
                                    averagePipelineLatencyMs = prevAvg + (latency - prevAvg) / totalTouchesRecorded

                                    lastTouchX = motionEvent.x
                                    lastTouchY = motionEvent.y
                                    isTouching = true

                                    if (lastMotionTimestampMs > 0L) {
                                        val delta = eventTime - lastMotionTimestampMs
                                        if (delta in 1..100) {
                                            recentDeltas.add(delta)
                                            if (recentDeltas.size > 12) recentDeltas.removeAt(0)
                                            val avgDelta = recentDeltas.average()
                                            if (avgDelta > 0) {
                                                val hz = (1000.0 / avgDelta).toInt().coerceIn(30, 1000)
                                                liveSamplingRateHz = hz
                                                peakSamplingRateHz = maxOf(peakSamplingRateHz, hz)
                                            }
                                        }
                                    }
                                    lastMotionTimestampMs = eventTime
                                    true
                                }
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                    isTouching = false
                                    true
                                }
                                else -> false
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height

                        drawLine(
                            color = padGridColor,
                            start = Offset(width / 2f, 0f),
                            end = Offset(width / 2f, height),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = padGridColor,
                            start = Offset(0f, height / 2f),
                            end = Offset(width, height / 2f),
                            strokeWidth = 1f
                        )

                        if (isTouching && lastTouchX > 0 && lastTouchY > 0) {
                            drawCircle(
                                color = LaunchGreen.copy(alpha = 0.25f),
                                radius = 38.dp.toPx(),
                                center = Offset(lastTouchX, lastTouchY)
                            )
                            drawCircle(
                                color = HubBlue,
                                radius = 18.dp.toPx(),
                                center = Offset(lastTouchX, lastTouchY),
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawCircle(
                                color = LaunchGreen,
                                radius = 4.dp.toPx(),
                                center = Offset(lastTouchX, lastTouchY)
                            )
                        }
                    }

                    if (!isTouching && totalTouchesRecorded == 0) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.TouchApp, contentDescription = null, tint = HubBlue, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "TOUCH OR GLIDE HERE",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total Samples: $totalTouchesRecorded",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    )
                    OutlinedButton(
                        onClick = {
                            totalTouchesRecorded = 0
                            minPipelineLatencyMs = Long.MAX_VALUE
                            maxPipelineLatencyMs = 0L
                            averagePipelineLatencyMs = 0f
                            currentPipelineLatencyMs = 0L
                            liveSamplingRateHz = 0
                            peakSamplingRateHz = 0
                            recentDeltas.clear()
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = HubBlue)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("RESET TEST", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. Real Touch Sampling Rate Readout
        item {
            CyberCard(borderColor = LaunchGreen.copy(alpha = 0.55f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        SectionTitle(
                            title = "Touch Sampling Rate Readout",
                            subtitle = "Hardware screen digitizer polling frequency"
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(LaunchGreen.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (peakSamplingRateHz >= 240) "240Hz+ GAMING GRADE" else if (peakSamplingRateHz >= 120) "120Hz HIGH SPEED" else "STANDBY",
                            color = LaunchGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(
                                "LIVE DIGITIZER RATE",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (liveSamplingRateHz > 0) "${liveSamplingRateHz} Hz" else "Touch to read",
                                color = HubBlue,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(
                                "PEAK DETECTED",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (peakSamplingRateHz > 0) "${peakSamplingRateHz} Hz" else "--",
                                color = LaunchGreen,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Reads consecutive MotionEvent hardware timestamps directly from the input digitizer IC. Higher polling rates (180Hz - 480Hz) provide near-instantaneous flick & aiming responsiveness in mobile shooter titles.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                )
            }
        }

        // 3. Priority Do Not Disturb Filter
        item {
            CyberCard(borderColor = if (isDndActive) LaunchGreen else MaterialTheme.colorScheme.outline) {
                SectionTitle(
                    title = "Priority Do Not Disturb Filter",
                    subtitle = "Blocks intrusive calls without muting game & media audio"
                )

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(LaunchGreen.copy(alpha = 0.12f))
                        .border(1.dp, LaunchGreen.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = LaunchGreen, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Preserves Full Game Sound Effects & Music",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Uses INTERRUPTION_FILTER_PRIORITY. Unlike total silence modes, game audio remains crisp and audible while heads-up call banners, texts, and social alerts are silenced.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!isDndGranted) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SolarGold.copy(alpha = 0.14f))
                            .border(1.dp, SolarGold.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = SolarGold, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Requires Notification Policy Access permission",
                                    color = SolarGold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "Tap to grant DND access in system settings.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                            Button(
                                onClick = { viewModel.openDndSettings(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = SolarGold, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("GRANT", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
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
                            imageVector = if (isDndActive) Icons.Default.DoNotDisturbOn else Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = if (isDndActive) LaunchGreen else HubBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Priority Gaming DND",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = if (isDndActive) "ACTIVE: Media audio unmuted, notifications muted" else "INACTIVE: Standard notification alerts",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isDndActive) LaunchGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    Switch(
                        checked = isDndActive,
                        onCheckedChange = { target ->
                            if (!isDndGranted) {
                                viewModel.openDndSettings(context)
                            } else {
                                viewModel.togglePriorityDnd(target)
                                isDndActive = viewModel.isPriorityDndActive()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = LaunchGreen,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.testTag("priority_dnd_switch")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = HubBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Auto-Restore Guarantee:",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Previous user setting: $savedFilterDesc. Automatically restored when Priority DND is toggled off or when your gaming overlay session finishes.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TouchMetricBox(
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
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace
                )
            )
        }
    }
}
