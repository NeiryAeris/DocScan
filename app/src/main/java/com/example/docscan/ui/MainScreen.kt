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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.docscan.logic.scan.DraftStore
import com.example.docscan.logic.utils.FileOps
import com.example.docscan.logic.utils.runCamScanAsync
import com.example.docscan.ui.navigation.Routes
import com.example.imaging_opencv_android.OpenCvImaging
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavController? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isProcessing by remember { mutableStateOf(false) }
    var overlayPreview by remember { mutableStateOf<Bitmap?>(null) }
    var enhancedPreview by remember { mutableStateOf<Bitmap?>(null) }

    val draftStore = remember { DraftStore(ctx) }
    var sessionId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    val processedPages = remember { mutableStateListOf<String>() } // absolute paths to processed JPEGs

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    isProcessing = true
                    val bmp = FileOps.loadImageFromUri(ctx, uri)

                    val imaging = OpenCvImaging()
                    val res = runCamScanAsync(imaging = imaging, src = bmp)

                    overlayPreview = res.overlay
                    enhancedPreview = res.enhanced

                    val outFile = draftStore.writeProcessedJpeg(sessionId, processedPages.size, res.outJpeg)
                    processedPages += outFile.absolutePath

                    snackbarHostState.showSnackbar("Added page #${processedPages.size}")
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

                    val imaging = OpenCvImaging()
                    val res = runCamScanAsync(imaging = imaging, src = bmp)

                    overlayPreview = res.overlay
                    enhancedPreview = res.enhanced

                    val outFile = draftStore.writeProcessedJpeg(sessionId, processedPages.size, res.outJpeg)
                    processedPages += outFile.absolutePath

                    snackbarHostState.showSnackbar("Captured page #${processedPages.size}")
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
        onDenied = { scope.launch { snackbarHostState.showSnackbar("Camera permission denied") } }
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
                    IconButton(onClick = { navController?.navigate(Routes.TextExtraction) }) {
                        Icon(imageVector = Icons.Default.TextFields, contentDescription = "Trích xuất văn bản")
                    }
                    Text(
                        "Pages: ${processedPages.size}",
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
                pageCount = processedPages.size,
                onImport = { pickImageLauncher.launch("image/*") },
                onCapture = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        takePicturePreviewLauncher.launch(null)
                    } else {
                        requestCameraPermission.launch(Manifest.permission.CAMERA)
                    }
                },
                onExport = {
                    scope.launch {
                        if (processedPages.isEmpty()) {
                            snackbarHostState.showSnackbar("No pages to export")
                        } else {
                            val dir = draftStore.sessionDir(sessionId).absolutePath
                            snackbarHostState.showSnackbar(
                                "Export pending. Saved ${processedPages.size} page(s) in: $dir"
                            )
                        }
                    }
                },
                onClear = {
                    draftStore.clearSession(sessionId)
                    processedPages.clear()
                    sessionId = UUID.randomUUID().toString()
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
            pageCount = processedPages.size,
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
        )
    }
}

@Composable
private fun BottomActionBar(
    isProcessing: Boolean,
    pageCount: Int,
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

            OutlinedButton(
                enabled = !isProcessing && pageCount > 0,
                onClick = onClear
            ) { Text("Clear") }

            Button(
                enabled = !isProcessing && pageCount > 0,
                onClick = onExport
            ) { Text("Export ($pageCount)") }
        }
    }
}

@Composable
private fun PreviewPane(
    overlay: Bitmap?,
    enhanced: Bitmap?,
    isProcessing: Boolean,
    pageCount: Int,
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

        if (overlay != null) {
            Text("Overlay", style = MaterialTheme.typography.titleMedium)
            Image(
                bitmap = overlay.asImageBitmap(),
                contentDescription = "Overlay",
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text("Enhanced", style = MaterialTheme.typography.titleMedium)
        Box(
            modifier = Modifier
                .fillMaxWidth()
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

        if (!isProcessing) {
            AssistChipsRow(pageCount = pageCount)
        }
    }
}

@Composable
private fun AssistChipsRow(pageCount: Int) {
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
            label = { Text("Pages: $pageCount") },
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
