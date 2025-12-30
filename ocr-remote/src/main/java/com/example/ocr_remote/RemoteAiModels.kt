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
    val answer: String,
    val citations: List<RemoteAiCitationDto> = emptyList(),
    @Json(name = "used_chunks") val usedChunks: Int = 0
)

interface RemoteAiClient {
    suspend fun upsertOcrIndex(body: RemoteAiUpsertOcrRequestDto): RemoteAiUpsertOcrResponseDto
    suspend fun deleteDocIndex(docId: String): RemoteAiDeleteDocResponseDto
    suspend fun askChat(question: String, docIds: List<String>? = null, topK: Int? = null): RemoteAiAskResponseDto
}
