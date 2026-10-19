package com.document.capture.core.detector

import android.graphics.Bitmap
import com.document.capture.core.model.DocumentQuad
import com.document.capture.core.model.PointF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Real-time Document Edge & Quadrilateral Detector.
 * Analyzes contrast boundaries, edge continuity, boundary step contrast,
 * and substrate corner coherence to accurately distinguish genuine documents from background clutter.
 */
class DocumentDetector {

    private var previousQuad: DocumentQuad? = null
    private val smoothingFactor = 0.35f

    /**
     * Detects document quadrilateral in given bitmap.
     * @param bitmap The frame bitmap to analyze.
     * @param useSmoothing Whether to apply temporal smoothing across frames.
     * @return Detected [DocumentQuad] if a distinct document is present, or null if no document is detected.
     */
    fun detect(bitmap: Bitmap, useSmoothing: Boolean = true): DocumentQuad? {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 100 || h < 100) return null

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val rawQuad = findDocumentBoundaries(pixels, w, h)

        if (rawQuad == null) {
            previousQuad = null
            return null
        }

        val smoothedQuad = if (useSmoothing && previousQuad != null) {
            val dTl = rawQuad.topLeft.distanceTo(previousQuad!!.topLeft)
            val dBr = rawQuad.bottomRight.distanceTo(previousQuad!!.bottomRight)
            if (max(dTl, dBr) < 80f) {
                smoothQuads(previousQuad!!, rawQuad, smoothingFactor)
            } else {
                rawQuad
            }
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

    private data class BoundaryResult(
        val coordinate: Int,
        val avgGradient: Float,
        val continuityRatio: Float,
        val isValidEdge: Boolean
    )

    fun findDocumentBoundaries(pixels: IntArray, w: Int, h: Int): DocumentQuad? {
        if (w < 100 || h < 100) return null

        // Downscale analysis grid for real-time 30+ FPS speed
        val stepX = max(2, w / 100)
        val stepY = max(2, h / 100)

        // Bounded search margins starting near image boundary
        val minMarginX = max(0, (w * 0.02f).toInt())
        val maxMarginX = min(w - 1, (w * 0.98f).toInt())
        val minMarginY = max(0, (h * 0.02f).toInt())
        val maxMarginY = min(h - 1, (h * 0.98f).toInt())

        // 1. Scan from left to right along horizontal rows
        val leftRes = scanBoundary(pixels, w, h, minMarginX, w / 3, minMarginY, maxMarginY, stepX, stepY, isHorizontal = true, isForward = true)

        // 2. Scan from right to left
        val rightRes = scanBoundary(pixels, w, h, maxMarginX, w * 2 / 3, minMarginY, maxMarginY, -stepX, stepY, isHorizontal = true, isForward = false)

        // 3. Scan from top to bottom
        val topRes = scanBoundary(pixels, w, h, minMarginY, h / 3, minMarginX, maxMarginX, stepY, stepX, isHorizontal = false, isForward = true)

        // 4. Scan from bottom to top
        val bottomRes = scanBoundary(pixels, w, h, maxMarginY, h * 2 / 3, minMarginX, maxMarginX, -stepY, stepX, isHorizontal = false, isForward = false)

        // A genuine document MUST have all 4 continuous, high-contrast enclosing edges
        if (!leftRes.isValidEdge || !rightRes.isValidEdge || !topRes.isValidEdge || !bottomRes.isValidEdge) {
            return null
        }

        val leftBound = leftRes.coordinate
        val rightBound = rightRes.coordinate
        val topBound = topRes.coordinate
        val bottomBound = bottomRes.coordinate

        val docWidth = (rightBound - leftBound).toFloat()
        val docHeight = (bottomBound - topBound).toFloat()

        // Size check: document must occupy reasonable area
        if (docWidth < w * 0.28f || docHeight * docWidth < (w * h) * 0.15f) {
            return null
        }

        // Plausible document aspect ratio check (between 0.45 and 2.30)
        val aspectRatio = docWidth / docHeight
        if (aspectRatio !in 0.45f..2.30f) {
            return null
        }

        // Substrate coherence: the 4 interior corners must belong to the same document substrate
        if (!isSubstrateUniform(pixels, w, h, leftBound, rightBound, topBound, bottomBound)) {
            return null
        }

        // Boundary step contrast: interior document surface must have distinct step from exterior background
        if (!verifyBoundaryContrastSteps(pixels, w, h, leftBound, rightBound, topBound, bottomBound)) {
            return null
        }

        val quad = DocumentQuad(
            topLeft = PointF(leftBound.toFloat(), topBound.toFloat()),
            topRight = PointF(rightBound.toFloat(), topBound.toFloat()),
            bottomRight = PointF(rightBound.toFloat(), bottomBound.toFloat()),
            bottomLeft = PointF(leftBound.toFloat(), bottomBound.toFloat())
        )

        return if (quad.isConvex()) quad else null
    }

    /**
     * Scans along a primary axis measuring edge gradient energy and spatial continuity.
     * The neighbor is sampled in the backward direction from the scan movement
     * so that the transition from outside to inside is always captured.
     * When scanning inward from the outside, the outermost valid edge peak is selected
     * and scanning terminates once that transition has settled, preventing inner text boxes
     * and headers from hijacking the document boundary.
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
    ): BoundaryResult {
        var strongestEdgeCoord = startScan
        var maxTotalGradient = 0L
        var bestEdgePointCount = 0
        var sampleCountAtMax = 0

        val offset = max(1, abs(scanStep) / 2)
        val pointContrastThreshold = 20

        var current = startScan
        val condition: (Int) -> Boolean = if (isForward) { c -> c <= endScan } else { c -> c >= endScan }

        var foundOuterEdge = false
        var postPeakSteps = 0

        while (condition(current)) {
            var rowGradientSum = 0L
            var currentSamples = 0
            var edgePoints = 0

            for (ortho in orthoStart until orthoEnd step orthoStep) {
                val x = if (isHorizontal) current else ortho
                val y = if (isHorizontal) ortho else current

                if (x in 0 until bitmapWidth && y in 0 until bitmapHeight) {
                    val pCurrent = getLuminance(pixels[y * bitmapWidth + x])
                    val pPrev = if (isHorizontal) {
                        val prevX = if (isForward) (x - offset).coerceAtLeast(0) else (x + offset).coerceAtMost(bitmapWidth - 1)
                        getLuminance(pixels[y * bitmapWidth + prevX])
                    } else {
                        val prevY = if (isForward) (y - offset).coerceAtLeast(0) else (y + offset).coerceAtMost(bitmapHeight - 1)
                        getLuminance(pixels[prevY * bitmapWidth + x])
                    }
                    val diff = abs(pCurrent - pPrev)
                    rowGradientSum += diff
                    if (diff >= pointContrastThreshold) {
                        edgePoints++
                    }
                    currentSamples++
                }
            }

            val currentAvg = if (currentSamples > 0) rowGradientSum.toFloat() / currentSamples else 0f
            val currentContinuity = if (currentSamples > 0) edgePoints.toFloat() / currentSamples else 0f
            val isCurrentValid = currentAvg >= 16.0f && currentContinuity >= 0.30f

            if (isCurrentValid) {
                foundOuterEdge = true
                if (rowGradientSum > maxTotalGradient) {
                    maxTotalGradient = rowGradientSum
                    strongestEdgeCoord = current
                    sampleCountAtMax = currentSamples
                    bestEdgePointCount = edgePoints
                    postPeakSteps = 0
                } else {
                    postPeakSteps++
                }
            } else if (foundOuterEdge) {
                postPeakSteps++
            }

            // Once the outer document boundary peak transition is passed, stop scanning to prevent inner lines from hijacking the quad
            if (foundOuterEdge && postPeakSteps >= 2) {
                break
            }

            current += scanStep
        }

        val avgGradient = if (sampleCountAtMax > 0) maxTotalGradient.toFloat() / sampleCountAtMax else 0f
        val continuity = if (sampleCountAtMax > 0) bestEdgePointCount.toFloat() / sampleCountAtMax else 0f
        val isValid = avgGradient >= 16.0f && continuity >= 0.30f

        return BoundaryResult(strongestEdgeCoord, avgGradient, continuity, isValid)
    }


    /**
     * Checks if the 4 interior corners of the candidate quad have coherent luminance.
     * Documents (cards, papers) have a consistent substrate color.
     * Disparate room objects (laptops + desk + walls) have huge standard deviation (> 42).
     */
    fun isSubstrateUniform(
        pixels: IntArray,
        w: Int,
        h: Int,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int
    ): Boolean {
        val insetX = ((right - left) * 0.05f).toInt().coerceAtLeast(2)
        val insetY = ((bottom - top) * 0.05f).toInt().coerceAtLeast(2)
        val patchRadius = 2

        val corners = listOf(
            Pair(left + insetX, top + insetY),
            Pair(right - insetX, top + insetY),
            Pair(right - insetX, bottom - insetY),
            Pair(left + insetX, bottom - insetY)
        )

        val cornerLums = mutableListOf<Float>()

        for ((cx, cy) in corners) {
            var sum = 0L
            var count = 0
            for (dy in -patchRadius..patchRadius) {
                for (dx in -patchRadius..patchRadius) {
                    val px = (cx + dx).coerceIn(0, w - 1)
                    val py = (cy + dy).coerceIn(0, h - 1)
                    sum += getLuminance(pixels[py * w + px])
                    count++
                }
            }
            cornerLums.add(if (count > 0) sum.toFloat() / count else 128f)
        }

        val mean = cornerLums.average().toFloat()
        var variance = 0f
        for (lum in cornerLums) {
            val d = lum - mean
            variance += d * d
        }
        val stdDev = sqrt(variance / cornerLums.size)

        return stdDev < 42f
    }

    /**
     * Verifies that each of the 4 candidate boundaries separates an interior document surface
     * from the exterior background with a consistent contrast step.
     */
    fun verifyBoundaryContrastSteps(
        pixels: IntArray,
        w: Int,
        h: Int,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int
    ): Boolean {
        val offset = 6
        val minStep = 13.0f

        // 1. Left boundary: inside (left + offset) vs outside (left - offset)
        var leftStepSum = 0L
        var leftCount = 0
        val yStart = top + ((bottom - top) * 0.15f).toInt()
        val yEnd = bottom - ((bottom - top) * 0.15f).toInt()
        val yStep = max(2, (yEnd - yStart) / 25)

        for (y in yStart until yEnd step yStep) {
            val inX = (left + offset).coerceIn(0, w - 1)
            val outX = (left - offset).coerceIn(0, w - 1)
            leftStepSum += abs(getLuminance(pixels[y * w + inX]) - getLuminance(pixels[y * w + outX]))
            leftCount++
        }
        val leftAvgStep = if (leftCount > 0) leftStepSum.toFloat() / leftCount else 0f

        // 2. Right boundary: inside (right - offset) vs outside (right + offset)
        var rightStepSum = 0L
        var rightCount = 0
        for (y in yStart until yEnd step yStep) {
            val inX = (right - offset).coerceIn(0, w - 1)
            val outX = (right + offset).coerceIn(0, w - 1)
            rightStepSum += abs(getLuminance(pixels[y * w + inX]) - getLuminance(pixels[y * w + outX]))
            rightCount++
        }
        val rightAvgStep = if (rightCount > 0) rightStepSum.toFloat() / rightCount else 0f

        // 3. Top boundary: inside (top + offset) vs outside (top - offset)
        var topStepSum = 0L
        var topCount = 0
        val xStart = left + ((right - left) * 0.15f).toInt()
        val xEnd = right - ((right - left) * 0.15f).toInt()
        val xStep = max(2, (xEnd - xStart) / 25)

        for (x in xStart until xEnd step xStep) {
            val inY = (top + offset).coerceIn(0, h - 1)
            val outY = (top - offset).coerceIn(0, h - 1)
            topStepSum += abs(getLuminance(pixels[inY * w + x]) - getLuminance(pixels[outY * w + x]))
            topCount++
        }
        val topAvgStep = if (topCount > 0) topStepSum.toFloat() / topCount else 0f

        // 4. Bottom boundary: inside (bottom - offset) vs outside (bottom + offset)
        var bottomStepSum = 0L
        var bottomCount = 0
        for (x in xStart until xEnd step xStep) {
            val inY = (bottom - offset).coerceIn(0, h - 1)
            val outY = (bottom + offset).coerceIn(0, h - 1)
            bottomStepSum += abs(getLuminance(pixels[inY * w + x]) - getLuminance(pixels[outY * w + x]))
            bottomCount++
        }
        val bottomAvgStep = if (bottomCount > 0) bottomStepSum.toFloat() / bottomCount else 0f

        var passingSteps = 0
        if (leftAvgStep >= minStep) passingSteps++
        if (rightAvgStep >= minStep) passingSteps++
        if (topAvgStep >= minStep) passingSteps++
        if (bottomAvgStep >= minStep) passingSteps++

        return passingSteps >= 3
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
