package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Strict Real-Internet Connectivity Monitor.
 *
 * Rejects Wi-Fi networks without upstream internet, mobile hotspots with data disabled,
 * captive portals, and local loopback VPNs by enforcing:
 * 1. OS-level `NET_CAPABILITY_INTERNET` + `NET_CAPABILITY_VALIDATED` + non-captive-portal checks
 *    on a real physical transport (`WIFI`, `CELLULAR`, or `ETHERNET`).
 * 2. Active HTTP 204 live internet reachability probing (`generate_204`) that immediately
 *    flips online state to `false` if a connected Wi-Fi/hotspot loses real internet access.
 */
object NetworkConnectivityMonitor {

    private val probeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + AppCrashGuard.coroutineExceptionHandler)
    private val isProbing = AtomicBoolean(false)

    // True when the active HTTP 204 probe detected that the current Wi-Fi/hotspot has no real internet
    @Volatile
    private var hasLiveProbeFailed: Boolean = false

    @Volatile
    private var consecutiveProbeFailures: Int = 0

    @Volatile
    private var lastProbeTimestampMs: Long = 0L

    private val _liveVerifiedState = MutableStateFlow<Boolean?>(null)

    @Volatile
    var simulatedOnlineOverrideForTests: Boolean? = null

    private val PROBE_URLS = listOf(
        "https://connectivitycheck.gstatic.com/generate_204",
        "https://clients3.google.com/generate_204",
        "https://www.google.com/generate_204"
    )

    /**
     * Evaluates whether a single [NetworkCapabilities] instance has validated real internet capabilities
     * and is not a captive portal.
     */
    fun isCapabilitiesValidated(caps: NetworkCapabilities?): Boolean {
        if (caps == null) return false
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val isCaptivePortal = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
        if (!hasInternet || !isValidated || isCaptivePortal) {
            return false
        }
        val hasPhysicalTransport =
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        val hasVpnTransport = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        return hasPhysicalTransport || hasVpnTransport
    }

    /**
     * Checks whether the device currently has an OS-validated network with an underlying
     * physical transport (`WIFI`, `CELLULAR`, or `ETHERNET`).
     */
    @Suppress("DEPRECATION")
    fun hasValidatedOSNetwork(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val activeNetwork = cm.activeNetwork
            if (activeNetwork != null) {
                val caps = cm.getNetworkCapabilities(activeNetwork)
                if (caps != null && isCapabilitiesValidated(caps)) {
                    val hasDirectPhysical =
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                    if (hasDirectPhysical) {
                        return true
                    }
                }
            }

            // Also check all active networks in case a VPN or dual-transport interface is active alongside Wi-Fi/Cellular
            val allNets = cm.allNetworks
            allNets.any { net ->
                val netCaps = cm.getNetworkCapabilities(net)
                netCaps != null &&
                    !netCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    (netCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        netCaps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        netCaps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) &&
                    isCapabilitiesValidated(netCaps)
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Synchronous check used across the app.
     * Trusts OS-validated network capabilities and only marks offline if 3 consecutive probes failed.
     */
    fun isCurrentlyOnline(context: Context): Boolean {
        simulatedOnlineOverrideForTests?.let { return it }

        val osValidated = hasValidatedOSNetwork(context)
        if (!osValidated) {
            hasLiveProbeFailed = false
            consecutiveProbeFailures = 0
            _liveVerifiedState.value = false
            return false
        }

        val now = System.currentTimeMillis()
        if (now - lastProbeTimestampMs > 20_000L) {
            triggerAsyncInternetProbe(context)
        }

        return !hasLiveProbeFailed
    }

    /**
     * Forces an immediate live HTTP 204 internet reachability check (e.g., when tapping "Retry Connection"
     * or when network state changes).
     */
    fun triggerImmediateRecheck(context: Context) {
        val osValidated = hasValidatedOSNetwork(context)
        if (!osValidated) {
            hasLiveProbeFailed = false
            consecutiveProbeFailures = 0
            _liveVerifiedState.value = false
            return
        }
        hasLiveProbeFailed = false
        consecutiveProbeFailures = 0
        _liveVerifiedState.value = true
        triggerAsyncInternetProbe(context, force = true)
    }

    private fun recordProbeResult(reachable: Boolean) {
        if (reachable) {
            consecutiveProbeFailures = 0
            hasLiveProbeFailed = false
        } else {
            consecutiveProbeFailures += 1
            if (consecutiveProbeFailures >= 3) {
                hasLiveProbeFailed = true
            }
        }
    }

    private fun triggerAsyncInternetProbe(context: Context, force: Boolean = false) {
        if (!force && !isProbing.compareAndSet(false, true)) return
        if (force) {
            isProbing.set(true)
        }
        val appContext = context.applicationContext
        probeScope.launch {
            try {
                if (!hasValidatedOSNetwork(appContext)) {
                    hasLiveProbeFailed = false
                    consecutiveProbeFailures = 0
                    lastProbeTimestampMs = System.currentTimeMillis()
                    _liveVerifiedState.value = false
                    return@launch
                }
                val reachable = probeRealInternetHttp204()
                lastProbeTimestampMs = System.currentTimeMillis()
                recordProbeResult(reachable)
                _liveVerifiedState.value = !hasLiveProbeFailed && hasValidatedOSNetwork(appContext)
            } finally {
                isProbing.set(false)
            }
        }
    }

    /**
     * Performs a real HTTP 204 probe against Google's connectivity check endpoints.
     * Rejects captive portals (HTTP 302 redirects or non-empty HTML login bodies).
     */
    suspend fun probeRealInternetHttp204(): Boolean = withContext(Dispatchers.IO) {
        for (endpoint in PROBE_URLS) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3500
                    readTimeout = 3500
                    instanceFollowRedirects = false
                    useCaches = false
                    setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                    setRequestProperty("Pragma", "no-cache")
                }
                connection.connect()
                val code = connection.responseCode
                val contentLength = connection.contentLength
                if (code == HttpURLConnection.HTTP_NO_CONTENT || (code == HttpURLConnection.HTTP_OK && contentLength <= 0)) {
                    return@withContext true
                }
            } catch (_: Exception) {
                // Probe failed on this endpoint; try fallback
            } finally {
                try {
                    connection?.disconnect()
                } catch (_: Exception) {}
            }
        }
        false
    }

    fun observeConnectivity(context: Context): Flow<Boolean> = observeOnlineState(context)

    fun observeOnlineState(context: Context): Flow<Boolean> = callbackFlow {
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            trySend(false)
            close()
            return@callbackFlow
        }

        fun evaluateAndEmit(runLiveProbe: Boolean) {
            simulatedOnlineOverrideForTests?.let {
                trySend(it)
                return
            }
            val osValidated = hasValidatedOSNetwork(appContext)
            if (!osValidated) {
                // Debounce brief Wi-Fi / Cellular network switches before emitting offline
                launch(Dispatchers.IO) {
                    delay(1200L)
                    if (!hasValidatedOSNetwork(appContext)) {
                        hasLiveProbeFailed = false
                        consecutiveProbeFailures = 0
                        _liveVerifiedState.value = false
                        trySend(false)
                    } else {
                        trySend(!hasLiveProbeFailed)
                    }
                }
                return
            }
            trySend(!hasLiveProbeFailed)
            if (runLiveProbe) {
                launch(Dispatchers.IO) {
                    val reachable = probeRealInternetHttp204()
                    lastProbeTimestampMs = System.currentTimeMillis()
                    recordProbeResult(reachable)
                    val finalState = !hasLiveProbeFailed && hasValidatedOSNetwork(appContext)
                    _liveVerifiedState.value = finalState
                    trySend(finalState)
                }
            }
        }

        evaluateAndEmit(runLiveProbe = true)

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                hasLiveProbeFailed = false
                consecutiveProbeFailures = 0
                if (hasValidatedOSNetwork(appContext)) {
                    trySend(true)
                }
                evaluateAndEmit(runLiveProbe = true)
            }

            override fun onLost(network: Network) {
                evaluateAndEmit(runLiveProbe = false)
            }

            override fun onUnavailable() {
                evaluateAndEmit(runLiveProbe = false)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                // Always verify active OS network rather than a single background interface's capabilities
                if (hasValidatedOSNetwork(appContext)) {
                    hasLiveProbeFailed = false
                    consecutiveProbeFailures = 0
                    trySend(true)
                } else {
                    evaluateAndEmit(runLiveProbe = false)
                }
            }

            override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                evaluateAndEmit(runLiveProbe = false)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            cm.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            trySend(hasValidatedOSNetwork(appContext))
        }

        // Forward updates triggered by manual Retry Connection (`triggerImmediateRecheck`)
        val stateCollectorJob = launch {
            _liveVerifiedState.collect { verified ->
                if (verified != null) {
                    val osNow = hasValidatedOSNetwork(appContext)
                    if (verified && osNow) {
                        trySend(true)
                    } else if (!osNow) {
                        evaluateAndEmit(runLiveProbe = false)
                    }
                }
            }
        }

        // Periodic 15-second heartbeat check requiring 3 consecutive failures before locking online features
        val heartbeatJob = launch(Dispatchers.IO) {
            while (isActive) {
                delay(15_000L)
                val osValidated = hasValidatedOSNetwork(appContext)
                if (!osValidated) {
                    evaluateAndEmit(runLiveProbe = false)
                } else {
                    val reachable = probeRealInternetHttp204()
                    lastProbeTimestampMs = System.currentTimeMillis()
                    recordProbeResult(reachable)
                    val finalState = !hasLiveProbeFailed && hasValidatedOSNetwork(appContext)
                    _liveVerifiedState.value = finalState
                    trySend(finalState)
                }
            }
        }

        awaitClose {
            stateCollectorJob.cancel()
            heartbeatJob.cancel()
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }
    }.distinctUntilChanged()
}

