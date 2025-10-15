package com.example.pipeline

import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.CLAHE
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow

data class ProcessResult(
    val overlay: Mat,            // original with green quad
    val quad: Array<Point>?,     // detected quad points (orig scale)
    val warped: Mat?,            // perspective-corrected doc (RGBA)
    val enhanced: Mat?           // enhanced output per mode
)

data class Params(
    // ---- color mode knobs ----
    val colorContrastAlpha: Double = 1.3,               // convertTo alpha
    val claheClipLimit: Double = 3.0,                   // Imgproc.createCLAHE clip limit
    val claheTileGrid: Size = Size(8.0, 8.0),           // CLAHE tile size
    val bilateralDiameter: Int = 7,                     // bilateral filter d
    val bilateralSigmaColor: Double = 50.0,
    val bilateralSigmaSpace: Double = 50.0,
    val sharpenAlpha: Double = 1.4,                     // addWeighted(denoised, alpha, claheOut, -beta)
    val sharpenBeta: Double = 0.4,
    val maskBlockFactor: Double = 55.0 / 1200.0,        // adaptiveThreshold block size scales with image minDim
    val maskC: Double = 25.0,
    val maskMorphKernel: Int = 2,                       // close kernel size (pixels)

    // ---- bw mode knobs ----
    val bwBlockFactor: Double = 25.0 / 1200.0,
    val bwC: Double = 10.0,

    // ---- sketch mode knobs ----
    val sketchCannyLo: Double = 60.0,
    val sketchCannyHi: Double = 180.0,
    val sketchDilate: Int = 1
)

private fun oddAtLeast(n: Int, minOdd: Int = 3) = if (n % 2 == 1) maxOf(n, minOdd) else maxOf(n + 1, minOdd)
private fun kernel(size: Int) = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(size.toDouble(), size.toDouble()))

object DocumentPipeline {

    /** Load OpenCV natives for desktop JVM (openpnp artifact). */
    fun init() {
        // If we're on Android, OpenCV is loaded via OpenCVLoader.initDebug() in App.kt
        val isAndroid = try { Class.forName("android.os.Build"); true } catch (_: Throwable) { false }
        if (isAndroid) return

        // Desktop/JVM: load natives from OpenPnP
        try {
            val cls = Class.forName("nu.pattern.OpenCV")
            val m = cls.getMethod("loadLocally")
            m.invoke(null)
        } catch (_: Throwable) {
            try {
                System.loadLibrary(org.opencv.core.Core.NATIVE_LIBRARY_NAME)
            } catch (_: Throwable) {
                // ignore: tests will fail loudly if loading actually didn’t work
            }
        }
    }

