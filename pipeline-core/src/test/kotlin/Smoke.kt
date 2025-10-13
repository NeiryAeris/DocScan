import com.example.pipeline.DocumentPipeline
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import kotlin.test.Test

class Smoke {
    @Test
    fun runOnce() {
        DocumentPipeline.init()

        // Find the sample image (works whether the test runs from project root or module dir)
        val input = locate("samples/test_subject.jpg")
        val srcBgr = Imgcodecs.imread(input, Imgcodecs.IMREAD_COLOR)
        require(!srcBgr.empty()) { "Put a test image at pipeline-core/samples/test_subject.jpg (not found: $input)" }

        // Convert to RGBA (the pipeline expects CV_8UC4)
        val rgba = Mat()
        Imgproc.cvtColor(srcBgr, rgba, Imgproc.COLOR_BGR2RGBA)

        val outDir = java.io.File("build/test-output").apply { mkdirs() }

        // Run COLOR mode
        run {
            val r = DocumentPipeline.process(rgba, "color")
            Imgcodecs.imwrite("${outDir.path}/color_overlay.png", r.overlay)
            r.warped?.let { Imgcodecs.imwrite("${outDir.path}/color_warped.png", it) }
            r.enhanced?.let { Imgcodecs.imwrite("${outDir.path}/color_enhanced.png", it) }
        }

        // Run BW mode
        run {
            val r = DocumentPipeline.process(rgba, "bw")
            r.enhanced?.let { Imgcodecs.imwrite("${outDir.path}/bw_enhanced.png", it) }
        }

        // Run GRAY mode
        run {
            val r = DocumentPipeline.process(rgba, "gray")
            r.enhanced?.let { Imgcodecs.imwrite("${outDir.path}/gray_enhanced.png", it) }
        }
    }

    private fun locate(rel: String): String {
        val p1 = java.nio.file.Paths.get(rel)
        if (java.nio.file.Files.exists(p1)) return p1.toString()
        val p2 = java.nio.file.Paths.get("pipeline-core", rel)
        if (java.nio.file.Files.exists(p2)) return p2.toString()
        return rel
    }
}
