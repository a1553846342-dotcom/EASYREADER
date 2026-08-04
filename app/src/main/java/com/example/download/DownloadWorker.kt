package com.example.download

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.BookRepository
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

        fun validateFileIntegrity(file: File, format: String): Boolean {
            if (!file.exists() || file.length() == 0L) return false
            
            // 1. Check if the file is accidentally an HTML error page (e.g. 404/500 disguised as download)
            try {
                file.inputStream().use { stream ->
                    val headBuffer = ByteArray(512)
                    val readBytes = stream.read(headBuffer)
                    if (readBytes > 0) {
                        val headStr = String(headBuffer, 0, readBytes, java.nio.charset.StandardCharsets.UTF_8).lowercase()
                        if (headStr.contains("<!doctype html") || headStr.contains("<html") || headStr.contains("<head>")) {
                            Log.e(TAG, "File ${file.name} is disguised HTML error page!")
                            return false
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading header stream for HTML check: ${e.message}")
            }

            // 2. Format specific ZIP / EPUB checks
            if (format.lowercase() == "epub") {
                return try {
                    java.util.zip.ZipFile(file).use { zip ->
                        val hasContainer = zip.getEntry("META-INF/container.xml") != null
                        val hasMime = zip.getEntry("mimetype") != null
                        hasContainer || hasMime
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "EPUB Zip structural verification failed for ${file.name}", e)
                    false
                }
            }
            return true
        }
    }

    private val client = run {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .dns(ZLibraryDns.INSTANCE)
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
            if (!cookie.isNullOrBlank()) {
                requestBuilder.header("Cookie", cookie)
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
                    tempFile.renameTo(finalFile)
                    taskDao.updateProgressAndStatus(bookId, DownloadStatus.COMPLETED, tempFile.length(), tempFile.length(), null)
                    repository.importBookFromUri(Uri.fromFile(finalFile), "$title.$format")
                    DownloadProgressBroadcaster.updateState(bookId, DownloadState.Success(finalFile.absolutePath))
                    Log.i(TAG, "File completed locally via 416 recovery: path=${finalFile.absolutePath}")
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
            if (tempFile.exists()) {
                if (!validateFileIntegrity(tempFile, format)) {
                    tempFile.delete()
                    val errorMsg = "文件校验失败：非有效的 ${format.uppercase()} 电子书格式或服务器返回了 HTML 错误页"
                    Log.e(TAG, "File integrity check failed for bookId=$bookId")
                    taskDao.updateProgressAndStatus(bookId, DownloadStatus.FAILED, 0L, 0L, errorMsg)
                    DownloadProgressBroadcaster.updateState(bookId, DownloadState.Error(errorMsg))
                    return@withContext Result.failure()
                }

                if (finalFile.exists()) {
                    finalFile.delete()
                }
                tempFile.renameTo(finalFile)
            }

            val finalLength = finalFile.length()
            val md5Hash = calculateMD5(finalFile)
            Log.i(TAG, "Download finished successfully for bookId=$bookId. Final length=$finalLength bytes, MD5=$md5Hash. Importing into repository...")
            taskDao.updateProgressAndStatus(
                id = bookId,
                status = DownloadStatus.COMPLETED,
                downloadedBytes = finalLength,
                totalBytes = finalLength,
                errorMessage = null
            )

            // Import into local Book database automatically
            val importFileName = "$title.$format"
            repository.importBookFromUri(Uri.fromFile(finalFile), importFileName)

            DownloadProgressBroadcaster.updateState(bookId, DownloadState.Success(finalFile.absolutePath))
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
