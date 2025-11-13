package com.example.imaging_opencv_android

import com.example.ocr.core.api.OcrImage
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

fun Mat.forOcrGray8(): OcrImage.Gray8 {
    // if not already CV_8UC1, convert:
    val g = if (type() != CvType.CV_8UC1) {
        val tmp = Mat(); Imgproc.cvtColor(this, tmp, Imgproc.COLOR_BGR2GRAY); tmp
    } else this

    // up-scale small text to ~300dpi feel
    val minDim = minOf(g.cols(), g.rows())
    val scaled = if (minDim < 1600) {
        val dst = Mat()
        Imgproc.resize(g, dst, org.opencv.core.Size(g.cols()*1.5, g.rows()*1.5),
            0.0, 0.0, Imgproc.INTER_CUBIC)
        dst
    } else g

    val w = scaled.cols(); val h = scaled.rows()
    val bytes = ByteArray(w * h)
    scaled.get(0, 0, bytes)
    if (scaled !== g) scaled.release()
    return OcrImage.Gray8(w, h, bytes, w)
}

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
