package com.example.ocr_remote

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class RemoteAiClientImpl(
    private val baseUrl: String,
    private val authTokenProvider: () -> String?, // Firebase ID token in production
    private val client: okhttp3.OkHttpClient = RemoteApiCore.defaultClient
) : RemoteAiClient {

    private val moshi = RemoteApiCore.moshi
    private val upsertReqAdapter = moshi.adapter(RemoteAiUpsertOcrRequestDto::class.java)
    private val upsertRespAdapter = moshi.adapter(RemoteAiUpsertOcrResponseDto::class.java)

    private val deleteReqAdapter = moshi.adapter(RemoteAiDeleteDocRequestDto::class.java)
    private val deleteRespAdapter = moshi.adapter(RemoteAiDeleteDocResponseDto::class.java)

    private val askReqAdapter = moshi.adapter(RemoteAiAskRequestDto::class.java)
    private val askRespAdapter = moshi.adapter(RemoteAiAskResponseDto::class.java)

    private fun requireToken(): String =
        authTokenProvider()?.takeIf { it.isNotBlank() }
            ?: throw RemoteApiException("Missing Firebase Bearer token (required for /api/ai/*)")

    override suspend fun upsertOcrIndex(body: RemoteAiUpsertOcrRequestDto): RemoteAiUpsertOcrResponseDto =
        withContext(Dispatchers.IO) {
            val url = "${baseUrl.trimEnd('/')}/api/ai/index/upsert-ocr"
            val json = upsertReqAdapter.toJson(body).toRequestBody(RemoteApiCore.jsonMediaType)

            val token = requireToken()
            val req = Request.Builder()
                .url(url)
                .post(json)
                .addHeader("Authorization", "Bearer $token")
                .build()

            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw RemoteApiException("HTTP ${resp.code}: $raw", resp.code, raw)
                upsertRespAdapter.fromJson(raw) ?: throw RemoteApiException("Bad JSON: $raw", resp.code, raw)
            }
        }

    override suspend fun upsertPdfIndex(
        docId: String,
        title: String?,
        replace: Boolean,
        pdfBytes: ByteArray,
        filename: String
    ): RemoteAiUpsertOcrResponseDto = withContext(Dispatchers.IO) {
        val url = "${baseUrl.trimEnd('/')}/api/ai/index/upsert-pdf"
        val token = requireToken()

        val pdfBody = pdfBytes.toRequestBody("application/pdf".toMediaType())

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("doc_id", docId)
            .addFormDataPart("title", title ?: filename)
            .addFormDataPart("replace", replace.toString())
            .addFormDataPart("file", filename, pdfBody)
            .build()

        val req = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw RemoteApiException("HTTP ${resp.code}: $raw", resp.code, raw)
            upsertRespAdapter.fromJson(raw) ?: throw RemoteApiException("Bad JSON: $raw", resp.code, raw)
        }
    }


    override suspend fun deleteDocIndex(docId: String): RemoteAiDeleteDocResponseDto =
        withContext(Dispatchers.IO) {
            val url = "${baseUrl.trimEnd('/')}/api/ai/index/delete"
            val json = deleteReqAdapter.toJson(RemoteAiDeleteDocRequestDto(docId))
                .toRequestBody(RemoteApiCore.jsonMediaType)

            val token = requireToken()
            val req = Request.Builder()
                .url(url)
                .post(json)
                .addHeader("Authorization", "Bearer $token")
                .build()

            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw RemoteApiException("HTTP ${resp.code}: $raw", resp.code, raw)
                deleteRespAdapter.fromJson(raw) ?: throw RemoteApiException("Bad JSON: $raw", resp.code, raw)
            }
        }

    override suspend fun askChat(question: String, docIds: List<String>?, topK: Int?): RemoteAiAskResponseDto =
        withContext(Dispatchers.IO) {
            val url = "${baseUrl.trimEnd('/')}/api/ai/chat/ask"
            val json = askReqAdapter.toJson(RemoteAiAskRequestDto(question, docIds, topK))
                .toRequestBody(RemoteApiCore.jsonMediaType)

            val token = requireToken()
            val req = Request.Builder()
                .url(url)
                .post(json)
                .addHeader("Authorization", "Bearer $token")
                .build()

            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw RemoteApiException("HTTP ${resp.code}: $raw", resp.code, raw)
                askRespAdapter.fromJson(raw) ?: throw RemoteApiException("Bad JSON: $raw", resp.code, raw)
            }
        }
}
