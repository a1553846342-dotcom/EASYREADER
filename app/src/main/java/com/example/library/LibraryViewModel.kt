package com.example.library

import android.app.Application
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

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

    private val _comicDownloading = MutableStateFlow<Set<String>>(emptySet())
    val comicDownloading: StateFlow<Set<String>> = _comicDownloading.asStateFlow()

    private val _comicDownloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val comicDownloadProgress: StateFlow<Map<String, Float>> = _comicDownloadProgress.asStateFlow()

    private val _comicPaused = MutableStateFlow<Set<String>>(emptySet())
    val comicPaused: StateFlow<Set<String>> = _comicPaused.asStateFlow()

    private val comicDownloadJobs = mutableMapOf<String, Job>()
    private val comicDownloadDirs = mutableMapOf<String, java.io.File>()
    private val pendingPause = mutableSetOf<String>()

    private val _comicMessage = MutableStateFlow<String?>(null)
    val comicMessage: StateFlow<String?> = _comicMessage.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    init {
        _searchHistory.value = prefs.searchHistory
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

    fun downloadComicChapter(book: SearchBook, chapter: ComicChapter) {
        if (chapter.external) {
            _comicMessage.value = "站外链接章节暂不支持下载"
            return
        }
        val source = sourceManager.availableSources.value
            .firstOrNull { it.id == book.sourceId } as? ComicSource
        if (source == null) {
            _comicMessage.value = "当前书源不支持漫画"
            return
        }
        // 正在下载且未暂停时忽略重复点击；已暂停允许重新继续
        if (chapter.id in _comicDownloading.value && chapter.id !in _comicPaused.value) return
        val app = getApplication<android.app.Application>()
        val job = viewModelScope.launch {
            var keepPaused = false
            var completed = false
            _comicDownloading.value = _comicDownloading.value + chapter.id
            _comicPaused.value = _comicPaused.value - chapter.id
            if (chapter.id !in _comicDownloadProgress.value) {
                _comicDownloadProgress.value = _comicDownloadProgress.value + (chapter.id to 0f)
            }
            try {
                val images = when (val result = source.getChapterImages(chapter.id)) {
                    is SourceResult.Success -> result.data
                    is SourceResult.Error -> {
                        _comicMessage.value = result.exception.message ?: "获取图片列表失败"
                        return@launch
                    }
                }
                val imageHeaders = source.getChapterImageHeaders(chapter.id, images)
                val dir = comicDownloadDirs[chapter.id]
                    ?: java.io.File(app.filesDir, "comics_${System.currentTimeMillis()}")
                        .apply { mkdirs() }
                        .also { comicDownloadDirs[chapter.id] = it }
                val currentProgress = _comicDownloadProgress.value[chapter.id] ?: 0f
                val startIndex = (currentProgress * images.size).toInt()
                    .coerceIn(0, (images.size - 1).coerceAtLeast(0))
                val importResult = ComicLocalImporter.importChapter(
                    context = app,
                    bookDao = database.bookDao(),
                    book = book,
                    chapter = chapter,
                    imageUrls = images,
                    headers = imageHeaders,
                    referer = if (book.sourceId == "mangadex") "https://mangadex.live/" else null,
                    targetDir = dir,
                    startIndex = startIndex,
                    onProgress = { p ->
                        _comicDownloadProgress.value = _comicDownloadProgress.value + (chapter.id to p)
                    }
                )
                importResult.onSuccess {
                    completed = true
                    _comicMessage.value = "已下载到书架：《${it.title}》"
                    markLocalBookImported()
                }.onFailure {
                    _comicMessage.value = it.message ?: "章节下载失败"
                }
            } catch (e: CancellationException) {
                keepPaused = chapter.id in pendingPause
                pendingPause.remove(chapter.id)
                if (keepPaused) {
                    _comicPaused.value = _comicPaused.value + chapter.id
                }
                throw e
            } finally {
                comicDownloadJobs.remove(chapter.id)
                pendingPause.remove(chapter.id)
                if (keepPaused) return@launch
                _comicDownloading.value = _comicDownloading.value - chapter.id
                _comicPaused.value = _comicPaused.value - chapter.id
                _comicDownloadProgress.value = _comicDownloadProgress.value - chapter.id
                val dir = comicDownloadDirs.remove(chapter.id)
                if (!completed && dir != null) dir.deleteRecursively()
            }
        }
        comicDownloadJobs[chapter.id] = job
    }

    fun pauseComicChapter(chapterId: String) {
        if (chapterId !in _comicDownloading.value) return
        if (chapterId in _comicPaused.value) return
        pendingPause.add(chapterId)
        comicDownloadJobs[chapterId]?.cancel()
    }

    fun cancelComicChapter(chapterId: String) {
        pendingPause.remove(chapterId)
        comicDownloadJobs.remove(chapterId)?.cancel()
        _comicDownloading.value = _comicDownloading.value - chapterId
        _comicPaused.value = _comicPaused.value - chapterId
        _comicDownloadProgress.value = _comicDownloadProgress.value - chapterId
    }

    fun clearComicMessage() {
        _comicMessage.value = null
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
