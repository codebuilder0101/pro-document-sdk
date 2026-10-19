package com.document.capture.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
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
import com.document.capture.core.model.QualityMetrics
import com.document.capture.core.model.QualityStatus
import com.document.capture.core.quality.QualityAnalyzer
import com.document.capture.core.util.getSerializableCompat
import com.document.capture.databinding.ActivityCameraCaptureBinding
import com.document.capture.ui.DocumentCaptureSDK
import com.document.capture.ui.crop.CamScannerCropActivity
import com.document.capture.ui.theme.DocumentCaptureTheme
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class DocumentCameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraCaptureBinding
    private val detector = DocumentDetector()
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private var imageCapture: ImageCapture? = null
    private var autoCaptureController: AutoCaptureController? = null
    private var camera: Camera? = null

    private var currentSide = DocumentSide.FRONT
    private var config = CaptureConfig()
    private var theme = DocumentCaptureTheme.current
    private var isTorchOn = false
    private var isProceeding = false
    private var frontProcessedPath: String? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required to capture documents", Toast.LENGTH_LONG).show()
            DocumentCaptureSDK.deliverError("Camera permission denied")
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentSide = intent.getSerializableCompat<DocumentSide>(EXTRA_SIDE) ?: DocumentSide.FRONT
        config = intent.getSerializableCompat<CaptureConfig>(EXTRA_CONFIG) ?: CaptureConfig()
        theme = intent.getSerializableCompat<DocumentCaptureTheme>(EXTRA_THEME) ?: DocumentCaptureTheme.current
        frontProcessedPath = intent.getStringExtra(EXTRA_FRONT_PATH)

        setupViews()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupViews() {
        binding.overlayView.setTheme(theme)

        binding.tvStepTitle.text = if (currentSide == DocumentSide.FRONT) {
            theme.titleFront
        } else {
            theme.titleBack
        }

        binding.tvGuidance.text = if (currentSide == DocumentSide.FRONT) {
            theme.instructionFront
        } else {
            theme.instructionBack
        }

        binding.btnClose.setOnClickListener {
            DocumentCaptureSDK.deliverCancelled()
            finish()
        }

        binding.btnShutter.setOnClickListener {
            capturePhoto()
        }

        binding.btnFlash.setOnClickListener {
            toggleFlash()
        }

        // Inform overlay view of shutter button geometry once view hierarchy is laid out
        binding.btnShutter.post {
            val loc = IntArray(2)
            binding.btnShutter.getLocationOnScreen(loc)
            val overlayLoc = IntArray(2)
            binding.overlayView.getLocationOnScreen(overlayLoc)
            val cx = (loc[0] - overlayLoc[0]) + binding.btnShutter.width / 2f
            val cy = (loc[1] - overlayLoc[1]) + binding.btnShutter.height / 2f
            val radius = (binding.btnShutter.width / 2f) + 12f
            binding.overlayView.setShutterCenter(cx, cy, radius)
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
                camera = cameraProvider.bindToLifecycle(
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
        if (analysisExecutor.isShutdown) {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val rawBitmap = imageProxy.toBitmap()
        imageProxy.close()

        // Normalize frame to portrait display orientation
        val bitmap = if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            if (rotated !== rawBitmap) {
                rawBitmap.recycle()
            }
            rotated
        } else {
            rawBitmap
        }

        val frameWidth = bitmap.width
        val frameHeight = bitmap.height

        val detectedQuad = detector.detect(bitmap, useSmoothing = true)
        val metrics = QualityAnalyzer.evaluate(bitmap, detectedQuad, config)

        val progress = autoCaptureController?.processFrame(detectedQuad, metrics) ?: 0f

        bitmap.recycle()

        runOnUiThread {
            binding.overlayView.updateFrame(detectedQuad, metrics, progress, frameWidth, frameHeight)

            val displayMessage = if (detectedQuad == null) {
                if (currentSide == DocumentSide.FRONT) theme.instructionFront else theme.instructionBack
            } else {
                metrics.guidanceMessage
            }
            binding.tvGuidance.text = displayMessage

            // Dynamic guidance pill border color feedback
            val strokeColor = when (metrics.status) {
                QualityStatus.PASS -> theme.quadValidBorderColor
                QualityStatus.BLUR_DETECTED, QualityStatus.GLARE_DETECTED, QualityStatus.UNSTEADY -> theme.warningColor
                QualityStatus.TOO_DARK, QualityStatus.TOO_BRIGHT, QualityStatus.TOO_SKEWED -> theme.errorColor
                QualityStatus.NOT_IN_FRAME -> 0x44FFFFFF.toInt()
            }
            binding.cardGuidance.strokeColor = strokeColor
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
                    val bitmap = loadAndNormalizeCapturedBitmap(photoFile.absolutePath)
                    onPhotoCaptured(bitmap, photoFile.absolutePath)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                }
            }
        )
    }

    private fun loadAndNormalizeCapturedBitmap(filePath: String): Bitmap {
        val rawBitmap = BitmapFactory.decodeFile(filePath)
            ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        return try {
            val exif = ExifInterface(filePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                if (rotated !== rawBitmap) {
                    rawBitmap.recycle()
                }
                FileOutputStream(filePath).use { out ->
                    rotated.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                rotated
            } else {
                rawBitmap
            }
        } catch (e: Exception) {
            rawBitmap
        }
    }

    private fun onPhotoCaptured(bitmap: Bitmap, filePath: String) {
        val quad = detector.detect(bitmap, useSmoothing = false)
            ?: DocumentQuad.defaultCentered(bitmap.width.toFloat(), bitmap.height.toFloat())
        bitmap.recycle()

        isProceeding = true

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
        camera?.cameraControl?.enableTorch(isTorchOn)
        binding.btnFlash.alpha = if (isTorchOn) 1.0f else 0.6f
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.shutdown()
        camera = null
        if (isFinishing && !isProceeding) {
            DocumentCaptureSDK.deliverCancelled()
        }
    }

    companion object {
        private const val TAG = "DocumentCameraActivity"
        const val EXTRA_SIDE = "extra_side"
        const val EXTRA_CONFIG = "extra_config"
        const val EXTRA_THEME = "extra_theme"
        const val EXTRA_FRONT_PATH = "extra_front_path"
    }
}
