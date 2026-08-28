package com.example.source.js

import android.content.Context
import android.util.Log
import com.example.source.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 一个 Venera 兼容的 JS 漫画源。
 * 通过 JsSourceEngine 调用源脚本的 search.load / comic.loadInfo / comic.loadEp。
 */

/**
 * 把 JS 源桥层的原始错误翻译成用户能看懂、能行动的中文提示。
 * 此前聚合搜索把所有非超时错误一律显示成「无结果」，真实原因（被墙需代理/需登录/登录过期）完全不可见。
 */
internal fun friendlyJsSourceError(raw: String): String {
    val text = raw.trim()
    return when {
        text.isEmpty() -> "JS 执行失败"
        text.contains("Not logged in", ignoreCase = true) -> "该源需要登录：请在书源管理中登录"
        text.contains("Invalid status code: 401") -> "登录已过期：请在书源管理中重新登录"
        text.contains("ERR_", ignoreCase = true) ||
            text.contains("timed out", ignoreCase = true) ||
            text.contains("timeout", ignoreCase = true) ||
            text.contains("请求超时") ||
            text.contains("Connection reset", ignoreCase = true) ||
            text.contains("SocketException", ignoreCase = true) ||
            text.contains("Unable to resolve host", ignoreCase = true) ->
            "网络连接失败：该源被墙，请在系统代理或 VPN 环境下使用"
        else -> text.take(120)
    }
}

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

    private data class ImageConfig(
        val originalUrl: String,
        val finalUrl: String,
        val headers: Map<String, String>,
        val modifyCode: String?,
        val nl: String? = null
    )

    private val imageConfigCache = object : LinkedHashMap<String, ImageConfig>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageConfig>?): Boolean = size > 512
    }

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

    /** 网络类错误（超时/断连/重置）自动重试一次，源站抽风时大幅提高成功率。 */
    private suspend fun callJsWithRetry(jsCall: String): JSONObject? {
        var result = callJs(jsCall)
        val err = result?.optString("error", "") ?: ""
        val retryable = err.contains("timeout", ignoreCase = true) ||
            err.contains("timed out", ignoreCase = true) ||
            err.contains("ERR_CONNECTION", ignoreCase = true) ||
            err.contains("ERR_EMPTY_RESPONSE", ignoreCase = true) ||
            err.contains("ERR_RESPONSE_HEADERS_TRUNCATED", ignoreCase = true) ||
            err.contains("Connection reset", ignoreCase = true) ||
            err.contains("SocketException", ignoreCase = true) ||
            err.contains("connect", ignoreCase = true)
        if (retryable) {
            kotlinx.coroutines.delay(900)
            result = callJs(jsCall)
        }
        return result
    }

    override suspend fun search(keyword: String): SourceResult<List<SearchBook>> =
        withContext(Dispatchers.IO) {
            try {
                val obj = callJsWithRetry("src.search.load.call(src, ${q(keyword)}, [], 1)")
                    ?: return@withContext SourceResult.Error(SourceException.ParseError("JS 源无响应"))
                if (!obj.optBoolean("ok")) {
                    val err = obj.optString("error", "JS 执行失败")
                    Log.w("JsComic[$sourceKey]", "call error: $err")
                    return@withContext SourceResult.Error(SourceException.ParseError(friendlyJsSourceError(err)))
                }
                val comics = obj.optJSONObject("data")?.optJSONArray("comics") ?: JSONArray()
                val books = (0 until comics.length()).mapNotNull { i ->
                    val c = comics.optJSONObject(i) ?: return@mapNotNull null
                    val id = c.optString("id")
                    val title = c.optString("title")
                    if (id.isBlank() || title.isBlank()) return@mapNotNull null
                    val rawAuthor = c.optStringOrJoin("subTitle")
                        .ifBlank { c.optStringOrJoin("subtitle") }
                        .ifBlank { c.optStringOrJoin("author") }
                        .ifBlank { extractAuthorFromTags(c.optJSONArray("tags")) }
                    SearchBook(
                        id = id,
                        sourceId = this@JsComicSource.id,
                        title = title,
                        author = rawAuthor.ifBlank { "未知作者" },
                        cover = c.optString("cover", "").ifBlank { null },
                        description = c.optString("description", "").ifBlank { null },
                        language = c.optString("language", "").ifBlank { null },
                        comicId = extractComicId(c),
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
                val obj = callJsWithRetry("src.comic.loadInfo.call(src, ${q(bookId)})")
                    ?: return@withContext SourceResult.Error(SourceException.ParseError("JS 源无响应"))
                if (!obj.optBoolean("ok")) {
                    val err = obj.optString("error", "JS 执行失败")
                    Log.w("JsComic[$sourceKey]", "call error: $err")
                    return@withContext SourceResult.Error(SourceException.ParseError(friendlyJsSourceError(err)))
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
                val obj = callJsWithRetry("src.comic.loadEp.call(src, ${q(pair.first)}, ${q(pair.second)})")
                    ?: return@withContext SourceResult.Error(SourceException.ParseError("JS 源无响应"))
                if (!obj.optBoolean("ok")) {
                    val err = obj.optString("error", "JS 执行失败")
                    Log.w("JsComic[$sourceKey]", "call error: $err")
                    return@withContext SourceResult.Error(SourceException.ParseError(friendlyJsSourceError(err)))
                }
                val images = obj.optJSONObject("data")?.optJSONArray("images") ?: JSONArray()
                val rawUrls = (0 until images.length()).mapNotNull { i ->
                    images.optString(i).ifBlank { null }
                }
                if (rawUrls.isEmpty()) {
                    SourceResult.Error(SourceException.ParseError("该章节没有可读取的图片"))
                } else if (sourceKey == "ehentai") {
                    // e-hentai 返回的是“图片页 URL”，真正的图片在阅读/下载时懒加载，
                    // 避免一次性逐页请求（大画廊会等几十秒）
                    SourceResult.Success(rawUrls)
                } else {
                    // 应用 onImageLoad 返回的重写 URL（部分源会替换图片域名/签名）
                    // 顺序解析：部分源（ehentai）需要把上一页的 nl 传给下一页才能取到真实图片 URL
                    val resolved = mutableListOf<String>()
                    var prevNl: String? = null
                    rawUrls.forEach { rawUrl ->
                        val config = resolveImageConfig(chapterId, rawUrl, prevNl)
                        resolved.add(config.finalUrl)
                        prevNl = config.nl
                    }
                    SourceResult.Success(resolved)
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
        val result = HashMap<String, Map<String, String>>()
        if (sourceKey == "ehentai") {
            urls.forEach { url ->
                result[url] = mapOf("Referer" to "https://e-hentai.org/")
            }
            return result
        }
        urls.forEach { url ->
            val config = synchronized(imageConfigCache) {
                imageConfigCache.values.firstOrNull { it.finalUrl == url }
            }
            val headers = config?.headers ?: resolveImageConfig(chapterId, url).headers
            if (headers.isNotEmpty()) result[url] = headers
        }
        return result
    }

    override suspend fun resolveChapterImage(url: String): String? = withContext(Dispatchers.IO) {
        if (sourceKey != "ehentai") return@withContext null
        resolveEhentaiImage(url, 0)
    }

    private suspend fun resolveEhentaiImage(url: String, attempt: Int): String? {
        val config = resolveImageConfig("$url\u0001", url)
        val realUrl = config.finalUrl
        if (realUrl == url) {
            // 图片页解析失败（代理抽风/超时），清掉缓存重试拿新链接
            if (attempt < 3) {
                synchronized(imageConfigCache) { imageConfigCache.remove(url) }
                delay(700L * (attempt + 1))
                return resolveEhentaiImage(url, attempt + 1)
            }
            return null
        }
        if (!realUrl.contains("hath.network")) return realUrl
        // H@H 图床对 OkHttp 握手不友好，改用 Cronet 下载并缓存到本地，阅读器直接加载本地文件
        val cacheDir = File(context.cacheDir, "ehimg").apply { mkdirs() }
        val cacheFile = File(cacheDir, "${realUrl.hashCode().toUInt().toString(16)}.img")
        if (!cacheFile.exists() || cacheFile.length() == 0L) {
            val headers = config.headers + mapOf("Referer" to "https://e-hentai.org/")
            val bytes = getEngine().fetchImageBytes(realUrl, headers)
            if (bytes != null && bytes.isNotEmpty()) {
                runCatching { cacheFile.writeBytes(bytes) }
            }
            if ((cacheFile.length() == 0L || !cacheFile.exists()) && attempt < 3) {
                // keystamp 可能过期/代理抽风，重新解析一次拿到新图片链接再下载
                synchronized(imageConfigCache) { imageConfigCache.remove(url) }
                delay(800L * (attempt + 1))
                return resolveEhentaiImage(url, attempt + 1)
            }
        }
        return if (cacheFile.exists() && cacheFile.length() > 0L) {
            android.net.Uri.fromFile(cacheFile).toString()
        } else {
            realUrl
        }
    }

    override suspend fun getResolvedHeaders(url: String): Map<String, String> =
        withContext(Dispatchers.IO) {
            if (sourceKey != "ehentai") return@withContext emptyMap()
            synchronized(imageConfigCache) {
                imageConfigCache[url]?.headers
                    ?: imageConfigCache.values.firstOrNull { it.finalUrl == url }?.headers
            } ?: emptyMap()
        }

    private suspend fun resolveImageConfig(
        chapterId: String,
        url: String,
        prevNl: String? = null
    ): ImageConfig {
        synchronized(imageConfigCache) {
            imageConfigCache[url]?.let { return it }
        }
        val pair = splitChapterId(chapterId)
        val fallback = ImageConfig(url, url, emptyMap(), null, null)
        val config = if (pair == null) fallback else try {
            val nlArg = prevNl?.let { ", ${q(it)}" } ?: ""
            val obj = callJsWithRetry(
                "(src.comic.onImageLoad ? src.comic.onImageLoad.call(src, ${q(url)}, ${q(pair.first)}, ${q(pair.second)}$nlArg) : null)"
            )
            if (obj?.optBoolean("ok") != true) fallback else {
                val data = obj.optJSONObject("data")
                if (data == null) fallback else {
                    val merged = LinkedHashMap<String, String>()
                    data.optJSONObject("headers")?.keys()?.forEach { k ->
                        merged[k] = data.optJSONObject("headers")!!.optString(k)
                    }
                    data.optString("referer").ifBlank { null }?.let {
                        merged.putIfAbsent("Referer", it)
                    }
                    ImageConfig(
                        originalUrl = url,
                        finalUrl = data.optString("url")
                            .takeIf { it.startsWith("http") || it.startsWith("//") }
                            ?.let { if (it.startsWith("//")) "https:$it" else it }
                            ?: url,
                        headers = merged,
                        modifyCode = data.optString("modifyImage").ifBlank { null },
                        nl = data.optString("nl").ifBlank { null }
                    )
                }
            }
        } catch (e: Exception) {
            fallback
        }
        synchronized(imageConfigCache) { imageConfigCache[url] = config }
        if (config.modifyCode != null) {
            Log.i("JsImageProcessor", "register ${config.finalUrl.take(80)} code=${config.modifyCode.take(40).replace('\n', ' ')}")
            JsImageProcessor.register(config.finalUrl, getEngine(), config.modifyCode)
        } else {
            JsImageProcessor.unregister(config.finalUrl)
        }
        return config
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
                    val raw = obj?.optString("error", "") ?: ""
                    Log.w("JsComic[$sourceKey]", "login error: $raw")
                    val msg = when {
                        raw.isBlank() -> "登录失败：账号或密码错误"
                        raw.contains("Invalid email", ignoreCase = true) ||
                            raw.contains("invalid password", ignoreCase = true) -> "登录失败：账号或密码错误"
                        raw.contains("ERR_", ignoreCase = true) ||
                            raw.contains("timed out", ignoreCase = true) ||
                            raw.contains("timeout", ignoreCase = true) ||
                            raw.contains("请求超时") ->
                            "登录失败：网络无法连接该源（该源被墙，请开系统代理或 VPN）"
                        else -> "登录失败：${friendlyJsSourceError(raw)}"
                    }
                    SourceResult.Error(SourceException.Unknown(msg))
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

    /** 兼容数组/对象形式的作者字段（jm 的 author 是数组，picacg 等为字符串）。 */
    private fun JSONObject.optStringOrJoin(key: String): String = when (val v = opt(key)) {
        is String -> v.trim()
        is JSONArray -> (0 until v.length()).mapNotNull { i ->
            v.optString(i).trim().ifBlank { null }
        }.distinct().joinToString("、")
        is JSONObject -> v.optString("name", "").trim()
        else -> ""
    }

    /** 从 tags 中提取 artist/author/cosplayer/group 作为作者兜底。 */
    private fun extractAuthorFromTags(tags: JSONArray?): String {
        if (tags == null) return ""
        val hits = mutableListOf<String>()
        for (i in 0 until tags.length()) {
            val t = tags.optString(i)
            val lower = t.lowercase()
            val hit = when {
                lower.startsWith("artist:") -> t.substringAfter(':').trim()
                lower.startsWith("author:") -> t.substringAfter(':').trim()
                lower.startsWith("cosplayer:") -> t.substringAfter(':').trim()
                lower.startsWith("group:") -> t.substringAfter(':').trim()
                else -> null
            }
            if (!hit.isNullOrBlank()) hits.add(hit)
        }
        return hits.distinct().joinToString("、")
    }

    /** 提取作品编号（jm 号 / ehentai gid / nhentai id 等），方便用户按号码搜索。 */
    private fun extractComicId(c: JSONObject): String? {
        val id = c.optString("id").trim()
        if (id.isBlank()) return null
        val patterns = listOf(
            Regex("""/g/(\d+)/"""),
            Regex("""album[=/](\d+)"""),
            Regex("""/(\d+)/?$""")
        )
        for (re in patterns) {
            re.find(id)?.groupValues?.getOrNull(1)?.let { return it }
        }
        if (id.all(Char::isDigit)) return id
        return null
    }
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
