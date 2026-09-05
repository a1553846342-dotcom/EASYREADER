package com.example.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.interaction.collectIsPressedAsState
import kotlin.math.max
import kotlin.math.roundToInt
import coil.ImageLoader
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import android.webkit.CookieManager
import android.app.Activity
import android.widget.Toast
import com.example.download.DownloadState
import com.example.source.SearchBook
import com.example.source.BookSource
import com.example.source.ComicSource
import com.example.source.LoginCredential
import com.example.source.SourceResult
import com.example.source.isNovelSource
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassDialogWindowEffect
import com.example.ui.components.scrollTiltSource
import com.example.ui.components.AcrylicBottomOverlay
import com.example.ui.components.PlayPauseMorphButton
import com.example.ui.components.filmGrain
import com.example.ui.components.iridescentBorder
import com.example.ui.components.liquidGlass
import com.example.ui.components.radialGlassScrim
import com.example.ui.components.rememberGlassPanelBackdrop
import com.example.ui.components.rememberIridescentColors
import com.example.ui.components.rememberThemedGlassBackdrop
import com.example.ui.components.SourceAvatar
import com.example.ui.components.ShimmerBox
import com.example.ui.components.AppActionButton
import com.example.ui.components.AppButtonSize
import com.example.ui.components.AppButtonVariant
import com.example.ui.theme.glassTitleColor
import com.example.ui.theme.MintPrimary
import com.example.ui.theme.MintSecondary
import com.example.ui.source.ZLibraryLoginDialog
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.widthIn
import com.example.ui.adaptive.AdaptiveSpec

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
    // 聚合搜索类别（v1.0.1）："comic" 漫画源 / "novel" 小说源，书源弹层分区选择、互斥过滤
    val aggregateKind by viewModel.aggregateKind.collectAsState()
    // 第十一轮第 6 条：多语言搜索开关状态（书源选择弹层内可控）
    val multiLangSearch by viewModel.multiLanguageSearch.collectAsState()
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
    val comicDownloadTasks by viewModel.comicDownloadTasks.collectAsState()
    val comicBook by viewModel.comicBook.collectAsState()
    val comicChapters by viewModel.comicChapters.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val formatPickerBook by viewModel.formatPickerBook.collectAsState()
    val pendingFormats by viewModel.pendingFormats.collectAsState()
    val formatLoading by viewModel.formatLoading.collectAsState()
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
    val scope = rememberCoroutineScope()

    val session = remember {
        ZLibraryNativeSession(
            onSearchResults = { books, status ->
                nativeBooks = books
                nativeStatus = status
                nativeSearching = false
            },
            onRealDownloadUrl = { url ->
                val book = activeDownloadBook
                val cookies = CookieManager.getInstance()
                    .getCookie("https://${ZLibraryNodeConfig.domain}/") ?: ""
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
            viewModel.checkSourceLoginStatus()
            if (!viewModel.isCurrentSourceLoggedIn.value) {
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

    LaunchedEffect(comicDownloading) {
        if (comicDownloading.isNotEmpty()) {
            showDownloadPanel = true
        }
    }

    var searchFieldFocused by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // 搜索词/滚动位置跨导航保持（1.05 修复）：进详情页（comic_chapters 路由）时
    // LibraryScreen 被销毁，remember 会全丢——回来后搜索词清空、grid 跳回顶部。
    // LazyStaggeredGridState 内部是可 parcel 的，rememberSaveable 原生支持。
    val staggeredGridState = rememberSaveable(saver = LazyStaggeredGridState.Saver) {
        LazyStaggeredGridState()
    }
    // 书架双视图滚动 → 卡片惯性倾斜信号源（任务书「整卡倾斜」§2）
    listState.scrollTiltSource()
    staggeredGridState.scrollTiltSource()

    // 滚动联动渐进折叠头部（书库专属强化版）：滚动偏移在 0→150dp 行程内连续映射为
    // fraction 0→1（内容紧凑化→整卡高度归零），而非旧版布尔两态切换；
    // 搜索聚焦时强制完全收起，给搜索结果让出首屏。回顶自然恢复。
    val headerCollapsePx = with(androidx.compose.ui.platform.LocalDensity.current) { 150.dp.toPx() }
    val searchFocusedNow by rememberUpdatedState(searchFieldFocused)
    val headerCollapseFraction by remember(headerCollapsePx) {
        derivedStateOf {
            if (searchFocusedNow) return@derivedStateOf 1f
            val gridIdx = staggeredGridState.firstVisibleItemIndex
            val listIdx = listState.firstVisibleItemIndex
            if (gridIdx > 0 || listIdx > 0) return@derivedStateOf 1f
            val off = max(
                staggeredGridState.firstVisibleItemScrollOffset,
                listState.firstVisibleItemScrollOffset
            )
            (off / headerCollapsePx).coerceIn(0f, 1f)
        }
    }

    val performSearch: (String) -> Unit = { keyword ->
        if (keyword.isNotBlank()) {
            // 新搜索从顶部开始：结果首屏回到中上部（修复视觉重心下移）
            scope.launch {
                staggeredGridState.scrollToItem(0)
                listState.scrollToItem(0)
            }
            viewModel.recordSearch(keyword)
            if (aggregateMode) {
                viewModel.aggregateSearch(keyword)
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
    // 搜索词跨导航保持（1.05 修复）：进详情页回来后搜索结果原样还在
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSourceSheet by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

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
    LaunchedEffect(staggeredGridState) {
        snapshotFlow { staggeredGridState.isScrollInProgress }
            .collect { scrolling ->
                if (scrolling && searchFieldFocused) {
                    searchFieldFocused = false
                    focusManager.clearFocus()
                }
            }
    }

    // 聚合结果「源速跳」：面板开关 / 当前所处源组 / 流式结果跳转防漂移目标
    var showJumpSheet by remember { mutableStateOf(false) }
    var pendingJumpSourceId by remember { mutableStateOf<String?>(null) }
    // 每源结果预览折叠状态（任务一）：默认 false=只显示前 6 条；速跳定位也依赖此状态。
    // 1.05 修复：rememberSaveable 跨导航保持——进详情页回来后已展开的组不再收起。
    val expandedGroups = rememberSaveable(
        saver = Saver(
            save = { map -> map.filterValues { v -> v == true }.keys.toList() },
            restore = { restored: List<String> -> mutableStateMapOf<String, Boolean>().apply { restored.forEach { k -> put(k, true) } } }
        )
    ) { mutableStateMapOf<String, Boolean>() }
    val aggResults = uiState as? LibraryUiState.AggregateResults
    val activeGroupIdx by remember(aggResults?.groups) {
        derivedStateOf {
            val groups = aggResults?.groups ?: return@derivedStateOf -1
            val first = staggeredGridState.firstVisibleItemIndex
            var acc = 0
            var result = groups.lastIndex
            groups.forEachIndexed { i, g ->
                if (first >= acc) result = i
                acc += 1 + aggregateGroupItemCount(g, expandedGroups[g.sourceId] == true)
            }
            result
        }
    }

    // 跳转后数据仍在流式刷新（加载组完成会改变 item 数）→ 数据一变就对准目标组头，直至该组加载完成
    LaunchedEffect(aggResults?.groups) {
        val targetId = pendingJumpSourceId ?: return@LaunchedEffect
        val groups = aggResults?.groups ?: return@LaunchedEffect
        val idx = groups.indexOfFirst { it.sourceId == targetId }
        if (idx < 0) {
            pendingJumpSourceId = null
            return@LaunchedEffect
        }
        staggeredGridState.scrollToItem(groupHeaderIndex(groups, idx, expandedGroups))
        if (!groups[idx].loading) pendingJumpSourceId = null
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
    // 滚动渐进折叠头部（书库强化版）：fraction 连续驱动副标题淡出→标题缩小→整卡高度归零，
    // 完全收起后零垂直占用；回顶或搜索失焦后按同一路径平滑恢复。
    // 搜索聚焦是状态跳变（非滚动连续量），用 260ms 补间过渡避免头部一帧内归零的硬切
    val headerFractionRaw = headerCollapseFraction
    val headerFraction by animateFloatAsState(
        targetValue = headerFractionRaw,
        animationSpec = if (searchFieldFocused || headerFractionRaw == 1f) tween(260) else tween(120),
        label = "libHeaderFraction",
    )
    LibraryCollapsingHeader(
        fraction = headerFraction,
        modifier = Modifier.statusBarsPadding(),
        title = "书库",
        subtitle = "LIBRARY & SEARCH",
        titleColor = glassTitleColor(),
                trailing = {
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
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(0.dp)
                    // 根因修复：键盘弹出时压缩本布局而非覆盖（此前无 imePadding，结果网格被键盘遮住约 40% 屏高）
                    .imePadding()
            ) {
                // 一体化搜索组件：书源入口整合进搜索框左侧（任务书「删除独立书源区域+搜索框重新设计」）
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    UnifiedSearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = { performSearch(searchQuery) },
                        onFocusChanged = { searchFieldFocused = it },
                        onSourceClick = { showSourceSheet = true },
                        sourceLabel = if (aggregateMode)
                            if (aggregateKind == "novel") "全部小说" else "全部漫画"
                        else currentSource?.name ?: "书源",
                        searchFocused = searchFieldFocused,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        GlassCard(
                            shape = RoundedCornerShape(12.dp),
                            tint = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
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
                    val historyBackdrop = rememberThemedGlassBackdrop()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .liquidGlass(
                                backdrop = historyBackdrop,
                                shape = RoundedCornerShape(20.dp),
                                surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                                blurRadius = 20.dp
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
                    // 每源结果预览折叠（任务一）：换搜索词才复位展开组。
                    // 1.05 修复：此前 LaunchedEffect(searchQuery) 首次组合必跑，
                    // 从详情页返回时把 rememberSaveable 恢复的展开状态全清了——
                    // 这就是“展开的会不见+页面跳位”的直接根因。
                    var lastQueryForExpandReset by rememberSaveable { mutableStateOf<String?>(null) }
                    LaunchedEffect(searchQuery) {
                        when (lastQueryForExpandReset) {
                            null -> lastQueryForExpandReset = searchQuery   // 恢复态：不清
                            searchQuery -> {}                                // 同词重组：不清
                            else -> {
                                expandedGroups.clear()                       // 真正换了词：复位
                                lastQueryForExpandReset = searchQuery
                            }
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(
                            // 多设备一致：手机2列 / 中屏3列 / 宽屏4列，避免平板上两列被拉伸过宽
                            when {
                                androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 840 -> 4
                                androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600 -> 3
                                else -> 2
                            }
                        ),
                        state = staggeredGridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 8.dp,
                            end = 16.dp,
                            bottom = 16.dp + extraBottomPadding
                        ),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalItemSpacing = 10.dp
                    ) {
                        agg.groups.forEach { group ->
                            item(
                                key = "agg_group_header_${group.sourceId}",
                                span = StaggeredGridItemSpan.FullLine
                            ) {
                                AggregateSourceHeader(
                                    name = group.sourceName,
                                    loading = group.loading,
                                    resultCount = group.books.size,
                                    onClick = { showJumpSheet = true }
                                )
                            }
                            if (group.loading) {
                                items(4, key = { "agg_loading_${group.sourceId}_$it" }) { index ->
                                    ShimmerBox(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(STAGGER_RATIO_PALETTE[index % STAGGER_RATIO_PALETTE.size])
                                    )
                                }
                            } else if (group.books.isNotEmpty()) {
                                val groupExpanded = expandedGroups[group.sourceId] == true
                                val visibleBooks = if (groupExpanded) group.books
                                    else group.books.take(AGGREGATE_PREVIEW_COUNT)
                                items(visibleBooks, key = { "${group.sourceId}_${it.id}" }) { book ->
                                    StaggeredComicCard(
                                        book = book,
                                        imageLoader = imageLoader,
                                        coverHeaders = rememberCoverHeaders(book, availableSources),
                                        sourceName = group.sourceName,
                                        novel = availableSources.firstOrNull { it.id == book.sourceId }?.isNovelSource == true,
                                        onClick = { onOpenComic(book) }
                                    )
                                }
                                // 展开按钮：还有隐藏结果时出现在第 6 条之后
                                if (!groupExpanded && group.books.size > AGGREGATE_PREVIEW_COUNT) {
                                    item(
                                        key = "agg_expand_${group.sourceId}",
                                        span = StaggeredGridItemSpan.FullLine
                                    ) {
                                        AggregateExpandButton(
                                            hiddenCount = group.books.size - visibleBooks.size,
                                            onExpand = { expandedGroups[group.sourceId] = true }
                                        )
                                    }
                                }
                            } else {
                                item(
                                    key = "agg_group_error_${group.sourceId}",
                                    span = StaggeredGridItemSpan.FullLine
                                ) {
                                    AggregateSourceError(
                                        error = group.error
                                    )
                                }
                            }
                        }
                        if (!agg.running && agg.groups.none { it.books.isNotEmpty() }) {
                            item(
                                key = "agg_empty",
                                span = StaggeredGridItemSpan.FullLine
                            ) {
                                com.example.ui.components.MascotEmptyState(
                                    mascotResId = com.example.ui.mascot.MascotSpriteSheet.sadDrawable,
                                    title = "未找到结果",
                                    description = "所有${if (aggregateKind == "novel") "小说源" else "漫画源"}都没有匹配“$searchQuery”的内容",
                                    actionLabel = "管理与导入书源",
                                    onActionClick = onOpenSourceManagement,
                                    testTagPrefix = "aggregate_empty_state"
                                )
                            }
                        }
                    }
                    // 聚合结果回顶：滑过一屏后浮现，点击回到搜索顶部
                    val showTopFab by remember {
                        derivedStateOf { staggeredGridState.firstVisibleItemIndex > 8 }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showTopFab,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = extraBottomPadding + 16.dp),
                        enter = scaleIn(
                            initialScale = 0.6f,
                            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                        ) + fadeIn(),
                        exit = scaleOut(targetScale = 0.6f) + fadeOut()
                    ) {
                        SmallFloatingActionButton(
                            onClick = { scope.launch { staggeredGridState.animateScrollToItem(0) } },
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MintPrimary,
                            shape = CircleShape,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp)
                        ) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "回到顶部")
                        }
                    }
                    }
                    if (showJumpSheet) {
                        AggregateJumpSheet(
                            groups = agg.groups,
                            activeGroupIdx = activeGroupIdx,
                            onDismiss = { showJumpSheet = false },
                            onJump = { idx ->
                                showJumpSheet = false
                                scope.launch {
                                    if (idx < 0) {
                                        pendingJumpSourceId = null
                                        staggeredGridState.animateScrollToItem(0)
                                    } else {
                                        pendingJumpSourceId = agg.groups[idx].sourceId
                                        staggeredGridState.animateScrollToItem(
                                            groupHeaderIndex(agg.groups, idx, expandedGroups)
                                        )
                                    }
                                }
                            }
                        )
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
                        // v1.0.1：网络失败展示真实原因（HTTP 状态码 / DNS / TLS / 超时），便于排查
                        is LibraryError.NetworkDetail -> Triple("请求失败", error.message, "重试")
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
                        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp + extraBottomPadding),
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
                                comicMode = currentSource?.capabilities?.supportComic == true ||
                                        currentSource?.capabilities?.supportOnlineText == true,
                                onStartDownload = {
                                    if (currentSource?.capabilities?.supportComic == true ||
                                        currentSource?.capabilities?.supportOnlineText == true) {
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
                            val src = currentSource
                            if (src == null) {
                                loginLoading = false
                                loginMessage = "当前书源不可用"
                                return@LibraryLoginDialog
                            }
                            loginLoading = true
                            loginMessage = "登录中…"
                            scope.launch {
                                when (val result = src.login(LoginCredential(username = email, password = pass))) {
                                    is SourceResult.Success -> {
                                        loginLoading = false
                                        loginMessage = "登录成功"
                                        showLoginDialog = false
                                        viewModel.checkSourceLoginStatus()
                                    }
                                    is SourceResult.Error -> {
                                        loginLoading = false
                                        loginMessage = result.exception.message ?: "登录失败，请检查账号密码"
                                    }
                                }
                            }
                        },
                        onDismiss = { if (!loginLoading) showLoginDialog = false },
                        hazeState = hazeState
                    )
                }

                // 居中悬浮下载面板（毛玻璃 + Q弹弹簧动画）
            }
        }

        // 下载格式选择弹窗（Z-Library 多格式书源）
        formatPickerBook?.let { book ->
            FormatPickerDialog(
                bookTitle = book.title,
                formats = pendingFormats,
                loading = formatLoading,
                onPick = { fmt -> viewModel.startDownload(book, fmt.format) },
                onDismiss = { viewModel.dismissFormatPicker() },
                hazeState = hazeState
            )
        }

        // 下载管理中心：亚克力底部面板（Dialog + decorView 实时模糊 + 径向遮罩）
        if (showDownloadPanel) {
            AcrylicBottomOverlay(onDismissRequest = { showDownloadPanel = false }) {
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
                val comicTasks = comicDownloadTasks.values.toList()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .graphicsLayer {
                            scaleX = panelScale
                            scaleY = panelScale
                            alpha = panelAlpha
                        }
                ) {
                    if (comicTasks.isNotEmpty()) {
                        ComicDownloadGlassCard(
                            tasks = comicTasks,
                            onPause = { viewModel.pauseComicChapter(it) },
                            onResume = { id ->
                                comicDownloadTasks[id]?.let { t ->
                                    viewModel.downloadComicChapter(t.book, t.chapter)
                                }
                            },
                            onRetry = { id ->
                                comicDownloadTasks[id]?.let { t ->
                                    viewModel.retryComicChapter(t.book, t.chapter)
                                }
                            },
                            onCancel = { viewModel.cancelComicChapter(it) },
                            onDismiss = { showDownloadPanel = false }
                        )
                    } else {
                        DownloadGlassCard(
                            book = book,
                            state = st,
                            hazeState = hazeState,
                            onDismiss = { showDownloadPanel = false },
                            onPause = { book?.let { viewModel.pauseDownload(it.id) } },
                            onResume = { book?.let { viewModel.resumeDownload(it.id) } },
                            onCancel = { book?.let { viewModel.cancelDownload(it.id) } },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // 书源选择 Liquid Glass 底部弹层
        if (showSourceSheet) {
            // v1.0.1：按源类型分区——聚合漫画（漫画源）/ 聚合小说（小说源）互斥展示
            val comicSources = remember(visibleSources) {
                visibleSources.filter { !it.isNovelSource }
            }
            val novelSources = remember(visibleSources) {
                visibleSources.filter { it.isNovelSource }
            }
            SourcePickerSheet(
                aggregateMode = aggregateMode,
                aggregateKind = aggregateKind,
                currentSource = currentSource,
                comicSources = comicSources,
                novelSources = novelSources,
                multiLanguageSearch = multiLangSearch,
                onToggleMultiLanguageSearch = { viewModel.setMultiLanguageSearch(it) },
                onSelectAggregate = { kind ->
                    viewModel.setAggregateMode(true)
                    viewModel.setAggregateKind(kind)
                    // 双状态残留修复：切模式后非活跃视图的滚动位置不清零会让折叠头部错误保持收起
                    scope.launch {
                        staggeredGridState.scrollToItem(0)
                        listState.scrollToItem(0)
                    }
                    showSourceSheet = false
                },
                onSelectSource = { id ->
                    viewModel.selectSource(id)
                    scope.launch {
                        staggeredGridState.scrollToItem(0)
                        listState.scrollToItem(0)
                    }
                    showSourceSheet = false
                },
                onManageSources = {
                    showSourceSheet = false
                    onOpenSourceManagement()
                },
                onDismiss = { showSourceSheet = false }
            )
        }
    }
}
}

/**
 * 书库渐进折叠头部（强化版收缩）——区别于四 Tab 共享的 [TabScreenHeader] 布尔两态：
 * [fraction] 由滚动偏移连续驱动（0=完全展开，1=完全收起零占位），
 * 三段式映射（区间重叠交叉淡化）保证全程连续无跳变：
 * - 0→0.5：内容紧凑化（副标题双通道淡出、标题 24→19sp、内外 padding 收紧）
 * - 0.4→0.8：整卡淡出（先于压高完成，压高裁切时内容已不可见，杜绝"半截字"伪影）
 * - 0.6→1：整卡高度按比例归零（自绘 layout 压缩高度，真正释放布局空间）
 * 滚动中每帧只重排一个 layout 节点，无监听器开销；回顶按同一路径反向恢复。
 */
@Composable
private fun LibraryCollapsingHeader(
    fraction: Float,
    modifier: Modifier = Modifier,
    title: String = "书库",
    subtitle: String? = null,
    titleColor: Color = Color.Unspecified,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val f0 = fraction.coerceIn(0f, 1f)
    // 三段式：0→0.5 内容紧凑化；0.4→0.8 整卡淡出；0.6→1 压高归零——
    // 淡出先于压高完成，避免压高裁切把标题/图标拦腰截断的"半截字"伪影
    val compact = (f0 / 0.5f).coerceIn(0f, 1f)
    val fade = ((f0 - 0.4f) / 0.4f).coerceIn(0f, 1f)
    val collapse = ((f0 - 0.6f) / 0.4f).coerceIn(0f, 1f)
    val titleSize = 24f - 5f * compact
    val subAlpha = (1f - f0 / 0.3f).coerceIn(0f, 1f)
    val subH = (17 * subAlpha).dp
    val outerV = (10 * (1f - collapse) * (1f - collapse)).dp
    val innerV = (12 - 8 * compact).dp

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = outerV)
            // 高度按 (1-collapse) 等比压缩 + 淡出（淡出已完成，裁切不可见）：
            // 完全收起后高度 0、padding 0，真正零垂直占用。
            // placement 必须真正 place placeable——空 placement 块会让整卡不参与绘制
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val h = (placeable.height * (1f - collapse)).roundToInt()
                layout(constraints.maxWidth, h) {
                    placeable.placeRelative(0, 0)
                }
            }
            .alpha(1f - fade)
            .clipToBounds(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = innerV),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading?.invoke(this)
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = titleSize.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    maxLines = 1
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = titleColor.copy(alpha = 0.75f),
                        letterSpacing = 1.5.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .height(subH)
                            .alpha(subAlpha)
                            .clipToBounds()
                    )
                }
            }
            trailing?.invoke(this)
        }
    }
}

