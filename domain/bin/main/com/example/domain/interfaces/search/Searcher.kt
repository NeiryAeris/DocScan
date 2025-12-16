package com.example.domain.interfaces.search

import com.example.domain.types.SearchHit

interface Searcher {
    fun search(query: String, limit: Int = 20): List<SearchHit>
}