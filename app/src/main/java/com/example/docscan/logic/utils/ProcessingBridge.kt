package com.example.docscan.logic.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.domain.interfaces.Imaging
import com.example.pipeline_core.scan.CamScanPipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AndroidProcessResult(
    val overlay: Bitmap?,          // optional debug overlay
    val enhanced: Bitmap?,         // preview bitmap
    val outJpeg: ByteArray,        // processed JPEG bytes (persist this)
    val quad: FloatArray,
    val paperName: String?
)

suspend fun runCamScanAsync(
    imaging: Imaging,
    cameraJpeg: ByteArray,
    options: CamScanPipeline.Options = CamScanPipeline.Options(
        enhanceMode = "auto_pro",
        jpegQuality = 85,
        includeOverlay = true,
        overlayQuality = 80
    )
): AndroidProcessResult = withContext(Dispatchers.Default) {

    val pipeline = CamScanPipeline(imaging = imaging)
    val r = pipeline.processJpeg(cameraJpeg = cameraJpeg, options = options)

    val enhancedBmp = BitmapFactory.decodeByteArray(r.outJpeg, 0, r.outJpeg.size)
    val overlayBmp = r.overlayJpeg?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }

    AndroidProcessResult(
        overlay = overlayBmp,
        enhanced = enhancedBmp,
        outJpeg = r.outJpeg,
        quad = r.quad,
        paperName = r.paperName
    )
}

// Convenience overload if some caller still has Bitmap
suspend fun runCamScanAsync(
    imaging: Imaging,
    src: Bitmap,
    cameraQuality: Int = 92
): AndroidProcessResult {
    val jpeg = bitmapToJpegBytes(src, quality = cameraQuality) // keep your helper :contentReference[oaicite:3]{index=3}
    return runCamScanAsync(imaging, jpeg)
}
