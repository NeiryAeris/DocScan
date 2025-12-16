package com.example.pipeline_core

import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc

object PageEncoder {
    fun from(result: ProcessResult): EncodedPage {
        val mat = result.enhanced ?: result.warped ?: result.overlay
        return from(mat)
    }
    fun from(mat: Mat): EncodedPage {
        require(!mat.empty())
        val rgba = when (mat.type()) {
            CvType.CV_8UC4 -> mat
            CvType.CV_8UC3 -> Mat().also { Imgproc.cvtColor(mat, it, Imgproc.COLOR_BGR2RGBA) }
            CvType.CV_8UC1 -> Mat().also { Imgproc.cvtColor(mat, it, Imgproc.COLOR_GRAY2RGBA) }
            else -> error("Unsupported Mat type: ${mat.type()}")
        }
        val buf = MatOfByte()
        Imgcodecs.imencode(".png", rgba, buf)
        val bytes = buf.toArray()
        val page = EncodedPage(bytes, rgba.cols(), rgba.rows())
        buf.release()
        if (rgba !== mat) rgba.release()
        return page
    }
}
