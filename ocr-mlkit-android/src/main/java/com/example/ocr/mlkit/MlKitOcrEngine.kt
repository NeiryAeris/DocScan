package com.example.ocr.mlkit

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import com.example.ocr.core.api.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.system.measureTimeMillis

class MlKitOcrEngine : OcrEngine {

    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun recognize(image: OcrImage, lang: String): OcrPageResult {
        val bmp = when (image) {
            is OcrImage.Gray8 -> gray8ToBitmap(image)
            is OcrImage.Rgba8888 -> rgbaToBitmap(image)
        }
        var text = ""
        val elapsed = measureTimeMillis {
            val result = client.process(InputImage.fromBitmap(bmp, 0)).await()
            val sb = StringBuilder()
            result.textBlocks.forEach { b -> b.lines.forEach { l -> sb.appendLine(l.text) } }
            text = sb.toString()
        }
        return OcrPageResult(pageNo = 0, text = text, durationMs = elapsed)
    }

    private fun gray8ToBitmap(g: OcrImage.Gray8): Bitmap {
        // Expand gray to ARGB for InputImage.fromBitmap
        val out = Bitmap.createBitmap(g.width, g.height, Config.ARGB_8888)
        val pixels = IntArray(g.width * g.height)
        var di = 0
        var si = 0
        val w = g.width
        for (y in 0 until g.height) {
            val rowStart = si
            for (x in 0 until w) {
                val v = g.bytes[rowStart + x].toInt() and 0xFF
                pixels[di++] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
            si += g.rowStride
        }
        out.setPixels(pixels, 0, w, 0, 0, w, g.height)
        return out
    }

    private fun rgbaToBitmap(r: OcrImage.Rgba8888): Bitmap {
        val out = Bitmap.createBitmap(r.width, r.height, Config.ARGB_8888)
        // ByteArray RGBA -> Int ARGB
        val pixels = IntArray(r.width * r.height)
        var di = 0
        var si = 0
        val w = r.width
        for (y in 0 until r.height) {
            val row = si
            var xOff = 0
            repeat(w) {
                val R = r.bytes[row + xOff].toInt() and 0xFF
                val G = r.bytes[row + xOff + 1].toInt() and 0xFF
                val B = r.bytes[row + xOff + 2].toInt() and 0xFF
                val A = r.bytes[row + xOff + 3].toInt() and 0xFF
                pixels[di++] = (A shl 24) or (R shl 16) or (G shl 8) or B
                xOff += 4
            }
            si += r.rowStride
        }
        out.setPixels(pixels, 0, w, 0, 0, w, r.height)
        return out
    }
}
