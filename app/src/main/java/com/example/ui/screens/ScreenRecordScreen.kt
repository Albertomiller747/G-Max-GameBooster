package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.Size
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import com.example.model.AudioSourceMode
import com.example.model.CapturedScreenshot
import com.example.model.GameMediaGroup
import com.example.model.RecordedVideo
import com.example.model.ResolutionPreset
import com.example.model.VideoQualitySettings
import com.example.service.BlackoutPresetType
import com.example.service.ScreenRecordOverlayService
import com.example.service.ScreenRecordingService
import com.example.service.StreamerBlackoutOverlayService
import com.example.service.StreamerBlackoutState
import com.example.ui.components.LocalClipTrimmerModalDialog
import com.example.ui.components.StreamerBlackoutQuickBar
import com.example.ui.components.StreamerBlackoutSettingsCard
import com.example.ui.theme.HubBlue
import com.example.ui.theme.LaunchGreen
import com.example.ui.theme.RecorderCoral
import com.example.util.ConsoleHapticManager
import com.example.util.ScreenRecordingManager
import com.example.viewmodel.GameBoosterViewModel
import java.io.File
import java.util.Locale

@Composable
fun ScreenRecordScreen(
    viewModel: GameBoosterViewModel,
    onRequestMediaProjection: () -> Unit,
    onRequestScreenshotProjection: (() -> Unit)? = null,
    initialSubTab: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val gamepads by viewModel.gamepads.collectAsState()
    val isControllerConnected = gamepads.isNotEmpty()
    val isDeviceOnline by com.example.util.rememberIsDeviceOnline()

    var selectedTab by remember(initialSubTab) { mutableIntStateOf(initialSubTab.coerceIn(0, 2)) }
    var settings by remember { mutableStateOf(ScreenRecordingManager.loadSettings(context)) }

    LaunchedEffect(isDeviceOnline) {
        settings = if (!isDeviceOnline) {
            settings.copy(
                resolutionLabel = "720p",
                width = 1280,
                height = 720,
                fps = 60,
                bitrateMbps = 8
            )
        } else {
            ScreenRecordingManager.loadSettings(context)
        }
    }
    var recordings by remember { mutableStateOf(emptyList<RecordedVideo>()) }
    var screenshots by remember { mutableStateOf(emptyList<CapturedScreenshot>()) }

    var selectedVideoForPlayback by remember { mutableStateOf<RecordedVideo?>(null) }
    var selectedVideoForTrimming by remember { mutableStateOf<RecordedVideo?>(null) }
    var videoToDelete by remember { mutableStateOf<RecordedVideo?>(null) }
    var selectedScreenshotForPreview by remember { mutableStateOf<CapturedScreenshot?>(null) }
    var selectedScreenshotForEdit by remember { mutableStateOf<CapturedScreenshot?>(null) }
    var screenshotToDelete by remember { mutableStateOf<CapturedScreenshot?>(null) }
    var userMessage by remember { mutableStateOf<String?>(null) }

    // State from ScreenRecordingService & StreamerBlackoutOverlayService
    val isRecording by ScreenRecordingService.isRecording.collectAsState()
    val recordingSeconds by ScreenRecordingService.recordingSeconds.collectAsState()
    val activeAudioMode by ScreenRecordingService.activeAudioMode.collectAsState()
    val lastSavedUri by ScreenRecordingService.lastSavedVideoUri.collectAsState()
    val lastScreenshotUri by ScreenRecordingService.lastSavedScreenshotUri.collectAsState()
    val blackoutState by StreamerBlackoutOverlayService.state.collectAsState()

    LaunchedEffect(Unit) {
        StreamerBlackoutOverlayService.loadState(context)
        com.example.util.InstantReplayBufferManager.init(context)
    }

    fun ensureOverlayPermissionOrPrompt(): Boolean {
        if (Settings.canDrawOverlays(context)) return true
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            )
        } catch (_: Exception) {}
        userMessage = com.example.util.AppStrings.trText(
            "Grant 'Display over other apps' permission to spawn Streamer Blackout Boxes",
            "Concede el permiso 'Mostrar sobre otras apps' para usar Cajas Negras"
        )
        return false
    }

    val handleToggleBlackout: (Boolean) -> Unit = { enable ->
        if (!enable || ensureOverlayPermissionOrPrompt()) {
            val st = StreamerBlackoutOverlayService.setEnabled(context, enable)
            userMessage = if (st.isEnabled) {
                com.example.util.AppStrings.trText(
                    "Streamer Blackout Boxes active (${st.boxes.size} shield(s)). Locks on Record.",
                    "Cajas Negras activas (${st.boxes.size} escudo(s)). Se bloquean al Grabar."
                )
            } else {
                com.example.util.AppStrings.trText(
                    "Streamer Blackout Boxes hidden",
                    "Cajas Negras ocultas"
                )
            }
        }
    }

    val handleSpawnBlackoutPreset: (BlackoutPresetType) -> Unit = { preset ->
        if (ensureOverlayPermissionOrPrompt()) {
            val st = StreamerBlackoutOverlayService.spawnPresetBox(context, preset)
            userMessage = com.example.util.AppStrings.trText(
                "Spawned ${preset.boxLabel} (${st.boxes.size}/${StreamerBlackoutOverlayService.MAX_BOX_COUNT}). Drag or resize on screen.",
                "Creado ${preset.boxLabel} (${st.boxes.size}/${StreamerBlackoutOverlayService.MAX_BOX_COUNT}). Arrastra o cambia su tamaño."
            )
        }
    }

    val handleToggleBlackoutManualLock: (Boolean) -> Unit = { locked ->
        StreamerBlackoutOverlayService.setManualTouchLocked(context, locked)
        userMessage = if (locked) {
            com.example.util.AppStrings.trText(
                "Blackout Boxes touch locked: 100% of taps now pass through to your game",
                "Cajas Negras bloqueadas: el 100% de los toques pasan al juego"
            )
        } else {
            com.example.util.AppStrings.trText(
                "Blackout Boxes unlocked for dragging and resizing",
                "Cajas Negras desbloqueadas para mover y redimensionar"
            )
        }
    }

    val handleToggleBlackoutPureBlack: (Boolean) -> Unit = { pure ->
        StreamerBlackoutOverlayService.setPurePitchBlack(context, pure)
    }

    val handleRemoveBlackoutBox: (String) -> Unit = { boxId ->
        StreamerBlackoutOverlayService.removeBox(context, boxId)
    }

    val handleClearAllBlackoutBoxes: () -> Unit = {
        StreamerBlackoutOverlayService.clearAllBoxes(context)
        userMessage = com.example.util.AppStrings.trText(
            "Cleared all Streamer Blackout Boxes",
            "Se eliminaron todas las Cajas Negras"
        )
    }

    // Query device supported options
    val supportedResolutions = remember(context) { ScreenRecordingManager.getSupportedResolutions(context) }
    val supportedFps = remember(context) { ScreenRecordingManager.getSupportedFps(context) }

    // Storage capacity info for bottom bar
    val storageInfo = remember(recordings.size, screenshots.size, settings.bitrateMbps) {
        ScreenRecordingManager.getStorageCapacityInfo(context)
    }

    var isOverlayBubbleEnabled by remember {
        mutableStateOf(settings.isOverlayEnabled)
    }
    var isInAppRecorderEnabled by remember {
        mutableStateOf(ScreenRecordingManager.isInAppRecorderEnabled(context))
    }

    var pendingAudioMode by remember { mutableStateOf<AudioSourceMode?>(null) }
    var pendingRecordLaunchAfterPerm by remember { mutableStateOf(false) }

    fun refreshMediaLists() {
        recordings = ScreenRecordingManager.queryRecordedVideos(context)
        screenshots = ScreenRecordingManager.queryCapturedScreenshots(context)
    }

    val restoreCapturesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val (restoredVids, restoredShots) = ScreenRecordingManager.importPreviousCaptures(context, uris)
            refreshMediaLists()
            userMessage = com.example.util.AppStrings.trText(
                "Restored $restoredVids video(s) and $restoredShots screenshot(s) into GMax library",
                "Restaurados $restoredVids video(s) y $restoredShots captura(s) en la biblioteca GMax"
            )
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val targetMode = pendingAudioMode ?: settings.audioSourceMode.let {
                if (it == AudioSourceMode.MUTE) AudioSourceMode.INTERNAL_ONLY else it
            }
            val updated = settings.copy(
                audioSourceMode = targetMode,
                recordMic = targetMode == AudioSourceMode.INTERNAL_AND_MIC || targetMode == AudioSourceMode.MIC_ONLY
            )
            settings = updated
            ScreenRecordingManager.saveSettings(context, updated)
            viewModel.scheduleCloudSettingsSync()
            userMessage = "Audio capture enabled: ${targetMode.label}"
        } else {
            userMessage = "Audio permission denied, recording will continue without sound"
        }
        pendingAudioMode = null
        if (pendingRecordLaunchAfterPerm) {
            pendingRecordLaunchAfterPerm = false
            onRequestMediaProjection()
        }
    }

    LaunchedEffect(Unit, isRecording, lastSavedUri, lastScreenshotUri) {
        refreshMediaLists()
    }

    val tabTitles = listOf(
        com.example.util.tr("Videos", "Videos"),
        com.example.util.tr("Screenshots", "Capturas"),
        com.example.util.tr("Settings", "Ajustes")
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Clean Top Header Bar (Matches screenshot #2: "Videos" / "Screenshots" / "Settings" + Floating Cam Bubble Toggle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tabTitles[selectedTab],
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 22.sp
                        )
                    )
                    if (isRecording) {
                        Spacer(modifier = Modifier.width(12.dp))
                        val mins = recordingSeconds / 60
                        val secs = recordingSeconds % 60
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(RecorderCoral.copy(alpha = 0.16f))
                                .border(1.dp, RecorderCoral, RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = String.format(Locale.US, "REC %02d:%02d • %s", mins, secs, activeAudioMode.shortLabel),
                                color = RecorderCoral,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                // Right side quick actions: Floating Bubble toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Docked Recorder Bubble quick toggle without gray box overlay
                    IconButton(
                        onClick = {
                            if (!Settings.canDrawOverlays(context)) {
                                try {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                } catch (_: Exception) {}
                            } else {
                                val newState = !isOverlayBubbleEnabled
                                isOverlayBubbleEnabled = newState
                                settings = settings.copy(isOverlayEnabled = newState)
                                ScreenRecordingManager.setOverlayEnabled(context, newState)
                                val svcIntent = Intent(context, ScreenRecordOverlayService::class.java).apply {
                                    action = if (newState) {
                                        ScreenRecordOverlayService.ACTION_START
                                    } else {
                                        ScreenRecordOverlayService.ACTION_STOP
                                    }
                                }
                                context.startService(svcIntent)
                                userMessage = if (newState) {
                                    "Floating recorder bubble enabled"
                                } else {
                                    "Floating recorder bubble disabled"
                                }
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("recorder_bubble_quick_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Toggle Floating Recorder Bubble",
                            tint = if (isOverlayBubbleEnabled) RecorderCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // 2. 3-Icon Tab Strip with Coral-Red Underline (Videos, Screenshots, Settings - NO wrench/tools icon)
            RecorderThreeTabStrip(
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it }
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                thickness = 1.dp
            )

            if (!isDeviceOnline) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HubBlue.copy(alpha = 0.14f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("offline_recording_cap_banner"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = com.example.util.tr(
                            "Offline Mode: Screen Recording locked to 720p • 60 FPS • 8 Mbps",
                            "Modo sin conexión: Grabación fijada en 720p • 60 FPS • 8 Mbps"
                        ),
                        color = HubBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Optional Toast / Status Banner
            userMessage?.let { msg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RecorderCoral.copy(alpha = 0.12f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { userMessage = null },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 3. Main Tab Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    0 -> VideosTabContent(
                        recordings = recordings,
                        blackoutState = blackoutState,
                        onToggleBlackoutEnabled = handleToggleBlackout,
                        onSpawnBlackoutPreset = handleSpawnBlackoutPreset,
                        onToggleBlackoutManualLock = handleToggleBlackoutManualLock,
                        onClearAllBlackoutBoxes = handleClearAllBlackoutBoxes,
                        onPlayVideo = { selectedVideoForPlayback = it },
                        onTrimVideo = { selectedVideoForTrimming = it },
                        onSaveToGallery = { video ->
                            val saved = ScreenRecordingManager.saveVideoToGallery(context, video)
                            userMessage = if (saved) {
                                com.example.util.AppStrings.trText(
                                    "Saved '${video.title}' to Movies/GMaxRecordings",
                                    "Guardado '${video.title}' en Movies/GMaxRecordings"
                                )
                            } else {
                                com.example.util.AppStrings.trText(
                                    "Could not export video",
                                    "No se pudo exportar el video"
                                )
                            }
                            if (saved) {
                                com.example.util.UnityAdsManager.maybeTriggerPostActionAd(
                                    context = context,
                                    actionLabelEn = "Video Saved",
                                    actionLabelEs = "Video Guardado"
                                )
                            }
                        },
                        onShareVideo = { video ->
                            val shareUri = ScreenRecordingManager.getShareUri(context, video)
                            if (shareUri != null) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "video/mp4"
                                    putExtra(Intent.EXTRA_STREAM, shareUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Gameplay Clip"))
                            }
                        },
                        onDeleteVideo = { videoToDelete = it },
                        onRestorePreviousVideos = {
                            restoreCapturesLauncher.launch(arrayOf("video/*"))
                        },
                        onReplaySaved = { replayResult ->
                            refreshMediaLists()
                            userMessage = com.example.util.AppStrings.trText(
                                replayResult.messageEn,
                                replayResult.messageEs
                            )
                        }
                    )

                    1 -> ScreenshotsTabContent(
                        screenshots = screenshots,
                        onCaptureScreenshot = {
                            if (onRequestScreenshotProjection != null) {
                                onRequestScreenshotProjection()
                            } else {
                                val intent = Intent(context, com.example.MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    putExtra("extra_trigger_screenshot", true)
                                }
                                context.startActivity(intent)
                            }
                        },
                        onPreviewScreenshot = { selectedScreenshotForPreview = it },
                        onEditScreenshot = { selectedScreenshotForEdit = it },
                        onShareScreenshot = { shot ->
                            val shareUri = ScreenRecordingManager.getScreenshotShareUri(context, shot)
                            if (shareUri != null) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, shareUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Screenshot"))
                            }
                        },
                        onDeleteScreenshot = { screenshotToDelete = it },
                        onRestorePreviousScreenshots = {
                            restoreCapturesLauncher.launch(arrayOf("image/*"))
                        }
                    )

                    2 -> RecorderSettingsTabContent(
                        settings = settings,
                        supportedResolutions = supportedResolutions,
                        supportedFps = supportedFps,
                        isInAppRecorderEnabled = isInAppRecorderEnabled,
                        isOverlayBubbleEnabled = isOverlayBubbleEnabled,
                        isDeviceOnline = isDeviceOnline,
                        onToggleInAppRecorder = { enable ->
                            isInAppRecorderEnabled = enable
                            ScreenRecordingManager.setInAppRecorderEnabled(context, enable)
                            userMessage = if (enable) {
                                com.example.util.AppStrings.trText(
                                    "App screen recorder controls enabled",
                                    "Controles de grabación en la app activados"
                                )
                            } else {
                                com.example.util.AppStrings.trText(
                                    "App Record button hidden (Floating Bubble can still be used independently)",
                                    "Botón Grabar oculto (la Burbuja Flotante sigue independiente)"
                                )
                            }
                        },
                        onUpdateSettings = { updated ->
                            val effective = if (!isDeviceOnline) {
                                updated.copy(
                                    resolutionLabel = "720p",
                                    width = 1280,
                                    height = 720,
                                    fps = 60,
                                    bitrateMbps = 8
                                )
                            } else {
                                updated
                            }
                            settings = effective
                            if (isDeviceOnline) {
                                ScreenRecordingManager.saveSettings(context, effective)
                                viewModel.scheduleCloudSettingsSync()
                            }
                        },
                        onRequestAudioPermissionForMode = { desiredMode ->
                            pendingAudioMode = desiredMode
                            pendingRecordLaunchAfterPerm = false
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onToggleOverlayBubble = { enable ->
                            if (!Settings.canDrawOverlays(context)) {
                                try {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                } catch (_: Exception) {}
                            } else {
                                isOverlayBubbleEnabled = enable
                                settings = settings.copy(isOverlayEnabled = enable)
                                ScreenRecordingManager.setOverlayEnabled(context, enable)
                                viewModel.scheduleCloudSettingsSync()
                                val svcIntent = Intent(context, ScreenRecordOverlayService::class.java).apply {
                                    action = if (enable) {
                                        ScreenRecordOverlayService.ACTION_START
                                    } else {
                                        ScreenRecordOverlayService.ACTION_STOP
                                    }
                                }
                                context.startService(svcIntent)
                            }
                        },
                        onResetBubblePosition = {
                            ScreenRecordingManager.resetBubblePosition(context)
                            if (isOverlayBubbleEnabled && Settings.canDrawOverlays(context)) {
                                try {
                                    context.startService(
                                        Intent(context, ScreenRecordOverlayService::class.java).apply {
                                            action = ScreenRecordOverlayService.ACTION_STOP
                                        }
                                    )
                                    context.startService(
                                        Intent(context, ScreenRecordOverlayService::class.java).apply {
                                            action = ScreenRecordOverlayService.ACTION_START
                                        }
                                    )
                                } catch (_: Exception) {}
                            }
                            userMessage = com.example.util.AppStrings.trText(
                                "Floating bubble position reset to default",
                                "Posición de la burbuja flotante restablecida"
                            )
                        },
                        onAutoConfigure = {
                            val optimal = ScreenRecordingManager.getAutoConfiguredIdealSettings(context)
                                .copy(isOverlayEnabled = isOverlayBubbleEnabled)
                            settings = optimal
                            ScreenRecordingManager.saveSettings(context, optimal)
                            viewModel.scheduleCloudSettingsSync()
                            userMessage = com.example.util.AppStrings.autoTranslateDynamic(
                                "Tuned to ${optimal.resolutionLabel} @ ${optimal.fps} FPS (${optimal.bitrateMbps} Mbps • ${optimal.audioSourceMode.label})"
                            )
                        },
                        blackoutState = blackoutState,
                        onToggleBlackoutEnabled = handleToggleBlackout,
                        onSpawnBlackoutPreset = handleSpawnBlackoutPreset,
                        onToggleBlackoutManualLock = handleToggleBlackoutManualLock,
                        onToggleBlackoutPureBlack = handleToggleBlackoutPureBlack,
                        onRemoveBlackoutBox = handleRemoveBlackoutBox,
                        onClearAllBlackoutBoxes = handleClearAllBlackoutBoxes
                    )
                }

                // Floating Coral-Red "[ 🎥 Record ]" Pill Button in bottom-right (independent of Floating Bubble)
                if (isInAppRecorderEnabled || isRecording) {
                    var recordFabFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = {
                            if (isRecording) {
                                val stopIntent = Intent(context, ScreenRecordingService::class.java).apply {
                                    action = ScreenRecordingService.ACTION_STOP
                                }
                                context.startService(stopIntent)
                                userMessage = com.example.util.AppStrings.trText(
                                    "Gameplay recording saved to Videos!",
                                    "¡Grabación de partida guardada en Videos!"
                                )
                                com.example.util.UnityAdsManager.maybeTriggerPostActionAd(
                                    context = context,
                                    actionLabelEn = "Screen Recording",
                                    actionLabelEs = "Grabación de Pantalla"
                                )
                            } else {
                                settings = ScreenRecordingManager.loadSettings(context)
                                onRequestMediaProjection()
                            }
                        },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RecorderCoral,
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 8.dp,
                            pressedElevation = 12.dp
                        ),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = 16.dp)
                            .onFocusChanged { recordFabFocused = it.isFocused }
                            .border(
                                width = if (recordFabFocused) 3.dp else 0.dp,
                                color = if (recordFabFocused) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(50)
                            )
                            .testTag("recorder_floating_record_button")
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Videocam,
                            contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isRecording) {
                                com.example.util.tr("Stop", "Detener")
                            } else {
                                com.example.util.tr("Record", "Grabar")
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 4. Bottom Storage Capacity Progress Bar & Labels (Matches screenshot #2: "12.72 GB available" | "(03:47:23 can be recorded)")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // Coral-red thin progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(storageInfo.usedRatio.coerceIn(0.08f, 0.95f))
                            .fillMaxHeight()
                            .background(RecorderCoral)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = com.example.util.AppStrings.autoTranslateDynamic(storageInfo.availableGbText),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    )
                    Text(
                        text = com.example.util.AppStrings.autoTranslateDynamic(storageInfo.recordableTimeText),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }
    }

    // Video Player Modal
    selectedVideoForPlayback?.let { video ->
        VideoPlayerModalDialog(
            video = video,
            onTrimClip = {
                selectedVideoForPlayback = null
                selectedVideoForTrimming = video
            },
            onDismiss = {
                selectedVideoForPlayback = null
                com.example.util.UnityAdsManager.maybeTriggerPostActionAd(
                    context = context,
                    actionLabelEn = "Video Preview",
                    actionLabelEs = "Vista Previa de Video"
                )
            }
        )
    }

    // Local Clip Trimmer Studio Modal
    selectedVideoForTrimming?.let { video ->
        LocalClipTrimmerModalDialog(
            video = video,
            onDismiss = { selectedVideoForTrimming = null },
            onTrimCompleted = { trimResult ->
                selectedVideoForTrimming = null
                refreshMediaLists()
                userMessage = com.example.util.AppStrings.trText(
                    trimResult.messageEn,
                    trimResult.messageEs
                )
                com.example.util.UnityAdsManager.maybeTriggerPostActionAd(
                    context = context,
                    actionLabelEn = "Clip Trimmed",
                    actionLabelEs = "Clip Recortado"
                )
            }
        )
    }

    // Confirm Delete Video Dialog
    videoToDelete?.let { video ->
        AlertDialog(
            onDismissRequest = { videoToDelete = null },
            title = { Text(com.example.util.tr("Delete Recording?", "¿Eliminar Grabación?")) },
            text = { Text(com.example.util.tr("Delete '${video.title}' permanently from your device?", "¿Eliminar '${video.title}' permanentemente de tu dispositivo?")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        ScreenRecordingManager.deleteRecording(context, video)
                        videoToDelete = null
                        refreshMediaLists()
                    }
                ) {
                    Text(com.example.util.tr("Delete", "Eliminar"), color = RecorderCoral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { videoToDelete = null }) {
                    Text(com.example.util.tr("Cancel", "Cancelar"))
                }
            }
        )
    }

    // Screenshot Preview Modal
    selectedScreenshotForPreview?.let { shot ->
        ScreenshotPreviewDialog(
            screenshot = shot,
            onEditScreenshot = {
                selectedScreenshotForPreview = null
                selectedScreenshotForEdit = shot
            },
            onDismiss = { selectedScreenshotForPreview = null }
        )
    }

    // Screenshot Studio Editor Modal (Cut / Crop & 6 Gaming Color Filters)
    selectedScreenshotForEdit?.let { shotToEdit ->
        com.example.ui.components.ScreenshotEditorModalDialog(
            screenshot = shotToEdit,
            onEditSaved = { saveResult ->
                selectedScreenshotForEdit = null
                refreshMediaLists()
                userMessage = com.example.util.AppStrings.trText(
                    saveResult.messageEn,
                    saveResult.messageEs
                )
            },
            onDismiss = { selectedScreenshotForEdit = null }
        )
    }

    // Confirm Delete Screenshot Dialog
    screenshotToDelete?.let { shot ->
        AlertDialog(
            onDismissRequest = { screenshotToDelete = null },
            title = { Text(com.example.util.tr("Delete Screenshot?", "¿Eliminar Captura?")) },
            text = { Text(com.example.util.tr("Remove '${shot.title}' from your Screenshots library?", "¿Eliminar '${shot.title}' de tu biblioteca de capturas?")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        ScreenRecordingManager.deleteScreenshot(context, shot)
                        screenshotToDelete = null
                        refreshMediaLists()
                    }
                ) {
                    Text(com.example.util.tr("Delete", "Eliminar"), color = RecorderCoral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { screenshotToDelete = null }) {
                    Text(com.example.util.tr("Cancel", "Cancelar"))
                }
            }
        )
    }
}

/**
 * 3-Icon Tab Strip matching screenshot #2 (Videos, Screenshots, Settings) with Coral-Red active icon and underline
 */
@Composable
private fun RecorderThreeTabStrip(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    val tabs = listOf(
        Triple(0, "Videos", Icons.Default.Slideshow),
        Triple(1, "Screenshots", Icons.Default.Image),
        Triple(2, "Settings", Icons.Default.Settings)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { (index, label, icon) ->
            val isSelected = selectedTab == index
            var isFocused by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isFocused = it.isFocused }
                    .clickable { onSelectTab(index) }
                    .focusable()
                    .padding(top = 8.dp)
                    .testTag("recorder_tab_${label.lowercase()}"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected || isFocused) {
                        RecorderCoral
                    } else {
                        RecorderCoral.copy(alpha = 0.35f)
                    },
                    modifier = Modifier.size(26.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Active Coral Underline Indicator
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (isSelected) RecorderCoral else Color.Transparent
                        )
                )
            }
        }
    }
}

@Composable
private fun VideosTabContent(
    recordings: List<RecordedVideo>,
    blackoutState: StreamerBlackoutState,
    onToggleBlackoutEnabled: (Boolean) -> Unit,
    onSpawnBlackoutPreset: (BlackoutPresetType) -> Unit,
    onToggleBlackoutManualLock: (Boolean) -> Unit,
    onClearAllBlackoutBoxes: () -> Unit,
    onPlayVideo: (RecordedVideo) -> Unit,
    onTrimVideo: (RecordedVideo) -> Unit,
    onSaveToGallery: (RecordedVideo) -> Unit,
    onShareVideo: (RecordedVideo) -> Unit,
    onDeleteVideo: (RecordedVideo) -> Unit,
    onRestorePreviousVideos: () -> Unit = {},
    onReplaySaved: (com.example.util.InstantReplaySaveResult) -> Unit = {}
) {
    val context = LocalContext.current
    val groupedVideos = remember(recordings) {
        ScreenRecordingManager.groupVideosByGame(recordings)
    }
    var selectedGameFilter by remember { mutableStateOf<String?>(null) }

    // Reset filter if that game's last video was deleted
    LaunchedEffect(groupedVideos) {
        if (selectedGameFilter != null && groupedVideos.none { it.gameName == selectedGameFilter }) {
            selectedGameFilter = null
        }
    }

    val visibleGroups = remember(groupedVideos, selectedGameFilter) {
        if (selectedGameFilter == null) groupedVideos
        else groupedVideos.filter { it.gameName == selectedGameFilter }
    }

    if (recordings.isEmpty()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 110.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Slideshow,
                        contentDescription = null,
                        tint = RecorderCoral.copy(alpha = 0.35f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = com.example.util.tr("No videos have been recorded", "No se han grabado videos"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = com.example.util.tr(
                            "Tap the coral Record button below or use the floating bubble during gameplay. Reinstalled GMax? Restore previous recordings below.",
                            "Toca el botón coral Grabar abajo o usa la burbuja flotante. ¿Reinstalaste GMax? Restaura grabaciones previas abajo."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = onRestorePreviousVideos,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, HubBlue.copy(alpha = 0.6f)),
                        modifier = Modifier.testTag("restore_previous_videos_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = HubBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = com.example.util.tr("Restore Previous Recordings", "Restaurar Grabaciones Previas"),
                            color = HubBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Unity Banner Card placed right below the empty-state text
            item {
                com.example.ui.components.GMaxUnityBannerCard(
                    placementTag = "recorder_videos_empty",
                    topBuffer = 28.dp,
                    bottomBuffer = 36.dp
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("videos_grouped_gallery"),
            contentPadding = PaddingValues(top = 12.dp, bottom = 112.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Game Filter Chip Bar
            item {
                GameMediaFilterBar(
                    groups = groupedVideos.map { Triple(it.gameName, it.gamePackage, it.items.size) },
                    totalCount = recordings.size,
                    selectedGameName = selectedGameFilter,
                    onSelectGame = { gameName ->
                        ConsoleHapticManager.playSelectClick(context)
                        selectedGameFilter = gameName
                    }
                )
            }

            var cumulativeVideoCount = 0
            visibleGroups.forEach { group ->
                item(key = "video_group_header_${group.gameName}") {
                    GameMediaGroupHeader(
                        gameName = group.gameName,
                        gamePackage = group.gamePackage,
                        itemCount = group.items.size,
                        unitLabel = if (group.items.size == 1) {
                            com.example.util.tr("clip", "clip")
                        } else {
                            com.example.util.tr("clips", "clips")
                        }
                    )
                }

                group.items.forEach { video ->
                    cumulativeVideoCount++
                    val currentIndex = cumulativeVideoCount
                    item(key = "video_item_${video.id}") {
                        RecordedVideoItemCard(
                            video = video,
                            onPlay = {
                                ConsoleHapticManager.playSelectClick(context)
                                onPlayVideo(video)
                            },
                            onTrim = {
                                ConsoleHapticManager.playSelectClick(context)
                                onTrimVideo(video)
                            },
                            onSaveToGallery = {
                                ConsoleHapticManager.playSelectClick(context)
                                onSaveToGallery(video)
                            },
                            onShare = {
                                ConsoleHapticManager.playSelectClick(context)
                                onShareVideo(video)
                            },
                            onDelete = {
                                ConsoleHapticManager.playSelectClick(context)
                                onDeleteVideo(video)
                            }
                        )
                    }

                    // Insert a Unity Banner Card after every 3 to 4 recorded video cards
                    if (currentIndex % 4 == 0) {
                        item(key = "recorder_native_ad_$currentIndex") {
                            com.example.ui.components.GMaxUnityBannerCard(
                                placementTag = "recorder_videos_feed_$currentIndex",
                                topBuffer = 24.dp,
                                bottomBuffer = 24.dp
                            )
                        }
                    }
                }
            }

            if (cumulativeVideoCount in 1..3) {
                item(key = "recorder_native_ad_tail") {
                    com.example.ui.components.GMaxUnityBannerCard(
                        placementTag = "recorder_videos_tail",
                        topBuffer = 28.dp,
                        bottomBuffer = 32.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun GameMediaFilterBar(
    groups: List<Triple<String, String?, Int>>,
    totalCount: Int,
    selectedGameName: String?,
    onSelectGame: (String?) -> Unit
) {
    val context = LocalContext.current
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("media_game_filter_bar"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp)
    ) {
        item {
            var allFocused by remember { mutableStateOf(false) }
            FilterChip(
                selected = selectedGameName == null,
                onClick = { onSelectGame(null) },
                label = {
                    Text(
                        text = com.example.util.tr("All Games ($totalCount)", "Todos los Juegos ($totalCount)"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = RecorderCoral.copy(alpha = 0.18f),
                    selectedLabelColor = RecorderCoral
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedGameName == null,
                    borderColor = if (allFocused) Color.White else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    selectedBorderColor = if (allFocused) Color.White else RecorderCoral,
                    borderWidth = if (allFocused) 2.dp else 1.dp,
                    selectedBorderWidth = if (allFocused) 2.dp else 1.5.dp
                ),
                modifier = Modifier.onFocusChanged {
                    if (it.isFocused && !allFocused) {
                        ConsoleHapticManager.playFocusTick(context)
                    }
                    allFocused = it.isFocused
                }
            )
        }

        items(groups, key = { it.first }) { (gameName, _, count) ->
            val isSelected = selectedGameName == gameName
            var chipFocused by remember { mutableStateOf(false) }
            FilterChip(
                selected = isSelected,
                onClick = { onSelectGame(if (isSelected) null else gameName) },
                label = {
                    Text(
                        text = "$gameName ($count)",
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = HubBlue.copy(alpha = 0.2f),
                    selectedLabelColor = HubBlue
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = if (chipFocused) Color.White else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    selectedBorderColor = if (chipFocused) Color.White else HubBlue,
                    borderWidth = if (chipFocused) 2.dp else 1.dp,
                    selectedBorderWidth = if (chipFocused) 2.dp else 1.5.dp
                ),
                modifier = Modifier.onFocusChanged {
                    if (it.isFocused && !chipFocused) {
                        ConsoleHapticManager.playFocusTick(context)
                    }
                    chipFocused = it.isFocused
                }
            )
        }
    }
}

@Composable
private fun GameMediaGroupHeader(
    gameName: String,
    gamePackage: String?,
    itemCount: Int,
    unitLabel: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!gamePackage.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                ) {
                    AppIconImage(
                        packageName = gamePackage,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                com.example.ui.components.GMaxBrandIcon(
                    size = 28.dp,
                    cornerRadius = 8.dp,
                    showGlowBorder = true,
                    glowColor = HubBlue
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = gameName.uppercase(Locale.US),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.7.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Text(
                text = "$itemCount $unitLabel",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecordedVideoItemCard(
    video: RecordedVideo,
    onPlay: () -> Unit,
    onTrim: () -> Unit,
    onSaveToGallery: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val thumbnail = remember(video.filePath, video.uriString) {
        try {
            val f = File(video.filePath)
            if (f.exists()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ThumbnailUtils.createVideoThumbnail(f, Size(320, 180), null)
                } else {
                    @Suppress("DEPRECATION")
                    ThumbnailUtils.createVideoThumbnail(
                        video.filePath,
                        MediaStore.Images.Thumbnails.MINI_KIND
                    )
                }
            } else if (video.uriString.startsWith("content://") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(Uri.parse(video.uriString), Size(320, 180), null)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    var isFocused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged {
                if (it.isFocused && !isFocused) {
                    ConsoleHapticManager.playFocusTick(context)
                }
                isFocused = it.isFocused
            }
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) Color.White else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onPlay() }
            .focusable(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail Preview with Play badge
            Box(
                modifier = Modifier
                    .width(102.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = video.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(RecorderCoral.copy(alpha = 0.88f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = video.gameName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = HubBlue,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${video.formattedDuration} • ${video.formattedSize} • ${video.resolution}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onTrim,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("trim_video_button_${video.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCut,
                    contentDescription = "Trim Clip",
                    tint = LaunchGreen,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(onClick = onSaveToGallery, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Save to Gallery",
                    tint = HubBlue,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = RecorderCoral,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ScreenshotsTabContent(
    screenshots: List<CapturedScreenshot>,
    onCaptureScreenshot: () -> Unit,
    onPreviewScreenshot: (CapturedScreenshot) -> Unit,
    onEditScreenshot: (CapturedScreenshot) -> Unit = {},
    onShareScreenshot: (CapturedScreenshot) -> Unit,
    onDeleteScreenshot: (CapturedScreenshot) -> Unit,
    onRestorePreviousScreenshots: () -> Unit = {}
) {
    val context = LocalContext.current
    val groupedScreenshots = remember(screenshots) {
        ScreenRecordingManager.groupScreenshotsByGame(screenshots)
    }
    var selectedGameFilter by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(groupedScreenshots) {
        if (selectedGameFilter != null && groupedScreenshots.none { it.gameName == selectedGameFilter }) {
            selectedGameFilter = null
        }
    }

    val visibleGroups = remember(groupedScreenshots, selectedGameFilter) {
        if (selectedGameFilter == null) groupedScreenshots
        else groupedScreenshots.filter { it.gameName == selectedGameFilter }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top Quick Action Bar to capture a screenshot right now
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = com.example.util.tr("Real Screen Capture", "Captura de Pantalla Real"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = com.example.util.tr(
                            "Capture lossless PNG frames automatically grouped by active game",
                            "Captura cuadros PNG sin pérdida agrupados automáticamente por juego activo"
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = {
                        ConsoleHapticManager.playSelectClick(context)
                        onCaptureScreenshot()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RecorderCoral,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("capture_screenshot_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(com.example.util.tr("Capture", "Capturar"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (screenshots.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = RecorderCoral.copy(alpha = 0.35f),
                        modifier = Modifier.size(60.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = com.example.util.tr("No screenshots captured yet", "Aún no se han tomado capturas"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = com.example.util.tr(
                            "Tap Capture above or restore previous GMax screenshots below.",
                            "Toca Capturar arriba o restaura capturas previas de GMax abajo."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = onRestorePreviousScreenshots,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, HubBlue.copy(alpha = 0.6f)),
                        modifier = Modifier.testTag("restore_previous_screenshots_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = HubBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = com.example.util.tr("Restore Previous Screenshots", "Restaurar Capturas Previas"),
                            color = HubBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .testTag("screenshots_grouped_gallery"),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Game Filter Chip Bar
                item {
                    GameMediaFilterBar(
                        groups = groupedScreenshots.map { Triple(it.gameName, it.gamePackage, it.items.size) },
                        totalCount = screenshots.size,
                        selectedGameName = selectedGameFilter,
                        onSelectGame = { gameName ->
                            ConsoleHapticManager.playSelectClick(context)
                            selectedGameFilter = gameName
                        }
                    )
                }

                visibleGroups.forEach { group ->
                    item(key = "shot_group_header_${group.gameName}") {
                        GameMediaGroupHeader(
                            gameName = group.gameName,
                            gamePackage = group.gamePackage,
                            itemCount = group.items.size,
                            unitLabel = if (group.items.size == 1) {
                                com.example.util.tr("shot", "captura")
                            } else {
                                com.example.util.tr("shots", "capturas")
                            }
                        )
                    }

                    items(group.items, key = { it.id }) { shot ->
                        val bmp = remember(shot.filePath, shot.uriString) {
                            try {
                                val f = File(shot.filePath)
                                if (f.exists()) {
                                    BitmapFactory.decodeFile(shot.filePath)
                                } else if (shot.uriString.startsWith("content://")) {
                                    context.contentResolver.openInputStream(Uri.parse(shot.uriString))?.use {
                                        BitmapFactory.decodeStream(it)
                                    }
                                } else {
                                    null
                                }
                            } catch (_: Exception) {
                                null
                            }
                        }

                        var shotFocused by remember { mutableStateOf(false) }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged {
                                    if (it.isFocused && !shotFocused) {
                                        ConsoleHapticManager.playFocusTick(context)
                                    }
                                    shotFocused = it.isFocused
                                }
                                .border(
                                    width = if (shotFocused) 2.5.dp else 1.dp,
                                    color = if (shotFocused) Color.White else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    ConsoleHapticManager.playSelectClick(context)
                                    onPreviewScreenshot(shot)
                                }
                                .focusable(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(96.dp)
                                        .height(60.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = shot.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = null,
                                            tint = RecorderCoral
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = shot.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = shot.gameName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = HubBlue,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${shot.resolution} • ${shot.formattedSize}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        ConsoleHapticManager.playSelectClick(context)
                                        onEditScreenshot(shot)
                                    },
                                    modifier = Modifier.testTag("edit_screenshot_button_${shot.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCut,
                                        contentDescription = "Edit Screenshot (Cut & Filter)",
                                        tint = LaunchGreen
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        ConsoleHapticManager.playSelectClick(context)
                                        onShareScreenshot(shot)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share Screenshot",
                                        tint = HubBlue
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        ConsoleHapticManager.playSelectClick(context)
                                        onDeleteScreenshot(shot)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Screenshot",
                                        tint = RecorderCoral
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Anchored Unity Banner pinned to the bottom of the Screenshots tab
        com.example.ui.components.GMaxUnityAnchoredBanner(
            placementTag = "screenshots_anchored_bottom",
            topBuffer = 8.dp,
            initialLoadDelayMs = 250L,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        )
    }
}

@Composable
private fun RecorderSettingsTabContent(
    settings: VideoQualitySettings,
    supportedResolutions: List<ResolutionPreset>,
    supportedFps: List<Int>,
    isInAppRecorderEnabled: Boolean,
    isOverlayBubbleEnabled: Boolean,
    isDeviceOnline: Boolean = true,
    onToggleInAppRecorder: (Boolean) -> Unit,
    onUpdateSettings: (VideoQualitySettings) -> Unit,
    onRequestAudioPermissionForMode: (AudioSourceMode) -> Unit,
    onToggleOverlayBubble: (Boolean) -> Unit,
    onResetBubblePosition: () -> Unit,
    onAutoConfigure: () -> Unit,
    blackoutState: StreamerBlackoutState = StreamerBlackoutState(),
    onToggleBlackoutEnabled: (Boolean) -> Unit = {},
    onSpawnBlackoutPreset: (BlackoutPresetType) -> Unit = {},
    onToggleBlackoutManualLock: (Boolean) -> Unit = {},
    onToggleBlackoutPureBlack: (Boolean) -> Unit = {},
    onRemoveBlackoutBox: (String) -> Unit = {},
    onClearAllBlackoutBoxes: () -> Unit = {}
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 96.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Auto-Tune Hardware Button
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = com.example.util.tr("Smart Encoder Tuning", "Ajuste Inteligente del Codificador"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isDeviceOnline) {
                                com.example.util.tr(
                                    "Automatically match resolution, FPS, and bitrate to your hardware",
                                    "Ajusta automáticamente resolución, FPS y tasa de bits a tu hardware"
                                )
                            } else {
                                com.example.util.tr(
                                    "Offline Mode active: Locked to 720p • 60 FPS • 8 Mbps",
                                    "Modo sin conexión activo: Fijado en 720p • 60 FPS • 8 Mbps"
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onAutoConfigure,
                        enabled = isDeviceOnline,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RecorderCoral,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(com.example.util.tr("Smart Tune", "Ajustar"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // 2. Resolution Presets
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = com.example.util.tr("Video Resolution", "Resolución de Video"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(supportedResolutions) { preset ->
                            val isSelected = settings.resolutionLabel == preset.label
                            FilterChip(
                                selected = isSelected,
                                enabled = isDeviceOnline || preset.label == "720p",
                                onClick = {
                                    if (isDeviceOnline) {
                                        onUpdateSettings(
                                            settings.copy(
                                                resolutionLabel = preset.label,
                                                width = preset.width,
                                                height = preset.height
                                            )
                                        )
                                    }
                                },
                                label = { Text("${preset.label} (${preset.width}x${preset.height})") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RecorderCoral.copy(alpha = 0.18f),
                                    selectedLabelColor = RecorderCoral
                                )
                            )
                        }
                    }
                }
            }
        }

        // 3. Frame Rate (FPS) & Bitrate
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = com.example.util.tr("Frame Rate (FPS)", "Cuadros por Segundo (FPS)"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        supportedFps.forEach { fps ->
                            val isSelected = settings.fps == fps
                            FilterChip(
                                selected = isSelected,
                                enabled = isDeviceOnline || fps == 60,
                                onClick = {
                                    if (isDeviceOnline) {
                                        onUpdateSettings(settings.copy(fps = fps))
                                    }
                                },
                                label = { Text(com.example.util.AppStrings.autoTranslateDynamic("$fps FPS")) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RecorderCoral.copy(alpha = 0.18f),
                                    selectedLabelColor = RecorderCoral
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = com.example.util.tr("Video Bitrate", "Tasa de Bits de Video"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${settings.bitrateMbps} Mbps",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = RecorderCoral
                        )
                    }

                    Slider(
                        value = settings.bitrateMbps.toFloat(),
                        onValueChange = {
                            if (isDeviceOnline) {
                                onUpdateSettings(settings.copy(bitrateMbps = it.toInt()))
                            }
                        },
                        enabled = isDeviceOnline,
                        valueRange = 4f..24f,
                        steps = 9,
                        colors = SliderDefaults.colors(
                            thumbColor = RecorderCoral,
                            activeTrackColor = RecorderCoral
                        )
                    )
                }
            }
        }

        // 4A. Dedicated Screen Recording Settings Card (Independent of Floating Bubble)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recorder_screen_recording_settings_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, RecorderCoral.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = com.example.util.tr("SCREEN RECORDING SETTINGS", "AJUSTES DE GRABACIÓN DE PANTALLA"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = RecorderCoral,
                        letterSpacing = 0.8.sp
                    )

                    // Independent In-App Recorder Controls Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Videocam, contentDescription = null, tint = RecorderCoral)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = com.example.util.tr("App Screen Recorder Controls", "Controles de Grabación en la App"),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = com.example.util.tr(
                                        "Show the Record action button inside Recorder tab (independent of Floating Bubble)",
                                        "Mostrar el botón Grabar en la pestaña Grabador (independiente de la Burbuja Flotante)"
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isInAppRecorderEnabled,
                            onCheckedChange = onToggleInAppRecorder,
                            colors = SwitchDefaults.colors(checkedTrackColor = RecorderCoral),
                            modifier = Modifier.testTag("recorder_in_app_toggle")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // Selectable Audio Capture Source (Internal Audio, Internal + Mic, Mic Only, Mute)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (settings.audioSourceMode == AudioSourceMode.MUTE) {
                                    Icons.AutoMirrored.Filled.VolumeOff
                                } else {
                                    Icons.AutoMirrored.Filled.VolumeUp
                                },
                                contentDescription = null,
                                tint = RecorderCoral
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = com.example.util.tr("Audio Capture Source", "Fuente de Captura de Audio"),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = com.example.util.AppStrings.autoTranslateDynamic(settings.audioSourceMode.description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(AudioSourceMode.entries.toList()) { mode ->
                                val isSelected = settings.audioSourceMode == mode
                                val tag = when (mode) {
                                    AudioSourceMode.INTERNAL_ONLY -> "audio_source_internal_chip"
                                    AudioSourceMode.INTERNAL_AND_MIC -> "audio_source_both_chip"
                                    AudioSourceMode.MIC_ONLY -> "audio_source_mic_chip"
                                    AudioSourceMode.MUTE -> "audio_source_mute_chip"
                                }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (mode != AudioSourceMode.MUTE &&
                                            ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            onRequestAudioPermissionForMode(mode)
                                        } else {
                                            onUpdateSettings(
                                                settings.copy(
                                                    audioSourceMode = mode,
                                                    recordMic = mode == AudioSourceMode.INTERNAL_AND_MIC ||
                                                        mode == AudioSourceMode.MIC_ONLY
                                                )
                                            )
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = com.example.util.AppStrings.autoTranslateDynamic(mode.label),
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                        )
                                    },
                                    leadingIcon = {
                                        val icon = when (mode) {
                                            AudioSourceMode.INTERNAL_ONLY -> Icons.AutoMirrored.Filled.VolumeUp
                                            AudioSourceMode.INTERNAL_AND_MIC -> Icons.Default.Mic
                                            AudioSourceMode.MIC_ONLY -> Icons.Default.Mic
                                            AudioSourceMode.MUTE -> Icons.AutoMirrored.Filled.VolumeOff
                                        }
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = RecorderCoral.copy(alpha = 0.18f),
                                        selectedLabelColor = RecorderCoral,
                                        selectedLeadingIconColor = RecorderCoral
                                    ),
                                    modifier = Modifier.testTag(tag)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // Timer HUD on Screen
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = RecorderCoral)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = com.example.util.tr("Screen Timer Pill", "Temporizador en Pantalla"),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = com.example.util.tr(
                                        "Show compact elapsed time pill during active recording",
                                        "Mostrar píldora de tiempo transcurrido durante la grabación"
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = settings.showTimerHud,
                            onCheckedChange = { onUpdateSettings(settings.copy(showTimerHud = it)) },
                            colors = SwitchDefaults.colors(checkedTrackColor = RecorderCoral)
                        )
                    }
                }
            }
        }

        // 4B. Dedicated Floating Bubble Settings Card (Independent of Screen Recording)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recorder_floating_bubble_settings_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, HubBlue.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = com.example.util.tr("FLOATING BUBBLE SETTINGS", "AJUSTES DE BURBUJA FLOTANTE"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = HubBlue,
                        letterSpacing = 0.8.sp
                    )

                    // Floating Radial Bubble & Notification Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Layers, contentDescription = null, tint = HubBlue)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = com.example.util.tr("Floating Radial Bubble Overlay", "Superposición de Burbuja Radial Flotante"),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = com.example.util.tr(
                                        "Enable or remove the floating bubble independently of screen recording",
                                        "Activa o elimina la burbuja flotante independientemente de la grabación"
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isOverlayBubbleEnabled,
                            onCheckedChange = onToggleOverlayBubble,
                            colors = SwitchDefaults.colors(checkedTrackColor = HubBlue),
                            modifier = Modifier.testTag("recorder_floating_bubble_toggle")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // Floating Bubble Size Adjuster (28dp to 64dp with live preview)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = com.example.util.tr("Floating Bubble Size", "Tamaño de Burbuja Flotante"),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${settings.bubbleSizeDp} dp (${com.example.util.tr("Compact 28dp to Large 64dp", "Compacta 28dp a Grande 64dp")})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HubBlue,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            // Live 1:1 Scale Circular Bubble Preview (no gray box)
                            Box(
                                modifier = Modifier
                                    .size(settings.bubbleSizeDp.coerceIn(28, 64).dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1D4ED8))
                                    .border(2.dp, Color(0xFF38BDF8), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = "Live Bubble Preview",
                                    tint = Color.White,
                                    modifier = Modifier.size((settings.bubbleSizeDp * 0.5f).dp)
                                )
                            }
                        }
                        Slider(
                            value = settings.bubbleSizeDp.toFloat(),
                            onValueChange = { newValue ->
                                val clamped = newValue.toInt().coerceIn(28, 64)
                                val updated = settings.copy(bubbleSizeDp = clamped)
                                onUpdateSettings(updated)
                                if (isOverlayBubbleEnabled && Settings.canDrawOverlays(context)) {
                                    try {
                                        val updateIntent = Intent(context, ScreenRecordOverlayService::class.java).apply {
                                            action = ScreenRecordOverlayService.ACTION_UPDATE_SIZE
                                        }
                                        context.startService(updateIntent)
                                    } catch (_: Exception) {}
                                }
                            },
                            valueRange = 28f..64f,
                            colors = SliderDefaults.colors(
                                thumbColor = HubBlue,
                                activeTrackColor = HubBlue
                            ),
                            modifier = Modifier.testTag("floating_bubble_size_slider")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = onResetBubblePosition,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, HubBlue.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("reset_bubble_position_button")
                        ) {
                            Text(
                                text = com.example.util.tr("Reset Bubble Position", "Restablecer Posición de Burbuja"),
                                color = HubBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // 4C. Streamer Blackout Boxes Settings Card (Anti-Stream Snipe Privacy Shields)
        item {
            StreamerBlackoutSettingsCard(
                blackoutState = blackoutState,
                onToggleEnabled = onToggleBlackoutEnabled,
                onSpawnPreset = onSpawnBlackoutPreset,
                onToggleManualLock = onToggleBlackoutManualLock,
                onTogglePurePitchBlack = onToggleBlackoutPureBlack,
                onRemoveBox = onRemoveBlackoutBox,
                onClearAll = onClearAllBlackoutBoxes
            )
        }
    }
}

@Composable
private fun ScreenshotPreviewDialog(
    screenshot: CapturedScreenshot,
    onEditScreenshot: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val bmp = remember(screenshot.filePath, screenshot.uriString) {
        try {
            val f = File(screenshot.filePath)
            if (f.exists()) {
                BitmapFactory.decodeFile(screenshot.filePath)
            } else if (screenshot.uriString.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(screenshot.uriString))?.use {
                    BitmapFactory.decodeStream(it)
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = screenshot.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = screenshot.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${screenshot.resolution} • ${screenshot.formattedSize}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = onEditScreenshot,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, LaunchGreen),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("preview_edit_screenshot_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCut,
                            contentDescription = null,
                            tint = LaunchGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = com.example.util.tr("Cut & Color Filter", "Recortar y Filtro"),
                            color = LaunchGreen,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Enhanced In-App Video Player Dialog using hardware-accelerated TextureView + MediaPlayer.
 * Dynamically adapts to Portrait (9:16 / 9:20) and Landscape (16:9 / 20:9) video dimensions
 * with zero black corner artifacts, interactive scrubbing seek bar, -10s/+10s skip,
 * playback speed control (0.5x, 1.0x, 1.5x, 2.0x), audio mute toggle, and Fit/Fill scaling.
 */
@Composable
fun VideoPlayerModalDialog(
    video: RecordedVideo,
    onTrimClip: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isFullscreen by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var isFillCrop by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var currentPosMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf((video.durationMs.toInt()).coerceAtLeast(1000)) }
    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableStateOf(0f) }
    var videoWidth by remember { mutableIntStateOf(1920) }
    var videoHeight by remember { mutableIntStateOf(1080) }

    val mediaPlayerRef = remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    val textureViewRef = remember { mutableStateOf<android.view.TextureView?>(null) }

    fun applyTextureTransform(tv: android.view.TextureView?, vw: Int, vh: Int, fillCrop: Boolean) {
        if (tv == null || vw <= 0 || vh <= 0 || tv.width <= 0 || tv.height <= 0) return
        val viewW = tv.width.toFloat()
        val viewH = tv.height.toFloat()
        val videoAspect = vw.toFloat() / vh.toFloat()
        val viewAspect = viewW / viewH

        var scaleX = 1f
        var scaleY = 1f
        if (fillCrop) {
            if (videoAspect > viewAspect) {
                scaleX = videoAspect / viewAspect
            } else {
                scaleY = viewAspect / videoAspect
            }
        } else {
            if (videoAspect > viewAspect) {
                scaleY = viewAspect / videoAspect
            } else {
                scaleX = videoAspect / viewAspect
            }
        }
        val matrix = android.graphics.Matrix()
        matrix.setScale(scaleX, scaleY, viewW / 2f, viewH / 2f)
        tv.setTransform(matrix)
        tv.invalidate()
    }

    fun formatTimeMs(ms: Int): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    LaunchedEffect(isPlaying, isUserScrubbing) {
        while (isPlaying) {
            val mp = mediaPlayerRef.value
            if (mp != null && !isUserScrubbing) {
                try {
                    if (mp.isPlaying) {
                        currentPosMs = mp.currentPosition.coerceAtLeast(0)
                        val dur = mp.duration
                        if (dur > 0) durationMs = dur
                    }
                } catch (_: Exception) {}
            }
            kotlinx.coroutines.delay(100L)
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
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

    val closePlayer: () -> Unit = {
        try {
            mediaPlayerRef.value?.pause()
        } catch (_: Exception) {}
        onDismiss()
    }

    val isPortraitVideo = videoHeight > videoWidth
    val dynamicAspectRatio = remember(videoWidth, videoHeight) {
        if (videoWidth > 0 && videoHeight > 0) {
            (videoWidth.toFloat() / videoHeight.toFloat()).coerceIn(0.45f, 2.4f)
        } else {
            16f / 9f
        }
    }

    Dialog(
        onDismissRequest = closePlayer,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = if (isFullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = if (isFullscreen) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            modifier = if (isFullscreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxWidth(0.95f)
                    .padding(vertical = 12.dp)
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isFullscreen) 10.dp else 16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${videoWidth}×${videoHeight} • ${if (isPortraitVideo) "Portrait" else "Landscape"} • ${video.formattedSize}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onTrimClip != null) {
                            TextButton(
                                onClick = {
                                    try {
                                        mediaPlayerRef.value?.pause()
                                    } catch (_: Exception) {}
                                    onTrimClip()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("video_player_trim_clip_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCut,
                                    contentDescription = "Trim Clip",
                                    tint = LaunchGreen,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = com.example.util.tr("TRIM", "RECORTAR"),
                                    color = LaunchGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        TextButton(
                            onClick = {
                                isFillCrop = !isFillCrop
                                applyTextureTransform(textureViewRef.value, videoWidth, videoHeight, isFillCrop)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("video_player_fit_fill_toggle")
                        ) {
                            Text(
                                text = if (isFillCrop) com.example.util.tr("FILL", "LLENAR") else com.example.util.tr("FIT", "AJUSTAR"),
                                color = HubBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        IconButton(
                            onClick = { isFullscreen = !isFullscreen },
                            modifier = Modifier.size(34.dp).testTag("video_player_fullscreen_toggle")
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = "Toggle Fullscreen",
                                tint = HubBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = closePlayer,
                            modifier = Modifier.size(34.dp).testTag("video_player_close_button")
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Video Viewport (TextureView avoids SurfaceView corner punching / black corner bugs)
                Box(
                    modifier = if (isFullscreen) {
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .height(if (isPortraitVideo) 400.dp else 230.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black)
                    },
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
                                            mp.setOnVideoSizeChangedListener { _, vw, vh ->
                                                if (vw > 0 && vh > 0) {
                                                    videoWidth = vw
                                                    videoHeight = vh
                                                    applyTextureTransform(this@apply, vw, vh, isFillCrop)
                                                }
                                            }
                                            mp.setOnPreparedListener { preparedMp ->
                                                durationMs = preparedMp.duration.coerceAtLeast(1000)
                                                if (preparedMp.videoWidth > 0 && preparedMp.videoHeight > 0) {
                                                    videoWidth = preparedMp.videoWidth
                                                    videoHeight = preparedMp.videoHeight
                                                }
                                                applyTextureTransform(
                                                    this@apply,
                                                    videoWidth,
                                                    videoHeight,
                                                    isFillCrop
                                                )
                                                preparedMp.start()
                                                isPlaying = true
                                            }
                                            mp.prepareAsync()
                                        } catch (_: Exception) {}
                                    }

                                    override fun onSurfaceTextureSizeChanged(
                                        surfaceTexture: android.graphics.SurfaceTexture,
                                        width: Int,
                                        height: Int
                                    ) {
                                        applyTextureTransform(this@apply, videoWidth, videoHeight, isFillCrop)
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
                        update = { tv ->
                            applyTextureTransform(tv, videoWidth, videoHeight, isFillCrop)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                val mp = mediaPlayerRef.value ?: return@clickable
                                try {
                                    if (mp.isPlaying) {
                                        mp.pause()
                                        isPlaying = false
                                    } else {
                                        mp.start()
                                        isPlaying = true
                                    }
                                } catch (_: Exception) {}
                            }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Interactive Scrubbing Seek Bar + Time Readout
                val sliderValue = if (isUserScrubbing) {
                    scrubPositionMs
                } else {
                    currentPosMs.toFloat().coerceIn(0f, durationMs.toFloat())
                }
                Slider(
                    value = sliderValue,
                    onValueChange = { newVal ->
                        isUserScrubbing = true
                        scrubPositionMs = newVal
                    },
                    onValueChangeFinished = {
                        val targetMs = scrubPositionMs.toInt().coerceIn(0, durationMs)
                        currentPosMs = targetMs
                        try {
                            val mp = mediaPlayerRef.value
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                mp?.seekTo(targetMs.toLong(), android.media.MediaPlayer.SEEK_CLOSEST)
                            } else {
                                mp?.seekTo(targetMs)
                            }
                        } catch (_: Exception) {}
                        isUserScrubbing = false
                    },
                    valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = RecorderCoral,
                        activeTrackColor = RecorderCoral
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .testTag("video_player_seek_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTimeMs(if (isUserScrubbing) scrubPositionMs.toInt() else currentPosMs),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = formatTimeMs(durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Transport & Speed Controls Row: Mute | -10s | Play/Pause | +10s | Speed
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute / Unmute
                    IconButton(
                        onClick = {
                            isMuted = !isMuted
                            val vol = if (isMuted) 0f else 1f
                            try {
                                mediaPlayerRef.value?.setVolume(vol, vol)
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.size(38.dp).testTag("video_player_mute_toggle")
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = if (isMuted) RecorderCoral else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // -10s Skip Back
                    OutlinedButton(
                        onClick = {
                            val mp = mediaPlayerRef.value ?: return@OutlinedButton
                            try {
                                val nextPos = (mp.currentPosition - 10_000).coerceAtLeast(0)
                                mp.seekTo(nextPos)
                                currentPosMs = nextPos
                            } catch (_: Exception) {}
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp).testTag("video_player_skip_back_10s")
                    ) {
                        Text("-10s", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Play / Pause Primary Button
                    Button(
                        onClick = {
                            val mp = mediaPlayerRef.value ?: return@Button
                            try {
                                if (mp.isPlaying) {
                                    mp.pause()
                                    isPlaying = false
                                } else {
                                    mp.start()
                                    isPlaying = true
                                }
                            } catch (_: Exception) {}
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RecorderCoral,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(44.dp).testTag("video_player_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play"
                        )
                    }

                    // +10s Skip Forward
                    OutlinedButton(
                        onClick = {
                            val mp = mediaPlayerRef.value ?: return@OutlinedButton
                            try {
                                val nextPos = (mp.currentPosition + 10_000).coerceAtMost(durationMs)
                                mp.seekTo(nextPos)
                                currentPosMs = nextPos
                            } catch (_: Exception) {}
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp).testTag("video_player_skip_forward_10s")
                    ) {
                        Text("+10s", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Playback Speed Cycle (0.5x -> 1.0x -> 1.5x -> 2.0x)
                    TextButton(
                        onClick = {
                            val nextSpeed = when (playbackSpeed) {
                                0.5f -> 1.0f
                                1.0f -> 1.5f
                                1.5f -> 2.0f
                                else -> 0.5f
                            }
                            playbackSpeed = nextSpeed
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                try {
                                    val mp = mediaPlayerRef.value
                                    if (mp != null) {
                                        val wasPlaying = mp.isPlaying
                                        mp.playbackParams = mp.playbackParams.setSpeed(nextSpeed)
                                        if (!wasPlaying) {
                                            mp.pause()
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("video_player_speed_button")
                    ) {
                        Text(
                            text = "${playbackSpeed}x",
                            color = HubBlue,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

