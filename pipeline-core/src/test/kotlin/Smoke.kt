import com.example.pipeline_core.legacy.DocumentPipeline

import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import kotlin.test.Test

class Smoke {
    @Test
    fun runSketchOnly() {
        DocumentPipeline.init()

        var sample: String = "sample_1.jpg"

        val input = locate("samples/$sample")
        val srcBgr = Imgcodecs.imread(input, Imgcodecs.IMREAD_COLOR)
        require(!srcBgr.empty()) { "Put a test image at pipeline-core/samples/$sample (not found: $input)" }

        val rgba = Mat()
        Imgproc.cvtColor(srcBgr, rgba, Imgproc.COLOR_BGR2RGBA)

        val outDir = java.io.File("build/test-output").apply { mkdirs() }

        // Run SKETCH mode only
        val r = DocumentPipeline.process(rgba, "auto")
//        Imgcodecs.imwrite("${outDir.path}/sketch_overlay.png", r.overlay)
//        r.warped?.let { Imgcodecs.imwrite("${outDir.path}/sketch_warped.png", it) }
        r.enhanced?.let { Imgcodecs.imwrite("${outDir.path}/sketch_enhanced.png", it) }

//        val r2 = DocumentPipeline.process(rgba, "bw")
//        r2.enhanced?.let { Imgcodecs.imwrite("${outDir.path}/bw_enhanced.png", it) }
    }

    private fun locate(rel: String): String {
        val p1 = java.nio.file.Paths.get(rel)
        if (java.nio.file.Files.exists(p1)) return p1.toString()
        val p2 = java.nio.file.Paths.get("pipeline-core", rel)
        if (java.nio.file.Files.exists(p2)) return p2.toString()
        return rel
    }
}
