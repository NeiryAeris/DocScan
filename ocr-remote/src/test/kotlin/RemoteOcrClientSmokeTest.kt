package com.example.docscan.ocr_remote

import com.example.ocr_remote.RemoteOcrClient
import com.example.ocr_remote.RemoteOcrClientImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream

class RemoteOcrClientSmokeTest {

    // 👉 Deployed Node gateway base URL
    // Try https first; if you get SSL issues, change to "http://gateway.neirylittlebox.com"
    private val baseUrl = "https://gateway.neirylittlebox.com"

    // 👉 JWT that your gateway accepts
    private val authToken =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyXzEiLCJlbWFpbCI6ImRlbW9AZXhhbXBsZS5jb20iLCJpYXQiOjE3NjU4OTE5MTEsImV4cCI6MTc2NjQ5NjcxMX0.5UL4rGDR3TepmMBsi0pS97MHfhutpWcjGn8v4l93Q84"

    private val client: RemoteOcrClient = RemoteOcrClientImpl(
        baseUrl = baseUrl,
        authTokenProvider = { authToken }
    )

    @Test
    fun remoteOcrSmokeTest() = runBlocking {
        // 1) Load sample image bytes from test resources
        val imageBytes = loadResourceBytes("sample_ocr.jpg")

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

        assertTrue("OCR text should not be blank", response.text.isNotBlank())
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
