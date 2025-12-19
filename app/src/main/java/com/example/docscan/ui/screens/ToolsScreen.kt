package com.example.docscan.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.docscan.ui.components.ActionGrid
import com.example.docscan.ui.components.ActionItemData
import com.example.docscan.ui.components.SectionTitle
import com.example.domain.interfaces.ocr.OcrGateway
import com.example.ocr.core.api.OcrImage
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(navController: NavHostController, ocrGateway: OcrGateway) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var extractedText by remember { mutableStateOf<String?>(null) }
    var isOcrLoading by remember { mutableStateOf(false) }

    val onImageImport: (Uri) -> Unit = {
        val encodedUri = URLEncoder.encode(it.toString(), StandardCharsets.UTF_8.toString())
        navController.navigate("scan?imageUri=$encodedUri")
    }

    val onDocumentImport: (Uri) -> Unit = {
        val encodedUri = URLEncoder.encode(it.toString(), StandardCharsets.UTF_8.toString())
        navController.navigate("scan?pdfUri=$encodedUri")
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let { onImageImport(it) }
        }
    )

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let { onDocumentImport(it) }
        }
    )

    val ocrImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                scope.launch {
                    isOcrLoading = true
                    try {
                        val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, it))
                        } else {
                            @Suppress("DEPRECATION")
                            MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                        }
                        val bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, false)
                        val buffer = java.nio.ByteBuffer.allocate(bitmap.byteCount)
                        bitmap.copyPixelsToBuffer(buffer)

                        val ocrImage = OcrImage.Rgba8888(
                            bytes = buffer.array(),
                            width = bitmap.width,
                            height = bitmap.height,
                            rowStride = bitmap.rowBytes
                        )

                        val result = ocrGateway.recognize(
                            docId = "transient-doc-${System.currentTimeMillis()}",
                            pageId = "transient-page-${System.currentTimeMillis()}",
                            image = ocrImage
                        )

                        extractedText = if (result.text.raw.isNotBlank()) {
                            result.text.raw
                        } else {
                            "Không tìm thấy văn bản"
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Lỗi OCR: ${e.message}", Toast.LENGTH_LONG).show()
                        e.printStackTrace()
                    } finally {
                        isOcrLoading = false
                    }
                }
            }
        }
    )

    val scanActions = remember(navController) {
        listOf(
            ActionItemData(Icons.Default.Scanner, "Quét tài liệu") { navController.navigate("scan") },
            ActionItemData(Icons.Default.CreditCard, "Thẻ ID") { navController.navigate("id_card_scan") },
            ActionItemData(Icons.Default.TextFields, "Trích xuất văn bản") { ocrImagePickerLauncher.launch("image/*") }
        )
    }
    val importActions = remember(navController) {
        listOf(
            ActionItemData(Icons.Default.Image, "Nhập ảnh") { imagePickerLauncher.launch("image/*") },
            ActionItemData(Icons.Default.UploadFile, "Nhập tệp tin") { documentPickerLauncher.launch("application/pdf") }
        )
    }
    val convertActions = remember(navController) {
        listOf(
            ActionItemData(Icons.Default.Description, "Thành Word") { navController.navigate("pdf_tools") },
            ActionItemData(Icons.Default.Slideshow, "Thành PPT") { navController.navigate("pdf_tools") },
            ActionItemData(Icons.Default.Photo, "PDF thành ảnh") { navController.navigate("pdf_to_image") }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            LazyColumn(modifier = Modifier.padding(bottom = 16.dp)) {
                item { SectionTitle("Quét") }
                item { ActionGrid(scanActions) }

                item { SectionTitle("Nhập") }
                item { ActionGrid(importActions, columnCount = 2) }

                item { SectionTitle("Chuyển đổi") }
                item { ActionGrid(convertActions) }
            }
        }

        if (extractedText != null) {
            AlertDialog(
                onDismissRequest = { extractedText = null },
                title = { Text("Văn bản được trích xuất") },
                text = {
                    SelectionContainer {
                        Text(extractedText ?: "")
                    }
                },
                confirmButton = {
                    Button(onClick = { extractedText = null }) {
                        Text("OK")
                    }
                }
            )
        }

        if (isOcrLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Preview(name = "ToolsScreen Preview", showBackground = true)
@Composable
fun Preview_ToolsScreen() {
    val navController = rememberNavController()
    val ocrGateway = object : OcrGateway {
        override suspend fun recognize(docId: String, pageId: String, image: OcrImage, policy: com.example.domain.types.ocr.OcrPolicy): OcrGateway.Result {
            return OcrGateway.Result(
                page = com.example.ocr.core.api.OcrPageResult(0, "", emptyList()),
                text = com.example.domain.types.ocr.OcrText("", "", ""),
                words = emptyList()
            )
        }
    }
    ToolsScreen(navController = navController, ocrGateway = ocrGateway)
}
