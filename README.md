# 📄 Document Capture SDK & Sample App

Enterprise-grade, white-label **Document Capture SDK** for high-quality document onboarding and identity verification. It solves issues with poorly cropped, skewed, blurry, or glaring documents with CamScanner-style automated edge detection, interactive manual corner adjustment with a **2.5× Magnifying Loupe**, perspective homography correction, real-time quality verification, and an end-to-end KYC identity pipeline (Document Capture $\rightarrow$ Liveness Selfie $\rightarrow$ 1:1 Biometric Face Match).

---

## 🏗️ Architecture & Modules

The repository is organized into a modular decoupled architecture:

```
document/
├── doc-capture-sdk/                 # Core Android Library Module (AAR)
│   ├── src/main/java/com/document/capture/
│   │   ├── core/
│   │   │   ├── model/              # DocumentQuad, PointF, QualityMetrics, CaptureConfig
│   │   │   ├── detector/           # Real-time DocumentDetector & AutoCaptureController
│   │   │   ├── quality/            # QualityAnalyzer (Blur, Glare, Lighting, Skew, Coverage)
│   │   │   ├── processor/          # PerspectiveCorrectionEngine & ImageEnhancer
│   │   │   ├── pdf/                # PdfDocumentRasterizer & PdfExportEngine
│   │   │   └── network/            # DocumentOcrService, LivenessService, MockEndpointClient
│   │   └── ui/
│   │       ├── camera/             # CameraX viewfinder with real-time HUD & overlay
│   │       ├── crop/               # CamScannerCropView (8 handles + 2.5× Loupe)
│   │       ├── liveness/           # LivenessCaptureActivity (biometric face oval)
│   │       ├── result/             # VerificationResultActivity & PDF sharing
│   │       └── theme/              # DocumentCaptureTheme (White-label styling)
│   └── src/test/java/              # Unit tests for geometry, ordering & convexity
└── sample-app/                      # Test Harness Application (APK)
    ├── src/main/assets/            # Preloaded Brazilian CNH (PDF & High-Res JPGs)
    └── src/main/java/com/document/capture/sample/
        ├── MainActivity.kt         # Dashboard with Live KYC, Reference Doc, and File Import
        ├── ReferenceDocTestActivity.kt # Interactive test of the Brazilian CNH PDF
        └── QualityLabActivity.kt   # Live diagnostics for Blur, Glare & Luminance
```

---

## 🚀 Quick Start: Integrating `doc-capture-sdk`

### 1. Add Library Dependency

In your host application's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":doc-capture-sdk"))
}
```

### 2. Launch Full White-Label KYC Workflow

```kotlin
import com.document.capture.ui.DocumentCaptureSDK
import com.document.capture.ui.theme.DocumentCaptureTheme
import com.document.capture.core.model.CaptureConfig

// Configure custom white-label brand theme
val customTheme = DocumentCaptureTheme(
    primaryColor = Color.parseColor("#0D99FF"),
    accentColor = Color.parseColor("#00E676"),
    brandName = "Acme Trust KYC"
)

// Launch end-to-end document capture + liveness selfie + face match
DocumentCaptureSDK.Builder(this)
    .setTheme(customTheme)
    .setConfig(
        CaptureConfig(
            requireBackSide = true,
            requireLiveness = true,
            autoCaptureEnabled = true,
            minBlurScore = 95f,
            maxGlareRatio = 0.035f
        )
    )
    .launch(this)
```

---

## 🛠️ Headless Computer Vision API (No UI Dependency)

For host applications that already maintain a custom camera implementation, the SDK can be consumed as a headless image processing engine:

```kotlin
import com.document.capture.ui.DocumentCaptureSDK
import com.document.capture.core.model.EnhancementMode

// 1. Detect document quadrilateral
val quad = DocumentCaptureSDK.detectEdges(frameBitmap)

// 2. Evaluate real-time image quality gates
val metrics = DocumentCaptureSDK.checkQuality(frameBitmap, quad)
if (metrics.isPassing) {
    // 3. Rectify skewed document using homography perspective warp
    val rectified = DocumentCaptureSDK.rectifyDocument(frameBitmap, quad)

    // 4. Enhance text contrast with CamScanner "Magic Color" or "Crisp B&W"
    val enhanced = DocumentCaptureSDK.enhanceDocument(rectified, EnhancementMode.MAGIC_COLOR)

    // 5. Export to standardized multi-page PDF
    val pdfFile = DocumentCaptureSDK.exportPdf(context, enhanced, backEnhanced)
}
```

---

## 🧪 Testing with the Reference Document (Brazilian CNH)

The test harness bundles the attached reference document (`reference_cnh.pdf`, `sample_cnh_front.jpg`, `sample_cnh_back.jpg`):

1. **Launch the Sample App** on an Android device or emulator.
2. Select **"Test with Reference CNH Document"**:
   - **Front License Tab**: Loads Danilo Marques de Oliveira's CNH card. Touch and drag any corner handle to activate the **2.5× Magnifying Loupe** with precision crosshairs (+).
   - **Full Digital PDF Page Tab**: Renders the complete digital PDF vector page, runs boundary detection, and unwarps the document into a high-res image.
   - Tap **"Perspective Rectify & Run KYC"**: Rectifies the document, runs simulated OCR data extraction, verifies biometric liveness, performs face matching ($97.8\%$), and provides a one-tap multi-page PDF export.
3. Open **"Quality Checks Diagnostic Lab"**:
   - Inspects real-time focus blur variance (Laplacian score), specular glare ratio, and luminance histograms.

---

## 📱 Cross-Platform Parity Details

| Feature | Android SDK (`doc-capture-sdk`) | iOS Native Parity | React Native Bridge |
| :--- | :--- | :--- | :--- |
| **Edge Detection** | `DocumentDetector` (Kotlin / Canvas / Gradients) | Vision framework `VNDetectDocumentSegmentationRequest` | JSI / TurboModule interface |
| **Perspective Warp** | `Matrix.setPolyToPoly` homography transform | CoreImage `CIPerspectiveCorrection` | Headless & Native View bridge |
| **Quality Analysis** | Laplacian variance, specular glare, luminance | vImage / Metal accelerated filters | Async Promise `<QualityMetrics>` |
| **CamScanner Loupe** | Custom `CamScannerCropView` with 2.5× floating reticle | UIKit custom loupe layer with `CATransform3D` | `<CamScannerCropView />` component |
| **PDF Processing** | Android `PdfRenderer` & `PdfDocument` | iOS `PDFKit` (`PDFDocument`, `PDFPage`) | Multi-page export bridge |
| **Biometrics** | Front camera liveness oval + 1:1 face matching | TrueDepth / AVFoundation face tracking | Full KYC orchestration callback |

---

## 📦 Build Artifacts

- **Library AAR**: `doc-capture-sdk/build/outputs/aar/doc-capture-sdk-debug.aar`
- **Sample App APK**: `sample-app/build/outputs/apk/debug/sample-app-debug.apk`
