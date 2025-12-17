package com.example.docscan.logic.utils

import android.graphics.Bitmap
import com.example.ocr_remote.RemoteOcrClient
import com.example.ocr_remote.RemoteOcrResponse

class RemoteOcrRepository(
    private val client: RemoteOcrClient
) {
    suspend fun ocrPage(
        pageId: String,
        bitmap: Bitmap,
        docId: String?,
        pageIndex: Int,
        rotation: Int
    ): RemoteOcrResponse {
        val bytes = bitmapToJpegBytes(bitmap, quality = 90)

        return client.ocrPage(
            pageId = pageId,
            imageBytes = bytes,
            mimeType = "image/jpeg",
            docId = docId,
            pageIndex = pageIndex,
            rotation = rotation
        )
    }
}
