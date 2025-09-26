package com.example.docscan.logic.camera

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Builds a single ImageCapture instance for this screen.
 */
fun buildImageCapture(): ImageCapture =
    ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .build()

/**
 * Bind the preview + image capture to lifecycle.
 */
suspend fun bindCameraUseCases(
    context: Context,
    previewView: PreviewView,
    imageCapture: ImageCapture
) {
    val provider = ProcessCameraProvider.getInstance(context).get()

    val preview = CameraPreview.Builder().build().also {
        it.setSurfaceProvider(previewView.surfaceProvider)
    }

    provider.unbindAll()
    provider.bindToLifecycle(
        (context as ComponentActivity),
        CameraSelector.DEFAULT_BACK_CAMERA,
        preview,
        imageCapture
    )
}

/**
 * Take a photo to the given file and return that file on success.
 */
fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    outputFile: File,
    onSuccess: (File) -> Unit,
    onError: (Throwable) -> Unit
) {
    val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()
    imageCapture.takePicture(
        options,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) = onError(exc)
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                onSuccess(outputFile)
            }
        }
    )
}