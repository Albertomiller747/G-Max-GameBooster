package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppBackgroundTheme
import com.example.ui.components.CyberCard
import com.example.ui.components.SectionTitle
import com.example.ui.theme.CuratedBackgroundPresets
import com.example.ui.theme.HubBlue
import com.example.ui.theme.LaunchGreen
import com.example.ui.theme.RecorderCoral
import com.example.util.SystemSettingsHelper
import com.example.viewmodel.GameBoosterViewModel
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: GameBoosterViewModel,
    onOpenAccountProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTheme by viewModel.appTheme.collectAsState()
    val customBgArgb by viewModel.customBackgroundColor.collectAsState()
    val suppressWarning by viewModel.suppressActivationWarning.collectAsState()
    val reducedAnimations by viewModel.reducedAnimations.collectAsState()
    val autoBigScreenLandscape by viewModel.autoBigScreenLandscape.collectAsState()
    val showControllerLegendBar by viewModel.showControllerLegendBar.collectAsState()
    val currentFirebaseUser by viewModel.currentFirebaseUser.collectAsState()
    val cloudSyncStatus by viewModel.cloudSyncStatus.collectAsState()
    val isSignedIn = currentFirebaseUser != null

    // Extract RGB components from customBgArgb
    val redChannel = ((customBgArgb shr 16) and 0xFF)
    val greenChannel = ((customBgArgb shr 8) and 0xFF)
    val blueChannel = (customBgArgb and 0xFF)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = HubBlue,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Hub Appearance & Settings",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Automatic Light/Dark display sync, custom backgrounds, and cloud account backup",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        // 1b. Account & Cloud Sync Shortcut Card
        item {
            CyberCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSignedIn) LaunchGreen.copy(alpha = 0.16f)
                                    else HubBlue.copy(alpha = 0.14f)
                                )
                                .border(
                                    1.5.dp,
                                    if (isSignedIn) LaunchGreen else HubBlue,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSignedIn) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = if (isSignedIn) LaunchGreen else HubBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isSignedIn) {
                                    currentFirebaseUser?.displayName?.takeIf { it.isNotBlank() }
                                        ?: currentFirebaseUser?.email
                                        ?: "Signed-In Account"
                                } else {
                                    "Guest Profile (Local Settings Only)"
                                },
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = cloudSyncStatus,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isSignedIn) LaunchGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    Button(
                        onClick = onOpenAccountProfile,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HubBlue,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("settings_open_account_profile_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSignedIn) "Account" else "Sign In",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 2. Display Theme & Custom Background Engine
        item {
            CyberCard {
                SectionTitle(
                    title = "Display Mode & Background",
                    subtitle = "Automatically follows your device display mode (Light / Dark) or use a custom color"
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppBackgroundTheme.values().forEach { themeOption ->
                        val isSelected = currentTheme == themeOption
                        var isFocused by remember { mutableStateOf(false) }

                        val optionIcon = when (themeOption) {
                            AppBackgroundTheme.AUTO_SYSTEM -> Icons.Default.BrightnessAuto
                            AppBackgroundTheme.LIGHT_HUB -> Icons.Default.LightMode
                            AppBackgroundTheme.DARK_HUB -> Icons.Default.DarkMode
                            AppBackgroundTheme.CUSTOM_COLOR -> Icons.Default.ColorLens
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isFocused = it.isFocused }
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    width = if (isSelected || isFocused) 2.dp else 1.dp,
                                    color = when {
                                        isFocused -> Color.White
                                        isSelected -> HubBlue
                                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    },
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { viewModel.setAppTheme(themeOption) }
                                .focusable()
                                .testTag("theme_option_${themeOption.name.lowercase()}"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    HubBlue.copy(alpha = 0.10f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (themeOption == AppBackgroundTheme.CUSTOM_COLOR) {
                                                    Color(customBgArgb or (0xFFL shl 24))
                                                } else {
                                                    HubBlue.copy(alpha = 0.14f)
                                                }
                                            )
                                            .border(
                                                1.5.dp,
                                                if (isSelected) HubBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = optionIcon,
                                            contentDescription = null,
                                            tint = if (themeOption == AppBackgroundTheme.CUSTOM_COLOR) {
                                                Color.White
                                            } else if (isSelected) {
                                                HubBlue
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column {
                                        Text(
                                            text = themeOption.title,
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                        Text(
                                            text = themeOption.description,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(HubBlue),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Curated Background Presets + Custom RGB Color Picker
                AnimatedVisibility(visible = currentTheme == AppBackgroundTheme.CUSTOM_COLOR) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Text(
                            text = "CURATED BACKGROUND PRESETS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.8.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(CuratedBackgroundPresets) { preset ->
                                val isPresetSelected = customBgArgb == preset.bgHex
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable {
                                            viewModel.setCustomBackgroundColor(preset.bgHex)
                                        }
                                        .padding(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(Color(preset.bgHex))
                                            .border(
                                                width = if (isPresetSelected) 3.dp else 1.dp,
                                                color = if (isPresetSelected) HubBlue else MaterialTheme.colorScheme.outline,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isPresetSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = preset.name,
                                                tint = if (preset.isLight) Color.Black else Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = preset.name,
                                        fontSize = 11.sp,
                                        fontWeight = if (isPresetSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Custom RGB Color Picker Sliders
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Custom Color Mixer",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = String.format(
                                            Locale.US,
                                            "#%02X%02X%02X",
                                            redChannel,
                                            greenChannel,
                                            blueChannel
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = HubBlue
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Red Slider
                                ColorChannelSlider(
                                    label = "Red",
                                    value = redChannel.toInt(),
                                    trackColor = Color(0xFFEF4444),
                                    onValueChange = { newR ->
                                        val newArgb = (0xFFL shl 24) or
                                                ((newR.toLong() and 0xFF) shl 16) or
                                                ((greenChannel and 0xFF) shl 8) or
                                                (blueChannel and 0xFF)
                                        viewModel.setCustomBackgroundColor(newArgb)
                                    }
                                )

                                // Green Slider
                                ColorChannelSlider(
                                    label = "Green",
                                    value = greenChannel.toInt(),
                                    trackColor = Color(0xFF22C55E),
                                    onValueChange = { newG ->
                                        val newArgb = (0xFFL shl 24) or
                                                ((redChannel and 0xFF) shl 16) or
                                                ((newG.toLong() and 0xFF) shl 8) or
                                                (blueChannel and 0xFF)
                                        viewModel.setCustomBackgroundColor(newArgb)
                                    }
                                )

                                // Blue Slider
                                ColorChannelSlider(
                                    label = "Blue",
                                    value = blueChannel.toInt(),
                                    trackColor = Color(0xFF3B82F6),
                                    onValueChange = { newB ->
                                        val newArgb = (0xFFL shl 24) or
                                                ((redChannel and 0xFF) shl 16) or
                                                ((greenChannel and 0xFF) shl 8) or
                                                (newB.toLong() and 0xFF)
                                        viewModel.setCustomBackgroundColor(newArgb)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2b. Lite Mode: Reduced Animations for Low-End Phones
        item {
            CyberCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle(
                        title = "Lite Mode (Reduced Animations)",
                        subtitle = "Disables heavy spring transitions, tile scaling, and boot effects for low-end phones"
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (viewModel.isLowRamDeviceDetected) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(LaunchGreen.copy(alpha = 0.14f))
                            .border(1.dp, LaunchGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Low-RAM phone detected (≤ 4 GB) • Auto-optimized for smooth UI",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = LaunchGreen
                        )
                    }
                }

                var liteModeFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { liteModeFocused = it.isFocused }
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = if (liteModeFocused) 2.dp else 1.dp,
                            color = if (liteModeFocused) Color.White else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            com.example.util.ConsoleHapticManager.playSelectClick(context)
                            viewModel.setReducedAnimations(!reducedAnimations)
                        }
                        .focusable()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Lite Mode (Reduced Animations)",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = if (reducedAnimations) {
                                "Active: Instant transitions & 0-lag static tile focus"
                            } else {
                                "Off: Full console spring physics & startup animation"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (reducedAnimations) LaunchGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = if (reducedAnimations) FontWeight.SemiBold else FontWeight.Normal
                            )
                        )
                    }

                    Switch(
                        checked = reducedAnimations,
                        onCheckedChange = {
                            com.example.util.ConsoleHapticManager.playSelectClick(context)
                            viewModel.setReducedAnimations(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = LaunchGreen
                        ),
                        modifier = Modifier.testTag("toggle_lite_mode_animations_switch")
                    )
                }
            }
        }

        // 2c. Console & Controller Preferences (Auto Big Screen on Landscape + Controller Shortcut Overlay Toggle)
        item {
            CyberCard {
                SectionTitle(
                    title = "Console & Controller Experience",
                    subtitle = "Automatic landscape Big Screen mode & controller shortcut bar visibility"
                )

                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Toggle 1: Auto Big Screen Mode when rotated to landscape
                    var autoBigFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { autoBigFocused = it.isFocused }
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (autoBigFocused) 2.dp else 1.dp,
                                color = if (autoBigFocused) Color.White else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                com.example.util.ConsoleHapticManager.playSelectClick(context)
                                viewModel.setAutoBigScreenLandscape(!autoBigScreenLandscape)
                            }
                            .focusable()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Automatic Big Screen on Landscape",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "Automatically switch to Big Screen Mode when turning your phone sideways. Pressing the Controller Home/Guide button always activates Big Screen Mode even if turned off.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = autoBigScreenLandscape,
                            onCheckedChange = {
                                com.example.util.ConsoleHapticManager.playSelectClick(context)
                                viewModel.setAutoBigScreenLandscape(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = HubBlue
                            ),
                            modifier = Modifier.testTag("toggle_auto_big_screen_landscape")
                        )
                    }

                    // Toggle 2: A Launch • X Boost • Y Record • L1/R1 Controller Shortcut Overlay
                    var legendFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { legendFocused = it.isFocused }
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (legendFocused) 2.dp else 1.dp,
                                color = if (legendFocused) Color.White else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                com.example.util.ConsoleHapticManager.playSelectClick(context)
                                viewModel.setShowControllerLegendBar(!showControllerLegendBar)
                            }
                            .focusable()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Controller Shortcut Overlay Bar",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "Show 'A Launch • X Boost • Y Record • L1/R1' legend bar when a controller is connected (enabled by default)",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = showControllerLegendBar,
                            onCheckedChange = {
                                com.example.util.ConsoleHapticManager.playSelectClick(context)
                                viewModel.setShowControllerLegendBar(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = LaunchGreen
                            ),
                            modifier = Modifier.testTag("toggle_controller_legend_bar")
                        )
                    }
                }
            }
        }

        // 3. Unified Permissions Center Card
        item {
            CyberCard {
                SectionTitle(
                    title = "Permissions & Feature Access",
                    subtitle = "Review what each permission grants and why features need it"
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { viewModel.setShowPermissionsPage(true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_permissions_overview_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HubBlue,
                        contentColor = Color.White
                    )
                ) {
                    Icon(imageVector = Icons.Default.Security, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Open Unified Permissions Page",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 4. Tweak Safeguard & Warnings
        item {
            CyberCard {
                SectionTitle(
                    title = "Tweak Safety Warnings",
                    subtitle = "Disclosure before applying performance tweaks"
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show Warnings Before Activating",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = "Alerts about online game anti-cheat notices, heat buildup, and animation changes",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }

                    Switch(
                        checked = !suppressWarning,
                        onCheckedChange = { viewModel.setSuppressActivationWarning(!it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = HubBlue
                        ),
                        modifier = Modifier.testTag("toggle_warning_switch")
                    )
                }
            }
        }

        // 5. System & Hardware Shortcuts
        item {
            CyberCard {
                SectionTitle(
                    title = "Device System Shortcuts",
                    subtitle = "Direct access to OS developer and display controls"
                )

                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { SystemSettingsHelper.openDeveloperOptions(context) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeveloperMode,
                            contentDescription = null,
                            tint = RecorderCoral
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Open Developer Options",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    OutlinedButton(
                        onClick = { SystemSettingsHelper.openDisplaySettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = HubBlue
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Open Display & 120Hz Refresh Settings",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    OutlinedButton(
                        onClick = { SystemSettingsHelper.openOverlayPermissionSettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = LaunchGreen
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Screen Overlay (HUD) Permission",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorChannelSlider(
    label: String,
    value: Int,
    trackColor: Color,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(46.dp)
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(
                thumbColor = trackColor,
                activeTrackColor = trackColor
            ),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value.toString(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(30.dp)
        )
    }
}
