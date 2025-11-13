package com.example.ocr.core

import com.example.ocr.core.api.OcrImage
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

class OcrSmokeJvmTest {

    @Test
    fun enhancedImage_OCR_Tess4J() = runBlocking {
        // 1) Load enhanced sample (from test resources)
        val imgPath = "ocr_samples/enhanced.jpg"
        val bytes = resourceBytes(imgPath)
        val bi = ImageIO.read(bytes.inputStream()) ?: error("Cannot decode $imgPath")

        // 2) Ensure TYPE_BYTE_GRAY and build OcrImage.Gray8
        val gray: BufferedImage =
            if (bi.type == BufferedImage.TYPE_BYTE_GRAY) bi
            else {
                val g = BufferedImage(bi.width, bi.height, BufferedImage.TYPE_BYTE_GRAY)
                val gfx = g.graphics
                gfx.drawImage(bi, 0, 0, null)
                gfx.dispose()
                g
            }

        val data = (gray.raster.dataBuffer as DataBufferByte).data
        val ocrImg = OcrImage.Gray8(
            width = gray.width,
            height = gray.height,
            bytes = data.copyOf(),   // copy to be safe
            rowStride = gray.width   // 1 byte per pixel
        )

        // 3) Prepare tessdata path (copy from test resources to a temp dir)
        val dataDir = prepareTessdata()

        // 4) Run OCR
        val engine = Tess4JOcrEngine(datapath = dataDir.toString())
        val text = engine.recognize(ocrImg, "vie+eng").text

        // 5) Write output + assert
        val outDir = Paths.get("build/test-output").also { Files.createDirectories(it) }
        write(outDir.resolve("ocr.txt"), text.toByteArray())
        assertTrue(text.isNotBlank(), "OCR output should not be blank")
    }

    private fun resourceBytes(path: String): ByteArray {
        val url = checkNotNull(javaClass.classLoader.getResource(path)) {
            "Missing test resource: $path (put under src/test/resources/$path)"
        }
        return Files.readAllBytes(Paths.get(url.toURI()))
    }

    private fun write(path: Path, data: ByteArray) {
        Files.write(
            path, data,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE
        )
    }

    private fun prepareTessdata(): Path {
        // Copy eng/vie traineddata from classpath to a temp dir: <tmp>/tessdata/
        val base = Files.createTempDirectory("tess4j-data")
        val td = base.resolve("tessdata")
        Files.createDirectories(td)

        fun copy(name: String) {
            val res = "tessdata/$name"
            val url = checkNotNull(javaClass.classLoader.getResource(res)) { "Missing $res" }
            Files.copy(Paths.get(url.toURI()), td.resolve(name), StandardCopyOption.REPLACE_EXISTING)
        }

        copy("eng.traineddata")
        copy("vie.traineddata")
        return base
    }
}
