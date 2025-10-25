import com.example.imaging_opencv_android.OpenCvImaging

import com.example.domain.Point
import com.example.domain.Size
import kotlin.test.Test
import kotlin.test.assertTrue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import nu.pattern.OpenCV

class ImagingSmokeTest {

    @Test
    fun runEndToEnd() {
        // Load OpenCV from openpnp test dependency
        OpenCV.loadLocally()

        val imaging = OpenCvImaging()
        val bytes = resourceBytes("sample_1.jpg")

        val src = imaging.fromBytes(bytes)
        val quad = imaging.detectDocumentQuad(src) ?: error("No document quad detected")

        val outDir = Paths.get("build/test-output").also { Files.createDirectories(it) }

        // 1) Overlay preview
        val overlayJpeg = imaging.drawQuadOverlayJpeg(src, quad, quality = 85)
        write(outDir.resolve("overlay.jpg"), overlayJpeg)

        // 2) Warp (free-size)
        val warped = imaging.warpPerspective(src, quad)

        // 3) Enhancements (pro presets)
        val auto = imaging.enhanceDocument(warped, "auto_pro")
        write(outDir.resolve("auto_pro.jpg"), imaging.toJpeg(auto))

        val color = imaging.enhanceDocument(warped, "color_pro")
        write(outDir.resolve("color_pro.jpg"), imaging.toJpeg(color))

        val bw = imaging.enhanceDocument(warped, "bw_pro")
        write(outDir.resolve("bw_pro.jpg"), imaging.toJpeg(bw))

        // 4) Optional: warp to A4 target (good for PDF page consistency)
        val quadPts = arrayOf(
            Point(quad[0].toDouble(), quad[1].toDouble()),
            Point(quad[2].toDouble(), quad[3].toDouble()),
            Point(quad[4].toDouble(), quad[5].toDouble()),
            Point(quad[6].toDouble(), quad[7].toDouble())
        )
        val a4 = imaging.fourPointWarpTo(src, quadPts, Size(1240, 1754))
        write(outDir.resolve("warp_a4.jpg"), imaging.toJpeg(a4))

        // Sanity checks
        listOf("overlay.jpg","auto_pro.jpg","color_pro.jpg","bw_pro.jpg","warp_a4.jpg").forEach { name ->
            assertTrue(Files.size(outDir.resolve(name)) > 10_000, "Output $name looks too small")
        }

        // Release native mats
        imaging.release(src)
        imaging.release(warped)
        imaging.release(auto)
        imaging.release(color)
        imaging.release(bw)
        imaging.release(a4)
    }

    private fun resourceBytes(path: String): ByteArray {
        val url = javaClass.classLoader.getResource(path)
            ?: error("Missing test resource: $path (expected under src/test/resources/$path)")
        return Files.readAllBytes(Paths.get(url.toURI()))
    }

    private fun write(path: Path, data: ByteArray) {
        Files.write(
            path, data,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE
        )
    }
}