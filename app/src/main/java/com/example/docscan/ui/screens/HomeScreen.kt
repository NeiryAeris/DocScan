package com.example.docscan.ui.screens

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
import com.example.docscan.logic.storage.AppStorage
import com.example.docscan.logic.storage.DocumentFile
import com.example.docscan.logic.utils.FileOpener
import com.example.docscan.ui.components.ActionGrid
import com.example.docscan.ui.components.ActionItemData
import com.example.docscan.ui.components.DocumentCard
import com.example.docscan.ui.components.SectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onScanClick: () -> Unit = {}, onShowAllClick: () -> Unit = {}) {
    val context = LocalContext.current
    var recentDocuments by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }

    // Load recent documents when the screen is composed or recomposed
    LaunchedEffect(Unit) {
        recentDocuments = AppStorage.listPdfDocuments().take(3)
    }

    val homeActions = listOf(
        ActionItemData(Icons.Default.Scanner, "Quét", onClick = onScanClick),
        ActionItemData(Icons.Default.PictureAsPdf, "Công cụ PDF"),
        ActionItemData(Icons.Default.Image, "Nhập ảnh"),
        ActionItemData(Icons.Default.UploadFile, "Nhập tập tin"),
        ActionItemData(Icons.Default.CreditCard, "Thẻ ID"),
        ActionItemData(Icons.Default.TextFields, "Trích xuất văn bản"),
        ActionItemData(Icons.Default.AutoAwesome, "Solver AI"),
        ActionItemData(Icons.Default.MoreHoriz, "Tất cả")
    )

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

            // Display recent documents or a placeholder
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
                items(recentDocuments) { doc ->
                    DocumentCard(
                        title = doc.name,
                        date = doc.formattedDate,
                        pageCount = doc.pageCount,
                        onClick = { FileOpener.openPdf(context, doc.file) }
                    )
                }
            }
        }
    }
}
