package com.example.ocr.core.api

interface OcrEngine {
    /** @param lang examples: "vie", "vie+eng", "eng", "chi_sim" */
    suspend fun recognize(image: OcrImage, lang: String = "vie+eng"): OcrPageResult
}
