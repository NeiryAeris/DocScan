package com.example.domain.types.ocr

/**
 * How OCR should be executed for a given page.
 */
data class OcrPolicy(
    /** If null → auto-detect; otherwise explicit like "vie", "vie+eng", "vie+eng+chi_sim". */
    val lang: String? = null,
    /** Try to infer lang first (light heuristic or micro-OCR). */
    val autoDetect: Boolean = true,
    /** Prefer on-device engines first. */
    val preferOnDevice: Boolean = true,
    /** Allow calling cloud OCR if on-device result is too weak. */
    val allowCloudFallback: Boolean = false,
    /** Page segmentation mode hint: "auto", "single_block", "single_column". */
    val psmHint: String = "single_block",
    /** Target DPI hint for engines that use it (e.g., Tesseract). */
    val dpiHint: Int = 300
)
