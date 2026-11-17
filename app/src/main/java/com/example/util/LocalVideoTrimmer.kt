package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.model.RecordedVideo
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToLong

enum class ClipTrimSaveMode(
    val id: String,
    val titleEn: String,
    val titleEs: String,
    val subtitleEn: String,
    val subtitleEs: String
) {
    SAVE_AS_NEW_CLIP(
        id = "new_clip",
        titleEn = "Save as New Clip",
        titleEs = "Guardar Nuevo Clip",
        subtitleEn = "Keep original recording & create a trimmed highlight",
        subtitleEs = "Conservar grabación original y crear clip recortado"
    ),
    SAVE_NEW_AND_DELETE_ORIGINAL(
        id = "new_and_delete",
        titleEn = "Save & Delete Original",
        titleEs = "Guardar y Borrar Original",
        subtitleEn = "Export trimmed clip and delete bulky source video",
        subtitleEs = "Exportar clip recortado y borrar el video original pesado"
    ),
    OVERWRITE_ORIGINAL(
        id = "overwrite_original",
        titleEn = "Save as New Original",
        titleEs = "Guardar como Nuevo Original",
        subtitleEn = "Replace the original file in-place to free phone storage",
        subtitleEs = "Reemplazar el archivo original para liberar espacio"
    )
}

data class ClipTrimPreset(
    val id: String,
    val labelEn: String,
    val labelEs: String,
    val durationMs: Long
)

data class ClipTrimResult(
    val success: Boolean,
    val outputFile: File? = null,
    val outputFileName: String = "",
    val trimmedDurationMs: Long = 0L,
    val newSizeBytes: Long = 0L,
    val bytesSaved: Long = 0L,
    val saveMode: ClipTrimSaveMode = ClipTrimSaveMode.SAVE_AS_NEW_CLIP,
    val messageEn: String = "",
    val messageEs: String = ""
)

/**
 * Lightweight, native Android MediaExtractor + MediaMuxer / MediaCodec lossless video trimmer.
 *
 * Allows players to slice a 15-second, 30-second, or custom highlight directly out of a
 * massive 4K / 120fps gameplay recording in under a second without re-encoding quality loss,
 * and either save it as a new clip, save & delete the original, or replace the original file.
 */
object LocalVideoTrimmer {

    const val MIN_TRIM_DURATION_MS = 1_000L

    val QUICK_CLUTCH_PRESETS = listOf(
        ClipTrimPreset("clutch_15s", "15s Clutch", "Clutch 15s", 15_000L),
        ClipTrimPreset("highlight_30s", "30s Highlight", "Destacado 30s", 30_000L),
        ClipTrimPreset("round_60s", "60s Round", "Ronda 60s", 60_000L)
    )

    /**
     * Normalizes and clamps [startMs] and [endMs] within [totalDurationMs], enforcing at least [MIN_TRIM_DURATION_MS].
     */
    fun normalizeTrimRange(
        startMs: Long,
        endMs: Long,
        totalDurationMs: Long
    ): Pair<Long, Long> {
        val safeTotal = max(MIN_TRIM_DURATION_MS, totalDurationMs)
        val clampedStart = startMs.coerceIn(0L, max(0L, safeTotal - MIN_TRIM_DURATION_MS))
        val clampedEnd = endMs.coerceIn(clampedStart + MIN_TRIM_DURATION_MS, safeTotal)
        return Pair(clampedStart, clampedEnd)
    }

    /**
     * Estimates the output file size of the trimmed clip in bytes based on the source duration and size.
     */
    fun estimateTrimmedSizeBytes(
        originalSizeBytes: Long,
        totalDurationMs: Long,
        trimStartMs: Long,
        trimEndMs: Long
    ): Long {
        if (originalSizeBytes <= 0L || totalDurationMs <= 0L) return 0L
        val (normStart, normEnd) = normalizeTrimRange(trimStartMs, trimEndMs, totalDurationMs)
        val sliceDuration = (normEnd - normStart).coerceIn(MIN_TRIM_DURATION_MS, totalDurationMs)
        val ratio = (sliceDuration.toDouble() / totalDurationMs.toDouble()).coerceIn(0.01, 1.0)
        return (originalSizeBytes * ratio).roundToLong().coerceAtLeast(1024L)
    }

