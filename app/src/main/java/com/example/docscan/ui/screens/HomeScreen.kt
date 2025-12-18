package com.example.docscan.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.docscan.logic.storage.DocumentRepository
import com.example.docscan.logic.utils.FileOpener
import com.example.docscan.ui.components.ActionGrid
import com.example.docscan.ui.components.ActionItemData
import com.example.docscan.ui.components.DocumentCard
import com.example.docscan.ui.components.SectionTitle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onScanClick: () -> Unit = {}, onShowAllClick: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Get documents directly from the repository. The data is pre-loaded and instantly available.
    val documents by DocumentRepository.documents.collectAsState()

    // Use derivedStateOf to get recent documents. This recalculates only when the source state changes.
    val recentDocuments by remember(documents) {
        derivedStateOf { documents.take(3) }
    }

    // Use remember to avoid recreating the action list on every recomposition
    val homeActions = remember {
        listOf(
            ActionItemData(Icons.Default.Scanner, "Quét", onClick = onScanClick),
            ActionItemData(Icons.Default.PictureAsPdf, "Công cụ PDF", onClick = {}),
            ActionItemData(Icons.Default.Image, "Nhập ảnh", onClick = {}),
            ActionItemData(Icons.Default.UploadFile, "Nhập tập tin", onClick = {}),
            ActionItemData(Icons.Default.CreditCard, "Thẻ ID", onClick = {}),
            ActionItemData(Icons.Default.TextFields, "Trích xuất văn bản", onClick = {}),
            ActionItemData(Icons.Default.AutoAwesome, "Solver AI", onClick = {}),
            ActionItemData(Icons.Default.MoreHoriz, "Tất cả", onClick = {})
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn {
            item {
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    label = { Text("Tìm kiếm") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
            item {
                ActionGrid(items = homeActions)
            }
            item {
                SectionTitle(title = "Gần đây", actionText = "Xem tất cả", onActionClick = onShowAllClick)
            }

            if (recentDocuments.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Chưa có tài liệu gần đây")
                    }
                }
            } else {
                items(recentDocuments, key = { it.file.absolutePath }) { doc ->
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
}
