package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.model.RecordedVideo
import com.example.model.VideoQualitySettings
import com.example.service.ScreenRecordOverlayService
import com.example.service.ScreenRecordingService
import com.example.ui.components.CyberCard
import com.example.ui.components.SectionTitle
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.SolarGold
import com.example.util.ScreenRecordingManager
import com.example.viewmodel.GameBoosterViewModel
import java.io.File

@Composable
fun ScreenRecordScreen(
    viewModel: GameBoosterViewModel,
    onRequestMediaProjection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTheme by viewModel.appTheme.collectAsState()

    var settings by remember { mutableStateOf(ScreenRecordingManager.loadSettings(context)) }
    var recordings by remember { mutableStateOf(emptyList<RecordedVideo>()) }
    var selectedVideoForPlayback by remember { mutableStateOf<RecordedVideo?>(null) }
    var userMessage by remember { mutableStateOf<String?>(null) }

    // State from ScreenRecordingService
    val isRecording by ScreenRecordingService.isRecording.collectAsState()
    val recordingSeconds by ScreenRecordingService.recordingSeconds.collectAsState()
    val lastSavedUri by ScreenRecordingService.lastSavedVideoUri.collectAsState()

    // Query device supported options
    val supportedResolutions = remember(context) { ScreenRecordingManager.getSupportedResolutions(context) }
    val supportedFps = remember(context) { ScreenRecordingManager.getSupportedFps(context) }

    // Permissions check
    var hasAudioPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasAudioPermission = result[Manifest.permission.RECORD_AUDIO] ?: hasAudioPermission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = result[Manifest.permission.POST_NOTIFICATIONS] ?: hasNotificationPermission
        }
        ScreenRecordingManager.setRequestedPermissionsOnce(context, true)
    }

    // Refresh recordings list
    val refreshRecordings: () -> Unit = {
        recordings = ScreenRecordingManager.queryRecordedVideos(context)
    }

    LaunchedEffect(Unit) {
        refreshRecordings()
        // If not asked yet, automatically request permissions on first entry
        if (!ScreenRecordingManager.hasRequestedPermissionsOnce(context)) {
            val list = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                list.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionsLauncher.launch(list.toTypedArray())
        }
    }

    // Re-query recordings when a new video finishes saving
    LaunchedEffect(lastSavedUri) {
        if (lastSavedUri != null) {
            refreshRecordings()
        }
    }

    val areRequiredPermissionsGranted = hasAudioPermission && hasOverlayPermission

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(currentTheme.backgroundColor)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Screen Title
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(
                    text = "Screen Recording",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 24.sp
                    )
                )
                Text(
                    text = "High-definition lag-free game capture & quick overlay controls",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF8893A7), fontSize = 13.sp)
                )
            }
        }

        // Permission Onboarding Banner (if permissions not yet granted)
        if (!areRequiredPermissionsGranted) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SolarGold.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1711)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = SolarGold, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Recording Permissions Needed",
                                style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "To record game audio and show the floating 5-in-1 widget over your games, please grant Microphone and Display Over Apps permissions.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFD1D5DB), fontSize = 12.sp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!hasAudioPermission) {
                                Button(
                                    onClick = {
                                        val list = mutableListOf(Manifest.permission.RECORD_AUDIO)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            list.add(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                        permissionsLauncher.launch(list.toTypedArray())
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SolarGold, contentColor = Color.Black),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Grant Mic", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            if (!hasOverlayPermission) {
                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Grant Overlay", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // User Feedback Alert
        if (userMessage != null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0D293E))
                        .border(1.dp, CyberCyan.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = userMessage ?: "", color = CyberCyan, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { userMessage = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = CyberCyan, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // 2. Active Status & Master Actions Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (isRecording) Color(0xFFEF4444) else DarkSurfaceBorder,
                        RoundedCornerShape(18.dp)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isRecording) Color(0xFF2E0E14) else Color(0xFF13192B)
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(if (isRecording) Color(0xFFEF4444) else NeonGreen)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isRecording) "RECORDING IN PROGRESS" else "RECORDER READY",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        color = if (isRecording) Color(0xFFEF4444) else Color.White,
                                        letterSpacing = 1.sp
                                    )
                                )
                                Text(
                                    text = if (isRecording) {
                                        val mins = recordingSeconds / 60
                                        val secs = recordingSeconds % 60
                                        String.format(java.util.Locale.US, "Elapsed Time: %02d:%02d", mins, secs)
                                    } else {
                                        "${settings.resolutionLabel} • ${settings.fps} FPS • ${settings.bitrateMbps} Mbps"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFF8893A7),
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }

                        // Big Action Button
                        Button(
                            onClick = {
                                if (isRecording) {
                                    val intent = Intent(context, ScreenRecordingService::class.java).apply {
                                        action = ScreenRecordingService.ACTION_STOP
                                    }
                                    context.startService(intent)
                                    userMessage = "Screen recording saved to Gallery!"
                                } else {
                                    onRequestMediaProjection()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecording) Color(0xFFDC2626) else Color(0xFFE11D48),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("main_record_action_button")
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isRecording) "Stop" else "Record",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Master Toggle for Floating Recording Overlay
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F1524))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = if (settings.isOverlayEnabled) Color(0xFFE11D48) else Color(0xFF8893A7),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Floating Recording Overlay",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                )
                                Text(
                                    text = "Draggable 5-in-1 quick widget over games",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF8893A7), fontSize = 10.sp)
                                )
                            }
                        }

                        Switch(
                            checked = settings.isOverlayEnabled,
                            onCheckedChange = { enable ->
                                if (enable && !Settings.canDrawOverlays(context)) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                    return@Switch
                                }
                                val updated = settings.copy(isOverlayEnabled = enable)
                                settings = updated
                                ScreenRecordingManager.saveSettings(context, updated)

                                val intent = Intent(context, ScreenRecordOverlayService::class.java).apply {
                                    action = if (enable) ScreenRecordOverlayService.ACTION_START else ScreenRecordOverlayService.ACTION_STOP
                                }
                                if (enable) {
                                    context.startService(intent)
                                    userMessage = "Floating recording overlay activated on screen."
                                } else {
                                    context.stopService(intent)
                                    userMessage = "Floating recording overlay hidden."
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFE11D48)
                            ),
                            modifier = Modifier.testTag("recording_overlay_toggle")
                        )
                    }
                }
            }
        }

        // 3. Auto-Configure Ideal Settings Button
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .clickable {
                        val auto = ScreenRecordingManager.getAutoConfiguredIdealSettings(context)
                        settings = auto
                        ScreenRecordingManager.saveSettings(context, auto)
                        userMessage = "Auto-configured ideal settings for your device: ${auto.resolutionLabel} @ ${auto.fps}fps (${auto.bitrateMbps} Mbps) for zero lag."
                    }
                    .testTag("auto_configure_settings_card"),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1A2C)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(CyberCyan.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(24.dp))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Configure Ideal Settings",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Auto-tunes resolution, FPS, and bitrate for your specific processor and screen to eliminate lag.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF9CA3AF), fontSize = 11.sp)
                        )
                    }
                }
            }
        }

        // 4. Video Quality Settings Card
        item {
            CyberCard {
                SectionTitle(
                    title = "Video Quality Settings",
                    subtitle = "Customize screen resolution, frame rate, and compression bitrate"
                )

                Spacer(modifier = Modifier.height(14.dp))

                // A. Resolution Choice
                Text(
                    text = "RESOLUTION (DEVICE SUPPORTED):",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8893A7),
                        letterSpacing = 1.sp
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(supportedResolutions, key = { it.label }) { preset ->
                        val isSelected = settings.resolutionLabel == preset.label
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val updated = settings.copy(
                                    resolutionLabel = preset.label,
                                    width = preset.width,
                                    height = preset.height
                                )
                                settings = updated
                                ScreenRecordingManager.saveSettings(context, updated)
                            },
                            label = {
                                Text(
                                    text = preset.label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF2563EB),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF13192B),
                                labelColor = Color(0xFF8893A7)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) Color(0xFF38BDF8) else DarkSurfaceBorder
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // B. Frame Rate Choice
                Text(
                    text = "FRAME RATE (CAPPED AT HARDWARE REFRESH RATE):",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8893A7),
                        letterSpacing = 1.sp
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (fps in supportedFps) {
                        val isSelected = settings.fps == fps
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val updated = settings.copy(fps = fps)
                                settings = updated
                                ScreenRecordingManager.saveSettings(context, updated)
                            },
                            label = {
                                Text(
                                    text = "${fps} FPS",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF2563EB),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF13192B),
                                labelColor = Color(0xFF8893A7)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) Color(0xFF38BDF8) else DarkSurfaceBorder
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // C. Bitrate Slider (1 to 16 Mbps)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VIDEO BITRATE:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8893A7),
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = "${settings.bitrateMbps} Mbps",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }

                Slider(
                    value = settings.bitrateMbps.toFloat(),
                    onValueChange = {
                        val mbps = it.toInt().coerceIn(1, 16)
                        val updated = settings.copy(bitrateMbps = mbps)
                        settings = updated
                        ScreenRecordingManager.saveSettings(context, updated)
                    },
                    valueRange = 1f..16f,
                    steps = 14,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF38BDF8),
                        activeTrackColor = Color(0xFF2563EB),
                        inactiveTrackColor = Color(0xFF1E283D)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // D. Microphone & Overlay Timer Toggles
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF101524))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (settings.recordMic) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = null,
                            tint = if (settings.recordMic) NeonGreen else Color(0xFF8893A7),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Record Microphone Audio",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontSize = 13.sp)
                        )
                    }

                    Switch(
                        checked = settings.recordMic,
                        onCheckedChange = { mic ->
                            val updated = settings.copy(recordMic = mic)
                            settings = updated
                            ScreenRecordingManager.saveSettings(context, updated)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = NeonGreen
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF101524))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = if (settings.showTimerHud) Color(0xFF38BDF8) else Color(0xFF8893A7),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Display Elapsed Time in Overlay",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontSize = 13.sp)
                        )
                    }

                    Switch(
                        checked = settings.showTimerHud,
                        onCheckedChange = { show ->
                            val updated = settings.copy(showTimerHud = show)
                            settings = updated
                            ScreenRecordingManager.saveSettings(context, updated)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF38BDF8)
                        )
                    )
                }
            }
        }

        // 5. Past Recordings Gallery Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PAST RECORDINGS (${recordings.size})",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF8893A7),
                        letterSpacing = 1.sp,
                        fontSize = 14.sp
                    )
                )

                IconButton(onClick = refreshRecordings) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Recordings", tint = CyberCyan)
                }
            }
        }

        // 6. Recordings Items or Empty State
        if (recordings.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF13192B)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = Color(0xFF3B4863),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No gameplay recordings yet",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Videos saved via the floating recording circle will appear in your phone's Gallery and in this tab.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF8893A7),
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        } else {
            items(recordings, key = { it.id }) { video ->
                RecordedVideoCard(
                    video = video,
                    onPlay = { selectedVideoForPlayback = video },
                    onShare = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "video/mp4"
                            putExtra(Intent.EXTRA_STREAM, Uri.parse(video.uriString))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
                    },
                    onDelete = {
                        ScreenRecordingManager.deleteRecording(context, video)
                        refreshRecordings()
                        userMessage = "Recording deleted."
                    }
                )
            }
        }
    }

    // In-app Video Player Dialog
    selectedVideoForPlayback?.let { video ->
        VideoPlayerModalDialog(
            video = video,
            onDismiss = { selectedVideoForPlayback = null }
        )
    }
}

