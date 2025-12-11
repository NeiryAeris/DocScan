package com.example.docscan.ocr_remote

import com.example.ocr_remote.RemoteOcrClientImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream

class RemoteOcrClientSmokeTest {

    // 🔧 CHANGE THIS to your gateway base URL
    private val baseUrl = "http://localhost:4000"

    // 🔧 PUT YOUR REAL JWT TOKEN HERE
    private val authToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyXzEiLCJlbWFpbCI6ImRlbW9AZXhhbXBsZS5jb20iLCJpYXQiOjE3NjU0OTI5ODEsImV4cCI6MTc2NjA5Nzc4MX0.AngK3zIZ2BZu263ACIN9fo8njRzWsgZhXBvqWMNPqk0"

    private val client = RemoteOcrClientImpl(
        baseUrl = baseUrl,
        authTokenProvider = { authToken }
    )

    @Test
    fun remoteOcrSmokeTest() {
        // JUnit expects a void/Unit method; we just call runBlocking *inside* it
        runBlocking {
            // 1) Load sample image bytes from test resources
            val imageBytes = loadResourceBytes("sample_ocr.png")

            // 2) Call OCR for a test page
            val pageId = "test-page-1"
            val docId = "doc-123"
            val pageIndex = 0
            val rotation = 0

            val response = client.ocrPage(
                pageId = pageId,
                imageBytes = imageBytes,
                mimeType = "image/jpeg",
                docId = docId,
                pageIndex = pageIndex,
                rotation = rotation
            )

            println("OCR text (first 300 chars):")
            println(response.text.take(300))

            println("Meta:")
            println(response.meta)

            // JUnit-style assert to avoid generic confusion
            assertTrue("OCR text should not be blank", response.text.isNotBlank())
        }
    }

    private fun loadResourceBytes(name: String): ByteArray {
        val stream: InputStream = checkNotNull(
            javaClass.classLoader.getResourceAsStream(name)
        ) {
            "Resource not found: $name (put it in ocr-remote/src/test/resources)"
        }

        return stream.use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(4096)
            while (true) {
                val read = input.read(chunk)
                if (read == -1) break
                buffer.write(chunk, 0, read)
            }
            buffer.toByteArray()
        }
    }
}
