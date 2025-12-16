package com.example.docscan.logic.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.domain.interfaces.Imaging
import com.example.pipeline_core.scan.CamScanPipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AndroidProcessResult(
    val overlay: Bitmap?,        // optional debug overlay
    val enhanced: Bitmap?,       // for preview (decoded from jpeg)
    val outJpeg: ByteArray,      // THE real processed page bytes
    val quad: FloatArray,
    val paperName: String?
)

/**
 * Coroutine-friendly wrapper.
 *
 * Preferred input is JPEG bytes (camera file bytes / gallery bytes),
 * but a Bitmap overload is provided for convenience.
 */
suspend fun runCamScanPipelineAsync(
    imaging: Imaging,
    cameraJpeg: ByteArray,
    options: CamScanPipeline.Options = CamScanPipeline.Options(includeOverlay = true)
): AndroidProcessResult = withContext(Dispatchers.Default) {

    val pipeline = CamScanPipeline(imaging = imaging)

    val r = pipeline.processJpeg(cameraJpeg = cameraJpeg, options = options)

    val enhancedBmp = BitmapFactory.decodeByteArray(r.outJpeg, 0, r.outJpeg.size)
    val overlayBmp = r.overlayJpeg?.let { bytes ->
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    AndroidProcessResult(
        overlay = overlayBmp,
        enhanced = enhancedBmp,
        outJpeg = r.outJpeg,
        quad = r.quad,
        paperName = r.paperName
    )
}

/** Convenience overload if some caller still has Bitmap. */
suspend fun runCamScanPipelineAsync(
    imaging: Imaging,
    src: Bitmap,
    options: CamScanPipeline.Options = CamScanPipeline.Options(includeOverlay = true),
    cameraQuality: Int = 92
): AndroidProcessResult {
    val jpeg = bitmapToJpegBytes(src, quality = cameraQuality) // from ImageEncoding.kt
    return runCamScanPipelineAsync(imaging, jpeg, options)
}
