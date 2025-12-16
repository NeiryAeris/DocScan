package com.example.domain.types.text

import java.text.Normalizer

// :domain/text/TextNormalize.kt
object TextNormalize {

    fun sanitize(s: String): String {
        val unified = s
            .replace('—','-').replace('–','-').replace('−','-')
            .replace('“','"').replace('”','"').replace('„','"').replace('‟','"')
            .replace('‘','\'').replace('’','\'').replace('‚','\'').replace('‛','\'')
            .replace("…","...")
            .replace(Regex("[\\u00A0\\u2007\\u202F]"), " ")
            .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
            .trim()

        // Context-aware fix for º/° only when they look like misread 'o'
        // Case 1: letter º letter  => likely 'o'
        val fix1 = Regex("(?i)(?<=[A-ZÀ-Ỹa-zà-ỹĐđ])(?:º|°)(?=[A-ZÀ-Ỹa-zà-ỹĐđ])")
        // Case 2: letter º space   => likely 'o' at word end
        val fix2 = Regex("(?i)(?<=[A-ZÀ-Ỹa-zà-ỹĐđ])(?:º|°)(?=\\b)")
        // We do NOT touch when preceded by a digit: 10ºC, 1º → keep as real superscript/degree
        return unified.replace(fix1, "o").replace(fix2, "o")
    }

    fun fold(s: String): String {
        val nfd = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        return nfd.replace("\\p{M}+".toRegex(), "")
            .replace('đ','d').replace('Đ','D')
    }
}
