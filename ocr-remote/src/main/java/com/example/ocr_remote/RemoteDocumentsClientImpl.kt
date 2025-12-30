package com.example.ocr_remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class RemoteDocumentsClientImpl(
    private val baseUrl: String,
    private val authTokenProvider: () -> String?, // Firebase ID token
    private val client: okhttp3.OkHttpClient = RemoteApiCore.defaultClient
) : RemoteDocumentsClient {

    private val moshi = RemoteApiCore.moshi
    private val createReqAdapter = moshi.adapter(RemoteCreateDocumentRequestDto::class.java)
    private val docAdapter = moshi.adapter(RemoteDocumentResponseDto::class.java)
    private val docsAdapter = moshi.adapter(RemoteDocumentsListResponseDto::class.java)

    private fun requireToken(): String =
        authTokenProvider()?.takeIf { it.isNotBlank() }
            ?: throw RemoteApiException("Missing Firebase Bearer token (required for /api/documents/*)")

    override suspend fun createDocument(title: String): RemoteDocumentDto = withContext(Dispatchers.IO) {
        val url = "${baseUrl.trimEnd('/')}/api/documents"
        val bodyJson = createReqAdapter.toJson(RemoteCreateDocumentRequestDto(title))
        val reqBody = bodyJson.toRequestBody(RemoteApiCore.jsonMediaType)

        val req = Request.Builder()
            .url(url)
            .post(reqBody)
            .addHeader("Authorization", "Bearer ${requireToken()}")
            .build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw RemoteApiException("HTTP ${resp.code}: $raw", resp.code, raw)
            val parsed = docAdapter.fromJson(raw) ?: throw RemoteApiException("Bad JSON: $raw", resp.code, raw)
            parsed.document
        }
    }

    override suspend fun listDocuments(): List<RemoteDocumentDto> = withContext(Dispatchers.IO) {
        val url = "${baseUrl.trimEnd('/')}/api/documents"

        val req = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer ${requireToken()}")
            .build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw RemoteApiException("HTTP ${resp.code}: $raw", resp.code, raw)
            val parsed = docsAdapter.fromJson(raw) ?: throw RemoteApiException("Bad JSON: $raw", resp.code, raw)
            parsed.documents
        }
    }

    override suspend fun getDocumentById(id: String): RemoteDocumentDto = withContext(Dispatchers.IO) {
        val url = "${baseUrl.trimEnd('/')}/api/documents/$id"

        val req = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer ${requireToken()}")
            .build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw RemoteApiException("HTTP ${resp.code}: $raw", resp.code, raw)
            val parsed = docAdapter.fromJson(raw) ?: throw RemoteApiException("Bad JSON: $raw", resp.code, raw)
            parsed.document
        }
    }
}
