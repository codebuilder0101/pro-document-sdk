# 📄 Document Capture SDK & Document Testing Demo App

Enterprise-grade, white-label **Document Capture SDK** for high-quality document onboarding and data extraction. It solves issues with poorly cropped, skewed, blurry, or glaring document photos with CamScanner-style automated edge detection, interactive manual corner adjustment with an 8-handle reticle and **2.5× Magnifying Loupe**, perspective homography correction, real-time quality verification, multi-page PDF processing, and integration with document identification and OCR data extraction endpoints.

---

## 🗺️ Product & Identity Verification Roadmap

The complete multi-stage onboarding workflow is designed as follows:

1. **📄 Document Capture SDK (This Repository)**:
   - Captures high-quality front (and back) document images or imports PDF/image files.
   - CamScanner-style auto edge detection, 8-handle manual corner adjustment with 2.5× zoom loupe, and perspective unwarping.
   - Evaluates quality gates (Laplacian focus blur, specular glare hotspots, luminance).
   - Sends the rectified document to the endpoint, which identifies the document type and extracts structured data fields.
   - Exports standardized, rectified multi-page PDF files.

2. **👤 Biometric Liveness (Future Roadmap)**:
   - Validates that the subject is a real, live human being and captures the optimal facial image.

3. **🤝 1:1 Face Match (Future Roadmap)**:
   - Compares the verified liveness selfie against the photo extracted from the document at the comparison endpoint.

> **Scope Note**: This repository strictly contains the **Document Capture SDK** (`:doc-capture-sdk`) and a dedicated **Document Testing Sample App** (`:sample-app`).

---

## 🏗️ Architecture & Modules

```
document/
├── doc-capture-sdk/                 # Core Android Library Module (AAR)
│   ├── src/main/java/com/document/capture/
│   │   ├── core/
│   │   │   ├── model/              # DocumentQuad, QualityMetrics, CaptureConfig, DocumentVerificationResult
│   │   │   ├── detector/           # Real-time DocumentDetector & AutoCaptureController
│   │   │   ├── quality/            # QualityAnalyzer (Laplacian blur, glare, lighting, skew)
│   │   │   ├── processor/          # PerspectiveCorrectionEngine & ImageEnhancer
│   │   │   ├── pdf/                # Multi-page PdfDocumentRasterizer & PdfExportEngine
│   │   │   └── network/            # DocumentEndpointService & MockDocumentEndpointClient
│   │   └── ui/
│   │       ├── camera/             # CameraX viewfinder with real-time HUD, quality alerts & auto-shutter
│   │       ├── crop/               # CamScannerCropView (8 handles + 2.5× Loupe) & CamScannerCropActivity
│   │       ├── result/             # VerificationResultActivity (planar doc display, OCR fields, PDF sharing)
│   │       └── theme/              # DocumentCaptureTheme (White-label styling)
│   └── src/test/java/              # Unit tests for geometry, ordering & convexity
└── sample-app/                      # Document Testing Harness Application (APK)
    ├── src/main/assets/            # Preloaded Brazilian CNH (reference_cnh.pdf, sample_cnh_front.jpg, sample_cnh_back.jpg)
    └── src/main/java/com/document/capture/sample/
        ├── MainActivity.kt         # Document Testing Dashboard
        ├── ReferenceDocTestActivity.kt # Attached Brazilian CNH PDF/images tester
        ├── PdfDocumentTesterActivity.kt # Any single/multi-page PDF tester with page navigation
        └── QualityLabActivity.kt   # Live diagnostics for Blur, Glare, Luminance & Skew
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

### 2. Launch White-Label Document Capture

```kotlin
import com.document.capture.ui.DocumentCaptureSDK
import com.document.capture.ui.theme.DocumentCaptureTheme
import com.document.capture.core.model.CaptureConfig

// Configure custom white-label brand theme
val customTheme = DocumentCaptureTheme(
    primaryColor = Color.parseColor("#0D99FF"),
    accentColor = Color.parseColor("#00E676"),
    brandName = "Acme Trust Doc SDK"
)

// Launch document capture (single-sided or two-sided front & back)
DocumentCaptureSDK.Builder(this)
    .setTheme(customTheme)
    .setConfig(
        CaptureConfig(
            requireBackSide = false, // Set true for IDs / Driver Licenses
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

## 🧪 Document Testing Harness Features

The sample app (`:sample-app`) is dedicated exclusively to testing document capture, rectification, PDF handling, and OCR extraction:

1. **Live Camera Document Capture**:
   - Choose between **Single-Sided** (e.g. Passport, Page, Invoice) or **Two-Sided** (Front & Back ID).
   - Real-time quality feedback: blur detection, specular glare warning, and hold-still guidance.
   - Auto-captures when stable or allows manual capture.
   - CamScanner 8-handle crop screen with 2.5× Loupe, Magic Color filter, and direct OCR result screen.

2. **Test Reference CNH Document**:
   - Instant testing with the bundled Brazilian CNH (`sample_cnh_front.jpg`, `sample_cnh_back.jpg`, `reference_cnh.pdf`).
   - Switch between Front, Back, or PDF vector page.
   - Drag corner handles to align boundaries with 2.5× precision loupe and test perspective rectification.

3. **Test Any PDF Document (Multi-Page)**:
   - Open and test any external single-page or multi-page PDF document.
   - Interactive page-by-page navigation (Previous / Next Page).
   - Auto-detects document boundaries on each page, allows interactive 8-handle adjustment, perspective unwarping, and standardized PDF re-export.

4. **Test Any Image from Device**:
   - Pick skewed, poorly cropped, or shadowed document photos from the gallery.
   - Adjust handles and run rectification and OCR field extraction.

5. **Document Quality Diagnostics Lab**:
   - Live visual meters for focus blur variance ($V_{Laplacian}$), specular glare hotspot percentage, and mean luminance.

---

## 📱 Cross-Platform Parity Details

| Feature | Android SDK (`doc-capture-sdk`) | iOS Native Parity | React Native Bridge |
| :--- | :--- | :--- | :--- |
| **Edge Detection** | `DocumentDetector` (Kotlin / Canvas / Gradients) | Vision framework `VNDetectDocumentSegmentationRequest` | JSI / TurboModule interface |
| **Perspective Warp** | `Matrix.setPolyToPoly` homography transform | CoreImage `CIPerspectiveCorrection` | Headless & Native View bridge |
| **Quality Analysis** | Laplacian variance, specular glare, luminance | vImage / Metal accelerated filters | Async Promise `<QualityMetrics>` |
| **CamScanner Loupe** | Custom `CamScannerCropView` with 2.5× floating reticle | UIKit custom loupe layer with `CATransform3D` | `<CamScannerCropView />` component |
| **PDF Processing** | Multi-page `PdfRenderer` & `PdfDocument` | iOS `PDFKit` (`PDFDocument`, `PDFPage`) | Multi-page export bridge |
| **Document Endpoint**| Identification & OCR field extraction | REST / gRPC client | React Native client service |

---

## 📦 Build Artifacts

- **Library AAR**: `doc-capture-sdk/build/outputs/aar/doc-capture-sdk-debug.aar`
- **Sample App APK**: `sample-app/build/outputs/apk/debug/sample-app-debug.apk`
