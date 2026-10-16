package com.document.capture.core.network

import android.graphics.Bitmap
import com.document.capture.core.model.DocumentVerificationResult
import kotlinx.coroutines.delay

/**
 * Service contract for sending captured document images to an endpoint
 * that identifies the document type and extracts structured data.
 */
interface DocumentEndpointService {
    suspend fun processDocument(
        frontBitmap: Bitmap,
        backBitmap: Bitmap? = null
    ): DocumentEndpointResponse
}

data class DocumentEndpointResponse(
    val success: Boolean,
    val documentType: String,
    val country: String,
    val fields: Map<String, String>,
    val documentPhotoCrop: Bitmap? = null,
    val errorMessage: String? = null
)

/**
 * Built-in mock document endpoint client.
 * Identifies document types (e.g., Brazilian CNH, National ID, Contract / PDF)
 * and extracts data fields. Host applications can supply their own remote REST/gRPC implementation.
 */
class MockDocumentEndpointClient : DocumentEndpointService {

    override suspend fun processDocument(frontBitmap: Bitmap, backBitmap: Bitmap?): DocumentEndpointResponse {
        // Simulate endpoint network latency
        delay(800)

        val aspectRatio = frontBitmap.width.toFloat() / frontBitmap.height.toFloat().coerceAtLeast(1f)

        // Crop document portrait photo region from front card if ID card proportions
        val faceCrop = try {
            if (aspectRatio in 1.1f..1.9f) {
                val cropW = (frontBitmap.width * 0.22f).toInt().coerceAtLeast(50)
                val cropH = (frontBitmap.height * 0.35f).toInt().coerceAtLeast(50)
                val cropX = (frontBitmap.width * 0.12f).toInt().coerceAtLeast(0)
                val cropY = (frontBitmap.height * 0.20f).toInt().coerceAtLeast(0)
                Bitmap.createBitmap(frontBitmap, cropX, cropY, cropW, cropH)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }

        // Determine if reference Brazilian CNH or other document format
        val isCnhProportion = aspectRatio in 1.30f..1.60f
        val fields = if (isCnhProportion) {
            mapOf(
                "Document Type" to "Carteira Nacional de Habilitação (CNH Digital)",
                "Full Name" to "DANILO MARQUES DE OLIVEIRA",
                "CPF" to "018.174.887-83",
                "Birth Date" to "22/10/1987",
                "CNH Number" to "03651498611",
                "Category" to "B",
                "Issuing Authority" to "SENATRAN / DETRAN-PR",
                "Emission Date" to "05/03/2025",
                "Valid Until" to "05/02/2030",
                "Mother" to "VERA LUCIA MARQUES DE OLIVEIRA",
                "Father" to "FRANCISCO JOSE DE OLIVEIRA",
                "MRZ" to "I<BRA036514986<110<<<<<<<<<<<\n6907097M3002050BRA<<<<<<<<<<<6\nDANILO<<MARQUES<DE<OLIVEIRA<<<",
                "Digital Signature" to "Assinador Serpro (MP 2200-2/2001 Validated)"
            )
        } else {
            mapOf(
                "Document Type" to "Official Document / PDF Page",
                "Resolution" to "${frontBitmap.width} × ${frontBitmap.height} px",
                "Page Status" to "Clean Rectified Planar Document",
                "Extracted Text" to "Document scanned and perspective-rectified successfully.",
                "Processing Engine" to "CamScanner-style Homography Unwarping",
                "Validation Status" to "Pass (Edges Aligned, Quality Verified)"
            )
        }

        val docType = if (isCnhProportion) "Carteira Nacional de Habilitação (CNH)" else "Standard Document"
        val country = if (isCnhProportion) "BRA" else "INT"

        return DocumentEndpointResponse(
            success = true,
            documentType = docType,
            country = country,
            fields = fields,
            documentPhotoCrop = faceCrop
        )
    }

    suspend fun executeExtraction(
        frontBitmap: Bitmap,
        backBitmap: Bitmap?,
        pdfPath: String?
    ): DocumentVerificationResult {
        val response = processDocument(frontBitmap, backBitmap)

        return DocumentVerificationResult(
            status = if (response.success) "SUCCESS" else "FAILED",
            documentType = response.documentType,
            country = response.country,
            extractedFields = response.fields,
            documentPhotoCrop = response.documentPhotoCrop,
            frontRectifiedBitmap = frontBitmap,
            backRectifiedBitmap = backBitmap,
            exportedPdfPath = pdfPath,
            qualityScore = 0.96f
        )
    }
}
