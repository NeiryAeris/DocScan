package com.example.ocr.core.api

data class OcrWord(val text: String, val bbox: IntArray, val conf: Float?)
data class OcrPageResult(
    val pageNo: Int,
    val text: String,
    val words: List<OcrWord> = emptyList(),
    val avgConf: Float? = null,
    val durationMs: Long? = null
)
data class OcrDocumentResult(
    val pages: List<OcrPageResult>
) {
    val fullText: String get() = pages.joinToString("\n") { it.text }.trimEnd()
}
