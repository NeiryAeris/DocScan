package com.example.ocr.core.api.legacy

import com.example.ocr.core.api.OcrImage
import com.example.ocr.core.api.OcrPageResult

interface OcrEngine {
    /** @param lang examples: "vie", "vie+eng", "eng", "chi_sim" */
    suspend fun recognize(image: OcrImage, lang: String = "vie+eng"): OcrPageResult
}