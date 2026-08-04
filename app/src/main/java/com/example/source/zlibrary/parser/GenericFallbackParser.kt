package com.example.source.zlibrary.parser

import android.util.Log
import com.example.source.SearchBook
import com.example.source.zlibrary.ParsedBookDetail
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Layout-agnostic fallback: extracts books from any page that links to /book/... detail pages.
 * Covers redesigns that drop the legacy .resItemBox / table.zbook markup.
 */
class GenericFallbackParser : ZLibraryLayoutParser {

    override val name: String = "GenericFallback"

    companion object {
        private const val TAG = "ZLibCover"
    }

    override fun canParseSearch(doc: Document): Boolean {
        return doc.selectFirst("z-bookcard[href], a[href*=/book/]") != null
    }

    override fun parseSearch(doc: Document, baseUrl: String, sourceId: String): List<SearchBook> {
        val results = LinkedHashMap<String, SearchBook>()
        Log.d(TAG, "page imgs=${doc.select("img").size} bookcards=${doc.select("z-bookcard[href]").size} " +
            "bgStyle=${doc.select("[style*=background-image]").size} base=$baseUrl")

        // New Z-Library layout: custom <z-bookcard> elements carry href/download/extension
        // as attributes and title/author in slot divs.
        for (card in doc.select("z-bookcard[href]")) {
            val href = card.attr("href").trim()
            if (href.isBlank()) continue
            val id = href.trimStart('/')
            if (!id.startsWith("book/")) continue
            val title = card.selectFirst("[slot=title], h3, h4, [class*=title]")?.text()?.trim() ?: ""
            if (title.isBlank()) continue

            val author = card.select("[slot=author], a[href*=/g/], [class*=author]")
                .firstOrNull()?.text()?.trim() ?: ""
            val dl = card.attr("download").ifBlank {
                card.selectFirst("a[href*=/dl/]")?.attr("href") ?: ""
            }
            val cover = extractCover(card)
            val format = extractFormat(card, dl)
            if (cover.isBlank()) {
                Log.d(TAG, "NO_COVER [$title] href=$href card=${card.outerHtml().take(1500)}")
            } else {
                Log.d(TAG, "COVER [$title] $cover")
            }

            results[id] = SearchBook(
                id = id,
                sourceId = sourceId,
                title = title,
                author = author,
                cover = absUrl(baseUrl, cover).ifBlank { null },
                format = format.ifBlank { "epub" },
                downloadUrl = absUrl(baseUrl, dl).ifBlank { null }
            )
        }

        // Legacy layouts: plain <a href="/book/..."> links
        for (link in doc.select("a[href*=/book/]")) {
            val href = link.attr("href").trim()
            if (href.isBlank()) continue
            val id = href.trimStart('/')
            if (!id.startsWith("book/")) continue
            if (results.containsKey(id)) continue

            val container = bestContainer(link) ?: link.parent() ?: continue
            val title = link.text().trim()
                .ifBlank { container.selectFirst("h3, h4, [class*=title]")?.text()?.trim() ?: "" }
            if (title.isBlank()) continue

            val author = container.select(
                "a[href*=/g/], [itemprop=author], .authors a, [class*=author]"
            ).firstOrNull()?.text()?.trim() ?: ""

            val downloadUrl = container.selectFirst("a[href*=/dl/]")?.attr("href")?.let { absUrl(baseUrl, it) } ?: ""
            val cover = extractCover(container)
            val format = extractFormat(container, downloadUrl)

            results[id] = SearchBook(
                id = id,
                sourceId = sourceId,
                title = title,
                author = author,
                cover = cover.ifBlank { null },
                format = format.ifBlank { "epub" },
                downloadUrl = downloadUrl.ifBlank { null }
            )
        }
        return results.values.toList()
    }

    /** Smallest ancestor that contains exactly one book link and a bounded amount of text. */
    private fun bestContainer(link: Element): Element? {
        var best: Element? = null
        for (p in link.parents()) {
            if (p.tagName() == "html" || p.tagName() == "body") break
            val bookLinks = p.select("a[href*=/book/]").size
            val textLen = p.text().length
            if (bookLinks == 1 && textLen in 1..600) {
                best = p
            }
            if (textLen > 600) break
        }
        return best
    }

    private fun absUrl(baseUrl: String, href: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        if (href.startsWith("//")) return "https:$href"
        return baseUrl.trimEnd('/') + "/" + href.trimStart('/')
    }

    private fun extractCover(root: Element): String {
        return CoverExtractor.extract(root)
    }

    private fun extractFormat(root: Element, downloadUrl: String): String {
        val fromAttr = root.attr("extension").ifBlank { root.attr("data-extension") }
            .ifBlank { root.attr("format") }
        if (fromAttr.isNotBlank()) return fromAttr.lowercase()
        val fromText = root.select("[class*=extension], [class*=format], [class*=file-type]")
            .firstOrNull()?.text()?.trim()?.lowercase()
        if (!fromText.isNullOrBlank()) return fromText
        return inferFormatFromUrl(downloadUrl)
    }

    private fun inferFormatFromUrl(url: String): String {
        if (url.isBlank()) return ""
        val clean = url.substringBefore('?').substringBefore('#')
        val ext = clean.substringAfterLast('.', "").lowercase()
        return if (ext.length in 2..5 &&
            ext.all { it.isLetterOrDigit() } &&
            ext != "html" && ext != "php"
        ) ext else ""
    }

    override fun canParseDetail(doc: Document): Boolean {
        return doc.selectFirst("h1, [itemprop=name]") != null
    }

    override fun parseDetail(doc: Document, baseUrl: String): ParsedBookDetail {
        val title = doc.selectFirst("h1, [itemprop=name]")?.text()?.trim() ?: ""
        val author = doc.select("a[itemprop=author], [class*=author] a")
            .joinToString(", ") { it.text().trim() }
        val cover = doc.selectFirst("img[src]")?.attr("src")?.let { absUrl(baseUrl, it) } ?: ""
        val format = doc.select("[class*=extension], [class*=format], [class*=file-type]")
            .firstOrNull()?.text()?.trim()?.lowercase() ?: ""
        val downloadUrl = doc.selectFirst("a[href*=/dl/], a.add_to_download_history, a.dlButton")
            ?.attr("href")?.let { absUrl(baseUrl, it) } ?: ""
        return ParsedBookDetail(
            title = title,
            author = author,
            cover = cover,
            format = format,
            downloadUrl = downloadUrl
        )
    }
}
