package com.example.ocr.core

import com.example.ocr.core.api.*
import net.sourceforge.tess4j.Tesseract
import java.awt.image.BufferedImage

/**
 * Test-only OcrEngine based on Tess4J (JVM) so we can run OCR in plain unit tests.
 * datapath must point to a directory that CONTAINS a "tessdata" subfolder.
 */
class Tess4JOcrEngine(private val datapath: String) : OcrEngine {

    override suspend fun recognize(image: OcrImage, lang: String): OcrPageResult {
        val bi = when (image) {
            is OcrImage.Gray8    -> gray8ToBuffered(image)
            is OcrImage.Rgba8888 -> rgbaToBuffered(image)
        }
        val t = Tesseract().apply {
            setDatapath(datapath)
            setLanguage(lang)            // e.g., "vie+eng"
            setTessVariable("user_defined_dpi", "300")
        }
        val text = t.doOCR(bi) ?: ""
        return OcrPageResult(pageNo = 1, text = text.trim())
    }

    private fun gray8ToBuffered(g: OcrImage.Gray8): BufferedImage {
        val img = BufferedImage(g.width, g.height, BufferedImage.TYPE_BYTE_GRAY)
        val raster = img.raster
        var src = 0
        for (y in 0 until g.height) {
            raster.setDataElements(0, y, g.width, 1, g.bytes.copyOfRange(src, src + g.width))
            src += g.rowStride
        }
        return img
    }

    private fun rgbaToBuffered(r: OcrImage.Rgba8888): BufferedImage {
        val img = BufferedImage(r.width, r.height, BufferedImage.TYPE_INT_ARGB)
        var si = 0
        val row = IntArray(r.width)
        for (y in 0 until r.height) {
            var px = 0
            for (x in 0 until r.width) {
                val R = r.bytes[si + 0].toInt() and 0xFF
                val G = r.bytes[si + 1].toInt() and 0xFF
                val B = r.bytes[si + 2].toInt() and 0xFF
                val A = r.bytes[si + 3].toInt() and 0xFF
                row[px++] = (A shl 24) or (R shl 16) or (G shl 8) or B
                si += 4
            }
            img.setRGB(0, y, r.width, 1, row, 0, r.width)
            si = y.inc() * r.rowStride
        }
        return img
    }
}
