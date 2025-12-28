package com.example.docscan.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.docscan.R
import com.example.docscan.logic.storage.DocumentRepository
import com.example.docscan.logic.utils.FileOpener
import com.example.docscan.logic.utils.PdfThumbnailGenerator
import com.example.docscan.ui.components.ActionGrid
import com.example.docscan.ui.components.ActionItemData
import com.example.docscan.ui.components.DocumentCard
import com.example.docscan.ui.components.SectionTitle
import com.example.domain.interfaces.ocr.OcrGateway
import com.example.domain.types.Document
import com.example.ocr.core.api.OcrImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onScanClick: () -> Unit = {},
    onShowAllClick: () -> Unit = {},
    onIdCardScanClick: () -> Unit = {},
    onImageImport: (Uri) -> Unit,
    onDocumentImport: (Uri) -> Unit,
    ocrGateway: OcrGateway // OCR Gateway for text extraction
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var extractedText by remember { mutableStateOf<String?>(null) }
    var isOcrLoading by remember { mutableStateOf(false) }

    val onImageResult = remember(onImageImport) {
        { uri: Uri? ->
            if (uri != null) {
                onImageImport(uri)
            }
        }
    }
    val onDocumentResult = remember(onDocumentImport) {
        { uri: Uri? ->
            if (uri != null) {
                onDocumentImport(uri)
            }
        }
    }

    val onOcrImageResult = remember(context, scope, ocrGateway) {
        { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    isOcrLoading = true
                    try {
                        val result = withContext(Dispatchers.IO) {
                            val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                            } else {
                                @Suppress("DEPRECATION")
                                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                            }
                            val bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, false)
                            val buffer = java.nio.ByteBuffer.allocate(bitmap.byteCount)
                            bitmap.copyPixelsToBuffer(buffer)

                            val ocrImage = OcrImage.Rgba8888(
                                bytes = buffer.array(), width = bitmap.width, height = bitmap.height, rowStride = bitmap.rowBytes
                            )

                            ocrGateway.recognize(
                                docId = "transient-doc-${System.currentTimeMillis()}",
                                pageId = "transient-page-${System.currentTimeMillis()}",
                                image = ocrImage
                            )
                        }

                        extractedText = result.text.raw.ifBlank { "Không tìm thấy văn bản" }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Lỗi OCR: ${e.message}", Toast.LENGTH_LONG).show()
                        e.printStackTrace()
                    } finally {
                        isOcrLoading = false
                    }
                }
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent(), onImageResult)
    val documentPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent(), onDocumentResult)
    val ocrImagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent(), onOcrImageResult)

    var searchQuery by remember { mutableStateOf("") }
    var committedSearchQuery by remember { mutableStateOf("") }

    val documentsState by DocumentRepository.documents.collectAsState(initial = null)
    var thumbnails by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }

    val documentsToDisplay by remember(documentsState, committedSearchQuery) {
        derivedStateOf {
            documentsState?.let {
                if (committedSearchQuery.isBlank()) {
                    it.take(3)
                } else {
                    it.filter { doc -> doc.name.contains(committedSearchQuery, ignoreCase = true) }
                }
            } ?: emptyList()
        }
    }

    LaunchedEffect(documentsToDisplay) {
        val currentThumbnails = thumbnails.toMutableMap()
        documentsToDisplay.forEach { doc ->
            if (!currentThumbnails.containsKey(doc.file.absolutePath)) {
                launch {
                    val thumbnail = PdfThumbnailGenerator.generateThumbnail(context, doc.file)
                    if (thumbnail != null) {
                        currentThumbnails[doc.file.absolutePath] = thumbnail
                        thumbnails = currentThumbnails.toMap() // Create a new map to trigger recomposition
                    }
                }
            }
        }
    }

    val homeActions = remember(onScanClick, onIdCardScanClick, imagePickerLauncher, documentPickerLauncher, ocrImagePickerLauncher) {
        listOf(
            ActionItemData(R.drawable.quet_tai_lieu, "Quét", onClick = onScanClick),
            ActionItemData(R.drawable.nhap_anh, "Nhập ảnh", onClick = { imagePickerLauncher.launch("image/*") }),
            ActionItemData(R.drawable.nhap_tep_tin, "Nhập tập tin", onClick = { documentPickerLauncher.launch("application/pdf") }),
            ActionItemData(R.drawable.the_id, "Thẻ ID", onClick = onIdCardScanClick),
            ActionItemData(R.drawable.trich_xuat_van_ban, "Trích xuất văn bản", onClick = { ocrImagePickerLauncher.launch("image/*") })
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFA9FFF8),
                        Color(0xFFF4FAFE),
                        Color(0xFFFFFFFF)
                    )
                )
            )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent
        ) {
            LazyColumn {
                item { SectionTitle("Công cụ") }
                item { ActionGrid(items = homeActions) }
                item { SectionTitle(title = "Gần đây", actionText = "Xem tất cả", onActionClick = onShowAllClick) }

                if (documentsState == null) {
                    item { Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                } else if (documentsToDisplay.isEmpty()) {
                    item { Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) { Text(if (committedSearchQuery.isBlank()) "Chưa có tài liệu gần đây" else "Không tìm thấy tài liệu nào") } }
                } else {
                    items(documentsToDisplay, key = { it.file.absolutePath }) { doc ->
                        val onClick = remember(doc, context) { { FileOpener.openPdf(context, doc.file) } }
                        val onDeleteClick = remember(doc, scope, context) {
                            fun() {
                                scope.launch {
                                    if (withContext(Dispatchers.IO) { DocumentRepository.deleteDocument(doc) }) {
                                        withContext(Dispatchers.Main) { Toast.makeText(context, "Đã xóa ${doc.name}", Toast.LENGTH_SHORT).show() }
                                    } else {
                                        withContext(Dispatchers.Main) { Toast.makeText(context, "Lỗi khi xóa ${doc.name}", Toast.LENGTH_SHORT).show() }
                                    }
                                }
                            }
                        }
                        val onRenameClick = remember(doc, scope, context) {
                            fun(newName: String) {
                                scope.launch {
                                    if (withContext(Dispatchers.IO) { DocumentRepository.renameDocument(doc, newName) }) {
                                        withContext(Dispatchers.Main) { Toast.makeText(context, "Đã đổi tên thành công", Toast.LENGTH_SHORT).show() }
                                    } else {
                                        withContext(Dispatchers.Main) { Toast.makeText(context, "Lỗi khi đổi tên", Toast.LENGTH_SHORT).show() }
                                    }
                                }
                            }
                        }

                        DocumentCard(
                            title = doc.name,
                            date = doc.formattedDate,
                            pageCount = doc.pageCount,
                            thumbnail = thumbnails[doc.file.absolutePath],
                            onClick = onClick,
                            onDeleteClick = onDeleteClick,
                            onRenameClick = onRenameClick
                        )
                    }
                }
            }
        }

        if (extractedText != null) {
            AlertDialog(
                onDismissRequest = { extractedText = null },
                title = { Text("Văn bản được trích xuất") },
                text = { SelectionContainer { Text(extractedText ?: "") } },
                confirmButton = { Button(onClick = { extractedText = null }) { Text("OK") } }
            )
        }

        if (isOcrLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}
