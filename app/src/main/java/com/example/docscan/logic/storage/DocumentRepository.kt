package com.example.docscan.logic.storage

import android.content.Context
import java.io.File

class DocumentRepository(private val context: Context) {

    data class DocSummary(
        val docId: String,
        val docDir: File,
        val pagesDir: File,
        val pageCount: Int,
        val coverPage: File? // first page jpg if any
    )

    private fun documentsRoot(): File =
        File(context.getExternalFilesDir(null), "documents").apply { mkdirs() }

    fun listDocumentsNewestFirst(): List<DocSummary> {
        val root = documentsRoot()
        val docDirs = root.listFiles { f -> f.isDirectory }?.toList().orEmpty()

        return docDirs
            .sortedByDescending { it.lastModified() }
            .mapNotNull { docDir ->
                val docId = docDir.name
                val pagesDir = File(docDir, "pages")
                val pages = pagesDir.listFiles { f ->
                    f.isFile && (f.name.endsWith(".jpg", true) || f.name.endsWith(".jpeg", true))
                }?.sortedBy { it.name }.orEmpty()

                DocSummary(
                    docId = docId,
                    docDir = docDir,
                    pagesDir = pagesDir,
                    pageCount = pages.size,
                    coverPage = pages.firstOrNull()
                )
            }
    }

    fun listPages(docId: String): List<File> {
        val pagesDir = File(documentsRoot(), "$docId/pages")
        val pages = pagesDir.listFiles { f ->
            f.isFile && (f.name.endsWith(".jpg", true) || f.name.endsWith(".jpeg", true))
        }?.sortedBy { it.name }.orEmpty()
        return pages
    }

    fun deleteDocument(docId: String): Boolean {
        val dir = File(documentsRoot(), docId)
        return dir.exists() && dir.deleteRecursively()
    }
}
