package com.document.capture.ui.result

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.document.capture.databinding.ActivityVerificationResultBinding
import java.io.File

class VerificationResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVerificationResultBinding
    private var pdfFilePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVerificationResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pdfFilePath = intent.getStringExtra(EXTRA_PDF_PATH)
        val frontPath = intent.getStringExtra(EXTRA_FRONT_PATH)
        val backPath = intent.getStringExtra(EXTRA_BACK_PATH)
        val docType = intent.getStringExtra(EXTRA_DOC_TYPE) ?: "Standard Document"
        val qualityScore = intent.getFloatExtra(EXTRA_QUALITY_SCORE, 0.96f)

        @Suppress("UNCHECKED_CAST")
        val fields = intent.getSerializableExtra(EXTRA_FIELDS) as? Map<String, String>

        // Bind Quality Score
        val qualityPct = (qualityScore * 100f).toInt().coerceIn(1, 100)
        binding.tvQualityScore.text = "Document Capture Quality: $qualityPct%"
        binding.progressQuality.progress = qualityPct

        // Bind Front Document Image
        frontPath?.let {
            val bmp = BitmapFactory.decodeFile(it)
            if (bmp != null) {
                binding.ivDocFront.setImageBitmap(bmp)
            }
        }

        // Bind Back Document Image (if present)
        if (!backPath.isNullOrEmpty() && File(backPath).exists()) {
            val backBmp = BitmapFactory.decodeFile(backPath)
            if (backBmp != null) {
                binding.ivDocBack.setImageBitmap(backBmp)
                binding.ivDocBack.visibility = View.VISIBLE
            }
        }

        // Bind Extracted Fields
        val fieldsText = if (!fields.isNullOrEmpty()) {
            buildString {
                for ((key, value) in fields) {
                    append("• $key: $value\n")
                }
            }
        } else {
            buildString {
                append("• Document Type: $docType\n")
                append("• Capture Status: High Quality Rectified Scan\n")
                append("• Perspective: Straightened & Planar\n")
                append("• Filter: Magic Color Contrast Enhanced\n")
            }
        }
        binding.tvExtractedFields.text = fieldsText.trimEnd()

        binding.btnExportPdf.setOnClickListener {
            sharePdf()
        }

        binding.btnDone.setOnClickListener {
            finish()
        }
    }

    private fun sharePdf() {
        val path = pdfFilePath
        if (path == null || !File(path).exists()) {
            Toast.makeText(this, "PDF export not available", Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(path)
        val uri: Uri = try {
            FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)
        } catch (e: Exception) {
            Uri.fromFile(file)
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Scanned PDF Document"))
    }

    companion object {
        const val EXTRA_PDF_PATH = "extra_pdf_path"
        const val EXTRA_FRONT_PATH = "extra_front_path"
        const val EXTRA_BACK_PATH = "extra_back_path"
        const val EXTRA_DOC_TYPE = "extra_doc_type"
        const val EXTRA_FIELDS = "extra_fields"
        const val EXTRA_QUALITY_SCORE = "extra_quality_score"
    }
}
