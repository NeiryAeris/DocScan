package com.example.docscan.logic.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.docscan.logic.utils.DebugLog
import java.io.File
import java.util.concurrent.Executor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import androidx.camera.view.PreviewView

class CameraController(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var preview: CameraPreview? = null
    private val mainExecutor: Executor by lazy { ContextCompat.getMainExecutor(context) }
    private var isBound: Boolean = false

    suspend fun initProviderIfNeeded(): ProcessCameraProvider {
        cameraProvider?.let { return it }
        val future = ProcessCameraProvider.getInstance(context)
        return suspendCancellableCoroutine { cont ->
            future.addListener(
                {
                    try {
                        val provider = future.get()
                        cameraProvider = provider
                        DebugLog.i("ProcessCameraProvider ready")
                        if (cont.isActive) cont.resume(provider)
                    } catch (t: Throwable) {
                        if (cont.isActive) cont.resumeWithException(t)
                    }
                },
                mainExecutor
            )
        }
    }

    fun bindUseCases(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: CameraPreview.SurfaceProvider,
        useBackCamera: Boolean = true,
        captureMode: Int = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
    ) {
        val provider = cameraProvider
            ?: throw IllegalStateException("initProviderIfNeeded() not called.")

        val cameraPreview = CameraPreview.Builder().build().also {
            it.setSurfaceProvider(surfaceProvider)
        }
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
        isBound = true
        DebugLog.i("Use cases bound: ${if (useBackCamera) "back" else "front"} camera")
    }

    fun unbindAll() {
        cameraProvider?.unbindAll()
        imageCapture = null
        preview = null
        isBound = false
    }

    fun takePhoto(
        outputFile: File,
        onSaved: (file: File) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val capture = imageCapture
            ?: return onError(IllegalStateException("ImageCapture not bound"))
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

    // Convenience suspend helper to start camera from a PreviewView and LifecycleOwner.
    suspend fun start(lifecycleOwner: LifecycleOwner, previewView: PreviewView, useBackCamera: Boolean = true) {
        if (isBound) return
        initProviderIfNeeded()
        bindUseCases(lifecycleOwner, previewView.surfaceProvider, useBackCamera)
    }

    fun isStarted(): Boolean = isBound
}
