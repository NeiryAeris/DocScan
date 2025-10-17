package com.example.pipeline_core

import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

sealed class PageRef {
    class InMemory(val mat: Mat): PageRef()
    class OnDisk(val file: File) : PageRef()
}

class PageStore private constructor(
    private val refs: MutableList<PageRef>,
    private val onClose: (PageRef) -> Unit
) : AutoCloseable {
    companion object {
        fun inMemory(): PageStore = PageStore(mutableListOf()) { ref ->
            if (ref is PageRef.InMemory) ref.mat.release()
        }

        fun diskCache(dir: File): PageStore {
            dir.mkdirs()
            return PageStore(mutableListOf()) { ref ->
                if (ref is PageRef.OnDisk) ref.file.delete()
            }
        }
    }

    private val counter = AtomicInteger(0)

    fun add(mat:Mat, diskDir: File? = null) {
        require(!mat.empty()) { "Mat must not be empty" }
        val rgba = ensureRgba(mat)
        if (diskDir == null) {
            refs += PageRef.InMemory(rgba)
        } else {
            val idx = counter.incrementAndGet()
            val file = File(diskDir, "page-$idx.png")

            val bgr = Mat()
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            Imgcodecs.imwrite(file.absolutePath, bgr)
            bgr.release()
            refs += PageRef.OnDisk(file)

        }
    }

    fun pages() : List<PageRef> = refs.toList()

    override fun close() {
        refs.forEach(onClose)
        refs.clear()
    }

    private fun ensureRgba(src: Mat): Mat {
        return when (src.type()) {
            CvType.CV_8UC4 -> src.clone()
            CvType.CV_8UC3 -> Mat().also { Imgproc.cvtColor(src, it, Imgproc.COLOR_BGR2RGBA) }
            CvType.CV_8UC1 -> Mat().also { Imgproc.cvtColor(src, it, Imgproc.COLOR_GRAY2RGBA) }
            else -> {throw IllegalArgumentException("Unsupported image type: ${src.type()}")}
        }
    }
}