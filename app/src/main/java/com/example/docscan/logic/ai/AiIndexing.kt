package com.example.docscan.logic.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.docscan.App
import com.example.ocr.core.api.OcrImage
import com.example.ocr_remote.RemoteAiPageInDto
import com.example.ocr_remote.RemoteAiUpsertOcrRequestDto
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest

object AiIndexing {

    fun stableDocId(file: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(file.absolutePath.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun indexPdf(context: Context, pdfFile: File, title: String? = null) {
        val docId = stableDocId(pdfFile)

        val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)

        val pages = mutableListOf<RemoteAiPageInDto>()

        try {
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val buffer = ByteBuffer.allocate(bitmap.byteCount)
                bitmap.copyPixelsToBuffer(buffer)
                bitmap.recycle()

                val ocrImage = OcrImage.Rgba8888(
                    bytes = buffer.array(),
                    width = page.width,
                    height = page.height,
                    rowStride = page.width * 4
                )

                val ocr = App.ocrGateway.recognize(
                    docId = docId,
                    pageId = "p_$i",
                    image = ocrImage
                )

                pages += RemoteAiPageInDto(pageNumber = i, text = ocr.text.clean)
            }
        } finally {
            renderer.close()
            pfd.close()
        }

        App.aiClient.upsertOcrIndex(
            RemoteAiUpsertOcrRequestDto(
                docId = docId,
                title = title ?: pdfFile.nameWithoutExtension,
                replace = true,
                pages = pages
            )
        )
    }
}
