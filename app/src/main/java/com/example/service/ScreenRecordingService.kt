package com.example.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
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
import android.view.Surface
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.model.AudioSourceMode
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

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
        const val EXTRA_AUDIO_SOURCE_MODE = "extra_audio_source_mode"

        const val NOTIFICATION_ID = 4001
        const val CHANNEL_ID = "gmax_recording_channel"

        private const val AUDIO_SAMPLE_RATE = 44100
        private const val AUDIO_CHANNEL_COUNT = 2
        private const val AUDIO_BITRATE = 128_000

        // Observable State for UI and Overlay
        private val _isRecording = MutableStateFlow(false)
        val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

        private val _recordingSeconds = MutableStateFlow(0)
        val recordingSeconds: StateFlow<Int> = _recordingSeconds.asStateFlow()

        private val _isMicEnabled = MutableStateFlow(true)
        val isMicEnabled: StateFlow<Boolean> = _isMicEnabled.asStateFlow()

        private val _activeAudioMode = MutableStateFlow(AudioSourceMode.INTERNAL_ONLY)
        val activeAudioMode: StateFlow<AudioSourceMode> = _activeAudioMode.asStateFlow()

        private val _lastSavedVideoUri = MutableStateFlow<String?>(null)
        val lastSavedVideoUri: StateFlow<String?> = _lastSavedVideoUri.asStateFlow()

        private val _lastSavedScreenshotUri = MutableStateFlow<String?>(null)
        val lastSavedScreenshotUri: StateFlow<String?> = _lastSavedScreenshotUri.asStateFlow()

        fun startRecording(
            context: Context,
            resultCode: Int,
            resultData: Intent,
            recordMic: Boolean? = null,
            audioSourceMode: AudioSourceMode? = null
        ) {
            val savedSettings = ScreenRecordingManager.loadSettings(context)
            val resolvedMode = audioSourceMode ?: savedSettings.audioSourceMode
            val resolvedMic = recordMic
                ?: (resolvedMode == AudioSourceMode.INTERNAL_AND_MIC || resolvedMode == AudioSourceMode.MIC_ONLY)

            val intent = Intent(context, ScreenRecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
                putExtra(EXTRA_RECORD_MIC, resolvedMic)
                putExtra(EXTRA_AUDIO_SOURCE_MODE, resolvedMode.name)
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
            val audioBadge = _activeAudioMode.value.shortLabel
            val statusTitle = if (isRecordingActive) {
                String.format(Locale.US, "Recording (%02d:%02d) • %s", mins, secs, audioBadge)
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

    // Internal Audio + Mic MediaCodec / MediaMuxer pipeline fields
    private var videoEncoder: MediaCodec? = null
    private var videoInputSurface: Surface? = null
    private var audioEncoder: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null
    private var internalAudioRecord: AudioRecord? = null
    private var micAudioRecord: AudioRecord? = null
    private var muxerEngineJob: Job? = null
    private val isUsingMuxerPipeline = AtomicBoolean(false)
    private val isStoppingMuxer = AtomicBoolean(false)

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
        // Ensure device microphone is never left muted by any prior session
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            if (audioManager?.isMicrophoneMute == true) {
                audioManager.isMicrophoneMute = false
            }
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                @Suppress("DEPRECATION")
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                val settings = ScreenRecordingManager.loadSettings(this)
                val modeName = intent.getStringExtra(EXTRA_AUDIO_SOURCE_MODE)
                val resolvedMode = if (!modeName.isNullOrBlank()) {
                    try {
                        AudioSourceMode.valueOf(modeName)
                    } catch (_: Exception) {
                        settings.audioSourceMode
                    }
                } else {
                    settings.audioSourceMode
                }

                _activeAudioMode.value = resolvedMode
                _isMicEnabled.value = resolvedMode != AudioSourceMode.MUTE

                // Never mute the system microphone! Ensure it stays unmuted so gameplay/voice works
                try {
                    val audioManager = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                    audioManager?.isMicrophoneMute = false
                } catch (_: Exception) {}

                if (resultCode != 0 && resultData != null) {
                    startRecording(resultCode, resultData, resolvedMode)
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
                val current = ScreenRecordingManager.loadSettings(this)
                val nextMode = when (current.audioSourceMode) {
                    AudioSourceMode.MUTE -> AudioSourceMode.INTERNAL_ONLY
                    AudioSourceMode.INTERNAL_ONLY -> AudioSourceMode.INTERNAL_AND_MIC
                    AudioSourceMode.INTERNAL_AND_MIC -> AudioSourceMode.MIC_ONLY
                    AudioSourceMode.MIC_ONLY -> AudioSourceMode.MUTE
                }
                _activeAudioMode.value = nextMode
                _isMicEnabled.value = nextMode != AudioSourceMode.MUTE
                ScreenRecordingManager.saveSettings(
                    this,
                    current.copy(
                        audioSourceMode = nextMode,
                        recordMic = nextMode == AudioSourceMode.INTERNAL_AND_MIC || nextMode == AudioSourceMode.MIC_ONLY
                    )
                )
            }
        }

        return START_NOT_STICKY
    }

    private fun performScreenshotCapture(resultCode: Int, resultData: Intent) {
        try {
            startForegroundServiceNotification(includeMicrophoneType = false)
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

    private fun startRecording(resultCode: Int, resultData: Intent, audioMode: AudioSourceMode) {
        if (_isRecording.value) return

        try {
            val settings = ScreenRecordingManager.loadSettings(this)
            currentWidth = settings.width
            currentHeight = settings.height
            currentFps = settings.fps
            currentBitrate = settings.bitrateMbps * 1000 * 1000

            if (currentWidth % 2 != 0) currentWidth -= 1
            if (currentHeight % 2 != 0) currentHeight -= 1

            val hasAudioPerm = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            val wantAudio = audioMode != AudioSourceMode.MUTE && hasAudioPerm
            startForegroundServiceNotification(includeMicrophoneType = wantAudio)

            val fileName = ScreenRecordingManager.buildTaggedVideoFileName(this)
            val moviesDir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "GMaxRecordings")
            if (!moviesDir.exists()) moviesDir.mkdirs()
            val outFile = File(moviesDir, fileName)
            currentOutputFile = outFile

            mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, resultData)
            val projection = mediaProjection ?: run {
                stopRecording()
                return
            }

            val callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    stopRecording()
                }
            }
            mediaProjectionCallback = callback
            projection.registerCallback(callback, Handler(Looper.getMainLooper()))

            val startedInternalMuxer = if (
                wantAudio &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                (audioMode == AudioSourceMode.INTERNAL_ONLY || audioMode == AudioSourceMode.INTERNAL_AND_MIC)
            ) {
                startInternalAudioMuxerRecording(
                    projection = projection,
                    outputFile = outFile,
                    includeMic = (audioMode == AudioSourceMode.INTERNAL_AND_MIC)
                )
            } else {
                false
            }

            if (!startedInternalMuxer) {
                // Fallback to MediaRecorder (used for MIC_ONLY, MUTE, or if internal playback capture is unsupported)
                startStandardMediaRecorder(
                    projection = projection,
                    outputFile = outFile,
                    recordAudio = wantAudio
                )
            }

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

    @SuppressLint("MissingPermission")
    private fun startInternalAudioMuxerRecording(
        projection: MediaProjection,
        outputFile: File,
        includeMic: Boolean
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false

        return try {
            val channelMask = AudioFormat.CHANNEL_IN_STEREO
            val encoding = AudioFormat.ENCODING_PCM_16BIT
            val minBufSize = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE, channelMask, encoding)
                .coerceAtLeast(4096)
            val bufferSizeInBytes = minBufSize * 2

            // 1. Configure Internal Device / Gameplay Audio Capture via AudioPlaybackCaptureConfiguration
            val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val internalFormat = AudioFormat.Builder()
                .setEncoding(encoding)
                .setSampleRate(AUDIO_SAMPLE_RATE)
                .setChannelMask(channelMask)
                .build()

            val internalRecord = AudioRecord.Builder()
                .setAudioFormat(internalFormat)
                .setBufferSizeInBytes(bufferSizeInBytes)
                .setAudioPlaybackCaptureConfig(playbackConfig)
                .build()

            if (internalRecord.state != AudioRecord.STATE_INITIALIZED) {
                try { internalRecord.release() } catch (_: Exception) {}
                return false
            }

            // 2. Optional Microphone AudioRecord for simultaneous voice + game audio mixing
            var micRecord: AudioRecord? = null
            if (includeMic) {
                try {
                    val candidateMic = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        AUDIO_SAMPLE_RATE,
                        channelMask,
                        encoding,
                        bufferSizeInBytes
                    )
                    if (candidateMic.state == AudioRecord.STATE_INITIALIZED) {
                        micRecord = candidateMic
                    } else {
                        candidateMic.release()
                        val fallbackMic = AudioRecord(
                            MediaRecorder.AudioSource.MIC,
                            AUDIO_SAMPLE_RATE,
                            channelMask,
                            encoding,
                            bufferSizeInBytes
                        )
                        if (fallbackMic.state == AudioRecord.STATE_INITIALIZED) {
                            micRecord = fallbackMic
                        } else {
                            fallbackMic.release()
                        }
                    }
                } catch (_: Exception) {
                    micRecord = null
                }
            }

            // 3. Configure Video Encoder (H.264 / AVC)
            val vFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, currentWidth, currentHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, currentBitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, currentFps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            val vEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            vEncoder.configure(vFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = vEncoder.createInputSurface()
            vEncoder.start()

            // 4. Configure Audio Encoder (AAC LC)
            val aFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                AUDIO_SAMPLE_RATE,
                AUDIO_CHANNEL_COUNT
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            val aEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            aEncoder.configure(aFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            aEncoder.start()

            // 5. Create MediaMuxer & VirtualDisplay
            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val displayMetrics = resources.displayMetrics
            val vDisplay = projection.createVirtualDisplay(
                "GMaxScreenRecorder",
                currentWidth,
                currentHeight,
                displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                null
            )

            internalRecord.startRecording()
            try { micRecord?.startRecording() } catch (_: Exception) {}

            videoEncoder = vEncoder
            videoInputSurface = surface
            audioEncoder = aEncoder
            mediaMuxer = muxer
            internalAudioRecord = internalRecord
            micAudioRecord = micRecord
            virtualDisplay = vDisplay
            isUsingMuxerPipeline.set(true)
            isStoppingMuxer.set(false)

            // 6. Start real-time Audio/Video encoding + muxing loop
            muxerEngineJob = serviceScope.launch(Dispatchers.Default) {
                runMuxerLoop(
                    vEncoder = vEncoder,
                    aEncoder = aEncoder,
                    muxer = muxer,
                    internalRec = internalRecord,
                    micRec = micRecord
                )
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            cleanupMuxerResources()
            false
        }
    }

    private fun runMuxerLoop(
        vEncoder: MediaCodec,
        aEncoder: MediaCodec,
        muxer: MediaMuxer,
        internalRec: AudioRecord,
        micRec: AudioRecord?
    ) {
        val vBufferInfo = MediaCodec.BufferInfo()
        val aBufferInfo = MediaCodec.BufferInfo()

        var videoTrackIndex = -1
        var audioTrackIndex = -1
        var muxerStarted = false

        val pcmReadBytes = 4096
        val internalBytes = ByteArray(pcmReadBytes)
        val micBytes = ByteArray(pcmReadBytes)
        val mixedBytes = ByteArray(pcmReadBytes)

        val startNs = System.nanoTime()
        var totalAudioFramesWritten = 0L

        val pendingVideoSamples = mutableListOf<Pair<ByteBuffer, MediaCodec.BufferInfo>>()
        val pendingAudioSamples = mutableListOf<Pair<ByteBuffer, MediaCodec.BufferInfo>>()

        fun tryStartMuxerIfReady() {
            if (!muxerStarted && videoTrackIndex >= 0 && audioTrackIndex >= 0) {
                muxer.start()
                muxerStarted = true
                for ((buf, info) in pendingVideoSamples) {
                    try { muxer.writeSampleData(videoTrackIndex, buf, info) } catch (_: Exception) {}
                }
                pendingVideoSamples.clear()
                for ((buf, info) in pendingAudioSamples) {
                    try { muxer.writeSampleData(audioTrackIndex, buf, info) } catch (_: Exception) {}
                }
                pendingAudioSamples.clear()
            }
        }

        try {
            while (!isStoppingMuxer.get()) {
                // A. Read PCM audio from Internal AudioPlaybackCapture (+ optional Mic) and feed to AAC encoder
                val inIndex = try { aEncoder.dequeueInputBuffer(2_000) } catch (_: Exception) { -1 }
                if (inIndex >= 0) {
                    val inBuf = aEncoder.getInputBuffer(inIndex)
                    if (inBuf != null) {
                        inBuf.clear()
                        val maxToRead = minOf(inBuf.remaining(), pcmReadBytes)
                        val readInternal = try {
                            internalRec.read(internalBytes, 0, maxToRead)
                        } catch (_: Exception) { 0 }

                        val bytesToEncode = if (readInternal > 0) readInternal else maxToRead
                        if (readInternal <= 0) {
                            java.util.Arrays.fill(internalBytes, 0, bytesToEncode, 0.toByte())
                        }

                        val readMic = if (micRec != null) {
                            try { micRec.read(micBytes, 0, bytesToEncode) } catch (_: Exception) { 0 }
                        } else {
                            0
                        }

                        val sourceArray = if (readMic > 0) {
                            // Mix 16-bit little-endian stereo PCM samples (Internal Gameplay + Voice Mic)
                            val sampleCount = bytesToEncode / 2
                            val intShortBuf = ByteBuffer.wrap(internalBytes, 0, bytesToEncode)
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .asShortBuffer()
                            val micShortBuf = ByteBuffer.wrap(micBytes, 0, bytesToEncode)
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .asShortBuffer()
                            val outShortBuf = ByteBuffer.wrap(mixedBytes, 0, bytesToEncode)
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .asShortBuffer()

                            for (i in 0 until sampleCount) {
                                val sInt = intShortBuf.get(i).toInt()
                                val sMic = if (i * 2 < readMic) micShortBuf.get(i).toInt() else 0
                                val mixed = (sInt + sMic).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                outShortBuf.put(i, mixed.toShort())
                            }
                            mixedBytes
                        } else {
                            internalBytes
                        }

                        inBuf.put(sourceArray, 0, bytesToEncode)
                        val ptsUs = (totalAudioFramesWritten * 1_000_000L) / AUDIO_SAMPLE_RATE
                        totalAudioFramesWritten += (bytesToEncode / (2 * AUDIO_CHANNEL_COUNT))
                        try {
                            aEncoder.queueInputBuffer(inIndex, 0, bytesToEncode, ptsUs, 0)
                        } catch (_: Exception) {}
                    }
                }

                // B. Drain Audio Encoder output
                while (true) {
                    val outIndex = try { aEncoder.dequeueOutputBuffer(aBufferInfo, 0) } catch (_: Exception) { break }
                    if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break
                    } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (!muxerStarted && audioTrackIndex < 0) {
                            audioTrackIndex = muxer.addTrack(aEncoder.outputFormat)
                            tryStartMuxerIfReady()
                        }
                    } else if (outIndex >= 0) {
                        val encodedData = aEncoder.getOutputBuffer(outIndex)
                        if (encodedData != null && (aBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && aBufferInfo.size > 0) {
                            encodedData.position(aBufferInfo.offset)
                            encodedData.limit(aBufferInfo.offset + aBufferInfo.size)
                            if (muxerStarted) {
                                try { muxer.writeSampleData(audioTrackIndex, encodedData, aBufferInfo) } catch (_: Exception) {}
                            } else if (pendingAudioSamples.size < 120) {
                                val copyBuf = ByteBuffer.allocateDirect(aBufferInfo.size)
                                copyBuf.put(encodedData)
                                copyBuf.flip()
                                val copyInfo = MediaCodec.BufferInfo().apply {
                                    set(0, aBufferInfo.size, aBufferInfo.presentationTimeUs, aBufferInfo.flags)
                                }
                                pendingAudioSamples.add(copyBuf to copyInfo)
                            }
                        }
                        try { aEncoder.releaseOutputBuffer(outIndex, false) } catch (_: Exception) {}
                    } else {
                        break
                    }
                }

                // C. Drain Video Encoder output
                while (true) {
                    val outIndex = try { vEncoder.dequeueOutputBuffer(vBufferInfo, 2_000) } catch (_: Exception) { break }
                    if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break
                    } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (!muxerStarted && videoTrackIndex < 0) {
                            videoTrackIndex = muxer.addTrack(vEncoder.outputFormat)
                            tryStartMuxerIfReady()
                        }
                    } else if (outIndex >= 0) {
                        val encodedData = vEncoder.getOutputBuffer(outIndex)
                        if (encodedData != null && (vBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && vBufferInfo.size > 0) {
                            encodedData.position(vBufferInfo.offset)
                            encodedData.limit(vBufferInfo.offset + vBufferInfo.size)
                            // Normalize video presentationTimeUs relative to startNs so audio & video stay in sync
                            val normalizedPtsUs = ((System.nanoTime() - startNs) / 1000L).coerceAtLeast(0L)
                            vBufferInfo.presentationTimeUs = normalizedPtsUs
                            if (muxerStarted) {
                                try { muxer.writeSampleData(videoTrackIndex, encodedData, vBufferInfo) } catch (_: Exception) {}
                            } else if (pendingVideoSamples.size < 90) {
                                val copyBuf = ByteBuffer.allocateDirect(vBufferInfo.size)
                                copyBuf.put(encodedData)
                                copyBuf.flip()
                                val copyInfo = MediaCodec.BufferInfo().apply {
                                    set(0, vBufferInfo.size, normalizedPtsUs, vBufferInfo.flags)
                                }
                                pendingVideoSamples.add(copyBuf to copyInfo)
                            }
                        }
                        try { vEncoder.releaseOutputBuffer(outIndex, false) } catch (_: Exception) {}
                    } else {
                        break
                    }
                }
            }

            // Signal End-Of-Stream on Video Encoder so final frames flush cleanly
            try { vEncoder.signalEndOfInputStream() } catch (_: Exception) {}
            val drainDeadline = System.currentTimeMillis() + 350L
            while (System.currentTimeMillis() < drainDeadline) {
                val outIndex = try { vEncoder.dequeueOutputBuffer(vBufferInfo, 5_000) } catch (_: Exception) { break }
                if (outIndex >= 0) {
                    val encodedData = vEncoder.getOutputBuffer(outIndex)
                    if (muxerStarted && encodedData != null &&
                        (vBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 &&
                        vBufferInfo.size > 0
                    ) {
                        encodedData.position(vBufferInfo.offset)
                        encodedData.limit(vBufferInfo.offset + vBufferInfo.size)
                        vBufferInfo.presentationTimeUs = ((System.nanoTime() - startNs) / 1000L).coerceAtLeast(0L)
                        try { muxer.writeSampleData(videoTrackIndex, encodedData, vBufferInfo) } catch (_: Exception) {}
                    }
                    try { vEncoder.releaseOutputBuffer(outIndex, false) } catch (_: Exception) {}
                    if ((vBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                } else {
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (muxerStarted) {
                try { muxer.stop() } catch (_: Exception) {}
            }
        }
    }

    private fun startStandardMediaRecorder(
        projection: MediaProjection,
        outputFile: File,
        recordAudio: Boolean
    ) {
        isUsingMuxerPipeline.set(false)
        @Suppress("DEPRECATION")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            MediaRecorder()
        }

        var audioConfigured = false
        if (recordAudio) {
            try {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                audioConfigured = true
            } catch (_: Exception) {
                audioConfigured = false
            }
        }

        recorder.apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(outputFile.absolutePath)
            setVideoSize(currentWidth, currentHeight)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (audioConfigured) {
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(2)
                setAudioEncodingBitRate(AUDIO_BITRATE)
                setAudioSamplingRate(AUDIO_SAMPLE_RATE)
            }
            setVideoEncodingBitRate(currentBitrate)
            setVideoFrameRate(currentFps)
            prepare()
        }

        mediaRecorder = recorder

        val displayMetrics = resources.displayMetrics
        virtualDisplay = projection.createVirtualDisplay(
            "GMaxScreenRecorder",
            currentWidth,
            currentHeight,
            displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            recorder.surface,
            null,
            null
        )

        recorder.start()
    }

    private fun cleanupMuxerResources() {
        isStoppingMuxer.set(true)
        try {
            kotlinx.coroutines.runBlocking {
                muxerEngineJob?.join()
            }
        } catch (_: Exception) {}
        muxerEngineJob = null

        try {
            internalAudioRecord?.stop()
        } catch (_: Exception) {}
        try {
            internalAudioRecord?.release()
        } catch (_: Exception) {}
        internalAudioRecord = null

        try {
            micAudioRecord?.stop()
        } catch (_: Exception) {}
        try {
            micAudioRecord?.release()
        } catch (_: Exception) {}
        micAudioRecord = null

        try {
            videoEncoder?.stop()
        } catch (_: Exception) {}
        try {
            videoEncoder?.release()
        } catch (_: Exception) {}
        videoEncoder = null

        try {
            videoInputSurface?.release()
        } catch (_: Exception) {}
        videoInputSurface = null

        try {
            audioEncoder?.stop()
        } catch (_: Exception) {}
        try {
            audioEncoder?.release()
        } catch (_: Exception) {}
        audioEncoder = null

        try {
            mediaMuxer?.release()
        } catch (_: Exception) {}
        mediaMuxer = null

        isUsingMuxerPipeline.set(false)
    }

    private fun stopRecording() {
        val wasRecording = _isRecording.value
        if (!wasRecording && mediaRecorder == null && mediaProjection == null && !isUsingMuxerPipeline.get()) {
            stopSelf()
            return
        }

        timerJob?.cancel()
        _isRecording.value = false

        if (isUsingMuxerPipeline.get()) {
            cleanupMuxerResources()
        } else {
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
        }

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

        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            audioManager?.isMicrophoneMute = false
        } catch (_: Exception) {}

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

    private fun startForegroundServiceNotification(includeMicrophoneType: Boolean) {
        val notification = buildRecorderNotification(this, true, _recordingSeconds.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val hasAudioPerm = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            val fgType = if (includeMicrophoneType && hasAudioPerm) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            try {
                startForeground(NOTIFICATION_ID, notification, fgType)
            } catch (_: Exception) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
