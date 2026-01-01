package com.example.docscan.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.docscan.App
import com.example.docscan.R
import com.example.docscan.auth.FirebaseIdTokenStore
import com.example.ocr.core.api.OcrImage
import com.example.ocr_remote.ChatMessageDto
import com.example.ocr_remote.RemoteAiAskResponseDto
import com.example.ocr_remote.RemoteAiClientImpl
import com.example.ocr_remote.RemoteAiPageInDto
import com.example.ocr_remote.RemoteAiUpsertOcrRequestDto
import com.example.ocr_remote.RemoteChatResult
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.nio.ByteBuffer

@Composable
fun ChatScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val firebaseUser = remember { FirebaseAuth.getInstance().currentUser }

    // Generic chat (already in your App)
    val chatClient = App.chatClient

    // AI RAG client (needs Firebase token)
    val aiClient = remember {
        RemoteAiClientImpl(
            baseUrl = "https://gateway.neirylittlebox.com",
            authTokenProvider = { FirebaseIdTokenStore.get() }
        )
    }

    // OCR gateway (already in your App)
    val ocrGateway = App.ocrGateway

    var prompt by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<ChatMessageDto>>(emptyList()) }

    var isAsking by remember { mutableStateOf(false) }
    var isIndexing by remember { mutableStateOf(false) }

    // Active document
    var activeDocId by remember { mutableStateOf<String?>(null) }
    var activeDocTitle by remember { mutableStateOf<String?>(null) }
    var useDocumentMode by remember { mutableStateOf(true) } // only matters if activeDocId != null

    fun uriDisplayName(uri: Uri): String? {
        val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && it.moveToFirst()) return it.getString(nameIndex)
        }
        return null
    }

    suspend fun copyUriToCache(uri: Uri, suggestedName: String?): File = withContext(Dispatchers.IO) {
        val name = (suggestedName ?: "document.pdf").replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val outFile = File(context.cacheDir, "chat_upload_$name")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open selected file" }
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
        }
        outFile
    }

    fun stableDocId(file: File): String {
        // Fast + stable enough: SHA-256 of (size + first 1MB)
        val md = MessageDigest.getInstance("SHA-256")
        md.update(file.length().toString().toByteArray())
        file.inputStream().use { input ->
            val buf = ByteArray(1024 * 1024)
            val read = input.read(buf)
            if (read > 0) md.update(buf, 0, read)
        }
        return md.digest().joinToString("") { "%02x".format(it) }.take(32)
    }

    suspend fun ocrPdfToPages(docId: String, pdfFile: File, maxPages: Int = 30): List<RemoteAiPageInDto> =
        withContext(Dispatchers.IO) {
            val pages = mutableListOf<RemoteAiPageInDto>()

            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)

            try {
                val count = minOf(renderer.pageCount, maxPages)
                for (i in 0 until count) {
                    val page = renderer.openPage(i)
                    try {
                        // Render at a reasonable scale (avoid OOM)
                        val scale = 2
                        val width = page.width * scale
                        val height = page.height * scale
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val buffer = ByteBuffer.allocate(bitmap.byteCount)
                        bitmap.copyPixelsToBuffer(buffer)

                        val ocrImage = OcrImage.Rgba8888(
                            bytes = buffer.array(),
                            width = bitmap.width,
                            height = bitmap.height,
                            rowStride = bitmap.rowBytes
                        )

                        val result = ocrGateway.recognize(
                            docId = docId,
                            pageId = "page_${i + 1}",
                            image = ocrImage
                        )

                        val text = result.text.raw.ifBlank { "" }
                        pages.add(RemoteAiPageInDto(pageNumber = i + 1, text = text))

                        bitmap.recycle()
                    } finally {
                        page.close()
                    }
                }
            } finally {
                renderer.close()
                pfd.close()
            }

            pages
        }

    suspend fun indexPdfIntoRag(docTitle: String?, pdfFile: File): String = withContext(Dispatchers.IO) {
        val docId = stableDocId(pdfFile)

        // 1) OCR all pages -> list of RemoteAiPageInDto
        val pages = ocrPdfToPages(docId, pdfFile, maxPages = 30)

        // 2) Upsert into vector index (gateway builds chunks server-side)
        aiClient.upsertOcrIndex(
            RemoteAiUpsertOcrRequestDto(
                docId = docId,
                title = docTitle ?: pdfFile.name,
                replace = true,
                pages = pages
            )
        )

        docId
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult

            val mime = context.contentResolver.getType(uri)
            if (mime != null && mime != "application/pdf") {
                Toast.makeText(context, "Hiện chỉ hỗ trợ PDF để index: ($mime)", Toast.LENGTH_LONG).show()
                return@rememberLauncherForActivityResult
            }

            scope.launch {
                try {
                    isIndexing = true
                    val displayName = uriDisplayName(uri)
                    val file = copyUriToCache(uri, displayName)

                    activeDocTitle = displayName ?: file.name
                    useDocumentMode = true

                    val docId = indexPdfIntoRag(activeDocTitle, file)
                    activeDocId = docId

                    Toast.makeText(context, "Đã index: $activeDocTitle", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    activeDocId = null
                    activeDocTitle = null
                    Toast.makeText(context, "Index lỗi: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isIndexing = false
                }
            }
        }
    )

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            prompt = results?.get(0) ?: ""
        }
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói để bắt đầu trò chuyện...")
            }
            speechRecognizerLauncher.launch(intent)
        } else {
            Toast.makeText(context, "Quyền ghi âm đã bị từ chối.", Toast.LENGTH_SHORT).show()
        }
    }

    fun startVoiceRecognition() {
        when (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)) {
            PackageManager.PERMISSION_GRANTED -> {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói để bắt đầu trò chuyện...")
                }
                speechRecognizerLauncher.launch(intent)
            }
            else -> requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun formatRagMeta(resp: RemoteAiAskResponseDto): String {
        if (resp.usedChunks <= 0) return ""
        val top = resp.citations.take(3).joinToString("\n") { c ->
            val doc = c.docId ?: "doc"
            val page = c.page?.let { "p$it" } ?: "p?"
            val score = c.score?.let { String.format("%.3f", it) } ?: "?"
            "• $doc / $page (score=$score)"
        }
        return "\n\nSources:\n$top"
    }

    fun sendMessage() {
        if (prompt.isBlank() || isAsking || isIndexing) return

        val historySnapshot = messages
        val currentPrompt = prompt.trim()
        prompt = ""

        val userMessage = ChatMessageDto(role = "user", text = currentPrompt)
        messages = historySnapshot + userMessage
        isAsking = true

        scope.launch {
            try {
                val hasDoc = (activeDocId != null && useDocumentMode)

                if (hasDoc) {
                    // ✅ RAG ask (NO history param here)
                    val resp = aiClient.askChat(
                        question = currentPrompt,
                        docIds = listOf(activeDocId!!),
                        topK = 8
                    )
                    val answerText = if (resp.hasError()) {
                        "Error: ${resp.error}"
                    } else {
                        resp.answerText()
                    }
                    val text = answerText + formatRagMeta(resp)
                    messages = messages + ChatMessageDto(role = "model", text = text)
                } else {
                    // ✅ Generic chat (keeps your history behavior)
                    val result = chatClient.ask(
                        prompt = currentPrompt,
                        history = historySnapshot
                    )
                    val reply = when (result) {
                        is RemoteChatResult.Success -> result.response
                        is RemoteChatResult.Error -> "Error: ${result.message}"
                    }
                    messages = messages + ChatMessageDto(role = "model", text = reply)
                }
            } catch (e: Exception) {
                messages = messages + ChatMessageDto(role = "model", text = "Error: ${e.message}")
            } finally {
                isAsking = false
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Trợ lý AI DocScan",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = Color.Black
            )

            // Active document badge + mode toggle
            if (activeDocId != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF3F4F6), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeDocTitle ?: "Document",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Black
                        )
                        Text(
                            text = if (useDocumentMode) "Mode: Document (RAG)" else "Mode: General",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }

                    Text(
                        text = if (useDocumentMode) "Switch: General" else "Switch: Doc",
                        color = Color(0xFF6366F1),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .clickable { useDocumentMode = !useDocumentMode }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(onClick = {
                        activeDocId = null
                        activeDocTitle = null
                        useDocumentMode = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear document",
                            tint = Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                reverseLayout = true
            ) {
                items(messages.reversed()) { message ->
                    ChatMessageItem(message = message, firebaseUser)
                }
            }

            if (isIndexing || isAsking) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = when {
                            isIndexing -> "Đang index tài liệu..."
                            else -> "Trợ lí đang phản hồi..."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { filePickerLauncher.launch("application/pdf") },
                    enabled = !isIndexing && !isAsking
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Chọn PDF để index",
                        tint = Color.Gray
                    )
                }

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Hỏi bất cứ điều gì...") },
                    enabled = !isIndexing && !isAsking,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        cursorColor = Color.Black
                    )
                )

                IconButton(onClick = { startVoiceRecognition() }, enabled = !isIndexing && !isAsking) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Ghi âm giọng nói",
                        tint = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { sendMessage() },
                    enabled = !isIndexing && !isAsking && prompt.isNotBlank()
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.send),
                        contentDescription = "Gửi",
                        tint = Color.Unspecified
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatMessageDto, firebaseUser: com.google.firebase.auth.FirebaseUser?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (message.role == "user") Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (message.role == "model") {
            Icon(
                painter = painterResource(id = R.drawable.ic_ai_assistant),
                contentDescription = "AI Avatar",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                tint = Color.Unspecified
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message.text,
                color = Color.Black,
                modifier = Modifier.padding(12.dp)
            )
        }

        if (message.role == "user") {
            Text(
                text = message.text,
                color = Color.White,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF6366F1), Color(0xFFA855F7))
                        ),
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(12.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (firebaseUser?.photoUrl != null) {
                AsyncImage(
                    model = firebaseUser.photoUrl.toString(),
                    contentDescription = "User Avatar",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "User Avatar",
                    modifier = Modifier.size(40.dp),
                    tint = Color.Gray
                )
            }
        }
    }
}
