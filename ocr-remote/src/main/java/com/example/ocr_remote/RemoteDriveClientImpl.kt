package com.example.ocr_remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class RemoteDriveClientImpl(
    private val baseUrl: String,
    private val authTokenProvider: () -> String?, // Firebase ID token
    private val client: okhttp3.OkHttpClient = RemoteApiCore.defaultClient
) : RemoteDriveClient {

    private val moshi = RemoteApiCore.moshi
    private val statusAdapter = moshi.adapter(RemoteDriveStatusDto::class.java)
    private val oauthAdapter = moshi.adapter(RemoteDriveOauthStartDto::class.java)
    private val initFolderAdapter = moshi.adapter(RemoteDriveInitFolderDto::class.java)
    private val uploadAdapter = moshi.adapter(RemoteDriveUploadDto::class.java)
    private val syncAdapter = moshi.adapter(RemoteDriveSyncDto::class.java)

    private fun requireToken(): String =
        authTokenProvider()?.takeIf { it.isNotBlank() }
            ?: throw RemoteApiException("Missing Firebase Bearer token (required for /api/drive/*)")

    override suspend fun status(): RemoteDriveStatusDto = withContext(Dispatchers.IO) {
        val url = "${baseUrl.trimEnd('/')}/api/drive/status"
        val token = requireToken()

        val req = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw RemoteApiException("HTTP ${resp.code}: $raw", resp.code, raw)
            statusAdapter.fromJson(raw) ?: throw RemoteApiException("Bad JSON: $raw", resp.code, raw)
        }
    }

    override suspend fun oauthStart(): RemoteDriveOauthStartDto = withContext(Dispatchers.IO) {
        val url = "${baseUrl.trimEnd('/')}/api/drive/oauth2/start"
        val token = requireToken()

        val req = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw RemoteApiException("HTTP ${resp.code}: $raw", resp.code, raw)
            oauthAdapter.fromJson(raw) ?: throw RemoteApiException("Bad JSON: $raw", resp.code, raw)
        }
    }

    override suspend fun initFolder(): RemoteDriveInitFolderDto = withContext(Dispatchers.IO) {
        val url = "${baseUrl.trimEnd('/')}/api/drive/folder/init"
        val token = requireToken()

        val req = Request.Builder()
            .url(url)
            .post(RemoteApiCore.emptyJsonBody())
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw RemoteApiException("HTTP ${resp.code}: $raw", resp.code, raw)
            initFolderAdapter.fromJson(raw) ?: throw RemoteApiException("Bad JSON: $raw", resp.code, raw)
        }
    }

    override suspend fun upload(bytes: ByteArray, filename: String, mimeType: String): RemoteDriveUploadDto =
        withContext(Dispatchers.IO) {
            val url = "${baseUrl.trimEnd('/')}/api/drive/upload"
            val token = requireToken()

            val fileBody = bytes.toRequestBody(mimeType.toMediaType())
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    /* name = */ "file", // MUST match upload.single("file") in drive.routes.ts
                    /* filename = */ filename,
                    /* body = */ fileBody
                )
                .build()

            val req = Request.Builder()
                .url(url)
                .post(multipart)
                .addHeader("Authorization", "Bearer $token")
                .build()

            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw RemoteApiException("HTTP ${resp.code}: $raw", resp.code, raw)
                uploadAdapter.fromJson(raw) ?: throw RemoteApiException("Bad JSON: $raw", resp.code, raw)
            }
        }

    override suspend fun sync(): RemoteDriveSyncDto = withContext(Dispatchers.IO) {
        val url = "${baseUrl.trimEnd('/')}/api/drive/sync"
        val token = requireToken()

        val req = Request.Builder()
            .url(url)
            .post(RemoteApiCore.emptyJsonBody())
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw RemoteApiException("HTTP ${resp.code}: $raw", resp.code, raw)
            syncAdapter.fromJson(raw) ?: throw RemoteApiException("Bad JSON: $raw", resp.code, raw)
        }
    }
}
