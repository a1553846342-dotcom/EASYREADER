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
        /** 第九轮：官方 API（title 搜索覆盖 altTitles 中文别名；镜像站搜索只匹配主标题） */
        private const val OFFICIAL_API = "https://api.mangadex.org"
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
                // 第九轮补强：镜像站（mangadex.live）的站内搜索只匹配罗马音/英文主标题
                // ——实测 航海王/海贼王/无职転生 均为 0 结果，而官方 API 的 title 搜索
                // 覆盖 altTitles（含中文别名，航海王→One Piece 命中）。两条路径合并：
                // 镜像结果直接可用（章节/图链路成熟），官方 API 结果带 mdapi: 前缀 id，
                // 详情/章节/图走官方 API 专用实现。
                val encoded = URLEncoder.encode(keyword, "UTF-8")
                val mirror = searchMirror(encoded)
                val official = searchOfficialApi(keyword)
                // 合并：镜像优先；官方结果去重（按规范化标题，避免同书双条目）
                val merged = LinkedHashMap<String, SearchBook>()
                mirror.forEach { merged[it.id] = it }
                official.forEach { api ->
                    if (merged.values.none {
                            it.title.equals(api.title, ignoreCase = true) ||
                                normalizeTitle(it.title) == normalizeTitle(api.title)
                        }) {
                        merged[api.id] = api
                    }
                }
                val books = merged.values.toList()
                Log.i(TAG, "search '$keyword' -> mirror ${mirror.size} + official ${official.size} = ${books.size} books")
                SourceResult.Success(books)
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

    private fun normalizeTitle(t: String): String =
        t.lowercase().replace(Regex("[\\s＆&！!？?，,。·・:：\\-—～~]"), "")

    /** 镜像站 HTML 搜索（原实现抽出，行为不变） */
    private fun searchMirror(encoded: String): List<SearchBook> {
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
        return books.distinctBy { it.id }
    }

    /**
     * 官方 API 搜索（api.mangadex.org）：title 参数覆盖 altTitles，
     * 中文别名（航海王/海贼王/无职转生…）可直接命中。任何失败静默返回空——
     * 官方 API 在部分网络不可达（注释见类头），此时镜像路径独立支撑搜索。
     * 结果 id 带 "mdapi:" 前缀，getDetail/getChapters/getChapterImages 据此分派。
     */
    private fun searchOfficialApi(keyword: String): List<SearchBook> = runCatching {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "$OFFICIAL_API/manga?title=$encoded&limit=12" +
            "&contentRating%5B%5D=safe&contentRating%5B%5D=suggestive" +
            "&includes%5B%5D=cover_art&order%5Brelevance%5D=desc"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.i(TAG, "official api search HTTP ${response.code}")
                return emptyList()
            }
            val body = response.body?.string() ?: return emptyList()
            val root = JSONObject(body)
            val data = root.optJSONArray("data") ?: return emptyList()
            val out = ArrayList<SearchBook>(data.length())
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val uuid = item.optString("id")
                if (uuid.isBlank()) continue
                val attrs = item.optJSONObject("attributes") ?: continue
                val titleObj = attrs.optJSONObject("title")
                val mainTitle = titleObj?.let { t ->
                    listOf("en", "ja-ro", "ja", "zh-hans", "zh").firstNotNullOfOrNull { k ->
                        t.optString(k, "").takeIf { it.isNotBlank() }
                    }
                } ?: ""
                // altTitles 里取最优展示标题（优先中文，其次英文，再取第一个）
                val altList = attrs.optJSONArray("altTitles")
                var zhAlt: String? = null
                var firstAlt: String? = null
                if (altList != null) {
                    for (k in 0 until altList.length()) {
                        val alt = altList.optJSONObject(k) ?: continue
                        val zh = alt.optString("zh-hans", "").ifBlank { alt.optString("zh", "") }
                        if (zh.isNotBlank() && zhAlt == null) zhAlt = zh
                        if (firstAlt == null) {
                            for (keyIdx in 0 until alt.names().length()) {
                                val keyName = alt.names().getString(keyIdx)
                                val v = alt.optString(keyName, "").takeIf { it.isNotBlank() }
                                if (v != null) {
                                    firstAlt = v
                                    break
                                }
                            }
                        }
                    }
                }
                val displayTitle = listOf(zhAlt, mainTitle, firstAlt)
                    .firstOrNull { !it.isNullOrBlank() } ?: "未知书名"
                // cover：includes[] 里的 cover_art 关系 → 文件名拼官方 CDN URL
                var cover: String? = null
                val rels = item.optJSONArray("relationships")
                if (rels != null) {
                    for (k in 0 until rels.length()) {
                        val rel = rels.optJSONObject(k) ?: continue
                        if (rel.optString("type") == "cover_art") {
                            val fileName = rel.optJSONObject("attributes")?.optString("fileName")
                            if (!fileName.isNullOrBlank()) {
                                cover = "https://uploads.mangadex.org/covers/$uuid/$fileName.512.jpg"
                            }
                            break
                        }
                    }
                }
                val originalLang = attrs.optString("originalLanguage", "")
                // 记录 uuid→主标题（罗马音/英文），供官方章节无图时回退镜像搜索
                if (mainTitle.isNotBlank()) {
                    synchronized(officialTitleCache) {
                        officialTitleCache[uuid] = mainTitle
                        if (officialTitleCache.size > 64) {
                            officialTitleCache.remove(officialTitleCache.keys.first())
                        }
                    }
                }
                out.add(
                    SearchBook(
                        id = "mdapi:$uuid",
                        sourceId = id,
                        title = displayTitle,
                        author = "MangaDex",
                        cover = cover,
                        format = "漫画",
                        language = languageLabel(originalLang)
                    )
                )
            }
            out
        }
    }.getOrDefault(emptyList())

    /** id 是否为官方 API 条目（"mdapi:<uuid>"） */
    private fun officialUuid(bookId: String): String? =
        if (bookId.startsWith("mdapi:")) bookId.removePrefix("mdapi:") else null

    /** 官方条目 uuid → 罗马音/英文主标题（章节无图时按标题回退镜像搜索的桥接表）。
     *  进程内会话缓存，容量极小（近次搜索结果量级）。 */
    private val officialTitleCache = LinkedHashMap<String, String>()

    private fun apiGet(url: String): JSONObject? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            response.body?.string()?.let { JSONObject(it) }
        }
    }.getOrNull()

    /** 官方 API：按 uuid 找最新章节 id，再走 at-home API 取图。
     *  注意：translatedLanguage 只接受 ^[a-z]{2}(-[a-z]{2})?$（zh-hans 非法→400），
     *  用 zh / zh-hk / en。 */
    private fun officialChapterImages(uuid: String): List<String> {
        val feed = apiGet(
            "$OFFICIAL_API/manga/$uuid/feed?translatedLanguage%5B%5D=zh" +
                "&translatedLanguage%5B%5D=zh-hk" +
                "&translatedLanguage%5B%5D=en" +
                "&order%5Bchapter%5D=asc&limit=1&contentRating%5B%5D=safe" +
                "&contentRating%5B%5D=suggestive&contentRating%5B%5D=erotica"
        ) ?: return emptyList()
        val data = feed.optJSONArray("data") ?: return emptyList()
        val chapterId = data.optJSONObject(0)?.optString("id") ?: return emptyList()
        if (chapterId.isBlank()) return emptyList()
        val atHome = apiGet("$OFFICIAL_API/at-home/server/$chapterId") ?: return emptyList()
        val baseUrl = atHome.optString("baseUrl")
        val chapter = atHome.optJSONObject("chapter") ?: return emptyList()
        val hash = chapter.optString("hash")
        val pages = chapter.optJSONArray("data") ?: return emptyList()
        if (baseUrl.isBlank() || hash.isBlank()) return emptyList()
        val out = ArrayList<String>(pages.length())
        for (i in 0 until pages.length()) {
            val p = pages.optString(i, "")
            if (p.isNotBlank()) out.add("$baseUrl/data/$hash/$p")
        }
        return out
    }

    /**
     * 第九轮回退：官方章节无托管图（externalLink 除外链）时，
     * 用 uuid→主标题桥接表在镜像站搜同作品，再在镜像章节列表里找同号章节，
     * 最后走镜像 read 页拿直链图。任何一步失败都静默返回空。
     */
    private fun mirrorFallbackImages(uuid: String, chapterNum: Float): List<String> = runCatching {
        val mainTitle = synchronized(officialTitleCache) { officialTitleCache[uuid] } ?: return emptyList()
        val slug = searchMirror(URLEncoder.encode(mainTitle, "UTF-8"))
            .firstOrNull { it.id.isNotBlank() && !it.id.startsWith("mdapi:") }
            ?.id
            ?: return emptyList()
        // 拉镜像章节列表，找同号
        val doc = Jsoup.parse(getHtml("$BASE/manga/$slug"))
        val blocks = doc.select("ul.chapter-list-item")
        var readPath: String? = null
        val preferred = listOf("ZH", "ZH-HK", "ZH-CN", "EN")
        for (block in blocks.sortedBy { b ->
            val idx = preferred.indexOf(b.attr("data-code").uppercase())
            if (idx >= 0) idx else 10
        }) {
            val code = block.attr("data-code").uppercase()
            val lang = code.lowercase()
            val hit = block.select("li.item[data-number]").firstOrNull {
                it.attr("data-number").trim().toFloatOrNull() == chapterNum
            }?.selectFirst("a[href^=/read/]")?.attr("href")?.trim('/')
            if (!hit.isNullOrBlank()) {
                readPath = hit
                break
            }
            var offset = block.attr("data-offset").toIntOrNull() ?: 0
            var hasMore = block.attr("data-has-more") == "true"
            var guard = 0
            while (hasMore && guard < 40) {
                val batchDoc = Jsoup.parse(
                    getHtml("$BASE/manga/$slug/chapters?lang=$code&offset=$offset", xhr = true)
                )
                val meta = batchDoc.selectFirst("span.chapter-batch-meta")
                val hit2 = batchDoc.select("li.item[data-number]").firstOrNull {
                    it.attr("data-number").trim().toFloatOrNull() == chapterNum
                }?.selectFirst("a[href^=/read/]")?.attr("href")?.trim('/')
                if (!hit2.isNullOrBlank()) {
                    readPath = hit2
                    break
                }
                hasMore = meta?.attr("data-has-more") == "true"
                offset = meta?.attr("data-next-offset")?.toIntOrNull() ?: (offset + 1)
                guard++
            }
            if (readPath != null) break
        }
        if (readPath.isNullOrBlank()) return emptyList()
        val pageDoc = Jsoup.parse(getHtml("$BASE/$readPath", referer = true))
        pageDoc.select("img[src]").mapNotNull { img ->
            val src = img.attr("src").trim()
            if (src.startsWith("http") && src.contains("/chapter/")) src else null
        }
    }.getOrDefault(emptyList())

    override suspend fun getDetail(bookId: String): SourceResult<SearchBook> =
        withContext(Dispatchers.IO) {
            try {
                // 第九轮：官方 API 条目（mdapi:<uuid>）走官方详情
                officialUuid(bookId)?.let { uuid ->
                    val json = apiGet(
                        "$OFFICIAL_API/manga/$uuid?includes%5B%5D=cover_art"
                    ) ?: return@let SourceResult.Error(
                        SourceException.NetworkError("MangaDex 官方 API 不可达")
                    )
                    val item = json.optJSONArray("data")?.optJSONObject(0)
                        ?: return@let SourceResult.Error(SourceException.ParseError("作品不存在"))
                    val attrs = item.optJSONObject("attributes")
                    val titleObj = attrs?.optJSONObject("title")
                    val title = titleObj?.let { t ->
                        listOf("en", "ja-ro", "ja", "zh-hans", "zh")
                            .firstNotNullOfOrNull { k -> t.optString(k, "").takeIf { it.isNotBlank() } }
                    } ?: uuid
                    var cover: String? = null
                    val rels = item.optJSONArray("relationships")
                    if (rels != null) {
                        for (k in 0 until rels.length()) {
                            val rel = rels.optJSONObject(k) ?: continue
                            if (rel.optString("type") == "cover_art") {
                                val fileName = rel.optJSONObject("attributes")?.optString("fileName")
                                if (!fileName.isNullOrBlank()) {
                                    cover = "https://uploads.mangadex.org/covers/$uuid/$fileName.512.jpg"
                                }
                                break
                            }
                        }
                    }
                    return@withContext SourceResult.Success(
                        SearchBook(
                            id = bookId,
                            sourceId = id,
                            title = title,
                            author = "MangaDex",
                            cover = cover,
                            format = "漫画"
                        )
                    )
                }
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
                // 第九轮：官方 API 条目——章节列表按官方 feed 拉取（中→英优先）
                val officialId = officialUuid(bookId)
                if (officialId != null) {
                    val feed = apiGet(
                        "$OFFICIAL_API/manga/$officialId/feed?translatedLanguage%5B%5D=zh" +
                            "&translatedLanguage%5B%5D=zh-hk" +
                            "&translatedLanguage%5B%5D=en" +
                            "&order%5Bchapter%5D=asc&limit=500&contentRating%5B%5D=safe" +
                            "&contentRating%5B%5D=suggestive&contentRating%5B%5D=erotica"
                    ) ?: return@withContext SourceResult.Error(
                        SourceException.NetworkError("MangaDex 官方 API 不可达")
                    )
                    val data = feed.optJSONArray("data")
                    if (data == null || data.length() == 0) {
                        return@withContext SourceResult.Success(emptyList())
                    }
                    val chapters = ArrayList<ComicChapter>(data.length())
                    val seen = HashSet<Float>()
                    for (i in 0 until data.length()) {
                        val item = data.optJSONObject(i) ?: continue
                        val attrs = item.optJSONObject("attributes") ?: continue
                        val chapterId = item.optString("id")
                        val num = attrs.optDouble("chapter", Double.NaN)
                        if (chapterId.isBlank() || num.isNaN()) continue
                        val key = num.toFloat()
                        if (!seen.add(key)) continue
                        val label = if (key == key.toLong().toFloat()) {
                            "第${key.toLong()}话"
                        } else {
                            "第$key 话"
                        }
                        chapters.add(
                            ComicChapter(
                                id = "mdapich:$chapterId:$key",
                                title = attrs.optString("title", "").ifBlank { label },
                                order = key
                            )
                        )
                    }
                    chapters.sortBy { it.order }
                    Log.i(TAG, "official chapters for $officialId -> ${chapters.size}")
                    return@withContext SourceResult.Success(chapters)
                }
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
                // 第九轮：官方 API 章节（mdapich:<uuid>）——at-home 接口取图；
                // 无图（章节为 externalLink/无中文英文托管源）时回退镜像：
                // 按标题搜镜像拿 slug → 镜像章节列表找同号章节 → 镜像 read 页直链
                if (chapterId.startsWith("mdapich:")) {
                    val parts = chapterId.removePrefix("mdapich:").split(':')
                    val uuid = parts.getOrNull(0).orEmpty()
                    val chapterNum = parts.getOrNull(1)?.toFloatOrNull()
                    val images = officialChapterImages(uuid)
                    Log.i(TAG, "official chapter images $uuid -> ${images.size}")
                    if (images.isNotEmpty()) {
                        return@withContext SourceResult.Success(images)
                    }
                    // 回退镜像：uuid→标题桥接表 → 镜像搜索 → 同号章节
                    val fallback = if (chapterNum != null) mirrorFallbackImages(uuid, chapterNum) else emptyList()
                    Log.i(TAG, "mirror fallback for $uuid ch$chapterNum -> ${fallback.size}")
                    return@withContext if (fallback.isEmpty()) {
                        SourceResult.Error(
                            SourceException.ParseError("该章节在官方源为外链/无托管图，镜像站也未找到同章节")
                        )
                    } else {
                        SourceResult.Success(fallback)
                    }
                }
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
