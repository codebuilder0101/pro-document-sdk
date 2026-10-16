package com.document.capture.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * Converts PDF pages into high-resolution Bitmaps for document detection and manual cropping.
 */
object PdfDocumentRasterizer {

    /**
     * Renders page [pageIndex] of the given PDF file to a high-res Bitmap.
     * @param renderDpi Target DPI (default 200 for crisp document scan resolution).
     */
    fun rasterizePage(
        file: File,
        pageIndex: Int = 0,
        renderDpi: Int = 200
    ): Bitmap? {
        if (!file.exists()) return null

        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)

        try {
            if (pageIndex >= renderer.pageCount) return null
            val page = renderer.openPage(pageIndex)

            // PDF dimensions are in 72 DPI points
            val scale = renderDpi / 72f
            val width = (page.width * scale).roundToInt()
            val height = (page.height * scale).roundToInt()

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            return bitmap
        } finally {
            renderer.close()
            pfd.close()
        }
    }

    /**
     * Copies a content URI into a temporary cache file and rasterizes the first page.
     */
    fun rasterizeUri(context: Context, uri: Uri, pageIndex: Int = 0): Bitmap? {
        val tempFile = File(context.cacheDir, "temp_import_${System.currentTimeMillis()}.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        val bitmap = rasterizePage(tempFile, pageIndex)
        tempFile.delete()
        return bitmap
    }
}
