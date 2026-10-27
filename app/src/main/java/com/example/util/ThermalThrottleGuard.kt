package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ThermalGuardState(
    val liveTempC: Float = 29.5f,
    val batteryPct: Int = 100,
    val thermalStatusInt: Int = 0,
    val thermalStatusName: String = "Normal",
    val thermalHeadroom: Float = 1.0f, // 0.0 (throttled) to 1.0 (at throttle threshold), >1.0 (plenty headroom)
    val isThrottling: Boolean = false,
    val throttleSeverity: String = "None",
    val activeThrottleAlert: String? = null,
    val lastThrottleTimestampMs: Long = 0L,
    val historicalThrottleEvents: List<ThrottleEvent> = emptyList()
)

data class ThrottleEvent(
    val timestampMs: Long,
    val tempC: Float,
    val fpsDropPercent: Int,
    val severity: String,
    val message: String
)

class ThermalThrottleGuard(private val context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val _state = MutableStateFlow(ThermalGuardState())
    val state: StateFlow<ThermalGuardState> = _state.asStateFlow()

    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    private var baselineFps = 60
    private var lastObservedFps = 60

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val rawTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                val tempC = if (rawTemp > 0) rawTemp / 10f else _state.value.liveTempC
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val pct = if (level >= 0 && scale > 0) ((level.toFloat() / scale) * 100).toInt() else _state.value.batteryPct

                updateThermalReadings(tempC = tempC, batteryPct = pct)
            }
        }
    }

    init {
        // Register genuine battery broadcast receiver for hardware temperature
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val stickyIntent = context.registerReceiver(batteryReceiver, filter)
            stickyIntent?.let {
                val rawTemp = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                if (rawTemp > 0) {
                    val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val pct = if (level >= 0 && scale > 0) ((level.toFloat() / scale) * 100).toInt() else 100
                    updateThermalReadings(tempC = rawTemp / 10f, batteryPct = pct)
                }
            }
        } catch (_: Exception) {}

        // Register PowerManager thermal listener (Android 10+ / API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            val currentStatus = powerManager.currentThermalStatus
            handleThermalStatusChanged(currentStatus)

            thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
                handleThermalStatusChanged(status)
            }
            try {
                powerManager.addThermalStatusListener(context.mainExecutor, thermalListener!!)
            } catch (_: Exception) {}
        }
    }

    private fun handleThermalStatusChanged(status: Int) {
        val (name, isThrottling, severity) = when (status) {
            PowerManager.THERMAL_STATUS_NONE -> Triple("Cool / Nominal", false, "None")
            PowerManager.THERMAL_STATUS_LIGHT -> Triple("Light Warmth", false, "Light")
            PowerManager.THERMAL_STATUS_MODERATE -> Triple("Moderate Heat", true, "Moderate")
            PowerManager.THERMAL_STATUS_SEVERE -> Triple("Severe Throttle", true, "Severe")
            PowerManager.THERMAL_STATUS_CRITICAL -> Triple("Critical Overheat", true, "Critical")
            PowerManager.THERMAL_STATUS_EMERGENCY -> Triple("Emergency Shutdown Imminent", true, "Emergency")
            PowerManager.THERMAL_STATUS_SHUTDOWN -> Triple("Shutdown Required", true, "Shutdown")
            else -> Triple("Normal", false, "None")
        }

        val headroom = queryThermalHeadroom()

        val current = _state.value
        val alert = if (isThrottling) {
            "OS Thermal Throttling active ($name): CPU/GPU clocks are being downclocked to protect SoC."
        } else null

        val events = if (isThrottling && !current.isThrottling) {
            val event = ThrottleEvent(
                timestampMs = System.currentTimeMillis(),
                tempC = current.liveTempC,
                fpsDropPercent = calculateFpsDropPercent(),
                severity = severity,
                message = "Kernel thermal event: $name"
            )
            (listOf(event) + current.historicalThrottleEvents).take(10)
        } else {
            current.historicalThrottleEvents
        }

        _state.value = current.copy(
            thermalStatusInt = status,
            thermalStatusName = name,
            thermalHeadroom = headroom,
            isThrottling = isThrottling,
            throttleSeverity = severity,
            activeThrottleAlert = alert,
            lastThrottleTimestampMs = if (isThrottling) System.currentTimeMillis() else current.lastThrottleTimestampMs,
            historicalThrottleEvents = events
        )
    }

    private fun updateThermalReadings(tempC: Float, batteryPct: Int) {
        val current = _state.value
        val headroom = queryThermalHeadroom()
        // If battery temp is very high (>41C), also flag throttle risk
        val isHotBattery = tempC >= 42.0f
        val isThrottled = current.isThrottling || isHotBattery

        _state.value = current.copy(
            liveTempC = tempC,
            batteryPct = batteryPct,
            thermalHeadroom = headroom,
            isThrottling = isThrottled,
            throttleSeverity = if (isHotBattery && current.throttleSeverity == "None") "Moderate" else current.throttleSeverity
        )
    }

    private fun queryThermalHeadroom(): Float {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && powerManager != null) {
            try {
                // Forecast headroom in 10 seconds
                val h = powerManager.getThermalHeadroom(10)
                if (!h.isNaN() && h > 0f) {
                    return h
                }
            } catch (_: Exception) {}
        }
        // Fallback estimate based on battery temp
        val temp = _state.value.liveTempC
        return when {
            temp < 33f -> 1.5f
            temp < 38f -> 1.0f
            temp < 42f -> 0.6f
            else -> 0.2f
        }
    }

    fun onFpsSample(currentFps: Int, targetFps: Int = 60) {
        if (targetFps > 0) baselineFps = targetFps
        lastObservedFps = currentFps

        // Correlate thermal throttle with in-game FPS drops
        val stateVal = _state.value
        if (stateVal.isThrottling || stateVal.liveTempC >= 40.0f) {
            val dropRatio = if (baselineFps > 0) (baselineFps - currentFps).toFloat() / baselineFps else 0f
            if (dropRatio >= 0.20f && stateVal.activeThrottleAlert == null) {
                val dropPct = (dropRatio * 100).toInt()
                val alertMsg = "Thermal Throttle Guard: FPS dropped ${dropPct}% ($currentFps FPS) due to ${stateVal.thermalStatusName} (${String.format(java.util.Locale.US, "%.1f", stateVal.liveTempC)}°C)."
                val event = ThrottleEvent(
                    timestampMs = System.currentTimeMillis(),
                    tempC = stateVal.liveTempC,
                    fpsDropPercent = dropPct,
                    severity = stateVal.throttleSeverity,
                    message = alertMsg
                )
                _state.value = stateVal.copy(
                    activeThrottleAlert = alertMsg,
                    historicalThrottleEvents = (listOf(event) + stateVal.historicalThrottleEvents).take(10)
                )
            }
        }
    }

    private fun calculateFpsDropPercent(): Int {
        if (baselineFps <= 0) return 0
        val diff = (baselineFps - lastObservedFps).coerceAtLeast(0)
        return ((diff.toFloat() / baselineFps) * 100).toInt()
    }

    fun dismissAlert() {
        _state.value = _state.value.copy(activeThrottleAlert = null)
    }

    fun destroy() {
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null && thermalListener != null) {
            try {
                powerManager.removeThermalStatusListener(thermalListener!!)
            } catch (_: Exception) {}
        }
    }
}
