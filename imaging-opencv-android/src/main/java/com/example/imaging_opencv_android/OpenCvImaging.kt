package com.example.imaging_opencv_android

import com.example.domain.ImageRef
import com.example.domain.Imaging
import com.example.domain.Point
import com.example.domain.Size
import org.opencv.core.*
import org.opencv.core.Core.findNonZero
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

class OpenCvImaging : Imaging {

    // ---------- helpers ----------

    private fun newRefFor(m: Mat): ImageRef =
        ImageRef(width = m.width(), height = m.height()).also { MatRegistry.put(it, m) }

    private fun requireMat(ref: ImageRef): Mat = MatRegistry.requireMat(ref)

    private fun toGrayOwned(src: Mat): Mat = when (src.channels()) {
        1 -> src.clone()
        3 -> Mat().also { Imgproc.cvtColor(src, it, Imgproc.COLOR_BGR2GRAY) }
        4 -> Mat().also { Imgproc.cvtColor(src, it, Imgproc.COLOR_BGRA2GRAY) }
        else -> src.clone()
    }

    // domain <-> opencv conversions
    private fun Point.toCv() = org.opencv.core.Point(this.x.toDouble(), this.y.toDouble())
    private fun org.opencv.core.Point.toDomain() = Point(this.x, this.y)
    private fun Size.toCv() = org.opencv.core.Size(this.width.toDouble(), this.height.toDouble())

    private fun ensureOddPositive(v: Int): Int = if (v <= 0) 1 else if (v % 2 == 1) v else v + 1

    private fun approxPoly(points: MatOfPoint2f, epsRatio: Double = 0.02): MatOfPoint2f {
        val peri = Imgproc.arcLength(points, true)
        val out = MatOfPoint2f()
        Imgproc.approxPolyDP(points, out, epsRatio * peri, true)
        return out
    }

    private fun contourAreaAbs(points: MatOfPoint2f): Double {
        val mop = MatOfPoint(*points.toArray().map { org.opencv.core.Point(it.x, it.y) }.toTypedArray())
        val a = kotlin.math.abs(Imgproc.contourArea(mop))
        mop.release()
        return a
    }

    private fun orderQuadClockwise(q: Array<Point>): Array<Point> {
        val tl = q.minByOrNull { it.x + it.y }!!
        val br = q.maxByOrNull { it.x + it.y }!!
        val tr = q.minByOrNull { it.x - it.y }!!
        val bl = q.maxByOrNull { it.x - it.y }!!
        return arrayOf(tl, tr, br, bl)
    }

    // ---------- contract I/O ----------

    override fun fromBytes(jpeg: ByteArray): ImageRef {
        val m = MatIo.decode(jpeg)
        return newRefFor(m)
    }

    override fun toJpeg(img: ImageRef, quality: Int): ByteArray {
        val m = requireMat(img)
        return MatIo.encodeJpeg(m, quality)
    }

    // ---------- required ops ----------

