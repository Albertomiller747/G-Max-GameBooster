package com.example.ui.screens.extras

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MatchRecap
import com.example.ui.components.CyberCard
import com.example.ui.components.SectionTitle
import com.example.ui.screens.ExtrasTelemetryRow
import com.example.ui.theme.ElectricCrimson
import com.example.ui.theme.HubBlue
import com.example.ui.theme.LaunchGreen
import com.example.ui.theme.SolarGold
import com.example.util.SystemSettingsHelper
import com.example.viewmodel.GameBoosterViewModel
import java.util.Locale

@Composable
fun PerformanceTab(
    viewModel: GameBoosterViewModel,
    modifier: Modifier = Modifier,
    onOpenSquadPassTab: () -> Unit = {}
) {
    val context = LocalContext.current
    val overlayConfig by viewModel.overlayConfig.collectAsState()
    val matchRecap by viewModel.matchRecap.collectAsState()
    val isRecording by viewModel.isMatchRecording.collectAsState()
    val thermalGuardState by viewModel.thermalGuardState.collectAsState()
    val squadPassState by viewModel.squadPassState.collectAsState()
    val userUsername by viewModel.userUsername.collectAsState()
    val canDrawOverlay = remember(context) { SystemSettingsHelper.canDrawOverlays(context) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("performance_tab_container"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Thermal Throttle Guard Card
        item {
            CyberCard(
                borderColor = if (thermalGuardState.isThrottling) ElectricCrimson else HubBlue.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        SectionTitle(
                            title = com.example.util.tr("Thermal Throttle Guard", "Protección de Estrangulamiento Térmico"),
                            subtitle = com.example.util.tr("Real kernel thermal state & SoC throttle defense", "Estado térmico real del kernel y defensa del SoC")
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (thermalGuardState.isThrottling) ElectricCrimson.copy(alpha = 0.16f)
                                else LaunchGreen.copy(alpha = 0.15f)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (thermalGuardState.isThrottling) {
                                com.example.util.tr("THROTTLING DETECTED", "ESTRANGULAMIENTO DETECTADO")
                            } else {
                                com.example.util.tr("OPTIMAL COOLING", "ENFRIAMIENTO ÓPTIMO")
                            },
                            color = if (thermalGuardState.isThrottling) ElectricCrimson else LaunchGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                thermalGuardState.activeThrottleAlert?.let { alertMsg ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ElectricCrimson.copy(alpha = 0.14f))
                            .border(1.dp, ElectricCrimson, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                            .testTag("thermal_throttle_alert_banner")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = ElectricCrimson,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            val activeLang = com.example.util.currentAppLanguage()
                            Text(
                                text = com.example.util.AppStrings.autoTranslateDynamic(alertMsg, activeLang),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.dismissThermalAlert() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = com.example.util.tr("Dismiss", "Cerrar"),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                val activeThermalLang = com.example.util.currentAppLanguage()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThermalMetricBox(
                        modifier = Modifier.weight(1f),
                        label = com.example.util.tr("HARDWARE TEMP", "TEMP. HARDWARE"),
                        value = String.format(Locale.US, "%.1f°C", thermalGuardState.liveTempC),
                        accent = if (thermalGuardState.liveTempC > 40f) ElectricCrimson else SolarGold
                    )
                    ThermalMetricBox(
                        modifier = Modifier.weight(1.2f),
                        label = com.example.util.tr("OS STATUS", "ESTADO DEL SISTEMA"),
                        value = com.example.util.AppStrings.autoTranslateDynamic(thermalGuardState.thermalStatusName, activeThermalLang),
                        accent = if (thermalGuardState.isThrottling) ElectricCrimson else HubBlue
                    )
                    ThermalMetricBox(
                        modifier = Modifier.weight(1f),
                        label = com.example.util.tr("HEADROOM", "MARGEN TÉRMICO"),
                        value = String.format(Locale.US, "%.2f", thermalGuardState.thermalHeadroom),
                        accent = if (thermalGuardState.thermalHeadroom < 0.6f) ElectricCrimson else LaunchGreen
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = com.example.util.tr(
                        "Monitors PowerManager kernel thermal listeners (API 29+) and correlates thermal events with in-game frame pacing to protect SoC silicon from sustained heat degradation.",
                        "Monitorea los sensores térmicos del kernel (API 29+) y correlaciona eventos térmicos con el ritmo de cuadros para proteger el procesador del calor continuo."
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                )
            }
        }

        // 2. Post-Match Performance Recap Card
        item {
            CyberCard(borderColor = HubBlue.copy(alpha = 0.5f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        com.example.ui.components.GMaxBrandIcon(
                            size = 38.dp,
                            cornerRadius = 10.dp,
                            showGlowBorder = true,
                            glowColor = Color(0xFF00E5FF),
                            contentDescription = "G Max Gaming Hub"
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = com.example.util.tr("G MAX GAMING HUB • OFFICIAL RECAP", "G MAX GAMING HUB • RESUMEN OFICIAL"),
                                color = Color(0xFF00E5FF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            )
                            SectionTitle(
                                title = com.example.util.tr("Session Performance Recap", "Resumen de Rendimiento de Sesión"),
                                subtitle = com.example.util.tr("Real FPS, 1% lows, stutters, and thermal curves", "FPS reales, caídas del 1%, tirones y curvas térmicas")
                            )
                        }
                    }
                    if (isRecording) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(LaunchGreen.copy(alpha = 0.16f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = com.example.util.tr("● REC ACTIVE", "● GRABANDO"),
                                color = LaunchGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MatchStatBox(
                        modifier = Modifier.weight(1f),
                        label = com.example.util.tr("AVG FPS", "PROM. CUADROS"),
                        value = "${matchRecap.averageFps}",
                        accent = HubBlue
                    )
                    MatchStatBox(
                        modifier = Modifier.weight(1f),
                        label = com.example.util.tr("1% LOWS", "MÍNIMO 1%"),
                        value = "${matchRecap.low1PercentFps}",
                        accent = if (matchRecap.low1PercentFps >= 45f) LaunchGreen else SolarGold
                    )
                    MatchStatBox(
                        modifier = Modifier.weight(1f),
                        label = com.example.util.tr("STABILITY", "ESTABILIDAD"),
                        value = "${matchRecap.stabilityScore}%",
                        accent = Color(0xFFA855F7)
                    )
                    MatchStatBox(
                        modifier = Modifier.weight(1f),
                        label = com.example.util.tr("PEAK TEMP", "TEMP. MÁX"),
                        value = String.format(Locale.US, "%.1f°C", matchRecap.maxTempC),
                        accent = if (matchRecap.maxTempC > 41f) ElectricCrimson else SolarGold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                val activeRecapLang = com.example.util.currentAppLanguage()
                val localizedGameTitle = com.example.util.AppStrings.autoTranslateDynamic(matchRecap.gameTitle, activeRecapLang)
                Text(
                    text = com.example.util.tr(
                        "Performance Dynamics ($localizedGameTitle):",
                        "Dinámica de Rendimiento ($localizedGameTitle):"
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    MatchRecapCurvesCanvas(
                        recap = matchRecap,
                        gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = com.example.util.tr(
                            "Battery Drain: -${matchRecap.batteryDrained}% (${matchRecap.startBatteryPct}% → ${matchRecap.endBatteryPct}%)",
                            "Consumo de Batería: -${matchRecap.batteryDrained}% (${matchRecap.startBatteryPct}% → ${matchRecap.endBatteryPct}%)"
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    )
                    Text(
                        text = com.example.util.tr(
                            "Stutters: ${matchRecap.stutterCount} drops",
                            "Tirones: ${matchRecap.stutterCount} caídas"
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (matchRecap.stutterCount > 5) ElectricCrimson else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isRecording) {
                        Button(
                            onClick = { viewModel.finishManualMatchRecap() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("finish_match_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCrimson, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(com.example.util.tr("FINISH MATCH", "FINALIZAR PARTIDA"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.startManualMatchRecap() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("start_match_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = HubBlue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(com.example.util.tr("START RECAP", "INICIAR RESUMEN"), color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = { viewModel.shareMatchCard(context, matchRecap) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("share_match_card_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = HubBlue, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(com.example.util.tr("SHARE CARD", "COMPARTIR TARJETA"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Contextual Post-Session / Highlight Squad Invite Prompt ("Running with a duo? Send them a 14 day Pro Guest Pass...")
                com.example.ui.components.PostMatchGuestPassPromptCard(
                    squadState = squadPassState,
                    referrerUsername = userUsername.ifBlank { "SquadLeader" },
                    gameTitle = matchRecap.gameTitle,
                    onOpenSquadPassHub = {
                        viewModel.requestExtrasSubTab(2)
                        onOpenSquadPassTab()
                    }
                )
            }
        }

        // 3. Screen Overlay HUD Controls
        item {
            CyberCard(borderColor = if (overlayConfig.isEnabled) LaunchGreen else MaterialTheme.colorScheme.outline) {
                SectionTitle(
                    title = com.example.util.tr("Screen Overlay HUD Controls", "Controles del Visor en Pantalla"),
                    subtitle = com.example.util.tr("Real-time floating metrics for FPS, battery, and thermals", "Métricas flotantes en tiempo real de cuadros por segundo, batería y temperatura")
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (!canDrawOverlay) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SolarGold.copy(alpha = 0.14f))
                            .border(1.dp, SolarGold.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = SolarGold, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = com.example.util.tr(
                                    "Requires 'Display over other apps' permission to stay visible over games.",
                                    "Requiere permiso 'Mostrar sobre otras apps' para verse en los juegos."
                                ),
                                style = MaterialTheme.typography.bodySmall.copy(color = SolarGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = if (overlayConfig.isEnabled) LaunchGreen else HubBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = com.example.util.tr("Floating Screen Overlay", "Superposición Flotante en Pantalla"),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = if (overlayConfig.isEnabled) {
                                    com.example.util.tr("HUD is active and draggable on screen", "El visor está activo y se puede arrastrar")
                                } else {
                                    com.example.util.tr("Currently inactive", "Actualmente inactivo")
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (overlayConfig.isEnabled) LaunchGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    Switch(
                        checked = overlayConfig.isEnabled,
                        onCheckedChange = { viewModel.toggleOverlayEnabled(context, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = LaunchGreen,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.testTag("performance_overlay_master_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = com.example.util.tr("Overlay Telemetry Metrics:", "Métricas de Telemetría en Pantalla:"),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExtrasTelemetryRow(
                        icon = Icons.Default.Speed,
                        label = com.example.util.tr("In-Game Frame Rate (96% to 98% EFPS)", "Tasa de Cuadros en Juego (96% a 98% Precisión)"),
                        description = com.example.util.tr(
                            "6-Way Hardware Telemetry Fusion (VRR + VSYNC + GPU Swap + Game Cap + CPU/Thermal)",
                            "Fusión de telemetría de hardware de 6 vías (Frecuencia Variable + Sincronización + Tarjeta Gráfica + Procesador)"
                        ),
                        isChecked = overlayConfig.showFps,
                        onCheckedChange = { viewModel.toggleOverlayFps(context, it) },
                        accentColor = HubBlue
                    )

                    ExtrasTelemetryRow(
                        icon = Icons.Default.Thermostat,
                        label = com.example.util.tr("Hardware Battery Thermals", "Temperatura de Batería de Hardware"),
                        description = com.example.util.tr("Genuine Android battery broadcast temperature", "Temperatura real de la batería de Android"),
                        isChecked = overlayConfig.showTemp,
                        onCheckedChange = { viewModel.toggleOverlayTemp(context, it) },
                        accentColor = SolarGold
                    )

                    ExtrasTelemetryRow(
                        icon = Icons.Default.BatteryChargingFull,
                        label = com.example.util.tr("Battery Percentage & Health", "Porcentaje y Salud de Batería"),
                        description = com.example.util.tr("Genuine battery capacity and charge state", "Capacidad real y estado de carga de la batería"),
                        isChecked = overlayConfig.showBattery,
                        onCheckedChange = { viewModel.toggleOverlayBattery(context, it) },
                        accentColor = LaunchGreen
                    )

                    ExtrasTelemetryRow(
                        icon = Icons.Default.Timer,
                        label = com.example.util.tr("Overlay and Focus Mode Timer", "Temporizador de Superposición y Modo Enfoque"),
                        description = com.example.util.tr("Limits the time your software overlay and focus mode stay active on screen (1h 30m)", "Limita el tiempo que la superposición y el modo enfoque permanecen activos en pantalla (1h 30m)"),
                        isChecked = overlayConfig.showTimer,
                        onCheckedChange = { viewModel.toggleOverlayTimer(context, it) },
                        accentColor = Color(0xFFA855F7)
                    )
                }
            }
        }

        // 4. Unity Banner Card embedded in the Performance scrollable list with 32dp top buffer
        item {
            com.example.ui.components.GMaxUnityBannerCard(
                placementTag = "performance_bottom",
                topBuffer = 32.dp,
                bottomBuffer = 24.dp
            )
        }
    }
}

@Composable
private fun ThermalMetricBox(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accent: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MatchStatBox(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accent: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            )
        }
    }
}

@Composable
private fun MatchRecapCurvesCanvas(recap: MatchRecap, gridColor: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val history = recap.telemetryHistory
        if (history.isEmpty()) {
            drawLine(
                color = gridColor,
                start = Offset(0f, height / 2f),
                end = Offset(width, height / 2f),
                strokeWidth = 2f
            )
            return@Canvas
        }

        val fpsPoints = history.map { it.fps }
        val maxFps = (fpsPoints.maxOrNull() ?: 60).coerceAtLeast(60).toFloat()
        val minFps = (fpsPoints.minOrNull() ?: 30).coerceAtMost(30).toFloat()

        val maxTemp = 48f
        val minTemp = 28f

        val fpsPath = Path()
        val tempPath = Path()

        history.forEachIndexed { index, point ->
            val x = (index.toFloat() / (history.size - 1).coerceAtLeast(1)) * width

            val normFps = ((point.fps - minFps) / (maxFps - minFps)).coerceIn(0f, 1f)
            val yFps = height - (normFps * (height - 16f)) - 8f
            if (index == 0) fpsPath.moveTo(x, yFps) else fpsPath.lineTo(x, yFps)

            val normTemp = ((point.tempC - minTemp) / (maxTemp - minTemp)).coerceIn(0f, 1f)
            val yTemp = height - (normTemp * (height - 16f)) - 8f
            if (index == 0) tempPath.moveTo(x, yTemp) else tempPath.lineTo(x, yTemp)
        }

        drawLine(
            color = gridColor,
            start = Offset(0f, height * 0.25f),
            end = Offset(width, height * 0.25f),
            strokeWidth = 1f
        )
        drawLine(
            color = gridColor,
            start = Offset(0f, height * 0.75f),
            end = Offset(width, height * 0.75f),
            strokeWidth = 1f
        )

        drawPath(
            path = fpsPath,
            color = HubBlue,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        drawPath(
            path = tempPath,
            color = Color(0xFFFF9100),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
