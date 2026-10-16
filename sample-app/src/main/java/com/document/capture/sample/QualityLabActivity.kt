package com.document.capture.sample

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.document.capture.core.model.QualityStatus
import com.document.capture.core.pdf.PdfDocumentRasterizer
import com.document.capture.sample.databinding.ActivityQualityLabBinding
import com.document.capture.ui.DocumentCaptureSDK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QualityLabActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQualityLabBinding
    private var currentBitmap: Bitmap? = null

    private val pickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { loadFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQualityLabBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLabBack.setOnClickListener { finish() }
        binding.btnLabPick.setOnClickListener {
            pickerLauncher.launch("*/*")
        }

        // Initial analysis with reference CNH image
        loadInitialSample()
    }

    private fun loadInitialSample() {
        lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = assets.open("sample_cnh_front.jpg").use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
            withContext(Dispatchers.Main) {
                analyzeBitmap(bitmap)
            }
        }
    }

    private fun loadFromUri(uri: Uri) {
        val mimeType = contentResolver.getType(uri) ?: ""
        val isPdf = mimeType.contains("pdf", ignoreCase = true) || uri.toString().endsWith(".pdf", ignoreCase = true)

        lifecycleScope.launch(Dispatchers.IO) {
            val bitmap: Bitmap? = if (isPdf) {
                PdfDocumentRasterizer.rasterizeUri(this@QualityLabActivity, uri, 0)
            } else {
                contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }

            bitmap?.let {
                withContext(Dispatchers.Main) {
                    analyzeBitmap(it)
                }
            }
        }
    }

    private fun analyzeBitmap(bitmap: Bitmap) {
        currentBitmap = bitmap
        binding.ivLabPreview.setImageBitmap(bitmap)

        val quad = DocumentCaptureSDK.detectEdges(bitmap)
        val metrics = DocumentCaptureSDK.checkQuality(bitmap, quad)

        // Status Banner
        val isPass = metrics.status == QualityStatus.PASS
        binding.tvLabOverallStatus.text = "QUALITY GATE: ${metrics.status.name} (${metrics.guidanceMessage})"
        val statusColor = if (isPass) {
            android.graphics.Color.parseColor("#00E676")
        } else {
            android.graphics.Color.parseColor("#FFAB00")
        }
        binding.tvLabOverallStatus.setTextColor(statusColor)

        // Blur Score
        binding.tvBlurLabel.text = "Focus Blur Score (Laplacian Var): %.1f (Min: 95.0)".format(metrics.blurScore)
        binding.progressBlur.progress = metrics.blurScore.toInt().coerceIn(0, 300)

        // Glare Ratio
        val glarePercent = metrics.glareRatio * 100f
        binding.tvGlareLabel.text = "Glare Hotspot Area: %.1f%% (Max: 3.5%%)".format(glarePercent)
        binding.progressGlare.progress = glarePercent.toInt().coerceIn(0, 100)

        // Luminance
        binding.tvLumLabel.text = "Mean Lighting Luminance: %.0f (Target: 65 - 245)".format(metrics.meanLuminance)
        binding.progressLum.progress = metrics.meanLuminance.toInt().coerceIn(0, 255)
    }
}
