package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AdbCommandItem
import com.example.ui.components.CyberCard
import com.example.ui.components.SectionTitle
import com.example.ui.components.copyToClipboard
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
fun PerformanceAdbScreen(
    viewModel: GameBoosterViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var targetPackageInput by remember { mutableStateOf("com.miHoYo.GenshinImpact") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }

    val allTweaks = viewModel.adbTweaks
    val categories = listOf("All", "Game Manager (Android 12+)", "Display & GPU Load", "Stutter Reduction (JIT/AOT)", "Developer Options")

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Introduction to ADB & Shizuku
        item {
            CyberCard(borderColor = CyberCyan.copy(alpha = 0.4f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "ADB & Shizuku Tuning Engine",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Run these commands via USB ADB or the Shizuku app to apply low-level display downscaling, Android 12+ GameManager flags, and AOT speed compilation without requiring root.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                }
            }
        }

        // Custom Package Input Box
        item {
            CyberCard {
                SectionTitle(
                    title = "Target Game Package",
                    subtitle = "All per-game commands will automatically format with this package"
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = targetPackageInput,
                    onValueChange = { targetPackageInput = it },
                    label = { Text("Package Name (e.g. com.tencent.ig)", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("adb_target_package_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant,
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = DarkSurfaceBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // Display Downscaler Tool (wm size & wm density)
        item {
            CyberCard(borderColor = SolarGold.copy(alpha = 0.5f)) {
                SectionTitle(
                    title = "Display Downscaler (Highest FPS Gain)",
                    subtitle = "Downscales the physical framebuffer. Cuts GPU fillrate load by 35-50% on high-res displays.",
                    badge = "Max FPS",
                    badgeColor = SolarGold
                )
                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                copyToClipboard(
                                    context,
                                    "wm size 1080x2400 && wm density 380",
                                    "1080p Downscale Command Copied!"
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("1080p (2400x1080)", fontSize = 11.sp, color = TextPrimary)
                        }

                        OutlinedButton(
                            onClick = {
                                copyToClipboard(
                                    context,
                                    "wm size 720x1600 && wm density 260",
                                    "720p Downscale Command Copied!"
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("720p (1600x720)", fontSize = 11.sp, color = TextPrimary)
                        }
                    }

                    Button(
                        onClick = {
                            copyToClipboard(
                                context,
                                "wm size reset && wm density reset",
                                "Reset Display Command Copied!"
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset Resolution: wm size reset", color = CyberCyan, fontSize = 12.sp)
                    }
                }
            }
        }

        // Developer Options Best Practice Advice Card (Section 2)
        item {
            CyberCard(borderColor = DarkSurfaceBorder) {
                SectionTitle(
                    title = "Developer Options Rules & Truths",
                    subtitle = "Avoid common internet myths that harm gaming performance"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DevRuleBullet(
                        rule = "Animation Scales (0.5x or Off)",
                        explanation = "Makes the UI snappy and cuts transition delay, but does NOT increase in-game 3D rendering FPS."
                    )
                    DevRuleBullet(
                        rule = "Keep 'Force 4x MSAA' OFF",
                        explanation = "Forcing MSAA quadruples GPU fragment shading, leading to intense heat and throttling in 3D titles."
                    )
                    DevRuleBullet(
                        rule = "Leave 'Disable HW Overlays' ALONE",
                        explanation = "Disabling hardware overlays forces CPU composition, wasting CPU cycles and battery during matches."
                    )
                    DevRuleBullet(
                        rule = "Keep Background Process Limit 'Standard'",
                        explanation = "Artificially limiting background processes forces Android to constantly kill and reload services, introducing micro-stutters."
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { SystemSettingsHelper.openDeveloperOptions(context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open System Developer Options", color = TextPrimary)
                }
            }
        }

        // Category Filter Chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (cat in categories.take(3)) {
                    FilterChip(
                        selected = selectedCategoryFilter == cat,
                        onClick = { selectedCategoryFilter = cat },
                        label = { Text(cat, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                            selectedLabelColor = CyberCyan
                        )
                    )
                }
            }
        }

        // Filtered List of Commands
        val filteredTweaks = allTweaks.filter {
            selectedCategoryFilter == "All" || it.category == selectedCategoryFilter
        }

        items(filteredTweaks, key = { it.id }) { tweak ->
            AdbCommandItem(
                tweak = tweak,
                currentPackage = targetPackageInput
            )
        }
    }
}

@Composable
fun DevRuleBullet(rule: String, explanation: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(CyberCyan)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = rule,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
        }
        Text(
            text = explanation,
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
            modifier = Modifier.padding(start = 14.dp)
        )
    }
}
