package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.ui.theme.clickableRowFeedback
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import android.os.Build
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.favorite.ChapterReadState
import com.example.data.favorite.ComicProgressEntity
import com.example.data.favorite.ComicReadingLogic
import com.example.data.favorite.OrderedChapter
import com.example.source.ComicChapter
import com.example.source.SearchBook
import com.example.ui.components.AppIconButton
import com.example.ui.components.ChasingDots
import com.example.ui.components.GradientActionButton
import com.example.ui.components.PlayPauseMorphButton
import com.example.ui.components.AppLiquidButton
import com.example.ui.components.AppActionButton
import com.example.ui.components.AppButtonSize
import com.example.ui.components.AppButtonVariant
import com.example.ui.favorite.ChapterRowState
import com.example.ui.favorite.ChapterStatusRow
import com.example.ui.favorite.ChapterVisual
import com.example.ui.favorite.ComicBottomActionBar
import com.example.ui.favorite.FavoriteHeartIcon
import com.example.ui.favorite.SourceUnavailableBanner
import com.example.ui.favorite.toVisual
import com.example.ui.theme.MintPrimary
import me.trishiraj.shadowglow.consistentShadow
import kotlinx.coroutines.delay

/**
 * 漫画主页面（从书库 / 我喜欢的点进）：
 * ① 封面信息区 + 简介；② 喜欢 / 继续阅读 / 下载 操作区；③ 章节列表（阅读态 × 下载态）。
 *
 * 加载策略：先用缓存快照秒开（stale-while-revalidate），后台刷新不整页转圈；
 * 网络失败/来源失效时顶部显示提示条，保留缓存信息与已读状态。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ComicChaptersScreen(
    book: SearchBook?,
    chapters: List<ComicChapter>,
    loading: Boolean,
    error: String?,
    downloadingChapters: Set<String>,
    downloadProgress: Map<String, Float>,
    pausedChapters: Set<String>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onChapterClick: (ComicChapter) -> Unit,
    onDownloadChapter: (ComicChapter) -> Unit,
    onDownloadAll: () -> Unit,
    onPauseDownload: (ComicChapter) -> Unit,
    onResumeDownload: (ComicChapter) -> Unit,
    onCancelDownload: (ComicChapter) -> Unit,
    /** 文本小说模式：隐藏图片下载/多选 UI，章节点击直接阅读正文 */
    textMode: Boolean = false,
    /* ── 「我喜欢的」/ 阅读进度（与是否下载无关） ── */
    favorite: Boolean = false,
    /** 无来源的书不能被喜欢 → 置灰并说明原因 */
    favoriteEnabled: Boolean = true,
    onToggleFavorite: (Boolean) -> Unit = {},
    /** 长按 ♡ = 直接弹出分类选择 Sheet */
    onFavoriteLongPress: () -> Unit = {},
    onFavoriteDisabledClick: () -> Unit = {},
    /** 章节级状态（含 pageIndex / pageCount）：chapterId → 实体 */
    chapterStates: Map<String, com.example.data.favorite.ChapterReadEntity> = emptyMap(),
    /** 已下载到本地书架的章节 id */
    downloadedChapterIds: Set<String> = emptySet(),
    progress: ComicProgressEntity? = null,
    /** 继续阅读：精确回到上次章节与页码 */
    onReadChapterAt: (ComicChapter, Int) -> Unit = { c, _ -> onChapterClick(c) },
    onMarkChapterRead: (ComicChapter, Int, Boolean) -> Unit = { _, _, _ -> },
    onMarkReadUpTo: (ComicChapter, Int) -> Unit = { _, _ -> },
    onChangeSource: () -> Unit = {},
    onMarkSeen: () -> Unit = {},
) {
    val context = LocalContext.current
    val ordered = remember(chapters) { ComicReadingLogic.ordered(chapters) }
    // 纯逻辑层只认状态枚举，这里把实体降维成 state
    val stateOnly = remember(chapterStates) { chapterStates.mapValues { it.value.state } }
    val target = remember(chapters, chapterStates, progress) {
        ComicReadingLogic.resolveContinue(chapters, stateOnly, progress)
    }
    val continueLabel = remember(target, chapters) {
        ComicReadingLogic.continueLabel(target, chapters)
    }
    val newIds = remember(chapters, progress) { ComicReadingLogic.newChapterIds(chapters, progress) }

    var sortDesc by rememberSaveable { mutableStateOf(false) }
    val displayChapters = remember(ordered, sortDesc) {
        if (sortDesc) ordered.asReversed() else ordered
    }

    val listState = rememberLazyListState()
    var highlightId by remember { mutableStateOf<String?>(null) }
    var chapterMenu by remember { mutableStateOf<OrderedChapter?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    val selectedChapterIds = remember { mutableStateListOf<String>() }

    // 进入页面：自动滚动到"阅读中"或"下一话"，并轻微高亮闪一下
    LaunchedEffect(ordered.size, target) {
        if (ordered.isEmpty()) return@LaunchedEffect
        val focusIndex = when (target) {
            is ComicReadingLogic.ContinueTarget.Resume -> target.chapterIndex
            is ComicReadingLogic.ContinueTarget.Next -> target.chapterIndex
            else -> ordered.indexOfFirst { chapterStates[it.chapter.id]?.state == ChapterReadState.READING }
                .takeIf { it >= 0 } ?: 0
        }
        val idx = if (sortDesc) ordered.size - 1 - focusIndex else focusIndex
        if (idx in 0 until displayChapters.size) {
            listState.animateScrollToItem(idx.coerceIn(0, (displayChapters.size - 1).coerceAtLeast(0)))
            highlightId = displayChapters.getOrNull(idx)?.chapter?.id
            delay(1000)
            highlightId = null
        }
    }

    // 记录"已见"快照：之后新增的章节才会显示「新」
    LaunchedEffect(book?.id, ordered.size) {
        if (ordered.isNotEmpty()) onMarkSeen()
    }

    val topBarScrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = book?.title ?: "漫画章节",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    AppIconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    // 滚动后顶部栏也出现一个小心形（热区 48dp）
                    if (topBarScrolled) {
                        FavoriteHeartIcon(
                            favorite = favorite,
                            enabled = favoriteEnabled,
                            onToggle = onToggleFavorite,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            if (!textMode && !selectionMode) {
                ComicBottomActionBar(
                    favorite = favorite,
                    favoriteEnabled = favoriteEnabled,
                    continueLabel = continueLabel,
                    isUpToDate = target is ComicReadingLogic.ContinueTarget.UpToDate,
                    onToggleFavorite = onToggleFavorite,
                    onFavoriteLongPress = onFavoriteLongPress,
                    onContinue = {
                        when (target) {
                            ComicReadingLogic.ContinueTarget.Start -> {
                                ordered.firstOrNull()?.chapter?.let { onReadChapterAt(it, 0) }
                                    ?: Toast.makeText(context, "暂无可阅读章节", Toast.LENGTH_SHORT).show()
                            }
                            is ComicReadingLogic.ContinueTarget.Resume -> {
                                ordered.getOrNull(target.chapterIndex)?.chapter?.let {
                                    onReadChapterAt(it, target.pageIndex)
                                }
                            }
                            is ComicReadingLogic.ContinueTarget.Next -> {
                                ordered.getOrNull(target.chapterIndex)?.chapter?.let { onReadChapterAt(it, 0) }
                            }
                            ComicReadingLogic.ContinueTarget.UpToDate -> {
                                // 「从头重读」与「已读到最新」都落到这里：回到第 1 话
                                ordered.firstOrNull()?.chapter?.let { onReadChapterAt(it, 0) }
                            }
                        }
                    },
                    onDownload = { selectionMode = true },
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                loading && chapters.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ChasingDots(size = 52.dp, color = MintPrimary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("正在加载章节…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                error != null && chapters.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = error, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            AppLiquidButton(text = "重试", onClick = onRetry)
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = onChangeSource) { Text("换源") }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item(key = "header") {
                            // 缓存信息仍在 → 顶部提示条（保留已读状态，可重试/换源）
                            if (error != null) {
                                SourceUnavailableBanner(
                                    message = "来源暂不可用（已保留缓存信息与阅读进度）",
                                    onRetry = onRetry,
                                    onChangeSource = onChangeSource,
                                )
                            }
                            ComicHeader(
                                book = book,
                                chapterCount = chapters.size,
                                onReadFirst = { ordered.firstOrNull()?.chapter?.let { onReadChapterAt(it, 0) } },
                                selectionMode = selectionMode && !textMode,
                                selectedCount = selectedChapterIds.size,
                                onEnterSelection = { if (!textMode) selectionMode = true },
                                onDownloadSelected = {
                                    if (textMode) return@ComicHeader
                                    chapters.filter { it.id in selectedChapterIds }.forEach(onDownloadChapter)
                                    selectedChapterIds.clear()
                                    selectionMode = false
                                },
                                onCancelSelection = {
                                    selectedChapterIds.clear()
                                    selectionMode = false
                                },
                                textMode = textMode,
                                sortDesc = sortDesc,
                                onToggleSort = { sortDesc = !sortDesc },
                                onJumpToLatest = {
                                    val idx = if (sortDesc) 0 else (displayChapters.size - 1).coerceAtLeast(0)
                                    scope.launch { listState.animateScrollToItem(idx) }
                                },
                                favorite = favorite,
                                favoriteEnabled = favoriteEnabled,
                                onToggleFavorite = onToggleFavorite,
                                onFavoriteLongPress = onFavoriteLongPress,
                                onFavoriteDisabledClick = onFavoriteDisabledClick,
                            )
                        }
                        items(displayChapters, key = { it.chapter.id }) { entry ->
                            val chapter = entry.chapter
                            val st = chapterStates[chapter.id]
                            val visual = st?.state?.toVisual() ?: ChapterVisual.UNREAD
                            val pageIndex = st?.pageIndex
                                ?: if (progress?.lastChapterId == chapter.id) progress.lastPageIndex else 0
                            val pageCount = st?.pageCount?.takeIf { it > 0 }
                                ?: if (progress?.lastChapterId == chapter.id) progress.lastPageCount else 0
                            ChapterStatusRow(
                                title = chapter.title,
                                coverUrl = book?.cover,
                                state = ChapterRowState(
                                    visual = visual,
                                    pageIndex = pageIndex,
                                    pageCount = pageCount,
                                    isNew = chapter.id in newIds,
                                    downloaded = chapter.id in downloadedChapterIds,
                                    downloading = chapter.id in downloadingChapters,
                                    downloadProgress = downloadProgress[chapter.id] ?: 0f,
                                    external = chapter.external,
                                ),
                                highlighted = highlightId == chapter.id,
                                onClick = {
                                    if (chapter.external) {
                                        Toast.makeText(context, "站外链接章节暂不支持在线阅读", Toast.LENGTH_SHORT).show()
                                    } else {
                                        onReadChapterAt(chapter, if (visual == ChapterVisual.UNREAD) 0 else pageIndex)
                                    }
                                },
                                onLongClick = { chapterMenu = entry },
                                onDownload = {
                                    if (chapter.external) {
                                        Toast.makeText(context, "站外链接章节暂不支持下载", Toast.LENGTH_SHORT).show()
                                    } else {
                                        onDownloadChapter(chapter)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // 下载进度悬浮窗
            val activeDownloadChapter = chapters.firstOrNull { it.id in downloadingChapters }
            if (activeDownloadChapter != null) {
                DownloadProgressOverlay(
                    chapter = activeDownloadChapter,
                    progress = downloadProgress[activeDownloadChapter.id] ?: 0f,
                    paused = pausedChapters.contains(activeDownloadChapter.id),
                    onPause = { onPauseDownload(activeDownloadChapter) },
                    onResume = { onResumeDownload(activeDownloadChapter) },
                    onCancel = { onCancelDownload(activeDownloadChapter) }
                )
            }
        }

        // 章节长按菜单：标记已读/未读、「将以上全部标记为已读」、「读到此处」
        chapterMenu?.let { entry ->
            ChapterActionSheet(
                chapter = entry,
                isRead = chapterStates[entry.chapter.id]?.state == ChapterReadState.READ,
                onDismiss = { chapterMenu = null },
                onToggleRead = { read ->
                    onMarkChapterRead(entry.chapter, entry.order, read)
                    chapterMenu = null
                },
                onMarkAboveRead = {
                    onMarkReadUpTo(entry.chapter, entry.order)
                    chapterMenu = null
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterActionSheet(
    chapter: OrderedChapter,
    isRead: Boolean,
    onDismiss: () -> Unit,
    onToggleRead: (Boolean) -> Unit,
    onMarkAboveRead: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(chapter.chapter.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            SheetRow(
                icon = if (isRead) Icons.Filled.RemoveDone else Icons.Filled.Done,
                text = if (isRead) "标记为未读" else "标记为已读",
            ) { onToggleRead(!isRead) }
            SheetRow(icon = Icons.Filled.Done, text = "将以上全部标记为已读") { onMarkAboveRead() }
            SheetRow(icon = Icons.Filled.MenuBook, text = "读到此处") { onToggleRead(true) }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SheetRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MintPrimary)
        Spacer(modifier = Modifier.width(14.dp))
        Text(text, fontSize = 14.sp)
    }
}

@Composable
private fun DownloadProgressOverlay(
    chapter: ComicChapter,
    progress: Float,
    paused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .consistentShadow(10.dp, RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        )
        {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.size(26.dp),
                    strokeWidth = 3.dp,
                    color = MintPrimary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (paused) "已暂停：${chapter.title}" else "正在下载：${chapter.title}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = MintPrimary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                if (!paused) {
                    Text(
                        text = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MintPrimary
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                PlayPauseMorphButton(
                    isPlaying = !paused,
                    onClick = if (paused) onResume else onPause,
                    sizeDp = 36
                )
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

@Composable
private fun ComicHeader(
    book: SearchBook?,
    chapterCount: Int,
    onReadFirst: () -> Unit,
    selectionMode: Boolean,
    selectedCount: Int,
    onEnterSelection: () -> Unit,
    onDownloadSelected: () -> Unit,
    onCancelSelection: () -> Unit,
    textMode: Boolean = false,
    sortDesc: Boolean = false,
    onToggleSort: () -> Unit = {},
    onJumpToLatest: () -> Unit = {},
    favorite: Boolean = false,
    favoriteEnabled: Boolean = true,
    onToggleFavorite: (Boolean) -> Unit = {},
    onFavoriteLongPress: () -> Unit = {},
    onFavoriteDisabledClick: () -> Unit = {},
) {
    if (book == null) return
    var descriptionExpanded by remember { mutableStateOf(false) }
    val desc = book.description?.takeIf { it.isNotBlank() }
    val formatBadge = remember(book, textMode) {
        listOfNotNull(
            book.comicId?.takeIf { it.isNotBlank() }?.let { "#$it" },
            book.format?.takeIf { it.isNotBlank() && !it.equals("epub", true) }?.uppercase()
        ).firstOrNull() ?: if (textMode) "小说" else "漫画"
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        // 降级遮罩用的主题色：drawWithContent 不是 @Composable 上下文，
        // 必须在组合期先取好（ hoist 出来 ），绘制期只读
        val scrimSurface = MaterialTheme.colorScheme.surface
        // 顶部视觉：封面模糊铺满做背景 + 清晰封面浮在上层（Apple Music 专辑页结构）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
        ) {
            if (!book.cover.isNullOrBlank()) {
                AsyncImage(
                    model = book.cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .graphicsLayer {
                            // RenderEffect / createBlurEffect 仅 API 31+ 存在；
                            // 低版本直接调用会 VerifyError 崩溃，必须先判级。
                            renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                android.graphics.RenderEffect
                                    .createBlurEffect(
                                        30f,
                                        30f,
                                        android.graphics.Shader.TileMode.CLAMP
                                    )
                                    .asComposeRenderEffect()
                            } else {
                                null
                            }
                        }
                        .then(
                            // ── 跨机型降级（API < 31 无 RenderEffect）──────────
                            // 无真实高斯模糊时，用「主题色磨砂遮罩 + 透光渐变」近似
                            // 虚化封面的氛围底图；上方本就叠有 0.45→0.82 的黑色
                            // 渐变压暗层，遮罩后观感接近虚化而不是清晰的原图。
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Modifier
                            } else {
                                Modifier.drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            listOf(
                                                scrimSurface.copy(alpha = 0.40f),
                                                scrimSurface.copy(alpha = 0.30f)
                                            )
                                        )
                                    )
                                    drawRect(
                                        brush = Brush.linearGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.10f),
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.08f)
                                            )
                                        )
                                    )
                                }
                            }
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)
                                )
                            )
                        )
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.45f),
                                Color.Black.copy(alpha = 0.82f)
                            )
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(170.dp)
                        .consistentShadow(12.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Gray.copy(alpha = 0.3f))
                ) {
                    if (!book.cover.isNullOrBlank()) {
                        AsyncImage(
                            model = book.cover,
                            contentDescription = book.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.MenuBook,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = formatBadge,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (book.author.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "作者：${book.author}",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 简介：默认 3 行，可展开
        if (desc != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = desc,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis
            )
            if (desc.length > 60) {
                TextButton(onClick = { descriptionExpanded = !descriptionExpanded }) {
                    Text(if (descriptionExpanded) "收起" else "展开")
                }
            }
        }

        // 元信息条
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "来源：${book.sourceId}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "语言：${book.language ?: "未知"}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "共 $chapterCount 话",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MintPrimary
            )
        }

        // 排序（正序/倒序，被记住）+ 跳到最新章
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (sortDesc) "倒序" else "正序",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    .clickable(onClick = onToggleSort)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "跳到最新章",
                fontSize = 12.sp,
                color = MintPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MintPrimary.copy(alpha = 0.12f))
                    .clickable(onClick = onJumpToLatest)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            if (selectionMode) {
                TextButton(onClick = onCancelSelection) { Text("取消") }
            } else if (!textMode) {
                TextButton(onClick = onEnterSelection) { Text("批量下载") }
            }
        }

        // 主操作：开始阅读 + 批量下载（进入选择模式后改为“下载选中”）
        if (selectionMode) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GradientActionButton(
                    text = if (selectedCount > 0) "下载选中（$selectedCount）" else "请选择章节",
                    onClick = onDownloadSelected,
                    enabled = selectedCount > 0,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                AppActionButton(
                    text = "取消",
                    onClick = onCancelSelection,
                    variant = AppButtonVariant.Secondary,
                    buttonSize = AppButtonSize.Small
                )
            }
        }
    }
}
