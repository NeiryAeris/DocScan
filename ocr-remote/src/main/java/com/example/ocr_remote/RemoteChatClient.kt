package com.example.ocr_remote

// Data models
data class ChatMessageDto(
    val role: String, // "user" or "model"
    val text: String
)

data class RemoteChatRequestDto(
    val prompt: String,
    val history: List<ChatMessageDto>
)

data class RemoteChatResponseDto(
    val response: String?,
    val error: String?
)

// Result wrapper
sealed class RemoteChatResult {
    data class Success(val response: String) : RemoteChatResult()
    data class Error(val message: String) : RemoteChatResult()
}

// Client interface
interface RemoteChatClient {
    suspend fun ask(
        prompt: String,
        history: List<ChatMessageDto>
    ): RemoteChatResult
}
