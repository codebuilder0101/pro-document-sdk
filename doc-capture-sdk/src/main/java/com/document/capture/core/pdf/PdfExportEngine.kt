package com.document.capture.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/**
 * Compiles rectified front and back document captures into a multi-page PDF document.
 */
object PdfExportEngine {

    /**
     * Creates a standardized PDF containing front and back document captures.
     */
    fun exportToPdf(
        context: Context,
        frontBitmap: Bitmap,
        backBitmap: Bitmap? = null,
        fileName: String = "scanned_document_${System.currentTimeMillis()}.pdf"
    ): File {
        val document = PdfDocument()

        // Page 1: Front Document
        addPage(document, frontBitmap, 1)

        // Page 2: Back Document (if present)
        if (backBitmap != null) {
            addPage(document, backBitmap, 2)
        }

        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val outputFile = File(exportDir, fileName)

        FileOutputStream(outputFile).use { out ->
            document.writeTo(out)
        }
        document.close()

        return outputFile
    }

    private fun addPage(document: PdfDocument, bitmap: Bitmap, pageNumber: Int) {
        // Standard A4 dimensions in 72 DPI points: 595 x 842
        val pageWidth = 595
        val pageHeight = 842

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // Compute aspect-fit target rect with margin
        val margin = 36f
        val availW = pageWidth - 2 * margin
        val availH = pageHeight - 2 * margin

        val bmpW = bitmap.width.toFloat()
        val bmpH = bitmap.height.toFloat()
        val scale = minOf(availW / bmpW, availH / bmpH)

        val drawW = bmpW * scale
        val drawH = bmpH * scale
        val left = margin + (availW - drawW) / 2f
        val top = margin + (availH - drawH) / 2f

        val dstRect = Rect(left.toInt(), top.toInt(), (left + drawW).toInt(), (top + drawH).toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        canvas.drawBitmap(bitmap, null, dstRect, paint)
        document.finishPage(page)
    }
}
