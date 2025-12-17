package com.example.docscan

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.docscan.logic.session.SessionController
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class SaveSessionSmokeTest {

    @Test
    fun saveSession_createsDocumentPages_andPrintPath() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("OpenCV init failed", OpenCVLoader.initDebug())

        val controller = SessionController(context)

        val bmp = BitmapFactory.decodeResource(context.resources, R.drawable.raw)
            ?: error("Missing R.drawable.sample_scan")

        val cameraJpeg = ByteArrayOutputStream().use { os ->
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, os)
            os.toByteArray()
        }

        runBlocking {
            // Scan 2 pages (slot 0 -> ready, slot 1 exists as empty, scan into slot 1)
            controller.processIntoSlot(0, cameraJpeg)
            controller.processIntoSlot(1, cameraJpeg)

            val docId = controller.saveSession()

            val pagesDir = File(context.getExternalFilesDir(null), "documents/$docId/pages")
            val p0 = File(pagesDir, "page_000.jpg")
            val p1 = File(pagesDir, "page_001.jpg")

            assertTrue("Missing: ${p0.absolutePath}", p0.exists() && p0.length() > 10_000)
            assertTrue("Missing: ${p1.absolutePath}", p1.exists() && p1.length() > 10_000)

            val msg =
                "\n===== SAVE SESSION SMOKE OUTPUT =====\n" +
                        "docId: $docId\n" +
                        "pagesDir: ${pagesDir.absolutePath}\n" +
                        "p0: ${p0.absolutePath} (bytes=${p0.length()})\n" +
                        "p1: ${p1.absolutePath} (bytes=${p1.length()})\n" +
                        "dir listing: ${pagesDir.listFiles()?.joinToString { it.name } ?: "<empty>"}\n" +
                        "=====================================\n"

            println(msg)
        }
    }
}
