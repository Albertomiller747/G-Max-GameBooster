package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.NetworkPingResult
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
import com.example.util.SystemSettingsHelper
import com.example.viewmodel.GameBoosterViewModel

@Composable
fun NetworkStorageScreen(
    viewModel: GameBoosterViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val wifiStatus by viewModel.wifiStatus.collectAsState()
    val pingResults by viewModel.pingResults.collectAsState()
    val isPingTesting by viewModel.isPingTesting.collectAsState()
    val memoryStats by viewModel.memoryStats.collectAsState()
    val storageSpeed by viewModel.storageSpeed.collectAsState()
    val isSpeedTesting by viewModel.isSpeedTesting.collectAsState()

    var wifiLockActive by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Wi-Fi Frequency & Low-Latency Lock
        item {
            CyberCard(borderColor = if (wifiStatus.is5GHzOr6GHz) NeonGreen.copy(alpha = 0.5f) else SolarGold.copy(alpha = 0.5f)) {
                SectionTitle(
                    title = "Wi-Fi Band & Radio Low-Latency",
                    subtitle = "Eliminate mid-match packet collisions & handoff lag",
                    badge = if (wifiStatus.is5GHzOr6GHz) "5GHz / 6GHz" else "2.4GHz",
                    badgeColor = if (wifiStatus.is5GHzOr6GHz) NeonGreen else SolarGold
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricGauge(
                        label = "Band Frequency",
                        value = if (wifiStatus.isWifi) "${wifiStatus.frequencyMhz} MHz" else "Cellular",
                        subValue = if (wifiStatus.is5GHzOr6GHz) "Optimal high bandwidth" else "Crowded band",
                        percent = if (wifiStatus.is5GHzOr6GHz) 1f else 0.4f,
                        barColor = if (wifiStatus.is5GHzOr6GHz) NeonGreen else SolarGold,
                        icon = Icons.Default.Wifi,
                        modifier = Modifier.weight(1f)
                    )

                    MetricGauge(
                        label = "Link Speed",
                        value = "${wifiStatus.linkSpeedMbps} Mbps",
                        subValue = "RSSI: ${wifiStatus.rssi} dBm",
                        percent = (wifiStatus.linkSpeedMbps / 1000f).coerceIn(0f, 1f),
                        barColor = CyberCyan,
                        icon = Icons.Default.Speed,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Low Latency Lock Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurfaceVariant)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "WIFI_MODE_FULL_LOW_LATENCY",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "Forces Wi-Fi chip into high-frequency listening mode. (Note: Android restricts lock to foreground sessions).",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                    Switch(
                        checked = wifiLockActive,
                        onCheckedChange = {
                            wifiLockActive = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CyberCyan
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { SystemSettingsHelper.openNetworkSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Wifi, contentDescription = null, tint = CyberCyan)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Turn Off Wi-Fi Assist & Smart Network Switch", color = TextPrimary)
                }
            }
        }

        // Real Game Server Ping & Jitter Tester (Section 5)
        item {
            CyberCard(borderColor = CyberCyan.copy(alpha = 0.5f)) {
                SectionTitle(
                    title = "Game Server Latency & Jitter Tester",
                    subtitle = "Real socket handshake latency against worldwide tournament servers"
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { viewModel.runAllPingTests() },
                    enabled = !isPingTesting,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF001F26)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("run_ping_test_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isPingTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFF001F26),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("TESTING SERVER PING & PACKET LOSS...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("TEST ALL SERVER REGIONS", fontWeight = FontWeight.Black)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (result in pingResults) {
                        PingResultItem(result = result)
                    }
                }
            }
        }

        // Private DNS Guidance Card
        item {
            CyberCard {
                SectionTitle(
                    title = "Private DNS: Honest Truth",
                    subtitle = "DNS-over-TLS (Cloudflare 1.1.1.1 / Google 8.8.8.8)",
                    badge = "Fact Check",
                    badgeColor = SolarGold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "A common myth claims Private DNS reduces in-game ping. In reality, Private DNS only accelerates the initial hostname lookup when connecting. In-match gameplay uses direct UDP packets where DNS plays zero role.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { SystemSettingsHelper.openPrivateDnsSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Dns, contentDescription = null, tint = SolarGold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Configure Private DNS (1dot1dot1dot1.cloudflare-dns.com)", color = TextPrimary)
                }
            }
        }

        // Internal Storage Health & Flash Speed Benchmark (Section 7)
        item {
            val freeGb = memoryStats.internalStorageFreeGb
            val totalGb = memoryStats.internalStorageTotalGb
            val freeRatio = if (totalGb > 0) freeGb / totalGb else 1.0
            val isStorageWarning = freeRatio < 0.15

            CyberCard(borderColor = if (isStorageWarning) ElectricCrimson else DarkSurfaceBorder) {
                SectionTitle(
                    title = "Internal Storage Health & UFS Flash Speed",
                    subtitle = "Crucial for Android 3D game shader caching and texture streaming",
                    badge = if (isStorageWarning) "LOW SPACE (<15%)" else "HEALTHY",
                    badgeColor = if (isStorageWarning) ElectricCrimson else NeonGreen
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricGauge(
                        label = "Free Storage",
                        value = "${freeGb} GB Free",
                        subValue = "Total: ${totalGb} GB",
                        percent = (1.0 - freeRatio).toFloat(),
                        barColor = if (isStorageWarning) ElectricCrimson else CyberCyan,
                        icon = Icons.Default.Storage,
                        modifier = Modifier.weight(1f)
                    )

                    val writeMb = storageSpeed?.writeSpeedMBps ?: 0.0
                    MetricGauge(
                        label = "Flash Write Speed",
                        value = if (writeMb > 0) "${writeMb.toInt()} MB/s" else "Untested",
                        subValue = "Sequential write",
                        percent = (writeMb / 800.0).toFloat().coerceIn(0f, 1f),
                        barColor = NeonGreen,
                        icon = Icons.Default.Speed,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (isStorageWarning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = ElectricCrimson, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Maintain 15-20% free space! Full flash memory causes massive texture loading stutters.",
                            style = MaterialTheme.typography.labelSmall.copy(color = ElectricCrimson)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Storage Speed Benchmark Button
                Button(
                    onClick = { viewModel.runStorageBenchmark() },
                    enabled = !isSpeedTesting,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("run_storage_speed_test"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isSpeedTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = CyberCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("TESTING FLASH READ/WRITE SPEED...", color = CyberCyan)
                    } else {
                        Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = CyberCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("RUN FLASH SPEED BENCHMARK (MB/s)", color = CyberCyan, fontWeight = FontWeight.Bold)
                    }
                }

                storageSpeed?.let { result ->
                    if (result.isSuccess) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Results: Write: ${result.writeSpeedMBps} MB/s | Read: ${result.readSpeedMBps} MB/s",
                            style = MaterialTheme.typography.bodySmall.copy(color = NeonGreen, fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { SystemSettingsHelper.openStorageSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.CleaningServices, contentDescription = null, tint = TextSecondary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open System Storage Manager (Clear Junk)", color = TextPrimary)
                }
            }
        }
    }
}

@Composable
fun PingResultItem(result: NetworkPingResult) {
    val pingColor = when {
        result.pingMs == 0L -> TextMuted
        result.pingMs < 50 -> NeonGreen
        result.pingMs < 100 -> SolarGold
        else -> ElectricCrimson
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceVariant)
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.regionName,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
            )
            Text(
                text = result.host,
                style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
            )
        }

        if (result.pingMs > 0) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${result.pingMs} ms",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = pingColor)
                )
                Text(
                    text = "Jitter: ${result.jitterMs}ms • Loss: ${result.packetLossPercent}%",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp)
                )
            }
        } else {
            Text(
                text = result.error ?: "Ready",
                style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
            )
        }
    }
}
