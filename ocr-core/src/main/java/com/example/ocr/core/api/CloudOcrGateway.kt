package com.example.ocr.core.api

interface CloudOcrGateway {
    data class Request(
        val image: OcrImage,
        val lang: String,           // "vie+eng" etc.
        val hints: Map<String, String> = emptyMap()
    )
    data class Response(
        val text: String,
        val words: List<OcrWord> = emptyList(),
        val elapsedMs: Long? = null
    )
    suspend fun recognize(req: Request): Response
}
