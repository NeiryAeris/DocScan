package com.example.docscan.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AttachFile
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
import com.example.ocr_remote.ChatMessageDto
import com.example.ocr_remote.RemoteChatResult
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(navController: NavHostController) {
    val context = LocalContext.current
    val chatClient = App.chatClient
    val scope = rememberCoroutineScope()
    val firebaseUser = remember { FirebaseAuth.getInstance().currentUser }

    var prompt by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<ChatMessageDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                Toast.makeText(context, "Tệp đã chọn: $it", Toast.LENGTH_LONG).show()
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
    ) { isGranted: Boolean ->
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
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    fun sendMessage() {
        if (prompt.isNotBlank() && !isLoading) {
            val userMessage = ChatMessageDto(role = "user", text = prompt)
            messages = messages + userMessage
            val currentPrompt = prompt
            prompt = ""
            isLoading = true

            scope.launch {
                val result = chatClient.ask(prompt = currentPrompt, history = messages.dropLast(1))
                val responseMessage = when (result) {
                    is RemoteChatResult.Success -> ChatMessageDto(role = "model", text = result.response)
                    is RemoteChatResult.Error -> ChatMessageDto(role = "model", text = "Error: ${result.message}")
                }
                messages = messages + responseMessage
                isLoading = false
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
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                reverseLayout = true
            ) {
                items(messages.reversed()) { message ->
                    ChatMessageItem(message = message, firebaseUser)
                }
            }
            if (isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Trợ lí đang phản hồi...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Tải tệp lên",
                        tint = Color.Gray
                    )
                }
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Hỏi bất cứ điều gì...") },
                    enabled = !isLoading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        cursorColor = Color.Black
                    )
                )
                IconButton(onClick = { startVoiceRecognition() }, enabled = !isLoading) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Ghi âm giọng nói",
                        tint = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { sendMessage() }, enabled = !isLoading && prompt.isNotBlank()) {
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
