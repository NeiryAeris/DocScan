package com.example.ocr_tesseract_android

import com.example.ocr.core.api.*
import com.googlecode.tesseract.android.TessBaseAPI

class TesseractOcrEngine(
    private val dataPath: String,
    private val defaultLang: String = "vie+eng"
) : OcrEngine {

    override suspend fun recognize(image: OcrImage, lang: String): OcrPageResult {
        val api = TessBaseAPI()
        api.init(dataPath, if (lang.isBlank()) defaultLang else lang)
        when (image) {
            is OcrImage.Gray8 -> {
                api.setImage(image.bytes, image.width, image.height, 1, image.rowStride)
            }
            is OcrImage.Rgba8888 -> {
                // bytes per pixel = 4
                api.setImage(image.bytes, image.width, image.height, 4, image.rowStride)
            }
        }
        val txt = api.utF8Text ?: ""
        api.end()
        return OcrPageResult(pageNo = 0, text = txt)
    }
}