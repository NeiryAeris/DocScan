package com.example.docscan.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.docscan.R // Giả sử bạn có ảnh placeholder trong drawable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun DocumentCard(
    title: String,
    date: String,
    pageCount: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                // Sử dụng một icon vector đơn giản làm placeholder
                painter = rememberVectorPainter(image = Icons.Default.Image),
                contentDescription = "Document Thumbnail",
                modifier = Modifier
                    .size(60.dp, 80.dp)
                    .clip(MaterialTheme.shapes.small)
                    .padding(8.dp), // Thêm padding để icon đẹp hơn
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
            )
            // KẾT THÚC THAY THẾ

            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "$date | $pageCount trang", color = Color.Gray, fontSize = 12.sp)
            }
            IconButton(onClick = { /*TODO*/ }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options")
            }
        }
    }
}

@Preview(name = "DocumentCard Preview", showBackground = true)
@Composable
fun Preview_DocumentCard() {
    DocumentCard(title = "Sample Document", date = "10/10/2025", pageCount = 3)
}
