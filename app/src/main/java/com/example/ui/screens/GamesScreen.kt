package com.example.ui.screens

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Process
import android.provider.Settings
import android.util.DisplayMetrics
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.BoostMode
import com.example.model.InstalledApp
import com.example.ui.theme.HubBlue
import com.example.ui.theme.LaunchGreen
import com.example.ui.theme.RecorderCoral
import com.example.ui.theme.ThermalCool
import com.example.ui.theme.ThermalWarm
import com.example.util.ConsoleHapticManager
import com.example.util.GamePlayStats
import com.example.util.SystemSettingsHelper
import com.example.viewmodel.GameBoosterViewModel
import java.util.Locale

private fun hasUsageAccessPermission(context: Context): Boolean {
    return try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
        false
    }
}

@Composable
fun GamesScreen(
    viewModel: GameBoosterViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val autoBigScreenLandscape by viewModel.autoBigScreenLandscape.collectAsState()
    val isBigScreenManual by viewModel.isBigScreenModeManual.collectAsState()
    val isBigScreenMode = isBigScreenManual || (isLandscape && autoBigScreenLandscape)

    val thermalInfo by viewModel.thermalInfo.collectAsState()
    val hardwareSpecs by viewModel.hardwareSpecs.collectAsState()
    val selectedBoostMode by viewModel.selectedBoostMode.collectAsState()
    val libraryGames by viewModel.libraryGames.collectAsState()
    val recentlyPlayedGames by viewModel.recentlyPlayedGames.collectAsState()
    val mostPlayedGames by viewModel.mostPlayedGames.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val sessionState by viewModel.sessionUiState.collectAsState()
    val overlayConfig by viewModel.overlayConfig.collectAsState()
    val boostFeedback by viewModel.boostFeedback.collectAsState()
    val gamepads by viewModel.gamepads.collectAsState()
    val reducedAnimations by viewModel.reducedAnimations.collectAsState()
    val showControllerLegendBar by viewModel.showControllerLegendBar.collectAsState()
    val activeControllerGlyphs by viewModel.activeControllerGlyphs.collectAsState()
    val isControllerConnected = gamepads.isNotEmpty()
    val firstGameFocusRequester = remember { FocusRequester() }
    val dailyStreakState by viewModel.dailyStreakState.collectAsState()
    val streakFeedback by viewModel.streakFeedbackMessage.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showAddGameDialog by remember { mutableStateOf(false) }
    var showStreakHubModal by remember { mutableStateOf(false) }
    var showStreakLostModal by remember { mutableStateOf(false) }
    var gameForLaunchPopup by remember { mutableStateOf<InstalledApp?>(null) }
    var usageGranted by remember { mutableStateOf(hasUsageAccessPermission(context)) }
    val isDeviceOnline by com.example.util.rememberIsDeviceOnline()

    // Automatically surface the "Streak Lost!" 12h grace period repair screen when a streak breaks
    LaunchedEffect(dailyStreakState.isStreakBrokenRepairable) {
        if (dailyStreakState.isStreakBrokenRepairable) {
            showStreakLostModal = true
        }
    }

    // Refresh play stats, Usage Access, and midnight streak rollover whenever user returns to the screen
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                usageGranted = hasUsageAccessPermission(context)
                viewModel.refreshPlayStats()
                viewModel.refreshDailyStreak()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val filteredGames = remember(libraryGames, searchQuery) {
        if (searchQuery.isBlank()) {
            libraryGames
        } else {
            libraryGames.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val filteredRecent = remember(recentlyPlayedGames, searchQuery) {
        if (searchQuery.isBlank()) recentlyPlayedGames
        else recentlyPlayedGames.filter {
            it.game.appName.contains(searchQuery, ignoreCase = true) ||
                    it.game.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredMostPlayed = remember(mostPlayedGames, searchQuery) {
        if (searchQuery.isBlank()) mostPlayedGames
        else mostPlayedGames.filter {
            it.game.appName.contains(searchQuery, ignoreCase = true) ||
                    it.game.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    // Track the currently hovered / selected game across shelves
    var selectedGamePackage by remember { mutableStateOf<String?>(null) }
    var selectedRecentPackage by remember { mutableStateOf<String?>(null) }
    var selectedMostPlayedPackage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(filteredGames) {
        if (filteredGames.isNotEmpty() &&
            (selectedGamePackage == null || filteredGames.none { it.packageName == selectedGamePackage })
        ) {
            selectedGamePackage = filteredGames.first().packageName
        }
    }

    LaunchedEffect(selectedGamePackage) {
        viewModel.setFocusedGamePackage(selectedGamePackage)
    }

    // Ensure D-Pad and joystick start directly on the first game icon tile rather than the top bar or search bar
    LaunchedEffect(filteredGames.firstOrNull()?.packageName, isControllerConnected) {
        if (filteredGames.isNotEmpty()) {
            kotlinx.coroutines.delay(60)
            try {
                firstGameFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Active spotlight game for Big Screen Console Mode (always resolves a valid spotlight card in Console Mode)
    val spotlightStats: GamePlayStats? = remember(
        selectedGamePackage,
        selectedRecentPackage,
        selectedMostPlayedPackage,
        recentlyPlayedGames,
        mostPlayedGames,
        libraryGames,
        installedApps,
        isBigScreenMode
    ) {
        val pkg = selectedGamePackage ?: selectedRecentPackage ?: selectedMostPlayedPackage
        val allStats = (recentlyPlayedGames + mostPlayedGames).associateBy { it.game.packageName }
        val resolved = if (pkg != null) {
            allStats[pkg] ?: libraryGames.firstOrNull { it.packageName == pkg }?.let {
                GamePlayStats(it, 0L, 0L, 0)
            }
        } else {
            recentlyPlayedGames.firstOrNull()
                ?: libraryGames.firstOrNull()?.let { GamePlayStats(it, 0L, 0L, 0) }
                ?: installedApps.firstOrNull { it.isGame }?.let { GamePlayStats(it, 0L, 0L, 0) }
        }
        resolved ?: if (isBigScreenMode) {
            GamePlayStats(
                game = InstalledApp(
                    packageName = "console.deck.ready",
                    appName = "GMAX Console Mode Ready",
                    isGame = true
                ),
                lastPlayedEpochMs = 0L,
                totalPlayTimeMs = 0L,
                launchCount = 0
            )
        } else null
    }

    val tileSizeDp = if (isBigScreenMode) 182.dp else 156.dp

    // Calculate Free RAM & Storage percentages
    val totalRamGb = hardwareSpecs.totalRamMb / 1024.0
    val freeRamGb = hardwareSpecs.availRamMb / 1024.0
    val freeRamPercent = if (totalRamGb > 0) ((freeRamGb / totalRamGb) * 100).coerceIn(0.0, 100.0) else 0.0

    val totalStorageGb = hardwareSpecs.totalStorageGb
    val freeStorageGb = hardwareSpecs.freeStorageGb
    val freeStoragePercent = if (totalStorageGb > 0) ((freeStorageGb / totalStorageGb) * 100).coerceIn(0.0, 100.0) else 0.0

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 12.dp,
                bottom = if (isBigScreenMode) 112.dp else 96.dp,
                start = if (isBigScreenMode) 28.dp else 20.dp,
                end = if (isBigScreenMode) 28.dp else 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Search Bar + Add Game + Quick Boost Row (Compact in Portrait Mode so search bar is never oversized)
            item {
                val isCompactPortraitBar = !isBigScreenMode
                val barHeight = if (isCompactPortraitBar) 42.dp else 52.dp
                val barCorner = if (isCompactPortraitBar) 12.dp else 16.dp
                val btnPadH = if (isCompactPortraitBar) 8.dp else 14.dp
                val btnFont = if (isCompactPortraitBar) 11.sp else 13.sp
                val iconSize = if (isCompactPortraitBar) 14.dp else 18.dp

                var isSearchBarEditable by remember { mutableStateOf(false) }
                LaunchedEffect(isControllerConnected) {
                    if (isControllerConnected && searchQuery.isEmpty()) {
                        isSearchBarEditable = false
                        try {
                            firstGameFocusRequester.requestFocus()
                        } catch (_: Exception) {}
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isCompactPortraitBar) 6.dp else 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 120.dp)
                            .height(barHeight)
                            .clip(RoundedCornerShape(barCorner))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                1.dp,
                                if (searchQuery.isNotEmpty()) HubBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                RoundedCornerShape(barCorner)
                            )
                            .focusProperties {
                                // Prevent controller D-Pad from getting trapped in the Search bar unless user explicitly tapped it
                                canFocus = !isControllerConnected || isSearchBarEditable
                            }
                            .clickable {
                                isSearchBarEditable = true
                            }
                            .padding(horizontal = if (isCompactPortraitBar) 10.dp else 14.dp)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown &&
                                    (keyEvent.key == Key.DirectionDown ||
                                            keyEvent.key == Key.DirectionUp ||
                                            keyEvent.key == Key.DirectionRight ||
                                            keyEvent.key == Key.ButtonB ||
                                            keyEvent.key == Key.Back ||
                                            keyEvent.key == Key.Escape)
                                ) {
                                    isSearchBarEditable = false
                                    try {
                                        firstGameFocusRequester.requestFocus()
                                    } catch (_: Exception) {
                                        focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
                            .testTag("launcher_search_bar"),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search Games",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(if (isCompactPortraitBar) 16.dp else 19.dp)
                            )
                            Spacer(modifier = Modifier.width(if (isCompactPortraitBar) 6.dp else 10.dp))
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = if (isCompactPortraitBar) {
                                            com.example.util.tr("Search games...", "Buscar juegos...")
                                        } else {
                                            com.example.util.tr("Search games in console launcher...", "Buscar juegos en el lanzador de consola...")
                                        },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = if (isCompactPortraitBar) 12.sp else 13.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (!isControllerConnected || isSearchBarEditable || searchQuery.isNotEmpty()) {
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = if (isCompactPortraitBar) 12.5.sp else 14.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        cursorBrush = androidx.compose.ui.graphics.SolidColor(HubBlue),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusProperties {
                                                canFocus = !isControllerConnected || isSearchBarEditable
                                            }
                                    )
                                }
                            }
                            if (searchQuery.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            searchQuery = ""
                                            isSearchBarEditable = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Quick Boost Button (Normal Boost works 100% offline; Beast Boost requires internet)
                    var boostBtnFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = {
                            ConsoleHapticManager.playLaunchPulse(context)
                            val target = filteredGames.firstOrNull { it.packageName == selectedGamePackage }
                            val effectiveMode = if (selectedBoostMode == BoostMode.BEAST && !isDeviceOnline) {
                                android.widget.Toast.makeText(
                                    context,
                                    com.example.util.AppStrings.trText(
                                        "Beast Boost requires internet. Running offline Normal Boost.",
                                        "Modo Bestia requiere internet. Ejecutando Impulso Normal sin conexión."
                                    ),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                BoostMode.GAMING
                            } else {
                                selectedBoostMode
                            }
                            viewModel.quickBoost(targetApp = target, mode = effectiveMode)
                        },
                        shape = RoundedCornerShape(barCorner),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HubBlue,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = btnPadH, vertical = 0.dp),
                        modifier = Modifier
                            .height(barHeight)
                            .onFocusChanged {
                                if (it.isFocused && !boostBtnFocused) {
                                    ConsoleHapticManager.playFocusTick(context)
                                }
                                boostBtnFocused = it.isFocused
                            }
                            .border(
                                width = if (boostBtnFocused) 2.5.dp else 0.dp,
                                color = if (boostBtnFocused) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(barCorner)
                            )
                            .testTag("launcher_quick_boost_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Quick Boost",
                            modifier = Modifier.size(iconSize)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = com.example.util.tr("Boost", "Impulsar"),
                            fontWeight = FontWeight.Bold,
                            fontSize = btnFont
                        )
                        if (isControllerConnected) {
                            Spacer(modifier = Modifier.width(4.dp))
                            InlineControllerGlyphBadge(
                                glyph = activeControllerGlyphs.westGlyph,
                                badgeColor = Color.White,
                                textColor = HubBlue
                            )
                        }
                    }

                    // Add Game Button
                    var addBtnFocused by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = {
                            ConsoleHapticManager.playSelectClick(context)
                            showAddGameDialog = true
                        },
                        shape = RoundedCornerShape(barCorner),
                        contentPadding = PaddingValues(horizontal = btnPadH, vertical = 0.dp),
                        border = BorderStroke(
                            width = if (addBtnFocused) 2.5.dp else 1.dp,
                            color = if (addBtnFocused) Color.White else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .height(barHeight)
                            .onFocusChanged {
                                if (it.isFocused && !addBtnFocused) {
                                    ConsoleHapticManager.playFocusTick(context)
                                }
                                addBtnFocused = it.isFocused
                            }
                            .testTag("launcher_add_game_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Game",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(iconSize)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = com.example.util.tr("Add", "Añadir"),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = btnFont
                        )
                    }

                    // Console Mode Toggle Button (Activates / Exits Big Screen Console Mode immediately)
                    var consoleBtnFocused by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = {
                            ConsoleHapticManager.playLaunchPulse(context)
                            viewModel.setBigScreenModeManual(!isBigScreenMode)
                        },
                        shape = RoundedCornerShape(barCorner),
                        contentPadding = PaddingValues(horizontal = btnPadH, vertical = 0.dp),
                        border = BorderStroke(
                            width = if (consoleBtnFocused || isBigScreenMode) 2.dp else 1.dp,
                            color = when {
                                consoleBtnFocused -> Color.White
                                isBigScreenMode -> LaunchGreen
                                else -> HubBlue.copy(alpha = 0.65f)
                            }
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isBigScreenMode) LaunchGreen.copy(alpha = 0.16f) else HubBlue.copy(alpha = 0.10f)
                        ),
                        modifier = Modifier
                            .height(barHeight)
                            .onFocusChanged {
                                if (it.isFocused && !consoleBtnFocused) {
                                    ConsoleHapticManager.playFocusTick(context)
                                }
                                consoleBtnFocused = it.isFocused
                            }
                            .testTag("launcher_console_mode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = "Console Mode",
                            tint = if (isBigScreenMode) LaunchGreen else HubBlue,
                            modifier = Modifier.size(iconSize)
                        )
                        if (!isCompactPortraitBar) {
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (isBigScreenMode) {
                                    com.example.util.tr("Exit Console", "Salir Consola")
                                } else {
                                    com.example.util.tr("Console", "Consola")
                                },
                                color = if (isBigScreenMode) LaunchGreen else HubBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = btnFont
                            )
                        }
                    }

                    // Daily Streak & G-Tokens Pill in Top Launcher Bar
                    com.example.ui.components.DailyStreakTopBarPill(
                        streakState = dailyStreakState,
                        onClick = {
                            ConsoleHapticManager.playSelectClick(context)
                            if (dailyStreakState.isStreakBrokenRepairable) {
                                showStreakLostModal = true
                            } else {
                                showStreakHubModal = true
                            }
                        }
                    )
                }
            }

            // 2. Big Screen Console Spotlight Hero Banner (shown in Landscape / Big Screen Mode)
            if (isBigScreenMode && spotlightStats != null) {
                item {
                    BigScreenSpotlightCard(
                        stats = spotlightStats,
                        freeRamText = String.format(
                            Locale.US,
                            com.example.util.tr("%.1f GB Free RAM", "%.1f GB RAM Libre"),
                            freeRamGb
                        ),
                        tempText = String.format(Locale.US, "%.1f°C", thermalInfo.batteryTempC),
                        showControllerGlyphs = isControllerConnected,
                        controllerGlyphs = activeControllerGlyphs,
                        onQuickPlayNormal = {
                            ConsoleHapticManager.playLaunchPulse(context)
                            viewModel.setBoostMode(BoostMode.GAMING)
                            viewModel.quickBoost(targetApp = spotlightStats.game, mode = BoostMode.GAMING)
                        },
                        onQuickPlayBeast = {
                            if (!isDeviceOnline) {
                                android.widget.Toast.makeText(
                                    context,
                                    com.example.util.AppStrings.trText(
                                        "Beast Boost requires an internet connection. Normal Boost is available offline.",
                                        "El Modo Bestia requiere conexión a internet. El Impulso Normal está disponible sin conexión."
                                    ),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                ConsoleHapticManager.playLaunchPulse(context)
                                viewModel.setBoostMode(BoostMode.BEAST)
                                viewModel.quickBoost(targetApp = spotlightStats.game, mode = BoostMode.BEAST)
                            }
                        },
                        onConfigurePopup = {
                            ConsoleHapticManager.playSelectClick(context)
                            gameForLaunchPopup = spotlightStats.game
                        }
                    )
                }
            }

            // Quick feedback alert if active
            if (boostFeedback != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = HubBlue.copy(alpha = 0.12f)
                        ),
                        border = BorderStroke(1.dp, HubBlue.copy(alpha = 0.45f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = boostFeedback ?: "",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearFeedback() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss feedback",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Active Boost Session Banner
            if (sessionState.isActive) {
                item {
                    val isBeast = sessionState.mode == BoostMode.BEAST
                    val accent = if (isBeast) RecorderCoral else LaunchGreen
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = accent.copy(alpha = 0.12f)
                        ),
                        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.6f))
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
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(accent.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "${sessionState.mode?.title ?: com.example.util.tr("BOOST", "IMPULSO")} ${com.example.util.tr("ACTIVE", "ACTIVO")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accent,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.8.sp
                                    )
                                    Text(
                                        text = "${com.example.util.tr("Remaining", "Restante")}: ${sessionState.formattedCountdown}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    ConsoleHapticManager.playSelectClick(context)
                                    viewModel.stopBoosterSession()
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, accent)
                            ) {
                                Text(
                                    text = com.example.util.tr("Stop", "Detener"),
                                    color = accent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // 3. REAL "RECENTLY PLAYED" ROW
            if (filteredRecent.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = HubBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = com.example.util.tr("RECENTLY PLAYED", "JUGADOS RECIENTEMENTE"),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        letterSpacing = 0.8.sp,
                                        fontSize = 15.sp
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(HubBlue.copy(alpha = 0.15f))
                                        .padding(horizontal = 9.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${filteredRecent.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = HubBlue
                                    )
                                }
                            }

                            if (!usageGranted) {
                                TextButton(
                                    onClick = {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                        } catch (_: Exception) {}
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = com.example.util.tr("Sync Device History", "Sincronizar Historial"),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = HubBlue
                                    )
                                }
                            } else {
                                Text(
                                    text = com.example.util.tr("Live UsageStats + Hub Sessions", "Estadísticas en Vivo + Sesiones"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("recently_played_row"),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            items(filteredRecent, key = { "recent_${it.game.packageName}" }) { stat ->
                                val isSelected = selectedRecentPackage == stat.game.packageName
                                ConsoleShowcaseGameTile(
                                    game = stat.game,
                                    tileSize = tileSizeDp,
                                    isSelected = isSelected,
                                    showControllerGlyphs = isControllerConnected,
                                    reducedAnimations = reducedAnimations,
                                    topBadgeText = stat.formattedLastPlayed,
                                    topBadgeColor = HubBlue,
                                    subtitleBadgeText = if (stat.totalPlayTimeMs > 0L) stat.formattedPlayTime else null,
                                    onSelectGame = {
                                        selectedRecentPackage = stat.game.packageName
                                        selectedGamePackage = stat.game.packageName
                                    },
                                    onLaunchPressed = {
                                        selectedRecentPackage = stat.game.packageName
                                        selectedGamePackage = stat.game.packageName
                                        gameForLaunchPopup = stat.game
                                    },
                                    testTagPrefix = "recent_tile"
                                )
                            }
                        }
                    }
                }
            }

            // 4. REAL "MOST PLAYED" ROW
            if (filteredMostPlayed.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = LaunchGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = com.example.util.tr("MOST PLAYED", "MÁS JUGADOS"),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        letterSpacing = 0.8.sp,
                                        fontSize = 15.sp
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(LaunchGreen.copy(alpha = 0.15f))
                                        .padding(horizontal = 9.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${filteredMostPlayed.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = LaunchGreen
                                    )
                                }
                            }

                            Text(
                                text = com.example.util.tr("Ranked by Total Play Time", "Ordenado por Tiempo Total"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("most_played_row"),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            itemsIndexed(filteredMostPlayed, key = { _, it -> "most_${it.game.packageName}" }) { idx, stat ->
                                val isSelected = selectedMostPlayedPackage == stat.game.packageName
                                ConsoleShowcaseGameTile(
                                    game = stat.game,
                                    tileSize = tileSizeDp,
                                    isSelected = isSelected,
                                    showControllerGlyphs = isControllerConnected,
                                    reducedAnimations = reducedAnimations,
                                    topBadgeText = "#${idx + 1} • ${stat.formattedPlayTime}",
                                    topBadgeColor = LaunchGreen,
                                    subtitleBadgeText = if (stat.launchCount > 0) "${stat.launchCount} sessions" else null,
                                    onSelectGame = {
                                        selectedMostPlayedPackage = stat.game.packageName
                                        selectedGamePackage = stat.game.packageName
                                    },
                                    onLaunchPressed = {
                                        selectedMostPlayedPackage = stat.game.packageName
                                        selectedGamePackage = stat.game.packageName
                                        gameForLaunchPopup = stat.game
                                    },
                                    testTagPrefix = "most_played_tile"
                                )
                            }
                        }
                    }
                }
            }

            // 5. MY GAMES Section Header (matching Razer Launcher screenshot #5)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.example.ui.components.GMaxBrandIcon(
                            size = 20.dp,
                            cornerRadius = 5.dp,
                            showGlowBorder = false
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = com.example.util.tr("MY GAMES", "MIS JUEGOS"),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                letterSpacing = 0.8.sp,
                                fontSize = 16.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 9.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${filteredGames.size}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 6. Razer Launcher-Inspired Horizontal Showcase Row (with Big Tiles & Haptics)
            item {
                if (filteredGames.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            com.example.ui.components.GMaxBrandIcon(
                                size = 52.dp,
                                cornerRadius = 14.dp,
                                showGlowBorder = true,
                                glowColor = HubBlue
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) {
                                    com.example.util.tr("No games match \"$searchQuery\"", "Ningún juego coincide con \"$searchQuery\"")
                                } else {
                                    com.example.util.tr("Your Launcher Library is Empty", "Tu Biblioteca del Lanzador está Vacía")
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = com.example.util.tr(
                                    "Add installed games from your device to launch with Boost & HUD.",
                                    "Añade juegos instalados desde tu dispositivo para iniciar con Impulso y HUD."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    ConsoleHapticManager.playSelectClick(context)
                                    showAddGameDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = HubBlue, contentColor = Color.White),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(com.example.util.tr("Add Games", "Añadir Juegos"), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("my_games_row"),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        itemsIndexed(filteredGames, key = { _, it -> it.packageName }) { index, game ->
                            val isSelected = selectedGamePackage == game.packageName
                            ConsoleShowcaseGameTile(
                                game = game,
                                tileSize = tileSizeDp,
                                isSelected = isSelected,
                                showControllerGlyphs = isControllerConnected,
                                reducedAnimations = reducedAnimations,
                                topBadgeText = null,
                                topBadgeColor = HubBlue,
                                subtitleBadgeText = null,
                                onSelectGame = {
                                    selectedGamePackage = game.packageName
                                },
                                onLaunchPressed = {
                                    selectedGamePackage = game.packageName
                                    gameForLaunchPopup = game
                                },
                                testTagPrefix = "game_tile",
                                focusRequester = if (index == 0) firstGameFocusRequester else null
                            )
                        }

                        // Dashed Add Game Tile at the end of MY GAMES
                        item {
                            DashedAddGameTile(
                                tileSize = tileSizeDp,
                                onClick = {
                                    ConsoleHapticManager.playSelectClick(context)
                                    showAddGameDialog = true
                                }
                            )
                        }
                    }
                }
            }

            // 7. Device Status & Telemetry Strip
            item {
                Text(
                    text = com.example.util.tr("SYSTEM TELEMETRY", "TELEMETRÍA DEL SISTEMA"),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.8.sp
                    )
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // RAM Card
                    LauncherMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Speed,
                        title = com.example.util.tr("Available RAM", "RAM Disponible"),
                        value = String.format(Locale.US, "%.1f / %.1f GB", freeRamGb, totalRamGb),
                        subtitle = String.format(Locale.US, com.example.util.tr("%.0f%% Free", "%.0f%% Libre"), freeRamPercent),
                        accentColor = HubBlue
                    )

                    // Storage Card
                    LauncherMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Storage,
                        title = com.example.util.tr("Free Storage", "Espacio Libre"),
                        value = String.format(Locale.US, "%.1f GB", freeStorageGb),
                        subtitle = String.format(Locale.US, com.example.util.tr("%.0f%% Available", "%.0f%% Disponible"), freeStoragePercent),
                        accentColor = LaunchGreen
                    )

                    // Thermal Card
                    val tempC = thermalInfo.batteryTempC
                    val tempColor = when {
                        tempC >= 43f -> RecorderCoral
                        tempC >= 39f -> ThermalWarm
                        else -> ThermalCool
                    }
                    LauncherMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Thermostat,
                        title = com.example.util.tr("Core Temp", "Temp. Núcleo"),
                        value = String.format(Locale.US, "%.1f°C", tempC),
                        subtitle = thermalInfo.thermalStatusName,
                        accentColor = tempColor
                    )
                }
            }

            // 8. Native Ad Card embedded in the scrollable list with 32dp top buffer from interactive tiles
            item {
                com.example.ui.components.GMaxNativeAdCard(
                    placementTag = "launcher_bottom",
                    topBuffer = 32.dp,
                    bottomBuffer = 24.dp
                )
            }
        }

        // Bottom Console Controller Legend Bar (appears when controller connects and enabled in Settings)
        if (isControllerConnected && showControllerLegendBar) {
            ConsoleControllerLegendBar(
                isBigScreenMode = isBigScreenMode,
                glyphs = activeControllerGlyphs,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Add Game Dialog Picker
        if (showAddGameDialog) {
            AddGameDialog(
                installedApps = installedApps,
                libraryPackages = libraryGames.map { it.packageName }.toSet(),
                onAddGame = { app ->
                    ConsoleHapticManager.playSelectClick(context)
                    viewModel.addGameToLibrary(app)
                    selectedGamePackage = app.packageName
                    showAddGameDialog = false
                },
                onDismiss = { showAddGameDialog = false }
            )
        }

        // Pre-Launch Booster Popup (shown when user presses green Launch button under a selected game)
        gameForLaunchPopup?.let { targetGame ->
            PreLaunchBoosterPopup(
                game = targetGame,
                initialBoostMode = selectedBoostMode,
                overlayConfig = overlayConfig,
                showControllerGlyphs = isControllerConnected,
                onToggleOverlay = { enabled ->
                    ConsoleHapticManager.playSelectClick(context)
                    viewModel.toggleOverlayEnabled(context, enabled)
                },
                onToggleFps = {
                    ConsoleHapticManager.playSelectClick(context)
                    viewModel.toggleOverlayFps(context, !overlayConfig.showFps)
                },
                onToggleTimer = {
                    ConsoleHapticManager.playSelectClick(context)
                    viewModel.toggleOverlayTimer(context, !overlayConfig.showTimer)
                },
                onToggleRamPct = {
                    ConsoleHapticManager.playSelectClick(context)
                    viewModel.toggleOverlayRamPct(context, !overlayConfig.showRamPct)
                },
                onToggleTemp = {
                    ConsoleHapticManager.playSelectClick(context)
                    viewModel.toggleOverlayTemp(context, !overlayConfig.showTemp)
                },
                onToggleBattery = {
                    ConsoleHapticManager.playSelectClick(context)
                    viewModel.toggleOverlayBattery(context, !overlayConfig.showBattery)
                },
                onToggleClock = {
                    ConsoleHapticManager.playSelectClick(context)
                    viewModel.toggleOverlayClock(context, !overlayConfig.showClock)
                },
                onToggleAutoScale = {
                    ConsoleHapticManager.playSelectClick(context)
                    viewModel.toggleOverlayAutoScale(context, !overlayConfig.autoScaleSmallScreens)
                },
                onOpenGame = { chosenMode ->
                    val effectiveMode = if (chosenMode == BoostMode.BEAST && !isDeviceOnline) {
                        android.widget.Toast.makeText(
                            context,
                            com.example.util.AppStrings.trText(
                                "Beast Boost requires internet. Launching with offline Normal Boost.",
                                "Modo Bestia requiere internet. Iniciando con Impulso Normal sin conexión."
                            ),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        BoostMode.GAMING
                    } else {
                        chosenMode
                    }
                    viewModel.setBoostMode(effectiveMode)
                    gameForLaunchPopup = null
                    viewModel.quickBoost(targetApp = targetGame, mode = effectiveMode)
                },
                onRemoveFromLibrary = {
                    ConsoleHapticManager.playSelectClick(context)
                    viewModel.removeGameFromLibrary(targetGame.packageName)
                    gameForLaunchPopup = null
                },
                onOpenAppInfo = {
                    SystemSettingsHelper.openAppDetails(context, targetGame.packageName)
                },
                onDismiss = { gameForLaunchPopup = null }
            )
        }

        // Dedicated Daily Engagement Streak & G-Tokens Economy Modal
        if (showStreakHubModal) {
            com.example.ui.components.DailyStreakHubModalDialog(
                streakState = dailyStreakState,
                feedbackMessage = streakFeedback,
                onBuyStreakFreeze = {
                    viewModel.buyStreakFreeze()
                },
                onSelectNeonColor = { hexColor ->
                    viewModel.selectStreakHudNeonColor(hexColor)
                },
                onOpenRepairModal = {
                    showStreakHubModal = false
                    showStreakLostModal = true
                },
                onDismiss = {
                    viewModel.clearStreakFeedbackMessage()
                    showStreakHubModal = false
                }
            )
        }

        // "Streak Lost!" 12-Hour Grace Period Rewarded Ad Repair Modal
        if (showStreakLostModal && dailyStreakState.isStreakBrokenRepairable) {
            com.example.ui.components.StreakLostRepairModalDialog(
                streakState = dailyStreakState,
                onRepairViaRewardedAd = {
                    viewModel.repairBrokenStreakAfterRewardedAd()
                    showStreakLostModal = false
                    showStreakHubModal = true
                },
                onLetStreakReset = {
                    viewModel.resetBrokenStreakToZero()
                    showStreakLostModal = false
                },
                onDismiss = {
                    showStreakLostModal = false
                }
            )
        }
    }
}

/**
 * Landscape / Big Screen Mode Console Spotlight Hero Card:
 * Auto-scales based on the user's screen width/height for compact optimization.
 */
@Composable
private fun BigScreenSpotlightCard(
    stats: GamePlayStats,
    freeRamText: String,
    tempText: String,
    showControllerGlyphs: Boolean,
    controllerGlyphs: com.example.model.ControllerGlyphMap = com.example.model.ControllerLayoutResolver.playStationGlyphs,
    onQuickPlayNormal: () -> Unit,
    onQuickPlayBeast: () -> Unit,
    onConfigurePopup: () -> Unit
) {
    val config = LocalConfiguration.current
    val minDim = minOf(config.screenWidthDp, config.screenHeightDp).coerceAtLeast(320)
    val scale = (minDim / 410f).coerceIn(0.72f, 0.96f)
    val iconSizeDp = (52f * scale).dp
    val padDp = (12f * scale).dp
    val titleSp = (16f * scale).sp
    val metaSp = (10f * scale).sp
    val btnFontSp = (11.5f * scale).sp
    val btnPadH = (12f * scale).dp
    val btnPadV = (8f * scale).dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("big_screen_spotlight_card"),
        shape = RoundedCornerShape((16f * scale).dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, HubBlue.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            HubBlue.copy(alpha = 0.16f),
                            LaunchGreen.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = padDp * 1.2f, vertical = padDp)
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
                    Box(
                        modifier = Modifier
                            .size(iconSizeDp)
                            .shadow(6.dp, RoundedCornerShape((12f * scale).dp))
                            .clip(RoundedCornerShape((12f * scale).dp))
                            .border(2.dp, Color.White, RoundedCornerShape((12f * scale).dp))
                    ) {
                        AppIconImage(
                            packageName = stats.game.packageName,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width((12f * scale).dp))

                    Column(verticalArrangement = Arrangement.spacedBy((3f * scale).dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(HubBlue.copy(alpha = 0.20f))
                                    .border(1.dp, HubBlue, RoundedCornerShape(50))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Tv,
                                        contentDescription = null,
                                        tint = HubBlue,
                                        modifier = Modifier.size((10f * scale).dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "BIG SCREEN",
                                        fontSize = (8.5f * scale).sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = HubBlue
                                    )
                                }
                            }

                            Text(
                                text = "• ${stats.formattedLastPlayed} • ${stats.formattedPlayTime}",
                                fontSize = metaSp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = stats.game.appName,
                            fontSize = titleSp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = freeRamText,
                                fontSize = metaSp,
                                fontWeight = FontWeight.Bold,
                                color = LaunchGreen
                            )
                            Text(
                                text = "• Core $tempText",
                                fontSize = metaSp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width((10f * scale).dp))

                // Right side auto-scaled quick console launch actions
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((8f * scale).dp)
                ) {
                    var normalFocus by remember { mutableStateOf(false) }
                    Button(
                        onClick = onQuickPlayNormal,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LaunchGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape((10f * scale).dp),
                        contentPadding = PaddingValues(horizontal = btnPadH, vertical = btnPadV),
                        modifier = Modifier
                            .onFocusChanged { normalFocus = it.isFocused }
                            .border(
                                width = if (normalFocus) 2.dp else 0.dp,
                                color = if (normalFocus) Color.White else Color.Transparent,
                                shape = RoundedCornerShape((10f * scale).dp)
                            )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size((15f * scale).dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play", fontWeight = FontWeight.ExtraBold, fontSize = btnFontSp)
                        if (showControllerGlyphs) {
                            Spacer(modifier = Modifier.width(5.dp))
                            InlineControllerGlyphBadge(
                                glyph = controllerGlyphs.southGlyph,
                                badgeColor = Color.White,
                                textColor = LaunchGreen
                            )
                        }
                    }

                    var beastFocus by remember { mutableStateOf(false) }
                    Button(
                        onClick = onQuickPlayBeast,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RecorderCoral,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape((10f * scale).dp),
                        contentPadding = PaddingValues(horizontal = btnPadH, vertical = btnPadV),
                        modifier = Modifier
                            .onFocusChanged { beastFocus = it.isFocused }
                            .border(
                                width = if (beastFocus) 2.dp else 0.dp,
                                color = if (beastFocus) Color.White else Color.Transparent,
                                shape = RoundedCornerShape((10f * scale).dp)
                            )
                    ) {
                        Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size((14f * scale).dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Beast", fontWeight = FontWeight.ExtraBold, fontSize = btnFontSp)
                        if (showControllerGlyphs) {
                            Spacer(modifier = Modifier.width(5.dp))
                            InlineControllerGlyphBadge(
                                glyph = controllerGlyphs.westGlyph,
                                badgeColor = Color.White,
                                textColor = RecorderCoral
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onConfigurePopup,
                        shape = RoundedCornerShape((10f * scale).dp),
                        contentPadding = PaddingValues(horizontal = (9f * scale).dp, vertical = btnPadV)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "HUD Options",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size((15f * scale).dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineControllerGlyphBadge(
    glyph: String,
    badgeColor: Color,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .size(17.dp)
            .clip(CircleShape)
            .background(badgeColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = glyph,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Black,
            color = textColor
        )
    }
}

/**
 * Auto-scaled Console Controller Shortcut Legend Bar at the bottom of the Launcher.
 */
@Composable
private fun ConsoleControllerLegendBar(
    isBigScreenMode: Boolean,
    glyphs: com.example.model.ControllerGlyphMap = com.example.model.ControllerLayoutResolver.playStationGlyphs,
    modifier: Modifier = Modifier
) {
    val config = LocalConfiguration.current
    val screenW = config.screenWidthDp.coerceAtLeast(320)
    val scale = (screenW / 440f).coerceIn(0.70f, 0.92f)

    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(
            width = 1.dp,
            color = if (isBigScreenMode) HubBlue.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        shadowElevation = 4.dp,
        modifier = modifier.testTag("controller_legend_bar")
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = (12f * scale).dp,
                vertical = (5f * scale).dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((9f * scale).dp)
        ) {
            ControllerKeyHint(badge = glyphs.southGlyph, label = com.example.util.tr("Launch", "Iniciar"), badgeColor = LaunchGreen, scale = scale)
            ControllerKeyHint(badge = glyphs.westGlyph, label = com.example.util.tr("Boost", "Impulsar"), badgeColor = HubBlue, scale = scale)
            ControllerKeyHint(badge = glyphs.northGlyph, label = com.example.util.tr("Record", "Grabar"), badgeColor = Color(0xFFF59E0B), scale = scale)
            ControllerKeyHint(badge = "${glyphs.leftBumper}/${glyphs.rightBumper}", label = com.example.util.tr("Tabs", "Pestañas"), scale = scale)
        }
    }
}

@Composable
private fun ControllerKeyHint(
    badge: String,
    label: String,
    badgeColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    scale: Float = 0.85f
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape((5f * scale).dp))
                .background(badgeColor.copy(alpha = 0.16f))
                .border(1.dp, badgeColor.copy(alpha = 0.55f), RoundedCornerShape((5f * scale).dp))
                .padding(horizontal = (5f * scale).dp, vertical = (1.5f * scale).dp)
        ) {
            Text(
                text = badge,
                fontSize = (8.5f * scale).sp,
                fontWeight = FontWeight.ExtraBold,
                color = badgeColor
            )
        }
        Spacer(modifier = Modifier.width((4f * scale).dp))
        Text(
            text = label,
            fontSize = (9.5f * scale).sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Console Showcase Game Tile (used across Recently Played, Most Played, and My Games):
 * - Features spring scale elevation (1.0f -> 1.06f) when focused/selected
 * - Triggers tactile console haptic tick on focus gain and click haptic on press
 * - Displays crisp 3.5dp white outline around the square app icon tile when selected
 * - Green "Launch" button appears ONLY under the selected game
 */
@Composable
private fun ConsoleShowcaseGameTile(
    game: InstalledApp,
    tileSize: Dp,
    isSelected: Boolean,
    showControllerGlyphs: Boolean,
    reducedAnimations: Boolean,
    topBadgeText: String?,
    topBadgeColor: Color,
    subtitleBadgeText: String?,
    onSelectGame: () -> Unit,
    onLaunchPressed: () -> Unit,
    testTagPrefix: String,
    focusRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    var isHardwareFocused by remember { mutableStateOf(false) }

    val animatedScale by animateFloatAsState(
        targetValue = if (!reducedAnimations && (isSelected || isHardwareFocused)) 1.05f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "tileScale"
    )
    val effectiveScale = if (reducedAnimations) 1.0f else animatedScale

    Column(
        modifier = Modifier
            .width(tileSize)
            .scale(effectiveScale)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
            )
            .onFocusChanged { focusState ->
                val nowFocused = focusState.isFocused || focusState.hasFocus
                if (nowFocused && !isHardwareFocused) {
                    ConsoleHapticManager.playFocusTick(context)
                    onSelectGame()
                }
                isHardwareFocused = nowFocused
            }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.ButtonA)
                ) {
                    ConsoleHapticManager.playSelectClick(context)
                    if (isSelected) {
                        onLaunchPressed()
                        true
                    } else {
                        onSelectGame()
                        true
                    }
                } else {
                    false
                }
            }
            .focusable()
            .testTag("${testTagPrefix}_${game.packageName}"),
        horizontalAlignment = Alignment.Start
    ) {
        // Square Game Artwork / Icon Tile with White Outline when Selected/Hovered
        Box(
            modifier = Modifier
                .size(tileSize)
                .shadow(
                    elevation = if (reducedAnimations) 2.dp else if (isSelected) 14.dp else 3.dp,
                    shape = RoundedCornerShape(12.dp)
                )
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (isSelected) {
                        Modifier
                            .border(3.5.dp, Color.White, RoundedCornerShape(12.dp))
                            .padding(3.5.dp)
                            .border(1.dp, Color.Black.copy(alpha = 0.35f), RoundedCornerShape(9.dp))
                    } else {
                        Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            RoundedCornerShape(12.dp)
                        )
                    }
                )
                .clickable {
                    if (isSelected) {
                        ConsoleHapticManager.playSelectClick(context)
                        onLaunchPressed()
                    } else {
                        ConsoleHapticManager.playFocusTick(context)
                        onSelectGame()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AppIconImage(
                packageName = game.packageName,
                modifier = Modifier.fillMaxSize()
            )

            // Optional Top Telemetry Badge (Last Played time or Play Hours rank)
            if (!topBadgeText.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xD90F172A))
                        .border(1.dp, topBadgeColor.copy(alpha = 0.85f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = topBadgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Game Title underneath (highlighted when selected)
        Text(
            text = game.appName,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontSize = 15.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (!subtitleBadgeText.isNullOrBlank()) {
            Text(
                text = subtitleBadgeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Green Launch button ONLY visible under the selected/hovered game
        if (reducedAnimations) {
            if (isSelected) {
                TileLaunchButtonContent(
                    game = game,
                    showControllerGlyphs = showControllerGlyphs,
                    onLaunchPressed = {
                        ConsoleHapticManager.playSelectClick(context)
                        onLaunchPressed()
                    }
                )
            }
        } else {
            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                TileLaunchButtonContent(
                    game = game,
                    showControllerGlyphs = showControllerGlyphs,
                    onLaunchPressed = {
                        ConsoleHapticManager.playSelectClick(context)
                        onLaunchPressed()
                    }
                )
            }
        }
    }
}

@Composable
private fun TileLaunchButtonContent(
    game: InstalledApp,
    showControllerGlyphs: Boolean,
    onLaunchPressed: () -> Unit
) {
    Column {
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onLaunchPressed,
            colors = ButtonDefaults.buttonColors(
                containerColor = LaunchGreen,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .testTag("launch_button_${game.packageName}")
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Launch ${game.appName}",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = com.example.util.tr("Launch", "Iniciar"),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp
            )
            if (showControllerGlyphs) {
                Spacer(modifier = Modifier.width(6.dp))
                InlineControllerGlyphBadge(
                    glyph = "A",
                    badgeColor = Color.White,
                    textColor = LaunchGreen
                )
            }
        }
    }
}

/**
 * Compact Pre-Launch Booster Popup when Launch is pressed on a selected game:
 * - Select Normal Booster or Beast Booster
 * - Toggle Device Display Overlay (HUD)
 * - Final "Open Game" button
 */
@Composable
private fun PreLaunchBoosterPopup(
    game: InstalledApp,
    initialBoostMode: BoostMode,
    overlayConfig: com.example.model.OverlayHudConfig,
    showControllerGlyphs: Boolean,
    onToggleOverlay: (Boolean) -> Unit,
    onToggleFps: () -> Unit,
    onToggleTimer: () -> Unit,
    onToggleRamPct: () -> Unit,
    onToggleTemp: () -> Unit,
    onToggleBattery: () -> Unit,
    onToggleClock: () -> Unit,
    onToggleAutoScale: () -> Unit,
    onOpenGame: (BoostMode) -> Unit,
    onRemoveFromLibrary: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.coerceAtLeast(320)
    val screenHeightDp = configuration.screenHeightDp.coerceAtLeast(320)
    val isCompactHeight = screenHeightDp < 520
    val isOverlayEnabled = overlayConfig.isEnabled
    val openGameFocusRequester = remember { FocusRequester() }
    val normalBoostFocusRequester = remember { FocusRequester() }
    var chosenMode by remember(initialBoostMode) {
        mutableStateOf(
            if (initialBoostMode == BoostMode.BEAST) BoostMode.BEAST else BoostMode.GAMING
        )
    }

    // Automatically focus the Open Game button first so pressing 'A' in Console Mode launches immediately without scrolling
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(50)
        try {
            openGameFocusRequester.requestFocus()
        } catch (_: Exception) {
            try {
                normalBoostFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(if (screenWidthDp > 560) 0.86f else 0.94f)
                .widthIn(max = 560.dp)
                .heightIn(max = (screenHeightDp - 24).coerceAtLeast(280).dp)
                .testTag("pre_launch_booster_popup")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = if (isCompactHeight) 12.dp else 16.dp)
            ) {
                // Header: Game Icon + Name + Close button (always visible at top)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(2.dp, Color.White, RoundedCornerShape(12.dp))
                        ) {
                            AppIconImage(
                                packageName = game.packageName,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = game.appName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = com.example.util.tr("Configure Boost & Display Overlay", "Configurar Impulso y Superposición"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close popup",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (isCompactHeight) 8.dp else 12.dp))

                // Scrollable middle configuration section so Landscape / Console Mode never cuts off the Open Game button
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 1. Booster Mode Selector: Normal vs Beast Booster
                    Text(
                        text = com.example.util.tr("SELECT BOOSTER MODE", "SELECCIONAR MODO DE IMPULSO"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.8.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isNormal = chosenMode != BoostMode.BEAST
                    var normalFocused by remember { mutableStateOf(false) }
                    var beastFocused by remember { mutableStateOf(false) }

                    // Normal Booster Option (Default initial focus with white outline)
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(normalBoostFocusRequester)
                            .onFocusChanged {
                                if (it.isFocused && !normalFocused) {
                                    ConsoleHapticManager.playFocusTick(context)
                                                                }
                                normalFocused = it.isFocused
                            }
                            .onKeyEvent { ev ->
                                if (ev.type == KeyEventType.KeyDown &&
                                    (ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.ButtonA)
                                ) {
                                    ConsoleHapticManager.playSelectClick(context)
                                    chosenMode = BoostMode.GAMING
                                    true
                                } else false
                            }
                            .clickable {
                                ConsoleHapticManager.playSelectClick(context)
                                chosenMode = BoostMode.GAMING
                            }
                            .focusable()
                            .testTag("popup_mode_normal"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isNormal) {
                                HubBlue.copy(alpha = 0.15f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }
                        ),
                        border = BorderStroke(
                            width = if (normalFocused) 2.5.dp else if (isNormal) 2.dp else 1.dp,
                            color = when {
                                normalFocused -> Color.White
                                isNormal -> HubBlue
                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = if (isNormal) HubBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = com.example.util.tr("Normal Booster", "Impulso Normal"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = com.example.util.tr("Balanced • Offline Ready", "Equilibrado • Sin Conexión"),
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Beast Booster Option
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged {
                                if (it.isFocused && !beastFocused) {
                                    ConsoleHapticManager.playFocusTick(context)
                                }
                                beastFocused = it.isFocused
                            }
                            .clickable {
                                ConsoleHapticManager.playSelectClick(context)
                                chosenMode = BoostMode.BEAST
                            }
                            .focusable()
                            .testTag("popup_mode_beast"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (!isNormal) {
                                RecorderCoral.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }
                        ),
                        border = BorderStroke(
                            width = if (beastFocused || !isNormal) 2.dp else 1.dp,
                            color = when {
                                beastFocused -> Color.White
                                !isNormal -> RecorderCoral
                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.RocketLaunch,
                                contentDescription = null,
                                tint = if (!isNormal) RecorderCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = com.example.util.tr("Beast Booster", "Modo Bestia"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = com.example.util.tr("Max FPS • Online", "Máx FPS • En Línea"),
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Device Display Overlay (HUD) Master Toggle + Inline Editable Metric Chips & 1-Row Auto-Scaled Preview
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleOverlay(!isOverlayEnabled) },
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
                                    tint = if (isOverlayEnabled) LaunchGreen else HubBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Device Display Overlay",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isOverlayEnabled) {
                                            "Tap chips below to edit your live HUD metrics"
                                        } else {
                                            "Enable to customize on-screen gaming telemetry"
                                        },
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Switch(
                                checked = isOverlayEnabled,
                                onCheckedChange = { onToggleOverlay(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = LaunchGreen
                                ),
                                modifier = Modifier.testTag("popup_overlay_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Inline Toggleable Metric Chips (EFPS, Timer, RAM %, Temp, Battery, Clock, Auto-Scale)
                        val activeMetricsCount = listOf(
                            overlayConfig.showFps,
                            overlayConfig.showTimer,
                            overlayConfig.showRamPct,
                            overlayConfig.showTemp,
                            overlayConfig.showBattery,
                            overlayConfig.showClock
                        ).count { it }.coerceAtLeast(1)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PopupOverlayMetricChip(
                                label = "EFPS",
                                isSelected = overlayConfig.showFps,
                                accentColor = Color(0xFF00E5FF),
                                testTag = "booster_chip_efps",
                                onClick = onToggleFps
                            )
                            PopupOverlayMetricChip(
                                label = "Timer",
                                isSelected = overlayConfig.showTimer,
                                accentColor = LaunchGreen,
                                testTag = "booster_chip_timer",
                                onClick = onToggleTimer
                            )
                            PopupOverlayMetricChip(
                                label = "RAM %",
                                isSelected = overlayConfig.showRamPct,
                                accentColor = Color(0xFFA855F7),
                                testTag = "booster_chip_ram",
                                onClick = onToggleRamPct
                            )
                            PopupOverlayMetricChip(
                                label = "Temp",
                                isSelected = overlayConfig.showTemp,
                                accentColor = Color(0xFFF59E0B),
                                testTag = "booster_chip_temp",
                                onClick = onToggleTemp
                            )
                            PopupOverlayMetricChip(
                                label = "Battery",
                                isSelected = overlayConfig.showBattery,
                                accentColor = Color(0xFF10B981),
                                testTag = "booster_chip_battery",
                                onClick = onToggleBattery
                            )
                            PopupOverlayMetricChip(
                                label = "Clock",
                                isSelected = overlayConfig.showClock,
                                accentColor = HubBlue,
                                testTag = "booster_chip_clock",
                                onClick = onToggleClock
                            )
                            PopupOverlayMetricChip(
                                label = "Auto-Scale",
                                isSelected = overlayConfig.autoScaleSmallScreens,
                                accentColor = LaunchGreen,
                                testTag = "booster_chip_autoscale",
                                onClick = onToggleAutoScale
                            )
                        }

                        // Live 1-Row Auto-Scaled HUD Preview Bar (Guaranteed never to wrap into 2 rows)
                        if (isOverlayEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val widthScale = if (overlayConfig.autoScaleSmallScreens) {
                                (screenWidthDp / 412f).coerceIn(0.76f, 1.0f)
                            } else 0.92f
                            val densityFactor = when {
                                activeMetricsCount >= 6 -> if (overlayConfig.autoScaleSmallScreens) 0.78f else 0.85f
                                activeMetricsCount == 5 -> 0.84f
                                activeMetricsCount == 4 -> 0.92f
                                else -> 1.0f
                            }
                            val previewFontSp = (10.5f * widthScale * densityFactor).coerceIn(7.8f, 11.0f).sp
                            val isCompactPreview = overlayConfig.autoScaleSmallScreens && activeMetricsCount >= 5

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF0D111A))
                                    .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.55f), RoundedCornerShape(50))
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                    .testTag("booster_hud_single_row_preview"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(if (isCompactPreview) 5.dp else 7.dp)
                            ) {
                                if (overlayConfig.showFps) {
                                    Text(
                                        text = if (isCompactPreview) "60 FPS" else "60 EFPS",
                                        color = Color(0xFF00E5FF),
                                        fontSize = previewFontSp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                if (overlayConfig.showTimer) {
                                    Text(
                                        text = if (chosenMode == BoostMode.BEAST) "🔥1:30:00" else "⏳3:00:00",
                                        color = if (chosenMode == BoostMode.BEAST) Color(0xFFFF5722) else LaunchGreen,
                                        fontSize = previewFontSp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                if (overlayConfig.showRamPct) {
                                    Text(
                                        text = if (isCompactPreview) "R:48%" else "RAM 48%",
                                        color = Color(0xFFA855F7),
                                        fontSize = previewFontSp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                if (overlayConfig.showTemp) {
                                    Text(
                                        text = "31.5°C",
                                        color = Color(0xFFF59E0B),
                                        fontSize = previewFontSp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                if (overlayConfig.showBattery) {
                                    Text(
                                        text = "🔋92%",
                                        color = Color(0xFF10B981),
                                        fontSize = previewFontSp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                if (overlayConfig.showClock) {
                                    Text(
                                        text = "2:45 PM",
                                        color = Color(0xFFE2E8F0),
                                        fontSize = previewFontSp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
                } // End scrollable middle section

                Spacer(modifier = Modifier.height(if (isCompactHeight) 8.dp else 12.dp))

                // 3. Pinned Final Button: Open Game (Never cut off in Console / Landscape Mode, focused first for instant launch)
                var openGameFocused by remember { mutableStateOf(true) }
                Button(
                    onClick = { onOpenGame(chosenMode) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LaunchGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isCompactHeight) 46.dp else 52.dp)
                        .focusRequester(openGameFocusRequester)
                        .onFocusChanged {
                            if (it.isFocused && !openGameFocused) {
                                ConsoleHapticManager.playFocusTick(context)
                            }
                            openGameFocused = it.isFocused
                        }
                        .onKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown &&
                                (ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.ButtonA)
                            ) {
                                ConsoleHapticManager.playSelectClick(context)
                                onOpenGame(chosenMode)
                                true
                            } else false
                        }
                        .border(
                            width = if (openGameFocused) 3.dp else 0.dp,
                            color = if (openGameFocused) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .focusable()
                        .testTag("popup_open_game_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = com.example.util.tr("Open Game", "Abrir Juego"),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    if (showControllerGlyphs) {
                        Spacer(modifier = Modifier.width(8.dp))
                        InlineControllerGlyphBadge(
                            glyph = "A",
                            badgeColor = Color.White,
                            textColor = LaunchGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (isCompactHeight) 4.dp else 8.dp))

                // Subtle bottom utility row: App Info & Remove from Library
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onOpenAppInfo) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = com.example.util.tr("App Info", "Info de App"),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TextButton(onClick = onRemoveFromLibrary) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = RecorderCoral
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = com.example.util.tr("Remove from Launcher", "Quitar del Lanzador"),
                            fontSize = 12.sp,
                            color = RecorderCoral
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LauncherMetricCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DashedAddGameTile(
    tileSize: Dp,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val outlineColor = MaterialTheme.colorScheme.outline
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.width(tileSize),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(tileSize)
                .onFocusChanged {
                    if (it.isFocused && !isFocused) {
                        ConsoleHapticManager.playFocusTick(context)
                    }
                    isFocused = it.isFocused
                }
                .clip(RoundedCornerShape(12.dp))
                .dashedBorder(
                    width = if (isFocused) 2.5.dp else 1.5.dp,
                    color = if (isFocused) Color.White else outlineColor,
                    cornerRadius = 12.dp,
                    dashLength = 12.dp,
                    gapLength = 8.dp
                )
                .clickable { onClick() }
                .focusable()
                .testTag("dashed_add_game_card"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add game",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = com.example.util.tr("Add Game", "Añadir Juego"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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

@Composable
private fun PopupOverlayMetricChip(
    label: String,
    isSelected: Boolean,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (isSelected) accentColor.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = 1.dp,
                color = if (isSelected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(50)
            )
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 5.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isSelected) "✓ $label" else label,
            fontSize = 10.5.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/**
 * Loads and displays an app's icon in crisp high resolution (DENSITY_XXXHIGH / 256x256 ARGB_8888)
 */
@Composable
fun AppIconImage(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        if (packageName.isBlank()) {
            null
        } else {
            try {
                val pm = context.packageManager
                val drawable = try {
                    val launchIntent = pm.getLaunchIntentForPackage(packageName)
                    if (launchIntent != null) {
                        pm.getActivityIcon(launchIntent)
                    } else null
                } catch (_: Exception) {
                    null
                } ?: try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    val res = pm.getResourcesForApplication(appInfo)
                    if (appInfo.icon != 0) {
                        res.getDrawableForDensity(appInfo.icon, DisplayMetrics.DENSITY_XXXHIGH, null)
                    } else null
                } catch (_: Exception) {
                    null
                } ?: try {
                    pm.getApplicationIcon(packageName)
                } catch (_: Exception) {
                    null
                }

                if (drawable != null) {
                    val targetSize = 256
                    val bmp = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, targetSize, targetSize)
                    drawable.draw(canvas)
                    bmp
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            com.example.ui.components.GMaxBrandIcon(
                size = 44.dp,
                cornerRadius = 10.dp,
                showGlowBorder = false
            )
        }
    }
}

/**
 * Add Game Dialog: Pulls installed apps/games from device and allows user to add to Launcher
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
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = com.example.util.tr("Add Game to Launcher", "Añadir Juego al Lanzador"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            com.example.util.tr("Search installed games & apps...", "Buscar juegos y apps instaladas..."),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HubBlue,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (availableApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) {
                                "No matching apps found."
                            } else {
                                "All device games are already in your Launcher!"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
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
                                    ) {
                                        AppIconImage(
                                            packageName = app.packageName,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = app.appName,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (app.isGame) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(LaunchGreen.copy(alpha = 0.18f))
                                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "GAME",
                                                        color = LaunchGreen,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 9.sp
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = app.packageName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Button(
                                    onClick = { onAddGame(app) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = HubBlue,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(com.example.util.tr("Add", "Añadir"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
