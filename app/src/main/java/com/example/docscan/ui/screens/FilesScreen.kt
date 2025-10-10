package com.example.docscan.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.docscan.ui.components.DocumentCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(navController: NavHostController) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            // Top Bar
            TopAppBar(
                title = { Text("Tất cả (1)") },
                actions = {
                    IconButton(onClick = { /*TODO*/ }) { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort") }
                    IconButton(onClick = { /*TODO*/ }) { Icon(Icons.Default.GridView, contentDescription = "Grid View") }
                    IconButton(onClick = { /*TODO*/ }) { Icon(Icons.Default.Check, contentDescription = "Select") }
                }
            )
        }

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
        items(5) { // Thay bằng danh sách thực tế
            DocumentCard(title = "CamScanner 09-10-2025 20.16", date = "09/10/2025 20:16", pageCount = 1)
        }
    }
}

@Preview(name = "FilesScreen Preview", showBackground = true)
@Composable
fun Preview_FilesScreen() {
    FilesScreen(navController = rememberNavController())
}
