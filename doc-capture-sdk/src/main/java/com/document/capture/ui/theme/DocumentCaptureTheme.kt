package com.document.capture.ui.theme

import android.graphics.Color
import java.io.Serializable

/**
 * White-label theme configuration for colors, branding, and text.
 */
data class DocumentCaptureTheme(
    val primaryColor: Int = Color.parseColor("#0D99FF"),
    val accentColor: Int = Color.parseColor("#00E676"),
    val warningColor: Int = Color.parseColor("#FFAB00"),
    val errorColor: Int = Color.parseColor("#FF3D00"),
    val surfaceColor: Int = Color.parseColor("#121212"),
    val surfaceTextColor: Int = Color.parseColor("#FFFFFF"),
    val overlayMaskColor: Int = Color.parseColor("#AA000000"),
    val quadBorderColor: Int = Color.parseColor("#0D99FF"),
    val quadValidBorderColor: Int = Color.parseColor("#00E676"),
    val cornerHandleColor: Int = Color.parseColor("#FFFFFF"),
    val loupeBorderColor: Int = Color.parseColor("#0D99FF"),
    val loupeZoom: Float = 2.5f,
    val brandName: String = "ID Document Scanner",
    val titleFront: String = "Capture Document Front",
    val titleBack: String = "Capture Document Back",
    val instructionFront: String = "Position the front of the document inside the frame",
    val instructionBack: String = "Flip the document and align the back side inside the frame"
) : Serializable {

    companion object {
        var current: DocumentCaptureTheme = DocumentCaptureTheme()

        fun defaultDark() = DocumentCaptureTheme()

        fun fintechNavy() = DocumentCaptureTheme(
            primaryColor = Color.parseColor("#1A73E8"),
            accentColor = Color.parseColor("#34A853"),
            quadBorderColor = Color.parseColor("#1A73E8"),
            loupeBorderColor = Color.parseColor("#1A73E8"),
            brandName = "Fintech Trust KYC"
        )

        fun healthcareEmerald() = DocumentCaptureTheme(
            primaryColor = Color.parseColor("#00BFA5"),
            accentColor = Color.parseColor("#64DD17"),
            quadBorderColor = Color.parseColor("#00BFA5"),
            loupeBorderColor = Color.parseColor("#00BFA5"),
            brandName = "HealthID Verified"
        )
    }
}
