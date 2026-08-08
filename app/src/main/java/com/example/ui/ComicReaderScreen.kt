package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.Book
import com.example.data.Chapter
import com.example.ui.components.AppIconButton
import com.example.ui.theme.MintPrimary
import com.example.ui.theme.MintSecondary
import kotlinx.coroutines.delay
import net.engawapg.lib.zoomable.ExperimentalZoomableApi
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import net.engawapg.lib.zoomable.zoomableWithScroll
import java.io.File

enum class ComicReadingMode {
    HORIZONTAL,
    VERTICAL
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalZoomableApi::class)
@Composable
@androidx.compose.animation.ExperimentalSharedTransitionApi
fun ComicReaderScreen(
    book: Book?,
    chapters: List<Chapter>,
    onBack: () -> Unit,
    onUpdateProgress: (bookId: Int, pageIndex: Int, scrollOffset: Int, isFinished: Boolean) -> Unit,
    onRecordTime: (seconds: Long) -> Unit
) {
    val sharedTransitionScope = com.example.LocalSharedTransitionScope.current
    val animatedVisibilityScope = com.example.LocalNavAnimatedVisibilityScope.current
    if (book == null || chapters.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("未找到漫画内容", color = Color.White)
        }
        return
    }

    var readingMode by remember { mutableStateOf(ComicReadingMode.HORIZONTAL) }
    var isRightToLeft by remember { mutableStateOf(true) } // Default R-to-L for Japanese manga
    var isControlsVisible by remember { mutableStateOf(true) }

    val initialPage = remember(book.id) { book.currentChapterIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0)) }
    var currentPageIndex by remember { mutableIntStateOf(initialPage) }

    val totalPages = chapters.size

    // Reading Timer
    var readSeconds by remember { mutableLongStateOf(0L) }
    var lastFlush by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            readSeconds += 1L
            // 每 30 秒上报一次，统计页更新及时
            if (readSeconds - lastFlush >= 30L) {
                onRecordTime(readSeconds - lastFlush)
                lastFlush = readSeconds
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (readSeconds - lastFlush > 0) {
                onRecordTime(readSeconds - lastFlush)
            }
            onUpdateProgress(book.id, currentPageIndex, 0, currentPageIndex >= totalPages - 1)
        }
    }

    // Pager State for Horizontal Mode
    val pagerState = rememberPagerState(initialPage = initialPage) { totalPages }

    // Sync current page index changes (from slider/taps) to pagerState
    LaunchedEffect(currentPageIndex) {
        if (pagerState.currentPage != currentPageIndex && currentPageIndex in 0 until totalPages) {
            pagerState.scrollToPage(currentPageIndex)
        }
    }
    
    // Sync pagerState swipe changes to currentPageIndex
    LaunchedEffect(pagerState.currentPage) {
        if (readingMode == ComicReadingMode.HORIZONTAL) {
            currentPageIndex = pagerState.currentPage
            onUpdateProgress(book.id, currentPageIndex, 0, currentPageIndex >= totalPages - 1)
        }
    }

    // LazyList State for Vertical Webtoon Mode
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)
    
    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (readingMode == ComicReadingMode.VERTICAL) {
            currentPageIndex = listState.firstVisibleItemIndex
            onUpdateProgress(book.id, currentPageIndex, 0, currentPageIndex >= totalPages - 1)
        }
    }

    val handleTapLeft = {
        if (isRightToLeft) {
            if (currentPageIndex < totalPages - 1) currentPageIndex++
        } else {
            if (currentPageIndex > 0) currentPageIndex--
        }
    }

    val handleTapRight = {
        if (isRightToLeft) {
            if (currentPageIndex > 0) currentPageIndex--
        } else {
            if (currentPageIndex < totalPages - 1) currentPageIndex++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (book != null && sharedTransitionScope != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                val imageRequest = if (!book.coverUri.isNullOrEmpty() && book.isCoverValid) {
                    if (book.coverUri!!.startsWith("content://")) android.net.Uri.parse(book.coverUri)
                    else java.io.File(book.coverUri!!)
                } else null
                if (imageRequest != null) {
                    coil.compose.AsyncImage(
                        model = imageRequest,
                        contentDescription = "Shared Cover",
                        modifier = Modifier
                            .fillMaxSize()
                            .sharedElement(
                                state = rememberSharedContentState(key = "book_cover_${book.id}"),
                                animatedVisibilityScope = animatedVisibilityScope,
                                boundsTransform = { _, _ -> androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 300f) }
                            )
                            .alpha(0.05f),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
            }
        }
        // Main Content: Horizontal Pager or Vertical Webtoon List
        when (readingMode) {
            ComicReadingMode.HORIZONTAL -> {
                HorizontalPager(
                    state = pagerState,
                    reverseLayout = isRightToLeft,
                    modifier = Modifier.fillMaxSize()
                ) { pageIdx ->
                    val chapter = chapters.getOrNull(pageIdx)
                    if (chapter != null) {
                        ZoomableComicPage(
                            imagePath = chapter.content,
                            onTapLeft = { handleTapLeft() },
                            onTapRight = { handleTapRight() },
                            onTapCenter = { isControlsVisible = !isControlsVisible }
                        )
                    }
                }
            }
            ComicReadingMode.VERTICAL -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .zoomableWithScroll(
                            zoomState = rememberZoomState(),
                            onTap = { isControlsVisible = !isControlsVisible }
                        ),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(chapters, key = { _, ch -> ch.id }) { index, chapter ->
                        AsyncImage(
                            model = File(chapter.content),
                            contentDescription = "第 ${index + 1} 页",
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            contentScale = ContentScale.FillWidth
                        )
                    }
                }
            }
        }

        // Overlay Top & Bottom Controls
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(tween(200)) + slideInVertically { -it },
            exit = fadeOut(tween(200)) + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.82f)
            ) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = book.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1
                            )
                            Text(
                                text = "漫画阅读器 • 第 ${currentPageIndex + 1} / $totalPages 页",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Switch Reading Mode (Horizontal vs Vertical)
                        IconButton(
                            onClick = {
                                readingMode = if (readingMode == ComicReadingMode.HORIZONTAL) {
                                    ComicReadingMode.VERTICAL
                                } else {
                                    ComicReadingMode.HORIZONTAL
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (readingMode == ComicReadingMode.HORIZONTAL) Icons.Filled.ViewCarousel else Icons.Filled.ViewStream,
                                contentDescription = "切换模式",
                                tint = MintPrimary
                            )
                        }

                        // Switch Right-to-Left Direction (Manga R-to-L)
                        if (readingMode == ComicReadingMode.HORIZONTAL) {
                            IconButton(
                                onClick = { isRightToLeft = !isRightToLeft }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SwapHoriz,
                                    contentDescription = "翻页方向",
                                    tint = if (isRightToLeft) MintPrimary else Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Overlay Bottom Controls Slider
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(tween(200)) + slideInVertically { it },
            exit = fadeOut(tween(200)) + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.85f)
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isRightToLeft && readingMode == ComicReadingMode.HORIZONTAL) "日漫模式(右至左)" else "常规模式(左至右)",
                            color = MintPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${currentPageIndex + 1} / $totalPages 页",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (totalPages > 1) {
                        Slider(
                            value = currentPageIndex.toFloat(),
                            onValueChange = { newPage ->
                                currentPageIndex = newPage.toInt().coerceIn(0, totalPages - 1)
                            },
                            valueRange = 0f..(totalPages - 1).toFloat(),
                            steps = (totalPages - 2).coerceAtLeast(0),
                            colors = SliderDefaults.colors(
                                thumbColor = MintPrimary,
                                activeTrackColor = MintPrimary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ZoomableComicPage(
    imagePath: String,
    onTapLeft: () -> Unit,
    onTapRight: () -> Unit,
    onTapCenter: () -> Unit
) {
    var pageWidth by remember { mutableIntStateOf(0) }
    val zoomState = rememberZoomState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { pageWidth = it.width },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = File(imagePath),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .zoomable(
                    zoomState = zoomState,
                    onTap = { tapOffset ->
                        val w = pageWidth.toFloat()
                        when {
                            tapOffset.x < w * 0.3f -> onTapLeft()
                            tapOffset.x > w * 0.7f -> onTapRight()
                            else -> onTapCenter()
                        }
                    }
                ),
            contentScale = ContentScale.Fit
        )
    }
}
