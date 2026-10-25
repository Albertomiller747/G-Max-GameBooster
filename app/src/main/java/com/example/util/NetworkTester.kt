package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.example.model.NetworkPingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.abs

class NetworkTester(private val context: Context) {

    data class ServerTarget(
        val region: String,
        val host: String,
        val port: Int = 80
    )

    val serverTargets = listOf(
        ServerTarget("US East (N. Virginia)", "ping.us-east-1.amazonaws.com", 80),
        ServerTarget("US West (Oregon)", "ping.us-west-1.amazonaws.com", 80),
        ServerTarget("Europe (Frankfurt)", "ping.eu-central-1.amazonaws.com", 80),
        ServerTarget("Asia East (Tokyo)", "ping.ap-northeast-1.amazonaws.com", 80),
        ServerTarget("Global Edge (Cloudflare)", "1.1.1.1", 53)
    )

    private var wifiLock: WifiManager.WifiLock? = null

    data class WifiStatus(
        val isWifi: Boolean = false,
        val is5GHzOr6GHz: Boolean = false,
        val frequencyMhz: Int = 0,
        val linkSpeedMbps: Int = 0,
        val rssi: Int = 0,
        val ssid: String = "Disconnected"
    )

    fun getWifiStatus(): WifiStatus {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

        val activeNetwork = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNetwork)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false

        if (!isWifi || wm == null) {
            return WifiStatus(isWifi = false)
        }

        val info: WifiInfo? = wm.connectionInfo
        val freq = info?.frequency ?: 0
        val isFast = freq >= 4900 // 5GHz is 5000MHz+, 6GHz is 5925MHz+
        val speed = info?.linkSpeed ?: 0
        val rssi = info?.rssi ?: 0
        val rawSsid = info?.ssid?.replace("\"", "") ?: "Connected Wi-Fi"

        return WifiStatus(
            isWifi = true,
            is5GHzOr6GHz = isFast,
            frequencyMhz = freq,
            linkSpeedMbps = speed,
            rssi = rssi,
            ssid = if (rawSsid == "<unknown ssid>") "Connected Wi-Fi" else rawSsid
        )
    }

    suspend fun runPingTest(target: ServerTarget): NetworkPingResult = withContext(Dispatchers.IO) {
        val attempts = 5
        val samples = mutableListOf<Long>()
        var lost = 0

        for (i in 0 until attempts) {
            val start = System.currentTimeMillis()
            var success = false
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(target.host, target.port), 1500)
                    success = true
                }
            } catch (_: Exception) {
                // If direct socket fails, try fallback connect
                try {
                    val fallback = java.net.InetAddress.getByName(target.host)
                    val r = fallback.isReachable(1000)
                    if (r) success = true else lost++
                } catch (_: Exception) {
                    lost++
                }
            }

            if (success) {
                val duration = System.currentTimeMillis() - start
                samples.add(duration.coerceAtLeast(1L))
            }
        }

        if (samples.isEmpty()) {
            return@withContext NetworkPingResult(
                regionName = target.region,
                host = target.host,
                packetLossPercent = 100,
                isCompleted = true,
                error = "Target unreachable (Check Internet connection)"
            )
        }

        val avgPing = samples.average().toLong()

        // Calculate jitter (mean absolute deviation between consecutive samples)
        var totalJitter = 0L
        for (i in 1 until samples.size) {
            totalJitter += abs(samples[i] - samples[i - 1])
        }
        val jitter = if (samples.size > 1) totalJitter / (samples.size - 1) else 0L
        val lossPct = ((lost.toDouble() / attempts) * 100).toInt()

        NetworkPingResult(
            regionName = target.region,
            host = target.host,
            pingMs = avgPing,
            jitterMs = jitter,
            packetLossPercent = lossPct,
            isCompleted = true
        )
    }

    fun setLowLatencyWifiLock(enable: Boolean): Boolean {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return false
        try {
            if (enable) {
                if (wifiLock == null) {
                    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                    } else {
                        WifiManager.WIFI_MODE_FULL_HIGH_PERF
                    }
                    wifiLock = wm.createWifiLock(mode, "GMaxBooster:LowLatencyLock").apply {
                        setReferenceCounted(false)
                        acquire()
                    }
                }
                return true
            } else {
                wifiLock?.let {
                    if (it.isHeld) it.release()
                }
                wifiLock = null
                return false
            }
        } catch (e: Exception) {
            return false
        }
    }

    fun isWifiLockHeld(): Boolean {
        return wifiLock?.isHeld == true
    }
}