/**
 * 一体化搜索组件：书源入口 + 搜索输入合体（任务书「搜索框重新设计」）。
 * 左侧书源区（当前书源名 + 下拉箭头）点击展开 Liquid Glass 书源选择浮层，
 * 不触发搜索；右侧 BasicTextField 承载输入，IME 搜索键直接发起检索；
 * 保留原有清空按钮与焦点联动（搜索历史浮层依赖 onFocusChanged）。
 * [searchFocused]：聚焦态描边高亮信号。
 */
@Composable
private fun UnifiedSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSourceClick: () -> Unit,
    sourceLabel: String,
    modifier: Modifier = Modifier,
    searchFocused: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val fieldTextColor = if (isDark) Color.White else Color.DarkGray
    val placeholderColor = if (isDark) Color.LightGray.copy(alpha = 0.6f) else Color.Gray
    val dividerColor = if (isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.12f)
    val sourceTint = MaterialTheme.colorScheme.onSurface
    // 聚焦态视觉反馈：主题强调色描边 + 微高亮（此前聚焦唯一反馈是历史面板弹出，
    // 违反搜索组件基本状态反馈规范）
    val focusStroke = MaterialTheme.colorScheme.secondary

    GlassCard(
        modifier = modifier
            .heightIn(min = 56.dp)
            .border(
                1.dp,
                if (searchFocused) focusStroke.copy(alpha = 0.55f) else Color.Transparent,
                RoundedCornerShape(22.dp)
            ),
        shape = RoundedCornerShape(22.dp)
    ) {
        // fillMaxWidth 而非 fillMaxSize：fillMaxHeight 会吃掉父级剩余全部屏高，
        // 把 56dp 搜索卡撑成整屏大空面板（用户实机反馈"搜索框那么大"的根因）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 书源入口：点击展开书源选择浮层（复用 Liquid Glass SourcePickerSheet）；
            // 保留 ripple 按压反馈；垂直 padding 加大到 14dp 保证 ≥44dp 命中高度
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onSourceClick)
                    .padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 6.dp)
            ) {
                Text(
                    text = sourceLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = sourceTint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 96.dp)
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "选择书源",
                    tint = sourceTint.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
            // 书源区与输入区的柔性分隔。
            // 高度必须固定：fillMaxHeight 在 Row 内会把子项量到父级最大可用高度（~整屏），
            // Row 随之被撑满 → 56dp 搜索卡变成整屏大空面板（用户实机"搜索框那么大"的真根因）
            Box(
                modifier = Modifier
                    .padding(vertical = 14.dp)
                    .width(1.dp)
                    .height(24.dp)
                    .background(dividerColor)
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = fieldTextColor),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    onSearch()
                    focusManager.clearFocus()
                }),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .onFocusChanged { onFocusChanged(it.isFocused) },
                decorationBox = { innerTextField ->
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (query.isEmpty()) {
                            // 占位字号与输入一致（15sp，避免首键入瞬间跳变）；单行防大字体溢出
                            Text(
                                text = "搜索书名或作者",
                                fontSize = 15.sp,
                                color = placeholderColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "清空",
                        tint = sourceTint.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(10.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcePickerSheet(
    aggregateMode: Boolean,
    aggregateKind: String,
    currentSource: BookSource?,
    comicSources: List<BookSource>,
    novelSources: List<BookSource>,
    multiLanguageSearch: Boolean,
    onToggleMultiLanguageSearch: (Boolean) -> Unit,
    onSelectAggregate: (String) -> Unit,
    onSelectSource: (String) -> Unit,
    onManageSources: () -> Unit,
    onDismiss: () -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val activity = androidx.compose.ui.platform.LocalContext.current as? Activity
    var visible by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val dismiss = {
        if (!dismissed) {
            dismissed = true
            visible = false
        }
    }
    LaunchedEffect(dismissed) {
        if (dismissed) {
            kotlinx.coroutines.delay(280)
            onDismiss()
        }
    }

    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val backdrop = rememberGlassPanelBackdrop()
    val iridescentColors = rememberIridescentColors()
    val blurPx = with(density) { 18.dp.toPx() }
    var dragOffsetY by remember { mutableStateOf(0f) }
    // 系统"减少透明度/关闭动画"开启时降级：不再用折射等重效果，面板更实
    val sheetContext = androidx.compose.ui.platform.LocalContext.current
    val reduceEffects = remember {
        val resolver = sheetContext.contentResolver
        val reduceTransparency = try {
            android.provider.Settings.Global.getInt(resolver, "reduce_transparency", 0) == 1
        } catch (_: Exception) {
            false
        }
        val animationsOff = try {
            android.provider.Settings.Global.getFloat(
                resolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Exception) {
            false
        }
        reduceTransparency || animationsOff
    }

    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        // 透明窗口 + 实时模糊宿主窗口（decorView RenderEffect）
        GlassDialogWindowEffect(activity = activity, blurRadiusPx = blurPx)
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(160)) +
                slideInVertically(tween(340), initialOffsetY = { it }),
            exit = fadeOut(tween(150)) +
                slideOutVertically(tween(260), targetOffsetY = { it })
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 径向渐变遮罩：中心亮、四周暗，聚光灯打在立牌上
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .radialGlassScrim()
                )
                // 点击空白处关闭
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = dismiss
                        )
                )
                // 液态玻璃弹窗本体：半透明 + 折射 + 高光 + 投影
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .offset { IntOffset(0, dragOffsetY.roundToInt()) }
                        .zIndex(1f)
                        // 双层阴影：环境阴影（品牌色）+ 贴地接触阴影
                        .shadow(
                            elevation = 32.dp,
                            shape = sheetShape,
                            ambientColor = iridescentColors.first().copy(alpha = 0.12f),
                            spotColor = iridescentColors.first().copy(alpha = 0.12f)
                        )
                        .shadow(
                            elevation = 8.dp,
                            shape = sheetShape,
                            ambientColor = Color.Black.copy(alpha = 0.20f),
                            spotColor = Color.Black.copy(alpha = 0.20f)
                        )
                        .liquidGlass(
                            backdrop = backdrop,
                            shape = sheetShape,
                            surfaceColor = MaterialTheme.colorScheme.surface.copy(
                                alpha = if (reduceEffects) 0.72f else 0.58f
                            ),
                            blurRadius = 12.dp,
                            refraction = false
                        )
                        .clip(sheetShape)
                        .filmGrain(alpha = 0.04f)
                        .iridescentBorder(
                            shape = sheetShape,
                            colors = iridescentColors,
                            width = 2.dp,
                            alpha = 0.22f
                        )
                        .navigationBarsPadding()
                ) {
                    // 拖拽手柄（弹窗内容最顶部）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 6.dp)
                            .pointerInput(Unit) {
                                val dismissThreshold = with(density) { 120.dp.toPx() }
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        if (dragOffsetY > dismissThreshold) {
                                            dismiss()
                                        } else {
                                            dragOffsetY = 0f
                                        }
                                    },
                                    onDragCancel = { dragOffsetY = 0f },
                                    onVerticalDrag = { _, dragAmount ->
                                        dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                        )
                    }
                    Text(
                        text = "选择书源",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            val interaction = remember { MutableInteractionSource() }
                            val pressed by interaction.collectIsPressedAsState()
                            val pressScale by animateFloatAsState(
                                targetValue = if (pressed) 0.97f else 1f,
                                label = "press"
                            )
                            ListItem(
                                headlineContent = { Text("管理书源", color = MaterialTheme.colorScheme.primary) },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                ),
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = pressScale
                                        scaleY = pressScale
                                    }
                                    .clickable(
                                        interactionSource = interaction,
                                        indication = null,
                                        onClick = onManageSources
                                    )
                            )
                        }
                        item {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                modifier = Modifier.padding(start = 72.dp, end = 20.dp)
                            )
                        }
                        item {
                            // v1.0.1：聚合搜索按类别分区——聚合漫画 / 聚合小说 互斥选择
                            AggregateOptionItem(
                                title = "聚合漫画（全部）",
                                subtitle = "同时搜索所有已启用的漫画源",
                                selected = aggregateMode && aggregateKind == "comic",
                                onClick = { onSelectAggregate("comic") }
                            )
                        }
                        item {
                            AggregateOptionItem(
                                title = "聚合小说（全部）",
                                subtitle = "同时搜索所有已启用的小说源（网文/电子书）",
                                selected = aggregateMode && aggregateKind == "novel",
                                onClick = { onSelectAggregate("novel") }
                            )
                        }
                        // 第十一轮第 6 条：多语言搜索开关——搜索词自动扩展各语言标题变体
                        //（如"无职转生"→ 無職転生 / Mushoku Tensei / Jobless Reincarnation）
                        item {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                modifier = Modifier.padding(start = 72.dp, end = 20.dp)
                            )
                        }
                        item {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = "多语言搜索",
                                        fontWeight = if (multiLanguageSearch) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        if (multiLanguageSearch) "开启：自动用各语言译名扩展搜索"
                                        else "关闭：仅使用输入的原始关键词"
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Default.Translate,
                                        contentDescription = null,
                                        tint = if (multiLanguageSearch) MaterialTheme.colorScheme.secondary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingContent = {
                                    com.example.ui.components.AppSwitch(
                                        checked = multiLanguageSearch,
                                        onCheckedChange = onToggleMultiLanguageSearch
                                    )
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                )
                            )
                        }
                        item {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                modifier = Modifier.padding(start = 72.dp, end = 20.dp)
                            )
                        }
                        // v1.0.1：书源按类型分区展示——漫画源 / 小说源
                        if (comicSources.isNotEmpty()) {
                            item { SourceSectionLabel("漫画源") }
                            items(comicSources, key = { it.id }) { source ->
                                SourceOptionItem(
                                    source = source,
                                    selected = !aggregateMode && currentSource?.id == source.id,
                                    onClick = { onSelectSource(source.id) }
                                )
                            }
                        }
                        if (novelSources.isNotEmpty()) {
                            item { SourceSectionLabel("小说源") }
                            items(novelSources, key = { it.id }) { source ->
                                SourceOptionItem(
                                    source = source,
                                    selected = !aggregateMode && currentSource?.id == source.id,
                                    onClick = { onSelectSource(source.id) }
                                )
                            }
                        }
                        item {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                modifier = Modifier.padding(start = 72.dp, end = 20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 聚合搜索选项行（聚合漫画/聚合小说）：按下缩放 + 选中弹入对勾，与弹层整体 Liquid Glass 风格一致。 */
@Composable
private fun AggregateOptionItem(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        label = "press"
    )
    val checkScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "check"
    )
    ListItem(
        headlineContent = {
            Text(
                text = title,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                imageVector = Icons.Default.MenuBook,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "当前选择",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            scaleX = checkScale
                            scaleY = checkScale
                        }
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
        ),
        modifier = Modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
    )
}

