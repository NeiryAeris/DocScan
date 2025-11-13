package com.example.ocr.tesseract

import com.example.ocr.core.api.*
import com.example.ocr.core.util.OcrSanitizer
import com.googlecode.tesseract.android.TessBaseAPI

class TesseractOcrEngine(
    private val dataPath: String,
    private val defaultLang: String = "vie+eng"
) : OcrEngine {

    override suspend fun recognize(image: OcrImage, lang: String): OcrPageResult {
        val api = TessBaseAPI()
        val language = if (lang.isBlank()) defaultLang else lang

        api.init(dataPath, language)
        // IMPORTANT knobs: DPI, PSM, blacklist common impostors, preserve spaces
        runCatching { api.setVariable("user_defined_dpi", "300") }
        api.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
        runCatching { api.setVariable("tessedit_char_blacklist", "º°•·●○▪▫■□¤§¨¸") }
        runCatching { api.setVariable("preserve_interword_spaces", "1") }

        when (image) {
            is OcrImage.Gray8 -> api.setImage(image.bytes, image.width, image.height, 1, image.rowStride)
            is OcrImage.Rgba8888 -> api.setImage(image.bytes, image.width, image.height, 4, image.rowStride)
        }

        val txt = api.utF8Text ?: ""
        api.end()
        return OcrPageResult(pageNo = 1, text = OcrSanitizer.sanitize(txt.trim()))
    }
}
