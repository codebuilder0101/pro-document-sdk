package com.document.capture.ui.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.document.capture.core.model.DocumentQuad
import com.document.capture.core.model.PointF
import com.document.capture.core.model.QualityMetrics
import com.document.capture.core.model.QualityStatus
import com.document.capture.ui.theme.DocumentCaptureTheme
import kotlin.math.max
import kotlin.math.min

/**
 * Camera Viewfinder Overlay displaying real-time quadrilateral document frame,
 * quality feedback colors, centered document guide viewfinder, and auto-capture countdown progress.
 */
class DocumentOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var theme: DocumentCaptureTheme = DocumentCaptureTheme.current
    private var currentQuad: DocumentQuad? = null
    private var qualityMetrics: QualityMetrics = QualityMetrics.initial()
    private var autoCaptureProgress: Float = 0f

    // Shutter button center for countdown progress ring
    private var shutterCenterX: Float = 0f
    private var shutterCenterY: Float = 0f
    private var shutterRadius: Float = 0f

    private val maskPaint = Paint().apply {
        color = Color.parseColor("#99000000")
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

    fun setTheme(newTheme: DocumentCaptureTheme) {
        theme = newTheme
        progressPaint.color = newTheme.accentColor
        invalidate()
    }

    fun setShutterCenter(cx: Float, cy: Float, radius: Float) {
        shutterCenterX = cx
        shutterCenterY = cy
        shutterRadius = radius
        invalidate()
    }

    /**
     * Updates viewfinder overlay with the latest detected quad from analysis stream.
     * Maps quad from analysis frame coordinate space (e.g. 480x640) to view coordinate space (e.g. 1080x2400)
     * using CameraX FILL_CENTER scaling and offset.
     */
    fun updateFrame(
        quad: DocumentQuad?,
        metrics: QualityMetrics,
        progress: Float,
        sourceWidth: Int = 0,
        sourceHeight: Int = 0
    ) {
        qualityMetrics = metrics
        autoCaptureProgress = progress

        if (quad != null && sourceWidth > 0 && sourceHeight > 0) {
            val mappedQuad = mapQuadToViewSpace(quad, sourceWidth, sourceHeight)

            // Smooth interpolation
            val current = currentQuad
            if (current == null) {
                currentQuad = mappedQuad
            } else {
                val alpha = 0.35f
                currentQuad = DocumentQuad(
                    topLeft = smoothPoint(current.topLeft, mappedQuad.topLeft, alpha),
                    topRight = smoothPoint(current.topRight, mappedQuad.topRight, alpha),
                    bottomRight = smoothPoint(current.bottomRight, mappedQuad.bottomRight, alpha),
                    bottomLeft = smoothPoint(current.bottomLeft, mappedQuad.bottomLeft, alpha)
                )
            }
        } else {
            currentQuad = null
        }

        invalidate()
    }

    /**
     * Transforms quadrilateral from camera analysis coordinate space to View space
     * adhering to PreviewView.ScaleType.FILL_CENTER.
     */
    private fun mapQuadToViewSpace(quad: DocumentQuad, frameW: Int, frameH: Int): DocumentQuad {
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        if (viewW <= 0f || viewH <= 0f || frameW <= 0 || frameH <= 0) return quad

        val scale = max(viewW / frameW.toFloat(), viewH / frameH.toFloat())
        val scaledW = frameW * scale
        val scaledH = frameH * scale
        val offsetX = (viewW - scaledW) / 2f
        val offsetY = (viewH - scaledH) / 2f

        return quad.scale(scale, scale).offset(offsetX, offsetY)
    }

    private fun smoothPoint(p1: PointF, p2: PointF, alpha: Float): PointF {
        return PointF(p1.x * (1f - alpha) + p2.x * alpha, p1.y * (1f - alpha) + p2.y * alpha)
    }

    /**
     * Computes a centered document guide frame for standard documents / ID cards
     * placed between the top guidance title and bottom controls.
     */
    fun getCenteredGuideQuad(viewW: Float, viewH: Float): DocumentQuad {
        val boxWidth = viewW * 0.88f
        // Standard document aspect ratio (~1.50)
        val boxHeight = (boxWidth / 1.50f).coerceAtMost(viewH * 0.60f)
        val cx = viewW / 2f
        val cy = viewH * 0.46f // Centered in viewfinder area above bottom shutter
        val left = cx - boxWidth / 2f
        val top = cy - boxHeight / 2f
        val right = cx + boxWidth / 2f
        val bottom = cy + boxHeight / 2f
        return DocumentQuad(
            topLeft = PointF(left, top),
            topRight = PointF(right, top),
            bottomRight = PointF(right, bottom),
            bottomLeft = PointF(left, bottom)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val isDocumentDetected = currentQuad != null
        val quad = currentQuad ?: getCenteredGuideQuad(width.toFloat(), height.toFloat())
        val pts = quad.corners

        // Update border color based on quality status when document is detected;
        // neutral theme border color when showing the waiting guide
        val borderColor = if (isDocumentDetected) {
            when (qualityMetrics.status) {
                QualityStatus.PASS -> theme.quadValidBorderColor
                QualityStatus.BLUR_DETECTED, QualityStatus.GLARE_DETECTED, QualityStatus.UNSTEADY -> theme.warningColor
                QualityStatus.TOO_DARK, QualityStatus.TOO_BRIGHT, QualityStatus.TOO_SKEWED -> theme.errorColor
                QualityStatus.NOT_IN_FRAME -> theme.quadBorderColor
            }
        } else {
            theme.quadBorderColor
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

        // 3. Draw heavy corner brackets aligned along quad edge vectors
        drawCornerBrackets(canvas, pts)

        // 4. Draw Auto-Capture progress ring around shutter button
        if (autoCaptureProgress > 0f) {
            val density = resources.displayMetrics.density
            val cx = if (shutterCenterX > 0f) shutterCenterX else width / 2f
            val cy = if (shutterCenterY > 0f) shutterCenterY else height - (70f * density)
            val radius = if (shutterRadius > 0f) shutterRadius else 46f * density

            progressPaint.strokeWidth = 7f * density
            progressPaint.color = theme.accentColor

            val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            val sweepAngle = autoCaptureProgress * 360f
            canvas.drawArc(rect, -90f, sweepAngle, false, progressPaint)
        }
    }

    private fun drawCornerBrackets(canvas: Canvas, pts: List<PointF>) {
        val len = 48f
        // Top-Left (pts[0]) towards TR (pts[1]) and BL (pts[3])
        drawBracketAtCorner(canvas, pts[0], pts[1], pts[3], len)
        // Top-Right (pts[1]) towards TL (pts[0]) and BR (pts[2])
        drawBracketAtCorner(canvas, pts[1], pts[0], pts[2], len)
        // Bottom-Right (pts[2]) towards TR (pts[1]) and BL (pts[3])
        drawBracketAtCorner(canvas, pts[2], pts[1], pts[3], len)
        // Bottom-Left (pts[3]) towards BR (pts[2]) and TL (pts[0])
        drawBracketAtCorner(canvas, pts[3], pts[2], pts[0], len)
    }

    private fun drawBracketAtCorner(canvas: Canvas, corner: PointF, targetA: PointF, targetB: PointF, len: Float) {
        val distA = corner.distanceTo(targetA)
        if (distA > 0f) {
            val endAx = corner.x + (targetA.x - corner.x) * min(1f, len / distA)
            val endAy = corner.y + (targetA.y - corner.y) * min(1f, len / distA)
            canvas.drawLine(corner.x, corner.y, endAx, endAy, cornerBracketPaint)
        }
        val distB = corner.distanceTo(targetB)
        if (distB > 0f) {
            val endBx = corner.x + (targetB.x - corner.x) * min(1f, len / distB)
            val endBy = corner.y + (targetB.y - corner.y) * min(1f, len / distB)
            canvas.drawLine(corner.x, corner.y, endBx, endBy, cornerBracketPaint)
        }
    }
}
