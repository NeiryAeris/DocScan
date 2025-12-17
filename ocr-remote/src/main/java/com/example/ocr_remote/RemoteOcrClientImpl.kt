package com.example.ocr_remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class RemoteOcrClientImpl(
    private val baseUrl: String,                 // e.g. "http://10.0.2.2:4000"
    private val authTokenProvider: () -> String?,// how to get current JWT
    private val client: OkHttpClient = defaultClient
) : RemoteOcrClient {

    companion object {
        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        private val moshi: Moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        private val responseAdapter =
            moshi.adapter(RemoteOcrResponseDto::class.java)
    }

    override suspend fun ocrPage(
        pageId: String,
        imageBytes: ByteArray,
        mimeType: String,
        docId: String?,
        pageIndex: Int?,
        rotation: Int?
    ): RemoteOcrResponse = withContext(Dispatchers.IO) {

        val mediaType = mimeType.toMediaType()
        val imageBody = imageBytes.toRequestBody(mediaType)

        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                /* name = */ "pageImage",           // MUST match upload.single("pageImage")
                /* filename = */ "page-$pageId.jpg",
                /* body = */ imageBody
            )

        docId?.let { multipartBuilder.addFormDataPart("docId", it) }
        pageIndex?.let { multipartBuilder.addFormDataPart("pageIndex", it.toString()) }
        rotation?.let { multipartBuilder.addFormDataPart("rotation", it.toString()) }

        val multipart = multipartBuilder.build()

        val url = "$baseUrl/api/pages/$pageId/ocr"

        val requestBuilder = Request.Builder()
            .url(url)
            .post(multipart)

        authTokenProvider()?.let { token ->
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        val request = requestBuilder.build()

        client.newCall(request).execute().use { resp ->
            val bodyString = resp.body?.string().orEmpty()

            if (!resp.isSuccessful) {
                throw RemoteOcrException("HTTP ${resp.code}: $bodyString")
            }

            val dto = responseAdapter.fromJson(bodyString)
                ?: throw RemoteOcrException("Failed to parse OCR response: $bodyString")

            RemoteOcrResponse(
                text = dto.text,
                words = dto.words,
                meta = dto.meta
            )
        }
    }
}

class RemoteOcrException(message: String) : Exception(message)
