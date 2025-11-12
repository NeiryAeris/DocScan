package com.example.ocr.core

import com.example.ocr.core.api.OcrImage
import java.nio.file.*
import javax.imageio.ImageIO
import kotlin.test.*

class OcrSmokeJvmTest {

    @Test
    fun enhancedImage_Tess4J_smoke() = kotlinx.coroutines.runBlocking {
        // 1) load enhanced image from resources
        val imgRes = "ocr_samples/enhanced.jpg"
        val url = checkNotNull(javaClass.classLoader.getResource(imgRes)) { "Missing $imgRes" }
        val bi = ImageIO.read(url) ?: error("Cannot decode $imgRes")

        // 2) convert to OcrImage.Gray8 (simple)
        val gray = if (bi.type == java.awt.image.BufferedImage.TYPE_BYTE_GRAY) bi else {
            val g = java.awt.image.BufferedImage(bi.width, bi.height, java.awt.image.BufferedImage.TYPE_BYTE_GRAY)
            g.graphics.drawImage(bi, 0, 0, null); g
        }
        val rowStride = gray.width
        val buf = ByteArray(gray.width * gray.height)
        for (y in 0 until gray.height) {
            gray.raster.getDataElements(0, y, gray.width, 1, buf, y * rowStride)
        }
        val ocrImg: OcrImage = OcrImage.Gray8(gray.width, gray.height, buf, rowStride)

        // 3) prepare tessdata dir (copy from resources)
        val base = Files.createTempDirectory("tessdata_base")
        val td = base.resolve("tessdata"); Files.createDirectories(td)
        fun copy(name: String) {
            val res = "tessdata/$name"
            val ru = checkNotNull(javaClass.classLoader.getResource(res)) { "Missing $res" }
            Files.copy(Paths.get(ru.toURI()), td.resolve(name), StandardCopyOption.REPLACE_EXISTING)
        }
        copy("eng.traineddata"); copy("vie.traineddata")

        // 4) run OCR
        val engine = Tess4JOcrEngine(datapath = base.toString())
        val res = engine.recognize(ocrImg, "vie+eng")
        val text = res.text

        // 5) write output & assert
        val outDir = Paths.get("build/test-output").also { Files.createDirectories(it) }
        Files.write(outDir.resolve("ocr.txt"), text.toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        assertTrue(text.isNotBlank(), "OCR output should not be blank")
    }
}
