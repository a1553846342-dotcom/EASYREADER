package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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
import com.example.ui.theme.MintPrimary

@OptIn(ExperimentalMaterial3Api::class)
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
    textMode: Boolean = false
) {
    val context = LocalContext.current
    var selectionMode by remember { mutableStateOf(false) }
    val selectedChapterIds = remember { mutableStateListOf<String>() }
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
            loading && chapters.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ChasingDots(
                            size = 52.dp,
                            color = MintPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("正在加载章节…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            error != null && chapters.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        AppLiquidButton(
                            text = "重试",
                            onClick = onRetry
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "header") {
                        ComicHeader(
                            book = book,
                            chapterCount = chapters.size,
                            onReadFirst = {
                                chapters.firstOrNull()?.let(onChapterClick)
                            },
                            selectionMode = selectionMode && !textMode,
                            selectedCount = selectedChapterIds.size,
                            onEnterSelection = { if (!textMode) selectionMode = true },
                            onDownloadSelected = {
                                if (textMode) return@ComicHeader
                                chapters
                                    .filter { it.id in selectedChapterIds }
                                    .forEach(onDownloadChapter)
                                selectedChapterIds.clear()
                                selectionMode = false
                            },
                            onCancelSelection = {
                                selectedChapterIds.clear()
                                selectionMode = false
                            }
                        )
                    }
                    items(chapters, key = { it.id }) { chapter ->
                        ChapterItem(
                            chapter = chapter,
                            selectionMode = selectionMode,
                            selected = chapter.id in selectedChapterIds,
                            onToggleSelect = {
                                if (chapter.id in selectedChapterIds) {
                                    selectedChapterIds.remove(chapter.id)
                                } else {
                                    selectedChapterIds.add(chapter.id)
                                }
                            },
                            downloading = downloadingChapters.contains(chapter.id),
                            paused = pausedChapters.contains(chapter.id),
                            progress = downloadProgress[chapter.id] ?: 0f,
                            onClick = {
                                if (chapter.external) {
                                    Toast.makeText(context, "站外链接章节暂不支持在线阅读", Toast.LENGTH_SHORT).show()
                                } else {
                                    onChapterClick(chapter)
                                }
                            },
                            onDownload = {
                                if (chapter.external) {
                                    Toast.makeText(context, "站外链接章节暂不支持下载", Toast.LENGTH_SHORT).show()
                                } else {
                                    onDownloadChapter(chapter)
                                }
                            },
                            onPause = { onPauseDownload(chapter) },
                            onResume = { onResumeDownload(chapter) },
                            onCancel = { onCancelDownload(chapter) },
                            showDownload = !textMode
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
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
                // 同一槽位：暂停↔继续时形变动画不重置
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
    textMode: Boolean = false
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
                            renderEffect = android.graphics.RenderEffect
                                .createBlurEffect(
                                    30f,
                                    30f,
                                    android.graphics.Shader.TileMode.CLAMP
                                )
                                .asComposeRenderEffect()
                        }
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
                        .shadow(12.dp, RoundedCornerShape(12.dp))
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

        // 主操作：开始阅读 + 批量下载（进入选择模式后改为“下载选中”）
        Spacer(modifier = Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
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
            } else {
                GradientActionButton(
                    text = "开始阅读",
                    onClick = onReadFirst,
                    modifier = Modifier.weight(1f)
                )
                if (!textMode) {
                    Spacer(modifier = Modifier.width(10.dp))
                    AppActionButton(
                        text = "批量下载",
                        onClick = onEnterSelection,
                        variant = AppButtonVariant.Secondary,
                        buttonSize = AppButtonSize.Small,
                        icon = Icons.Filled.Download
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterItem(
    chapter: ComicChapter,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    downloading: Boolean,
    paused: Boolean,
    progress: Float,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    showDownload: Boolean = true
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = if (selectionMode) onToggleSelect else onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelect() }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = chapter.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (chapter.external) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    Icons.Filled.Link,
                    contentDescription = "站外链接",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            if (!selectionMode) when {
                downloading -> {
                    if (!paused) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MintPrimary
                        )
                    }
                    // 同一槽位：暂停↔继续时形变动画不重置
                    PlayPauseMorphButton(
                        isPlaying = !paused,
                        onClick = if (paused) onResume else onPause,
                        sizeDp = 32
                    )
                    AppIconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "取消下载",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                showDownload && !chapter.external -> {
                    AppIconButton(onClick = onDownload, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = "下载本章",
                            tint = MintPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
