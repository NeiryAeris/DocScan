package com.example.docscan.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.docscan.R
import com.example.ocr.core.api.OcrRequest
import com.example.ocr.core.api.OcrResult
import com.example.ocr.mlkit.MlKitOcrEngine
import com.example.ocr.mlkit.fromBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class TextExtractionState(
    val sourceBitmap: Bitmap? = null,
    val result: OcrResult? = null,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextExtractionScreen(navController: NavController?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var state by remember { mutableStateOf(TextExtractionState()) }
    val engine = remember { MlKitOcrEngine() }

    DisposableEffect(Unit) {
        onDispose { engine.close() }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            state = state.copy(errorMessage = null)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            state = state.copy(
                isProcessing = true,
                errorMessage = null,
                result = null,
                sourceBitmap = null
            )
            val bitmap = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }
            if (bitmap == null) {
                state = state.copy(
                    isProcessing = false,
                    errorMessage = context.getString(R.string.text_extraction_decode_error)
                )
                return@launch
            }
            try {
                val request = withContext(Dispatchers.Default) {
                    OcrRequest.fromBitmap(bitmap)
                }
                val result = withContext(Dispatchers.Default) {
                    engine.recognize(request)
                }
                state = state.copy(
                    sourceBitmap = bitmap,
                    result = result,
                    isProcessing = false,
                    errorMessage = null
                )
            } catch (t: Throwable) {
                state = state.copy(
                    sourceBitmap = bitmap,
                    isProcessing = false,
                    errorMessage = t.message ?: context.getString(R.string.text_extraction_unknown_error)
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trích xuất văn bản") },
                navigationIcon = {
                    navController?.let {
                        IconButton(onClick = { it.popBackStack() }) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = null)
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = { launcher.launch("image/*") }, enabled = !state.isProcessing) {
                Text("Chọn ảnh để nhận dạng")
            }

            when {
                state.isProcessing -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Đang xử lý ảnh...")
                    }
                }

                state.result != null -> {
                    ExtractedContent(state)
                }

                state.sourceBitmap != null -> {
                    Text(
                        "Không tìm thấy văn bản trong ảnh.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    Text(
                        "Chọn một ảnh bất kỳ để trích xuất nội dung văn bản ngay trên thiết bị.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtractedContent(state: TextExtractionState) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        state.sourceBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        Text(
            text = "Văn bản nhận dạng",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = state.result?.text.orEmpty(),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
