package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest
import java.util.Locale

/**
 * Generates a deterministic, Google Play-compliant hardware/device fingerprint hash
 * (`hw_sha256_<32-hex-chars>`) to bind Squad Invite Guest Pass activations to a physical
 * device and block multi-account / burner-account referral farming on the same hardware.
 */
object DeviceFingerprintGenerator {

    @Volatile
    private var testFingerprintOverride: String? = null

    fun setTestFingerprintOverride(overrideHash: String?) {
        testFingerprintOverride = overrideHash
    }

    @SuppressLint("HardwareIds")
    fun getDeviceFingerprintHash(context: Context): String {
        testFingerprintOverride?.takeIf { it.isNotBlank() }?.let { return it }

        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?.trim()
                ?.lowercase(Locale.ROOT)
                .orEmpty()
        } catch (_: Exception) {
            ""
        }

        val hardwareComposite = buildString {
            append("aid=").append(androidId.ifBlank { "unknown_aid" })
            append("|board=").append(Build.BOARD.orEmpty().lowercase(Locale.ROOT))
            append("|hw=").append(Build.HARDWARE.orEmpty().lowercase(Locale.ROOT))
            append("|mfg=").append(Build.MANUFACTURER.orEmpty().lowercase(Locale.ROOT))
            append("|model=").append(Build.MODEL.orEmpty().lowercase(Locale.ROOT))
            append("|device=").append(Build.DEVICE.orEmpty().lowercase(Locale.ROOT))
            append("|abis=").append(Build.SUPPORTED_ABIS?.joinToString(",").orEmpty().lowercase(Locale.ROOT))
        }

        return sha256Hex(hardwareComposite)
    }

    fun sha256Hex(rawInput: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(rawInput.toByteArray(Charsets.UTF_8))
            val hex = bytes.joinToString("") { "%02x".format(it) }.take(32)
            "hw_sha256_$hex"
        } catch (_: Exception) {
            val fallback = rawInput.hashCode().toUInt().toString(16).padStart(16, '0')
            "hw_sha256_${fallback}00000000"
        }
    }
}
