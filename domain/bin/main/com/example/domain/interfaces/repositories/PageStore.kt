package com.example.domain.interfaces.repositories

import com.example.domain.types.Document
import com.example.domain.types.Page

// Interface for storing pages and document metadata
interface PageStore {
    fun savePage(page: Page)
    fun getPage(pageId: String): Page?
    fun saveDocument(document: Document)
    fun getDocument(documentId: String): Document?
}