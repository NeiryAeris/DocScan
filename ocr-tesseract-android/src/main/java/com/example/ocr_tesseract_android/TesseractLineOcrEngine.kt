package com.example.ocr_tesseract_android

import com.example.domain.types.text.TextNormalize
import com.example.ocr.core.api.OcrEngine
import com.example.ocr.core.api.OcrImage
import com.example.ocr.core.api.OcrPageResult
import com.googlecode.tesseract.android.TessBaseAPI
import kotlin.math.max

/**
 * OCR per text line with PSM_SINGLE_LINE (7). Improves accuracy vs whole page.
 * Expects Gray8; will fall back to full-frame if segmentation finds nothing.
 */
class TesseractLineOcrEngine(
    private val dataPath: String,
    private val defaultLang: String = "vie+eng"
) : OcrEngine {

    override suspend fun recognize(image: OcrImage, lang: String): OcrPageResult {
        val language = if (lang.isBlank()) defaultLang else lang
        val (w, h, bytes, stride) = when (image) {
            is OcrImage.Gray8    -> Quad(image.width, image.height, image.bytes, image.rowStride)
            is OcrImage.Rgba8888 -> return fallbackWholePage(image, language) // prefer Gray8
        }

        val lines = segmentLines(bytes, w, h, stride)
        val api = TessBaseAPI().apply {
            init(dataPath, language)
            runCatching { setVariable("user_defined_dpi", "300") }
            runCatching { setVariable("preserve_interword_spaces", "1") }
            pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_LINE
        }

        val out = StringBuilder()
        if (lines.isEmpty()) {
            api.setImage(bytes, w, h, 1, stride)
            out.append(api.utF8Text ?: "")
        } else {
            for (r in lines) {
                api.setRectangle(r.x, r.y, r.w, r.h)
                val t = api.utF8Text ?: ""
                val clean = TextNormalize.sanitize(t.trim())
                if (clean.isNotEmpty()) out.appendLine(clean)
            }
        }
        api.end()
        return OcrPageResult(1, out.toString().trimEnd())
    }

    private fun fallbackWholePage(img: OcrImage.Rgba8888, lang: String): OcrPageResult {
        val api = TessBaseAPI()
        api.init(dataPath, lang)
        runCatching { api.setVariable("user_defined_dpi", "300") }
        runCatching { api.setVariable("preserve_interword_spaces", "1") }
        api.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
        api.setImage(img.bytes, img.width, img.height, 4, img.rowStride)
        val txt = api.utF8Text ?: ""
        api.end()
        return OcrPageResult(1, TextNormalize.sanitize(txt.trim()))
    }

    data class Rect(val x: Int, val y: Int, val w: Int, val h: Int)
    private data class Quad(val w: Int, val h: Int, val b: ByteArray, val s: Int)

    /** Lightweight horizontal-projection line segmentation on Gray8. */
    private fun segmentLines(bytes: ByteArray, w: Int, h: Int, stride: Int): List<Rect> {
        val proj = IntArray(h)
        var si = 0
        for (y in 0 until h) {
            var sum = 0
            var x = 0
            val row = si
            while (x < w) {
                val v = bytes[row + x].toInt() and 0xFF
                if (v < 200) sum++                 // treat "ink" as dark
                x++
            }
            proj[y] = sum
            si += stride
        }
        val lines = ArrayList<Rect>()
        var inRun = false
        var y0 = 0
        val minInk = max(10, w / 50)          // robust threshold
        for (y in 0 until h) {
            if (!inRun && proj[y] > minInk) { inRun = true; y0 = y }
            if (inRun && proj[y] <= minInk) {
                val y1 = y
                val hh = (y1 - y0).coerceAtLeast(1)
                lines.add(Rect(0, y0, w, hh))
                inRun = false
            }
        }
        if (inRun) {
            lines.add(Rect(0, y0, w, (h - y0).coerceAtLeast(1)))
        }
        return lines
    }
}
