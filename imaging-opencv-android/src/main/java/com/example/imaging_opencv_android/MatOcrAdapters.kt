package com.example.imaging_opencv_android

import com.example.ocr.core.api.OcrImage
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

fun Mat.asOcrGray8(): OcrImage.Gray8 {
    require(type() == CvType.CV_8UC1) { "Expected CV_8UC1 (gray), got $type" }
    val bytes = ByteArray(rows() * cols())
    get(0, 0, bytes)
    val stride = cols() // 1 byte per pixel
    return OcrImage.Gray8(width = cols(), height = rows(), bytes = bytes, rowStride = stride)
}

fun Mat.asOcrRgba8888(): OcrImage.Rgba8888 {
    val rgba = Mat()
    when (channels()) {
        1 -> Imgproc.cvtColor(this, rgba, Imgproc.COLOR_GRAY2RGBA)
        3 -> Imgproc.cvtColor(this, rgba, Imgproc.COLOR_BGR2RGBA)
        4 -> this.copyTo(rgba) // assume already RGBA
        else -> error("Unsupported channels: ${channels()}")
    }
    val w = rgba.cols()
    val h = rgba.rows()
    val bytes = ByteArray(w * h * 4)
    rgba.get(0, 0, bytes)
    rgba.release()
    return OcrImage.Rgba8888(width = w, height = h, bytes = bytes, rowStride = w * 4, premultiplied = false)
}