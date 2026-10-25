package com.example.util

import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.WindowManager
import com.example.model.RecordedVideo
import com.example.model.ResolutionPreset
import com.example.model.VideoQualitySettings
import java.io.File

object ScreenRecordingManager {

    private const val PREFS_NAME = "gmax_screen_recording_prefs"
    private const val KEY_RESOLUTION_LABEL = "rec_res_label"
    private const val KEY_RES_WIDTH = "rec_res_width"
    private const val KEY_RES_HEIGHT = "rec_res_height"
    private const val KEY_FPS = "rec_fps"
    private const val KEY_BITRATE_MBPS = "rec_bitrate_mbps"
    private const val KEY_RECORD_MIC = "rec_mic"
    private const val KEY_SHOW_TIMER_HUD = "rec_show_timer_hud"
    private const val KEY_OVERLAY_ENABLED = "rec_overlay_enabled"
    private const val KEY_PERMISSION_ASKED = "rec_permissions_asked_once"

    // Master list of standard presets
    val ALL_PRESETS = listOf(
        ResolutionPreset(label = "480p", width = 854, height = 480, minRamGbRequired = 1.0),
        ResolutionPreset(label = "540p", width = 960, height = 540, minRamGbRequired = 1.5),
        ResolutionPreset(label = "640p", width = 1024, height = 640, minRamGbRequired = 2.0),
        ResolutionPreset(label = "720p", width = 1280, height = 720, minRamGbRequired = 2.0),
        ResolutionPreset(label = "1080p", width = 1920, height = 1080, minRamGbRequired = 3.0),
        ResolutionPreset(label = "1440p", width = 2560, height = 1440, minRamGbRequired = 6.0),
        ResolutionPreset(label = "2K", width = 2560, height = 1440, minRamGbRequired = 6.0),
        ResolutionPreset(label = "4K", width = 3840, height = 2160, minRamGbRequired = 8.0)
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun hasRequestedPermissionsOnce(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_PERMISSION_ASKED, false)
    }

    fun setRequestedPermissionsOnce(context: Context, asked: Boolean = true) {
        getPrefs(context).edit().putBoolean(KEY_PERMISSION_ASKED, asked).apply()
    }

    /**
     * Determines which resolutions the device actually supports based on screen dimensions and hardware.
     * Only displays resolutions > 1080p if the physical display supports it.
     */
    fun getSupportedResolutions(context: Context): List<ResolutionPreset> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val displayMetrics = context.resources.displayMetrics
        val maxScreenDimension = maxOf(displayMetrics.widthPixels, displayMetrics.heightPixels)

