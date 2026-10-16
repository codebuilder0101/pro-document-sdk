package com.document.capture.ui.camera

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.document.capture.core.model.DocumentQuad
import com.document.capture.core.model.PointF
import com.document.capture.core.model.QualityMetrics
import com.document.capture.core.model.QualityStatus
import com.document.capture.ui.theme.DocumentCaptureTheme

/**
 * Camera Viewfinder Overlay displaying real-time quadrilateral document frame,
 * quality feedback colors, and auto-capture countdown progress.
 */
class DocumentOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var theme: DocumentCaptureTheme = DocumentCaptureTheme.current
    private var targetQuad: DocumentQuad? = null
    private var currentQuad: DocumentQuad? = null
    private var qualityMetrics: QualityMetrics = QualityMetrics.initial()
    private var autoCaptureProgress: Float = 0f

    private val maskPaint = Paint().apply {
        color = Color.parseColor("#88000000")
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }

    private val cornerBracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 12f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
        strokeWidth = 10f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    fun updateFrame(
        quad: DocumentQuad?,
        metrics: QualityMetrics,
        progress: Float
    ) {
        targetQuad = quad
        qualityMetrics = metrics
        autoCaptureProgress = progress

        // Smooth interpolation
        if (currentQuad == null && quad != null) {
            currentQuad = quad
        } else if (currentQuad != null && quad != null) {
            val alpha = 0.40f
            currentQuad = DocumentQuad(
                topLeft = smoothPoint(currentQuad!!.topLeft, quad.topLeft, alpha),
                topRight = smoothPoint(currentQuad!!.topRight, quad.topRight, alpha),
                bottomRight = smoothPoint(currentQuad!!.bottomRight, quad.bottomRight, alpha),
                bottomLeft = smoothPoint(currentQuad!!.bottomLeft, quad.bottomLeft, alpha)
            )
        } else if (quad == null) {
            currentQuad = null
        }

        invalidate()
    }

    private fun smoothPoint(p1: PointF, p2: PointF, alpha: Float): PointF {
        return PointF(p1.x * (1f - alpha) + p2.x * alpha, p1.y * (1f - alpha) + p2.y * alpha)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val quad = currentQuad ?: DocumentQuad.defaultCentered(width.toFloat(), height.toFloat())
        val pts = quad.corners

        // Update border color based on quality status
        val borderColor = when (qualityMetrics.status) {
            QualityStatus.PASS -> theme.quadValidBorderColor
            QualityStatus.BLUR_DETECTED, QualityStatus.GLARE_DETECTED, QualityStatus.UNSTEADY -> theme.warningColor
            QualityStatus.TOO_DARK, QualityStatus.TOO_BRIGHT, QualityStatus.TOO_SKEWED -> theme.errorColor
            QualityStatus.NOT_IN_FRAME -> theme.quadBorderColor
        }
        borderPaint.color = borderColor
        cornerBracketPaint.color = borderColor

        // 1. Draw darkened backdrop outside the document quad
        val maskPath = Path().apply {
            fillType = Path.FillType.INVERSE_WINDING
            moveTo(pts[0].x, pts[0].y)
            lineTo(pts[1].x, pts[1].y)
            lineTo(pts[2].x, pts[2].y)
            lineTo(pts[3].x, pts[3].y)
            close()
        }
        canvas.drawPath(maskPath, maskPaint)

        // 2. Draw Quadrilateral border
        val borderPath = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            lineTo(pts[1].x, pts[1].y)
            lineTo(pts[2].x, pts[2].y)
            lineTo(pts[3].x, pts[3].y)
            close()
        }
        canvas.drawPath(borderPath, borderPaint)

        // 3. Draw heavy corner brackets for CamScanner framing aesthetic
        drawCornerBrackets(canvas, pts)

        // 4. Draw Auto-Capture progress ring at center top if active
        if (autoCaptureProgress > 0f) {
            val ringRadius = 40f
            val ringCenterX = width / 2f
            val ringCenterY = 120f
            val rect = RectF(
                ringCenterX - ringRadius,
                ringCenterY - ringRadius,
                ringCenterX + ringRadius,
                ringCenterY + ringRadius
            )
            val sweepAngle = autoCaptureProgress * 360f
            canvas.drawArc(rect, -90f, sweepAngle, false, progressPaint)
        }
    }

    private fun drawCornerBrackets(canvas: Canvas, pts: List<PointF>) {
        val len = 45f

        // Top-Left corner
        canvas.drawLine(pts[0].x, pts[0].y, pts[0].x + len, pts[0].y, cornerBracketPaint)
        canvas.drawLine(pts[0].x, pts[0].y, pts[0].x, pts[0].y + len, cornerBracketPaint)

        // Top-Right corner
        canvas.drawLine(pts[1].x, pts[1].y, pts[1].x - len, pts[1].y, cornerBracketPaint)
        canvas.drawLine(pts[1].x, pts[1].y, pts[1].x, pts[1].y + len, cornerBracketPaint)

        // Bottom-Right corner
        canvas.drawLine(pts[2].x, pts[2].y, pts[2].x - len, pts[2].y, cornerBracketPaint)
        canvas.drawLine(pts[2].x, pts[2].y, pts[2].x, pts[2].y - len, cornerBracketPaint)

        // Bottom-Left corner
        canvas.drawLine(pts[3].x, pts[3].y, pts[3].x + len, pts[3].y, cornerBracketPaint)
        canvas.drawLine(pts[3].x, pts[3].y, pts[3].x, pts[3].y - len, cornerBracketPaint)
    }
}
