package com.example.service

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.view.KeyEvent
import android.view.MotionEvent
import com.example.util.ControllerHealthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Non-Accessibility Direct USB & Raw Input Bridge State Holder.
 *
 * Replaces the legacy AccessibilityService so the app never requires or declares
 * `android.permission.BIND_ACCESSIBILITY_SERVICE`, while maintaining full compatibility
 * with `ControllerOverlayService` and `GamepadRawEventBridge`.
 */
object ControllerInputPassThroughService {

    private val _isServiceConnected = MutableStateFlow(true)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    @Volatile
    var activeInstance: ControllerInputPassThroughService? = this
        private set

    fun setBridgeActive(active: Boolean) {
        _isServiceConnected.value = active
        activeInstance = if (active) this else null
    }

    fun maybeForwardMotionToLauncherFocus(@Suppress("UNUSED_PARAMETER") event: MotionEvent) {
        // No-op: native window retains focus directly.
    }

    fun forwardDirectionalFocusToActiveWindow(@Suppress("UNUSED_PARAMETER") direction: Int) {
        // No-op: native window retains focus directly.
    }

    fun forwardKeyFromOverlayToSystem(@Suppress("UNUSED_PARAMETER") event: KeyEvent) {
        // No-op: overlay uses FLAG_NOT_FOCUSABLE.
    }
}

/**
 * Direct USB Host & Raw Event Input Bridge (No Accessibility Service Required).
 *
 * Streams wired controller inputs to `ControllerHealthManager` so the floating
 * `ControllerOverlayService` HUD updates in real time:
 * 1. Linux `/dev/input/eventX` Raw Event Reader (`EV_ABS` analog sticks, triggers, D-Pad hat,
 *    and `EV_KEY` gamepad buttons `BTN_SOUTH`, `BTN_EAST`, `BTN_NORTH`, `BTN_WEST`, `BTN_TL`,
 *    `BTN_TR`, `BTN_TL2`, `BTN_TR2`, `BTN_SELECT`, `BTN_START`, `BTN_MODE`, `BTN_THUMBL`, `BTN_THUMBR`).
 * 2. Direct USB HID Interrupt Endpoint Reader (`UsbManager`) for connected wired USB controllers
 *    when direct USB permission is available.
 */
object GamepadRawEventBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var readerJob: Job? = null

    private val KNOWN_USB_GAMEPAD_VENDORS = setOf(
        "054c", // Sony (DualSense / DualShock 4)
        "045e", // Microsoft Xbox
        "057e", // Nintendo Switch Pro
        "28de", // Valve
        "1532", // Razer
        "2dc8", // 8BitDo
        "3537", // GameSir
        "27f8"  // Backbone
    )

    private val KNOWN_USB_VENDOR_IDS = setOf(
        0x054C, 0x045E, 0x057E, 0x28DE, 0x1532, 0x2DC8, 0x3537, 0x27F8
    )

    fun ensureStarted(context: Context) {
        ControllerInputPassThroughService.setBridgeActive(true)
        if (readerJob?.isActive == true) return
        val appCtx = context.applicationContext
        readerJob = scope.launch {
            val manager = ControllerHealthManager(appCtx)
            while (isActive) {
                if (!ControllerOverlayService.isRunning || ControllerHealthManager.isAppInForeground) {
                    delay(400L)
                    continue
                }

                // Path 1: Try reading `/dev/input/eventX` raw input node if readable
                val eventNodePath = findExternalUsbGamepadEventNode()
                if (eventNodePath != null) {
                    val file = File(eventNodePath)
                    if (file.exists() && file.canRead()) {
                        readRawInputEventStream(file, manager)
                        delay(500L)
                        continue
                    }
                }

                // Path 2: Try reading USB HID interrupt endpoint directly via UsbManager if permitted
                val handledByUsbHid = tryReadUsbHidReports(appCtx, manager)
                if (!handledByUsbHid) {
                    delay(1000L)
                }
            }
        }
    }

    private suspend fun readRawInputEventStream(file: File, manager: ControllerHealthManager) {
        try {
            FileInputStream(file).use { fis ->
                val buf = ByteArray(24) // 64-bit struct input_event (16 bytes timeval + 2 type + 2 code + 4 value)
                var lx = 0f
                var ly = 0f
                var rx = 0f
                var ry = 0f
                var lt = 0f
                var rt = 0f
                var hatX = 0f
                var hatY = 0f
                while (scope.isActive &&
                    ControllerOverlayService.isRunning &&
                    !ControllerHealthManager.isAppInForeground
                ) {
                    val n = fis.read(buf)
                    if (n < 16) break
                    val bb = ByteBuffer.wrap(buf, 0, n).order(ByteOrder.LITTLE_ENDIAN)
                    val offset = if (n >= 24) 16 else 8
                    val type = bb.getShort(offset).toInt() and 0xFFFF
                    val code = bb.getShort(offset + 2).toInt() and 0xFFFF
                    val value = bb.getInt(offset + 4)
                    when (type) {
                        0x03 -> { // EV_ABS
                            when (code) {
                                0x00 -> lx = ((value - 128) / 128f).coerceIn(-1f, 1f) // ABS_X
                                0x01 -> ly = ((value - 128) / 128f).coerceIn(-1f, 1f) // ABS_Y
                                0x02 -> lt = (value / 255f).coerceIn(0f, 1f)          // ABS_Z / L2
                                0x03 -> rx = ((value - 128) / 128f).coerceIn(-1f, 1f) // ABS_RX / Right Stick X
                                0x04 -> ry = ((value - 128) / 128f).coerceIn(-1f, 1f) // ABS_RY / Right Stick Y
                                0x05 -> rt = (value / 255f).coerceIn(0f, 1f)          // ABS_RZ / R2
                                0x10 -> hatX = value.toFloat().coerceIn(-1f, 1f)      // ABS_HAT0X
                                0x11 -> hatY = value.toFloat().coerceIn(-1f, 1f)      // ABS_HAT0Y
                            }
                        }
                        0x01 -> { // EV_KEY (Physical Gamepad Buttons & D-Pad)
                            val btnName = mapLinuxKeyCodeToOverlayButton(code)
                            if (btnName != null) {
                                manager.setSimulatedButtonState(btnName, value != 0)
                            }
                        }
                        0x00 -> { // EV_SYN / SYN_REPORT
                            if (code == 0x00) {
                                manager.updateAxesFromRawStream(lx, ly, rx, ry, lt, rt, hatX, hatY)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            delay(1000L)
        }
    }

    private fun mapLinuxKeyCodeToOverlayButton(code: Int): String? {
        return when (code) {
            103, 544 -> "DPAD_UP"
            108, 545 -> "DPAD_DOWN"
            105, 546 -> "DPAD_LEFT"
            106, 547 -> "DPAD_RIGHT"
            304, 288 -> "A"
            305, 289, 306 -> "B"
            307, 290 -> "X"
            308, 291 -> "Y"
            310, 292 -> "L1"
            311, 293, 309 -> "R1"
            312, 294 -> "L2"
            313, 295 -> "R2"
            314, 296 -> "SELECT"
            315, 297 -> "START"
            316, 300 -> "MODE"
            317, 298 -> "THUMBL"
            318, 299 -> "THUMBR"
            else -> null
        }
    }

    private fun tryReadUsbHidReports(context: Context, manager: ControllerHealthManager): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return false
        val deviceList = try {
            usbManager.deviceList?.values ?: return false
        } catch (_: Exception) {
            return false
        }

        val gamepadDevice = deviceList.firstOrNull { dev ->
            isUsbGamepadDevice(dev) && usbManager.hasPermission(dev)
        } ?: return false

        var targetInterface: UsbInterface? = null
        var targetEndpoint: UsbEndpoint? = null
        for (i in 0 until gamepadDevice.interfaceCount) {
            val intf = gamepadDevice.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_HID ||
                intf.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC
            ) {
                for (j in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(j)
                    if (ep.direction == UsbConstants.USB_DIR_IN &&
                        (ep.type == UsbConstants.USB_ENDPOINT_XFER_INT || ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK)
                    ) {
                        targetInterface = intf
                        targetEndpoint = ep
                        break
                    }
                }
            }
            if (targetEndpoint != null) break
        }

        val intf = targetInterface ?: return false
        val endpoint = targetEndpoint ?: return false
        val connection = try {
            usbManager.openDevice(gamepadDevice) ?: return false
        } catch (_: Exception) {
            return false
        }

        try {
            // Claim non-forcefully so we never detach kernel input drivers used by active games
            if (!connection.claimInterface(intf, false)) {
                connection.close()
                return false
            }
            val packetSize = endpoint.maxPacketSize.coerceIn(16, 128)
            val buffer = ByteArray(packetSize)
            val isSony = gamepadDevice.vendorId == 0x054C
            while (scope.isActive &&
                ControllerOverlayService.isRunning &&
                !ControllerHealthManager.isAppInForeground
            ) {
                val readBytes = connection.bulkTransfer(endpoint, buffer, buffer.size, 32)
                if (readBytes >= 8) {
                    parseStandardHidReport(buffer, readBytes, isSony, manager)
                }
            }
            return true
        } catch (_: Exception) {
            return false
        } finally {
            try {
                connection.releaseInterface(intf)
            } catch (_: Exception) {}
            try {
                connection.close()
            } catch (_: Exception) {}
        }
    }

    private fun parseStandardHidReport(
        buf: ByteArray,
        len: Int,
        isSony: Boolean,
        manager: ControllerHealthManager
    ) {
        if (len < 8) return
        val offset = if (buf[0].toInt() == 0x01 && len >= 10) 1 else 0
        val lxRaw = buf[offset].toInt() and 0xFF
        val lyRaw = buf[offset + 1].toInt() and 0xFF
        val rxRaw = buf[offset + 2].toInt() and 0xFF
        val ryRaw = buf[offset + 3].toInt() and 0xFF

        val lx = ((lxRaw - 128) / 128f).coerceIn(-1f, 1f)
        val ly = ((lyRaw - 128) / 128f).coerceIn(-1f, 1f)
        val rx = ((rxRaw - 128) / 128f).coerceIn(-1f, 1f)
        val ry = ((ryRaw - 128) / 128f).coerceIn(-1f, 1f)

        val buttonsByte1 = buf[offset + 7.coerceAtMost(len - 1 - offset)].toInt() and 0xFF
        val hat = buttonsByte1 and 0x0F
        val hatX = when (hat) {
            1, 2, 3 -> 1f
            5, 6, 7 -> -1f
            else -> 0f
        }
        val hatY = when (hat) {
            0, 1, 7 -> -1f
            3, 4, 5 -> 1f
            else -> 0f
        }

        val lt = if (isSony && offset + 4 < len) ((buf[offset + 4].toInt() and 0xFF) / 255f).coerceIn(0f, 1f) else 0f
        val rt = if (isSony && offset + 5 < len) ((buf[offset + 5].toInt() and 0xFF) / 255f).coerceIn(0f, 1f) else 0f

        manager.updateAxesFromRawStream(lx, ly, rx, ry, lt, rt, hatX, hatY)

        if (isSony) {
            manager.setSimulatedButtonState("X", (buttonsByte1 and 0x10) != 0)
            manager.setSimulatedButtonState("A", (buttonsByte1 and 0x20) != 0)
            manager.setSimulatedButtonState("B", (buttonsByte1 and 0x40) != 0)
            manager.setSimulatedButtonState("Y", (buttonsByte1 and 0x80) != 0)
            if (offset + 8 < len) {
                val b2 = buf[offset + 8].toInt() and 0xFF
                manager.setSimulatedButtonState("L1", (b2 and 0x01) != 0)
                manager.setSimulatedButtonState("R1", (b2 and 0x02) != 0)
                manager.setSimulatedButtonState("L2", (b2 and 0x04) != 0 || lt > 0.25f)
                manager.setSimulatedButtonState("R2", (b2 and 0x08) != 0 || rt > 0.25f)
                manager.setSimulatedButtonState("SELECT", (b2 and 0x10) != 0)
                manager.setSimulatedButtonState("START", (b2 and 0x20) != 0)
                manager.setSimulatedButtonState("THUMBL", (b2 and 0x40) != 0)
                manager.setSimulatedButtonState("THUMBR", (b2 and 0x80) != 0)
            }
        }
    }

    private fun isUsbGamepadDevice(device: UsbDevice): Boolean {
        if (device.vendorId in KNOWN_USB_VENDOR_IDS) return true
        val nameLower = (device.productName ?: "").lowercase()
        return nameLower.contains("controller") ||
            nameLower.contains("gamepad") ||
            nameLower.contains("dualsense") ||
            nameLower.contains("dualshock") ||
            nameLower.contains("xbox") ||
            nameLower.contains("joystick")
    }

    private fun findExternalUsbGamepadEventNode(): String? {
        return try {
            val procFile = File("/proc/bus/input/devices")
            if (!procFile.exists() || !procFile.canRead()) return null
            val blocks = procFile.readText().split("\n\n")
            for (block in blocks) {
                val lower = block.lowercase()
                if (lower.contains("motion sensor") ||
                    lower.contains("touchpad") ||
                    lower.contains("accelerometer") ||
                    lower.contains("gyroscope") ||
                    lower.contains("touchscreen")
                ) {
                    continue
                }
                val hasKnownVendor = KNOWN_USB_GAMEPAD_VENDORS.any { v ->
                    lower.contains("vendor=$v")
                }
                val isUsbBus = lower.contains("bus=0003") // BUS_USB = 0x03
                if (hasKnownVendor || (isUsbBus && (lower.contains("controller") || lower.contains("gamepad") || lower.contains("joystick")))) {
                    val match = Regex("event\\d+").find(block)
                    if (match != null) {
                        return "/dev/input/${match.value}"
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
