package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PerformanceOptionsRepository
import com.example.model.OptionActionType
import com.example.model.OptionIconType
import com.example.model.PerformanceOption
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ui.components.AntiCheatNoticeDialog
import com.example.ui.components.DeveloperModeHelperBanner
import com.example.ui.components.DeveloperModeTutorialDialog
import com.example.ui.components.SectionTitle
import com.example.ui.components.TacticalCrosshairCard
import com.example.ui.components.copyToClipboard
import com.example.ui.theme.HubBlue
import com.example.ui.theme.LaunchGreen
import com.example.viewmodel.GameBoosterViewModel

val BoostBlue = HubBlue
val BoostActiveCyan = Color(0xFF0EA5E9)
val RankGold = Color(0xFFF59E0B)
val RankBlue = Color(0xFF3B82F6)
val RankBronze = Color(0xFFEA580C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsScreen(
    viewModel: GameBoosterViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedOptionForModal by remember { mutableStateOf<PerformanceOption?>(null) }
    val appliedOptionIds by viewModel.appliedOptionIds.collectAsState()
    val suppressActivationWarning by viewModel.suppressActivationWarning.collectAsState()
    var pendingOptionToActivate by remember { mutableStateOf<PerformanceOption?>(null) }
    val boostFeedback by viewModel.boostFeedback.collectAsState()
    val overlaySettings by viewModel.overlaySettings.collectAsState()
    val libraryGames by viewModel.libraryGames.collectAsState()
    val controllerOverlayConfig by viewModel.controllerOverlayConfig.collectAsState()
    val controllerHealthState by viewModel.controllerHealthState.collectAsState()
    val controllerLayoutMode by viewModel.controllerLayoutMode.collectAsState()
    val activeControllerGlyphs by viewModel.activeControllerGlyphs.collectAsState()
    var showDevModeTutorial by remember { mutableStateOf(false) }

    val requestActivateOption: (PerformanceOption) -> Unit = { option ->
        val runOptionActivation: () -> Unit = {
            handleOptionClick(context, option, viewModel) {
                selectedOptionForModal = option
            }
        }
        com.example.util.UnityAdsManager.maybeTriggerPostActionAd(
            context = context,
            actionLabelEn = "Apply Tweaks",
            actionLabelEs = "Aplicar Ajustes",
            onAdFinished = runOptionActivation
        )
    }

    val featuredOptions = remember(context) { PerformanceOptionsRepository.getFeaturedOptions(context) }
    val pagerState = rememberPagerState(pageCount = { featuredOptions.size })

    val allOptions = remember(context) { PerformanceOptionsRepository.getAllOptions(context) }
    val popularTweaks = remember(context) { PerformanceOptionsRepository.getMostPopularTweaks(context) }
    val directHardwareTweaks = remember(context) { PerformanceOptionsRepository.getDirectHardwareTweaks(context) }
    val secureSettingsTweaks = remember(context) { PerformanceOptionsRepository.getSecureSettingsTweaks(context) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp)
        ) {
            // 1. Search Bar following MaterialTheme.colorScheme (sized so placeholder is never cut off)
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        } else null,
                        placeholder = {
                            Text(
                                text = com.example.util.tr("Search Options & Performance Tweaks", "Buscar Opciones y Ajustes de Rendimiento"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_options_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = HubBlue,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }

            // Quick feedback alert if turbo boost was pressed
            if (boostFeedback != null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(HubBlue.copy(alpha = 0.14f))
                            .border(1.dp, HubBlue.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val activeLang = com.example.util.currentAppLanguage()
                            Text(
                                text = com.example.util.AppStrings.autoTranslateDynamic(boostFeedback ?: "", activeLang),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearFeedback() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = HubBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Featured Horizontal Carousel Banner
            if (searchQuery.isBlank()) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalPager(
                            state = pagerState,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            pageSpacing = 12.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) { page ->
                            val option = featuredOptions[page]
                            FeaturedHeroCard(
                                option = option,
                                isApplied = appliedOptionIds.contains(option.id),
                                onActionClick = {
                                    requestActivateOption(option)
                                }
                            )
                        }

                        // Carousel Dots
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(featuredOptions.size) { index ->
                                val isSelected = pagerState.currentPage == index
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .size(width = if (isSelected) 18.dp else 6.dp, height = 6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) HubBlue
                                            else MaterialTheme.colorScheme.outline
                                        )
                                )
                            }
                        }
                    }
                }
            }

            // If user is searching, show all matched options
            if (searchQuery.isNotBlank()) {
                val activeSearchLang = com.example.util.AppStrings.resolveActiveLanguageContext(context)
                val matches = allOptions.filter {
                    it.title.contains(searchQuery, ignoreCase = true) ||
                        it.tags.contains(searchQuery, ignoreCase = true) ||
                        it.description.contains(searchQuery, ignoreCase = true) ||
                        com.example.util.AppStrings.autoTranslateDynamic(it.title, activeSearchLang).contains(searchQuery, ignoreCase = true) ||
                        com.example.util.AppStrings.autoTranslateDynamic(it.tags, activeSearchLang).contains(searchQuery, ignoreCase = true) ||
                        com.example.util.AppStrings.autoTranslateDynamic(it.description, activeSearchLang).contains(searchQuery, ignoreCase = true)
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = com.example.util.tr("Search Results (${matches.size})", "Resultados de Búsqueda (${matches.size})"),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                val showTickerInSearch = searchQuery.contains("ticker", ignoreCase = true) ||
                    searchQuery.contains("notification", ignoreCase = true) ||
                    searchQuery.contains("reply", ignoreCase = true) ||
                    searchQuery.contains("silent", ignoreCase = true)
                if (showTickerInSearch) {
                    item(key = "search_ticker_card") {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            TopEdgeNotificationTickerSectionCard()
                        }
                    }
                }

                val showVolumeInSearch = searchQuery.contains("volume", ignoreCase = true) ||
                    searchQuery.contains("audio", ignoreCase = true) ||
                    searchQuery.contains("sound", ignoreCase = true)
                if (showVolumeInSearch) {
                    item(key = "search_volume_card") {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            GamingVolumeLockSectionCard()
                        }
                    }
                }

                val showCallBlockerInSearch = searchQuery.contains("call", ignoreCase = true) ||
                    searchQuery.contains("hardcore", ignoreCase = true) ||
                    searchQuery.contains("reject", ignoreCase = true) ||
                    searchQuery.contains("block", ignoreCase = true)
                if (showCallBlockerInSearch) {
                    item(key = "search_call_blocker_card") {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            HardcoreCallBlockerSectionCard()
                        }
                    }
                }

                val showSpeedrunInSearch = searchQuery.contains("speedrun", ignoreCase = true) ||
                    searchQuery.contains("timer", ignoreCase = true) ||
                    searchQuery.contains("split", ignoreCase = true) ||
                    searchQuery.contains("cheat", ignoreCase = true)
                if (showSpeedrunInSearch) {
                    item(key = "search_speedrun_card") {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            AntiCheatSpeedrunSectionCard(availableGames = libraryGames)
                        }
                    }
                }

                items(matches, key = { "search_${it.id}" }) { option ->
                    PerformanceOptionRow(
                        option = option,
                        isApplied = appliedOptionIds.contains(option.id),
                        onBoostClick = {
                            requestActivateOption(option)
                        },
                        onRowClick = {
                            selectedOptionForModal = option
                        }
                    )
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Divider(color = dividerColor, thickness = 0.8.dp)
                    }
                }
            } else {
                // 2d. Standalone Section: Top Edge Notification Ticker & Silent Reply
                item(key = "tweaks_notification_ticker_section") {
                    Spacer(modifier = Modifier.height(20.dp))
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SectionTitle(
                            com.example.util.tr(
                                "Top Edge Notification Ticker & Silent Reply",
                                "Ticker Superior de Notificaciones y Respuesta Silenciosa"
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        TopEdgeNotificationTickerSectionCard()
                    }
                }

                // 2e. Standalone Section: Gaming Volume Lock
                item(key = "tweaks_gaming_volume_lock_section") {
                    Spacer(modifier = Modifier.height(20.dp))
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SectionTitle(
                            com.example.util.tr(
                                "Gaming Volume Lock (AudioManager)",
                                "Bloqueo de Volumen Gamer (AudioManager)"
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        GamingVolumeLockSectionCard()
                    }
                }

                // 2f. Standalone Section: Hardcore Mode Call Blocker
                item(key = "tweaks_hardcore_call_blocker_section") {
                    Spacer(modifier = Modifier.height(20.dp))
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SectionTitle(
                            com.example.util.tr(
                                "Hardcore Call Blocker & Telecom Reject",
                                "Bloqueador de Llamadas Hardcore y Rechazo Telecom"
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        HardcoreCallBlockerSectionCard()
                    }
                }

                // 2g. Standalone Section: Speedrun Timer & Past Speedruns by App
                item(key = "tweaks_speedrun_timer_section") {
                    Spacer(modifier = Modifier.height(20.dp))
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SectionTitle(
                            com.example.util.tr(
                                "Speedrun Timer & App History",
                                "Cronómetro Speedrun e Historial por App"
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        AntiCheatSpeedrunSectionCard(availableGames = libraryGames)
                    }
                }

                // 3. Section: Most Popular Tweaks
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = com.example.util.tr("Most Popular Tweaks", "Ajustes Más Populares"),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 20.sp
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                items(popularTweaks, key = { "pop_${it.id}" }) { option ->
                    PerformanceOptionRow(
                        option = option,
                        isApplied = appliedOptionIds.contains(option.id),
                        onBoostClick = {
                            requestActivateOption(option)
                        },
                        onRowClick = {
                            selectedOptionForModal = option
                        }
                    )
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Divider(color = dividerColor, thickness = 0.8.dp)
                    }
                }

                // 4. Section: Hardware & Kernel Governors
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = com.example.util.tr("Hardware & Kernel Governors", "Hardware y Gobernadores de Kernel"),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 20.sp
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                items(directHardwareTweaks, key = { "hw_${it.id}" }) { option ->
                    PerformanceOptionRow(
                        option = option,
                        isApplied = appliedOptionIds.contains(option.id),
                        onBoostClick = {
                            requestActivateOption(option)
                        },
                        onRowClick = {
                            selectedOptionForModal = option
                        }
                    )
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Divider(color = dividerColor, thickness = 0.8.dp)
                    }
                }

                // 5. Section: System Latency & Compilers
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = com.example.util.tr("System Latency & Compilers", "Latencia del Sistema y Compiladores"),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 20.sp
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    DeveloperModeHelperBanner(
                        onClick = { showDevModeTutorial = true },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                items(secureSettingsTweaks, key = { "sec_${it.id}" }) { option ->
                    PerformanceOptionRow(
                        option = option,
                        isApplied = appliedOptionIds.contains(option.id),
                        onBoostClick = {
                            requestActivateOption(option)
                        },
                        onRowClick = {
                            selectedOptionForModal = option
                        }
                    )
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Divider(color = dividerColor, thickness = 0.8.dp)
                    }
                }

                // 6. Unity Banner Card embedded in the Tweaks scrollable list with 32dp top buffer
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        com.example.ui.components.GMaxUnityBannerCard(
                            placementTag = "tweaks_bottom",
                            topBuffer = 32.dp,
                            bottomBuffer = 24.dp
                        )
                    }
                }
            }
        }

        // Option Action & Detail Modal Bottom Sheet
        selectedOptionForModal?.let { option ->
            val isApplied = appliedOptionIds.contains(option.id)
            ModalBottomSheet(
                onDismissRequest = { selectedOptionForModal = null },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 10.dp)
                            .size(width = 40.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outline)
                    )
                }
            ) {
                OptionDetailModal(
                    option = option,
                    isApplied = isApplied,
                    onToggleApplied = {
                        requestActivateOption(option)
                    },
                    onOpenSettings = {
                        requestActivateOption(option)
                        selectedOptionForModal = null
                    },
                    onDismiss = { selectedOptionForModal = null },
                    onOpenDevTutorial = {
                        selectedOptionForModal = null
                        showDevModeTutorial = true
                    }
                )
            }
        }

        // Pre-Activation Warning & Details Dialog
        pendingOptionToActivate?.let { opt ->
            ActivationWarningDialog(
                optionTitle = opt.title,
                onConfirm = { dontAskAgain ->
                    if (dontAskAgain) {
                        viewModel.setSuppressActivationWarning(true)
                    }
                    val targetOpt = opt
                    pendingOptionToActivate = null
                    handleOptionClick(context, targetOpt, viewModel) {
                        selectedOptionForModal = targetOpt
                    }
                },
                onDismiss = {
                    pendingOptionToActivate = null
                }
            )
        }

        // Android Developer Mode Activation Tutorial Dialog
        if (showDevModeTutorial) {
            DeveloperModeTutorialDialog(
                onDismiss = { showDevModeTutorial = false }
            )
        }
    }
}

