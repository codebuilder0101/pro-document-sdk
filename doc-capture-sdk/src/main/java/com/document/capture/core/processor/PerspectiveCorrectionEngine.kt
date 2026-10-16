package com.document.capture.core.processor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.document.capture.core.model.DocumentQuad
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Perspective unwarping and rectification engine.
 * Computes the projective homography matrix to transform an arbitrary quadrilateral into a planar rectangular document.
 */
object PerspectiveCorrectionEngine {

    /**
     * Warps and rectifies the quadrilateral document region in [sourceBitmap] into a flat rectangular bitmap.
     */
    fun rectify(sourceBitmap: Bitmap, quad: DocumentQuad): Bitmap {
        // Calculate destination width & height
        val targetWidth = max(
            quad.topLeft.distanceTo(quad.topRight),
            quad.bottomLeft.distanceTo(quad.bottomRight)
        ).roundToInt().coerceAtLeast(200)

        val targetHeight = max(
            quad.topLeft.distanceTo(quad.bottomLeft),
            quad.topRight.distanceTo(quad.bottomRight)
        ).roundToInt().coerceAtLeast(200)

        val srcPoints = quad.toPointsArray()
        val dstPoints = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat(), 0f,
            targetWidth.toFloat(), targetHeight.toFloat(),
            0f, targetHeight.toFloat()
        )

        // Calculate homography matrix mapping src to dst
        val matrix = Matrix()
        val success = matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)
        if (!success) {
            // Fallback: return sub-bitmap crop if homography calculation fails
            return Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.width, sourceBitmap.height)
        }

        // Draw rectified output
        val outputBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        canvas.concat(matrix)
        canvas.drawBitmap(sourceBitmap, 0f, 0f, paint)

        return outputBitmap
    }

    /**
     * Rotates bitmap by specified degrees (e.g. 90, 180, 270).
     */
    fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360f == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
