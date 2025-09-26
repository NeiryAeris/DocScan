package com.example.docscan

import android.Manifest
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import com.example.docscan.logic.camera.CameraController
import com.example.docscan.logic.io.PhotoFileStore
import com.example.docscan.logic.utils.DebugLog            // ⬅️ NEW
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest        // ⬅️ NEW
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var imageView: ImageView
    private lateinit var btnCapture: Button
    private lateinit var statusView: TextView

    // ⬇️ Debug UI
    private lateinit var debugToggle: TextView
    private lateinit var debugScroll: ScrollView
    private lateinit var debugText: TextView
    private var debugVisible = false

    private lateinit var cameraController: CameraController
    private lateinit var fileStore: PhotoFileStore

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                lifecycleScope.launch { setupCamera() }
            } else {
                status("Permission denied")
                DebugLog.w("Camera permission denied")
                toast("Camera permission denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ---------- Minimal UI (camera preview + controls + debug console) ----------
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        previewView = PreviewView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        root.addView(previewView)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(16)
            gravity = Gravity.CENTER_VERTICAL
        }

        btnCapture = Button(this).apply {
            text = "Capture"
            setOnClickListener { onCaptureClicked() }
        }
        controls.addView(btnCapture)

        statusView = TextView(this).apply {
            text = "Ready"
            setPadding(24, 0, 0, 0)
        }
        controls.addView(statusView)

        // ⬇️ Debug toggle
        debugToggle = TextView(this).apply {
            text = "Show Debug"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(24, 0, 0, 0)
            setOnClickListener { toggleDebug() }
        }
        controls.addView(debugToggle)

        root.addView(controls)

        imageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(220)
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }
        root.addView(imageView)

        // ⬇️ Debug console (collapsed by default)
        debugText = TextView(this).apply {
            setTextIsSelectable(true)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(16)
        }

        debugScroll = ScrollView(this).apply {
            isFillViewport = true
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(180)
            )
            addView(debugText)
        }
        root.addView(debugScroll)

        setContentView(root)
        // ---------------------------------------------------------------------------

        cameraController = CameraController(this)
        fileStore = PhotoFileStore(this)

        requestCameraPermission.launch(Manifest.permission.CAMERA)

        // ⬇️ Subscribe to DebugLog stream to update on-screen console
        lifecycleScope.launch {
            DebugLog.stream.collectLatest { lines ->
                debugText.text = lines.joinToString("\n")
                // auto-scroll to bottom when visible
                if (debugScroll.visibility == View.VISIBLE) {
                    debugScroll.post { debugScroll.fullScroll(View.FOCUS_DOWN) }
                }
            }
        }

        DebugLog.i("MainActivity created")
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraController.unbindAll()
        DebugLog.i("MainActivity destroyed")
    }

    private suspend fun setupCamera() {
        try {
            status("Starting camera…")
            DebugLog.i("Initializing ProcessCameraProvider")
            cameraController.initProviderIfNeeded()

            DebugLog.i("Binding use cases (back camera)")
            cameraController.bindUseCases(
                lifecycleOwner = this,
                surfaceProvider = previewView.surfaceProvider,
                useBackCamera = true
            )
            status("Camera ready")
            DebugLog.i("Camera ready")
        } catch (t: Throwable) {
            status("Camera error: ${t.message}")
            DebugLog.e("setupCamera failed: ${t.message}", tr = t)
            toast("Camera error: ${t.message}")
        }
    }

    private fun onCaptureClicked() {
        lifecycleScope.launch {
            try {
                btnCapture.isEnabled = false
                status("Capturing…")
                DebugLog.i("Capture started")

                val outFile = withContext(Dispatchers.IO) { fileStore.newTempJpeg("raw") }
                DebugLog.d("Capture target: ${outFile.absolutePath}")

                val savedFile = awaitCaptureToFile(outFile)
                DebugLog.i("Capture saved: ${savedFile.absolutePath}")

                val bmp = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(savedFile.absolutePath)
                        ?: error("Decode returned null")
                }
                imageView.setImageBitmap(bmp)

                status("Saved: ${savedFile.name}")
                toast("Captured: ${savedFile.name}")
            } catch (t: Throwable) {
                status("Capture error: ${t.message}")
                DebugLog.e("Capture failed: ${t.message}", tr = t)
                toast("Error: ${t.message}")
            } finally {
                btnCapture.isEnabled = true
            }
        }
    }

    private suspend fun awaitCaptureToFile(target: File): File =
        suspendCancellableCoroutine { cont ->
            cameraController.takePhoto(
                outputFile = target,
                onSaved = { file ->
                    DebugLog.d("onImageSaved callback")
                    if (cont.isActive) cont.resume(file)
                },
                onError = { err ->
                    DebugLog.e("onImageSaved error: ${err.message}", tr = err)
                    if (cont.isActive) cont.resumeWithException(err)
                }
            )
        }

    private fun toggleDebug() {
        debugVisible = !debugVisible
        debugScroll.visibility = if (debugVisible) View.VISIBLE else View.GONE
        debugToggle.text = if (debugVisible) "Hide Debug" else "Show Debug"
    }

    private fun status(msg: String) {
        statusView.text = msg
        DebugLog.d("STATUS: $msg")
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun dp(px: Int): Int =
        (px * resources.displayMetrics.density).toInt()
}