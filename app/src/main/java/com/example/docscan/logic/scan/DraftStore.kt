package com.example.docscan.logic.scan

import android.content.Context
import java.io.File

class DraftStore(private val context: Context) {

    fun sessionDir(sessionId: String): File =
        File(context.cacheDir, "scan-session/$sessionId").apply { mkdirs() }

    fun processedJpegFile(sessionId: String, index: Int): File =
        File(sessionDir(sessionId), "page_${index.toString().padStart(3, '0')}.jpg")

    fun writeProcessedJpeg(sessionId: String, index: Int, jpeg: ByteArray): File {
        val f = processedJpegFile(sessionId, index)
        f.parentFile?.mkdirs()
        f.writeBytes(jpeg)
        return f
    }

    fun clearSession(sessionId: String) {
        sessionDir(sessionId).deleteRecursively()
    }
}
