package com.example.docscan

import android.app.Application
import android.util.Log
import com.example.docscan.logic.ocr.OcrGatewayImpl
import com.example.docscan.logic.utils.NodeCloudOcrGateway
import com.example.docscan.logic.utils.logging.DebugLog
import com.example.ocr_remote.RemoteOcrClientImpl
import com.example.pipeline_core.legacy.DocumentPipeline
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val ok = OpenCVLoader.initDebug()
        Log.i("OpenCV", "Loaded=$ok, version=${Core.getVersionString()}")

        // Initialize the shared pipeline core (safe no-op if already loaded)
        DocumentPipeline.init()   // <-- add

        // Initialize OCR Gateway
        val baseUrl = "https://gateway.neirylittlebox.com"
        val authToken =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyXzEiLCJlbWFpbCI6ImRlbW9AZXhhbXBsZS5jb20iLCJpYXQiOjE3NjU4OTE5MTEsImV4cCI6MTc2NjQ5NjcxMX0.5UL4rGDR3TepmMBsi0pS97MHfhutpWcjGn8v4l93Q84"

        val remoteClient = RemoteOcrClientImpl(
            baseUrl = baseUrl,
            authTokenProvider = { authToken }
        )
        val cloudGateway = NodeCloudOcrGateway(remoteClient)
        ocrGateway = OcrGatewayImpl(cloudGateway)

        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            DebugLog.e("FATAL in thread ${t.name}", tr = e)
        }
    }

    companion object {
        lateinit var ocrGateway: OcrGatewayImpl
            private set
    }
}