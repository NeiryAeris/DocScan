package com.example.pipeline_core

import com.example.domain.interfaces.Imaging
import com.example.imaging_opencv_android.OpenCvImaging
import kotlin.test.Test
import kotlin.test.assertTrue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import nu.pattern.OpenCV

class DocumentLayouterSmokeTest {

    @Test
    fun autoPaperAndEnhance() {
        OpenCV.loadLocally()

        val imaging: Imaging = OpenCvImaging() // runtime dep via testImplementation only
        val layouter = DocumentLayouter(
            imaging = imaging,
            defaultDpi = 150,
            policy = OrientationPolicy.PortraitPreferred,
            tol = 0.08
        )

        var sample: String = "skeb_2.jpg"

//        val input = locate("samples/$sample")

        val bytes = resourceBytes(sample)
        val src = imaging.fromBytes(bytes)
        val quad = imaging.detectDocumentQuad(src) ?: error("No document quad detected")

        val warped = layouter.warpAutoPaper(src, quad) // should choose A4/Letter/Legal if aspect matches
        val enhanced = imaging.enhanceDocument(warped, "auto_pro")
        val outJpeg = imaging.toJpeg(enhanced, 85)

        val outDir = Paths.get("build/test-output").also { Files.createDirectories(it) }
        write(outDir.resolve("wrapper_auto_paper.jpg"), outJpeg)
        assertTrue(outJpeg.size > 10_000)

        // best-effort native cleanup when using OpenCvImaging (don’t couple the module)
        tryRelease(imaging, src, warped, enhanced)
    }

    private fun resourceBytes(path: String): ByteArray {
        val url = checkNotNull(javaClass.classLoader.getResource(path)) {
            "Missing test resource: $path (put it under src/test/resources/$path)"
        }
        return Files.readAllBytes(Paths.get(url.toURI()))
    }

    private fun write(path: Path, data: ByteArray) {
        Files.write(path, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
    }

    private fun tryRelease(imaging: Imaging, vararg refs: Any) {
        try {
            val klass = imaging::class.java
            val m = klass.methods.firstOrNull { it.name == "release" && it.parameterTypes.size == 1 } ?: return
            refs.forEach { m.invoke(imaging, it) }
        } catch (_: Throwable) { /* ignore */ }
    }
}
