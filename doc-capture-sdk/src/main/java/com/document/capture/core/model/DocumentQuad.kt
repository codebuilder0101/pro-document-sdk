package com.document.capture.core.model

import java.io.Serializable
import kotlin.math.abs
import kotlin.math.max

/**
 * Represents a quadrilateral bounded document contour with 4 ordered corners:
 * topLeft, topRight, bottomRight, bottomLeft (clockwise order).
 */
data class DocumentQuad(
    val topLeft: PointF,
    val topRight: PointF,
    val bottomRight: PointF,
    val bottomLeft: PointF
) : Serializable {

    val corners: List<PointF>
        get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    /**
     * The 4 edge midpoints used for CamScanner-style side dragging.
     */
    val midPoints: List<PointF>
        get() = listOf(
            PointF((topLeft.x + topRight.x) / 2f, (topLeft.y + topRight.y) / 2f), // Top
            PointF((topRight.x + bottomRight.x) / 2f, (topRight.y + bottomRight.y) / 2f), // Right
            PointF((bottomRight.x + bottomLeft.x) / 2f, (bottomRight.y + bottomLeft.y) / 2f), // Bottom
            PointF((bottomLeft.x + topLeft.x) / 2f, (bottomLeft.y + topLeft.y) / 2f) // Left
        )

    fun scale(scaleX: Float, scaleY: Float): DocumentQuad {
        return DocumentQuad(
            topLeft = topLeft.scale(scaleX, scaleY),
            topRight = topRight.scale(scaleX, scaleY),
            bottomRight = bottomRight.scale(scaleX, scaleY),
            bottomLeft = bottomLeft.scale(scaleX, scaleY)
        )
    }

    fun offset(dx: Float, dy: Float): DocumentQuad {
        return DocumentQuad(
            topLeft = topLeft.offset(dx, dy),
            topRight = topRight.offset(dx, dy),
            bottomRight = bottomRight.offset(dx, dy),
            bottomLeft = bottomLeft.offset(dx, dy)
        )
    }

    /**
     * Converts to flat array of 8 coordinates [tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y]
     * for direct use in android.graphics.Matrix.setPolyToPoly.
     */
    fun toPointsArray(): FloatArray {
        return floatArrayOf(
            topLeft.x, topLeft.y,
            topRight.x, topRight.y,
            bottomRight.x, bottomRight.y,
            bottomLeft.x, bottomLeft.y
        )
    }

    /**
     * Compute polygon area using Shoelace formula.
     */
    fun getArea(): Float {
        val pts = corners
        var sum = 0f
        for (i in pts.indices) {
            val j = (i + 1) % pts.size
            sum += pts[i].x * pts[j].y - pts[j].x * pts[i].y
        }
        return abs(sum) / 2f
    }

    /**
     * Computes width as max of top and bottom edge lengths.
     */
    fun getWidth(): Float {
        val topWidth = topLeft.distanceTo(topRight)
        val bottomWidth = bottomLeft.distanceTo(bottomRight)
        return max(topWidth, bottomWidth)
    }

    /**
     * Computes height as max of left and right edge lengths.
     */
    fun getHeight(): Float {
        val leftHeight = topLeft.distanceTo(bottomLeft)
        val rightHeight = topRight.distanceTo(bottomRight)
        return max(leftHeight, rightHeight)
    }

    /**
     * Width / Height ratio
     */
    fun getAspectRatio(): Float {
        val h = getHeight()
        return if (h > 0f) getWidth() / h else 1.414f
    }

    /**
     * Checks if quadrilateral is strictly convex (all cross products have identical sign)
     */
    fun isConvex(): Boolean {
        val pts = corners
        var sign = 0
        for (i in pts.indices) {
            val p1 = pts[i]
            val p2 = pts[(i + 1) % 4]
            val p3 = pts[(i + 2) % 4]

            val dx1 = p2.x - p1.x
            val dy1 = p2.y - p1.y
            val dx2 = p3.x - p2.x
            val dy2 = p3.y - p2.y

            val crossProduct = dx1 * dy2 - dy1 * dx2
            if (crossProduct != 0f) {
                val currentSign = if (crossProduct > 0) 1 else -1
                if (sign == 0) {
                    sign = currentSign
                } else if (sign != currentSign) {
                    return false
                }
            }
        }
        return true
    }

    companion object {
        /**
         * Re-orders 4 points into canonical [topLeft, topRight, bottomRight, bottomLeft] order.
         */
        fun fromUnordered(pts: List<PointF>): DocumentQuad {
            require(pts.size == 4) { "Must provide exactly 4 points" }

            // Sort by sum of (x + y): top-left has min sum, bottom-right has max sum
            val sortedBySum = pts.sortedBy { it.x + it.y }
            val tl = sortedBySum.first()
            val br = sortedBySum.last()

            // Remaining two points: top-right has min (y - x), bottom-left has max (y - x)
            val remaining = pts.filter { it != tl && it != br }

            // Guard: degenerate input where two or more points coincide
            if (remaining.size < 2) {
                val w = (pts.maxOf { it.x } - pts.minOf { it.x }).coerceAtLeast(1f)
                val h = (pts.maxOf { it.y } - pts.minOf { it.y }).coerceAtLeast(1f)
                return defaultCentered(w, h)
            }

            val tr = remaining.minByOrNull { it.y - it.x } ?: remaining[0]
            val bl = remaining.maxByOrNull { it.y - it.x } ?: remaining[1]

            return DocumentQuad(tl, tr, br, bl)
        }

        /**
         * Creates an initial default centered quad for a given frame dimension.
         * Defaults to a clean 2% touch-friendly boundary inset so headers, serials, and signatures are preserved.
         */
        fun defaultCentered(width: Float, height: Float, marginPercent: Float = 0.02f): DocumentQuad {
            val mx = width * marginPercent
            val my = height * marginPercent
            return DocumentQuad(
                topLeft = PointF(mx, my),
                topRight = PointF(width - mx, my),
                bottomRight = PointF(width - mx, height - my),
                bottomLeft = PointF(mx, height - my)
            )
        }
    }
}
