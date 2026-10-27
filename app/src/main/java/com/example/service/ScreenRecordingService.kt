package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.util.ScreenRecordingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenRecordingService : Service() {

    companion object {
        const val ACTION_START = "com.example.action.START_RECORDING"
        const val ACTION_STOP = "com.example.action.STOP_RECORDING"
        const val ACTION_TOGGLE_MIC = "com.example.action.TOGGLE_MIC"
        const val ACTION_CAPTURE_SCREENSHOT = "com.example.action.CAPTURE_SCREENSHOT"
        const val ACTION_EXIT_RECORDER = "com.example.action.EXIT_RECORDER"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_RECORD_MIC = "extra_record_mic"

        const val NOTIFICATION_ID = 4001
        const val CHANNEL_ID = "gmax_recording_channel"

        // Observable State for UI and Overlay
        private val _isRecording = MutableStateFlow(false)
        val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

        private val _recordingSeconds = MutableStateFlow(0)
        val recordingSeconds: StateFlow<Int> = _recordingSeconds.asStateFlow()

        private val _isMicEnabled = MutableStateFlow(false)
        val isMicEnabled: StateFlow<Boolean> = _isMicEnabled.asStateFlow()

        private val _lastSavedVideoUri = MutableStateFlow<String?>(null)
        val lastSavedVideoUri: StateFlow<String?> = _lastSavedVideoUri.asStateFlow()

        private val _lastSavedScreenshotUri = MutableStateFlow<String?>(null)
        val lastSavedScreenshotUri: StateFlow<String?> = _lastSavedScreenshotUri.asStateFlow()

        fun startRecording(context: Context, resultCode: Int, resultData: Intent, recordMic: Boolean = false) {
            val intent = Intent(context, ScreenRecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
                putExtra(EXTRA_RECORD_MIC, recordMic)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun captureScreenshot(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, ScreenRecordingService::class.java).apply {
                action = ACTION_CAPTURE_SCREENSHOT
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopRecording(context: Context) {
            val intent = Intent(context, ScreenRecordingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun buildRecorderNotification(
            context: Context,
            isRecordingActive: Boolean,
            elapsedSeconds: Int = 0
        ): android.app.Notification {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "G Max Screen Recorder",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Quick controls for screen recording and screenshots"
                    setShowBadge(false)
                }
                val nm = context.getSystemService(NotificationManager::class.java)
                nm?.createNotificationChannel(channel)
            }

            // Record or Stop Intent
            val recordIntent = if (isRecordingActive) {
                PendingIntent.getService(
                    context,
                    101,
                    Intent(context, ScreenRecordingService::class.java).apply { action = ACTION_STOP },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                PendingIntent.getActivity(
                    context,
                    102,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("extra_trigger_recording", true)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            // Open Videos Tab Intent
            val videosPendingIntent = PendingIntent.getActivity(
                context,
                103,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("extra_open_tab", 1) // Recorder Tab
                    putExtra("extra_recorder_subtab", 0) // Videos
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Capture Screenshot / Open Screenshots Tab Intent
            val screenshotsPendingIntent = PendingIntent.getActivity(
                context,
                104,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("extra_trigger_screenshot", true)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Exit Recorder Intent
            val exitPendingIntent = PendingIntent.getService(
                context,
                105,
                Intent(context, ScreenRecordingService::class.java).apply { action = ACTION_EXIT_RECORDER },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val collapsedView = RemoteViews(context.packageName, R.layout.notification_recorder_collapsed).apply {
                setImageViewResource(
                    R.id.notif_icon_record,
                    if (isRecordingActive) R.drawable.ic_notif_stop else R.drawable.ic_notif_record
                )
                setTextViewText(R.id.notif_txt_record, if (isRecordingActive) "Stop" else "Record")
                setOnClickPendingIntent(R.id.notif_btn_record, recordIntent)
                setOnClickPendingIntent(R.id.notif_btn_videos, videosPendingIntent)
                setOnClickPendingIntent(R.id.notif_btn_screenshots, screenshotsPendingIntent)
                setOnClickPendingIntent(R.id.notif_btn_exit, exitPendingIntent)
            }

            val mins = elapsedSeconds / 60
            val secs = elapsedSeconds % 60
            val statusTitle = if (isRecordingActive) {
                String.format(Locale.US, "Recording (%02d:%02d)", mins, secs)
            } else {
                "Recorder is ready"
            }
            val statusSubtitle = if (isRecordingActive) {
                "Tap here to stop recording"
            } else {
                "Tap here to start recording"
            }

            val expandedView = RemoteViews(context.packageName, R.layout.notification_recorder_expanded).apply {
                setTextViewText(R.id.notif_status_title, statusTitle)
                setTextViewText(R.id.notif_status_subtitle, statusSubtitle)
                setImageViewResource(
                    R.id.notif_exp_icon_record,
                    if (isRecordingActive) R.drawable.ic_notif_stop else R.drawable.ic_notif_record
                )
                setTextViewText(R.id.notif_exp_txt_record, if (isRecordingActive) "Stop" else "Record")
                setOnClickPendingIntent(R.id.notif_header_row, recordIntent)
                setOnClickPendingIntent(R.id.notif_exp_btn_record, recordIntent)
                setOnClickPendingIntent(R.id.notif_exp_btn_videos, videosPendingIntent)
                setOnClickPendingIntent(R.id.notif_exp_btn_screenshots, screenshotsPendingIntent)
                setOnClickPendingIntent(R.id.notif_exp_btn_exit, exitPendingIntent)
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notif_record)
                .setContentTitle(statusTitle)
                .setContentText(statusSubtitle)
                .setCustomContentView(collapsedView)
                .setCustomBigContentView(expandedView)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setOngoing(isRecordingActive)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(if (isRecordingActive) recordIntent else videosPendingIntent)

            if (isRecordingActive) {
                builder.addAction(
                    R.drawable.ic_notif_stop,
                    "Stop Recording",
                    recordIntent
                )
            }

            return builder.build()
        }
    }

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var mediaProjectionCallback: MediaProjection.Callback? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null

    private var currentOutputFile: File? = null
    private var currentWidth = 1920
    private var currentHeight = 1080
    private var currentFps = 60
    private var currentBitrate = 8_000_000

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                @Suppress("DEPRECATION")
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                val recordMic = intent.getBooleanExtra(EXTRA_RECORD_MIC, false)
                _isMicEnabled.value = recordMic

                val audioManager = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                audioManager?.isMicrophoneMute = !recordMic

                if (resultCode != 0 && resultData != null) {
                    startRecording(resultCode, resultData, recordMic)
                }
            }
            ACTION_CAPTURE_SCREENSHOT -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                @Suppress("DEPRECATION")
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (resultCode != 0 && resultData != null) {
                    performScreenshotCapture(resultCode, resultData)
                }
            }
            ACTION_STOP -> {
                stopRecording()
            }
            ACTION_EXIT_RECORDER -> {
                ScreenRecordingManager.setOverlayEnabled(this, false)
                try {
                    stopService(Intent(this, ScreenRecordOverlayService::class.java))
                } catch (_: Exception) {}
                val nm = getSystemService(NotificationManager::class.java)
                nm?.cancel(NOTIFICATION_ID)
                stopRecording()
            }
            ACTION_TOGGLE_MIC -> {
                val newMic = !_isMicEnabled.value
                _isMicEnabled.value = newMic
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                audioManager?.isMicrophoneMute = !newMic
                val current = ScreenRecordingManager.loadSettings(this)
                ScreenRecordingManager.saveSettings(this, current.copy(recordMic = newMic))
            }
        }

        return START_NOT_STICKY
    }

    private fun performScreenshotCapture(resultCode: Int, resultData: Intent) {
        try {
            startForegroundServiceNotification()
            val displayMetrics = resources.displayMetrics
            val width = displayMetrics.widthPixels
            val height = displayMetrics.heightPixels
            val density = displayMetrics.densityDpi

            val projection = mediaProjectionManager?.getMediaProjection(resultCode, resultData) ?: run {
                stopSelf()
                return
            }
            val callback = object : MediaProjection.Callback() {}
            projection.registerCallback(callback, Handler(Looper.getMainLooper()))

            val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            val vDisplay = projection.createVirtualDisplay(
                "GMaxScreenshotCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                null
            )

            serviceScope.launch {
                // Allow brief delay for surface buffer to populate
                delay(280)
                val image = imageReader.acquireLatestImage()
                if (image != null) {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width

                    val rawBitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    rawBitmap.copyPixelsFromBuffer(buffer)
                    image.close()

                    val cropped = Bitmap.createBitmap(rawBitmap, 0, 0, width, height)
                    val saved = ScreenRecordingManager.saveScreenshotBitmap(this@ScreenRecordingService, cropped)
                    if (saved != null) {
                        _lastSavedScreenshotUri.value = saved.uriString
                        Toast.makeText(
                            this@ScreenRecordingService,
                            "Screenshot saved to Screenshots tab",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                try { vDisplay?.release() } catch (_: Exception) {}
                try { imageReader.close() } catch (_: Exception) {}
                try { projection.unregisterCallback(callback) } catch (_: Exception) {}
                try { projection.stop() } catch (_: Exception) {}

                if (!_isRecording.value) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    // Restore ready notification if overlay is enabled
                    if (ScreenRecordingManager.loadSettings(this@ScreenRecordingService).isOverlayEnabled) {
                        val nm = getSystemService(NotificationManager::class.java)
                        nm?.notify(NOTIFICATION_ID, buildRecorderNotification(this@ScreenRecordingService, false, 0))
                    }
                    stopSelf()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (!_isRecording.value) {
                stopSelf()
            }
        }
    }

    private fun startRecording(resultCode: Int, resultData: Intent, recordMic: Boolean) {
        if (_isRecording.value) return

        try {
            val settings = ScreenRecordingManager.loadSettings(this)
            currentWidth = settings.width
            currentHeight = settings.height
            currentFps = settings.fps
            currentBitrate = settings.bitrateMbps * 1000 * 1000

            if (currentWidth % 2 != 0) currentWidth -= 1
            if (currentHeight % 2 != 0) currentHeight -= 1

            startForegroundServiceNotification()

            val fileName = ScreenRecordingManager.buildTaggedVideoFileName(this)

            val moviesDir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "GMaxRecordings")
            if (!moviesDir.exists()) moviesDir.mkdirs()
            currentOutputFile = File(moviesDir, fileName)

            val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val shouldRecordAudio = recordMic && hasMicPermission

            @Suppress("DEPRECATION")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                MediaRecorder()
            }

            mediaRecorder?.apply {
                if (shouldRecordAudio) {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                }
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(currentOutputFile?.absolutePath)
                setVideoSize(currentWidth, currentHeight)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                if (shouldRecordAudio) {
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(128_000)
                    setAudioSamplingRate(44100)
                }
                setVideoEncodingBitRate(currentBitrate)
                setVideoFrameRate(currentFps)
                prepare()
            }

            mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, resultData)

            val callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    stopRecording()
                }
            }
            mediaProjectionCallback = callback
            mediaProjection?.registerCallback(callback, Handler(Looper.getMainLooper()))

            val displayMetrics = resources.displayMetrics
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "GMaxScreenRecorder",
                currentWidth,
                currentHeight,
                displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder?.surface,
                null,
                null
            )

            mediaRecorder?.start()
            _isRecording.value = true
            _recordingSeconds.value = 0

            val nm = getSystemService(NotificationManager::class.java)
            timerJob?.cancel()
            timerJob = serviceScope.launch {
                while (_isRecording.value) {
                    delay(1000)
                    _recordingSeconds.value += 1
                    nm?.notify(
                        NOTIFICATION_ID,
                        buildRecorderNotification(this@ScreenRecordingService, true, _recordingSeconds.value)
                    )
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            stopRecording()
        }
    }

    private fun stopRecording() {
        val wasRecording = _isRecording.value
        if (!wasRecording && mediaRecorder == null && mediaProjection == null) {
            stopSelf()
            return
        }

        timerJob?.cancel()
        _isRecording.value = false

        if (wasRecording) {
            try {
                mediaRecorder?.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            mediaRecorder?.reset()
        } catch (_: Exception) {}

        try {
            mediaRecorder?.release()
            mediaRecorder = null
        } catch (_: Exception) {}

        try {
            virtualDisplay?.release()
            virtualDisplay = null
        } catch (_: Exception) {}

        try {
            mediaProjectionCallback?.let {
                mediaProjection?.unregisterCallback(it)
            }
        } catch (_: Exception) {}
        mediaProjectionCallback = null

        try {
            mediaProjection?.stop()
            mediaProjection = null
        } catch (_: Exception) {}

        if (wasRecording) {
            currentOutputFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    saveVideoToMediaStore(file)
                }
            }
        }

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        audioManager?.isMicrophoneMute = false

        stopForeground(STOP_FOREGROUND_REMOVE)
        if (ScreenRecordingManager.loadSettings(this).isOverlayEnabled) {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.notify(NOTIFICATION_ID, buildRecorderNotification(this, false, 0))
        }
        stopSelf()
    }

    private fun saveVideoToMediaStore(file: File) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.TITLE, file.nameWithoutExtension)
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
            put(MediaStore.Video.Media.SIZE, file.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/GMaxRecordings")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            } else {
                put(MediaStore.Video.Media.DATA, file.absolutePath)
            }
        }

        try {
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { input -> input.copyTo(out) }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                }
                _lastSavedVideoUri.value = uri.toString()
            } else {
                _lastSavedVideoUri.value = Uri.fromFile(file).toString()
            }
        } catch (_: Exception) {
            _lastSavedVideoUri.value = Uri.fromFile(file).toString()
        }

        // Also copy directly to public Movies/GMaxRecordings if accessible
        try {
            val publicMovies = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "GMaxRecordings")
            if (!publicMovies.exists()) publicMovies.mkdirs()
            val target = File(publicMovies, file.name)
            if (!target.exists() || target.length() != file.length()) {
                file.copyTo(target, overwrite = true)
            }
        } catch (_: Exception) {}
    }

    private fun startForegroundServiceNotification() {
        val notification = buildRecorderNotification(this, true, _recordingSeconds.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        serviceScope.cancel()
    }
}
