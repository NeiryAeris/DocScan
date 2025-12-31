package com.example.docscan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun AppBackground(content: @Composable () -> Unit) {
    val isDarkTheme = MaterialTheme.colorScheme.background == Color.Black

    val backgroundModifier = if (isDarkTheme) {
        Modifier.background(MaterialTheme.colorScheme.background)
    } else {
        Modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFA9FFF8),
                    Color(0xFFF4FAFE),
                    Color(0xFFFFFFFF)
                )
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(backgroundModifier)
    ) {
        content()
    }
}
