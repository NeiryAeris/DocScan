package com.example.docscan

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview as CameraPreview   // 👈 alias to avoid conflict
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File
import androidx.core.graphics.createBitmap

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme { CameraOneShotScreen() }
        }
    }
}

@Composable
fun CameraOneShotScreen() {
    val ctx = LocalContext.current
    var permissionGranted by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }
    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!permissionGranted) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Camera permission is required to test scanning.")
        }
        return
    }

    val previewView = remember { PreviewView(ctx) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

    LaunchedEffect(Unit) {
        val provider = ProcessCameraProvider.getInstance(ctx).get()
        val preview = CameraPreview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        provider.unbindAll()
        provider.bindToLifecycle(
            (ctx as ComponentActivity),
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageCapture
        )
    }

    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf("Ready") }

    CameraOneShotContent(
        status = status,
        processedBitmap = processedBitmap,
        onTakePhoto = {
            status = "Capturing…"
            takePhotoThenProcess(
                context = ctx,
                imageCapture = imageCapture,
                onSuccess = { bmp ->
                    processedBitmap = bmp
                    status = "Processed!"
                },
                onError = { e ->
                    status = "Error: ${e.message ?: e.javaClass.simpleName}"
                }
            )
        },
        onClear = {
            processedBitmap = null
            status = "Ready"
        },
        livePreview = {
            AndroidView({ previewView }, modifier = Modifier.fillMaxSize())
        }
    )
}

@Composable
fun CameraOneShotContent(
    status: String,
    processedBitmap: Bitmap?,
    onTakePhoto: () -> Unit,
    onClear: () -> Unit,
    livePreview: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.systemBars.asPaddingValues()) // keep safe area
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title
        Text(
            "One-shot test: Capture → OpenCV (Canny) → Preview",
            style = MaterialTheme.typography.titleMedium
        )

        // Camera Preview (fixed height, clipped to bounds)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clipToBounds()
        ) {
            livePreview()
        }

        // Buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(onClick = onTakePhoto) { Text("Take & Process") }
            if (processedBitmap != null) {
                Button(onClick = onClear) { Text("Clear") }
            }
        }

        // Status
        Text(status)

        // Processed result
        processedBitmap?.let { bmp ->
            Text("Processed result (edges):")
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
            )
        }
    }
}

private fun takePhotoThenProcess(
    context: Context,
    imageCapture: ImageCapture,
    onSuccess: (Bitmap) -> Unit,
    onError: (Throwable) -> Unit
) {
    try {
        val file = File(context.cacheDir, "shot_${System.currentTimeMillis()}.jpg")
        val output = ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture.takePicture(
            output,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) = onError(exc)
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val raw = BitmapFactory.decodeFile(file.absolutePath) ?: run {
                        onError(IllegalStateException("Decode failed")); return
                    }
                    val processed = cannyEdgesBitmap(raw)
                    onSuccess(processed)
                }
            }
        )
    } catch (t: Throwable) {
        onError(t)
    }
}

private fun cannyEdgesBitmap(orig: Bitmap): Bitmap {
    val srcBmp = if (orig.config != Bitmap.Config.ARGB_8888)
        orig.copy(Bitmap.Config.ARGB_8888, false) else orig

    val src = Mat(srcBmp.height, srcBmp.width, CvType.CV_8UC4)
    Utils.bitmapToMat(srcBmp, src)

    val gray = Mat()
    Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
    Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

    val edges = Mat()
    Imgproc.Canny(gray, edges, 75.0, 200.0)

    val edgesRgba = Mat()
    Imgproc.cvtColor(edges, edgesRgba, Imgproc.COLOR_GRAY2RGBA)

    val out = createBitmap(edgesRgba.cols(), edgesRgba.rows())
    Utils.matToBitmap(edgesRgba, out)
    return out
}

@Preview(showBackground = true, name = "Camera Screen Preview")
@Composable
fun CameraOneShotScreenPreview() {
    MaterialTheme {
        CameraOneShotContent(
            status = "Ready",
            processedBitmap = null,
            onTakePhoto = {},
            onClear = {},
            livePreview = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Camera Preview Placeholder")
                }
            }
        )
    }
}