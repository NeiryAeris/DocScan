package com.example.ocr_remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RemoteHandwritingResponseDto(
    val jobId: String,
    val status: String,
    @Json(name = "cleanImageUrl") val cleanImageUrl: String? = null,
    val error: String? = null
)

sealed class RemoteHandwritingResult {
    data class ImageBytes(
        val bytes: ByteArray,
        val mimeType: String
    ) : RemoteHandwritingResult()

    data class Url(
        val url: String
    ) : RemoteHandwritingResult()

    data class Error(
        val message: String,
        val httpCode: Int? = null,
        val rawBody: String? = null
    ) : RemoteHandwritingResult()
}

interface RemoteHandwritingClient {
    suspend fun removeHandwriting(
        pageId: String,
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg",
        strength: String = "medium" // low|medium|high
    ): RemoteHandwritingResult
}