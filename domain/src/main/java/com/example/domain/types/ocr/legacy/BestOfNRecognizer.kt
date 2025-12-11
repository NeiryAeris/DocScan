package com.example.domain.types.ocr.legacy

import com.example.domain.types.text.TextNormalize
import com.example.domain.types.text.TextScore
import com.example.ocr.core.api.legacy.OcrEngine
import com.example.ocr.core.api.OcrImage
import com.example.ocr.core.api.OcrPageResult

/** Runs OCR over multiple OcrImage candidates and returns the best text by a scorer. */
class BestOfNRecognizer(
    private val engine: OcrEngine,
    private val scorer: (String) -> Double = { TextScore.score(it) }
) {
    suspend fun recognize(candidates: List<OcrImage>, lang: String): OcrPageResult {
        require(candidates.isNotEmpty()) { "candidates empty" }
        var best: OcrPageResult? = null
        var bestScore = Double.NEGATIVE_INFINITY

        for (img in candidates) {
            val res = engine.recognize(img, lang)
            val clean = TextNormalize.sanitize(res.text)
            val score = scorer(clean)
            if (score > bestScore) {
                bestScore = score
                best = res.copy(text = clean)
            }
        }
        return checkNotNull(best)
    }
}