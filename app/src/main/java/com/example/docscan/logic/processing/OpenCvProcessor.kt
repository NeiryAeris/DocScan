package com.example.docscan.logic.processing

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

object OpenCvProcessor {

    /**
     * Convert to edges using: RGBA -> Gray -> GaussianBlur -> Canny -> RGBA
     */
    fun cannyEdgesBitmap(orig: Bitmap): Bitmap {
        val srcBmp = ensureArgb8888(orig)

        val src = Mat(srcBmp.height, srcBmp.width, CvType.CV_8UC4)
        Utils.bitmapToMat(srcBmp, src)

        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

        val edges = Mat()
        Imgproc.Canny(gray, edges, 75.0, 200.0)

        val edgesRgba = Mat()
        Imgproc.cvtColor(edges, edgesRgba, Imgproc.COLOR_GRAY2RGBA)

        val out = createBitmap(edgesRgba.cols(), edgesRgba.rows())
        Utils.matToBitmap(edgesRgba, out)
        return out
    }

    private fun ensureArgb8888(bmp: Bitmap): Bitmap =
        if (bmp.config != Bitmap.Config.ARGB_8888)
            bmp.copy(Bitmap.Config.ARGB_8888, false)
        else bmp
}