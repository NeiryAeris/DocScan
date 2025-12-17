package com.example.docscan.logic.storage

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class DocumentFileStore(private val context: Context) {

    data class DocPaths(
        val docId: String,
        val docDir: File,
        val pagesDir: File
    )

    fun createNewDocumentDir(): DocPaths {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val docId = "doc_${ts}_${UUID.randomUUID().toString().take(8)}"

        val docDir = File(context.getExternalFilesDir(null), "documents/$docId").apply { mkdirs() }
        val pagesDir = File(docDir, "pages").apply { mkdirs() }

        return DocPaths(docId = docId, docDir = docDir, pagesDir = pagesDir)
    }

    fun pageFile(pagesDir: File, pageIndex: Int): File {
        val name = "page_${pageIndex.toString().padStart(3, '0')}.jpg"
        return File(pagesDir, name)
    }
}
