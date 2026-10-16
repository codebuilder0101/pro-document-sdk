package com.document.capture.ui.liveness

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.document.capture.core.model.CaptureConfig
import com.document.capture.core.network.MockIdentityVerificationClient
import com.document.capture.core.pdf.PdfExportEngine
import com.document.capture.databinding.ActivityLivenessCaptureBinding
import com.document.capture.ui.result.VerificationResultActivity
import com.document.capture.ui.theme.DocumentCaptureTheme
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class LivenessCaptureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLivenessCaptureBinding
    private var imageCapture: ImageCapture? = null

    private var frontPath: String? = null
    private var backPath: String? = null
    private var config = CaptureConfig()
    private var theme = DocumentCaptureTheme.current

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startFrontCamera()
        } else {
            Toast.makeText(this, "Camera permission needed for selfie verification", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLivenessCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        frontPath = intent.getStringExtra(EXTRA_FRONT_PATH)
        backPath = intent.getStringExtra(EXTRA_BACK_PATH)
        config = (intent.getSerializableExtra(EXTRA_CONFIG) as? CaptureConfig) ?: CaptureConfig()
        theme = (intent.getSerializableExtra(EXTRA_THEME) as? DocumentCaptureTheme) ?: DocumentCaptureTheme.current

        binding.btnLivenessClose.setOnClickListener { finish() }

        binding.btnLivenessShutter.setOnClickListener {
            captureSelfie()
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startFrontCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startFrontCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.livenessPreviewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                binding.faceOvalOverlay.isFaceAligned = true
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureSelfie() {
        val capture = imageCapture ?: return
        val selfieFile = File(cacheDir, "selfie_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(selfieFile).build()

        binding.tvLivenessGuidance.text = "Verifying biometric liveness..."
        binding.btnLivenessShutter.isEnabled = false

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val selfieBitmap = BitmapFactory.decodeFile(selfieFile.absolutePath)
                    processFullVerification(selfieBitmap, selfieFile.absolutePath)
                }

                override fun onError(exception: ImageCaptureException) {
                    binding.tvLivenessGuidance.text = "Capture error, please try again"
                    binding.btnLivenessShutter.isEnabled = true
                }
            }
        )
    }

    private fun processFullVerification(selfieBitmap: Bitmap, selfiePath: String) {
        lifecycleScope.launch {
            val frontBitmap = frontPath?.let { BitmapFactory.decodeFile(it) } ?: selfieBitmap
            val backBitmap = backPath?.let { BitmapFactory.decodeFile(it) }

            // Export Multi-page PDF
            val exportedPdf = PdfExportEngine.exportToPdf(
                this@LivenessCaptureActivity,
                frontBitmap,
                backBitmap
            )

            // Execute mock/pluggable verification
            val client = MockIdentityVerificationClient()
            val result = client.executeFullVerification(
                frontBitmap,
                backBitmap,
                selfieBitmap,
                exportedPdf.absolutePath
            )

            // Cache result bitmaps for summary
            val resultIntent = Intent(this@LivenessCaptureActivity, VerificationResultActivity::class.java).apply {
                putExtra(VerificationResultActivity.EXTRA_PDF_PATH, exportedPdf.absolutePath)
                putExtra(VerificationResultActivity.EXTRA_STATUS, result.status)
                putExtra(VerificationResultActivity.EXTRA_LIVENESS_SCORE, result.livenessScore)
                putExtra(VerificationResultActivity.EXTRA_MATCH_SCORE, result.faceMatchScore)
                putExtra(VerificationResultActivity.EXTRA_FRONT_PATH, frontPath)
                putExtra(VerificationResultActivity.EXTRA_SELFIE_PATH, selfiePath)
            }
            startActivity(resultIntent)
            finish()
        }
    }

    companion object {
        private const val TAG = "LivenessCaptureActivity"
        const val EXTRA_FRONT_PATH = "extra_front_path"
        const val EXTRA_BACK_PATH = "extra_back_path"
        const val EXTRA_CONFIG = "extra_config"
        const val EXTRA_THEME = "extra_theme"
    }
}
