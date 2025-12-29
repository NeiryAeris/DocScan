package com.example.ocr_remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RemoteDocumentDto(
    val id: String,
    @Json(name = "userId") val userId: String,
    val title: String,
    @Json(name = "createdAt") val createdAt: String
)

@JsonClass(generateAdapter = true)
data class RemoteDocumentsListResponseDto(
    val documents: List<RemoteDocumentDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RemoteDocumentResponseDto(
    val document: RemoteDocumentDto
)

@JsonClass(generateAdapter = true)
data class RemoteCreateDocumentRequestDto(
    val title: String
)

interface RemoteDocumentsClient {
    suspend fun createDocument(title: String): RemoteDocumentDto
    suspend fun listDocuments(): List<RemoteDocumentDto>
    suspend fun getDocumentById(id: String): RemoteDocumentDto
}
