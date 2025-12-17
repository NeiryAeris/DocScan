package com.example.docscan

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.docscan.logic.scan.PageSlot
import com.example.docscan.logic.session.SessionController
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class ScanSessionControllerSmokeTest {

    @Test
    fun processOneSlot_writesDraftJpeg() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(OpenCVLoader.initDebug())

        val controller = SessionController(context)

        val bmp = BitmapFactory.decodeResource(context.resources, R.drawable.raw)
            ?: error("Missing R.drawable.raw")

        val os = ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, os)
        val jpeg = os.toByteArray()

        // slot 0
        kotlinx.coroutines.runBlocking {
            controller.processIntoSlot(index = 0, cameraJpeg = jpeg)
        }

        val slot0 = controller.state.value.slots.first()
        assertTrue(slot0 is PageSlot.Ready)

        val ready = slot0 as PageSlot.Ready
        val f = java.io.File(ready.processedJpegPath)
        assertTrue("Draft jpeg missing: ${ready.processedJpegPath}", f.exists())
        assertTrue("Draft jpeg too small: ${f.length()}", f.length() > 10_000)

        println("DRAFT OUTPUT: ${f.absolutePath}")
    }
}
