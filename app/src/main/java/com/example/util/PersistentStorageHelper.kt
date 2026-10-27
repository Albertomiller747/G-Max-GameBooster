package com.example.util

import android.content.Context
import android.os.Environment
import java.io.File

object PersistentStorageHelper {

    private const val PREFS_NAME = "gmax_hub_ui_prefs"
    private const val KEY_ONBOARDING_COMPLETED = "hub_onboarding_completed"
    private const val MARKER_DIR = "GMaxHub"
    private const val MARKER_FILE = ".provisioned"

    /**
     * Checks whether onboarding has been completed on this device, checking both
     * private SharedPreferences and external persistent marker file in Documents.
     * This ensures returning users who uninstall and reinstall do not get prompted again.
     */
    fun hasCompletedOnboarding(context: Context): Boolean {
        // 1. Check local SharedPreferences
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)) {
            return true
        }

        // 2. Check public Documents/GMaxHub/.provisioned marker file
        try {
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val marker = File(File(docsDir, MARKER_DIR), MARKER_FILE)
            if (marker.exists()) {
                // Restore to SharedPreferences for fast subsequent reads
                prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
                return true
            }
        } catch (_: Exception) {}

        // 3. Check Movies/GMaxRecordings or Pictures/GMaxScreenshots
        // If the user already has recordings or screenshots from a past installation, they are a returning user!
        try {
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val gmaxMovies = File(moviesDir, "GMaxRecordings")
            if (gmaxMovies.exists() && (gmaxMovies.list()?.isNotEmpty() == true)) {
                prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
                return true
            }

            val picsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val gmaxPics = File(picsDir, "GMaxScreenshots")
            if (gmaxPics.exists() && (gmaxPics.list()?.isNotEmpty() == true)) {
                prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
                return true
            }
        } catch (_: Exception) {}

        return false
    }

    /**
     * Marks onboarding completed in SharedPreferences and writes persistent external marker
     */
    fun markOnboardingCompleted(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()

        try {
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val hubFolder = File(docsDir, MARKER_DIR)
            if (!hubFolder.exists()) {
                hubFolder.mkdirs()
            }
            val marker = File(hubFolder, MARKER_FILE)
            if (!marker.exists()) {
                marker.writeText("gmax_provisioned_${System.currentTimeMillis()}")
            }
        } catch (_: Exception) {}
    }
}
