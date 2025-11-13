package com.example.ocr.core.api

sealed interface OcrImage {
    val width: Int
    val height: Int

    /** Single-channel 8-bit grayscale. */
    data class Gray8(
        override val width: Int,
        override val height: Int,
        val bytes: ByteArray,
        val rowStride: Int, // bytes per row (== width)
    ) : OcrImage

    /** RGBA, 8bpc. */
    data class Rgba8888(
        override val width: Int,
        override val height: Int,
        val bytes: ByteArray,
        val rowStride: Int, // bytes per row (typically width*4)
        val premultiplied: Boolean = false
    ) : OcrImage
}
