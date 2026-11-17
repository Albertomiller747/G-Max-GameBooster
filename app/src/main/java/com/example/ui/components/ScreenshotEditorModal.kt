package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.CapturedScreenshot
import com.example.ui.theme.HubBlue
import com.example.ui.theme.LaunchGreen
import com.example.ui.theme.RecorderCoral
import com.example.util.InstantReplayBufferManager
import com.example.util.InstantReplaySaveResult
import com.example.util.NormalizedCropBounds
import com.example.util.ScreenshotCropAspect
import com.example.util.ScreenshotEditHelper
import com.example.util.ScreenshotEditSaveResult
import com.example.util.ScreenshotFilterPreset
import com.example.util.tr
import kotlin.math.roundToInt

/**
 * Screenshot Studio Editor Modal Dialog:
 * - Freeform crop handles + X/Y range trimmers
 * - 5 Aspect Ratio Presets (`Freeform`, `16:9`, `1:1`, `9:16`, `4:3`)
 * - 6 Gaming Color Filters (`Original`, `Night Vision`, `Thermal Boost`, `Cyber Neon`, `Tactical Warm`, `Noir Mono`)
 * - Save as New Copy or Overwrite Original
 */
@Composable
fun ScreenshotEditorModalDialog(
    screenshot: CapturedScreenshot,
    onEditSaved: (ScreenshotEditSaveResult) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sourceBitmap: Bitmap = remember(screenshot.filePath, screenshot.uriString) {
        ScreenshotEditHelper.loadScreenshotBitmap(context, screenshot)
            ?: Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.rgb(20, 32, 56))
            }
    }

    var selectedAspect by remember { mutableStateOf(ScreenshotCropAspect.FREE) }
    var selectedFilter by remember { mutableStateOf(ScreenshotFilterPreset.ORIGINAL) }
    var cropBounds by remember { mutableStateOf(NormalizedCropBounds(0f, 0f, 1f, 1f)) }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }

    val cropWidthPx = ((cropBounds.rightFrac - cropBounds.leftFrac) * sourceBitmap.width)
        .roundToInt().coerceAtLeast(1)
    val cropHeightPx = ((cropBounds.bottomFrac - cropBounds.topFrac) * sourceBitmap.height)
        .roundToInt().coerceAtLeast(1)

    val composeColorFilter = remember(selectedFilter) {
        if (selectedFilter == ScreenshotFilterPreset.ORIGINAL) {
            null
        } else {
            ColorFilter.colorMatrix(ColorMatrix(selectedFilter.matrixValues.copyOf()))
        }
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
                .fillMaxWidth(0.95f)
                .widthIn(max = 580.dp)
                .heightIn(max = 680.dp)
                .testTag("screenshot_editor_modal")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
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
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(HubBlue.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCut,
                                contentDescription = null,
                                tint = HubBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = tr("Screenshot Studio (Cut & Color Filter)", "Estudio de Capturas (Recortar y Filtro)"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${screenshot.title} • ${cropWidthPx}×${cropHeightPx} px • ${selectedFilter.labelEn}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF38BDF8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable { onDismiss() }
                            .testTag("screenshot_editor_close_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Live Interactive Crop & Filter Preview Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF060B14))
                            .border(1.dp, HubBlue.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                            .onSizeChanged { canvasSize = it }
                            .pointerInput(selectedAspect) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val w = canvasSize.width.coerceAtLeast(1).toFloat()
                                    val h = canvasSize.height.coerceAtLeast(1).toFloat()
                                    val posX = (change.position.x / w).coerceIn(0f, 1f)
                                    val posY = (change.position.y / h).coerceIn(0f, 1f)
                                    val dx = dragAmount.x / w
                                    val dy = dragAmount.y / h
                                    val midX = (cropBounds.leftFrac + cropBounds.rightFrac) / 2f
                                    val midY = (cropBounds.topFrac + cropBounds.bottomFrac) / 2f

                                    selectedAspect = ScreenshotCropAspect.FREE
                                    cropBounds = NormalizedCropBounds(
                                        leftFrac = if (posX < midX) cropBounds.leftFrac + dx else cropBounds.leftFrac,
                                        topFrac = if (posY < midY) cropBounds.topFrac + dy else cropBounds.topFrac,
                                        rightFrac = if (posX >= midX) cropBounds.rightFrac + dx else cropBounds.rightFrac,
                                        bottomFrac = if (posY >= midY) cropBounds.bottomFrac + dy else cropBounds.bottomFrac
                                    ).clamped()
                                }
                            }
                            .testTag("screenshot_editor_canvas"),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = sourceBitmap.asImageBitmap(),
                            contentDescription = screenshot.title,
                            contentScale = ContentScale.FillBounds,
                            colorFilter = composeColorFilter,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Darkened outer mask + glowing cyan crop rectangle + rule of thirds grid
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val leftPx = cropBounds.leftFrac * size.width
                            val topPx = cropBounds.topFrac * size.height
                            val rightPx = cropBounds.rightFrac * size.width
                            val bottomPx = cropBounds.bottomFrac * size.height
                            val rectW = (rightPx - leftPx).coerceAtLeast(1f)
                            val rectH = (bottomPx - topPx).coerceAtLeast(1f)

                            // Top, bottom, left, right dim overlays outside the cut box
                            val dimColor = Color.Black.copy(alpha = 0.58f)
                            drawRect(dimColor, Offset(0f, 0f), Size(size.width, topPx))
                            drawRect(dimColor, Offset(0f, bottomPx), Size(size.width, size.height - bottomPx))
                            drawRect(dimColor, Offset(0f, topPx), Size(leftPx, rectH))
                            drawRect(dimColor, Offset(rightPx, topPx), Size(size.width - rightPx, rectH))

                            // Rule of thirds lines inside crop rect
                            val gridColor = Color.White.copy(alpha = 0.32f)
                            drawLine(
                                gridColor,
                                Offset(leftPx + rectW / 3f, topPx),
                                Offset(leftPx + rectW / 3f, bottomPx),
                                strokeWidth = 1.dp.toPx()
                            )
                            drawLine(
                                gridColor,
                                Offset(leftPx + (2f * rectW) / 3f, topPx),
                                Offset(leftPx + (2f * rectW) / 3f, bottomPx),
                                strokeWidth = 1.dp.toPx()
                            )
                            drawLine(
                                gridColor,
                                Offset(leftPx, topPx + rectH / 3f),
                                Offset(rightPx, topPx + rectH / 3f),
                                strokeWidth = 1.dp.toPx()
                            )
                            drawLine(
                                gridColor,
                                Offset(leftPx, topPx + (2f * rectH) / 3f),
                                Offset(rightPx, topPx + (2f * rectH) / 3f),
                                strokeWidth = 1.dp.toPx()
                            )

                            // Cyan Crop Border
                            val cyan = Color(0xFF00E5FF)
                            drawRect(
                                color = cyan,
                                topLeft = Offset(leftPx, topPx),
                                size = Size(rectW, rectH),
                                style = Stroke(width = 2.2.dp.toPx())
                            )

                            // 4 Corner Handles
                            val handleR = 6.dp.toPx()
                            drawCircle(cyan, handleR, Offset(leftPx, topPx))
                            drawCircle(cyan, handleR, Offset(rightPx, topPx))
                            drawCircle(cyan, handleR, Offset(leftPx, bottomPx))
                            drawCircle(cyan, handleR, Offset(rightPx, bottomPx))
                        }
                    }

                    // 1. Cut / Crop Aspect Ratio Presets
                    Text(
                        text = tr("1. CUT & CROP ASPECT RATIO", "1. RECORTAR Y PROPORCIÓN DE ASPECTO"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.7.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScreenshotCropAspect.entries.forEach { aspect ->
                            val isSelected = selectedAspect == aspect
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedAspect = aspect
                                    cropBounds = ScreenshotEditHelper.computeAspectBounds(
                                        sourceWidth = sourceBitmap.width,
                                        sourceHeight = sourceBitmap.height,
                                        aspect = aspect
                                    )
                                },
                                label = {
                                    Text(
                                        text = tr(aspect.labelEn, aspect.labelEs),
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = HubBlue.copy(alpha = 0.22f),
                                    selectedLabelColor = HubBlue
                                ),
                                modifier = Modifier.testTag("screenshot_aspect_${aspect.name.lowercase()}")
                            )
                        }
                    }

                    // Fine Horizontal & Vertical Crop Range Sliders
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = tr(
                                    "Horizontal & Vertical Cut Trim (${(cropBounds.leftFrac * 100).toInt()}% to ${(cropBounds.rightFrac * 100).toInt()}% X • ${(cropBounds.topFrac * 100).toInt()}% to ${(cropBounds.bottomFrac * 100).toInt()}% Y)",
                                    "Ajuste Fino de Recorte (${(cropBounds.leftFrac * 100).toInt()}% a ${(cropBounds.rightFrac * 100).toInt()}% X • ${(cropBounds.topFrac * 100).toInt()}% a ${(cropBounds.bottomFrac * 100).toInt()}% Y)"
                                ),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            RangeSlider(
                                value = cropBounds.leftFrac..cropBounds.rightFrac,
                                onValueChange = { range ->
                                    selectedAspect = ScreenshotCropAspect.FREE
                                    cropBounds = cropBounds.copy(
                                        leftFrac = range.start,
                                        rightFrac = range.endInclusive
                                    ).clamped()
                                },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = HubBlue,
                                    activeTrackColor = HubBlue
                                ),
                                modifier = Modifier.height(28.dp)
                            )
                            RangeSlider(
                                value = cropBounds.topFrac..cropBounds.bottomFrac,
                                onValueChange = { range ->
                                    selectedAspect = ScreenshotCropAspect.FREE
                                    cropBounds = cropBounds.copy(
                                        topFrac = range.start,
                                        bottomFrac = range.endInclusive
                                    ).clamped()
                                },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = LaunchGreen,
                                    activeTrackColor = LaunchGreen
                                ),
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }

                    // 2. Color Filter Presets
                    Text(
                        text = tr("2. GAMING COLOR FILTER", "2. FILTRO DE COLOR PARA GAMING"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.7.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScreenshotFilterPreset.entries.forEach { preset ->
                            val isSelected = selectedFilter == preset
                            val accent = Color(preset.badgeHex)
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFilter = preset },
                                label = {
                                    Text(
                                        text = tr(preset.labelEn, preset.labelEs),
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                    )
                                },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(accent)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accent.copy(alpha = 0.22f),
                                    selectedLabelColor = accent
                                ),
                                modifier = Modifier.testTag("screenshot_filter_${preset.name.lowercase()}")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Action Row: Reset • Overwrite • Save Copy
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            selectedAspect = ScreenshotCropAspect.FREE
                            selectedFilter = ScreenshotFilterPreset.ORIGINAL
                            cropBounds = NormalizedCropBounds(0f, 0f, 1f, 1f)
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("screenshot_editor_reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = tr("Reset", "Restablecer"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val edited = ScreenshotEditHelper.cropAndFilterBitmap(
                                source = sourceBitmap,
                                bounds = cropBounds,
                                filterPreset = selectedFilter
                            )
                            val res = ScreenshotEditHelper.saveEditedScreenshot(
                                context = context,
                                original = screenshot,
                                editedBitmap = edited,
                                overwriteOriginal = true,
                                filterPreset = selectedFilter
                            )
                            onEditSaved(res)
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, RecorderCoral),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("screenshot_editor_overwrite_button")
                    ) {
                        Text(
                            text = tr("Overwrite", "Sobrescribir"),
                            color = RecorderCoral,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            val edited = ScreenshotEditHelper.cropAndFilterBitmap(
                                source = sourceBitmap,
                                bounds = cropBounds,
                                filterPreset = selectedFilter
                            )
                            val res = ScreenshotEditHelper.saveEditedScreenshot(
                                context = context,
                                original = screenshot,
                                editedBitmap = edited,
                                overwriteOriginal = false,
                                filterPreset = selectedFilter
                            )
                            onEditSaved(res)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LaunchGreen,
                            contentColor = Color(0xFF04141F)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("screenshot_editor_save_copy_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tr("Save Copy", "Guardar Copia"),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reusable Card for the Last 30 Seconds Instant Replay (Flashback Buffer) in the Recorder tab.
 * Lets users keep a rolling 30-second buffer armed so they never miss a moment even when not recording
 * a full match, and provides a 1-tap "Clip Last 30s" button.
 */
@Composable
fun InstantReplayBufferCard(
    onReplaySaved: (InstantReplaySaveResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val replayState by InstantReplayBufferManager.state.collectAsState()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("instant_replay_30s_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (replayState.isBufferEnabled) LaunchGreen.copy(alpha = 0.55f) else HubBlue.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
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
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (replayState.isBufferEnabled) LaunchGreen.copy(alpha = 0.16f)
                                else HubBlue.copy(alpha = 0.14f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = if (replayState.isBufferEnabled) LaunchGreen else HubBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = tr("Last 30s Instant Replay Buffer", "Búfer de Últimos 30s (Instant Replay)"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (replayState.isBufferEnabled) LaunchGreen.copy(alpha = 0.2f)
                                        else Color(0xFF334155)
                                    )
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (replayState.isBufferEnabled) {
                                        "${replayState.bufferedSecondsAvailable}s ARMED"
                                    } else {
                                        "OFF"
                                    },
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (replayState.isBufferEnabled) LaunchGreen else Color.White
                                )
                            }
                        }
                        Text(
                            text = tr(
                                "Never miss a highlight when you forgot to record: keeps the last 30 seconds ready to save in 1 tap",
                                "No pierdas ningún momento aunque no estés grabando: guarda los últimos 30 segundos con 1 toque"
                            ),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = replayState.isBufferEnabled,
                    onCheckedChange = { enabled ->
                        InstantReplayBufferManager.setBufferEnabled(context, enabled)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = LaunchGreen
                    ),
                    modifier = Modifier.testTag("instant_replay_buffer_toggle")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (!replayState.lastSavedReplayTitle.isNullOrBlank()) {
                            "${tr("Latest", "Último")}: ${replayState.lastSavedReplayTitle}"
                        } else {
                            tr(
                                "Also available in the Floating Bubble menu (⏪ 30s)",
                                "También disponible en la Burbuja Flotante (⏪ 30s)"
                            )
                        },
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF38BDF8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Button(
                    onClick = {
                        val res = InstantReplayBufferManager.saveLast30SecondsReplay(context)
                        onReplaySaved(res)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RecorderCoral,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("clip_last_30s_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tr("Clip Last 30s", "Guardar Últimos 30s"),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
