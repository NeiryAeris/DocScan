package com.example.docscan.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
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
import com.example.docscan.logic.storage.DocumentFile
import com.example.docscan.logic.storage.DocumentRepository
import com.example.docscan.logic.utils.FileOpener
import com.example.docscan.ui.components.DocumentCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(navController: NavHostController? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val documents by DocumentRepository.documents.collectAsState()

    var selectionMode by remember { mutableStateOf(false) }
    val selectedDocuments = remember { mutableStateListOf<DocumentFile>() }

    val clearSelection = remember {
        fun() {
            selectionMode = false
            selectedDocuments.clear()
        }
    }

    val deleteSelectedDocuments = remember(scope, context) {
        fun() {
            scope.launch {
                val docsToDelete = selectedDocuments.toList()
                val deletedCount = docsToDelete.size
                // Perform heavy I/O on background thread
                withContext(Dispatchers.IO) {
                    DocumentRepository.deleteDocuments(docsToDelete)
                }
                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Đã xóa $deletedCount tệp", Toast.LENGTH_SHORT).show()
                    clearSelection()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (selectionMode) {
                        Text("Đã chọn ${selectedDocuments.size}")
                    } else {
                        Text("Tất cả (${documents.size})")
                    }
                },
                navigationIcon = {
                    if (selectionMode) {
                        IconButton(onClick = clearSelection) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(onClick = deleteSelectedDocuments) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                        }
                    } else {
                        IconButton(onClick = { /*TODO: Sort logic*/ }) { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort") }
                        IconButton(onClick = { /*TODO: Grid/List toggle*/ }) { Icon(Icons.Default.GridView, contentDescription = "Grid View") }
                        IconButton(onClick = { selectionMode = true }) { Icon(Icons.Default.Check, contentDescription = "Select") }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (documents.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Chưa có tài liệu nào")
                    }
                }
            } else {
                items(documents, key = { it.file.absolutePath }) { doc ->
                    val isSelected = selectedDocuments.contains(doc)

                    val onClick = remember(selectionMode, isSelected, doc, context) {
                        fun() {
                            if (selectionMode) {
                                if (isSelected) {
                                    selectedDocuments.remove(doc)
                                } else {
                                    selectedDocuments.add(doc)
                                }
                            } else {
                                FileOpener.openPdf(context, doc.file)
                            }
                        }
                    }

                    val onLongClick = remember(selectionMode, doc) {
                        fun() {
                            if (!selectionMode) {
                                selectionMode = true
                                selectedDocuments.add(doc)
                            }
                        }
                    }

                    val onDeleteClick = remember(doc, scope, context) {
                        fun() {
                            scope.launch {
                                val success = withContext(Dispatchers.IO) {
                                    DocumentRepository.deleteDocument(doc)
                                }
                                withContext(Dispatchers.Main) {
                                    if (success) {
                                        Toast.makeText(context, "Đã xóa ${doc.name}", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Lỗi khi xóa ${doc.name}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }

                    val onRenameClick = remember(doc, scope, context) {
                        fun(newName: String) {
                            scope.launch {
                                val success = withContext(Dispatchers.IO) {
                                    DocumentRepository.renameDocument(doc, newName)
                                }
                                withContext(Dispatchers.Main) {
                                    if (success) {
                                        Toast.makeText(context, "Đã đổi tên thành công", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Lỗi khi đổi tên", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }

                    DocumentCard(
                        title = doc.name,
                        date = doc.formattedDate,
                        pageCount = doc.pageCount,
                        isSelected = isSelected,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        onDeleteClick = onDeleteClick,
                        onRenameClick = onRenameClick
                    )
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
