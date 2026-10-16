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
import com.document.capture.core.model.DocumentVerificationResult
import com.document.capture.sample.databinding.ActivityMainBinding
import com.document.capture.ui.DocumentCaptureCallback
import com.document.capture.ui.DocumentCaptureSDK
import com.document.capture.ui.crop.CamScannerCropActivity
import com.document.capture.ui.theme.DocumentCaptureTheme
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selectedTheme: DocumentCaptureTheme = DocumentCaptureTheme.defaultDark()

    private val imagePickerLauncher = registerForActivityResult(
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
        // 1. Live Camera Document Capture (with Single Page vs Two-Sided selection)
        binding.cardLiveCamera.setOnClickListener {
            showDocumentTypeDialog()
        }

        // 2. Test with Reference CNH Document
        binding.cardReferenceCnh.setOnClickListener {
            val intent = Intent(this, ReferenceDocTestActivity::class.java)
            startActivity(intent)
        }

        // 3. Test Any PDF Document (Multi-page interactive browser & cropper)
        binding.cardTestPdf.setOnClickListener {
            val intent = Intent(this, PdfDocumentTesterActivity::class.java)
            startActivity(intent)
        }

        // 4. Test Any Image from Device
        binding.cardPickImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        // 5. Document Quality Diagnostics Lab
        binding.cardQualityLab.setOnClickListener {
            val intent = Intent(this, QualityLabActivity::class.java)
            startActivity(intent)
        }
    }

    private fun showDocumentTypeDialog() {
        val options = arrayOf(
            "Single-Sided Document (Passport, Invoice, Single Page)",
            "Two-Sided Document (Front & Back ID / Driver License)"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("Select Document Mode")
            .setItems(options) { _, which ->
                val requireBack = (which == 1)
                DocumentCaptureSDK.Builder(this)
                    .setTheme(selectedTheme)
                    .setConfig(CaptureConfig(requireBackSide = requireBack))
                    .launch(this, object : DocumentCaptureCallback {
                        override fun onCaptureSuccess(result: DocumentVerificationResult) {
                            Toast.makeText(
                                this@MainActivity,
                                "Capture Success: ${result.documentType} (Quality: ${(result.qualityScore * 100).toInt()}%)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        override fun onCaptureCancelled() {
                            Toast.makeText(this@MainActivity, "Capture flow cancelled", Toast.LENGTH_SHORT).show()
                        }

                        override fun onCaptureError(message: String) {
                            Toast.makeText(this@MainActivity, "Capture error: $message", Toast.LENGTH_LONG).show()
                        }
                    })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleImportedUri(uri: Uri) {
        val mimeType = contentResolver.getType(uri) ?: ""
        if (mimeType.contains("pdf", ignoreCase = true) || uri.toString().endsWith(".pdf", ignoreCase = true)) {
            val intent = Intent(this, PdfDocumentTesterActivity::class.java).apply {
                putExtra(PdfDocumentTesterActivity.EXTRA_PDF_URI, uri)
            }
            startActivity(intent)
            return
        }

        val bitmap: Bitmap? = try {
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            null
        }

        if (bitmap == null) {
            Toast.makeText(this, "Could not open selected image", Toast.LENGTH_SHORT).show()
            return
        }

        // Cache bitmap to file for CamScanner crop activity
        val cacheFile = File(cacheDir, "imported_doc_${System.currentTimeMillis()}.jpg")
        FileOutputStream(cacheFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }

        val quad = DocumentCaptureSDK.detectEdges(bitmap)
        bitmap.recycle() // Recycle decoded bitmap after edge detection

        val cropIntent = Intent(this, CamScannerCropActivity::class.java).apply {
            putExtra(CamScannerCropActivity.EXTRA_IMAGE_PATH, cacheFile.absolutePath)
            putExtra(CamScannerCropActivity.EXTRA_QUAD, quad)
            putExtra(CamScannerCropActivity.EXTRA_SIDE, DocumentSide.FRONT)
            putExtra(CamScannerCropActivity.EXTRA_CONFIG, CaptureConfig(requireBackSide = false))
            putExtra(CamScannerCropActivity.EXTRA_THEME, selectedTheme)
        }
        startActivity(cropIntent)
    }
}