    override fun detectDocumentQuad(src: ImageRef): FloatArray? {
        val s = requireMat(src)
        val g = toGrayOwned(s)

        val blur = Mat()
        Imgproc.GaussianBlur(g, blur, Size(5.0, 5.0), 0.0)
        val edges = Mat()
        Imgproc.Canny(blur, edges, 50.0, 150.0)

        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        val closed = Mat()
        Imgproc.morphologyEx(edges, closed, Imgproc.MORPH_CLOSE, kernel)

        g.release(); blur.release(); edges.release(); kernel.release()

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(closed, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        closed.release(); hierarchy.release()

        var best: MatOfPoint2f? = null
        var bestArea = 0.0

        contours.forEach { c ->
            val pts = c.toArray() // Array<org.opencv.core.Point>
            val c2f = MatOfPoint2f(*pts.map { org.opencv.core.Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray())
            val approx = approxPoly(c2f)
            if (approx.total().toInt() == 4) {
                val a = contourAreaAbs(approx)
                val mop = MatOfPoint(*approx.toArray()) // make MatOfPoint to check convex
                val convex = Imgproc.isContourConvex(mop)
                mop.release()
                if (convex && a > bestArea) {
                    best?.release()
                    best = approx
                    bestArea = a
                } else {
                    approx.release()
                }
            } else {
                approx.release()
            }
            c2f.release()
            c.release()
        }

        val quad = best?.let { nonNull ->
            val pts = nonNull.toArray()
            nonNull.release()
            val ordered = orderQuadClockwise(arrayOf(
                Point(pts[0].x, pts[0].y),
                Point(pts[1].x, pts[1].y),
                Point(pts[2].x, pts[2].y),
                Point(pts[3].x, pts[3].y),
            ))
            floatArrayOf(
                ordered[0].x.toFloat(), ordered[0].y.toFloat(),
                ordered[1].x.toFloat(), ordered[1].y.toFloat(),
                ordered[2].x.toFloat(), ordered[2].y.toFloat(),
                ordered[3].x.toFloat(), ordered[3].y.toFloat(),
            )
        }

        return quad
    }

    override fun warpPerspective(src: ImageRef, quad: FloatArray): ImageRef {
        require(quad.size == 8) { "quad must be 8 floats (x0,y0,...,x3,y3)" }
        val s = requireMat(src)
        val ordered = orderQuadClockwise(arrayOf(
            Point(quad[0].toDouble(), quad[1].toDouble()),
            Point(quad[2].toDouble(), quad[3].toDouble()),
            Point(quad[4].toDouble(), quad[5].toDouble()),
            Point(quad[6].toDouble(), quad[7].toDouble())
        ))
        val p = ordered.map { it.toCv() }

        val widthA  = hypot(p[2].x - p[3].x, p[2].y - p[3].y)
        val widthB  = hypot(p[1].x - p[0].x, p[1].y - p[0].y)
        val maxW    = max(widthA, widthB).toInt().coerceAtLeast(1)

        val heightA = hypot(p[1].x - p[2].x, p[1].y - p[2].y)
        val heightB = hypot(p[0].x - p[3].x, p[0].y - p[3].y)
        val maxH    = max(heightA, heightB).toInt().coerceAtLeast(1)

        val srcPts = MatOfPoint2f(*p.toTypedArray())
        val dstPts = MatOfPoint2f(
            org.opencv.core.Point(0.0, 0.0),
            org.opencv.core.Point(maxW - 1.0, 0.0),
            org.opencv.core.Point(maxW - 1.0, maxH - 1.0),
            org.opencv.core.Point(0.0, maxH - 1.0)
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

        val nonZero = MatOfPoint()
        findNonZero(mask, nonZero) // Core.findNonZero

        contours.forEach { it.release() }
        hierarchy.release()
        mask.release()
        bw.release()
        gray.release()

        if (nonZero.empty()) {
            nonZero.release()
            return newRefFor(s.clone())
        }

        val ptsArr = nonZero.toArray()
        nonZero.release()
        val pts2f = MatOfPoint2f(*ptsArr) // OK: toArray() gives Point[], vararg spread is non-null

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
            setClipLimit(clip)
            setTilesGridSize(Size(tiles.toDouble(), tiles.toDouble()))
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

    override fun resize(src: ImageRef, maxSide: Double): ImageRef {
        val s = requireMat(src)
        val w = s.width().toDouble()
        val h = s.height().toDouble()
        val maxDim = max(w, h)
        if (maxDim <= max(1.0, maxSide)) return newRefFor(s.clone())
        val scale = maxSide / maxDim
        val out = Mat()
        Imgproc.resize(s, out, Size(w * scale, h * scale), 0.0, 0.0, Imgproc.INTER_AREA)
        return newRefFor(out)
    }

    override fun grayscale(src: ImageRef): ImageRef {
        val s = requireMat(src)
        val g = toGrayOwned(s)
        return newRefFor(g)
    }

    override fun blur(src: ImageRef, kernelSize: Size): ImageRef {
        val s = requireMat(src)
        val kx = ensureOddPositive(kernelSize.width)
        val ky = ensureOddPositive(kernelSize.height)
        val out = Mat()
        Imgproc.GaussianBlur(s, out, Size(kx.toDouble(), ky.toDouble()), 0.0, 0.0)
        return newRefFor(out)
    }

    override fun threshold(src: ImageRef, blur: ImageRef): ImageRef {
        val s = toGrayOwned(requireMat(src))
        val b = toGrayOwned(requireMat(blur))
        val diff = Mat()
        Core.absdiff(s, b, diff)
        val bw = Mat()
        Imgproc.threshold(diff, bw, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
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
        k.convertTo(k8u, CvType.CV_8U)
        val out = Mat()
        Imgproc.morphologyEx(s, out, Imgproc.MORPH_CLOSE, k8u)
        k8u.release()
        return newRefFor(out)
    }

    override fun findContours(src: ImageRef): List<List<Point>> {
        val s = requireMat(src)
        val grayOrBin = if (s.channels() == 1) s.clone() else toGrayOwned(s)
        val bin = Mat()
        Imgproc.threshold(grayOrBin, bin, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
        grayOrBin.release()

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(bin, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        bin.release(); hierarchy.release()

        val result = contours.map { mop ->
            val pts = mop.toArray().map { it.toDomain() }
            mop.release()
            pts
        }
        return result
    }

    override fun fourPointWarp(src: ImageRef, quad: Array<Point>): ImageRef {
        require(quad.size == 4) { "quad must have 4 Points" }
        val ordered = orderQuadClockwise(quad)
        val arr = floatArrayOf(
            ordered[0].x.toFloat(), ordered[0].y.toFloat(),
            ordered[1].x.toFloat(), ordered[1].y.toFloat(),
            ordered[2].x.toFloat(), ordered[2].y.toFloat(),
            ordered[3].x.toFloat(), ordered[3].y.toFloat(),
        )
        return warpPerspective(src, arr)
    }

    override fun enhanceDocument(src: ImageRef, mode: String): ImageRef {
        return when (mode.lowercase()) {
            "bw", "b&w", "binary" -> {
                val g = grayscale(src)
                val norm = illuminationNormalize(g, 81)
                MatRegistry.release(g)
                val out = toBWAdaptive(norm)
                MatRegistry.release(norm)
                out
            }
            "clean", "color" -> {
                val den = denoise(src)
                val g = grayscale(den)
                val cla = clahe(g, 3.0, 8)
                MatRegistry.release(den); MatRegistry.release(g)
                val shp = unsharp(cla, 1.2, 0.7)
                MatRegistry.release(cla)
                shp
            }
            else -> {
                val g = grayscale(src)
                val cla = clahe(g, 3.0, 8)
                MatRegistry.release(g)
                cla
            }
        }
    }

    /** Not in the interface, but you’ll likely need it. */
    fun release(img: ImageRef) = MatRegistry.release(img)
}
