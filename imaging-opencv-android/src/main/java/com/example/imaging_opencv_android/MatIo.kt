package com.example.imaging_opencv_android

import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfInt
import org.opencv.imgcodecs.Imgcodecs

internal object MatIo {
    fun decode(bytes: ByteArray): Mat {
        val mob = MatOfByte().apply { fromArray(*bytes) }
        return Imgcodecs.imdecode(mob, Imgcodecs.IMREAD_UNCHANGED)
    }

    fun encodeJpeg(mat: Mat, quality: Int = 85): ByteArray {
        val out = MatOfByte()
        val params = MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, quality)
        Imgcodecs.imencode(".jpg", mat, out, params)
        return out.toArray()
    }

    fun encodePng(mat: Mat): ByteArray {
        val out = MatOfByte()
        Imgcodecs.imencode(".png", mat, out)
        return out.toArray()
    }
}
