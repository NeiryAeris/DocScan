package com.example.docscan.logic.storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A singleton repository to hold and manage the global list of documents.
 * This ensures data is loaded only once and is instantly available across all screens.
 */
object DocumentRepository {
    private val _documents = MutableStateFlow<List<DocumentFile>>(emptyList())
    val documents = _documents.asStateFlow()

    // Use a custom scope to ensure repository operations don't get cancelled with screen-level scopes.
    private val repositoryScope = CoroutineScope(Dispatchers.Main)

    init {
        // Initial load when the repository is first created.
        refresh()
    }

    /**
     * Refreshes the document list from storage off the main thread.
     * This is a non-blocking call that updates the flow when complete.
     */
    fun refresh() {
        repositoryScope.launch(Dispatchers.IO) {
            val updatedDocs = AppStorage.listPdfDocuments()
            _documents.value = updatedDocs
        }
    }

    /**
     * Deletes a document and refreshes the list.
     */
    suspend fun deleteDocument(doc: DocumentFile): Boolean {
        val success = AppStorage.deleteDocument(doc)
        if (success) {
            refresh() // Trigger a refresh
        }
        return success
    }

    /**
     * Renames a document and refreshes the list.
     */
    suspend fun renameDocument(doc: DocumentFile, newName: String): Boolean {
        val success = AppStorage.renameDocument(doc, newName)
        if (success) {
            refresh() // Trigger a refresh
        }
        return success
    }
}
