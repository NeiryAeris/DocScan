package com.example.docscan.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.docscan.App
import com.itextpdf.text.BaseColor
import com.itextpdf.text.pdf.PdfContentByte
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.PdfStamper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class SignatureState(val strokes: List<List<Offset>> = emptyList())

// A data class to hold the PdfRenderer and its ParcelFileDescriptor for proper cleanup
private data class PdfRendererState(val renderer: PdfRenderer, val fileDescriptor: ParcelFileDescriptor) : AutoCloseable {
    override fun close() {
        try {
            renderer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            fileDescriptor.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SigningScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pdfRendererState by remember { mutableStateOf<PdfRendererState?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val pdfUri = App.pdfToSign

    // This effect will run when pdfUri changes, and will handle opening the PDF
    // and cleaning up resources correctly.
    DisposableEffect(pdfUri) {
        val job = scope.launch(Dispatchers.IO) {
            var pfd: ParcelFileDescriptor? = null
            if (pdfUri != null) {
                try {
                    pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
                    if (pfd != null) {
                        val renderer = PdfRenderer(pfd)
                        val newState = PdfRendererState(renderer, pfd)
                        withContext(Dispatchers.Main) {
                            pdfRendererState = newState
                            errorMessage = null
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            errorMessage = "Không thể mở tệp tin."
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        errorMessage = "Tệp PDF bị lỗi hoặc không hợp lệ."
                    }
                    pfd?.close() // Close the descriptor if renderer creation failed
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        errorMessage = "Đã xảy ra lỗi không xác định khi mở PDF."
                    }
                    pfd?.close()
                }
            } else {
                withContext(Dispatchers.Main) {
                    errorMessage = "Không có tệp PDF nào được chọn."
                }
            }
        }

        onDispose {
            job.cancel() // Cancel the coroutine if the composable is disposed
            scope.launch(Dispatchers.IO) {
                pdfRendererState?.close()
                withContext(Dispatchers.Main) {
                    pdfRendererState = null
                }
            }
        }
    }

    val pdfRenderer = pdfRendererState?.renderer
    val pageCount = pdfRenderer?.pageCount ?: 0

    val signatureStates = remember(pageCount) {
        mutableStateListOf<SignatureState>().apply {
            if (pageCount > 0) {
                repeat(pageCount) { add(SignatureState()) }
            }
        }
    }

    val pdfSaverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
        onResult = { savedPdfUri ->
            if (savedPdfUri != null) {
                scope.launch {
                    Toast.makeText(context, "Đang lưu tệp PDF...", Toast.LENGTH_SHORT).show()
                    try {
                        withContext(Dispatchers.IO) {
                            val inputStream = pdfUri?.let { context.contentResolver.openInputStream(it) }
                                ?: throw IOException("Không thể mở tệp PDF gốc.")
                            val reader = PdfReader(inputStream)
                            val outputStream = context.contentResolver.openOutputStream(savedPdfUri)
                                ?: throw IOException("Không thể tạo tệp đầu ra.")

                            outputStream.use { out ->
                                val stamper = PdfStamper(reader, out)

                                for (i in 0 until reader.numberOfPages) {
                                    val pageIndex = i + 1
                                    val signature = signatureStates.getOrNull(i)

                                    if (signature != null && signature.strokes.isNotEmpty()) {
                                        val pageCanvas = stamper.getOverContent(pageIndex)
                                        val pageSize = reader.getPageSize(pageIndex)
                                        val pageHeight = pageSize.height

                                        pageCanvas.setColorStroke(BaseColor.BLACK)
                                        pageCanvas.setLineWidth(5f)
                                        pageCanvas.setLineCap(PdfContentByte.LINE_CAP_ROUND)
                                        pageCanvas.setLineJoin(PdfContentByte.LINE_JOIN_ROUND)

                                        signature.strokes.forEach { stroke ->
                                            if (stroke.size > 1) {
                                                val firstPoint = stroke.first()
                                                pageCanvas.moveTo(firstPoint.x, pageHeight - firstPoint.y)
                                                stroke.drop(1).forEach { offset ->
                                                    pageCanvas.lineTo(offset.x, pageHeight - offset.y)
                                                }
                                                pageCanvas.stroke()
                                            }
                                        }
                                    }
                                }
                                stamper.close()
                            }
                            reader.close()
                            inputStream.close()
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Đã lưu tệp PDF thành công!", Toast.LENGTH_LONG).show()
                            navController.popBackStack()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Lỗi khi lưu PDF: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Ký tên") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        for (i in 0 until signatureStates.size) {
                            signatureStates[i] = SignatureState()
                        }
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Xóa chữ ký")
                    }
                    IconButton(onClick = {
                        val defaultFileName = "signed-document-${System.currentTimeMillis()}.pdf"
                        pdfSaverLauncher.launch(defaultFileName)
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Lưu PDF")
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            errorMessage != null -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text(errorMessage ?: "Đã xảy ra lỗi.")
                }
            }
            pdfUri == null -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text("Không có tệp PDF nào được chọn.")
                }
            }
            pdfRenderer == null -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                PdfViewer(
                    modifier = Modifier.padding(paddingValues),
                    pdfRenderer = pdfRenderer,
                    pageCount = pageCount,
                    signatureStates = signatureStates,
                    onSignatureStateChanged = { index, newState ->
                        if (index < signatureStates.size) {
                            signatureStates[index] = newState
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PdfPage(
    pdfRenderer: PdfRenderer,
    pageIndex: Int,
    signatureState: SignatureState,
    onSignatureStateChanged: (SignatureState) -> Unit
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, pdfRenderer, pageIndex) {
        value = withContext(Dispatchers.IO) {
            try {
                pdfRenderer.openPage(pageIndex).use { page ->
                    val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        awaitDispose {
            value?.recycle()
        }
    }

    val currentPathPoints = remember { mutableStateListOf<Offset>() }

    if (bitmap == null) {
        Box(
            modifier = Modifier
                .padding(8.dp)
                .background(Color.LightGray)
                .aspectRatio(1f / 1.414f) // A4 aspect ratio
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        Box(
            Modifier
                .padding(8.dp)
                .background(Color.White)
                .aspectRatio(bitmap!!.width.toFloat() / bitmap!!.height.toFloat())
                .fillMaxWidth()
        ) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxSize()
            )
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(pageIndex) {
                        detectDragGestures(
                            onDragStart = { offset -> currentPathPoints.add(offset) },
                            onDrag = { change, _ -> currentPathPoints.add(change.position) },
                            onDragEnd = {
                                if (currentPathPoints.isNotEmpty()) {
                                    val newStroke = currentPathPoints.toList()
                                    val newStrokes = signatureState.strokes + listOf(newStroke)
                                    onSignatureStateChanged(signatureState.copy(strokes = newStrokes))
                                }
                                currentPathPoints.clear()
                            },
                            onDragCancel = { currentPathPoints.clear() }
                        )
                    }
            ) {
                signatureState.strokes.forEach { stroke ->
                    if (stroke.size > 1) {
                        drawPoints(stroke, PointMode.Polygon, Color.Black, 5f, StrokeCap.Round)
                    }
                }

                if (currentPathPoints.size > 1) {
                    drawPoints(currentPathPoints, PointMode.Polygon, Color.Black, 5f, StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
fun PdfViewer(
    modifier: Modifier = Modifier,
    pdfRenderer: PdfRenderer,
    pageCount: Int,
    signatureStates: List<SignatureState>,
    onSignatureStateChanged: (Int, SignatureState) -> Unit
) {
    LazyColumn(modifier = modifier) {
        items(
            count = pageCount,
            key = { index -> index }
        ) { index ->
            PdfPage(
                pdfRenderer = pdfRenderer,
                pageIndex = index,
                signatureState = signatureStates.getOrElse(index) { SignatureState() },
                onSignatureStateChanged = { newState ->
                    onSignatureStateChanged(index, newState)
                }
            )
        }
    }
}
