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
import androidx.compose.ui.tooling.preview.PreviewFontScale
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
@Preview(name = "Grid • 4 columns • phone", showBackground = true, device = Devices.PIXEL_7)
@PreviewLightDark
@Composable
fun Preview_ActionGrid_4Cols() {
    PreviewSurface {
        ActionGrid(items = sampleItems, columnCount = 4)
    }
}

@Preview(name = "Grid • 3 columns • small phone", showBackground = true, device = Devices.PHONE)
@Composable
fun Preview_ActionGrid_3Cols() {
    PreviewSurface {
        ActionGrid(items = sampleItems, columnCount = 3)
    }
}

@Preview(name = "Grid • long labels • fontScale 1.3", showBackground = true, device = Devices.PIXEL_7)
@PreviewFontScale // Uses default 1.2; combine with name to indicate intent
@Composable
fun Preview_ActionGrid_LongLabels() {
    val longItems = sampleItems.mapIndexed { index, it ->
        // Make every second label longer to test wrapping
        if (index % 2 == 0) it.copy(label = it.label + " • ultra long label to test wrapping")
        else it
    }
    PreviewSurface {
        ActionGrid(items = longItems, columnCount = 4)
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
