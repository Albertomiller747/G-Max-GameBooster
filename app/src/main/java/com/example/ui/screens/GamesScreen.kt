package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.BoostMode
import com.example.model.InstalledApp
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.SolarGold
import com.example.util.SystemSettingsHelper
import com.example.viewmodel.GameBoosterViewModel
import java.util.Locale

// Styling colors matching reference screenshot
val HubBgDark = Color(0xFF090D1A)
val CardSurfaceDark = Color(0xFF13192B)
val CardSurfaceBorder = Color(0xFF1E283E)
val BarProgressBlue = Color(0xFF3880F6)
val BarTrackDark = Color(0xFF0D1322)
val TextMutedGray = Color(0xFF8893A7)
val HealthyGreen = Color(0xFF10B981)
val HealthyBadgeBg = Color(0xFF0B2E21)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(
    viewModel: GameBoosterViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val memoryStats by viewModel.memoryStats.collectAsState()
    val thermalInfo by viewModel.thermalInfo.collectAsState()
    val hardwareSpecs by viewModel.hardwareSpecs.collectAsState()
    val selectedBoostMode by viewModel.selectedBoostMode.collectAsState()
    val libraryGames by viewModel.libraryGames.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val sessionState by viewModel.sessionUiState.collectAsState()
    val overlayConfig by viewModel.overlayConfig.collectAsState()
    val currentTheme by viewModel.appTheme.collectAsState()
    val boostFeedback by viewModel.boostFeedback.collectAsState()

    var showAddGameDialog by remember { mutableStateOf(false) }
    var showModeSelectorDialog by remember { mutableStateOf(false) }
    var selectedGameForMenu by remember { mutableStateOf<InstalledApp?>(null) }
    val menuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Calculate Free RAM & percentages
    val totalRamGb = hardwareSpecs.totalRamMb / 1024.0
    val freeRamGb = hardwareSpecs.availRamMb / 1024.0
    val freeRamPercent = if (totalRamGb > 0) ((freeRamGb / totalRamGb) * 100).coerceIn(0.0, 100.0) else 0.0

    // Storage metrics
    val totalStorageGb = hardwareSpecs.totalStorageGb
    val freeStorageGb = hardwareSpecs.freeStorageGb
    val freeStoragePercent = if (totalStorageGb > 0) ((freeStorageGb / totalStorageGb) * 100).coerceIn(0.0, 100.0) else 0.0

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(currentTheme.backgroundColor)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Top Header: [ = ] Games  | X in library
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Games",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 24.sp
                            )
                        )
                        Text(
                            text = "${libraryGames.size} in library",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextMutedGray,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }

            // Quick feedback alert if active
            if (boostFeedback != null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF003844))
                            .border(1.dp, CyberCyan.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = boostFeedback ?: "",
                                color = CyberCyan,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearFeedback() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = CyberCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Active Boost Countdown Banner if session is active
            if (sessionState.isActive) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (sessionState.mode == BoostMode.BEAST) Color(0xFF351111) else Color(0xFF0B2144)
                            )
                            .border(
                                1.dp,
                                if (sessionState.mode == BoostMode.BEAST) Color(0xFFEF4444) else BarProgressBlue,
                                RoundedCornerShape(14.dp)
                            )
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = if (sessionState.mode == BoostMode.BEAST) Color(0xFFEF4444) else CyberCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "${sessionState.mode?.title ?: "BOOST"} ENGAGED",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (sessionState.mode == BoostMode.BEAST) Color(0xFFEF4444) else CyberCyan,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                    Text(
                                        text = "Remaining: ${sessionState.formattedCountdown}",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { viewModel.stopBoosterSession() },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text("End", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 2. Card: "Device status" with Free RAM, Free Storage, Thermals and Refresh Rate
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CardSurfaceBorder, RoundedCornerShape(18.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        // Title row: "Device status" + [Healthy]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Device status",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 17.sp
                                )
                            )

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(HealthyBadgeBg)
                                    .border(1.dp, HealthyGreen.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (sessionState.isActive) "Boosted" else "Healthy",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = HealthyGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Metric 1: Free RAM
                        StatusMetricRow(
                            label = "Free RAM",
                            percentageText = String.format(Locale.US, "%.1f%% free", freeRamPercent),
                            fractionText = String.format(Locale.US, "%.1f GB / %.1f GB", freeRamGb, totalRamGb),
                            fractionProgress = (freeRamPercent / 100f).toFloat(),
                            percentageColor = HealthyGreen,
                            barColor = BarProgressBlue
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Metric 2: Free storage
                        StatusMetricRow(
                            label = "Free storage",
                            percentageText = String.format(Locale.US, "%.1f%% free", freeStoragePercent),
                            fractionText = String.format(Locale.US, "%.1f GB / %.1f GB", freeStorageGb, totalStorageGb),
                            fractionProgress = (freeStoragePercent / 100f).toFloat(),
                            percentageColor = BarProgressBlue,
                            barColor = BarProgressBlue
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Metric 3: Thermals (Direct hardware battery broadcast)
                        val tempVal = thermalInfo.batteryTempC
                        val tempStatus = when {
                            tempVal < 36f -> "Optimal"
                            tempVal < 42f -> "Normal"
                            else -> "Warm"
                        }
                        val tempColor = when {
                            tempVal < 36f -> HealthyGreen
                            tempVal < 42f -> CyberCyan
                            else -> SolarGold
                        }
                        StatusMetricRow(
                            label = "Thermals",
                            percentageText = "$tempStatus (${String.format(Locale.US, "%.1f°C", tempVal)})",
                            fractionText = String.format(Locale.US, "%.1f°C / 50°C Max", tempVal),
                            fractionProgress = (tempVal / 50f).coerceIn(0f, 1f),
                            percentageColor = tempColor,
                            barColor = tempColor
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Metric 4: Refresh rate
                        val refreshHz = hardwareSpecs.refreshRateHz
                        StatusMetricRow(
                            label = "Refresh rate",
                            percentageText = "$refreshHz Hz Dynamic",
                            fractionText = "$refreshHz Hz Peak Hardware",
                            fractionProgress = (refreshHz / 165f).coerceIn(0.3f, 1f),
                            percentageColor = BarProgressBlue,
                            barColor = BarProgressBlue
                        )
                    }
                }
            }

            // 3. Quick Action Buttons: [ Mode ] [ Add game ]
            // (Only these two, per instructions: "dont add the {ads, settings button, Sync} but keep add game and mode")
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Button 1: Mode
                    val isBeast = selectedBoostMode == BoostMode.BEAST
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(86.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showModeSelectorDialog = true }
                            .testTag("action_mode_button"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isBeast) Color(0xFF3B1515) else Color(0xFF2662F0)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = if (isBeast) Color(0xFFF87171) else Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isBeast) "Mode: Beast" else "Mode: Normal",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }

                    // Button 2: Add game
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(86.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, CardSurfaceBorder, RoundedCornerShape(16.dp))
                            .clickable { showAddGameDialog = true }
                            .testTag("action_add_game_button"),
                        colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Add game",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                }
            }

            // 4. Header: LIBRARY  (count)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIBRARY",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Black,
                            color = TextMutedGray,
                            letterSpacing = 1.sp,
                            fontSize = 14.sp
                        )
                    )

                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(CardSurfaceBorder),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${libraryGames.size}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }

            // 5. Library Games Cards List / Row (matching Screenshot 1 & 2 layout)
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    // Added Games Cards
                    items(libraryGames, key = { it.packageName }) { game ->
                        GameLibraryCard(
                            game = game,
                            onLaunch = {
                                // "if the user press launch it should normal mode boost will activate"
                                viewModel.quickBoost(targetApp = game, mode = BoostMode.GAMING)
                            },
                            onOpenMenu = {
                                selectedGameForMenu = game
                            }
                        )
                    }

                    // Dashed Add Game card (matching reference image)
                    item {
                        DashedAddGameCard(
                            onClick = { showAddGameDialog = true }
                        )
                    }
                }
            }
        }

        // Add Game Dialog Picker
        if (showAddGameDialog) {
            AddGameDialog(
                installedApps = installedApps,
                libraryPackages = libraryGames.map { it.packageName }.toSet(),
                onAddGame = { app ->
                    viewModel.addGameToLibrary(app)
                    showAddGameDialog = false
                },
                onDismiss = { showAddGameDialog = false }
            )
        }

        // Mode Selector Dialog
        if (showModeSelectorDialog) {
            ModeSelectorDialog(
                currentMode = selectedBoostMode,
                onSelectMode = { mode ->
                    viewModel.setBoostMode(mode)
                    showModeSelectorDialog = false
                },
                onDismiss = { showModeSelectorDialog = false }
            )
        }

        // 3-Lines Options BottomSheet for game
        selectedGameForMenu?.let { game ->
            ModalBottomSheet(
                onDismissRequest = { selectedGameForMenu = null },
                sheetState = menuSheetState,
                containerColor = CardSurfaceDark,
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 10.dp)
                            .size(width = 40.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF374151))
                    )
                }
            ) {
                GameOptionsMenuContent(
                    game = game,
                    currentBoostMode = selectedBoostMode,
                    isOverlayEnabled = overlayConfig.isEnabled,
                    onToggleMode = { mode ->
                        viewModel.setBoostMode(mode)
                    },
                    onToggleOverlay = { enabled ->
                        viewModel.toggleOverlayEnabled(context, enabled)
                    },
                    onLaunchBeast = {
                        selectedGameForMenu = null
                        viewModel.quickBoost(targetApp = game, mode = BoostMode.BEAST)
                    },
                    onRemoveGame = {
                        viewModel.removeGameFromLibrary(game.packageName)
                        selectedGameForMenu = null
                    },
                    onOpenAppInfo = {
                        SystemSettingsHelper.openAppDetails(context, game.packageName)
                    },
                    onDismiss = { selectedGameForMenu = null }
                )
            }
        }
    }
}

