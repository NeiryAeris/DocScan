package com.example.ocr_remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RemoteAiPageInDto(
    @Json(name = "page_number") val pageNumber: Int,
    val text: String
)

@JsonClass(generateAdapter = true)
data class RemoteAiUpsertOcrRequestDto(
    @Json(name = "doc_id") val docId: String,
    val title: String? = null,
    val replace: Boolean = true,
    val pages: List<RemoteAiPageInDto>
)

@JsonClass(generateAdapter = true)
data class RemoteAiUpsertOcrResponseDto(
    val indexed: Boolean,
    val chunks: Int,
    val replaced: Boolean
)

@JsonClass(generateAdapter = true)
data class RemoteAiDeleteDocRequestDto(
    @Json(name = "doc_id") val docId: String
)

@JsonClass(generateAdapter = true)
data class RemoteAiDeleteDocResponseDto(
    val deleted: Boolean,
    @Json(name = "doc_id") val docId: String
)

@JsonClass(generateAdapter = true)
data class RemoteAiAskRequestDto(
    val question: String,
    @Json(name = "doc_ids") val docIds: List<String>? = null,
    @Json(name = "top_k") val topK: Int? = null
)

@JsonClass(generateAdapter = true)
data class RemoteAiCitationDto(
    @Json(name = "doc_id") val docId: String?,
    val page: Int?,
    @Json(name = "chunk_index") val chunkIndex: Int?,
    val score: Double?
)

@JsonClass(generateAdapter = true)
data class RemoteAiAskResponseDto(
    // Python may return "answer"
    val answer: String? = null,

    // Gateway currently returns "response"
    val response: String? = null,

    // Gateway returns "error" (string or null)
    val error: String? = null,

    // If later you update gateway to pass citations through, this will start working automatically
    val citations: List<RemoteAiCitationDto> = emptyList(),

    @Json(name = "used_chunks") val usedChunks: Int = 0
) {
    fun answerText(): String = answer ?: response ?: ""
    fun hasError(): Boolean = !error.isNullOrBlank()
}

interface RemoteAiClient {
    suspend fun upsertOcrIndex(body: RemoteAiUpsertOcrRequestDto): RemoteAiUpsertOcrResponseDto
    suspend fun upsertPdfIndex(docId: String, title: String?, replace: Boolean, pdfBytes: ByteArray, filename: String): RemoteAiUpsertOcrResponseDto
    suspend fun deleteDocIndex(docId: String): RemoteAiDeleteDocResponseDto
    suspend fun askChat(question: String, docIds: List<String>? = null, topK: Int? = null): RemoteAiAskResponseDto
}
