package com.example.ocr_remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class RemoteChatClientImpl(
    private val baseUrl: String,
    private val authTokenProvider: suspend () -> String?
) : RemoteChatClient {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val requestAdapter = moshi.adapter(RemoteChatRequestDto::class.java)
    private val responseAdapter = moshi.adapter(RemoteChatResponseDto::class.java)

    override suspend fun ask(prompt: String, history: List<ChatMessageDto>): RemoteChatResult = withContext(Dispatchers.IO) {
        val token = authTokenProvider()
        if (token == null) {
            return@withContext RemoteChatResult.Error("Firebase token is missing.")
        }

        val requestDto = RemoteChatRequestDto(prompt = prompt, history = history)
        val jsonBody = requestAdapter.toJson(requestDto)

        val url = "${baseUrl.trimEnd('/')}/api/ai/chat/ask"

        val req = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $token")
            .build()

        client.newCall(req).execute().use { resp ->
            val bodyString = resp.body?.string()
            if (!resp.isSuccessful) {
                return@withContext RemoteChatResult.Error("HTTP ${resp.code}: ${bodyString ?: resp.message}")
            }

            if (bodyString == null) {
                return@withContext RemoteChatResult.Error("Empty response body.")
            }

            val responseDto = responseAdapter.fromJson(bodyString)
            if (responseDto?.response != null) {
                RemoteChatResult.Success(responseDto.response)
            } else {
                RemoteChatResult.Error(responseDto?.error ?: "Unknown error")
            }
        }
    }
}
