package com.example.docscan.ui.camera

import android.Manifest
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.docscan.logic.camera.bindCameraUseCases
import com.example.docscan.logic.camera.buildImageCapture
import com.example.docscan.logic.camera.takePhoto
import com.example.docscan.logic.processing.OpenCvProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CameraOneShotScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // Permission
    var permissionGranted by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }
    LaunchedEffect(Unit) { permissionLauncher.launch(Manifest.permission.CAMERA) }

    if (!permissionGranted) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Camera permission is required to test scanning.")
        }
        return
    }

    // Camera
    val previewView = remember { PreviewView(ctx) }
    val imageCapture = remember { buildImageCapture() }

    LaunchedEffect(Unit) {
        // Bind on first composition
        bindCameraUseCases(ctx, previewView, imageCapture)
    }

    var processedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var status by remember { mutableStateOf("Ready") }

    CameraOneShotContent(
        status = status,
        processedBitmap = processedBitmap,
        onTakePhoto = {
            status = "Capturing…"
            val file = File(ctx.cacheDir, "shot_${System.currentTimeMillis()}.jpg")
            takePhoto(
                context = ctx,
                imageCapture = imageCapture,
                outputFile = file,
                onSuccess = { savedFile ->
                    // Decode + process on background
                    scope.launch(Dispatchers.Default) {
                        val raw = BitmapFactory.decodeFile(savedFile.absolutePath)
                        if (raw == null) {
                            status = "Error: decode failed"
                        } else {
                            val result = OpenCvProcessor.cannyEdgesBitmap(raw)
                            processedBitmap = result
                            status = "Processed!"
                        }
                    }
                },
                onError = { e -> status = "Error: ${e.message ?: e.javaClass.simpleName}" }
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
private fun CameraOneShotContent(
    status: String,
    processedBitmap: android.graphics.Bitmap?,
    onTakePhoto: () -> Unit,
    onClear: () -> Unit,
    livePreview: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.systemBars.asPaddingValues())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "One-shot test: Capture → OpenCV (Canny) → Preview",
            style = MaterialTheme.typography.titleMedium
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clipToBounds()
        ) { livePreview() }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onTakePhoto) { Text("Take & Process") }
            if (processedBitmap != null) Button(onClick = onClear) { Text("Clear") }
        }

        Text(status)

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

@Preview(showBackground = true)
@Composable
private fun CameraOneShotScreenPreview() {
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
                ) { Text("Camera Preview Placeholder") }
            }
        )
    }
}