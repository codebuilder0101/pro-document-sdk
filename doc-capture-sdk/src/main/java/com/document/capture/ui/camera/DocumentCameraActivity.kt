package com.document.capture.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.document.capture.core.detector.AutoCaptureController
import com.document.capture.core.detector.DocumentDetector
import com.document.capture.core.model.CaptureConfig
import com.document.capture.core.model.DocumentQuad
import com.document.capture.core.model.DocumentSide
import com.document.capture.core.quality.QualityAnalyzer
import com.document.capture.databinding.ActivityCameraCaptureBinding
import com.document.capture.ui.crop.CamScannerCropActivity
import com.document.capture.ui.theme.DocumentCaptureTheme
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class DocumentCameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraCaptureBinding
    private val detector = DocumentDetector()
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private var imageCapture: ImageCapture? = null
    private var autoCaptureController: AutoCaptureController? = null

    private var currentSide = DocumentSide.FRONT
    private var config = CaptureConfig()
    private var theme = DocumentCaptureTheme.current
    private var isTorchOn = false
    private var frontProcessedPath: String? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required to capture documents", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentSide = (intent.getSerializableExtra(EXTRA_SIDE) as? DocumentSide) ?: DocumentSide.FRONT
        config = (intent.getSerializableExtra(EXTRA_CONFIG) as? CaptureConfig) ?: CaptureConfig()
        theme = (intent.getSerializableExtra(EXTRA_THEME) as? DocumentCaptureTheme) ?: DocumentCaptureTheme.current
        frontProcessedPath = intent.getStringExtra(EXTRA_FRONT_PATH)

        setupViews()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupViews() {
        binding.tvStepTitle.text = if (currentSide == DocumentSide.FRONT) {
            theme.titleFront
        } else {
            theme.titleBack
        }

        binding.btnClose.setOnClickListener { finish() }

        binding.btnShutter.setOnClickListener {
            capturePhoto()
        }

        binding.btnFlash.setOnClickListener {
            toggleFlash()
        }

        autoCaptureController = AutoCaptureController(config) {
            runOnUiThread {
                capturePhoto()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(analysisExecutor) { imageProxy ->
                        processImageAnalysis(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageAnalysis(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()
        imageProxy.close()

        val detectedQuad = detector.detect(bitmap, useSmoothing = true)
        val metrics = QualityAnalyzer.evaluate(bitmap, detectedQuad, config)

        val progress = autoCaptureController?.processFrame(detectedQuad, metrics) ?: 0f

        runOnUiThread {
            binding.overlayView.updateFrame(detectedQuad, metrics, progress)
            binding.tvGuidance.text = metrics.guidanceMessage
        }
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: return

        val photoFile = File(cacheDir, "doc_raw_${currentSide.name}_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                    onPhotoCaptured(bitmap, photoFile.absolutePath)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                }
            }
        )
    }

    private fun onPhotoCaptured(bitmap: Bitmap, filePath: String) {
        // Detect initial quad on captured frame
        val quad = detector.detect(bitmap, useSmoothing = false)

        val cropIntent = Intent(this, CamScannerCropActivity::class.java).apply {
            putExtra(CamScannerCropActivity.EXTRA_IMAGE_PATH, filePath)
            putExtra(CamScannerCropActivity.EXTRA_QUAD, quad)
            putExtra(CamScannerCropActivity.EXTRA_SIDE, currentSide)
            putExtra(CamScannerCropActivity.EXTRA_CONFIG, config)
            putExtra(CamScannerCropActivity.EXTRA_THEME, theme)
            putExtra(CamScannerCropActivity.EXTRA_FRONT_PATH, frontProcessedPath)
        }
        startActivity(cropIntent)
        finish()
    }

    private fun toggleFlash() {
        isTorchOn = !isTorchOn
        binding.btnFlash.alpha = if (isTorchOn) 1.0f else 0.6f
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.shutdown()
    }

    companion object {
        private const val TAG = "DocumentCameraActivity"
        const val EXTRA_SIDE = "extra_side"
        const val EXTRA_CONFIG = "extra_config"
        const val EXTRA_THEME = "extra_theme"
        const val EXTRA_FRONT_PATH = "extra_front_path"
    }
}
