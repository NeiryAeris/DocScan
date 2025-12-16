package com.example.docscan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
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

    private val TAG = "NodeCloudOcrGatewayTest"

    // Your Cloudflare / gateway URL
    private val baseUrl = "http://gateway.neirylittlebox.com"

    // Your JWT token
    private val authToken =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyXzEiLCJlbWFpbCI6ImRlbW9AZXhhbXBsZS5jb20iLCJpYXQiOjE3NjU4Nzk1ODgsImV4cCI6MTc2NjQ4NDM4OH0.pIsaPrtYnUDGekp-29HdvcxVBul11Whm-FWNMEZkWxE"

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

        // 4) Call gateway (Node -> Python -> Tesseract)
        val resp = cloudGateway.recognize(req)

        Log.i(TAG, "===== ANDROID OCR SMOKE TEST RESULT =====")
        Log.i(TAG, "Text (first 300 chars): ${resp.text.take(300)}")
        Log.i(TAG, "Words: ${resp.words.size}")
        Log.i(TAG, "Elapsed: ${resp.elapsedMs} ms")
        Log.i(TAG, "========================================")

        // If it ever fails, show a bit of text in the assertion message
        assertTrue(
            "OCR text should not be blank. Got: '${resp.text.take(120)}'",
            resp.text.isNotBlank()
        )
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
