package com.example.docscan.logic.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.Executor

/**
 * Controls CameraX: bind, unbind, capture.
 * No UI code here — a SurfaceProvider must be provided by whoever uses this.
 */
class CameraController(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var preview: CameraPreview? = null
    private val mainExecutor: Executor by lazy { ContextCompat.getMainExecutor(context) }

    /**
     * Initialize camera provider once. Safe to call multiple times; it caches.
     */
    suspend fun initProviderIfNeeded(): ProcessCameraProvider {
        val existing = cameraProvider
        if (existing != null) return existing

        val provider = ProcessCameraProvider.getInstance(context).get()
        cameraProvider = provider
        return provider
    }

    /**
     * Bind preview + image capture to lifecycle. Call after initProviderIfNeeded().
     * @param lifecycleOwner Activity or Fragment implementing LifecycleOwner
     * @param surfaceProvider A provider from PreviewView or your own SurfaceProvider
     * @param useBackCamera When true selects DEFAULT_BACK_CAMERA, otherwise front.
     * @param captureMode MAXIMIZE_QUALITY by default; set MINIMIZE_LATENCY if you need speed.
     */
    fun bindUseCases(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: CameraPreview.SurfaceProvider,
        useBackCamera: Boolean = true,
        captureMode: Int = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
    ) {
        val provider = cameraProvider
            ?: throw IllegalStateException("Call initProviderIfNeeded() before bindUseCases().")

        // Build Preview
        val cameraPreview = CameraPreview.Builder()
            .build()
            .also { it.setSurfaceProvider(surfaceProvider) }

        // Build ImageCapture
        val capture = ImageCapture.Builder()
            .setCaptureMode(captureMode)
            .build()

        provider.unbindAll()
        provider.bindToLifecycle(
            lifecycleOwner,
            if (useBackCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA,
            cameraPreview,
            capture
        )

        preview = cameraPreview
        imageCapture = capture
    }

    /**
     * Unbind everything (e.g., on lifecycle destroy).
     */
    fun unbindAll() {
        cameraProvider?.unbindAll()
        imageCapture = null
        preview = null
    }

    /**
     * Take a photo to the given file.
     * This runs callbacks on the mainExecutor (you can switch threads in the caller).
     */
    fun takePhoto(
        outputFile: File,
        onSaved: (file: File) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val capture = imageCapture
            ?: return onError(IllegalStateException("ImageCapture not bound. Did you call bindUseCases()?"))

        val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        capture.takePicture(
            options,
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) = onError(exception)
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onSaved(outputFile)
                }
            }
        )
    }
}