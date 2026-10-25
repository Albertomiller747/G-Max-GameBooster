package com.example.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

object SecureSettingsManager {

    const val GRANT_PERMISSION_COMMAND =
        "pm grant com.aistudio.gamebooster.kxmpzq android.permission.WRITE_SECURE_SETTINGS"

    fun canWriteSecureSettings(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun setAnimationScales(context: Context, scale: Float): Boolean {
        if (!canWriteSecureSettings(context)) return false
        return try {
            val resolver = context.contentResolver
            Settings.Global.putFloat(resolver, "window_animation_scale", scale)
            Settings.Global.putFloat(resolver, "transition_animation_scale", scale)
            Settings.Global.putFloat(resolver, "animator_duration_scale", scale)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun setPrivateDns(context: Context, mode: String, specifier: String): Boolean {
        if (!canWriteSecureSettings(context)) return false
        return try {
            val resolver = context.contentResolver
            Settings.Global.putString(resolver, "private_dns_mode", mode)
            Settings.Global.putString(resolver, "private_dns_specifier", specifier)
            true
        } catch (e: Exception) {
            false
        }
    }
}
