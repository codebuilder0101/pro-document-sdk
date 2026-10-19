package com.document.capture.ui.crop

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.document.capture.core.model.CaptureConfig
import com.document.capture.core.model.DocumentQuad
import com.document.capture.core.model.DocumentSide
import com.document.capture.core.model.EnhancementMode
import com.document.capture.core.network.MockDocumentEndpointClient
import com.document.capture.core.pdf.PdfExportEngine
import com.document.capture.core.processor.ImageEnhancer
import com.document.capture.core.processor.PerspectiveCorrectionEngine
import com.document.capture.core.util.getSerializableCompat
import com.document.capture.databinding.ActivityCamscannerCropBinding
import com.document.capture.ui.DocumentCaptureSDK
import com.document.capture.ui.camera.DocumentCameraActivity
import com.document.capture.ui.result.VerificationResultActivity
import com.document.capture.ui.theme.DocumentCaptureTheme
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.Serializable

class CamScannerCropActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCamscannerCropBinding
    private var sourceBitmap: Bitmap? = null
    private var currentQuad: DocumentQuad? = null
    private var currentSide = DocumentSide.FRONT
    private var config = CaptureConfig()
    private var theme = DocumentCaptureTheme.current
    private var selectedMode = EnhancementMode.MAGIC_COLOR
    private var rotationDegrees = 0f
    private var frontProcessedPath: String? = null
    private var isProceeding = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCamscannerCropBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
        currentQuad = intent.getSerializableCompat<DocumentQuad>(EXTRA_QUAD)
        currentSide = intent.getSerializableCompat<DocumentSide>(EXTRA_SIDE) ?: DocumentSide.FRONT
        config = intent.getSerializableCompat<CaptureConfig>(EXTRA_CONFIG) ?: CaptureConfig()
        theme = intent.getSerializableCompat<DocumentCaptureTheme>(EXTRA_THEME) ?: DocumentCaptureTheme.current
        frontProcessedPath = intent.getStringExtra(EXTRA_FRONT_PATH)

        if (imagePath == null || !File(imagePath).exists()) {
            Toast.makeText(this, "Error loading document image", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        sourceBitmap = loadBitmapWithExif(imagePath)
        binding.cropView.setTheme(theme)
        sourceBitmap?.let { bmp ->
            binding.cropView.setImageBitmap(bmp, currentQuad)
        }

        setupButtons()
    }

    private fun loadBitmapWithExif(filePath: String): Bitmap {
        val rawBitmap = BitmapFactory.decodeFile(filePath)
            ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        return try {
            val exif = ExifInterface(filePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val rotation = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (rotation != 0f) {
                val matrix = Matrix().apply { postRotate(rotation) }
                val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                if (rotated !== rawBitmap) {
                    rawBitmap.recycle()
                }
                rotated
            } else {
                rawBitmap
            }
        } catch (e: Exception) {
            rawBitmap
        }
    }

    private fun setupButtons() {
        binding.btnCropBack.setOnClickListener {
            DocumentCaptureSDK.deliverCancelled()
            finish()
        }

        binding.btnResetCrop.setOnClickListener {
            binding.cropView.resetToDefault()
        }

        binding.btnRotate.setOnClickListener {
            rotationDegrees = (rotationDegrees + 90f) % 360f
            sourceBitmap?.let { bmp ->
                val rotated = PerspectiveCorrectionEngine.rotate(bmp, 90f)
                sourceBitmap = rotated
                binding.cropView.setImageBitmap(rotated, null)
            }
        }

        binding.btnFilterMagic.setOnClickListener {
            selectedMode = EnhancementMode.MAGIC_COLOR
            highlightFilterButton(binding.btnFilterMagic)
        }

        binding.btnFilterOriginal.setOnClickListener {
            selectedMode = EnhancementMode.ORIGINAL
            highlightFilterButton(binding.btnFilterOriginal)
        }

        binding.btnFilterBw.setOnClickListener {
            selectedMode = EnhancementMode.CRISP_BW
            highlightFilterButton(binding.btnFilterBw)
        }

        binding.btnConfirmCrop.setOnClickListener {
            processAndProceed()
        }
    }

    private fun highlightFilterButton(activeBtn: com.google.android.material.button.MaterialButton) {
        val buttons = listOf(binding.btnFilterMagic, binding.btnFilterOriginal, binding.btnFilterBw)
        for (btn in buttons) {
            if (btn == activeBtn) {
                btn.setBackgroundColor(theme.primaryColor)
                btn.setTextColor(android.graphics.Color.WHITE)
            } else {
                btn.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                btn.setTextColor(theme.surfaceTextColor)
            }
        }
    }

    private fun processAndProceed() {
        val bmp = sourceBitmap ?: return
        val quad = binding.cropView.getCroppedQuad() ?: return

        isProceeding = true

        // 1. Perspective unwarping (CamScanner homography transform)
        val rectified = PerspectiveCorrectionEngine.rectify(bmp, quad)

        // 2. Apply CamScanner enhancement filter
        val enhanced = ImageEnhancer.apply(rectified, selectedMode)
        if (rectified !== bmp && rectified !== enhanced) {
            rectified.recycle()
        }

        // Save processed bitmap to cache file
        val processedFile = File(cacheDir, "doc_processed_${currentSide.name}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(processedFile).use { out ->
            enhanced.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }

        if (currentSide == DocumentSide.FRONT && config.requireBackSide) {
            // Proceed to Back capture
            val nextIntent = Intent(this, DocumentCameraActivity::class.java).apply {
                putExtra(DocumentCameraActivity.EXTRA_SIDE, DocumentSide.BACK)
                putExtra(DocumentCameraActivity.EXTRA_CONFIG, config)
                putExtra(DocumentCameraActivity.EXTRA_THEME, theme)
                putExtra(EXTRA_FRONT_PATH, processedFile.absolutePath)
            }
            startActivity(nextIntent)
            finish()
        } else {
            // Document capture complete -> Send to Document Endpoint & Export PDF
            binding.btnConfirmCrop.isEnabled = false
            binding.btnConfirmCrop.text = "Processing Document..."

            lifecycleScope.launch {
                val frontFile = frontProcessedPath?.let { File(it) } ?: processedFile
                val frontBitmap = loadBitmapWithExif(frontFile.absolutePath)
                val backBitmap = if (currentSide == DocumentSide.BACK) enhanced else null

                // Compile multi-page PDF
                val pdfFile = PdfExportEngine.exportToPdf(
                    this@CamScannerCropActivity,
                    frontBitmap,
                    backBitmap
                )

                // Query Document Endpoint (identifies doc type & extracts fields)
                val endpointClient = MockDocumentEndpointClient()
                val result = endpointClient.executeExtraction(
                    frontBitmap = frontBitmap,
                    backBitmap = backBitmap,
                    pdfPath = pdfFile.absolutePath
                )

                // Deliver result to host app callback if registered
                DocumentCaptureSDK.deliverSuccess(result)

                if (frontBitmap !== enhanced) {
                    frontBitmap.recycle()
                }

                val resultIntent = Intent(this@CamScannerCropActivity, VerificationResultActivity::class.java).apply {
                    putExtra(VerificationResultActivity.EXTRA_FRONT_PATH, frontFile.absolutePath)
                    putExtra(VerificationResultActivity.EXTRA_BACK_PATH, if (currentSide == DocumentSide.BACK) processedFile.absolutePath else null)
                    putExtra(VerificationResultActivity.EXTRA_PDF_PATH, pdfFile.absolutePath)
                    putExtra(VerificationResultActivity.EXTRA_DOC_TYPE, result.documentType)
                    putExtra(VerificationResultActivity.EXTRA_FIELDS, HashMap(result.extractedFields) as Serializable)
                    putExtra(VerificationResultActivity.EXTRA_QUALITY_SCORE, result.qualityScore)
                }
                startActivity(resultIntent)
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && !isProceeding) {
            DocumentCaptureSDK.deliverCancelled()
        }
    }

    companion object {
        const val EXTRA_IMAGE_PATH = "extra_image_path"
        const val EXTRA_QUAD = "extra_quad"
        const val EXTRA_SIDE = "extra_side"
        const val EXTRA_CONFIG = "extra_config"
        const val EXTRA_THEME = "extra_theme"
        const val EXTRA_FRONT_PATH = "extra_front_path"
    }
}
