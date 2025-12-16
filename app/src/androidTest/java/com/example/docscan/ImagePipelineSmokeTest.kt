package com.example.docscan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.domain.interfaces.Imaging
import com.example.imaging_opencv_android.OpenCvImaging
import com.example.pipeline_core.DocumentLayouter
import com.example.pipeline_core.OrientationPolicy
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.io.File
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class ImagePipelineSmokeTest {

    private val TAG = "ImagePipelineSmokeTest"

    @Test
    fun cameraLikeJpeg_goThroughPipeline_producesEnhancedJpeg() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // 0) Init OpenCV on Android
        assertTrue("OpenCVLoader.initDebug() failed", OpenCVLoader.initDebug())

        // 1) Load a “captured” image (simulate camera output as JPEG bytes)
        val bmp: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.raw)
            ?: error("Failed to decode R.drawable.sample_scan")

        val cameraJpegBytes = bmp.toJpeg(92) // simulate phone camera jpeg

        // 2) Real pipeline (same flow as your module smoke test)
        val imaging: Imaging = OpenCvImaging()
        val layouter = DocumentLayouter(
            imaging = imaging,
            defaultDpi = 150,
            policy = OrientationPolicy.PortraitPreferred,
            tol = 0.08
        )

        val src = imaging.fromBytes(cameraJpegBytes)
        val quad = imaging.detectDocumentQuad(src)
        assertNotNull("No document quad detected", quad)

        val warped = layouter.warpAutoPaper(src, quad!!)
        val enhanced = imaging.enhanceDocument(warped, "auto_pro")
        val outJpeg = imaging.toJpeg(enhanced, 85)

        // 3) Assertions (smoke-level)
        assertTrue("Output jpeg too small: ${outJpeg.size}", outJpeg.size > 10_000)

        // 4) Save output so you can visually inspect in Device Explorer
        val outDir = File(context.getExternalFilesDir(null), "test-output").apply { mkdirs() }
        val outFile = File(outDir, "enhanced_smoke.jpg")
        outFile.outputStream().use { it.write(outJpeg) }

        Log.i(TAG, "✅ Enhanced JPEG bytes=${outJpeg.size}")
        Log.i(TAG, "✅ Wrote: ${outFile.absolutePath}")

        // best-effort native cleanup (same pattern you used)
        tryRelease(imaging, src, warped, enhanced)
    }

    private fun Bitmap.toJpeg(quality: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        // camera-like output is JPEG
        compress(Bitmap.CompressFormat.JPEG, quality, baos)
        return baos.toByteArray()
    }

    private fun tryRelease(imaging: Imaging, vararg refs: Any) {
        try {
            val m = imaging::class.java.methods.firstOrNull {
                it.name == "release" && it.parameterTypes.size == 1
            } ?: return
            refs.forEach { m.invoke(imaging, it) }
        } catch (_: Throwable) { /* ignore */ }
    }
}
