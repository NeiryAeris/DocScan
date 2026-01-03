package com.example.docscan

import android.app.Application
import android.net.Uri
import android.util.Log
import com.example.docscan.auth.FirebaseIdTokenStore
import com.example.docscan.logic.ocr.OcrGatewayImpl
import com.example.docscan.logic.utils.NodeCloudOcrGateway
import com.example.docscan.logic.utils.logging.DebugLog
import com.example.ocr_remote.RemoteChatClient
import com.example.ocr_remote.RemoteChatClientImpl
import com.example.ocr_remote.RemoteDriveClientImpl
import com.example.ocr_remote.RemoteOcrClientImpl
import com.example.ocr_remote.RemoteHandwritingClientImpl
import com.example.ocr_remote.RemoteAiClient
import com.example.ocr_remote.RemoteAiClientImpl
import com.example.pipeline_core.legacy.DocumentPipeline
import com.google.firebase.auth.FirebaseAuth
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

        // Chat (Firebase auth required)
        chatClient = RemoteChatClientImpl(
            baseUrl = baseUrl,
            authTokenProvider = { FirebaseIdTokenStore.get() }
        )

        aiClient = RemoteAiClientImpl(
            baseUrl = baseUrl,
            authTokenProvider = { FirebaseIdTokenStore.get() }
        )

        // Drive (Firebase auth required)
        driveClient = RemoteDriveClientImpl(
            baseUrl = baseUrl,
            authTokenProvider = { FirebaseIdTokenStore.get() }
        )

        // Listen to auth state changes
        FirebaseAuth.getInstance().addAuthStateListener { firebaseAuth ->
            isUserLoggedIn = firebaseAuth.currentUser != null
        }

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

        lateinit var chatClient: RemoteChatClient
            private set

        lateinit var aiClient: RemoteAiClient
            private set

        var isUserLoggedIn: Boolean = false
            private set

        var pdfToSign: Uri? = null
        
        var pdfToView: Uri? = null

        // Generic preview data
        var previewImageBytes: ByteArray? = null
        var previewTitle: String? = null
        var previewMimeType: String? = null
        var previewDefaultFileName: String? = null

        fun clearPreviewData() {
            previewImageBytes = null
            previewTitle = null
            previewMimeType = null
            previewDefaultFileName = null
        }
    }
}