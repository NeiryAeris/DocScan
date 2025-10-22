package com.example.domain

data class Document(
    val id: String,
    val title: String,
    val pages: List<Page> // List of pages inside the document
)

data class Page(
    val id: String,
    val documentId: String,
    val contentUri: String, // URI to the processed image file
    val status: PageStatus // Status of the page (e.g., QUEUED, PROCESSING, PROCESSED)
)

enum class PageStatus {
    QUEUED,
    PROCESSING,
    PROCESSED,
    FAILED
}
