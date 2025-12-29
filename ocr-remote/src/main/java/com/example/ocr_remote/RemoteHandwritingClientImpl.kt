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
import java.util.Base64

class RemoteHandwritingClientImpl(
    private val baseUrl: String, //"https://gateway.neirylittlebox.com"
) : RemoteHandwritingClient {

    private val client: OkHttpClient = OkHttpClient.Builder()
        // handwriting can be slow
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val dtoAdapter = moshi.adapter(RemoteHandwritingResponseDto::class.java)

    override suspend fun removeHandwriting(
        pageId: String,
        imageBytes: ByteArray,
        mimeType: String,
        strength: String
    ): RemoteHandwritingResult = withContext(Dispatchers.IO) {

        val safeStrength = strength.lowercase().trim().let {
            when (it) {
                "low", "medium", "high" -> it
                else -> "medium"
            }
        }

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "pageImage",
                "page_$pageId.jpg",
                imageBytes.toRequestBody(mimeType.toMediaType())
            )
            .addFormDataPart("strength", safeStrength)
            .build()

        val url = "${baseUrl.trimEnd('/')}/api/pages/$pageId/remove-handwriting"

        val req = Request.Builder()
            .url(url)
            .post(multipart)
            .build()

        client.newCall(req).execute().use { resp ->
            val bodyBytes = resp.body?.bytes() ?: ByteArray(0)

            if (!resp.isSuccessful) {
                return@withContext RemoteHandwritingResult.Error(
                    message = "HTTP ${resp.code} ${resp.message}",
                    httpCode = resp.code,
                    rawBody = bodyBytes.toString(Charsets.UTF_8).takeIf { it.isNotBlank() }
                )
            }

            val contentType = (resp.header("Content-Type") ?: "").lowercase()

            // Case A: Node returned raw image bytes (most common when cleanImageUrl is data:image/..)
            if (contentType.startsWith("image/")) {
                val mime = contentType.substringBefore(";").ifBlank { "image/png" }
                return@withContext RemoteHandwritingResult.ImageBytes(bytes = bodyBytes, mimeType = mime)
            }

            // Case B: Node returned JSON (fallback)
            val bodyString = bodyBytes.toString(Charsets.UTF_8)
            val dto = dtoAdapter.fromJson(bodyString)
                ?: return@withContext RemoteHandwritingResult.Error(
                    message = "Failed to parse JSON response",
                    rawBody = bodyString
                )

            if (dto.status != "success") {
                return@withContext RemoteHandwritingResult.Error(
                    message = dto.error ?: "handwriting removal failed",
                    rawBody = bodyString
                )
            }

            val clean = dto.cleanImageUrl
                ?: return@withContext RemoteHandwritingResult.Error(
                    message = "Missing cleanImageUrl in success response",
                    rawBody = bodyString
                )

            // If backend returns data URL, decode here
            if (clean.startsWith("data:image/")) {
                val decoded = decodeDataUrl(clean)
                return@withContext RemoteHandwritingResult.ImageBytes(
                    bytes = decoded.bytes,
                    mimeType = decoded.mimeType
                )
            }

            // Otherwise it's a normal URL
            return@withContext RemoteHandwritingResult.Url(clean)
        }
    }

    private data class DecodedDataUrl(val mimeType: String, val bytes: ByteArray)

    private fun decodeDataUrl(dataUrl: String): DecodedDataUrl {
        // format: data:image/png;base64,AAAA...
        val comma = dataUrl.indexOf(',')
        require(comma > 0) { "Invalid data URL" }

        val header = dataUrl.substring(0, comma)
        val b64 = dataUrl.substring(comma + 1)

        val mime = header.removePrefix("data:")
            .substringBefore(";")
            .ifBlank { "image/png" }

        val bytes = Base64.getDecoder().decode(b64)
        return DecodedDataUrl(mimeType = mime, bytes = bytes)
    }
}