package com.example.docscan.logic.utils

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.BitmapFactory
import com.example.pipeline_core.EncodedPage
import java.io.File
import java.io.FileOutputStream

data class PdfOptions(
    val dpi: Int = 150,
    val marginMm: Float = 8f,
    val whiteBackground: Boolean = true
)

class AndroidPdfExporter(
    private val context: Context,
    private val options: PdfOptions = PdfOptions()
) {
    fun export(pages: List<EncodedPage>, outFile: File): File {
        require(pages.isNotEmpty()) { "No pages to export." }

        val (a4W, a4H) = a4Pixels(options.dpi)
        val margin = mmToPx(options.marginMm, options.dpi)
        val pdf = PdfDocument()

        try {
            pages.forEachIndexed { i, p ->
                val bmp = BitmapFactory.decodeByteArray(p.png, 0, p.png.size)
                    ?: error("Cannot decode PNG for page ${i + 1}")

                val landscape = bmp.width >= bmp.height
                val pageW = if (landscape) a4H else a4W
                val pageH = if (landscape) a4W else a4H

                val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create()
                val page = pdf.startPage(pageInfo)
                val canvas = page.canvas
                if (options.whiteBackground) canvas.drawColor(Color.WHITE)

                val contentLeft = margin
                val contentTop = margin
                val contentRight = pageW - margin
                val contentBottom = pageH - margin
                val cw = contentRight - contentLeft
                val ch = contentBottom - contentTop

                val scale = minOf(cw.toFloat() / bmp.width, ch.toFloat() / bmp.height)
                val dw = (bmp.width * scale).toInt()
                val dh = (bmp.height * scale).toInt()
                val left = contentLeft + (cw - dw) / 2
                val top = contentTop + (ch - dh) / 2

                canvas.drawBitmap(bmp, null, Rect(left, top, left + dw, top + dh), null)
                pdf.finishPage(page)
                bmp.recycle()
            }

            outFile.parentFile?.mkdirs()
            FileOutputStream(outFile).use { pdf.writeTo(it) }
            return outFile
        } finally {
            pdf.close()
        }
    }

    private fun a4Pixels(dpi: Int): Pair<Int, Int> {
        val wIn = 8.27f
        val hIn = 11.69f
        return (wIn * dpi).toInt() to (hIn * dpi).toInt()
    }

    private fun mmToPx(mm: Float, dpi: Int) = (mm / 25.4f * dpi).toInt()
}