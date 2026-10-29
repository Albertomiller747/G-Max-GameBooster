package com.example.model

data class ResolutionPreset(
    val label: String,        // e.g. "1080p", "720p", "4K"
    val width: Int,          // e.g. 1920
    val height: Int,         // e.g. 1080
    val minRamGbRequired: Double = 2.0
)

enum class AudioSourceMode(val label: String, val shortLabel: String, val description: String) {
    INTERNAL_ONLY(
        label = "Internal Audio",
        shortLabel = "Internal",
        description = "Records game & device sound directly without background mic noise"
    ),
    INTERNAL_AND_MIC(
        label = "Internal + Mic",
        shortLabel = "Game + Mic",
        description = "Records both in-game sound and your microphone voice commentary"
    ),
    MIC_ONLY(
        label = "Microphone Only",
        shortLabel = "Mic Only",
        description = "Records microphone audio only"
    ),
    MUTE(
        label = "Mute Audio",
        shortLabel = "Muted",
        description = "Records video without any audio track"
    )
}

data class VideoQualitySettings(
    val resolutionLabel: String = "1080p",
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 60,
    val bitrateMbps: Int = 8,
    val recordMic: Boolean = false,
    val audioSourceMode: AudioSourceMode = AudioSourceMode.INTERNAL_ONLY,
    val showTimerHud: Boolean = false,
    val isOverlayEnabled: Boolean = false,
    val bubbleSizeDp: Int = 38
)

data class RecordedVideo(
    val id: Long,
    val uriString: String,
    val title: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val resolution: String,
    val filePath: String,
    val gameName: String = "General Gameplay",
    val gamePackage: String? = null
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

data class CapturedScreenshot(
    val id: Long,
    val uriString: String,
    val title: String,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val width: Int,
    val height: Int,
    val filePath: String,
    val gameName: String = "General Gameplay",
    val gamePackage: String? = null
) {
    val resolution: String
        get() = "${width}x${height}"

    val formattedSize: String
        get() {
            val kb = sizeBytes / 1024.0
            return if (kb >= 1024) {
                String.format(java.util.Locale.US, "%.2f MB", kb / 1024.0)
            } else {
                String.format(java.util.Locale.US, "%.0f KB", kb)
            }
        }
}

data class GameMediaGroup<T>(
    val gameName: String,
    val gamePackage: String?,
    val items: List<T>
)

data class StorageCapacityInfo(
    val freeBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val availableGbText: String = "0.00 GB available",
    val recordableTimeText: String = "(00:00:00 can be recorded)",
    val usedRatio: Float = 0.5f
)
