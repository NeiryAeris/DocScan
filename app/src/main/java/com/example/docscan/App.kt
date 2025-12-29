package com.example.docscan

import android.app.Application
import android.net.Uri
import android.util.Log
import com.example.docscan.auth.FirebaseIdTokenStore
import com.example.docscan.logic.ocr.OcrGatewayImpl
import com.example.docscan.logic.utils.NodeCloudOcrGateway
import com.example.docscan.logic.utils.logging.DebugLog
import com.example.ocr_remote.RemoteDriveClientImpl
import com.example.ocr_remote.RemoteOcrClientImpl
import com.example.ocr_remote.RemoteHandwritingClientImpl
import com.example.pipeline_core.legacy.DocumentPipeline
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        val ok = OpenCVLoader.initDebug()
        Log.i("OpenCV", "Loaded=$ok, version=${Core.getVersionString()}")

        DocumentPipeline.init()

        // Start Firebase token cache (Drive/AI/Documents need this)
        FirebaseIdTokenStore.start()

        val baseUrl = "https://gateway.neirylittlebox.com"

        // OCR (public)
        val remoteOcrClient = RemoteOcrClientImpl(
            baseUrl = baseUrl,
            authTokenProvider = { null } // OCR does not need auth
        )
        val cloudGateway = NodeCloudOcrGateway(remoteOcrClient)
        ocrGateway = OcrGatewayImpl(cloudGateway)

        // Handwriting (public)
        handwritingClient = RemoteHandwritingClientImpl(baseUrl)

        // Drive (Firebase auth required)
        driveClient = RemoteDriveClientImpl(
            baseUrl = baseUrl,
            authTokenProvider = { FirebaseIdTokenStore.get() }
        )

        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            DebugLog.e("FATAL in thread ${t.name}", tr = e)
        }
    }

    companion object {
        lateinit var ocrGateway: OcrGatewayImpl
            private set

        lateinit var driveClient: RemoteDriveClientImpl
            private set

        lateinit var handwritingClient: RemoteHandwritingClientImpl
            private set

        var pdfToSign: Uri? = null
    }
}