package com.example.domain.interfaces.ocr

import com.example.domain.types.ocr.OcrPolicy
import com.example.domain.types.ocr.OcrText
import com.example.ocr.core.api.OcrImage
import com.example.ocr.core.api.OcrPageResult
import com.example.ocr.core.api.OcrWord

/**
 * Domain-level port for OCR. Implemented by an orchestrator that can route between engines.
 */
interface OcrGateway {
    /**
     * Run OCR on a single page image using the given policy.
     * Returns both the page result (words/conf if provided by engine) and the normalized text bundle.
     */
    suspend fun recognize(
        docId: String,
        pageId: String,
        image: OcrImage,
        policy: OcrPolicy = OcrPolicy()
    ): Result

    data class Result(
        val page: OcrPageResult, // text, words, conf, duration
        val text: OcrText,       // raw/clean/folded for persistence & search
        val words: List<OcrWord> = page.words
    )
}
