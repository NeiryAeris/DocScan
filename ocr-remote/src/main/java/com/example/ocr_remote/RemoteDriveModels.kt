package com.example.ocr_remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RemoteDriveStatusDto(
    val linked: Boolean,
    @Json(name = "folderId") val folderId: String? = null
)

@JsonClass(generateAdapter = true)
data class RemoteDriveOauthStartDto(
    val url: String
)

@JsonClass(generateAdapter = true)
data class RemoteDriveInitFolderDto(
    @Json(name = "folderId") val folderId: String
)

@JsonClass(generateAdapter = true)
data class RemoteDriveUploadDto(
    val id: String? = null,
    val name: String? = null,
    val mimeType: String? = null,
    val modifiedTime: String? = null,
    val md5Checksum: String? = null
)

@JsonClass(generateAdapter = true)
data class RemoteDriveSyncCountsDto(
    val total: Int,
    val indexed: Int,
    val skipped: Int,
    val errored: Int
)

@JsonClass(generateAdapter = true)
data class RemoteDriveSyncResultDto(
    val fileId: String? = null,
    val name: String? = null,
    val status: String? = null,
    val pages: Int? = null,
    val reason: String? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class RemoteDriveSyncDto(
    @Json(name = "folderId") val folderId: String,
    val counts: RemoteDriveSyncCountsDto,
    val results: List<RemoteDriveSyncResultDto> = emptyList()
)

interface RemoteDriveClient {
    suspend fun status(): RemoteDriveStatusDto
    suspend fun oauthStart(): RemoteDriveOauthStartDto
    suspend fun initFolder(): RemoteDriveInitFolderDto
    suspend fun upload(bytes: ByteArray, filename: String, mimeType: String): RemoteDriveUploadDto
    suspend fun sync(): RemoteDriveSyncDto
}
