package com.example.imaging_opencv_android

import com.example.domain.types.ImageRef
import com.example.domain.interfaces.Imaging
import com.example.domain.types.Point
import com.example.domain.types.Size
import org.opencv.core.*
import org.opencv.core.Core.findNonZero
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class OpenCvImaging(private val cfg: ImagingConfig = ImagingConfig()) : Imaging {

    // ------------------ helpers / conversions ------------------

    private fun newRefFor(m: Mat): ImageRef =
        ImageRef(width = m.width(), height = m.height()).also { MatRegistry.put(it, m) }

    private fun requireMat(ref: ImageRef): Mat = MatRegistry.requireMat(ref)

    private fun toGrayOwned(src: Mat): Mat = when (src.channels()) {
        1 -> src.clone()
        3 -> Mat().also { Imgproc.cvtColor(src, it, Imgproc.COLOR_BGR2GRAY) }
        4 -> Mat().also { Imgproc.cvtColor(src, it, Imgproc.COLOR_BGRA2GRAY) }
        else -> src.clone()
    }

    // domain <-> opencv
    private fun Point.toCv() = org.opencv.core.Point(this.x.toDouble(), this.y.toDouble())
    private fun org.opencv.core.Point.toDomain() = Point(this.x, this.y)
    private fun Size.toCv() = org.opencv.core.Size(this.width.toDouble(), this.height.toDouble())

    private fun ensureOddPositive(v: Int): Int = if (v <= 0) 1 else if (v % 2 == 1) v else v + 1

    private fun approxPoly(points: MatOfPoint2f, epsRatio: Double): MatOfPoint2f {
        val peri = Imgproc.arcLength(points, true)
        val out = MatOfPoint2f()
        Imgproc.approxPolyDP(points, out, epsRatio * peri, true)
        return out
    }

    private fun contourAreaAbs(points: MatOfPoint2f): Double {
        val mop = MatOfPoint(*points.toArray().map { org.opencv.core.Point(it.x, it.y) }.toTypedArray())
        val a = abs(Imgproc.contourArea(mop))
        mop.release()
        return a
    }

    private fun orderQuadClockwise(q: Array<Point>): Array<Point> {
        // TL=min(x+y), BR=max(x+y), TR=min(x−y), BL=max(x−y)
        val tl = q.minByOrNull { it.x + it.y }!!
        val br = q.maxByOrNull { it.x + it.y }!!
        val tr = q.maxByOrNull { it.x - it.y }!!
        val bl = q.minByOrNull { it.x - it.y }!!
        return arrayOf(tl, tr, br, bl)
    }

    private data class Downscale(val scaled: Mat, val scale: Double)
    private fun downscaleForDetect(src: Mat, targetMaxSide: Int = cfg.detect.downscaleMaxSide): Downscale {
        val w = src.width().toDouble()
        val h = src.height().toDouble()
        val maxDim = max(w, h)
        if (maxDim <= targetMaxSide) return Downscale(src.clone(), 1.0)
        val scale = targetMaxSide / maxDim
        val dst = Mat()
        Imgproc.resize(src, dst, Size(w * scale, h * scale), 0.0, 0.0, Imgproc.INTER_AREA)
        return Downscale(dst, scale)
    }

    // ------------------ interface: I/O ------------------

    override fun fromBytes(jpeg: ByteArray): ImageRef {
        val m = MatIo.decode(jpeg)
        return newRefFor(m)
    }

    override fun toJpeg(img: ImageRef, quality: Int): ByteArray {
        val m = requireMat(img)
        return MatIo.encodeJpeg(m, quality)
    }

    // ------------------ detection ------------------

    override fun detectDocumentQuad(src: ImageRef): FloatArray? {
        val full = requireMat(src)
        // 1) Downscale for speed & robustness
        val (scaled, scale) = downscaleForDetect(full)

        // 2) GRAY -> BLUR -> OTSU (binary)
        val g = toGrayOwned(scaled)
        val blur = Mat()
        Imgproc.GaussianBlur(g, blur, Size(cfg.detect.blurK.toDouble(), cfg.detect.blurK.toDouble()), 0.0)
        val bin = Mat()
        Imgproc.threshold(blur, bin, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)

        // 3) Morphological CLOSE to bridge gaps
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(cfg.detect.closeK.toDouble(), cfg.detect.closeK.toDouble()))
        val closed = Mat()
        Imgproc.morphologyEx(bin, closed, Imgproc.MORPH_CLOSE, kernel)

        g.release(); blur.release(); bin.release(); kernel.release()

        // 4) Find contours on the closed mask
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(closed, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        closed.release(); hierarchy.release()

        // 5) Filter/sort by area (top N, ignore near-full-frame)
        val imgArea = scaled.width().toDouble() * scaled.height().toDouble()
        val topN = contours
            .map { c ->
                val a = abs(Imgproc.contourArea(c))
                Pair(c, a)
            }
            .sortedByDescending { it.second }
            .take(cfg.detect.topN) // top 15
            .filter { it.second in (imgArea * cfg.detect.areaMinRatio)..(imgArea * cfg.detect.areaMaxRatio) } // ignore tiny & near-full-rect

        var best: MatOfPoint2f? = null
        var bestArea = 0.0

        topN.forEach { (c, _) ->
            val c2f = MatOfPoint2f(*c.toArray())
            val approx = approxPoly(c2f, epsRatio = cfg.detect.approxEpsRatio) // looser epsilon than default for doc shapes
            val mop = MatOfPoint(*approx.toArray())
            val convex = Imgproc.isContourConvex(mop)
            val a = if (approx.total().toInt() == 4) contourAreaAbs(approx) else 0.0
            if (convex && approx.total().toInt() == 4 && a > bestArea) {
                best?.release()
                best = approx
                bestArea = a
            } else {
                approx.release()
            }
            mop.release()
            c2f.release()
            c.release()
        }

        // Fallback: minAreaRect of foreground pixels
        if (best == null) {
            val mask = Mat()
            // treat the earlier "closed" as mask; we already released it, but we can re-create quickly:
            // Use a light Canny to create a mask fallback
            val g2 = toGrayOwned(scaled)
            Imgproc.Canny(g2, mask, cfg.detect.canny1, cfg.detect.canny2)
            val nz = MatOfPoint()
            findNonZero(mask, nz)
            mask.release(); g2.release()
            if (!nz.empty()) {
                val nz2f = MatOfPoint2f(*nz.toArray())
                val rect = Imgproc.minAreaRect(nz2f)
                nz2f.release(); nz.release()
                val boxPts = arrayOf(
                    org.opencv.core.Point(), org.opencv.core.Point(),
                    org.opencv.core.Point(), org.opencv.core.Point()
                )
                rect.points(boxPts)
                val m2f = MatOfPoint2f(*boxPts)
                best = m2f
                bestArea = contourAreaAbs(best!!)
            } else {
                nz.release()
            }
        }

        scaled.release()

        val quad = best?.let { nonNull ->
            val pts = nonNull.toArray()
            nonNull.release()
            // Map back to full res by 1/scale
            val inv = if (scale == 0.0) 1.0 else 1.0 / scale
            val domainPts = arrayOf(
                Point(pts[0].x * inv, pts[0].y * inv),
                Point(pts[1].x * inv, pts[1].y * inv),
                Point(pts[2].x * inv, pts[2].y * inv),
                Point(pts[3].x * inv, pts[3].y * inv),
            )
            val ordered = orderQuadClockwise(domainPts)
            floatArrayOf(
                ordered[0].x.toFloat(), ordered[0].y.toFloat(),
                ordered[1].x.toFloat(), ordered[1].y.toFloat(),
                ordered[2].x.toFloat(), ordered[2].y.toFloat(),
                ordered[3].x.toFloat(), ordered[3].y.toFloat(),
            )
        }

        return quad
    }

    // ------------------ warp (two forms) ------------------

    override fun warpPerspective(src: ImageRef, quad: FloatArray): ImageRef {
        require(quad.size == 8) { "quad must be 8 floats (x0,y0,...,x3,y3)" }
        val ordered = orderQuadClockwise(arrayOf(
            Point(quad[0].toDouble(), quad[1].toDouble()),
            Point(quad[2].toDouble(), quad[3].toDouble()),
            Point(quad[4].toDouble(), quad[5].toDouble()),
            Point(quad[6].toDouble(), quad[7].toDouble())
        ))
        return fourPointWarp(src, ordered)
    }

    override fun fourPointWarp(src: ImageRef, quad: Array<Point>): ImageRef {
        val s = requireMat(src)
        val ordered = orderQuadClockwise(quad).map { it.toCv() }

        val widthA  = hypot(ordered[2].x - ordered[3].x, ordered[2].y - ordered[3].y)
        val widthB  = hypot(ordered[1].x - ordered[0].x, ordered[1].y - ordered[0].y)
        val outW    = max(widthA, widthB).toInt().coerceAtLeast(1)

        val heightA = hypot(ordered[1].x - ordered[2].x, ordered[1].y - ordered[2].y)
        val heightB = hypot(ordered[0].x - ordered[3].x, ordered[0].y - ordered[3].y)
        val outH    = max(heightA, heightB).toInt().coerceAtLeast(1)

        val srcPts = MatOfPoint2f(*ordered.toTypedArray())
        val dstPts = MatOfPoint2f(
            org.opencv.core.Point(0.0, 0.0),
            org.opencv.core.Point(outW - 1.0, 0.0),
            org.opencv.core.Point(outW - 1.0, outH - 1.0),
            org.opencv.core.Point(0.0, outH - 1.0)
        )
        val M = Imgproc.getPerspectiveTransform(srcPts, dstPts)
        val out = Mat()
        try {
            Imgproc.warpPerspective(s, out, M, Size(outW.toDouble(), outH.toDouble()),
                Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE)
        } finally {
            srcPts.release(); dstPts.release(); M.release()
        }
        return newRefFor(out)
    }

    /** Extra helper: warp to an explicit target (e.g., A4). Not part of the interface. */
    fun fourPointWarpTo(src: ImageRef, quad: Array<Point>, target: Size): ImageRef {
        val s = requireMat(src)
        val ordered = orderQuadClockwise(quad).map { it.toCv() }
        val outW = max(1, target.width)
        val outH = max(1, target.height)
        val srcPts = MatOfPoint2f(*ordered.toTypedArray())
        val dstPts = MatOfPoint2f(
            org.opencv.core.Point(0.0, 0.0),
            org.opencv.core.Point(outW - 1.0, 0.0),
            org.opencv.core.Point(outW - 1.0, outH - 1.0),
            org.opencv.core.Point(0.0, outH - 1.0)
        )
        val M = Imgproc.getPerspectiveTransform(srcPts, dstPts)
        val out = Mat()
        try {
            Imgproc.warpPerspective(s, out, M, Size(outW.toDouble(), outH.toDouble()),
                Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE)
        } finally {
            srcPts.release(); dstPts.release(); M.release()
        }
        return newRefFor(out)
    }

    // ------------------ deskew / normalize ------------------

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

        // Create a skinny mask of strokes
        val mask = Mat.zeros(bw.size(), CvType.CV_8UC1)
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(bw, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        contours.forEach { c -> Imgproc.drawContours(mask, listOf(c), -1, Scalar(255.0), 1); c.release() }
        hierarchy.release(); bw.release(); gray.release()

        val nonZero = MatOfPoint()
        findNonZero(mask, nonZero)
        mask.release()

        if (nonZero.empty()) {
            nonZero.release()
            return newRefFor(s.clone())
        }

        val pts2f = MatOfPoint2f(*nonZero.toArray())
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

    // ------------------ basic filters ------------------

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

    // ------------------ enhancement presets ------------------

    override fun enhanceDocument(src: ImageRef, mode: String): ImageRef {
        return when (mode.lowercase()) {
            // Scanner-style automatic normalization + pop
            "auto_pro" -> {
                val g = grayscale(src)               // gray
                val gMat = requireMat(g)
                // Background estimation by median (ksize scales with minDim)
                val minDim = min(gMat.width(), gMat.height())
                val bgK = ensureOddPositive(max(31, minDim / cfg.enhance.autoPro.medianKFactor))
                val bg = Mat()
                Imgproc.medianBlur(gMat, bg, bgK)
                // Normalize: g / bg * 255
                val norm = Mat()
                Core.divide(gMat, bg, norm, 255.0)
                // CLAHE gentle
                val clahe = Imgproc.createCLAHE().apply {
                    clipLimit = cfg.enhance.autoPro.claheClip
                    tilesGridSize = Size(cfg.enhance.autoPro.claheTiles.toDouble(), cfg.enhance.autoPro.claheTiles.toDouble())
                }
                val cla = Mat()
                clahe.apply(norm, cla)
                // Unsharp
                val sharp = Mat()
                Imgproc.GaussianBlur(cla, sharp, Size(0.0, 0.0), 1.0) // blur into 'sharp' temp
                val un = Mat()
                Core.addWeighted(cla, cfg.enhance.autoPro.unsharpA, sharp, cfg.enhance.autoPro.unsharpB, 0.0, un)
                // Gamma 0.85 LUT
                val lut = Mat(1, 256, CvType.CV_8UC1)
                val buf = ByteArray(256) { i ->
                    val v = (255.0 * (i / 255.0).pow(cfg.enhance.autoPro.gamma)).toInt().coerceIn(0, 255)
                    v.toByte()
                }
                lut.put(0, 0, buf)
                val out = Mat()
                Core.LUT(un, lut, out)
                // release temps
                sharp.release(); cla.release(); norm.release(); bg.release(); gMat.release(); lut.release()
                MatRegistry.release(g)
                newRefFor(out)
            }

            // Color with mask compositing (white background), edge-preserving
            "color_pro" -> {
                val s = requireMat(src)

                // 1. Convert to LAB
                val lab = Mat()
                Imgproc.cvtColor(s, lab, Imgproc.COLOR_BGR2Lab)

                val channels = mutableListOf<Mat>()
                Core.split(lab, channels)
                val L = channels[0]

                // 2. Background normalization on L only
                val minDim = min(L.width(), L.height())
                val bgK = ensureOddPositive(max(31, minDim / cfg.enhance.autoPro.medianKFactor))

                val bg = Mat()
                Imgproc.medianBlur(L, bg, bgK)

                val normL = Mat()
                Core.divide(L, bg, normL, 255.0)

                // 3. Gentle CLAHE
                val clahe = Imgproc.createCLAHE().apply {
                    clipLimit = cfg.enhance.autoPro.claheClip
                    tilesGridSize = Size(
                        cfg.enhance.autoPro.claheTiles.toDouble(),
                        cfg.enhance.autoPro.claheTiles.toDouble()
                    )
                }

                val claL = Mat()
                clahe.apply(normL, claL)

                // 4. Replace L channel
                claL.copyTo(channels[0])
                Core.merge(channels, lab)

                // 5. Back to BGR
                val out = Mat()
                Imgproc.cvtColor(lab, out, Imgproc.COLOR_Lab2BGR)

                // cleanup
                channels.forEach { it.release() }
                lab.release(); bg.release(); normL.release(); claL.release()

                newRefFor(out)
            }


            // Black & White with dynamic block size
            "bw_pro" -> {
                val g = grayscale(src)
                val gMat = requireMat(g)
                // Normalize illumination a bit
                val minDim = min(gMat.width(), gMat.height())
                val medK = ensureOddPositive(max(31, minDim / 20))
                val bg = Mat()
                Imgproc.medianBlur(gMat, bg, medK)
                val norm = Mat()
                Core.divide(gMat, bg, norm, 255.0)
                // Dynamic block Size
                val block = ensureOddPositive(max(25, minDim / 32))
                val bw = Mat()
                Imgproc.adaptiveThreshold(norm, bw, 255.0,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, block, 10.0)
                // cleanup
                gMat.release(); MatRegistry.release(g); bg.release(); norm.release()
                newRefFor(bw)
            }

            // Keep your simpler modes as fallbacks
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
                val g = grayscale(src)
                val cla = clahe(g, 3.0, 8)
                release(g)
                cla
            }
        }
    }

    // ------------------ extra public helpers (not in interface) ------------------

    /** Free native memory for a ref (idempotent). */
    fun release(img: ImageRef) = MatRegistry.release(img)

    /** Draw a green quad overlay on top of the source and return JPEG bytes (for UI preview). */
    fun drawQuadOverlayJpeg(src: ImageRef, quad: FloatArray, quality: Int = 80): ByteArray {
        require(quad.size == 8) { "quad must be 8 floats" }
        val s = requireMat(src)
        val vis = if (s.channels() == 3 || s.channels() == 4) s.clone() else {
            val bgr = Mat()
            Imgproc.cvtColor(s, bgr, Imgproc.COLOR_GRAY2BGR)
            bgr
        }
        val pts = arrayOf(
            org.opencv.core.Point(quad[0].toDouble(), quad[1].toDouble()),
            org.opencv.core.Point(quad[2].toDouble(), quad[3].toDouble()),
            org.opencv.core.Point(quad[4].toDouble(), quad[5].toDouble()),
            org.opencv.core.Point(quad[6].toDouble(), quad[7].toDouble())
        )
        val green = Scalar(0.0, 255.0, 0.0)
        for (i in 0 until 4) {
            Imgproc.line(vis, pts[i], pts[(i + 1) % 4], green, 3, Imgproc.LINE_AA, 0)
        }
        val bytes = MatIo.encodeJpeg(vis, quality)
        vis.release()
        return bytes
    }
}
