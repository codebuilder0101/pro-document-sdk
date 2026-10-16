package com.document.capture.core.detector

import android.graphics.Bitmap
import com.document.capture.core.model.DocumentQuad
import com.document.capture.core.model.PointF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Real-time Document Edge & Quadrilateral Detector.
 * Analyzes contrast boundaries and corner gradients to accurately localize rectangular documents.
 */
class DocumentDetector {

    private var previousQuad: DocumentQuad? = null
    private val smoothingFactor = 0.35f // Exponential moving average weight

    /**
     * Detects document quadrilateral in given bitmap.
     * @param bitmap The frame bitmap to analyze.
     * @param useSmoothing Whether to apply temporal smoothing across frames.
     */
    fun detect(bitmap: Bitmap, useSmoothing: Boolean = true): DocumentQuad {
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()

        val rawQuad = findDocumentBoundaries(bitmap) ?: DocumentQuad.defaultCentered(width, height)

        val smoothedQuad = if (useSmoothing && previousQuad != null) {
            smoothQuads(previousQuad!!, rawQuad, smoothingFactor)
        } else {
            rawQuad
        }

        previousQuad = smoothedQuad
        return smoothedQuad
    }

    /**
     * Resets temporal history when switching cameras or starting fresh capture.
     */
    fun reset() {
        previousQuad = null
    }

    private fun findDocumentBoundaries(bitmap: Bitmap): DocumentQuad? {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 100 || h < 100) return null

        // Extract all pixels once to avoid per-pixel JNI overhead in hot loops
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Downscale analysis grid for real-time 30+ FPS speed
        val stepX = max(2, w / 100)
        val stepY = max(2, h / 100)

        // Find boundary edges using directional gradient peaks
        val minMarginX = (w * 0.05f).toInt()
        val maxMarginX = (w * 0.95f).toInt()
        val minMarginY = (h * 0.05f).toInt()
        val maxMarginY = (h * 0.95f).toInt()

        var leftBound = (w * 0.10f).toInt()
        var rightBound = (w * 0.90f).toInt()
        var topBound = (h * 0.12f).toInt()
        var bottomBound = (h * 0.88f).toInt()

        // 1. Scan from left to right along horizontal rows
        val leftCandidate = scanBoundary(pixels, w, h, minMarginX, w / 3, minMarginY, maxMarginY, stepX, stepY, isHorizontal = true, isForward = true)
        if (leftCandidate > minMarginX) leftBound = leftCandidate

        // 2. Scan from right to left
        val rightCandidate = scanBoundary(pixels, w, h, maxMarginX, w * 2 / 3, minMarginY, maxMarginY, -stepX, stepY, isHorizontal = true, isForward = false)
        if (rightCandidate < maxMarginX) rightBound = rightCandidate

        // 3. Scan from top to bottom
        val topCandidate = scanBoundary(pixels, w, h, minMarginY, h / 3, minMarginX, maxMarginX, stepY, stepX, isHorizontal = false, isForward = true)
        if (topCandidate > minMarginY) topBound = topCandidate

        // 4. Scan from bottom to top
        val bottomCandidate = scanBoundary(pixels, w, h, maxMarginY, h * 2 / 3, minMarginX, maxMarginX, -stepY, stepX, isHorizontal = false, isForward = false)
        if (bottomCandidate < maxMarginY) bottomBound = bottomCandidate

        // Ensure minimum valid size
        if (rightBound - leftBound < w * 0.3f || bottomBound - topBound < h * 0.3f) {
            return DocumentQuad.defaultCentered(w.toFloat(), h.toFloat())
        }

        val quad = DocumentQuad(
            topLeft = PointF(leftBound.toFloat(), topBound.toFloat()),
            topRight = PointF(rightBound.toFloat(), topBound.toFloat()),
            bottomRight = PointF(rightBound.toFloat(), bottomBound.toFloat()),
            bottomLeft = PointF(leftBound.toFloat(), bottomBound.toFloat())
        )

        return if (quad.isConvex()) quad else DocumentQuad.defaultCentered(w.toFloat(), h.toFloat())
    }

    /**
     * Scans along a primary axis accumulating gradient energy per scan line.
     *
     * Uses a bulk [pixels] array pre-extracted via [Bitmap.getPixels] to eliminate
     * per-pixel JNI overhead. The gradient neighbor is always sampled leftward (horizontal)
     * or upward (vertical) using abs(scanStep)/2 — fixing the prior bug where backward
     * scans sampled in the wrong direction.
     */
    private fun scanBoundary(
        pixels: IntArray,
        bitmapWidth: Int,
        bitmapHeight: Int,
        startScan: Int,
        endScan: Int,
        orthoStart: Int,
        orthoEnd: Int,
        scanStep: Int,
        orthoStep: Int,
        isHorizontal: Boolean,
        isForward: Boolean
    ): Int {
        var strongestEdgeCoord = startScan
        var maxTotalGradient = 0L

        // Always sample leftward/upward regardless of scan direction for consistent gradient
        val neighborOffset = abs(scanStep) / 2

        var current = startScan
        val condition: (Int) -> Boolean = if (isForward) { c -> c <= endScan } else { c -> c >= endScan }

        while (condition(current)) {
            var rowGradientSum = 0L

            for (ortho in orthoStart until orthoEnd step orthoStep) {
                val x = if (isHorizontal) current else ortho
                val y = if (isHorizontal) ortho else current

                if (x in 0 until bitmapWidth && y in 0 until bitmapHeight) {
                    val pCurrent = getLuminance(pixels[y * bitmapWidth + x])
                    val pPrev = if (isHorizontal) {
                        val prevX = (x - neighborOffset).coerceAtLeast(0)
                        getLuminance(pixels[y * bitmapWidth + prevX])
                    } else {
                        val prevY = (y - neighborOffset).coerceAtLeast(0)
                        getLuminance(pixels[prevY * bitmapWidth + x])
                    }
                    val diff = abs(pCurrent - pPrev)
                    rowGradientSum += diff
                }
            }

            if (rowGradientSum > maxTotalGradient) {
                maxTotalGradient = rowGradientSum
                strongestEdgeCoord = current
            }

            current += scanStep
        }

        return strongestEdgeCoord
    }

    private fun smoothQuads(q1: DocumentQuad, q2: DocumentQuad, alpha: Float): DocumentQuad {
        return DocumentQuad(
            topLeft = smoothPoint(q1.topLeft, q2.topLeft, alpha),
            topRight = smoothPoint(q1.topRight, q2.topRight, alpha),
            bottomRight = smoothPoint(q1.bottomRight, q2.bottomRight, alpha),
            bottomLeft = smoothPoint(q1.bottomLeft, q2.bottomLeft, alpha)
        )
    }

    private fun smoothPoint(p1: PointF, p2: PointF, alpha: Float): PointF {
        return PointF(
            p1.x * (1f - alpha) + p2.x * alpha,
            p1.y * (1f - alpha) + p2.y * alpha
        )
    }

    private fun getLuminance(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (299 * r + 587 * g + 114 * b) / 1000
    }
}
