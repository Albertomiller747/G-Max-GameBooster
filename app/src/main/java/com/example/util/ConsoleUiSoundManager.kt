package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.view.InputDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 100% Original, Copyright-Free Procedural Console UI Sound Synthesizer.
 *
 * Generates tactile, low-latency PCM audio waveforms tailored to specific console menu actions:
 * - Focus / D-Pad Navigation Tick: Warm harmonic wood/marimba micro-pop
 * - Select / Confirm Action: Crisp ascending fifth interval chime
 * - Back / Cancel Action: Soft descending interval tone
 * - Bumper Tab Switch (L1/R1): Airy harmonic slide
 * - Game Launch / Boost Activation: Ascending 3-note console chord
 *
 * Strictly active ONLY when a controller is connected AND Console Controller UI Sounds are enabled in Settings.
 */
object ConsoleUiSoundManager {

    private const val PREFS_NAME = "gmax_hub_settings_prefs"
    private const val KEY_CONSOLE_SOUNDS_ENABLED = "console_ui_sounds_enabled"
    private const val SAMPLE_RATE = 44100

    private val soundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + AppCrashGuard.coroutineExceptionHandler)
    private var lastTickTimeMs = 0L

    fun isConsoleUiSoundsEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CONSOLE_SOUNDS_ENABLED, true)
    }

    fun setConsoleUiSoundsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CONSOLE_SOUNDS_ENABLED, enabled)
            .apply()
    }

    /**
     * Checks if a physical/Bluetooth/USB gamepad or joystick is currently connected.
     */
    fun isControllerConnected(): Boolean {
        return try {
            val deviceIds = InputDevice.getDeviceIds()
            for (id in deviceIds) {
                val dev = InputDevice.getDevice(id) ?: continue
                val sources = dev.sources
                val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                if (isGamepad) {
                    return true
                }
            }
            ControllerHealthManager.liveInputState.value.controllerId >= 0
        } catch (_: Exception) {
            false
        }
    }

    private fun shouldPlaySound(context: Context): Boolean {
        return isConsoleUiSoundsEnabled(context)
    }

    /**
     * Tactile D-Pad / Analog Stick focus movement sound (warm harmonic marimba tick).
     */
    fun playFocusTick(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastTickTimeMs < 35L) return
        lastTickTimeMs = now
        if (!shouldPlaySound(context)) return

        val appCtx = context.applicationContext
        soundScope.launch {
            val pcm = synthesizeSweepTone(
                startFreqHz = 540.0,
                endFreqHz = 810.0,
                durationMs = 38,
                volume = 0.52f,
                decayRate = 10.5
            )
            playPcmBuffer(appCtx, pcm)
        }
    }

    /**
     * Primary button (A / Cross) selection confirmation pop-chime.
     */
    fun playSelectClick(context: Context) {
        if (!shouldPlaySound(context)) return

        val appCtx = context.applicationContext
        soundScope.launch {
            val pcm = synthesizeTwoNoteChime(
                freq1Hz = 659.25, // E5
                freq2Hz = 987.77, // B5
                note1Ms = 30,
                note2Ms = 56,
                volume = 0.56f
            )
            playPcmBuffer(appCtx, pcm)
        }
    }

    /**
     * Back / Cancel (B / Circle) descending two-note tone.
     */
    fun playBackTone(context: Context) {
        if (!shouldPlaySound(context)) return

        val appCtx = context.applicationContext
        soundScope.launch {
            val pcm = synthesizeTwoNoteChime(
                freq1Hz = 740.0, // F#5
                freq2Hz = 493.88, // B4
                note1Ms = 32,
                note2Ms = 52,
                volume = 0.48f
            )
            playPcmBuffer(appCtx, pcm)
        }
    }

    /**
     * Shoulder Bumper (L1 / R1) tab switch harmonic glide.
     */
    fun playTabSwitch(context: Context) {
        if (!shouldPlaySound(context)) return

        val appCtx = context.applicationContext
        soundScope.launch {
            val pcm = synthesizeSweepTone(
                startFreqHz = 587.33,
                endFreqHz = 880.0,
                durationMs = 58,
                volume = 0.52f,
                decayRate = 7.5
            )
            playPcmBuffer(appCtx, pcm)
        }
    }

    /**
     * Game Launch / Boost Activation ascending console chord.
     */
    fun playLaunchChord(context: Context) {
        if (!shouldPlaySound(context)) return

        val appCtx = context.applicationContext
        soundScope.launch {
            val pcm = synthesizeThreeNoteArpeggio(
                freq1Hz = 523.25, // C5
                freq2Hz = 659.25, // E5
                freq3Hz = 1046.50, // C6
                totalMs = 175,
                volume = 0.60f
            )
            playPcmBuffer(appCtx, pcm)
        }
    }

    private fun synthesizeSweepTone(
        startFreqHz: Double,
        endFreqHz: Double,
        durationMs: Int,
        volume: Float,
        decayRate: Double
    ): ShortArray {
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ShortArray(numSamples)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples.coerceAtLeast(1)
            val freq = startFreqHz + (endFreqHz - startFreqHz) * progress
            phase += 2.0 * PI * freq / SAMPLE_RATE

            // Attack + exponential decay envelope to prevent clicks
            val attack = (i / (SAMPLE_RATE * 0.003)).coerceAtMost(1.0)
            val envelope = attack * exp(-decayRate * progress)

            // Fundamental + soft 2nd harmonic for warm console wooden/bell timbre
            val wave = 0.82 * sin(phase) + 0.18 * sin(phase * 2.0)
            val sample = (wave * envelope * volume * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[i] = sample.toShort()
        }
        return buffer
    }

    private fun synthesizeTwoNoteChime(
        freq1Hz: Double,
        freq2Hz: Double,
        note1Ms: Int,
        note2Ms: Int,
        volume: Float
    ): ShortArray {
        val samples1 = (SAMPLE_RATE * note1Ms) / 1000
        val samples2 = (SAMPLE_RATE * note2Ms) / 1000
        val totalSamples = samples1 + samples2
        val buffer = ShortArray(totalSamples)
        var phase = 0.0

        for (i in 0 until totalSamples) {
            val isSecondNote = i >= samples1
            val localIdx = if (isSecondNote) i - samples1 else i
            val localLen = if (isSecondNote) samples2 else samples1
            val progress = localIdx.toDouble() / localLen.coerceAtLeast(1)
            val freq = if (isSecondNote) freq2Hz else freq1Hz

            phase += 2.0 * PI * freq / SAMPLE_RATE
            val attack = (localIdx / (SAMPLE_RATE * 0.0025)).coerceAtMost(1.0)
            val envelope = attack * exp(-7.5 * progress)
            val wave = 0.85 * sin(phase) + 0.15 * sin(phase * 2.0)
            val sample = (wave * envelope * volume * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[i] = sample.toShort()
        }
        return buffer
    }

    private fun synthesizeThreeNoteArpeggio(
        freq1Hz: Double,
        freq2Hz: Double,
        freq3Hz: Double,
        totalMs: Int,
        volume: Float
    ): ShortArray {
        val totalSamples = (SAMPLE_RATE * totalMs) / 1000
        val seg1 = totalSamples / 4
        val seg2 = (totalSamples * 2) / 4
        val buffer = ShortArray(totalSamples)
        var phase = 0.0

        for (i in 0 until totalSamples) {
            val (freq, localIdx, localLen) = when {
                i < seg1 -> Triple(freq1Hz, i, seg1)
                i < seg2 -> Triple(freq2Hz, i - seg1, seg2 - seg1)
                else -> Triple(freq3Hz, i - seg2, totalSamples - seg2)
            }
            val progress = localIdx.toDouble() / localLen.coerceAtLeast(1)
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val attack = (localIdx / (SAMPLE_RATE * 0.003)).coerceAtMost(1.0)
            val envelope = attack * exp(-5.5 * progress)
            val wave = 0.80 * sin(phase) + 0.20 * sin(phase * 2.0)
            val sample = (wave * envelope * volume * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[i] = sample.toShort()
        }
        return buffer
    }

    private fun playPcmBuffer(context: Context, pcm: ShortArray) {
        if (pcm.isEmpty()) return
        var track: AudioTrack? = null
        try {
            val minBufBytes = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)
            val minSamples = (minBufBytes / 2) + 256
            val paddedPcm = if (pcm.size < minSamples) {
                ShortArray(minSamples).also { pcm.copyInto(it, 0, 0, pcm.size) }
            } else {
                pcm
            }
            val byteSize = (paddedPcm.size * 2).coerceAtLeast(minBufBytes)

            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(byteSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                try {
                    val audioMgr = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                    val outputs = audioMgr?.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
                    val preferredOut = outputs.firstOrNull {
                        it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                            it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET
                    } ?: outputs.firstOrNull {
                        it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (preferredOut != null) {
                        track.setPreferredDevice(preferredOut)
                    }
                } catch (_: Exception) {}
            }

            track.write(paddedPcm, 0, paddedPcm.size)
            track.play()
            val durationMs = (pcm.size * 1000L) / SAMPLE_RATE + 55L
            Thread.sleep(durationMs)
        } catch (_: Exception) {
            try {
                val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 60)
                tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 35)
                Thread.sleep(45L)
                tg.release()
            } catch (_: Exception) {}
        } finally {
            try {
                track?.stop()
            } catch (_: Exception) {}
            try {
                track?.release()
            } catch (_: Exception) {}
        }
    }
}
