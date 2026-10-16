package com.document.capture.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Robust PDF Document Rasterizer for single-page and multi-page PDF documents.
 * Extracts pages at crisp DPI for document edge detection, cropping, and OCR analysis.
 */
object PdfDocumentRasterizer {

    /**
     * Returns total page count of the given PDF file.
     */
    fun getPageCount(file: File): Int {
        if (!file.exists() || file.length() == 0L) return 0
        return try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            renderer.close()
            pfd.close()
            count
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Returns total page count of a PDF content Uri.
     */
    fun getPageCount(context: Context, uri: Uri): Int {
        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return 0
            val renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            renderer.close()
            pfd.close()
            count
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Renders page [pageIndex] of the given PDF file to a high-res Bitmap.
     * @param renderDpi Target DPI (default 180 for high clarity document scan).
     */
    fun rasterizePage(
        file: File,
        pageIndex: Int = 0,
        renderDpi: Int = 180
    ): Bitmap? {
        if (!file.exists() || file.length() == 0L) return null

        return try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)

            if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                renderer.close()
                pfd.close()
                return null
            }

            val page = renderer.openPage(pageIndex)

            // PDF dimensions are in 72 DPI points
            val scale = renderDpi / 72f
            var width = (page.width * scale).roundToInt()
            var height = (page.height * scale).roundToInt()

            // Memory guard: limit max dimension to 3840px (4K) to prevent OutOfMemoryError
            val maxDimension = 3840
            if (width > maxDimension || height > maxDimension) {
                val downscale = maxDimension.toFloat() / max(width, height)
                width = (width * downscale).roundToInt()
                height = (height * downscale).roundToInt()
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            pfd.close()

            bitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Copies a content URI into a persistent/temporary cache file.
     */
    fun copyUriToCache(context: Context, uri: Uri, targetFileName: String? = null): File? {
        return try {
            val name = targetFileName ?: "imported_doc_${System.currentTimeMillis()}.pdf"
            val file = File(context.cacheDir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            if (file.exists() && file.length() > 0) file else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Rasterizes a specific page from a content Uri.
     */
    fun rasterizeUri(context: Context, uri: Uri, pageIndex: Int = 0, renderDpi: Int = 180): Bitmap? {
        val tempFile = copyUriToCache(context, uri) ?: return null
        val bitmap = rasterizePage(tempFile, pageIndex, renderDpi)
        // Keep tempFile or delete if not needed
        return bitmap
    }
}
