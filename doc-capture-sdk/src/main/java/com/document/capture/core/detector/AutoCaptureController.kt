package com.document.capture.core.detector

import com.document.capture.core.model.CaptureConfig
import com.document.capture.core.model.DocumentQuad
import com.document.capture.core.model.QualityMetrics
import kotlin.math.max

/**
 * Manages frame stability tracking and triggers automated capture when quality conditions are met.
 */
class AutoCaptureController(
    private val config: CaptureConfig,
    private val onTriggerCapture: () -> Unit
) {

    private var previousQuad: DocumentQuad? = null
    private var consecutivePassingFrames = 0
    private val stabilityMovementThresholdPx = 15f

    var isCapturing = false
        private set

    /**
     * Call for each analyzed frame.
     * @return Current capture countdown progress (0.0f to 1.0f).
     */
    fun processFrame(quad: DocumentQuad?, metrics: QualityMetrics): Float {
        if (isCapturing || !config.autoCaptureEnabled) {
            return 0f
        }

        if (quad == null || !metrics.isPassing) {
            consecutivePassingFrames = 0
            previousQuad = null
            return 0f
        }

        val prev = previousQuad
        if (prev != null) {
            val dTl = quad.topLeft.distanceTo(prev.topLeft)
            val dTr = quad.topRight.distanceTo(prev.topRight)
            val dBr = quad.bottomRight.distanceTo(prev.bottomRight)
            val dBl = quad.bottomLeft.distanceTo(prev.bottomLeft)
            val maxShift = max(max(dTl, dTr), max(dBr, dBl))

            if (maxShift < stabilityMovementThresholdPx) {
                consecutivePassingFrames++
            } else {
                consecutivePassingFrames = max(0, consecutivePassingFrames - 1)
            }
        } else {
            consecutivePassingFrames = 1
        }

        previousQuad = quad

        if (consecutivePassingFrames >= config.stabilityThresholdFrames) {
            isCapturing = true
            onTriggerCapture()
            return 1.0f
        }

        return progress()
    }

    private fun progress(): Float {
        return (consecutivePassingFrames.toFloat() / config.stabilityThresholdFrames).coerceIn(0f, 1f)
    }

    fun reset() {
        consecutivePassingFrames = 0
        previousQuad = null
        isCapturing = false
    }
}
