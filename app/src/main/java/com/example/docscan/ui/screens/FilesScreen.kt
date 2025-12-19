package com.example.docscan.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.docscan.logic.storage.DocumentFile
import com.example.docscan.logic.storage.DocumentRepository
import com.example.docscan.logic.utils.FileOpener
import com.example.docscan.ui.components.DocumentCard
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(navController: NavHostController? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val documents by DocumentRepository.documents.collectAsState()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { DocumentRepository.refresh() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                val encodedUri = URLEncoder.encode(it.toString(), StandardCharsets.UTF_8.toString())
                navController?.navigate("scan?imageUri=$encodedUri")
            }
        }
    )

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                val encodedUri = URLEncoder.encode(it.toString(), StandardCharsets.UTF_8.toString())
                navController?.navigate("scan?pdfUri=$encodedUri")
            }
        }
    )

    var selectionMode by remember { mutableStateOf(false) }
    var selectedDocuments by remember { mutableStateOf<Set<DocumentFile>>(emptySet()) }
    var viewMode by remember { mutableStateOf("list") } // "list" or "grid"
    var showImportMenu by remember { mutableStateOf(false) }
    var searchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val documentsToDisplay by remember(documents, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                documents
            } else {
                documents.filter { it.name.contains(searchQuery, ignoreCase = true) }
            }
        }
    }

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
                docsToDelete.forEach { if (DocumentRepository.deleteDocument(it)) successCount++ }
                Toast.makeText(context, "Đã xóa $successCount / ${docsToDelete.size} tài liệu", Toast.LENGTH_SHORT).show()
                selectionMode = false
            }
        }
    }

    Scaffold(
        topBar = {
            when {
                selectionMode -> {
                    TopAppBar(
                        title = { Text("${selectedDocuments.size} đã chọn") },
                        navigationIcon = { IconButton(onClick = { selectionMode = false }) { Icon(Icons.Default.Close, "Hủy") } },
                        actions = {
                            IconButton(onClick = { selectedDocuments = if (selectedDocuments.size == documentsToDisplay.size) emptySet() else documentsToDisplay.toSet() }) { Icon(Icons.Default.SelectAll, "Chọn tất cả") }
                            IconButton(onClick = { if (selectedDocuments.isNotEmpty()) deleteSelectedDocuments() }) { Icon(Icons.Default.Delete, "Xóa") }
                        }
                    )
                }
                searchMode -> {
                    TopAppBar(
                        title = {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Tìm kiếm tài liệu...") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                )
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                searchMode = false
                                searchQuery = ""
                            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
                        }
                    )
                }
                else -> {
                    TopAppBar(
                        title = { Text("Tất cả tài liệu") },
                        actions = {
                            IconButton(onClick = { searchMode = true }) { Icon(Icons.Default.Search, "Tìm kiếm") }
                            IconButton(onClick = { viewMode = if (viewMode == "list") "grid" else "list" }) {
                                if (viewMode == "list") Icon(Icons.Default.GridView, "Chế độ xem lưới") else Icon(Icons.Default.ViewList, "Chế độ xem danh sách")
                            }
                            Box {
                               IconButton(onClick = { showImportMenu = true }) { Icon(Icons.Default.Add, "Nhập") }
                                DropdownMenu(expanded = showImportMenu, onDismissRequest = { showImportMenu = false }) {
                                    DropdownMenuItem(text = { Text("Nhập tập tin") }, onClick = { documentPickerLauncher.launch("application/pdf"); showImportMenu = false })
                                    DropdownMenuItem(text = { Text("Nhập ảnh") }, onClick = { imagePickerLauncher.launch("image/*"); showImportMenu = false })
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        if (documentsToDisplay.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    text = if (searchQuery.isNotBlank()) "Không tìm thấy tài liệu nào" else "Không có tài liệu nào",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val onDocumentClick: (DocumentFile, Boolean) -> Unit = {
                doc, isSelected ->
                if (selectionMode) {
                    selectedDocuments = if (isSelected) selectedDocuments - doc else selectedDocuments + doc
                } else {
                    FileOpener.openPdf(context, doc.file)
                }
            }

            val onDocumentLongClick: (DocumentFile) -> Unit = {
                 doc ->
                if (!selectionMode) {
                    selectionMode = true
                    selectedDocuments = selectedDocuments + doc
                }
            }

            if (viewMode == "list") {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(documentsToDisplay, key = { it.file.absolutePath }) { doc ->
                        val isSelected = selectedDocuments.contains(doc)
                        DocumentCard(
                            title = doc.name,
                            date = doc.formattedDate,
                            pageCount = doc.pageCount,
                            isSelected = isSelected,
                            onClick = { onDocumentClick(doc, isSelected) },
                            onLongClick = { onDocumentLongClick(doc) },
                            onDeleteClick = { scope.launch { DocumentRepository.deleteDocument(doc) } },
                            onRenameClick = { newName -> scope.launch { DocumentRepository.renameDocument(doc, newName) } }
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
                    items(documentsToDisplay, key = { it.file.absolutePath }) { doc ->
                        val isSelected = selectedDocuments.contains(doc)
                        DocumentCard(
                            title = doc.name,
                            date = doc.formattedDate,
                            pageCount = doc.pageCount,
                            isSelected = isSelected,
                            onClick = { onDocumentClick(doc, isSelected) },
                            onLongClick = { onDocumentLongClick(doc) },
                            onDeleteClick = { scope.launch { DocumentRepository.deleteDocument(doc) } },
                            onRenameClick = { newName -> scope.launch { DocumentRepository.renameDocument(doc, newName) } }
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
