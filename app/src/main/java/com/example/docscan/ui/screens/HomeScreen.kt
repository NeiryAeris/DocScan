package com.example.docscan.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.docscan.ui.components.ActionGrid
import com.example.docscan.ui.components.ActionItemData
import com.example.docscan.ui.components.DocumentCard
import com.example.docscan.ui.components.SectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {

    // Lấy màu primary từ theme
    val primaryColor = MaterialTheme.colorScheme.primary
    // (Không cần onPrimaryColor nữa)

    val homeActions = listOf(
        // Cập nhật mục "Quét"
        ActionItemData(
            icon = Icons.Default.Scanner,
            label = "Quét",
            // backgroundColor = null, // (Bỏ qua để dùng màu mặc định)
            iconTintColor = primaryColor // <-- Chỉ đổi màu icon
        ),
        ActionItemData(Icons.Default.PictureAsPdf, "Công cụ PDF"),
        ActionItemData(Icons.Default.Image, "Nhập ảnh"),
        // Các mục đã bị loại bỏ ở yêu cầu trước
        ActionItemData(Icons.Default.MoreHoriz, "Tất cả")
    )

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
            SectionTitle(title = "Gần đây", actionText = "Xem tất cả") {}
        }
        // Hiển thị một vài tài liệu mẫu
        item {
            DocumentCard(title = "CamScanner 09-10-2025 20.16", date = "09/10/2025 20:16", pageCount = 1)
        }
    }
}