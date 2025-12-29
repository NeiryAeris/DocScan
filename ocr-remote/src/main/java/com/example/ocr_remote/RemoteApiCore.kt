package com.example.ocr_remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

internal object RemoteApiCore {
    val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val defaultClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    fun emptyJsonBody(): RequestBody = "{}".toRequestBody(jsonMediaType)

    fun withAuth(
        builder: Request.Builder,
        authTokenProvider: () -> String?
    ): Request.Builder {
        authTokenProvider()?.let { token ->
            builder.addHeader("Authorization", "Bearer $token")
        }
        return builder
    }
}

class RemoteApiException(
    message: String,
    val httpCode: Int? = null,
    val rawBody: String? = null
) : Exception(message)
