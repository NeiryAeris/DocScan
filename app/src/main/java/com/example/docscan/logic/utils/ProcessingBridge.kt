package com.example.docscan.logic.utils

import android.graphics.Bitmap
import com.example.pipeline_core.DocumentPipeline
import com.example.pipeline_core.EncodedPage
import com.example.pipeline_core.PageEncoder
import org.opencv.core.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AndroidProcessResult(val overlay: Bitmap?, val enhanced: Bitmap?, val page: EncodedPage)

/** Synchronous call (run on a background thread). */
//fun runPipeline(bitmap: Bitmap, mode: String = "color"): AndroidProcessResult {
//    val src = AndroidMatAdapter.bitmapToMat(bitmap)
//    val r = DocumentPipeline.process(src, mode)
//
//    val overlayBmp = AndroidMatAdapter.matToBitmap(r.overlay)
//    val enhancedBmp = r.enhanced?.let { AndroidMatAdapter.matToBitmap(it) }
//
//    // free native memory
//    r.warped?.release()
//    r.enhanced?.release()
//    r.overlay.release()
//    src.release()
//
//    return AndroidProcessResult(overlay = overlayBmp, enhanced = enhancedBmp, quad = r.quad)
//}

/** Coroutine-friendly wrapper (safe to call from ViewModel/Repository). */
suspend fun runPipelineAsync(src: Bitmap, mode: String = "sketch"): AndroidProcessResult =
    withContext(Dispatchers.Default) {
        val rgba = AndroidMatAdapter.bitmapToMat(src)
        val r = DocumentPipeline.process(rgba, mode)
        val best = r.enhanced ?: r.warped ?: r.overlay
        val encoded = PageEncoder.from(best)
        val overlayBmp = AndroidMatAdapter.matToBitmap(r.overlay)
        val enhancedBmp = AndroidMatAdapter.matToBitmap(best)
        r.warped?.release(); r.enhanced?.release(); if (best !== r.overlay) r.overlay.release(); rgba.release()
        AndroidProcessResult(overlayBmp, enhancedBmp, encoded)
    }