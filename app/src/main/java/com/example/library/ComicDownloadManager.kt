package com.example.library

import android.app.Application
import com.example.data.AppDatabase
import com.example.source.ComicChapter
import com.example.source.ComicSource
import com.example.source.SearchBook
import com.example.source.SourceResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class ComicDownloadStatus { DOWNLOADING, PAUSED, FAILED, SUCCESS }

data class ComicDownloadTask(
    val chapterId: String,
    val book: SearchBook,
    val chapter: ComicChapter,
    val status: ComicDownloadStatus,
    val progress: Float = 0f,
    val error: String? = null
)

/**
 * 漫画章节下载中心：应用级单例，持有独立协程作用域，
 * 切换页面/重建 ViewModel 都不会中断下载；失败任务保留供重试。
 */
object ComicDownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _tasks = MutableStateFlow<Map<String, ComicDownloadTask>>(emptyMap())
    val tasks: StateFlow<Map<String, ComicDownloadTask>> = _tasks.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val jobs = HashMap<String, Job>()
    private val dirs = HashMap<String, File>()
    private val paused = HashSet<String>()

    fun start(
        app: Application,
        database: AppDatabase,
        book: SearchBook,
        chapter: ComicChapter,
        source: ComicSource
    ) {
        val id = chapter.id
        val existing = _tasks.value[id]
        if (existing?.status == ComicDownloadStatus.DOWNLOADING) return
        paused.remove(id)
        _tasks.value = _tasks.value + (id to ComicDownloadTask(
            chapterId = id,
            book = book,
            chapter = chapter,
            status = ComicDownloadStatus.DOWNLOADING,
            progress = existing?.progress ?: 0f,
            error = null
        ))
        jobs.remove(id)?.cancel()
        jobs[id] = scope.launch {
            var completed = false
            try {
                val images = when (val r = source.getChapterImages(chapter.id)) {
                    is SourceResult.Success -> r.data
                    is SourceResult.Error -> throw Exception(r.exception.message ?: "获取图片列表失败")
                }
                val imageHeaders = source.getChapterImageHeaders(chapter.id, images)
                val dir = dirs[id] ?: File(app.filesDir, "comics_${System.currentTimeMillis()}")
                    .apply { mkdirs() }
                    .also { dirs[id] = it }
                val startIndex = (existing?.progress ?: 0f)
                    .let { p -> (p * images.size).toInt().coerceIn(0, (images.size - 1).coerceAtLeast(0)) }
                val result = ComicLocalImporter.importChapter(
                    context = app,
                    bookDao = database.bookDao(),
                    book = book,
                    chapter = chapter,
                    imageUrls = images,
                    headers = imageHeaders,
                    targetDir = dir,
                    startIndex = startIndex,
                    resolveImage = { url -> source.resolveChapterImage(url) },
                    resolveHeaders = { url -> source.getResolvedHeaders(url) },
                    concurrency = 3,
                    onProgress = { p -> update(id) { it.copy(progress = p) } }
                )
                result.onSuccess {
                    completed = true
                    update(id) { it.copy(status = ComicDownloadStatus.SUCCESS, progress = 1f) }
                    _message.value = "已下载到书架：《${it.title}》"
                }.onFailure { e ->
                    update(id) { task -> task.copy(status = ComicDownloadStatus.FAILED, error = e.message ?: "章节下载失败") }
                    _message.value = "下载失败：${e.message ?: "章节下载失败"}"
                }
            } catch (e: CancellationException) {
                if (paused.contains(id)) update(id) { it.copy(status = ComicDownloadStatus.PAUSED) }
                throw e
            } catch (e: Exception) {
                update(id) { it.copy(status = ComicDownloadStatus.FAILED, error = e.message ?: "章节下载失败") }
                _message.value = "下载失败：${e.message ?: "章节下载失败"}"
            } finally {
                jobs.remove(id)
                paused.remove(id)
            }
        }
    }

    fun pause(chapterId: String) {
        val t = _tasks.value[chapterId] ?: return
        if (t.status != ComicDownloadStatus.DOWNLOADING) return
        paused.add(chapterId)
        jobs[chapterId]?.cancel()
    }

    fun cancel(chapterId: String) {
        paused.remove(chapterId)
        jobs.remove(chapterId)?.cancel()
        _tasks.value = _tasks.value - chapterId
        dirs.remove(chapterId)?.deleteRecursively()
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun update(chapterId: String, transform: (ComicDownloadTask) -> ComicDownloadTask) {
        val cur = _tasks.value[chapterId] ?: return
        _tasks.value = _tasks.value + (chapterId to transform(cur))
    }
}