/**
 * Reusable Metric Row for the Device Status card
 */
@Composable
fun StatusMetricRow(
    label: String,
    percentageText: String,
    fractionText: String,
    fractionProgress: Float,
    percentageColor: Color,
    barColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = TextMutedGray,
                    fontSize = 13.sp
                )
            )
            Text(
                text = percentageText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = percentageColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Custom filled progress bar with centered/overlaid fraction text
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BarTrackDark),
            contentAlignment = Alignment.Center
        ) {
            // Filled fraction portion
            Box(
                modifier = Modifier
                    .fillMaxWidth(fractionProgress.coerceIn(0.05f, 1f))
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(8.dp))
                    .background(barColor)
            )

            // Value text
            Text(
                text = fractionText,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            )
        }
    }
}

/**
 * Game Library Card matching reference image Screenshot 2:
 * Rounded card with game icon, bold title, "Ready to play" in green,
 * and bottom row with [ ▶ Launch ] button and [ ☰ ] 3 lines button.
 */
@Composable
fun GameLibraryCard(
    game: InstalledApp,
    onLaunch: () -> Unit,
    onOpenMenu: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(180.dp)
            .height(260.dp)
            .border(1.dp, CardSurfaceBorder, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // App Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF1E283D)),
                contentAlignment = Alignment.Center
            ) {
                AppIconImage(
                    packageName = game.packageName,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title & Status
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = game.appName.uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        fontSize = 14.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Ready to play",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = HealthyGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Buttons: [ ▶ Launch ] and [ ☰ ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Launch Button
                Button(
                    onClick = onLaunch,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF5B6BFA),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("launch_game_${game.packageName}")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Launch",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Launch",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                // 3-Lines Menu Button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E2638))
                        .clickable { onOpenMenu() }
                        .testTag("menu_game_${game.packageName}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Options",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Dashed Add Game Card matching reference image Screenshot 1 & 2
 */
@Composable
fun DashedAddGameCard(
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(180.dp)
            .height(260.dp)
            .clip(RoundedCornerShape(20.dp))
            .dashedBorder(
                width = 1.5.dp,
                color = Color(0xFF2E3A52),
                cornerRadius = 20.dp,
                dashLength = 12.dp,
                gapLength = 8.dp
            )
            .clickable { onClick() }
            .testTag("dashed_add_game_card"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Color(0xFF8893A7),
                modifier = Modifier.size(42.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Add game",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8893A7),
                    fontSize = 15.sp
                )
            )
        }
    }
}

/**
 * Draw a dashed border on a Composable
 */
fun Modifier.dashedBorder(
    width: Dp,
    color: Color,
    cornerRadius: Dp,
    dashLength: Dp = 10.dp,
    gapLength: Dp = 8.dp
) = drawBehind {
    val stroke = Stroke(
        width = width.toPx(),
        pathEffect = PathEffect.dashPathEffect(
            floatArrayOf(dashLength.toPx(), gapLength.toPx()),
            0f
        )
    )
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = stroke
    )
}

/**
 * Loads and displays an app's icon from PackageManager safely
 */
@Composable
fun AppIconImage(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        try {
            val pm = context.packageManager
            val drawable = pm.getApplicationIcon(packageName)
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap
            } else {
                val bmp = Bitmap.createBitmap(
                    if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 144,
                    if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 144,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }
        } catch (_: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(Color(0xFF1E283D)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SportsEsports,
                contentDescription = null,
                tint = BarProgressBlue,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

/**
 * 3-Lines Options Menu Bottom Sheet
 * Allows user to:
 * - Toggle between Normal Mode and Beast Mode
 * - Toggle Screen Overlay HUD
 * - Launch with Beast Mode
 * - Remove game from library
 */
@Composable
fun GameOptionsMenuContent(
    game: InstalledApp,
    currentBoostMode: BoostMode,
    isOverlayEnabled: Boolean,
    onToggleMode: (BoostMode) -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
    onLaunchBeast: () -> Unit,
    onRemoveGame: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .padding(bottom = 32.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E283D)),
                    contentAlignment = Alignment.Center
                ) {
                    AppIconImage(packageName = game.packageName, modifier = Modifier.fillMaxSize())
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = game.appName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = game.packageName,
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMutedGray),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = onDismiss) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 1. Boost Mode Selection (Normal vs Beast Mode)
        Text(
            text = "BOOST PROFILE",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = TextMutedGray,
                letterSpacing = 1.sp
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0D1322))
                .border(1.dp, CardSurfaceBorder, RoundedCornerShape(14.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val isNormal = currentBoostMode == BoostMode.GAMING
            // Normal Mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isNormal) BarProgressBlue else Color.Transparent)
                    .clickable { onToggleMode(BoostMode.GAMING) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Normal Boost (3h)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (isNormal) Color.White else TextMutedGray
                )
            }

            // Beast Mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (!isNormal) Color(0xFFEF4444) else Color.Transparent)
                    .clickable { onToggleMode(BoostMode.BEAST) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Beast Mode (90m)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (!isNormal) Color.White else TextMutedGray
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 2. Screen Overlay HUD Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF192135))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    tint = if (isOverlayEnabled) NeonGreen else CyberCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Screen Overlay HUD",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                    )
                    Text(
                        text = if (isOverlayEnabled) "Floating FPS, Temp & Battery HUD active" else "HUD hidden during gameplay",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMutedGray)
                    )
                }
            }

            Switch(
                checked = isOverlayEnabled,
                onCheckedChange = { onToggleOverlay(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = NeonGreen
                )
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 3. Launch with Beast Mode Button
        Button(
            onClick = onLaunchBeast,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFDC2626),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(imageVector = Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Boost & Launch in Beast Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 4. App Info shortcut
        OutlinedButton(
            onClick = onOpenAppInfo,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = TextMutedGray, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("App Info & Battery Unrestricted", color = Color.White, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 5. Remove from Library Button
        OutlinedButton(
            onClick = onRemoveGame,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFFF87171)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Remove Game from Library", color = Color(0xFFF87171), fontSize = 13.sp)
        }
    }
}

/**
 * Mode Selector Dialog: Normal vs Beast
 */
@Composable
fun ModeSelectorDialog(
    currentMode: BoostMode,
    onSelectMode: (BoostMode) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = CardSurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, CardSurfaceBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Select Booster Mode",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Choose optimization aggressiveness for your session",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMutedGray)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Normal Gaming Mode
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            1.dp,
                            if (currentMode == BoostMode.GAMING) BarProgressBlue else CardSurfaceBorder,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelectMode(BoostMode.GAMING) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (currentMode == BoostMode.GAMING) Color(0xFF14244A) else Color(0xFF0F1524)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = BarProgressBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Normal Mode (Gaming)", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "3 hours active session. Moderate RAM cleaning, low-latency Wi-Fi lock, and Priority DND focus shield.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMutedGray, fontSize = 12.sp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Beast Mode
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            1.dp,
                            if (currentMode == BoostMode.BEAST) Color(0xFFEF4444) else CardSurfaceBorder,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelectMode(BoostMode.BEAST) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (currentMode == BoostMode.BEAST) Color(0xFF381212) else Color(0xFF0F1524)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFEF4444))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Beast Mode", fontWeight = FontWeight.Bold, color = Color(0xFFEF4444), fontSize = 15.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "1 hour 30 min intensive profile. Aggressive 3-minute RAM purger, screen HUD auto-enable, maximum display refresh rate, and full silence shield.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMutedGray, fontSize = 12.sp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = TextMutedGray)
                    }
                }
            }
        }
    }
}

