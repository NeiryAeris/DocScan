package com.example.docscan.logic.processing

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Pure processing (no Android UI). Can be called from any layer.
 */
object ImageProcessor {

    /**
     * Canny edge detection. Input: color or grayscale bitmap.
     * Output: RGBA bitmap showing edges.
     */
    fun cannyEdgesBitmap(
        orig: Bitmap,
        blurSize: Size = Size(5.0, 5.0),
        lowThresh: Double = 75.0,
        highThresh: Double = 200.0
    ): Bitmap {
        val srcBmp = if (orig.config != Bitmap.Config.ARGB_8888)
            orig.copy(Bitmap.Config.ARGB_8888, false) else orig

        val src = Mat(srcBmp.height, srcBmp.width, CvType.CV_8UC4)
        Utils.bitmapToMat(srcBmp, src)

        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        val edgesRgba = Mat()

        try {
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, blurSize, 0.0)
            Imgproc.Canny(blurred, edges, lowThresh, highThresh)
            Imgproc.cvtColor(edges, edgesRgba, Imgproc.COLOR_GRAY2RGBA)

            val out = createBitmap(edgesRgba.cols(), edgesRgba.rows())
            Utils.matToBitmap(edgesRgba, out)
            return out
        } finally {
            // release native memory
            src.release()
            gray.release()
            blurred.release()
            edges.release()
            edgesRgba.release()
        }
    }
}