package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

enum class ShizukuState {
    ACTIVE,             // Shizuku running & permission granted (Real FPS active)
    PERMISSION_NEEDED,  // Shizuku running but needs permission grant
    NOT_RUNNING,        // Shizuku app not running / binder not responding
    UNSUPPORTED         // Device is Android 10 or below (< API 30)
}

data class ShizukuInfo(
    val state: ShizukuState = ShizukuState.NOT_RUNNING,
    val version: Int = 0,
    val explanation: String = ""
)

object ShizukuManager {

    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val REQUEST_CODE_SHIZUKU = 9001

    private val _shizukuInfo = MutableStateFlow(ShizukuInfo())
    val shizukuInfo: StateFlow<ShizukuInfo> = _shizukuInfo.asStateFlow()

    private var isListenerRegistered = false
    private var lastQueryTimestampNanos = 0L
    private var lastRecordedRealFps = 0

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        checkStatus()
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE_SHIZUKU) {
            checkStatus()
        }
    }

    fun init() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            _shizukuInfo.value = ShizukuInfo(
                state = ShizukuState.UNSUPPORTED,
                explanation = "Shizuku real frame timing requires Android 11+ (API 30+). Your device uses EFPS."
            )
            return
        }

        try {
            if (!isListenerRegistered) {
                Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
                Shizuku.addBinderDeadListener(binderDeadListener)
                Shizuku.addRequestPermissionResultListener(permissionResultListener)
                isListenerRegistered = true
            }
        } catch (_: Exception) {}

        checkStatus()
    }

    fun checkStatus(): ShizukuInfo {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val info = ShizukuInfo(
                state = ShizukuState.UNSUPPORTED,
                explanation = "Shizuku real frame timing requires Android 11+ (API 30+). Falling back to EFPS (Estimated Frames Per Second)."
            )
            _shizukuInfo.value = info
            return info
        }

        try {
            if (!Shizuku.pingBinder()) {
                val info = ShizukuInfo(
                    state = ShizukuState.NOT_RUNNING,
                    explanation = "Shizuku service is not running. Launch Shizuku to activate real FPS (currently using EFPS fallback)."
                )
                _shizukuInfo.value = info
                return info
            }

            val hasPermission = try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (_: Exception) {
                false
            }

            val version = try { Shizuku.getVersion() } catch (_: Exception) { 0 }

            val info = if (hasPermission) {
                ShizukuInfo(
                    state = ShizukuState.ACTIVE,
                    version = version,
                    explanation = "Shizuku v$version Active: Real in-game frame timing privileged pipeline ready."
                )
            } else {
                ShizukuInfo(
                    state = ShizukuState.PERMISSION_NEEDED,
                    version = version,
                    explanation = "Shizuku v$version detected! Tap 'Grant Permission' to unlock real in-game FPS."
                )
            }

            _shizukuInfo.value = info
            return info
        } catch (_: Exception) {
            val info = ShizukuInfo(
                state = ShizukuState.NOT_RUNNING,
                explanation = "Could not communicate with Shizuku binder. Using EFPS fallback."
            )
            _shizukuInfo.value = info
            return info
        }
    }

    fun requestPermission() {
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.shouldShowRequestPermissionRationale()) {
                    // Direct request
                    Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
                } else {
                    Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
                }
            }
        } catch (_: Exception) {}
    }

    fun openShizukuApp(context: Context): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun isRealFpsAvailable(): Boolean {
        return _shizukuInfo.value.state == ShizukuState.ACTIVE
    }

    /**
     * Executes privileged dumpsys gfxinfo on the boosted game package to read exact hardware frame timings.
     * Returns the calculated real FPS, or null if Shizuku is unavailable or no frames were recorded.
     */
    suspend fun queryRealGameFps(packageName: String, maxCapHz: Int = 120): Int? = withContext(Dispatchers.IO) {
        if (!isRealFpsAvailable() || packageName.isBlank()) {
            return@withContext null
        }

        try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }

            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", "dumpsys gfxinfo $packageName framestats"),
                null,
                null
            ) as java.lang.Process

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var inFramestats = false
            var headerLine: String? = null
            val frameCompletionTimes = mutableListOf<Long>()

            var line: String? = reader.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.startsWith("---PROFILEDATA---")) {
                    inFramestats = true
                    headerLine = reader.readLine() // Header columns line
                    line = reader.readLine()
                    continue
                }

                if (inFramestats) {
                    if (trimmed.startsWith("---") || trimmed.isEmpty()) {
                        break
                    }
                    val parts = trimmed.split(",")
                    // In framestats, column 13 (or last column) is FRAME_COMPLETED timestamp in nanos
                    if (parts.size >= 14) {
                        val frameCompletedNanos = parts[13].trim().toLongOrNull()
                        if (frameCompletedNanos != null && frameCompletedNanos > 0L) {
                            frameCompletionTimes.add(frameCompletedNanos)
                        }
                    }
                }
                line = reader.readLine()
            }
            reader.close()
            process.destroy()

            if (frameCompletionTimes.size >= 2) {
                // Filter frames from the most recent 1 second window
                val latestTime = frameCompletionTimes.last()
                val oneSecAgo = latestTime - 1_000_000_000L
                val recentFrames = frameCompletionTimes.filter { it >= oneSecAgo }
                if (recentFrames.size >= 2) {
                    val frameCount = recentFrames.size
                    val timeSpanNanos = recentFrames.last() - recentFrames.first()
                    if (timeSpanNanos > 0) {
                        val computedFps = ((frameCount - 1).toDouble() * 1_000_000_000.0 / timeSpanNanos).toInt()
                        val result = computedFps.coerceIn(1, maxCapHz)
                        lastRecordedRealFps = result
                        return@withContext result
                    }
                }
            }

            if (lastRecordedRealFps > 0) {
                return@withContext lastRecordedRealFps
            }
            return@withContext null
        } catch (_: Exception) {
            return@withContext null
        }
    }
}
