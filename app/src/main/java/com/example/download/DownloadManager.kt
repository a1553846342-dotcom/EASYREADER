package com.example.download

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.data.AppDatabase
import com.example.source.DownloadInfo
import com.example.source.SearchBook
import com.example.download.DownloadRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class DownloadManager(private val context: Context) {

    companion object {
        private const val TAG = "DownloadManager"

        /** Book ids contain slashes/Chinese (e.g. book/xxx/三体.html); sanitize for file paths. */
        fun sanitizeFileName(id: String): String {
            // Truncate to stay under filesystem filename limits (255 bytes) — long titles
            // like 球状闪电（…超长营销文案…） produce ENAMETOOLONG otherwise.
            return id.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100)
        }
    }

    private val db = AppDatabase.getDatabase(context)
    private val taskDao = db.downloadTaskDao()
    private val workManager = WorkManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    val allTasksFlow: Flow<List<DownloadTaskEntity>> = taskDao.getAllTasksFlow()
    val downloadStates: StateFlow<Map<String, DownloadState>> = DownloadProgressBroadcaster.states

    init {
        restoreTasks()
    }

    /** 已完成任务对应的书是否还在书架（按文件路径匹配，file:// 前缀归一化）。 */
    private suspend fun bookExistsForTask(task: DownloadTaskEntity): Boolean =
        runCatching {
            db.bookDao().getAllBooksSync().any { it.filePath.removePrefix("file://") == task.filePath }
        }.getOrDefault(false)

    private fun restoreTasks() {
        scope.launch {
            val unfinished = taskDao.getUnfinishedTasksSync()
            Log.i(TAG, "Restoring unfinished tasks from DB. Count=${unfinished.size}")
            for (task in unfinished) {
                if (task.status == DownloadStatus.DOWNLOADING) {
                    Log.i(TAG, "Restoring interrupted DOWNLOADING task to PAUSED: bookId=${task.id}")
                    taskDao.updateProgressAndStatus(
                        id = task.id,
                        status = DownloadStatus.PAUSED,
                        downloadedBytes = task.downloadedBytes,
                        totalBytes = task.totalBytes,
                        errorMessage = null
                    )
                    DownloadProgressBroadcaster.updateState(
                        task.id,
                        DownloadState.Paused(task.downloadedBytes, task.totalBytes)
                    )
                } else if (task.status == DownloadStatus.PAUSED) {
                    Log.i(TAG, "Restoring PAUSED task state in broadcaster: bookId=${task.id}")
                    DownloadProgressBroadcaster.updateState(
                        task.id,
                        DownloadState.Paused(task.downloadedBytes, task.totalBytes)
                    )
                } else if (task.status == DownloadStatus.COMPLETED) {
                    val finalFile = File(context.filesDir, "downloads/${sanitizeFileName(task.id)}.${task.format}")
                    Log.i(TAG, "Restoring COMPLETED task state in broadcaster: bookId=${task.id}")
                    DownloadProgressBroadcaster.updateState(
                        task.id,
                        DownloadState.Success(finalFile.absolutePath)
                    )
                }
            }
        }
    }

    fun enqueueDownload(request: DownloadRequest, referer: String? = null, headers: Map<String, String> = emptyMap()) {
        Log.i(TAG, "enqueueDownload requested for bookId=${request.bookId}, title=${request.title}")
        scope.launch {
            // Dedupe: don't enqueue a second task for the same book if one is already
            // pending/downloading/paused/completed. Failed tasks may be retried.
            val existing = taskDao.getTaskById(request.bookId)
            if (existing != null && existing.status != DownloadStatus.FAILED) {
                if (existing.status == DownloadStatus.COMPLETED) {
                    // 书架里是否还存在这本书；若已被删除，则清掉旧任务重新下载并重新入库
                    val stillOnShelf = bookExistsForTask(existing)
                    if (stillOnShelf) {
                        DownloadProgressBroadcaster.updateState(
                            request.bookId,
                            DownloadState.Success(existing.filePath)
                        )
                        return@launch
                    }
                    Log.i(TAG, "Completed task found but book removed from shelf; re-downloading ${request.bookId}")
                    taskDao.deleteTaskById(existing.id)
                    runCatching { File(existing.filePath).delete() }
                    runCatching {
                        File(File(existing.filePath).parentFile, "${sanitizeFileName(existing.id)}.tmp").delete()
                    }
                } else {
                    Log.i(TAG, "Task already exists for bookId=${request.bookId} (status=${existing.status}); skipping duplicate")
                    DownloadProgressBroadcaster.updateState(
                        request.bookId,
                        when (existing.status) {
                            DownloadStatus.PAUSED -> DownloadState.Paused(existing.downloadedBytes, existing.totalBytes)
                            DownloadStatus.DOWNLOADING -> DownloadState.Downloading(
                                existing.downloadedBytes,
                                existing.totalBytes,
                                if (existing.totalBytes > 0) {
                                    (existing.downloadedBytes.toFloat() / existing.totalBytes).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                            )
                            else -> DownloadState.Pending
                        }
                    )
                    return@launch
                }
            }

            val downloadsDir = File(context.filesDir, "downloads")
            val finalFormat = request.format
            val finalFilePath = File(downloadsDir, "${sanitizeFileName(request.bookId)}.$finalFormat").absolutePath

            val cookieHeader = headers["Cookie"] ?: headers["cookie"]

            val task = DownloadTaskEntity(
                id = request.bookId,
                sourceId = request.sourceId,
                title = request.title,
                author = request.author,
                coverUrl = request.coverUrl,
                downloadUrl = request.downloadUrl,
                format = finalFormat,
                status = DownloadStatus.PENDING,
                downloadedBytes = 0L,
                totalBytes = 0L,
                filePath = finalFilePath
            )

            taskDao.insertOrUpdate(task)
            DownloadProgressBroadcaster.updateState(request.bookId, DownloadState.Pending)

            enqueueWorker(
                bookId = request.bookId,
                url = request.downloadUrl,
                title = request.title,
                format = finalFormat,
                referer = referer,
                cookie = cookieHeader
            )
        }
    }

    fun pauseDownload(bookId: String) {
        Log.i(TAG, "pauseDownload requested for bookId=$bookId")
        scope.launch {
            workManager.cancelUniqueWork("download_$bookId")
            val task = taskDao.getTaskById(bookId)
            val downloadsDir = File(context.filesDir, "downloads")
            val tempFile = File(downloadsDir, "$bookId.tmp")
            val currentDownloaded = if (tempFile.exists()) tempFile.length() else task?.downloadedBytes ?: 0L

            Log.i(TAG, "Task paused for bookId=$bookId. Preserved temp bytes=$currentDownloaded")
            taskDao.updateProgressAndStatus(
                id = bookId,
                status = DownloadStatus.PAUSED,
                downloadedBytes = currentDownloaded,
                totalBytes = task?.totalBytes ?: 0L,
                errorMessage = null
            )
            DownloadProgressBroadcaster.updateState(
                bookId,
                DownloadState.Paused(currentDownloaded, task?.totalBytes ?: 0L)
            )
        }
    }

    fun resumeDownload(bookId: String) {
        Log.i(TAG, "resumeDownload requested for bookId=$bookId")
        scope.launch {
            val task = taskDao.getTaskById(bookId) ?: return@launch
            taskDao.updateProgressAndStatus(
                id = bookId,
                status = DownloadStatus.PENDING,
                downloadedBytes = task.downloadedBytes,
                totalBytes = task.totalBytes,
                errorMessage = null
            )
            DownloadProgressBroadcaster.updateState(bookId, DownloadState.Pending)

            enqueueWorker(bookId, task.downloadUrl, task.title, task.format)
        }
    }

    fun cancelDownload(bookId: String) {
        Log.i(TAG, "cancelDownload requested for bookId=$bookId. Cleaning up work and files.")
        scope.launch {
            workManager.cancelUniqueWork("download_$bookId")

            val downloadsDir = File(context.filesDir, "downloads")
            val tempFile = File(downloadsDir, "$bookId.tmp")
            if (tempFile.exists()) {
                val deleted = tempFile.delete()
                Log.i(TAG, "Temp file deletion for bookId=$bookId: $deleted")
            }

            val task = taskDao.getTaskById(bookId)
            if (task != null) {
                val finalFile = File(task.filePath)
                if (finalFile.exists()) {
                    finalFile.delete()
                }
            }

            taskDao.deleteTaskById(bookId)
            DownloadProgressBroadcaster.removeState(bookId)
            Log.i(TAG, "Task bookId=$bookId deleted from DB and memory state.")
        }
    }

    private fun enqueueWorker(
        bookId: String,
        url: String,
        title: String,
        format: String,
        referer: String? = null,
        cookie: String? = null
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = workDataOf(
            "book_id" to bookId,
            "url" to url,
            "title" to title,
            "format" to format,
            "referer" to (referer ?: ""),
            "cookie" to (cookie ?: "")
        )

        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .build()

        Log.i(TAG, "Enqueuing WorkManager task download_$bookId")
        workManager.enqueueUniqueWork(
            "download_$bookId",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun getDownloadState(bookId: String): DownloadState {
        return DownloadProgressBroadcaster.getState(bookId)
    }
}
