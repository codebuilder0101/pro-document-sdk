package com.document.capture.ui.crop

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.document.capture.core.model.CaptureConfig
import com.document.capture.core.model.DocumentQuad
import com.document.capture.core.model.DocumentSide
import com.document.capture.core.model.EnhancementMode
import com.document.capture.core.processor.ImageEnhancer
import com.document.capture.core.processor.PerspectiveCorrectionEngine
import com.document.capture.databinding.ActivityCamscannerCropBinding
import com.document.capture.ui.camera.DocumentCameraActivity
import com.document.capture.ui.liveness.LivenessCaptureActivity
import com.document.capture.ui.theme.DocumentCaptureTheme
import java.io.File
import java.io.FileOutputStream

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCamscannerCropBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
        currentQuad = intent.getSerializableExtra(EXTRA_QUAD) as? DocumentQuad
        currentSide = (intent.getSerializableExtra(EXTRA_SIDE) as? DocumentSide) ?: DocumentSide.FRONT
        config = (intent.getSerializableExtra(EXTRA_CONFIG) as? CaptureConfig) ?: CaptureConfig()
        theme = (intent.getSerializableExtra(EXTRA_THEME) as? DocumentCaptureTheme) ?: DocumentCaptureTheme.current
        frontProcessedPath = intent.getStringExtra(EXTRA_FRONT_PATH)

        if (imagePath == null || !File(imagePath).exists()) {
            Toast.makeText(this, "Error loading document image", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        sourceBitmap = BitmapFactory.decodeFile(imagePath)
        binding.cropView.setTheme(theme)
        sourceBitmap?.let { bmp ->
            binding.cropView.setImageBitmap(bmp, currentQuad)
        }

        setupButtons()
    }

    private fun setupButtons() {
        binding.btnCropBack.setOnClickListener { finish() }

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

        // 1. Perspective unwarping (homography transform)
        val rectified = PerspectiveCorrectionEngine.rectify(bmp, quad)

        // 2. Apply CamScanner enhancement filter
        val enhanced = ImageEnhancer.apply(rectified, selectedMode)

        // Save processed bitmap to file
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
            // Front & Back are complete, proceed to Liveness Selfie or Result
            val livenessIntent = Intent(this, LivenessCaptureActivity::class.java).apply {
                putExtra(LivenessCaptureActivity.EXTRA_FRONT_PATH, frontProcessedPath ?: processedFile.absolutePath)
                putExtra(LivenessCaptureActivity.EXTRA_BACK_PATH, if (currentSide == DocumentSide.BACK) processedFile.absolutePath else null)
                putExtra(LivenessCaptureActivity.EXTRA_CONFIG, config)
                putExtra(LivenessCaptureActivity.EXTRA_THEME, theme)
            }
            startActivity(livenessIntent)
            finish()
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
