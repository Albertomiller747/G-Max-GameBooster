package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import com.example.model.ControllerGlyphMap
import com.example.model.ControllerLayoutMode
import com.example.model.ControllerLayoutResolver
import com.example.model.ControllerOverlayConfig
import com.example.ui.theme.HubBlue
import com.example.ui.theme.LaunchGreen
import com.example.ui.theme.RecorderCoral
import com.example.util.ConsoleHapticManager
import com.example.util.ControllerHealthState
import com.example.util.SystemSettingsHelper
import kotlin.math.roundToInt

/**
 * Controller Layout Selector Card (PlayStation, Xbox, Nintendo Switch, Auto-Detect).
 * Updates button prompts, legend bars, startup screen, and overlay layout across the entire app.
 */
@Composable
fun ControllerLayoutSelectorCard(
    selectedMode: ControllerLayoutMode,
    resolvedGlyphs: ControllerGlyphMap,
    onSelectMode: (ControllerLayoutMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    CyberCard(modifier = modifier) {
        SectionTitle(
            title = com.example.util.tr("Controller Button Layout", "Diseño de Botones del Mando"),
            subtitle = com.example.util.tr(
                "Choose PlayStation, Xbox, or Nintendo Switch so every button prompt matches your controller",
                "Elige PlayStation, Xbox o Nintendo Switch para que cada botón coincida con tu mando"
            ),
            badge = resolvedGlyphs.brandTitle.uppercase(),
            badgeColor = HubBlue
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Live Active Button Legend Preview Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0A1424))
                .border(1.dp, HubBlue.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = com.example.util.tr(
                        "Active Layout: ${resolvedGlyphs.brandTitle}",
                        "Diseño Activo: ${resolvedGlyphs.brandTitle}"
                    ),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "${resolvedGlyphs.leftBumper}/${resolvedGlyphs.rightBumper} • ${resolvedGlyphs.leftTrigger}/${resolvedGlyphs.rightTrigger} • ${resolvedGlyphs.selectLabel}/${resolvedGlyphs.startLabel} • ${resolvedGlyphs.homePromptName}",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FaceGlyphCircleBadge(resolvedGlyphs.southGlyph, Color(resolvedGlyphs.southColorArgb))
                FaceGlyphCircleBadge(resolvedGlyphs.eastGlyph, Color(resolvedGlyphs.eastColorArgb))
                FaceGlyphCircleBadge(resolvedGlyphs.westGlyph, Color(resolvedGlyphs.westColorArgb))
                FaceGlyphCircleBadge(resolvedGlyphs.northGlyph, Color(resolvedGlyphs.northColorArgb))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ControllerLayoutMode.values().forEach { mode ->
                val isSelected = selectedMode == mode
                var isFocused by remember { mutableStateOf(false) }
                val modePreviewGlyphs = remember(mode, resolvedGlyphs) {
                    when (mode) {
                        ControllerLayoutMode.PLAYSTATION -> ControllerLayoutResolver.playStationGlyphs
                        ControllerLayoutMode.XBOX -> ControllerLayoutResolver.xboxGlyphs
                        ControllerLayoutMode.NINTENDO -> ControllerLayoutResolver.nintendoGlyphs
                        ControllerLayoutMode.AUTO -> resolvedGlyphs
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused }
                        .clickable {
                            ConsoleHapticManager.playSelectClick(context)
                            onSelectMode(mode)
                        }
                        .focusable()
                        .testTag("controller_layout_option_${mode.name.lowercase()}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            HubBlue.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        }
                    ),
                    border = BorderStroke(
                        width = if (isFocused) 2.5.dp else if (isSelected) 2.dp else 1.dp,
                        color = when {
                            isFocused -> Color.White
                            isSelected -> HubBlue
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) HubBlue else MaterialTheme.colorScheme.surface
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SportsEsports,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = mode.title,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    text = mode.subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${modePreviewGlyphs.southGlyph} ${modePreviewGlyphs.eastGlyph} ${modePreviewGlyphs.westGlyph} ${modePreviewGlyphs.northGlyph}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSelected) HubBlue else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(HubBlue),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FaceGlyphCircleBadge(
    glyph: String,
    accentColor: Color
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Color(0xFF1A2234))
            .border(1.5.dp, accentColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = glyph,
            color = accentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black
        )
    }
}

/**
 * Real-Time Controller Input Screen Overlay Card:
 * - Toggle floating on-screen mini controller HUD (works over games & in-app)
 * - Live real-time controller visualizer preview (highlights pressed buttons, D-pad, triggers, and sticks)
 * - Auto-Scaling toggle + manual Size (55% - 165%) and Opacity (25% - 100%) sliders
 * - Lock Touch Pass-Through mode & Highlight Color picker
 */
@Composable
fun ControllerScreenOverlayCard(
    config: ControllerOverlayConfig,
    controllerState: ControllerHealthState,
    glyphs: ControllerGlyphMap,
    selectedLayoutMode: ControllerLayoutMode = ControllerLayoutMode.AUTO,
    onSelectLayoutMode: (ControllerLayoutMode) -> Unit = {},
    onSelectStickTrackingMode: (com.example.model.StickTrackingMode) -> Unit = {},
    onToggleEnabled: (Boolean) -> Unit,
    onToggleAutoScale: (Boolean) -> Unit,
    onScaleFactorChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onToggleLockTouch: (Boolean) -> Unit,
    onToggleDisableSpeakerAudio: (Boolean) -> Unit = {},
    onHighlightColorChange: (Long) -> Unit,
    onTriggerDemoPulse: () -> Unit,
    onSimulateButtonToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var canDrawOverlays by remember { mutableStateOf(SystemSettingsHelper.canDrawOverlays(context)) }
    val unlockUntilMs by com.example.util.UnityAdsManager.proControllerUnlockUntilMs.collectAsState()
    val isProUnlocked = unlockUntilMs > System.currentTimeMillis()
    val remainingUnlockHours = com.example.util.UnityAdsManager.remainingUnlockHours()
    var isControllerConnected by remember(controllerState) {
        mutableStateOf(
            controllerState.controllerId >= 0 ||
                controllerState.isWiredConnection ||
                com.example.util.ControllerHealthManager.isAnyControllerConnected(context)
        )
    }
    var showWiredWarningPopup by remember { mutableStateOf(false) }
    var showJoystickNoticePopup by remember { mutableStateOf(false) }
    var wasOverlayEnabled by remember { mutableStateOf(config.isEnabled) }

    androidx.compose.runtime.LaunchedEffect(config.isEnabled) {
        if (config.isEnabled && !wasOverlayEnabled) {
            showJoystickNoticePopup = true
        }
        wasOverlayEnabled = config.isEnabled
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                canDrawOverlays = SystemSettingsHelper.canDrawOverlays(context)
                isControllerConnected =
                    controllerState.controllerId >= 0 ||
                        controllerState.isWiredConnection ||
                        com.example.util.ControllerHealthManager.isAnyControllerConnected(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showWiredWarningPopup) {
        WiredControllerRequiredDialog(
            isBluetoothControllerConnected = controllerState.controllerId >= 0 && !controllerState.isWiredConnection,
            controllerName = controllerState.activeControllerName,
            onDismiss = { showWiredWarningPopup = false }
        )
    }

    if (showJoystickNoticePopup) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showJoystickNoticePopup = false },
            title = {
                Text(
                    text = com.example.util.tr("Controller Overlay Joystick Notice", "Aviso del Joystick en la Superposición"),
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Text(
                    text = com.example.util.tr(
                        "Note: On some controllers, the analog joystick on the floating overlay might not appear to be moving while you are outside the hub, even though your joystick input continues to work normally in your game.",
                        "Nota: En algunos mandos, es posible que el joystick en la superposición flotante no parezca moverse fuera del hub, aunque el movimiento del joystick seguirá funcionando normalmente dentro del juego."
                    ),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showJoystickNoticePopup = false },
                    colors = ButtonDefaults.buttonColors(containerColor = HubBlue, contentColor = Color.White)
                ) {
                    Text(
                        text = com.example.util.tr("Got It", "Entendido"),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            modifier = Modifier.testTag("controller_overlay_joystick_notice_dialog")
        )
    }

    val highlightPresets = remember {
        listOf(
            "Crimson Red" to 0xFFE5252AL,
            "Neon Cyan" to 0xFF00E5FFL,
            "Launch Green" to 0xFF10B981L,
            "Electric Purple" to 0xFFA855F7L,
            "Solar Gold" to 0xFFF59E0BL
        )
    }

    CyberCard(
        borderColor = if (config.isEnabled) Color(config.highlightColorArgb) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SectionTitle(
                    title = com.example.util.tr("Controller Input Screen Overlay (Bluetooth & USB)", "Superposición de Mando en Pantalla (Bluetooth y USB)"),
                    subtitle = com.example.util.tr(
                        "Displays a live mini controller on screen when your controller is connected via Bluetooth or wired USB",
                        "Muestra un mini mando en vivo en pantalla cuando tu mando está conectado por Bluetooth o cable USB"
                    ),
                    badge = if (config.isEnabled && isControllerConnected) {
                        if (controllerState.isWiredConnection) {
                            com.example.util.tr("WIRED HUD ON", "HUD USB ACTIVO")
                        } else {
                            com.example.util.tr("BLUETOOTH HUD ON", "HUD BT ACTIVO")
                        }
                    } else if (isProUnlocked) {
                        com.example.util.tr("UNLOCKED (${remainingUnlockHours}H)", "DESBLOQUEADO (${remainingUnlockHours}H)")
                    } else {
                        com.example.util.tr("BT & USB READY", "BT Y USB LISTO")
                    },
                    badgeColor = if (config.isEnabled && isControllerConnected || isProUnlocked) LaunchGreen else HubBlue
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = config.isEnabled && isControllerConnected,
                onCheckedChange = { enable ->
                    ConsoleHapticManager.playSelectClick(context)
                    val currentlyConnected = controllerState.controllerId >= 0 ||
                        controllerState.isWiredConnection ||
                        com.example.util.ControllerHealthManager.isAnyControllerConnected(context)
                    isControllerConnected = currentlyConnected
                    if (enable && !currentlyConnected) {
                        showWiredWarningPopup = true
                        onToggleEnabled(false)
                        return@Switch
                    }
                    if (enable && !SystemSettingsHelper.canDrawOverlays(context)) {
                        SystemSettingsHelper.openOverlayPermissionSettings(context)
                    }
                    onToggleEnabled(enable)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = LaunchGreen
                ),
                modifier = Modifier.testTag("toggle_controller_screen_overlay")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bluetooth & Wired USB Connection Status Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isControllerConnected) LaunchGreen.copy(alpha = 0.14f)
                    else Color(0xFFF59E0B).copy(alpha = 0.14f)
                )
                .border(
                    1.dp,
                    if (isControllerConnected) LaunchGreen.copy(alpha = 0.55f)
                    else Color(0xFFF59E0B).copy(alpha = 0.6f),
                    RoundedCornerShape(10.dp)
                )
                .clickable {
                    if (!isControllerConnected) {
                        showWiredWarningPopup = true
                    }
                }
                .padding(horizontal = 12.dp, vertical = 9.dp)
                .testTag("wired_controller_status_banner"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when {
                    controllerState.isWiredConnection -> Icons.Default.Usb
                    isControllerConnected -> Icons.Default.SportsEsports
                    else -> Icons.Default.Warning
                },
                contentDescription = null,
                tint = if (isControllerConnected) LaunchGreen else Color(0xFFFBBF24),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (controllerState.isWiredConnection) {
                        com.example.util.tr(
                            "Wired USB Controller Connected (${controllerState.activeControllerName})",
                            "Mando por Cable USB Conectado (${controllerState.activeControllerName})"
                        )
                    } else if (isControllerConnected) {
                        com.example.util.tr(
                            "Bluetooth / Wireless Controller Connected (${controllerState.activeControllerName})",
                            "Mando Bluetooth / Inalámbrico Conectado (${controllerState.activeControllerName})"
                        )
                    } else {
                        com.example.util.tr(
                            "Connect a Bluetooth or Wired USB Controller for Live Overlay",
                            "Conecta un Mando Bluetooth o por Cable USB para la Superposición"
                        )
                    },
                    color = if (isControllerConnected) LaunchGreen else Color(0xFFFBBF24),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = if (controllerState.isWiredConnection) {
                        com.example.util.tr(
                            "Direct wired USB connection active • Full real-time overlay supported.",
                            "Conexión directa por cable USB activa • Superposición en tiempo real disponible."
                        )
                    } else if (isControllerConnected) {
                        com.example.util.tr(
                            "Bluetooth wireless mode active • Full real-time overlay & diagnostics enabled.",
                            "Modo inalámbrico Bluetooth activo • Superposición y diagnósticos en tiempo real activados."
                        )
                    } else {
                        com.example.util.tr(
                            "Pair a controller via Bluetooth or plug in via USB-C / OTG to enable the live screen overlay.",
                            "Vincula un mando por Bluetooth o conéctalo por USB-C / OTG para activar la superposición en vivo."
                        )
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Controller Input Accuracy Note
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .border(1.dp, HubBlue.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp)
                .testTag("controller_input_accuracy_note"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFF59E0B),
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = com.example.util.tr(
                    "Note: Some Controllers input might not be as Accurate as others",
                    "Nota: Algunos mandos pueden no tener una entrada tan precisa como otros"
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (!canDrawOverlays) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = { SystemSettingsHelper.openOverlayPermissionSettings(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("grant_controller_overlay_permission_button"),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFF59E0B))
            ) {
                Text(
                    text = com.example.util.tr(
                        "Grant 'Display Over Other Apps' for Floating Game Overlay",
                        "Conceder 'Mostrar sobre otras apps' para la superposición"
                    ),
                    color = Color(0xFFF59E0B),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Overlay Controller Brand Selector Strip (PlayStation, Xbox, Nintendo Switch, Auto)
        Text(
            text = com.example.util.tr(
                "Overlay Controller Style (PlayStation • Xbox • Nintendo Switch)",
                "Estilo de Mando de Superposición (PlayStation • Xbox • Nintendo Switch)"
            ),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val brandOptions = listOf(
                ControllerLayoutMode.PLAYSTATION to "PlayStation",
                ControllerLayoutMode.XBOX to "Xbox",
                ControllerLayoutMode.NINTENDO to "Nintendo",
                ControllerLayoutMode.AUTO to com.example.util.tr("Auto", "Auto")
            )
            brandOptions.forEach { (mode, shortName) ->
                val isSelected = selectedLayoutMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) HubBlue.copy(alpha = 0.22f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .border(
                            1.5.dp,
                            if (isSelected) HubBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            ConsoleHapticManager.playSelectClick(context)
                            onSelectLayoutMode(mode)
                        }
                        .padding(vertical = 8.dp)
                        .testTag("overlay_brand_chip_${mode.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = shortName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSelected) HubBlue else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // External D-Pad & Joystick Tracking Mode Selector
        Text(
            text = com.example.util.tr(
                "External DPad & Joystick Tracking Mode",
                "Modo de Seguimiento de DPad y Joysticks Externo"
            ),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val trackingOptions = listOf(
                Triple(
                    com.example.model.StickTrackingMode.SMART_HYBRID,
                    com.example.util.tr("Smart Pass Through", "Paso Inteligente"),
                    com.example.util.tr("Home Screen 120Hz + Game Direct", "Inicio 120Hz + Juego Directo")
                ),
                Triple(
                    com.example.model.StickTrackingMode.FULL_120HZ_LOCK,
                    com.example.util.tr("Full 120Hz Stick Lock", "Bloqueo Total 120Hz"),
                    com.example.util.tr("Max DPad & Stick Overlay Everywhere", "DPad y Joysticks 120Hz Siempre")
                )
            )
            trackingOptions.forEach { (mode, title, subtitle) ->
                val isSelected = config.stickTrackingMode == mode
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) LaunchGreen.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                        .border(
                            1.5.dp,
                            if (isSelected) LaunchGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            ConsoleHapticManager.playSelectClick(context)
                            onSelectStickTrackingMode(mode)
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("overlay_stick_mode_${mode.name.lowercase()}"),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = title,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSelected) LaunchGreen else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        fontSize = 9.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Live Real-Time Controller Visualizer Preview Box (Transparent background — no gray box)
        val previewScale = (config.scaleFactor.coerceIn(0.55f, 1.65f)).coerceIn(0.65f, 1.25f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(205.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Transparent)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            ControllerInputVisualizerCanvas(
                state = controllerState,
                glyphs = glyphs,
                highlightColorArgb = config.highlightColorArgb,
                opacity = config.opacity,
                modifier = Modifier
                    .width((210f * previewScale).dp)
                    .height((142f * previewScale).dp)
                    .testTag("controller_overlay_live_preview")
            )

            // Top-left active layout & scale badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xAA0A1220))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "${glyphs.brandTitle} • ${(config.scaleFactor * 100).roundToInt()}% ${if (config.autoScale) com.example.util.tr("(Smart Scale)", "(Escala Automática)") else com.example.util.tr("(Manual)", "(Manual)")}",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Bottom-right pressed buttons readout
            val activeBtnsText = if (controllerState.pressedButtons.isEmpty()) {
                com.example.util.tr(
                    "Press any controller button or tap chips below",
                    "Presiona cualquier botón del mando o toca abajo"
                )
            } else {
                com.example.util.tr(
                    "ACTIVE: ${controllerState.pressedButtons.joinToString(" + ")}",
                    "ACTIVO: ${controllerState.pressedButtons.joinToString(" + ")}"
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xAA0A1220))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = activeBtnsText,
                    color = if (controllerState.pressedButtons.isEmpty()) Color(0xFFCBD5E1) else Color(0xFF00E5FF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Interactive Test Buttons & Demo Pulse Row so users can test button & D-Pad/Stick movement right on screen
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    ConsoleHapticManager.playSelectClick(context)
                    onTriggerDemoPulse()
                },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HubBlue,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .height(32.dp)
                    .testTag("controller_overlay_demo_pulse_button")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(com.example.util.tr("Test Input Pulse", "Probar Pulso de Entrada"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            val interactiveTestButtons = listOf(
                "A" to glyphs.southGlyph,
                "B" to glyphs.eastGlyph,
                "X" to glyphs.westGlyph,
                "Y" to glyphs.northGlyph,
                "L1" to glyphs.leftBumper,
                "R1" to glyphs.rightBumper,
                "L2" to glyphs.leftTrigger,
                "R2" to glyphs.rightTrigger,
                "DPAD_UP" to "D-Up",
                "DPAD_DOWN" to "D-Down",
                "DPAD_LEFT" to "D-Left",
                "DPAD_RIGHT" to "D-Right",
                "MODE" to glyphs.homeGlyph
            )

            interactiveTestButtons.forEach { (key, label) ->
                val isPressed = controllerState.pressedButtons.contains(key)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isPressed) Color(config.highlightColorArgb)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(
                            1.dp,
                            if (isPressed) Color.White else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            RoundedCornerShape(50)
                        )
                        .clickable {
                            ConsoleHapticManager.playFocusTick(context)
                            onSimulateButtonToggle(key)
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isPressed) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 1. Auto-Scaling Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .clickable {
                    ConsoleHapticManager.playSelectClick(context)
                    onToggleAutoScale(!config.autoScale)
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = com.example.util.tr(
                        "Smart Scaling (Screen & Orientation Aware)",
                        "Escala Inteligente (Adaptada a Pantalla y Orientación)"
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Text(
                    text = com.example.util.tr(
                        "Automatically scales the controller overlay proportionally for Portrait, Landscape, and tablet screens",
                        "Ajusta automáticamente la superposición para modo Vertical, Horizontal y tablets"
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = config.autoScale,
                onCheckedChange = {
                    ConsoleHapticManager.playSelectClick(context)
                    onToggleAutoScale(it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = HubBlue
                ),
                modifier = Modifier.testTag("toggle_controller_overlay_autoscale")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Manual Size Adjustment Slider (55% - 165%)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = com.example.util.tr("Overlay Size Adjustment", "Ajuste de Tamaño de Superposición"),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${(config.scaleFactor * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = HubBlue
                    )
                )
                if (kotlin.math.abs(config.scaleFactor - 1.0f) > 0.03f) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = { onScaleFactorChange(1.0f) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset size", modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(com.example.util.tr("Reset", "Restablecer"), fontSize = 10.sp)
                    }
                }
            }
        }

        Slider(
            value = config.scaleFactor,
            onValueChange = onScaleFactorChange,
            valueRange = 0.55f..1.65f,
            colors = SliderDefaults.colors(
                thumbColor = HubBlue,
                activeTrackColor = HubBlue
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("controller_overlay_size_slider")
        )

        // Quick Size Preset Chips (Small 75%, Standard 100%, Large 125%, XL 150%)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val sizePresets = listOf(
                com.example.util.tr("Compact 75%", "Compacto 75%") to 0.75f,
                com.example.util.tr("Standard 100%", "Estándar 100%") to 1.0f,
                com.example.util.tr("Large 125%", "Grande 125%") to 1.25f,
                "XL 150%" to 1.50f
            )
            sizePresets.forEach { (label, factor) ->
                val isSelected = kotlin.math.abs(config.scaleFactor - factor) < 0.05f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) HubBlue.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .border(
                            1.dp,
                            if (isSelected) HubBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            ConsoleHapticManager.playSelectClick(context)
                            onScaleFactorChange(factor)
                        }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) HubBlue else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Opacity Adjustment Slider (25% - 100%)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = com.example.util.tr("Overlay Opacity", "Opacidad de Superposición"),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Text(
                text = "${(config.opacity * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = LaunchGreen
                )
            )
        }

        Slider(
            value = config.opacity,
            onValueChange = onOpacityChange,
            valueRange = 0.25f..1.0f,
            colors = SliderDefaults.colors(
                thumbColor = LaunchGreen,
                activeTrackColor = LaunchGreen
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("controller_overlay_opacity_slider")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 4. Button Highlight Color Picker
        Text(
            text = com.example.util.tr(
                "Active Button & Stick Highlight Color",
                "Color de Iluminación de Botones y Palancas"
            ),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            highlightPresets.forEach { (name, colorArgb) ->
                val isSelected = config.highlightColorArgb == colorArgb
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(colorArgb))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) Color.White else Color.Black.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                        .clickable {
                            ConsoleHapticManager.playSelectClick(context)
                            onHighlightColorChange(colorArgb)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = name,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 5. Lock Touch Pass-Through Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .clickable {
                    ConsoleHapticManager.playSelectClick(context)
                    onToggleLockTouch(!config.lockTouchPassThrough)
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (config.lockTouchPassThrough) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = if (config.lockTouchPassThrough) RecorderCoral else HubBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (config.lockTouchPassThrough) {
                            com.example.util.tr(
                                "Position Locked (100% Game Touch Pass Through)",
                                "Posición Bloqueada (100% Paso Táctil al Juego)"
                            )
                        } else {
                            com.example.util.tr(
                                "Unlocked (Drag Controller Overlay Anywhere on Screen)",
                                "Desbloqueado (Arrastra la Superposición en Pantalla)"
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Text(
                        text = com.example.util.tr(
                            "Drag the mini controller to your preferred corner, then lock it so touches pass straight through to your game",
                            "Arrastra el mini mando a tu esquina preferida y bloquéalo para que los toques pasen directo al juego"
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = config.lockTouchPassThrough,
                onCheckedChange = {
                    ConsoleHapticManager.playSelectClick(context)
                    onToggleLockTouch(it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = RecorderCoral
                ),
                modifier = Modifier.testTag("toggle_controller_overlay_lock_touch")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 6. Disable Phone Sounds Through Controller Speakers Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .clickable {
                    ConsoleHapticManager.playSelectClick(context)
                    onToggleDisableSpeakerAudio(!config.disableControllerSpeakerAudio)
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (config.disableControllerSpeakerAudio) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = if (config.disableControllerSpeakerAudio) LaunchGreen else HubBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = com.example.util.tr(
                            "Disable Phone Sounds Through Controller Speakers",
                            "Desactivar Sonidos del Teléfono en Altavoz del Mando"
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Text(
                        text = com.example.util.tr(
                            "Prevents USB controllers (DualSense / DualShock) from hijacking game audio and keeps sound routed to your phone speakers or headphones",
                            "Evita que los mandos USB desvíen el audio del juego y mantiene el sonido en los altavoces o auriculares del teléfono"
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = config.disableControllerSpeakerAudio,
                onCheckedChange = {
                    ConsoleHapticManager.playSelectClick(context)
                    onToggleDisableSpeakerAudio(it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = LaunchGreen
                ),
                modifier = Modifier.testTag("toggle_controller_overlay_disable_speaker")
            )
        }
    }
}

/**
 * Popup dialog informing the user that the Controller Input Screen Overlay only works when the
 * controller is connected directly to the device via a wired USB / USB-C cable, and that
 * wireless / Bluetooth connections may not work well while playing games.
 */
@Composable
fun WiredControllerRequiredDialog(
    isBluetoothControllerConnected: Boolean,
    controllerName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0E1726),
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF59E0B).copy(alpha = 0.18f))
                    .border(1.5.dp, Color(0xFFF59E0B), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Usb,
                    contentDescription = "Wired USB Connection Required",
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Text(
                text = com.example.util.tr(
                    "Connect a Bluetooth or Wired USB Controller",
                    "Conecta un Mando Bluetooth o por Cable USB"
                ),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.testTag("wired_controller_required_dialog_title")
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = com.example.util.tr(
                        "To activate the floating Controller Input Screen Overlay, please pair a wireless controller via Bluetooth or connect your gamepad using a USB-C / OTG cable.",
                        "Para activar la Superposición de Mando en Pantalla, vincula un mando inalámbrico por Bluetooth o conecta tu mando mediante un cable USB-C / OTG."
                    ),
                    color = Color(0xFFE2E8F0),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (isBluetoothControllerConnected) {
                                com.example.util.tr(
                                    "Current Status: Wireless / Bluetooth ($controllerName)",
                                    "Estado Actual: Inalámbrico / Bluetooth ($controllerName)"
                                )
                            } else {
                                com.example.util.tr(
                                    "Current Status: No Controller Detected",
                                    "Estado Actual: Ningún Mando Detectado"
                                )
                            },
                            color = Color(0xFFFBBF24),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = com.example.util.tr(
                                "Turn on your Bluetooth controller or plug in via USB-C / OTG, then toggle on the Controller Overlay.",
                                "Enciende tu mando Bluetooth o conéctalo por USB-C / OTG y luego activa la Superposición de Mando."
                            ),
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    ConsoleHapticManager.playSelectClick(context)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = HubBlue,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wired_controller_required_dialog_confirm")
            ) {
                Text(
                    text = com.example.util.tr("Got It", "Entendido"),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }
        }
    )
}

