package com.example.model

data class ResolutionPreset(
    val label: String,        // e.g. "1080p", "720p", "4K"
    val width: Int,          // e.g. 1920
    val height: Int,         // e.g. 1080
    val minRamGbRequired: Double = 2.0
)

data class VideoQualitySettings(
    val resolutionLabel: String = "1080p",
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 60,
    val bitrateMbps: Int = 8,
    val recordMic: Boolean = true,
    val showTimerHud: Boolean = true,
    val isOverlayEnabled: Boolean = false
)

data class RecordedVideo(
    val id: Long,
    val uriString: String,
    val title: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val resolution: String,
    val filePath: String
) {
    val formattedDuration: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
        }

    val formattedSize: String
        get() {
            val mb = sizeBytes / (1024.0 * 1024.0)
            return if (mb >= 1000) {
                String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
            } else {
                String.format(java.util.Locale.US, "%.1f MB", mb)
            }
        }
}
