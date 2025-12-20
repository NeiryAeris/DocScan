package com.example.docscan.logic.storage

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        return withContext(Dispatchers.IO) {
            val success = AppStorage.deleteDocument(doc)
            if (success) {
                refresh() // Trigger a refresh
            }
            success
        }
    }

    /**
     * Deletes multiple documents and refreshes the list.
     */
    suspend fun deleteDocuments(docs: List<DocumentFile>) {
        withContext(Dispatchers.IO) {
            docs.forEach { doc ->
                AppStorage.deleteDocument(doc)
            }
        }
        refresh()
    }

    /**
     * Renames a document and refreshes the list.
     */
    suspend fun renameDocument(doc: DocumentFile, newName: String): Boolean {
        return withContext(Dispatchers.IO) {
            val success = AppStorage.renameDocument(doc, newName)
            if (success) {
                refresh() // Trigger a refresh
            }
            success
        }
    }

    /**
     * Creates a new PDF file from a list of image URIs and refreshes the document list.
     */
    suspend fun createPdfFromImages(context: Context, imageUris: List<Uri>): Uri? {
        return withContext(Dispatchers.IO) {
            val pdfFile = AppStorage.createPdfFromImages(context, imageUris)
            if (pdfFile != null) {
                refresh()
                pdfFile.toUri()
            } else {
                null
            }
        }
    }

    suspend fun convertPdfToImages(context: Context, doc: DocumentFile) {
        val success = withContext(Dispatchers.IO) {
            AppStorage.convertPdfToImages(context, doc)
        }
        if (success) {
            Toast.makeText(context, "Đã chuyển đổi thành công sang hình ảnh", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Lỗi khi chuyển đổi PDF sang hình ảnh", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Finds a document by its URI in the current list of documents.
     */
    fun findDocumentByUri(uri: Uri): DocumentFile? {
        return _documents.value.find { it.file.toUri() == uri }
    }
}