        return ALL_PRESETS.filter { preset ->
            // Resolutions <= 1080p are always supported as scaled projections
            if (preset.width <= 1920 && preset.height <= 1080) {
                true
            } else {
                // Higher resolutions (> 1080p, like 1440p, 2K, 4K) only if physical display supports it
                maxScreenDimension >= preset.width - 200
            }
        }
    }

    /**
     * Determines which frame rates the device actually supports based on display refresh rate.
     * Only shows > 60 FPS (90fps, 120fps) if the display actually runs at that frequency.
     */
    fun getSupportedFps(context: Context): List<Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        @Suppress("DEPRECATION")
        val refreshRate = try {
            windowManager?.defaultDisplay?.refreshRate?.toInt() ?: 60
        } catch (_: Exception) { 60 }

        val allFps = listOf(30, 60, 90, 120)
        return allFps.filter { fps ->
            if (fps <= 60) true
            else refreshRate >= fps - 5
        }
    }

    /**
     * Automatically configures the ideal settings for the device without lag
     * and with stable clearance in screen quality.
     */
    fun getAutoConfiguredIdealSettings(context: Context): VideoQualitySettings {
        val supportedResolutions = getSupportedResolutions(context)
        val supportedFps = getSupportedFps(context)

        val totalRamMb = try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            memInfo.totalMem / (1024 * 1024)
        } catch (_: Exception) { 4000L }

        // Pick ideal resolution:
        // High RAM (>= 6GB): 1080p (or 1440p if available)
        // Mid RAM (3GB - 6GB): 1080p or 720p
        // Budget (< 3GB): 720p or 540p
        val idealPreset = when {
            totalRamMb >= 6000 && supportedResolutions.any { it.label == "1080p" } ->
                supportedResolutions.first { it.label == "1080p" }
            totalRamMb >= 3000 && supportedResolutions.any { it.label == "720p" } ->
                supportedResolutions.first { it.label == "720p" }
            supportedResolutions.any { it.label == "720p" } ->
                supportedResolutions.first { it.label == "720p" }
            else -> supportedResolutions.firstOrNull() ?: ResolutionPreset("720p", 1280, 720)
        }

        // Pick ideal FPS:
        // High RAM: 60 FPS
        // Budget: 30 FPS
        val idealFps = when {
            totalRamMb >= 4000 && supportedFps.contains(60) -> 60
            supportedFps.contains(30) -> 30
            else -> 60
        }

        // Bitrate without lag:
        // 1080p: 8 Mbps
        // 720p: 6 Mbps
        // 480p/540p: 3 Mbps
        val idealBitrate = when (idealPreset.height) {
            in 1080..2160 -> 8
            in 720..1079 -> 6
            else -> 4
        }

        return VideoQualitySettings(
            resolutionLabel = idealPreset.label,
            width = idealPreset.width,
            height = idealPreset.height,
            fps = idealFps,
            bitrateMbps = idealBitrate,
            recordMic = true,
            showTimerHud = true,
            isOverlayEnabled = true
        )
    }

    fun loadSettings(context: Context): VideoQualitySettings {
        val prefs = getPrefs(context)
        if (!prefs.contains(KEY_RESOLUTION_LABEL)) {
            // First time load: use auto-configured ideal
            val defaults = getAutoConfiguredIdealSettings(context)
            saveSettings(context, defaults)
            return defaults
        }

        val resLabel = prefs.getString(KEY_RESOLUTION_LABEL, "1080p") ?: "1080p"
        val width = prefs.getInt(KEY_RES_WIDTH, 1920)
        val height = prefs.getInt(KEY_RES_HEIGHT, 1080)
        val fps = prefs.getInt(KEY_FPS, 60)
        val bitrate = prefs.getInt(KEY_BITRATE_MBPS, 8)
        val mic = prefs.getBoolean(KEY_RECORD_MIC, true)
        val showTimer = prefs.getBoolean(KEY_SHOW_TIMER_HUD, true)
        val overlay = prefs.getBoolean(KEY_OVERLAY_ENABLED, false)

        return VideoQualitySettings(
            resolutionLabel = resLabel,
            width = width,
            height = height,
            fps = fps,
            bitrateMbps = bitrate,
            recordMic = mic,
            showTimerHud = showTimer,
            isOverlayEnabled = overlay
        )
    }

    fun saveSettings(context: Context, settings: VideoQualitySettings) {
        getPrefs(context).edit()
            .putString(KEY_RESOLUTION_LABEL, settings.resolutionLabel)
            .putInt(KEY_RES_WIDTH, settings.width)
            .putInt(KEY_RES_HEIGHT, settings.height)
            .putInt(KEY_FPS, settings.fps)
            .putInt(KEY_BITRATE_MBPS, settings.bitrateMbps)
            .putBoolean(KEY_RECORD_MIC, settings.recordMic)
            .putBoolean(KEY_SHOW_TIMER_HUD, settings.showTimerHud)
            .putBoolean(KEY_OVERLAY_ENABLED, settings.isOverlayEnabled)
            .apply()
    }

    fun setOverlayEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_OVERLAY_ENABLED, enabled).apply()
    }

    /**
     * Queries past GMax screen recordings from device MediaStore Video table
     */
    fun queryRecordedVideos(context: Context): List<RecordedVideo> {
        val list = mutableListOf<RecordedVideo>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.RESOLUTION,
            MediaStore.Video.Media.DATA
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val resCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.RESOLUTION)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val name = c.getString(nameCol) ?: "Recording"
                    val path = c.getString(dataCol) ?: ""

                    // Filter to GMax recordings or videos in GMaxRecordings folder
                    if (name.startsWith("GMax_") || path.contains("GMaxRecordings") || path.contains("ScreenRecording")) {
                        val duration = c.getLong(durCol)
                        val size = c.getLong(sizeCol)
                        val date = c.getLong(dateCol)
                        val resolution = c.getString(resCol) ?: "HD"
                        val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                        list.add(
                            RecordedVideo(
                                id = id,
                                uriString = contentUri.toString(),
                                title = name,
                                durationMs = if (duration > 0) duration else 0L,
                                sizeBytes = size,
                                dateAddedSec = date,
                                resolution = resolution,
                                filePath = path
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        // Also check app-specific Movies/GMaxRecordings files in case MediaStore sync is delayed
        try {
            val moviesDir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES), "GMaxRecordings")
            if (moviesDir.exists()) {
                val files = moviesDir.listFiles { f -> f.extension.equals("mp4", ignoreCase = true) }
                files?.forEach { file ->
                    if (list.none { it.filePath == file.absolutePath }) {
                        list.add(
                            RecordedVideo(
                                id = file.hashCode().toLong(),
                                uriString = Uri.fromFile(file).toString(),
                                title = file.name,
                                durationMs = 0L,
                                sizeBytes = file.length(),
                                dateAddedSec = file.lastModified() / 1000,
                                resolution = "HD",
                                filePath = file.absolutePath
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        return list.sortedByDescending { it.dateAddedSec }
    }

    /**
     * Deletes a recorded video file and its MediaStore entry
     */
    fun deleteRecording(context: Context, video: RecordedVideo): Boolean {
        var success = false
        try {
            if (video.uriString.startsWith("content://")) {
                val uri = Uri.parse(video.uriString)
                val rows = context.contentResolver.delete(uri, null, null)
                if (rows > 0) success = true
            }
        } catch (_: Exception) {}

        try {
            val file = File(video.filePath)
            if (file.exists()) {
                file.delete()
                success = true
            }
        } catch (_: Exception) {}

        return success
    }
}
