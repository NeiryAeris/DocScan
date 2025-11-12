package com.example.ocr.core.api

interface OcrEngine {
    suspend fun recognize(image: OcrImage, lang: String = "vie+eng"): OcrPageResult
}
