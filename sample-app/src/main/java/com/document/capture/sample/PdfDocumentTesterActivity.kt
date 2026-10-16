package com.document.capture.sample

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.document.capture.core.model.EnhancementMode
import com.document.capture.core.network.MockDocumentEndpointClient
import com.document.capture.core.pdf.PdfDocumentRasterizer
import com.document.capture.core.pdf.PdfExportEngine
import com.document.capture.core.util.getParcelableCompat
import com.document.capture.sample.databinding.ActivityPdfDocumentTesterBinding
import com.document.capture.ui.DocumentCaptureSDK
import com.document.capture.ui.result.VerificationResultActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.Serializable

class PdfDocumentTesterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPdfDocumentTesterBinding
    private var currentPdfFile: File? = null
    private var currentPdfName: String = "Reference CNH Document"
    private var currentPageIndex: Int = 0
    private var totalPages: Int = 1
    private var currentBitmap: Bitmap? = null

    private val pdfPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { loadPdfFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfDocumentTesterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()

        // Check if an external Uri was passed in the intent
        val passedUri = intent.data ?: intent.getParcelableCompat<Uri>(EXTRA_PDF_URI)
        if (passedUri != null) {
            loadPdfFromUri(passedUri)
        } else {
            loadBundledReferencePdf()
        }
    }

    private fun setupListeners() {
        binding.btnPdfBack.setOnClickListener { finish() }

        binding.btnOpenPdf.setOnClickListener {
            pdfPickerLauncher.launch("application/pdf")
        }

        binding.btnPrevPage.setOnClickListener {
            if (currentPageIndex > 0) {
                currentPageIndex--
                renderCurrentPage()
            }
        }

        binding.btnNextPage.setOnClickListener {
            if (currentPageIndex < totalPages - 1) {
                currentPageIndex++
                renderCurrentPage()
            }
        }

        binding.btnRectifyPdf.setOnClickListener {
            processAndExtract()
        }

        binding.btnExportRectifiedPdf.setOnClickListener {
            exportCurrentPageToPdf()
        }
    }

    private fun loadBundledReferencePdf() {
        lifecycleScope.launch(Dispatchers.IO) {
            val file = File(cacheDir, "reference_cnh.pdf")
            if (!file.exists() || file.length() == 0L) {
                assets.open("reference_cnh.pdf").use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            currentPdfFile = file
            currentPdfName = "reference_cnh.pdf"
            totalPages = PdfDocumentRasterizer.getPageCount(file).coerceAtLeast(1)
            currentPageIndex = 0

            withContext(Dispatchers.Main) {
                renderCurrentPage()
            }
        }
    }

    private fun loadPdfFromUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val cachedFile = PdfDocumentRasterizer.copyUriToCache(this@PdfDocumentTesterActivity, uri)
            if (cachedFile == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PdfDocumentTesterActivity, "Error opening PDF file", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            currentPdfFile = cachedFile
            currentPdfName = uri.lastPathSegment ?: "Imported Document.pdf"
            totalPages = PdfDocumentRasterizer.getPageCount(cachedFile).coerceAtLeast(1)
            currentPageIndex = 0

            withContext(Dispatchers.Main) {
                renderCurrentPage()
            }
        }
    }

    private fun renderCurrentPage() {
        val file = currentPdfFile ?: return

        binding.tvPdfInfo.text = "$currentPdfName (Page ${currentPageIndex + 1} of $totalPages)"
        binding.btnPrevPage.isEnabled = currentPageIndex > 0
        binding.btnNextPage.isEnabled = currentPageIndex < totalPages - 1
        binding.btnPrevPage.alpha = if (currentPageIndex > 0) 1.0f else 0.3f
        binding.btnNextPage.alpha = if (currentPageIndex < totalPages - 1) 1.0f else 0.3f

        lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = PdfDocumentRasterizer.rasterizePage(file, currentPageIndex, 180)
            if (bitmap != null) {
                val quad = DocumentCaptureSDK.detectEdges(bitmap)
                withContext(Dispatchers.Main) {
                    currentBitmap = bitmap
                    binding.pdfCropView.setImageBitmap(bitmap, quad)
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PdfDocumentTesterActivity, "Error rendering page ${currentPageIndex + 1}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun processAndExtract() {
        val bmp = currentBitmap ?: return
        val quad = binding.pdfCropView.getCroppedQuad() ?: return

        lifecycleScope.launch {
            // 1. Perspective unwarp
            val rectified = DocumentCaptureSDK.rectifyDocument(bmp, quad)

            // 2. Enhance with Magic Color
            val enhanced = DocumentCaptureSDK.enhanceDocument(rectified, EnhancementMode.MAGIC_COLOR)
            if (rectified !== bmp && rectified !== enhanced) {
                rectified.recycle()
            }

            // 3. Save rectified bitmap
            val frontFile = File(cacheDir, "pdf_page_${currentPageIndex + 1}_rectified_${System.currentTimeMillis()}.jpg")
            FileOutputStream(frontFile).use { out ->
                enhanced.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            // 4. Export rectified page to PDF
            val exportedPdf = PdfExportEngine.exportToPdf(this@PdfDocumentTesterActivity, enhanced, null)

            // 5. Query Document Endpoint
            val client = MockDocumentEndpointClient()
            val result = client.executeExtraction(
                frontBitmap = enhanced,
                backBitmap = null,
                pdfPath = exportedPdf.absolutePath
            )

            // 6. Launch Result Screen
            val resultIntent = Intent(this@PdfDocumentTesterActivity, VerificationResultActivity::class.java).apply {
                putExtra(VerificationResultActivity.EXTRA_FRONT_PATH, frontFile.absolutePath)
                putExtra(VerificationResultActivity.EXTRA_PDF_PATH, exportedPdf.absolutePath)
                putExtra(VerificationResultActivity.EXTRA_DOC_TYPE, result.documentType)
                putExtra(VerificationResultActivity.EXTRA_FIELDS, HashMap(result.extractedFields) as Serializable)
                putExtra(VerificationResultActivity.EXTRA_QUALITY_SCORE, result.qualityScore)
            }
            startActivity(resultIntent)
        }
    }

    private fun exportCurrentPageToPdf() {
        val bmp = currentBitmap ?: return
        val quad = binding.pdfCropView.getCroppedQuad() ?: return

        lifecycleScope.launch {
            val rectified = DocumentCaptureSDK.rectifyDocument(bmp, quad)
            val enhanced = DocumentCaptureSDK.enhanceDocument(rectified, EnhancementMode.MAGIC_COLOR)
            if (rectified !== bmp && rectified !== enhanced) {
                rectified.recycle()
            }
            val exportedPdf = PdfExportEngine.exportToPdf(
                this@PdfDocumentTesterActivity,
                enhanced,
                null,
                "rectified_page_${currentPageIndex + 1}_${System.currentTimeMillis()}.pdf"
            )

            val uri: Uri = try {
                FileProvider.getUriForFile(
                    this@PdfDocumentTesterActivity,
                    "${applicationContext.packageName}.provider",
                    exportedPdf
                )
            } catch (e: Exception) {
                Toast.makeText(this@PdfDocumentTesterActivity, "Error sharing PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Rectified PDF Page"))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        PdfDocumentRasterizer.clearPdfCache(this)
    }

    companion object {
        const val EXTRA_PDF_URI = "extra_pdf_uri"
    }
}
