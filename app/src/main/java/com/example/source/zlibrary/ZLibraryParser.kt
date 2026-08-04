package com.example.source.zlibrary

import com.example.source.SearchBook
import com.example.source.zlibrary.parser.ZLibraryParserManager

object ZLibraryParser {

    fun parseSearchPage(html: String, baseUrl: String, sourceId: String = "zlibrary"): List<SearchBook> {
        return ZLibraryParserManager.parseSearchPage(html, baseUrl, sourceId)
    }

    fun parseDetailPage(html: String, baseUrl: String): ParsedBookDetail {
        return ZLibraryParserManager.parseDetailPage(html, baseUrl)
    }
}

data class ParsedBookDetail(
    val title: String,
    val author: String,
    val cover: String?,
    val downloadUrl: String,
    val format: String
)

