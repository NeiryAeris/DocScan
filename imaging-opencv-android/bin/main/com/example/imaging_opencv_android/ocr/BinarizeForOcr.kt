package com.example.imaging_opencv_android.ocr

import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.min

object BinarizeForOcr {

    fun toGray(src: Mat): Mat =
        if (src.channels() == 1) src else Mat().also { Imgproc.cvtColor(src, it, Imgproc.COLOR_BGR2GRAY) }

    /** Gaussian adaptive threshold; tune blockSize (odd) 31–41 and C: 5–15. */
    fun adaptiveBin(gray: Mat, blockSize: Int = 35, c: Double = 10.0): Mat {
        val out = Mat()
        Imgproc.adaptiveThreshold(gray, out, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, blockSize, c)
        return out
    }

    /** Upscale small pages so text x-height is friendly to Tesseract (~300dpi-ish). */
    fun upscaleIfSmall(img: Mat, minTarget: Int = 1600, scale: Double = 1.5): Mat {
        val minDim = min(img.cols(), img.rows())
        if (minDim >= minTarget) return img
        val dst = Mat()
        Imgproc.resize(img, dst, Size(img.cols() * scale, img.rows() * scale), 0.0, 0.0, Imgproc.INTER_CUBIC)
        return dst
    }
}
