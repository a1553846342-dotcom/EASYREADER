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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    onPauseDownload: (ComicChapter) -> Unit,
    onResumeDownload: (ComicChapter) -> Unit,
    onCancelDownload: (ComicChapter) -> Unit
) {
    val context = LocalContext.current
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
                    IconButton(onClick = onBack) {
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
                        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)) {
                            Text("重试")
                        }
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
                        ComicHeader(book, chapters.size)
                    }
                    items(chapters, key = { it.id }) { chapter ->
                        ChapterItem(
                            chapter = chapter,
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
                            onCancel = { onCancelDownload(chapter) }
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

@Composable
private fun ComicHeader(book: SearchBook?, chapterCount: Int) {
    if (book == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = book.cover,
            contentDescription = book.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(72.dp)
                .height(100.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = book.author,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "共 $chapterCount 话可用 · 在线漫画",
                fontSize = 11.sp,
                color = MintPrimary
            )
        }
    }
}

@Composable
private fun ChapterItem(
    chapter: ComicChapter,
    downloading: Boolean,
    paused: Boolean,
    progress: Float,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
            when {
                downloading -> {
                    if (paused) {
                        AppIconButton(onClick = onResume, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "继续下载",
                                tint = MintPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MintPrimary
                        )
                        AppIconButton(onClick = onPause, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "暂停下载",
                                tint = MintPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    AppIconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "取消下载",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                !chapter.external -> {
                    IconButton(onClick = onDownload, modifier = Modifier.size(32.dp)) {
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
