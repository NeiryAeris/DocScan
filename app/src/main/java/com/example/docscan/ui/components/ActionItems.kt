package com.example.docscan.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf

data class ActionItemData(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit = {},
    val backgroundColor: Color? = null, // Giữ nguyên
    val iconTintColor: Color? = null   // Giữ nguyên
)

@Composable
fun ActionGrid(items: List<ActionItemData>, columnCount: Int = 4) {
    // Sử dụng Column thay vì LazyVerticalGrid
    Column(
        modifier = Modifier.padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Chia danh sách các mục thành các hàng
        items.chunked(columnCount).forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hiển thị từng mục trong hàng
                for (item in rowItems) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        ActionGridItem(item)
                    }
                }
                // Thêm các Spacer để căn chỉnh hàng cuối nếu không đủ mục
                if (rowItems.size < columnCount) {
                    for (i in 0 until (columnCount - rowItems.size)) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun ActionGridItem(item: ActionItemData) {
    // Xác định màu sắc để sử dụng
    val surfaceColor = item.backgroundColor ?: MaterialTheme.colorScheme.surfaceVariant

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.clickable { item.onClick() }
    ) {
        // Áp dụng màu nền (sẽ là màu default 'surfaceVariant' nếu item.backgroundColor là null)
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = surfaceColor, // <-- Sử dụng màu nền
            modifier = Modifier.size(48.dp)
        ) {
            // Áp dụng màu icon tùy chỉnh
            val iconTint = item.iconTintColor
            if (iconTint != null) {
                Icon(
                    item.icon,
                    contentDescription = item.label,
                    modifier = Modifier.padding(12.dp),
                    tint = iconTint // <-- Áp dụng màu icon
                )
            } else {
                Icon(
                    item.icon,
                    contentDescription = item.label,
                    modifier = Modifier.padding(12.dp)
                    // (Sử dụng màu icon mặc định 'onSurfaceVariant')
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = item.label, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 16.sp)
    }
}


@Composable
fun SectionTitle(title: String, actionText: String? = null, onActionClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        if (actionText != null && onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(actionText, color = Color.Gray, fontSize = 14.sp)
            }
        }
    }
}

@Preview(name = "ActionGrid Preview", showBackground = true)
@Composable
fun Preview_ActionGrid() {
    val sampleItems = listOf(
        ActionItemData(Icons.Filled.Camera, "Scan"),
        ActionItemData(Icons.Filled.Edit, "Edit"),
        ActionItemData(Icons.Filled.Description, "Docs"),
        ActionItemData(Icons.Filled.PictureAsPdf, "PDF")
    )
    ActionGrid(items = sampleItems, columnCount = 4)
}

@Preview(name = "ActionGridItem Preview", showBackground = true)
@Composable
fun Preview_ActionGridItem() {
    ActionGridItem(ActionItemData(Icons.Filled.Camera, "Scan"))
}

@Preview(name = "SectionTitle Preview", showBackground = true)
@Composable
fun Preview_SectionTitle() {
    SectionTitle(title = "Section Title", actionText = "Action") {}
}
