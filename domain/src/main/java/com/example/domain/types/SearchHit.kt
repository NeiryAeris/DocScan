package com.example.domain.types


data class SearchHit(
    val docId: DocumentId,
    val title: String,
    val snippet: String,
    val score: Double
)