package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
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
import androidx.core.app.NotificationCompat
import com.example.MainActivity
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

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_RECORD_MIC = "extra_record_mic"

        private const val NOTIFICATION_ID = 4001
        private const val CHANNEL_ID = "gmax_recording_channel"

        // Observable State for UI and Overlay
        private val _isRecording = MutableStateFlow(false)
        val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

        private val _recordingSeconds = MutableStateFlow(0)
        val recordingSeconds: StateFlow<Int> = _recordingSeconds.asStateFlow()

        private val _isMicEnabled = MutableStateFlow(true)
        val isMicEnabled: StateFlow<Boolean> = _isMicEnabled.asStateFlow()

        private val _lastSavedVideoUri = MutableStateFlow<String?>(null)
        val lastSavedVideoUri: StateFlow<String?> = _lastSavedVideoUri.asStateFlow()

        fun startRecording(context: Context, resultCode: Int, resultData: Intent, recordMic: Boolean = true) {
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

        fun stopRecording(context: Context) {
            val intent = Intent(context, ScreenRecordingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
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
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                val recordMic = intent.getBooleanExtra(EXTRA_RECORD_MIC, true)
                _isMicEnabled.value = recordMic

                if (resultCode != 0 && resultData != null) {
                    startRecording(resultCode, resultData, recordMic)
                }
            }
            ACTION_STOP -> {
                stopRecording()
            }
            ACTION_TOGGLE_MIC -> {
                _isMicEnabled.value = !_isMicEnabled.value
            }
        }

        return START_NOT_STICKY
    }

    private fun startRecording(resultCode: Int, resultData: Intent, recordMic: Boolean) {
        if (_isRecording.value) return

        try {
            // Load user's customized settings
            val settings = ScreenRecordingManager.loadSettings(this)
            currentWidth = settings.width
            currentHeight = settings.height
            currentFps = settings.fps
            currentBitrate = settings.bitrateMbps * 1000 * 1000

            // Ensure dimensions are even (required by H264 hardware encoders)
            if (currentWidth % 2 != 0) currentWidth -= 1
            if (currentHeight % 2 != 0) currentHeight -= 1

            // Promote to Foreground Service immediately
            startForegroundServiceNotification()

            // Setup Output File
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "GMax_${timeStamp}.mp4"

            val moviesDir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "GMaxRecordings")
            if (!moviesDir.exists()) moviesDir.mkdirs()
            currentOutputFile = File(moviesDir, fileName)

            // Check microphone permission before setting audio source
            val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val shouldRecordAudio = recordMic && hasMicPermission

            // Setup MediaRecorder
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

            // Acquire MediaProjection
            mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, resultData)

            // CRITICAL (Android 14+ / API 34+): Must register MediaProjection callback BEFORE createVirtualDisplay
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

            // Start 1-second ticker for elapsed recording time
            timerJob?.cancel()
            timerJob = serviceScope.launch {
                while (_isRecording.value) {
                    delay(1000)
                    _recordingSeconds.value += 1
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

        // Index video into device MediaStore Video table so it immediately appears in Photos/Gallery
        if (wasRecording) {
            currentOutputFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    saveVideoToMediaStore(file)
                }
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
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
            put(MediaStore.Video.Media.DATA, file.absolutePath)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/GMaxRecordings")
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
        }

        try {
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            _lastSavedVideoUri.value = uri?.toString() ?: Uri.fromFile(file).toString()
        } catch (_: Exception) {
            _lastSavedVideoUri.value = Uri.fromFile(file).toString()
        }
    }

    private fun startForegroundServiceNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ScreenRecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("G Max Screen Recorder")
            .setContentText("Screen recording is actively running...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop Recording", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "G Max Screen Recorder",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when screen recording is active"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        serviceScope.cancel()
    }
}
