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
 * 从下载链接的路径/查询参数里尽力推断真实文件格式。
 * 例：`/dl/123/abc.mobi` → mobi，`...?format=azw3` → azw3。
 * 解析不到返回 null，调用方再回退到页面解析出的格式。
 */
internal fun guessFileFormatFromUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val lower = url.lowercase()
    val path = lower.substringBefore('?').substringBefore('#')
    val known = listOf(
        "epub", "mobi", "azw3", "azw", "prc", "txt", "pdf", "fb2", "lit",
        "doc", "docx", "rtf", "cbz", "cbr"
    )
    known.firstOrNull { path.endsWith(".$it") }?.let { return it }

    val query = lower.substringAfter('?', "")
    Regex("(?:^|[?&])format=([a-z0-9]+)").find(query)?.groupValues?.get(1)?.let {
        if (it in known) return it
    }
    return null
}

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
