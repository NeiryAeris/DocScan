package com.example.docscan.logic.storage

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Represents a document file stored in the app's public directory.
 */
data class DocumentFile(
    val file: File,
    val name: String,
    val formattedDate: String,
    val pageCount: Int // Placeholder, actual page count might need to be extracted from PDF
)

object AppStorage {

    private const val ROOT_DIR_NAME = "DocscanFile"

    /**
     * Returns the root directory for all app-related public files.
     * This will be a folder named "DocscanFile" in the public Documents directory.
     * It will be created if it doesn't exist.
     */
    suspend fun getPublicAppDir(): File? = withContext(Dispatchers.IO) {
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (documentsDir == null || (!documentsDir.exists() && !documentsDir.mkdirs())) {
            return@withContext null
        }
        val appDir = File(documentsDir, ROOT_DIR_NAME)
        try {
            if (!appDir.exists()) {
                if (appDir.mkdirs()) appDir else null
            } else {
                appDir
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Lists all PDF document files from the public app directory, sorted by modification date (newest first).
     *
     * @return A list of [DocumentFile] objects.
     */
    suspend fun listPdfDocuments(): List<DocumentFile> = withContext(Dispatchers.IO) {
        val appDir = getPublicAppDir() ?: return@withContext emptyList()

        val pdfs = appDir.listFiles { _, name -> name.lowercase(Locale.ROOT).endsWith(".pdf") }
            ?: return@withContext emptyList()

        // Sort by last modified date, newest first
        pdfs.sortByDescending { it.lastModified() }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        pdfs.map {
            DocumentFile(
                file = it,
                name = it.nameWithoutExtension,
                formattedDate = dateFormat.format(Date(it.lastModified())),
                pageCount = 1 // TODO: Implement actual PDF page count extraction if needed
            )
        }
    }

    /**
     * Deletes a document file.
     *
     * @param documentFile The document to delete.
     * @return True if the file was deleted, false otherwise.
     */
    suspend fun deleteDocument(documentFile: DocumentFile): Boolean = withContext(Dispatchers.IO) {
        try {
            documentFile.file.delete()
        } catch (e: SecurityException) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Renames a document file.
     *
     * @param documentFile The document to rename.
     * @param newName The new name for the document (without extension).
     * @return True if the file was renamed, false otherwise.
     */
    suspend fun renameDocument(documentFile: DocumentFile, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val newFile = File(documentFile.file.parent, "$newName.pdf")
            documentFile.file.renameTo(newFile)
        } catch (e: SecurityException) {
            e.printStackTrace()
            false
        }
    }
}
