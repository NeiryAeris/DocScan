package com.example.docscan.logic.utils

import com.example.ocr.core.api.CloudOcrGateway
import com.example.ocr.core.api.OcrImage
import com.example.ocr.core.api.OcrWord
import android.graphics.Bitmap
import com.example.ocr_remote.RemoteOcrClient
import com.example.ocr_remote.RemoteOcrResponse
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class NodeCloudOcrGateway(
    private val remoteClient: RemoteOcrClient
) : CloudOcrGateway {

    override suspend fun recognize(req: CloudOcrGateway.Request): CloudOcrGateway.Response {
        val pageId = req.hints["pageId"]
            ?: error("NodeCloudOcrGateway: hints[\"pageId\"] is required")

        val docId = req.hints["docId"]
        val pageIndex = req.hints["pageIndex"]?.toIntOrNull()
        val rotation = req.hints["rotation"]?.toIntOrNull()

        val jpegBytes = encodeToJpeg(req.image)

        val remoteResp: RemoteOcrResponse = remoteClient.ocrPage(
            pageId = pageId,
            imageBytes = jpegBytes,
            mimeType = "image/jpeg",
            docId = docId,
            pageIndex = pageIndex,
            rotation = rotation
        )

        return CloudOcrGateway.Response(
            text = remoteResp.text,
            words = remoteResp.words.map { w ->
                OcrWord(
                    text = w.text,
                    bbox = intArrayOf(
                        w.bbox.getOrNull(0) ?: 0,
                        w.bbox.getOrNull(1) ?: 0,
                        w.bbox.getOrNull(2) ?: 0,
                        w.bbox.getOrNull(3) ?: 0
                    ),
                    conf = w.conf
                )
            },
            elapsedMs = remoteResp.meta?.durationMs
        )
    }

    private fun encodeToJpeg(image: OcrImage): ByteArray {
        val bitmap: Bitmap = when (image) {
            is OcrImage.Gray8 -> gray8ToBitmap(image)
            is OcrImage.Rgba8888 -> rgbaToBitmap(image)
        }

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return out.toByteArray()
    }

    private fun gray8ToBitmap(img: OcrImage.Gray8): Bitmap {
        val bmp = Bitmap.createBitmap(img.width, img.height, Bitmap.Config.ARGB_8888)
        val argb = IntArray(img.width * img.height)

        var idx = 0
        var srcIdx = 0
        for (y in 0 until img.height) {
            for (x in 0 until img.width) {
                val g = img.bytes[srcIdx].toInt() and 0xFF
                argb[idx++] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
                srcIdx++
            }
        }
        bmp.setPixels(argb, 0, img.width, 0, 0, img.width, img.height)
        return bmp
    }

    private fun rgbaToBitmap(img: OcrImage.Rgba8888): Bitmap {
        val bmp = Bitmap.createBitmap(img.width, img.height, Bitmap.Config.ARGB_8888)
        val buffer = ByteBuffer.wrap(img.bytes)
        bmp.copyPixelsFromBuffer(buffer)
        return bmp
    }
}