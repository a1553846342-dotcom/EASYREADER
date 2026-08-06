package com.example.library

import android.content.Context
import android.util.Log
import com.example.data.Book
import com.example.data.BookDao
import com.example.data.Chapter
import com.example.source.ComicChapter
import com.example.source.SearchBook
import com.example.source.js.JsCookieJar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads one online comic chapter as a local comic book:
 * images are saved into a folder and registered in Room, so the existing
 * local comic reader can open it offline (same format as imported CBZ).
 */
object ComicLocalImporter {

    private const val TAG = "ComicDownload"
    private const val UA = "EASYREADER/0.21 (CialloReader)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun importChapter(
        context: Context,
        bookDao: BookDao,
        book: SearchBook,
        chapter: ComicChapter,
        imageUrls: List<String>,
        headers: Map<String, Map<String, String>> = emptyMap(),
        referer: String? = null,
        onProgress: (Float) -> Unit,
        targetDir: File? = null,
        startIndex: Int = 0
    ): Result<Book> = withContext(Dispatchers.IO) {
        var comicDir: File? = null
        try {
            if (imageUrls.isEmpty()) {
                return@withContext Result.failure(Exception("章节没有图片"))
            }

            comicDir = targetDir ?: File(context.filesDir, "comics_${System.currentTimeMillis()}")
            if (!comicDir.exists()) comicDir.mkdirs()

            val pageFiles = mutableListOf<File>()
            // 断点续传：先收集目标目录里已下载的图片
            if (startIndex > 0) {
                comicDir.listFiles()
                    ?.filter { it.isFile && it.name.startsWith("img_") }
                    ?.sortedBy {
                        it.name.substringAfter("img_").substringBefore('.').toIntOrNull() ?: Int.MAX_VALUE
                    }
                    ?.let { pageFiles.addAll(it) }
            }
            for (index in startIndex until imageUrls.size) {
                coroutineContext.ensureActive()
                val url = imageUrls[index]
                val rawExt = url.substringAfterLast('.', "").substringBefore('?').lowercase()
                val ext = if (rawExt.length in 3..4 && rawExt.all { it.isLetterOrDigit() }) rawExt else "jpg"
                val pageFile = File(comicDir, String.format("img_%04d.%s", index + 1, ext))
                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", UA)
                headers[url]?.forEach { (k, v) ->
                    requestBuilder.header(k, v)
                }
                val cookie = JsCookieJar.cookieHeader(context, url)
                if (cookie.isNotBlank() &&
                    headers[url]?.keys?.none { it.equals("Cookie", ignoreCase = true) } != false
                ) {
                    requestBuilder.header("Cookie", cookie)
                }
                if (!referer.isNullOrBlank()) {
                    requestBuilder.header("Referer", referer)
                }
                val request = requestBuilder.build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("图片下载失败 HTTP ${response.code} (${url.take(80)})")
                    }
                    val raw = response.body?.bytes() ?: throw IOException("图片响应为空")
                    val bytes = if (MhttuImageDecryptor.isEncryptedHost(
                            try { java.net.URL(url).host } catch (e: Exception) { "" }
                        )
                    ) {
                        MhttuImageDecryptor.decryptIfNeeded(raw)
                    } else {
                        raw
                    }
                    pageFile.outputStream().use { output -> output.write(bytes) }
                }
                pageFiles.add(pageFile)
                onProgress((index + 1) / imageUrls.size.toFloat())
            }

            coroutineContext.ensureActive()
            if (pageFiles.isEmpty()) {
                comicDir?.deleteRecursively()
                return@withContext Result.failure(Exception("没有下载到任何图片"))
            }

            val coverDir = File(context.filesDir, "comic_covers")
            if (!coverDir.exists()) coverDir.mkdirs()
            val coverFile = File(coverDir, "cover_${System.currentTimeMillis()}.jpg")
            pageFiles.first().copyTo(coverFile, overwrite = true)

            val cleanTitle = "${book.title} · ${chapter.title}"
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .trim()
                .take(120)
                .ifBlank { book.title }

            val newBook = Book(
                title = cleanTitle,
                author = book.author.ifBlank { "漫画" },
                filePath = comicDir.absolutePath,
                coverUri = coverFile.absolutePath,
                totalChapters = pageFiles.size,
                contentType = "COMIC"
            )
            val bookId = bookDao.insertBook(newBook).toInt()
            bookDao.insertChapters(
                pageFiles.mapIndexed { index, file ->
                    Chapter(
                        bookId = bookId,
                        chapterOrder = index,
                        title = "第 ${index + 1} 页",
                        content = file.absolutePath
                    )
                }
            )

            Log.i(TAG, "downloaded chapter ${chapter.title} -> ${pageFiles.size} pages")
            Result.success(newBook.copy(id = bookId))
        } catch (e: CancellationException) {
            // 暂停/取消：保留目标目录，由调用方决定删除还是续传
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "chapter download failed", t)
            // 只有新建目录时失败才清理；续传目录由调用方（暂停/取消）管理
            if (targetDir == null) comicDir?.deleteRecursively()
            Result.failure(Exception(t.localizedMessage ?: "章节下载失败"))
        }
    }
}
