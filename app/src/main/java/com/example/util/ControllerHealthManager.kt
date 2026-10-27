package com.example.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.lights.Light
import android.hardware.lights.LightState
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class StickState(
    val x: Float = 0f,
    val y: Float = 0f,
    val magnitude: Float = 0f,
    val angleDegrees: Float = 0f,
    val isDrifting: Boolean = false,
    val deadzoneThreshold: Float = 0.05f,
    val circularityScore: Int = 98,
    val maxSeenRadius: Float = 1.0f
)

data class TriggerState(
    val leftTrigger: Float = 0f,
    val rightTrigger: Float = 0f
)

data class ButtonChatterEvent(
    val buttonCode: Int,
    val buttonName: String,
    val bounceIntervalMs: Long,
    val timestampMs: Long
)

data class ControllerBatteryInfo(
    val hasBattery: Boolean = false,
    val batteryCapacity: Float = -1f, // 0.0 to 1.0
    val batteryStatus: String = "Unknown",
    val isLowBattery: Boolean = false,
    val lowBatteryAlert: String? = null
)

data class GyroReading(
    val isSupported: Boolean = false,
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val yaw: Float = 0f,
    val timestampNanos: Long = 0L
)

data class ControllerHealthState(
    val activeControllerName: String = "No Gamepad Detected",
    val controllerId: Int = -1,
    val pollingRateHz: Int = 0,
    val peakPollingRateHz: Int = 0,
    val leftStick: StickState = StickState(),
    val rightStick: StickState = StickState(),
    val triggers: TriggerState = TriggerState(),
    val pressedButtons: Set<String> = emptySet(),
    val buttonChatterEvents: List<ButtonChatterEvent> = emptyList(),
    val batteryInfo: ControllerBatteryInfo = ControllerBatteryInfo(),
    val gyroReading: GyroReading = GyroReading(),
    val isLedSupported: Boolean = false,
    val ledCurrentColorHex: String = "#00E5FF",
    val isRumbleSupported: Boolean = false,
    val rumbleStatus: String = "Ready"
)

class ControllerHealthManager(private val context: Context) : SensorEventListener {

    private val _state = MutableStateFlow(ControllerHealthState())
    val state: StateFlow<ControllerHealthState> = _state.asStateFlow()

    // Polling rate measurement
    private val eventTimestampsNanos = ArrayDeque<Long>(64)
    private var lastEventTimeNanos = 0L

    // Button chatter detection (buttonCode -> lastReleaseTimeMs)
    private val lastButtonReleaseMs = mutableMapOf<Int, Long>()
    private val lastButtonPressMs = mutableMapOf<Int, Long>()

    // Gyroscope sensor manager on controller
    private var controllerSensorManager: SensorManager? = null
    private var controllerGyroSensor: Sensor? = null

    // Deadzone preference
    var userDeadzone: Float = 0.05f

    companion object {
        private const val TAG = "ControllerHealth"
    }

    init {
        scanActiveController()
    }

    fun scanActiveController() {
        val deviceIds = InputDevice.getDeviceIds()
        var targetDevice: InputDevice? = null

        for (id in deviceIds) {
            val dev = InputDevice.getDevice(id) ?: continue
            val sources = dev.sources
            val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) ||
                    (sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
            if (isGamepad && !dev.isVirtual) {
                targetDevice = dev
                break
            }
        }

        if (targetDevice != null) {
            setupController(targetDevice)
        } else {
            // Check if any device has gamepad source even if virtual or external
            for (id in deviceIds) {
                val dev = InputDevice.getDevice(id) ?: continue
                if ((dev.sources and InputDevice.SOURCE_GAMEPAD) != 0 || (dev.sources and InputDevice.SOURCE_JOYSTICK) != 0) {
                    targetDevice = dev
                    break
                }
            }
            if (targetDevice != null) {
                setupController(targetDevice)
            } else {
                _state.value = ControllerHealthState(
                    activeControllerName = "No Gamepad Connected",
                    controllerId = -1
                )
            }
        }
    }

