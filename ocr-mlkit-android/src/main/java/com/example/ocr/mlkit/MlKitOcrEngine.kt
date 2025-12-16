package com.example.ocr.mlkit

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import com.example.domain.types.text.TextNormalize
import com.example.ocr.core.api.*
import com.example.ocr.core.api.legacy.OcrEngine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.system.measureTimeMillis

class MlKitOcrEngine : OcrEngine {
    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun recognize(image: OcrImage, lang: String): OcrPageResult {
        val bmp = when (image) {
            is OcrImage.Gray8    -> grayToBitmap(image)
            is OcrImage.Rgba8888 -> rgbaToBitmap(image)
        }
        var text = ""
        val elapsed = measureTimeMillis {
            val result = client.process(InputImage.fromBitmap(bmp, 0)).await()
            val sb = StringBuilder()
            result.textBlocks.forEach { b -> b.lines.forEach { l -> sb.appendLine(l.text) } }
            text = TextNormalize.sanitize(sb.toString())
        }
        return OcrPageResult(pageNo = 1, text = text, durationMs = elapsed)
    }

    private fun grayToBitmap(g: OcrImage.Gray8): Bitmap {
        val w = g.width; val h = g.height
        val out = Bitmap.createBitmap(w, h, Config.ARGB_8888)
        val pixels = IntArray(w * h)
        var di = 0
        var si = 0
        repeat(h) {
            val row = si; si += g.rowStride
            for (x in 0 until w) {
                val v = g.bytes[row + x].toInt() and 0xFF
                pixels[di++] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun rgbaToBitmap(r: OcrImage.Rgba8888): Bitmap {
        val w = r.width; val h = r.height
        val out = Bitmap.createBitmap(w, h, Config.ARGB_8888)
        val pixels = IntArray(w * h)
        var di = 0
        var si = 0
        repeat(h) {
            val row = si; si += r.rowStride
            var xOff = 0
            for (x in 0 until w) {
                val R = r.bytes[row + xOff].toInt() and 0xFF
                val G = r.bytes[row + xOff + 1].toInt() and 0xFF
                val B = r.bytes[row + xOff + 2].toInt() and 0xFF
                val A = r.bytes[row + xOff + 3].toInt() and 0xFF
                pixels[di++] = (A shl 24) or (R shl 16) or (G shl 8) or B
                xOff += 4
            }
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }
}
