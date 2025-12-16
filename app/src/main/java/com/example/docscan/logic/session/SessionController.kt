package com.example.docscan.logic.scan

import android.content.Context
import com.example.imaging_opencv_android.OpenCvImaging
import com.example.pipeline_core.scan.CamScanPipeline
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class SessionController(
    private val context: Context,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val draftStore = DraftStore(context)

    private val _state = MutableStateFlow(
        ScanSessionState(
            sessionId = newSessionId(),
            slots = listOf(PageSlot.Empty(index = 0)),
            selectedIndex = 0,
            isDirty = false
        )
    )
    val state: StateFlow<ScanSessionState> = _state

    fun startNewSession() {
        val id = newSessionId()
        _state.value = ScanSessionState(
            sessionId = id,
            slots = listOf(PageSlot.Empty(0)),
            selectedIndex = 0,
            isDirty = false
        )
    }

    fun addEmptySlot() {
        _state.update { s ->
            val nextIndex = (s.slots.maxOfOrNull { it.index } ?: -1) + 1
            s.copy(
                slots = s.slots + PageSlot.Empty(nextIndex),
                selectedIndex = nextIndex
            )
        }
    }

    fun selectSlot(index: Int) {
        _state.update { it.copy(selectedIndex = index.coerceIn(0, it.slots.lastIndex)) }
    }

    /**
     * Main “capture -> process -> put into slot” entry.
     * Pass the bytes you got from camera file or gallery.
     */
    suspend fun processIntoSlot(index: Int, cameraJpeg: ByteArray) {
        val s0 = _state.value
        val sessionId = s0.sessionId

        // mark slot processing
        _state.update { s ->
            s.copy(
                slots = s.slots.map { if (it.index == index) PageSlot.Processing(index) else it },
                lastError = null
            )
        }

        try {
            // safest for future parallelism: new instance per call
            val imaging = OpenCvImaging()
            val pipeline = CamScanPipeline(imaging)

            val result = kotlinx.coroutines.withContext(workDispatcher) {
                pipeline.processJpeg(
                    cameraJpeg = cameraJpeg,
                    options = CamScanPipeline.Options(
                        enhanceMode = "auto_pro",
                        jpegQuality = 85,
                        includeOverlay = false
                    )
                )
            }

            val outFile = draftStore.writeProcessedJpeg(sessionId, index, result.outJpeg)

            _state.update { s ->
                s.copy(
                    slots = s.slots.map {
                        if (it.index == index) {
                            PageSlot.Ready(
                                index = index,
                                processedJpegPath = outFile.absolutePath,
                                quad = result.quad,
                                paperName = result.paperName
                            )
                        } else it
                    },
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

    fun discardSession() {
        val id = _state.value.sessionId
        draftStore.clearSession(id)
        startNewSession()
    }

    private fun newSessionId(): String = UUID.randomUUID().toString()
}
