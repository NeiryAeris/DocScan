// ocr-core/src/test/kotlin/com/example/ocr/core/Tess4JOcrEngine.kt
package com.example.ocr.core

import com.example.ocr.core.api.*
import com.example.ocr.core.util.OcrSanitizer
import net.sourceforge.tess4j.Tesseract
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte

class Tess4JOcrEngine(private val datapath: String) : OcrEngine {
    override suspend fun recognize(image: OcrImage, lang: String): OcrPageResult {
        val bi = when (image) {
            is OcrImage.Gray8    -> gray8ToBuffered(image)
            is OcrImage.Rgba8888 -> rgbaToBuffered(image)
        }
        val t = Tesseract().apply {
            setDatapath(datapath)                 // <base> (has tessdata/)
            setLanguage(if (lang.isBlank()) "vie" else lang)  // try pure Vietnamese first
            // Robust across Tess4J versions:
            setVariable("tessedit_pageseg_mode", "6")         // 6 = SINGLE_BLOCK
            setVariable("user_defined_dpi", "300")
            setVariable("tessedit_char_blacklist", "º°•·●○▪▫■□¤§¨¸")
            setVariable("preserve_interword_spaces", "1")
        }
        val text = t.doOCR(bi) ?: ""
        return OcrPageResult(1, OcrSanitizer.sanitize(text.trim()))
    }

    private fun gray8ToBuffered(g: OcrImage.Gray8): BufferedImage {
        val img = BufferedImage(g.width, g.height, BufferedImage.TYPE_BYTE_GRAY)
        val dst = (img.raster.dataBuffer as DataBufferByte).data
        System.arraycopy(g.bytes, 0, dst, 0, g.bytes.size)
        return img
    }
    private fun rgbaToBuffered(r: OcrImage.Rgba8888): BufferedImage {
        val img = BufferedImage(r.width, r.height, BufferedImage.TYPE_INT_ARGB)
        val w = r.width
        for (y in 0 until r.height) {
            val rowStart = y * r.rowStride; var xOff = 0
            for (x in 0 until w) {
                val R = r.bytes[rowStart + xOff].toInt() and 0xFF
                val G = r.bytes[rowStart + xOff + 1].toInt() and 0xFF
                val B = r.bytes[rowStart + xOff + 2].toInt() and 0xFF
                val A = r.bytes[rowStart + xOff + 3].toInt() and 0xFF
                img.setRGB(x, y, (A shl 24) or (R shl 16) or (G shl 8) or B)
                xOff += 4
            }
        }
        return img
    }
}
