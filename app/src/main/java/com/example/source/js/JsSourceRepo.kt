package com.example.source.js

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import com.example.source.SourceResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Venera 兼容源仓库管理：拉取 index.json、下载源脚本、本地缓存与更新。
 */
object JsSourceRepo {

    /** 本地补丁版本：升级后强制重新下载全部源脚本，避免缓存到旧补丁/坏脚本。 */
    private const val PATCH_VERSION = 25

    /** 已知成人源 key 黑名单（默认隐藏，设置彩蛋开启后可见）。 */
    val ADULT_KEYS = setOf(
        "nhentai", "ehentai", "hitomi", "jm", "picacg", "wnacg", "mxs",
        "mh18", "hcomic", "hot_manga"
    )

    /** 需要账号登录才能搜索/阅读的源。 */
    val LOGIN_KEYS = setOf("picacg")

    /** 与 App 内置源重复、同 key 多账号、或需要用户自建服务器的源。 */
    private val EXCLUDED_KEYS = setOf(
        "manga_dex", "lanraragi", "komga", "kavita",
        "baozi", "jcomic",
        // 当前网络/站点确认不可用：同步仓库时默认排除
        "zaimanhua", "ManHuaGui", "ykmh", "happy", "Komiic",
        "shonen_jump_plus", "mh1234", "ccc", "comic_walker"
        , "mh18"
    )

    /** 证书不完整/自签名，需要忽略 TLS 校验的源。 */
    private val INSECURE_KEYS = setOf("baozi")

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    data class SourceMeta(
        val key: String,
        val name: String,
        val fileName: String,
        val version: String,
        val adult: Boolean,
        val insecure: Boolean
    )

    private fun dir(context: Context): File =
        File(context.filesDir, "js_sources").apply { mkdirs() }

    private fun indexFile(context: Context): File = File(dir(context), "index.json")

