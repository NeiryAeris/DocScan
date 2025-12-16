package com.example.domain.types.text

object TextScore {
    private val viet = Regex("[À-Ỹà-ỹĐđ]")
    fun score(s: String): Double {
        if (s.isBlank()) return 0.0
        val len = s.count { !it.isWhitespace() }
        val diac = viet.findAll(s).count()
        return diac * 2.0 + len * 0.01 // weight diacritics strongly, length mildly
    }
}
