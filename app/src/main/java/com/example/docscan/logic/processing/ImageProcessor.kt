package com.example.docscan.logic.processing

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlin.math.max

object ImageProcessor {

    data class Result(
        val bitmap: Bitmap,        // preview with contour
        val quad: Array<Point>?,   // detected quad points
        val warped: Mat?,          // raw warped document
        val enhanced: Mat?,        // enhanced final image
        val file: File?            // saved JPEG (enhanced)
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

        // --- Resize for detection speed ---
        val maxSide = 1000.0
        val h = src.rows()
        val w = src.cols()
        val scaleFactor = if (max(w, h) > maxSide) maxSide / max(w, h) else 1.0
        val small = Mat()
        if (scaleFactor != 1.0)
            Imgproc.resize(src, small, Size(w * scaleFactor, h * scaleFactor))
        else
            src.copyTo(small)

        val gray = Mat()
        val blurred = Mat()
        val binary = Mat()
        val edges = Mat()
        val closed = Mat()
        val contours = mutableListOf<MatOfPoint>()

        var quad: Array<Point>? = null
        var warped: Mat? = null
        var warpedEnhanced: Mat? = null
        val outBmp: Bitmap

        try {
            // Preprocess
            Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

            // Otsu threshold
            Imgproc.threshold(
                blurred, binary, 0.0, 255.0,
                Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU
            )

            // Canny edges
            Imgproc.Canny(binary, edges, 50.0, 150.0)

            // Morphological close to fill gaps
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0))
            Imgproc.morphologyEx(edges, closed, Imgproc.MORPH_CLOSE, kernel)

            // Find contours
            val hierarchy = Mat()
            Imgproc.findContours(closed, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            contours.sortByDescending { Imgproc.contourArea(it) }

            val imgArea = (small.rows() * small.cols()).toDouble()
            val maxArea = imgArea * 0.9

            // Try top contours
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

            // Fallback to rectangle if no quad
            if (quad == null && contours.isNotEmpty()) {
                val rect = Imgproc.minAreaRect(MatOfPoint2f(*contours[0].toArray()))
                val boxPts = arrayOf(Point(), Point(), Point(), Point())
                rect.points(boxPts)
                quad = boxPts.map { Point(it.x / scaleFactor, it.y / scaleFactor) }.toTypedArray()
                warped = fourPointWarp(src, quad!!)
            }

            // ======================
            // 📈 Post-warp enhancement
            // ======================
            if (warped != null) {
                val enhanced = Mat()
                Imgproc.cvtColor(warped, enhanced, Imgproc.COLOR_BGR2GRAY)

                // 1️⃣ Adaptive histogram equalization (CLAHE)
                val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
                clahe.apply(enhanced, enhanced)

                // 2️⃣ Denoise + sharpen
                val smooth = Mat()
                Imgproc.bilateralFilter(enhanced, smooth, 9, 75.0, 75.0)
                Core.addWeighted(enhanced, 1.5, smooth, -0.5, 0.0, enhanced)

                // 3️⃣ Adaptive thresholding for clear text
                val finalEnhanced = Mat()
                Imgproc.adaptiveThreshold(
                    enhanced,
                    finalEnhanced,
                    255.0,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY,
                    15,
                    11.0
                )

                // 4️⃣ Morphological cleanup
                val cleanKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(2.0, 2.0))
                Imgproc.morphologyEx(finalEnhanced, finalEnhanced, Imgproc.MORPH_OPEN, cleanKernel)

                warpedEnhanced = finalEnhanced

                // Save to disk if required
                if (outFile != null) {
                    val enhancedBitmap = Bitmap.createBitmap(finalEnhanced.cols(), finalEnhanced.rows(), Bitmap.Config.ARGB_8888)
                    Utils.matToBitmap(finalEnhanced, enhancedBitmap)
                    FileOutputStream(outFile).use { fos ->
                        enhancedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                    }
                }
            }

            // Draw detected contour overlay for preview
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

            return Result(outBmp, quad, warped, warpedEnhanced, outFile)

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
}
