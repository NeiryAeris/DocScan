package com.example.ocr.mlkit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.graphics.Rect
import com.example.ocr.core.api.OcrBoundingBox
import com.example.ocr.core.api.OcrEngine
import com.example.ocr.core.api.OcrException
import com.example.ocr.core.api.OcrImageFormat
import com.example.ocr.core.api.OcrPoint
import com.example.ocr.core.api.OcrRequest
import com.example.ocr.core.api.OcrResult
import com.example.ocr.core.api.OcrTextBlock
import com.example.ocr.core.api.OcrTextElement
import com.example.ocr.core.api.OcrTextLine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizerOptionsInterface
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.io.Closeable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit implementation of [OcrEngine] that performs text recognition fully on device.
 */
class MlKitOcrEngine(
    options: TextRecognizerOptionsInterface = TextRecognizerOptions.DEFAULT_OPTIONS
) : OcrEngine, Closeable {

    private val recognizer = TextRecognition.getClient(options)

    override suspend fun recognize(request: OcrRequest): OcrResult {
        val image = try {
            request.toInputImage()
        } catch (t: Throwable) {
            throw OcrException("Unable to prepare image for OCR", t)
        }

        val text = try {
            recognizer.process(image).await()
        } catch (t: Throwable) {
            throw OcrException("Text recognition failed", t)
        }

        return text.toResult(request.width, request.height)
    }

    override fun close() {
        recognizer.close()
    }
}

/**
 * Create an [OcrRequest] from a [Bitmap]. The bitmap is compressed to JPEG internally.
 */
fun OcrRequest.Companion.fromBitmap(
    bitmap: Bitmap,
    rotationDegrees: Int = 0,
    jpegQuality: Int = 95,
): OcrRequest {
    require(jpegQuality in 1..100) { "jpegQuality must be within 1..100" }
    val output = ByteArrayOutputStream()
    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, output)) {
        throw IllegalArgumentException("Failed to compress bitmap for OCR request")
    }
    val bytes = output.toByteArray()
    return OcrRequest(
        bytes = bytes,
        width = bitmap.width,
        height = bitmap.height,
        rotationDegrees = rotationDegrees,
        format = OcrImageFormat.JPEG,
    )
}

private fun OcrRequest.toInputImage(): InputImage = when (format) {
    OcrImageFormat.NV21 -> InputImage.fromByteArray(
        bytes,
        width,
        height,
        rotationDegrees,
        InputImage.IMAGE_FORMAT_NV21
    )

    OcrImageFormat.JPEG -> {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw OcrException("Unable to decode JPEG bytes for OCR")
        InputImage.fromBitmap(bitmap, rotationDegrees)
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result -> if (cont.isActive) cont.resume(result) }
        addOnFailureListener { error -> if (cont.isActive) cont.resumeWithException(error) }
        cont.invokeOnCancellation { cancel() }
    }

private fun Text.toResult(width: Int, height: Int): OcrResult = OcrResult(
    text = text,
    blocks = textBlocks.map { it.toBlock() },
    width = width,
    height = height,
)

private fun Text.TextBlock.toBlock(): OcrTextBlock = OcrTextBlock(
    text = text,
    boundingBox = boundingBox.toBoundingBox(),
    cornerPoints = cornerPoints.toPoints(),
    lines = lines.map { it.toLine() }
)

private fun Text.Line.toLine(): OcrTextLine = OcrTextLine(
    text = text,
    boundingBox = boundingBox.toBoundingBox(),
    cornerPoints = cornerPoints.toPoints(),
    elements = elements.map { it.toElement() }
)

private fun Text.Element.toElement(): OcrTextElement = OcrTextElement(
    text = text,
    boundingBox = boundingBox.toBoundingBox(),
    cornerPoints = cornerPoints.toPoints(),
    confidence = confidence
)

private fun Rect?.toBoundingBox(): OcrBoundingBox? = this?.let {
    OcrBoundingBox(it.left, it.top, it.right, it.bottom)
}

private fun Array<Point>?.toPoints(): List<OcrPoint> = this?.map { OcrPoint(it.x, it.y) } ?: emptyList()
