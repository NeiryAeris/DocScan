package com.example.docscan.logic.io

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class PhotoFileStore(private val context: Context) {
    private val dateFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    fun newTempJpeg(prefix: String = "shot"): File {
        val name = "${prefix}_${dateFmt.format(System.currentTimeMillis())}.jpg"
        return File(context.cacheDir, name)
    }
}
