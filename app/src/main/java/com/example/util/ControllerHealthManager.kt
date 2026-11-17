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
    val batteryCapacity: Float = -1f, // 0.0 to 1.0 (-1f when disconnected)
    val batteryStatus: String = "Disconnected",
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
    val rumbleStatus: String = "Ready",
    val isWiredConnection: Boolean = false,
    val connectionTypeLabel: String = "Disconnected"
)

class ControllerHealthManager(private val context: Context) : SensorEventListener {

    private val _state: MutableStateFlow<ControllerHealthState>
        get() = _sharedLiveState
    val state: StateFlow<ControllerHealthState> = _sharedLiveState.asStateFlow()

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

        val VALID_OVERLAY_BUTTONS = setOf(
            "A", "B", "X", "Y",
            "L1", "R1", "L2", "R2",
            "SELECT", "START", "MODE",
            "THUMBL", "THUMBR",
            "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT"
        )

        private val _sharedLiveState = MutableStateFlow(ControllerHealthState())
        val liveInputState: StateFlow<ControllerHealthState> = _sharedLiveState.asStateFlow()

        private val _homeLoginSignal = MutableStateFlow(0L)
        val homeLoginSignal: StateFlow<Long> = _homeLoginSignal.asStateFlow()

        // Shared across all ControllerHealthManager instances (MainActivity, AccessibilityService, OverlayService)
        private val keyHeldDpad = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        private val keyHeldDpadTimestampMs = java.util.concurrent.ConcurrentHashMap<String, Long>()
        private val keyHeldTriggers = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        private val hatHeldDpad = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        private val pulseHeldDpad = java.util.concurrent.ConcurrentHashMap<String, Long>()
        private val pulseHeldButtons = java.util.concurrent.ConcurrentHashMap<String, Long>()
        private val activeScanCodeToButton = java.util.concurrent.ConcurrentHashMap<Int, String>()
        private val faceButtonDownTimestampMs = java.util.concurrent.ConcurrentHashMap<String, Long>()
        @Volatile
        private var suppressedCompanionTriangleActive: Boolean = false
        @Volatile
        var isControllerStudioActive: Boolean = false
            private set

        fun setControllerStudioActive(active: Boolean) {
            isControllerStudioActive = active
        }

        @Volatile
        var lastMotionStickUpdateMs: Long = 0L
            private set
        @Volatile
        private var lastMotionStateEmitMs: Long = 0L
        @Volatile
        var lastButtonActivityMs: Long = 0L
            private set
        @Volatile
        var isStickDeceleratingTowardCenter: Boolean = false
            private set
        @Volatile
        var isAppInForeground: Boolean = true
            private set
        @Volatile
        var isLauncherOrSystemInForeground: Boolean = false
            private set
        @Volatile
        var isGameInForeground: Boolean = false
            private set
        @Volatile
        var foregroundPackageName: String = ""
            private set
        @Volatile
        var lastOutsideTouchTimestampMs: Long = 0L
            private set
        @Volatile
        var isTimeSlicedSamplingActive: Boolean = false
        @Volatile
        private var lastInGameActionMs: Long = 0L
        @Volatile
        private var inGameTargetLx: Float = 0f
        @Volatile
        private var inGameTargetLy: Float = 0f

        fun notifyOutsideTouch() {
            lastOutsideTouchTimestampMs = System.currentTimeMillis()
        }

        fun clearTransientHeldInputs() {
            keyHeldDpad.clear()
            keyHeldDpadTimestampMs.clear()
            keyHeldTriggers.clear()
            hatHeldDpad.clear()
            pulseHeldDpad.clear()
            pulseHeldButtons.clear()
            activeScanCodeToButton.clear()
            faceButtonDownTimestampMs.clear()
            suppressedCompanionTriangleActive = false
            isStickDeceleratingTowardCenter = false
            lastMotionStickUpdateMs = 0L
            lastMotionStateEmitMs = 0L
            lastButtonActivityMs = 0L
            inGameTargetLx = 0f
            inGameTargetLy = 0f
            val cur = _sharedLiveState.value
            if (cur.pressedButtons.isNotEmpty() ||
                cur.leftStick.x != 0f || cur.leftStick.y != 0f ||
                cur.rightStick.x != 0f || cur.rightStick.y != 0f ||
                cur.triggers.leftTrigger != 0f || cur.triggers.rightTrigger != 0f
            ) {
                _sharedLiveState.value = cur.copy(
                    pressedButtons = emptySet(),
                    leftStick = StickState(deadzoneThreshold = cur.leftStick.deadzoneThreshold),
                    rightStick = StickState(deadzoneThreshold = cur.rightStick.deadzoneThreshold),
                    triggers = TriggerState()
                )
            }
        }

        fun setAppInForeground(inForeground: Boolean) {
            val changed = isAppInForeground != inForeground
            isAppInForeground = inForeground
            if (inForeground) {
                isLauncherOrSystemInForeground = false
                isGameInForeground = false
            } else if (!isGameInForeground) {
                isLauncherOrSystemInForeground = true
            }
            if (changed) {
                clearTransientHeldInputs()
            }
        }

        fun updateForegroundPackage(context: Context, pkg: String) {
            if (pkg.isBlank()) return
            val lower = pkg.lowercase()
            // Ignore transient overlays, keyboards, and system UI bars
            if (lower.contains("systemui") ||
                lower.contains("inputmethod") ||
                lower.contains("keyboard") ||
                lower.contains("gboard") ||
                lower.contains("permissioncontroller")
            ) {
                return
            }
            val pkgChanged = foregroundPackageName != pkg
            foregroundPackageName = pkg
            if (pkgChanged) {
                clearTransientHeldInputs()
            }
            if (pkg == context.packageName) {
                isAppInForeground = true
                isLauncherOrSystemInForeground = false
                isGameInForeground = false
                return
            }

            isAppInForeground = false
            val isLauncherPkg = lower.contains("launcher") ||
                lower.contains("home") ||
                lower.contains("trebuchet") ||
                lower.contains("nexuslauncher") ||
                lower.contains("lawnchair") ||
                lower.contains("nova") ||
                lower.contains("pixel") ||
                lower == "com.android.settings" ||
                lower == "android"

            isLauncherOrSystemInForeground = isLauncherPkg
            isGameInForeground = !isLauncherPkg
        }

        @Volatile
        var simulatedWiredOverrideForTests: Boolean? = null

        @Volatile
        var lastLiveControllerEventMs: Long = 0L
            private set

        val KNOWN_GAMEPAD_VENDORS = setOf(
            0x054C, // Sony Interactive Entertainment (PS4 DualShock 4, PS5 DualSense, DualSense Edge)
            0x045E, // Microsoft (Xbox Wireless / Elite / Series X|S)
            0x057E, // Nintendo (Switch Pro Controller, Joy-Con)
            0x28DE, // Valve (Steam Controller)
            0x1532, // Razer (Kishi, Wolverine, Raiju)
            0x2DC8, // 8BitDo
            0x3537, // GameSir
            0x27F8, // Backbone One
            0x20D6, // PowerA / BDA
            0x0F0D, // Hori
            0x0738, // Mad Catz
            0x046D, // Logitech
            0x1038, // SteelSeries
            0x24C6  // PowerA
        )

        fun isInternalPhoneDevice(device: InputDevice?): Boolean {
            if (device == null || device.isVirtual || device.id < 0) return true
            val lower = device.name.lowercase()
            if (lower.isBlank() || lower == "virtual") return true
            return lower.contains("qpnp") ||
                lower.contains("gpio") ||
                lower.contains("pmic") ||
                lower.contains("pwrkey") ||
                lower.contains("power_key") ||
                lower.contains("pon") ||
                lower.contains("fingerprint") ||
                lower.contains("uinput-fpc") ||
                lower.contains("uinput-goodix") ||
                lower.contains("goodix_ts") ||
                lower.contains("synaptics") ||
                lower.contains("fts_ts") ||
                lower.contains("sec_touchscreen") ||
                lower.contains("touchscreen") ||
                lower.contains("stylus") ||
                lower.contains("s-pen") ||
                lower.contains("headset") ||
                lower.contains("jack") ||
                lower.contains("snd-card") ||
                lower.contains("audiocodec")
        }

        /**
         * Over Wired USB-C and Bluetooth (Android 12+ hid-playstation / hid-sony), PlayStation DualSense and
         * DualShock 4 controllers expose 3 separate InputDevice nodes:
         * 1. "Wireless Controller" (primary gamepad)
         * 2. "Wireless Controller Motion Sensors" (250Hz IMU accelerometer/gyro tagged with SOURCE_JOYSTICK)
         * 3. "Wireless Controller Touchpad" (touchpad node)
         * We ignore 250Hz IMU axis frames from the Motion Sensors node inside onGenericMotionEvent(),
         * while allowing sibling nodes to prove controller presence and provide gyro/battery/LED/rumble.
         */
        fun isMotionSensorSubNode(device: InputDevice?): Boolean {
            if (device == null) return false
            val lower = device.name.lowercase()
            return lower.contains("motion sensor") ||
                lower.contains("accelerometer") ||
                lower.contains("gyroscope")
        }

