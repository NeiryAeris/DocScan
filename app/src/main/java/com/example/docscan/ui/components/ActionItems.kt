package com.example.docscan.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
    val icon: Any,
    val label: String,
    val backgroundColor: Color? = null, // Giữ lại để tương thích, nhưng sẽ không được sử dụng
    val iconTintColor: Color? = null,   // Giữ lại để tương thích
    val onClick: () -> Unit = {}
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionGrid(items: List<ActionItemData>) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        items.forEach { item ->
            Box(
                modifier = Modifier.width(80.dp), // Set a fixed width for each item
                contentAlignment = Alignment.TopCenter
            ) {
                ActionGridItem(item)
            }
        }
    }
}

@Composable
fun ActionGridItem(item: ActionItemData) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.clickable { item.onClick() }
    ) {
        val painter = when (item.icon) {
            is ImageVector -> rememberVectorPainter(image = item.icon)
            is Int -> painterResource(id = item.icon)
            else -> throw IllegalArgumentException("Unsupported icon type for ActionItemData")
        }

        Image(
            painter = painter,
            contentDescription = item.label,
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.medium),
            contentScale = ContentScale.Fit // <-- Đã sửa lại thành Fit
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(text = item.label, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onBackground)
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
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        if (actionText != null && onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(actionText, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
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
    ActionGrid(items = sampleItems)
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
