package com.example.docscan.logic.utils.legacy

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Mat
import androidx.core.graphics.createBitmap
import org.opencv.core.CvType

object AndroidMatAdapter {
    fun bitmapToMat(bmp: Bitmap): Mat {
        val m = Mat(bmp.height, bmp.width, CvType.CV_8UC4)
        Utils.bitmapToMat(bmp, m)
        return m
    }
    fun matToBitmap(mat: Mat): Bitmap {
        val bmp = createBitmap(mat.cols(), mat.rows())
        Utils.matToBitmap(mat, bmp)
        return bmp
    }
}