    /**
     * Calculates how many bytes will be reclaimed when replacing or deleting the original recording.
     */
    fun estimateBytesSaved(
        originalSizeBytes: Long,
        totalDurationMs: Long,
        trimStartMs: Long,
        trimEndMs: Long,
        saveMode: ClipTrimSaveMode
    ): Long {
        if (saveMode == ClipTrimSaveMode.SAVE_AS_NEW_CLIP) return 0L
        val estTrimmed = estimateTrimmedSizeBytes(originalSizeBytes, totalDurationMs, trimStartMs, trimEndMs)
        return max(0L, originalSizeBytes - estTrimmed)
    }

    fun formatStorageSize(bytes: Long): String {
        val safeBytes = max(0L, bytes)
        val mb = safeBytes.toDouble() / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        } else if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            val kb = safeBytes.toDouble() / 1024.0
            String.format(Locale.US, "%.0f KB", kb)
        }
    }

    /**
     * Resolves the real duration of a RecordedVideo in milliseconds using MediaMetadataRetriever if needed.
     */
    fun resolveVideoDurationMs(context: Context, video: RecordedVideo): Long {
        if (video.durationMs >= MIN_TRIM_DURATION_MS) return video.durationMs
        val retriever = MediaMetadataRetriever()
        return try {
            val file = File(video.filePath)
            if (file.exists() && file.length() > 0L) {
                retriever.setDataSource(file.absolutePath)
            } else if (video.uriString.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(video.uriString))
            }
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durStr?.toLongOrNull()?.coerceAtLeast(MIN_TRIM_DURATION_MS) ?: 15_000L
        } catch (_: Exception) {
            15_000L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Executes lossless native Android MediaExtractor + MediaMuxer trimming on [video] between
     * [startMs] and [endMs] according to [saveMode].
     */
    fun trimVideo(
        context: Context,
        video: RecordedVideo,
        startMs: Long,
        endMs: Long,
        saveMode: ClipTrimSaveMode
    ): ClipTrimResult {
        val sourceFile = resolveReadableSourceFile(context, video)
            ?: return ClipTrimResult(
                success = false,
                saveMode = saveMode,
                messageEn = "Source video file could not be opened for trimming.",
                messageEs = "No se pudo abrir el archivo de video original para recortar."
            )

        val originalSize = if (sourceFile.exists() && sourceFile.length() > 0L) {
            sourceFile.length()
        } else {
            video.sizeBytes
        }

        val totalDurationMs = resolveVideoDurationMs(context, video)
        val (normStartMs, normEndMs) = normalizeTrimRange(startMs, endMs, totalDurationMs)
        val trimmedDurationMs = (normEndMs - normStartMs).coerceAtLeast(MIN_TRIM_DURATION_MS)

        val moviesDir = sourceFile.parentFile ?: run {
            val extMovies = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            File(extMovies, "GMaxRecordings").apply { if (!exists()) mkdirs() }
        }
        if (!moviesDir.exists()) moviesDir.mkdirs()

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val baseSlug = video.gameName.replace(Regex("[^A-Za-z0-9]"), "").ifBlank { "Gameplay" }.take(16)
        val newClipFileName = "GMax_${baseSlug}_Trim_${timeStamp}.mp4"
        val tempOutFile = File(moviesDir, ".trim_tmp_${System.currentTimeMillis()}.mp4")

        val muxSuccess = sliceMp4WithMediaMuxer(
            sourcePath = sourceFile.absolutePath,
            destPath = tempOutFile.absolutePath,
            startUs = normStartMs * 1_000L,
            endUs = normEndMs * 1_000L
        )

        if (!muxSuccess || !tempOutFile.exists() || tempOutFile.length() <= 0L) {
            try {
                tempOutFile.delete()
            } catch (_: Exception) {}
            return ClipTrimResult(
                success = false,
                saveMode = saveMode,
                messageEn = "Could not trim this clip. Ensure the selected range contains valid video frames.",
                messageEs = "No se pudo recortar este clip. Asegúrate de que el rango tenga cuadros válidos."
            )
        }

        return try {
            when (saveMode) {
                ClipTrimSaveMode.SAVE_AS_NEW_CLIP -> {
                    val destFile = File(moviesDir, newClipFileName)
                    tempOutFile.copyTo(destFile, overwrite = true)
                    tempOutFile.delete()
                    publishTrimmedVideoToGallery(context, destFile, trimmedDurationMs)
                    val newSize = destFile.length()
                    ClipTrimResult(
                        success = true,
                        outputFile = destFile,
                        outputFileName = destFile.name,
                        trimmedDurationMs = trimmedDurationMs,
                        newSizeBytes = newSize,
                        bytesSaved = 0L,
                        saveMode = saveMode,
                        messageEn = "Saved new ${trimmedDurationMs / 1000}s clip '${destFile.name}' (${formatStorageSize(newSize)})",
                        messageEs = "Nuevo clip de ${trimmedDurationMs / 1000}s guardado '${destFile.name}' (${formatStorageSize(newSize)})"
                    )
                }

                ClipTrimSaveMode.SAVE_NEW_AND_DELETE_ORIGINAL -> {
                    val destFile = File(moviesDir, newClipFileName)
                    tempOutFile.copyTo(destFile, overwrite = true)
                    tempOutFile.delete()
                    val newSize = destFile.length()
                    val savedBytes = max(0L, originalSize - newSize)
                    ScreenRecordingManager.deleteRecording(context, video)
                    publishTrimmedVideoToGallery(context, destFile, trimmedDurationMs)
                    ClipTrimResult(
                        success = true,
                        outputFile = destFile,
                        outputFileName = destFile.name,
                        trimmedDurationMs = trimmedDurationMs,
                        newSizeBytes = newSize,
                        bytesSaved = savedBytes,
                        saveMode = saveMode,
                        messageEn = "Saved '${destFile.name}' & deleted original (Freed ${formatStorageSize(savedBytes)})",
                        messageEs = "Guardado '${destFile.name}' y original eliminado (Liberados ${formatStorageSize(savedBytes)})"
                    )
                }

                ClipTrimSaveMode.OVERWRITE_ORIGINAL -> {
                    val targetOriginalFile = File(video.filePath).let { f ->
                        if (f.parentFile?.exists() == true) f else File(moviesDir, video.title.ifBlank { newClipFileName })
                    }
                    tempOutFile.copyTo(targetOriginalFile, overwrite = true)
                    tempOutFile.delete()
                    val newSize = targetOriginalFile.length()
                    val savedBytes = max(0L, originalSize - newSize)

                    // Also update public Movies/GMaxRecordings copy if present
                    try {
                        val pubMovies = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                            "GMaxRecordings"
                        )
                        val pubCopy = File(pubMovies, targetOriginalFile.name)
                        if (pubCopy.exists() && pubCopy.absolutePath != targetOriginalFile.absolutePath) {
                            targetOriginalFile.copyTo(pubCopy, overwrite = true)
                        }
                    } catch (_: Exception) {}

                    // Refresh MediaStore entry so gallery reflects the new trimmed size & duration
                    if (video.uriString.startsWith("content://")) {
                        try {
                            val uri = Uri.parse(video.uriString)
                            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                                targetOriginalFile.inputStream().use { it.copyTo(out) }
                            }
                            val cv = ContentValues().apply {
                                put(MediaStore.Video.Media.DURATION, trimmedDurationMs)
                                put(MediaStore.Video.Media.SIZE, newSize)
                            }
                            context.contentResolver.update(uri, cv, null, null)
                        } catch (_: Exception) {}
                    } else {
                        publishTrimmedVideoToGallery(context, targetOriginalFile, trimmedDurationMs)
                    }

                    ClipTrimResult(
                        success = true,
                        outputFile = targetOriginalFile,
                        outputFileName = targetOriginalFile.name,
                        trimmedDurationMs = trimmedDurationMs,
                        newSizeBytes = newSize,
                        bytesSaved = savedBytes,
                        saveMode = saveMode,
                        messageEn = "Updated '${targetOriginalFile.name}' as new original (Freed ${formatStorageSize(savedBytes)})",
                        messageEs = "Actualizado '${targetOriginalFile.name}' como nuevo original (Liberados ${formatStorageSize(savedBytes)})"
                    )
                }
            }
        } catch (e: Exception) {
            try {
                tempOutFile.delete()
            } catch (_: Exception) {}
            ClipTrimResult(
                success = false,
                saveMode = saveMode,
                messageEn = "Failed to save trimmed video: ${e.localizedMessage ?: "I/O error"}",
                messageEs = "Error al guardar el video recortado: ${e.localizedMessage ?: "Error de E/S"}"
            )
        }
    }

    private fun resolveReadableSourceFile(context: Context, video: RecordedVideo): File? {
        val directFile = File(video.filePath)
        if (directFile.exists() && directFile.length() > 0L) {
            return directFile
        }
        if (video.uriString.startsWith("content://")) {
            return try {
                val cacheFile = File(context.cacheDir, "trim_src_${System.currentTimeMillis()}.mp4")
                context.contentResolver.openInputStream(Uri.parse(video.uriString))?.use { input ->
                    cacheFile.outputStream().use { out ->
                        input.copyTo(out)
                    }
                }
                if (cacheFile.exists() && cacheFile.length() > 0L) cacheFile else null
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    /**
     * Core native Android MediaExtractor + MediaMuxer packet slicer.
     * Copies encoded video and audio tracks between [startUs] and [endUs] with zero re-encode loss.
     */
    fun sliceMp4WithMediaMuxer(
        sourcePath: String,
        destPath: String,
        startUs: Long,
        endUs: Long
    ): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(sourcePath)
            val trackCount = extractor.trackCount
            if (trackCount <= 0) return false

            muxer = MediaMuxer(destPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val indexMap = HashMap<Int, Int>(trackCount)
            var bufferSize = 1 * 1024 * 1024 // Default 1 MB packet buffer

            // Preserve video rotation metadata if present
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(sourcePath)
                val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                val rotationDegrees = rotationStr?.toIntOrNull() ?: 0
                if (rotationDegrees == 90 || rotationDegrees == 180 || rotationDegrees == 270) {
                    muxer.setOrientationHint(rotationDegrees)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    val dstIndex = muxer.addTrack(format)
                    indexMap[i] = dstIndex
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        val maxInput = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                        bufferSize = max(bufferSize, maxInput)
                    }
                }
            }

            if (indexMap.isEmpty()) return false

            if (startUs > 0L) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            } else {
                extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            val dstBuf = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            muxer.start()

            var firstSampleTimeUs = -1L
            var wroteSamples = 0

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(dstBuf, 0)
                if (bufferInfo.size < 0) {
                    bufferInfo.size = 0
                    break
                }

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs < 0L) break
                if (endUs > startUs && sampleTimeUs > endUs) {
                    break
                }

                val trackIndex = extractor.sampleTrackIndex
                val dstTrackIndex = indexMap[trackIndex]
                if (dstTrackIndex != null) {
                    if (firstSampleTimeUs < 0L) {
                        firstSampleTimeUs = sampleTimeUs
                    }
                    bufferInfo.presentationTimeUs = max(0L, sampleTimeUs - firstSampleTimeUs)
                    val sampleFlags = extractor.sampleFlags
                    var codecFlags = 0
                    if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                    }
                    if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0 &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ) {
                        codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
                    }
                    bufferInfo.flags = codecFlags
                    muxer.writeSampleData(dstTrackIndex, dstBuf, bufferInfo)
                    wroteSamples++
                }
                extractor.advance()
            }

            try {
                muxer.stop()
            } catch (_: Exception) {}
            wroteSamples > 0
        } catch (_: Exception) {
            false
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {}
            try {
                muxer?.release()
            } catch (_: Exception) {}
        }
    }

    private fun publishTrimmedVideoToGallery(
        context: Context,
        file: File,
        durationMs: Long
    ) {
        try {
            val pubMovies = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "GMaxRecordings"
            )
            if (!pubMovies.exists()) pubMovies.mkdirs()
            val pubFile = File(pubMovies, file.name)
            if (pubFile.absolutePath != file.absolutePath) {
                file.copyTo(pubFile, overwrite = true)
            }
        } catch (_: Exception) {}

        try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.DURATION, durationMs)
                put(MediaStore.Video.Media.SIZE, file.length())
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_MOVIES + "/GMaxRecordings"
                    )
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                }
            } else {
                @Suppress("DEPRECATION")
                context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)))
            }
        } catch (_: Exception) {}
    }
}
