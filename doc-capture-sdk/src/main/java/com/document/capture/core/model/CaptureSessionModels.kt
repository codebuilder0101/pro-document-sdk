package com.document.capture.core.model

import android.graphics.Bitmap
import java.io.File
import java.io.Serializable

/**
 * Real-time image quality evaluation metrics.
 */
data class QualityMetrics(
    val blurScore: Float,
    val glareRatio: Float,
    val meanLuminance: Float,
    val skewRatio: Float,
    val coverageRatio: Float,
    val isStable: Boolean,
    val status: QualityStatus,
    val guidanceMessage: String
) : Serializable {

    val isPassing: Boolean
        get() = status == QualityStatus.PASS

    companion object {
        fun initial(): QualityMetrics = QualityMetrics(
            blurScore = 0f,
            glareRatio = 0f,
            meanLuminance = 128f,
            skewRatio = 0f,
            coverageRatio = 0f,
            isStable = false,
            status = QualityStatus.NOT_IN_FRAME,
            guidanceMessage = "Position document inside frame"
        )
    }
}

/**
 * Document Capture Session Configuration
 */
data class CaptureConfig(
    val requireBackSide: Boolean = false,
    val autoCaptureEnabled: Boolean = true,
    val stabilityThresholdFrames: Int = 12, // ~400ms at 30fps
    val minBlurScore: Float = 95f,
    val maxGlareRatio: Float = 0.035f,
    val minLuminance: Float = 65f,
    val maxLuminance: Float = 248f,
    val outputFormat: OutputFormat = OutputFormat.ALL,
    val defaultEnhancement: EnhancementMode = EnhancementMode.MAGIC_COLOR
) : Serializable

/**
 * Document capture output package holding captured and rectified images.
 */
data class DocumentCaptureResult(
    val frontOriginal: Bitmap,
    val frontRectified: Bitmap,
    val frontQuad: DocumentQuad,
    val frontMetrics: QualityMetrics,
    val backOriginal: Bitmap? = null,
    val backRectified: Bitmap? = null,
    val backQuad: DocumentQuad? = null,
    val backMetrics: QualityMetrics? = null,
    val exportedPdf: File? = null
)

/**
 * Document Verification & Data Extraction Result returned by the document endpoint.
 */
data class DocumentVerificationResult(
    val status: String,
    val documentType: String,
    val country: String,
    val extractedFields: Map<String, String>,
    val documentPhotoCrop: Bitmap? = null,
    val frontRectifiedBitmap: Bitmap? = null,
    val backRectifiedBitmap: Bitmap? = null,
    val exportedPdfPath: String? = null,
    val qualityScore: Float = 0.95f
) : Serializable
