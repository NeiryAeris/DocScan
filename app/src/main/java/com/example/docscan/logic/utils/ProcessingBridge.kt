package com.example.docscan.logic.utils

import android.graphics.Bitmap
import com.example.pipeline_core.DocumentPipeline
import org.opencv.core.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AndroidProcessResult(
    val overlay: Bitmap?,         // original with green quad, for preview
    val enhanced: Bitmap?,        // final enhanced image (mode: "auto"/"color"/"gray"/"bw")
    val quad: Array<Point>?       // detected corners in original coordinates
)

/** Synchronous call (run on a background thread). */
fun runPipeline(bitmap: Bitmap, mode: String = "color"): AndroidProcessResult {
    val src = AndroidMatAdapter.bitmapToMat(bitmap)
    val r = DocumentPipeline.process(src, mode)

    val overlayBmp = AndroidMatAdapter.matToBitmap(r.overlay)
    val enhancedBmp = r.enhanced?.let { AndroidMatAdapter.matToBitmap(it) }

    // free native memory
    r.warped?.release()
    r.enhanced?.release()
    r.overlay.release()
    src.release()

    return AndroidProcessResult(overlay = overlayBmp, enhanced = enhancedBmp, quad = r.quad)
}

/** Coroutine-friendly wrapper (safe to call from ViewModel/Repository). */
suspend fun runPipelineAsync(bitmap: Bitmap, mode: String = "color"): AndroidProcessResult =
    withContext(Dispatchers.Default) { runPipeline(bitmap, mode) }