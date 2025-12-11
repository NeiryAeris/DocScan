package com.example.imaging_opencv_android.ocr.legacy

import com.example.ocr.core.api.OcrImage
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import kotlin.collections.plusAssign

object LineSegmentation {

    /** Segment approximate text lines on a gray/binary Mat and return Gray8 crops per line. */
    fun segmentLinesAsGray8(grayOrBin: Mat): List<OcrImage.Gray8> {
        val gray = if (grayOrBin.channels() == 1) grayOrBin else {
            val g = Mat(); Imgproc.cvtColor(grayOrBin, g, Imgproc.COLOR_BGR2GRAY); g
        }
        val bin = Mat()
        Imgproc.threshold(gray, bin, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)

        // Project to find line runs
        val w = bin.cols(); val h = bin.rows()
        val proj = IntArray(h)
        for (y in 0 until h) {
            var sum = 0
            for (x in 0 until w) sum += (255 - bin.get(y, x)[0].toInt()) // "ink" as >0
            proj[y] = sum
        }
        val rects = ArrayList<Rect>()
        var inRun = false; var y0 = 0
        val minInk = (w / 50).coerceAtLeast(8)
        for (y in 0 until h) {
            if (!inRun && proj[y] > minInk) { inRun = true; y0 = y }
            if (inRun && proj[y] <= minInk) {
                rects plusAssign Rect(0, y0, w, (y - y0).coerceAtLeast(1)); inRun = false
            }
        }
        if (inRun) rects plusAssign Rect(0, y0, w, (h - y0).coerceAtLeast(1))

        // Crop each rect to Gray8
        val lines = ArrayList<OcrImage.Gray8>(rects.size)
        for (r in rects) {
            val roi = Mat(gray, r)
            lines plusAssign roi.asOcrGray8()
            roi.release()
        }
        if (bin !== grayOrBin) bin.release()
        if (gray !== grayOrBin) gray.release()
        return lines
    }
}