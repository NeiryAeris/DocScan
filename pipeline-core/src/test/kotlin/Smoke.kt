import com.example.pipeline.DocumentPipeline
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import kotlin.test.Test

class Smoke {
    @Test
    fun runOnce() {
        DocumentPipeline.init()
        val srcBgr = Imgcodecs.imread("samples/test_subject.jpg", Imgcodecs.IMREAD_COLOR)
        require(!srcBgr.empty()) { "Put a test image at pipeline-core/samples/test_subject.jpg" }
        val rgba = Mat()
        Imgproc.cvtColor(srcBgr, rgba, Imgproc.COLOR_BGR2RGBA)

        val r = DocumentPipeline.process(rgba)

        // Write outputs under build/ so they don't clutter the module root
        java.io.File("build/test-output").mkdirs()
        Imgcodecs.imwrite("build/test-output/out_overlay.png", r.overlay)
        r.warped?.let { Imgcodecs.imwrite("build/test-output/out_warped.png", it) }
        r.enhanced?.let { Imgcodecs.imwrite("build/test-output/out_enhanced.png", it) }
    }
}
