package com.document.capture.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import com.document.capture.core.detector.DocumentDetector
import com.document.capture.core.model.CaptureConfig
import com.document.capture.core.model.DocumentQuad
import com.document.capture.core.model.DocumentSide
import com.document.capture.core.model.EnhancementMode
import com.document.capture.core.model.QualityMetrics
import com.document.capture.core.pdf.PdfDocumentRasterizer
import com.document.capture.core.pdf.PdfExportEngine
import com.document.capture.core.processor.ImageEnhancer
import com.document.capture.core.processor.PerspectiveCorrectionEngine
import com.document.capture.core.quality.QualityAnalyzer
import com.document.capture.ui.camera.DocumentCameraActivity
import com.document.capture.ui.theme.DocumentCaptureTheme
import java.io.File

/**
 * Public Entry Point for DocumentCaptureSDK.
 * Provides both high-level UI launching flows and low-level headless Computer Vision methods.
 */
class DocumentCaptureSDK private constructor(
    private val config: CaptureConfig,
    private val theme: DocumentCaptureTheme
) {

    fun launch(activity: Activity) {
        DocumentCaptureTheme.current = theme
        val intent = Intent(activity, DocumentCameraActivity::class.java).apply {
            putExtra(DocumentCameraActivity.EXTRA_SIDE, DocumentSide.FRONT)
            putExtra(DocumentCameraActivity.EXTRA_CONFIG, config)
            putExtra(DocumentCameraActivity.EXTRA_THEME, theme)
        }
        activity.startActivity(intent)
    }

    class Builder(private val context: Context) {
        private var config = CaptureConfig()
        private var theme = DocumentCaptureTheme.current

        fun setConfig(config: CaptureConfig) = apply {
            this.config = config
        }

        fun setTheme(theme: DocumentCaptureTheme) = apply {
            this.theme = theme
        }

        fun build(): DocumentCaptureSDK {
            return DocumentCaptureSDK(config, theme)
        }

        fun launch(activity: Activity) {
            build().launch(activity)
        }
    }

    companion object {
        // --- Headless Computer Vision API ---

        /**
         * Detect document edges & quadrilateral polygon on a static bitmap.
         */
        fun detectEdges(bitmap: Bitmap): DocumentQuad {
            return DocumentDetector().detect(bitmap, useSmoothing = false)
        }

        /**
         * Rectify skewed document using homography perspective transformation.
         */
        fun rectifyDocument(bitmap: Bitmap, quad: DocumentQuad): Bitmap {
            return PerspectiveCorrectionEngine.rectify(bitmap, quad)
        }

        /**
         * Apply CamScanner enhancement filter (Original, Magic Color, Crisp B&W).
         */
        fun enhanceDocument(bitmap: Bitmap, mode: EnhancementMode = EnhancementMode.MAGIC_COLOR): Bitmap {
            return ImageEnhancer.apply(bitmap, mode)
        }

        /**
         * Real-time quality evaluation (focus blur, glare, lighting balance).
         */
        fun checkQuality(bitmap: Bitmap, quad: DocumentQuad? = null, config: CaptureConfig = CaptureConfig()): QualityMetrics {
            return QualityAnalyzer.evaluate(bitmap, quad, config)
        }

        /**
         * Rasterize a page from a PDF document to Bitmap.
         */
        fun rasterizePdf(file: File, pageIndex: Int = 0, dpi: Int = 200): Bitmap? {
            return PdfDocumentRasterizer.rasterizePage(file, pageIndex, dpi)
        }

        /**
         * Export front and optional back captures to a multi-page PDF.
         */
        fun exportPdf(context: Context, front: Bitmap, back: Bitmap? = null): File {
            return PdfExportEngine.exportToPdf(context, front, back)
        }
    }
}
