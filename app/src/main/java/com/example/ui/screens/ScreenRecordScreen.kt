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
import com.example.service.ScreenRecordOverlayService
import com.example.service.ScreenRecordingService
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

    var selectedTab by remember(initialSubTab) { mutableIntStateOf(initialSubTab.coerceIn(0, 2)) }
    var settings by remember { mutableStateOf(ScreenRecordingManager.loadSettings(context)) }
    var recordings by remember { mutableStateOf(emptyList<RecordedVideo>()) }
    var screenshots by remember { mutableStateOf(emptyList<CapturedScreenshot>()) }

    var selectedVideoForPlayback by remember { mutableStateOf<RecordedVideo?>(null) }
    var videoToDelete by remember { mutableStateOf<RecordedVideo?>(null) }
    var selectedScreenshotForPreview by remember { mutableStateOf<CapturedScreenshot?>(null) }
    var screenshotToDelete by remember { mutableStateOf<CapturedScreenshot?>(null) }
    var userMessage by remember { mutableStateOf<String?>(null) }

    // State from ScreenRecordingService
    val isRecording by ScreenRecordingService.isRecording.collectAsState()
    val recordingSeconds by ScreenRecordingService.recordingSeconds.collectAsState()
    val activeAudioMode by ScreenRecordingService.activeAudioMode.collectAsState()
    val lastSavedUri by ScreenRecordingService.lastSavedVideoUri.collectAsState()
    val lastScreenshotUri by ScreenRecordingService.lastSavedScreenshotUri.collectAsState()

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

    var pendingAudioMode by remember { mutableStateOf<AudioSourceMode?>(null) }
    var pendingRecordLaunchAfterPerm by remember { mutableStateOf(false) }

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
            userMessage = "Audio permission denied — recording will continue without sound"
        }
        pendingAudioMode = null
        if (pendingRecordLaunchAfterPerm) {
            pendingRecordLaunchAfterPerm = false
            onRequestMediaProjection()
        }
    }

    fun refreshMediaLists() {
        recordings = ScreenRecordingManager.queryRecordedVideos(context)
        screenshots = ScreenRecordingManager.queryCapturedScreenshots(context)
    }

    LaunchedEffect(Unit, isRecording, lastSavedUri, lastScreenshotUri) {
        refreshMediaLists()
    }

    val tabTitles = listOf("Videos", "Screenshots", "Settings")

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

                // Right side quick actions: Refresh & Floating Coral Bubble toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { refreshMediaLists() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh media",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

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
                                    "Floating recorder bubble & notification bar enabled"
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
                        onPlayVideo = { selectedVideoForPlayback = it },
                        onSaveToGallery = { video ->
                            val saved = ScreenRecordingManager.saveVideoToGallery(context, video)
                            userMessage = if (saved) {
                                "Saved '${video.title}' to Movies/GMaxRecordings"
                            } else {
                                "Could not export video"
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
                        onDeleteVideo = { videoToDelete = it }
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
                        onDeleteScreenshot = { screenshotToDelete = it }
                    )

                    2 -> RecorderSettingsTabContent(
                        settings = settings,
                        supportedResolutions = supportedResolutions,
                        supportedFps = supportedFps,
                        isOverlayBubbleEnabled = isOverlayBubbleEnabled,
                        onUpdateSettings = { updated ->
                            settings = updated
                            ScreenRecordingManager.saveSettings(context, updated)
                            viewModel.scheduleCloudSettingsSync()
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
                        onAutoConfigure = {
                            val optimal = ScreenRecordingManager.getAutoConfiguredIdealSettings(context)
                            settings = optimal
                            ScreenRecordingManager.saveSettings(context, optimal)
                            viewModel.scheduleCloudSettingsSync()
                            userMessage = "Auto-tuned to ${optimal.resolutionLabel} @ ${optimal.fps} FPS (${optimal.bitrateMbps} Mbps • ${optimal.audioSourceMode.label})"
                        }
                    )
                }

                // Floating Coral-Red "[ 🎥 Record ]" Pill Button in bottom-right (matching screenshot #2)
                var recordFabFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = {
                        if (isRecording) {
                            val stopIntent = Intent(context, ScreenRecordingService::class.java).apply {
                                action = ScreenRecordingService.ACTION_STOP
                            }
                            context.startService(stopIntent)
                            userMessage = "Gameplay recording saved to Videos!"
                        } else {
                            val needsAudioPerm = settings.audioSourceMode != AudioSourceMode.MUTE &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) != PackageManager.PERMISSION_GRANTED
                            if (needsAudioPerm) {
                                pendingAudioMode = settings.audioSourceMode
                                pendingRecordLaunchAfterPerm = true
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                onRequestMediaProjection()
                            }
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
                        text = if (isRecording) "Stop" else "Record",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isControllerConnected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(21.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Y",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = RecorderCoral
                            )
                        }
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
                        text = storageInfo.availableGbText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    )
                    Text(
                        text = storageInfo.recordableTimeText,
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
            onDismiss = { selectedVideoForPlayback = null }
        )
    }

    // Confirm Delete Video Dialog
    videoToDelete?.let { video ->
        AlertDialog(
            onDismissRequest = { videoToDelete = null },
            title = { Text("Delete Recording?") },
            text = { Text("Delete '${video.title}' permanently from your device?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        ScreenRecordingManager.deleteRecording(context, video)
                        videoToDelete = null
                        refreshMediaLists()
                    }
                ) {
                    Text("Delete", color = RecorderCoral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { videoToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Screenshot Preview Modal
    selectedScreenshotForPreview?.let { shot ->
        ScreenshotPreviewDialog(
            screenshot = shot,
            onDismiss = { selectedScreenshotForPreview = null }
        )
    }

    // Confirm Delete Screenshot Dialog
    screenshotToDelete?.let { shot ->
        AlertDialog(
            onDismissRequest = { screenshotToDelete = null },
            title = { Text("Delete Screenshot?") },
            text = { Text("Remove '${shot.title}' from your Screenshots library?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        ScreenRecordingManager.deleteScreenshot(context, shot)
                        screenshotToDelete = null
                        refreshMediaLists()
                    }
                ) {
                    Text("Delete", color = RecorderCoral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { screenshotToDelete = null }) {
                    Text("Cancel")
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
    onPlayVideo: (RecordedVideo) -> Unit,
    onSaveToGallery: (RecordedVideo) -> Unit,
    onShareVideo: (RecordedVideo) -> Unit,
    onDeleteVideo: (RecordedVideo) -> Unit
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Slideshow,
                    contentDescription = null,
                    tint = RecorderCoral.copy(alpha = 0.35f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No videos have been recorded",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap the coral Record button below or use the floating bubble during gameplay. Videos are automatically grouped by game.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("videos_grouped_gallery"),
            contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp, start = 16.dp, end = 16.dp),
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

            visibleGroups.forEach { group ->
                item(key = "video_group_header_${group.gameName}") {
                    GameMediaGroupHeader(
                        gameName = group.gameName,
                        gamePackage = group.gamePackage,
                        itemCount = group.items.size,
                        unitLabel = if (group.items.size == 1) "clip" else "clips"
                    )
                }

                items(group.items, key = { it.id }) { video ->
                    RecordedVideoItemCard(
                        video = video,
                        onPlay = {
                            ConsoleHapticManager.playSelectClick(context)
                            onPlayVideo(video)
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
                        text = "All Games ($totalCount)",
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
    onSaveToGallery: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val thumbnail = remember(video.filePath) {
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
            } else null
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
                    .width(110.dp)
                    .height(66.dp)
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
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(RecorderCoral.copy(alpha = 0.88f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

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

            IconButton(onClick = onSaveToGallery, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Save to Gallery",
                    tint = HubBlue,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(onClick = onShare, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
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
    onShareScreenshot: (CapturedScreenshot) -> Unit,
    onDeleteScreenshot: (CapturedScreenshot) -> Unit
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
                        text = "Real Screen Capture",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Capture lossless PNG frames automatically grouped by active game",
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
                    Text("Capture", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                        text = "No screenshots captured yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap Capture above or use the Shots button in the notification bar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
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
                            unitLabel = if (group.items.size == 1) "shot" else "shots"
                        )
                    }

                    items(group.items, key = { it.id }) { shot ->
                        val bmp = remember(shot.filePath) {
                            try {
                                BitmapFactory.decodeFile(shot.filePath)
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
    }
}

@Composable
private fun RecorderSettingsTabContent(
    settings: VideoQualitySettings,
    supportedResolutions: List<ResolutionPreset>,
    supportedFps: List<Int>,
    isOverlayBubbleEnabled: Boolean,
    onUpdateSettings: (VideoQualitySettings) -> Unit,
    onRequestAudioPermissionForMode: (AudioSourceMode) -> Unit,
    onToggleOverlayBubble: (Boolean) -> Unit,
    onAutoConfigure: () -> Unit
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
                            text = "Smart Encoder Auto-Tune",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Automatically match resolution, FPS, and bitrate to your hardware",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onAutoConfigure,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RecorderCoral,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Auto-Tune", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                        text = "Video Resolution",
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
                                onClick = {
                                    onUpdateSettings(
                                        settings.copy(
                                            resolutionLabel = preset.label,
                                            width = preset.width,
                                            height = preset.height
                                        )
                                    )
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
                        text = "Frame Rate (FPS)",
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
                                onClick = { onUpdateSettings(settings.copy(fps = fps)) },
                                label = { Text("$fps FPS") },
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
                            text = "Video Bitrate",
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
                        onValueChange = { onUpdateSettings(settings.copy(bitrateMbps = it.toInt())) },
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

        // 4. Audio Source, Floating Radial Bubble, & Timer HUD Toggles
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
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
                                    text = "Audio Capture Source",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = settings.audioSourceMode.description,
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
                                            text = mode.label,
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

                    // Floating Radial Bubble & Notification Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Layers, contentDescription = null, tint = RecorderCoral)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Floating Radial Bubble & Notification",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Show edge bubble & 'Recorder is ready' notification bar",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isOverlayBubbleEnabled,
                            onCheckedChange = onToggleOverlayBubble,
                            colors = SwitchDefaults.colors(checkedTrackColor = RecorderCoral)
                        )
                    }

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
                                    text = "Floating Bubble Size",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${settings.bubbleSizeDp} dp (Compact 28dp – Large 64dp)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RecorderCoral,
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
                                thumbColor = RecorderCoral,
                                activeTrackColor = RecorderCoral
                            ),
                            modifier = Modifier.testTag("floating_bubble_size_slider")
                        )
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
                                    text = "On-Screen Timer Pill",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Show compact elapsed time pill during active recording",
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
    }
}

@Composable
private fun ScreenshotPreviewDialog(
    screenshot: CapturedScreenshot,
    onDismiss: () -> Unit
) {
    val bmp = remember(screenshot.filePath) {
        try {
            BitmapFactory.decodeFile(screenshot.filePath)
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

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${screenshot.resolution} • ${screenshot.formattedSize}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * In-app Video Player Dialog with Fullscreen expansion and Portrait / Landscape / Auto-Rotate controls
 */
@Composable
fun VideoPlayerModalDialog(
    video: RecordedVideo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var isFullscreen by remember { mutableStateOf(false) }

    val closePlayer: () -> Unit = {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDismiss()
    }

    Dialog(
        onDismissRequest = closePlayer,
        properties = DialogProperties(
            usePlatformDefaultWidth = !isFullscreen
        )
    ) {
        Surface(
            shape = if (isFullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = if (isFullscreen) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            modifier = if (isFullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isFullscreen) 8.dp else 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("P", fontWeight = FontWeight.Bold, color = HubBlue, fontSize = 12.sp)
                        }

                        IconButton(
                            onClick = {
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("L", fontWeight = FontWeight.Bold, color = HubBlue, fontSize = 12.sp)
                        }

                        IconButton(
                            onClick = {
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.ScreenRotation,
                                contentDescription = "Auto Rotate",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { isFullscreen = !isFullscreen },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = "Toggle Fullscreen",
                                tint = HubBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(onClick = closePlayer, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = if (isFullscreen) {
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                    },
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                val mc = MediaController(ctx)
                                mc.setAnchorView(this)
                                setMediaController(mc)
                                val uri = if (video.uriString.startsWith("content://")) {
                                    Uri.parse(video.uriString)
                                } else {
                                    val f = File(video.filePath)
                                    if (f.exists()) {
                                        androidx.core.content.FileProvider.getUriForFile(
                                            ctx,
                                            "${ctx.packageName}.fileprovider",
                                            f
                                        )
                                    } else {
                                        Uri.parse(video.uriString)
                                    }
                                }
                                setVideoURI(uri)
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    start()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (!isFullscreen) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${video.formattedDuration} • ${video.resolution}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = closePlayer) {
                            Text("Close Player", color = HubBlue)
                        }
                    }
                }
            }
        }
    }
}
