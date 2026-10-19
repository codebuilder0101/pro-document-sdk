package com.document.capture.core.detector

import com.document.capture.core.model.CaptureConfig
import com.document.capture.core.model.PointF
import com.document.capture.core.model.QualityMetrics
import com.document.capture.core.model.QualityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentDetectorTest {

    private val detector = DocumentDetector()

    /**
     * Helper to create an ARGB pixel value from a grayscale luminance value (0..255).
     */
    private fun grayPixel(lum: Int): Int {
        val l = lum.coerceIn(0, 255)
        return (0xFF shl 24) or (l shl 16) or (l shl 8) or l
    }

    @Test
    fun testFindDocumentBoundariesOnCleanDocument() {
        val w = 400
        val h = 600
        val pixels = IntArray(w * h)

        // Dark background (luminance 35)
        pixels.fill(grayPixel(35))

        // White document sheet placed in center: left=60, top=100, right=340, bottom=500
        val docLeft = 60
        val docTop = 100
        val docRight = 340
        val docBottom = 500

        for (y in docTop..docBottom) {
            for (x in docLeft..docRight) {
                pixels[y * w + x] = grayPixel(220)
            }
        }

        val quad = detector.findDocumentBoundaries(pixels, w, h)
        assertNotNull("Detector should detect a clear white document on dark surface", quad)

        // Detected bounds should closely match doc coordinates within step margin
        val pts = quad!!.corners
        assertTrue("Top-Left x should be near docLeft", pts[0].x in (docLeft - 15f)..(docLeft + 15f))
        assertTrue("Top-Left y should be near docTop", pts[0].y in (docTop - 15f)..(docTop + 15f))
        assertTrue("Bottom-Right x should be near docRight", pts[2].x in (docRight - 15f)..(docRight + 15f))
        assertTrue("Bottom-Right y should be near docBottom", pts[2].y in (docBottom - 15f)..(docBottom + 15f))
    }

    @Test
    fun testFindDocumentBoundariesIgnoresInternalHighContrastHeader() {
        val w = 400
        val h = 600
        val pixels = IntArray(w * h)

        // Dark background (luminance 35)
        pixels.fill(grayPixel(35))

        // White document sheet placed in center: left=60, top=100, right=340, bottom=500
        val docLeft = 60
        val docTop = 100
        val docRight = 340
        val docBottom = 500

        for (y in docTop..docBottom) {
            for (x in docLeft..docRight) {
                pixels[y * w + x] = grayPixel(220)
            }
        }

        // Add a high-contrast dark header banner inside the document (top=150..180) like a CNH/passport header
        for (y in 150..180) {
            for (x in (docLeft + 10)..(docRight - 10)) {
                pixels[y * w + x] = grayPixel(5)
            }
        }

        val quad = detector.findDocumentBoundaries(pixels, w, h)
        assertNotNull("Detector should detect document despite internal high contrast banner", quad)

        // The top of the detected document MUST remain near docTop (100), NOT truncated to internal header (150)
        val pts = quad!!.corners
        assertTrue("Top-Left y must detect outer document edge (~100), not inner header (~150). Found: ${pts[0].y}", pts[0].y in (docTop - 15f)..(docTop + 15f))
    }

    @Test
    fun testRejectClutterAndNonDocumentScene() {
        val w = 400
        val h = 600
        val pixels = IntArray(w * h)

        // Scene mimicking a laptop keyboard + wooden desk:
        // Left side: alternating keys (random high frequency noise)
        // Right side: dark brown wood
        for (y in 0 until h) {
            for (x in 0 until w) {
                pixels[y * w + x] = if (x < w / 2) {
                    // Keyboard: alternating dark keys and lighter chassis lines
                    if ((x / 10 + y / 10) % 2 == 0) grayPixel(30) else grayPixel(160)
                } else {
                    // Wood desk: mid-tone with faint streaks
                    grayPixel(85 + (x % 5))
                }
            }
        }

        val quad = detector.findDocumentBoundaries(pixels, w, h)
        assertNull("Detector must return null when pointing at desk clutter without a real document", quad)
    }

    @Test
    fun testRejectDisparateCornerSubstrates() {
        val w = 400
        val h = 600
        val pixels = IntArray(w * h)

        // Fill with four very different corner patches
        // TL is dark screen (30), TR is wood (70), BL is bright palmrest (190), BR is mid desk (90)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val lum = when {
                    x < w / 2 && y < h / 2 -> 30
                    x >= w / 2 && y < h / 2 -> 70
                    x < w / 2 && y >= h / 2 -> 190
                    else -> 90
                }
                pixels[y * w + x] = grayPixel(lum)
            }
        }

        val isUniform = detector.isSubstrateUniform(pixels, w, h, 50, 350, 80, 520)
        assertFalse("Substrate with wildly different corner luminances should not be recognized as a document", isUniform)
    }

    @Test
    fun testAutoCaptureControllerResetsOnMissingDocument() {
        var captureTriggered = false
        val config = CaptureConfig(autoCaptureEnabled = true, stabilityThresholdFrames = 24)
        val controller = AutoCaptureController(config) {
            captureTriggered = true
        }

        // When quad is null (no document)
        val metricsNoDoc = QualityMetrics.initial()
        val progress = controller.processFrame(null, metricsNoDoc)

        assertEquals(0f, progress, 0.001f)
        assertFalse("Capture should never trigger when no document is in frame", captureTriggered)
    }

    @Test
    fun testAutoCaptureControllerRequiresFullStabilityDuration() {
        var captureTriggered = false
        val config = CaptureConfig(autoCaptureEnabled = true, stabilityThresholdFrames = 24)
        val controller = AutoCaptureController(config) {
            captureTriggered = true
        }

        val validQuad = com.document.capture.core.model.DocumentQuad(
            topLeft = PointF(100f, 150f),
            topRight = PointF(300f, 150f),
            bottomRight = PointF(300f, 450f),
            bottomLeft = PointF(100f, 450f)
        )
        val passingMetrics = QualityMetrics(
            blurScore = 150f,
            glareRatio = 0.01f,
            meanLuminance = 140f,
            skewRatio = 0.05f,
            coverageRatio = 0.40f,
            isStable = true,
            status = QualityStatus.PASS,
            guidanceMessage = "Perfect! Hold still..."
        )

        // Feed 10 passing frames (less than the 24 threshold)
        for (i in 1..10) {
            val prog = controller.processFrame(validQuad, passingMetrics)
            assertTrue("Progress should be increasing", prog > 0f)
            assertFalse("Capture should not trigger before 24 frames", captureTriggered)
        }

        // Interrupt with 1 failing frame (e.g. document moved or not passing)
        val failingProgress = controller.processFrame(null, QualityMetrics.initial())
        assertEquals("Countdown must immediately reset on interruption", 0f, failingProgress, 0.001f)
    }
}
