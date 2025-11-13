package com.example.imaging_opencv_android.ocr

import com.example.ocr.core.api.OcrImage
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/** Ensure Gray8 for Tesseract; simpler & more robust than RGBA. */
fun Mat.asOcrGray8(): OcrImage.Gray8 {
    val gray = if (this.type() == CvType.CV_8UC1) this else {
        val g = Mat(); Imgproc.cvtColor(this, g, Imgproc.COLOR_BGR2GRAY); g
    }
    val w = gray.cols(); val h = gray.rows()
    val bytes = ByteArray(w * h)
    gray.get(0, 0, bytes)
    return OcrImage.Gray8(width = w, height = h, bytes = bytes, rowStride = w)
}

/** Only use if an engine needs RGBA bytes (ML Kit path is fine with Bitmap). */
fun Mat.asOcrRgba8888(): OcrImage.Rgba8888 {
    val rgba = Mat()
    when (channels()) {
        1 -> Imgproc.cvtColor(this, rgba, Imgproc.COLOR_GRAY2RGBA)
        3 -> Imgproc.cvtColor(this, rgba, Imgproc.COLOR_BGR2RGBA)
        4 -> this.copyTo(rgba)
        else -> error("Unsupported channels: ${channels()}")
    }
    val w = rgba.cols(); val h = rgba.rows()
    val bytes = ByteArray(w * h * 4)
    rgba.get(0, 0, bytes)
    rgba.release()
    return OcrImage.Rgba8888(width = w, height = h, bytes = bytes, rowStride = w * 4, premultiplied = false)
}
