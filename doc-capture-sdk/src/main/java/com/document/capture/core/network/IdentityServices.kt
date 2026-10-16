package com.document.capture.core.network

import android.graphics.Bitmap
import com.document.capture.core.model.VerificationResult
import kotlinx.coroutines.delay

interface DocumentOcrService {
    suspend fun extractData(
        frontBitmap: Bitmap,
        backBitmap: Bitmap?
    ): OcrResponse
}

interface LivenessVerificationService {
    suspend fun verifyLiveness(selfieBitmap: Bitmap): LivenessResponse
}

interface FaceMatchService {
    suspend fun matchFaces(
        documentFaceCrop: Bitmap,
        selfieBitmap: Bitmap
    ): FaceMatchResponse
}

data class OcrResponse(
    val success: Boolean,
    val documentType: String,
    val country: String,
    val fields: Map<String, String>,
    val faceCrop: Bitmap? = null,
    val errorMessage: String? = null
)

data class LivenessResponse(
    val isLive: Boolean,
    val livenessScore: Float,
    val errorMessage: String? = null
)

data class FaceMatchResponse(
    val isMatch: Boolean,
    val similarityScore: Float,
    val threshold: Float = 0.80f,
    val errorMessage: String? = null
)

/**
 * Built-in mock client preconfigured with realistic responses,
 * including data from the reference Brazilian CNH digital driver's license.
 */
class MockIdentityVerificationClient : DocumentOcrService, LivenessVerificationService, FaceMatchService {

    override suspend fun extractData(frontBitmap: Bitmap, backBitmap: Bitmap?): OcrResponse {
        // Simulate network latency
        delay(900)

        // Crop document portrait photo region from front card if possible
        val faceCrop = try {
            val cropW = (frontBitmap.width * 0.22f).toInt().coerceAtLeast(50)
            val cropH = (frontBitmap.height * 0.35f).toInt().coerceAtLeast(50)
            val cropX = (frontBitmap.width * 0.12f).toInt().coerceAtLeast(0)
            val cropY = (frontBitmap.height * 0.20f).toInt().coerceAtLeast(0)
            Bitmap.createBitmap(frontBitmap, cropX, cropY, cropW, cropH)
        } catch (e: Exception) {
            null
        }

        val cnhData = mapOf(
            "Document" to "Carteira Nacional de Habilitação (CNH)",
            "Full Name" to "DANILO MARQUES DE OLIVEIRA",
            "CPF" to "018.174.887-83",
            "Birth Date" to "22/10/1987",
            "CNH Number" to "03651498611",
            "Category" to "B",
            "Issuing State" to "RJ / DETRAN-PR",
            "Valid Until" to "05/02/2030",
            "Mother" to "VERA LUCIA MARQUES DE OLIVEIRA",
            "Father" to "FRANCISCO JOSE DE OLIVEIRA",
            "MRZ" to "I<BRA036514986<110<<<<<<<<<<<\n6907097M3002050BRA<<<<<<<<<<<6\nDANILO<<MARQUES<DE<OLIVEIRA<<<",
            "Digital Signature" to "Assinador Serpro (MP 2200-2/2001 Validated)"
        )

        return OcrResponse(
            success = true,
            documentType = "CNH_DIGITAL",
            country = "BRA",
            fields = cnhData,
            faceCrop = faceCrop
        )
    }

    override suspend fun verifyLiveness(selfieBitmap: Bitmap): LivenessResponse {
        delay(700)
        return LivenessResponse(
            isLive = true,
            livenessScore = 0.992f
        )
    }

    override suspend fun matchFaces(documentFaceCrop: Bitmap, selfieBitmap: Bitmap): FaceMatchResponse {
        delay(800)
        return FaceMatchResponse(
            isMatch = true,
            similarityScore = 0.978f,
            threshold = 0.80f
        )
    }

    suspend fun executeFullVerification(
        frontBitmap: Bitmap,
        backBitmap: Bitmap?,
        selfieBitmap: Bitmap,
        pdfPath: String?
    ): VerificationResult {
        val ocr = extractData(frontBitmap, backBitmap)
        val liveness = verifyLiveness(selfieBitmap)
        val match = if (ocr.faceCrop != null) {
            matchFaces(ocr.faceCrop, selfieBitmap)
        } else {
            FaceMatchResponse(true, 0.95f)
        }

        return VerificationResult(
            status = if (ocr.success && liveness.isLive && match.isMatch) "VERIFIED" else "FAILED",
            documentType = ocr.documentType,
            country = ocr.country,
            extractedFields = ocr.fields,
            documentFaceCrop = ocr.faceCrop,
            livenessSelfie = selfieBitmap,
            livenessScore = liveness.livenessScore,
            livenessPassed = liveness.isLive,
            faceMatchScore = match.similarityScore,
            faceMatchPassed = match.isMatch,
            exportedPdfPath = pdfPath
        )
    }
}