@Composable
fun FeaturedHeroCard(
    option: PerformanceOption,
    isApplied: Boolean,
    onActionClick: () -> Unit
) {
    val isSupported = option.isSupported
    val activeLang = com.example.util.currentAppLanguage()
    val localizedCategory = com.example.util.AppStrings.autoTranslateDynamic(option.category, activeLang)
    val localizedTitle = com.example.util.AppStrings.autoTranslateDynamic(option.title, activeLang)
    val localizedDesc = if (!isSupported && option.unsupportedReason != null) {
        com.example.util.AppStrings.autoTranslateDynamic("Requires: ${option.unsupportedReason}", activeLang)
    } else {
        com.example.util.AppStrings.autoTranslateDynamic(option.description, activeLang)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                    )
                )
            )
            .border(
                1.dp,
                if (isApplied) LaunchGreen.copy(alpha = 0.6f) else HubBlue.copy(alpha = 0.4f),
                RoundedCornerShape(16.dp)
            )
            .alpha(if (isSupported) 1f else 0.55f)
            .clickable { onActionClick() }
            .padding(16.dp)
    ) {
        // Decorative background symbol
        Icon(
            imageVector = getIconForType(option.iconType),
            contentDescription = null,
            tint = HubBlue.copy(alpha = 0.12f),
            modifier = Modifier
                .size(110.dp)
                .align(Alignment.CenterEnd)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 90.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(HubBlue.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = localizedCategory.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = HubBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }

                    if (!isSupported) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = com.example.util.tr("UNSUPPORTED", "NO COMPATIBLE"),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFFEF4444),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = localizedTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = localizedDesc,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (!isSupported) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Button(
            onClick = onActionClick,
            enabled = isSupported,
            colors = ButtonDefaults.buttonColors(
                containerColor = when {
                    !isSupported -> MaterialTheme.colorScheme.surfaceVariant
                    isApplied -> LaunchGreen.copy(alpha = 0.18f)
                    else -> HubBlue
                },
                contentColor = when {
                    !isSupported -> MaterialTheme.colorScheme.onSurfaceVariant
                    isApplied -> LaunchGreen
                    else -> Color.White
                },
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            shape = RoundedCornerShape(20.dp),
            border = if (!isSupported) null else if (isApplied) androidx.compose.foundation.BorderStroke(1.dp, LaunchGreen) else null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .height(34.dp)
                .testTag("featured_hero_action_${option.id}")
        ) {
            Text(
                text = if (!isSupported) {
                    com.example.util.tr("N/A", "N/D")
                } else if (isApplied) {
                    com.example.util.tr("Applied", "Aplicado")
                } else {
                    com.example.util.tr("Configure", "Configurar")
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PerformanceOptionRow(
    option: PerformanceOption,
    isApplied: Boolean,
    onBoostClick: () -> Unit,
    onRowClick: () -> Unit
) {
    val isSupported = option.isSupported

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isSupported) 1f else 0.45f)
            .clickable { onRowClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            option.rank?.let { rankNum ->
                val ribbonColor = when (rankNum) {
                    1 -> RankGold
                    2 -> RankBlue
                    else -> RankBronze
                }
                Box(
                    modifier = Modifier
                        .size(width = 16.dp, height = 22.dp)
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                        .background(ribbonColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$rankNum",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp
                        )
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            val activeLang = com.example.util.currentAppLanguage()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = com.example.util.AppStrings.autoTranslateDynamic(option.title, activeLang),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (!isSupported && option.unsupportedReason != null) {
                    Text(
                        text = com.example.util.AppStrings.autoTranslateDynamic("Unavailable: ${option.unsupportedReason}", activeLang),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFFEF4444),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = com.example.util.AppStrings.autoTranslateDynamic(option.tags, activeLang),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Button(
            onClick = onBoostClick,
            enabled = isSupported,
            colors = ButtonDefaults.buttonColors(
                containerColor = when {
                    !isSupported -> MaterialTheme.colorScheme.surfaceVariant
                    isApplied -> LaunchGreen.copy(alpha = 0.16f)
                    else -> HubBlue
                },
                contentColor = when {
                    !isSupported -> MaterialTheme.colorScheme.onSurfaceVariant
                    isApplied -> LaunchGreen
                    else -> Color.White
                },
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            border = if (isApplied && isSupported) androidx.compose.foundation.BorderStroke(1.dp, LaunchGreen) else null,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .height(34.dp)
                .testTag("boost_btn_${option.id}")
        ) {
            Text(
                text = when {
                    !isSupported -> com.example.util.tr("N/A", "N/D")
                    isApplied -> com.example.util.tr("Active", "Activo")
                    else -> com.example.util.tr("Apply", "Aplicar")
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun OptionDetailModal(
    option: PerformanceOption,
    isApplied: Boolean,
    onToggleApplied: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    onOpenDevTutorial: () -> Unit = {}
) {
    val context = LocalContext.current
    val activeLang = com.example.util.currentAppLanguage()
    val isSupported = option.isSupported
    val requiresDevMode = option.actionType == OptionActionType.DIRECT_SECURE_SETTINGS_ANIMATIONS ||
        option.actionType == OptionActionType.DIRECT_SECURE_SETTINGS_DNS ||
        option.tags.contains("Developer", ignoreCase = true)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = com.example.util.AppStrings.autoTranslateDynamic(option.title, activeLang),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = com.example.util.AppStrings.autoTranslateDynamic(option.category, activeLang),
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }

            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = com.example.util.tr("Close", "Cerrar"),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (requiresDevMode) {
            Button(
                onClick = onOpenDevTutorial,
                colors = ButtonDefaults.buttonColors(containerColor = HubBlue.copy(alpha = 0.14f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, HubBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeveloperMode,
                    contentDescription = null,
                    tint = HubBlue,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = com.example.util.tr("Developer Mode 4 Step Tutorial", "Tutorial de 4 Pasos para Modo Desarrollador"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = HubBlue
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        if (!isSupported) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFEF4444).copy(alpha = 0.14f))
                    .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = com.example.util.tr("Not Supported on This Phone", "No Compatible en Este Teléfono"),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = com.example.util.AppStrings.autoTranslateDynamic(
                                option.unsupportedReason ?: "Required hardware or Android API is absent on this device.",
                                activeLang
                            ),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Text(
            text = com.example.util.AppStrings.autoTranslateDynamic(option.description, activeLang),
            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = com.example.util.tr("Applied on Phone", "Aplicado en el Teléfono"),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Text(
                    text = if (isApplied) {
                        com.example.util.tr("Option marked as active on this device", "Opción marcada como activa en este dispositivo")
                    } else {
                        com.example.util.tr("Not yet enabled", "Aún no activado")
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isApplied) LaunchGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
            Switch(
                checked = isApplied,
                enabled = isSupported,
                onCheckedChange = { onToggleApplied() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = HubBlue
                )
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (option.actionType != OptionActionType.ADB_ONLY_COMMAND) {
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isApplied) LaunchGreen else HubBlue,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isApplied) {
                        com.example.util.tr("Deactivate Tweak Now", "Desactivar Ajuste Ahora")
                    } else {
                        com.example.util.tr("Apply Tweak Now", "Aplicar Ajuste Ahora")
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        option.adbCommand?.let { adbCmd ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = com.example.util.tr("ADB Command (Power Users)", "Comando de Depuración de Android (Usuarios Avanzados)"),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = adbCmd,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = HubBlue,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    val copiedMsg = com.example.util.tr("ADB Command Copied!", "¡Comando Copiado!")
                    IconButton(
                        onClick = {
                            copyToClipboard(context, adbCmd, copiedMsg)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = com.example.util.tr("Copy", "Copiar"),
                            tint = HubBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

fun handleOptionClick(
    context: Context,
    option: PerformanceOption,
    viewModel: GameBoosterViewModel,
    onOpenModal: () -> Unit
) {
    viewModel.executePerformanceOption(context, option, onOpenModal)
}

fun openSystemSetting(context: Context, actionType: OptionActionType) {
    // All current tweaks are direct actions executed in-app
}

fun getIconForType(type: OptionIconType): ImageVector {
    return when (type) {
        OptionIconType.REFRESH_RATE -> Icons.Default.Refresh
        OptionIconType.GAME_MODE -> Icons.Default.Speed
        OptionIconType.DND_FOCUS -> Icons.Default.DoNotDisturb
        OptionIconType.RAM_CLEAN -> Icons.Default.Memory
        OptionIconType.RESOLUTION_DOWNSCALE -> Icons.Default.ViewCarousel
        OptionIconType.AUTO_BRIGHTNESS -> Icons.Default.BrightnessAuto
        OptionIconType.SCREEN_PINNING -> Icons.Default.Pin
        OptionIconType.ART_COMPILE -> Icons.Default.Code
        OptionIconType.HEAVY_APPS -> Icons.Default.Speed
        OptionIconType.BLOATWARE -> Icons.Default.AcUnit
        OptionIconType.ANIMATION_SCALE -> Icons.Default.Speed
        OptionIconType.WIFI_5G -> Icons.Default.Wifi
        OptionIconType.WIFI_LOW_LATENCY -> Icons.Default.WifiTethering
        OptionIconType.WIFI_ASSIST -> Icons.Default.Wifi
        OptionIconType.PRIVATE_DNS -> Icons.Default.Dns
        OptionIconType.THERMAL_HEADROOM -> Icons.Default.Thermostat
        OptionIconType.UNRESTRICTED_BATTERY -> Icons.Default.BatteryChargingFull
        OptionIconType.BYPASS_CHARGING -> Icons.Default.BatteryAlert
        OptionIconType.CROSSHAIR_HUD -> Icons.Default.Adjust
        OptionIconType.LOW_LATENCY_AUDIO -> Icons.Default.Headphones
        OptionIconType.GAMEPAD_CONTROLLER -> Icons.Default.SportsEsports
        OptionIconType.SUSTAINED_PERFORMANCE -> Icons.Default.Speed
        OptionIconType.THREAD_PRIORITY -> Icons.Default.Bolt
    }
}

@Composable
fun ActivationWarningDialog(
    optionTitle: String,
    onConfirm: (dontAskAgain: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var dontAskAgain by remember { mutableStateOf(false) }
    val activeLang = com.example.util.currentAppLanguage()
    val localizedTitle = com.example.util.AppStrings.autoTranslateDynamic(optionTitle, activeLang)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = com.example.util.tr("Performance Notice", "Aviso de Rendimiento"),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = com.example.util.tr(
                        "Before activating \"$localizedTitle\", review the following details:",
                        "Antes de activar \"$localizedTitle\", revisa los siguientes detalles:"
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF59E0B).copy(alpha = 0.14f))
                        .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = com.example.util.tr(
                            "• Online games might see this as cheating.\n• Your phone might heat up under sustained load.\n• System loses some animations to perform better.",
                            "• Los juegos en línea podrían detectarlo como trampa.\n• Tu teléfono puede calentarse con la carga continua.\n• El sistema reduce animaciones para rendir mejor."
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { dontAskAgain = !dontAskAgain }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = dontAskAgain,
                        onCheckedChange = { dontAskAgain = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = HubBlue,
                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            checkmarkColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = com.example.util.tr("Do not ask again", "No volver a preguntar"),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(dontAskAgain) },
                colors = ButtonDefaults.buttonColors(containerColor = HubBlue, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(com.example.util.tr("Proceed & Activate", "Continuar y Activar"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(com.example.util.tr("Cancel", "Cancelar"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun TopEdgeNotificationTickerSectionCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tickerConfig by com.example.service.NotificationTickerOverlayService.config.collectAsState()
    val lastReplySent by com.example.service.NotificationTickerOverlayService.lastSilentReplySent.collectAsState()
    val isNotifListenerGranted = remember(tickerConfig) {
        com.example.service.NotificationTickerOverlayService.isNotificationListenerGranted(context)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                1.dp,
                if (tickerConfig.isEnabled) BoostActiveCyan.copy(alpha = 0.75f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                RoundedCornerShape(16.dp)
            )
            .testTag("tweaks_notification_ticker_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DoNotDisturb,
                            contentDescription = null,
                            tint = BoostActiveCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = com.example.util.tr(
                                "Top Edge Notification Ticker & Silent Reply",
                                "Ticker Superior de Notificaciones y Respuesta Silenciosa"
                            ),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = com.example.util.tr(
                            "Intercepts WhatsApp, Telegram, SMS & incoming calls into a top edge ticker, hides when answered, and sends silent RemoteInput replies",
                            "Intercepta WhatsApp, Telegram, SMS y llamadas en un ticker superior, se oculta al contestar y envía respuestas silenciosas reales"
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    )
                }

                Switch(
                    checked = tickerConfig.isEnabled,
                    onCheckedChange = { enabled ->
                        com.example.service.NotificationTickerOverlayService.setEnabled(context, enabled)
                        if (enabled && !isNotifListenerGranted) {
                            com.example.service.NotificationTickerOverlayService.openNotificationListenerSettings(context)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = BoostActiveCyan
                    ),
                    modifier = Modifier.testTag("tweaks_notification_ticker_toggle")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = com.example.util.tr(
                        "Ticker Countdown (Default 6s):",
                        "Cuenta Atrás del Ticker (6s por defecto):"
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "${tickerConfig.countdownSeconds}s",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = BoostActiveCyan,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(4, 6, 8, 10, 12, 15).forEach { secs ->
                    val isSelected = tickerConfig.countdownSeconds == secs
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            com.example.service.NotificationTickerOverlayService.setCountdownSeconds(context, secs)
                        },
                        label = {
                            Text(
                                text = if (secs == 6) com.example.util.tr("6s (Default)", "6s (Por defecto)") else "${secs}s",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BoostActiveCyan.copy(alpha = 0.22f),
                            selectedLabelColor = BoostActiveCyan
                        ),
                        modifier = Modifier.testTag("tweaks_ticker_duration_${secs}s")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = com.example.util.tr(
                    "Silent Quick Reply Presets (Tap 'Reply ▾' on ticker):",
                    "Respuestas Rápidas Silenciosas (Toca 'Reply ▾' en el ticker):"
                ),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                com.example.service.NotificationTickerOverlayService.QUICK_REPLIES.forEach { presetReply ->
                    val activeLang = com.example.util.currentAppLanguage()
                    val localizedReply = com.example.util.AppStrings.autoTranslateDynamic(presetReply, activeLang)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, HubBlue.copy(alpha = 0.45f), RoundedCornerShape(50))
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "\"$localizedReply\"",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (!lastReplySent.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = com.example.util.tr(
                        "✓ Last silent background reply sent: \"$lastReplySent\"",
                        "✓ Última respuesta silenciosa enviada: \"$lastReplySent\""
                    ),
                    color = LaunchGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isNotifListenerGranted) {
                    OutlinedButton(
                        onClick = {
                            com.example.service.NotificationTickerOverlayService.openNotificationListenerSettings(context)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("tweaks_grant_notif_listener_button")
                    ) {
                        Text(
                            text = com.example.util.tr("Grant Notification Access", "Dar Acceso a Notificaciones"),
                            color = BoostActiveCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        if (!android.provider.Settings.canDrawOverlays(context)) {
                            com.example.util.SystemSettingsHelper.openOverlayPermissionSettings(context)
                        } else {
                            com.example.service.NotificationTickerOverlayService.triggerDemoTicker(context, isCall = false)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("tweaks_test_message_ticker_button")
                ) {
                    Text(
                        text = com.example.util.tr("Test Message Ticker", "Probar Ticker de Mensaje"),
                        color = HubBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = {
                        if (!android.provider.Settings.canDrawOverlays(context)) {
                            com.example.util.SystemSettingsHelper.openOverlayPermissionSettings(context)
                        } else {
                            com.example.service.NotificationTickerOverlayService.triggerDemoTicker(context, isCall = true)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("tweaks_test_call_ticker_button")
                ) {
                    Text(
                        text = com.example.util.tr("Test Call Ticker", "Probar Ticker de Llamada"),
                        color = LaunchGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun GamingVolumeLockSectionCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val volLockState by com.example.service.GamingVolumeLockManager.state.collectAsState()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                1.dp,
                if (volLockState.isEnabled) HubBlue.copy(alpha = 0.75f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                RoundedCornerShape(16.dp)
            )
            .testTag("tweaks_gaming_volume_lock_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = null,
                            tint = HubBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = com.example.util.tr(
                                "Gaming Volume Lock (${volLockState.lockedVolumePercent}%)",
                                "Bloqueo de Volumen Gamer (${volLockState.lockedVolumePercent}%)"
                            ),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = com.example.util.tr(
                            "Uses Android AudioManager to detect accidental volume button presses during matches, restores volume to ${volLockState.lockedVolumePercent}%, and shows a compact notice banner",
                            "Usa Android AudioManager para detectar cambios accidentales de volumen, restaurarlo al ${volLockState.lockedVolumePercent}% y mostrar un aviso compacto"
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    )
                }

                Switch(
                    checked = volLockState.isEnabled,
                    onCheckedChange = { enabled ->
                        com.example.service.GamingVolumeLockManager.setEnabled(context, enabled)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = HubBlue
                    ),
                    modifier = Modifier.testTag("tweaks_gaming_volume_lock_toggle")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(50, 65, 80, 90, 100).forEach { pct ->
                    val isSelected = volLockState.lockedVolumePercent == pct
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            com.example.service.GamingVolumeLockManager.setLockedVolumePercent(context, pct)
                        },
                        label = {
                            Text(
                                text = if (pct == 80) com.example.util.tr("80% (Default)", "80% (Por defecto)") else "$pct%",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = HubBlue.copy(alpha = 0.22f),
                            selectedLabelColor = HubBlue
                        ),
                        modifier = Modifier.testTag("tweaks_volume_lock_pct_$pct")
                    )
                }

                OutlinedButton(
                    onClick = {
                        com.example.service.GamingVolumeLockManager.simulateAccidentalVolumeDropAndSnapBack(context)
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("tweaks_test_volume_lock_snapback_button")
                ) {
                    Text(
                        text = com.example.util.tr("Test Volume Restore Notice", "Probar Aviso de Restauración"),
                        color = LaunchGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (!volLockState.lastNoticeMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                val activeLang = com.example.util.currentAppLanguage()
                Text(
                    text = com.example.util.AppStrings.autoTranslateDynamic(volLockState.lastNoticeMessage ?: "", activeLang),
                    color = BoostActiveCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun HardcoreCallBlockerSectionCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hardcoreState by com.example.service.HardcoreCallBlockerManager.state.collectAsState()
    var showTweaksPostMatchModal by remember { mutableStateOf(false) }

    val phonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        com.example.service.HardcoreCallBlockerManager.refreshPermissionStates(context)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                1.dp,
                if (hardcoreState.isEnabled) Color(0xFFEF4444).copy(alpha = 0.75f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                RoundedCornerShape(16.dp)
            )
            .testTag("tweaks_hardcore_call_blocker_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = com.example.util.tr(
                                "Hardcore Mode Call Blocker & Missed Call Log",
                                "Modo Hardcore Bloqueador de Llamadas y Registro Real"
                            ),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = com.example.util.tr(
                            "Uses Android CallScreeningService, TelecomManager.endCall(), and DND silence to reject incoming phone calls and log them for quick callback",
                            "Usa CallScreeningService, TelecomManager.endCall() y silencio DND para rechazar llamadas entrantes reales y registrarlas para devolver la llamada"
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    )
                }

                Switch(
                    checked = hardcoreState.isEnabled,
                    onCheckedChange = { enabled ->
                        com.example.service.HardcoreCallBlockerManager.setEnabled(context, enabled)
                        if (enabled && !hardcoreState.isTelecomPermissionGranted) {
                            phonePermissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.READ_PHONE_STATE,
                                    android.Manifest.permission.ANSWER_PHONE_CALLS
                                )
                            )
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFEF4444)
                    ),
                    modifier = Modifier.testTag("tweaks_hardcore_mode_toggle")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { showTweaksPostMatchModal = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("tweaks_open_post_match_summary_button")
                ) {
                    Text(
                        text = com.example.util.tr(
                            "Open Session Summary (${hardcoreState.blockedCalls.size})",
                            "Abrir Resumen de Partida (${hardcoreState.blockedCalls.size})"
                        ),
                        color = HubBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!hardcoreState.isCallScreeningRoleHeld) {
                    OutlinedButton(
                        onClick = {
                            com.example.service.HardcoreCallBlockerManager.openCallScreeningSettingsFallback(context)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("tweaks_grant_call_screening_role_button")
                    ) {
                        Text(
                            text = com.example.util.tr("Grant Call Screening Role", "Activar Rol de Filtro de Llamadas"),
                            color = BoostActiveCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (!hardcoreState.isTelecomPermissionGranted) {
                    OutlinedButton(
                        onClick = {
                            phonePermissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.READ_PHONE_STATE,
                                    android.Manifest.permission.ANSWER_PHONE_CALLS
                                )
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("tweaks_grant_telecom_hangup_button")
                    ) {
                        Text(
                            text = com.example.util.tr("Grant Telecom Hangup", "Dar Permiso Telecom Colgar"),
                            color = Color(0xFFEF4444),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (!hardcoreState.isDndAccessGranted) {
                    OutlinedButton(
                        onClick = {
                            com.example.service.HardcoreCallBlockerManager.openDndPolicyAccessSettings(context)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("tweaks_grant_dnd_silence_button")
                    ) {
                        Text(
                            text = com.example.util.tr("Grant DND Silence", "Dar Acceso No Molestar"),
                            color = RankGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (hardcoreState.blockedCalls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    hardcoreState.blockedCalls.take(3).forEach { callRecord ->
                        com.example.ui.components.BlockedCallRowItem(
                            record = callRecord,
                            onCallBack = {
                                com.example.service.HardcoreCallBlockerManager.dialCallback(
                                    context,
                                    callRecord.phoneNumber
                                )
                            }
                        )
                    }
                }
            }

            if (showTweaksPostMatchModal) {
                com.example.ui.components.PostMatchSummaryModalDialog(
                    hardcoreState = hardcoreState,
                    latestRecap = null,
                    onCallBack = { phone ->
                        com.example.service.HardcoreCallBlockerManager.dialCallback(context, phone)
                    },
                    onClearCallsLog = {
                        com.example.service.HardcoreCallBlockerManager.clearBlockedCallsLog(context)
                    },
                    onDismiss = { showTweaksPostMatchModal = false }
                )
            }
        }
    }
}

@Composable
fun AntiCheatSpeedrunSectionCard(
    availableGames: List<com.example.model.InstalledApp> = emptyList(),
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                RoundedCornerShape(16.dp)
            )
            .testTag("tweaks_speedrun_section_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            com.example.ui.components.AntiCheatSpeedrunTimerCard(
                availableGames = availableGames
            )
        }
    }
}



