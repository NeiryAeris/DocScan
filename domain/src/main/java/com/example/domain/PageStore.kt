package com.example.domain

// Interface for storing pages and document metadata
interface PageStore {
    fun savePage(page: Page)
    fun getPage(pageId: String): Page?
    fun saveDocument(document: Document)
    fun getDocument(documentId: String): Document?
}

