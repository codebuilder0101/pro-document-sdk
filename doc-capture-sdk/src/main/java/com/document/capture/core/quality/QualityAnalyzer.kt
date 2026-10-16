package com.document.capture.core.quality

import android.graphics.Bitmap
import android.graphics.Color
import com.document.capture.core.model.CaptureConfig
import com.document.capture.core.model.DocumentQuad
import com.document.capture.core.model.QualityMetrics
import com.document.capture.core.model.QualityStatus
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance on-device image quality analyzer.
 * Evaluates focus blur, specular glare, lighting balance, perspective skew, and framing.
 */
object QualityAnalyzer {

    fun evaluate(
        bitmap: Bitmap,
        quad: DocumentQuad?,
        config: CaptureConfig,
        isStable: Boolean = true
    ): QualityMetrics {
        val width = bitmap.width
        val height = bitmap.height

        // If no quad detected, prompt framing
        if (quad == null || !quad.isConvex()) {
            return QualityMetrics(
                blurScore = 0f,
                glareRatio = 0f,
                meanLuminance = 128f,
                skewRatio = 0f,
                coverageRatio = 0f,
                isStable = isStable,
                status = QualityStatus.NOT_IN_FRAME,
                guidanceMessage = "Align document within the frame"
            )
        }

        val quadArea = quad.getArea()
        val totalArea = (width * height).toFloat()
        val coverage = quadArea / totalArea

        if (coverage < 0.20f) {
            return QualityMetrics(
                blurScore = 0f,
                glareRatio = 0f,
                meanLuminance = 128f,
                skewRatio = 0f,
                coverageRatio = coverage,
                isStable = isStable,
                status = QualityStatus.NOT_IN_FRAME,
                guidanceMessage = "Bring document closer"
            )
        }

        // Skew check
        val leftLen = quad.topLeft.distanceTo(quad.bottomLeft)
        val rightLen = quad.topRight.distanceTo(quad.bottomRight)
        val maxLen = max(leftLen, rightLen)
        val skewRatio = if (maxLen > 0) abs(leftLen - rightLen) / maxLen else 0f

        if (skewRatio > 0.35f) {
            return QualityMetrics(
                blurScore = 0f,
                glareRatio = 0f,
                meanLuminance = 128f,
                skewRatio = skewRatio,
                coverageRatio = coverage,
                isStable = isStable,
                status = QualityStatus.TOO_SKEWED,
                guidanceMessage = "Tilt camera to align straight with document"
            )
        }

        // Sample bitmap pixels for fast analysis (~80x80 grid within quad bounds)
        val fullPixels = IntArray(width * height)
        bitmap.getPixels(fullPixels, 0, width, 0, 0, width, height)

        // Compute bounds for sampling
        val minX = max(0, min(min(quad.topLeft.x, quad.bottomLeft.x), min(quad.topRight.x, quad.bottomRight.x)).toInt())
        val maxX = min(width - 1, max(max(quad.topLeft.x, quad.bottomLeft.x), max(quad.topRight.x, quad.bottomRight.x)).toInt())
        val minY = max(0, min(min(quad.topLeft.y, quad.topRight.y), min(quad.bottomLeft.y, quad.bottomRight.y)).toInt())
        val maxY = min(height - 1, max(max(quad.topLeft.y, quad.topRight.y), max(quad.bottomLeft.y, quad.bottomRight.y)).toInt())

        val stepX = max(1, (maxX - minX) / 80)
        val stepY = max(1, (maxY - minY) / 80)

        var totalLuminance = 0L
        var sampleCount = 0
        var glarePixelCount = 0

        for (y in minY..maxY step stepY) {
            for (x in minX..maxX step stepX) {
                val pixel = fullPixels[y * width + x]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // Standard Rec. 601 luminance
                val lum = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
                totalLuminance += lum
                sampleCount++

                // Glare detection: intense white highlight where channels saturate
                if (lum > 248 && (r > 245 && g > 245 && b > 245)) {
                    glarePixelCount++
                }
            }
        }

        val meanLum = if (sampleCount > 0) totalLuminance.toFloat() / sampleCount else 128f
        val glareRatio = if (sampleCount > 0) glarePixelCount.toFloat() / sampleCount else 0f

        // Focus & Blur via Laplacian variance on center quadrant (uses pre-extracted pixel array)
        val blurScore = computeLaplacianVariance(fullPixels, width, minX, minY, maxX, maxY)

        // Evaluate Status
        val status = when {
            meanLum < config.minLuminance -> QualityStatus.TOO_DARK
            meanLum > config.maxLuminance -> QualityStatus.TOO_BRIGHT
            glareRatio > config.maxGlareRatio -> QualityStatus.GLARE_DETECTED
            blurScore < config.minBlurScore -> QualityStatus.BLUR_DETECTED
            !isStable -> QualityStatus.UNSTEADY
            else -> QualityStatus.PASS
        }

        val message = when (status) {
            QualityStatus.PASS -> "Perfect! Hold still..."
            QualityStatus.BLUR_DETECTED -> "Focusing — hold steady"
            QualityStatus.GLARE_DETECTED -> "Glare detected — tilt away from light"
            QualityStatus.TOO_DARK -> "Too dark — improve lighting"
            QualityStatus.TOO_BRIGHT -> "Too bright — reduce harsh lighting"
            QualityStatus.UNSTEADY -> "Hold phone steady"
            QualityStatus.TOO_SKEWED -> "Align camera directly above document"
            QualityStatus.NOT_IN_FRAME -> "Position document inside frame"
        }

        return QualityMetrics(
            blurScore = blurScore,
            glareRatio = glareRatio,
            meanLuminance = meanLum,
            skewRatio = skewRatio,
            coverageRatio = coverage,
            isStable = isStable,
            status = status,
            guidanceMessage = message
        )
    }

    /**
     * Approximates Laplacian variance to measure edge high-frequency energy.
     * Uses a pre-extracted [pixels] IntArray to avoid JNI overhead per kernel sample.
     */
    private fun computeLaplacianVariance(
        pixels: IntArray,
        bitmapWidth: Int,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int
    ): Float {
        val cx1 = startX + (endX - startX) / 4
        val cx2 = startX + (endX - startX) * 3 / 4
        val cy1 = startY + (endY - startY) / 4
        val cy2 = startY + (endY - startY) * 3 / 4

        val step = max(2, (cx2 - cx1) / 50)
        var sum = 0.0
        var sumSq = 0.0
        var count = 0

        for (y in (cy1 + step) until cy2 step step) {
            for (x in (cx1 + step) until cx2 step step) {
                val center = getLuminance(pixels[y * bitmapWidth + x])
                val left   = getLuminance(pixels[y * bitmapWidth + (x - step)])
                val right  = getLuminance(pixels[y * bitmapWidth + (x + step)])
                val top    = getLuminance(pixels[(y - step) * bitmapWidth + x])
                val bottom = getLuminance(pixels[(y + step) * bitmapWidth + x])

                // Discrete Laplacian filter: 4*C - (L+R+T+B)
                val laplacian = 4 * center - (left + right + top + bottom)
                sum += laplacian
                sumSq += (laplacian * laplacian)
                count++
            }
        }

        if (count <= 1) return 150f
        val mean = sum / count
        val variance = (sumSq / count) - (mean * mean)
        return max(0f, variance.toFloat())
    }

    private fun getLuminance(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (299 * r + 587 * g + 114 * b) / 1000
    }
}
