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
    val isBigScreenMode = (isLandscape && autoBigScreenLandscape) || isBigScreenManual

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
    val isControllerConnected = gamepads.isNotEmpty()
    val firstGameFocusRequester = remember { FocusRequester() }

    var searchQuery by remember { mutableStateOf("") }
    var showAddGameDialog by remember { mutableStateOf(false) }
    var gameForLaunchPopup by remember { mutableStateOf<InstalledApp?>(null) }
    var usageGranted by remember { mutableStateOf(hasUsageAccessPermission(context)) }

    // Refresh play stats and Usage Access status whenever user returns to the screen
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                usageGranted = hasUsageAccessPermission(context)
                viewModel.refreshPlayStats()
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

    // Ensure D-Pad and joystick start directly on the first game icon tile rather than the top bar or search bar
    LaunchedEffect(filteredGames.firstOrNull()?.packageName, isControllerConnected) {
        if (filteredGames.isNotEmpty()) {
            kotlinx.coroutines.delay(60)
            try {
                firstGameFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Active spotlight game for Big Screen Console Mode
    val spotlightStats: GamePlayStats? = remember(
        selectedGamePackage,
        selectedRecentPackage,
        selectedMostPlayedPackage,
        recentlyPlayedGames,
        mostPlayedGames,
        libraryGames
    ) {
        val pkg = selectedGamePackage ?: selectedRecentPackage ?: selectedMostPlayedPackage
        val allStats = (recentlyPlayedGames + mostPlayedGames).associateBy { it.game.packageName }
        if (pkg != null) {
            allStats[pkg] ?: libraryGames.firstOrNull { it.packageName == pkg }?.let {
                GamePlayStats(it, 0L, 0L, 0)
            }
        } else {
            recentlyPlayedGames.firstOrNull()
                ?: libraryGames.firstOrNull()?.let { GamePlayStats(it, 0L, 0L, 0) }
        }
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
            // 1. Search Bar + Add Game + Quick Boost Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Search games in console launcher...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search Games",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = HubBlue,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown &&
                                    (keyEvent.key == Key.DirectionDown ||
                                            keyEvent.key == Key.DirectionUp ||
                                            keyEvent.key == Key.ButtonB ||
                                            keyEvent.key == Key.Escape)
                                ) {
                                    try {
                                        firstGameFocusRequester.requestFocus()
                                    } catch (_: Exception) {
                                        focusManager.clearFocus()
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
                            .testTag("launcher_search_bar")
                    )

                    // Quick Boost Button
                    var boostBtnFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = {
                            ConsoleHapticManager.playLaunchPulse(context)
                            val target = filteredGames.firstOrNull { it.packageName == selectedGamePackage }
                            viewModel.quickBoost(targetApp = target, mode = selectedBoostMode)
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HubBlue,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                        modifier = Modifier
                            .onFocusChanged {
                                if (it.isFocused && !boostBtnFocused) {
                                    ConsoleHapticManager.playFocusTick(context)
                                }
                                boostBtnFocused = it.isFocused
                            }
                            .border(
                                width = if (boostBtnFocused) 2.5.dp else 0.dp,
                                color = if (boostBtnFocused) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .testTag("launcher_quick_boost_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Quick Boost",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Boost",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        if (isControllerConnected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            InlineControllerGlyphBadge(
                                glyph = "X",
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
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                        border = BorderStroke(
                            width = if (addBtnFocused) 2.5.dp else 1.dp,
                            color = if (addBtnFocused) Color.White else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
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
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Add",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // 2. Big Screen Console Spotlight Hero Banner (shown in Landscape / Big Screen Mode)
            if (isBigScreenMode && spotlightStats != null) {
                item {
                    BigScreenSpotlightCard(
                        stats = spotlightStats,
                        freeRamText = String.format(Locale.US, "%.1f GB Free RAM", freeRamGb),
                        tempText = String.format(Locale.US, "%.1f°C", thermalInfo.batteryTempC),
                        showControllerGlyphs = isControllerConnected,
                        onQuickPlayNormal = {
                            ConsoleHapticManager.playLaunchPulse(context)
                            viewModel.setBoostMode(BoostMode.GAMING)
                            viewModel.quickBoost(targetApp = spotlightStats.game, mode = BoostMode.GAMING)
                        },
                        onQuickPlayBeast = {
                            ConsoleHapticManager.playLaunchPulse(context)
                            viewModel.setBoostMode(BoostMode.BEAST)
                            viewModel.quickBoost(targetApp = spotlightStats.game, mode = BoostMode.BEAST)
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
                                        text = "${sessionState.mode?.title ?: "BOOST"} ACTIVE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accent,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.8.sp
                                    )
                                    Text(
                                        text = "Remaining: ${sessionState.formattedCountdown}",
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
                                    text = "Stop",
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
                                    text = "RECENTLY PLAYED",
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
                                        text = "Sync Device History",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = HubBlue
                                    )
                                }
                            } else {
                                Text(
                                    text = "Live UsageStats + Hub Sessions",
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
                                    text = "MOST PLAYED",
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
                                text = "Ranked by Total Play Time",
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
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MY GAMES",
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

                    Text(
                        text = if (isControllerConnected) {
                            "D-Pad to focus • Press A to Launch"
                        } else {
                            "Tap a game to focus & Launch"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) {
                                    "No games match \"$searchQuery\""
                                } else {
                                    "Your Launcher Library is Empty"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Add installed games from your device to launch with Boost & HUD.",
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
                                Text("Add Games", fontWeight = FontWeight.Bold)
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
                    text = "SYSTEM TELEMETRY",
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
                        title = "Available RAM",
                        value = String.format(Locale.US, "%.1f / %.1f GB", freeRamGb, totalRamGb),
                        subtitle = String.format(Locale.US, "%.0f%% Free", freeRamPercent),
                        accentColor = HubBlue
                    )

                    // Storage Card
                    LauncherMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Storage,
                        title = "Free Storage",
                        value = String.format(Locale.US, "%.1f GB", freeStorageGb),
                        subtitle = String.format(Locale.US, "%.0f%% Available", freeStoragePercent),
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
                        title = "Core Temp",
                        value = String.format(Locale.US, "%.1f°C", tempC),
                        subtitle = thermalInfo.thermalStatusName,
                        accentColor = tempColor
                    )
                }
            }
        }

        // Bottom Console Controller Legend Bar (appears when controller connects and enabled in Settings)
        if (isControllerConnected && showControllerLegendBar) {
            ConsoleControllerLegendBar(
                isBigScreenMode = isBigScreenMode,
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
                isOverlayEnabled = overlayConfig.isEnabled,
                showControllerGlyphs = isControllerConnected,
                onToggleOverlay = { enabled ->
                    ConsoleHapticManager.playSelectClick(context)
                    viewModel.toggleOverlayEnabled(context, enabled)
                },
                onOpenGame = { chosenMode ->
                    viewModel.setBoostMode(chosenMode)
                    gameForLaunchPopup = null
                    viewModel.quickBoost(targetApp = targetGame, mode = chosenMode)
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
                                glyph = "A",
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
                                glyph = "X",
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
            ControllerKeyHint(badge = "A", label = "Launch", badgeColor = LaunchGreen, scale = scale)
            ControllerKeyHint(badge = "X", label = "Boost", badgeColor = HubBlue, scale = scale)
            ControllerKeyHint(badge = "Y", label = "Record", badgeColor = Color(0xFFF59E0B), scale = scale)
            ControllerKeyHint(badge = "L1/R1", label = "Tabs", scale = scale)
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
                text = "Launch",
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
    isOverlayEnabled: Boolean,
    showControllerGlyphs: Boolean,
    onToggleOverlay: (Boolean) -> Unit,
    onOpenGame: (BoostMode) -> Unit,
    onRemoveFromLibrary: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val normalBoostFocusRequester = remember { FocusRequester() }
    var chosenMode by remember(initialBoostMode) {
        mutableStateOf(
            if (initialBoostMode == BoostMode.BEAST) BoostMode.BEAST else BoostMode.GAMING
        )
    }

    // Automatically focus and outline the Normal Booster button when the popup opens
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(50)
        try {
            normalBoostFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pre_launch_booster_popup")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header: Game Icon + Name + Close button (touch clickable so D-pad starts on Normal Boost)
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
                                .size(48.dp)
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
                                text = "Configure Boost & Display Overlay",
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

                Spacer(modifier = Modifier.height(18.dp))

                // 1. Booster Mode Selector: Normal vs Beast Booster
                Text(
                    text = "SELECT BOOSTER MODE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.8.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isNormal = chosenMode != BoostMode.BEAST
                    var normalFocused by remember { mutableStateOf(true) }
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
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = if (isNormal) HubBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Normal Booster",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Balanced • 3h",
                                fontSize = 11.sp,
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
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.RocketLaunch,
                                contentDescription = null,
                                tint = if (!isNormal) RecorderCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Beast Booster",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Max FPS • 90m",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Device Display Overlay (HUD) Toggle
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleOverlay(!isOverlayEnabled) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
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
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Device Display Overlay",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isOverlayEnabled) {
                                        "Live FPS, Temp & Battery HUD enabled"
                                    } else {
                                        "Show real-time telemetry over game"
                                    },
                                    fontSize = 11.sp,
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
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 3. Final Button: Open Game
                var openGameFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = { onOpenGame(chosenMode) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LaunchGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .onFocusChanged {
                            if (it.isFocused && !openGameFocused) {
                                ConsoleHapticManager.playFocusTick(context)
                            }
                            openGameFocused = it.isFocused
                        }
                        .border(
                            width = if (openGameFocused) 3.dp else 0.dp,
                            color = if (openGameFocused) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .testTag("popup_open_game_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Open Game",
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

                Spacer(modifier = Modifier.height(8.dp))

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
                            text = "App Info",
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
                            text = "Remove from Launcher",
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
                    text = "Add Game",
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

/**
 * Loads and displays an app's icon in crisp high resolution (DENSITY_XXXHIGH / 256x256 ARGB_8888)
 */
@Composable
fun AppIconImage(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val highResDrawable = try {
                val res = pm.getResourcesForApplication(appInfo)
                if (appInfo.icon != 0) {
                    res.getDrawableForDensity(appInfo.icon, DisplayMetrics.DENSITY_XXXHIGH, null)
                } else null
            } catch (_: Exception) {
                null
            } ?: pm.getApplicationIcon(packageName)

            val targetSize = 256
            val bmp = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            highResDrawable.setBounds(0, 0, targetSize, targetSize)
            highResDrawable.draw(canvas)
            bmp
        } catch (_: Exception) {
            null
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
            Icon(
                imageVector = Icons.Default.SportsEsports,
                contentDescription = null,
                tint = HubBlue,
                modifier = Modifier.size(42.dp)
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
                        text = "Add Game to Launcher",
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
                            "Search installed games & apps...",
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
