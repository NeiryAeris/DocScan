package com.example.docscan

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.example.docscan.ui.components.ActionGrid
import com.example.docscan.ui.components.ActionGridItem
import com.example.docscan.ui.components.ActionItemData
import com.example.docscan.ui.components.SectionTitle

// --- Sample data for previews ---
private val sampleItems = listOf(
    ActionItemData(Icons.Filled.Camera, "Scan"),
    ActionItemData(Icons.Filled.Edit, "Enhance"),
    ActionItemData(Icons.Filled.Description, "Docs"),
    ActionItemData(Icons.Filled.PictureAsPdf, "Export PDF"),
    ActionItemData(Icons.Filled.Camera, "ID Scan"),
    ActionItemData(Icons.Filled.Edit, "Crop"),
    ActionItemData(Icons.Filled.Description, "Rename"),
    ActionItemData(Icons.Filled.PictureAsPdf, "Share")
)

// Wrap previews with a Material surface so colors look correct.
@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

// ---------- ActionGrid previews ----------
@Preview(name = "Grid • Phone", showBackground = true, device = Devices.PIXEL_7)
@Preview(name = "Grid • Tablet", showBackground = true, device = Devices.PIXEL_TABLET)
@PreviewLightDark
@Composable
fun Preview_ActionGrid_Responsive() {
    PreviewSurface {
        ActionGrid(items = sampleItems)
    }
}

// ---------- Item & SectionTitle previews ----------
@Preview(name = "Item", showBackground = true)
@Composable
fun Preview_ActionGridItem() {
    PreviewSurface {
        ActionGridItem(ActionItemData(Icons.Filled.Camera, "Scan"))
    }
}

@Preview(name = "SectionTitle with action", showBackground = true)
@Composable
fun Preview_SectionTitle_WithAction() {
    PreviewSurface {
        SectionTitle(title = "Tools", actionText = "See all") { /* no-op */ }
    }
}

@Preview(name = "SectionTitle only", showBackground = true)
@Composable
fun Preview_SectionTitle_Only() {
    PreviewSurface {
        SectionTitle(title = "Recent")
    }
}
