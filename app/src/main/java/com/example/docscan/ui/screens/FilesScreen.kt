package com.example.docscan.ui.screens

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.docscan.logic.storage.AppStorage
import com.example.docscan.logic.storage.DocumentFile
import com.example.docscan.logic.utils.FileOpener
import com.example.docscan.ui.components.DocumentCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(navController: NavHostController? = null) {
    val context = LocalContext.current
    var documents by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }

    // Load documents when the screen is first composed
    LaunchedEffect(Unit) {
        documents = AppStorage.listPdfDocuments()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tất cả (${documents.size})") },
                actions = {
                    IconButton(onClick = { /*TODO: Sort logic*/ }) { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort") }
                    IconButton(onClick = { /*TODO: Grid/List toggle*/ }) { Icon(Icons.Default.GridView, contentDescription = "Grid View") }
                    IconButton(onClick = { /*TODO: Selection mode*/ }) { Icon(Icons.Default.Check, contentDescription = "Select") }
                }
            )
        }
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(it)) {
            // Action Buttons
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = { /*TODO*/ }) { Text("Nhập tập tin") }
                    Button(onClick = { /*TODO*/ }) { Text("Nhập ảnh") }
                    Button(onClick = { /*TODO*/ }) { Text("Tạo thư mục") }
                }
            }

            // List of documents
            if (documents.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Chưa có tài liệu nào")
                    }
                }
            } else {
                items(documents) { doc ->
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

@Preview(name = "FilesScreen Preview", showBackground = true)
@Composable
fun Preview_FilesScreen() {
    FilesScreen(navController = rememberNavController())
}
