package com.example.docscan.logic.processing

import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.imgcodecs.Imgcodecs

fun detectAndWarp(imagePath: String): Mat? {
    // Load image
    var img = Imgcodecs.imread(imagePath)
    if (img.empty()) {
        println("⚠️ Could not read: $imagePath")
        return null
    }

    // Resize for speed
    val scale = 1000.0 / img.width()
    val newSize = Size(1000.0, img.height() * scale)
    val small = Mat()
    Imgproc.resize(img, small, newSize)

    // Grayscale + Blur
    val gray = Mat()
    Imgproc.cvtColor(small, gray, Imgproc.COLOR_BGR2GRAY)
    Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

    // Otsu threshold
    val binary = Mat()
    Imgproc.threshold(gray, binary, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)

    // Canny edges
    val edges = Mat()
    Imgproc.Canny(binary, edges, 50.0, 150.0)

    // Morph close
    val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0))
    val closed = Mat()
    Imgproc.morphologyEx(edges, closed, Imgproc.MORPH_CLOSE, kernel)

    // Find contours
    val contours = mutableListOf<MatOfPoint>()
    val hierarchy = Mat()
    Imgproc.findContours(closed, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
    contours.sortByDescending { Imgproc.contourArea(it) }

    val h = small.height()
    val w = small.width()
    val maxArea = h * w * 0.9
    var docContour: MatOfPoint2f? = null

    for (c in contours.take(15)) {
        val area = Imgproc.contourArea(c)
        if (area > maxArea) continue

        val peri = Imgproc.arcLength(MatOfPoint2f(*c.toArray()), true)
        val approx = MatOfPoint2f()
        Imgproc.approxPolyDP(MatOfPoint2f(*c.toArray()), approx, 0.08 * peri, true)

        if (approx.toArray().size == 4) {
            docContour = approx
            break
        }
    }

    // Fallback: minAreaRect
    if (docContour == null && contours.isNotEmpty()) {
        val rect = Imgproc.minAreaRect(MatOfPoint2f(*contours[0].toArray()))
        val box = MatOfPoint2f()
        rect.points(box.toArray())
        docContour = box
    }

    if (docContour == null) {
        println("⚠️ No document contour found")
        return null
    }

    // Scale contour back to original image size
    val scaleFactor = img.width().toDouble() / small.width()
    val scaledPoints = docContour.toArray().map {
        Point(it.x * scaleFactor, it.y * scaleFactor)
    }
    val docContourScaled = MatOfPoint2f(*scaledPoints.toTypedArray())

    // Warp perspective
    return fourPointWarp(img, docContourScaled)
}

private fun fourPointWarp(image: Mat, pts: MatOfPoint2f): Mat {
    val points = orderPoints(pts.toArray())
    val (tl, tr, br, bl) = points

    val widthA = Math.hypot((br.x - bl.x), (br.y - bl.y))
    val widthB = Math.hypot((tr.x - tl.x), (tr.y - tl.y))
    val maxWidth = Math.max(widthA, widthB).toInt()

    val heightA = Math.hypot((tr.x - br.x), (tr.y - br.y))
    val heightB = Math.hypot((tl.x - bl.x), (tl.y - bl.y))
    val maxHeight = Math.max(heightA, heightB).toInt()

    val dst = MatOfPoint2f(
        Point(0.0, 0.0),
        Point((maxWidth - 1).toDouble(), 0.0),
        Point((maxWidth - 1).toDouble(), (maxHeight - 1).toDouble()),
        Point(0.0, (maxHeight - 1).toDouble())
    )

    val M = Imgproc.getPerspectiveTransform(MatOfPoint2f(*points), dst)
    val warped = Mat()
    Imgproc.warpPerspective(image, warped, M, Size(maxWidth.toDouble(), maxHeight.toDouble()))
    return warped
}

private fun orderPoints(pts: Array<Point>): Array<Point> {
    val rect = Array(4) { Point() }
    val s = pts.map { it.x + it.y }
    val diff = pts.map { it.x - it.y }

    rect[0] = pts[s.indexOf(s.minOrNull()!!)] // top-left
    rect[2] = pts[s.indexOf(s.maxOrNull()!!)] // bottom-right
    rect[1] = pts[diff.indexOf(diff.minOrNull()!!)] // top-right
    rect[3] = pts[diff.indexOf(diff.maxOrNull()!!)] // bottom-left
    return rect
}