package com.example.docscan

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.imaging_opencv_android.OpenCvImaging
import com.example.pipeline_core.scan.CamScanPipeline
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class CamScanPipelineSmokeTest {

    private val TAG = "CamScanPipelineSmokeTest"

    @Test
    fun processOneImage_saveAndPrintPaths() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("OpenCVLoader.initDebug() failed", OpenCVLoader.initDebug())

        val bmp: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.raw)
            ?: error("Missing R.drawable.sample_scan")

        val cameraJpeg = bmpToJpeg(bmp, quality = 92)

        val pipeline = CamScanPipeline(imaging = OpenCvImaging())
        val result = pipeline.processJpeg(
            cameraJpeg = cameraJpeg,
            options = CamScanPipeline.Options(
                enhanceMode = "auto_pro",
                jpegQuality = 85,
                includeOverlay = true,
                overlayQuality = 80
            )
        )

        assertTrue("Output JPEG too small: ${result.outJpeg.size}", result.outJpeg.size > 10_000)

        // A) App-scoped external dir (may be deleted if app gets uninstalled)
        val appOutDir = File(context.getExternalFilesDir(null), "test-output").apply { mkdirs() }
        val appEnhanced = File(appOutDir, "enhanced.jpg").apply { writeBytes(result.outJpeg) }
        val appOverlay = result.overlayJpeg?.let { File(appOutDir, "overlay.jpg").apply { writeBytes(it) } }

        // B) Downloads/DocScanTest (persists, easiest to find + pull)
        val dlEnhancedUri = saveToDownloads(context, "enhanced_smoke.jpg", result.outJpeg)
        val dlOverlayUri = result.overlayJpeg?.let { saveToDownloads(context, "overlay_smoke.jpg", it) }

        val msg =
            "\n===== CamScanPipeline Smoke Output =====\n" +
                    "packageName: ${context.packageName}\n" +
                    "\n[App external files dir]\n" +
                    "dir: ${appOutDir.absolutePath}\n" +
                    "enhanced: ${appEnhanced.absolutePath} (bytes=${appEnhanced.length()})\n" +
                    (appOverlay?.let { "overlay:  ${it.absolutePath} (bytes=${it.length()})\n" } ?: "overlay:  <not generated>\n") +
                    "\n[Downloads]\n" +
                    "relative: Downloads/DocScanTest/\n" +
                    "enhanced uri: $dlEnhancedUri\n" +
                    "overlay uri:  ${dlOverlayUri ?: "<not generated>"}\n" +
                    "=======================================\n"

        Log.i(TAG, msg)
        println(msg)
    }

    private fun bmpToJpeg(bmp: Bitmap, quality: Int): ByteArray {
        val os = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, os)
        return os.toByteArray()
    }

    private fun saveToDownloads(context: android.content.Context, fileName: String, bytes: ByteArray): String {
        // Modern Android (API 29+) using MediaStore
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "image/jpeg")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DocScanTest")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Failed to insert into Downloads")

            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error("Failed to open Downloads output stream")

            return uri.toString()
        }

        // Legacy fallback (API < 29)
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "DocScanTest")
            .apply { mkdirs() }
        val out = File(dir, fileName)
        out.writeBytes(bytes)
        return out.absolutePath
    }
}
