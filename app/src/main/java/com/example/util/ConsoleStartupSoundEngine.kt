package com.example.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Procedural console audio synthesizer that generates the PS4-inspired warm ambient
 * startup pad chord and the harmonic "Log Into Console" confirmation chime.
 */
object ConsoleStartupSoundEngine {

    private val audioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + AppCrashGuard.coroutineExceptionHandler)
    private const val SAMPLE_RATE = 44100

    /**
     * Plays the warm, atmospheric PS4-inspired startup synth pad chord when the wave backdrop reveals.
     */
    fun playStartupAmbientChord() {
        audioScope.launch {
            try {
                val durationSec = 2.8
                val numSamples = (SAMPLE_RATE * durationSec).toInt()
                val buffer = ShortArray(numSamples)

                // Ethereal suspended ninth chord (D3, A3, E4, F#4, A4) with gentle detune
                val freqs = doubleArrayOf(146.83, 220.00, 329.63, 369.99, 440.00, 587.33)
                val amps = doubleArrayOf(0.28, 0.24, 0.18, 0.14, 0.10, 0.06)

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / SAMPLE_RATE
                    // Smooth fade-in (0.65s) and long atmospheric decay
                    val attack = (t / 0.65).coerceIn(0.0, 1.0)
                    val release = exp(-t * 0.85)
                    val envelope = attack * release

                    var sample = 0.0
                    for (k in freqs.indices) {
                        val f = freqs[k]
                        val shimmer = 1.0 + 0.0018 * sin(2.0 * PI * 2.2 * t)
                        sample += amps[k] * sin(2.0 * PI * f * shimmer * t)
                    }

                    val normalized = (sample * envelope * 0.55).coerceIn(-1.0, 1.0)
                    buffer[i] = (normalized * Short.MAX_VALUE).toInt().toShort()
                }

                playPcmBuffer(buffer)
            } catch (_: Exception) {}
        }
    }

    /**
     * Plays the crisp PS4-style two-tone harmonic confirmation chime when the user presses
     * the Controller Home button (or taps Log Into Console) to enter the dashboard.
     */
    fun playLoginChime() {
        audioScope.launch {
            try {
                val durationSec = 0.85
                val numSamples = (SAMPLE_RATE * durationSec).toInt()
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / SAMPLE_RATE
                    // Note 1 (0.0s..0.85s): A4 (440 Hz) + E5 (659.25 Hz)
                    val env1 = (t / 0.015).coerceIn(0.0, 1.0) * exp(-t * 5.2)
                    val tone1 = (0.55 * sin(2.0 * PI * 440.0 * t) + 0.35 * sin(2.0 * PI * 659.25 * t)) * env1

                    // Note 2 (0.11s..0.85s): D5 (587.33 Hz) + A5 (880.0 Hz) crystal bell harmonic
                    val tone2 = if (t >= 0.11) {
                        val t2 = t - 0.11
                        val env2 = (t2 / 0.012).coerceIn(0.0, 1.0) * exp(-t2 * 4.0)
                        (0.60 * sin(2.0 * PI * 587.33 * t2) +
                            0.35 * sin(2.0 * PI * 880.0 * t2) +
                            0.20 * sin(2.0 * PI * 1174.66 * t2)) * env2
                    } else {
                        0.0
                    }

                    val combined = ((tone1 + tone2) * 0.52).coerceIn(-1.0, 1.0)
                    buffer[i] = (combined * Short.MAX_VALUE).toInt().toShort()
                }

                playPcmBuffer(buffer)
            } catch (_: Exception) {}
        }
    }

    private fun playPcmBuffer(buffer: ShortArray) {
        var track: AudioTrack? = null
        try {
            val byteSize = buffer.size * 2
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
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
                .setBufferSizeInBytes(byteSize.coerceAtLeast(4096))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(buffer, 0, buffer.size)
            track.play()

            val sleepMs = ((buffer.size.toDouble() / SAMPLE_RATE) * 1000).toLong() + 120L
            Thread.sleep(sleepMs)
        } catch (_: Exception) {
        } finally {
            try {
                track?.stop()
                track?.release()
            } catch (_: Exception) {}
        }
    }
}
