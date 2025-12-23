package com.example.docscan.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentCard(
    title: String,
    date: String,
    pageCount: Int,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onRenameClick: (String) -> Unit = {},
    isSelected: Boolean = false,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    if (showRenameDialog) {
        RenameDialog(
            currentName = title,
            onDismiss = { showRenameDialog = false },
            onConfirm = {
                onRenameClick(it)
                showRenameDialog = false
            }
        )
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.PictureAsPdf,
                contentDescription = "Document Thumbnail",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$date | $pageCount trang",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Đổi tên") },
                        onClick = {
                            showMenu = false
                            showRenameDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Xóa") },
                        onClick = {
                            showMenu = false
                            onDeleteClick()
                        }
                    )
                }
            }
        }
    }
}

@Preview(name = "DocumentCard Preview", showBackground = true)
@Composable
fun Preview_DocumentCard() {
    DocumentCard(title = "Sample Document With A Very Long Name That Overflows", date = "10/10/2025", pageCount = 3)
}
