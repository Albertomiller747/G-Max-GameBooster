package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.RecordedVideo
import com.example.service.BlackoutPresetType
import com.example.service.StreamerBlackoutOverlayService
import com.example.service.StreamerBlackoutState
import com.example.ui.theme.HubBlue
import com.example.ui.theme.LaunchGreen
import com.example.ui.theme.RecorderCoral
import com.example.util.ClipTrimResult
import com.example.util.ClipTrimSaveMode
import com.example.util.ConsoleHapticManager
import com.example.util.LocalVideoTrimmer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Compact Streamer Blackout Boxes Quick Bar displayed at the top of the Videos tab
 * so streamers can spawn privacy shields over their minimap, chat, or username right before recording.
 */
@Composable
fun StreamerBlackoutQuickBar(
    blackoutState: StreamerBlackoutState,
    onToggleEnabled: (Boolean) -> Unit,
    onSpawnPreset: (BlackoutPresetType) -> Unit,
    onToggleManualLock: (Boolean) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("streamer_blackout_quick_bar"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (blackoutState.isEnabled) RecorderCoral.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black)
                            .border(1.dp, RecorderCoral.copy(alpha = 0.7f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = "Streamer Blackout Boxes",
                            tint = RecorderCoral,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = com.example.util.tr(
                                    "Streamer Blackout Boxes",
                                    "Cajas Negras para Streamers"
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (blackoutState.boxes.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(RecorderCoral.copy(alpha = 0.18f))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${blackoutState.boxes.size}",
                                        color = RecorderCoral,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (blackoutState.isRecordingAutoLocked) {
                                com.example.util.tr(
                                    "LOCKED FOR RECORDING • Full Touch Input",
                                    "BLOQUEADO EN GRABACIÓN • Toque Libre"
                                )
                            } else {
                                com.example.util.tr(
                                    "Cover game chat, username, or minimap during recordings",
                                    "Cubre el chat, nombre o minimapa durante tus grabaciones"
                                )
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (blackoutState.isRecordingAutoLocked) LaunchGreen
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = blackoutState.isEnabled,
                    onCheckedChange = { enable ->
                        ConsoleHapticManager.playSelectClick(context)
                        onToggleEnabled(enable)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = RecorderCoral),
                    modifier = Modifier.testTag("streamer_blackout_quick_switch")
                )
            }

            // Preset Spawn Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BlackoutPresetType.entries.forEach { preset ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            ConsoleHapticManager.playSelectClick(context)
                            onSpawnPreset(preset)
                        },
                        label = {
                            Text(
                                text = com.example.util.tr(preset.titleEn, preset.titleEs),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.testTag("blackout_quick_spawn_${preset.id}")
                    )
                }

                if (blackoutState.boxes.isNotEmpty()) {
                    FilterChip(
                        selected = blackoutState.isEffectiveTouchLocked,
                        onClick = {
                            ConsoleHapticManager.playSelectClick(context)
                            onToggleManualLock(!blackoutState.isManualTouchLocked)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (blackoutState.isEffectiveTouchLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        label = {
                            Text(
                                text = if (blackoutState.isEffectiveTouchLocked) {
                                    com.example.util.tr("Locked", "Bloqueado")
                                } else {
                                    com.example.util.tr("Lock Touch", "Bloquear Toque")
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LaunchGreen.copy(alpha = 0.18f),
                            selectedLabelColor = LaunchGreen,
                            selectedLeadingIconColor = LaunchGreen
                        ),
                        modifier = Modifier.testTag("blackout_quick_lock_chip")
                    )

                    TextButton(
                        onClick = {
                            ConsoleHapticManager.playSelectClick(context)
                            onClearAll()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("blackout_quick_clear_all")
                    ) {
                        Text(
                            text = com.example.util.tr("Clear All", "Limpiar Todo"),
                            color = RecorderCoral,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Full Streamer Blackout Boxes Settings Card for the Recorder Settings Tab.
 */
@Composable
fun StreamerBlackoutSettingsCard(
    blackoutState: StreamerBlackoutState,
    onToggleEnabled: (Boolean) -> Unit,
    onSpawnPreset: (BlackoutPresetType) -> Unit,
    onToggleManualLock: (Boolean) -> Unit,
    onTogglePurePitchBlack: (Boolean) -> Unit,
    onRemoveBox: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("recorder_streamer_blackout_settings_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, RecorderCoral.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = RecorderCoral,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = com.example.util.tr(
                            "STREAMER BLACKOUT BOXES",
                            "CAJAS NEGRAS PARA STREAMERS"
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = RecorderCoral,
                        letterSpacing = 0.7.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (blackoutState.isEffectiveTouchLocked) LaunchGreen.copy(alpha = 0.16f)
                            else HubBlue.copy(alpha = 0.16f)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (blackoutState.isRecordingAutoLocked) {
                            "LOCKED (REC)"
                        } else if (blackoutState.isManualTouchLocked) {
                            "TOUCH LOCKED"
                        } else {
                            "EDITABLE"
                        },
                        color = if (blackoutState.isEffectiveTouchLocked) LaunchGreen else HubBlue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Text(
                text = com.example.util.tr(
                    "Place floating black shields over your game chat, username, or minimap. Drag the center to position, drag the bottom right corner (↘) to resize, and tap Record. Boxes lock automatically while recording so your taps go straight to the game.",
                    "Coloca escudos negros sobre el chat, nombre de usuario o minimapa. Arrastra el centro para mover, la esquina inferior derecha (↘) para cambiar tamaño y presiona Grabar. Se bloquean al grabar para dejar pasar todos los toques."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Master Enable Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = com.example.util.tr("Show Streamer Blackout Boxes", "Mostrar Cajas Negras en Pantalla"),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = com.example.util.tr(
                            "Active shields: ${blackoutState.boxes.size} / ${StreamerBlackoutOverlayService.MAX_BOX_COUNT}",
                            "Escudos activos: ${blackoutState.boxes.size} / ${StreamerBlackoutOverlayService.MAX_BOX_COUNT}"
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = blackoutState.isEnabled,
                    onCheckedChange = { enable ->
                        ConsoleHapticManager.playSelectClick(context)
                        onToggleEnabled(enable)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = RecorderCoral),
                    modifier = Modifier.testTag("streamer_blackout_master_switch")
                )
            }

            // Spawn Preset Buttons Row
            Text(
                text = com.example.util.tr("SPAWN PRIVACY SHIELD PRESET", "CREAR ESCUDO RÁPIDO"),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = HubBlue
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BlackoutPresetType.entries.forEach { preset ->
                    OutlinedButton(
                        onClick = {
                            ConsoleHapticManager.playSelectClick(context)
                            onSpawnPreset(preset)
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, RecorderCoral.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("blackout_settings_spawn_${preset.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = RecorderCoral,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = com.example.util.tr(preset.titleEn.removePrefix("+ "), preset.titleEs.removePrefix("+ ")),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            // Manual Touch Lock Toggle (in addition to Lock on Record)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = com.example.util.tr(
                            "Lock Touch Input (Locks on Record)",
                            "Bloquear Toque (Se bloquea al Grabar)"
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = com.example.util.tr(
                            "When locked or recording, all touches go straight to your game",
                            "Al bloquear o grabar, todos los toques van directamente al juego"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = blackoutState.isManualTouchLocked,
                    onCheckedChange = { locked ->
                        ConsoleHapticManager.playSelectClick(context)
                        onToggleManualLock(locked)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = LaunchGreen),
                    modifier = Modifier.testTag("streamer_blackout_manual_lock_switch")
                )
            }

            // Solid Black Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = com.example.util.tr(
                            "Solid Black Box (Hide Label)",
                            "Negro Total (Ocultar Etiqueta)"
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = com.example.util.tr(
                            "Render solid black boxes without any badge text when locked",
                            "Muestra cajas negras sólidas sin texto al estar bloqueadas"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = blackoutState.isPurePitchBlack,
                    onCheckedChange = { pure ->
                        ConsoleHapticManager.playSelectClick(context)
                        onTogglePurePitchBlack(pure)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = HubBlue),
                    modifier = Modifier.testTag("streamer_blackout_pure_black_switch")
                )
            }

            // Active Boxes List
            if (blackoutState.boxes.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = com.example.util.tr(
                            "ACTIVE BLACKOUT SHIELDS (${blackoutState.boxes.size})",
                            "ESCUDOS ACTIVOS (${blackoutState.boxes.size})"
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = {
                            ConsoleHapticManager.playSelectClick(context)
                            onClearAll()
                        },
                        modifier = Modifier.testTag("streamer_blackout_clear_all_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = RecorderCoral,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = com.example.util.tr("Clear All", "Eliminar Todas"),
                            color = RecorderCoral,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                blackoutState.boxes.forEachIndexed { idx, box ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "#${idx + 1} • ${box.label}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${box.widthDp}×${box.heightDp} dp • Pos (${box.x}, ${box.y})",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                ConsoleHapticManager.playSelectClick(context)
                                onRemoveBox(box.id)
                            },
                            modifier = Modifier
                                .size(30.dp)
                                .testTag("remove_blackout_box_${box.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove Box",
                                tint = RecorderCoral,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Native Android MediaExtractor / MediaMuxer Local Clip Trimmer Studio Modal.
 * Lets players slice a 15s/30s/60s or custom highlight out of a large 4K 120fps recording
 * and choose whether to Save as New Clip, Save & Delete Original, or Save as New Original.
 */
@Composable
fun LocalClipTrimmerModalDialog(
    video: RecordedVideo,
    onDismiss: () -> Unit,
    onTrimCompleted: (ClipTrimResult) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val resolvedTotalMs = remember(video.filePath, video.uriString, video.durationMs) {
        LocalVideoTrimmer.resolveVideoDurationMs(context, video).coerceAtLeast(2_000L)
    }

    var totalDurationMs by remember { mutableLongStateOf(resolvedTotalMs) }
    // Default to a 15-second clutch clip (or full clip if shorter than 15s)
    var trimStartMs by remember { mutableLongStateOf(0L) }
    var trimEndMs by remember {
        mutableLongStateOf(min(resolvedTotalMs, 15_000L).coerceAtLeast(1_000L))
    }

    var selectedSaveMode by remember { mutableStateOf(ClipTrimSaveMode.SAVE_AS_NEW_CLIP) }
    var isPlayingPreview by remember { mutableStateOf(true) }
    var currentPlayheadMs by remember { mutableLongStateOf(0L) }
    var isTrimming by remember { mutableStateOf(false) }
    var errorBanner by remember { mutableStateOf<String?>(null) }

    var videoWidth by remember { mutableIntStateOf(1920) }
    var videoHeight by remember { mutableIntStateOf(1080) }

    val mediaPlayerRef = remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    val textureViewRef = remember { mutableStateOf<android.view.TextureView?>(null) }

    fun applyFitTransform(tv: android.view.TextureView?, vw: Int, vh: Int) {
        if (tv == null || vw <= 0 || vh <= 0 || tv.width <= 0 || tv.height <= 0) return
        val viewW = tv.width.toFloat()
        val viewH = tv.height.toFloat()
        val videoAspect = vw.toFloat() / vh.toFloat()
        val viewAspect = viewW / viewH
        var scaleX = 1f
        var scaleY = 1f
        if (videoAspect > viewAspect) {
            scaleY = viewAspect / videoAspect
        } else {
            scaleX = videoAspect / viewAspect
        }
        val matrix = android.graphics.Matrix()
        matrix.setScale(scaleX, scaleY, viewW / 2f, viewH / 2f)
        tv.setTransform(matrix)
        tv.invalidate()
    }

    fun seekPreviewTo(targetMs: Long) {
        val mp = mediaPlayerRef.value ?: return
        try {
            val clamped = targetMs.coerceIn(0L, totalDurationMs)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mp.seekTo(clamped, android.media.MediaPlayer.SEEK_CLOSEST)
            } else {
                mp.seekTo(clamped.toInt())
            }
            currentPlayheadMs = clamped
        } catch (_: Exception) {}
    }

    // Keep preview looping strictly inside [trimStartMs, trimEndMs]
    LaunchedEffect(isPlayingPreview, trimStartMs, trimEndMs, totalDurationMs) {
        while (isPlayingPreview) {
            val mp = mediaPlayerRef.value
            if (mp != null) {
                try {
                    if (mp.isPlaying) {
                        val pos = mp.currentPosition.toLong().coerceAtLeast(0L)
                        currentPlayheadMs = pos
                        if (pos >= trimEndMs || pos < trimStartMs - 250L) {
                            seekPreviewTo(trimStartMs)
                        }
                    }
                } catch (_: Exception) {}
            }
            delay(80L)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayerRef.value?.stop()
            } catch (_: Exception) {}
            try {
                mediaPlayerRef.value?.release()
            } catch (_: Exception) {}
            mediaPlayerRef.value = null
        }
    }

    val sliceDurationMs = max(1_000L, trimEndMs - trimStartMs)
    val estTrimmedBytes = LocalVideoTrimmer.estimateTrimmedSizeBytes(
        originalSizeBytes = video.sizeBytes,
        totalDurationMs = totalDurationMs,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs
    )
    val estSavedBytes = max(0L, video.sizeBytes - estTrimmedBytes)

    fun formatPreciseTime(ms: Long): String {
        val safe = max(0L, ms)
        val totalSec = safe / 1000L
        val mins = totalSec / 60L
        val secs = totalSec % 60L
        val tenths = (safe % 1000L) / 100L
        return String.format(Locale.US, "%02d:%02d.%d", mins, secs, tenths)
    }

    Dialog(
        onDismissRequest = { if (!isTrimming) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, HubBlue.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 10.dp)
                .testTag("local_clip_trimmer_modal")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Header Row
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
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(RecorderCoral.copy(alpha = 0.16f))
                                .border(1.dp, RecorderCoral, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCut,
                                contentDescription = "Local Clip Trimmer",
                                tint = RecorderCoral,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = com.example.util.tr("Local Clip Trimmer", "Recortador Local de Clips"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${video.title} • ${video.formattedSize}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = { if (!isTrimming) onDismiss() },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("clip_trimmer_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Trimmer",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 2. Live Looping Video Preview Viewport
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(185.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            android.view.TextureView(ctx).apply {
                                isOpaque = false
                                textureViewRef.value = this
                                surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(
                                        surfaceTexture: android.graphics.SurfaceTexture,
                                        width: Int,
                                        height: Int
                                    ) {
                                        val surface = android.view.Surface(surfaceTexture)
                                        val mp = android.media.MediaPlayer()
                                        mediaPlayerRef.value = mp
                                        try {
                                            val f = File(video.filePath)
                                            if (f.exists()) {
                                                mp.setDataSource(f.absolutePath)
                                            } else {
                                                mp.setDataSource(ctx, Uri.parse(video.uriString))
                                            }
                                            mp.setSurface(surface)
                                            mp.isLooping = true
                                            mp.setOnPreparedListener { preparedMp ->
                                                val dur = preparedMp.duration.toLong()
                                                if (dur >= 1_000L) {
                                                    totalDurationMs = dur
                                                    if (trimEndMs > dur || trimEndMs <= trimStartMs) {
                                                        trimStartMs = 0L
                                                        trimEndMs = min(dur, 15_000L).coerceAtLeast(1_000L)
                                                    }
                                                }
                                                if (preparedMp.videoWidth > 0 && preparedMp.videoHeight > 0) {
                                                    videoWidth = preparedMp.videoWidth
                                                    videoHeight = preparedMp.videoHeight
                                                    applyFitTransform(this@apply, videoWidth, videoHeight)
                                                }
                                                preparedMp.start()
                                                isPlayingPreview = true
                                            }
                                            mp.prepareAsync()
                                        } catch (_: Exception) {}
                                    }

                                    override fun onSurfaceTextureSizeChanged(
                                        surfaceTexture: android.graphics.SurfaceTexture,
                                        width: Int,
                                        height: Int
                                    ) {
                                        applyFitTransform(this@apply, videoWidth, videoHeight)
                                    }

                                    override fun onSurfaceTextureDestroyed(
                                        surfaceTexture: android.graphics.SurfaceTexture
                                    ): Boolean {
                                        try {
                                            mediaPlayerRef.value?.release()
                                        } catch (_: Exception) {}
                                        mediaPlayerRef.value = null
                                        return true
                                    }

                                    override fun onSurfaceTextureUpdated(
                                        surfaceTexture: android.graphics.SurfaceTexture
                                    ) {}
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                val mp = mediaPlayerRef.value ?: return@clickable
                                try {
                                    if (mp.isPlaying) {
                                        mp.pause()
                                        isPlayingPreview = false
                                    } else {
                                        mp.start()
                                        isPlayingPreview = true
                                    }
                                } catch (_: Exception) {}
                            }
                    )

                    // Playhead & Slice Duration Badge overlay
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPlayingPreview) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = RecorderCoral,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${formatPreciseTime(currentPlayheadMs)} • Slice: ${String.format(Locale.US, "%.1fs", sliceDurationMs / 1000.0)}",
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 3. Quick Clutch Duration Presets Row (15s Clutch, 30s Highlight, 60s Round, Full)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LocalVideoTrimmer.QUICK_CLUTCH_PRESETS.forEach { preset ->
                        val targetSlice = min(totalDurationMs, preset.durationMs)
                        val isSelected = kotlin.math.abs(sliceDurationMs - targetSlice) < 400L
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                ConsoleHapticManager.playSelectClick(context)
                                val newEnd = min(totalDurationMs, trimStartMs + preset.durationMs)
                                val newStart = max(0L, newEnd - min(totalDurationMs, preset.durationMs))
                                val (normS, normE) = LocalVideoTrimmer.normalizeTrimRange(newStart, newEnd, totalDurationMs)
                                trimStartMs = normS
                                trimEndMs = normE
                                seekPreviewTo(normS)
                            },
                            label = {
                                Text(
                                    text = com.example.util.tr(preset.labelEn, preset.labelEs),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RecorderCoral.copy(alpha = 0.18f),
                                selectedLabelColor = RecorderCoral
                            ),
                            modifier = Modifier.testTag("trim_preset_${preset.id}")
                        )
                    }

                    FilterChip(
                        selected = trimStartMs == 0L && trimEndMs == totalDurationMs,
                        onClick = {
                            ConsoleHapticManager.playSelectClick(context)
                            trimStartMs = 0L
                            trimEndMs = totalDurationMs
                            seekPreviewTo(0L)
                        },
                        label = {
                            Text(
                                text = com.example.util.tr("Full Clip", "Clip Completo"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = HubBlue.copy(alpha = 0.18f),
                            selectedLabelColor = HubBlue
                        ),
                        modifier = Modifier.testTag("trim_preset_full")
                    )
                }

                // 4. Start & End Trim Sliders with -1s / +1s Fine-Tune Steppers
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Start Trim Handle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${com.example.util.tr("START", "INICIO")}: ${formatPreciseTime(trimStartMs)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                color = HubBlue
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val (s, e) = LocalVideoTrimmer.normalizeTrimRange(
                                            trimStartMs - 1_000L,
                                            trimEndMs,
                                            totalDurationMs
                                        )
                                        trimStartMs = s
                                        trimEndMs = e
                                        seekPreviewTo(s)
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp).testTag("trim_start_minus_1s")
                                ) {
                                    Text("-1s", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val (s, e) = LocalVideoTrimmer.normalizeTrimRange(
                                            trimStartMs + 1_000L,
                                            trimEndMs,
                                            totalDurationMs
                                        )
                                        trimStartMs = s
                                        trimEndMs = e
                                        seekPreviewTo(s)
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp).testTag("trim_start_plus_1s")
                                ) {
                                    Text("+1s", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Slider(
                            value = trimStartMs.toFloat(),
                            onValueChange = { raw ->
                                val candidateStart = raw.toLong().coerceIn(0L, max(0L, trimEndMs - 1_000L))
                                trimStartMs = candidateStart
                            },
                            onValueChangeFinished = {
                                seekPreviewTo(trimStartMs)
                            },
                            valueRange = 0f..totalDurationMs.toFloat().coerceAtLeast(1_000f),
                            colors = SliderDefaults.colors(
                                thumbColor = HubBlue,
                                activeTrackColor = HubBlue
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .testTag("trim_start_slider")
                        )

                        // End Trim Handle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${com.example.util.tr("END", "FIN")}: ${formatPreciseTime(trimEndMs)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                color = RecorderCoral
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val (s, e) = LocalVideoTrimmer.normalizeTrimRange(
                                            trimStartMs,
                                            trimEndMs - 1_000L,
                                            totalDurationMs
                                        )
                                        trimStartMs = s
                                        trimEndMs = e
                                        seekPreviewTo(max(s, e - 1_500L))
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp).testTag("trim_end_minus_1s")
                                ) {
                                    Text("-1s", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val (s, e) = LocalVideoTrimmer.normalizeTrimRange(
                                            trimStartMs,
                                            trimEndMs + 1_000L,
                                            totalDurationMs
                                        )
                                        trimStartMs = s
                                        trimEndMs = e
                                        seekPreviewTo(max(s, e - 1_500L))
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp).testTag("trim_end_plus_1s")
                                ) {
                                    Text("+1s", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Slider(
                            value = trimEndMs.toFloat(),
                            onValueChange = { raw ->
                                val candidateEnd = raw.toLong().coerceIn(
                                    min(totalDurationMs, trimStartMs + 1_000L),
                                    totalDurationMs
                                )
                                trimEndMs = candidateEnd
                            },
                            onValueChangeFinished = {
                                seekPreviewTo(max(trimStartMs, trimEndMs - 2_000L))
                            },
                            valueRange = 0f..totalDurationMs.toFloat().coerceAtLeast(1_000f),
                            colors = SliderDefaults.colors(
                                thumbColor = RecorderCoral,
                                activeTrackColor = RecorderCoral
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .testTag("trim_end_slider")
                        )
                    }
                }

                // 5. Storage Savings Readout Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(LaunchGreen.copy(alpha = 0.12f))
                        .border(1.dp, LaunchGreen.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = com.example.util.tr(
                                "Est. Trimmed Clip: ${LocalVideoTrimmer.formatStorageSize(estTrimmedBytes)}",
                                "Clip Estimado: ${LocalVideoTrimmer.formatStorageSize(estTrimmedBytes)}"
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = com.example.util.tr(
                                "Lossless Native MediaMuxer • Fast Direct Export",
                                "MediaMuxer Nativo Sin Pérdida • Exportación Rápida"
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(LaunchGreen.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = com.example.util.tr(
                                "Frees ~${LocalVideoTrimmer.formatStorageSize(estSavedBytes)}",
                                "Libera ~${LocalVideoTrimmer.formatStorageSize(estSavedBytes)}"
                            ),
                            color = LaunchGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // 6. Save Mode Selector (Save as New Clip | Save & Delete Original | Save as New Original)
                Text(
                    text = com.example.util.tr("STORAGE ACTION", "ACCIÓN DE ALMACENAMIENTO"),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ClipTrimSaveMode.entries.forEach { mode ->
                        val isSelected = selectedSaveMode == mode
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ConsoleHapticManager.playSelectClick(context)
                                    selectedSaveMode = mode
                                }
                                .testTag("trim_save_mode_${mode.id}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    RecorderCoral.copy(alpha = 0.14f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                }
                            ),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) RecorderCoral else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = com.example.util.tr(mode.titleEn, mode.titleEs),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isSelected) RecorderCoral else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = com.example.util.tr(mode.subtitleEn, mode.subtitleEs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = RecorderCoral,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                errorBanner?.let { err ->
                    Text(
                        text = err,
                        color = RecorderCoral,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 7. Primary Slice & Save Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { if (!isTrimming) onDismiss() },
                        enabled = !isTrimming,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(0.38f)
                    ) {
                        Text(
                            text = com.example.util.tr("Cancel", "Cancelar"),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            if (isTrimming) return@Button
                            ConsoleHapticManager.playSelectClick(context)
                            isTrimming = true
                            errorBanner = null
                            try {
                                mediaPlayerRef.value?.pause()
                                isPlayingPreview = false
                            } catch (_: Exception) {}

                            coroutineScope.launch {
                                val res = withContext(Dispatchers.IO) {
                                    LocalVideoTrimmer.trimVideo(
                                        context = context,
                                        video = video,
                                        startMs = trimStartMs,
                                        endMs = trimEndMs,
                                        saveMode = selectedSaveMode
                                    )
                                }
                                isTrimming = false
                                if (res.success) {
                                    onTrimCompleted(res)
                                } else {
                                    errorBanner = com.example.util.AppStrings.trText(res.messageEn, res.messageEs)
                                }
                            }
                        },
                        enabled = !isTrimming,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RecorderCoral,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(0.62f)
                            .testTag("confirm_slice_clip_button")
                    ) {
                        if (isTrimming) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = com.example.util.tr("Slicing Clip...", "Recortando..."),
                                fontWeight = FontWeight.ExtraBold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.ContentCut,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = com.example.util.tr(
                                    selectedSaveMode.titleEn,
                                    selectedSaveMode.titleEs
                                ),
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
