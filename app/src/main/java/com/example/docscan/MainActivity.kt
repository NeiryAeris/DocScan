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
import androidx.core.graphics.createBitmap
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import com.example.docscan.logic.camera.CameraController
import com.example.docscan.logic.io.PhotoFileStore
import com.example.docscan.logic.io.PdfFileStore
import com.example.docscan.logic.processing.ImageProcessor
import com.example.docscan.logic.utils.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var bottomImageView: ImageView
    private lateinit var btnCapture: Button
    private lateinit var btnPick: Button
    private lateinit var btnSavePdf: Button
    private lateinit var statusView: TextView

    // Debug UI
    private lateinit var debugToggle: TextView
    private lateinit var debugScroll: ScrollView
    private lateinit var debugText: TextView
    private var debugVisible = false

    // Gallery-style result preview
    private lateinit var resultContainer: LinearLayout
    private lateinit var fullImageView: ImageView
    private lateinit var btnBackToCamera: Button

    private lateinit var cameraController: CameraController
    private lateinit var fileStore: PhotoFileStore

    // Store multiple scanned images
    private val scannedFiles = mutableListOf<File>()

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
            text = "Pick"
            setOnClickListener { pickImageLauncher.launch("image/*") }
        }
        controls.addView(btnPick)

        btnSavePdf = Button(this).apply {
            text = "Save PDF"
            setOnClickListener { onSavePdfClicked() }
        }
        controls.addView(btnSavePdf)

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

        // ---------- Fullscreen processed image ----------
        resultContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.CENTER
            visibility = android.view.View.GONE
        }

        fullImageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        resultContainer.addView(fullImageView)

        btnBackToCamera = Button(this).apply {
            text = "← Back"
            setOnClickListener { returnToCamera() }
        }
        resultContainer.addView(btnBackToCamera)

        root.addView(resultContainer)

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
            val warpedOut = File(getExternalFilesDir(null), "scanned_page_${System.currentTimeMillis()}.jpg")
            ImageProcessor.processDocument(bitmap, outFile = warpedOut)
        }

        val enhancedBitmap = result.enhanced?.let {
            val warpedBmp = createBitmap(it.cols(), it.rows())
            Utils.matToBitmap(it, warpedBmp)
            warpedBmp
        } ?: result.bitmap

        // Add to list for PDF export
        result.file?.let { scannedFiles.add(it) }

        bottomImageView.setImageBitmap(enhancedBitmap)
        fullImageView.setImageBitmap(enhancedBitmap)

        previewView.visibility = android.view.View.GONE
        btnCapture.visibility = android.view.View.GONE
        btnPick.visibility = android.view.View.GONE
        btnSavePdf.visibility = android.view.View.GONE
        resultContainer.visibility = android.view.View.VISIBLE

        status("Captured page ${scannedFiles.size}")
        toast("Captured page ${scannedFiles.size}")
    }

    private fun returnToCamera() {
        resultContainer.visibility = android.view.View.GONE
        previewView.visibility = android.view.View.VISIBLE
        btnCapture.visibility = android.view.View.VISIBLE
        btnPick.visibility = android.view.View.VISIBLE
        btnSavePdf.visibility = android.view.View.VISIBLE
        status("Ready for next page")
    }

    private fun onSavePdfClicked() {
        if (scannedFiles.isEmpty()) {
            toast("No pages scanned yet")
            return
        }
        lifecycleScope.launch {
            try {
                status("Creating PDF…")
                val pdfStore = PdfFileStore(this@MainActivity)
                val pdfFile = withContext(Dispatchers.IO) {
                    pdfStore.createPdfFromImages(scannedFiles)
                }
                status("PDF saved at: ${pdfFile.name}")
                toast("PDF saved: ${pdfFile.name}")
                DebugLog.i("PDF saved: ${pdfFile.absolutePath}")
                scannedFiles.clear() // reset after saving
            } catch (t: Throwable) {
                status("PDF error: ${t.message}")
                toast("Error: ${t.message}")
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
