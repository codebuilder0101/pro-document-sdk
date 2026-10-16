package com.document.capture.sample

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.document.capture.core.model.CaptureConfig
import com.document.capture.core.model.DocumentSide
import com.document.capture.core.pdf.PdfDocumentRasterizer
import com.document.capture.sample.databinding.ActivityMainBinding
import com.document.capture.ui.DocumentCaptureSDK
import com.document.capture.ui.crop.CamScannerCropActivity
import com.document.capture.ui.theme.DocumentCaptureTheme
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selectedTheme: DocumentCaptureTheme = DocumentCaptureTheme.defaultDark()

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleImportedUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupThemeSelector()
        setupActionCards()
    }

    private fun setupThemeSelector() {
        binding.chipGroupTheme.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedTheme = when {
                checkedIds.any { it == R.id.chipThemeFintech } -> DocumentCaptureTheme.fintechNavy()
                checkedIds.any { it == R.id.chipThemeHealth } -> DocumentCaptureTheme.healthcareEmerald()
                else -> DocumentCaptureTheme.defaultDark()
            }
            DocumentCaptureTheme.current = selectedTheme
        }
    }

    private fun setupActionCards() {
        // 1. Live Camera KYC Flow
        binding.cardLiveCamera.setOnClickListener {
            DocumentCaptureSDK.Builder(this)
                .setTheme(selectedTheme)
                .setConfig(CaptureConfig(requireBackSide = true, requireLiveness = true))
                .launch(this)
        }

        // 2. Test with Reference CNH Document
        binding.cardReferenceCnh.setOnClickListener {
            val intent = Intent(this, ReferenceDocTestActivity::class.java)
            startActivity(intent)
        }

        // 3. Pick Image / PDF from Device
        binding.cardPickFile.setOnClickListener {
            filePickerLauncher.launch("*/*")
        }

        // 4. Quality Checks Diagnostic Lab
        binding.cardQualityLab.setOnClickListener {
            val intent = Intent(this, QualityLabActivity::class.java)
            startActivity(intent)
        }
    }

    private fun handleImportedUri(uri: Uri) {
        val mimeType = contentResolver.getType(uri) ?: ""
        val isPdf = mimeType.contains("pdf", ignoreCase = true) || uri.toString().endsWith(".pdf", ignoreCase = true)

        val bitmap: Bitmap? = if (isPdf) {
            PdfDocumentRasterizer.rasterizeUri(this, uri, 0)
        } else {
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }

        if (bitmap == null) {
            Toast.makeText(this, "Could not open selected file", Toast.LENGTH_SHORT).show()
            return
        }

        // Cache bitmap to file for CamScanner crop activity
        val cacheFile = File(cacheDir, "imported_doc_${System.currentTimeMillis()}.jpg")
        FileOutputStream(cacheFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }

        val quad = DocumentCaptureSDK.detectEdges(bitmap)
        val cropIntent = Intent(this, CamScannerCropActivity::class.java).apply {
            putExtra(CamScannerCropActivity.EXTRA_IMAGE_PATH, cacheFile.absolutePath)
            putExtra(CamScannerCropActivity.EXTRA_QUAD, quad)
            putExtra(CamScannerCropActivity.EXTRA_SIDE, DocumentSide.FRONT)
            putExtra(CamScannerCropActivity.EXTRA_CONFIG, CaptureConfig(requireBackSide = false, requireLiveness = true))
            putExtra(CamScannerCropActivity.EXTRA_THEME, selectedTheme)
        }
        startActivity(cropIntent)
    }
}
