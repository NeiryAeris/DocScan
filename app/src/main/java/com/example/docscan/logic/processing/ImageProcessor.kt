package com.example.docscan.logic.processing

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Image processing utilities (OpenCV).
 */
object ImageProcessor {

    /**
     * Draws a green contour around the largest 4-point polygon that looks like a "paper".
     * Returns a new bitmap with the contour overlaid.
     *
     * Strategy:
     * 1) (Optionally) downscale for speed
     * 2) grayscale -> blur -> Canny
     * 3) findContours -> sort by area desc
     * 4) approxPolyDP -> take first polygon with 4 points and "big enough"
     * 5) scale contour back to original coords and draw it
     */
    fun drawPaperContour(
        orig: Bitmap,
        maxProcessSize: Int = 900,         // downscale longer side to this for speed/robustness
        minAreaRatio: Double = 0.10,       // min area (vs image) to consider as paper (10%)
        epsilonRatio: Double = 0.02        // polygon approximation: 2% of perimeter
    ): Bitmap {
        // --- ensure format OpenCV likes
        val srcBmp = if (orig.config != Bitmap.Config.ARGB_8888)
            orig.copy(Bitmap.Config.ARGB_8888, false) else orig

        // --- build Mats
        val src = Mat(srcBmp.height, srcBmp.width, CvType.CV_8UC4)
        Utils.bitmapToMat(srcBmp, src)

        // --- optional downscale for processing
        val (proc, scale) = resizeForProcessing(src, maxProcessSize)

        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        val hierarchy = Mat()
        val contours = mutableListOf<MatOfPoint>()

        var overlay: Bitmap

        try {
            // 1) grayscale -> blur -> edges
            Imgproc.cvtColor(proc, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 75.0, 200.0)

            // 2) find contours
            Imgproc.findContours(
                edges, contours, hierarchy,
                Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE
            )

            // 3) sort by area (desc)
            contours.sortByDescending { Imgproc.contourArea(it) }

            // 4) choose first "paper-like" (4 points, convex, big enough)
            val imgArea = (proc.rows() * proc.cols()).toDouble()
            var bestQuad: MatOfPoint2f? = null

            for (c in contours) {
                val area = Imgproc.contourArea(c)
                if (area < imgArea * minAreaRatio) continue

                val c2f = MatOfPoint2f(*c.toArray())
                val peri = Imgproc.arcLength(c2f, true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(c2f, approx, epsilonRatio * peri, true)

                if (approx.total().toInt() == 4 && Imgproc.isContourConvex(MatOfPoint(*approx.toArray().map { Point(it.x, it.y) }.toTypedArray()))) {
                    bestQuad = approx
                    break
                }
                approx.release()
                c2f.release()
            }

            // 5) draw on a copy of the original (full-res) image
            val drawMat = src.clone()

            if (bestQuad != null) {
                // scale points back to full resolution
                val scaledQuad = MatOfPoint()
                val pts = bestQuad!!.toArray().map {
                    Point(it.x / scale, it.y / scale)
                }.toTypedArray()
                scaledQuad.fromArray(*pts)

                // reorder to consistent order (optional; looks nicer)
                val ordered = orderQuadClockwise(pts)
                val poly = MatOfPoint(*ordered)

                val listOfPoly = ArrayList<MatOfPoint>().apply { add(poly) }
                Imgproc.polylines(
                    drawMat,
                    listOfPoly,
                    true,
                    Scalar(0.0, 255.0, 0.0, 255.0), // green
                    5
                )

                poly.release()
                scaledQuad.release()
                bestQuad.release()
            }

            // back to Bitmap
            overlay = createBitmap(drawMat.cols(), drawMat.rows())
            Utils.matToBitmap(drawMat, overlay)
            drawMat.release()
        } finally {
            // free native memory
            src.release()
            if (proc !== src) proc.release()
            gray.release()
            blurred.release()
            edges.release()
            hierarchy.release()
            contours.forEach { it.release() }
        }

        return overlay
    }

    /** Resize longer side to maxProcessSize; return (matToUse, scaleFactor used to reach proc from src). */
    private fun resizeForProcessing(src: Mat, maxProcessSize: Int): Pair<Mat, Double> {
        val h = src.rows()
        val w = src.cols()
        val longSide = max(w, h).toDouble()
        if (longSide <= maxProcessSize) return src to 1.0

        val scale = maxProcessSize / longSide
        val newW = (w * scale).toInt().coerceAtLeast(1)
        val newH = (h * scale).toInt().coerceAtLeast(1)
        val dst = Mat()
        Imgproc.resize(src, dst, Size(newW.toDouble(), newH.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
        return dst to scale
    }

    /** Order quad points clockwise starting from top-left (roughly). */
    private fun orderQuadClockwise(pts: Array<Point>): Array<Point> {
        // top-left = smallest (x+y), bottom-right = largest (x+y)
        val sumSorted = pts.sortedBy { it.x + it.y }
        val tl = sumSorted.first()
        val br = sumSorted.last()

        // top-right = largest (x - y), bottom-left = smallest (x - y)
        val diffSorted = pts.sortedBy { it.x - it.y }
        val bl = diffSorted.first()
        val tr = diffSorted.last()

        // Ensure all distinct; fallback if any duplicates (rare)
        val uniq = listOf(tl, tr, br, bl).distinct()
        return if (uniq.size == 4) arrayOf(tl, tr, br, bl) else pts
    }
}