package com.example.source.zlibrary.parser

import com.example.source.SearchBook
import com.example.source.zlibrary.ParsedBookDetail
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

interface ZLibraryLayoutParser {
    val name: String
    fun canParseSearch(doc: Document): Boolean
    fun parseSearch(doc: Document, baseUrl: String, sourceId: String): List<SearchBook>
    fun canParseDetail(doc: Document): Boolean
    fun parseDetail(doc: Document, baseUrl: String): ParsedBookDetail
}

/**
 * 优先取真实下载按钮链接，跳过 <template> 里 [hidden] 的死链。
 * 当前站点详情页第一个 /dl/ 链接是隐藏模板占位，直接取会拿到 204 空文件。
 */
internal fun Document.firstRealDownloadLink(): Element? =
    selectFirst("a.dlButton[href*=/dl/]:not([hidden])")
        ?: selectFirst("a[href*=/dl/]:not([hidden])")
        ?: selectFirst("a.add_to_download_history:not([hidden])")
