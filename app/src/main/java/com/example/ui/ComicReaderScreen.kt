package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.data.Book
import com.example.data.Chapter
import com.example.data.ReadingSession
import com.example.ui.comic.ComicPageRef
import com.example.ui.comic.ComicReaderCore

/**
 * 本地漫画阅读页（升级版）：
 * 由统一阅读引擎 [ComicReaderCore] 驱动 —— 单页/双页/条漫/无缝滚动/磁吸、
 * 三方向、缩放手势、裁边/拆页/合页、画质增强、滤镜、背景/场景、自动阅读、
 * 预设与每漫画独立配置、进度缩略图目录、连续阅读上一本/下一本。
 * 对外签名保持不变，MainActivity 无需感知内部升级。
 */
@androidx.compose.animation.ExperimentalSharedTransitionApi
@Composable
fun ComicReaderScreen(
    book: Book?,
    chapters: List<Chapter>,
    onBack: () -> Unit,
    onUpdateProgress: (bookId: Int, pageIndex: Int, scrollOffset: Int, isFinished: Boolean) -> Unit,
    onRecordTime: (seconds: Long) -> Unit,
    onSessionEnd: (ReadingSession) -> Unit = {},
    /** 书架全部书籍（用于连续阅读：上一本 / 下一本） */
    libraryBooks: List<Book> = emptyList(),
    onOpenBook: ((Book) -> Unit)? = null,
) {
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

    // 阅读计时：只在 App 前台 + 屏幕亮着时累计
    ReadingTimerEffect(
        bookId = book.id,
        bookTitle = book.title,
        onFlush = { seconds -> onRecordTime(seconds) },
        onSessionEnd = { session -> onSessionEnd(session) }
    )

    val pages = remember(chapters) {
        chapters.map { ComicPageRef.Local(id = "b${it.bookId}_c${it.id}", path = it.content) }
    }
    val initialPage = remember(book.id) {
        book.currentChapterIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    }

    // 连续阅读：书架漫画按最近阅读排序，定位相邻两本
    val (prevBook, nextBook) = remember(book.id, libraryBooks) {
        val comics = libraryBooks.filter { it.isComic }
            .sortedByDescending { it.lastReadTime }
        val idx = comics.indexOfFirst { it.id == book.id }
        if (idx >= 0) (comicOrNull(comics, idx - 1) to comicOrNull(comics, idx + 1))
        else null to null
    }

    // 引擎每次翻页已保存真实进度；无需 dispose 兜底（旧兜底会用过期 initialPage 覆盖进度）

    // 本地漫画目录 = 页缩略图网格（toc 传空触发页网格模式）
    ComicReaderCore(
        pages = pages,
        title = book.title,
        chapterTitle = null,
        bookKey = "local_${book.id}",
        initialPage = initialPage,
        toc = emptyList(),
        currentChapterIndex = -1,
        onJumpToChapter = null,
        onPrevChapter = prevBook?.let { pb -> { onOpenBook?.invoke(pb) } },
        onNextChapter = nextBook?.let { nb -> { onOpenBook?.invoke(nb) } },
        chapterNavLabel = "本",
        onPageChanged = { raw, isFinished ->
            onUpdateProgress(book.id, raw, 0, isFinished)
        },
        onExit = onBack,
    )
}

private fun comicOrNull(books: List<Book>, idx: Int): Book? =
    if (idx in books.indices) books[idx] else null
