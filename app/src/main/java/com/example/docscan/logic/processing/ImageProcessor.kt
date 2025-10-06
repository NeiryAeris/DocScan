package com.example.docscan.logic.processing

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlin.math.max
import androidx.core.graphics.createBitmap

object ImageProcessor {

    data class Result(
        val bitmap: Bitmap,        // preview with contour
        val quad: Array<Point>?,    // detected quad points
        val warped: Mat?,           // raw warped document
//        val gray: Mat?,             // enhanced grayscale
//        val bw: Mat?,               // enhanced black & white
//        val color: Mat?,            // enhanced color
        val enhanced: Mat?,
        val file: File?             // saved JPEG (original warped)
    )

    fun processDocument(
        orig: Bitmap,
        outFile: File? = null,
//        modes: Set<String> = setOf("gray", "bw", "color")
    ): Result {
        // Ensure ARGB_8888
        val srcBmp = if (orig.config != Bitmap.Config.ARGB_8888)
            orig.copy(Bitmap.Config.ARGB_8888, false) else orig

        val src = Mat(srcBmp.height, srcBmp.width, CvType.CV_8UC4)
        Utils.bitmapToMat(srcBmp, src)

        // --- Resize ---
        val maxSide = 1000.0
        val h = src.rows()
        val w = src.cols()
        val scaleFactor = if (max(w, h) > maxSide) maxSide / max(w, h) else 1.0
        val small = Mat()
        if (scaleFactor != 1.0) {
            Imgproc.resize(src, small, Size(w * scaleFactor, h * scaleFactor))
        } else {
            src.copyTo(small)
        }

        val gray = Mat()
        val blurred = Mat()
        val binary = Mat()
        val edges = Mat()
        val closed = Mat()
        val contours = mutableListOf<MatOfPoint>()

        var quad: Array<Point>? = null
        var warped: Mat? = null
        var outBmp: Bitmap

//        var warpedGray: Mat? = null
//        var warpedBW: Mat? = null
//        var warpedColor: Mat? = null
        var warpedEnhanced: Mat? = null

        try {
            // Preprocess
            Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

            // Otsu threshold
            Imgproc.threshold(blurred, binary, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)

            // Canny
            Imgproc.Canny(binary, edges, 50.0, 150.0)

            // Morph close
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0))
            Imgproc.morphologyEx(edges, closed, Imgproc.MORPH_CLOSE, kernel)

            // Find contours
            val hierarchy = Mat()
            Imgproc.findContours(closed, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            contours.sortByDescending { Imgproc.contourArea(it) }

            val imgArea = (small.rows() * small.cols()).toDouble()
            val maxArea = imgArea * 0.9

            // Try top 15 contours
            for (c in contours.take(15)) {
                val area = Imgproc.contourArea(c)
                if (area > maxArea) continue

                val c2f = MatOfPoint2f(*c.toArray())
                val peri = Imgproc.arcLength(c2f, true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(c2f, approx, 0.08 * peri, true)

                val pts = approx.toArray()
                if (pts.size == 4) {
                    quad = pts.map { Point(it.x / scaleFactor, it.y / scaleFactor) }.toTypedArray()
                    warped = fourPointWarp(src, quad!!)
                    break
                }
            }

            // Fallback
            if (quad == null && contours.isNotEmpty()) {
                val rect = Imgproc.minAreaRect(MatOfPoint2f(*contours[0].toArray()))
                val boxPts = arrayOf(Point(), Point(), Point(), Point())
                rect.points(boxPts)
                quad = boxPts.map { Point(it.x / scaleFactor, it.y / scaleFactor) }.toTypedArray()
                warped = fourPointWarp(src, quad!!)
            }

            // Enhancements
//            if (warped != null) {
//                if ("gray" in modes) warpedGray = enhanceDocument(warped, "gray")
//                if ("bw" in modes) warpedBW = enhanceDocument(warped, "bw")
//                if ("color" in modes) warpedColor = enhanceDocument(warped, "color")
//            }
            if (warped != null) warpedEnhanced = enhanceDocument(warped, "color")



            // Draw contour on preview
            val previewMat = src.clone()
            quad?.let {
                Imgproc.polylines(
                    previewMat,
                    listOf(MatOfPoint(*it)),
                    true,
                    Scalar(0.0, 255.0, 0.0),
                    6
                )
            }
            outBmp = createBitmap(previewMat.cols(), previewMat.rows())
            Utils.matToBitmap(previewMat, outBmp)
            previewMat.release()

            // Save warped if needed
            var savedFile: File? = null
            if (outFile != null && warpedEnhanced != null) {
                val warpedBmp = createBitmap(warpedEnhanced.cols(), warpedEnhanced.rows())
                Utils.matToBitmap(warpedEnhanced, warpedBmp)
                FileOutputStream(outFile).use { fos ->
                    warpedBmp.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                }
                savedFile = outFile
            }

//            return Result(outBmp, quad, warped, warpedGray, warpedBW, warpedColor,warpedEnhanced , savedFile)
            return Result(outBmp, quad, warped, warpedEnhanced , savedFile)

        } finally {
            src.release()
            small.release()
            gray.release()
            blurred.release()
            binary.release()
            edges.release()
            closed.release()
            contours.forEach { it.release() }
        }
    }

