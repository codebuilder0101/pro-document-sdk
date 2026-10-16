package com.document.capture.core.model

import android.os.Parcelable
import java.io.Serializable
import kotlin.math.hypot

/**
 * High-precision 2D floating-point coordinate for document corners and touch handles.
 */
data class PointF(
    val x: Float,
    val y: Float
) : Serializable {

    fun distanceTo(other: PointF): Float {
        return hypot(x - other.x, y - other.y)
    }

    fun offset(dx: Float, dy: Float): PointF {
        return PointF(x + dx, y + dy)
    }

    fun scale(sx: Float, sy: Float): PointF {
        return PointF(x * sx, y * sy)
    }

    companion object {
        val ZERO = PointF(0f, 0f)
    }
}
