package com.example.ocr_remote

import android.graphics.Bitmap
import com.example.ocr.core.api.CloudOcrGateway
import com.example.ocr.core.api.OcrImage
import com.example.ocr.core.api.OcrWord
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

class NodeCloudOcrGateway(
    private val baseUrl: String,                 // e.g. "http://10.0.2.2:4000"
    private val authTokenProvider: () -> String?,// how to get current JWT
    private val client: OkHttpClient = defaultClient
) : CloudOcrGateway {

    companion object {
        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        private val moshi: Moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        private val responseAdapter = moshi.adapter(NodeOcrResponse::class.java)

        private val jpegMediaType = "image/jpeg".toMediaType()
    }

    override suspend fun recognize(req: CloudOcrGateway.Request): CloudOcrGateway.Response =
        withContext(Dispatchers.IO) {

            val pageId = req.hints["pageId"]
                ?: error("NodeCloudOcrGateway: hints[\"pageId\"] is required")

            val docId = req.hints["docId"]
            val pageIndex = req.hints["pageIndex"]
            val rotation = req.hints["rotation"]

            val imageJpeg = encodeToJpeg(req.image)

            val multipartBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "pageImage",
                    "page-$pageId.jpg",
                    imageJpeg.toRequestBody(jpegMediaType)
                )

            docId?.let { multipartBuilder.addFormDataPart("docId", it) }
            pageIndex?.let { multipartBuilder.addFormDataPart("pageIndex", it) }
            rotation?.let { multipartBuilder.addFormDataPart("rotation", it) }

            val multipart = multipartBuilder.build()

            val url = "$baseUrl/api/pages/$pageId/ocr"

            val requestBuilder = Request.Builder()
                .url(url)
                .post(multipart)

            authTokenProvider()?.let { token ->
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }

            val httpRequest = requestBuilder.build()

            client.newCall(httpRequest).execute().use { resp ->
                val bodyString = resp.body?.string().orEmpty()

                if (!resp.isSuccessful) {
                    throw RuntimeException("Cloud OCR error: HTTP ${resp.code} – $bodyString")
                }

                val nodeResp = responseAdapter.fromJson(bodyString)
                    ?: error("Failed to parse Node OCR response")

                // Map Node response -> CloudOcrGateway.Response
                CloudOcrGateway.Response(
                    text = nodeResp.text,
                    words = nodeResp.words.map {
                        OcrWord(
                            text = it.text,
                            bbox = intArrayOf(
                                it.bbox[0], it.bbox[1],
                                it.bbox[2], it.bbox[3]
                            ),
                            conf = it.conf
                        )
                    },
                    elapsedMs = nodeResp.meta?.durationMs
                )
            }
        }

    /**
     * Encode OcrImage to JPEG bytes for upload.
     * This is simple and may be optimized later.
     */
    private fun encodeToJpeg(image: OcrImage): ByteArray {
        val bitmap: Bitmap = when (image) {
            is OcrImage.Gray8 -> gray8ToBitmap(image)
            is OcrImage.Rgba8888 -> rgbaToBitmap(image)
        }

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return out.toByteArray()
    }

    private fun gray8ToBitmap(img: OcrImage.Gray8): Bitmap {
        // Simple way: expand gray to ARGB8888
        val bmp = Bitmap.createBitmap(img.width, img.height, Bitmap.Config.ARGB_8888)
        val argb = IntArray(img.width * img.height)

        var idx = 0
        var srcIdx = 0
        for (y in 0 until img.height) {
            for (x in 0 until img.width) {
                val g = img.bytes[srcIdx].toInt() and 0xFF
                argb[idx++] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
                srcIdx++
            }
            // if rowStride > width, you’d skip padding here
        }
        bmp.setPixels(argb, 0, img.width, 0, 0, img.width, img.height)
        return bmp
    }

    private fun rgbaToBitmap(img: OcrImage.Rgba8888): Bitmap {
        val bmp = Bitmap.createBitmap(img.width, img.height, Bitmap.Config.ARGB_8888)
        val buffer = ByteBuffer.wrap(img.bytes)
        bmp.copyPixelsFromBuffer(buffer)
        return bmp
    }
}