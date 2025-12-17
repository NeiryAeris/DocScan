package com.example.docscan

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.docscan.logic.io.PdfFileStore
import com.example.docscan.logic.session.SessionController
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class ExportPdfSmokeTest {

    @Test
    fun exportPdf_fromSavedDoc_producesPdfFile_andCopiesToDownloads() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("OpenCV init failed", OpenCVLoader.initDebug())

        val controller = SessionController(context)

        val bmp = BitmapFactory.decodeResource(context.resources, R.drawable.raw)
            ?: error("Missing R.drawable.raw")

        val jpeg = ByteArrayOutputStream().use { os ->
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, os)
            os.toByteArray()
        }

        runBlocking {
            controller.processIntoSlot(0, jpeg)
            controller.processIntoSlot(1, jpeg)

            val docId = controller.saveSession()

            val pdfStore = PdfFileStore()
            val pdfFile = pdfStore.exportSavedDocToPdf(context, docId)

            assertTrue("PDF missing: ${pdfFile.absolutePath}", pdfFile.exists())
            assertTrue("PDF too small: ${pdfFile.length()}", pdfFile.length() > 5_000)

            // ✅ Persist to Downloads so it survives test cleanup/uninstall
            val downloadsRef = copyPdfToDownloads(
                context = context,
                src = pdfFile,
                displayName = "docscan_smoke_$docId.pdf",
                subDir = "DocScanTest"
            )

            val msg =
                "\n===== PDF EXPORT SMOKE OUTPUT =====\n" +
                        "docId: $docId\n" +
                        "pdf (app dir): ${pdfFile.absolutePath} (bytes=${pdfFile.length()})\n" +
                        "docDir: ${File(context.getExternalFilesDir(null), "documents/$docId").absolutePath}\n" +
                        "pdf (Downloads): $downloadsRef\n" +
                        "Downloads folder: Downloads/DocScanTest/\n" +
                        "===================================\n"

            println(msg)
        }
    }

    private fun copyPdfToDownloads(
        context: android.content.Context,
        src: File,
        displayName: String,
        subDir: String
    ): String {
        require(src.exists()) { "Source PDF missing: ${src.absolutePath}" }

        // Modern Android (API 29+) using MediaStore
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/$subDir"
                )
            }

            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Failed to insert Downloads entry")

            context.contentResolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            } ?: error("Failed to open Downloads output stream")

            return uri.toString()
        }

        // Legacy fallback (API < 29)
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), subDir)
            .apply { mkdirs() }
        val outFile = File(dir, displayName)
        src.copyTo(outFile, overwrite = true)
        return outFile.absolutePath
    }
}
