package com.document.capture.ui.liveness

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.document.capture.ui.theme.DocumentCaptureTheme

/**
 * Biometric Face Oval overlay for liveness selfie capture.
 */
class FaceOvalOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var theme: DocumentCaptureTheme = DocumentCaptureTheme.current
    var isFaceAligned: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val maskPaint = Paint().apply {
        color = Color.parseColor("#B0000000")
        style = Paint.Style.FILL
    }

    private val ovalBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 8f
        style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height * 0.42f
        val radiusX = width * 0.35f
        val radiusY = height * 0.25f

        val ovalRect = RectF(cx - radiusX, cy - radiusY, cx + radiusX, cy + radiusY)

        // 1. Darkened backdrop with punched-out oval
        val path = Path().apply {
            fillType = Path.FillType.INVERSE_WINDING
            addOval(ovalRect, Path.Direction.CW)
        }
        canvas.drawPath(path, maskPaint)

        // 2. Oval border with dynamic alignment color
        ovalBorderPaint.color = if (isFaceAligned) theme.accentColor else Color.WHITE
        canvas.drawOval(ovalRect, ovalBorderPaint)
    }
}
