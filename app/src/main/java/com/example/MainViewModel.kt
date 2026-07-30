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
    val repository = BookRepository(application, database.bookDao())

    private val _autoNightMode = MutableStateFlow(prefs.autoNightMode)
    val autoNightMode: StateFlow<Boolean> = _autoNightMode.asStateFlow()

    private val _blueLightFilter = MutableStateFlow(prefs.blueLightFilter)
    val blueLightFilter: StateFlow<Boolean> = _blueLightFilter.asStateFlow()

    private val _blueLightAlpha = MutableStateFlow(prefs.blueLightAlpha)
    val blueLightAlpha: StateFlow<Float> = _blueLightAlpha.asStateFlow()

    private val _colorPrimaryIndex = MutableStateFlow(prefs.colorPrimaryIndex)
    val colorPrimaryIndex: StateFlow<Int> = _colorPrimaryIndex.asStateFlow()

    private val _colorSecondaryIndex = MutableStateFlow(prefs.colorSecondaryIndex)
    val colorSecondaryIndex: StateFlow<Int> = _colorSecondaryIndex.asStateFlow()

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

    fun updateColorTheme(primary: Int, secondary: Int) {
        prefs.colorPrimaryIndex = primary
        prefs.colorSecondaryIndex = secondary
        _colorPrimaryIndex.value = primary
        _colorSecondaryIndex.value = secondary
    }

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.checkAndSeedDefaultBooks()
        }
    }

    val allBooks: StateFlow<List<Book>> = repository.allBooks.stateIn(
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
    private var lastLoadedBookId: Int? = null
    private var lastLoadedChapterIndex: Int? = null

    private fun loadActiveChaptersContent(bookId: Int, currentIdx: Int) {
        viewModelScope.launch {
            try {
                if (cachedMetadataList.isEmpty()) {
                    _chapters.value = emptyList()
                    return@launch
                }
                
                val targetOrders = listOf(currentIdx - 1, currentIdx, currentIdx + 1)
                    .filter { it >= 0 && it < cachedMetadataList.size }
                
                val activeChapters = repository.getChaptersByOrders(bookId, targetOrders)
                val activeMap = activeChapters.associateBy { it.chapterOrder }
                
                val merged = cachedMetadataList.map { chapter ->
                    activeMap[chapter.chapterOrder] ?: chapter
                }
                
                _chapters.value = merged
                android.util.Log.d("BookImport", "[MainViewModel] Lazy loaded content for chapters: ${activeChapters.map { it.chapterOrder }}")
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
                    repository.getChaptersForBook(book.id).collect {
                        _chapters.value = it
                    }
                } else {
                    // For novels, use lazy loading
                    val metadata = repository.getChaptersMetadataList(book.id)
                    cachedMetadataList = metadata
                    lastLoadedBookId = book.id
                    lastLoadedChapterIndex = book.currentChapterIndex
                    loadActiveChaptersContent(book.id, book.currentChapterIndex)
                }
            } catch (t: Throwable) {
                android.util.Log.e("BookImport", "[MainViewModel] Error selecting book ${book.title}", t)
            }
        }
        viewModelScope.launch {
            repository.getBookmarksForBook(book.id).collect {
                _bookmarks.value = it
            }
        }
        viewModelScope.launch {
            repository.getHighlightsForBook(book.id).collect {
                _highlights.value = it
            }
        }
    }

    fun moveBookToCategory(book: Book, newCategory: String) {
        viewModelScope.launch {
            val updated = book.copy(category = newCategory)
            database.bookDao().updateBook(updated)
        }
    }

    fun importBook(uri: Uri, fileName: String, category: String = "未分类") {
        viewModelScope.launch {
            try {
                android.util.Log.d("BookImport", "[MainViewModel] Starting import: $fileName, category: $category")
                val result = repository.importBookFromUri(uri, fileName)
                result.onSuccess { book ->
                    val finalBook = if (category != "全部" && category != "未分类") {
                        val updated = book.copy(category = category)
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
            repository.updateBookProgress(bookId, chapterIndex, scrollOffset, isFinished)
            
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

    fun addBookmark(bookId: Int, chapterIndex: Int, scrollOffset: Int, title: String, snippet: String) {
        viewModelScope.launch {
            val existing = _bookmarks.value.find { (it.bookId == bookId || it.bookId == 0) && it.chapterIndex == chapterIndex }
            if (existing == null) {
                repository.addBookmark(
                    Bookmark(
                        bookId = bookId,
                        chapterIndex = chapterIndex,
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
                repository.addBookmark(
                    Bookmark(
                        bookId = bookId,
                        chapterIndex = chapterIndex,
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
            repository.addHighlight(
                Highlight(
                    bookId = bookId,
                    chapterIndex = chapterIndex,
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

    fun recordTime(seconds: Long) {
        if (seconds <= 0) return
        prefs.totalReadTimeSeconds += seconds
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val currentDaily = prefs.getDailyReadTime(todayStr)
        prefs.setDailyReadTime(todayStr, currentDaily + seconds)

        // Also record to reading_records database table
        val currentBook = _selectedBook.value
        if (currentBook != null) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val record = database.bookDao().getReadingRecordForBookAndDate(currentBook.id, todayStr)
                    if (record != null) {
                        database.bookDao().insertReadingRecord(
                            record.copy(durationSeconds = record.durationSeconds + seconds)
                        )
                    } else {
                        database.bookDao().insertReadingRecord(
                            ReadingRecord(
                                bookId = currentBook.id,
                                bookTitle = currentBook.title,
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
                matchedChapters.forEach { chapter ->
                    val pos = chapter.content.indexOf(query, ignoreCase = true)
                    if (pos >= 0) {
                        val start = (pos - 15).coerceAtLeast(0)
                        val end = (pos + query.length + 25).coerceAtMost(chapter.content.length)
                        val snippet = "..." + chapter.content.substring(start, end) + "..."
                        results.add(SearchResultItem(chapter.chapterOrder, chapter.title, snippet))
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
