package com.example.domain.types.ocr

import com.example.domain.types.text.TextNormalize
import com.example.ocr.core.api.*

/**
 * Concatenates OCR of pre-segmented line images (each already an OcrImage Gray8/RGBA).
 * Segmentation & cropping happen outside (e.g., imaging module).
 */
class PerLineRecognizer(private val engine: OcrEngine) {
    suspend fun recognizeLines( lines: List<OcrImage>, langForLine: (Int) -> String) : OcrPageResult {
        val out = StringBuilder()
        for ((i, img) in lines.withIndex()){
            val lang = langForLine(i)
            val t = engine.recognize(img, lang).text
            val clean = TextNormalize.sanitize(t.trim())
            if (clean.isNotEmpty()) out.appendLine(clean)
        }
        return OcrPageResult(pageNo = 1, text = out.toString().trimEnd())
    }
}