/**
 * Individual Recorded Video Item Card
 */
@Composable
fun RecordedVideoCard(
    video: RecordedVideo,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var thumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(video.id) {
        try {
            thumbnailBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && video.uriString.startsWith("content://")) {
                context.contentResolver.loadThumbnail(Uri.parse(video.uriString), Size(320, 180), null)
            } else {
                @Suppress("DEPRECATION")
                ThumbnailUtils.createVideoThumbnail(video.filePath, MediaStore.Images.Thumbnails.MINI_KIND)
            }
        } catch (_: Exception) {}
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1E283E), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13192B)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail preview box
                Box(
                    modifier = Modifier
                        .size(width = 90.dp, height = 64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0A0F1D))
                        .clickable { onPlay() },
                    contentAlignment = Alignment.Center
                ) {
                    if (thumbnailBitmap != null) {
                        Image(
                            bitmap = thumbnailBitmap!!.asImageBitmap(),
                            contentDescription = "Thumbnail",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF4B5563))
                    }

                    // Play Overlay Icon
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0x80000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Metadata
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${video.formattedDuration} • ${video.formattedSize} • ${video.resolution}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF8893A7),
                            fontSize = 11.sp
                        )
                    )
                }

                // Quick Play, Share, Delete Buttons
                IconButton(onClick = onShare, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = CyberCyan, modifier = Modifier.size(18.dp))
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFF87171), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/**
 * In-app Video Player Dialog with standard playback controls
 */
@Composable
fun VideoPlayerModalDialog(
    video: RecordedVideo,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF0A0F1D),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E283E)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Video View Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
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
                                    Uri.fromFile(File(video.filePath))
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

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close Player", color = CyberCyan)
                    }
                }
            }
        }
    }
}
