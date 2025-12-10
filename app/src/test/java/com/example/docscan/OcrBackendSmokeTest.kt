package com.example.docscan

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

class OcrBackendSmokeTest {

    // 🔧 CHANGE THIS to your NodeJS gateway endpoint
    private val gatewayUrl = "http://localhost:4000/api/ocr/page"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Test
    fun sendImageToBackend_smokeTest() {
        // 1) Copy test resource to a temp file
        val imageFile = copyResourceToTempFile("sample_ocr.jpg")

        // 2) Build multipart/form-data request
        val imageBody = imageFile
            .asRequestBody("image/jpeg".toMediaType())   // or image/png

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image",           // 🔧 must match field name expected by gateway
                imageFile.name,
                imageBody
            )
            .addFormDataPart("lang", "vi+en") // optional, adjust if needed
            .addFormDataPart("mode", "inline")  // or "ingest"
            .build()

        val request = Request.Builder()
            .url(gatewayUrl)
            .post(multipart)
            .build()

        // 3) Execute HTTP call
        client.newCall(request).execute().use { resp ->
            println("HTTP status: ${resp.code}")
            val bodyString = resp.body?.string().orEmpty()
            println("Response body:\n$bodyString")

            // super simple smoke: just check it's 2xx and non-empty
            assert(resp.isSuccessful) { "Not successful: ${resp.code}" }
            assert(bodyString.isNotBlank()) { "Empty response body" }
        }
    }

    /**
     * Copy a file from src/test/resources into a temp file
     * so OkHttp can stream it as a normal File.
     */
    private fun copyResourceToTempFile(resourceName: String): File {
        val url = checkNotNull(javaClass.classLoader.getResource(resourceName)) {
            "Resource not found: $resourceName (put it in app/src/test/resources)"
        }

        val tempFile = Files
            .createTempFile("ocr-smoke-", "-$resourceName")
            .toFile()
            .also { it.deleteOnExit() }

        url.openStream().use { input ->
            Files.copy(input, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        return tempFile
    }
}