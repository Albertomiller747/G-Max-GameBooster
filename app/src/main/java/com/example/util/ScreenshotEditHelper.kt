package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.model.CapturedScreenshot
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 6 tactical gaming color filters for the Screenshot Studio Editor.
 */
enum class ScreenshotFilterPreset(
    val labelEn: String,
    val labelEs: String,
    val badgeHex: Long,
    val matrixValues: FloatArray
) {
    ORIGINAL(
        labelEn = "Original",
        labelEs = "Original",
        badgeHex = 0xFF38BDF8,
        matrixValues = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    NIGHT_VISION(
        labelEn = "Night Vision",
        labelEs = "Visión Nocturna",
        badgeHex = 0xFF10B981,
        matrixValues = floatArrayOf(
            0.20f, 0.45f, 0.10f, 0f, 10f,
            0.35f, 1.35f, 0.35f, 0f, 28f,
            0.12f, 0.40f, 0.20f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    THERMAL_BOOST(
        labelEn = "Thermal Boost",
        labelEs = "Impulso Térmico",
        badgeHex = 0xFFFF5722,
        matrixValues = floatArrayOf(
            1.38f, 0.12f, -0.10f, 0f, 18f,
            0.05f, 1.12f, -0.05f, 0f, 4f,
            -0.15f, -0.10f, 1.25f, 0f, -12f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    CYBER_NEON(
        labelEn = "Cyber Neon",
        labelEs = "Ciber Neón",
        badgeHex = 0xFFA855F7,
        matrixValues = floatArrayOf(
            1.22f, -0.08f, 0.18f, 0f, 8f,
            -0.06f, 1.18f, 0.14f, 0f, 6f,
            0.16f, 0.10f, 1.36f, 0f, 18f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    TACTICAL_WARM(
        labelEn = "Tactical Warm",
        labelEs = "Cálido Táctico",
        badgeHex = 0xFFF59E0B,
        matrixValues = floatArrayOf(
            1.18f, 0.08f, 0.0f, 0f, 12f,
            0.04f, 1.08f, 0.0f, 0f, 6f,
            -0.06f, -0.04f, 0.88f, 0f, -8f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    NOIR_MONO(
        labelEn = "Noir Mono",
        labelEs = "Monocromo Noir",
        badgeHex = 0xFF94A3B8,
        matrixValues = floatArrayOf(
            0.38f, 0.72f, 0.14f, 0f, -10f,
            0.38f, 0.72f, 0.14f, 0f, -10f,
            0.38f, 0.72f, 0.14f, 0f, -10f,
            0f, 0f, 0f, 1f, 0f
        )
    );

    fun toAndroidColorMatrix(): ColorMatrix = ColorMatrix(matrixValues.copyOf())
}

/**
 * Aspect ratio presets for cutting/cropping screenshots.
 */
enum class ScreenshotCropAspect(
    val labelEn: String,
    val labelEs: String,
    val targetRatio: Float?
) {
    FREE("Freeform", "Libre", null),
    RATIO_16_9("16:9 Wide", "16:9 Horizontal", 16f / 9f),
    RATIO_1_1("1:1 Square", "1:1 Cuadrado", 1f),
    RATIO_9_16("9:16 Shorts", "9:16 Vertical", 9f / 16f),
    RATIO_4_3("4:3 Classic", "4:3 Clásico", 4f / 3f)
}

data class NormalizedCropBounds(
    val leftFrac: Float = 0f,
    val topFrac: Float = 0f,
    val rightFrac: Float = 1f,
    val bottomFrac: Float = 1f
) {
    fun clamped(): NormalizedCropBounds {
        val l = leftFrac.coerceIn(0f, 0.90f)
        val t = topFrac.coerceIn(0f, 0.90f)
        val r = rightFrac.coerceIn(l + 0.10f, 1f)
        val b = bottomFrac.coerceIn(t + 0.10f, 1f)
        return NormalizedCropBounds(l, t, r, b)
    }
}

data class ScreenshotEditSaveResult(
    val success: Boolean,
    val savedFilePath: String,
    val savedUriString: String,
    val widthPx: Int,
    val heightPx: Int,
    val messageEn: String,
    val messageEs: String
)

object ScreenshotEditHelper {

    /**
     * Computes centered normalized crop bounds `[0..1]` that match [aspect] for a source image of `[sourceWidth] x [sourceHeight]`.
     */
    fun computeAspectBounds(
        sourceWidth: Int,
        sourceHeight: Int,
        aspect: ScreenshotCropAspect
    ): NormalizedCropBounds {
        val ratio = aspect.targetRatio ?: return NormalizedCropBounds(0f, 0f, 1f, 1f)
        val safeW = sourceWidth.coerceAtLeast(1).toFloat()
        val safeH = sourceHeight.coerceAtLeast(1).toFloat()
        val srcAspect = safeW / safeH

        return if (srcAspect > ratio) {
            // Source is wider than target aspect: full height, centered width
            val targetPixelW = (safeH * ratio).coerceAtMost(safeW)
            val widthFrac = (targetPixelW / safeW).coerceIn(0.15f, 1f)
            val marginX = (1f - widthFrac) / 2f
            NormalizedCropBounds(
                leftFrac = marginX,
                topFrac = 0f,
                rightFrac = 1f - marginX,
                bottomFrac = 1f
            ).clamped()
        } else {
            // Source is taller than target aspect: full width, centered height
            val targetPixelH = (safeW / ratio).coerceAtMost(safeH)
            val heightFrac = (targetPixelH / safeH).coerceIn(0.15f, 1f)
            val marginY = (1f - heightFrac) / 2f
            NormalizedCropBounds(
                leftFrac = 0f,
                topFrac = marginY,
                rightFrac = 1f,
                bottomFrac = 1f - marginY
            ).clamped()
        }
    }

    /**
     * Cuts/crops [source] to the normalized rectangle `[leftFrac, topFrac, rightFrac, bottomFrac]`
     * and applies the selected [filterPreset] `ColorMatrixColorFilter` onto a newly rendered `Bitmap`.
     */
    fun cropAndFilterBitmap(
        source: Bitmap,
        bounds: NormalizedCropBounds,
        filterPreset: ScreenshotFilterPreset
    ): Bitmap {
        val clamped = bounds.clamped()
        val srcW = source.width.coerceAtLeast(1)
        val srcH = source.height.coerceAtLeast(1)

        val cropX = (clamped.leftFrac * srcW).roundToInt().coerceIn(0, (srcW - 1).coerceAtLeast(0))
        val cropY = (clamped.topFrac * srcH).roundToInt().coerceIn(0, (srcH - 1).coerceAtLeast(0))
        val cropW = ((clamped.rightFrac - clamped.leftFrac) * srcW).roundToInt()
            .coerceIn(1, (srcW - cropX).coerceAtLeast(1))
        val cropH = ((clamped.bottomFrac - clamped.topFrac) * srcH).roundToInt()
            .coerceIn(1, (srcH - cropY).coerceAtLeast(1))

        val cropped = Bitmap.createBitmap(source, cropX, cropY, cropW, cropH)
        if (filterPreset == ScreenshotFilterPreset.ORIGINAL) {
            return cropped
        }

        val outBitmap = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(filterPreset.toAndroidColorMatrix())
        }
        canvas.drawBitmap(cropped, 0f, 0f, paint)

        // Fallback pixel transform if Canvas shadow did not apply ColorMatrixColorFilter (e.g. JVM unit tests)
        val midX = cropW / 2
        val midY = cropH / 2
        if (outBitmap.getPixel(midX, midY) == cropped.getPixel(midX, midY) || outBitmap.getPixel(midX, midY) == 0) {
            applyColorMatrixToPixels(cropped, outBitmap, filterPreset.matrixValues)
        }

        if (cropped !== source && cropped !== outBitmap) {
            try {
                cropped.recycle()
            } catch (_: Exception) {}
        }
        return outBitmap
    }

    private fun applyColorMatrixToPixels(src: Bitmap, dst: Bitmap, m: FloatArray) {
        if (m.size < 20) return
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val a = (c ushr 24) and 0xFF
            val r = (c ushr 16) and 0xFF
            val g = (c ushr 8) and 0xFF
            val b = c and 0xFF

            val nr = (m[0] * r + m[1] * g + m[2] * b + m[3] * a + m[4]).roundToInt().coerceIn(0, 255)
            val ng = (m[5] * r + m[6] * g + m[7] * b + m[8] * a + m[9]).roundToInt().coerceIn(0, 255)
            val nb = (m[10] * r + m[11] * g + m[12] * b + m[13] * a + m[14]).roundToInt().coerceIn(0, 255)
            val na = (m[15] * r + m[16] * g + m[17] * b + m[18] * a + m[19]).roundToInt().coerceIn(0, 255)

            pixels[i] = (na shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
        dst.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    /**
     * Loads a decodeable [Bitmap] from a [CapturedScreenshot] file path or content URI.
     */
    fun loadScreenshotBitmap(context: Context, screenshot: CapturedScreenshot): Bitmap? {
        return try {
            val f = File(screenshot.filePath)
            if (f.exists()) {
                BitmapFactory.decodeFile(screenshot.filePath)
            } else if (screenshot.uriString.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(screenshot.uriString))?.use {
                    BitmapFactory.decodeStream(it)
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Saves the cropped and color-filtered [editedBitmap] either overwriting [original] or creating a new
     * screenshot file in the GMaxRecorder Screenshots library.
     */
    fun saveEditedScreenshot(
        context: Context,
        original: CapturedScreenshot,
        editedBitmap: Bitmap,
        overwriteOriginal: Boolean,
        filterPreset: ScreenshotFilterPreset
    ): ScreenshotEditSaveResult {
        val w = editedBitmap.width
        val h = editedBitmap.height
        return try {
            if (overwriteOriginal) {
                val origFile = File(original.filePath)
                if (origFile.exists() && origFile.canWrite()) {
                    FileOutputStream(origFile, false).use { out ->
                        editedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        out.flush()
                    }
                    return ScreenshotEditSaveResult(
                        success = true,
                        savedFilePath = origFile.absolutePath,
                        savedUriString = original.uriString.ifBlank { Uri.fromFile(origFile).toString() },
                        widthPx = w,
                        heightPx = h,
                        messageEn = "Overwrote '${original.title}' (${w}x${h} • ${filterPreset.labelEn})",
                        messageEs = "Sobrescrito '${original.title}' (${w}x${h} • ${filterPreset.labelEs})"
                    )
                } else if (original.uriString.startsWith("content://")) {
                    val uri = Uri.parse(original.uriString)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        editedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        out.flush()
                    }
                    return ScreenshotEditSaveResult(
                        success = true,
                        savedFilePath = original.filePath,
                        savedUriString = original.uriString,
                        widthPx = w,
                        heightPx = h,
                        messageEn = "Overwrote '${original.title}' (${w}x${h} • ${filterPreset.labelEn})",
                        messageEs = "Sobrescrito '${original.title}' (${w}x${h} • ${filterPreset.labelEs})"
                    )
                }
            }

            // Save as a new copy in Pictures/GMaxScreenshots or app-scoped Pictures/GMaxScreenshots
            val slug = original.gameName.replace(Regex("[^A-Za-z0-9]"), "").ifBlank { "Gameplay" }.take(18)
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val newFileName = "GMax_Shot_${slug}_Edit_${stamp}.png"

            // Also write to app-scoped external pictures dir so it is always immediately queryable in all environments
            val appPicsDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir,
                "GMaxScreenshots"
            )
            if (!appPicsDir.exists()) appPicsDir.mkdirs()
            val localOutFile = File(appPicsDir, newFileName)
            FileOutputStream(localOutFile).use { out ->
                editedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }

            var savedUriStr = Uri.fromFile(localOutFile).toString()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, newFileName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            "${Environment.DIRECTORY_PICTURES}/GMaxScreenshots"
                        )
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { out ->
                            editedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                            out.flush()
                        }
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                        savedUriStr = uri.toString()
                    }
                } catch (_: Exception) {}
            }

            ScreenshotEditSaveResult(
                success = true,
                savedFilePath = localOutFile.absolutePath,
                savedUriString = savedUriStr,
                widthPx = w,
                heightPx = h,
                messageEn = "Saved edited screenshot '$newFileName' (${w}x${h} • ${filterPreset.labelEn})",
                messageEs = "Captura editada guardada '$newFileName' (${w}x${h} • ${filterPreset.labelEs})"
            )
        } catch (e: Exception) {
            ScreenshotEditSaveResult(
                success = false,
                savedFilePath = "",
                savedUriString = "",
                widthPx = w,
                heightPx = h,
                messageEn = "Could not save edited screenshot: ${e.message ?: "Error"}",
                messageEs = "No se pudo guardar la captura editada"
            )
        }
    }
}
