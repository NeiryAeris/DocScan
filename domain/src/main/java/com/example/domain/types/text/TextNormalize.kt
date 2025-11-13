package com.example.domain.types.text

import java.text.Normalizer

object TextNormalize {

    /** Clean up common OCR artifacts + unify punctuation/spaces. */
    fun sanitize(s: String): String = s
        .replace('º','o').replace('°','o').replace('ª','a')
        .replace('—','-').replace('–','-').replace('−','-')
        .replace('“','"').replace('”','"').replace('„','"').replace('‟','"')
        .replace('‘','\'').replace('’','\'').replace('‚','\'').replace('‛','\'')
        .replace("…","...")
        .replace(Regex("[\\u00A0\\u2007\\u202F]"), " ")
        .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
        .trim()

    /** Accent-folded version for diacritic-insensitive search (store separately). */
    fun fold(s: String): String {
        val nfd = Normalizer.normalize(s, Normalizer.Form.NFD)
        return nfd.replace("\\p{M}+".toRegex(), "")
            .replace('đ','d').replace('Đ','D')
    }

    /** Placeholder for future dictionary/statistical diacritic restoration. */
    fun restoreDiacriticsIfNeeded(s: String): String = s
}