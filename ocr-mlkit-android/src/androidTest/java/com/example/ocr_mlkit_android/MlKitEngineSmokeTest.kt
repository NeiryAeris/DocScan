package com.example.ocr_mlkit_android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.domain.types.text.TextNormalize
import com.example.ocr.core.api.OcrImage
import com.example.ocr.mlkit.MlKitOcrEngine
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertTrue
import java.io.File

@RunWith(AndroidJUnit4::class)
class MlKitEngineSmokeTest {

    @Test
    fun ocr_on_processed_image() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext

        // 1) load processed image from androidTest assets
        val bm = ctx.assets.open("ocr_samples/enhanced.jpg").use { BitmapFactory.decodeStream(it)!! }

        // 2) ML Kit works fine from RGBA or Gray8; we’ll use RGBA here
        val img = bitmapToRgba(bm)

        // 3) run OCR
        val engine = MlKitOcrEngine()
        val res = engine.recognize(img, "vie+eng")
        val clean = TextNormalize.sanitize(res.text)

        // 4) dump & assert
        val outDir = File(ctx.filesDir, "test-output").apply { mkdirs() }
        File(outDir, "mlkit_ocr.txt").writeText(clean)
        Log.i("MlKitSmoke", "len=${clean.length}\n${clean.take(200)}")
        assertTrue("OCR output should not be blank", clean.isNotBlank())
    }

    private fun bitmapToRgba(bm: Bitmap): OcrImage.Rgba8888 {
        val src = if (bm.config != Bitmap.Config.ARGB_8888) bm.copy(Bitmap.Config.ARGB_8888, false) else bm
        val w = src.width; val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val bytes = ByteArray(w * h * 4)
        var i = 0
        for (p in pixels) {
            val a = (p ushr 24) and 0xFF
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = (p) and 0xFF
            bytes[i++] = r.toByte(); bytes[i++] = g.toByte(); bytes[i++] = b.toByte(); bytes[i++] = a.toByte()
        }
        return OcrImage.Rgba8888(w, h, bytes, w * 4, premultiplied = false)
    }
}