        fun isTouchpadOnlySubNode(device: InputDevice?): Boolean {
            if (device == null) return false
            val lower = device.name.lowercase()
            if (!lower.contains("touchpad")) return false
            val sources = device.sources
            val hasGamepadOrDpad =
                (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (sources and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
            return !hasGamepadOrDpad && !hasGamepadHardwareKeys(device)
        }

        fun isSubDeviceOrSensorNode(device: InputDevice?): Boolean {
            if (device == null) return false
            return isInternalPhoneDevice(device) || isMotionSensorSubNode(device)
        }

        fun hasKnownControllerName(name: String?): Boolean {
            if (name.isNullOrBlank()) return false
            val lower = name.lowercase()
            return lower.contains("wireless controller") ||
                lower.contains("dualsense") ||
                lower.contains("dualshock") ||
                lower.contains("playstation") ||
                lower.contains("ps4") ||
                lower.contains("ps5") ||
                lower.contains("sony") ||
                lower.contains("xbox") ||
                lower.contains("nintendo") ||
                lower.contains("pro controller") ||
                lower.contains("joy-con") ||
                lower.contains("joycon") ||
                lower.contains("gamepad") ||
                lower.contains("joystick") ||
                lower.contains("8bitdo") ||
                lower.contains("gamesir") ||
                lower.contains("backbone") ||
                lower.contains("kishi") ||
                lower.contains("razer") ||
                lower.contains("steelseries") ||
                lower.contains("moga") ||
                lower.contains("controller")
        }

        fun hasGamepadHardwareKeys(device: InputDevice?): Boolean {
            if (device == null) return false
            return try {
                val keys = device.hasKeys(
                    KeyEvent.KEYCODE_BUTTON_A,
                    KeyEvent.KEYCODE_BUTTON_B,
                    KeyEvent.KEYCODE_BUTTON_X,
                    KeyEvent.KEYCODE_BUTTON_Y,
                    KeyEvent.KEYCODE_BUTTON_L1,
                    KeyEvent.KEYCODE_BUTTON_R1,
                    KeyEvent.KEYCODE_BUTTON_START,
                    KeyEvent.KEYCODE_BUTTON_SELECT,
                    KeyEvent.KEYCODE_BUTTON_MODE,
                    KeyEvent.KEYCODE_BUTTON_C,
                    KeyEvent.KEYCODE_BUTTON_Z
                )
                keys.count { it } >= 2
            } catch (_: Exception) {
                false
            }
        }

        fun hasDualStickOrHatAxes(device: InputDevice?): Boolean {
            if (device == null) return false
            return try {
                val hasXY = device.getMotionRange(MotionEvent.AXIS_X) != null &&
                    device.getMotionRange(MotionEvent.AXIS_Y) != null
                val hasRightOrHat = device.getMotionRange(MotionEvent.AXIS_Z) != null ||
                    device.getMotionRange(MotionEvent.AXIS_RZ) != null ||
                    device.getMotionRange(MotionEvent.AXIS_RX) != null ||
                    device.getMotionRange(MotionEvent.AXIS_HAT_X) != null
                hasXY && hasRightOrHat
            } catch (_: Exception) {
                false
            }
        }

        fun isActualGamepadInputDevice(device: InputDevice?): Boolean {
            if (device == null || isInternalPhoneDevice(device)) return false
            if (isMotionSensorSubNode(device) || isTouchpadOnlySubNode(device)) return false
            val sources = device.sources
            val hasGamepadOrJoystickSource =
                (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
                    (sources and InputDevice.SOURCE_CLASS_JOYSTICK) != 0 ||
                    device.supportsSource(InputDevice.SOURCE_GAMEPAD) ||
                    device.supportsSource(InputDevice.SOURCE_JOYSTICK)
            if (hasGamepadOrJoystickSource) return true
            if (device.vendorId in KNOWN_GAMEPAD_VENDORS) return true
            if (hasKnownControllerName(device.name)) return true
            if (hasGamepadHardwareKeys(device)) return true
            if (hasDualStickOrHatAxes(device)) return true
            return false
        }

        fun isCompanionControllerNode(device: InputDevice?): Boolean {
            if (device == null || isInternalPhoneDevice(device)) return false
            return device.vendorId in KNOWN_GAMEPAD_VENDORS || hasKnownControllerName(device.name)
        }

        fun normalizeControllerDisplayName(device: InputDevice): String {
            val rawName = device.name.trim()
            val cleaned = rawName
                .replace(Regex("(?i)\\s*motion sensors$"), "")
                .replace(Regex("(?i)\\s*touchpad$"), "")
                .trim()
            if (device.vendorId == 0x054C || hasKnownControllerName(cleaned) && cleaned.equals("Wireless Controller", ignoreCase = true)) {
                return when (device.productId) {
                    0x0CE6 -> "PS5 DualSense Wireless Controller"
                    0x0DF2 -> "PS5 DualSense Edge Controller"
                    0x05C4, 0x09CC -> "PS4 DualShock 4 Controller"
                    else -> if (cleaned.equals("Wireless Controller", ignoreCase = true)) {
                        "PlayStation Wireless Controller"
                    } else {
                        cleaned.ifBlank { "PlayStation Controller" }
                    }
                }
            }
            return cleaned.ifBlank { rawName.ifBlank { "Wireless Gamepad" } }
        }

        fun findConnectedUsbGamepadDevice(context: Context): android.hardware.usb.UsbDevice? {
            if (simulatedWiredOverrideForTests == false) return null
            return try {
                val usbManager = context.getSystemService(Context.USB_SERVICE) as? android.hardware.usb.UsbManager
                val usbDevices = usbManager?.deviceList?.values ?: return null
                usbDevices.firstOrNull { usbDev ->
                    usbDev.vendorId in KNOWN_GAMEPAD_VENDORS ||
                        hasKnownControllerName(usbDev.productName)
                } ?: usbDevices.firstOrNull { usbDev ->
                    if (usbDev.deviceClass == android.hardware.usb.UsbConstants.USB_CLASS_HID) {
                        true
                    } else {
                        var hasHid = false
                        for (i in 0 until usbDev.interfaceCount) {
                            if (usbDev.getInterface(i).interfaceClass == android.hardware.usb.UsbConstants.USB_CLASS_HID) {
                                hasHid = true
                                break
                            }
                        }
                        hasHid
                    }
                }
            } catch (_: Exception) {
                null
            }
        }

        fun resolveUsbControllerName(usbDev: android.hardware.usb.UsbDevice): String {
            val prodName = try { usbDev.productName?.trim() } catch (_: Exception) { null }
            if (usbDev.vendorId == 0x054C) {
                return when (usbDev.productId) {
                    0x0CE6 -> "PS5 DualSense Wireless Controller"
                    0x0DF2 -> "PS5 DualSense Edge Controller"
                    0x05C4, 0x09CC -> "PS4 DualShock 4 Controller"
                    else -> prodName?.takeIf { it.isNotBlank() } ?: "PlayStation USB Controller"
                }
            }
            return when (usbDev.vendorId) {
                0x045E -> prodName?.takeIf { it.isNotBlank() } ?: "Xbox USB Controller"
                0x057E -> prodName?.takeIf { it.isNotBlank() } ?: "Nintendo Switch Pro Controller"
                else -> prodName?.takeIf { it.isNotBlank() } ?: "USB Gamepad Controller"
            }
        }

        data class ProcBusControllerEntry(
            val name: String,
            val isWiredUsb: Boolean
        )

        fun findProcBusControllerInfo(): ProcBusControllerEntry? {
            if (simulatedWiredOverrideForTests == false) return null
            return try {
                val procFile = java.io.File("/proc/bus/input/devices")
                if (!procFile.exists() || !procFile.canRead()) return null
                val blocks = procFile.readText().split("\n\n")
                for (block in blocks) {
                    val lower = block.lowercase()
                    val isUsb = lower.contains("bus=0003")
                    val isBt = lower.contains("bus=0005")
                    if (!isUsb && !isBt) continue
                    val matchesKnownVid = KNOWN_GAMEPAD_VENDORS.any { vid ->
                        lower.contains(String.format("vendor=%04x", vid))
                    }
                    val nameLine = block.lines().firstOrNull { it.startsWith("N: Name=") }
                    val extractedName = nameLine?.substringAfter("N: Name=")?.trim()?.removeSurrounding("\"") ?: ""
                    if (matchesKnownVid || hasKnownControllerName(extractedName)) {
                        if (extractedName.lowercase().contains("motion sensor")) continue
                        val displayName = if (lower.contains("vendor=054c") && extractedName.equals("Wireless Controller", ignoreCase = true)) {
                            "PlayStation Wireless Controller"
                        } else {
                            extractedName.ifBlank { "Wireless Controller" }
                        }
                        return ProcBusControllerEntry(name = displayName, isWiredUsb = isUsb)
                    }
                }
                null
            } catch (_: Exception) {
                null
            }
        }

        fun isDeviceWiredUsb(context: Context, device: InputDevice?): Boolean {
            simulatedWiredOverrideForTests?.let { return it }
            if (device != null && isInternalPhoneDevice(device)) return false
            try {
                val usbManager = context.getSystemService(Context.USB_SERVICE) as? android.hardware.usb.UsbManager
                val usbDevices = usbManager?.deviceList?.values
                if (!usbDevices.isNullOrEmpty()) {
                    val matchesVidPid = usbDevices.any { usbDev ->
                        (device != null && device.vendorId > 0 && usbDev.vendorId == device.vendorId &&
                            (device.productId <= 0 || usbDev.productId == device.productId)) ||
                            (usbDev.vendorId in KNOWN_GAMEPAD_VENDORS)
                    }
                    if (matchesVidPid) return true

                    val hasUsbHid = usbDevices.any { usbDev ->
                        if (usbDev.deviceClass == android.hardware.usb.UsbConstants.USB_CLASS_HID) {
                            true
                        } else {
                            var hasHidInterface = false
                            for (i in 0 until usbDev.interfaceCount) {
                                val intf = usbDev.getInterface(i)
                                if (intf.interfaceClass == android.hardware.usb.UsbConstants.USB_CLASS_HID) {
                                    hasHidInterface = true
                                    break
                                }
                            }
                            hasHidInterface
                        }
                    }
                    if (hasUsbHid) return true
                }
            } catch (_: Exception) {}

            // Fallback: check /proc/bus/input/devices for Bus=0003 (USB) vs Bus=0005 (Bluetooth)
            try {
                val procFile = java.io.File("/proc/bus/input/devices")
                if (procFile.exists() && procFile.canRead()) {
                    val blocks = procFile.readText().split("\n\n")
                    val devNameLower = device?.name?.lowercase() ?: ""
                    for (block in blocks) {
                        val lower = block.lowercase()
                        if (lower.contains("bus=0003")) {
                            if ((devNameLower.isNotBlank() && lower.contains(devNameLower)) ||
                                (device != null && device.vendorId > 0 && lower.contains(String.format("vendor=%04x", device.vendorId)))
                            ) {
                                return true
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            return false
        }

        /**
         * Returns true when ANY physical controller (Bluetooth / Wireless OR Wired USB) is connected.
         * Honors `simulatedWiredOverrideForTests` when set by unit tests.
         */
        fun isAnyControllerConnected(context: Context): Boolean {
            simulatedWiredOverrideForTests?.let { return it }
            if (_sharedLiveState.value.controllerId >= 0) return true
            return try {
                val ids = InputDevice.getDeviceIds()
                for (id in ids) {
                    val dev = InputDevice.getDevice(id) ?: continue
                    if (isActualGamepadInputDevice(dev) || isCompanionControllerNode(dev)) {
                        return true
                    }
                }
                if (findConnectedUsbGamepadDevice(context) != null) return true
                if (findProcBusControllerInfo() != null) return true
                false
            } catch (_: Exception) {
                false
            }
        }

        fun isWiredControllerConnected(context: Context): Boolean {
            return isAnyControllerConnected(context)
        }

        fun isPhysicalGamepadPresent(): Boolean {
            if (_sharedLiveState.value.controllerId >= 0) return true
            return try {
                val ids = InputDevice.getDeviceIds()
                ids.any { id ->
                    val dev = InputDevice.getDevice(id)
                    isActualGamepadInputDevice(dev) || isCompanionControllerNode(dev)
                } || findProcBusControllerInfo() != null
            } catch (_: Exception) {
                false
            }
        }

        fun notifyHomeLoginButtonPressed() {
            _homeLoginSignal.value = System.currentTimeMillis()
        }

        fun updateSharedState(newState: ControllerHealthState) {
            _sharedLiveState.value = newState
        }

        /**
         * Disables phone audio routing through the wired USB controller's speaker/headphone interface
         * and routes audio back to the phone's built-in speakers (or connected Bluetooth headset).
         */
        fun applyControllerSpeakerAudioRouting(context: Context, disableControllerSpeaker: Boolean) {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
                if (disableControllerSpeaker) {
                    try {
                        audioManager.setParameters("usb_audio_out_disabled=true;routing=2")
                    } catch (_: Exception) {}
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val outputs = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                        val hasUsbAudioOut = outputs.any {
                            it.type == android.media.AudioDeviceInfo.TYPE_USB_DEVICE ||
                                it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET ||
                                it.type == android.media.AudioDeviceInfo.TYPE_USB_ACCESSORY
                        }
                        if (hasUsbAudioOut) {
                            val preferredNonUsb = outputs.firstOrNull {
                                it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                                    it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET
                            } ?: outputs.firstOrNull {
                                it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && preferredNonUsb != null) {
                                try {
                                    audioManager.setCommunicationDevice(preferredNonUsb)
                                } catch (_: Exception) {}
                            }
                            @Suppress("DEPRECATION")
                            audioManager.isSpeakerphoneOn = true
                        }
                    }
                } else {
                    try {
                        audioManager.setParameters("usb_audio_out_disabled=false")
                    } catch (_: Exception) {}
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        try {
                            audioManager.clearCommunicationDevice()
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }
    }

    init {
        scanActiveController()
    }

    private fun getSiblingNodesForDevice(device: InputDevice): List<InputDevice> {
        val result = mutableListOf(device)
        try {
            val baseName = normalizeControllerDisplayName(device).lowercase()
            for (id in InputDevice.getDeviceIds()) {
                if (id == device.id) continue
                val other = InputDevice.getDevice(id) ?: continue
                if (isInternalPhoneDevice(other)) continue
                val sameVendor = device.vendorId > 0 && other.vendorId == device.vendorId
                val sameBaseName = normalizeControllerDisplayName(other).lowercase() == baseName
                if (sameVendor || sameBaseName) {
                    result.add(other)
                }
            }
        } catch (_: Exception) {}
        return result
    }

    private fun ensureControllerBoundFromEvent(dev: InputDevice?, fallbackDeviceId: Int = -1) {
        val nowMs = System.currentTimeMillis()
        lastLiveControllerEventMs = nowMs
        if (dev != null && !isInternalPhoneDevice(dev)) {
            val bestDev = if (isMotionSensorSubNode(dev) || isTouchpadOnlySubNode(dev)) {
                getSiblingNodesForDevice(dev).firstOrNull { isActualGamepadInputDevice(it) } ?: dev
            } else {
                dev
            }
            if (_state.value.controllerId != bestDev.id || _state.value.controllerId < 0) {
                setupController(bestDev)
            }
            return
        }
        if (_state.value.controllerId < 0 && simulatedWiredOverrideForTests == null) {
            scanActiveController()
            if (_state.value.controllerId < 0 && (dev != null || fallbackDeviceId >= 0)) {
                val resolvedName = dev?.name?.takeIf { it.isNotBlank() && !it.equals("Virtual", ignoreCase = true) }
                    ?: "PlayStation Wireless Controller"
                val isWired = isDeviceWiredUsb(context, dev)
                val updated = _state.value.copy(
                    activeControllerName = resolvedName,
                    controllerId = (dev?.id ?: fallbackDeviceId).coerceAtLeast(1),
                    isRumbleSupported = true,
                    rumbleStatus = "Ready",
                    isWiredConnection = isWired,
                    connectionTypeLabel = if (isWired) "Wired USB" else "Bluetooth / Wireless"
                )
                _state.value = updated
                updateSharedState(updated)
            }
        }
    }

    fun scanActiveController() {
        val deviceIds = try { InputDevice.getDeviceIds() } catch (_: Exception) { IntArray(0) }
        val primaryCandidates = mutableListOf<InputDevice>()
        val companionCandidates = mutableListOf<InputDevice>()
        for (id in deviceIds) {
            val dev = InputDevice.getDevice(id) ?: continue
            if (isActualGamepadInputDevice(dev)) {
                primaryCandidates.add(dev)
            } else if (isCompanionControllerNode(dev)) {
                companionCandidates.add(dev)
            }
        }

        val candidates = if (primaryCandidates.isNotEmpty()) primaryCandidates else companionCandidates

        // 1. Prefer a wired or Sony/Xbox/Switch device that explicitly has SOURCE_GAMEPAD
        val targetDevice = candidates.firstOrNull { dev ->
            (dev.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD &&
                isDeviceWiredUsb(context, dev)
        } ?: candidates.firstOrNull { dev ->
            (dev.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
        } ?: candidates.firstOrNull { dev ->
            dev.vendorId in KNOWN_GAMEPAD_VENDORS
        } ?: candidates.firstOrNull()

        if (targetDevice != null) {
            setupController(targetDevice)
            return
        }

        // 2. Hardware fallback: check UsbManager for wired USB controllers
        val usbGamepad = findConnectedUsbGamepadDevice(context)
        if (usbGamepad != null) {
            val usbName = resolveUsbControllerName(usbGamepad)
            val usbId = usbGamepad.deviceId.coerceAtLeast(1)
            val updated = _state.value.copy(
                activeControllerName = usbName,
                controllerId = usbId,
                isRumbleSupported = true,
                rumbleStatus = "Ready",
                isWiredConnection = true,
                connectionTypeLabel = "Wired USB"
            )
            _state.value = updated
            updateSharedState(updated)
            return
        }

        // 3. Kernel procfs fallback: check /proc/bus/input/devices for Bluetooth (Bus=0005) or USB (Bus=0003) controllers
        val procEntry = findProcBusControllerInfo()
        if (procEntry != null) {
            val updated = _state.value.copy(
                activeControllerName = procEntry.name,
                controllerId = _state.value.controllerId.takeIf { it >= 0 } ?: 1,
                isRumbleSupported = true,
                rumbleStatus = "Ready",
                isWiredConnection = procEntry.isWiredUsb,
                connectionTypeLabel = if (procEntry.isWiredUsb) "Wired USB" else "Bluetooth / Wireless"
            )
            _state.value = updated
            updateSharedState(updated)
            return
        }

        // 4. Preserve active live-input controller if events were recently received and not forced off by tests
        if (simulatedWiredOverrideForTests != false &&
            _state.value.controllerId >= 0 &&
            (System.currentTimeMillis() - lastLiveControllerEventMs) < 120_000L
        ) {
            return
        }

        val updated = ControllerHealthState(
            activeControllerName = "No Gamepad Connected",
            controllerId = -1,
            pollingRateHz = 0,
            peakPollingRateHz = 0,
            batteryInfo = ControllerBatteryInfo(
                hasBattery = false,
                batteryCapacity = -1f,
                batteryStatus = "Disconnected",
                isLowBattery = false,
                lowBatteryAlert = null
            ),
            gyroReading = GyroReading(isSupported = false),
            isLedSupported = false,
            isRumbleSupported = false,
            rumbleStatus = "Disconnected",
            isWiredConnection = false,
            connectionTypeLabel = "Disconnected"
        )
        _state.value = updated
        updateSharedState(updated)
    }

    private fun setupController(device: InputDevice) {
        val siblings = getSiblingNodesForDevice(device)
        val hasVibe = siblings.any { node ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                node.vibratorManager?.defaultVibrator?.hasVibrator() == true ||
                    node.vibratorManager?.vibratorIds?.isNotEmpty() == true ||
                    node.vibrator.hasVibrator()
            } else {
                @Suppress("DEPRECATION")
                node.vibrator.hasVibrator()
            }
        }

        val battery = siblings.firstNotNullOfOrNull { node ->
            queryBattery(node).takeIf { it.hasBattery }
        } ?: queryBattery(device)

        val hasLed = siblings.any { node -> checkLedSupport(node) }
        val isWired = siblings.any { node -> isDeviceWiredUsb(context, node) }
        setupGyroWithSiblings(siblings)

        if (isWired) {
            try {
                val cfg = com.example.service.ControllerOverlayService.loadConfig(context)
                applyControllerSpeakerAudioRouting(context, cfg.disableControllerSpeakerAudio)
            } catch (_: Exception) {}
        }

        val displayName = normalizeControllerDisplayName(device)
        val updated = _state.value.copy(
            activeControllerName = displayName,
            controllerId = device.id,
            isRumbleSupported = hasVibe,
            rumbleStatus = if (hasVibe) "Ready" else _state.value.rumbleStatus,
            batteryInfo = battery,
            isLedSupported = hasLed,
            isWiredConnection = isWired,
            connectionTypeLabel = if (isWired) "Wired USB" else "Bluetooth / Wireless"
        )
        _state.value = updated
        updateSharedState(updated)
        Log.i(TAG, "Attached to controller: $displayName (ID ${device.id}, wired=$isWired)")
    }

    private var lastBatteryQueryMs: Long = 0L

    private fun queryBattery(device: InputDevice): ControllerBatteryInfo {
        lastBatteryQueryMs = System.currentTimeMillis()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val batteryState = device.batteryState
                if (batteryState.isPresent) {
                    val capacity = batteryState.capacity // 0.0 to 1.0, or Float.NaN
                    val status = batteryState.status
                    val statusLabel = when (status) {
                        android.hardware.BatteryState.STATUS_CHARGING -> "Charging"
                        android.hardware.BatteryState.STATUS_FULL -> "Full"
                        android.hardware.BatteryState.STATUS_DISCHARGING -> "Discharging"
                        android.hardware.BatteryState.STATUS_NOT_CHARGING -> "Connected"
                        else -> null
                    }

                    val hasValidCapacity = !capacity.isNaN() && capacity in 0.0f..1.0f &&
                        !(capacity == 0.0f && status == android.hardware.BatteryState.STATUS_UNKNOWN)

                    if (hasValidCapacity) {
                        val capacityPct = (capacity * 100f).roundToInt().coerceIn(0, 100)
                        val isLow = capacityPct in 1..15 && status != android.hardware.BatteryState.STATUS_CHARGING
                        val alert = if (isLow) {
                            "Low Controller Battery ($capacityPct%). Recharge soon to avoid input lag or disconnects."
                        } else null
                        val statusText = if (statusLabel != null) "$capacityPct% • $statusLabel" else "$capacityPct%"
                        return ControllerBatteryInfo(
                            hasBattery = true,
                            batteryCapacity = capacityPct / 100f,
                            batteryStatus = statusText,
                            isLowBattery = isLow,
                            lowBatteryAlert = alert
                        )
                    } else if (status == android.hardware.BatteryState.STATUS_FULL) {
                        return ControllerBatteryInfo(
                            hasBattery = true,
                            batteryCapacity = 1.0f,
                            batteryStatus = "100% • Full",
                            isLowBattery = false,
                            lowBatteryAlert = null
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "BatteryState query failed: ${e.message}")
            }
        }

        // Check dedicated controller power_supply nodes only (never generic phone battery nodes)
        try {
            val powerSupplyDir = java.io.File("/sys/class/power_supply")
            val supplies = powerSupplyDir.listFiles() ?: emptyArray()
            for (supply in supplies) {
                val name = supply.name.lowercase()
                if (name.contains("sony") || name.contains("dualshock") ||
                    name.contains("dualsense") || name.contains("xbox") ||
                    name.contains("joycon") || name.contains("pro_controller")
                ) {
                    val capFile = java.io.File(supply, "capacity")
                    if (capFile.exists() && capFile.canRead()) {
                        val pct = capFile.readText().trim().toIntOrNull()
                        if (pct != null && pct in 0..100) {
                            val isLow = pct in 1..15
                            val alert = if (isLow) {
                                "Low Controller Battery ($pct%). Recharge soon to avoid input lag or disconnects."
                            } else null
                            return ControllerBatteryInfo(
                                hasBattery = true,
                                batteryCapacity = pct / 100f,
                                batteryStatus = "$pct%",
                                isLowBattery = isLow,
                                lowBatteryAlert = alert
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return ControllerBatteryInfo(
            hasBattery = false,
            batteryCapacity = -1f,
            batteryStatus = "Battery Level Not Reported by Controller",
            isLowBattery = false,
            lowBatteryAlert = null
        )
    }

    private fun refreshBatteryIfNeeded(device: InputDevice?) {
        if (device == null) return
        val now = System.currentTimeMillis()
        if (now - lastBatteryQueryMs >= 10_000L) {
            val updatedBattery = queryBattery(device)
            if (updatedBattery != _state.value.batteryInfo) {
                _state.value = _state.value.copy(batteryInfo = updatedBattery)
            }
        }
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

    // Instance sensor tracking
    private var lastAccelX = 0f
    private var lastAccelY = 0f
    private var hasLastAccel = false
    private var lastGyroEmitMs = 0L

    private fun setupGyroWithSiblings(siblings: List<InputDevice>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (node in siblings) {
                try {
                    val sensorManager = node.sensorManager
                    val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
                    val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                    if (gyro != null || accel != null) {
                        controllerSensorManager = sensorManager
                        controllerGyroSensor = gyro
                        if (gyro != null) {
                            sensorManager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_UI)
                        }
                        if (accel != null) {
                            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)
                        }
                        _state.value = _state.value.copy(
                            gyroReading = GyroReading(isSupported = true)
                        )
                        Log.i(TAG, "Controller motion sensors registered on ${node.name}")
                        return
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "SensorManager setup failed on ${node.name}: ${e.message}")
                }
            }
        }
        // Do NOT fall back to phone's built-in gyroscope/accelerometer; phone tilt must never move controller sticks.
        _state.value = _state.value.copy(
            gyroReading = GyroReading(isSupported = false)
        )
    }

    /**
     * Process motion events from gamepad thumbsticks, D-Pad hat switches, and analog triggers.
     */
    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val dev = event.device
        if (dev != null && isSubDeviceOrSensorNode(dev)) {
            // Even if this is the Motion Sensors sub-node of a PlayStation controller, ensure the parent controller is bound!
            if (isMotionSensorSubNode(dev) && _state.value.controllerId < 0) {
                ensureControllerBoundFromEvent(dev, event.deviceId)
            }
            return false
        }
        val src = event.source
        val isJoystickOrGamepad =
            (src and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
                (src and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (src and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD ||
                (src and InputDevice.SOURCE_CLASS_JOYSTICK) != 0
        if (!isJoystickOrGamepad) {
            return false
        }

        val nowMs = System.currentTimeMillis()
        measurePollingRate()
        ensureControllerBoundFromEvent(dev, event.deviceId)
        refreshBatteryIfNeeded(dev)
        lastMotionStickUpdateMs = nowMs
        lastInGameActionMs = nowMs

        val devNameLower = (dev?.name ?: _state.value.activeControllerName).lowercase()
        val isSonyController = dev?.vendorId == 0x054C ||
            devNameLower.contains("dualsense") ||
            devNameLower.contains("dualshock") ||
            devNameLower.contains("playstation") ||
            devNameLower.contains("wireless controller") ||
            devNameLower.contains("sony")

        // Left Thumbstick (AXIS_X, AXIS_Y)
        val lx = event.getAxisValue(MotionEvent.AXIS_X)
        val ly = event.getAxisValue(MotionEvent.AXIS_Y)

        // Right Thumbstick & Analog Triggers across both Wired USB and Bluetooth HID descriptors:
        val axisZ = event.getAxisValue(MotionEvent.AXIS_Z)
        val axisRz = event.getAxisValue(MotionEvent.AXIS_RZ)
        val rawRx = event.getAxisValue(MotionEvent.AXIS_RX)
        val rawRy = event.getAxisValue(MotionEvent.AXIS_RY)

        val primaryLt = event.getAxisValue(MotionEvent.AXIS_BRAKE)
            .coerceAtLeast(event.getAxisValue(MotionEvent.AXIS_LTRIGGER))
        val primaryRt = event.getAxisValue(MotionEvent.AXIS_GAS)
            .coerceAtLeast(event.getAxisValue(MotionEvent.AXIS_RTRIGGER))

        // On Sony USB/BT when AXIS_RX/AXIS_RY are analog triggers, they rest at -1.0f when unpressed.
        // On generic Bluetooth / Xbox profiles where Right Stick is mapped to AXIS_RX/AXIS_RY, axisZ/axisRz stay 0f.
        val rxRyLooksLikeRestingTriggers = isSonyController &&
            (rawRx < -0.85f || rawRy < -0.85f || _sharedLiveState.value.pressedButtons.contains("L2") || _sharedLiveState.value.pressedButtons.contains("R2"))

        val rx = if (kotlin.math.abs(axisZ) > 0.015f || kotlin.math.abs(axisRz) > 0.015f || rxRyLooksLikeRestingTriggers) {
            axisZ
        } else {
            rawRx
        }
        val ry = if (kotlin.math.abs(axisZ) > 0.015f || kotlin.math.abs(axisRz) > 0.015f || rxRyLooksLikeRestingTriggers) {
            axisRz
        } else {
            rawRy
        }

        val sonyUsbLt = if (isSonyController && primaryLt < 0.02f && rawRx != 0f && kotlin.math.abs(axisZ) >= kotlin.math.abs(rawRx).coerceAtMost(0.01f)) {
            if (rawRx < -0.05f || _sharedLiveState.value.pressedButtons.contains("L2")) {
                ((rawRx + 1f) / 2f).coerceIn(0f, 1f)
            } else if (kotlin.math.abs(axisZ) > 0.02f) {
                rawRx.coerceIn(0f, 1f)
            } else 0f
        } else 0f
        val sonyUsbRt = if (isSonyController && primaryRt < 0.02f && rawRy != 0f && kotlin.math.abs(axisRz) >= kotlin.math.abs(rawRy).coerceAtMost(0.01f)) {
            if (rawRy < -0.05f || _sharedLiveState.value.pressedButtons.contains("R2")) {
                ((rawRy + 1f) / 2f).coerceIn(0f, 1f)
            } else if (kotlin.math.abs(axisRz) > 0.02f) {
                rawRy.coerceIn(0f, 1f)
            } else 0f
        } else 0f

        val rawLt = primaryLt.coerceAtLeast(sonyUsbLt)
        val rawRt = primaryRt.coerceAtLeast(sonyUsbRt)
        val lt = if (keyHeldTriggers.contains("L2")) maxOf(rawLt, 1f) else rawLt
        val rt = if (keyHeldTriggers.contains("R2")) maxOf(rawRt, 1f) else rawRt

        // D-Pad Hat Axes (AXIS_HAT_X, AXIS_HAT_Y)
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        if (hatX < -0.35f) hatHeldDpad.add("DPAD_LEFT") else hatHeldDpad.remove("DPAD_LEFT")
        if (hatX > 0.35f) hatHeldDpad.add("DPAD_RIGHT") else hatHeldDpad.remove("DPAD_RIGHT")
        if (hatY < -0.35f) hatHeldDpad.add("DPAD_UP") else hatHeldDpad.remove("DPAD_UP")
        if (hatY > 0.35f) hatHeldDpad.add("DPAD_DOWN") else hatHeldDpad.remove("DPAD_DOWN")

        // Whenever physical D-Pad Hat axes are neutral (0, 0), clear any synthetic ViewRootImpl D-Pad key
        // state — especially when the user is moving the left or right analog joystick!
        val stickActive = kotlin.math.abs(lx) > 0.12f || kotlin.math.abs(ly) > 0.12f ||
            kotlin.math.abs(rx) > 0.12f || kotlin.math.abs(ry) > 0.12f
        if (kotlin.math.abs(hatX) < 0.25f && (kotlin.math.abs(lx) < 0.25f || stickActive)) {
            keyHeldDpad.remove("DPAD_LEFT")
            keyHeldDpad.remove("DPAD_RIGHT")
            keyHeldDpadTimestampMs.remove("DPAD_LEFT")
            keyHeldDpadTimestampMs.remove("DPAD_RIGHT")
        }
        if (kotlin.math.abs(hatY) < 0.25f && (kotlin.math.abs(ly) < 0.25f || stickActive)) {
            keyHeldDpad.remove("DPAD_UP")
            keyHeldDpad.remove("DPAD_DOWN")
            keyHeldDpadTimestampMs.remove("DPAD_UP")
            keyHeldDpadTimestampMs.remove("DPAD_DOWN")
        }

        pulseHeldDpad.clear()
        val baseState = _sharedLiveState.value

        val prevLMag = hypot(baseState.leftStick.x, baseState.leftStick.y)
        val prevRMag = hypot(baseState.rightStick.x, baseState.rightStick.y)
        val newLMag = hypot(lx, ly)
        val newRMag = hypot(rx, ry)
        isStickDeceleratingTowardCenter =
            (newLMag in 0.01f..0.38f && newLMag < prevLMag) ||
                (newRMag in 0.01f..0.38f && newRMag < prevRMag)

        val dpadLeftActive = hatHeldDpad.contains("DPAD_LEFT") || keyHeldDpad.contains("DPAD_LEFT")
        val dpadRightActive = hatHeldDpad.contains("DPAD_RIGHT") || keyHeldDpad.contains("DPAD_RIGHT")
        val dpadUpActive = hatHeldDpad.contains("DPAD_UP") || keyHeldDpad.contains("DPAD_UP")
        val dpadDownActive = hatHeldDpad.contains("DPAD_DOWN") || keyHeldDpad.contains("DPAD_DOWN")
        val l2Active = lt > 0.25f || keyHeldTriggers.contains("L2")
        val r2Active = rt > 0.25f || keyHeldTriggers.contains("R2")

        val basePressed = baseState.pressedButtons
        val buttonsChanged =
            (basePressed.contains("DPAD_LEFT") != dpadLeftActive) ||
                (basePressed.contains("DPAD_RIGHT") != dpadRightActive) ||
                (basePressed.contains("DPAD_UP") != dpadUpActive) ||
                (basePressed.contains("DPAD_DOWN") != dpadDownActive) ||
                (basePressed.contains("L2") != l2Active) ||
                (basePressed.contains("R2") != r2Active)

        val snappedToZero =
            (kotlin.math.abs(lx) < 0.05f && kotlin.math.abs(baseState.leftStick.x) >= 0.05f) ||
                (kotlin.math.abs(ly) < 0.05f && kotlin.math.abs(baseState.leftStick.y) >= 0.05f) ||
                (kotlin.math.abs(rx) < 0.05f && kotlin.math.abs(baseState.rightStick.x) >= 0.05f) ||
                (kotlin.math.abs(ry) < 0.05f && kotlin.math.abs(baseState.rightStick.y) >= 0.05f) ||
                (lt < 0.03f && baseState.triggers.leftTrigger >= 0.03f) ||
                (rt < 0.03f && baseState.triggers.rightTrigger >= 0.03f)

        if (!buttonsChanged && !snappedToZero) {
            val maxDelta = maxOf(
                kotlin.math.abs(lx - baseState.leftStick.x),
                kotlin.math.abs(ly - baseState.leftStick.y),
                kotlin.math.abs(rx - baseState.rightStick.x),
                kotlin.math.abs(ry - baseState.rightStick.y),
                kotlin.math.abs(lt - baseState.triggers.leftTrigger),
                kotlin.math.abs(rt - baseState.triggers.rightTrigger)
            )
            // Filter out sub-pixel 500Hz USB-C ADC noise (< 0.008f) and cap micro-interpolation to 140Hz (7ms)
            if (maxDelta < 0.008f) {
                return true
            }
            if (maxDelta < 0.06f && (nowMs - lastMotionStateEmitMs) < 7L) {
                return true
            }
        }

        lastMotionStateEmitMs = nowMs
        val pressed = if (buttonsChanged) {
            basePressed.toMutableSet().apply {
                if (dpadLeftActive) add("DPAD_LEFT") else remove("DPAD_LEFT")
                if (dpadRightActive) add("DPAD_RIGHT") else remove("DPAD_RIGHT")
                if (dpadUpActive) add("DPAD_UP") else remove("DPAD_UP")
                if (dpadDownActive) add("DPAD_DOWN") else remove("DPAD_DOWN")
                if (l2Active) add("L2") else remove("L2")
                if (r2Active) add("R2") else remove("R2")
            }
        } else {
            basePressed
        }

        val leftStickState = calculateStickState(lx, ly, baseState.leftStick)
        val rightStickState = calculateStickState(rx, ry, baseState.rightStick)

        val updated = _state.value.copy(
            leftStick = leftStickState,
            rightStick = rightStickState,
            triggers = TriggerState(
                leftTrigger = lt.coerceIn(0f, 1f),
                rightTrigger = rt.coerceIn(0f, 1f)
            ),
            pressedButtons = pressed
        )
        _state.value = updated
        updateSharedState(updated)
        return true
    }

    /**
     * Process key events from gamepad buttons, D-Pad, raw HID scanCodes, and directional stick translations.
     */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val dev = event.device
        if (dev != null && isSubDeviceOrSensorNode(dev)) {
            return false
        }
        val isFallbackEvent = (event.flags and KeyEvent.FLAG_FALLBACK) != 0
        // If a real external controller device emits even a FLAG_FALLBACK event while controllerId < 0,
        // immediately bind the controller so the UI recognizes it as connected!
        if ((dev != null && !isInternalPhoneDevice(dev) || event.deviceId >= 0) && _state.value.controllerId < 0) {
            ensureControllerBoundFromEvent(dev, event.deviceId)
        }
        // When raw 120Hz MotionEvents are actively streaming (within 350ms), reject synthetic fallback KeyEvents
        // so moving the analog thumbstick never falsely illuminates the D-Pad cross.
        if (isFallbackEvent && (System.currentTimeMillis() - lastMotionStickUpdateMs) < 350L) {
            return false
        }
        val keyCode = event.keyCode
        val scanCode = event.scanCode

        if (isFallbackEvent && !isAppInForeground) {
            val dir = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP -> "DPAD_UP"
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN -> "DPAD_DOWN"
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT -> "DPAD_LEFT"
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> "DPAD_RIGHT"
                else -> null
            }
            if (dir != null) {
                val nowMs = System.currentTimeMillis()
                if (event.action == KeyEvent.ACTION_DOWN) {
                    pulseHeldDpad[dir] = nowMs + 240L
                } else if (event.action == KeyEvent.ACTION_UP) {
                    pulseHeldDpad.remove(dir)
                }
                val sx = (if (pulseHeldDpad.containsKey("DPAD_RIGHT")) 1f else 0f) -
                    (if (pulseHeldDpad.containsKey("DPAD_LEFT")) 1f else 0f)
                val sy = (if (pulseHeldDpad.containsKey("DPAD_DOWN")) 1f else 0f) -
                    (if (pulseHeldDpad.containsKey("DPAD_UP")) 1f else 0f)
                val norm = hypot(sx, sy)
                val finalX = if (norm > 1f) sx / norm else sx
                val finalY = if (norm > 1f) sy / norm else sy
                val baseState = _sharedLiveState.value
                val updated = _state.value.copy(
                    leftStick = calculateStickState(finalX, finalY, baseState.leftStick)
                )
                _state.value = updated
                updateSharedState(updated)
                return true
            }
            return false
        }

        val isGamepadSource = dev != null &&
            ((dev.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (dev.sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
                (dev.sources and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD ||
                isActualGamepadInputDevice(dev) ||
                isCompanionControllerNode(dev))

        // Reject volume keys, power key, and phone navigation bar keys when not from a gamepad device
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == KeyEvent.KEYCODE_VOLUME_MUTE ||
            keyCode == KeyEvent.KEYCODE_POWER ||
            keyCode == KeyEvent.KEYCODE_SLEEP ||
            keyCode == KeyEvent.KEYCODE_WAKEUP
        ) {
            return false
        }

        val devNameLower = (dev?.name ?: _state.value.activeControllerName).lowercase()
        val isSonyController = dev?.vendorId == 0x054C ||
            devNameLower.contains("dualsense") ||
            devNameLower.contains("dualshock") ||
            devNameLower.contains("playstation") ||
            devNameLower.contains("wireless controller") ||
            devNameLower.contains("sony")

        val scanMapped = if (isGamepadSource || keyCode == KeyEvent.KEYCODE_UNKNOWN || KeyEvent.isGamepadButton(keyCode)) {
            mapRawScanCode(scanCode, isSonyController)
        } else {
            null
        }

        val isDpadKey = keyCode == KeyEvent.KEYCODE_DPAD_UP ||
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
            keyCode == KeyEvent.KEYCODE_DPAD_UP_LEFT ||
            keyCode == KeyEvent.KEYCODE_DPAD_UP_RIGHT ||
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN_LEFT ||
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN_RIGHT ||
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            (scanMapped != null && scanMapped.startsWith("DPAD_"))

        val isExplicitGamepadKey = KeyEvent.isGamepadButton(keyCode) ||
            isDpadKey ||
            scanMapped != null ||
            (isGamepadSource && (keyCode == KeyEvent.KEYCODE_BUTTON_MODE || keyCode == KeyEvent.KEYCODE_HOME || keyCode == KeyEvent.KEYCODE_BACK))

        if (isExplicitGamepadKey) {
            val nowMs = System.currentTimeMillis()
            val baseState = _sharedLiveState.value

            // Guard against synthetic joystick-to-DPAD KeyEvents when raw MotionEvents are actively streaming:
            val isDirectionalDpadKey = isDpadKey && keyCode != KeyEvent.KEYCODE_DPAD_CENTER
            if (isDirectionalDpadKey && isAppInForeground) {
                val stickDeflected = hypot(baseState.leftStick.x, baseState.leftStick.y) > 0.14f ||
                    hypot(baseState.rightStick.x, baseState.rightStick.y) > 0.14f
                val motionStickActive = (nowMs - lastMotionStickUpdateMs) < 220L && stickDeflected
                if (motionStickActive && hatHeldDpad.isEmpty()) {
                    return false
                }
                if (dev != null && scanCode == 0 && (nowMs - lastMotionStickUpdateMs) < 350L) {
                    return false
                }
            }

            measurePollingRate()
            ensureControllerBoundFromEvent(dev, event.deviceId)

            var mappedButtons = normalizeGamepadKeyCodes(
                keyCode = keyCode,
                scanCode = if (scanMapped != null) scanCode else 0,
                isSonyController = isSonyController
            ).filter { it in VALID_OVERLAY_BUTTONS }
            if (mappedButtons.isEmpty()) return false

            // Per-scanCode button lock: if a physical scanCode (> 0) is already active,
            // prevent a companion/fallback event with the same scanCode from mapping to a second button.
            if (scanCode > 0 && mappedButtons.size == 1 && !mappedButtons.first().startsWith("DPAD_")) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val existingForScan = activeScanCodeToButton[scanCode]
                    if (existingForScan != null) {
                        mappedButtons = listOf(existingForScan)
                    } else {
                        activeScanCodeToButton[scanCode] = mappedButtons.first()
                    }
                } else if (event.action == KeyEvent.ACTION_UP) {
                    val lockedForScan = activeScanCodeToButton.remove(scanCode)
                    if (lockedForScan != null) {
                        mappedButtons = listOf(lockedForScan)
                    }
                }
            }

            var primaryBtnName = mappedButtons.first()
            val pressed = baseState.pressedButtons.toMutableSet()
            val isDpadAction = mappedButtons.any { it.startsWith("DPAD_") }

            if (!isDpadAction) {
                lastButtonActivityMs = nowMs
            }

            if (isDpadAction && pulseHeldDpad.isNotEmpty()) {
                pulseHeldDpad.keys.forEach { dir ->
                    if (!keyHeldDpad.contains(dir) && !hatHeldDpad.contains(dir)) {
                        pressed.remove(dir)
                    }
                }
                pulseHeldDpad.clear()
            }

            // Outside GMAX Gaming Hub, if scanCode == 0 on a directional key, Android synthesized it from the
            // left analog stick rather than the physical D-Pad cross! Animate leftStick without lighting up D-Pad buttons.
            val isOutOfAppSyntheticStickKey = !isAppInForeground && isDirectionalDpadKey && scanCode == 0
            if (isOutOfAppSyntheticStickKey) {
                for (btn in mappedButtons) {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        pulseHeldDpad[btn] = nowMs + 240L
                    } else if (event.action == KeyEvent.ACTION_UP) {
                        pulseHeldDpad.remove(btn)
                    }
                }
                val sx = (if (pulseHeldDpad.containsKey("DPAD_RIGHT")) 1f else 0f) -
                    (if (pulseHeldDpad.containsKey("DPAD_LEFT")) 1f else 0f)
                val sy = (if (pulseHeldDpad.containsKey("DPAD_DOWN")) 1f else 0f) -
                    (if (pulseHeldDpad.containsKey("DPAD_UP")) 1f else 0f)
                val norm = hypot(sx, sy)
                val finalX = if (norm > 1f) sx / norm else sx
                val finalY = if (norm > 1f) sy / norm else sy
                val updated = _state.value.copy(
                    leftStick = calculateStickState(finalX, finalY, baseState.leftStick)
                )
                _state.value = updated
                updateSharedState(updated)
                return true
            }

            if (!isAppInForeground && (event.action == KeyEvent.ACTION_DOWN || !pressed.contains(primaryBtnName))) {
                // Out-of-app micro-slice capture: illuminate button on ACTION_DOWN (or on ACTION_UP if ACTION_DOWN
                // occurred between slices) for 220ms so fast taps and pass-through slices always show on the
                // floating overlay and automatically release without ever getting stuck.
                if (primaryBtnName == "X" || primaryBtnName == "Y") {
                    faceButtonDownTimestampMs[primaryBtnName] = nowMs
                    val xTime = faceButtonDownTimestampMs["X"] ?: 0L
                    val yTime = faceButtonDownTimestampMs["Y"] ?: 0L
                    if (xTime > 0L && yTime > 0L && kotlin.math.abs(xTime - yTime) <= 30L) {
                        suppressedCompanionTriangleActive = true
                        pressed.remove("Y")
                        pulseHeldButtons.remove("Y")
                        mappedButtons = listOf("X")
                        primaryBtnName = "X"
                    }
                }
                val expiry = nowMs + 235L
                for (btn in mappedButtons) {
                    pressed.add(btn)
                    pulseHeldButtons[btn] = expiry
                    if (btn.startsWith("DPAD_")) {
                        pulseHeldDpad[btn] = expiry
                    }
                }
                lastInGameActionMs = nowMs
            } else if (event.action == KeyEvent.ACTION_DOWN) {
                if (primaryBtnName == "X" || primaryBtnName == "Y") {
                    faceButtonDownTimestampMs[primaryBtnName] = nowMs
                    val xTime = faceButtonDownTimestampMs["X"] ?: 0L
                    val yTime = faceButtonDownTimestampMs["Y"] ?: 0L
                    if (xTime > 0L && yTime > 0L && kotlin.math.abs(xTime - yTime) <= 25L) {
                        suppressedCompanionTriangleActive = true
                        pressed.remove("Y")
                        mappedButtons = listOf("X")
                        primaryBtnName = "X"
                    } else if (primaryBtnName == "Y" && suppressedCompanionTriangleActive && pressed.contains("X")) {
                        return true
                    }
                }

                for (btn in mappedButtons) {
                    pressed.add(btn)
                    if (btn.startsWith("DPAD_")) {
                        keyHeldDpad.add(btn)
                        keyHeldDpadTimestampMs[btn] = nowMs
                    } else if (btn == "L2" || btn == "R2") {
                        keyHeldTriggers.add(btn)
                    }
                }
                if (keyCode == KeyEvent.KEYCODE_BUTTON_MODE ||
                    keyCode == KeyEvent.KEYCODE_BUTTON_START ||
                    keyCode == KeyEvent.KEYCODE_BUTTON_A ||
                    keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                ) {
                    notifyHomeLoginButtonPressed()
                }
                val lastRelease = lastButtonReleaseMs[keyCode] ?: 0L
                if (lastRelease > 0L && event.repeatCount == 0) {
                    val interval = nowMs - lastRelease
                    if (interval in 1..25) {
                        val chatter = ButtonChatterEvent(
                            buttonCode = keyCode,
                            buttonName = primaryBtnName,
                            bounceIntervalMs = interval,
                            timestampMs = nowMs
                        )
                        val chatterList = (listOf(chatter) + _state.value.buttonChatterEvents).take(8)
                        _state.value = _state.value.copy(buttonChatterEvents = chatterList)
                    }
                }
                lastButtonPressMs[keyCode] = nowMs
                lastInGameActionMs = nowMs
            } else if (event.action == KeyEvent.ACTION_UP) {
                if (primaryBtnName == "X" || primaryBtnName == "Y") {
                    faceButtonDownTimestampMs.remove(primaryBtnName)
                    if (suppressedCompanionTriangleActive) {
                        suppressedCompanionTriangleActive = false
                        faceButtonDownTimestampMs.remove("X")
                        faceButtonDownTimestampMs.remove("Y")
                        pressed.remove("X")
                        pressed.remove("Y")
                        pulseHeldButtons.remove("X")
                        pulseHeldButtons.remove("Y")
                    }
                }
                for (btn in mappedButtons) {
                    pulseHeldButtons.remove(btn)
                    if (btn.startsWith("DPAD_")) {
                        pulseHeldDpad.remove(btn)
                        keyHeldDpad.remove(btn)
                        keyHeldDpadTimestampMs.remove(btn)
                        if (!hatHeldDpad.contains(btn)) {
                            pressed.remove(btn)
                        }
                    } else {
                        if (btn == "L2" || btn == "R2") {
                            keyHeldTriggers.remove(btn)
                        }
                        pressed.remove(btn)
                    }
                }
                lastButtonReleaseMs[keyCode] = nowMs
                lastInGameActionMs = nowMs
            }

            val currentTriggers = baseState.triggers
            val updatedLeftTrigger = when {
                primaryBtnName == "L2" && (!isAppInForeground || event.action == KeyEvent.ACTION_DOWN) -> 1f
                primaryBtnName == "L2" && event.action == KeyEvent.ACTION_UP -> 0f
                pressed.contains("L2") -> maxOf(currentTriggers.leftTrigger, 1f)
                else -> currentTriggers.leftTrigger
            }
            val updatedRightTrigger = when {
                primaryBtnName == "R2" && (!isAppInForeground || event.action == KeyEvent.ACTION_DOWN) -> 1f
                primaryBtnName == "R2" && event.action == KeyEvent.ACTION_UP -> 0f
                pressed.contains("R2") -> maxOf(currentTriggers.rightTrigger, 1f)
                else -> currentTriggers.rightTrigger
            }

            val motionRecentlyActive = (nowMs - lastMotionStickUpdateMs) < 350L
            val (updatedLeftStick, updatedRightStick) = if (isDpadAction && !motionRecentlyActive) {
                computeInGameStickStates(pressed, nowMs, baseState)
            } else {
                Pair(baseState.leftStick, baseState.rightStick)
            }

            val updated = _state.value.copy(
                pressedButtons = pressed,
                leftStick = updatedLeftStick,
                rightStick = updatedRightStick,
                triggers = TriggerState(
                    leftTrigger = updatedLeftTrigger,
                    rightTrigger = updatedRightTrigger
                )
            )
            _state.value = updated
            updateSharedState(updated)
            return true
        }
        return false
    }

    private fun computeInGameStickStates(
        pressed: Set<String>,
        nowMs: Long,
        baseState: ControllerHealthState = _sharedLiveState.value
    ): Pair<StickState, StickState> {
        var lx = (if (pressed.contains("DPAD_RIGHT") || pulseHeldDpad.containsKey("DPAD_RIGHT")) 1f else 0f) -
            (if (pressed.contains("DPAD_LEFT") || pulseHeldDpad.containsKey("DPAD_LEFT")) 1f else 0f)
        var ly = (if (pressed.contains("DPAD_DOWN") || pulseHeldDpad.containsKey("DPAD_DOWN")) 1f else 0f) -
            (if (pressed.contains("DPAD_UP") || pulseHeldDpad.containsKey("DPAD_UP")) 1f else 0f)

        val norm = hypot(lx, ly)
        if (norm > 1f) {
            lx /= norm
            ly /= norm
        }

        inGameTargetLx = lx.coerceIn(-1f, 1f)
        inGameTargetLy = ly.coerceIn(-1f, 1f)

        val lState = calculateStickState(inGameTargetLx, inGameTargetLy, baseState.leftStick)
        val rState = baseState.rightStick
        return Pair(lState, rState)
    }

    /**
     * Triggered by AccessibilityService when directional scrolling or focus changes happen inside an external game or launcher.
     */
    fun onAccessibilityDirectionalPulse(dx: Float, dy: Float, triggerDpad: Boolean = true) {
        val nowMs = System.currentTimeMillis()
        if ((nowMs - lastMotionStickUpdateMs) < 350L) return

        val norm = hypot(dx, dy)
        val sx = if (norm > 1f) (dx / norm).coerceIn(-1f, 1f) else dx.coerceIn(-1f, 1f)
        val sy = if (norm > 1f) (dy / norm).coerceIn(-1f, 1f) else dy.coerceIn(-1f, 1f)

        val baseState = _sharedLiveState.value
        val pressed = baseState.pressedButtons.toMutableSet()
        val expiry = nowMs + 220L
        if (sx < -0.25f) {
            pulseHeldDpad["DPAD_LEFT"] = expiry
            if (triggerDpad) pressed.add("DPAD_LEFT")
        }
        if (sx > 0.25f) {
            pulseHeldDpad["DPAD_RIGHT"] = expiry
            if (triggerDpad) pressed.add("DPAD_RIGHT")
        }
        if (sy < -0.25f) {
            pulseHeldDpad["DPAD_UP"] = expiry
            if (triggerDpad) pressed.add("DPAD_UP")
        }
        if (sy > 0.25f) {
            pulseHeldDpad["DPAD_DOWN"] = expiry
            if (triggerDpad) pressed.add("DPAD_DOWN")
        }

        inGameTargetLx = sx
        inGameTargetLy = sy
        lastInGameActionMs = nowMs

        val updated = _state.value.copy(
            pressedButtons = pressed,
            leftStick = calculateStickState(sx, sy, baseState.leftStick),
            rightStick = baseState.rightStick,
            triggers = baseState.triggers
        )
        _state.value = updated
        updateSharedState(updated)
    }

    /**
     * Called by ControllerOverlayService to expire transient pulses and stale synthetic D-Pad keys.
     */
    fun stepInGameStickAnimation(nowMs: Long = System.currentTimeMillis()) {
        val sliceExpired = isTimeSlicedSamplingActive && !isAppInForeground &&
            lastMotionStickUpdateMs > 0L && (nowMs - lastMotionStickUpdateMs) > 215L
        if (pulseHeldDpad.isEmpty() && pulseHeldButtons.isEmpty() && keyHeldDpadTimestampMs.isEmpty() && !sliceExpired) {
            return
        }

        var changed = false
        val baseState = _sharedLiveState.value
        val pressed = baseState.pressedButtons.toMutableSet()
        var nextLt = baseState.triggers.leftTrigger
        var nextRt = baseState.triggers.rightTrigger

        // Expire out-of-app micro-sliced button pulses
        val btnIter = pulseHeldButtons.entries.iterator()
        while (btnIter.hasNext()) {
            val entry = btnIter.next()
            if (nowMs >= entry.value) {
                val btn = entry.key
                btnIter.remove()
                changed = true
                if (!keyHeldDpad.contains(btn) && !hatHeldDpad.contains(btn)) {
                    pressed.remove(btn)
                    if (btn == "L2" && (nowMs - lastMotionStickUpdateMs) > 215L) {
                        nextLt = 0f
                    }
                    if (btn == "R2" && (nowMs - lastMotionStickUpdateMs) > 215L) {
                        nextRt = 0f
                    }
                }
            }
        }

        // Expire transient directional pulses
        val pulseIter = pulseHeldDpad.entries.iterator()
        while (pulseIter.hasNext()) {
            val entry = pulseIter.next()
            if (nowMs >= entry.value) {
                val dir = entry.key
                pulseIter.remove()
                changed = true
                if (!keyHeldDpad.contains(dir) && !hatHeldDpad.contains(dir)) {
                    pressed.remove(dir)
                }
            }
        }

        // Expire any stale synthetic D-Pad key that missed an ACTION_UP due to window focus switch
        val keyIter = keyHeldDpadTimestampMs.entries.iterator()
        while (keyIter.hasNext()) {
            val entry = keyIter.next()
            if (nowMs - entry.value > 2500L) {
                val dir = entry.key
                keyIter.remove()
                keyHeldDpad.remove(dir)
                if (!hatHeldDpad.contains(dir)) {
                    if (pressed.remove(dir)) {
                        changed = true
                    }
                }
            }
        }

        // If time-sliced motion sampling is active in-game and no MotionEvent arrived for >165ms,
        // the user returned the Hall-effect stick or D-Pad hat to center (0, 0).
        var resetSticksFromSlice = false
        if (sliceExpired) {
            isStickDeceleratingTowardCenter = false
            if (hatHeldDpad.isNotEmpty()) {
                hatHeldDpad.forEach { dir ->
                    if (!keyHeldDpad.contains(dir) && !pulseHeldDpad.containsKey(dir)) {
                        pressed.remove(dir)
                    }
                }
                hatHeldDpad.clear()
                changed = true
            }
            val curL = baseState.leftStick
            val curR = baseState.rightStick
            if ((kotlin.math.abs(curL.x) > 0.05f || kotlin.math.abs(curL.y) > 0.05f ||
                    kotlin.math.abs(curR.x) > 0.05f || kotlin.math.abs(curR.y) > 0.05f) &&
                keyHeldDpad.isEmpty() && pulseHeldDpad.isEmpty()
            ) {
                resetSticksFromSlice = true
                changed = true
            }
        }

        if (changed) {
            val motionRecentlyActive = (nowMs - lastMotionStickUpdateMs) < 165L
            val nextLeftStick = when {
                resetSticksFromSlice -> calculateStickState(0f, 0f, baseState.leftStick)
                !motionRecentlyActive -> computeInGameStickStates(pressed, nowMs, baseState).first
                else -> baseState.leftStick
            }
            val nextRightStick = if (resetSticksFromSlice) {
                calculateStickState(0f, 0f, baseState.rightStick)
            } else {
                baseState.rightStick
            }
            val updated = _state.value.copy(
                pressedButtons = pressed,
                leftStick = nextLeftStick,
                rightStick = nextRightStick,
                triggers = TriggerState(leftTrigger = nextLt, rightTrigger = nextRt)
            )
            _state.value = updated
            updateSharedState(updated)
        }
    }

    private fun mapRawScanCode(scanCode: Int, isSonyController: Boolean = false): String? {
        if (isSonyController) {
            when (scanCode) {
                103, 544 -> return "DPAD_UP"
                108, 545 -> return "DPAD_DOWN"
                105, 546 -> return "DPAD_LEFT"
                106, 547 -> return "DPAD_RIGHT"
                288 -> return "X"
                289 -> return "A"
                290 -> return "B"
                291 -> return "Y"
                292 -> return "L1"
                293 -> return "R1"
                294 -> return "L2"
                295 -> return "R2"
                296 -> return "SELECT"
                297 -> return "START"
                298 -> return "THUMBL"
                299 -> return "THUMBR"
                300 -> return "MODE"
                304 -> return "A"
                305 -> return "B"
                306 -> return "B"
                // In Linux hid-playstation / hid-sony (Vendor_054c), BTN_WEST (308) is Square ("X")
                // and BTN_NORTH (307) is Triangle ("Y"):
                307 -> return "Y"
                308 -> return "X"
                309 -> return "R1"
                310 -> return "L1"
                311 -> return "R1"
                312 -> return "L2"
                313 -> return "R2"
                314 -> return "SELECT"
                315 -> return "START"
                316 -> return "MODE"
                317 -> return "THUMBL"
                318 -> return "THUMBR"
            }
        }
        return when (scanCode) {
            103, 544 -> "DPAD_UP"
            108, 545 -> "DPAD_DOWN"
            105, 546 -> "DPAD_LEFT"
            106, 547 -> "DPAD_RIGHT"
            304, 288 -> "A"
            305, 289 -> "B"
            306 -> "B"
            307, 290 -> "X"
            308, 291 -> "Y"
            309 -> "R1"
            310, 292 -> "L1"
            311, 293 -> "R1"
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

    private fun normalizeGamepadKeyCodes(
        keyCode: Int,
        scanCode: Int = 0,
        isSonyController: Boolean = false
    ): List<String> {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP_LEFT -> listOf("DPAD_UP", "DPAD_LEFT")
            KeyEvent.KEYCODE_DPAD_UP_RIGHT -> listOf("DPAD_UP", "DPAD_RIGHT")
            KeyEvent.KEYCODE_DPAD_DOWN_LEFT -> listOf("DPAD_DOWN", "DPAD_LEFT")
            KeyEvent.KEYCODE_DPAD_DOWN_RIGHT -> listOf("DPAD_DOWN", "DPAD_RIGHT")
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP -> listOf("DPAD_UP")
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN -> listOf("DPAD_DOWN")
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT -> listOf("DPAD_LEFT")
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> listOf("DPAD_RIGHT")
            else -> listOf(normalizeGamepadKeyCode(keyCode, scanCode, isSonyController))
        }
    }

    private fun normalizeGamepadKeyCode(
        keyCode: Int,
        scanCode: Int = 0,
        isSonyController: Boolean = false
    ): String {
        val fromKeyCode = when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_2, KeyEvent.KEYCODE_DPAD_CENTER -> "A"
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_3, KeyEvent.KEYCODE_BUTTON_C -> "B"
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_1 -> "X"
            KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_4 -> "Y"
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_5 -> "L1"
            KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_6, KeyEvent.KEYCODE_BUTTON_Z -> "R1"
            KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_7 -> "L2"
            KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_BUTTON_8 -> "R2"
            KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_9, KeyEvent.KEYCODE_BUTTON_14, KeyEvent.KEYCODE_BACK -> "SELECT"
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_10 -> "START"
            KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_11 -> "THUMBL"
            KeyEvent.KEYCODE_BUTTON_THUMBR, KeyEvent.KEYCODE_BUTTON_12 -> "THUMBR"
            KeyEvent.KEYCODE_BUTTON_MODE, KeyEvent.KEYCODE_BUTTON_13, KeyEvent.KEYCODE_HOME -> "MODE"
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP -> "DPAD_UP"
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN -> "DPAD_DOWN"
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT -> "DPAD_LEFT"
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> "DPAD_RIGHT"
            else -> null
        }
        if (fromKeyCode != null) return fromKeyCode
        val fromScan = mapRawScanCode(scanCode, isSonyController)
        if (fromScan != null) return fromScan
        return KeyEvent.keyCodeToString(keyCode)
            .removePrefix("KEYCODE_BUTTON_")
            .removePrefix("KEYCODE_")
    }

    /**
     * Allows interactive on-screen testing or simulation of controller buttons & sticks.
     */
    fun setSimulatedButtonState(buttonName: String, isPressed: Boolean) {
        val pressed = _state.value.pressedButtons.toMutableSet()
        if (isPressed) {
            pressed.add(buttonName)
            if (buttonName.startsWith("DPAD_")) keyHeldDpad.add(buttonName)
        } else {
            pressed.remove(buttonName)
            if (buttonName.startsWith("DPAD_")) keyHeldDpad.remove(buttonName)
        }
        val lt = if (pressed.contains("L2")) 1f else 0f
        val rt = if (pressed.contains("R2")) 1f else 0f
        val rawX = (if (pressed.contains("DPAD_RIGHT")) 1f else 0f) - (if (pressed.contains("DPAD_LEFT")) 1f else 0f)
        val rawY = (if (pressed.contains("DPAD_DOWN")) 1f else 0f) - (if (pressed.contains("DPAD_UP")) 1f else 0f)
        val norm = hypot(rawX, rawY)
        val sx = if (norm > 1f) rawX / norm else rawX
        val sy = if (norm > 1f) rawY / norm else rawY

        val updated = _state.value.copy(
            pressedButtons = pressed,
            leftStick = if (sx != 0f || sy != 0f || buttonName.startsWith("DPAD_")) {
                calculateStickState(sx, sy, _state.value.leftStick)
            } else {
                _state.value.leftStick
            },
            triggers = TriggerState(
                leftTrigger = lt,
                rightTrigger = rt
            )
        )
        _state.value = updated
        updateSharedState(updated)
    }

    fun setSimulatedSticks(lx: Float, ly: Float, rx: Float, ry: Float) {
        lastMotionStickUpdateMs = System.currentTimeMillis()
        val updated = _state.value.copy(
            leftStick = calculateStickState(lx, ly, _state.value.leftStick),
            rightStick = calculateStickState(rx, ry, _state.value.rightStick)
        )
        _state.value = updated
        updateSharedState(updated)
    }

    fun updateAxesFromRawStream(
        lx: Float,
        ly: Float,
        rx: Float,
        ry: Float,
        lt: Float,
        rt: Float,
        hatX: Float,
        hatY: Float
    ) {
        measurePollingRate()
        lastMotionStickUpdateMs = System.currentTimeMillis()
        if (hatX < -0.35f) hatHeldDpad.add("DPAD_LEFT") else hatHeldDpad.remove("DPAD_LEFT")
        if (hatX > 0.35f) hatHeldDpad.add("DPAD_RIGHT") else hatHeldDpad.remove("DPAD_RIGHT")
        if (hatY < -0.35f) hatHeldDpad.add("DPAD_UP") else hatHeldDpad.remove("DPAD_UP")
        if (hatY > 0.35f) hatHeldDpad.add("DPAD_DOWN") else hatHeldDpad.remove("DPAD_DOWN")

        val pressed = _state.value.pressedButtons.toMutableSet()
        listOf("DPAD_LEFT", "DPAD_RIGHT", "DPAD_UP", "DPAD_DOWN").forEach { dir ->
            if (hatHeldDpad.contains(dir) || keyHeldDpad.contains(dir)) {
                pressed.add(dir)
            } else {
                pressed.remove(dir)
            }
        }
        if (lt > 0.25f) pressed.add("L2") else pressed.remove("L2")
        if (rt > 0.25f) pressed.add("R2") else pressed.remove("R2")

        val updated = _state.value.copy(
            leftStick = calculateStickState(lx, ly, _state.value.leftStick),
            rightStick = calculateStickState(rx, ry, _state.value.rightStick),
            triggers = TriggerState(
                leftTrigger = lt.coerceIn(0f, 1f),
                rightTrigger = rt.coerceIn(0f, 1f)
            ),
            pressedButtons = pressed
        )
        _state.value = updated
        updateSharedState(updated)
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
     * Test rumble motors on controller (searches primary and sibling InputDevice nodes for Bluetooth/USB controllers).
     */
    fun testRumble(intensity: Float = 1.0f, durationMs: Long = 300) {
        if (_state.value.controllerId < 0 || InputDevice.getDevice(_state.value.controllerId) == null) {
            scanActiveController()
        }
        val devId = _state.value.controllerId
        val primaryDevice = if (devId >= 0) InputDevice.getDevice(devId) else null

        val candidateDevices = buildList {
            if (primaryDevice != null) add(primaryDevice)
            try {
                for (id in InputDevice.getDeviceIds()) {
                    val d = InputDevice.getDevice(id) ?: continue
                    if (d.id != devId && !d.isVirtual) {
                        val sameVid = primaryDevice != null && primaryDevice.vendorId > 0 && d.vendorId == primaryDevice.vendorId
                        if (sameVid || isActualGamepadInputDevice(d)) {
                            add(d)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        val amp = (intensity * 255).toInt().coerceIn(1, 255)
        try {
            for (device in candidateDevices) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.vibratorManager != null) {
                    val vm = device.vibratorManager
                    val hasAnyMotor = vm.vibratorIds.isNotEmpty() || vm.defaultVibrator.hasVibrator()
                    if (hasAnyMotor) {
                        val effect = VibrationEffect.createOneShot(durationMs, amp)
                        val combined = CombinedVibration.createParallel(effect)
                        vm.vibrate(combined)
                        val updated = _state.value.copy(
                            isRumbleSupported = true,
                            rumbleStatus = "Vibrating (${durationMs}ms @ ${(intensity * 100).toInt()}%)"
                        )
                        _state.value = updated
                        updateSharedState(updated)
                        return
                    }
                }
                @Suppress("DEPRECATION")
                val vib = device.vibrator
                if (vib.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vib.vibrate(VibrationEffect.createOneShot(durationMs, amp))
                    } else {
                        @Suppress("DEPRECATION")
                        vib.vibrate(durationMs)
                    }
                    val updated = _state.value.copy(
                        isRumbleSupported = true,
                        rumbleStatus = "Actuating Controller Motors (${durationMs}ms @ ${(intensity * 100).toInt()}%)"
                    )
                    _state.value = updated
                    updateSharedState(updated)
                    return
                }
            }
            fallbackPhoneVibrate(durationMs)
        } catch (e: Exception) {
            Log.e(TAG, "Rumble failed: ${e.message}")
            fallbackPhoneVibrate(durationMs)
        }
    }

    private fun fallbackPhoneVibrate(durationMs: Long) {
        try {
            val phoneVib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            phoneVib?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(durationMs)
                }
            }
            val updated = _state.value.copy(rumbleStatus = "Haptic Pulse Fired (${durationMs}ms)")
            _state.value = updated
            updateSharedState(updated)
        } catch (_: Exception) {}
    }

    /**
     * Set controller LED color (Android 12+ / API 31+ with supported lights manager across primary and sibling nodes).
     */
    fun setLedColor(colorHex: String) {
        if (_state.value.controllerId < 0 || InputDevice.getDevice(_state.value.controllerId) == null) {
            scanActiveController()
        }
        val devId = _state.value.controllerId
        val primaryDevice = if (devId >= 0) InputDevice.getDevice(devId) else null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val candidateDevices = buildList {
                if (primaryDevice != null) add(primaryDevice)
                try {
                    for (id in InputDevice.getDeviceIds()) {
                        val d = InputDevice.getDevice(id) ?: continue
                        if (d.id != devId && !d.isVirtual) {
                            val sameVid = primaryDevice != null && primaryDevice.vendorId > 0 && d.vendorId == primaryDevice.vendorId
                            if (sameVid || isActualGamepadInputDevice(d)) {
                                add(d)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            val colorInt = try {
                android.graphics.Color.parseColor(colorHex)
            } catch (_: Exception) {
                android.graphics.Color.CYAN
            }
            for (device in candidateDevices) {
                try {
                    val lightsManager = device.lightsManager
                    val lightsList = lightsManager.lights
                    if (lightsList.isNotEmpty()) {
                        val reqBuilder = android.hardware.lights.LightsRequest.Builder()
                        for (light in lightsList) {
                            val state = LightState.Builder().setColor(colorInt).build()
                            reqBuilder.addLight(light, state)
                        }
                        val session = lightsManager.openSession()
                        session.requestLights(reqBuilder.build())
                        val updated = _state.value.copy(
                            isLedSupported = true,
                            ledCurrentColorHex = colorHex
                        )
                        _state.value = updated
                        updateSharedState(updated)
                        Log.i(TAG, "Set controller LED to $colorHex")
                        return
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "LED set color failed: ${e.message}")
                }
            }
        }
        val updated = _state.value.copy(ledCurrentColorHex = colorHex)
        _state.value = updated
        updateSharedState(updated)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                val nowMs = System.currentTimeMillis()
                if (nowMs - lastGyroEmitMs < 200L) return
                val pitch = ((event.values.getOrNull(0) ?: 0f) * 100).roundToInt() / 100f
                val roll = ((event.values.getOrNull(1) ?: 0f) * 100).roundToInt() / 100f
                val yaw = ((event.values.getOrNull(2) ?: 0f) * 100).roundToInt() / 100f

                val prev = _state.value.gyroReading
                val delta = kotlin.math.abs(pitch - prev.pitch) +
                    kotlin.math.abs(roll - prev.roll) +
                    kotlin.math.abs(yaw - prev.yaw)
                if (delta < 0.08f) return

                lastGyroEmitMs = nowMs
                val gyroInfo = GyroReading(
                    isSupported = true,
                    pitch = pitch,
                    roll = roll,
                    yaw = yaw,
                    timestampNanos = event.timestamp
                )
                _state.value = _state.value.copy(gyroReading = gyroInfo)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                lastAccelX = event.values.getOrNull(0) ?: 0f
                lastAccelY = event.values.getOrNull(1) ?: 0f
                hasLastAccel = true
            }
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
