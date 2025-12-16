package com.example.domain.types.ocr

/**
 * Canonical text bundle persisted per page and used for search/export.
 */
data class OcrText(
    val raw: String,     // engine output as-is (trimmed)
    val clean: String,   // sanitized punctuation/spaces & common OCR artifacts fixed
    val folded: String   // diacritic-folded for accent-insensitive search
)
