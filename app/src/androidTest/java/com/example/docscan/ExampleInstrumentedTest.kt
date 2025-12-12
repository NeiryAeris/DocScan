package com.example.docscan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.docscan.logic.utils.NodeCloudOcrGateway
import com.example.ocr.core.api.CloudOcrGateway
import com.example.ocr.core.api.OcrImage
import com.example.ocr_remote.RemoteOcrClient
import com.example.ocr_remote.RemoteOcrClientImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
class NodeCloudOcrGatewaySmokeTest {

    // 🔧 CHANGE THIS depending on where Node is running
    // Emulator → PC: use 10.0.2.2
    // Physical phone → PC: use your PC's LAN IP, e.g. "http://192.168.1.10:4000"
    private val baseUrl = "http://10.0.2.2:4000"

    // 🔧 PUT YOUR REAL JWT HERE (same one that works for the :ocr-remote smoke test)
    private val authToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyXzEiLCJlbWFpbCI6ImRlbW9AZXhhbXBsZS5jb20iLCJpYXQiOjE3NjU1MzI0MTEsImV4cCI6MTc2NjEzNzIxMX0.CW9N2rTuFlpXTt3Ga1kXlcu_wrY3BAU78xvRNrHysU0"

    private val remoteClient: RemoteOcrClient = RemoteOcrClientImpl(
        baseUrl = baseUrl,
        authTokenProvider = { authToken }
    )

    private val cloudGateway: CloudOcrGateway = NodeCloudOcrGateway(remoteClient)

    @Test
    fun nodeCloudOcrSmokeTest() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // 1) Load bitmap from res/drawable
        val bitmap: Bitmap = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.sample_ocr
        ) ?: error("Failed to decode sample_ocr drawable")

        // 2) Convert Bitmap -> OcrImage.Rgba8888
        val ocrImage = bitmap.toOcrRgba()

        // 3) Build CloudOcrGateway.Request
        val req = CloudOcrGateway.Request(
            image = ocrImage,
            lang = "vie+eng",
            hints = mapOf(
                "pageId" to "android-test-page-1",
                "docId" to "android-doc-123",
                "pageIndex" to "0",
                "rotation" to "0"
            )
        )

        // 4) Call gateway (will hit Node -> Python)
        val resp = cloudGateway.recognize(req)

        println("===== ANDROID OCR SMOKE TEST RESULT =====")
        println("Text (first 300 chars):")
        println(resp.text.take(300))
        println("Words: ${resp.words.size}")
        println("Elapsed: ${resp.elapsedMs} ms")
        println("========================================")

        // 5) Basic assertion – you can relax this while backend is flaky
        assertTrue("OCR text should not be blank", resp.text.isNotBlank())
    }

    /**
     * Convert ARGB_8888 Bitmap into OcrImage.Rgba8888
     * so NodeCloudOcrGateway can turn it back into JPEG.
     */
    private fun Bitmap.toOcrRgba(): OcrImage.Rgba8888 {
        val bmp = if (config != Bitmap.Config.ARGB_8888) {
            copy(Bitmap.Config.ARGB_8888, false)
        } else {
            this
        }

        val w = bmp.width
        val h = bmp.height
        val bytes = ByteArray(w * h * 4)
        val buffer = ByteBuffer.wrap(bytes)
        bmp.copyPixelsToBuffer(buffer)

        return OcrImage.Rgba8888(
            width = w,
            height = h,
            bytes = bytes,
            rowStride = w * 4,
            premultiplied = bmp.isPremultiplied
        )
    }
}
