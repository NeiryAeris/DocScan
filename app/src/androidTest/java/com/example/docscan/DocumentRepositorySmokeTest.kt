package com.example.docscan

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.docscan.logic.io.PdfFileStore
import com.example.docscan.logic.session.SessionController
import com.example.docscan.logic.storage.DocumentRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class DocumentRepositorySmokeTest {

    @Test
    fun listDocuments_containsNewDoc_andListsPages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("OpenCV init failed", OpenCVLoader.initDebug())

        // 1) Create a doc by scanning 2 pages
        val controller = SessionController(context)

        val bmp = BitmapFactory.decodeResource(context.resources, R.drawable.raw)
            ?: error("Missing R.drawable.raw")

        val jpeg = ByteArrayOutputStream().use { os ->
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, os)
            os.toByteArray()
        }

        val docId = runBlocking {
            controller.processIntoSlot(0, jpeg)
            controller.processIntoSlot(1, jpeg)
            controller.saveSession()
        }

        // 2) (Optional) export PDF to confirm doc folder is valid
        PdfFileStore().exportSavedDocToPdf(context, docId)

        // 3) List docs + verify the new one exists
        val repo = DocumentRepository(context)
        val docs = repo.listDocumentsNewestFirst()

        assertTrue("Doc list empty", docs.isNotEmpty())
        assertTrue("Created docId not found in list: $docId", docs.any { it.docId == docId })

        // 4) List pages + verify page files exist
        val pages = repo.listPages(docId)
        assertTrue("Pages should be >= 2 but was ${pages.size}", pages.size >= 2)

        // Print helpful paths
        val docDir = java.io.File(context.getExternalFilesDir(null), "documents/$docId")
        val msg =
            "\n===== DOCUMENT REPO SMOKE OUTPUT =====\n" +
                    "docId: $docId\n" +
                    "docDir: ${docDir.absolutePath}\n" +
                    "docs (top 5): ${docs.take(5).joinToString { it.docId + \"(\" + it.pageCount + \")\" }}\n" +
                        "pages: ${pages.joinToString { it.name }}\n" +
                                "======================================\n"

                        println(msg)
    }
}