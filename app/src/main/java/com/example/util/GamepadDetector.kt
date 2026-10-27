package com.example.util

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import com.example.model.GamepadDevice
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class ControllerConnectionAlert(
    val isConnected: Boolean,
    val controllerName: String,
    val deviceId: Int,
    val timestamp: Long = System.currentTimeMillis()
)

class GamepadDetector(private val context: Context) {

    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _connectionAlerts = MutableSharedFlow<ControllerConnectionAlert>(extraBufferCapacity = 16)
    val connectionAlerts: SharedFlow<ControllerConnectionAlert> = _connectionAlerts.asSharedFlow()

    private val knownGamepadMap = mutableMapOf<Int, String>()

    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            val device = InputDevice.getDevice(deviceId) ?: return
            if (isGamepadOrJoystick(device) && !device.isVirtual) {
                knownGamepadMap[deviceId] = device.name
                _connectionAlerts.tryEmit(
                    ControllerConnectionAlert(
                        isConnected = true,
                        controllerName = device.name,
                        deviceId = deviceId
                    )
                )
            }
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            val cachedName = knownGamepadMap.remove(deviceId)
            if (cachedName != null) {
                _connectionAlerts.tryEmit(
                    ControllerConnectionAlert(
                        isConnected = false,
                        controllerName = cachedName,
                        deviceId = deviceId
                    )
                )
            }
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            // No action needed
        }
    }

    init {
        try {
            inputManager?.registerInputDeviceListener(inputDeviceListener, mainHandler)
            // Pre-seed known gamepads
            getConnectedGamepads().forEach { gp ->
                knownGamepadMap[gp.id] = gp.name
            }
        } catch (_: Exception) {}
    }

    fun getConnectedGamepads(): List<GamepadDevice> {
        val connected = mutableListOf<GamepadDevice>()
        val deviceIds = InputDevice.getDeviceIds()

        for (id in deviceIds) {
            val device = InputDevice.getDevice(id) ?: continue
            if (isGamepadOrJoystick(device) && !device.isVirtual) {
                connected.add(
                    GamepadDevice(
                        id = device.id,
                        name = device.name,
                        vendorId = device.vendorId,
                        productId = device.productId,
                        hasVibrator = device.vibrator.hasVibrator(),
                        isConnected = true
                    )
                )
            }
        }
        return connected
    }

    private fun isGamepadOrJoystick(device: InputDevice): Boolean {
        val sources = device.sources
        return (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
    }

    fun destroy() {
        try {
            inputManager?.unregisterInputDeviceListener(inputDeviceListener)
        } catch (_: Exception) {}
    }
}
