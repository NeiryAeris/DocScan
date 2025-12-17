package com.example.ocr_remote

import com.squareup.moshi.JsonClass

/**
 * Metadata returned by the backend. Shape can be adapted
 * to whatever your Node/Python stack actually returns.
 */
@JsonClass(generateAdapter = true)
data class RemoteOcrMetaDto(
    val userId: String? = null,
    val pageId: String? = null,
    val docId: String? = null,
    val pageIndex: Int? = null,
    val rotation: Int? = null,
    val durationMs: Long? = null
)

/**
 * One word with bounding box and confidence.
 * bbox = [x1, y1, x2, y2]
 */
@JsonClass(generateAdapter = true)
data class RemoteOcrWordDto(
    val text: String,
    val bbox: List<Int> = emptyList(),
    val conf: Float? = null
)

/**
 * Raw JSON response from your gateway/Python OCR.
 */
@JsonClass(generateAdapter = true)
data class RemoteOcrResponseDto(
    val text: String,
    val words: List<RemoteOcrWordDto> = emptyList(),
    val meta: RemoteOcrMetaDto? = null
)

/**
 * Public model you use in the app.
 * For now it's the same as the DTO, but you can decouple later.
 */
data class RemoteOcrResponse(
    val text: String,
    val words: List<RemoteOcrWordDto> = emptyList(),
    val meta: RemoteOcrMetaDto? = null
)

/**
 * Remote OCR client: send bytes → get OCR result.
 */
interface RemoteOcrClient {
    suspend fun ocrPage(
        pageId: String,
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg",
        docId: String? = null,
        pageIndex: Int? = null,
        rotation: Int? = null
    ): RemoteOcrResponse
}