    /** Port of your Android pipeline.
     *  Expect **RGBA** input (CV_8UC4) for desktop<->Android parity.
     *  @param mode "auto" (no extra enhance), "color", "bw", or "gray"
     */
    fun process(srcRgba: Mat, mode: String = "auto", params: Params = Params()): ProcessResult {
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
    private fun enhanceDocument(src: Mat, mode: String, params: Params = Params()): Mat {
        val dst = Mat()

        when (mode) {
            "color" -> {
                val contrast = Mat()
                src.convertTo(contrast, -1, params.colorContrastAlpha, 0.0)

                val gray = Mat()
                Imgproc.cvtColor(contrast, gray, Imgproc.COLOR_RGBA2GRAY)

                val clahe: CLAHE = Imgproc.createCLAHE(params.claheClipLimit, params.claheTileGrid)
                val claheOut = Mat()
                clahe.apply(gray, claheOut)

                val denoised = Mat()
                Imgproc.bilateralFilter(
                    claheOut, denoised,
                    params.bilateralDiameter,
                    params.bilateralSigmaColor,
                    params.bilateralSigmaSpace
                )

                val sharpened = Mat()
                Core.addWeighted(denoised, params.sharpenAlpha, claheOut, -params.sharpenBeta, 0.0, sharpened)

                // Text mask via adaptive threshold (scale block size to image size)
                val minDim = minOf(src.rows(), src.cols())
                val maskBlock = oddAtLeast((params.maskBlockFactor * minDim).toInt(), 15) // keep odd & >= 15
                val mask = Mat()
                Imgproc.adaptiveThreshold(
                    sharpened, mask, 255.0,
                    Imgproc.ADAPTIVE_THRESH_MEAN_C,
                    Imgproc.THRESH_BINARY_INV,
                    maskBlock, params.maskC
                )

                // Close small gaps in mask
                val k = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(2.0, 2.0))
                Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, k)

                // Copy to white background with alpha=255 for RGBA
                val result = Mat(src.size(), src.type(), Scalar(255.0, 255.0, 255.0, 255.0))
                src.copyTo(result, mask)
                result.copyTo(dst)

                listOf(contrast, gray, claheOut, denoised, sharpened, mask, result).forEach { it.release() }
            }

            "bw" -> {
                // Strong black/white
                val gray = Mat()
                Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
                val minDim = minOf(src.rows(), src.cols())
                val block = oddAtLeast((params.bwBlockFactor * minDim).toInt(), 15)
                Imgproc.adaptiveThreshold(
                    gray, dst, 255.0,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY,
                    block, params.bwC
                )
                gray.release()
            }

            "sketch" -> {
                // Bước 1️⃣: Grayscale
                val gray = Mat()
                Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
                Imgcodecs.imwrite("build/test-output/step1_gray.jpg", gray)

                // Bước 2️⃣: Preserve vùng cực trị (clip shadow / highlight)
                val clipped = Mat()
                Core.min(gray, Scalar(245.0), clipped)
                Core.max(clipped, Scalar(10.0), clipped)
                Imgcodecs.imwrite("build/test-output/step2_clipped.jpg", clipped)

                // Bước 3️⃣: Background normalization (khử bóng)
                val bg = Mat()
                Imgproc.medianBlur(clipped, bg, 51)
                val norm = Mat()
                Core.divide(clipped, bg, norm, 255.0)
                Imgcodecs.imwrite("build/test-output/step3_normalized.jpg", norm)

                // Bước 4️⃣: Căng dải sáng
                val stretched = Mat()
                Core.normalize(norm, stretched, 0.0, 255.0, Core.NORM_MINMAX)
                Imgcodecs.imwrite("build/test-output/step4_stretched.jpg", stretched)

                // ===============================
                // (A) NHÁNH MASK – BINARIZE SỚM
                // ===============================
                val mask = Mat()
                Imgproc.adaptiveThreshold(
                    stretched, mask, 255.0,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY_INV, 25, 10.0
                )
                val mk = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(2.0, 2.0))
                Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, mk)
                Imgcodecs.imwrite("build/test-output/step5_mask_early.jpg", mask)

                // ===============================
                // (B) NHÁNH TONE – ENHANCE RIÊNG
                // ===============================
                val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
                val claheOut = Mat()
                clahe.apply(stretched, claheOut)
                Imgcodecs.imwrite("build/test-output/step6_clahe.jpg", claheOut)

                val blurred = Mat()
                Imgproc.GaussianBlur(claheOut, blurred, Size(5.0, 5.0), 0.0)

                val sharpened = Mat()
                Core.addWeighted(claheOut, 1.6, blurred, -0.6, 0.0, sharpened)
                Imgcodecs.imwrite("build/test-output/step7_sharpened.jpg", sharpened)

                // Gamma correction (LUT)
                val gammaCorrected = Mat()
                val lutArray = ByteArray(256) { i ->
                    ((i / 255.0).pow(0.85) * 255.0).toInt().coerceIn(0, 255).toByte()
                }
                val gammaLut = Mat(1, 256, CvType.CV_8U)
                gammaLut.put(0, 0, lutArray)
                Core.LUT(sharpened, gammaLut, gammaCorrected)
                Imgcodecs.imwrite("build/test-output/step8_gamma.jpg", gammaCorrected)
                gammaLut.release()

                // ===============================
                // (C) COMPOSE – ÁP MASK SỚM LÊN NHÁNH TONE
                // ===============================
                val final = Mat(gammaCorrected.size(), gammaCorrected.type(), Scalar(255.0))
                gammaCorrected.copyTo(final, mask)
                Imgcodecs.imwrite("build/test-output/step9_hybrid_result.jpg", final)

                final.copyTo(dst)

                listOf(
                    gray, clipped, bg, norm, stretched,
                    mask, claheOut, blurred, sharpened, gammaCorrected, final
                ).forEach { it.release() }
            }


            else -> src.copyTo(dst) // "auto" falls back for now; we’ll improve auto later
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