/**
 * Add Game Dialog: Pulls installed apps/games from device and allows user to add to library
 */
@Composable
fun AddGameDialog(
    installedApps: List<InstalledApp>,
    libraryPackages: Set<String>,
    onAddGame: (InstalledApp) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val availableApps = remember(installedApps, libraryPackages, searchQuery) {
        val nonAdded = installedApps.filter { !libraryPackages.contains(it.packageName) }
        val filtered = if (searchQuery.isBlank()) nonAdded else {
            nonAdded.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
        filtered.sortedWith(compareByDescending<InstalledApp> { it.isGame }.thenBy { it.appName.lowercase() })
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = CardSurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, CardSurfaceBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add Game to Library",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search installed games & apps...", color = TextMutedGray, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = TextMutedGray)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BarProgressBlue,
                        unfocusedBorderColor = CardSurfaceBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF0F1524),
                        unfocusedContainerColor = Color(0xFF0F1524)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // List of apps
                if (availableApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching apps found." else "All device games are already added to your library!",
                            color = TextMutedGray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableApps, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF0F1524))
                                    .border(1.dp, CardSurfaceBorder, RoundedCornerShape(12.dp))
                                    .clickable { onAddGame(app) }
                                    .padding(10.dp),
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
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF1E283D)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AppIconImage(packageName = app.packageName, modifier = Modifier.fillMaxSize())
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = app.appName,
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (app.isGame) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(HealthyGreen.copy(alpha = 0.2f))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "GAME",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = HealthyGreen,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 9.sp
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = app.packageName,
                                            style = MaterialTheme.typography.labelSmall.copy(color = TextMutedGray),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Button(
                                    onClick = { onAddGame(app) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = BarProgressBlue,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
