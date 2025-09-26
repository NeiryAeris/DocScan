package com.example.docscan

import android.app.Application
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import com.example.docscan.logic.utils.DebugLog

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val ok = OpenCVLoader.initDebug()
        Log.i("OpenCV", "Loaded=$ok, version=${Core.getVersionString()}")
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            DebugLog.e("FATAL in thread ${t.name}", tr = e)
    }
}}