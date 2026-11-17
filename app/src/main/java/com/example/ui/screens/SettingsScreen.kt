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
import androidx.compose.material.icons.filled.PrivacyTip
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
import com.example.ui.theme.SolarGold
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
    val controllerLayoutMode by viewModel.controllerLayoutMode.collectAsState()
    val activeControllerGlyphs by viewModel.activeControllerGlyphs.collectAsState()
    val controllerOverlayConfig by viewModel.controllerOverlayConfig.collectAsState()
    val controllerHealthState by viewModel.controllerHealthState.collectAsState()
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
                        text = com.example.util.tr("Hub Appearance & Settings", "Apariencia y Ajustes del Hub"),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = com.example.util.tr(
                        "Automatic Light/Dark display sync, custom backgrounds, and cloud account backup",
                        "Sincronización automática Claro/Oscuro, fondos personalizados y respaldo en la nube"
                    ),
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
                                        ?: com.example.util.tr("Signed In Account", "Cuenta Conectada")
                                } else {
                                    com.example.util.tr("Guest Profile (Local Settings Only)", "Perfil de Invitado (Solo Ajustes Locales)")
                                },
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            val activeLang = com.example.util.currentAppLanguage()
                            Text(
                                text = com.example.util.AppStrings.autoTranslateDynamic(cloudSyncStatus, activeLang),
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
                            text = if (isSignedIn) com.example.util.tr("Account", "Cuenta") else com.example.util.tr("Sign In", "Iniciar Sesión"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // 1c. App Language & Region Selector Card (Auto-Detect + EN, ES-MX, PT-BR, ID, VI, TL)
        item {
            val selectedLanguageMode by viewModel.appLanguageMode.collectAsState()
            val resolvedActiveLanguage = com.example.util.currentAppLanguage()

            CyberCard {
                SectionTitle(
                    title = com.example.util.tr("App Language & Region", "Idioma y Región de la App"),
                    subtitle = com.example.util.tr(
                        "Detects device language or choose English, Español, Português, Indonesian, Vietnamese, or Tagalog",
                        "Detecta automáticamente el idioma del dispositivo o elige Español, Inglés, Portugués, Indonesio, Vietnamita o Tagalo"
                    ),
                    badge = resolvedActiveLanguage.flagBadge,
                    badgeColor = LaunchGreen
                )

                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.example.util.AppLanguage.entries.forEach { langOption ->
                        val isSelected = selectedLanguageMode == langOption
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) HubBlue.copy(alpha = 0.16f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) HubBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    viewModel.setAppLanguageMode(langOption)
                                    com.example.util.ConsoleHapticManager.playSelectClick(context)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("language_option_${langOption.code.lowercase(Locale.ROOT)}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isSelected) HubBlue else MaterialTheme.colorScheme.surface
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = langOption.flagBadge,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.sp
                                        ),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (langOption == com.example.util.AppLanguage.AUTO) {
                                            com.example.util.tr(
                                                "Auto-Detect (System)",
                                                "Detección Automática (Sistema)",
                                                "Detecção Automática (Sistema)",
                                                "Deteksi Otomatis (Sistem)",
                                                "Tự động nhận diện (Hệ thống)",
                                                "Awtomatikong I-detect (Sistema)",
                                                "Detección Automática (Sistema)",
                                                "自動検出 (システム)",
                                                "ऑटो-डिटेक्ट (सिस्टम)"
                                            )
                                        } else {
                                            langOption.nativeLabel
                                        },
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = com.example.util.AppStrings.autoTranslateDynamic(langOption.englishSubtitle, resolvedActiveLanguage),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        ),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (isSelected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = LaunchGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Display Theme & Custom Background Engine
        item {
            CyberCard {
                SectionTitle(
                    title = com.example.util.tr("Display Mode & Background", "Modo de Pantalla y Fondo"),
                    subtitle = com.example.util.tr(
                        "Automatically follows your device display mode (Light / Dark) or use a custom color",
                        "Sigue automáticamente el modo de pantalla de tu dispositivo (Claro / Oscuro) o usa un color personalizado"
                    )
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

                                    val activeThemeLang = com.example.util.currentAppLanguage()
                                    Column {
                                        Text(
                                            text = com.example.util.AppStrings.autoTranslateDynamic(themeOption.title, activeThemeLang),
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                        Text(
                                            text = com.example.util.AppStrings.autoTranslateDynamic(themeOption.description, activeThemeLang),
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
                                            contentDescription = com.example.util.tr("Selected", "Seleccionado"),
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
                            text = com.example.util.tr("CURATED BACKGROUND PRESETS", "FONDOS PREDETERMINADOS"),
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
                                    val activePresetLang = com.example.util.currentAppLanguage()
                                    Text(
                                        text = com.example.util.AppStrings.autoTranslateDynamic(preset.name, activePresetLang),
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
                                        text = com.example.util.tr("Custom Color Mixer", "Mezclador de Color Personalizado"),
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
                                    label = com.example.util.tr("Red", "Rojo"),
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
                                    label = com.example.util.tr("Green", "Verde"),
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
                                    label = com.example.util.tr("Blue", "Azul"),
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
                        title = com.example.util.tr("Lite Mode (Reduced Animations)", "Modo Ligero (Animaciones Reducidas)"),
                        subtitle = com.example.util.tr(
                            "Disables heavy spring transitions, tile scaling, and boot effects for low-end phones",
                            "Desactiva transiciones pesadas, escalado de tarjetas y efectos de inicio para móviles de gama baja"
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (viewModel.isLowRamDeviceDetected) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(HubBlue.copy(alpha = 0.14f))
                            .border(1.dp, HubBlue.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = com.example.util.tr(
                                "Disabled by default • Optional toggle for ≤ 4 GB RAM devices",
                                "Desactivado por defecto • Opción para dispositivos con ≤ 4 GB de RAM"
                            ),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = HubBlue
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
                            text = com.example.util.tr("Enable Lite Mode (Reduced Animations)", "Activar Modo Ligero (Animaciones Reducidas)"),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = if (reducedAnimations) {
                                com.example.util.tr("Active: Instant transitions & fast static tile focus", "Activo: Transiciones instantáneas y enfoque sin retraso")
                            } else {
                                com.example.util.tr("Off by default: Full console spring physics & startup animation", "Desactivado: Física completa de consola y animación de inicio")
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

        // 2b2. Memory, Cache & Battery Footprint Engine
        item {
            val memoryStats by viewModel.memoryStats.collectAsState()
            val lastTrimResult by viewModel.lastMemoryTrimResult.collectAsState()
            val isCleaningMemory by viewModel.isCleaningMemory.collectAsState()
            val isBgPaused by viewModel.isBackgroundTelemetryPaused.collectAsState()

            CyberCard {
                SectionTitle(
                    title = com.example.util.tr(
                        "Memory, Cache & Battery Footprint",
                        "Memoria, Caché y Consumo de Batería"
                    ),
                    subtitle = com.example.util.tr(
                        "Smart trim on launch & focus mode • Idle battery saver",
                        "Limpieza automática al iniciar • Ahorro en inactividad"
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .border(
                            width = 1.dp,
                            color = HubBlue.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = com.example.util.tr("RAM & Storage Footprint", "Huella de RAM y Almacenamiento"),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            val usagePct = if (memoryStats.totalRamMb > 0) {
                                ((memoryStats.usedRamMb * 100L) / memoryStats.totalRamMb).toInt()
                            } else 0
                            val activeMemLang = com.example.util.currentAppLanguage()
                            Text(
                                text = com.example.util.AppStrings.autoTranslateDynamic(
                                    "RAM: ${memoryStats.usedRamMb}MB / ${memoryStats.totalRamMb}MB (${usagePct}%) • Storage Free: ${String.format(Locale.US, "%.1f", memoryStats.internalStorageFreeGb)}GB",
                                    activeMemLang
                                ),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(LaunchGreen.copy(alpha = 0.16f))
                                .border(1.dp, LaunchGreen.copy(alpha = 0.5f), RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isBgPaused) {
                                    com.example.util.tr("IDLE SAVER ON", "AHORRO ACTIVO")
                                } else {
                                    com.example.util.tr("GUARD ACTIVE", "GUARDIA ACTIVA")
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = LaunchGreen,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    Text(
                        text = com.example.util.tr(
                            "Pauses idle sensors to save battery and clears app cache on game launch.",
                            "Pausa sensores en inactividad para ahorrar batería y limpia caché al iniciar un juego."
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    )

                    lastTrimResult?.let { res ->
                        val cacheKb = (res.cacheClearedBytes / 1024L).coerceAtLeast(0L)
                        val activeMemLang = com.example.util.currentAppLanguage()
                        val localizedReason = com.example.util.AppStrings.autoTranslateDynamic(res.reason, activeMemLang)
                        Text(
                            text = com.example.util.AppStrings.autoTranslateDynamic(
                                "Last Cleanup ($localizedReason): ${res.ramFreedMb} MB RAM freed • ${cacheKb} KB cache purged (${res.backgroundAppsTrimmed} bg apps trimmed)",
                                activeMemLang
                            ),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = HubBlue,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.testTag("last_memory_trim_summary_text")
                        )
                    }

                    Button(
                        onClick = {
                            com.example.util.ConsoleHapticManager.playSelectClick(context)
                            viewModel.cleanRamAndCacheNow()
                        },
                        enabled = !isCleaningMemory,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HubBlue,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("purge_cache_and_ram_button")
                    ) {
                        Text(
                            text = if (isCleaningMemory) {
                                com.example.util.tr("Purging Cache & Trimming RAM...", "Purgando Caché y Liberando RAM...")
                            } else {
                                com.example.util.tr("Purge Cache & Trim RAM Now", "Purgar Caché y Liberar RAM Ahora")
                            },
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.Black
                            )
                        )
                    }
                }
            }
        }

        // 2c. Console & Controller Preferences (Auto Big Screen on Landscape + Controller Shortcut Overlay Toggle)
        item {
            CyberCard {
                SectionTitle(
                    title = com.example.util.tr("Console & Controller Experience", "Experiencia de Consola y Mando"),
                    subtitle = com.example.util.tr(
                        "Automatic landscape Big Screen mode & controller shortcut bar visibility",
                        "Modo Pantalla Grande automático en horizontal y visibilidad de la barra de atajos"
                    )
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
                                text = com.example.util.tr("Automatic Big Screen on Landscape", "Pantalla Grande Automática en Horizontal"),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = com.example.util.tr(
                                    "Automatically switch to Big Screen Mode when turning your phone sideways. Pressing the Controller Home/Guide button always activates Big Screen Mode even if turned off.",
                                    "Cambia automáticamente al Modo Pantalla Grande al girar el teléfono horizontalmente. El botón Home del mando siempre activa el Modo Pantalla Grande."
                                ),
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
                                text = com.example.util.tr("Controller Shortcut Overlay Bar", "Barra de Atajos del Mando"),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = com.example.util.tr(
                                    "Show '${activeControllerGlyphs.southGlyph} Launch • ${activeControllerGlyphs.westGlyph} Focus Mode • ${activeControllerGlyphs.northGlyph} Record • ${activeControllerGlyphs.leftBumper}/${activeControllerGlyphs.rightBumper}' legend bar when a controller is connected (enabled by default)",
                                    "Muestra la barra '${activeControllerGlyphs.southGlyph} Iniciar • ${activeControllerGlyphs.westGlyph} Modo Enfoque • ${activeControllerGlyphs.northGlyph} Grabar • ${activeControllerGlyphs.leftBumper}/${activeControllerGlyphs.rightBumper}' cuando hay un mando conectado"
                                ),
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

                    // Toggle 3: Console Controller UI Sounds (Controller Connected Only)
                    var consoleSoundsEnabled by remember {
                        mutableStateOf(com.example.util.ConsoleUiSoundManager.isConsoleUiSoundsEnabled(context))
                    }
                    var soundsFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { soundsFocused = it.isFocused }
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (soundsFocused) 2.dp else 1.dp,
                                color = if (soundsFocused) Color.White else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                val next = !consoleSoundsEnabled
                                consoleSoundsEnabled = next
                                com.example.util.ConsoleUiSoundManager.setConsoleUiSoundsEnabled(context, next)
                                if (next) com.example.util.ConsoleHapticManager.playSelectClick(context)
                            }
                            .focusable()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = com.example.util.tr(
                                    "Console Mode UI Sounds (Controller Connected Only)",
                                    "Sonidos de Interfaz en Modo Consola (Solo con Mando)"
                                ),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = com.example.util.tr(
                                    "Plays tactile console navigation ticks, button confirmation chimes, and launch chords only when a controller is connected",
                                    "Reproduce sonidos táctiles de navegación, confirmación y lanzamiento solo cuando hay un mando conectado"
                                ),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = consoleSoundsEnabled,
                            onCheckedChange = { enabled ->
                                consoleSoundsEnabled = enabled
                                com.example.util.ConsoleUiSoundManager.setConsoleUiSoundsEnabled(context, enabled)
                                if (enabled) com.example.util.ConsoleHapticManager.playSelectClick(context)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = HubBlue
                            ),
                            modifier = Modifier.testTag("toggle_console_controller_ui_sounds")
                        )
                    }
                }
            }
        }

        // 3. Unified Permissions Center Card
        item {
            CyberCard {
                SectionTitle(
                    title = com.example.util.tr("Permissions & Feature Access", "Permisos y Acceso a Funciones"),
                    subtitle = com.example.util.tr("Review what each permission grants and why features need it", "Revisa qué otorga cada permiso y por qué lo necesitan las funciones")
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
                        text = com.example.util.tr("Open Unified Permissions Page", "Abrir Página de Permisos Unificada"),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 4. Tweak Safeguard & Warnings
        item {
            CyberCard {
                SectionTitle(
                    title = com.example.util.tr("Tweak Safety Warnings", "Advertencias de Seguridad de Ajustes"),
                    subtitle = com.example.util.tr("Disclosure before applying performance tweaks", "Aviso antes de aplicar ajustes de rendimiento")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = com.example.util.tr("Show Warnings Before Activating", "Mostrar Advertencias Antes de Activar"),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = com.example.util.tr(
                                "Alerts about online game anti-cheat notices, heat buildup, and animation changes",
                                "Alertas sobre avisos antitrampas en juegos en línea, temperatura y cambios de animación"
                            ),
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

        // 4b. Google Analytics & Anonymous Telemetry Toggle (Synced with Top Gear & Firebase Analytics)
        item {
            val analyticsEnabled by com.example.data.repository.TopGearAnalyticsManager.isEnabledFlow.collectAsState()
            androidx.compose.runtime.LaunchedEffect(Unit) {
                com.example.data.repository.TopGearAnalyticsManager.isAnalyticsEnabled(context)
            }
            CyberCard {
                SectionTitle(
                    title = com.example.util.tr("Google Analytics & Usage Telemetry", "Google Analytics y Telemetría de Uso"),
                    subtitle = com.example.util.tr(
                        "Anonymous feature usage, session performance, and Top Gear catalog analytics",
                        "Uso anónimo de funciones, rendimiento de sesión y estadísticas de Top Gear"
                    ),
                    badge = if (analyticsEnabled) com.example.util.tr("ACTIVE", "ACTIVO") else com.example.util.tr("PAUSED", "PAUSADO"),
                    badgeColor = if (analyticsEnabled) LaunchGreen else MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = com.example.util.tr(
                                "Share Anonymous App & Feature Analytics",
                                "Compartir Analíticas Anónimas de la App"
                            ),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = com.example.util.tr(
                                "Logs anonymous screen views, focus sessions, recording presets, controller connections, and Top Gear taps via Google Analytics",
                                "Registra vistas de pantalla, sesiones de enfoque, grabaciones, mandos y toques en Top Gear mediante Google Analytics"
                            ),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }

                    Switch(
                        checked = analyticsEnabled,
                        onCheckedChange = { enabled ->
                            com.example.data.repository.TopGearAnalyticsManager.setAnalyticsEnabled(context, enabled)
                            com.example.util.ConsoleHapticManager.playSelectClick(context)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = LaunchGreen
                        ),
                        modifier = Modifier.testTag("settings_google_analytics_switch")
                    )
                }
            }
        }

        // 4c. Unity Ads Privacy & Consent Preferences
        item {
            val isPersonalizedAds by com.example.util.UnityAdsManager.isPersonalizedAdsEnabled.collectAsState()
            CyberCard {
                SectionTitle(
                    title = com.example.util.tr("Ad Privacy & Consent", "Privacidad y Consentimiento de Anuncios"),
                    subtitle = com.example.util.tr(
                        "Manage Unity Ads personalization and Advertising ID consent preferences",
                        "Administra la personalización de anuncios de Unity Ads y tu ID de publicidad"
                    ),
                    badge = if (isPersonalizedAds) {
                        com.example.util.tr("PERSONALIZED", "PERSONALIZADO")
                    } else {
                        com.example.util.tr("LIMITED", "LIMITADO")
                    },
                    badgeColor = if (isPersonalizedAds) LaunchGreen else SolarGold
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (isPersonalizedAds) {
                        com.example.util.tr(
                            "Personalized gaming ads enabled via Unity Ads privacy consent.",
                            "Anuncios de juegos personalizados activados mediante tu consentimiento de Unity Ads."
                        )
                    } else {
                        com.example.util.tr(
                            "Non-personalized ads only enabled via Unity Ads privacy consent.",
                            "Solo anuncios no personalizados activados mediante tu consentimiento de Unity Ads."
                        )
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        com.example.util.ConsoleHapticManager.playSelectClick(context)
                        com.example.util.UnityAdsManager.openPrivacyConsentDialog()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_manage_ad_privacy_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HubBlue.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.Default.PrivacyTip,
                        contentDescription = null,
                        tint = HubBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = com.example.util.tr("Manage Ad Privacy Preferences", "Administrar Privacidad de Anuncios"),
                        fontWeight = FontWeight.Bold,
                        color = HubBlue
                    )
                }
            }
        }

        // 5. G Max Gaming Hub Version 1.0 Launch Badge
        item {
            CyberCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_app_version_card"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "G Max Gaming Hub",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = com.example.util.tr(
                                "Automatic Language Sync • Device Locale Active",
                                "Sincronización Automática de Idioma • Idioma del Dispositivo Activo"
                            ),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(LaunchGreen.copy(alpha = 0.16f))
                            .border(1.dp, LaunchGreen.copy(alpha = 0.45f), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = com.example.util.tr("Version 1.0", "Versión 1.0"),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = LaunchGreen
                            ),
                            maxLines = 1,
                            softWrap = false
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
