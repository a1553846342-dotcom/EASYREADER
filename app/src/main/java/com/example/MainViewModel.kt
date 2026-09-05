package com.example

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    val prefs = PreferencesManager(application)
    val backupManager = BackupManager(application, prefs)
    val ttsManager = TtsManager(application)
    val downloadManager = com.example.download.DownloadManager(application)
    val repository = BookRepository(application, database.bookDao())

    /* ── 隐私模式（第七轮第 6.4/6.5 条） ── */
    val privacy = PrivacyManager(application)

    /** 隐私模式开关状态（重启后保持——受保护分类仍需 PIN 验证） */
    private val _privacyModeEnabled = MutableStateFlow(privacy.isEnabled())
    val privacyModeEnabled: StateFlow<Boolean> = _privacyModeEnabled.asStateFlow()

    /** 受保护分类名集合（数据源 = categories.isProtected，随 DB Flow 自动更新） */
    val protectedCategoryNames: StateFlow<Set<String>> = allCategoriesProtected()

    /** 本次进程内已通过 PIN 验证解锁的分类 id（重启 App 即失效，需重新验证） */
    private val _unlockedCategoryIds = MutableStateFlow<Set<Int>>(emptySet())
    val unlockedCategoryIds: StateFlow<Set<Int>> = _unlockedCategoryIds.asStateFlow()

    private fun allCategoriesProtected(): StateFlow<Set<String>> = repository.allCategories
        .map { cats -> cats.filter { it.isProtected }.map { it.name }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _autoNightMode = MutableStateFlow(prefs.autoNightMode)
    val autoNightMode: StateFlow<Boolean> = _autoNightMode.asStateFlow()

    private val _blueLightFilter = MutableStateFlow(prefs.blueLightFilter)
    val blueLightFilter: StateFlow<Boolean> = _blueLightFilter.asStateFlow()

    private val _blueLightAlpha = MutableStateFlow(prefs.blueLightAlpha)
    val blueLightAlpha: StateFlow<Float> = _blueLightAlpha.asStateFlow()

    private val _screenOrientationLock = MutableStateFlow(prefs.screenOrientationLock)
    val screenOrientationLock: StateFlow<Int> = _screenOrientationLock.asStateFlow()

    private val _colorPrimaryIndex = MutableStateFlow(prefs.colorPrimaryIndex)
    val colorPrimaryIndex: StateFlow<Int> = _colorPrimaryIndex.asStateFlow()

    private val _colorSecondaryIndex = MutableStateFlow(prefs.colorSecondaryIndex)

    /** 封面有效性缓存：避免每次书架数据变更都重新 file.exists() 全部书籍。 */
    private val coverValidCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    val colorSecondaryIndex: StateFlow<Int> = _colorSecondaryIndex.asStateFlow()

    private val _renderQuality = MutableStateFlow(prefs.renderQuality)
    val renderQuality: StateFlow<Int> = _renderQuality.asStateFlow()

    fun updateRenderQuality(quality: Int) {
        prefs.renderQuality = quality
        _renderQuality.value = quality
    }

    fun updateAutoNightMode(enabled: Boolean) {
        prefs.autoNightMode = enabled
        _autoNightMode.value = enabled
    }

    fun updateBlueLightFilter(enabled: Boolean) {
        prefs.blueLightFilter = enabled
        _blueLightFilter.value = enabled
    }

    fun updateBlueLightAlpha(alpha: Float) {
        prefs.blueLightAlpha = alpha
        _blueLightAlpha.value = alpha
    }

    fun updateScreenOrientationLock(mode: Int) {
        prefs.screenOrientationLock = mode
        _screenOrientationLock.value = mode
    }

    fun updateColorTheme(primary: Int, secondary: Int) {
        prefs.colorPrimaryIndex = primary
        prefs.colorSecondaryIndex = secondary
        _colorPrimaryIndex.value = primary
        _colorSecondaryIndex.value = secondary
    }

    // 注意：init 块必须位于其访问的全部属性声明之后——Kotlin 按声明顺序执行
    // 初始化，launch(Dispatchers.IO) 的协程可能在构造函数完成前并发执行，
    // 若 _streakDays 等字段尚未初始化则在该协程里读到 null（高负载下实测
    // 触发 FATAL NPE，MainViewModel$3），故 init 移至文件后部声明。
    private val _totalReadTimeSeconds = MutableStateFlow(prefs.totalReadTimeSeconds)
    val totalReadTimeSeconds: StateFlow<Long> = _totalReadTimeSeconds.asStateFlow()

    /**
     * 今日已阅读秒数（第七轮第 4 条修复）：
     * 旧版书架统计卡片把全生命周期累计值 [totalReadTimeSeconds] 标成"今日已阅读"，
     * 数字只会单调增长、永不清零——分钟数"不对"的直接根因。
     * 此流读取 prefs 的 daily_read_time_<今天> 键，随每次 [recordTime] 实时累加。
     */
    private val _todayReadSeconds = MutableStateFlow(
        prefs.getDailyReadTime(
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        )
    )
    val todayReadSeconds: StateFlow<Long> = _todayReadSeconds.asStateFlow()

    private val _streakDays = MutableStateFlow(0)
    val streakDays: StateFlow<Int> = _streakDays.asStateFlow()

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.checkAndSeedDefaultBooks()
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // 第七轮第 6.1 条：默认分类幂等种子（迁移兜底；书架始终至少有一个分类）
            repository.ensureDefaultCategory()
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // 存量超大章节自动拆分（与本地导入书一致，修复旧下载书的打开卡顿/闪退）
            repository.splitOversizedChaptersInLibrary()
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val streak = prefs.calculateStreak()
            _streakDays.value = streak
        }
    }

    val allBooks: StateFlow<List<Book>> = repository.allBooks
        .map { books ->
            books.forEach { book ->
                if (!book.coverUri.isNullOrEmpty()) {
                    val key = book.coverUri
                    book.isCoverValid = coverValidCache.getOrPut(key) {
                        val path = if (key.startsWith("file://")) key.substring(7) else key
                        java.io.File(path).exists()
                    }
                } else {
                    book.isCoverValid = false
                }
            }
            books
        }
        .flowOn(kotlinx.coroutines.Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allCategories: StateFlow<List<CategoryEntity>> = repository.allCategories.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allReadingRecords: StateFlow<List<ReadingRecord>> = repository.allReadingRecords.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    val allReadingSessions: StateFlow<List<ReadingSession>> = repository.allReadingSessions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedBook = MutableStateFlow<Book?>(null)
    val selectedBook: StateFlow<Book?> = _selectedBook

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks

    private val _highlights = MutableStateFlow<List<Highlight>>(emptyList())
    val highlights: StateFlow<List<Highlight>> = _highlights

    private val _searchResults = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val searchResults: StateFlow<List<SearchResultItem>> = _searchResults

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private val _importStatusMessage = MutableStateFlow<String?>(null)
    val importStatusMessage: StateFlow<String?> = _importStatusMessage

    private var cachedMetadataList = emptyList<Chapter>()
    private var chapterMapping: LogicalChapterBook? = null
    private var lastLoadedBookId: Int? = null
    private var lastLoadedChapterIndex: Int? = null

    private fun loadActiveChaptersContent(bookId: Int, currentLogicalIdx: Int) {
        viewModelScope.launch {
            try {
                val mapping = chapterMapping
                if (cachedMetadataList.isEmpty() || mapping == null) {
                    _chapters.value = emptyList()
                    return@launch
                }

                val targetLogical = listOf(currentLogicalIdx - 1, currentLogicalIdx, currentLogicalIdx + 1)
                    .filter { it >= 0 && it < cachedMetadataList.size }

                val targetOrders = targetLogical
                    .flatMap { mapping.logicalToPhysicalOrders[it].asIterable() }
                    .distinct()

                val activeParts = repository.getChaptersByOrders(bookId, targetOrders).associateBy { it.chapterOrder }

                val merged = cachedMetadataList.mapIndexed { logicalIdx, chapter ->
                    if (logicalIdx !in targetLogical) {
                        chapter
                    } else {
                        val parts = mapping.logicalToPhysicalOrders[logicalIdx]
                            .map { activeParts[it] }
                            .filterNotNull()
                        if (parts.isEmpty()) {
                            chapter
                        } else {
                            chapter.copy(content = parts.joinToString(separator = "") { it.content })
                        }
                    }
                }

                _chapters.value = merged
                android.util.Log.d("BookImport", "[MainViewModel] Lazy loaded content for logical chapters: $targetLogical, physical: $targetOrders")
            } catch (t: Throwable) {
                android.util.Log.e("BookImport", "[MainViewModel] Error lazy loading active chapters content", t)
            }
        }
    }

    fun selectBook(book: Book) {
        _selectedBook.value = book
        viewModelScope.launch {
            try {
                android.util.Log.d("BookImport", "[MainViewModel] Selecting book: ${book.title}, isComic: ${book.isComic}")
                if (book.isComic) {
                    // For comics, load all chapters directly since their content is just image file paths (very small)
                    chapterMapping = null
                    collectAnnotations(book.id)
                    repository.getChaptersForBook(book.id).collect {
                        _chapters.value = it
                    }
                } else {
                    // For novels, use lazy loading
                    val metadata = repository.getChaptersMetadataList(book.id)
                    val logical = ChapterMerger.buildLogicalChapters(metadata)
                    cachedMetadataList = logical.chapters
                    chapterMapping = logical
                    lastLoadedBookId = book.id

                    val physicalStart = book.currentChapterIndex.coerceAtLeast(0)
                    val logicalStart = logical.logicalIndexOf(physicalStart)
                    val logicalOffset = logical.logicalOffsetOf(physicalStart, book.scrollOffset)
                    lastLoadedChapterIndex = logicalStart
                    _selectedBook.value = book.copy(
                        currentChapterIndex = logicalStart,
                        scrollOffset = logicalOffset
                    )
                    collectAnnotations(book.id)
                    loadActiveChaptersContent(book.id, logicalStart)
                }
            } catch (t: Throwable) {
                android.util.Log.e("BookImport", "[MainViewModel] Error selecting book ${book.title}", t)
            }
        }
    }

    private fun collectAnnotations(bookId: Int) {
        viewModelScope.launch {
            repository.getBookmarksForBook(bookId).collect { list ->
                val mapping = chapterMapping
                _bookmarks.value = if (mapping == null) {
                    list
                } else {
                    list.map { bm ->
                        val li = mapping.logicalIndexOf(bm.chapterIndex)
                        val off = mapping.logicalOffsetOf(bm.chapterIndex, bm.scrollOffset)
                        if (li == bm.chapterIndex && off == bm.scrollOffset) {
                            val cleanTitle = ChapterMerger.cleanSplitTitle(bm.title)
                            if (cleanTitle == bm.title) bm else bm.copy(title = cleanTitle)
                        } else {
                            bm.copy(chapterIndex = li, scrollOffset = off, title = ChapterMerger.cleanSplitTitle(bm.title))
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            repository.getHighlightsForBook(bookId).collect { list ->
                val mapping = chapterMapping
                _highlights.value = if (mapping == null) {
                    list
                } else {
                    list.map { h ->
                        val li = mapping.logicalIndexOf(h.chapterIndex)
                        if (li == h.chapterIndex) h else h.copy(chapterIndex = li)
                    }
                }
            }
        }
    }

    fun moveBookToCategory(book: Book, newCategory: String) {
        viewModelScope.launch {
            val updated = book.copy(category = newCategory)
            database.bookDao().updateBook(updated)
        }
    }

    fun importBook(uri: Uri, fileName: String, category: String = DEFAULT_CATEGORY) {
        viewModelScope.launch {
            try {
                android.util.Log.d("BookImport", "[MainViewModel] Starting import: $fileName, category: $category")
                val result = repository.importBookFromUri(uri, fileName)
                result.onSuccess { book ->
                    // 第七轮第 6.1/6.2 条：书籍单一归属真实分类；旧聚合词归一为"默认"
                    val normalizedCategory = if (category == "全部" || category == "未分类") DEFAULT_CATEGORY else category
                    val finalBook = if (normalizedCategory != book.category) {
                        val updated = book.copy(category = normalizedCategory)
                        database.bookDao().updateBook(updated)
                        updated
                    } else {
                        book
                    }
                    android.util.Log.d("BookImport", "[MainViewModel] Import success: ${finalBook.title}")
                    _importStatusMessage.value = "《${finalBook.title}》 导入成功"
                }.onFailure {
                    android.util.Log.e("BookImport", "[MainViewModel] Import failure", it)
                    _importStatusMessage.value = "导入失败: ${it.localizedMessage ?: "未知错误"}"
                }
            } catch (t: Throwable) {
                android.util.Log.e("BookImport", "[MainViewModel] Uncaught exception in import coroutine", t)
                _importStatusMessage.value = "导入出错: ${t.localizedMessage ?: "发生未知异常"}"
            }
        }
    }

    fun clearImportMessage() {
        _importStatusMessage.value = null
    }

    fun updateProgress(bookId: Int, chapterIndex: Int, scrollOffset: Int, isFinished: Boolean) {
        viewModelScope.launch {
            // Reader uses logical (merged) chapter indexes; persist the first physical part so
            // restoring the book maps back to exactly the same position.
            val physicalIndex = chapterMapping?.physicalIndexFor(chapterIndex) ?: chapterIndex
            repository.updateBookProgress(bookId, physicalIndex, scrollOffset, isFinished)

            // For lazy loaded novels, load contents of new active window if index changed
            if (lastLoadedBookId == bookId && lastLoadedChapterIndex != chapterIndex) {
                lastLoadedChapterIndex = chapterIndex
                _selectedBook.value?.let { currentBook ->
                    if (currentBook.id == bookId && !currentBook.isComic) {
                        _selectedBook.value = currentBook.copy(
                            currentChapterIndex = chapterIndex,
                            scrollOffset = scrollOffset,
                            isFinished = isFinished
                        )
                        loadActiveChaptersContent(bookId, chapterIndex)
                    }
                }
            }
        }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch {
            repository.deleteBook(book)
        }
    }

    fun deleteReadingRecord(id: Int) {
        viewModelScope.launch {
            repository.deleteReadingRecord(id)
            getApplication<Application>()
                .getSharedPreferences("record_cover_cache", android.content.Context.MODE_PRIVATE)
                .edit()
                .remove(id.toString())
                .apply()
        }
    }

    /** 记录一次完整阅读会话（阅读器可见且前台期间），供日历时段/高峰时段使用。
     *  第七轮第 6.5 条：无痕浏览（受保护分类内阅读）不写入任何统计。 */
    fun addReadingSession(session: ReadingSession) {
        if (session.durationSeconds <= 0) return
        if (isIncognitoReading()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                repository.addReadingSession(session)
            }.onFailure {
                android.util.Log.e("MainViewModel", "Error saving reading session", it)
            }
        }
    }

    fun addBookmark(bookId: Int, chapterIndex: Int, scrollOffset: Int, title: String, snippet: String) {
        viewModelScope.launch {
            val existing = _bookmarks.value.find { (it.bookId == bookId || it.bookId == 0) && it.chapterIndex == chapterIndex }
            if (existing == null) {
                val physicalIndex = chapterMapping?.physicalIndexFor(chapterIndex) ?: chapterIndex
                repository.addBookmark(
                    Bookmark(
                        bookId = bookId,
                        chapterIndex = physicalIndex,
                        scrollOffset = scrollOffset,
                        title = title,
                        snippet = snippet
                    )
                )
            }
        }
    }

    fun toggleBookmark(bookId: Int, chapterIndex: Int, scrollOffset: Int, title: String, snippet: String) {
        viewModelScope.launch {
            val existing = _bookmarks.value.find { (it.bookId == bookId || it.bookId == 0) && it.chapterIndex == chapterIndex }
            if (existing != null) {
                repository.deleteBookmark(existing.id)
            } else {
                val physicalIndex = chapterMapping?.physicalIndexFor(chapterIndex) ?: chapterIndex
                repository.addBookmark(
                    Bookmark(
                        bookId = bookId,
                        chapterIndex = physicalIndex,
                        scrollOffset = scrollOffset,
                        title = title,
                        snippet = snippet
                    )
                )
            }
        }
    }

    fun deleteBookmark(id: Int) {
        viewModelScope.launch {
            repository.deleteBookmark(id)
        }
    }

    fun addHighlight(bookId: Int, chapterIndex: Int, selectedText: String, note: String, colorHex: String) {
        viewModelScope.launch {
            val physicalIndex = chapterMapping?.physicalIndexFor(chapterIndex) ?: chapterIndex
            repository.addHighlight(
                Highlight(
                    bookId = bookId,
                    chapterIndex = physicalIndex,
                    selectedText = selectedText,
                    note = note,
                    colorHex = colorHex
                )
            )
        }
    }

    fun deleteHighlight(id: Int) {
        viewModelScope.launch {
            repository.deleteHighlight(id)
        }
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            repository.addCategory(name)
        }
    }

    /**
     * 第七轮第 6.1 条：删除分类。"默认"分类不可删除。
     * @return true = 已删除；false = 被拒绝（默认分类不可删除）
     */
    fun deleteCategory(category: com.example.data.CategoryEntity, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = repository.deleteCategory(category)
            onResult(ok)
        }
    }

    /* ── 隐私模式操作（第七轮第 6.3/6.4/6.5 条） ── */

    /** 首次开启：设置 6 位 PIN 并启用。返回 false = PIN 非法。 */
    fun enablePrivacyMode(pin: String): Boolean {
        val ok = privacy.enableWithPin(pin)
        if (ok) _privacyModeEnabled.value = true
        return ok
    }

    /** 关闭隐私模式（先验证 PIN）。返回 false = PIN 错误。 */
    fun disablePrivacyMode(pin: String): Boolean {
        val ok = privacy.disable(pin)
        if (ok) {
            _privacyModeEnabled.value = false
            _unlockedCategoryIds.value = emptySet()
        }
        return ok
    }

    fun verifyPrivacyPin(pin: String): Boolean = privacy.verifyPin(pin)

    /** 修改 PIN（先验证旧 PIN） */
    fun changePrivacyPin(oldPin: String, newPin: String): Boolean = privacy.changePin(oldPin, newPin)

    /** 切换某分类的密码保护标记（仅在隐私模式开启时允许——6.4 总开关约束） */
    fun setCategoryProtected(categoryId: Int, isProtected: Boolean) {
        if (!_privacyModeEnabled.value) return
        viewModelScope.launch {
            repository.setCategoryProtected(categoryId, isProtected)
            if (!isProtected) {
                // 取消保护时一并收起解锁态
                _unlockedCategoryIds.value = _unlockedCategoryIds.value - categoryId
            }
        }
    }

    /** 进入受保护分类：验证 PIN，成功则本次进程内解锁该分类 */
    fun unlockCategory(categoryId: Int, pin: String): Boolean {
        if (!_privacyModeEnabled.value) return false
        if (!privacy.verifyPin(pin)) return false
        _unlockedCategoryIds.value = _unlockedCategoryIds.value + categoryId
        return true
    }

    /**
     * 无痕浏览判定（6.5 + 第九轮扩展）：
     * - 全局无痕开关开启 → 一切阅读不计统计；
     * - 否则：隐私模式开启 且 当前书所在分类受保护 → 不计统计。
     * 两种口径都只作用于统计/会话写入，阅读进度保存链路不受影响。
     */
    private fun isIncognitoReading(): Boolean {
        if (_incognitoBrowsingEnabled.value) return true
        if (!_privacyModeEnabled.value) return false
        val book = _selectedBook.value ?: return false
        return protectedCategoryNames.value.contains(book.category)
    }

    /* ── 第九轮：全局无痕浏览开关 ── */

    private val _incognitoBrowsingEnabled = MutableStateFlow(prefs.incognitoBrowsingEnabled)
    val incognitoBrowsingEnabled: StateFlow<Boolean> = _incognitoBrowsingEnabled.asStateFlow()

    fun setIncognitoBrowsing(enabled: Boolean) {
        _incognitoBrowsingEnabled.value = enabled
        prefs.incognitoBrowsingEnabled = enabled
    }

    fun recordTime(seconds: Long, title: String? = null) {
        if (seconds <= 0) return
        // 第七轮第 6.5 条：无痕浏览——受保护分类内的阅读时长不计入任何统计源
        // （阅读进度走独立的进度保存链路，不受此门控影响）
        if (isIncognitoReading()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            prefs.totalReadTimeSeconds += seconds
            val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val currentDaily = prefs.getDailyReadTime(todayStr)
            prefs.setDailyReadTime(todayStr, currentDaily + seconds)

            _totalReadTimeSeconds.value = prefs.totalReadTimeSeconds
            // 今日数据源实时更新（跨天时 todayStr 已是新一天，累加落在新键上）
            _todayReadSeconds.value = currentDaily + seconds
            val newStreak = prefs.calculateStreak()
            _streakDays.value = newStreak
        }

        // Also record to reading_records database table
        val currentBook = _selectedBook.value
        val recordTitle = currentBook?.title ?: title
        if (recordTitle != null) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                    // 本地书按 bookId 聚合；在线阅读/在线漫画没有本地 bookId，按书名聚合，
                    // 避免同一本书在同一天反复插入多条记录导致周几阅读记录重复显示。
                    val record = if (currentBook != null) {
                        database.bookDao().getReadingRecordForBookAndDate(currentBook.id, todayStr)
                    } else {
                        database.bookDao().getReadingRecordForTitleAndDate(recordTitle, todayStr)
                    }
                    if (record != null) {
                        database.bookDao().insertReadingRecord(
                            record.copy(durationSeconds = record.durationSeconds + seconds)
                        )
                    } else {
                        database.bookDao().insertReadingRecord(
                            ReadingRecord(
                                bookId = currentBook?.id,
                                bookTitle = recordTitle,
                                dateStr = todayStr,
                                durationSeconds = seconds
                            )
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainViewModel", "Error saving reading record to DB", e)
                }
            }
        }
    }

    fun searchFullText(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        _isSearching.value = true
        val bookId = _selectedBook.value?.id ?: run {
            _isSearching.value = false
            return
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Search directly in DB to avoid loading the whole book into memory
                val matchedChapters = database.bookDao().searchChapters(bookId, query)
                val results = mutableListOf<SearchResultItem>()
                val mapping = chapterMapping
                matchedChapters.forEach { chapter ->
                    val pos = chapter.content.indexOf(query, ignoreCase = true)
                    if (pos >= 0) {
                        val start = (pos - 15).coerceAtLeast(0)
                        val end = (pos + query.length + 25).coerceAtMost(chapter.content.length)
                        val snippet = "..." + chapter.content.substring(start, end) + "..."
                        val logicalIndex = mapping?.logicalIndexOf(chapter.chapterOrder) ?: chapter.chapterOrder
                        val logicalTitle = mapping?.chapters?.getOrNull(logicalIndex)?.title ?: chapter.title
                        results.add(SearchResultItem(logicalIndex, logicalTitle, snippet))
                    }
                }
                _searchResults.value = results
            } catch (t: Throwable) {
                android.util.Log.e("BookImport", "Error searching full text", t)
            } finally {
                _isSearching.value = false
            }
        }
    }
}
