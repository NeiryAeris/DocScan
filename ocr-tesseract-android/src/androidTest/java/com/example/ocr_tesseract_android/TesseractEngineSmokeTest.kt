package com.example.ocr_tesseract_android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.domain.types.text.TextNormalize
import com.example.ocr.core.api.OcrImage
import com.example.ocr.tesseract.TesseractOcrEngine
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertTrue
import java.io.File

@RunWith(AndroidJUnit4::class)
class TesseractEngineSmokeTest {

    @Test
    fun ocr_on_processed_image() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext

        // 1) tessdata from assets -> app files
        val dataPath = TessDataInstaller.ensure(ctx, listOf("eng","vie"))

        // 2) load processed image from androidTest assets
        val bm = ctx.assets.open("ocr_samples/enhanced.jpg").use { BitmapFactory.decodeStream(it)!! }

        // 3) convert to Gray8 (better for Tesseract than RGBA)
        val img = bitmapToGray8(bm)

        // 4) run OCR
        val engine = TesseractOcrEngine(dataPath, defaultLang = "vie+eng")
        val res = engine.recognize(img, "vie+eng")
        val clean = TextNormalize.sanitize(res.text)

        // 5) dump & assert
        val outDir = File(ctx.filesDir, "test-output").apply { mkdirs() }
        File(outDir, "tesseract_ocr.txt").writeText(clean)
        Log.i("TessSmoke", "len=${clean.length}\n${clean.take(200)}")
        assertTrue("OCR output should not be blank", clean.isNotBlank())
    }

    private fun bitmapToGray8(bm: Bitmap): OcrImage.Gray8 {
        // Ensure ARGB_8888 for predictable pixel format
        val src = if (bm.config != Bitmap.Config.ARGB_8888) bm.copy(Bitmap.Config.ARGB_8888, false) else bm
        val w = src.width; val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val bytes = ByteArray(w * h)
        var i = 0
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = (p) and 0xFF
            // luminance (BT.601)
            val y = (0.299 * r + 0.587 * g + 0.114 * b).toInt().coerceIn(0,255)
            bytes[i++] = y.toByte()
        }
        return OcrImage.Gray8(w, h, bytes, w)
    }
}
