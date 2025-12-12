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
import com.example.docscan.logic.utils.NodeCloudOcrGateway
import com.example.ocr.core.api.CloudOcrGateway
import com.example.ocr.core.api.OcrImage
import com.example.ocr_remote.RemoteOcrClient
import com.example.ocr_remote.RemoteOcrClientImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

// For now, a simple constant base URL for the gateway.
// You can later move this to BuildConfig or a config module.
private const val DEBUG_OCR_BASE_URL = "http://10.0.2.2:4000" // emulator -> host

private data class TextExtractionState(
    val sourceBitmap: Bitmap? = null,
    val recognizedText: String? = null,
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

    // Remote OCR client + gateway, remembered for the lifetime of this composable
    val remoteClient: RemoteOcrClient = remember {
        RemoteOcrClientImpl(
            baseUrl = DEBUG_OCR_BASE_URL,
            // TODO: wire to your real AuthManager / token provider
            authTokenProvider = { null }
        )
    }
    val cloudGateway: CloudOcrGateway = remember {
        NodeCloudOcrGateway(remoteClient)
    }

    // No engine to close anymore, but we keep this pattern for future if needed
    DisposableEffect(Unit) {
        onDispose {
            // nothing to close for now
        }
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
                recognizedText = null,
                sourceBitmap = null
            )

            // 1) Load bitmap from URI
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
                // 2) Convert Bitmap -> OcrImage (RGBA)
                val ocrImage = withContext(Dispatchers.Default) {
                    bitmap.toOcrRgba()
                }

                // 3) Build CloudOcrGateway.Request
                val req = CloudOcrGateway.Request(
                    image = ocrImage,
                    lang = "vie+eng", // adjust to your languages
                    hints = mapOf(
                        "pageId" to "text-extract-${System.currentTimeMillis()}",
                        "docId" to "local-doc",
                        "pageIndex" to "0",
                        "rotation" to "0"
                    )
                )

                // 4) Call remote OCR gateway
                val resp = withContext(Dispatchers.IO) {
                    cloudGateway.recognize(req)
                }

                state = state.copy(
                    sourceBitmap = bitmap,
                    recognizedText = resp.text,
                    isProcessing = false,
                    errorMessage = null
                )
            } catch (t: Throwable) {
                state = state.copy(
                    sourceBitmap = bitmap,
                    isProcessing = false,
                    errorMessage = t.message
                        ?: context.getString(R.string.text_extraction_unknown_error)
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
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = null
                            )
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

                state.recognizedText != null -> {
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
                        "Chọn một ảnh bất kỳ để trích xuất nội dung văn bản (OCR backend).",
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
            text = state.recognizedText.orEmpty(),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * Convert an ARGB_8888 Bitmap into OcrImage.Rgba8888 so NodeCloudOcrGateway
 * can turn it into JPEG and send to your Node/Python backend.
 */
private fun Bitmap.toOcrRgba(): OcrImage.Rgba8888 {
    val bmp = if (config != Bitmap.Config.ARGB_8888) {
        copy(Bitmap.Config.ARGB_8888, false)
    } else {
        this
    }

    val w = bmp.width
    val h = bmp.height
    val bytes = ByteArray(w * h * 4)
    val buffer = ByteBuffer.wrap(bytes)
    bmp.copyPixelsToBuffer(buffer)

    return OcrImage.Rgba8888(
        width = w,
        height = h,
        bytes = bytes,
        rowStride = w * 4,
        premultiplied = bmp.isPremultiplied
    )
}
