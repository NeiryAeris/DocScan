package com.example.ocr.core.util

object OcrSanitizer {
    fun sanitize(s: String): String = s
        .replace('º','o').replace('°','o')
        .replace('—','-').replace('–','-').replace('−','-')
        .replace(Regex("[\\u00A0\\u2007\\u202F]"), " ") // nbsp/thin spaces
        .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
        .trim()
}
