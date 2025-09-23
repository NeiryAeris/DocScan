package com.example.docscan

import android.app.Application
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val ok = OpenCVLoader.initDebug()
        Log.i("OpenCV", "Loaded=$ok, version=${Core.getVersionString()}")
    }
}