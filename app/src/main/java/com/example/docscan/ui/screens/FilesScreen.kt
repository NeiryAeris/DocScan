package com.example.docscan.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.docscan.logic.storage.DocumentFile
import com.example.docscan.logic.storage.DocumentRepository
import com.example.docscan.logic.utils.FileOpener
import com.example.docscan.ui.components.DocumentCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(navController: NavHostController? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Get documents directly from the repository. Data is pre-loaded and instantly available.
    val documents by DocumentRepository.documents.collectAsState()

    var selectionMode by remember { mutableStateOf(false) }
    var selectedDocuments by remember { mutableStateOf<Set<DocumentFile>>(emptySet()) }
    var viewMode by remember { mutableStateOf("list") } // "list" or "grid"
    var showImportMenu by remember { mutableStateOf(false) }

    // Clear selection when exiting selection mode
    LaunchedEffect(selectionMode) {
        if (!selectionMode) {
            selectedDocuments = emptySet()
        }
    }

    val deleteSelectedDocuments = remember {
        {
            scope.launch {
                val docsToDelete = selectedDocuments.toList()
                var successCount = 0
                docsToDelete.forEach {
                    if (DocumentRepository.deleteDocument(it)) successCount++
                }
                Toast.makeText(context, "Đã xóa $successCount / ${docsToDelete.size} tài liệu", Toast.LENGTH_SHORT).show()
                selectionMode = false // Exit selection mode
            }
        }
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("${selectedDocuments.size} đã chọn") },
                    navigationIcon = { IconButton(onClick = { selectionMode = false }) { Icon(Icons.Default.Close, "Hủy") } },
                    actions = {
                        IconButton(onClick = { selectedDocuments = if (selectedDocuments.size == documents.size) emptySet() else documents.toSet() }) { Icon(Icons.Default.SelectAll, "Chọn tất cả") }
                        IconButton(onClick = { if (selectedDocuments.isNotEmpty()) deleteSelectedDocuments() }) { Icon(Icons.Default.Delete, "Xóa") }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("Tất cả tài liệu") },
                    actions = {
                        IconButton(onClick = { /* TODO: Search */ }) { Icon(Icons.Default.Search, "Tìm kiếm") }
                        IconButton(onClick = { viewMode = if (viewMode == "list") "grid" else "list" }) {
                            if (viewMode == "list") Icon(Icons.Default.GridView, "Chế độ xem lưới") else Icon(Icons.Default.ViewList, "Chế độ xem danh sách")
                        }
                        Box {
                           IconButton(onClick = { showImportMenu = true }) { Icon(Icons.Default.Add, "Nhập") }
                            DropdownMenu(expanded = showImportMenu, onDismissRequest = { showImportMenu = false }) {
                                DropdownMenuItem(text = { Text("Nhập tập tin") }, onClick = { /* TODO */ })
                                DropdownMenuItem(text = { Text("Nhập ảnh") }, onClick = { /* TODO */ })
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        if (documents.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    text = "Không có tài liệu nào",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val onDocumentClick = remember(selectionMode) {
                { doc: DocumentFile, isSelected: Boolean ->
                    if (selectionMode) {
                        selectedDocuments = if (isSelected) selectedDocuments - doc else selectedDocuments + doc
                    } else {
                        FileOpener.openPdf(context, doc.file)
                    }
                }
            }

            val onDocumentLongClick = remember(selectionMode) {
                { doc: DocumentFile ->
                    if (!selectionMode) {
                        selectionMode = true
                        selectedDocuments = selectedDocuments + doc
                    }
                }
            }

            val onDeleteAction = remember {
                { doc: DocumentFile ->
                    scope.launch {
                        if (DocumentRepository.deleteDocument(doc)) {
                            Toast.makeText(context, "Đã xóa ${doc.name}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Lỗi khi xóa ${doc.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            val onRenameAction = remember {
                { doc: DocumentFile, newName: String ->
                    scope.launch {
                        if (DocumentRepository.renameDocument(doc, newName)) {
                            Toast.makeText(context, "Đã đổi tên thành công", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Lỗi khi đổi tên", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            if (viewMode == "list") {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(documents, key = { it.file.absolutePath }) { doc ->
                        val isSelected = selectedDocuments.contains(doc)
                        DocumentCard(
                            title = doc.name,
                            date = doc.formattedDate,
                            pageCount = doc.pageCount,
                            isSelected = isSelected,
                            onClick = { onDocumentClick(doc, isSelected) },
                            onLongClick = { onDocumentLongClick(doc) },
                            onDeleteClick = { onDeleteAction(doc) },
                            onRenameClick = { newName -> onRenameAction(doc, newName) }
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(documents, key = { it.file.absolutePath }) { doc ->
                        val isSelected = selectedDocuments.contains(doc)
                        DocumentCard(
                            title = doc.name,
                            date = doc.formattedDate,
                            pageCount = doc.pageCount,
                            isSelected = isSelected,
                            onClick = { onDocumentClick(doc, isSelected) },
                            onLongClick = { onDocumentLongClick(doc) },
                            onDeleteClick = { onDeleteAction(doc) },
                            onRenameClick = { newName -> onRenameAction(doc, newName) }
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "FilesScreen Preview", showBackground = true)
@Composable
fun Preview_FilesScreen() {
    FilesScreen(navController = rememberNavController())
}
