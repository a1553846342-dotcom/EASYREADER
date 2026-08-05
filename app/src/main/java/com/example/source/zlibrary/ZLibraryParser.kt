package com.example.source.zlibrary

import com.example.source.SearchBook
import com.example.source.zlibrary.parser.ZLibraryParserManager

/**
 * Unified book detail model produced by Z-Library layout parsers.
 */
data class ParsedBookDetail(
    val title: String,
    val author: String,
    val cover: String?,
    val format: String,
    val downloadUrl: String
)

/**
 * Backwards-compatible facade used by tests and legacy callers.
 * All real parsing now lives in [ZLibraryParserManager].
 */
object ZLibraryParser {
    fun parseSearchPage(html: String, baseUrl: String, sourceId: String = "zlibrary"): List<SearchBook> =
        ZLibraryParserManager.parseSearchPage(html, baseUrl, sourceId)

    fun parseDetailPage(html: String, baseUrl: String): ParsedBookDetail =
        ZLibraryParserManager.parseDetailPage(html, baseUrl)
}
