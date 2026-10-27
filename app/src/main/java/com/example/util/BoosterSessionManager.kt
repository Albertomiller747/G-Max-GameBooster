package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.model.BoostMode

/**
 * Manages persistent booster active sessions with time limits:
 * - Normal Boost (Gaming Mode): 3 hours active duration
 * - Beast Mode: 1 hour 30 minutes (90 minutes) active duration
 *
 * Persistence Guarantee:
 * - Does NOT start automatically when the app is installed or opened for the first time.
 * - Does NOT reset when the app is closed or restarted; calculates remaining time from persistent epoch timestamp.
 * - Remembers the user's selected target game for auto-launching.
 */
class BoosterSessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "gmax_booster_session_prefs"

        private const val KEY_IS_ACTIVE = "key_is_boost_active"
        private const val KEY_EXPIRES_AT = "key_boost_expires_at"
        private const val KEY_ACTIVE_MODE = "key_boost_active_mode"
        private const val KEY_LAST_PACKAGE = "key_last_game_package"
        private const val KEY_LAST_APP_NAME = "key_last_game_app_name"

        // 3 Hours for Normal Boost
        const val DURATION_NORMAL_BOOST_MS = 3 * 60 * 60 * 1000L // 10,800,000 ms

        // 1 Hour 30 Minutes for Beast Mode
        const val DURATION_BEAST_MODE_MS = 90 * 60 * 1000L // 5,400,000 ms

        // Background Multi-Action Optimization Intervals
        // Beast Mode: aggressive 3-minute RAM purge, strict thread priority, and sustained clock lock
        const val INTERVAL_BEAST_PURGE_MS = 3 * 60 * 1000L // 180,000 ms (3 min)

        // Normal Boost: gentle 12-minute RAM trim and low-latency Wi-Fi lock
        const val INTERVAL_NORMAL_PURGE_MS = 12 * 60 * 1000L // 720,000 ms (12 min)
    }

    /**
     * Returns the periodic optimization interval in milliseconds for the given mode.
     */
    fun getPurgeIntervalMs(mode: BoostMode?): Long {
        return if (mode == BoostMode.BEAST) {
            INTERVAL_BEAST_PURGE_MS
        } else {
            INTERVAL_NORMAL_PURGE_MS
        }
    }

    /**
     * Checks if a booster session is currently valid and not expired.
     * If the expiration time has passed, automatically deactivates the session.
     */
    fun isSessionActive(): Boolean {
        val isActiveFlag = prefs.getBoolean(KEY_IS_ACTIVE, false)
        if (!isActiveFlag) return false

        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        val now = System.currentTimeMillis()

        if (now >= expiresAt) {
            // Expired naturally
            stopSession()
            return false
        }
        return true
    }

    /**
     * Returns remaining active time in milliseconds. Returns 0 if expired or inactive.
     */
    fun getRemainingMillis(): Long {
        if (!prefs.getBoolean(KEY_IS_ACTIVE, false)) return 0L
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        val now = System.currentTimeMillis()
        val remaining = expiresAt - now
        return if (remaining > 0L) remaining else 0L
    }

    /**
     * Returns the currently active BoostMode, or null if inactive.
     */
    fun getActiveMode(): BoostMode? {
        if (!isSessionActive()) return null
        val modeStr = prefs.getString(KEY_ACTIVE_MODE, null) ?: return null
        return try {
            BoostMode.valueOf(modeStr)
        } catch (_: Exception) {
            BoostMode.GAMING
        }
    }

    /**
     * Starts a booster session with the specified mode and target game.
     * If a session is already active, preserves the existing expiration timestamp
     * so choosing a different game does not reset the remaining boost duration.
     */
    fun startSession(
        mode: BoostMode,
        targetPackage: String? = null,
        targetAppName: String? = null,
        forceResetTime: Boolean = false
    ) {
        val currentExpiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        val now = System.currentTimeMillis()

        // Preserve countdown if already active and not explicitly forced to reset
        val expiresAt = if (!forceResetTime && isSessionActive() && currentExpiresAt > now) {
            currentExpiresAt
        } else {
            val durationMs = if (mode == BoostMode.BEAST) {
                DURATION_BEAST_MODE_MS
            } else {
                DURATION_NORMAL_BOOST_MS
            }
            now + durationMs
        }

        val editor = prefs.edit()
            .putBoolean(KEY_IS_ACTIVE, true)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .putString(KEY_ACTIVE_MODE, mode.name)

        if (!targetPackage.isNullOrBlank()) {
            editor.putString(KEY_LAST_PACKAGE, targetPackage)
        }
        if (!targetAppName.isNullOrBlank()) {
            editor.putString(KEY_LAST_APP_NAME, targetAppName)
        }
        editor.apply()
    }

    /**
     * Deactivates the booster session.
     */
    fun stopSession() {
        prefs.edit()
            .putBoolean(KEY_IS_ACTIVE, false)
            .putLong(KEY_EXPIRES_AT, 0L)
            .remove(KEY_ACTIVE_MODE)
            .apply()
    }

    /**
     * Returns the last target game package and name (package, name).
     */
    fun getLastTargetGame(): Pair<String?, String?> {
        val pkg = prefs.getString(KEY_LAST_PACKAGE, null)
        val name = prefs.getString(KEY_LAST_APP_NAME, null)
        return Pair(pkg, name)
    }

    /**
     * Saves the chosen target game without activating a boost.
     */
    fun setLastTargetGame(packageName: String, appName: String) {
        prefs.edit()
            .putString(KEY_LAST_PACKAGE, packageName)
            .putString(KEY_LAST_APP_NAME, appName)
            .apply()
    }

    /**
     * Formats remaining milliseconds into HH:mm:ss format.
     */
    fun formatCountdown(millis: Long): String {
        if (millis <= 0L) return "00:00:00"
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private val KEY_LIBRARY_PACKAGES = "key_library_game_packages"

    /**
     * Returns the set of user-added game package names from persistent storage.
     */
    fun getLibraryGamePackages(): Set<String> {
        return prefs.getStringSet(KEY_LIBRARY_PACKAGES, emptySet()) ?: emptySet()
    }

    /**
     * Adds a game package to the user's persistent library.
     */
    fun addLibraryGamePackage(packageName: String) {
        val current = getLibraryGamePackages().toMutableSet()
        current.add(packageName)
        prefs.edit().putStringSet(KEY_LIBRARY_PACKAGES, current).apply()
    }

    /**
     * Removes a game package from the user's persistent library.
     */
    fun removeLibraryGamePackage(packageName: String) {
        val current = getLibraryGamePackages().toMutableSet()
        current.remove(packageName)
        prefs.edit().putStringSet(KEY_LIBRARY_PACKAGES, current).apply()
    }
}