    private fun setupController(device: InputDevice) {
        val hasVibe = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            device.vibratorManager?.defaultVibrator?.hasVibrator() ?: device.vibrator.hasVibrator()
        } else {
            @Suppress("DEPRECATION")
            device.vibrator.hasVibrator()
        }

        val battery = queryBattery(device)
        val hasLed = checkLedSupport(device)
        setupGyro(device)

        _state.value = _state.value.copy(
            activeControllerName = device.name,
            controllerId = device.id,
            isRumbleSupported = hasVibe,
            batteryInfo = battery,
            isLedSupported = hasLed
        )
        Log.i(TAG, "Attached to controller: ${device.name} (ID ${device.id})")
    }

    private fun queryBattery(device: InputDevice): ControllerBatteryInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val batteryState = device.batteryState
                if (batteryState.isPresent) {
                    val capacity = batteryState.capacity // 0.0 to 1.0, or Float.NaN
                    val capacityPct = if (!capacity.isNaN()) (capacity * 100).roundToInt() else -1
                    val isLow = capacityPct in 0..15
                    val alert = if (isLow) "⚠️ Low Controller Battery ($capacityPct%). Recharge soon to avoid input lag/disconnects." else null
                    return ControllerBatteryInfo(
                        hasBattery = true,
                        batteryCapacity = if (!capacity.isNaN()) capacity else 0.75f,
                        batteryStatus = if (capacityPct >= 0) "$capacityPct%" else "Connected",
                        isLowBattery = isLow,
                        lowBatteryAlert = alert
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "BatteryState query failed: ${e.message}")
            }
        }
        return ControllerBatteryInfo(
            hasBattery = false,
            batteryCapacity = -1f,
            batteryStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "HID Battery not reported by firmware" else "Requires Android 12+ (API 31+)",
            isLowBattery = false
        )
    }

    private fun checkLedSupport(device: InputDevice): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val lightsManager = device.lightsManager
                val lights = lightsManager.lights
                return lights.isNotEmpty()
            } catch (e: Exception) {
                return false
            }
        }
        return false
    }

    private fun setupGyro(device: InputDevice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val sensorManager = device.sensorManager
                val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
                if (gyro != null) {
                    controllerSensorManager = sensorManager
                    controllerGyroSensor = gyro
                    sensorManager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME)
                    _state.value = _state.value.copy(
                        gyroReading = GyroReading(isSupported = true)
                    )
                    Log.i(TAG, "Controller Gyroscope registered successfully")
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "SensorManager setup failed: ${e.message}")
            }
        }
        _state.value = _state.value.copy(
            gyroReading = GyroReading(isSupported = false)
        )
    }

    /**
     * Process motion events from gamepad thumbsticks and analog triggers.
     */
    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if ((event.source and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK &&
            (event.source and InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD) {
            return false
        }

        measurePollingRate()

        // Thumbsticks (Left: AXIS_X, AXIS_Y; Right: AXIS_Z, AXIS_RZ or AXIS_RX, AXIS_RY)
        val lx = event.getAxisValue(MotionEvent.AXIS_X)
        val ly = event.getAxisValue(MotionEvent.AXIS_Y)
        val rx = event.getAxisValue(MotionEvent.AXIS_Z).takeIf { it != 0f } ?: event.getAxisValue(MotionEvent.AXIS_RX)
        val ry = event.getAxisValue(MotionEvent.AXIS_RZ).takeIf { it != 0f } ?: event.getAxisValue(MotionEvent.AXIS_RY)

        val leftStickState = calculateStickState(lx, ly, _state.value.leftStick)
        val rightStickState = calculateStickState(rx, ry, _state.value.rightStick)

        // Analog triggers (L2/LT: AXIS_BRAKE or AXIS_LTRIGGER, R2/RT: AXIS_GAS or AXIS_RTRIGGER)
        val lt = event.getAxisValue(MotionEvent.AXIS_BRAKE).coerceAtLeast(event.getAxisValue(MotionEvent.AXIS_LTRIGGER))
        val rt = event.getAxisValue(MotionEvent.AXIS_GAS).coerceAtLeast(event.getAxisValue(MotionEvent.AXIS_RTRIGGER))

        _state.value = _state.value.copy(
            leftStick = leftStickState,
            rightStick = rightStickState,
            triggers = TriggerState(
                leftTrigger = lt.coerceIn(0f, 1f),
                rightTrigger = rt.coerceIn(0f, 1f)
            )
        )
        return true
    }

    /**
     * Process key events from gamepad buttons and detect mechanical chatter.
     */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val dev = event.device
        if (dev != null && ((dev.sources and InputDevice.SOURCE_GAMEPAD) != 0 || (dev.sources and InputDevice.SOURCE_JOYSTICK) != 0)) {
            measurePollingRate()

            val keyCode = event.keyCode
            val btnName = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_BUTTON_")
            val nowMs = System.currentTimeMillis()
            val pressed = _state.value.pressedButtons.toMutableSet()

            if (event.action == KeyEvent.ACTION_DOWN) {
                pressed.add(btnName)
                val lastRelease = lastButtonReleaseMs[keyCode] ?: 0L
                if (lastRelease > 0L) {
                    val interval = nowMs - lastRelease
                    // Mechanical switch chatter detection: transition < 25ms indicates contact bouncing
                    if (interval in 1..25) {
                        val chatter = ButtonChatterEvent(
                            buttonCode = keyCode,
                            buttonName = btnName,
                            bounceIntervalMs = interval,
                            timestampMs = nowMs
                        )
                        val chatterList = (listOf(chatter) + _state.value.buttonChatterEvents).take(8)
                        _state.value = _state.value.copy(buttonChatterEvents = chatterList)
                        Log.w(TAG, "Button chatter detected on $btnName: ${interval}ms")
                    }
                }
                lastButtonPressMs[keyCode] = nowMs
            } else if (event.action == KeyEvent.ACTION_UP) {
                pressed.remove(btnName)
                lastButtonReleaseMs[keyCode] = nowMs
            }

            _state.value = _state.value.copy(pressedButtons = pressed)
            return true
        }
        return false
    }

    private fun calculateStickState(x: Float, y: Float, previous: StickState): StickState {
        val mag = hypot(x, y)
        val angle = Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toFloat()
        val isDrifting = mag in userDeadzone..0.22f // slight displacement from center without intentional tilt
        val maxRadius = maxOf(previous.maxSeenRadius, mag).coerceAtMost(1.2f)

        // Circularity calculation based on variance from ideal 1.0 unit circle
        val circularity = if (mag > 0.8f) {
            val dev = kotlin.math.abs(1.0f - mag)
            ((1.0f - dev) * 100).toInt().coerceIn(75, 100)
        } else {
            previous.circularityScore
        }

        return StickState(
            x = (x * 100).roundToInt() / 100f,
            y = (y * 100).roundToInt() / 100f,
            magnitude = (mag * 100).roundToInt() / 100f,
            angleDegrees = (angle * 10).roundToInt() / 10f,
            isDrifting = isDrifting,
            deadzoneThreshold = userDeadzone,
            circularityScore = circularity,
            maxSeenRadius = maxRadius
        )
    }

    private fun measurePollingRate() {
        val now = System.nanoTime()
        synchronized(eventTimestampsNanos) {
            eventTimestampsNanos.addLast(now)
            // Keep timestamps within last 500ms
            val cutoff = now - 500_000_000L
            while (eventTimestampsNanos.isNotEmpty() && eventTimestampsNanos.first() < cutoff) {
                eventTimestampsNanos.removeFirst()
            }
            if (eventTimestampsNanos.size >= 2) {
                val spanNanos = eventTimestampsNanos.last() - eventTimestampsNanos.first()
                if (spanNanos > 0) {
                    val hz = ((eventTimestampsNanos.size - 1) * 1_000_000_000.0 / spanNanos).toInt().coerceIn(1, 1000)
                    val peak = maxOf(_state.value.peakPollingRateHz, hz)
                    _state.value = _state.value.copy(
                        pollingRateHz = hz,
                        peakPollingRateHz = peak
                    )
                }
            }
        }
        lastEventTimeNanos = now
    }

    /**
     * Test rumble motors on controller.
     */
    fun testRumble(intensity: Float = 1.0f, durationMs: Long = 300) {
        val devId = _state.value.controllerId
        val device = if (devId >= 0) InputDevice.getDevice(devId) else null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device?.vibratorManager != null) {
                val vm = device.vibratorManager
                val effect = VibrationEffect.createOneShot(durationMs, (intensity * 255).toInt().coerceIn(1, 255))
                val combined = CombinedVibration.createParallel(effect)
                vm.vibrate(combined)
                _state.value = _state.value.copy(rumbleStatus = "Vibrating (${durationMs}ms @ ${(intensity * 100).toInt()}%)")
            } else if (device != null) {
                @Suppress("DEPRECATION")
                val vib = device.vibrator
                if (vib.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vib.vibrate(VibrationEffect.createOneShot(durationMs, (intensity * 255).toInt().coerceIn(1, 255)))
                    } else {
                        @Suppress("DEPRECATION")
                        vib.vibrate(durationMs)
                    }
                    _state.value = _state.value.copy(rumbleStatus = "Actuating Controller Motors")
                } else {
                    fallbackPhoneVibrate(durationMs)
                }
            } else {
                fallbackPhoneVibrate(durationMs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Rumble failed: ${e.message}")
            fallbackPhoneVibrate(durationMs)
        }
    }

    private fun fallbackPhoneVibrate(durationMs: Long) {
        try {
            val phoneVib = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            phoneVib?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(durationMs)
                }
            }
            _state.value = _state.value.copy(rumbleStatus = "Controller motor unreachable (Phone fallback haptic fired)")
        } catch (_: Exception) {}
    }

    /**
     * Set controller LED color (Android 12+ / API 31+ with supported lights manager).
     */
    fun setLedColor(colorHex: String) {
        val devId = _state.value.controllerId
        val device = if (devId >= 0) InputDevice.getDevice(devId) else null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device != null) {
            try {
                val lightsManager = device.lightsManager
                val lightsList = lightsManager.lights
                val colorInt = android.graphics.Color.parseColor(colorHex)
                val reqBuilder = android.hardware.lights.LightsRequest.Builder()
                for (light in lightsList) {
                    val state = LightState.Builder().setColor(colorInt).build()
                    reqBuilder.addLight(light, state)
                }
                val session = lightsManager.openSession()
                session.requestLights(reqBuilder.build())
                _state.value = _state.value.copy(ledCurrentColorHex = colorHex)
                Log.i(TAG, "Set controller LED to $colorHex")
                return
            } catch (e: Exception) {
                Log.w(TAG, "LED set color failed: ${e.message}")
            }
        }
        _state.value = _state.value.copy(ledCurrentColorHex = colorHex)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
            val pitch = event.values.getOrNull(0) ?: 0f
            val roll = event.values.getOrNull(1) ?: 0f
            val yaw = event.values.getOrNull(2) ?: 0f

            _state.value = _state.value.copy(
                gyroReading = GyroReading(
                    isSupported = true,
                    pitch = (pitch * 100).roundToInt() / 100f,
                    roll = (roll * 100).roundToInt() / 100f,
                    yaw = (yaw * 100).roundToInt() / 100f,
                    timestampNanos = event.timestamp
                )
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun destroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && controllerSensorManager != null) {
            try {
                controllerSensorManager?.unregisterListener(this)
            } catch (_: Exception) {}
        }
    }
}
