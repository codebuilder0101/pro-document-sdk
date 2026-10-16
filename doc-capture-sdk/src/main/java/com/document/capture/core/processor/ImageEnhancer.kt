package com.document.capture.core.processor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.document.capture.core.model.EnhancementMode
import kotlin.math.max
import kotlin.math.min

/**
 * CamScanner-style enhancement filters: Original, Magic Color, and Crisp B&W.
 */
object ImageEnhancer {

    fun apply(bitmap: Bitmap, mode: EnhancementMode): Bitmap {
        return when (mode) {
            EnhancementMode.ORIGINAL -> bitmap
            EnhancementMode.MAGIC_COLOR -> applyMagicColor(bitmap)
            EnhancementMode.CRISP_BW -> applyCrispBw(bitmap)
        }
    }

    /**
     * CamScanner "Magic Color": Boosts text contrast and edge clarity while keeping color photo/stamps vivid.
     */
    private fun applyMagicColor(src: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Combined Contrast + Slight Saturation Boost + Brightness normalization
        val contrast = 1.35f
        val brightness = -10f
        val saturation = 1.25f

        val cm = ColorMatrix()
        cm.setSaturation(saturation)

        // Scale contrast
        val scale = contrast
        val translate = (-0.5f * scale + 0.5f) * 255f + brightness

        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        contrastMatrix.preConcat(cm)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(contrastMatrix)
        }

        canvas.drawBitmap(src, 0f, 0f, paint)
        return output
    }

    /**
     * High-contrast document binarization for clean printed text on white background.
     */
    private fun applyCrispBw(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        // Compute adaptive threshold using Otsu approximation
        var sum = 0L
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            sum += (299 * r + 587 * g + 114 * b) / 1000
        }
        val avgLum = (sum / pixels.size).toInt()
        val threshold = (avgLum * 0.88f).toInt().coerceIn(70, 180)

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val lum = (299 * r + 587 * g + 114 * b) / 1000

            pixels[i] = if (lum < threshold) {
                Color.BLACK
            } else {
                Color.WHITE
            }
        }

        output.setPixels(pixels, 0, w, 0, 0, w, h)
        return output
    }
}
