package com.example.ocr_tesseract_android

import android.content.Context
import java.io.File

object TessDataInstaller {
    fun ensure(context: Context, langs: List<String>): String {
        val base = File(context.filesDir, "tesseract")
        val tessdata = File(base, "tessdata").apply { mkdirs() }
        val am = context.assets
        langs.forEach { lang ->
            val dst = File(tessdata, "$lang.traineddata")
            if (!dst.exists()) am.open("tessdata/$lang.traineddata").use { it.copyTo(dst.outputStream()) }
        }
        return base.absolutePath
    }
}