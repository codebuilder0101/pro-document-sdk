package com.document.capture.ui.crop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.document.capture.core.model.DocumentQuad
import com.document.capture.core.model.PointF
import com.document.capture.ui.theme.DocumentCaptureTheme
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * CamScanner-style interactive document cropper with 8 control handles
 * (4 corners + 4 edge bars) and a floating 2.5x magnifying loupe with crosshair reticle.
 */
class CamScannerCropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var sourceBitmap: Bitmap? = null
    private var documentQuad: DocumentQuad? = null
    private var theme: DocumentCaptureTheme = DocumentCaptureTheme.current

    // View to Bitmap coordinate mapping
    private var scale = 1.0f
    private var offsetX = 0.0f
    private var offsetY = 0.0f

    // Interactive Drag State
    private var activeHandleIndex = -1 // 0..3 corners, 4..7 edges
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    // Loupe Magnifier settings
    private val loupeRadius = 140f // ~55dp in standard pixels
    private val loupeZoom = 2.5f
    private val loupeOffsetDistance = 220f
    private val touchHitRadius = 90f // generous touch area

    // Drawing Paints
    private val maskPaint = Paint().apply {
        color = Color.parseColor("#99000000")
        style = Paint.Style.FILL
    }

    private val polygonStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.quadBorderColor
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }

    private val cornerHandleOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.quadBorderColor
        style = Paint.Style.FILL
    }

    private val cornerHandleInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val edgeHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.quadBorderColor
        style = Paint.Style.FILL
    }

    private val loupeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.loupeBorderColor
        strokeWidth = 8f
        style = Paint.Style.STROKE
    }

    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val loupeShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#44000000")
        style = Paint.Style.FILL
    }

    fun setImageBitmap(bitmap: Bitmap, initialQuad: DocumentQuad? = null) {
        sourceBitmap = bitmap
        val bmpW = bitmap.width.toFloat()
        val bmpH = bitmap.height.toFloat()

        documentQuad = initialQuad ?: DocumentQuad.defaultCentered(bmpW, bmpH)
        requestLayout()
        invalidate()
    }

    fun setTheme(newTheme: DocumentCaptureTheme) {
        theme = newTheme
        polygonStrokePaint.color = theme.quadBorderColor
        cornerHandleOuterPaint.color = theme.quadBorderColor
        edgeHandlePaint.color = theme.quadBorderColor
        loupeBorderPaint.color = theme.loupeBorderColor
        invalidate()
    }

    fun getCroppedQuad(): DocumentQuad? = documentQuad

    fun resetToDefault() {
        sourceBitmap?.let { bmp ->
            documentQuad = DocumentQuad.defaultCentered(bmp.width.toFloat(), bmp.height.toFloat())
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        calculateBitmapScale(measuredWidth.toFloat(), measuredHeight.toFloat())
    }

    private fun calculateBitmapScale(viewW: Float, viewH: Float) {
        val bmp = sourceBitmap ?: return
        if (viewW <= 0 || viewH <= 0) return

        val scaleW = viewW / bmp.width.toFloat()
        val scaleH = viewH / bmp.height.toFloat()
        scale = min(scaleW, scaleH)

        val scaledW = bmp.width * scale
        val scaledH = bmp.height * scale

        offsetX = (viewW - scaledW) / 2f
        offsetY = (viewH - scaledH) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = sourceBitmap ?: return
        val quad = documentQuad ?: return

        // 1. Draw source bitmap scaled and centered
        val dstRect = RectF(offsetX, offsetY, offsetX + bmp.width * scale, offsetY + bmp.height * scale)
        canvas.drawBitmap(bmp, null, dstRect, null)

        // 2. Map quad to view space
        val viewQuad = quad.scale(scale, scale).offset(offsetX, offsetY)
        val pts = viewQuad.corners

        // 3. Draw Darkened Mask outside the document polygon
        val maskPath = Path().apply {
            fillType = Path.FillType.INVERSE_WINDING
            moveTo(pts[0].x, pts[0].y)
            lineTo(pts[1].x, pts[1].y)
            lineTo(pts[2].x, pts[2].y)
            lineTo(pts[3].x, pts[3].y)
            close()
        }
        canvas.drawPath(maskPath, maskPaint)

        // 4. Draw Quadrilateral border lines
        val polyPath = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            lineTo(pts[1].x, pts[1].y)
            lineTo(pts[2].x, pts[2].y)
            lineTo(pts[3].x, pts[3].y)
            close()
        }
        canvas.drawPath(polyPath, polygonStrokePaint)

        // 5. Draw 4 Edge Midpoint Handles (bars)
        val midPts = viewQuad.midPoints
        for (m in midPts) {
            canvas.drawCircle(m.x, m.y, 14f, edgeHandlePaint)
        }

        // 6. Draw 4 Corner Handles (circles with center dots)
        for (p in pts) {
            canvas.drawCircle(p.x, p.y, 28f, cornerHandleOuterPaint)
            canvas.drawCircle(p.x, p.y, 14f, cornerHandleInnerPaint)
        }

        // 7. Draw Floating Loupe Magnifier when dragging a corner
        if (isDragging && activeHandleIndex in 0..3) {
            val activeCornerView = pts[activeHandleIndex]
            val activeCornerBmp = quad.corners[activeHandleIndex]
            drawLoupe(canvas, bmp, activeCornerView, activeCornerBmp)
        }
    }

    private fun drawLoupe(
        canvas: Canvas,
        bitmap: Bitmap,
        touchViewPt: PointF,
        targetBmpPt: PointF
    ) {
        // Place loupe above finger, or flip below if finger is in upper third
        val loupeCenterY = if (touchViewPt.y > loupeRadius * 2.5f) {
            touchViewPt.y - loupeOffsetDistance
        } else {
            touchViewPt.y + loupeOffsetDistance
        }
        val loupeCenterX = touchViewPt.x.coerceIn(loupeRadius + 20f, width - loupeRadius - 20f)

        canvas.save()

        // Shadow circle
        canvas.drawCircle(loupeCenterX, loupeCenterY + 8f, loupeRadius + 4f, loupeShadowPaint)

        // Clip circular aperture
        val circlePath = Path().apply {
            addCircle(loupeCenterX, loupeCenterY, loupeRadius, Path.Direction.CW)
        }
        canvas.clipPath(circlePath)

        // Draw magnified background
        canvas.drawColor(Color.WHITE)
        canvas.save()
        canvas.translate(loupeCenterX, loupeCenterY)
        canvas.scale(loupeZoom * scale, loupeZoom * scale)
        canvas.translate(-targetBmpPt.x, -targetBmpPt.y)

        canvas.drawBitmap(bitmap, 0f, 0f, null)
        canvas.restore()

        // Draw Crosshair target reticle (+)
        val chLen = 30f
        canvas.drawLine(loupeCenterX - chLen, loupeCenterY, loupeCenterX + chLen, loupeCenterY, crosshairPaint)
        canvas.drawLine(loupeCenterX, loupeCenterY - chLen, loupeCenterX, loupeCenterY + chLen, crosshairPaint)

        canvas.restore()

        // Draw Loupe boundary stroke ring
        canvas.drawCircle(loupeCenterX, loupeCenterY, loupeRadius, loupeBorderPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val quad = documentQuad ?: return super.onTouchEvent(event)
        val viewQuad = quad.scale(scale, scale).offset(offsetX, offsetY)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y

                // 1. Check corner handles first (highest priority)
                val cornerPts = viewQuad.corners
                for (i in cornerPts.indices) {
                    if (hypot(event.x - cornerPts[i].x, event.y - cornerPts[i].y) <= touchHitRadius) {
                        activeHandleIndex = i
                        isDragging = true
                        invalidate()
                        return true
                    }
                }

                // 2. Check edge midpoint handles
                val midPts = viewQuad.midPoints
                for (i in midPts.indices) {
                    if (hypot(event.x - midPts[i].x, event.y - midPts[i].y) <= touchHitRadius) {
                        activeHandleIndex = 4 + i // 4: top, 5: right, 6: bottom, 7: left
                        isDragging = true
                        invalidate()
                        return true
                    }
                }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDragging || activeHandleIndex == -1) return false

                val dx = (event.x - lastTouchX) / scale
                val dy = (event.y - lastTouchY) / scale
                lastTouchX = event.x
                lastTouchY = event.y

                val newQuad = when (activeHandleIndex) {
                    0 -> quad.copy(topLeft = quad.topLeft.offset(dx, dy))
                    1 -> quad.copy(topRight = quad.topRight.offset(dx, dy))
                    2 -> quad.copy(bottomRight = quad.bottomRight.offset(dx, dy))
                    3 -> quad.copy(bottomLeft = quad.bottomLeft.offset(dx, dy))
                    // Edge drags move both adjacent corners simultaneously
                    4 -> quad.copy(topLeft = quad.topLeft.offset(0f, dy), topRight = quad.topRight.offset(0f, dy)) // Top edge
                    5 -> quad.copy(topRight = quad.topRight.offset(dx, 0f), bottomRight = quad.bottomRight.offset(dx, 0f)) // Right edge
                    6 -> quad.copy(bottomLeft = quad.bottomLeft.offset(0f, dy), bottomRight = quad.bottomRight.offset(0f, dy)) // Bottom edge
                    7 -> quad.copy(topLeft = quad.topLeft.offset(dx, 0f), bottomLeft = quad.bottomLeft.offset(dx, 0f)) // Left edge
                    else -> quad
                }

                // Enforce strictly convex polygon constraint
                if (newQuad.isConvex()) {
                    documentQuad = newQuad
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                activeHandleIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
