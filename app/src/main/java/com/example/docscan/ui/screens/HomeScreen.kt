package com.example.docscan.ui.screens

import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.docscan.logic.storage.DocumentRepository
import com.example.docscan.logic.utils.FileOpener
import com.example.docscan.ui.components.ActionGrid
import com.example.docscan.ui.components.ActionItemData
import com.example.docscan.ui.components.DocumentCard
import com.example.docscan.ui.components.SectionTitle
import com.example.domain.interfaces.ocr.OcrGateway
import com.example.ocr.core.api.OcrImage
import kotlinx.coroutines.launch
import android.graphics.Bitmap

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
    val lifecycleOwner = LocalLifecycleOwner.current

    var extractedText by remember { mutableStateOf<String?>(null) }
    var isOcrLoading by remember { mutableStateOf(false) }


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
                        // 1. Get Bitmap from Uri
                        val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, it))
                        } else {
                            @Suppress("DEPRECATION")
                            MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                        }

                        // Ensure the bitmap is mutable and in a software-readable format (ARGB_8888)
                        val bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, false)

                        // 2. Convert Bitmap to OcrImage
                        val buffer = java.nio.ByteBuffer.allocate(bitmap.byteCount)
                        bitmap.copyPixelsToBuffer(buffer)

                        val ocrImage = OcrImage.Rgba8888(
                            bytes = buffer.array(),
                            width = bitmap.width,
                            height = bitmap.height,
                            rowStride = bitmap.rowBytes
                        )

                        // 3. Call OCR gateway
                        val result = ocrGateway.recognize(
                            docId = "transient-doc-${System.currentTimeMillis()}",
                            pageId = "transient-page-${System.currentTimeMillis()}",
                            image = ocrImage
                        )

                        // 4. Show result
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

    var searchQuery by remember { mutableStateOf("") }
    var committedSearchQuery by remember { mutableStateOf("") }

    // Get documents directly from the repository. The data is pre-loaded and instantly available.
    val documents by DocumentRepository.documents.collectAsState()

    // Refresh documents when the screen is resumed
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    DocumentRepository.refresh()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Filter documents based on the committed search query. If the query is empty, show the 3 most recent documents.
    val documentsToDisplay by remember(documents, committedSearchQuery) {
        derivedStateOf {
            if (committedSearchQuery.isBlank()) {
                documents.take(3)
            } else {
                documents.filter { it.name.contains(committedSearchQuery, ignoreCase = true) }
            }
        }
    }

    // Use remember to avoid recreating the action list on every recomposition
    val homeActions = remember {
        listOf(
            ActionItemData(Icons.Default.Scanner, "Quét", onClick = onScanClick),
            ActionItemData(Icons.Default.PictureAsPdf, "Công cụ PDF", onClick = {}),
            ActionItemData(Icons.Default.Image, "Nhập ảnh", onClick = { imagePickerLauncher.launch("image/*") }),
            ActionItemData(Icons.Default.UploadFile, "Nhập tập tin", onClick = { documentPickerLauncher.launch("application/pdf") }),
            ActionItemData(Icons.Default.CreditCard, "Thẻ ID", onClick = onIdCardScanClick),
            ActionItemData(Icons.Default.TextFields, "Trích xuất văn bản", onClick = { ocrImagePickerLauncher.launch("image/*") })
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            LazyColumn {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Tìm kiếm tài liệu") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                committedSearchQuery = searchQuery
                                keyboardController?.hide()
                            }
                        )
                    )
                }
                item {
                    ActionGrid(items = homeActions)
                }
                item {
                    SectionTitle(title = "Gần đây", actionText = "Xem tất cả", onActionClick = onShowAllClick)
                }

                if (documentsToDisplay.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val message = if (committedSearchQuery.isBlank()) {
                                "Chưa có tài liệu gần đây"
                            } else {
                                "Không tìm thấy tài liệu nào"
                            }
                            Text(message)
                        }
                    }
                } else {
                    items(documentsToDisplay, key = { it.file.absolutePath }) { doc ->
                        DocumentCard(
                            title = doc.name,
                            date = doc.formattedDate,
                            pageCount = doc.pageCount,
                            onClick = { FileOpener.openPdf(context, doc.file) },
                            onDeleteClick = {
                                scope.launch {
                                    if (DocumentRepository.deleteDocument(doc)) {
                                        Toast.makeText(context, "Đã xóa ${doc.name}", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Lỗi khi xóa ${doc.name}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onRenameClick = { newName ->
                                scope.launch {
                                    if (DocumentRepository.renameDocument(doc, newName)) {
                                        Toast.makeText(context, "Đã đổi tên thành công", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Lỗi khi đổi tên", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
        // Dialog to show extracted text
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

        // Loading indicator for OCR
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
