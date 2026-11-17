package com.example.util

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
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
                val displayName = ControllerHealthManager.normalizeControllerDisplayName(device)
                knownGamepadMap[deviceId] = displayName
                _connectionAlerts.tryEmit(
                    ControllerConnectionAlert(
                        isConnected = true,
                        controllerName = displayName,
                        deviceId = deviceId
                    )
                )
            }
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            val cachedName = knownGamepadMap.remove(deviceId)
            if (cachedName != null) {
                if (!ControllerHealthManager.isWiredControllerConnected(context)) {
                    com.example.service.ControllerOverlayService.disableIfNotWired(context)
                }
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
            val device = InputDevice.getDevice(deviceId)
            if (device != null && isGamepadOrJoystick(device) && !device.isVirtual) {
                val displayName = ControllerHealthManager.normalizeControllerDisplayName(device)
                val wasKnown = knownGamepadMap.containsKey(deviceId)
                knownGamepadMap[deviceId] = displayName
                if (!wasKnown) {
                    _connectionAlerts.tryEmit(
                        ControllerConnectionAlert(
                            isConnected = true,
                            controllerName = displayName,
                            deviceId = deviceId
                        )
                    )
                }
            }
            if (!ControllerHealthManager.isWiredControllerConnected(context)) {
                com.example.service.ControllerOverlayService.disableIfNotWired(context)
            }
        }
    }

    init {
        try {
            inputManager?.registerInputDeviceListener(inputDeviceListener, mainHandler)
            getConnectedGamepads().forEach { gp ->
                knownGamepadMap[gp.id] = gp.name
            }
        } catch (_: Exception) {}
    }

    fun getConnectedGamepads(): List<GamepadDevice> {
        val connectedByGroup = linkedMapOf<String, GamepadDevice>()
        val deviceIds = try { InputDevice.getDeviceIds() } catch (_: Exception) { IntArray(0) }

        for (id in deviceIds) {
            val device = InputDevice.getDevice(id) ?: continue
            if (isGamepadOrJoystick(device) && !device.isVirtual) {
                val displayName = ControllerHealthManager.normalizeControllerDisplayName(device)
                val wired = ControllerHealthManager.isDeviceWiredUsb(context, device)
                val hasVibe = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    device.vibratorManager?.defaultVibrator?.hasVibrator() == true ||
                        device.vibratorManager?.vibratorIds?.isNotEmpty() == true ||
                        @Suppress("DEPRECATION") device.vibrator.hasVibrator()
                } else {
                    @Suppress("DEPRECATION")
                    device.vibrator.hasVibrator()
                }
                val groupKey = "${device.vendorId}:${device.productId}:${displayName.lowercase()}"
                val existing = connectedByGroup[groupKey]
                val isPrimaryNode = ControllerHealthManager.isActualGamepadInputDevice(device)
                if (existing == null || isPrimaryNode) {
                    connectedByGroup[groupKey] = GamepadDevice(
                        id = if (isPrimaryNode || existing == null) device.id else existing.id,
                        name = displayName,
                        vendorId = device.vendorId,
                        productId = device.productId,
                        hasVibrator = hasVibe || (existing?.hasVibrator == true),
                        isConnected = true,
                        isWiredUsb = wired || (existing?.isWiredUsb == true),
                        connectionTypeLabel = if (wired || existing?.isWiredUsb == true) "Wired USB" else "Bluetooth / Wireless"
                    )
                } else {
                    connectedByGroup[groupKey] = existing.copy(
                        hasVibrator = existing.hasVibrator || hasVibe,
                        isWiredUsb = existing.isWiredUsb || wired,
                        connectionTypeLabel = if (existing.isWiredUsb || wired) "Wired USB" else "Bluetooth / Wireless"
                    )
                }
            }
        }

        if (connectedByGroup.isNotEmpty()) {
            return connectedByGroup.values.toList()
        }

        // Hardware USB fallback when UsbManager sees a connected gamepad
        val usbDev = ControllerHealthManager.findConnectedUsbGamepadDevice(context)
        if (usbDev != null) {
            return listOf(
                GamepadDevice(
                    id = usbDev.deviceId.coerceAtLeast(1),
                    name = ControllerHealthManager.resolveUsbControllerName(usbDev),
                    vendorId = usbDev.vendorId,
                    productId = usbDev.productId,
                    hasVibrator = true,
                    isConnected = true,
                    isWiredUsb = true,
                    connectionTypeLabel = "Wired USB"
                )
            )
        }

        // Kernel /proc/bus/input/devices fallback for Bluetooth or USB controllers
        val procEntry = ControllerHealthManager.findProcBusControllerInfo()
        if (procEntry != null) {
            return listOf(
                GamepadDevice(
                    id = 1,
                    name = procEntry.name,
                    vendorId = 0x054C,
                    productId = 0,
                    hasVibrator = true,
                    isConnected = true,
                    isWiredUsb = procEntry.isWiredUsb,
                    connectionTypeLabel = if (procEntry.isWiredUsb) "Wired USB" else "Bluetooth / Wireless"
                )
            )
        }

        // Live-input fallback if ControllerHealthManager is actively bound to a controller
        val liveState = ControllerHealthManager.liveInputState.value
        if (liveState.controllerId >= 0) {
            return listOf(
                GamepadDevice(
                    id = liveState.controllerId,
                    name = liveState.activeControllerName,
                    vendorId = 0x054C,
                    productId = 0,
                    hasVibrator = liveState.isRumbleSupported,
                    isConnected = true,
                    isWiredUsb = liveState.isWiredConnection,
                    connectionTypeLabel = liveState.connectionTypeLabel
                )
            )
        }

        return emptyList()
    }

    private fun isGamepadOrJoystick(device: InputDevice): Boolean {
        return ControllerHealthManager.isActualGamepadInputDevice(device) ||
            ControllerHealthManager.isCompanionControllerNode(device)
    }

    fun destroy() {
        try {
            inputManager?.unregisterInputDeviceListener(inputDeviceListener)
        } catch (_: Exception) {}
    }
}
