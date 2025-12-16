package com.example.imaging_opencv_android.ocr

import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.*

object OcrPreprocess {

    /** White-balance + background (illumination) correction. */
    fun illuminationCorrect(gray: Mat): Mat {
        // gray in [0..255], CV_8UC1
        val float = Mat()
        gray.convertTo(float, CvType.CV_32F, 1.0 / 255.0)
        val bg = Mat()
        // big blur to approximate background illumination
        Imgproc.GaussianBlur(float, bg, Size(51.0, 51.0), 0.0)
        val norm = Mat()
        Core.divide(float, bg, norm)           // flatten illumination
        Core.MinMaxLocResult() // keep code explicit; (no-op, clarity)
        val out = Mat()
        norm.convertTo(out, CvType.CV_8U, 255.0)
        float.release(); bg.release(); norm.release()
        return out
    }

    /** Estimate skew (in degrees) via Hough on edges; rotate to deskew. */
    fun deskew(gray: Mat): Mat {
        val edges = Mat()
        Imgproc.Canny(gray, edges, 50.0, 150.0)
        val lines = Mat()
        Imgproc.HoughLinesP(edges, lines, 1.0, Math.PI/180.0, 80, 80.0, 5.0)
        var angle = 0.0
        var count = 0
        for (i in 0 until lines.rows()) {
            val l = lines.get(i, 0)
            val dx = l[2] - l[0]; val dy = l[3] - l[1]
            if (abs(dx) < 1e-3) continue
            val a = Math.toDegrees(atan2(dy, dx))
            // Keep near-horizontal lines
            if (abs(a) < 20.0) { angle += a; count++ }
        }
        edges.release(); lines.release()
        if (count == 0) return gray
        angle /= count.toDouble()
        val center = Point(gray.cols()/2.0, gray.rows()/2.0)
        val rot = Imgproc.getRotationMatrix2D(center, angle, 1.0)
        val rotated = Mat()
        Imgproc.warpAffine(gray, rotated, rot, Size(gray.cols().toDouble(), gray.rows().toDouble()),
            Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE, Scalar(255.0))
        return rotated
    }

    /** Adaptive Gaussian threshold; tune blockSize/C if needed. */
    fun adaptiveBin(gray: Mat, blockSize: Int = 35, c: Double = 10.0): Mat {
        val bin = Mat()
        Imgproc.adaptiveThreshold(
            gray, bin, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            blockSize, c
        )
        return bin
    }

    /** Otsu as a second candidate (often good on uniformly lit pages). */
    fun otsu(gray: Mat): Mat {
        val bin = Mat()
        Imgproc.threshold(gray, bin, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
        return bin
    }

    /** Upscale if small to approximate ~300dpi x-height. */
    fun upscaleIfSmall(src: Mat, minDimTarget: Int = 1600): Mat {
        val minDim = min(src.cols(), src.rows())
        if (minDim >= minDimTarget) return src
        val dst = Mat()
        Imgproc.resize(src, dst, Size(src.cols()*1.5, src.rows()*1.5),
            0.0, 0.0, Imgproc.INTER_CUBIC)
        return dst
    }

    /** Full pipeline producing two candidates: [adaptive, otsu]. */
    fun produceCandidates(inputGray: Mat): Pair<Mat, Mat> {
        val illum = illuminationCorrect(inputGray)
        val deSkew = deskew(illum)
        val up = upscaleIfSmall(deSkew)
        val a = adaptiveBin(up) // variant A
        val b = otsu(up)        // variant B
        return a to b
    }
}
