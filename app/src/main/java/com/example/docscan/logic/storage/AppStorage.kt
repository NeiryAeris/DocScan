package com.example.docscan.logic.storage

import android.os.Environment
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
    fun getPublicAppDir(): File? {
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (documentsDir == null || (!documentsDir.exists() && !documentsDir.mkdirs())) {
            return null
        }
        val appDir = File(documentsDir, ROOT_DIR_NAME)
        return try {
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
    fun listPdfDocuments(): List<DocumentFile> {
        val appDir = getPublicAppDir() ?: return emptyList()

        val pdfs = appDir.listFiles { _, name -> name.lowercase(Locale.ROOT).endsWith(".pdf") }
            ?: return emptyList()

        // Sort by last modified date, newest first
        pdfs.sortByDescending { it.lastModified() }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        return pdfs.map {
            DocumentFile(
                file = it,
                name = it.nameWithoutExtension,
                formattedDate = dateFormat.format(Date(it.lastModified())),
                pageCount = 1 // TODO: Implement actual PDF page count extraction if needed
            )
        }
    }
}
