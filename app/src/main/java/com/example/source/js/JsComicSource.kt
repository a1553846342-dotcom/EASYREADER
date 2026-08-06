package com.example.source.js

import android.content.Context
import android.util.Log
import com.example.source.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 一个 Venera 兼容的 JS 漫画源。
 * 通过 JsSourceEngine 调用源脚本的 search.load / comic.loadInfo / comic.loadEp。
 */
class JsComicSource(
    private val context: Context,
    val sourceKey: String,
    override val name: String,
    val version: String,
    private val script: String,
    private val insecureTls: Boolean = false,
    private val loginRequired: Boolean = false
) : ComicSource {

    override val id: String get() = "js_$sourceKey"
    override val capabilities: SourceCapabilities = SourceCapabilities(
        supportSearch = true,
        supportDownload = true,
        supportComic = true,
        searchRequiresLogin = loginRequired,
        downloadRequiresLogin = loginRequired
    )

    private val createMutex = Mutex()
    private var engine: JsSourceEngine? = null

    private suspend fun getEngine(): JsSourceEngine {
        engine?.let { return it }
        return createMutex.withLock {
            engine ?: JsSourceEngine(
                runtimeJs = VeneraRuntime.get(context),
                sourceJs = script,
                sourceKey = sourceKey,
                context = context,
                insecureTls = insecureTls
            ).also {
                it.call("null")
                engine = it
            }
        }
    }

    private suspend fun callJs(jsCall: String): JSONObject? {
        val raw = getEngine().call(jsCall) ?: return null
        return try {
            JSONObject(raw)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun search(keyword: String): SourceResult<List<SearchBook>> =
        withContext(Dispatchers.IO) {
            try {
                val obj = callJs("src.search.load.call(src, ${q(keyword)}, [], 1)")
                    ?: return@withContext SourceResult.Error(SourceException.ParseError("JS 源无响应"))
                if (!obj.optBoolean("ok")) {
                    return@withContext SourceResult.Error(SourceException.ParseError(obj.optString("error", "JS 执行失败")))
                }
                val comics = obj.optJSONObject("data")?.optJSONArray("comics") ?: JSONArray()
                val books = (0 until comics.length()).mapNotNull { i ->
                    val c = comics.optJSONObject(i) ?: return@mapNotNull null
                    val id = c.optString("id")
                    val title = c.optString("title")
                    if (id.isBlank() || title.isBlank()) return@mapNotNull null
                    SearchBook(
                        id = id,
                        sourceId = this@JsComicSource.id,
                        title = title,
                        author = c.optString("subTitle", "")
                            .ifBlank { c.optString("subtitle", "") }
                            .ifBlank { c.optString("author", "") }
                            .ifBlank { "未知作者" },
                        cover = c.optString("cover", "").ifBlank { null },
                        description = c.optString("description", "").ifBlank { null },
                        format = "漫画"
                    )
                }
                SourceResult.Success(books)
            } catch (e: Exception) {
                Log.e("JsComic[$sourceKey]", "search failed", e)
                SourceResult.Error(SourceException.Unknown("JS 搜索失败: ${e.message}", e))
            }
        }

    override suspend fun getChapters(bookId: String): SourceResult<List<ComicChapter>> =
        withContext(Dispatchers.IO) {
            try {
                val obj = callJs("src.comic.loadInfo.call(src, ${q(bookId)})")
                    ?: return@withContext SourceResult.Error(SourceException.ParseError("JS 源无响应"))
                if (!obj.optBoolean("ok")) {
                    return@withContext SourceResult.Error(SourceException.ParseError(obj.optString("error", "JS 执行失败")))
                }
                val data = obj.optJSONObject("data") ?: return@withContext SourceResult.Error(
                    SourceException.ParseError("JS 源无数据")
                )
                val chapters = data.optJSONArray("chapters") ?: JSONArray()
                val list = (0 until chapters.length()).mapNotNull { i ->
                    val c = chapters.optJSONObject(i) ?: return@mapNotNull null
                    val epId = c.optString("id")
                    val title = c.optString("title")
                    if (epId.isBlank() || title.isBlank()) return@mapNotNull null
                    ComicChapter(
                        id = "$bookId\u0001$epId",
                        title = title,
                        volume = c.optString("group", "").ifBlank { null },
                        order = c.optDouble("order", 0.0).toFloat()
                    )
                }
                // 与 Venera 一致：单图集/画廊类源（nhentai/hitomi/ehentai 等）
                // loadInfo 不返回 chapters，App 会合成一个“整本阅读”章节
                if (list.isEmpty()) {
                    SourceResult.Success(
                        listOf(
                            ComicChapter(
                                id = "$bookId\u0001",
                                title = data.optString("title").ifBlank { "开始阅读" },
                                order = 1f
                            )
                        )
                    )
                } else {
                    SourceResult.Success(list)
                }
            } catch (e: Exception) {
                Log.e("JsComic[$sourceKey]", "chapters failed", e)
                SourceResult.Error(SourceException.Unknown("JS 章节加载失败: ${e.message}", e))
            }
        }

    override suspend fun getChapterImages(chapterId: String): SourceResult<List<String>> =
        withContext(Dispatchers.IO) {
            val pair = splitChapterId(chapterId)
                ?: return@withContext SourceResult.Error(SourceException.ParseError("无效章节 ID"))
            try {
                val obj = callJs("src.comic.loadEp.call(src, ${q(pair.first)}, ${q(pair.second)})")
                    ?: return@withContext SourceResult.Error(SourceException.ParseError("JS 源无响应"))
                if (!obj.optBoolean("ok")) {
                    return@withContext SourceResult.Error(SourceException.ParseError(obj.optString("error", "JS 执行失败")))
                }
                val images = obj.optJSONObject("data")?.optJSONArray("images") ?: JSONArray()
                val urls = (0 until images.length()).mapNotNull { i ->
                    images.optString(i).ifBlank { null }
                }
                if (urls.isEmpty()) {
                    SourceResult.Error(SourceException.ParseError("该章节没有可读取的图片"))
                } else {
                    SourceResult.Success(urls)
                }
            } catch (e: Exception) {
                Log.e("JsComic[$sourceKey]", "images failed", e)
                SourceResult.Error(SourceException.Unknown("JS 图片加载失败: ${e.message}", e))
            }
        }

    override suspend fun getChapterImageHeaders(
        chapterId: String,
        urls: List<String>
    ): Map<String, Map<String, String>> {
        val pair = splitChapterId(chapterId) ?: return emptyMap()
        val result = HashMap<String, Map<String, String>>()
        urls.forEach { url ->
            try {
                val obj = callJs(
                    "(src.comic.onImageLoad ? src.comic.onImageLoad.call(src, ${q(url)}, ${q(pair.first)}, ${q(pair.second)}) : null)"
                ) ?: return@forEach
                if (!obj.optBoolean("ok")) return@forEach
                val data = obj.optJSONObject("data") ?: return@forEach
                val headers = data.optJSONObject("headers")
                val merged = LinkedHashMap<String, String>()
                headers?.keys()?.forEach { k -> merged[k] = headers.optString(k) }
                data.optString("referer").ifBlank { null }?.let { merged.putIfAbsent("Referer", it) }
                if (merged.isNotEmpty()) result[url] = merged
            } catch (e: Exception) {
                // 单个图片 header 失败不阻塞整体
            }
        }
        return result
    }

    override suspend fun getDetail(bookId: String): SourceResult<SearchBook> {
        return SourceResult.Success(
            SearchBook(id = bookId, sourceId = id, title = bookId, author = "未知作者")
        )
    }

    override suspend fun getDownloadInfo(bookId: String): SourceResult<DownloadInfo> {
        return SourceResult.Error(SourceException.ParseError("JS 漫画源请通过章节下载"))
    }

    override suspend fun login(credential: LoginCredential): SourceResult<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val obj = callJs(
                    """src.account && typeof src.account.login === 'function' ? src.account.login.call(src, ${q(credential.username)}, ${q(credential.password)}) : Promise.resolve(false)"""
                )
                if (obj?.optBoolean("ok") == true) {
                    getEngine().setLoggedIn(true)
                    SourceResult.Success(true)
                } else {
                    SourceResult.Error(
                        SourceException.Unknown(obj?.optString("error", "登录失败") ?: "登录失败")
                    )
                }
            } catch (e: Exception) {
                SourceResult.Error(SourceException.Unknown("登录失败: ${e.message}", e))
            }
        }

    override suspend fun logout() {
        runCatching {
            callJs("src.account && typeof src.account.logout === 'function' ? src.account.logout.call(src) : null")
        }
        runCatching { getEngine().setLoggedIn(false) }
    }

    override suspend fun isLoggedIn(): Boolean = withContext(Dispatchers.IO) {
        runCatching { getEngine().isLoggedIn() }.getOrDefault(false)
    }

    override suspend fun getCoverHeaders(url: String): Map<String, String> =
        withContext(Dispatchers.IO) {
            try {
                val obj = callJs(
                    """src.comic && typeof src.comic.onThumbnailLoad === 'function' ? src.comic.onThumbnailLoad.call(src, ${q(url)}) : null"""
                )
                if (obj?.optBoolean("ok") == true) {
                    val data = obj.optJSONObject("data") ?: return@withContext emptyMap()
                    val merged = LinkedHashMap<String, String>()
                    data.optJSONObject("headers")?.keys()?.forEach { k ->
                        merged[k] = data.optJSONObject("headers")!!.optString(k)
                    }
                    data.optString("referer").ifBlank { null }?.let {
                        merged.putIfAbsent("Referer", it)
                    }
                    merged
                } else {
                    emptyMap()
                }
            } catch (e: Exception) {
                emptyMap()
            }
        }

    private fun splitChapterId(chapterId: String): Pair<String, String>? {
        val sep = chapterId.indexOf('\u0001')
        if (sep <= 0) return null
        return chapterId.substring(0, sep) to chapterId.substring(sep + 1)
    }

    private fun q(value: String): String = org.json.JSONObject.quote(value)
}

/** 缓存 Venera 运行时脚本，避免每个源重复读 assets。 */
internal object VeneraRuntime {
    @Volatile
    private var cached: String? = null

    fun get(context: Context): String {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val text = context.assets.open("venera/_venera_.js")
                .bufferedReader()
                .use { it.readText() }
            cached = text
            return text
        }
    }
}
