package com.example.docscan.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun SignaturePad(
    modifier: Modifier = Modifier,
    onSignatureChanged: (Path) -> Unit
) {
    val currentPath = remember { mutableStateOf(Path()) }
    val paths = remember { mutableStateOf<List<Path>>(emptyList()) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f)
            .background(Color.LightGray)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        currentPath.value.moveTo(it.x, it.y)
                    },
                    onDrag = { change, _ ->
                        currentPath.value.lineTo(change.position.x, change.position.y)
                        onSignatureChanged(currentPath.value)
                    },
                    onDragEnd = {
                        paths.value = paths.value + currentPath.value
                        currentPath.value = Path()
                    }
                )
            }
    ) {
        paths.value.forEach { path ->
            drawPath(
                path = path,
                color = Color.Black,
                style = Stroke(width = 4.dp.toPx())
            )
        }
        drawPath(
            path = currentPath.value,
            color = Color.Black,
            style = Stroke(width = 4.dp.toPx())
        )
    }
}
