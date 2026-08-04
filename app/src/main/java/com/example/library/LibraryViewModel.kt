package com.example.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BookRepository
import com.example.data.AppDatabase
import com.example.download.DownloadManager
import com.example.download.DownloadRequest
import com.example.download.DownloadState
import com.example.download.DownloadTaskEntity
import com.example.source.*
import com.example.source.zlibrary.ZLibrarySource
import com.example.source.storage.SharedPreferencesSourceStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            sourceManager.initialize()
                        sourceManager.registerSource(ZLibrarySource(application))
            
            sourceManager.activeSource.collect { source ->
                checkSourceLoginStatus()
            }
        }
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

    fun selectSource(sourceId: String) {
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
