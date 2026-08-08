package com.example.library

import android.app.Application
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BookRepository
import com.example.data.AppDatabase
import com.example.download.DownloadManager
import com.example.download.DownloadRequest
import com.example.download.DownloadState
import com.example.download.DownloadTaskEntity
import com.example.source.*
import com.example.source.impl.MangaDexSource
import com.example.source.js.JsSourceRepo
import com.example.source.zlibrary.ZLibrarySource
import com.example.source.storage.SharedPreferencesSourceStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    
    val sourceManager = SourceManager(SharedPreferencesSourceStorage(application))
    val downloadManager = DownloadManager(application)
    private val database = AppDatabase.getDatabase(application)
    private val repository = BookRepository(application, database.bookDao())

    val currentSource: StateFlow<BookSource?> = sourceManager.activeSource
    val availableSources: StateFlow<List<BookSource>> = sourceManager.availableSources

    val prefs = com.example.data.PreferencesManager(application)
    val hasSeenWelcome = MutableStateFlow(prefs.hasSeenWelcome)
    val hasConfiguredSource = MutableStateFlow(prefs.hasConfiguredSource)
    val hasImportedLocalBook = MutableStateFlow(prefs.hasImportedLocalBook)

    // 阅读统计：为已删除书籍补抓封面与书信息（按书名在所有漫画源搜索一次，缓存到本地，删除历史时清除）
    private val _recordCovers = MutableStateFlow<Map<Int, String>>(emptyMap())
    val recordCovers: StateFlow<Map<Int, String>> = _recordCovers.asStateFlow()
    private val _recordBooks = MutableStateFlow<Map<Int, SearchBook>>(emptyMap())
    val recordBooks: StateFlow<Map<Int, SearchBook>> = _recordBooks.asStateFlow()

    fun resolveMissingRecordCovers(records: List<com.example.data.ReadingRecord>) {
        viewModelScope.launch {
            val missing = records.filter { _recordBooks.value[it.id] == null }
            if (missing.isEmpty()) return@launch
            val prefs = getApplication<Application>().getSharedPreferences(
                "record_cover_cache",
                android.content.Context.MODE_PRIVATE
            )
            val cached = missing.mapNotNull { r ->
                val json = prefs.getString(r.id.toString(), null) ?: return@mapNotNull null
                val book = runCatching {
                    val obj = org.json.JSONObject(json)
                    SearchBook(
                        id = obj.optString("comicId").ifBlank { "record_${r.id}" },
                        sourceId = obj.optString("sourceId", ""),
                        title = obj.optString("title", r.bookTitle),
                        cover = obj.optString("cover").ifBlank { null },
                        author = ""
                    )
                }.getOrNull() ?: return@mapNotNull null
                r.id to book
            }.toMap()
            if (cached.isNotEmpty()) {
                _recordBooks.update { it + cached }
                _recordCovers.update {
                    it + cached.mapValues { entry -> entry.value.cover ?: "" }.filterValues { v -> v.isNotBlank() }
                }
            }
            val toResolve = missing.filter { it.id !in cached }.take(5)
            val resolved = toResolve.map { r ->
                r.id to fetchRecordBook(r.bookTitle)
            }.filter { it.second != null }.map { it.first to it.second!! }
            resolved.forEach { (id, book) ->
                val json = org.json.JSONObject().apply {
                    put("title", book.title)
                    put("sourceId", book.sourceId)
                    put("comicId", book.id)
                    put("cover", book.cover ?: "")
                }.toString()
                prefs.edit().putString(id.toString(), json).apply()
            }
            if (resolved.isNotEmpty()) {
                _recordBooks.update { it + resolved.toMap() }
                _recordCovers.update {
                    it + resolved.toMap().mapValues { entry -> entry.value.cover ?: "" }.filterValues { v -> v.isNotBlank() }
                }
            }
        }
    }

    private suspend fun fetchRecordBook(title: String): SearchBook? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        val sources = sourceManager.allSources.value.filterIsInstance<ComicSource>()
        for (source in sources) {
            val result = withTimeoutOrNull(8000) { source.search(title) }
            val book = (result as? SourceResult.Success)
                ?.data
                ?.firstOrNull { !it.cover.isNullOrBlank() || !it.comicId.isNullOrBlank() }
            if (book != null) {
                return@withContext SearchBook(
                    id = book.comicId?.takeIf { it.isNotBlank() } ?: book.id,
                    sourceId = book.sourceId,
                    title = book.title,
                    cover = book.cover,
                    author = ""
                )
            }
        }
        null
    }

    private val _isCurrentSourceLoggedIn = MutableStateFlow(false)
    val isCurrentSourceLoggedIn: StateFlow<Boolean> = _isCurrentSourceLoggedIn

    private val _comicBook = MutableStateFlow<SearchBook?>(null)
    val comicBook: StateFlow<SearchBook?> = _comicBook.asStateFlow()

    private val _comicChapters = MutableStateFlow<List<ComicChapter>>(emptyList())
    val comicChapters: StateFlow<List<ComicChapter>> = _comicChapters.asStateFlow()

    private val _comicChaptersLoading = MutableStateFlow(false)
    val comicChaptersLoading: StateFlow<Boolean> = _comicChaptersLoading.asStateFlow()

    private val _comicChaptersError = MutableStateFlow<String?>(null)
    val comicChaptersError: StateFlow<String?> = _comicChaptersError.asStateFlow()

    private val _comicChapterImages = MutableStateFlow<List<String>>(emptyList())
    val comicChapterImages: StateFlow<List<String>> = _comicChapterImages.asStateFlow()

    private val _comicChapterHeaders = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    val comicChapterHeaders: StateFlow<Map<String, Map<String, String>>> = _comicChapterHeaders.asStateFlow()

    private val _comicChapterLoading = MutableStateFlow(false)
    val comicChapterLoading: StateFlow<Boolean> = _comicChapterLoading.asStateFlow()

    private val _comicChapterError = MutableStateFlow<String?>(null)
    val comicChapterError: StateFlow<String?> = _comicChapterError.asStateFlow()

    private val _activeComicChapter = MutableStateFlow<ComicChapter?>(null)
    val activeComicChapter: StateFlow<ComicChapter?> = _activeComicChapter.asStateFlow()

    /** 漫画下载统一由应用级 ComicDownloadManager 管理：切页面不中断、失败可重试。 */
    val comicDownloadTasks: StateFlow<Map<String, ComicDownloadTask>> = ComicDownloadManager.tasks

    val comicDownloading: StateFlow<Set<String>> = ComicDownloadManager.tasks
        .map { tasks -> tasks.filterValues { it.status == ComicDownloadStatus.DOWNLOADING }.keys }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val comicDownloadProgress: StateFlow<Map<String, Float>> = ComicDownloadManager.tasks
        .map { tasks -> tasks.mapValues { (_, t) -> t.progress } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val comicPaused: StateFlow<Set<String>> = ComicDownloadManager.tasks
        .map { tasks -> tasks.filterValues { it.status == ComicDownloadStatus.PAUSED }.keys }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val comicMessage: StateFlow<String?> = ComicDownloadManager.message

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    init {
        _searchHistory.value = prefs.searchHistory
        // 漫画下载完成后刷新“已导入本地书”状态
        val notifiedSuccess = mutableSetOf<String>()
        viewModelScope.launch {
            ComicDownloadManager.tasks.collect { tasks ->
                tasks.forEach { (id, t) ->
                    if (t.status == ComicDownloadStatus.SUCCESS && notifiedSuccess.add(id)) {
                        markLocalBookImported()
                    }
                }
            }
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            sourceManager.initialize()
            sourceManager.registerSource(ZLibrarySource(application))
            sourceManager.registerSource(MangaDexSource())

            // 一次性清理上一版内置的社区漫画源（comic_* 自定义书源）
            if (prefs.hasImportedCommunityComics) {
                sourceManager.availableSources.value
                    .filter { it.id.startsWith("comic_") }
                    .forEach { sourceManager.unregisterSource(it.id) }
                prefs.hasImportedCommunityComics = false
            }

            // Venera 兼容 JS 源：优先本地缓存，无缓存时从远程仓库安装
            val jsSources = JsSourceRepo.loadCached(application, prefs.showAdultSources)
                .ifEmpty {
                    JsSourceRepo.install(application, prefs.jsSourceRepoUrl, prefs.showAdultSources)
                }
            jsSources.forEach { source ->
                sourceManager.registerSource(source, defaultEnabled = true)
            }
            Log.i(
                "JsRepo",
                "JS sources ready: ${jsSources.size} (${jsSources.joinToString { it.name }})"
            )

            sourceManager.activeSource.collect { source ->
                checkSourceLoginStatus()
            }
        }
    }

    fun openComic(book: SearchBook) {
        _comicBook.value = book
        _comicChapters.value = emptyList()
        _comicChaptersError.value = null
        _comicChapterImages.value = emptyList()
        _comicChapterHeaders.value = emptyMap()
        _comicChapterError.value = null
        loadChapters(book)
    }

    private val _aggregateMode = MutableStateFlow(true)
    val aggregateMode: StateFlow<Boolean> = _aggregateMode.asStateFlow()

    fun setAggregateMode(enabled: Boolean) {
        _aggregateMode.value = enabled
    }

    private var aggregateSearchSeq = 0L

    fun aggregateSearch(keyword: String) {
        val sources = sourceManager.availableSources.value.filter { it.capabilities.supportComic }
        Log.i("Aggregate", "aggregateSearch '$keyword' sources=${sources.map { it.name }}")
        if (sources.isEmpty()) {
            _uiState.value = LibraryUiState.Error(LibraryError.SourceUnavailable)
            return
        }
        val seq = ++aggregateSearchSeq
        viewModelScope.launch {
            errorMessage.value = null
            // 先展示所有源的“加载中”分组，哪个源先完成就先把哪个源的结果推给 UI
            val initialGroups = sources.map { source ->
                LibraryUiState.AggregateGroup(
                    sourceId = source.id,
                    sourceName = source.name,
                    books = emptyList(),
                    error = null,
                    loading = true
                )
            }
            _uiState.value = LibraryUiState.AggregateResults(initialGroups, running = true)
            sources.forEach { source ->
                launch {
                    val result = withTimeoutOrNull(20000) { source.search(keyword) }
                    if (seq != aggregateSearchSeq) return@launch
                    val group = when (result) {
                        is SourceResult.Success -> {
                            Log.i("Aggregate", "${source.name}: ${result.data.size} books")
                            LibraryUiState.AggregateGroup(
                                sourceId = source.id,
                                sourceName = source.name,
                                books = result.data,
                                error = null,
                                loading = false
                            )
                        }
                        is SourceResult.Error -> {
                            Log.i("Aggregate", "${source.name}: ERROR ${result.exception.message}")
                            LibraryUiState.AggregateGroup(
                                sourceId = source.id,
                                sourceName = source.name,
                                books = emptyList(),
                                error = result.exception.message,
                                loading = false
                            )
                        }
                        null -> {
                            Log.i("Aggregate", "${source.name}: TIMEOUT")
                            LibraryUiState.AggregateGroup(
                                sourceId = source.id,
                                sourceName = source.name,
                                books = emptyList(),
                                error = "搜索超时",
                                loading = false
                            )
                        }
                    }
                    _uiState.update { current ->
                        if (current !is LibraryUiState.AggregateResults) return@update current
                        val newGroups = current.groups.map {
                            if (it.sourceId == source.id) group else it
                        }
                        LibraryUiState.AggregateResults(
                            groups = newGroups,
                            running = newGroups.any { it.loading }
                        )
                    }
                }
            }
        }
    }

    fun loadChapters(book: SearchBook) {
        val source = sourceManager.availableSources.value.firstOrNull { it.id == book.sourceId } as? ComicSource
            ?: sourceManager.activeSource.value as? ComicSource
        if (source == null) {
            _comicChaptersError.value = "当前书源不支持漫画"
            return
        }
        viewModelScope.launch {
            _comicChaptersLoading.value = true
            _comicChaptersError.value = null
            when (val result = source.getChapters(book.id)) {
                is SourceResult.Success -> {
                    _comicChapters.value = result.data
                    if (result.data.isEmpty()) {
                        _comicChaptersError.value = "暂无可用章节"
                    }
                }
                is SourceResult.Error -> {
                    _comicChaptersError.value = result.exception.message ?: "章节加载失败"
                }
            }
            _comicChaptersLoading.value = false
        }
    }

    fun loadChapterImages(chapter: ComicChapter) {
        val source = sourceManager.availableSources.value
            .firstOrNull { it.id == _comicBook.value?.sourceId } as? ComicSource
        if (source == null) return
        viewModelScope.launch {
            _activeComicChapter.value = chapter
            _comicChapterLoading.value = true
            _comicChapterError.value = null
            _comicChapterImages.value = emptyList()
            when (val result = source.getChapterImages(chapter.id)) {
                is SourceResult.Success -> {
                    _comicChapterImages.value = result.data
                    _comicChapterHeaders.value = source.getChapterImageHeaders(chapter.id, result.data)
                }
                is SourceResult.Error -> {
                    _comicChapterError.value = result.exception.message ?: "图片加载失败"
                }
            }
            _comicChapterLoading.value = false
        }
    }

    /** 供阅读器按页懒加载：把 e-hentai 图片页 URL 解析成真实图片 URL（带缓存）。 */
    suspend fun resolveComicImage(url: String): String? = withContext(Dispatchers.IO) {
        val source = sourceManager.availableSources.value
            .firstOrNull { it.id == _comicBook.value?.sourceId } as? ComicSource
        source?.resolveChapterImage(url)
    }

    /** 供阅读器按页懒加载：真实图片 URL 需要的请求头。 */
    suspend fun resolveComicImageHeaders(url: String): Map<String, String> =
        withContext(Dispatchers.IO) {
            val source = sourceManager.availableSources.value
                .firstOrNull { it.id == _comicBook.value?.sourceId } as? ComicSource
            source?.getResolvedHeaders(url) ?: emptyMap()
        }

    fun downloadComicChapter(book: SearchBook, chapter: ComicChapter) {
        if (chapter.external) {
            android.widget.Toast.makeText(
                getApplication(),
                "站外链接章节暂不支持下载",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        val source = sourceManager.availableSources.value
            .firstOrNull { it.id == book.sourceId } as? ComicSource
        if (source == null) {
            android.widget.Toast.makeText(
                getApplication(),
                "当前书源不支持漫画",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        ComicDownloadManager.start(getApplication(), database, book, chapter, source)
    }

    fun retryComicChapter(book: SearchBook, chapter: ComicChapter) {
        downloadComicChapter(book, chapter)
    }

    fun pauseComicChapter(chapterId: String) {
        ComicDownloadManager.pause(chapterId)
    }

    fun cancelComicChapter(chapterId: String) {
        ComicDownloadManager.cancel(chapterId)
    }

    fun clearComicMessage() {
        ComicDownloadManager.clearMessage()
    }

    fun recordSearch(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        val updated = (listOf(q) + _searchHistory.value.filter { it != q }).take(20)
        _searchHistory.value = updated
        prefs.searchHistory = updated
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        prefs.searchHistory = emptyList()
    }

    /**
     * 远程触发的逐源冒烟测试（adb am start -n ... --ez smoke_test true）：
     * 对每个 JS 漫画源 + MangaDex 依次做 搜索 → 章节 → 图片列表 → 真实拉取第一张图并解码，
     * 结果输出到 Logcat 的 SmokeTest 标签，用于排查“黑屏”问题。
     */
    fun runSourceSmokeTest(filter: String = "", keyword: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            Log.i("SmokeTest", "=== smoke test start ===")
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .followRedirects(true)
                .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
                .build()
            val prefs = com.example.data.PreferencesManager(getApplication())
            var jsSources = JsSourceRepo.loadCached(getApplication(), includeAdult = true)
            if (jsSources.isEmpty()) {
                jsSources = JsSourceRepo.install(getApplication(), prefs.jsSourceRepoUrl, includeAdult = true)
            }
            val all = jsSources + listOf(MangaDexSource())
            all.forEach { source ->
                val name = (source as? com.example.source.js.JsComicSource)?.name ?: source.id
                if (filter.isNotBlank() && !source.id.contains(filter, ignoreCase = true) &&
                    !name.contains(filter, ignoreCase = true)
                ) {
                    return@forEach
                }
                val started = System.currentTimeMillis()
                try {
                    if (source.id == "js_picacg") {
                        val loginResult = source.login(
                            LoginCredential(
                                username = "a1553846342",
                                password = "a1553846342"
                            )
                        )
                        Log.i("SmokeTest", "$name|login=${loginResult is SourceResult.Success}")
                    }
                    var books = emptyList<SearchBook>()
                    var second: SourceResult<List<SearchBook>>? = null
                    val searchTimeout = if (name.contains("ehentai", ignoreCase = true)) 50000L else 15000L
                    val first = if (keyword.isNotBlank()) {
                        withTimeoutOrNull(searchTimeout) { source.search(keyword) }
                    } else {
                        withTimeoutOrNull(searchTimeout) { source.search("海贼王") }
                    }
                    if (first is SourceResult.Success && first.data.isNotEmpty()) {
                        books = first.data
                    } else if (keyword.isBlank()) {
                        second = withTimeoutOrNull(searchTimeout) { source.search("one piece") }
                        if (second is SourceResult.Success) books = second.data
                    }
                    if (books.isEmpty()) {
                        val err1 = (first as? SourceResult.Error)?.exception?.message
                        val err2 = if (first is SourceResult.Success) "" else (second as? SourceResult.Error)?.exception?.message ?: ""
                        Log.i("SmokeTest", "$name|FAIL|search empty ($err1 / $err2)|${System.currentTimeMillis() - started}ms")
                        return@forEach
                    }
                    val chapters = when (val r = withTimeoutOrNull(20000) { source.getChapters(books.first().id) }) {
                        is SourceResult.Success -> r.data
                        is SourceResult.Error -> {
                            Log.i("SmokeTest", "$name|FAIL|chapters err: ${r.exception.message}|${System.currentTimeMillis() - started}ms")
                            return@forEach
                        }
                        else -> emptyList()
                    }
                    if (chapters.isEmpty()) {
                        Log.i("SmokeTest", "$name|FAIL|chapters empty|${System.currentTimeMillis() - started}ms")
                        return@forEach
                    }
                    val chapter = chapters.first()
                    Log.i("SmokeTest", "  first chapter id: ${chapter.id.take(120)}")
                    val imgTimeout = if (name.contains("hitomi", ignoreCase = true) || name.contains("ehentai", ignoreCase = true)) 60000L else 25000L
                    val urls = when (val r = withTimeoutOrNull(imgTimeout) { source.getChapterImages(chapter.id) }) {
                        is SourceResult.Success -> r.data
                        is SourceResult.Error -> {
                            Log.i("SmokeTest", "$name|FAIL|images err: ${r.exception.message}|${System.currentTimeMillis() - started}ms")
                            return@forEach
                        }
                        else -> emptyList()
                    }
                    if (urls.isEmpty()) {
                        Log.i("SmokeTest", "$name|FAIL|images empty|${System.currentTimeMillis() - started}ms")
                        return@forEach
                    }
                    var fetchTarget = urls.first()
                    val resolved = source.resolveChapterImage(fetchTarget)
                    if (resolved != null && resolved != fetchTarget) fetchTarget = resolved
                    val baseHeaders = source.getChapterImageHeaders(chapter.id, urls)[urls.first()] ?: emptyMap()
                    val resolvedHeaders = source.getResolvedHeaders(fetchTarget)
                    val headers = baseHeaders + resolvedHeaders
                    val ok = fetchAndDecode(client, fetchTarget, headers)
                    if (ok) {
                        Log.i("SmokeTest", "$name|OK|pages=${urls.size}|${System.currentTimeMillis() - started}ms")
                    } else {
                        Log.i("SmokeTest", "$name|FAIL|image fetch/decode|pages=${urls.size}|${System.currentTimeMillis() - started}ms")
                    }
                } catch (e: Exception) {
                    Log.i("SmokeTest", "$name|FAIL|${e.javaClass.simpleName}: ${e.message}|${System.currentTimeMillis() - started}ms")
                }
            }
            Log.i("SmokeTest", "=== smoke test done ===")
        }
    }

    private suspend fun fetchAndDecode(
        client: OkHttpClient,
        url: String,
        headers: Map<String, String>
    ): Boolean = withTimeoutOrNull(30000) {
        var lastErr: Exception? = null
        for (attempt in 1..3) {
            try {
                return@withTimeoutOrNull fetchOnce(client, url, headers)
            } catch (e: Exception) {
                lastErr = e
                Log.i("SmokeTest", "  fetch attempt $attempt failed: ${e.javaClass.simpleName}: ${e.message} url=${url.take(120)}")
                kotlinx.coroutines.delay(1000L * attempt)
            }
        }
        Log.i("SmokeTest", "  fetch all attempts failed: ${lastErr?.message}")
        false
    } ?: false

    private suspend fun fetchOnce(
        client: OkHttpClient,
        url: String,
        headers: Map<String, String>
    ): Boolean {
        if (url.startsWith("file:")) {
            val f = java.io.File(java.net.URI.create(url))
            if (!f.exists() || f.length() == 0L) return false
            val raw = f.readBytes()
            val bmp = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size)
            val ok = bmp != null && bmp.width > 0
            Log.i("SmokeTest", "  file fetch ${f.name}: ${raw.size} bytes, decode ok=$ok")
            return ok
        }
        val builder = Request.Builder().url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.5359.128 Mobile Safari/537.36"
            )
        headers.forEach { (k, v) -> builder.header(k, v) }
        if (headers.keys.none { it.equals("Accept", ignoreCase = true) }) {
            builder.header("Accept", "image/webp,image/jpeg,image/png,*/*;q=0.8")
        }
        val cookie = com.example.source.js.JsCookieJar.cookieHeader(getApplication(), url)
        if (cookie.isNotBlank()) builder.header("Cookie", cookie)
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                Log.i("SmokeTest", "  fetch ${url.take(90)} -> HTTP ${response.code}")
                return false
            }
            val raw = response.body?.bytes() ?: return false
            Log.i(
                "SmokeTest",
                "  fetch ${url.take(90)} -> HTTP 200, ${raw.size} bytes, type=${response.header("Content-Type")}"
            )
            var bytes = ImageBytes.normalizeImage(raw, response.header("Content-Encoding"))
            val magicAfter = bytes.take(12).joinToString(" ") { "%02X".format(it) }
            Log.i("SmokeTest", "  after normalize: ${bytes.size} bytes, magic=$magicAfter")
            if (ImageBytes.isAvif(bytes) && !ImageBytes.decodeOk(bytes)) {
                for (candidate in ImageBytes.webpVariants(url)) {
                    try {
                        val rb = Request.Builder().url(candidate)
                            .header(
                                "User-Agent",
                                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.5359.128 Mobile Safari/537.36"
                            )
                        client.newCall(rb.build()).execute().use { r2 ->
                            if (r2.isSuccessful) {
                                val b2 = r2.body?.bytes()
                                if (b2 != null) {
                                    val p2 = ImageBytes.normalizeImage(b2, r2.header("Content-Encoding"))
                                    if (!ImageBytes.isAvif(p2) && ImageBytes.decodeOk(p2)) {
                                        Log.i("SmokeTest", "  webp fallback OK: $candidate")
                                        bytes = p2
                                    }
                                }
                            }
                        }
                        if (!ImageBytes.isAvif(bytes) && ImageBytes.decodeOk(bytes)) break
                    } catch (e: Exception) {
                        // 尝试下一个候选
                    }
                }
            }
            bytes = if (MhttuImageDecryptor.isEncryptedHost(
                    try { java.net.URL(url).host } catch (e: Exception) { "" }
                )
            ) {
                MhttuImageDecryptor.decryptIfNeeded(bytes)
            } else {
                bytes
            }
            val transformed = com.example.source.js.JsImageProcessor.transform(url, bytes)
            if (transformed != null) {
                Log.i("SmokeTest", "  modifyImage applied: ${raw.size} -> ${transformed.size} bytes")
                bytes = transformed
            }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val ok = bmp != null && bmp.width > 0 && bmp.height > 0
            Log.i("SmokeTest", "  decode ok=$ok size=${bmp?.width}x${bmp?.height}")
            return ok
        }
    }

    fun clearComicState() {
        _comicBook.value = null
        _comicChapters.value = emptyList()
        _comicChaptersError.value = null
        _comicChapterImages.value = emptyList()
        _comicChapterHeaders.value = emptyMap()
        _comicChapterError.value = null
        _activeComicChapter.value = null
    }

    fun markWelcomeSeen() {
        prefs.hasSeenWelcome = true
        hasSeenWelcome.value = true
    }

    fun markSourceConfigured() {
        prefs.hasConfiguredSource = true
        hasConfiguredSource.value = true
    }

    fun markLocalBookImported() {
        prefs.hasImportedLocalBook = true
        hasImportedLocalBook.value = true
    }

    fun checkSourceLoginStatus() {
        val source = sourceManager.activeSource.value
        if (source != null) {
            viewModelScope.launch {
                _isCurrentSourceLoggedIn.value = source.isLoggedIn()
            }
        } else {
            _isCurrentSourceLoggedIn.value = false
        }
    }

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Empty)
    val uiState: StateFlow<LibraryUiState> = _uiState

    val downloadStates: StateFlow<Map<String, DownloadState>> = downloadManager.downloadStates
    val allDownloadTasks: Flow<List<DownloadTaskEntity>> = downloadManager.allTasksFlow
    val errorMessage = MutableStateFlow<String?>(null)

    fun search(keyword: String) {
        val source = sourceManager.activeSource.value ?: return
        viewModelScope.launch {
            _uiState.value = LibraryUiState.Searching
            errorMessage.value = null
            when (val result = source.search(keyword)) {
                is SourceResult.Success -> {
                    _uiState.value = LibraryUiState.SearchResults(result.data)
                    if (source.id == "mangadex" && result.data.isNotEmpty()) {
                        enrichMangaDexAuthors(result.data, source)
                    }
                }
                is SourceResult.Error -> {
                    val error = when (val e = result.exception) {
                        is SourceException.NetworkError -> {
                            if (e.message?.contains("Cloudflare") == true || e.message?.contains("DiamWall") == true) LibraryError.CloudflareBlocked
                            else if (e.message?.contains("Z-Library 当前节点不可用") == true) LibraryError.SourceUnavailable
                            else LibraryError.NetworkUnavailable
                        }
                        is SourceException.LoginRequired -> LibraryError.AuthenticationRequired
                        is SourceException.ParseError -> LibraryError.ParseFailed(e.message ?: "解析失败")
                        else -> LibraryError.Unknown(e.message ?: "未知错误", e)
                    }
                    _uiState.value = LibraryUiState.Error(error)
                    errorMessage.value = error.message ?: "发生错误"
                }
            }
        }
    }

    private suspend fun enrichMangaDexAuthors(books: List<SearchBook>, source: BookSource) {
        val semaphore = Semaphore(5)
        val enriched = books.map { book ->
            semaphore.withPermit {
                val detail = source.getDetail(book.id).getOrNull()
                if (detail != null) {
                    book.copy(
                        author = detail.author.takeIf { it.isNotBlank() && it != "MangaDex" } ?: book.author,
                        description = detail.description ?: book.description,
                        cover = book.cover ?: detail.cover
                    )
                } else {
                    book
                }
            }
        }
        withContext(kotlinx.coroutines.Dispatchers.Main) {
            if (enriched.isNotEmpty()) {
                _uiState.value = LibraryUiState.SearchResults(enriched)
            }
        }
    }

    fun selectSource(sourceId: String) {
        _aggregateMode.value = false
        viewModelScope.launch {
            sourceManager.setActiveSource(sourceId)
        }
    }

    fun startDownload(book: SearchBook) {
        val source = sourceManager.availableSources.value.firstOrNull { it.id == book.sourceId }
            ?: sourceManager.activeSource.value
            ?: return
        viewModelScope.launch {
            when (val result = source.getDownloadInfo(book.id)) {
                is SourceResult.Success -> {
                    val request = DownloadRequest(
                        bookId = book.id,
                        title = book.title,
                        author = book.author,
                        sourceId = book.sourceId,
                        downloadUrl = result.data.url,
                        format = result.data.format.ifBlank { book.format },
                        coverUrl = book.cover
                    )
                    downloadManager.enqueueDownload(request, result.data.referer, result.data.headers)
                }
                is SourceResult.Error -> {
                    errorMessage.value = result.exception.message ?: "获取下载信息失败"
                }
            }
        }
    }

    /**
     * WebView 会话下载入口：真实文件 URL 来自隐藏 WebView 的 DownloadListener，
     * Cookie 头来自该会话（含 __diamwall / 登录凭证）。
     */
    fun startWebViewDownload(book: SearchBook?, realUrl: String, cookieHeader: String) {
        if (book == null || realUrl.isBlank()) return
        val request = DownloadRequest(
            bookId = book.id,
            title = book.title,
            author = book.author,
            sourceId = book.sourceId,
            downloadUrl = realUrl,
            format = book.format.ifBlank { "epub" },
            coverUrl = book.cover
        )
        downloadManager.enqueueDownload(
            request,
            referer = "https://1lib.sk/",
            headers = mapOf("Cookie" to cookieHeader)
        )
    }

    fun pauseDownload(bookId: String) {
        downloadManager.pauseDownload(bookId)
    }

    fun resumeDownload(bookId: String) {
        downloadManager.resumeDownload(bookId)
    }

    fun cancelDownload(bookId: String) {
        downloadManager.cancelDownload(bookId)
    }
}
