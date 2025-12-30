package com.example.docscan.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.docscan.logic.storage.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToImageScreen(
    navController: NavHostController,
    encodedUri: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uri = remember(encodedUri) { Uri.parse(encodedUri) }

    var bitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        if (uri != null) {
            isLoading = true
            scope.launch {
                try {
                    val loadedBitmaps = convertPdfToImages(context, uri)
                    withContext(Dispatchers.Main) {
                        bitmaps = loadedBitmaps
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Lỗi khi chuyển đổi PDF: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                    }
                }
            }
        }
    }

    val onSaveClick: () -> Unit = {
        scope.launch {
            try {
                val imageFiles = DocumentRepository.saveBitmapsAsImages(context, bitmaps)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Đã lưu ${imageFiles.size} ảnh.", Toast.LENGTH_LONG).show()
                    navController.popBackStack()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Lỗi khi lưu ảnh: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF thành ảnh") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = onSaveClick,
                        modifier = Modifier.padding(end = 8.dp),
                        enabled = bitmaps.isNotEmpty() && !isLoading
                    ) {
                        Text("Lưu")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else if (bitmaps.isEmpty()) {
                Text("Không thể tải PDF hoặc PDF trống.")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    items(bitmaps) { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(4.dp)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

private suspend fun convertPdfToImages(context: Context, uri: Uri): List<Bitmap> {
    return withContext(Dispatchers.IO) {
        val bitmaps = mutableListOf<Bitmap>()
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            pfd?.use {
                val renderer = PdfRenderer(it)
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmaps.add(bitmap)
                    }
                }
                renderer.close()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        bitmaps
    }
}
