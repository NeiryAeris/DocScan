package com.example.docscan.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.docscan.logic.camera.CameraController
import com.example.docscan.logic.scan.PageSlot
import com.example.docscan.logic.session.SessionController
import com.example.docscan.logic.storage.AppStorage
import com.example.docscan.logic.utils.AndroidPdfExporter
import com.example.pipeline_core.EncodedPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val cameraController = remember { CameraController(context) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val sessionController = remember { SessionController(context) }
    val sessionState by sessionController.state.collectAsState()
    var enhanceMode by remember { mutableStateOf("color-pro") } // color-pro, auto-pro

    fun captureAndProcess() {
        val targetSlot = sessionState.slots.firstOrNull { it is PageSlot.Empty }
            ?: return
        val tempFile = File.createTempFile("raw_capture_", ".jpg", context.cacheDir)

        cameraController.takePhoto(
            outputFile = tempFile,
            onSaved = { file ->
                scope.launch {
                    val bytes = file.readBytes()
                    sessionController.processIntoSlot(targetSlot.index, bytes, enhanceMode)
                    file.delete()
                }
            },
            onError = { exc ->
                exc.printStackTrace()
                Toast.makeText(context, "Capture error: ${exc.message}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    fun finishAndExport() = scope.launch {
        val readySlots = sessionState.slots.filterIsInstance<PageSlot.Ready>()
        if (readySlots.isEmpty()) {
            Toast.makeText(context, "No pages to save", Toast.LENGTH_SHORT).show()
            return@launch
        }

        Toast.makeText(context, "Saving...", Toast.LENGTH_SHORT).show()

        try {
            val pagesToExport = withContext(Dispatchers.IO) {
                readySlots.map {
                    val jpegBytes = File(it.processedJpegPath).readBytes()
                    EncodedPage(png = jpegBytes, width = 0, height = 0)
                }
            }

            val outputDir = AppStorage.getPublicAppDir()
            if (outputDir == null) {
                Toast.makeText(context, "Cannot access storage directory", Toast.LENGTH_LONG).show()
                return@launch
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val outFile = File(outputDir, "Scan_$timestamp.pdf")

            withContext(Dispatchers.IO) {
                AndroidPdfExporter(context).export(pagesToExport, outFile)
            }

            Toast.makeText(context, "Saved to ${outFile.name}", Toast.LENGTH_LONG).show()
            sessionController.discardSession()
            navController.popBackStack()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error saving PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan (${sessionState.slots.count { it is PageSlot.Ready }})") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (sessionState.isDirty) {
                        IconButton(onClick = { finishAndExport() }) {
                            Icon(Icons.Default.Check, contentDescription = "Finish")
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 16.dp)
                ) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        items(sessionState.slots) { slot ->
                            SlotItemView(slot)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var showModeMenu by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { showModeMenu = true },
                                shape = CircleShape,
                                modifier = Modifier.size(64.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                val icon = if (enhanceMode == "color-pro") Icons.Default.ColorLens else Icons.Default.FilterBAndW
                                Icon(icon, contentDescription = "Change scan mode")
                            }
                            DropdownMenu(
                                expanded = showModeMenu,
                                onDismissRequest = { showModeMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Quét màu") },
                                    onClick = {
                                        enhanceMode = "color-pro"
                                        showModeMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.ColorLens, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Quét đen trắng") },
                                    onClick = {
                                        enhanceMode = "auto-pro"
                                        showModeMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.FilterBAndW, null) }
                                )
                            }
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(if (hasCameraPermission) MaterialTheme.colorScheme.primary else Color.Gray)
                                .clickable(enabled = hasCameraPermission, onClick = { captureAndProcess() })
                        ) {
                            Icon(Icons.Default.Camera, "Chụp", tint = Color.White, modifier = Modifier.size(40.dp))
                        }

                        Spacer(modifier = Modifier.size(64.dp))
                    }
                }
            }
        }
    ) { innerPadding ->
        if (hasCameraPermission) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                var previewView by remember { mutableStateOf<PreviewView?>(null) }
                if (previewView != null) {
                    LaunchedEffect(previewView) { cameraController.start(lifecycleOwner, previewView!!) }
                }
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view -> previewView = view }
                )
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Cần quyền truy cập Camera để quét tài liệu.")
            }
        }
    }
}

@Composable
fun SlotItemView(slot: PageSlot) {
    Box(
        modifier = Modifier
            .size(60.dp, 80.dp)
            .background(Color.DarkGray, MaterialTheme.shapes.small)
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, Color.White, MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center
    ) {
        when (slot) {
            is PageSlot.Empty -> Text("${slot.index + 1}", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
            is PageSlot.Processing -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
            is PageSlot.Ready -> {
                AsyncImage(
                    model = File(slot.processedJpegPath),
                    contentDescription = "Page ${slot.index}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = 0.6f)).padding(2.dp)) {
                    Text("${slot.index + 1}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
            is PageSlot.Failed -> Icon(Icons.Default.Close, "Error", tint = MaterialTheme.colorScheme.error)
        }
    }
}