    // ---- helpers ----
    private fun orderQuadClockwise(pts: Array<Point>): Array<Point> {
        val sumSorted = pts.sortedBy { it.x + it.y }
        val tl = sumSorted.first()
        val br = sumSorted.last()
        val diffSorted = pts.sortedBy { it.x - it.y }
        val bl = diffSorted.first()
        val tr = diffSorted.last()
        return arrayOf(tl, tr, br, bl)
    }

//    private fun fourPointWarp(image: Mat, pts: Array<Point>): Mat {
//        val rect = orderQuadClockwise(pts)
//        val (tl, tr, br, bl) = rect
//
//        val widthA = hypot(br.x - bl.x, br.y - bl.y)
//        val widthB = hypot(tr.x - tl.x, tr.y - tl.y)
//        val maxWidth = max(widthA, widthB).toInt()
//
//        val heightA = hypot(tr.x - br.x, tr.y - br.y)
//        val heightB = hypot(tl.x - bl.x, tl.y - bl.y)
//        val maxHeight = max(heightA, heightB).toInt()
//
//        val srcPts = MatOfPoint2f(tl, tr, br, bl)
//        val dstPts = MatOfPoint2f(
//            Point(0.0, 0.0),
//            Point((maxWidth - 1).toDouble(), 0.0),
//            Point((maxWidth - 1).toDouble(), (maxHeight - 1).toDouble()),
//            Point(0.0, (maxHeight - 1).toDouble())
//        )
//
//        val M = Imgproc.getPerspectiveTransform(srcPts, dstPts)
//        val warped = Mat()
//        Imgproc.warpPerspective(image, warped, M, Size(maxWidth.toDouble(), maxHeight.toDouble()))
//        return warped
//    }

    private fun fourPointWarp(image: Mat, pts: Array<Point>, mode: String = "auto"): Mat {
        val rect = orderQuadClockwise(pts)
        val (tl, tr, br, bl) = rect

        val widthA = hypot(br.x - bl.x, br.y - bl.y)
        val widthB = hypot(tr.x - tl.x, tr.y - tl.y)
        val heightA = hypot(tr.x - br.x, tr.y - br.y)
        val heightB = hypot(tl.x - bl.x, tl.y - bl.y)

        val (targetW, targetH) = when (mode) {
            "a4" -> 1240 to 1754
            else -> ((widthA + widthB) / 2.0).toInt() to ((heightA + heightB) / 2.0).toInt()
        }

        val srcPts = MatOfPoint2f(tl, tr, br, bl)
        val dstPts = MatOfPoint2f(
            Point(0.0, 0.0),
            Point((targetW - 1).toDouble(), 0.0),
            Point((targetW - 1).toDouble(), (targetH - 1).toDouble()),
            Point(0.0, (targetH - 1).toDouble())
        )

        val M = Imgproc.getPerspectiveTransform(srcPts, dstPts)
        val warped = Mat()
        Imgproc.warpPerspective(image, warped, M, Size(targetW.toDouble(), targetH.toDouble()))
        return warped
    }

    private fun enhanceDocument(src: Mat, mode: String): Mat {
        val dst = Mat()
        when (mode) {
            "color" -> {
                src.convertTo(dst, -1, 1.5, 0.0) // contrast gain
                val gray = Mat()
                Imgproc.cvtColor(dst, gray, Imgproc.COLOR_RGBA2GRAY)

                val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
                val claheOut = Mat()
                clahe.apply(gray, claheOut)

                val blur = Mat()
                Imgproc.GaussianBlur(claheOut, blur, Size(0.0, 0.0), 3.0)
                Core.addWeighted(claheOut, 1.5, blur, -0.5, 0.0, dst)

                val mask = Mat()
                Imgproc.adaptiveThreshold(
                    claheOut, mask, 255.0,
                    Imgproc.ADAPTIVE_THRESH_MEAN_C,
                    Imgproc.THRESH_BINARY_INV,
                    31, 5.0
                )
                dst.setTo(Scalar(255.0, 255.0, 255.0))
                src.copyTo(dst, mask)
                claheOut.release()
                blur.release()
                gray.release()
                mask.release()
            }
            "bw" -> {
                Imgproc.cvtColor(src, dst, Imgproc.COLOR_RGBA2GRAY)
                Imgproc.adaptiveThreshold(
                    dst, dst, 255.0,
                    Imgproc.ADAPTIVE_THRESH_MEAN_C,
                    Imgproc.THRESH_BINARY,
                    15, 15.0
                )
            }
            "gray" -> {
                Imgproc.cvtColor(src, dst, Imgproc.COLOR_RGBA2GRAY)
            }
            else -> src.copyTo(dst)
        }
        return dst
    }
}
