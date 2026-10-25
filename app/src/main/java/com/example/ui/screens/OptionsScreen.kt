package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import com.example.ui.components.AntiCheatNoticeDialog
import com.example.ui.components.CrosshairData
import com.example.ui.components.CrosshairPreview
import com.example.ui.components.DeveloperModeTutorialDialog
import com.example.ui.components.TacticalCrosshairCard
import com.example.ui.components.DeveloperModeHelperBanner
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import com.example.ui.components.copyToClipboard
import com.example.util.SystemSettingsHelper
import com.example.viewmodel.GameBoosterViewModel

// Dark Cyber aesthetic palette matching reference screenshot
val ScreenshotBg = Color(0xFF0D111A)
val SearchBarBg = Color(0xFF191D28)
val SearchBarText = Color(0xFF8B94A6)
val CardBg = Color(0xFF141824)
val DividerColor = Color(0xFF1C2230)
val BoostBlue = Color(0xFF2563EB)
val BoostActiveCyan = Color(0xFF00E5FF)
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
    val isBoosting by viewModel.isBoosting.collectAsState()
    val boostFeedback by viewModel.boostFeedback.collectAsState()
    val currentTheme by viewModel.appTheme.collectAsState()
    val overlaySettings by viewModel.overlaySettings.collectAsState()
    var showAntiCheatNotice by remember { mutableStateOf(false) }
    var showDevModeTutorial by remember { mutableStateOf(false) }

    val requestActivateOption: (PerformanceOption) -> Unit = { option ->
        val isApplied = appliedOptionIds.contains(option.id)
        if (option.actionType == OptionActionType.DIRECT_SECURE_SETTINGS_ANIMATIONS ||
            option.actionType == OptionActionType.DIRECT_SECURE_SETTINGS_DNS
        ) {
            if (!com.example.util.SecureSettingsManager.canWriteSecureSettings(context)) {
                showDevModeTutorial = true
            } else {
                handleOptionClick(context, option, viewModel) {
                    selectedOptionForModal = option
                }
            }
        } else if (option.id == "tweak_crosshair" || option.actionType == OptionActionType.DIRECT_CROSSHAIR_OVERLAY) {
            if (!overlaySettings.antiCheatNoticeAcknowledged) {
                showAntiCheatNotice = true
            } else {
                handleOptionClick(context, option, viewModel) {
                    selectedOptionForModal = option
                }
            }
        } else if (isApplied || suppressActivationWarning) {
            handleOptionClick(context, option, viewModel) {
                selectedOptionForModal = option
            }
        } else {
            pendingOptionToActivate = option
        }
    }

    val featuredOptions = remember(context) { PerformanceOptionsRepository.getFeaturedOptions(context) }
    val pagerState = rememberPagerState(pageCount = { featuredOptions.size })

    val allOptions = remember(context) { PerformanceOptionsRepository.getAllOptions(context) }
    val popularTweaks = remember(context) { PerformanceOptionsRepository.getMostPopularTweaks(context) }
    val directHardwareTweaks = remember(context) { PerformanceOptionsRepository.getDirectHardwareTweaks(context) }
    val secureSettingsTweaks = remember(context) { PerformanceOptionsRepository.getSecureSettingsTweaks(context) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(currentTheme.backgroundColor)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
        ) {
            // 1. Search Bar (matches pill in reference screenshot)
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(SearchBarBg)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = SearchBarText,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = "Search Options & Performance Tweaks",
                                    color = SearchBarText,
                                    fontSize = 14.sp
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_options_field"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }
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
                            .background(Color(0xFF003D47))
                            .border(1.dp, BoostActiveCyan.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = boostFeedback ?: "",
                                color = BoostActiveCyan,
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
                                    tint = BoostActiveCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Featured Horizontal Carousel Banner (matches top card in reference screenshot)
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
                                        .background(if (isSelected) Color.White else Color(0xFF374151))
                                )
                            }
                        }
                    }
                }
            }

            // If user is searching, show all matched options
            if (searchQuery.isNotBlank()) {
                val matches = allOptions.filter {
                    it.title.contains(searchQuery, ignoreCase = true) ||
                            it.tags.contains(searchQuery, ignoreCase = true) ||
                            it.description.contains(searchQuery, ignoreCase = true)
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Search Results (${matches.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(matches, key = { it.id }) { option ->
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
                        Divider(color = DividerColor, thickness = 0.8.dp)
                    }
                }
            } else {
                // Dedicated Tactical Crosshair Customizer Card
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    TacticalCrosshairCard(
                        overlaySettings = overlaySettings,
                        onToggle = { enabled -> viewModel.setCrosshairEnabled(context, enabled) },
                        onSelectStyle = { style -> viewModel.updateCrosshairStyle(style) },
                        onSelectColor = { colorVal -> viewModel.updateCrosshairColor(colorVal) },
                        onSelectSize = { size -> viewModel.updateCrosshairSize(size) },
                        onShowAntiCheatNotice = { showAntiCheatNotice = true },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // 3. Section: Most Popular Tweaks
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Most Popular Tweaks",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 20.sp
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                items(popularTweaks, key = { it.id }) { option ->
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
                        Divider(color = DividerColor, thickness = 0.8.dp)
                    }
                }

                // 2. Section: Hardware & Kernel Governors
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Hardware & Kernel Governors",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 20.sp
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                items(directHardwareTweaks, key = { it.id }) { option ->
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
                        Divider(color = DividerColor, thickness = 0.8.dp)
                    }
                }

                // 3. Section: System Latency & Compilers
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "System Latency & Compilers",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
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

                items(secureSettingsTweaks, key = { it.id }) { option ->
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
                        Divider(color = DividerColor, thickness = 0.8.dp)
                    }
                }
            }
        }

        // 3. Option Action & Detail Modal Bottom Sheet
        selectedOptionForModal?.let { option ->
            val isApplied = appliedOptionIds.contains(option.id)
            ModalBottomSheet(
                onDismissRequest = { selectedOptionForModal = null },
                sheetState = sheetState,
                containerColor = Color(0xFF141824),
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
                OptionDetailModal(
                    option = option,
                    isApplied = isApplied,
                    onToggleApplied = {
                        if (!isApplied) {
                            requestActivateOption(option)
                        } else {
                            viewModel.toggleOptionApplied(option.id)
                        }
                    },
                    onOpenSettings = {
                        openSystemSetting(context, option.actionType)
                    },
                    onDismiss = { selectedOptionForModal = null },
                    onOpenDevTutorial = {
                        selectedOptionForModal = null
                        showDevModeTutorial = true
                    }
                )
            }
        }

        // 4. Pre-Activation Warning & Details Dialog
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

        // Anti-Cheat Fair Play Notice Dialog
        if (showAntiCheatNotice) {
            AntiCheatNoticeDialog(
                onConfirm = {
                    viewModel.acknowledgeAntiCheatNotice()
                    showAntiCheatNotice = false
                    viewModel.setCrosshairEnabled(context, true)
                },
                onDismiss = {
                    showAntiCheatNotice = false
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF232938), Color(0xFF151922))
                )
            )
            .border(1.dp, Color(0xFF2C3446), RoundedCornerShape(16.dp))
            .alpha(if (isSupported) 1f else 0.55f)
            .clickable { onActionClick() }
            .padding(16.dp)
    ) {
        // Decorative background symbol
        Icon(
            imageVector = getIconForType(option.iconType),
            contentDescription = null,
            tint = Color(0x15FFFFFF),
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
                            .background(BoostBlue.copy(alpha = 0.3f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = option.category.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = BoostActiveCyan,
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
                                .background(Color(0xFF451A1A))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "UNSUPPORTED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFFF87171),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (!isSupported && option.unsupportedReason != null) "Requires: ${option.unsupportedReason}" else option.description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (!isSupported) Color(0xFFFCA5A5) else Color(0xFF9CA3AF),
                        fontSize = 12.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Pill button matching "Discover" in screenshot
        Button(
            onClick = onActionClick,
            enabled = isSupported,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (!isSupported) Color(0xFF1E2536) else if (isApplied) Color(0xFF003844) else Color(0x22FFFFFF),
                contentColor = if (!isSupported) Color(0xFF6B7280) else if (isApplied) BoostActiveCyan else Color.White,
                disabledContainerColor = Color(0xFF1A1F2C),
                disabledContentColor = Color(0xFF4B5563)
            ),
            shape = RoundedCornerShape(20.dp),
            border = if (!isSupported) null else if (isApplied) androidx.compose.foundation.BorderStroke(1.dp, BoostActiveCyan) else androidx.compose.foundation.BorderStroke(1.dp, Color(0x44FFFFFF)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .height(34.dp)
                .testTag("featured_hero_action_${option.id}")
        ) {
            Text(
                text = if (!isSupported) "N/A" else if (isApplied) "Applied" else "Configure",
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
            // Ribbon Rank Badge (1, 2, 3) as seen in screenshot
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

            // Title & Tags / Unsupported reason
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (!isSupported && option.unsupportedReason != null) {
                    Text(
                        text = "Unavailable: ${option.unsupportedReason}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFFF87171),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = option.tags,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF818C9E),
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Right Pill Button: "Boost" or "Active" or "N/A"
        Button(
            onClick = onBoostClick,
            enabled = isSupported,
            colors = ButtonDefaults.buttonColors(
                containerColor = when {
                    !isSupported -> Color(0xFF1E2536)
                    isApplied -> Color(0xFF003844)
                    else -> BoostBlue
                },
                contentColor = when {
                    !isSupported -> Color(0xFF6B7280)
                    isApplied -> BoostActiveCyan
                    else -> Color.White
                },
                disabledContainerColor = Color(0xFF1A1F2C),
                disabledContentColor = Color(0xFF4B5563)
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .height(34.dp)
                .testTag("boost_btn_${option.id}")
        ) {
            Text(
                text = when {
                    !isSupported -> "N/A"
                    isApplied -> "Active"
                    else -> "Boost"
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
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = option.category,
                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF818C9E))
                )
            }

            IconButton(onClick = onDismiss) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))



        // Developer Mode Tutorial prompt button for tweaks requiring developer options
        if (requiresDevMode) {
            Button(
                onClick = onOpenDevTutorial,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2438)),
                border = androidx.compose.foundation.BorderStroke(1.dp, BoostActiveCyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.DeveloperMode, contentDescription = null, tint = BoostActiveCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Developer Mode 4-Step Tutorial",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = BoostActiveCyan
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Device Support Banner if not supported
        if (!isSupported) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF331414))
                    .border(1.dp, Color(0xFF7F1D1D), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFF87171),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Not Supported on This Phone",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color(0xFFF87171),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = option.unsupportedReason ?: "Required hardware or Android API is absent on this device.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFFFCA5A5),
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Description
        Text(
            text = option.description,
            style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFD1D5DB))
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Status Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E2433))
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Applied on Phone",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                )
                Text(
                    text = if (isApplied) "Option marked as active on this device" else "Not yet enabled",
                    style = MaterialTheme.typography.labelSmall.copy(color = if (isApplied) BoostActiveCyan else Color(0xFF9CA3AF))
                )
            }
            Switch(
                checked = isApplied,
                enabled = isSupported,
                onCheckedChange = { onToggleApplied() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = BoostBlue
                )
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Open Direct System Settings Button if applicable
        if (option.actionType != OptionActionType.ADB_ONLY_COMMAND) {
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(containerColor = BoostBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Open Phone Setting Now",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        // ADB Command snippet if available
        option.adbCommand?.let { adbCmd ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "ADB / Shizuku Command (Power Users)",
                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF818C9E), fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF070A12))
                    .border(1.dp, Color(0xFF1F2636), RoundedCornerShape(8.dp))
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
                            color = BoostActiveCyan,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            copyToClipboard(context, adbCmd, "ADB Command Copied!")
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = BoostActiveCyan, modifier = Modifier.size(16.dp))
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

