package com.example.docscan.logic.utils

import java.io.File
import java.util.*

data class ScannedPage(
    val id: String = UUID.randomUUID().toString(),
    val file: File,
    val thumbnail: File? = null,
    val filterMode: String = "color"
)


object DocumentSession {
    private val pages = mutableListOf<ScannedPage>()

    fun addPage (file:File, mode: String = "color") {
        pages.add(ScannedPage(file = file, filterMode = mode))
    }

    fun getPages(): List<ScannedPage> {
        return pages.toList()
    }

    fun removePage(pageId: String) {
        pages.removeAll { it.id == pageId }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        if (fromIndex in pages.indices && toIndex in pages.indices) {
            Collections.swap(pages, fromIndex, toIndex)
        }
    }

    fun clear() = pages.clear()

}