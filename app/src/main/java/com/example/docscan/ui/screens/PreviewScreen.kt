package com.example.docscan.ui.screens

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.docscan.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PreviewScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val imageBytes = App.previewImageBytes
    val title = App.previewTitle ?: "Xem trước"
    val mimeType = App.previewMimeType ?: "image/png"
    val defaultFileName = App.previewDefaultFileName ?: "file.png"

    val saverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(mimeType),
        onResult = { savedImageUri ->
            if (savedImageUri != null) {
                if (imageBytes != null) {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                context.contentResolver.openOutputStream(savedImageUri)?.use {
                                    it.write(imageBytes)
                                }
                            }
                            Toast.makeText(context, "Đã lưu tệp thành công", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Lỗi khi lưu tệp: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                        App.clearPreviewData()
                        navController.popBackStack()
                    }
                }
            } else {
                Toast.makeText(context, "Đã hủy lưu", Toast.LENGTH_SHORT).show()
            }
        }
    )

    Surface(modifier = Modifier.fillMaxSize()) {
        if (imageBytes == null) {
            Box(contentAlignment = Alignment.Center) {
                Text("Không có dữ liệu để xem trước.")
            }
            return@Surface
        }

        val imageBitmap = remember(imageBytes) {
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size).asImageBitmap()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Image(
                bitmap = imageBitmap,
                contentDescription = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = {
                    App.clearPreviewData()
                    navController.popBackStack()
                }) {
                    Text("Hủy")
                }
                Button(onClick = { saverLauncher.launch(defaultFileName) }) {
                    Text("Lưu")
                }
            }
        }
    }
}