/** 书源分区小标题（漫画源 / 小说源）。 */
@Composable
private fun SourceSectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 2.dp)
    )
}

/** 单个书源选项行。 */
@Composable
private fun SourceOptionItem(
    source: BookSource,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        label = "press"
    )
    val checkScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "check"
    )
    ListItem(
        headlineContent = {
            Text(
                text = source.name,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        },
        supportingContent = {
            Text(
                text = source.id,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            SourceAvatar(
                sourceId = source.id,
                sourceName = source.name,
                size = 32.dp
            )
        },
        trailingContent = {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "当前使用",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            scaleX = checkScale
                            scaleY = checkScale
                        }
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
        ),
        modifier = Modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
    )
}

@Composable
private fun ComicDownloadGlassCard(
    tasks: List<ComicDownloadTask>,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRetry: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
            if (tasks.isEmpty()) {
                Text(
                    text = "暂无漫画下载任务",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                tasks.take(4).forEach { task ->
                    ComicDownloadTaskRow(
                        task = task,
                        onPause = onPause,
                        onResume = onResume,
                        onRetry = onRetry,
                        onCancel = onCancel
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (tasks.size > 4) {
                    Text(
                        text = "还有 ${tasks.size - 4} 个任务…",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ComicDownloadTaskRow(
    task: ComicDownloadTask,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRetry: (String) -> Unit,
    onCancel: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = task.chapter.title.ifBlank { "漫画章节" },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            when (task.status) {
                ComicDownloadStatus.DOWNLOADING -> {
                    Text(
                        text = "${(task.progress.coerceIn(0f, 1f) * 100).toInt()}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MintPrimary
                    )
                }
                ComicDownloadStatus.PAUSED -> {
                    Text("已暂停", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ComicDownloadStatus.FAILED -> {
                    Text("失败", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
                ComicDownloadStatus.SUCCESS -> {
                    Text(
                        "已完成",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            // 同一槽位的播放/暂停形态按钮：状态切换时形变动画不重置
            if (task.status == ComicDownloadStatus.DOWNLOADING || task.status == ComicDownloadStatus.PAUSED) {
                Spacer(modifier = Modifier.width(8.dp))
                PlayPauseMorphButton(
                    isPlaying = task.status == ComicDownloadStatus.DOWNLOADING,
                    onClick = if (task.status == ComicDownloadStatus.DOWNLOADING) {
                        { onPause(task.chapterId) }
                    } else {
                        { onResume(task.chapterId) }
                    },
                    sizeDp = 36
                )
            }
            if (task.status == ComicDownloadStatus.FAILED) {
                Spacer(modifier = Modifier.width(8.dp))
                AppIconButton(onClick = { onRetry(task.chapterId) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "重新下载",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            AppIconButton(onClick = { onCancel(task.chapterId) }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "取消任务",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (task.status == ComicDownloadStatus.DOWNLOADING) {
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { task.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = MintPrimary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
        if (task.status == ComicDownloadStatus.FAILED && !task.error.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = task.error,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
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
            painter = painterResource(id = com.example.ui.mascot.MascotSpriteSheet.idleDrawable),
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
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onImportLocal() }
                .testTag("welcome_import_local_card"),
            shape = RoundedCornerShape(16.dp)
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
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOnlineSearch() }
                .testTag("welcome_online_search_card"),
            shape = RoundedCornerShape(16.dp)
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
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSourceManage() }
                .testTag("welcome_source_manage_card"),
            shape = RoundedCornerShape(16.dp)
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
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
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
                    val extras = buildList {
                        book.comicId?.takeIf { it.isNotBlank() }?.let { add("#$it") }
                        book.language?.takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                    Text(
                        text = "格式：${book.displayFormat()}${if (extras.isNotEmpty()) " · " + extras.joinToString(" · ") else ""}",
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
                            AppActionButton(
                                text = if (comicMode) "阅读" else "下载",
                                onClick = onStartDownload,
                                variant = AppButtonVariant.Primary,
                                buttonSize = AppButtonSize.Small,
                                icon = if (comicMode) Icons.Filled.MenuBook else Icons.Default.Download,
                                modifier = Modifier.testTag("download_button_idle")
                            )
                        }
                        is DownloadState.Error -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AppActionButton(
                                    text = "重试",
                                    onClick = onStartDownload,
                                    variant = AppButtonVariant.Primary,
                                    buttonSize = AppButtonSize.Small,
                                    icon = Icons.Default.Refresh,
                                    modifier = Modifier.testTag("download_button_retry")
                                )
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
                        is DownloadState.Downloading, is DownloadState.Paused -> {
                            val isPaused = state is DownloadState.Paused
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isPaused) {
                                            Icon(Icons.Default.Pause, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                                        } else {
                                            CircularProgressIndicator(
                                                progress = {
                                                    (state as? DownloadState.Downloading)?.progress ?: 0f
                                                },
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isPaused) "已暂停" else {
                                                val p = (state as? DownloadState.Downloading)?.progress ?: 0f
                                                "下载中: ${(p * 100).toInt()}%"
                                            },
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isPaused) Color.Gray else MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        // 同一槽位：暂停↔继续时形变动画不重置
                                        PlayPauseMorphButton(
                                            isPlaying = !isPaused,
                                            onClick = if (isPaused) onResumeDownload else onPauseDownload,
                                            sizeDp = 30
                                        )
                                        AppIconButton(onClick = onCancelDownload, modifier = Modifier.size(30.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "取消", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                                if (!isPaused) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = {
                                            (state as? DownloadState.Downloading)?.progress ?: 0f
                                        },
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                        color = MaterialTheme.colorScheme.secondary,
                                        trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                    )
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
    // 按书名哈希取柔和渐变背景色：同一本书颜色稳定，加载失败态也有区分度
    val gradientColors = remember(book.title) {
        val hue = ((book.title.hashCode().toLong() and 0x7fffffffL) % 360L).toFloat()
        listOf(
            Color.hsv(hue, 0.32f, 0.92f),
            Color.hsv((hue + 42f) % 360f, 0.30f, 0.78f)
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(gradientColors)
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

/**
 * 瀑布流节奏调色板：卡片高度从第一帧起就固定，图片解码完成后不再改尺寸，
 * 避免 LazyVerticalStaggeredGrid 已排好的条目被后续高度变化拉扯（滚动卡死根因）。
 */
private val STAGGER_RATIO_PALETTE = listOf(0.62f, 0.70f, 0.78f, 0.90f, 3f / 4f)

/** 聚合搜索每源默认预览条数（任务一）：超出部分折叠到「展开全部」按钮之后。 */
private const val AGGREGATE_PREVIEW_COUNT = 6

/** 用 书源+书ID 稳定映射到一个瀑布流比例：同一本书任何时候算出来都一样。 */
private fun SearchBook.staggerRatio(): Float {
    val seed = "${sourceId}_$id"
    val hash = (seed.hashCode().toLong() and 0x7fffffffL)
    return STAGGER_RATIO_PALETTE[(hash % STAGGER_RATIO_PALETTE.size).toInt()]
}

@Composable
private fun StaggeredComicCard(
    book: SearchBook,
    imageLoader: ImageLoader,
    coverHeaders: Map<String, String>,
    sourceName: String,
    novel: Boolean = false,
    onClick: () -> Unit
) {
    // 固定比例：不再依赖图片解码结果，卡片测量高度全程不变
    val coverRatio = book.staggerRatio()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(120),
        label = "gridPress"
    )
    // 按下时阴影同步加深（联动反馈）
    val shadowAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.20f else 0.12f,
        animationSpec = tween(120),
        label = "gridShadow"
    )
    // 入场动画：淡入 + 轻微上移（只动透明度/位移，不影响测量尺寸）
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val enterAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(260),
        label = "cardEnterAlpha"
    )
    val enterSlide by animateFloatAsState(
        targetValue = if (entered) 0f else 1f,
        animationSpec = tween(260),
        label = "cardEnterSlide"
    )
    val cardShape = RoundedCornerShape(14.dp)
    val formatBadge = remember(book, novel) {
        listOfNotNull(
            book.comicId?.takeIf { it.isNotBlank() }?.let { "#$it" },
            book.format?.takeIf { it.isNotBlank() && !it.equals("epub", true) }?.uppercase()
        ).firstOrNull() ?: if (novel) "小说" else "漫画"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                alpha = enterAlpha
                translationY = enterSlide * size.height / 8f
            }
            .shadow(
                elevation = 8.dp,
                shape = cardShape,
                ambientColor = Color.Black.copy(alpha = shadowAlpha),
                spotColor = Color.Black.copy(alpha = shadowAlpha)
            )
            .clip(cardShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(coverRatio)
                .clip(cardShape)
                .background(Color.Gray.copy(alpha = 0.15f))
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
                        // 图片加载完成只负责渲染（ContentScale.Crop 自动裁切填满），
                        // 绝不修改卡片尺寸，避免已排版条目高度跳变
                        is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                        else -> BookCoverPlaceholder(book)
                    }
                }
            }
            // 底部渐变遮罩（黑→透明，占封面 40%），标题永远可读
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.40f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )
            // 标题 + 作者压在遮罩上
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = book.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.shadow(2.dp, RoundedCornerShape(4.dp))
                )
                if (book.author.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = book.author,
                        color = Color.White.copy(alpha = 0.70f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // 格式角标（右上）
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.40f)
            ) {
                Text(
                    text = formatBadge,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            // 来源角标（左上，16dp 圆形头像）
            SourceAvatar(
                sourceId = book.sourceId,
                sourceName = sourceName,
                size = 16.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            )
        }
    }
}

@Composable
private fun AggregateSourceHeader(
    name: String,
    loading: Boolean,
    resultCount: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(20.dp)
            )
            .background(MaterialTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            // 24dp 圆形源头像（哈希取色 + 首字母）
            SourceAvatar(
                sourceId = "agg_$name",
                sourceName = name,
                size = 24.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (loading) {
                ChasingDots(size = 18.dp, color = MintPrimary)
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (!loading && resultCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MintPrimary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "$resultCount",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MintPrimary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
            if (onClick != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    Icons.Filled.UnfoldMore,
                    contentDescription = "打开源组跳转面板",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
        }
    }
}

/**
 * 「展开全部 N 条」按钮（任务一）：聚合结果每源默认只展示前 6 条，
 * 点击后展开该书源全部搜索结果。整行胶囊样式与 [AggregateSourceHeader] 同语言。
 */
@Composable
private fun AggregateExpandButton(
    hiddenCount: Int,
    onExpand: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        label = "aggExpandPress"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = 1.dp,
                color = MintPrimary.copy(alpha = 0.35f),
                shape = RoundedCornerShape(20.dp)
            )
            .background(MintPrimary.copy(alpha = 0.08f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onExpand
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "展开全部 $hiddenCount 条",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MintPrimary
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = "展开该书源全部结果",
            tint = MintPrimary,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * 每个源组在 StaggeredGrid 占用的 item 数：组头 + (shimmer×4 | 书卡[+展开按钮] | 错误卡)，
 * 与网格 emit 逻辑一一对应。折叠态只占前 [AGGREGATE_PREVIEW_COUNT] 张书卡 + 1 个展开按钮。
 */
private fun aggregateGroupItemCount(
    group: LibraryUiState.AggregateGroup,
    expanded: Boolean = false
): Int = when {
    group.loading -> 4
    group.books.isNotEmpty() -> {
        val cards = if (expanded) group.books.size
            else minOf(group.books.size, AGGREGATE_PREVIEW_COUNT)
        if (!expanded && group.books.size > AGGREGATE_PREVIEW_COUNT) cards + 1 else cards
    }
    else -> 1
}

/** 目标源组组头的 item index（跳转定位用）。 */
private fun groupHeaderIndex(
    groups: List<LibraryUiState.AggregateGroup>,
    target: Int,
    expandedGroups: Map<String, Boolean> = emptyMap()
): Int {
    var index = 0
    for (i in 0 until target.coerceIn(0, groups.lastIndex)) {
        index += 1 + aggregateGroupItemCount(groups[i], expandedGroups[groups[i].sourceId] == true)
    }
    return index
}

/**
 * 「源速跳」面板：列出全部源组（头像 + 名称 + 数量/状态 + 当前位置标记）与「回到搜索顶部」。
 * 由组头胶囊点击唤起，选中后弹性滚动直达对应组头。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AggregateJumpSheet(
    groups: List<LibraryUiState.AggregateGroup>,
    activeGroupIdx: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxHeight(0.6f)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
Column(modifier = Modifier.widthIn(max = AdaptiveSpec.sheetMaxWidth).fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.UnfoldMore, contentDescription = null, tint = MintPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "跳转到源组",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "关闭", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
                item(key = "jump_top") {
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onJump(-1) },
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = MintPrimary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "回到搜索顶部",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                itemsIndexed(groups, key = { _, g -> "jump_${g.sourceId}" }) { idx, group ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onJump(idx) },
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SourceAvatar(
                                sourceId = "agg_${group.sourceName}",
                                sourceName = group.sourceName,
                                size = 28.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                group.sourceName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            when {
                                group.loading -> ChasingDots(size = 14.dp, color = MintPrimary)
                                group.books.isNotEmpty() -> Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MintPrimary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        "${group.books.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MintPrimary,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                                else -> Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = "无结果",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            if (idx == activeGroupIdx) {
                                Icon(Icons.Filled.CheckCircle, "当前位置", tint = MintPrimary, modifier = Modifier.size(18.dp))
                            } else {
                                Spacer(Modifier.width(18.dp))
                            }
                        }
                    }
                }
            }
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
    // 源正常返回空列表（error=null）才是真正的「无结果」；任何真实错误都翻译成可行动的提示
    val message = when {
        error.isNullOrBlank() -> "无结果"
        timedOut -> "链接超时"
        else -> com.example.source.js.friendlyJsSourceError(error)
    }
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
                text = message,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
