package com.example.ocr.core

import com.example.ocr.core.api.*
import net.sourceforge.tess4j.Tesseract
import java.awt.image.BufferedImage

class Tess4JOcrEngine(private val datapath: String) : OcrEngine {
    override suspend fun recognize(image: OcrImage, lang: String): OcrPageResult {
        val bi = when (image) {
            is OcrImage.Gray8 -> gray8(image)
            is OcrImage.Rgba8888 -> rgba(image)
        }
        val t = Tesseract().apply {
            setDatapath(datapath)       // folder that contains "tessdata/"
            setLanguage(lang)           // e.g. "vie+eng"
            setTessVariable("user_defined_dpi", "300")
        }
        val text = t.doOCR(bi) ?: ""
        return OcrPageResult(1, text.trim())
    }
    private fun gray8(g: OcrImage.Gray8): BufferedImage {
        val img = BufferedImage(g.width, g.height, BufferedImage.TYPE_BYTE_GRAY)
        val row = ByteArray(g.width)
        for (y in 0 until g.height) {
            System.arraycopy(g.bytes, y * g.rowStride, row, 0, g.width)
            img.raster.setDataElements(0, y, g.width, 1, row)
        }
        return img
    }
    private fun rgba(r: OcrImage.Rgba8888): BufferedImage {
        val img = BufferedImage(r.width, r.height, BufferedImage.TYPE_INT_ARGB)
        var si = 0
        val line = IntArray(r.width)
        for (y in 0 until r.height) {
            var xOff = 0
            for (x in 0 until r.width) {
                val R = r.bytes[si + xOff].toInt() and 0xFF
                val G = r.bytes[si + xOff + 1].toInt() and 0xFF
                val B = r.bytes[si + xOff + 2].toInt() and 0xFF
                val A = r.bytes[si + xOff + 3].toInt() and 0xFF
                line[x] = (A shl 24) or (R shl 16) or (G shl 8) or B
                xOff += 4
            }
            img.setRGB(0, y, r.width, 1, line, 0, r.width)
            si += r.rowStride
        }
        return img
    }
}
