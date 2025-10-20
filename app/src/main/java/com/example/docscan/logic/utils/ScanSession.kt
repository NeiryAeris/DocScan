package com.example.docscan.logic.utils

import androidx.compose.runtime.mutableStateListOf
import com.example.pipeline_core.EncodedPage

/**
 * Simple in-memory session store for scanned pages.
 * Holds pages as EncodedPage (PNG bytes + w/h) so the UI can export later.
 *
 * - Compose observes changes via mutableStateListOf
 * - Not thread-safe; mutate on the main thread with UI events
 */
object ScanSession {

    // Backing store observed by Compose
    private val _pages = mutableStateListOf<EncodedPage>()

    /** Read-only view for UI rendering */
    val pages: List<EncodedPage> get() = _pages

    /** Number of pages currently in the session */
    val count: Int get() = _pages.size

    /** True if there are no pages */
    val isEmpty: Boolean get() = _pages.isEmpty()

    /** Add a single page to the end */
    fun add(page: EncodedPage) {
        _pages += page
    }

    /** Add multiple pages (e.g., batch import) */
    fun addAll(pages: Collection<EncodedPage>) {
        if (pages.isNotEmpty()) _pages.addAll(pages)
    }

    /** Replace a page at index */
    fun replace(index: Int, page: EncodedPage) {
        require(index in _pages.indices) { "Index out of bounds: $index" }
        _pages[index] = page
    }

    /** Remove a page at index and return it */
    fun removeAt(index: Int): EncodedPage {
        require(index in _pages.indices) { "Index out of bounds: $index" }
        return _pages.removeAt(index)
    }

    /** Remove all pages */
    fun clear() {
        _pages.clear()
    }

    /**
     * Move a page from one index to another (reorder).
     * Example: move(3, 0) moves page at 3 to the front.
     */
    fun move(fromIndex: Int, toIndex: Int) {
        require(fromIndex in _pages.indices) { "fromIndex out of bounds: $fromIndex" }
        require(toIndex in 0.._pages.size) { "toIndex out of bounds: $toIndex" }
        if (fromIndex == toIndex) return

        val item = _pages.removeAt(fromIndex)
        if (toIndex >= _pages.size) {
            _pages.add(item)
        } else {
            _pages.add(toIndex, item)
        }
    }

    /** Get a defensive copy for non-Compose consumers (e.g., exporter) */
    fun snapshot(): List<EncodedPage> = _pages.toList()
}