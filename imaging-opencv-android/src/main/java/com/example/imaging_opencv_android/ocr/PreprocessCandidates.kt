package com.example.imaging_opencv_android.ocr

import com.example.ocr.core.api.OcrImage
import org.opencv.core.Mat

object PreprocessCandidates {
    /** From a gray Mat, produce 1..N OcrImage candidates (adaptive/otsu, etc.). */
    fun fromGrayMat(gray: Mat): List<OcrImage.Gray8> {
        val (a, b) = OcrPreprocess.produceCandidates(gray)  // adaptive + otsu
        val upA = OcrPreprocess.upscaleIfSmall(a)
        val upB = OcrPreprocess.upscaleIfSmall(b)

        return listOf(
            upA.asOcrGray8(),
            upB.asOcrGray8(),
        )
    }
}