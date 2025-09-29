package com.example.docscan

import android.Manifest
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import com.example.docscan.logic.camera.CameraController
import com.example.docscan.logic.io.PhotoFileStore
import com.example.docscan.logic.processing.ImageProcessor
import com.example.docscan.logic.utils.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var bottomImageView: ImageView
    private lateinit var btnCapture: Button
    private lateinit var btnPick: Button
    private lateinit var statusView: TextView

    // Debug UI
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

    // Gallery picker
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { handlePickedImage(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ---------- Root vertical layout ----------
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // ---------- Live camera preview ----------
        previewView = PreviewView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        root.addView(previewView)

        // ---------- Controls row ----------
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

        btnPick = Button(this).apply {
            text = "Pick from Gallery"
            setOnClickListener { pickImageLauncher.launch("image/*") }
        }
        controls.addView(btnPick)

        statusView = TextView(this).apply {
            text = "Ready"
            setPadding(24, 0, 0, 0)
        }
        controls.addView(statusView)

        debugToggle = TextView(this).apply {
            text = "Show Debug"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(24, 0, 0, 0)
            setOnClickListener { toggleDebug() }
        }
        controls.addView(debugToggle)

        root.addView(controls)

        // ---------- Bottom image preview ----------
        bottomImageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(240)
            )
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        root.addView(bottomImageView)

        // ---------- Debug console ----------
        debugText = TextView(this).apply {
            setTextIsSelectable(true)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(16)
        }
        debugScroll = ScrollView(this).apply {
            isFillViewport = true
            visibility = android.view.View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(180)
            )
            addView(debugText)
        }
        root.addView(debugScroll)

        setContentView(root)

        cameraController = CameraController(this)
        fileStore = PhotoFileStore(this)

        requestCameraPermission.launch(Manifest.permission.CAMERA)

        lifecycleScope.launch {
            DebugLog.stream.collectLatest { lines ->
                debugText.text = lines.joinToString("\n")
                if (debugScroll.visibility == android.view.View.VISIBLE) {
                    debugScroll.post { debugScroll.fullScroll(android.view.View.FOCUS_DOWN) }
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
            cameraController.initProviderIfNeeded()
            cameraController.bindUseCases(
                lifecycleOwner = this,
                surfaceProvider = previewView.surfaceProvider,
                useBackCamera = true
            )
            status("Camera ready")
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

                val outFile = withContext(Dispatchers.IO) { fileStore.newTempJpeg("raw") }
                val savedFile = awaitCaptureToFile(outFile)

                val original = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(savedFile.absolutePath)
                        ?: error("Decode returned null")
                }

                processAndShow(original)

            } catch (t: Throwable) {
                status("Capture error: ${t.message}")
                DebugLog.e("Capture failed: ${t.message}", tr = t)
                toast("Error: ${t.message}")
            } finally {
                btnCapture.isEnabled = true
            }
        }
    }

    private fun handlePickedImage(uri: Uri) {
        lifecycleScope.launch {
            try {
                status("Loading image…")
                val bitmap = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri).use { input ->
                        BitmapFactory.decodeStream(input)
                            ?: error("Decode returned null")
                    }
                }
                processAndShow(bitmap)
            } catch (t: Throwable) {
                status("Pick error: ${t.message}")
                DebugLog.e("Pick failed: ${t.message}", tr = t)
                toast("Error: ${t.message}")
            }
        }
    }

    private suspend fun processAndShow(bitmap: android.graphics.Bitmap) {
        val result = withContext(Dispatchers.Default) {
            val warpedOut = File(getExternalFilesDir(null), "scanned_page.jpg")
            ImageProcessor.processDocument(bitmap, outFile = warpedOut)
        }

        bottomImageView.setImageBitmap(result.bitmap)

        if (result.quad == null) {
            status("No paper detected")
            toast("No paper detected")
        } else {
            status("Contour drawn & warped")
            toast("Contour drawn")
            DebugLog.i("Quad: " + result.quad.joinToString { "(${it.x.toInt()},${it.y.toInt()})" })
            result.file?.let {
                DebugLog.i("Warped saved at: ${it.absolutePath}")
            }
        }
    }

    private suspend fun awaitCaptureToFile(target: File): File =
        suspendCancellableCoroutine { cont ->
            cameraController.takePhoto(
                outputFile = target,
                onSaved = { file ->
                    if (cont.isActive) cont.resume(file)
                },
                onError = { err ->
                    if (cont.isActive) cont.resumeWithException(err)
                }
            )
        }

    private fun toggleDebug() {
        debugVisible = !debugVisible
        debugScroll.visibility = if (debugVisible) android.view.View.VISIBLE else android.view.View.GONE
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
