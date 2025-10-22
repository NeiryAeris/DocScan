package com.example.imaging_opencv_android

import com.example.domain.ImageRef
import com.example.domain.Imaging
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class OpenCvImaging : Imaging {

    // --- helpers ---

    private fun newRefFor(mat: Mat): ImageRef =
        ImageRef(width = mat.width(), height = mat.height()).also { MatRegistry.put(it, mat) }

    private fun requireMat(ref: ImageRef): Mat = MatRegistry.requireMat(ref)

    private fun toGrayOwned(src: Mat): Mat = when (src.channels()) {
        1 -> src.clone()
        3 -> Mat().also { Imgproc.cvtColor(src, it, Imgproc.COLOR_BGR2GRAY) }
        4 -> Mat().also { Imgproc.cvtColor(src, it, Imgproc.COLOR_BGRA2GRAY) }
        else -> src.clone()
    }

    private fun ensureSizeValid(sz: Size): Size {
        val w = max(1.0, sz.width)
        val h = max(1.0, sz.height)
        return Size(w, h)
    }

    private fun approxPoly(points: MatOfPoint2f, epsRatio: Double = 0.02): MatOfPoint2f {
        val peri = Imgproc.arcLength(points, true)
        val out = MatOfPoint2f()
        Imgproc.approxPolyDP(points, out, epsRatio * peri, true)
        return out
    }

    private fun area(points: MatOfPoint2f): Double {
        val mop = MatOfPoint(*points.toArray().map { Point(it.x, it.y) }.toTypedArray())
        val a = abs(Imgproc.contourArea(mop))
        mop.release()
        return a
    }

    private fun orderQuadClockwise(q: Array<Point>): Array<Point> {
        // sort by y then x to get rough TL/BL then pick TR/BR
        val sorted = q.sortedWith(compareBy<Point> { it.y }.thenBy { it.x })
        val tl = sorted[0]
        val bl = sorted[1]
        val remaining = q.filter { it !== tl && it !== bl }
        // decide TR/BR by x (TR has smaller y distance to TL)
        val (c1, c2) = remaining
        val tr: Point; val br: Point
        if (c1.y < c2.y) { tr = c1; br = c2 } else { tr = c2; br = c1 }
        return arrayOf(tl, tr, br, bl)
    }

    // --- contract I/O ---

    override fun fromBytes(bytes: ByteArray): ImageRef {
        val mat = MatIo.decode(bytes)
        return newRefFor(mat)
    }

    override fun toJpeg(img: ImageRef, quality: Int): ByteArray {
        val m = requireMat(img)
        return MatIo.encodeJpeg(m, quality)
    }

    // --- processing primitives already present in your earlier file ---

    override fun warpPerspective(src: ImageRef, quad: FloatArray): ImageRef {
        require(quad.size == 8) { "quad must be 8 floats (x0,y0,...,x3,y3)" }
        val s = requireMat(src)

        val p0 = Point(quad[0].toDouble(), quad[1].toDouble())
        val p1 = Point(quad[2].toDouble(), quad[3].toDouble())
        val p2 = Point(quad[4].toDouble(), quad[5].toDouble())
        val p3 = Point(quad[6].toDouble(), quad[7].toDouble())

        val widthA  = hypot(p2.x - p3.x, p2.y - p3.y)
        val widthB  = hypot(p1.x - p0.x, p1.y - p0.y)
        val maxW    = max(widthA, widthB).toInt().coerceAtLeast(1)

        val heightA = hypot(p1.x - p2.x, p1.y - p2.y)
        val heightB = hypot(p0.x - p3.x, p0.y - p3.y)
        val maxH    = max(heightA, heightB).toInt().coerceAtLeast(1)

        val srcPts = MatOfPoint2f(p0, p1, p2, p3)
        val dstPts = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(maxW - 1.0, 0.0),
            Point(maxW - 1.0, maxH - 1.0),
            Point(0.0, maxH - 1.0)
        )
        val M = Imgproc.getPerspectiveTransform(srcPts, dstPts)
        val out = Mat()
        try {
            Imgproc.warpPerspective(
                s, out, M, Size(maxW.toDouble(), maxH.toDouble()),
                Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE
            )
        } finally {
            srcPts.release(); dstPts.release(); M.release()
        }
        return newRefFor(out)
    }

    override fun deskewLight(src: ImageRef, maxDeg: Double): ImageRef {
        val s = requireMat(src)
        val gray = toGrayOwned(s)

        val bw = Mat()
        Imgproc.adaptiveThreshold(
            gray, bw, 255.0,
            Imgproc.ADAPTIVE_THRESH_MEAN_C,
            Imgproc.THRESH_BINARY_INV,
            31, 15.0
        )

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(bw, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        val mask = Mat.zeros(bw.size(), CvType.CV_8UC1)
        contours.forEach { c -> Imgproc.drawContours(mask, listOf(c), -1, Scalar(255.0), 1) }

        val nonZero = Mat()
        Imgproc.findNonZero(mask, nonZero)

        contours.forEach { it.release() }
        hierarchy.release()
        mask.release()
        bw.release()
        gray.release()

        if (nonZero.empty()) {
            nonZero.release()
            return newRefFor(s.clone())
        }

        val pts2f = MatOfPoint2f(
            *nonZero.toArray().map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray()
        )
        nonZero.release()

        val rect = Imgproc.minAreaRect(pts2f)
        pts2f.release()

        var angle = rect.angle
        if (angle < -45) angle += 90.0
        if (abs(angle) <= maxDeg) return newRefFor(s.clone())

        val rot = Imgproc.getRotationMatrix2D(rect.center, angle, 1.0)
        val out = Mat()
        try {
            Imgproc.warpAffine(s, out, rot, s.size(), Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE)
        } finally {
            rot.release()
        }
        return newRefFor(out)
    }

    override fun illuminationNormalize(gray: ImageRef, medianK: Int): ImageRef {
        require(medianK >= 3 && medianK % 2 == 1) { "medianK must be odd and >= 3" }
        val g = toGrayOwned(requireMat(gray))
        val bg = Mat()
        Imgproc.medianBlur(g, bg, medianK)
        val norm = Mat()
        Core.divide(g, bg, norm, 255.0)
        g.release(); bg.release()
        return newRefFor(norm)
    }

    override fun denoise(src: ImageRef): ImageRef {
        val s = requireMat(src)
        val out = Mat()
        if (s.channels() == 1) {
            Photo.fastNlMeansDenoising(s, out, 3f, 7, 21)
        } else {
            Photo.fastNlMeansDenoisingColored(s, out, 3f, 7f, 7, 21)
        }
        return newRefFor(out)
    }

    override fun clahe(gray: ImageRef, clip: Double, tiles: Int): ImageRef {
        val g = toGrayOwned(requireMat(gray))
        val clahe = Imgproc.createCLAHE().apply {
            clipLimit = clip
            tileGridSize = Size(tiles.toDouble(), tiles.toDouble())
        }
        val out = Mat()
        clahe.apply(g, out)
        g.release()
        return newRefFor(out)
    }

    override fun unsharp(src: ImageRef, sigma: Double, amount: Double): ImageRef {
        val s = requireMat(src)
        val blur = Mat()
        Imgproc.GaussianBlur(s, blur, Size(0.0, 0.0), sigma)
        val sharp = Mat()
        Core.addWeighted(s, 1.0 + amount, blur, -amount, 0.0, sharp)
        blur.release()
        return newRefFor(sharp)
    }

    override fun toBWAdaptive(src: ImageRef): ImageRef {
        val s = requireMat(src)
        val g = toGrayOwned(s)
        val bw = Mat()
        Imgproc.adaptiveThreshold(
            g, bw, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            31, 10.0
        )
        g.release()
        return newRefFor(bw)
    }

    override fun release(img: ImageRef) {
        MatRegistry.release(img)
    }

    // --- additional members from your interface ---

    override fun resize(src: ImageRef, maxSide: Double): ImageRef {
        val s = requireMat(src)
        val w = s.width().toDouble()
        val h = s.height().toDouble()
        val maxDim = max(w, h)
        if (maxDim <= max(1.0, maxSide)) return newRefFor(s.clone())
        val scale = maxSide / maxDim
        val newW = max(1.0, w * scale)
        val newH = max(1.0, h * scale)
        val out = Mat()
        Imgproc.resize(s, out, Size(newW, newH), 0.0, 0.0, Imgproc.INTER_AREA)
        return newRefFor(out)
    }

    override fun grayscale(src: ImageRef): ImageRef {
        val s = requireMat(src)
        val g = toGrayOwned(s)
        return newRefFor(g)
    }

    override fun blur(src: ImageRef, kernelSize: Size): ImageRef {
        val s = requireMat(src)
        val k = ensureSizeValid(kernelSize)
        val out = Mat()
        Imgproc.GaussianBlur(s, out, k, 0.0)
        return newRefFor(out)
    }

    override fun threshold(src: ImageRef, blur: ImageRef): ImageRef {
        // Common doc-normalize pattern: absdiff(srcGray, blurGray) -> OTSU
        val s = toGrayOwned(requireMat(src))
        val b = toGrayOwned(requireMat(blur))
        val diff = Mat()
        Core.absdiff(s, b, diff)
        val bw = Mat()
        Imgproc.threshold(diff, bw, /*thresh*/0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
        s.release(); b.release(); diff.release()
        return newRefFor(bw)
    }

    override fun canny(src: ImageRef, threshold1: Double, threshold2: Double): ImageRef {
        val s = toGrayOwned(requireMat(src))
        val edges = Mat()
        Imgproc.Canny(s, edges, threshold1, threshold2)
        s.release()
        return newRefFor(edges)
    }

    override fun morphologyEx(src: ImageRef, kernel: ImageRef): ImageRef {
        val s = requireMat(src)
        val k = requireMat(kernel)
        val k8u = Mat()
        // Ensure kernel type & values are valid (binary kernel)
        k.convertTo(k8u, CvType.CV_8U)
        val out = Mat()
        Imgproc.morphologyEx(s, out, Imgproc.MORPH_CLOSE, k8u) // CLOSE is good for doc edges
        k8u.release()
        return newRefFor(out)
    }

    override fun findContours(src: ImageRef): List<List<Point>> {
        val s = requireMat(src)
        val bin = if (s.channels() == 1) s.clone() else toGrayOwned(s)
        // Ensure binary-ish input
        val tmp = Mat()
        Imgproc.threshold(bin, tmp, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
        bin.release()
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(tmp, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        tmp.release(); hierarchy.release()
        val out = contours.map { it.toList().also { _ -> it.release() } }
        return out
    }

    override fun fourPointWarp(src: ImageRef, quad: Array<Point>): ImageRef {
        require(quad.size == 4) { "quad must have 4 Points" }
        val ordered = orderQuadClockwise(quad)
        val arr = floatArrayOf(
            ordered[0].x.toFloat(), ordered[0].y.toFloat(),
            ordered[1].x.toFloat(), ordered[1].y.toFloat(),
            ordered[2].x.toFloat(), ordered[2].y.toFloat(),
            ordered[3].x.toFloat(), ordered[3].y.toFloat()
        )
        return warpPerspective(src, arr)
    }

    override fun detectDocumentQuad(src: ImageRef): FloatArray? {
        val s = requireMat(src)
        val g = toGrayOwned(s)
        val blur = Mat()
        Imgproc.GaussianBlur(g, blur, Size(5.0, 5.0), 0.0)
        val edges = Mat()
        Imgproc.Canny(blur, edges, 50.0, 150.0)
        // close gaps
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        val closed = Mat()
        Imgproc.morphologyEx(edges, closed, Imgproc.MORPH_CLOSE, kernel)
        edges.release(); blur.release(); kernel.release()

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(closed, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        closed.release(); hierarchy.release(); g.release()

        // pick the biggest 4-point convex polygon
        var best: MatOfPoint2f? = null
        var bestArea = 0.0
        contours.forEach { c ->
            val c2f = MatOfPoint2f(*c.toArray().map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray())
            val approx = approxPoly(c2f)
            if (approx.total().toInt() == 4) {
                val ar = area(approx)
                if (ar > bestArea && Imgproc.isContourConvex(MatOfPoint(*approx.toArray()))) {
                    bestArea = ar
                    best?.release()
                    best = approx
                } else {
                    approx.release()
                }
            } else {
                approx.release()
            }
            c2f.release()
            c.release()
        }

        val quadFloats = best?.let {
            val pts = it.toArray()
            it.release()
            val ordered = orderQuadClockwise(arrayOf(pts[0], pts[1], pts[2], pts[3]))
            floatArrayOf(
                ordered[0].x.toFloat(), ordered[0].y.toFloat(),
                ordered[1].x.toFloat(), ordered[1].y.toFloat(),
                ordered[2].x.toFloat(), ordered[2].y.toFloat(),
                ordered[3].x.toFloat(), ordered[3].y.toFloat()
            )
        }
        return quadFloats
    }

    override fun enhanceDocument(src: ImageRef, mode: String): ImageRef {
        // Simple preset modes; tune as needed.
        return when (mode.lowercase()) {
            "bw", "b&w", "binary" -> {
                val norm = illuminationNormalize(grayscale(src), 81)
                val out = toBWAdaptive(norm)
                release(norm)
                out
            }
            "clean", "color" -> {
                val den = denoise(src)
                val cla = clahe(grayscale(den), 3.0, 8)
                val shp = unsharp(cla, 1.2, 0.7)
                release(den); release(cla)
                shp
            }
            else -> {
                // Fallback: light enhance
                val g = grayscale(src)
                val cla = clahe(g, 3.0, 8)
                release(g)
                cla
            }
        }
    }
}
