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
            setDatapath(datapath)      // e.g. .../tmp/xxx  (must contain "tessdata/")
            setLanguage(lang)          // e.g., "vie+eng"
            // If you want to tweak variables, use try/catch (older Tess4J may not expose this method)
            // runCatching { setTessVariable("user_defined_dpi", "300") }
        }
        val text = t.doOCR(bi) ?: ""
        return OcrPageResult(pageNo = 1, text = text.trim())
    }

    private fun gray8ToBuffered(g: OcrImage.Gray8): BufferedImage {
        val img = BufferedImage(g.width, g.height, BufferedImage.TYPE_BYTE_GRAY)
        var src = 0
        val row = ByteArray(g.width)
        for (y in 0 until g.height) {
            System.arraycopy(g.bytes, src, row, 0, g.width)
            img.raster.setDataElements(0, y, g.width, 1, row)
            src += g.rowStride
        }
        return img
    }

    private fun rgbaToBuffered(r: OcrImage.Rgba8888): BufferedImage {
        val img = BufferedImage(r.width, r.height, BufferedImage.TYPE_INT_ARGB)
        val w = r.width
        for (y in 0 until r.height) {
            val rowStart = y * r.rowStride
            var xOff = 0
            for (x in 0 until w) {
                val R = r.bytes[rowStart + xOff].toInt() and 0xFF
                val G = r.bytes[rowStart + xOff + 1].toInt() and 0xFF
                val B = r.bytes[rowStart + xOff + 2].toInt() and 0xFF
                val A = r.bytes[rowStart + xOff + 3].toInt() and 0xFF
                val argb = (A shl 24) or (R shl 16) or (G shl 8) or B
                img.setRGB(x, y, argb)
                xOff += 4
            }
        }
        return img
    }
}
