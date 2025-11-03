package com.example.ocr.core.api

/**
 * Geometry for OCR annotations.
 */
data class OcrPoint(
    val x: Int,
    val y: Int,
)

/**
 * Axis-aligned bounding box around a text element.
 */
data class OcrBoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(right >= left) { "right must be >= left" }
        require(bottom >= top) { "bottom must be >= top" }
    }

    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Lowest level text element (roughly a word or token).
 */
data class OcrTextElement(
    val text: String,
    val boundingBox: OcrBoundingBox?,
    val cornerPoints: List<OcrPoint>,
    val confidence: Float?
)

/**
 * Line of text returned by OCR.
 */
data class OcrTextLine(
    val text: String,
    val boundingBox: OcrBoundingBox?,
    val cornerPoints: List<OcrPoint>,
    val elements: List<OcrTextElement>
)

/**
 * Paragraph or block grouping of OCR results.
 */
data class OcrTextBlock(
    val text: String,
    val boundingBox: OcrBoundingBox?,
    val cornerPoints: List<OcrPoint>,
    val lines: List<OcrTextLine>
)

/**
 * Full OCR response for a single image.
 */
data class OcrResult(
    val text: String,
    val blocks: List<OcrTextBlock>,
    val width: Int,
    val height: Int,
)

/**
 * Exception thrown by OCR engines when recognition fails.
 */
class OcrException(message: String, cause: Throwable? = null) : Exception(message, cause)
