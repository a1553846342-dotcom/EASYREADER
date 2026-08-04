package com.example.source.zlibrary.parser

import com.example.source.SearchBook
import com.example.source.zlibrary.ParsedBookDetail
import org.jsoup.nodes.Document

interface ZLibraryLayoutParser {
    val name: String
    fun canParseSearch(doc: Document): Boolean
    fun parseSearch(doc: Document, baseUrl: String, sourceId: String): List<SearchBook>
    fun canParseDetail(doc: Document): Boolean
    fun parseDetail(doc: Document, baseUrl: String): ParsedBookDetail
}
