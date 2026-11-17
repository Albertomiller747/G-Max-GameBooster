package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.InstalledApp
import com.example.service.SpeedrunOverlaySizePreset
import com.example.service.SpeedrunTimerOverlayService
import com.example.ui.theme.HubBlue
import com.example.ui.theme.LaunchGreen
import com.example.ui.theme.RecorderCoral
import com.example.util.GameAppDossier
import com.example.util.GameAppDossierHelper
import com.example.util.SystemSettingsHelper
import com.example.util.tr
import kotlin.math.roundToInt

/**
 * Rich App Info Sheet / Modal displayed when the user taps the `(i)` button on an app in the Launcher.
 * Displays:
 * 1. App Size (installed APK + split APKs size)
 * 2. How many times the user launched the app (`launchCount`)
 * 3. How much video was recorded for the app (`formattedVideoDuration`, clip count, and storage size)
 * 4. App Version Number (`versionName` & versionCode)
 * Plus recent recorded clips for that app and System App Info (Speedrun launch removed per user request;
 * Speedrun Timer is configured via a Yes/No toggle in Launcher Options and Tweaks).
 */
@Composable
fun GameAppInfoModalDialog(
    game: InstalledApp,
    onLaunchWithSpeedrunTimer: () -> Unit = {},
    onOpenSystemAppInfo: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dossier: GameAppDossier = remember(game.packageName, game.versionName, game.memoryUsageMb) {
        GameAppDossierHelper.buildAppDossier(context, game)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, HubBlue.copy(alpha = 0.5f)),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 540.dp)
                .heightIn(max = 620.dp)
                .testTag("game_app_info_modal")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header: App Title + Package Name + Close Button
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
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(HubBlue.copy(alpha = 0.16f))
                                .border(1.dp, HubBlue.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = HubBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = dossier.appName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = dossier.packageName,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable { onDismiss() }
                            .testTag("game_app_info_close_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Row 1: App Size & Times Launched
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppDossierMetricBox(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("app_info_metric_size"),
                            icon = Icons.Default.Storage,
                            label = tr("APP SIZE", "TAMAÑO DE APP"),
                            primaryValue = dossier.formattedAppSize,
                            secondaryValue = tr("Installed APK footprint", "Espacio instalado en disco"),
                            accentColor = HubBlue
                        )
                        AppDossierMetricBox(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("app_info_metric_launches"),
                            icon = Icons.Default.RocketLaunch,
                            label = tr("TIMES LAUNCHED", "VECES INICIADA"),
                            primaryValue = "${dossier.launchCount}x",
                            secondaryValue = dossier.formattedPlayTime,
                            accentColor = LaunchGreen
                        )
                    }

                    // Row 2: Video Recorded for App & App Version Number
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppDossierMetricBox(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("app_info_metric_video_recorded"),
                            icon = Icons.Default.Videocam,
                            label = tr("VIDEO RECORDED", "VIDEO GRABADO"),
                            primaryValue = dossier.formattedVideoDuration,
                            secondaryValue = "${dossier.recordedClipsCount} ${tr("clips", "clips")} • ${dossier.formattedVideoSize}",
                            accentColor = RecorderCoral
                        )
                        AppDossierMetricBox(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("app_info_metric_version"),
                            icon = Icons.Default.Info,
                            label = tr("VERSION NUMBER", "VERSIÓN DE APP"),
                            primaryValue = "v${dossier.versionName}",
                            secondaryValue = "Build #${dossier.versionCode}",
                            accentColor = Color(0xFFA855F7)
                        )
                    }

                    // Recent Recorded Clips for this App (if any)
                    if (dossier.recordedClips.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tr(
                                "RECENT CLIPS RECORDED FOR THIS APP (${dossier.recordedClips.size})",
                                "CLIPS GRABADOS PARA ESTA APP (${dossier.recordedClips.size})"
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.7.sp
                        )
                        dossier.recordedClips.take(3).forEach { clip ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = clip.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${clip.formattedDuration} • ${clip.formattedSize} • ${clip.resolution}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons Row: System App Info • Done (Speedrun removed per user request)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onOpenSystemAppInfo,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("app_info_system_settings_button")
                    ) {
                        Text(
                            text = tr("System App Info", "Info del Sistema"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HubBlue,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("app_info_done_button")
                    ) {
                        Text(
                            text = tr("Done", "Listo"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppDossierMetricBox(
    icon: ImageVector,
    label: String,
    primaryValue: String,
    secondaryValue: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor,
                    letterSpacing = 0.6.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = primaryValue,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = secondaryValue,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Standalone Card for configuring and controlling the Anti-Cheat Speedrun Timer Overlay
 * inside Tweaks (`OptionsScreen.kt`), including:
 * - Size adjustment (Compact / Normal / Expanded presets + continuous scale slider + floating `⤢` handle)
 * - Target Game/App tagging
 * - Persistent Past Speedruns History grouped/filterable by App
 */
@Composable
fun AntiCheatSpeedrunTimerCard(
    availableGames: List<InstalledApp> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val speedrunState by SpeedrunTimerOverlayService.state.collectAsState()
    val isClean = speedrunState.integrityStatus.isClean
    var selectedAppFilter by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = if (isClean) LaunchGreen else RecorderCoral,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tr(
                            "Speedrun Timer Overlay",
                            "Superposición de Temporizador Speedrun"
                        ),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = tr(
                        "Hardware nanosecond clock, clock tamper detection, verification hash (${speedrunState.liveVerificationHash}), resizable overlay, and run history by app",
                        "Reloj nanotemporal de hardware, detección de alteración, código de verificación (${speedrunState.liveVerificationHash}), tamaño ajustable e historial por app"
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                )
            }

            Switch(
                checked = speedrunState.isEnabled,
                onCheckedChange = { enabled ->
                    if (enabled && !Settings.canDrawOverlays(context)) {
                        SystemSettingsHelper.openOverlayPermissionSettings(context)
                    }
                    SpeedrunTimerOverlayService.setEnabled(context, enabled)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = LaunchGreen
                ),
                modifier = Modifier.testTag("tweaks_speedrun_timer_toggle")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 1. Overlay Size Presets (Compact / Normal / Expanded) + Custom Scale Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = tr("Overlay Size & Split Density:", "Tamaño de Superposición y Splits:"),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = "${(speedrunState.overlayScale * 100).roundToInt()}% (${tr(speedrunState.sizePreset.labelEn, speedrunState.sizePreset.labelEs)})",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = HubBlue,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpeedrunOverlaySizePreset.entries.forEach { preset ->
                val isSelected = speedrunState.sizePreset == preset
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        SpeedrunTimerOverlayService.setSizePreset(context, preset)
                    },
                    label = {
                        Text(
                            text = "${tr(preset.labelEn, preset.labelEs)} (${(preset.scaleFactor * 100).roundToInt()}%)",
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = HubBlue.copy(alpha = 0.22f),
                        selectedLabelColor = HubBlue
                    ),
                    modifier = Modifier.testTag("speedrun_size_preset_${preset.name.lowercase()}")
                )
            }
        }

        Slider(
            value = speedrunState.overlayScale,
            onValueChange = { newScale ->
                SpeedrunTimerOverlayService.setCustomOverlayScale(context, newScale)
            },
            valueRange = 0.70f..1.45f,
            colors = SliderDefaults.colors(
                thumbColor = LaunchGreen,
                activeTrackColor = HubBlue
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("speedrun_size_scale_slider")
        )

        // 2. Target Game / App Selector for Speedrun Tagging
        if (availableGames.isNotEmpty()) {
            Text(
                text = tr("Active App / Game for Run:", "App / Juego Activo para la Carrera:"),
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
                val generalSelected = speedrunState.targetGamePackage.isBlank() || speedrunState.targetGamePackage == "global.speedrun"
                FilterChip(
                    selected = generalSelected,
                    onClick = {
                        SpeedrunTimerOverlayService.setEnabled(
                            context = context,
                            enabled = speedrunState.isEnabled,
                            gamePackage = "global.speedrun",
                            gameName = "General Speedrun"
                        )
                    },
                    label = {
                        Text("🎮 General Speedrun", fontSize = 10.5.sp)
                    }
                )
                availableGames.take(10).forEach { app ->
                    val isAppSelected = speedrunState.targetGamePackage == app.packageName
                    FilterChip(
                        selected = isAppSelected,
                        onClick = {
                            SpeedrunTimerOverlayService.setEnabled(
                                context = context,
                                enabled = speedrunState.isEnabled,
                                gamePackage = app.packageName,
                                gameName = app.appName
                            )
                        },
                        label = {
                            Text(
                                text = "🎮 ${app.appName}",
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 3. Live Security Status Pill + Timer Readout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0A101D))
                .border(
                    1.dp,
                    if (isClean) LaunchGreen.copy(alpha = 0.45f) else RecorderCoral,
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tr(
                        speedrunState.integrityStatus.badgeEn,
                        speedrunState.integrityStatus.badgeEs
                    ),
                    color = if (isClean) LaunchGreen else RecorderCoral,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "🎮 ${speedrunState.targetGameName} • ${speedrunState.liveVerificationHash} • ${speedrunState.splits.size} Splits",
                    color = Color(0xFF38BDF8),
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = speedrunState.formattedTime,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 4. Quick Action Controls: Start/Finish • Record Split • Reset • Copy Certificate
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    if (!speedrunState.isRunning) {
                        SpeedrunTimerOverlayService.startNewVerifiedRun()
                    } else {
                        SpeedrunTimerOverlayService.finishRun()
                    }
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("tweaks_speedrun_start_finish_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = LaunchGreen,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (speedrunState.isRunning) tr("Finish & Save Run", "Finalizar y Guardar") else tr("Start Verified Run", "Iniciar Carrera"),
                    color = LaunchGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = {
                    if (!speedrunState.isRunning) {
                        SpeedrunTimerOverlayService.startNewVerifiedRun()
                    }
                    SpeedrunTimerOverlayService.recordSplit()
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("tweaks_speedrun_split_button")
            ) {
                Text(
                    text = tr("Record Split", "Marcar Split"),
                    color = HubBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = {
                    SpeedrunTimerOverlayService.resetRun()
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("tweaks_speedrun_reset_button")
            ) {
                Text(
                    text = tr("Reset", "Reiniciar"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!speedrunState.runCertificateCode.isNullOrBlank()) {
                OutlinedButton(
                    onClick = {
                        try {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cb?.setPrimaryClip(
                                ClipData.newPlainText("Speedrun Certificate", speedrunState.runCertificateCode)
                            )
                            Toast.makeText(
                                context,
                                com.example.util.AppStrings.trText(
                                    "Copied Verified Speedrun Certificate!",
                                    "¡Certificado de Speedrun copiado!"
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (_: Exception) {}
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("tweaks_speedrun_copy_cert_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = LaunchGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = tr("Copy Run Proof", "Copiar Prueba"),
                        color = LaunchGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 5. Past Speedruns History & App Filter Section
        Spacer(modifier = Modifier.height(12.dp))
        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), thickness = 1.dp)
        Spacer(modifier = Modifier.height(10.dp))

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
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = tr(
                        "PAST SPEEDRUNS BY APP (${speedrunState.pastRuns.size})",
                        "HISTORIAL DE SPEEDRUNS POR APP (${speedrunState.pastRuns.size})"
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 0.6.sp
                    )
                )
            }

            if (speedrunState.pastRuns.isNotEmpty()) {
                Text(
                    text = tr("Clear All", "Limpiar Todo"),
                    color = RecorderCoral,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            SpeedrunTimerOverlayService.clearPastRunsHistory(context)
                            selectedAppFilter = null
                        }
                        .padding(4.dp)
                        .testTag("speedrun_clear_history_button")
                )
            }
        }

        val distinctAppNames = remember(speedrunState.pastRuns) {
            speedrunState.pastRuns.map { it.gameName }.distinct()
        }

        if (distinctAppNames.size > 1) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedAppFilter == null,
                    onClick = { selectedAppFilter = null },
                    label = { Text(tr("All Apps (${speedrunState.pastRuns.size})", "Todas (${speedrunState.pastRuns.size})"), fontSize = 10.5.sp) }
                )
                distinctAppNames.forEach { gName ->
                    val count = speedrunState.pastRuns.count { it.gameName == gName }
                    FilterChip(
                        selected = selectedAppFilter == gName,
                        onClick = { selectedAppFilter = gName },
                        label = { Text("🎮 $gName ($count)", fontSize = 10.5.sp) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val filteredRuns = remember(speedrunState.pastRuns, selectedAppFilter) {
            if (selectedAppFilter == null) speedrunState.pastRuns
            else speedrunState.pastRuns.filter { it.gameName == selectedAppFilter }
        }

        if (filteredRuns.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(12.dp)
                    .testTag("speedrun_empty_history_box"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tr(
                        "No completed speedruns yet. Tap 'Start Verified Run' then 'Finish & Save Run' to record runs by app.",
                        "Aún no hay speedruns completados. Inicia y finaliza una carrera para guardar el historial por app."
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.testTag("speedrun_past_runs_list")
            ) {
                filteredRuns.take(8).forEach { runRecord ->
                    val runClean = runRecord.integrityStatus.isClean
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .border(
                                1.dp,
                                if (runClean) LaunchGreen.copy(alpha = 0.35f) else RecorderCoral.copy(alpha = 0.45f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("speedrun_history_row_${runRecord.id}"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "🎮 ${runRecord.gameName}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (runClean) "🛡️ VERIFIED" else "⚠️ TAMPERED",
                                    color = if (runClean) LaunchGreen else RecorderCoral,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = "${runRecord.splitsCount} Splits • ${runRecord.verificationHash} • ${runRecord.formattedDate}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = runRecord.formattedTime,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.5.sp,
                                color = LaunchGreen
                            )
                            IconButton(
                                onClick = {
                                    try {
                                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        cb?.setPrimaryClip(
                                            ClipData.newPlainText("Speedrun Certificate", runRecord.certificateCode)
                                        )
                                        Toast.makeText(
                                            context,
                                            com.example.util.AppStrings.trText(
                                                "Copied ${runRecord.gameName} Run Certificate!",
                                                "¡Certificado de ${runRecord.gameName} copiado!"
                                            ),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } catch (_: Exception) {}
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Certificate",
                                    tint = HubBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    SpeedrunTimerOverlayService.deletePastRunById(runRecord.id, context)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Run",
                                    tint = RecorderCoral,
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
