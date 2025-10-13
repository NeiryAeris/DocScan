package com.example.pipeline

import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.imgproc.CLAHE

data class ProcessResult(
    val overlay: Mat,
    val quad: Array<Point>?,
    val warped: Mat?,
    val enhanced: Mat?
)

object DocumentPipeline {

    /** Load OpenCV natives in the desktop JVM. Works with org.openpnp:opencv:4.9.0-0 */
    fun init() {
        // Try both strategies so it works regardless of packager:
        try {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME) // e.g., "opencv_java490"
        } catch (_: Throwable) {
            // Fallback to nu.pattern loader if present inside the artifact
            try {
                val cls = Class.forName("nu.pattern.OpenCV")
                val m = cls.getMethod("loadLocally")
                m.invoke(null)
            } catch (_: Throwable) {
                // last resort: ignore; if loading failed, calls will throw UnsatisfiedLinkError later
            }
        }
    }

    /** Expect RGBA Mat (CV_8UC4) on BOTH desktop and Android for parity */
    fun process(srcRgba: Mat): ProcessResult {
        require(srcRgba.type() == CvType.CV_8UC4) { "Provide RGBA Mat (CV_8UC4)" }

        // 1) Gray + blur + edges
        val gray = Mat()
        Imgproc.cvtColor(srcRgba, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
        val edges = Mat()
        Imgproc.Canny(gray, edges, 50.0, 150.0)

        // 2) Find largest quad
        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        contours.sortByDescending { Imgproc.contourArea(it) }

        var quad: Array<Point>? = null
        val approx = MatOfPoint2f()
        for (c in contours) {
            val c2f = MatOfPoint2f(*c.toArray())
            val peri = Imgproc.arcLength(c2f, true)
            Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
            val poly = approx.toArray()
            if (poly.size == 4 && Imgproc.contourArea(MatOfPoint(*poly)) > 10_000) {
                quad = poly
                break
            }
        }

        // 3) Perspective warp
        var warped: Mat? = null
        quad?.let {
            val ordered = order(it)
            val (tl, tr, br, bl) = ordered
            fun d(a: Point, b: Point) = Math.hypot(a.x - b.x, a.y - b.y)
            val w = maxOf(d(tl, tr), d(bl, br))
            val h = maxOf(d(tl, bl), d(tr, br))

            val M = Imgproc.getPerspectiveTransform(
                MatOfPoint2f(*ordered),
                MatOfPoint2f(
                    Point(0.0, 0.0), Point(w - 1, 0.0),
                    Point(w - 1, h - 1), Point(0.0, h - 1)
                )
            )
            val size = Size(w, h)
            warped = Mat(size, srcRgba.type())
            Imgproc.warpPerspective(srcRgba, warped, M, size, Imgproc.INTER_LINEAR)
        }

        // 4) Enhancement (CLAHE + unsharp)
        var enhanced: Mat? = null
        warped?.let { w ->
            val g = Mat()
            Imgproc.cvtColor(w, g, Imgproc.COLOR_RGBA2GRAY)

            // Prefer CLAHE; fallback to equalizeHist if CLAHE isn't available
            val eq = Mat()
            try {
                val clahe: CLAHE = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
                clahe.apply(g, eq)
            } catch (_: Throwable) {
                // Some packaged builds may miss CLAHE; this keeps things running
                Imgproc.equalizeHist(g, eq)
            }

            val blur = Mat()
            Imgproc.GaussianBlur(eq, blur, Size(0.0, 0.0), 1.5)
            val sharp = Mat()
            Core.addWeighted(eq, 1.5, blur, -0.5, 0.0, sharp)

            enhanced = Mat()
            Imgproc.cvtColor(sharp, enhanced, Imgproc.COLOR_GRAY2RGBA)
        }

        // 5) Draw overlay
        val overlay = srcRgba.clone()
        quad?.let {
            for (i in 0 until 4) {
                val p1 = it[i]; val p2 = it[(i + 1) % 4]
                Imgproc.line(overlay, p1, p2, Scalar(0.0, 255.0, 0.0, 255.0), 3)
            }
        }

        return ProcessResult(overlay, quad, warped, enhanced)
    }

    // Order TL, TR, BR, BL
    private fun order(q: Array<Point>): Array<Point> {
        val sum = q.map { it.x + it.y }
        val diff = q.map { it.y - it.x }
        val tl = q[sum.indexOf(sum.minOrNull()!!)]
        val br = q[sum.indexOf(sum.maxOrNull()!!)]
        val tr = q[diff.indexOf(diff.minOrNull()!!)]
        val bl = q[diff.indexOf(diff.maxOrNull()!!)]
        return arrayOf(tl, tr, br, bl)
    }
}
