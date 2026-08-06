package com.example.source.impl

import android.util.Log
import com.example.source.AuthenticationState
import com.example.source.ComicChapter
import com.example.source.ComicSource
import com.example.source.DownloadInfo
import com.example.source.LoginCredential
import com.example.source.SearchBook
import com.example.source.SourceCapabilities
import com.example.source.SourceException
import com.example.source.SourceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * MangaDex comic source backed by the mangadex.live mirror.
 *
 * The official MangaDex API is blocked on many mainland networks and many
 * chapters are "external" without hosted images. mangadex.live mirrors the
 * full catalog with direct CDN image URLs (t.imoutcl.sbs), which is reachable
 * from the phone, and every chapter has readable pages - including chapters
 * that are external on the official API.
 */
class MangaDexSource(
    private val client: OkHttpClient = defaultClient
) : ComicSource {

    override val id: String = "mangadex"
    override val name: String = "MangaDex 漫画"
    override val capabilities: SourceCapabilities = SourceCapabilities(
        supportSearch = true,
        supportDownload = false,
        supportComic = true,
        environmentOnly = false
    )

    companion object {
        private const val TAG = "MangaDex"
        private const val BASE = "https://mangadex.live"
        private const val REFERER = "https://mangadex.live/"
        private const val UA =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun search(keyword: String): SourceResult<List<SearchBook>> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(keyword, "UTF-8")
                val doc = Jsoup.parse(getHtml("$BASE/search?q=$encoded"))
                val books = mutableListOf<SearchBook>()
                for (unit in doc.select("div.unit")) {
                    val link = unit.selectFirst("a[href^=/manga/]") ?: continue
                    val slug = link.attr("href").trim('/').removePrefix("manga/")
                    if (slug.isBlank()) continue
                    val title = link.attr("title").ifBlank { link.text() }.trim()
                    val img = unit.selectFirst("img[src]") ?: unit.selectFirst("img[data-src]")
                    val cover = img?.let {
                        (it.attr("src").ifBlank { it.attr("data-src") }).trim()
                    }.orEmpty()
                    val langCode = unit.selectFirst(".content li a[href^=/read/]")?.attr("href")
                        ?.trim('/')?.split('/')?.getOrNull(2)
                    books.add(
                        SearchBook(
                            id = slug,
                            sourceId = id,
                            title = title.ifBlank { "未知书名" },
                            author = "MangaDex",
                            cover = cover.ifBlank { null }?.replace(".256.jpg", ".512.jpg"),
                            format = "漫画",
                            language = langCode?.let { languageLabel(it) }
                        )
                    )
                }
                Log.i(TAG, "search '$keyword' -> ${books.size} books")
                SourceResult.Success(books.distinctBy { it.id })
            } catch (e: SourceException) {
                Log.e(TAG, "search failed: ${e.message}", e)
                SourceResult.Error(e)
            } catch (e: IOException) {
                Log.e(TAG, "search io error: ${e.message}", e)
                SourceResult.Error(SourceException.NetworkError("网络连接错误: ${e.message}", e))
            } catch (e: Exception) {
                Log.e(TAG, "search unexpected: ${e.message}", e)
                SourceResult.Error(SourceException.Unknown("未知错误: ${e.message}", e))
            }
        }

    override suspend fun getDetail(bookId: String): SourceResult<SearchBook> =
        withContext(Dispatchers.IO) {
            try {
                val doc = Jsoup.parse(getHtml("$BASE/manga/$bookId"))
                val title = doc.selectFirst("h1")?.text()?.trim()
                    ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
                    ?: bookId
                val cover = doc.selectFirst("meta[property=og:image]")?.attr("content")?.trim()
                    ?: doc.selectFirst("img[src*=cover]")?.attr("src")?.trim()
                val author = parseJsonLdAuthor(doc)
                SourceResult.Success(
                    SearchBook(
                        id = bookId,
                        sourceId = id,
                        title = title,
                        author = author,
                        cover = cover?.ifBlank { null }?.replace(".256.jpg", ".512.jpg"),
                        format = "漫画"
                    )
                )
            } catch (e: SourceException) {
                Log.e(TAG, "detail failed: ${e.message}", e)
                SourceResult.Error(e)
            } catch (e: IOException) {
                Log.e(TAG, "detail io error: ${e.message}", e)
                SourceResult.Error(SourceException.NetworkError("网络连接错误: ${e.message}", e))
            } catch (e: Exception) {
                Log.e(TAG, "detail unexpected: ${e.message}", e)
                SourceResult.Error(SourceException.Unknown("未知错误: ${e.message}", e))
            }
        }

    override suspend fun getChapters(bookId: String): SourceResult<List<ComicChapter>> =
        withContext(Dispatchers.IO) {
            try {
                val doc = Jsoup.parse(getHtml("$BASE/manga/$bookId"))
                val blocks = doc.select("ul.chapter-list-item")
                if (blocks.isEmpty()) {
                    return@withContext SourceResult.Success(emptyList())
                }

                // 语言优先级：中 > 英 > 其余按章节数从多到少
                val preferred = listOf("ZH", "ZH-HK", "ZH-CN", "EN")
                val orderedBlocks = blocks.sortedBy { block ->
                    val idx = preferred.indexOf(block.attr("data-code").uppercase())
                    if (idx >= 0) idx else 10
                }

                val byNumber = LinkedHashMap<Int, ComicChapter>()
                var batchRequests = 0

                for (block in orderedBlocks) {
                    val code = block.attr("data-code").uppercase()
                    val lang = code.lowercase()
                    collectItems(block, bookId, code, byNumber)

                    var offset = block.attr("data-offset").toIntOrNull() ?: 0
                    var hasMore = block.attr("data-has-more") == "true"
                    var guard = 0
                    while (hasMore && guard < 60 && batchRequests < 80) {
                        val batchHtml = getHtml(
                            "$BASE/manga/$bookId/chapters?lang=$code&offset=$offset",
                            xhr = true
                        )
                        val batchDoc = Jsoup.parse(batchHtml)
                        val meta = batchDoc.selectFirst("span.chapter-batch-meta")
                        hasMore = meta?.attr("data-has-more") == "true"
                        offset = meta?.attr("data-next-offset")?.toIntOrNull() ?: (offset + 1)
                        collectItems(batchDoc, bookId, code, byNumber)
                        guard++
                        batchRequests++
                        if (lang.isBlank()) break
                    }
                }

                val chapters = byNumber.values.sortedBy { it.order }
                Log.i(TAG, "chapters for $bookId -> ${chapters.size}")
                SourceResult.Success(chapters)
            } catch (e: SourceException) {
                Log.e(TAG, "chapters failed: ${e.message}", e)
                SourceResult.Error(e)
            } catch (e: IOException) {
                Log.e(TAG, "chapters io error: ${e.message}", e)
                SourceResult.Error(SourceException.NetworkError("网络连接错误: ${e.message}", e))
            } catch (e: Exception) {
                Log.e(TAG, "chapters unexpected: ${e.message}", e)
                SourceResult.Error(SourceException.Unknown("未知错误: ${e.message}", e))
            }
        }

    override suspend fun getChapterImages(chapterId: String): SourceResult<List<String>> =
        withContext(Dispatchers.IO) {
            try {
                val path = chapterId.trim('/')
                if (!path.startsWith("read/")) {
                    return@withContext SourceResult.Error(SourceException.ParseError("章节标识无效"))
                }
                val doc = Jsoup.parse(getHtml("$BASE/$path", referer = true))
                val images = doc.select("img[src]").mapNotNull { img ->
                    val src = img.attr("src").trim()
                    if (src.startsWith("http") && src.contains("/chapter/")) src else null
                }
                Log.i(TAG, "chapter images $chapterId -> ${images.size}")
                if (images.isEmpty()) {
                    SourceResult.Error(SourceException.ParseError("该章节没有可读取的图片"))
                } else {
                    SourceResult.Success(images)
                }
            } catch (e: SourceException) {
                Log.e(TAG, "chapter images failed: ${e.message}", e)
                SourceResult.Error(e)
            } catch (e: IOException) {
                Log.e(TAG, "chapter images io error: ${e.message}", e)
                SourceResult.Error(SourceException.NetworkError("网络连接错误: ${e.message}", e))
            } catch (e: Exception) {
                Log.e(TAG, "chapter images unexpected: ${e.message}", e)
                SourceResult.Error(SourceException.Unknown("未知错误: ${e.message}", e))
            }
        }

    override suspend fun getDownloadInfo(bookId: String): SourceResult<DownloadInfo> =
        SourceResult.Error(SourceException.ParseError("漫画源按章节在线阅读/下载，不支持单文件下载"))

    override suspend fun login(credential: LoginCredential): SourceResult<Boolean> =
        SourceResult.Success(false)

    override suspend fun logout() {}

    override suspend fun isLoggedIn(): Boolean = false

    override suspend fun getAuthenticationState(): AuthenticationState =
        AuthenticationState.NotRequired

    private fun collectItems(
        doc: Element,
        slug: String,
        code: String,
        out: LinkedHashMap<Int, ComicChapter>
    ) {
        val lang = code.lowercase()
        for (li in doc.select("li.item[data-number]")) {
            val numStr = li.attr("data-number").trim()
            val num = numStr.toIntOrNull() ?: continue
            if (out.containsKey(num)) continue
            val a = li.selectFirst("a[href^=/read/]") ?: continue
            val text = a.text().trim()
            val href = a.attr("href").trim('/')
            if (href.isBlank()) continue
            out[num] = ComicChapter(
                id = href,
                title = text.ifBlank { "第${numStr}话" },
                order = num.toFloat()
            )
        }
    }

    private fun parseJsonLdAuthor(doc: Document): String {
        for (script in doc.select("script[type=application/ld+json]")) {
            val text = script.html().trim()
            if (!text.contains("\"author\"")) continue
            try {
                val trimmed = text.trim()
                if (trimmed.startsWith("[")) {
                    val arr = org.json.JSONArray(trimmed)
                    for (i in 0 until arr.length()) {
                        val name = arr.optJSONObject(i)?.optJSONObject("author")?.optString("name")
                        if (!name.isNullOrBlank()) return name
                    }
                } else {
                    val json = JSONObject(trimmed)
                    val name = json.optJSONObject("author")?.optString("name")
                    if (!name.isNullOrBlank()) return name
                }
            } catch (_: Exception) {}
        }
        return "MangaDex"
    }

    private fun languageLabel(code: String): String = when (code.uppercase()) {
        "ZH", "ZH-HK", "ZH-CN", "ZH-TW" -> "中文"
        "EN" -> "英文"
        "JA" -> "日文"
        "KO" -> "韩文"
        "ES" -> "西班牙语"
        "PT-BR", "PT" -> "葡萄牙语"
        "FR" -> "法语"
        "DE" -> "德语"
        "RU" -> "俄语"
        "IT" -> "意大利语"
        "AR" -> "阿拉伯语"
        "CA" -> "加泰罗尼亚语"
        "TR" -> "土耳其语"
        "TH" -> "泰语"
        "VI" -> "越南语"
        "ID" -> "印尼语"
        else -> code.uppercase()
    }

    private fun getHtml(
        url: String,
        xhr: Boolean = false,
        referer: Boolean = false
    ): String {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        if (referer) builder.header("Referer", REFERER)
        if (xhr) builder.header("X-Requested-With", "XMLHttpRequest")
        val request = builder.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(TAG, "HTTP ${response.code} for ${url.take(140)}")
                throw SourceException.NetworkError("MangaDex HTTP ${response.code} @ ${url.take(160)}")
            }
            return response.body?.string() ?: ""
        }
    }
}