fun getIconContainerColor(type: OptionIconType): Color {
    return when (type) {
        OptionIconType.REFRESH_RATE -> Color(0xFF1E2D4A)
        OptionIconType.DND_FOCUS -> Color(0xFF381E2E)
        OptionIconType.RAM_CLEAN -> Color(0xFF1E383A)
        OptionIconType.WIFI_5G, OptionIconType.WIFI_LOW_LATENCY -> Color(0xFF1C3224)
        OptionIconType.THERMAL_HEADROOM -> Color(0xFF3D2518)
        OptionIconType.GAMEPAD_CONTROLLER, OptionIconType.CROSSHAIR_HUD -> Color(0xFF291F44)
        OptionIconType.SUSTAINED_PERFORMANCE -> Color(0xFF3D1E1E)
        OptionIconType.THREAD_PRIORITY -> Color(0xFF1E293B)
        else -> Color(0xFF1E2536)
    }
}

fun getIconTintColor(type: OptionIconType): Color {
    return when (type) {
        OptionIconType.REFRESH_RATE -> Color(0xFF60A5FA)
        OptionIconType.DND_FOCUS -> Color(0xFFF43F5E)
        OptionIconType.RAM_CLEAN -> Color(0xFF2DD4BF)
        OptionIconType.WIFI_5G, OptionIconType.WIFI_LOW_LATENCY -> Color(0xFF4ADE80)
        OptionIconType.THERMAL_HEADROOM -> Color(0xFFFB923C)
        OptionIconType.GAMEPAD_CONTROLLER, OptionIconType.CROSSHAIR_HUD -> Color(0xFFA78BFA)
        OptionIconType.SUSTAINED_PERFORMANCE -> Color(0xFFEF4444)
        OptionIconType.THREAD_PRIORITY -> Color(0xFF38BDF8)
        else -> Color(0xFF93C5FD)
    }
}

@Composable
fun ActivationWarningDialog(
    optionTitle: String,
    onConfirm: (dontAskAgain: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var dontAskAgain by remember { mutableStateOf(false) }

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
                    text = "Performance Notice",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Before activating \"$optionTitle\", note the following trade-offs:",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFFD1D5DB),
                        fontWeight = FontWeight.SemiBold
                    )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF241A12))
                        .border(1.dp, Color(0xFF92400E), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "• Online games might see this as cheating.\n• Your phone might heat up under sustained boost.\n• System loses some animations to perform better.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFFFDE68A),
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
                            checkedColor = Color(0xFF3B82F6),
                            uncheckedColor = Color(0xFF9CA3AF),
                            checkmarkColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Do not ask again",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(dontAskAgain) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Proceed & Activate", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancel", color = Color(0xFF9CA3AF))
            }
        },
        containerColor = Color(0xFF131826),
        shape = RoundedCornerShape(16.dp)
    )
}
