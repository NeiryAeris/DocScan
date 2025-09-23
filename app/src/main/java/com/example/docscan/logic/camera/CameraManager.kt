package com.example.docscan.logic.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.ImageCapture

class CameraManager(private val context: Context) {

    private var imageCapture: ImageCapture? = null

    fun startCamera() {
        // TODO: Initialize CameraX Preview + ImageCapture
    }

    fun captureImage(onResult: (Bitmap?) -> Unit) {
        // TODO: Capture image and return Bitmap
        // Use imageCapture?.takePicture()
    }

    fun switchCamera() {
        // TODO: Switch between front/back camera
    }

    fun toggleFlash(enabled: Boolean) {
        // TODO: Enable/disable flash
    }
}