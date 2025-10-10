package com.example.docscan.ui.screens
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.docscan.ui.components.ActionGrid
import com.example.docscan.ui.components.ActionItemData
import com.example.docscan.ui.components.SectionTitle
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController

@Composable
fun ToolsScreen(navController: NavHostController) {
    val scanActions = listOf(
        ActionItemData(Icons.Default.CreditCard, "Thẻ ID") { navController.navigate("scan") },
        ActionItemData(Icons.Default.TextFields, "Trích xuất văn bản") { navController.navigate("text_extraction") },
        ActionItemData(Icons.Default.Face, "Trình tạo ảnh ID") { navController.navigate("scan") },
        ActionItemData(Icons.Default.Functions, "Công thức") { /* TODO */ }
    )
    val importActions = listOf(
        ActionItemData(Icons.Default.Image, "Nhập ảnh") { navController.navigate("import_image") },
        ActionItemData(Icons.Default.UploadFile, "Nhập tệp tin") { navController.navigate("import_image") }
    )
    val convertActions = listOf(
        ActionItemData(Icons.Default.Description, "Thành Word") { navController.navigate("pdf_tools") },
        ActionItemData(Icons.Default.GridOn, "Thành Excel") { navController.navigate("pdf_tools") },
        ActionItemData(Icons.Default.Slideshow, "Thành PPT") { navController.navigate("pdf_tools") },
        ActionItemData(Icons.Default.Photo, "PDF thành ảnh") { navController.navigate("pdf_tools") }
    )

    LazyColumn(modifier = Modifier.padding(bottom = 16.dp)) {
        item { SectionTitle("Quét") }
        item { ActionGrid(scanActions) }

        item { SectionTitle("Nhập") }
        item { ActionGrid(importActions, columnCount = 2) }

        item { SectionTitle("Chuyển đổi") }
        item { ActionGrid(convertActions) }
    }
}

@Preview(name = "ToolsScreen Preview", showBackground = true)
@Composable
fun Preview_ToolsScreen() {
    ToolsScreen(navController = rememberNavController())
}
