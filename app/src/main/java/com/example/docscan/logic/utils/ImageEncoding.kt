package com.example.docscan.logic.utils

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

fun bitmapToJpegBytes(
    bitmap: Bitmap,
    quality: Int = 90
): ByteArray {
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
    return out.toByteArray()
}