package com.example.docscan.logic.scan

data class ScanSessionState(
    val sessionId: String,
    val slots: List<PageSlot>,
    val selectedIndex: Int = 0,
    val isDirty: Boolean = false,
    val lastError: String? = null
)

sealed class PageSlot {
    abstract val index: Int

    data class Empty(override val index: Int) : PageSlot()
    data class Processing(override val index: Int) : PageSlot()

    data class Ready(
        override val index: Int,
        val processedJpegPath: String,
        val quad: FloatArray,
        val paperName: String?
    ) : PageSlot()

    data class Failed(override val index: Int, val reason: String) : PageSlot()
}
