package com.document.capture.core.model

/**
 * Quality evaluation status for live camera frames and captured documents.
 */
enum class QualityStatus {
    PASS,
    BLUR_DETECTED,
    GLARE_DETECTED,
    TOO_DARK,
    TOO_BRIGHT,
    UNSTEADY,
    NOT_IN_FRAME,
    TOO_SKEWED
}

enum class DocumentSide {
    FRONT,
    BACK
}

enum class EnhancementMode {
    ORIGINAL,
    MAGIC_COLOR,
    CRISP_BW
}

enum class OutputFormat {
    JPEG,
    PNG,
    PDF,
    ALL
}
