package com.example.domain.interfaces.repositories

import com.example.domain.types.*
import com.example.domain.types.Document as DomainDocument

interface DocumentStore {
    suspend fun upsert(doc: DomainDocument)
    suspend fun upsertPageMeta(docId: DocumentId, pageNo: PageNo, width: Int?, height: Int?, rotation: Int = 0, dpi: Int?)
    suspend fun saveFullText(docId: DocumentId, rawText: String, foldedText: String)
    suspend fun saveTextSpans(docId: DocumentId, spans: List<PageTextSpan>)
}