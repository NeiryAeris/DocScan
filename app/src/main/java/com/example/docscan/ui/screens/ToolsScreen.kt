package com.example.docscan.ui.screens
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.docscan.ui.components.ActionGrid
import com.example.docscan.ui.components.ActionItemData
import com.example.docscan.ui.components.SectionTitle

@Composable
fun ToolsScreen() {
    val scanActions = listOf(
        ActionItemData(Icons.Default.CreditCard, "Thẻ ID"),
        ActionItemData(Icons.Default.TextFields, "Trích xuất văn bản"),
        ActionItemData(Icons.Default.Face, "Trình tạo ảnh ID"),
        ActionItemData(Icons.Default.Functions, "Công thức")
    )
    val importActions = listOf(
        ActionItemData(Icons.Default.Image, "Nhập ảnh"),
        ActionItemData(Icons.Default.UploadFile, "Nhập tệp tin")
    )
    val convertActions = listOf(
        ActionItemData(Icons.Default.Description, "Thành Word"),
        ActionItemData(Icons.Default.GridOn, "Thành Excel"),
        ActionItemData(Icons.Default.Slideshow, "Thành PPT"),
        ActionItemData(Icons.Default.Photo, "PDF thành ảnh")
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