package com.example.docscan.ui

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.docscan.logic.utils.FileOps
import com.example.docscan.logic.utils.AndroidPdfExporter
import com.example.docscan.logic.utils.runPipelineAsync
import com.example.docscan.logic.utils.ScanSession
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isProcessing by remember { mutableStateOf(false) }
    var overlayPreview by remember { mutableStateOf<Bitmap?>(null) }
    var enhancedPreview by remember { mutableStateOf<Bitmap?>(null) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    isProcessing = true
                    val bmp = FileOps.loadImageFromUri(ctx, uri)
                    val res = runPipelineAsync(bmp, mode = "color")
                    overlayPreview = res.overlay
                    enhancedPreview = res.enhanced
                    ScanSession.add(res.page)
                    snackbarHostState.showSnackbar("Added page #${ScanSession.count}")
                } catch (t: Throwable) {
                    snackbarHostState.showSnackbar("Import failed: ${t.message}")
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    val takePicturePreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            scope.launch {
                try {
                    isProcessing = true
                    val res = runPipelineAsync(bmp, mode = "color")
                    overlayPreview = res.overlay
                    enhancedPreview = res.enhanced
                    ScanSession.add(res.page)
                    snackbarHostState.showSnackbar("Captured page #${ScanSession.count}")
                } catch (t: Throwable) {
                    snackbarHostState.showSnackbar("Capture failed: ${t.message}")
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    val requestCameraPermission = rememberCameraPermissionLauncher(
        onGranted = { takePicturePreviewLauncher.launch(null) },
        onDenied = {
            scope.launch { snackbarHostState.showSnackbar("Camera permission denied") }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "DocScan",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    Text(
                        "Pages: ${ScanSession.count}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            BottomActionBar(
                isProcessing = isProcessing,
                onImport = { pickImageLauncher.launch("image/*") },
                onCapture = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        // On Android 13+, TakePicturePreview doesn't need explicit permission,
                        // but we keep a unified flow for clarity.
                        takePicturePreviewLauncher.launch(null)
                    } else {
                        requestCameraPermission.launch(Manifest.permission.CAMERA)
                    }
                },
                onExport = {
                    scope.launch {
                        if (ScanSession.isEmpty) {
                            snackbarHostState.showSnackbar("No pages to export")
                        } else {
                            try {
                                val out = File(ctx.cacheDir, "scan_${System.currentTimeMillis()}.pdf")
                                AndroidPdfExporter(ctx).export(ScanSession.snapshot(), out)
                                snackbarHostState.showSnackbar("Exported PDF: ${out.absolutePath}")
                                // optionally clear after export:
                                // ScanSession.clear()
                            } catch (t: Throwable) {
                                snackbarHostState.showSnackbar("Export failed: ${t.message}")
                            }
                        }
                    }
                },
                onClear = {
                    ScanSession.clear()
                    overlayPreview = null
                    enhancedPreview = null
                    scope.launch { snackbarHostState.showSnackbar("Cleared all pages") }
                }
            )
        }
    ) { inner ->
        PreviewPane(
            overlay = overlayPreview,
            enhanced = enhancedPreview,
            isProcessing = isProcessing,
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
        )
    }
}

@Composable
private fun BottomActionBar(
    isProcessing: Boolean,
    onImport: () -> Unit,
    onCapture: () -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit
) {
    Surface(tonalElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                enabled = !isProcessing,
                onClick = onImport
            ) { Text("Import") }

            OutlinedButton(
                enabled = !isProcessing,
                onClick = onCapture
            ) { Text("Capture") }

            Spacer(Modifier.weight(1f))

            OutlinedButton(
                enabled = !isProcessing && ScanSession.count > 0,
                onClick = onClear
            ) { Text("Clear") }

            Button(
                enabled = !isProcessing && ScanSession.count > 0,
                onClick = onExport
            ) { Text("Export (${ScanSession.count})") }
        }
    }
}

@Composable
private fun PreviewPane(
    overlay: Bitmap?,
    enhanced: Bitmap?,
    isProcessing: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isProcessing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Text("Preview", style = MaterialTheme.typography.titleMedium)

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 180.dp),
                contentAlignment = Alignment.Center
            ) {
                if (overlay != null) {
                    Image(
                        bitmap = overlay.asImageBitmap(),
                        contentDescription = "Overlay",
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text("Overlay will appear here", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 180.dp),
                contentAlignment = Alignment.Center
            ) {
                if (enhanced != null) {
                    Image(
                        bitmap = enhanced.asImageBitmap(),
                        contentDescription = "Enhanced",
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text("Enhanced will appear here", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (!isProcessing) {
            AssistChipsRow()
        }
    }
}

@Composable
private fun AssistChipsRow() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistChip(
            onClick = {},
            label = { Text("Mode: color") },
            enabled = false
        )
        AssistChip(
            onClick = {},
            label = { Text("Pages: ${ScanSession.count}") },
            enabled = false
        )
    }
}

/** Camera permission launcher helper */
@Composable
private fun rememberCameraPermissionLauncher(
    onGranted: () -> Unit,
    onDenied: () -> Unit
): ManagedActivityResultLauncher<String, Boolean> {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onGranted() else onDenied()
    }
    return launcher
}
