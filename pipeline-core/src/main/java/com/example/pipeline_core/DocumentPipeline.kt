package com.example.pipeline

import org.opencv.core.*
import org.opencv.imgproc.CLAHE
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot
import kotlin.math.max

data class ProcessResult(
    val overlay: Mat,            // original with green quad
    val quad: Array<Point>?,     // detected quad points (orig scale)
    val warped: Mat?,            // perspective-corrected doc (RGBA)
    val enhanced: Mat?           // enhanced output per mode
)

object DocumentPipeline {

    /** Load OpenCV natives for desktop JVM (openpnp artifact). */
    fun init() {
        try {
            // This class is packaged inside org.openpnp:opencv 4.x
            val cls = Class.forName("nu.pattern.OpenCV")
            val m = cls.getMethod("loadLocally")
            m.invoke(null)                              // extracts & loads the matching native lib
        } catch (_: Throwable) {
            // Fallback (in case the loader class isn’t present for some reason)
            System.loadLibrary(org.opencv.core.Core.NATIVE_LIBRARY_NAME)
        }
    }

    /** Port of your Android pipeline.
     *  Expect **RGBA** input (CV_8UC4) for desktop<->Android parity.
     *  @param mode "auto" (no extra enhance), "color", "bw", or "gray"
     */
    fun process(srcRgba: Mat, mode: String = "auto"): ProcessResult {
        require(srcRgba.type() == CvType.CV_8UC4) { "Provide RGBA Mat (CV_8UC4)" }

        val src = srcRgba

        // --- Resize for detection speed (same as your script) ---
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

        try {
            // Preprocess
            Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

            // Otsu
            Imgproc.threshold(blurred, binary, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)

            // Canny
            Imgproc.Canny(binary, edges, 50.0, 150.0)

            // Morph close
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0))
            Imgproc.morphologyEx(edges, closed, Imgproc.MORPH_CLOSE, kernel)

            // Contours
            val hierarchy = Mat()
            Imgproc.findContours(closed, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            contours.sortByDescending { Imgproc.contourArea(it) }

            val imgArea = (small.rows() * small.cols()).toDouble()
            val maxAreaAllowed = imgArea * 0.9

            // Try top contours
            for (c in contours.take(15)) {
                val area = Imgproc.contourArea(c)
                if (area > maxAreaAllowed) continue

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

            // Fallback to min-area rectangle
            if (quad == null && contours.isNotEmpty()) {
                val rect = Imgproc.minAreaRect(MatOfPoint2f(*contours[0].toArray()))
                val boxPts = arrayOf(Point(), Point(), Point(), Point())
                rect.points(boxPts)
                quad = boxPts.map { Point(it.x / scaleFactor, it.y / scaleFactor) }.toTypedArray()
                warped = fourPointWarp(src, quad!!)
            }

            // Enhancement
            if (warped != null) {
                warpedEnhanced = enhanceDocument(warped!!, mode)
            }

            // Overlay
            val overlay = src.clone()
            quad?.let {
                Imgproc.polylines(overlay, listOf(MatOfPoint(*it)), true, Scalar(0.0, 255.0, 0.0, 255.0), 6)
            }

            return ProcessResult(overlay, quad, warped, warpedEnhanced)
        } finally {
            // release temps
            small.release(); gray.release(); blurred.release(); binary.release()
            edges.release(); closed.release(); contours.forEach { it.release() }
        }
    }

    // ---- Enhancement filters (ported) ----
    private fun enhanceDocument(src: Mat, mode: String): Mat {
        val dst = Mat()
        when (mode) {
            "color" -> {
                val contrast = Mat()
                src.convertTo(contrast, -1, 1.3, 0.0)

                val gray = Mat()
                Imgproc.cvtColor(contrast, gray, Imgproc.COLOR_RGBA2GRAY)

                val clahe: CLAHE = Imgproc.createCLAHE(3.0, Size(8.0, 8.0))
                val claheOut = Mat()
                clahe.apply(gray, claheOut)

                val denoised = Mat()
                Imgproc.bilateralFilter(claheOut, denoised, 7, 50.0, 50.0)

                val sharpened = Mat()
                Core.addWeighted(denoised, 1.4, claheOut, -0.4, 0.0, sharpened)

                val mask = Mat()
                Imgproc.adaptiveThreshold(
                    sharpened, mask, 255.0,
                    Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV,
                    55, 25.0
                )

                val k = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(2.0, 2.0))
                Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, k)

                // Copy to white background with alpha=255 for RGBA
                val result = Mat(src.size(), src.type(), Scalar(255.0, 255.0, 255.0, 255.0))
                src.copyTo(result, mask)
                result.copyTo(dst)

                listOf(contrast, gray, claheOut, denoised, sharpened, mask, result).forEach { it.release() }
            }

            "bw" -> {
                val gray = Mat()
                Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
                Imgproc.adaptiveThreshold(
                    gray, dst, 255.0,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY,
                    25, 10.0
                )
                // keep RGBA out for parity
                val rgba = Mat()
                Imgproc.cvtColor(dst, rgba, Imgproc.COLOR_GRAY2RGBA)
                rgba.copyTo(dst)
                gray.release(); rgba.release()
            }

            "gray" -> {
                Imgproc.cvtColor(src, dst, Imgproc.COLOR_RGBA2GRAY)
                val rgba = Mat()
                Imgproc.cvtColor(dst, rgba, Imgproc.COLOR_GRAY2RGBA)
                rgba.copyTo(dst)
                rgba.release()
            }

            else -> src.copyTo(dst) // "auto" or unknown -> no extra enhance
        }
        return dst
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

        val targetW: Int
        val targetH: Int
        when (mode) {
            "a4" -> { targetW = 1240; targetH = 1754 }  // ~A4 @ ~150dpi
            else -> {
                targetW = ((widthA + widthB) / 2.0).toInt().coerceAtLeast(1)
                targetH = ((heightA + heightB) / 2.0).toInt().coerceAtLeast(1)
            }
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
