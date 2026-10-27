package com.example.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.InputDevice

/**
 * Provides console-grade tactile haptic feedback across both the device motor
 * and any connected gamepad/controller with rumble support.
 */
object ConsoleHapticManager {

    private var lastTickTimeMs = 0L

    private fun getDeviceVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun vibrateConnectedGamepads(durationMs: Long, amplitude: Int) {
        try {
            val deviceIds = InputDevice.getDeviceIds()
            for (id in deviceIds) {
                val dev = InputDevice.getDevice(id) ?: continue
                val sources = dev.sources
                val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                        (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                if (!isGamepad) continue

                @Suppress("DEPRECATION")
                val padVibrator = dev.vibrator
                if (padVibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        padVibrator.vibrate(
                            VibrationEffect.createOneShot(
                                durationMs.coerceAtLeast(10L),
                                amplitude.coerceIn(1, 255)
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        padVibrator.vibrate(durationMs)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Subtle console D-Pad / tile focus tick.
     * Debounced to avoid motor saturation during fast scrolling.
     */
    fun playFocusTick(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastTickTimeMs < 35L) return
        lastTickTimeMs = now

        try {
            val vibrator = getDeviceVibrator(context)
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(12L, 75))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(10L)
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Crisp confirmation click for tile selection, L1/R1 bumper tab switch, or modal toggle.
     */
    fun playSelectClick(context: Context) {
        try {
            val vibrator = getDeviceVibrator(context)
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(20L, 140))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(18L)
                }
            }
            vibrateConnectedGamepads(22L, 120)
        } catch (_: Exception) {}
    }

    /**
     * Energetic double-pulse when launching a game or activating Boost.
     */
    fun playLaunchPulse(context: Context) {
        try {
            val vibrator = getDeviceVibrator(context)
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0L, 28L, 45L, 55L)
                    val amplitudes = intArrayOf(0, 160, 0, 255)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(65L)
                }
            }
            vibrateConnectedGamepads(70L, 220)
        } catch (_: Exception) {}
    }

    /**
     * Synchronized 2-stage console boot haptic pulse during the 2-second startup reveal.
     */
    fun playBootPulse(context: Context, stage: Int) {
        try {
            val vibrator = getDeviceVibrator(context)
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (stage == 1) {
                        // Stage 1: Sonic ring ignition
                        vibrator.vibrate(VibrationEffect.createOneShot(25L, 110))
                    } else {
                        // Stage 2: Core lock-in harmonic pulse
                        val timings = longArrayOf(0L, 24L, 40L, 50L)
                        val amplitudes = intArrayOf(0, 140, 0, 230)
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                    }
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(if (stage == 1) 20L else 45L)
                }
            }
            if (stage == 2) {
                vibrateConnectedGamepads(55L, 180)
            }
        } catch (_: Exception) {}
    }
}
