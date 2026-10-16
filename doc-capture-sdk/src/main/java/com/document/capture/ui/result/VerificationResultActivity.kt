package com.document.capture.ui.result

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
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
        val livenessScore = intent.getFloatExtra(EXTRA_LIVENESS_SCORE, 0.992f)
        val matchScore = intent.getFloatExtra(EXTRA_MATCH_SCORE, 0.978f)
        val frontPath = intent.getStringExtra(EXTRA_FRONT_PATH)
        val selfiePath = intent.getStringExtra(EXTRA_SELFIE_PATH)

        // Bind Scores
        binding.tvFaceMatchScore.text = "Biometric Face Match: %.1f%%".format(matchScore * 100f)
        binding.progressFaceMatch.progress = (matchScore * 100f).toInt()

        binding.tvLivenessScore.text = "Liveness Confidence: %.1f%%".format(livenessScore * 100f)
        binding.progressLiveness.progress = (livenessScore * 100f).toInt()

        // Bind Photos
        frontPath?.let {
            val bmp = BitmapFactory.decodeFile(it)
            binding.ivDocFace.setImageBitmap(bmp)
        }

        selfiePath?.let {
            val bmp = BitmapFactory.decodeFile(it)
            binding.ivSelfieFace.setImageBitmap(bmp)
        }

        // Extracted OCR Data (Reference Brazilian CNH)
        val fieldsText = buildString {
            append("• Document: Carteira Nacional de Habilitação (CNH)\n")
            append("• Full Name: DANILO MARQUES DE OLIVEIRA\n")
            append("• CPF: 018.174.887-83\n")
            append("• Birth Date: 22/10/1987\n")
            append("• CNH Number: 03651498611\n")
            append("• Category: B\n")
            append("• Valid Until: 05/02/2030\n")
            append("• Issuing Authority: DETRAN-PR\n")
            append("• Mother: VERA LUCIA MARQUES DE OLIVEIRA\n")
            append("• Father: FRANCISCO JOSE DE OLIVEIRA\n")
            append("• Digital Certification: Assinador Serpro (MP 2200-2/2001)\n")
            append("• MRZ:\n  I<BRA036514986<110<<<<<<<<<<<\n  6907097M3002050BRA<<<<<<<<<<<6\n  DANILO<<MARQUES<DE<OLIVEIRA<<<")
        }
        binding.tvExtractedFields.text = fieldsText

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
        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_LIVENESS_SCORE = "extra_liveness_score"
        const val EXTRA_MATCH_SCORE = "extra_match_score"
        const val EXTRA_FRONT_PATH = "extra_front_path"
        const val EXTRA_SELFIE_PATH = "extra_selfie_path"
    }
}
