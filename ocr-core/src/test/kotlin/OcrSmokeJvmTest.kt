package com.example.ocr.core

import com.example.ocr.core.api.OcrImage
import kotlin.test.Test
import kotlin.test.assertTrue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import javax.imageio.ImageIO

class OcrSmokeJvmTest {

    @Test
    fun enhancedImage_OCR_Tess4J() = kotlinx.coroutines.runBlocking {
        // 1) Load enhanced sample (from test resources)
        val imgPath = "ocr_samples/enhanced.jpg"
        val bytes = resourceBytes(imgPath)
        val bi = ImageIO.read(bytes.inputStream()) ?: error("Cannot decode $imgPath")

        // 2) Convert BufferedImage -> OcrImage.Gray8 (simple, robust)
        val gray = if (bi.type == java.awt.image.BufferedImage.TYPE_BYTE_GRAY) bi else {
            val g = java.awt.image.BufferedImage(bi.width, bi.height, java.awt.image.BufferedImage.TYPE_BYTE_GRAY)
            val g2 = g.graphics
            g2.drawImage(bi, 0, 0, null)
            g2.dispose()
            g
        }
        val raster = gray.raster
        val rowStride = gray.width
        val buf = ByteArray(gray.width * gray.height)
        for (y in 0 until gray.height) {
            raster.getDataElements(0, y, gray.width, 1, buf, y * rowStride)
        }
        val ocrImg: OcrImage = OcrImage.Gray8(gray.width, gray.height, buf, rowStride)

        // 3) Prepare tessdata path (copy from test resources to a temp dir)
        val dataDir = prepareTessdata()

        // 4) Run OCR
        val engine = Tess4JOcrEngine(datapath = dataDir.toString())
        val res = engine.recognize(ocrImg, lang = "vie+eng")
        val text = res.text

        // 5) Write output + assert
        val outDir = Paths.get("build/test-output").also { Files.createDirectories(it) }
        write(outDir.resolve("ocr.txt"), text.toByteArray())
        assertTrue(text.isNotBlank(), "OCR output should not be blank")
        // Optional sanity for Vietnamese: accents vs folding are tested later in search
        // assertTrue(text.contains("GIẤY") || text.contains("ĐỀ NGHỊ", ignoreCase = true))
    }

    private fun resourceBytes(path: String): ByteArray {
        val url = checkNotNull(javaClass.classLoader.getResource(path)) {
            "Missing test resource: $path (put under src/test/resources/$path)"
        }
        return Files.readAllBytes(Paths.get(url.toURI()))
    }

    private fun write(path: Path, data: ByteArray) {
        Files.write(path, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
    }

    private fun prepareTessdata(): Path {
        // Copy eng/vie traineddata from classpath to a temp dir: <tmp>/tessdata/
        val base = Files.createTempDirectory("tess4j-data")
        val td = base.resolve("tessdata")
        Files.createDirectories(td)
        fun copy(name: String) {
            val res = "tessdata/$name"
            val url = checkNotNull(javaClass.classLoader.getResource(res)) { "Missing $res" }
            Files.copy(Paths.get(url.toURI()), td.resolve(name))
        }
        copy("eng.traineddata")
        copy("vie.traineddata")
        return base
    }
}
