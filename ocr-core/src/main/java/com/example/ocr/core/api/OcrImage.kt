package com.example.ocr.core.api

sealed interface OcrImage {
    val width: Int
    val height: Int

    /** Single-channel 8-bit grayscale. */
    data class Gray8(
        override val width: Int,
        override val height: Int,
        val bytes: ByteArray,
        val rowStride: Int,         // bytes per row
    ) : OcrImage

    /** RGBA, 8 bits per component, little-endian byte order. */
    data class Rgba8888(
        override val width: Int,
        override val height: Int,
        val bytes: ByteArray,
        val rowStride: Int,         // bytes per row (typically width*4)
        val premultiplied: Boolean = false
    ) : OcrImage
}