    /** 从本地缓存加载已安装的 JS 源（尊重成人源开关）。 */
    suspend fun loadCached(context: Context, includeAdult: Boolean): List<JsComicSource> =
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences("js_source_meta", Context.MODE_PRIVATE)
            if (prefs.getInt("patch_version", 0) != PATCH_VERSION) {
                dir(context).deleteRecursively()
                // 清理旧补丁写入的 e-hentai 测试 cookie（sl/ns），避免污染新脚本
                context.getSharedPreferences("js_source_cookies", Context.MODE_PRIVATE)
                    .edit()
                    .remove("ck_e-hentai.org")
                    .remove("ck_exhentai.org")
                    .apply()
                return@withContext emptyList()
            }
            val index = indexFile(context)
            if (!index.exists()) return@withContext emptyList()
            try {
                val metas = parseIndex(index.readText())
                    .filter { includeAdult || !it.adult }
                val loaded = metas.mapNotNull { meta ->
                    val file = File(dir(context), meta.fileName)
                    if (!file.exists()) return@mapNotNull null
                    JsComicSource(
                        context = context,
                        sourceKey = meta.key,
                        name = meta.name,
                        version = meta.version,
                        script = patchScript(meta.key, file.readText()),
                        insecureTls = meta.insecure,
                        loginRequired = meta.key in LOGIN_KEYS
                    )
                }
                // 自愈：索引里有但脚本文件缺失（例如上次补丁失败）时返回空，
                // 让调用方走 install 重新拉取，避免源悄悄消失
                if (loaded.size != metas.size) emptyList() else loaded
            } catch (e: Exception) {
                emptyList()
            }
        }

    /**
     * 书源健康检查：并行搜索一个通用关键词，
     * 返回“搜索失败/超时”的源 key（当前网络下不可用，建议停用）。
     */
    suspend fun healthCheck(
        sources: List<JsComicSource>
    ): Set<String> = withContext(Dispatchers.IO) {
        if (sources.isEmpty()) return@withContext emptySet()
        val disabled = HashSet<String>()
        coroutineScope {
            sources.map { source ->
                async {
                    val result = withTimeoutOrNull(12000) { source.search("斗破苍穹") }
                    when (result) {
                        is SourceResult.Success -> {
                            // 能连通但搜出结果时再验一次章节，避免“搜到却点不开”的源留在列表里
                            if (result.data.isNotEmpty()) {
                                val chapterResult = withTimeoutOrNull(10000) {
                                    source.getChapters(result.data.first().id)
                                }
                                if (chapterResult == null || chapterResult is SourceResult.Error) {
                                    disabled.add(source.sourceKey)
                                }
                            }
                        }
                        is SourceResult.Error -> disabled.add(source.sourceKey)
                        null -> disabled.add(source.sourceKey)
                    }
                }
            }.awaitAll()
        }
        disabled
    }

    /**
     * 从远程仓库安装/更新全部源（跳过失败源），并写入本地缓存。
     * 返回成功安装的源列表；网络失败返回空并保留旧缓存。
     */
    suspend fun install(
        context: Context,
        repoUrl: String,
        includeAdult: Boolean,
        onStatus: (String) -> Unit = {}
    ): List<JsComicSource> = withContext(Dispatchers.IO) {
        try {
            onStatus("正在获取源仓库列表…")
            val indexJson = fetch(repoUrl)
            if (indexJson == null) {
                Log.w("JsRepo", "fetch index failed: $repoUrl")
                return@withContext emptyList()
            }
            val metas = parseIndex(indexJson).filter { includeAdult || !it.adult }
            Log.i("JsRepo", "index ok, metas=${metas.size}")
            val baseUrl = repoUrl.substringBeforeLast('/', repoUrl)
            val semaphore = Semaphore(4)
            val results = coroutineScope {
                metas.map { meta ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            try {
                                val script = fetch("$baseUrl/${meta.fileName}")
                                if (script != null) {
                                    val patched = patchScript(meta.key, script)
                                    File(dir(context), meta.fileName).writeText(patched)
                                    meta to patched
                                } else null
                            } catch (e: Exception) {
                                Log.w("JsRepo", "install failed: ${meta.fileName}", e)
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            if (results.isEmpty()) return@withContext emptyList()
            indexFile(context).writeText(indexJson)
            context.getSharedPreferences("js_source_meta", Context.MODE_PRIVATE)
                .edit()
                .putInt("patch_version", PATCH_VERSION)
                .apply()
            Log.i("JsRepo", "installed ${results.size} sources")
            onStatus("已安装 ${results.size} 个漫画源")
            results.map { (meta, script) ->
                JsComicSource(
                    context = context,
                    sourceKey = meta.key,
                    name = meta.name,
                    version = meta.version,
                    script = script,
                    insecureTls = meta.insecure,
                    loginRequired = meta.key in LOGIN_KEYS
                )
            }
        } catch (e: Exception) {
            Log.w("JsRepo", "install failed", e)
            emptyList()
        }
    }

    private fun parseIndex(json: String): List<SourceMeta> {
        val arr = JSONArray(json)
        val seen = HashSet<String>()
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val key = obj.optString("key")
            val fileName = obj.optString("fileName")
            if (key.isBlank() || fileName.isBlank() || key in EXCLUDED_KEYS) return@mapNotNull null
            if (!seen.add(key)) return@mapNotNull null
            SourceMeta(
                key = key,
                name = obj.optString("name", key),
                fileName = fileName,
                version = obj.optString("version", "0"),
                adult = key in ADULT_KEYS,
                insecure = key in INSECURE_KEYS
            )
        }
    }

    /**
     * 针对远端脚本的本地兼容补丁（站点改版后脚本选择器失效，等上游更新前先兜底）。
     * 仅做最小改动，不破坏源脚本其它逻辑。
     */
    private fun patchScript(key: String, script: String): String = when (key) {
        "ehentai" -> {
            var p = script.replace(
            Regex(
                """if\(isFavorited\) \{\s*let position = document\s*\.querySelector\("div#fav"\)\s*\.children\[0\]\s*\.attributes\["style"\]\s*\.split\("background-position:0px -"\)\[1\]\s*\.split\("px;"\)\[0\];\s*folder = \(Number\(position-2\) / 19\)\.toString\(\)\s*\}""",
                setOf(RegexOption.DOT_MATCHES_ALL)
            ),
            """
            if(isFavorited) {
                let favEl = document.querySelector("div#fav");
                let favStyle = "";
                if (favEl && favEl.children[0] && favEl.children[0].attributes) {
                    favStyle = favEl.children[0].attributes["style"] || "";
                }
                let posMatch = favStyle.split("background-position:0px -")[1];
                if (posMatch) {
                    let position = posMatch.split("px;")[0];
                    folder = (Number(position-2) / 19).toString();
                }
            }
            """.trimIndent()
        )
            // 预连接暖身：Clash 代理首连 e-hentai 可能冷启动 30s+，init 时后台预热连接
            p = p.replace(
                """    // update url
    url = "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/ehentai.js"""",
                """    // update url
    url = "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/ehentai.js"

    async init() {
        try {
            Network.get(this.baseUrl + '/', {}).catch(() => {});
            Network.get(this.baseUrl + '/popular', {}).catch(() => {});
        } catch (e) {}
    }"""
            )
            // 详情页走 nw=session（delta-comic 风格）；不预置 sl/ns cookie（实测会导致匿名搜索挂起）
            // 详情页请求与 delta-comic 一致：hc=1&nw=session + referer（临时保留诊断日志）
            p = p.replace(
                """            let res = await Network.get(id, {
                'cookie': 'nw=1'
            });""",
                """            let __detailUrl = id.includes('?') ? id : id + '?hc=1&nw=session';
            let res = await Network.get(__detailUrl, {
                'cookie': 'nw=1',
                'referer': this.baseUrl + '/',
            });"""
            )
            // 缩略图分页请求同样带 referer 与 nw=session
            p = p.replace(
                """            let res = await Network.get(url, {
                'cache-time': 'long',
                'prevent-parallel': 'true',
                'cookie': 'nw=1'
            });""",
                """            let __thumbUrl = url.includes('?') ? url + '&nw=session' : url + '?nw=session';
            let res = await Network.get(__thumbUrl, {
                'cache-time': 'long',
                'prevent-parallel': 'true',
                'cookie': 'nw=1',
                'referer': this.baseUrl + '/',
            });"""
            )
            // 复制 delta-comic 的取图方式：直接抓图片页 #img，不再走 api.e-hentai.org
            p = p.replace(
                Regex(
                    """onImageLoad: async \(image, comicId, epId, nl\) => \{.*?\n        \},\n        /\*\*\n         \* \[Optional\] provide configs for a thumbnail loading""",
                    setOf(RegexOption.DOT_MATCHES_ALL)
                ),
                """        onImageLoad: async (image, comicId, epId, nl) => {
            if (typeof image === 'string' && image.startsWith('http') && !image.includes('/s/')) {
                return {
                    url: image,
                    headers: { 'referer': this.baseUrl + '/' },
                }
            }
            let url = ''
            if (typeof image === 'string' && image.startsWith('http') && image.includes('/s/')) {
                url = image
            } else {
                let page = Number(image)
                if (!(page >= 0)) page = 0
                let cache = await this.getThumbUrls(comicId)
                let urls = cache.urls || []
                let pageSize = cache.pageSize || Math.max(urls.length, 1)
                if (page < urls.length) {
                    url = urls[page]
                } else {
                    let pageIndex = Math.floor(page / pageSize)
                    let index = page % pageSize
                    let more = await this.getThumbPage(comicId, pageIndex)
                    url = more[index] || urls[urls.length - 1]
                }
            }
            if (!url) throw "Failed to get image page url"
            let res = await Network.get(url, {
                'cookie': 'nw=1',
                'referer': this.baseUrl + '/',
            })
            if (res.status !== 200) throw 'Invalid status code: ' + res.status
            let document = new HtmlDocument(res.body)
            let img = document.querySelector('#img')
            let imgUrl = img ? img.attributes["src"] : ""
            document.dispose()
            if (!imgUrl) throw "Failed to get image url"
            return {
                url: imgUrl,
                headers: { 'referer': this.baseUrl + '/' },
            }
        },
        /**
         * [Optional] provide configs for a thumbnail loading"""
            )
            // loadEp 只返回每页的“图片页 URL”（每 40 页一次请求），真正图片在阅读/下载时懒加载
            p = p.replace(
                """        loadEp: async (comicId, epId) => {
            let comic = await this.comic.loadInfo(comicId)
            return {
                images: Array.from({length: comic.maxPage}, (_, i) => i.toString())
            }
        },""",
                """        loadEp: async (comicId, epId) => {
            let comic = await this.comic.loadInfo(comicId)
            let total = comic.maxPage || 0
            if (total === 0) return { images: [] }
            let cache = await this.getThumbUrls(comicId)
            let urls = cache.urls || []
            let pageSize = cache.pageSize || Math.max(urls.length, 1)
            let results = new Array(total)
            let pageIndices = new Set()
            for (let p = 0; p < total; p++) {
                if (p < urls.length) {
                    results[p] = urls[p]
                } else {
                    pageIndices.add(Math.floor(p / pageSize))
                }
            }
            await Promise.all(Array.from(pageIndices).map((pi) => this.getThumbPage(comicId, pi)))
            for (let p = 0; p < total; p++) {
                if (p >= urls.length) {
                    let pi = Math.floor(p / pageSize)
                    let idx = p % pageSize
                    let more = await this.getThumbPage(comicId, pi)
                    results[p] = more[idx] || urls[urls.length - 1]
                }
            }
            return { images: results }
        },"""
            )
            // 搜索作者：从 tags 里提取 artist/cosplayer/group，方便卡片显示真实作者
            p = p.replace(
                "    async onLoadFailed() {",
                """    getThumbUrls(comicId) {
        if (!this.__thumbCache) this.__thumbCache = {};
        if (!this.__thumbCache[comicId]) {
            this.__thumbCache[comicId] = (async () => {
                let first = await this.comic.loadThumbnails(comicId);
                return {
                    urls: first.urls || [],
                    pageSize: Math.max(first.thumbnails.length, first.urls.length, 1)
                };
            })();
        }
        return this.__thumbCache[comicId];
    }

    getThumbPage(comicId, pageIndex) {
        if (!this.__thumbCache) this.__thumbCache = {};
        let key = comicId + '#p' + pageIndex;
        if (!this.__thumbCache[key]) {
            this.__thumbCache[key] = (async () => {
                let t = await this.comic.loadThumbnails(comicId, String(pageIndex));
                return t.urls || [];
            })();
        }
        return this.__thumbCache[key];
    }

    getAuthorFromTags(tags, fallback) {
        try {
            if (Array.isArray(tags)) {
                for (let prefix of ["artist:", "cosplayer:", "group:"]) {
                    let hit = tags.find((e) => e && e.toLowerCase().startsWith(prefix));
                    if (hit) {
                        let v = hit.split(":")[1];
                        if (v && v.trim()) return v.trim();
                    }
                }
            }
        } catch (e) {}
        return fallback || "";
    }

    async onLoadFailed() {"""
            )
            // extended 模式
            p = p.replace(
                """                    subTitle: uploader,
                    cover: coverPath,
                    tags: tags,""",
                """                    subTitle: this.getAuthorFromTags(tags, uploader),
                    cover: coverPath,
                    tags: tags,"""
            )
            // compact 模式
            p = p.replace(
                """                    subTitle: uploader,
                    cover: cover,
                    tags: tags,""",
                """                    subTitle: this.getAuthorFromTags(tags, uploader),
                    cover: cover,
                    tags: tags,"""
            )
            p
        }
        "wnacg" -> script.replace(
            Regex(
                """let title = document\.querySelector\("div\.userwrap > h2"\)\.text\s*""" +
                    """let cover = document\.querySelector\("div\.userwrap > div\.asTB > div\.asTBcell\.uwthumb > img"\)\.attributes\["src"\]\s*""" +
                    """cover = 'https:' \+ cover\s*""" +
                    """cover = cover\.substring\(0, 6\) \+ cover\.substring\(8\)\s*""" +
                    """let labels = document\.querySelectorAll\("div\.asTBcell\.uwconn > label"\)\s*""" +
                    """let category = labels\[0\]\.text\.split\("："\)\[1\]\s*""" +
                    """let pages = labels\[1\]\.text\.split\("："\)\[1\];\s*""" +
                    """let tagsDom = document\.querySelectorAll\("a\.tagshow"\);\s*""" +
                    """let tags = new Map\(\)\s*""" +
                    """tags\.set\("頁數", \[pages\]\)\s*""" +
                    """tags\.set\("分類", \[category\]\)\s*""" +
                    """if \(tagsDom\.length > 0\) \{\s*""" +
                    """tags\.set\("標籤", tagsDom\.map\(\(e\) => e\.text\)\)\s*""" +
                    """\}\s*""" +
                    """let description = document\.querySelector\("div\.asTBcell\.uwconn > p"\)\.text;\s*""" +
                    """let uploader = document\.querySelector\("div\.asTBcell\.uwuinfo > a > p"\)\.text;""",
                setOf(RegexOption.DOT_MATCHES_ALL)
            ),
            """
            let titleEl = document.querySelector("div#comicName") || document.querySelector("div.userwrap > h2")
            let title = titleEl ? titleEl.text : String(id)
            let coverEl = document.querySelector("div#Cover > img") || document.querySelector("div.userwrap > div.asTB > div.asTBcell.uwthumb > img")
            let cover = coverEl ? (coverEl.attributes["src"] || "") : ""
            if (cover.startsWith("////")) {
                cover = 'https:' + cover.substring(4)
            } else if (cover.startsWith("//")) {
                cover = 'https:' + cover.substring(2)
            }
            let labels = document.querySelectorAll("div.asTBcell.uwconn > label")
            let pagesEl = document.querySelector("p.txtItme > span.date") || labels[1]
            let categoryEl = document.querySelectorAll("p.txtItme > a.pd")[0] || labels[0]
            let category = categoryEl ? categoryEl.text.split("：").pop() : ""
            let pages = pagesEl ? pagesEl.text.replace(/[^0-9]/g, "") : "1"
            let tagsDom = document.querySelectorAll("a.tagshow");
            let tags = new Map()
            tags.set("頁數", [pages])
            tags.set("分類", [category])
            if (tagsDom.length > 0) {
                tags.set("標籤", tagsDom.map((e) => e.text))
            }
            let descriptionEl = document.querySelector("div.asTBcell.uwconn > p") || document.querySelector("div.Introduct_Sub")
            let description = descriptionEl ? descriptionEl.text : ""
            let uploaderEl = document.querySelector("a.introName") || document.querySelector("div.asTBcell.uwuinfo > a > p")
            let uploader = uploaderEl ? uploaderEl.text : ""
            """.trimIndent()
        )
        // hitomi：gg.js（图片子域映射）10 分钟内复用，避免每开一个章节都重新下载并 eval
        "hitomi" -> {
            var patched = script.replace(
                Regex(
                    """async function get_image_srcs\(files\) \{\s*const resp = await Network\.get\(\s*"https://" \+ domain \+ "/" \+ "gg\.js\?_=" \+ new Date\(\)\.getTime\(\),\s*\{\s*referer: refererUrl,\s*\}\s*\);\s*if \(resp\.status >= 400\) \{\s*throw new Error\(resp\.status\);\s*\}\s*eval\(resp\.body\);\s*if \(!gg\.b\) throw new Error\(\);""",
                    setOf(RegexOption.DOT_MATCHES_ALL)
                ),
                """
                let __ggCacheTime = 0;
                async function get_image_srcs(files) {
                  const now = Date.now();
                  if (!(__ggCacheTime && now - __ggCacheTime < 600000 && typeof gg !== 'undefined' && gg && gg.b)) {
                    const resp = await Network.get(
                      "https://" + domain + "/" + "gg.js?_=" + now,
                      {
                        referer: refererUrl,
                      }
                    );
                    if (resp.status >= 400) {
                      throw new Error(resp.status);
                    }
                    eval(resp.body);
                    if (!gg.b) throw new Error();
                    __ggCacheTime = now;
                  }
                """.trimIndent()
            )
            // hitomi CDN 现在默认给 AVIF（Android 平台解码不稳定），改要 webp
            patched.replace(
                Regex("""return files\.map\(\(image\) => url_from_url_from_hash\(0, image, "avif"\)\);"""),
                """return files.map((image) => url_from_url_from_hash(0, image, "webp"));"""
            )
        }
        // 漫画人：loadEp 直接把 epId 拼成相对路径，Cronet 无法请求；补成绝对 URL
        "manhuaren" -> script.replace(
            Regex("""let url = `\$\{epId\}/`;"""),
            """
            let url = epId.startsWith('http') ? epId : this.baseUrl + epId;
            if (!url.endsWith('/')) url += '/';
            """.trimIndent()
        )
        // comick：loadEp 请求章节页没带 headers，容易被反爬拒绝导致图片列表为空
        "comick" -> script.replace(
            Regex("""let res = await Network\.get\(url\);"""),
            """let res = await Network.get(url, Comick.getRandomHeaders());"""
        )
        // picacg：登录后补存账号，否则搜索时的 reLogin 报 Invalid account data
        "picacg" -> {
            var patched = script.replace(
                Regex("""this\.saveData\('token', json\.data\.token\)\s*return 'ok'"""),
                """this.saveData('token', json.data.token); this.saveData('account', [account, pwd]); return 'ok'"""
            )
            // 账号数据缺失时不再中断，直接用现有 token 重试
            patched = patched.replace(
                Regex("""if\(!Array\.isArray\(account\)\) \{\s*throw new Error\('Failed to reLogin: Invalid account data'\);\s*\}"""),
                """if(!Array.isArray(account)) { return 'ok'; }"""
            )
            patched = patched.replace(
                Regex("""this\.buildHeaders\('POST', `comics/advanced-search\?page=\$\{page\}`, this\.loadData\('token'\)\)"""),
                """this.buildHeaders('POST', 'comics/advanced-search?page=' + page, this.loadData('token'))"""
            )
            patched.replace(
                Regex("""throw 'Invalid status code: ' \+ res\.status"""),
                """throw 'Invalid status code: ' + res.status + ' body=' + String(res.body || '').substring(0, 160)"""
            )
        }
        // 拷贝漫画：动态 API 域名没有真正持久化（脚本里写 this.settings 不走 loadSetting），
        // 导致一直用默认域名 api.copy2000.online，它一抽风就全挂。
        // 这里把网络2接口返回的可用域名 saveData 持久化，并加入候选域名 + 失败自动刷新重试。
        "copy_manga" -> {
            var patched = script
            // 1) apiUrl getter：优先使用已持久化的动态域名
            val apiStart = patched.indexOf("get apiUrl() {")
            if (apiStart >= 0) {
                // 原 getter 的 return 是模板字符串，里面有 ${...} 的右花括号，
                // 不能从 apiStart 直接找第一个 }，要等 return 行结束后的下一个 }
                val apiReturn = patched.indexOf("loadSetting('base_url')", apiStart)
                val apiClose = if (apiReturn >= 0) {
                    patched.indexOf('}', patched.indexOf('\n', apiReturn))
                } else {
                    patched.indexOf('}', apiStart)
                }
                if (apiClose > apiStart) {
                    patched = patched.substring(0, apiStart) +
                        """
                        get apiUrl() {
                            const dynamic = this.loadData("_api_url");
                            if (dynamic && dynamic.startsWith("http")) return dynamic;
                            return "https://" + (this.loadSetting('base_url') || CopyManga.defaultApiUrl);
                        }
                        """.trimIndent() +
                        patched.substring(apiClose + 1)
                }
            }
            // 2) refreshAppApi：候选域名逐个尝试，成功后 saveData 持久化
            val refreshStart = patched.indexOf("async refreshAppApi() {")
            if (refreshStart >= 0) {
                val marker = "this.settings.base_url = data.results.api[0][0];"
                val markerIdx = patched.indexOf(marker, refreshStart)
                if (markerIdx >= 0) {
                    val ifClose = patched.indexOf('}', markerIdx)
                    val funcClose = patched.indexOf('}', ifClose + 1)
                    if (funcClose > markerIdx) {
                        patched = patched.substring(0, refreshStart) +
                            """
                            async refreshAppApi() {
                                const candidates = [
                                    "https://api.copy-manga.com/api/v3/system/network2?platform=3",
                                    "https://api.copy2000.online/api/v3/system/network2?platform=3",
                                    "https://api.copymanga.tv/api/v3/system/network2?platform=3",
                                    "https://api.mangacopy.com/api/v3/system/network2?platform=3"
                                ];
                                for (const url of candidates) {
                                    try {
                                        const res = await fetch(url, { headers: this.headers });
                                        if (res.status === 200) {
                                            const data = await res.json();
                                            const apiList = data && data.results && data.results.api;
                                            const host = apiList && apiList[0] && apiList[0][0]
                                                ? String(apiList[0][0]).replace(/^https?:\/\//, "").replace(/\/.*$/, "")
                                                : "";
                                            if (host) {
                                                this.saveData("_api_url", "https://" + host);
                                                this.settings.base_url = host;
                                                return;
                                            }
                                        }
                                    } catch (e) {}
                                }
                            }
                            """.trimIndent() +
                            patched.substring(funcClose + 1)
                    }
                }
            }
            patched + """
            ;(() => {
                const S = CopyManga.prototype;
                const wrapRetry = (obj, fnName) => {
                    const orig = obj[fnName];
                    if (typeof orig !== 'function') return;
                    obj[fnName] = async function () {
                        let lastErr;
                        for (let attempt = 0; attempt < 3; attempt++) {
                            try {
                                return await orig.apply(this, arguments);
                            } catch (e) {
                                lastErr = e;
                                if (attempt === 0) {
                                    try { await this.refreshAppApi(); } catch (e2) {}
                                }
                                await new Promise(r => setTimeout(r, 600 * (attempt + 1)));
                            }
                        }
                        throw lastErr;
                    };
                };
                if (S.search) wrapRetry(S.search, 'load');
                if (S.comic) wrapRetry(S.comic, 'loadInfo');
            })();
            """.trimIndent()
        }
        else -> script
    }

    private fun fetch(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.5359.128 Mobile Safari/537.36"
                )
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        } catch (e: Exception) {
            null
        }
    }
}
