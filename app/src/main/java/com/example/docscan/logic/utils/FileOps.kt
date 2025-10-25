package com.example.docscan.logic.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.docscan.R
import java.io.InputStream

// Helper methods for file handling operations
class FileOps {

    companion object {

        // Parse the `Bitmap` from `inputStream`
        // Replace ONLY this function in your existing FileOps.kt
        fun loadImageFromUri(context: Context, imageUri: Uri): Bitmap {
            val maxDim = 3000 // optional safety to avoid 50MB+ allocations

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, imageUri)
                val raw = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    // CRITICAL: prevent HARDWARE config
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true

                    // Optional downscale at decode time to save RAM on giant photos
                    val w = info.size.width
                    val h = info.size.height
                    val biggest = maxOf(w, h)
                    if (biggest > maxDim) {
                        val sample = (biggest + maxDim - 1) / maxDim // ceil
                        decoder.setTargetSampleSize(sample)
                    }

                    // Keep sRGB to avoid weird color spaces
                    decoder.setTargetColorSpace(android.graphics.ColorSpace.get(
                        android.graphics.ColorSpace.Named.SRGB
                    ))
                }

                // Ensure ARGB_8888 + mutable (some devices still return immutable)
                if (raw.config != Bitmap.Config.ARGB_8888 || !raw.isMutable) {
                    raw.copy(Bitmap.Config.ARGB_8888, /*mutable=*/true).also { raw.recycle() }
                } else {
                    raw
                }
            } else {
                // Legacy path: decode as ARGB_8888, mutable, with bounds->sample to avoid OOM
                val resolver = context.contentResolver
                // 1) bounds
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

                val sample = run {
                    val biggest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
                    if (biggest > maxDim) (biggest + maxDim - 1) / maxDim else 1
                }

                // 2) decode
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inMutable = true
                    inSampleSize = sample
                }
                val bmp = resolver.openInputStream(imageUri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                } ?: error("Cannot decode image from $imageUri")

                if (bmp.config != Bitmap.Config.ARGB_8888 || !bmp.isMutable) {
                    bmp.copy(Bitmap.Config.ARGB_8888, true).also { bmp.recycle() }
                } else {
                    bmp
                }
            }
        }
    }
}