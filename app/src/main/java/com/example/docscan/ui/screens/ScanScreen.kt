package com.example.docscan.ui.screens

import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaScannerConnection
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.docscan.App
import com.example.docscan.data.UserPreferencesRepository
import com.example.docscan.logic.camera.CameraController
import com.example.docscan.logic.scan.PageSlot
import com.example.docscan.logic.session.SessionController
import com.example.docscan.logic.storage.AppStorage
import com.example.docscan.logic.utils.AndroidPdfExporter
import com.example.docscan.logic.ai.AiIndexing
import com.example.pipeline_core.EncodedPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(navController: NavController, imageUri: Uri? = null, pdfUri: Uri? = null) {
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
        if (imageUri == null && pdfUri == null && !hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val sessionController = remember { SessionController(context) }
    val sessionState by sessionController.state.collectAsState()
//    var enhanceMode by remember { mutableStateOf("color-pro") }
    var enhanceMode by remember { mutableStateOf("color_pro") }

    fun finishAndExport(andPop: Boolean = true) {
        scope.launch(Dispatchers.IO) {
            val readySlots = sessionState.slots.filterIsInstance<PageSlot.Ready>()
            if (readySlots.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "No pages to save", Toast.LENGTH_SHORT).show()
                    if (andPop) navController.popBackStack()
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Saving...", Toast.LENGTH_SHORT).show()
            }

            try {
                val pagesToExport = readySlots.map {
                    val jpegBytes = File(it.processedJpegPath).readBytes()
                    EncodedPage(png = jpegBytes, width = 0, height = 0)
                }

                val outputDir = AppStorage.getPublicAppDir()
                if (outputDir == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Cannot access storage directory", Toast.LENGTH_LONG).show()
                        if (andPop) navController.popBackStack()
                    }
                    return@launch
                }

                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
                val outFile = File(outputDir, "Scan_$timestamp.pdf")

                AndroidPdfExporter(context).export(pagesToExport, outFile)

                // Local RAG indexing (your current behavior)
                try {
                    AiIndexing.indexPdf(context, outFile, title = outFile.nameWithoutExtension)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // ✅ Drive backup + Drive->Index sync (non-blocking UX; errors don't prevent save)
                try {
                    // Skip huge files (gateway default upload limit is typically ~25MB)
                    val maxBytes = 25L * 1024 * 1024
                    if (outFile.length() <= maxBytes) {
                        val status = App.driveClient.status()
                        if (status.linked) {
                            if (status.folderId == null) {
                                App.driveClient.initFolder()
                            }

                            App.driveClient.upload(
                                bytes = outFile.readBytes(),
                                filename = outFile.name,
                                mimeType = "application/pdf"
                            )

                            // Optional but recommended: server indexes Drive files -> searchable in RAG
                            val sync = App.driveClient.sync()

                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "Drive: uploaded. Indexed=${sync.counts.indexed}, skipped=${sync.counts.skipped}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Drive: skipped (PDF > 25MB)", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Drive backup failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }

                // Notify the media scanner
                MediaScannerConnection.scanFile(context, arrayOf(outFile.toString()), null, null)

                // Backup to Google Drive if enabled (non-blocking)
                launch {
                    val userPreferencesRepository = UserPreferencesRepository(context)
                    try {
                        if (userPreferencesRepository.isBackupEnabled.first()) {
                            val driveStatus = App.driveClient.status()
                            if (driveStatus.linked) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Backing up to Google Drive...", Toast.LENGTH_SHORT).show()
                                }
                                val fileBytes = outFile.readBytes()
                                App.driveClient.upload(fileBytes, outFile.name, "application/pdf")
                                // Optional: success toast, but might be too much.
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved to ${outFile.name}", Toast.LENGTH_LONG).show()
                    sessionController.discardSession()
                    if (andPop) navController.popBackStack()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error saving PDF: ${e.message}", Toast.LENGTH_LONG).show()
                    if (andPop) navController.popBackStack()
                }
            }
        }
    }

    LaunchedEffect(imageUri) {
        imageUri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    val appendIndex = sessionState.slots.indexOfFirst { it is PageSlot.Empty }.let { if (it == -1) sessionState.slots.size else it }
                    if (appendIndex == sessionState.slots.size) {
                        sessionController.addEmptySlot()
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Processing image...", Toast.LENGTH_SHORT).show()
                    }
                    val bytes = context.contentResolver.openInputStream(it)?.use(InputStream::readBytes)
                    if (bytes != null) {
                        sessionController.processIntoSlot(appendIndex, bytes, enhanceMode)
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error processing image: ${e.message}", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                }
            }
        }
    }

    LaunchedEffect(pdfUri) {
        pdfUri?.let { uri ->
            scope.launch(Dispatchers.IO) {
                try {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Processing PDF...", Toast.LENGTH_SHORT).show()
                    }
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        val renderer = PdfRenderer(pfd)
                        val pageCount = renderer.pageCount

                        val appendIndex = sessionState.slots.indexOfFirst { it is PageSlot.Empty }.let { if (it == -1) sessionState.slots.size else it }
                        val availableEmptySlots = if (appendIndex == sessionState.slots.size) 0 else sessionState.slots.size - appendIndex
                        val slotsToAdd = pageCount - availableEmptySlots
                        if (slotsToAdd > 0) {
                            repeat(slotsToAdd) {
                                sessionController.addEmptySlot()
                            }
                        }

                        for (i in 0 until pageCount) {
//                            val page = renderer.openPage(i)
//                            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
//                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
//                            page.close()
//
//                            val stream = ByteArrayOutputStream()
//                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
//                            val byteArray = stream.toByteArray()
//                            bitmap.recycle()
//
//                            sessionController.processIntoSlot(appendIndex + i, byteArray, enhanceMode)

                            val page = renderer.openPage(i)

                            // Render at higher scale for better OCR + fill white background to avoid black pages
                            val scale = 2f
                            val w = (page.width * scale).toInt().coerceAtLeast(1)
                            val h = (page.height * scale).toInt().coerceAtLeast(1)

                            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

                            // IMPORTANT: born-digital PDFs may have transparent background -> JPEG becomes black without this
                            AndroidCanvas(bitmap).drawColor(AndroidColor.WHITE)

                            val matrix = Matrix().apply { postScale(scale, scale) }

                            // FOR_PRINT tends to be sharper for text PDFs
                            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            page.close()

                            val stream = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                            val byteArray = stream.toByteArray()
                            bitmap.recycle()

                            // Use PDF-flat pipeline (skip detect/warp)
                            sessionController.processPdfIntoSlot(appendIndex + i, byteArray, enhanceMode)
                        }
                        renderer.close()
                        pfd.close()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error processing PDF: ${e.message}", Toast.LENGTH_LONG).show()
                        navController.popBackStack()
                    }
                }
            }
        }
    }

    val captureAndProcess = remember(sessionState.slots, enhanceMode) {
        fun() {
            val targetSlot = sessionState.slots.firstOrNull { it is PageSlot.Empty }
                ?: return
            val tempFile = File.createTempFile("raw_capture_", ".jpg", context.cacheDir)

            cameraController.takePhoto(
                outputFile = tempFile,
                onSaved = { file ->
                    scope.launch(Dispatchers.IO) {
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
                        IconButton(onClick = { finishAndExport(true) }) {
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

                    if (imageUri == null && pdfUri == null) {
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
//                                    val icon = if (enhanceMode == "color-pro") Icons.Default.ColorLens else Icons.Default.FilterBAndW
                                    val icon = if (enhanceMode == "color_pro") Icons.Default.ColorLens else Icons.Default.FilterBAndW
                                    Icon(icon, contentDescription = "Change scan mode")
                                }
                                DropdownMenu(
                                    expanded = showModeMenu,
                                    onDismissRequest = { showModeMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Quét màu") },
                                        onClick = {
//                                            enhanceMode = "color-pro"
                                            enhanceMode = "color_pro"
                                            showModeMenu = false
                                        },
                                        leadingIcon = { Icon(Icons.Default.ColorLens, null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Quét đen trắng") },
                                        onClick = {
//                                            enhanceMode = "auto-pro"
                                            enhanceMode = "bw_pro"
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
                                    .clickable(enabled = hasCameraPermission, onClick = captureAndProcess)
                            ) {
                                Icon(Icons.Default.Camera, "Chụp", tint = Color.White, modifier = Modifier.size(40.dp))
                            }

                            Spacer(modifier = Modifier.size(64.dp))
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            val readySlot = sessionState.slots.filterIsInstance<PageSlot.Ready>().lastOrNull()

            if (readySlot != null) {
                AsyncImage(
                    model = File(readySlot.processedJpegPath),
                    contentDescription = "Processed page preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (imageUri == null && pdfUri == null) {
                if (hasCameraPermission) {
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
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cần quyền truy cập Camera để quét tài liệu.")
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (sessionState.slots.any { it is PageSlot.Processing }) {
                        CircularProgressIndicator()
                    } else {
                        Text("Đang chuẩn bị...", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun SlotItemView(slot: PageSlot) {
    val shape = MaterialTheme.shapes.medium
    val baseModifier = Modifier
        .size(60.dp, 80.dp)
        .clip(shape)

    when (slot) {
        is PageSlot.Empty -> {
            val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
            val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
            Box(
                modifier = baseModifier.background(surfaceVariantColor),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 1.dp.toPx()
                    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    drawRoundRect(
                        color = onSurfaceVariantColor,
                        style = Stroke(width = strokeWidth, pathEffect = pathEffect)
                    )
                }
                Text(
                    text = "${slot.index + 1}",
                    color = onSurfaceVariantColor,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        is PageSlot.Processing -> {
            Box(
                modifier = baseModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        is PageSlot.Ready -> {
            Box(
                modifier = baseModifier
                    .border(2.dp, MaterialTheme.colorScheme.primary, shape)
            ) {
                AsyncImage(
                    model = File(slot.processedJpegPath),
                    contentDescription = "Page ${slot.index}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${slot.index + 1}",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        is PageSlot.Failed -> {
            Box(
                modifier = baseModifier
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .border(2.dp, MaterialTheme.colorScheme.error, shape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
