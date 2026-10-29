package com.example.util

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.WindowManager
import com.example.model.AudioSourceMode
import com.example.model.CapturedScreenshot
import com.example.model.GameMediaGroup
import com.example.model.RecordedVideo
import com.example.model.ResolutionPreset
import com.example.model.VideoQualitySettings
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ScreenRecordingManager {

    private const val PREFS_NAME = "gmax_screen_recording_prefs"
    private const val KEY_RESOLUTION_LABEL = "rec_res_label"
    private const val KEY_RES_WIDTH = "rec_res_width"
    private const val KEY_RES_HEIGHT = "rec_res_height"
    private const val KEY_FPS = "rec_fps"
    private const val KEY_BITRATE_MBPS = "rec_bitrate_mbps"
    private const val KEY_RECORD_MIC = "rec_mic"
    private const val KEY_AUDIO_SOURCE_MODE = "rec_audio_source_mode"
    private const val KEY_SHOW_TIMER_HUD = "rec_show_timer_hud"
    private const val KEY_OVERLAY_ENABLED = "rec_overlay_enabled"
    private const val KEY_BUBBLE_SIZE_DP = "rec_bubble_size_dp"
    private const val KEY_PERMISSION_ASKED = "rec_permissions_asked_once"
    private const val KEY_ACTIVE_GAME_PKG = "rec_active_game_pkg"
    private const val KEY_ACTIVE_GAME_NAME = "rec_active_game_name"
    private const val KEY_MEDIA_GAME_TAGS_JSON = "rec_media_game_tags_json"

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
     * Records the active game context so subsequent recordings and screenshots are automatically
     * associated with that game.
     */
    fun setActiveGameContext(context: Context, packageName: String?, appName: String?) {
        getPrefs(context).edit()
            .putString(KEY_ACTIVE_GAME_PKG, packageName)
            .putString(KEY_ACTIVE_GAME_NAME, appName)
            .apply()
    }

    private fun getPublicMediaTagsFile(): File {
        val docs = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOCUMENTS
        )
        val hubDir = File(docs, "GMaxHub")
        if (!hubDir.exists()) hubDir.mkdirs()
        return File(hubDir, "media_game_tags.json")
    }

    private fun saveMediaTagMapping(context: Context, fileName: String, gameName: String, gamePackage: String?) {
        try {
            val prefs = getPrefs(context)
            var raw = prefs.getString(KEY_MEDIA_GAME_TAGS_JSON, null)
            if (raw.isNullOrBlank()) {
                val f = getPublicMediaTagsFile()
                if (f.exists()) raw = f.readText()
            }
            val root = if (!raw.isNullOrBlank()) JSONObject(raw) else JSONObject()
            val entry = JSONObject().apply {
                put("gameName", gameName)
                put("gamePackage", gamePackage ?: "")
            }
            root.put(fileName, entry)
            val str = root.toString()
            prefs.edit().putString(KEY_MEDIA_GAME_TAGS_JSON, str).apply()
            try {
                getPublicMediaTagsFile().writeText(str)
            } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    private fun sanitizeGameSlug(appName: String): String {
        val cleaned = appName.replace(Regex("[^A-Za-z0-9]"), "")
        return if (cleaned.isNotBlank()) cleaned.take(18) else "Gameplay"
    }

    /**
     * Resolves the currently active or most recent game for tagging a new capture,
     * returning Triple(gameSlug, gameName, gamePackage).
     */
    private fun resolveActiveGameForNewCapture(context: Context): Triple<String, String, String?> {
        // 1. Check live foreground / recent game via GamePlayStatsHelper
        val detected = GamePlayStatsHelper.resolveCurrentOrRecentGame(context)
        if (detected != null) {
            val (pkg, name) = detected
            return Triple(sanitizeGameSlug(name), name, pkg)
        }

        // 2. Check saved active game in Recorder prefs
        val prefs = getPrefs(context)
        val savedPkg = prefs.getString(KEY_ACTIVE_GAME_PKG, null)
        val savedName = prefs.getString(KEY_ACTIVE_GAME_NAME, null)
        if (!savedName.isNullOrBlank()) {
            return Triple(sanitizeGameSlug(savedName), savedName, savedPkg)
        }

        return Triple("General", "General Gameplay", null)
    }

    fun buildTaggedVideoFileName(context: Context): String {
        val (slug, gameName, gamePkg) = resolveActiveGameForNewCapture(context)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "GMax_${slug}_${timeStamp}.mp4"
        saveMediaTagMapping(context, fileName, gameName, gamePkg)
        return fileName
    }

    fun buildTaggedScreenshotFileName(context: Context): String {
        val (slug, gameName, gamePkg) = resolveActiveGameForNewCapture(context)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "GMax_Shot_${slug}_${timeStamp}.png"
        saveMediaTagMapping(context, fileName, gameName, gamePkg)
        return fileName
    }

    /**
     * Resolves (gameName, gamePackage) for an existing video or screenshot file.
     */
    fun resolveGameForMediaFile(context: Context, fileName: String): Pair<String, String?> {
        // 1. Check persisted JSON tag map
        try {
            val prefs = getPrefs(context)
            var raw = prefs.getString(KEY_MEDIA_GAME_TAGS_JSON, null)
            if (raw.isNullOrBlank()) {
                val f = getPublicMediaTagsFile()
                if (f.exists()) {
                    raw = f.readText()
                    if (!raw.isNullOrBlank()) {
                        prefs.edit().putString(KEY_MEDIA_GAME_TAGS_JSON, raw).apply()
                    }
                }
            }
            if (!raw.isNullOrBlank()) {
                val root = JSONObject(raw)
                val obj = root.optJSONObject(fileName)
                if (obj != null) {
                    val gName = obj.optString("gameName", "")
                    val gPkg = obj.optString("gamePackage", "").takeIf { it.isNotBlank() }
                    if (gName.isNotBlank()) {
                        return Pair(gName, gPkg)
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Parse slug from filename: GMax_<Slug>_<yyyyMMdd>_<HHmmss>.mp4 or GMax_Shot_<Slug>_<yyyyMMdd>_<HHmmss>.png
        val base = fileName.substringBeforeLast(".")
        val parts = base.split("_")
        val candidateSlug = when {
            parts.size >= 4 && parts[0] == "GMax" && parts[1] == "Shot" && !parts[2].all { it.isDigit() } -> parts[2]
            parts.size >= 3 && parts[0] == "GMax" && parts[1] != "Shot" && !parts[1].all { it.isDigit() } -> parts[1]
            else -> null
        }

        if (!candidateSlug.isNullOrBlank() && !candidateSlug.equals("General", ignoreCase = true)) {
            // Match slug against installed or library games for exact title and icon package
            try {
                val sessionMgr = BoosterSessionManager(context)
                val libPkgs = sessionMgr.getLibraryGamePackages()
                val pm = context.packageManager
                for (pkg in libPkgs) {
                    try {
                        val info = pm.getApplicationInfo(pkg, 0)
                        val label = pm.getApplicationLabel(info).toString()
                        if (sanitizeGameSlug(label).equals(candidateSlug, ignoreCase = true)) {
                            return Pair(label, pkg)
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}

            // Convert CamelCase slug back to spaced readable title
            val readable = candidateSlug.replace(Regex("([a-z])([A-Z])"), "$1 $2")
            return Pair(readable, null)
        }

        // 3. Fallback to last target game if available, else General Gameplay
        val sessionMgr = BoosterSessionManager(context)
        val (lastPkg, lastName) = sessionMgr.getLastTargetGame()
        if (!lastName.isNullOrBlank()) {
            return Pair(lastName, lastPkg)
        }

        return Pair("General Gameplay", null)
    }

    fun groupVideosByGame(videos: List<RecordedVideo>): List<GameMediaGroup<RecordedVideo>> {
        if (videos.isEmpty()) return emptyList()
        return videos
            .groupBy { it.gameName }
            .map { (gameName, list) ->
                val pkg = list.firstNotNullOfOrNull { it.gamePackage }
                GameMediaGroup(
                    gameName = gameName,
                    gamePackage = pkg,
                    items = list.sortedByDescending { it.dateAddedSec }
                )
            }
            .sortedWith(
                compareBy<GameMediaGroup<RecordedVideo>> { it.gameName == "General Gameplay" }
                    .thenByDescending { it.items.maxOfOrNull { v -> v.dateAddedSec } ?: 0L }
            )
    }

    fun groupScreenshotsByGame(screenshots: List<CapturedScreenshot>): List<GameMediaGroup<CapturedScreenshot>> {
        if (screenshots.isEmpty()) return emptyList()
        return screenshots
            .groupBy { it.gameName }
            .map { (gameName, list) ->
                val pkg = list.firstNotNullOfOrNull { it.gamePackage }
                GameMediaGroup(
                    gameName = gameName,
                    gamePackage = pkg,
                    items = list.sortedByDescending { it.dateAddedSec }
                )
            }
            .sortedWith(
                compareBy<GameMediaGroup<CapturedScreenshot>> { it.gameName == "General Gameplay" }
                    .thenByDescending { it.items.maxOfOrNull { s -> s.dateAddedSec } ?: 0L }
            )
    }

    /**
     * Determines which resolutions the device actually supports based on screen dimensions and hardware.
     * Only displays resolutions > 1080p if the physical display supports it.
     */
    fun getSupportedResolutions(context: Context): List<ResolutionPreset> {
        val displayMetrics = context.resources.displayMetrics
        val maxScreenDimension = maxOf(displayMetrics.widthPixels, displayMetrics.heightPixels)

        return ALL_PRESETS.filter { preset ->
            if (preset.width <= 1920 && preset.height <= 1080) {
                true
            } else {
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

        val idealPreset = when {
            totalRamMb >= 10000 && supportedResolutions.any { it.label == "4K" } ->
                supportedResolutions.first { it.label == "4K" }
            totalRamMb >= 7000 && supportedResolutions.any { it.label == "1440p" || it.label == "2K" } ->
                supportedResolutions.first { it.label == "1440p" || it.label == "2K" }
            totalRamMb >= 3500 && supportedResolutions.any { it.label == "1080p" } ->
                supportedResolutions.first { it.label == "1080p" }
            supportedResolutions.any { it.label == "720p" } ->
                supportedResolutions.first { it.label == "720p" }
            else -> supportedResolutions.firstOrNull() ?: ResolutionPreset("1080p", 1920, 1080)
        }

        val idealFps = when {
            totalRamMb >= 10000 && supportedFps.contains(120) -> 120
            totalRamMb >= 7000 && supportedFps.contains(90) -> 90
            totalRamMb >= 3500 && supportedFps.contains(60) -> 60
            supportedFps.contains(60) -> 60
            supportedFps.contains(30) -> 30
            else -> 60
        }

        val idealBitrate = when {
            idealPreset.height >= 2160 -> 16
            idealPreset.height >= 1440 -> 12
            idealPreset.height >= 1080 -> 8
            idealPreset.height >= 720 -> 6
            else -> 4
        }

        return VideoQualitySettings(
            resolutionLabel = idealPreset.label,
            width = idealPreset.width,
            height = idealPreset.height,
            fps = idealFps,
            bitrateMbps = idealBitrate,
            recordMic = false,
            audioSourceMode = AudioSourceMode.INTERNAL_ONLY,
            showTimerHud = false,
            isOverlayEnabled = false
        )
    }

    fun loadSettings(context: Context): VideoQualitySettings {
        val prefs = getPrefs(context)
        val hasResolution = prefs.contains(KEY_RESOLUTION_LABEL)
        val defaultIdeal = if (!hasResolution) getAutoConfiguredIdealSettings(context) else null

        val resLabel = prefs.getString(KEY_RESOLUTION_LABEL, defaultIdeal?.resolutionLabel ?: "1080p") ?: "1080p"
        val width = prefs.getInt(KEY_RES_WIDTH, defaultIdeal?.width ?: 1920)
        val height = prefs.getInt(KEY_RES_HEIGHT, defaultIdeal?.height ?: 1080)
        val fps = prefs.getInt(KEY_FPS, defaultIdeal?.fps ?: 60)
        val bitrate = prefs.getInt(KEY_BITRATE_MBPS, defaultIdeal?.bitrateMbps ?: 8)
        val mic = prefs.getBoolean(KEY_RECORD_MIC, false)
        val savedAudioModeStr = prefs.getString(KEY_AUDIO_SOURCE_MODE, null)
        val audioMode = when {
            !savedAudioModeStr.isNullOrBlank() -> {
                try {
                    AudioSourceMode.valueOf(savedAudioModeStr)
                } catch (_: Exception) {
                    AudioSourceMode.INTERNAL_ONLY
                }
            }
            mic -> AudioSourceMode.INTERNAL_AND_MIC
            else -> AudioSourceMode.INTERNAL_ONLY
        }
        val showTimer = prefs.getBoolean(KEY_SHOW_TIMER_HUD, false)
        val overlay = prefs.getBoolean(KEY_OVERLAY_ENABLED, false)
        val bubbleSize = prefs.getInt(KEY_BUBBLE_SIZE_DP, 38).coerceIn(28, 64)

        val settings = VideoQualitySettings(
            resolutionLabel = resLabel,
            width = width,
            height = height,
            fps = fps,
            bitrateMbps = bitrate,
            recordMic = audioMode == AudioSourceMode.INTERNAL_AND_MIC || audioMode == AudioSourceMode.MIC_ONLY,
            audioSourceMode = audioMode,
            showTimerHud = showTimer,
            isOverlayEnabled = overlay,
            bubbleSizeDp = bubbleSize
        )
        if (!hasResolution || savedAudioModeStr == null) {
            saveSettings(context, settings)
        }
        return settings
    }

    fun saveSettings(context: Context, settings: VideoQualitySettings) {
        val micIncluded = settings.audioSourceMode == AudioSourceMode.INTERNAL_AND_MIC ||
            settings.audioSourceMode == AudioSourceMode.MIC_ONLY
        getPrefs(context).edit()
            .putString(KEY_RESOLUTION_LABEL, settings.resolutionLabel)
            .putInt(KEY_RES_WIDTH, settings.width)
            .putInt(KEY_RES_HEIGHT, settings.height)
            .putInt(KEY_FPS, settings.fps)
            .putInt(KEY_BITRATE_MBPS, settings.bitrateMbps)
            .putBoolean(KEY_RECORD_MIC, micIncluded)
            .putString(KEY_AUDIO_SOURCE_MODE, settings.audioSourceMode.name)
            .putBoolean(KEY_SHOW_TIMER_HUD, settings.showTimerHud)
            .putBoolean(KEY_OVERLAY_ENABLED, settings.isOverlayEnabled)
            .putInt(KEY_BUBBLE_SIZE_DP, settings.bubbleSizeDp.coerceIn(28, 64))
            .apply()
    }

    fun setOverlayEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_OVERLAY_ENABLED, enabled).apply()
    }

    /**
     * Queries past GMax screen recordings from device MediaStore Video table and public folders,
     * automatically resolving the associated game for each video.
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

                    if (name.startsWith("GMax_") || path.contains("GMaxRecordings") || path.contains("ScreenRecording")) {
                        val duration = c.getLong(durCol)
                        val size = c.getLong(sizeCol)
                        val date = c.getLong(dateCol)
                        val resolution = c.getString(resCol) ?: "HD"
                        val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        val (gameTitle, gamePkg) = resolveGameForMediaFile(context, name)

                        list.add(
                            RecordedVideo(
                                id = id,
                                uriString = contentUri.toString(),
                                title = name,
                                durationMs = if (duration > 0) duration else 0L,
                                sizeBytes = size,
                                dateAddedSec = date,
                                resolution = resolution,
                                filePath = path,
                                gameName = gameTitle,
                                gamePackage = gamePkg
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        // Also check public Movies/GMaxRecordings and app-specific Movies/GMaxRecordings files
        val candidateMovieDirs = listOfNotNull(
            File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES), "GMaxRecordings"),
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)?.let { File(it, "GMaxRecordings") }
        )
        for (moviesDir in candidateMovieDirs) {
            try {
                if (moviesDir.exists()) {
                    val files = moviesDir.listFiles { f -> f.extension.equals("mp4", ignoreCase = true) }
                    files?.forEach { file ->
                        if (list.none { it.filePath == file.absolutePath || it.title == file.name }) {
                            val (gameTitle, gamePkg) = resolveGameForMediaFile(context, file.name)
                            list.add(
                                RecordedVideo(
                                    id = file.hashCode().toLong(),
                                    uriString = Uri.fromFile(file).toString(),
                                    title = file.name,
                                    durationMs = 0L,
                                    sizeBytes = file.length(),
                                    dateAddedSec = file.lastModified() / 1000,
                                    resolution = "HD",
                                    filePath = file.absolutePath,
                                    gameName = gameTitle,
                                    gamePackage = gamePkg
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }

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

    /**
     * Resolves a safe content Uri for sharing via FileProvider or MediaStore
     */
    fun getShareUri(context: Context, video: RecordedVideo): Uri? {
        return try {
            if (video.uriString.startsWith("content://")) {
                Uri.parse(video.uriString)
            } else {
                val file = File(video.filePath)
                if (file.exists()) {
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Exports and saves the video directly into the device's public Movies/Photos gallery
     */
    fun saveVideoToGallery(context: Context, video: RecordedVideo): Boolean {
        return try {
            val file = File(video.filePath)
            if (!file.exists()) return false

            val values = android.content.ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_MOVIES + "/GMaxRecordings")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                }
                true
            } else {
                @Suppress("DEPRECATION")
                context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)))
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Queries captured GMax screenshots from MediaStore and public/app Pictures/GMaxScreenshots,
     * automatically resolving the associated game for each screenshot.
     */
    fun queryCapturedScreenshots(context: Context): List<CapturedScreenshot> {
        val list = mutableListOf<CapturedScreenshot>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATA
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )
            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val widthCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val name = c.getString(nameCol) ?: "Screenshot"
                    val path = c.getString(dataCol) ?: ""

                    if (name.startsWith("GMax_Shot_") || path.contains("GMaxScreenshots")) {
                        val size = c.getLong(sizeCol)
                        val date = c.getLong(dateCol)
                        val w = c.getInt(widthCol)
                        val h = c.getInt(heightCol)
                        val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        val (gameTitle, gamePkg) = resolveGameForMediaFile(context, name)

                        list.add(
                            CapturedScreenshot(
                                id = id,
                                uriString = contentUri.toString(),
                                title = name,
                                sizeBytes = size,
                                dateAddedSec = date,
                                width = if (w > 0) w else 1920,
                                height = if (h > 0) h else 1080,
                                filePath = path,
                                gameName = gameTitle,
                                gamePackage = gamePkg
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        // Also check public Pictures/GMaxScreenshots and app-specific Pictures/GMaxScreenshots
        val candidatePicDirs = listOfNotNull(
            File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "GMaxScreenshots"),
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)?.let { File(it, "GMaxScreenshots") }
        )
        for (picsDir in candidatePicDirs) {
            try {
                if (picsDir.exists()) {
                    val files = picsDir.listFiles { f ->
                        f.extension.equals("png", ignoreCase = true) || f.extension.equals("jpg", ignoreCase = true)
                    }
                    files?.forEach { file ->
                        if (list.none { it.filePath == file.absolutePath || it.title == file.name }) {
                            val (gameTitle, gamePkg) = resolveGameForMediaFile(context, file.name)
                            list.add(
                                CapturedScreenshot(
                                    id = file.hashCode().toLong(),
                                    uriString = Uri.fromFile(file).toString(),
                                    title = file.name,
                                    sizeBytes = file.length(),
                                    dateAddedSec = file.lastModified() / 1000,
                                    width = 1920,
                                    height = 1080,
                                    filePath = file.absolutePath,
                                    gameName = gameTitle,
                                    gamePackage = gamePkg
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return list.sortedByDescending { it.dateAddedSec }
    }

    /**
     * Saves a Bitmap as a PNG screenshot into Pictures/GMaxScreenshots and MediaStore,
     * tagging the filename with the active game.
     */
    fun saveScreenshotBitmap(context: Context, bitmap: android.graphics.Bitmap): CapturedScreenshot? {
        return try {
            val fileName = buildTaggedScreenshotFileName(context)
            val (gameTitle, gamePkg) = resolveGameForMediaFile(context, fileName)
            val picsDir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "GMaxScreenshots")
            if (!picsDir.exists()) picsDir.mkdirs()

            val file = File(picsDir, fileName)
            file.outputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }

            // Also copy to public Pictures/GMaxScreenshots
            try {
                val pubPics = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "GMaxScreenshots")
                if (!pubPics.exists()) pubPics.mkdirs()
                file.copyTo(File(pubPics, fileName), overwrite = true)
            } catch (_: Exception) {}

            // Publish to public MediaStore Pictures/GMaxScreenshots
            try {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                    put(MediaStore.Images.Media.WIDTH, bitmap.width)
                    put(MediaStore.Images.Media.HEIGHT, bitmap.height)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/GMaxScreenshots")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        context.contentResolver.update(uri, values, null, null)
                    }
                }
            } catch (_: Exception) {}

            CapturedScreenshot(
                id = file.hashCode().toLong(),
                uriString = Uri.fromFile(file).toString(),
                title = fileName,
                sizeBytes = file.length(),
                dateAddedSec = System.currentTimeMillis() / 1000,
                width = bitmap.width,
                height = bitmap.height,
                filePath = file.absolutePath,
                gameName = gameTitle,
                gamePackage = gamePkg
            )
        } catch (_: Exception) {
            null
        }
    }

    fun deleteScreenshot(context: Context, shot: CapturedScreenshot): Boolean {
        var success = false
        try {
            if (shot.uriString.startsWith("content://")) {
                val rows = context.contentResolver.delete(Uri.parse(shot.uriString), null, null)
                if (rows > 0) success = true
            }
        } catch (_: Exception) {}
        try {
            val f = File(shot.filePath)
            if (f.exists()) {
                f.delete()
                success = true
            }
        } catch (_: Exception) {}
        return success
    }

    fun getScreenshotShareUri(context: Context, shot: CapturedScreenshot): Uri? {
        return try {
            if (shot.uriString.startsWith("content://")) {
                Uri.parse(shot.uriString)
            } else {
                val file = File(shot.filePath)
                if (file.exists()) {
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Computes live available storage in GB and estimated recordable duration based on current video bitrate.
     */
    fun getStorageCapacityInfo(context: Context): com.example.model.StorageCapacityInfo {
        return try {
            val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
            val totalBytes = stat.blockCountLong * stat.blockSizeLong
            val freeGb = freeBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            val usedRatio = if (totalBytes > 0L) {
                ((totalBytes - freeBytes).toFloat() / totalBytes.toFloat()).coerceIn(0.05f, 0.98f)
            } else 0.5f

            val settings = loadSettings(context)
            val bytesPerSecond = (settings.bitrateMbps.coerceAtLeast(1) * 1_000_000L) / 8L
            val recordableSeconds = (freeBytes / bytesPerSecond).coerceAtLeast(0L)
            val hours = recordableSeconds / 3600
            val minutes = (recordableSeconds % 3600) / 60
            val seconds = recordableSeconds % 60

            com.example.model.StorageCapacityInfo(
                freeBytes = freeBytes,
                totalBytes = totalBytes,
                availableGbText = String.format(Locale.US, "%.2f GB available", freeGb),
                recordableTimeText = String.format(
                    Locale.US,
                    "(%02d:%02d:%02d can be recorded)",
                    hours,
                    minutes,
                    seconds
                ),
                usedRatio = usedRatio
            )
        } catch (_: Exception) {
            com.example.model.StorageCapacityInfo()
        }
    }
}
