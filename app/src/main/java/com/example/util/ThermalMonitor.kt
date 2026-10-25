package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.example.model.ThermalInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThermalMonitor(private val context: Context) {

    private val _thermalInfo = MutableStateFlow(ThermalInfo())
    val thermalInfo: StateFlow<ThermalInfo> = _thermalInfo.asStateFlow()

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                updateBatteryAndThermal(intent)
            }
        }
    }

    fun start() {
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(batteryReceiver, filter)
            }
        } catch (_: Exception) {}
        refresh()
    }

    fun stop() {
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}
    }

    fun refresh() {
        try {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            updateBatteryAndThermal(batteryIntent)
        } catch (_: Exception) {}
    }

    private fun updateBatteryAndThermal(batteryIntent: Intent?) {
        val intent = batteryIntent ?: try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) { null }

        var tempC = _thermalInfo.value.batteryTempC
        var level = 100
        var isCharging = false
        var voltage = 4000
        var healthStr = "Good"

        // Direct hardware capacity query via BatteryManager (zero latency)
        try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val hwCap = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (hwCap in 1..100) {
                level = hwCap
            }
        } catch (_: Exception) {}

        intent?.let { bIntent ->
            // Android hardware battery temperature: Tenths of a degree Celsius (e.g. 345 = 34.5°C)
            val rawTemp = bIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            if (rawTemp > 0) {
                tempC = rawTemp / 10.0f
            }
            val rawLevel = bIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = bIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (rawLevel >= 0 && scale > 0) {
                level = (rawLevel * 100) / scale
            }
            val status = bIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            voltage = bIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4000)

            healthStr = when (bIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheated!"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                else -> "Normal"
            }
        }

        var statusInt = 0
        var statusName = "Normal"
        var headroom = 1.0f

        if (powerManager != null) {
            statusInt = powerManager.currentThermalStatus
            statusName = when (statusInt) {
                PowerManager.THERMAL_STATUS_NONE -> "Cool (No Throttling)"
                PowerManager.THERMAL_STATUS_LIGHT -> "Light (Nominal Load)"
                PowerManager.THERMAL_STATUS_MODERATE -> "Moderate (Warm)"
                PowerManager.THERMAL_STATUS_SEVERE -> "Severe (Throttling Active!)"
                PowerManager.THERMAL_STATUS_CRITICAL -> "Critical (Heavy Throttling!)"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency (Thermal Shutdown Imminent)"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
                else -> "Optimal"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val rawHeadroom = powerManager.getThermalHeadroom(10)
                    if (!rawHeadroom.isNaN()) {
                        headroom = rawHeadroom.coerceIn(0f, 2.0f)
                    }
                } catch (_: Exception) {}
            }
        }

        val throttlingRisk = statusInt >= PowerManager.THERMAL_STATUS_MODERATE ||
                tempC >= 42.0f ||
                headroom < 0.4f

        _thermalInfo.value = ThermalInfo(
            thermalStatusInt = statusInt,
            thermalStatusName = statusName,
            thermalHeadroom = headroom,
            batteryTempC = tempC,
            batteryLevel = level,
            isCharging = isCharging,
            batteryVoltageMv = voltage,
            batteryHealth = healthStr,
            isThrottlingRisk = throttlingRisk
        )
    }
}
