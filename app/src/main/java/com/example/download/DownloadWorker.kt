package com.example.download

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.BookRepository
import com.example.source.zlibrary.DiamWallInterceptor
import com.example.source.zlibrary.EncryptedCookieJar
import com.example.source.zlibrary.ZLibraryCredentialStorage
import com.example.source.zlibrary.network.SystemProxyResolver
import com.example.source.zlibrary.network.ZLibraryDns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class DownloadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DownloadWorker"
    }

    private val client = run {
        val credentialStorage = ZLibraryCredentialStorage(applicationContext)
        val cookieJar = EncryptedCookieJar(credentialStorage)
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            // 大文件/CDN 末端偶发慢速传输，给足读超时避免 99% 处被误杀
            .readTimeout(120, TimeUnit.SECONDS)
            .dns(ZLibraryDns.INSTANCE)
            .cookieJar(cookieJar)
            // 下载文件同样会触发 DiamWall PoW，带上求解器 + Cookie 存储，
            // 否则文件请求会被 503 挑战直接拦下。
            .addInterceptor(DiamWallInterceptor(cookieJar))
        // Downloads must follow the same network path as the rest of the app (system proxy)
        SystemProxyResolver.resolve(applicationContext)?.let { builder.proxy(it) }
        builder.build()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val bookId = inputData.getString("book_id") ?: return@withContext Result.failure()
        val url = inputData.getString("url") ?: return@withContext Result.failure()
        val format = inputData.getString("format") ?: "epub"
        val title = inputData.getString("title") ?: "Unknown"

        Log.d(TAG, "Starting download task: bookId=$bookId, url=$url, title=$title")

        val db = AppDatabase.getDatabase(context)
        val taskDao = db.downloadTaskDao()
        val repository = BookRepository(context, db.bookDao())

        val downloadsDir = File(context.filesDir, "downloads")
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        val safeBookId = com.example.download.DownloadManager.sanitizeFileName(bookId)
        val tempFile = File(downloadsDir, "$safeBookId.tmp")
        val finalFile = File(downloadsDir, "$safeBookId.$format")

        var existingLength = if (tempFile.exists()) tempFile.length() else 0L
        Log.d(TAG, "Check existing temp file for bookId=$bookId: bytes=$existingLength")

        // Update DB and Memory state to DOWNLOADING
        val task = taskDao.getTaskById(bookId)
        if (task != null) {
            taskDao.updateProgressAndStatus(
                id = bookId,
                status = DownloadStatus.DOWNLOADING,
                downloadedBytes = existingLength,
                totalBytes = task.totalBytes,
                errorMessage = null
            )
        }

        try {
            val requestBuilder = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

            val referer = inputData.getString("referer")
            if (!referer.isNullOrBlank()) {
                requestBuilder.header("Referer", referer)
            }

            val cookie = inputData.getString("cookie")
            val requestHost = runCatching { requestBuilder.build().url.host }.getOrNull()?.lowercase() ?: ""
            val isCdnHost = requestHost.contains("ncdn") ||
                requestHost.contains("cdn-zlib") ||
                requestHost.contains("s3proxy") ||
                requestHost.contains("dln")
            if (!cookie.isNullOrBlank() && !isCdnHost) {
                // eapi 直链是带签名授权的 CDN 链接，不需要 zlib 会话 Cookie；
                // 把 zlib Cookie 发给 CDN 会被部分 CDN 拒绝并返回 HTML 错误页。
                requestBuilder.header("Cookie", cookie)
            } else if (cookie.isNullOrBlank().not()) {
                Log.i(TAG, "Skipping zlib Cookie header for CDN host $requestHost")
            }

            if (existingLength > 0) {
                Log.i(TAG, "Requesting Range for breakpoint download: bytes=$existingLength- for bookId=$bookId")
                requestBuilder.header("Range", "bytes=$existingLength-")
            }
            val request = requestBuilder.build()

            val response = executeWithRetry(client, request)

            Log.i(TAG, "HTTP Response Code: ${response.code} for bookId=$bookId")

            if (!response.isSuccessful && response.code != 416) {
                val errMsg = "HTTP Error: ${response.code}"
                Log.e(TAG, "Download failed with HTTP error: ${response.code} for bookId=$bookId")
                taskDao.updateProgressAndStatus(bookId, DownloadStatus.FAILED, existingLength, 0L, errMsg)
                DownloadProgressBroadcaster.updateState(bookId, DownloadState.Error(errMsg))
                return@withContext Result.failure()
            }

            var append = false
            var totalBytes = 0L

            if (response.code == 206) { // Partial content
                append = true
                val body = response.body ?: throw Exception("Empty response body")
                val contentLength = body.contentLength()
                totalBytes = if (contentLength > 0) existingLength + contentLength else task?.totalBytes ?: 0L
                Log.i(TAG, "HTTP 206 Partial Content confirmed. existingBytes=$existingLength, remainingBytes=$contentLength, totalBytes=$totalBytes")
            } else if (response.code == 200) { // Full content
                append = false
                existingLength = 0L
                val body = response.body ?: throw Exception("Empty response body")
                totalBytes = body.contentLength()
                Log.i(TAG, "HTTP 200 Full Content. totalBytes=$totalBytes")
            } else if (response.code == 416) { // Range Not Satisfiable
                Log.w(TAG, "HTTP 416 Range Not Satisfiable for bookId=$bookId. Testing local file completeness.")
                if (tempFile.exists() && tempFile.length() > 0) {
                    val integrity = DownloadFileValidator.validateFileIntegrity(tempFile, format)
                    if (!integrity.valid) {
                        tempFile.delete()
                        val reason = if (integrity.isHtmlErrorPage) {
                            integrity.htmlErrorHint ?: cdnHtmlReason(url) ?: "服务器返回了 HTML 错误页"
                        } else {
                            "非有效的 ${format.uppercase()} 电子书格式"
                        }
                        val errorMsg = "文件校验失败：$reason"
                        taskDao.updateProgressAndStatus(bookId, DownloadStatus.FAILED, 0L, 0L, errorMsg)
                        DownloadProgressBroadcaster.updateState(bookId, DownloadState.Error(errorMsg))
                        return@withContext Result.failure()
                    }
                    val actualFormat = integrity.actualFormat ?: format.lowercase()
                    val completedFile = if (actualFormat.equals(format, ignoreCase = true)) {
                        finalFile
                    } else {
                        File(downloadsDir, "$safeBookId.$actualFormat")
                    }
                    if (completedFile.exists()) {
                        completedFile.delete()
                    }
                    tempFile.renameTo(completedFile)
                    if (!actualFormat.equals(format, ignoreCase = true)) {
                        task?.let { t ->
                            taskDao.insertOrUpdate(
                                t.copy(format = actualFormat, filePath = completedFile.absolutePath)
                            )
                        }
                    }
                    taskDao.updateProgressAndStatus(
                        bookId,
                        DownloadStatus.COMPLETED,
                        completedFile.length(),
                        completedFile.length(),
                        null
                    )
                    val importResult = repository.importBookFromUri(
                        Uri.fromFile(completedFile),
                        "$title.$actualFormat",
                        forcePdfPlaceholder = actualFormat.equals("pdf", ignoreCase = true)
                    )
                    // TXT 全文已完整落入数据库，原始下载文件是纯冗余，删掉省空间；
                    // EPUB/漫画目前仍可能按需读原文件，不做处理。
                    if (importResult.isSuccess && actualFormat.equals("txt", ignoreCase = true)) {
                        runCatching { completedFile.delete() }
                        Log.i(TAG, "TXT import succeeded via 416 recovery, removed raw file: ${completedFile.absolutePath}")
                    }
                    DownloadProgressBroadcaster.updateState(bookId, DownloadState.Success(completedFile.absolutePath))
                    Log.i(TAG, "File completed locally via 416 recovery: path=${completedFile.absolutePath}")
                    return@withContext Result.success()
                } else {
                    taskDao.updateProgressAndStatus(bookId, DownloadStatus.FAILED, 0L, 0L, "Invalid Range")
                    DownloadProgressBroadcaster.updateState(bookId, DownloadState.Error("Invalid Range"))
                    return@withContext Result.failure()
                }
            }

            val body = response.body ?: throw Exception("Empty body")
            var downloaded = existingLength
            val buffer = ByteArray(8 * 1024)
            var read: Int
            var lastLogTime = System.currentTimeMillis()
            var lastProgressTime = System.currentTimeMillis()
            var lastReportedProgress = -1f

            body.byteStream().use { inputStream ->
                FileOutputStream(tempFile, append).use { outputStream ->
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        if (isStopped) {
                            outputStream.flush()
                            val currentDownloaded = tempFile.length()
                            Log.i(TAG, "Download worker stopped/paused for bookId=$bookId at bytes=$currentDownloaded")
                            taskDao.updateProgressAndStatus(
                                id = bookId,
                                status = DownloadStatus.PAUSED,
                                downloadedBytes = currentDownloaded,
                                totalBytes = totalBytes,
                                errorMessage = null
                            )
                            DownloadProgressBroadcaster.updateState(
                                bookId,
                                DownloadState.Paused(currentDownloaded, totalBytes)
                            )
                            return@withContext Result.success()
                        }

                        outputStream.write(buffer, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        if (now - lastLogTime > 2000) { // Log progress every 2s
                            Log.d(TAG, "Downloading bookId=$bookId progress: $downloaded / $totalBytes bytes")
                            lastLogTime = now
                        }

                        val progress = if (totalBytes > 0) downloaded.toFloat() / totalBytes.toFloat() else 0f
                        // 进度节流：≥300ms 或进度变化 ≥1% 才推送一次，
                        // 避免每 8KB 更新一次导致整个书库列表持续重组。
                        if (now - lastProgressTime >= 300 || progress - lastReportedProgress >= 0.01f) {
                            lastProgressTime = now
                            lastReportedProgress = progress
                            DownloadProgressBroadcaster.updateState(
                                bookId,
                                DownloadState.Downloading(downloaded, totalBytes, progress)
                            )
                        }
                    }
                    outputStream.flush()
                }
            }

            // Finished reading
            var actualFormat = format.lowercase()
            var actualFinalFile = finalFile
            if (tempFile.exists()) {
                val integrity = DownloadFileValidator.validateFileIntegrity(tempFile, format)
                if (!integrity.valid) {
                    tempFile.delete()
                    val reason = if (integrity.isHtmlErrorPage) {
                        integrity.htmlErrorHint ?: cdnHtmlReason(url) ?: "服务器返回了 HTML 错误页"
                    } else {
                        "非有效的 ${format.uppercase()} 电子书格式"
                    }
                    val errorMsg = "文件校验失败：$reason"
                    Log.e(TAG, "File integrity check failed for bookId=$bookId: $reason")
                    taskDao.updateProgressAndStatus(bookId, DownloadStatus.FAILED, 0L, 0L, errorMsg)
                    DownloadProgressBroadcaster.updateState(bookId, DownloadState.Error(errorMsg))
                    return@withContext Result.failure()
                }

                actualFormat = integrity.actualFormat ?: format.lowercase()
                actualFinalFile = if (actualFormat.equals(format, ignoreCase = true)) {
                    finalFile
                } else {
                    File(downloadsDir, "$safeBookId.$actualFormat")
                }
                if (actualFinalFile.exists()) {
                    actualFinalFile.delete()
                }
                tempFile.renameTo(actualFinalFile)

                // 检测出的真实格式与任务记录不一致时，同步更新任务，保证下载中心/书架路径一致
                if (!actualFormat.equals(format, ignoreCase = true)) {
                    task?.let { t ->
                        taskDao.insertOrUpdate(
                            t.copy(format = actualFormat, filePath = actualFinalFile.absolutePath)
                        )
                    }
                }
            }

            val finalLength = actualFinalFile.length()
            val md5Hash = calculateMD5(actualFinalFile)
            Log.i(
                TAG,
                "Download finished successfully for bookId=$bookId. Final length=$finalLength bytes, format=$actualFormat, MD5=$md5Hash. Importing into repository..."
            )
            taskDao.updateProgressAndStatus(
                id = bookId,
                status = DownloadStatus.COMPLETED,
                downloadedBytes = finalLength,
                totalBytes = finalLength,
                errorMessage = null
            )

            // Import into local Book database automatically
            val importFileName = "$title.$actualFormat"
            val importResult = repository.importBookFromUri(
                Uri.fromFile(actualFinalFile),
                importFileName,
                forcePdfPlaceholder = actualFormat.equals("pdf", ignoreCase = true)
            )

            // TXT 全文已完整落入数据库，原始下载文件是纯冗余，删掉省空间；
            // 导入失败时保留文件，方便用户重试或排查问题；EPUB/漫画不动。
            if (importResult.isSuccess && actualFormat.equals("txt", ignoreCase = true)) {
                runCatching { actualFinalFile.delete() }
                Log.i(TAG, "TXT import succeeded, removed raw downloaded file to save space: ${actualFinalFile.absolutePath}")
            } else if (!importResult.isSuccess) {
                Log.w(TAG, "Import failed, keeping raw downloaded file for retry: ${actualFinalFile.absolutePath}")
            }

            DownloadProgressBroadcaster.updateState(bookId, DownloadState.Success(actualFinalFile.absolutePath))
            Log.i(TAG, "Successfully imported bookId=$bookId into BookRepository.")
            Result.success()

        } catch (e: Exception) {
            if (isStopped) {
                val currentLength = if (tempFile.exists()) tempFile.length() else 0L
                Log.i(TAG, "Download caught stopped/paused exception for bookId=$bookId. Bytes saved=$currentLength")
                taskDao.updateProgressAndStatus(bookId, DownloadStatus.PAUSED, currentLength, 0L, null)
                DownloadProgressBroadcaster.updateState(bookId, DownloadState.Paused(currentLength, 0L))
                return@withContext Result.success()
            }
            val currentLength = if (tempFile.exists()) tempFile.length() else 0L
            val errorMsg = e.message ?: "Download failed"
            Log.e(TAG, "Download failed with exception for bookId=$bookId: $errorMsg", e)
            taskDao.updateProgressAndStatus(bookId, DownloadStatus.FAILED, currentLength, 0L, errorMsg)
            DownloadProgressBroadcaster.updateState(bookId, DownloadState.Error(errorMsg))
            Result.failure()
        }
    }

    private fun calculateMD5(file: File): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("MD5")
            file.inputStream().use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "unknown_md5"
        }
    }

    /** eapi CDN 直链（dln1.ncdn.ec/redirection）过期后服务器会返回 HTML 页，给出明确提示。 */
    private fun cdnHtmlReason(url: String?): String? {
        val lower = url?.lowercase() ?: return null
        return if (lower.contains("ncdn") || lower.contains("redirection") || lower.contains("cdn-zlib")) {
            "下载链接可能已过期（CDN 返回 HTML），请重新下载"
        } else {
            null
        }
    }

    /** Retry transient 5xx (e.g. DiamWall 502) and IO errors up to 3 times. */
    private fun executeWithRetry(client: OkHttpClient, request: Request): Response {
        var lastError: Exception? = null
        for (attempt in 1..3) {
            try {
                val r = client.newCall(request).execute()
                if (r.code in 500..599 && attempt < 3) {
                    Log.w(TAG, "HTTP ${r.code} on attempt $attempt, retrying")
                    r.close()
                    Thread.sleep(2000)
                    continue
                }
                return r
            } catch (e: Exception) {
                lastError = e
                if (attempt < 3) {
                    Log.w(TAG, "Attempt $attempt failed: ${e.message}; retrying")
                    Thread.sleep(2000)
                }
            }
        }
        throw lastError ?: Exception("download failed")
    }
}
