package com.example.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.platform.testTag
import com.example.ui.components.AppIconButton
import com.example.ui.components.ChasingDots
import androidx.compose.material3.*
import androidx.compose.runtime.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import coil.ImageLoader
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import android.webkit.CookieManager
import com.example.download.DownloadState
import com.example.source.SearchBook
import com.example.source.BookSource
import com.example.source.ComicSource
import com.example.ui.theme.MintPrimary
import com.example.ui.theme.MintSecondary
import com.example.ui.theme.MintPrimary
import com.example.ui.theme.MintSecondary
import com.example.ui.source.ZLibraryLoginDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onBookImported: () -> Unit,
    onOpenSourceManagement: () -> Unit = {},
    onImportLocalBook: () -> Unit = {},
    onOpenComic: (SearchBook) -> Unit = {},
    extraBottomPadding: Dp = 0.dp
) {
    val currentSource by viewModel.currentSource.collectAsState()
    val aggregateMode by viewModel.aggregateMode.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val availableSources by viewModel.availableSources.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val searchResults = (uiState as? LibraryUiState.SearchResults)?.results ?: emptyList()
    val isSearching = uiState is LibraryUiState.Searching
    val errorMessage by viewModel.errorMessage.collectAsState()
    val downloadStatesState = viewModel.downloadStates.collectAsState()
    val downloadStates by downloadStatesState
    val comicDownloading by viewModel.comicDownloading.collectAsState()
    val comicDownloadProgress by viewModel.comicDownloadProgress.collectAsState()
    val comicPaused by viewModel.comicPaused.collectAsState()
    val comicBook by viewModel.comicBook.collectAsState()
    val comicChapters by viewModel.comicChapters.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    // 封面加载器：ZLibrary 走专用 DoH/会话 Cookie 加载器，其余（MangaDex/JS 源/聚合）走通用加载器
    val imageLoader = remember(aggregateMode, currentSource?.id) {
        if (aggregateMode || currentSource?.id != "zlibrary") {
            GenericCoverLoader.get(context)
        } else {
            ZLibraryCoverLoader.get(context)
        }
    }

    // Z-Library 走隐藏 WebView 会话（原生书库），其余书源走 OkHttp。
    val isZlibSource = currentSource?.id == "zlibrary"

    var nativeBooks by remember { mutableStateOf<List<SearchBook>>(emptyList()) }
    var nativeStatus by remember { mutableStateOf("") }
    var nativeSearching by remember { mutableStateOf(false) }
    var activeDownloadBook by remember { mutableStateOf<SearchBook?>(null) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var loginMessage by remember { mutableStateOf("") }
    var loginLoading by remember { mutableStateOf(false) }
    var loginChecked by remember { mutableStateOf(false) }
    var showDownloadPanel by remember { mutableStateOf(false) }
    val hazeState = remember { HazeState() }

    val session = remember {
        ZLibraryNativeSession(
            onSearchResults = { books, status ->
                nativeBooks = books
                nativeStatus = status
                nativeSearching = false
            },
            onRealDownloadUrl = { url ->
                val book = activeDownloadBook
                val cookies = CookieManager.getInstance().getCookie("https://1lib.sk/") ?: ""
                viewModel.startWebViewDownload(book, url, cookies)
            },
            onLoginResult = { ok, msg ->
                loginLoading = false
                loginMessage = msg
                if (ok) showLoginDialog = false
            }
        )
    }

    // 离开书库页面时释放隐藏 WebView，避免渲染进程常驻占用 CPU/内存
    DisposableEffect(Unit) {
        onDispose {
            session.destroy()
        }
    }

    // 进入书库自动检测登录状态；未登录时弹出软件内登录窗口
    LaunchedEffect(isZlibSource, aggregateMode) {
        if (isZlibSource && !aggregateMode && !loginChecked) {
            loginChecked = true
            kotlinx.coroutines.delay(1200)
            // 登录检测只读 Cookie，无需提前创建隐藏 WebView（首次搜索/登录时再创建）
            if (!session.isLoggedIn()) {
                showLoginDialog = true
                loginMessage = "检测到未登录，登录后可正常下载（搜索无需登录）"
            }
        }
    }

    // 下载超时提示：点击下载后 15 秒仍未捕获真实文件链接（可能需登录/中间页）时给出反馈
    LaunchedEffect(activeDownloadBook?.id) {
        val book = activeDownloadBook
        if (book != null) {
            kotlinx.coroutines.delay(15000)
            val st = viewModel.downloadStates.value[book.id]
            if (st == null || st is DownloadState.Idle) {
                nativeStatus = "未能获取下载链接（可能需要登录），可稍后重试"
            }
        }
    }

    // 下载开始/进行中自动展开悬浮卡片（右上角按钮也可手动开合）
    LaunchedEffect(downloadStates) {
        val st = activeDownloadBook?.let { downloadStates[it.id] }
        if (st is DownloadState.Pending || st is DownloadState.Downloading) {
            showDownloadPanel = true
        }
    }
    LaunchedEffect(comicDownloading) {
        if (comicDownloading.isNotEmpty()) {
            showDownloadPanel = true
        }
    }

    val performSearch: (String) -> Unit = { keyword ->
        if (keyword.isNotBlank()) {
            viewModel.recordSearch(keyword)
            if (aggregateMode) {
                viewModel.aggregateSearch(keyword)
            } else if (isZlibSource) {
                nativeBooks = emptyList()
                nativeStatus = ""
                nativeSearching = true
                session.ensureCreated(context)
                session.search(keyword)
            } else {
                viewModel.search(keyword)
            }
        }
    }
    
    val hasSeenWelcome by viewModel.hasSeenWelcome.collectAsState()
    val isCurrentSourceLoggedIn by viewModel.isCurrentSourceLoggedIn.collectAsState()
    
    // Filter environment-only sources in production UI
    val visibleSources = remember(availableSources) {
        availableSources.filter { !it.capabilities.environmentOnly }
    }

    var loginDialogSource by remember { mutableStateOf<BookSource?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchFieldFocused by remember { mutableStateOf(false) }
    var sourceDropdownExpanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    // 页面滚动时同步收起搜索历史面板
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (scrolling && searchFieldFocused) {
                    searchFieldFocused = false
                    focusManager.clearFocus()
                }
            }
    }

    loginDialogSource?.let { src ->
        ZLibraryLoginDialog(hazeState = hazeState,
            source = src,
            onDismiss = { loginDialogSource = null },
            onSuccess = { 
                loginDialogSource = null
                viewModel.checkSourceLoginStatus()
            }
        )
    }

    if (!hasSeenWelcome) {
        LibraryWelcomeScreen(
            onImportLocal = {
                viewModel.markLocalBookImported()
                viewModel.markWelcomeSeen()
                onImportLocalBook()
            },
            onOnlineSearch = {
                val onlineSource = visibleSources.firstOrNull()
                if (onlineSource != null) {
                    viewModel.selectSource(onlineSource.id)
                    viewModel.markWelcomeSeen()
                } else {
                    if (visibleSources.isNotEmpty()) {
                        viewModel.selectSource(visibleSources.first().id)
                        viewModel.markWelcomeSeen()
                    } else {
                        onOpenSourceManagement()
                        viewModel.markWelcomeSeen()
                    }
                }
            },
            onSourceManage = {
                viewModel.markWelcomeSeen()
                onOpenSourceManagement()
            },
            hasOnlineSource = visibleSources.isNotEmpty()
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            // 点击搜索框外任意区域：收起历史面板并释放焦点
                            if (searchFieldFocused) {
                                searchFieldFocused = false
                                focusManager.clearFocus()
                            }
                        }
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        // 仅在登录弹窗/下载卡片可见时才启用毛玻璃背板，平时滚动不产生模糊开销
                        if (showLoginDialog || showDownloadPanel) Modifier.haze(hazeState) else Modifier
                    )
            ) {
            Surface(
                modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                color = MaterialTheme.colorScheme.background
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("书库", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    val latestSt = activeDownloadBook?.let { downloadStates[it.id] }
                    val comicActive = comicDownloading.isNotEmpty()
                    IconButton(onClick = { showDownloadPanel = !showDownloadPanel }) {
                        Box(contentAlignment = Alignment.Center) {
                            if (latestSt is DownloadState.Downloading || comicActive) {
                                CircularProgressIndicator(
                                    progress = {
                                        if (latestSt is DownloadState.Downloading) {
                                            latestSt.progress.coerceIn(0f, 1f)
                                        } else {
                                            val task = comicDownloading.firstOrNull()
                                            (if (task != null) comicDownloadProgress[task] else null)
                                                ?.coerceIn(0f, 1f) ?: 0f
                                        }
                                    },
                                    modifier = Modifier.size(30.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "下载任务",
                                tint = if (latestSt is DownloadState.Downloading || comicActive) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(0.dp)
            ) {
                // Source Selector & Search
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = "书源:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        Box {
                            FilterChip(
                                selected = true,
                                onClick = { sourceDropdownExpanded = true },
                                label = {
                                    Text(
                                        if (aggregateMode) "聚合漫画（全部）"
                                        else currentSource?.name ?: "无可用书源"
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "选择书源"
                                    )
                                }
                            )

                            DropdownMenu(
                                expanded = sourceDropdownExpanded,
                                onDismissRequest = { sourceDropdownExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("聚合漫画（全部）") },
                                    onClick = {
                                        viewModel.setAggregateMode(true)
                                        sourceDropdownExpanded = false
                                    },
                                    trailingIcon = if (aggregateMode) {
                                        { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) }
                                    } else null
                                )
                                HorizontalDivider()
                                visibleSources.forEach { source ->
                                    DropdownMenuItem(
                                        text = { Text(source.name) },
                                        onClick = {
                                            viewModel.selectSource(source.id)
                                            sourceDropdownExpanded = false
                                        },
                                        trailingIcon = if (!aggregateMode && currentSource?.id == source.id) {
                                            { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) }
                                        } else null
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("管理书源...") },
                                    onClick = {
                                        sourceDropdownExpanded = false
                                        onOpenSourceManagement()
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("搜索书名或作者") },
                        trailingIcon = {
                            IconButton(onClick = { performSearch(searchQuery) }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Search
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSearch = { performSearch(searchQuery) }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { searchFieldFocused = it.isFocused },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = errorMessage ?: "",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                if (currentSource?.capabilities?.downloadRequiresLogin == true && !isCurrentSourceLoggedIn) {
                                    TextButton(onClick = onOpenSourceManagement) {
                                        Text("去管理/登录")
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // 搜索历史：点击搜索框获得焦点、输入为空且历史非空时，以“窗帘”动画展开/收起
                AnimatedVisibility(
                    visible = searchFieldFocused && searchQuery.isBlank() && searchHistory.isNotEmpty(),
                    enter = expandVertically(
                        animationSpec = tween(320, easing = CubicBezierEasing(0f, 0f, 0.2f, 1f))
                    ) + fadeIn(
                        animationSpec = tween(320, easing = CubicBezierEasing(0f, 0f, 0.2f, 1f))
                    ),
                    exit = shrinkVertically(
                        animationSpec = tween(280, easing = CubicBezierEasing(0.55f, 0.055f, 0.675f, 0.19f))
                    ) + fadeOut(
                        animationSpec = tween(240, easing = CubicBezierEasing(0.55f, 0.055f, 0.675f, 0.19f))
                    )
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "搜索历史",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.clearSearchHistory() }) {
                                    Text(
                                        text = "清空",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            searchHistory.take(10).forEach { q ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            searchQuery = q
                                            performSearch(q)
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = q,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "›",
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (aggregateMode && uiState is LibraryUiState.AggregateResults) {
                    val agg = uiState as LibraryUiState.AggregateResults
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + extraBottomPadding),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        agg.groups.forEach { group ->
                            item(key = "agg_group_header_${group.sourceId}") {
                                AggregateSourceHeader(
                                    name = group.sourceName,
                                    loading = group.loading,
                                    resultCount = group.books.size,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            if (group.loading) {
                                item(key = "agg_group_loading_${group.sourceId}") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 18.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            ChasingDots(size = 30.dp, color = MintPrimary)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "正在搜索…",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else if (group.books.isNotEmpty()) {
                                items(group.books, key = { "${group.sourceId}_${it.id}" }) { book ->
                                    val st by remember(book.id) {
                                        derivedStateOf {
                                            downloadStatesState.value[book.id] ?: DownloadState.Idle
                                        }
                                    }
                                    LibraryBookCard(
                                        book = book,
                                        downloadState = st,
                                        imageLoader = imageLoader,
                                        coverHeaders = rememberCoverHeaders(book, availableSources),
                                        comicMode = true,
                                        onStartDownload = { onOpenComic(book) },
                                        onPauseDownload = { viewModel.pauseDownload(book.id) },
                                        onResumeDownload = { viewModel.resumeDownload(book.id) },
                                        onCancelDownload = { viewModel.cancelDownload(book.id) }
                                    )
                                }
                            } else {
                                item(key = "agg_group_error_${group.sourceId}") {
                                    AggregateSourceError(
                                        error = group.error,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            }
                        }
                        if (!agg.running && agg.groups.none { it.books.isNotEmpty() }) {
                            item(key = "agg_empty") {
                                com.example.ui.components.MascotEmptyState(
                                    mascotResId = com.example.ui.mascot.MascotSpriteSheet.sadDrawable,
                                    title = "未找到结果",
                                    description = "所有漫画源都没有匹配“$searchQuery”的内容",
                                    actionLabel = "管理与导入书源",
                                    onActionClick = onOpenSourceManagement,
                                    testTagPrefix = "aggregate_empty_state"
                                )
                            }
                        }
                    }
                } else if (isZlibSource) {
                    when {
                        nativeSearching -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                ChasingDots(
                                    size = 52.dp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        nativeBooks.isNotEmpty() -> {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + extraBottomPadding),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(nativeBooks, key = { it.id }) { book ->
                                    val st by remember(book.id) {
                                        derivedStateOf {
                                            downloadStatesState.value[book.id] ?: DownloadState.Idle
                                        }
                                    }
                                    LibraryBookCard(
                                        book = book,
                                        downloadState = st,
                                        imageLoader = imageLoader,
                                        coverHeaders = rememberCoverHeaders(book, availableSources),
                                        onStartDownload = {
                                            activeDownloadBook = book
                                            showDownloadPanel = true
                                            val dl = book.downloadUrl
                                            if (dl.isNullOrBlank()) {
                                                nativeStatus = "该书无下载链接"
                                            } else {
                                                session.loadUrl(dl)
                                            }
                                        },
                                        onPauseDownload = { viewModel.pauseDownload(book.id) },
                                        onResumeDownload = { viewModel.resumeDownload(book.id) },
                                        onCancelDownload = { viewModel.cancelDownload(book.id) }
                                    )
                                }
                            }
                        }
                        nativeStatus.isNotBlank() -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(nativeStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                com.example.ui.components.MascotEmptyState(
                                    mascotResId = com.example.ui.mascot.MascotSpriteSheet.sadDrawable,
                                    title = if (searchQuery.isBlank()) "检索图书" else "未找到结果",
                                    description = if (searchQuery.isBlank()) {
                                        "在上方输入书名、作者或关键词"
                                    } else {
                                        "未找到与“$searchQuery”匹配的内容，请尝试更换关键词或书源"
                                    },
                                    actionLabel = "管理与导入书源",
                                    onActionClick = onOpenSourceManagement,
                                    testTagPrefix = "search_empty_state"
                                )
                            }
                        }
                    }
                } else if (isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        ChasingDots(
                            size = 52.dp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                } else if (uiState is LibraryUiState.Error) {
                    val error = (uiState as LibraryUiState.Error).error
                    val (title, desc, action) = when (error) {
                        is LibraryError.NetworkUnavailable -> Triple("无网络连接", "请检查网络设置后重试", "重试")
                        is LibraryError.SourceUnavailable -> Triple("服务无响应", "当前书源站点暂无响应，请稍后重试或切换书源", "重试")
                        is LibraryError.AuthenticationRequired -> Triple("需要登录", "当前书源需要账号身份验证", "去登录")
                        is LibraryError.CloudflareBlocked -> Triple("安全验证拦截", "目标站点已启用安全防护，请稍后重试", "重试")
                        is LibraryError.ParseFailed -> Triple("数据解析失败", "返回数据格式异常，无法解析内容", "重试")
                        else -> Triple("请求超时", error.message ?: "网络请求超时，请重试", "重试")
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        com.example.ui.components.MascotEmptyState(
                            mascotResId = com.example.ui.mascot.MascotSpriteSheet.sadDrawable,
                            title = title,
                            description = desc,
                            actionLabel = action,
                            onActionClick = {
                                if (error is LibraryError.AuthenticationRequired) {
                                    onOpenSourceManagement()
                                } else {
                                    performSearch(searchQuery)
                                }
                            },
                            testTagPrefix = "search_error_state"
                        )
                    }
                } else if (searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        com.example.ui.components.MascotEmptyState(
                            mascotResId = com.example.ui.mascot.MascotSpriteSheet.sadDrawable,
                            title = if (searchQuery.isBlank()) "检索图书" else "未找到结果",
                            description = if (searchQuery.isBlank()) {
                                "在上方输入书名、作者或关键词"
                            } else {
                                "未找到与“$searchQuery”匹配的内容，请尝试更换关键词或书源"
                            },
                            actionLabel = "管理与导入书源",
                            onActionClick = onOpenSourceManagement,
                            testTagPrefix = "search_empty_state"
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + extraBottomPadding),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(searchResults, key = { it.id }) { book ->
                            val st by remember(book.id) {
                                derivedStateOf {
                                    downloadStatesState.value[book.id] ?: DownloadState.Idle
                                }
                            }
                            LibraryBookCard(
                                book = book,
                                downloadState = st,
                                imageLoader = imageLoader,
                                coverHeaders = rememberCoverHeaders(book, availableSources),
                                comicMode = currentSource?.capabilities?.supportComic == true,
                                onStartDownload = {
                                    if (currentSource?.capabilities?.supportComic == true) {
                                        onOpenComic(book)
                                    } else if (currentSource?.capabilities?.downloadRequiresLogin == true && !isCurrentSourceLoggedIn) {
                                        loginDialogSource = currentSource
                                    } else {
                                        activeDownloadBook = book
                                        showDownloadPanel = true
                                        viewModel.startDownload(book)
                                    }
                                },
                                onPauseDownload = { viewModel.pauseDownload(book.id) },
                                onResumeDownload = { viewModel.resumeDownload(book.id) },
                                onCancelDownload = { viewModel.cancelDownload(book.id) }
                            )
                        }
                    }
                }

                // 原生登录弹窗
                if (showLoginDialog) {
                    LibraryLoginDialog(
                        message = loginMessage,
                        loading = loginLoading,
                        onLogin = { email, pass ->
                            loginLoading = true
                            loginMessage = "登录中…"
                            session.ensureCreated(context)
                            session.login(email, pass)
                        },
                        onDismiss = { if (!loginLoading) showLoginDialog = false },
                        hazeState = hazeState
                    )
                }

                // 居中悬浮下载面板（毛玻璃 + Q弹弹簧动画）
            }
        }

        // Overlay Box for Download Panel
        if (showDownloadPanel) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { showDownloadPanel = false },
                contentAlignment = Alignment.Center
            ) {
                var appear by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { appear = true }
                val panelScale by animateFloatAsState(
                    targetValue = if (appear) 1f else 0.55f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "downloadPanelSpring"
                )
                val panelAlpha by animateFloatAsState(
                    targetValue = if (appear) 1f else 0f,
                    animationSpec = tween(220),
                    label = "downloadPanelFade"
                )
                val book = activeDownloadBook
                val st = book?.let { downloadStates[it.id] } ?: DownloadState.Idle
                val comicTask = comicDownloading.firstOrNull()
                val comicProgress = comicTask?.let { comicDownloadProgress[it] } ?: 0f
                val comicPausedTask = comicTask?.let { comicPaused.contains(it) } ?: false
                val comicTitle = comicTask?.let { task ->
                    comicChapters.firstOrNull { it.id == task }?.title ?: "漫画章节"
                } ?: ""
                val comicChapter = comicTask?.let { task ->
                    comicChapters.firstOrNull { it.id == task }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .graphicsLayer {
                            scaleX = panelScale
                            scaleY = panelScale
                            alpha = panelAlpha
                        }
                        .clickable(enabled = false) {} // Prevent clicks from passing through
                ) {
                    if (comicTask != null) {
                        ComicDownloadGlassCard(
                            title = comicTitle,
                            progress = comicProgress,
                            paused = comicPausedTask,
                            onPause = { viewModel.pauseComicChapter(comicTask) },
                            onResume = {
                                comicChapter?.let { chapter ->
                                    comicBook?.let { book ->
                                        viewModel.downloadComicChapter(book, chapter)
                                    }
                                }
                            },
                            onCancel = { viewModel.cancelComicChapter(comicTask) },
                            onDismiss = { showDownloadPanel = false }
                        )
                    } else {
                        DownloadGlassCard(
                            book = book,
                            state = st,
                            hazeState = hazeState,
                            onDismiss = { showDownloadPanel = false },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun ComicDownloadGlassCard(
    title: String,
    progress: Float,
    paused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "漫画下载",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (paused) "已暂停：$title" else title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = MintPrimary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!paused) {
                    Text(
                        text = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MintPrimary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (paused) {
                        AppIconButton(onClick = onResume, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "继续下载",
                                tint = MintPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        AppIconButton(onClick = onPause, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "暂停下载",
                                tint = MintPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    AppIconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "取消下载",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryWelcomeScreen(
    onImportLocal: () -> Unit,
    onOnlineSearch: () -> Unit,
    onSourceManage: () -> Unit,
    hasOnlineSource: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = com.example.ui.mascot.MascotSpriteSheet.happyDrawable),
            contentDescription = "Roxy",
            modifier = Modifier.size(120.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "欢迎使用书库",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "在这里，您可以轻松导入本地电子书，或开启云端在线书源自由搜索。",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Option 1: Local Reading
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onImportLocal() }
                .testTag("welcome_import_local_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "本地图书",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("本地阅读", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text("直接导入手机中的 EPUB / TXT 电子书开始阅读", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Option 2: Online Search
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOnlineSearch() }
                .testTag("welcome_online_search_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "在线搜索",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("在线搜索", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        if (hasOnlineSource) "开启云端搜索，检索图书资源" else "连接 Z-Library 云端书源检索图书",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Option 3: Advanced Source Management
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSourceManage() }
                .testTag("welcome_source_manage_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "高级设置",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("高级设置", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text("管理书源或导入自定义 JSON 配置", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun rememberCoverHeaders(
    book: SearchBook,
    sources: List<BookSource>
): Map<String, String> {
    val cover = book.cover
    var headers by remember(cover, book.sourceId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(cover, book.sourceId) {
        if (cover.isNullOrBlank()) return@LaunchedEffect
        val source = sources.firstOrNull { it.id == book.sourceId } as? ComicSource
        headers = source?.getCoverHeaders(cover) ?: emptyMap()
    }
    return headers
}

@Composable
fun LibraryBookCard(
    book: SearchBook,
    downloadState: DownloadState,
    imageLoader: ImageLoader,
    coverHeaders: Map<String, String> = emptyMap(),
    comicMode: Boolean = false,
    onStartDownload: () -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    onCancelDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Cover：无封面或加载失败时显示占位（书名首字 + 格式）
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(110.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Gray.copy(alpha = 0.2f))
            ) {
                if (book.cover.isNullOrBlank()) {
                    BookCoverPlaceholder(book)
                } else {
                    val coverModel: Any = if (coverHeaders.isEmpty()) {
                        book.cover
                    } else {
                        ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(book.cover)
                            .apply { coverHeaders.forEach { (k, v) -> addHeader(k, v) } }
                            .build()
                    }
                    SubcomposeAsyncImage(
                        model = coverModel,
                        imageLoader = imageLoader,
                        contentDescription = book.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when (painter.state) {
                            is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                            else -> BookCoverPlaceholder(book)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = book.author,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (!book.description.isNullOrBlank()) {
                    Text(
                        text = book.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = "格式：${book.displayFormat()}${book.language?.let { " · $it" } ?: ""}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MintPrimary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                AnimatedContent(
                    targetState = downloadState,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)))
                            .togetherWith(fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)))
                    },
                    label = "download_state_transition"
                ) { state ->
                    when (state) {
                        is DownloadState.Idle -> {
                            Button(
                                onClick = onStartDownload,
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier.height(32.dp).testTag("download_button_idle")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (comicMode) Icons.Filled.MenuBook else Icons.Default.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (comicMode) "阅读" else "下载", fontSize = 12.sp)
                                }
                            }
                        }
                        is DownloadState.Error -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onStartDownload,
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.height(32.dp).testTag("download_button_retry")
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("重试", fontSize = 12.sp)
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = "错误",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "下载失败: ${state.message}",
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        is DownloadState.Pending -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("正在准备...", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.width(6.dp))
                                AppIconButton(onClick = onCancelDownload, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "取消", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                        is DownloadState.Downloading -> {
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            progress = { state.progress },
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "下载中: ${(state.progress * 100).toInt()}%",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        AppIconButton(onClick = onPauseDownload, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Pause, contentDescription = "暂停", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                                        }
                                        AppIconButton(onClick = onCancelDownload, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "取消", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = MaterialTheme.colorScheme.secondary,
                                    trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                )
                            }
                        }
                        is DownloadState.Paused -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("已暂停", fontSize = 12.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.width(8.dp))
                                AppIconButton(onClick = onResumeDownload, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "继续", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                                }
                                AppIconButton(onClick = onCancelDownload, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "取消", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                        is DownloadState.Success -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "已完成",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "已存入书架",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookCoverPlaceholder(book: SearchBook) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        MintPrimary.copy(alpha = 0.30f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = book.title.trim().firstOrNull()?.uppercase() ?: "书",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = book.displayFormat(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MintPrimary
            )
        }
    }
}

private fun SearchBook.displayFormat(): String {
    val raw = format.ifBlank {
        downloadUrl
            ?.substringBefore('?')
            ?.substringAfterLast('.', "")
            ?.lowercase()
            ?.takeIf {
                it.length in 2..5 && it.all(Char::isLetterOrDigit) && it != "html" && it != "php"
            }
            ?: ""
    }
    return raw.uppercase().ifBlank { "EPUB" }
}

@Composable
private fun AggregateSourceHeader(
    name: String,
    loading: Boolean,
    resultCount: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.22f),
                shape = RoundedCornerShape(26.dp)
            )
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        MintPrimary.copy(alpha = 0.16f),
                        MintSecondary.copy(alpha = 0.32f),
                        MintPrimary.copy(alpha = 0.16f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                ChasingDots(size = 18.dp, color = MintPrimary)
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = name,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!loading && resultCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "· $resultCount",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MintPrimary
                )
            }
        }
    }
}

@Composable
private fun AggregateSourceError(
    error: String?,
    modifier: Modifier = Modifier
) {
    val timedOut = error?.contains("timeout", ignoreCase = true) == true ||
        error?.contains("timed out", ignoreCase = true) == true ||
        error?.contains("超时") == true
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (timedOut) Icons.Default.Info else Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (timedOut) "链接超时" else "无结果",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
