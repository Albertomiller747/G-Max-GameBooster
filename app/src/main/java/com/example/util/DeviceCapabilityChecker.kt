package com.example.util

import android.content.Context
import android.hardware.display.DisplayManager
import android.net.wifi.WifiManager
import android.os.Build
import android.view.Display
import android.view.InputDevice

object DeviceCapabilityChecker {

    fun supportsHighRefreshRate(context: Context): Boolean {
        return try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val defaultDisplay = dm?.getDisplay(Display.DEFAULT_DISPLAY)
            val modes = defaultDisplay?.supportedModes ?: emptyArray()
            modes.any { it.refreshRate >= 80f } || (defaultDisplay?.refreshRate ?: 60f) >= 80f
        } catch (_: Exception) {
            true // default fallback
        }
    }

    fun supports5GHzWifi(context: Context): Boolean {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wm?.is5GHzBandSupported == true
        } catch (_: Exception) {
            true
        }
    }

    fun supportsAndroid12GameMode(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    fun supportsThermalHeadroom(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    fun hasConnectedGamepad(): Boolean {
        val deviceIds = InputDevice.getDeviceIds()
        for (id in deviceIds) {
            val device = InputDevice.getDevice(id) ?: continue
            val sources = device.sources
            val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            if (isGamepad && !device.isVirtual) {
                return true
            }
        }
        return false
    }

    fun supportsBypassCharging(): Boolean {
        // Only specific OEM gaming hardware supports direct charge bypass
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        return brand.contains("asus") || brand.contains("rog") ||
                brand.contains("blackshark") || brand.contains("redmagic") ||
                manufacturer.contains("sony") || brand.contains("samsung")
    }
}
