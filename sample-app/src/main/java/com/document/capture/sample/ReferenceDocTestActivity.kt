package com.document.capture.sample

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.document.capture.core.model.EnhancementMode
import com.document.capture.core.network.MockIdentityVerificationClient
import com.document.capture.core.pdf.PdfDocumentRasterizer
import com.document.capture.sample.databinding.ActivityReferenceDocTestBinding
import com.document.capture.ui.DocumentCaptureSDK
import com.document.capture.ui.result.VerificationResultActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ReferenceDocTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReferenceDocTestBinding
    private var currentBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReferenceDocTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRefBack.setOnClickListener { finish() }

        binding.chipGroupRefDoc.setOnCheckedStateChangeListener { _, checkedIds ->
            when {
                checkedIds.any { it == R.id.chipBack } -> loadAssetImage("sample_cnh_back.jpg")
                checkedIds.any { it == R.id.chipPdf } -> loadPdfPage()
                else -> loadAssetImage("sample_cnh_front.jpg")
            }
        }

        binding.btnRunRectification.setOnClickListener {
            runRectificationAndKyc()
        }

        // Initial load: Front CNH
        loadAssetImage("sample_cnh_front.jpg")
    }

    private fun loadAssetImage(assetFileName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = assets.open(assetFileName).use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
            val quad = DocumentCaptureSDK.detectEdges(bitmap)

            withContext(Dispatchers.Main) {
                currentBitmap = bitmap
                binding.refCropView.setImageBitmap(bitmap, quad)
            }
        }
    }

    private fun loadPdfPage() {
        lifecycleScope.launch(Dispatchers.IO) {
            val tempPdf = File(cacheDir, "reference_cnh.pdf")
            if (!tempPdf.exists()) {
                assets.open("reference_cnh.pdf").use { input ->
                    FileOutputStream(tempPdf).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            val bitmap = PdfDocumentRasterizer.rasterizePage(tempPdf, 0, 180)
            if (bitmap != null) {
                val quad = DocumentCaptureSDK.detectEdges(bitmap)
                withContext(Dispatchers.Main) {
                    currentBitmap = bitmap
                    binding.refCropView.setImageBitmap(bitmap, quad)
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ReferenceDocTestActivity, "Error rasterizing PDF page", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun runRectificationAndKyc() {
        val bmp = currentBitmap ?: return
        val quad = binding.refCropView.getCroppedQuad() ?: return

        lifecycleScope.launch {
            // 1. Perspective unwarping (CamScanner homography)
            val rectified = DocumentCaptureSDK.rectifyDocument(bmp, quad)

            // 2. Enhance with Magic Color
            val enhanced = DocumentCaptureSDK.enhanceDocument(rectified, EnhancementMode.MAGIC_COLOR)

            // 3. Save rectified bitmap to cache
            val frontFile = File(cacheDir, "reference_cnh_rectified_${System.currentTimeMillis()}.jpg")
            FileOutputStream(frontFile).use { out ->
                enhanced.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            // 4. Export multi-page PDF
            val exportedPdf = DocumentCaptureSDK.exportPdf(this@ReferenceDocTestActivity, enhanced, null)

            // 5. Run Mock Identity Verification (OCR + Face Match)
            val client = MockIdentityVerificationClient()
            val result = client.executeFullVerification(
                frontBitmap = enhanced,
                backBitmap = null,
                selfieBitmap = enhanced, // simulated match against portrait
                pdfPath = exportedPdf.absolutePath
            )

            // 6. Launch Verification Result screen
            val resultIntent = Intent(this@ReferenceDocTestActivity, VerificationResultActivity::class.java).apply {
                putExtra(VerificationResultActivity.EXTRA_PDF_PATH, exportedPdf.absolutePath)
                putExtra(VerificationResultActivity.EXTRA_STATUS, result.status)
                putExtra(VerificationResultActivity.EXTRA_LIVENESS_SCORE, result.livenessScore)
                putExtra(VerificationResultActivity.EXTRA_MATCH_SCORE, result.faceMatchScore)
                putExtra(VerificationResultActivity.EXTRA_FRONT_PATH, frontFile.absolutePath)
                putExtra(VerificationResultActivity.EXTRA_SELFIE_PATH, frontFile.absolutePath)
            }
            startActivity(resultIntent)
        }
    }
}
