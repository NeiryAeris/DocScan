package com.example.docscan.logic.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

object ImageUtils {

    fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val planeProxy = image.planes[0]
        val buffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        return when (image.format) {
            ImageFormat.JPEG -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ImageFormat.YUV_420_888 -> {
                val yuvImage = YuvImage(
                    bytes,
                    image.format,
                    image.width,
                    image.height,
                    null
                )
                val out = ByteArrayOutputStream()
                yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
                val imageBytes = out.toByteArray()
                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            }
            else -> throw UnsupportedOperationException("Unsupported image format: ${image.format}")
        }
    }

    fun saveBitmapAndGetUri(context: Context, bitmap: Bitmap): Uri? {
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DocScan-ID")
            }
        }

        var uri: Uri? = null
        try {
            uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri == null) {
                throw Exception("Failed to create new MediaStore record.")
            }
            context.contentResolver.openOutputStream(uri)?.use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
            }
        } catch (e: Exception) {
            uri?.let { orphanUri ->
                context.contentResolver.delete(orphanUri, null, null)
            }
            uri = null
        }
        return uri
    }
}
