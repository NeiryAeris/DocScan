package com.example.ocr_tesseract_android

import com.example.domain.types.text.TextNormalize
import com.example.domain.types.text.TextScore
import com.example.imaging_opencv_android.ocr.OcrPreprocess
import com.example.imaging_opencv_android.ocr.MatOcrAdapters
import com.example.ocr.core.api.*
import org.opencv.core.Mat

/**
 * Preprocess the Mat into two variants and pick the OCR with higher text score.
 * Use when you control the Mat source (Android/OpenCV side).
 */
class BestOfTwoOcrEngine(
    private val tesseract: OcrEngine
) {

    suspend fun recognizeFromMat(srcGrayMat: Mat, lang: String): OcrPageResult {
        val (a, b) = OcrPreprocess.produceCandidates(srcGrayMat)
        val imgA = MatOcrAdapters.toGray8(a)
        val imgB = MatOcrAdapters.toGray8(b)

        val rA = tesseract.recognize(imgA, lang).text.let { TextNormalize.sanitize(it) }
        val rB = tesseract.recognize(imgB, lang).text.let { TextNormalize.sanitize(it) }

        val pick = if (TextScore.score(rA) >= TextScore.score(rB)) rA else rB
        return OcrPageResult(1, pick)
    }
}
