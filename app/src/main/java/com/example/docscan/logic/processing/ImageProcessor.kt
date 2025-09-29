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
        val quad: Array<Point>?,   // detected quad points
        val warped: Mat?,          // warped Mat (scan result)
        val file: File?            // saved JPEG (for PDF use)
    )

    fun processDocument(
        orig: Bitmap,
        outFile: File? = null
    ): Result {
        // Ensure ARGB_8888
        val srcBmp = if (orig.config != Bitmap.Config.ARGB_8888)
            orig.copy(Bitmap.Config.ARGB_8888, false) else orig

        val src = Mat(srcBmp.height, srcBmp.width, CvType.CV_8UC4)
        Utils.bitmapToMat(srcBmp, src)

        // --- Resize like Python ---
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
                Imgproc.approxPolyDP(c2f, approx, 0.08 * peri, true) // looser like Python

                val pts = approx.toArray()
                if (pts.size == 4) {
                    // scale back to original coords
                    quad = pts.map { Point(it.x / scaleFactor, it.y / scaleFactor) }.toTypedArray()
                    warped = fourPointWarp(src, quad!!)
                    break
                }
            }

            // --- Fallback with minAreaRect ---
            if (quad == null && contours.isNotEmpty()) {
                val rect = Imgproc.minAreaRect(MatOfPoint2f(*contours[0].toArray()))
                val boxPts = arrayOf(Point(), Point(), Point(), Point())
                rect.points(boxPts) // fills array in-place
                quad = boxPts.map { Point(it.x / scaleFactor, it.y / scaleFactor) }.toTypedArray()
                warped = fourPointWarp(src, quad!!)
            }

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
            if (outFile != null && warped != null) {
                val warpedBmp = createBitmap(warped.cols(), warped.rows())
                Utils.matToBitmap(warped, warpedBmp)
                FileOutputStream(outFile).use { fos ->
                    warpedBmp.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                }
                savedFile = outFile
            }

            return Result(outBmp, quad, warped, savedFile)

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

    private fun fourPointWarp(image: Mat, pts: Array<Point>): Mat {
        val rect = orderQuadClockwise(pts)
        val (tl, tr, br, bl) = rect

        val widthA = hypot(br.x - bl.x, br.y - bl.y)
        val widthB = hypot(tr.x - tl.x, tr.y - tl.y)
        val maxWidth = max(widthA, widthB).toInt()

        val heightA = hypot(tr.x - br.x, tr.y - br.y)
        val heightB = hypot(tl.x - bl.x, tl.y - bl.y)
        val maxHeight = max(heightA, heightB).toInt()

        val srcPts = MatOfPoint2f(tl, tr, br, bl)
        val dstPts = MatOfPoint2f(
            Point(0.0, 0.0),
            Point((maxWidth - 1).toDouble(), 0.0),
            Point((maxWidth - 1).toDouble(), (maxHeight - 1).toDouble()),
            Point(0.0, (maxHeight - 1).toDouble())
        )

        val M = Imgproc.getPerspectiveTransform(srcPts, dstPts)
        val warped = Mat()
        Imgproc.warpPerspective(image, warped, M, Size(maxWidth.toDouble(), maxHeight.toDouble()))
        return warped
    }
}
