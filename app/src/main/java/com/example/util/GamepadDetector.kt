package com.example.util

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import com.example.model.GamepadDevice

class GamepadDetector(private val context: Context) {

    fun getConnectedGamepads(): List<GamepadDevice> {
        val connected = mutableListOf<GamepadDevice>()
        val deviceIds = InputDevice.getDeviceIds()

        for (id in deviceIds) {
            val device = InputDevice.getDevice(id) ?: continue
            val sources = device.sources

            // Check if device is a gamepad or joystick
            val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK

            if (isGamepad && !device.isVirtual) {
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
}
