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
        val imgPath = "ocr_samples/img.png"
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
            bytes = data.copyOf(),
            rowStride = gray.width
        )

        // 3) Prepare tessdata path (copy to BOTH <base>/ and <base>/tessdata/)
        val dataDir = prepareTessdata() // returns <base>

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

    /**
     * Some Tess4J builds look for <datapath>/eng.traineddata,
     * others for <datapath>/tessdata/eng.traineddata.
     * To be safe, copy to BOTH places and return <datapath>=<base>.
     */
    private fun prepareTessdata(): Path {
        val base = Files.createTempDirectory("tess4j-data")
        val td = base.resolve("tessdata")
        Files.createDirectories(td)

        fun copyToBoth(name: String) {
            val res = "tessdata/$name"
            val url = checkNotNull(javaClass.classLoader.getResource(res)) { "Missing $res" }
            val src = Paths.get(url.toURI())
            Files.copy(src, base.resolve(name), StandardCopyOption.REPLACE_EXISTING) // <base>/eng.traineddata
            Files.copy(src, td.resolve(name),   StandardCopyOption.REPLACE_EXISTING) // <base>/tessdata/eng.traineddata
        }

        copyToBoth("eng.traineddata")
        copyToBoth("vie.traineddata")
        return base // pass this to Tess4JOcrEngine(datapath)
    }
}
