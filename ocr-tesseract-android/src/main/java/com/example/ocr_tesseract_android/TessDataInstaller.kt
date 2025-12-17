package com.example.ocr_tesseract_android

import android.content.Context
import java.io.File

object TessDataInstaller {
    fun ensure(context: Context, languages: List<String>): String {
        val base = File(context.filesDir, "tesseract")
        val tessdata = File(base, "tessdata").apply { mkdirs() }
        val am = context.assets
        languages.forEach { lang ->
            val dst = File(tessdata, "$lang.traineddata")
            if (!dst.exists() || dst.length() < 1_000_000L) {
                am.open("tessdata/$lang.traineddata").use { it.copyTo(dst.outputStream()) }
            }
        }
        return base.absolutePath // pass to TesseractOcrEngine(dataPath)
    }
}