package com.example.domain.interfaces.search

import com.example.domain.types.DocumentId

interface Indexer {
    fun upsert(docId: DocumentId, title: String, body: String)
}