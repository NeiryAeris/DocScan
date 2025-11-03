package com.example.ocr.core.api

/**
 * Supported encodings for providing image data to the OCR engine.
 */
enum class OcrImageFormat {
    /** YUV NV21 byte array (CameraX default). */
    NV21,

    /** Encoded JPEG byte array. */
    JPEG,
}

/**
 * Request describing the image that should be processed for OCR.
 */
data class OcrRequest(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val format: OcrImageFormat,
    val languageHints: List<String> = emptyList(),
) {
    init {
        require(width > 0 && height > 0) { "width/height must be > 0" }
        require(rotationDegrees % 90 == 0) { "rotationDegrees must be a multiple of 90" }
    }

    companion object
}

/**
 * Contract for OCR providers used by the pipeline.
 */
interface OcrEngine {
    /**
     * Performs OCR on the supplied image and returns structured text annotations.
     * @throws OcrException if the engine fails to produce a result
     */
    suspend fun recognize(request: OcrRequest): OcrResult
}
