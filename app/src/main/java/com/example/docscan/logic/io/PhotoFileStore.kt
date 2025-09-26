package com.example.docscan.logic.io

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class PhotoFileStore(private val context: Context) {

    private val dateFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    /**
     * Returns a file in app cache (auto-cleaned by OS when space is needed).
     * You can swap to external Pictures if you want user-visible files.
     */
    fun newTempJpeg(prefix: String = "shot"): File {
        val name = "${prefix}_${dateFmt.format(System.currentTimeMillis())}.jpg"
        return File(context.cacheDir, name)
    }
}