@Composable
fun rememberIsDeviceOnline(): State<Boolean> {
    val context = LocalContext.current
    val initialOnline = remember(context) { NetworkConnectivityMonitor.isCurrentlyOnline(context) }
    return NetworkConnectivityMonitor.observeOnlineState(context)
        .collectAsState(initial = initialOnline)
}

@Composable
fun OnlineRequiredGate(
    featureNameEn: String,
    featureNameEs: String,
    onRetry: () -> Unit = {},
    onRetryConnection: () -> Unit = onRetry,
    onGoToOfflineLauncher: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val effectiveRetry = {
        NetworkConnectivityMonitor.triggerImmediateRecheck(context)
        onRetryConnection()
    }
    val featureLabel = tr(featureNameEn, featureNameEs)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .testTag("online_required_gate"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    modifier = Modifier.size(68.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = tr("Offline", "Sin conexión"),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Text(
                    text = tr("Internet Connection Required", "Se Requiere Conexión a Internet"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = tr(
                        "$featureLabel requires a verified internet connection. Wi-Fi networks or hotspots without active internet access cannot unlock online features.",
                        "$featureLabel requiere una conexión a internet verificada. Las redes Wi-Fi o puntos de acceso sin internet activo no pueden desbloquear funciones en línea."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tr(
                                "Available Offline: Game Launcher & 720p Recorder",
                                "Disponible sin Conexión: Lanzador y Grabador 720p"
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = effectiveRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("offline_retry_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tr("Retry Connection", "Reintentar Conexión"),
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onGoToOfflineLauncher,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("offline_go_launcher_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tr("Go to Offline Launcher", "Ir al Lanzador sin Conexión"),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
