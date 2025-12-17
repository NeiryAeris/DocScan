package com.example.docscan.logic.session

import android.content.Context
import com.example.docscan.logic.scan.DraftStore
import com.example.docscan.logic.scan.PageSlot
import com.example.docscan.logic.scan.ScanSessionState
import com.example.docscan.logic.storage.DocumentFileStore
import com.example.imaging_opencv_android.OpenCvImaging
import com.example.pipeline_core.scan.CamScanPipeline
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class SessionController(
    private val context: Context,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val draftStore = DraftStore(context)
    private val docStore = DocumentFileStore(context)

    private val _state = MutableStateFlow(
        ScanSessionState(
            sessionId = UUID.randomUUID().toString(),
            slots = listOf(PageSlot.Empty(0)),
            selectedIndex = 0
        )
    )
    val state: StateFlow<ScanSessionState> = _state

    fun addEmptySlot() {
        _state.update { s ->
            val nextIndex = (s.slots.maxOfOrNull { it.index } ?: -1) + 1
            s.copy(slots = s.slots + PageSlot.Empty(nextIndex), selectedIndex = nextIndex)
        }
    }

    suspend fun processIntoSlot(index: Int, cameraJpeg: ByteArray) {
        val sessionId = _state.value.sessionId

        _state.update { s ->
            s.copy(
                slots = s.slots.map { if (it.index == index) PageSlot.Processing(index) else it },
                lastError = null
            )
        }

        try {
            // safest for parallel later: per-job imaging instance (no shared mutable state)
            val imaging = OpenCvImaging()
            val pipeline = CamScanPipeline(imaging)

            val r = withContext(workDispatcher) {
                pipeline.processJpeg(
                    cameraJpeg = cameraJpeg,
                    options = CamScanPipeline.Options(
                        enhanceMode = "auto_pro",
                        jpegQuality = 85,
                        includeOverlay = false
                    )
                )
            }

            val outFile = draftStore.writeProcessedJpeg(sessionId, index, r.outJpeg)

            _state.update { s ->
                // 1) mark this slot Ready
                val updatedSlots = s.slots.map {
                    if (it.index == index) {
                        PageSlot.Ready(index, outFile.absolutePath, r.quad, r.paperName)
                    } else it
                }

                // 2) ensure there's always an Empty slot at the end
                val hasEmptyTail = updatedSlots.lastOrNull() is PageSlot.Empty
                val slotsWithTail = if (hasEmptyTail) {
                    updatedSlots
                } else {
                    val nextIndex = (updatedSlots.maxOfOrNull { it.index } ?: -1) + 1
                    updatedSlots + PageSlot.Empty(nextIndex)
                }

                s.copy(
                    slots = slotsWithTail,
                    selectedIndex = index, // keep focus on page just processed
                    isDirty = true
                )
            }

        } catch (t: Throwable) {
            _state.update { s ->
                s.copy(
                    slots = s.slots.map {
                        if (it.index == index) PageSlot.Failed(index, t.message ?: "Processing failed")
                        else it
                    },
                    lastError = t.message
                )
            }
        }
    }

    /**
     * Commit all Ready pages (in slot order) into /Android/data/<pkg>/files/documents/<docId>/pages/
     * Returns docId.
     */
    suspend fun saveSession(): String {
        val s = _state.value

        val readySlots = s.slots
            .filterIsInstance<PageSlot.Ready>()
            .sortedBy { it.index }

        require(readySlots.isNotEmpty()) { "No scanned pages to save" }

        val doc = docStore.createNewDocumentDir()

        // Re-index pages sequentially in the saved document (0..N-1)
        readySlots.forEachIndexed { pageIndex, slot ->
            val src = File(slot.processedJpegPath)
            require(src.exists()) { "Draft page missing: ${slot.processedJpegPath}" }

            val dst = docStore.pageFile(doc.pagesDir, pageIndex)
            src.copyTo(dst, overwrite = true)
        }

        // clear draft
        draftStore.clearSession(s.sessionId)

        // reset session
        _state.value = ScanSessionState(
            sessionId = UUID.randomUUID().toString(),
            slots = listOf(PageSlot.Empty(0)),
            selectedIndex = 0
        )

        return doc.docId
    }

    fun discardSession() {
        val id = _state.value.sessionId
        draftStore.clearSession(id)
        _state.value = ScanSessionState(
            sessionId = UUID.randomUUID().toString(),
            slots = listOf(PageSlot.Empty(0)),
            selectedIndex = 0
        )
    }
}
