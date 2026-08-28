package com.example.library

import android.content.Context
import android.util.Log
import com.example.data.Book
import com.example.data.BookDao
import com.example.data.Chapter
import com.example.source.ComicChapter
import com.example.source.SearchBook
import com.example.source.js.JsCookieJar
import com.example.source.js.JsImageProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Protocol
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

    @Volatile
    private var cachedClient: OkHttpClient? = null

    /** 命中 JS 源代理路由（如 picacg）的图片域名走显式/系统代理，其余直连。 */
    private fun client(context: Context): OkHttpClient =
        cachedClient ?: synchronized(this) {
            cachedClient ?: OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .protocols(listOf(Protocol.HTTP_1_1))
                .proxySelector(com.example.source.js.JsSourceProxy.selector(context.applicationContext))
                .build()
                .also { cachedClient = it }
        }

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
        startIndex: Int = 0,
        resolveImage: (suspend (String) -> String?)? = null,
        resolveHeaders: (suspend (String) -> Map<String, String>)? = null,
        concurrency: Int = 3
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
            // 并发下载（默认 3 路），按 index 顺序收集结果，避免大章节逐张串行
            val semaphore = Semaphore(concurrency.coerceAtLeast(1))
            suspend fun downloadPage(index: Int): File {
                coroutineContext.ensureActive()
                val url = imageUrls[index]
                var fetchUrl = url
                var fetchHeaders = headers[url].orEmpty()
                if (resolveImage != null) {
                    val resolved = resolveImage(url)
                    if (!resolved.isNullOrBlank() && resolved != url) {
                        fetchUrl = resolved
                        fetchHeaders = resolveHeaders?.invoke(resolved)
                            ?.takeIf { it.isNotEmpty() } ?: fetchHeaders
                    }
                }
                val rawExt = fetchUrl.substringAfterLast('.', "").substringBefore('?').lowercase()
                val ext = if (rawExt.length in 3..4 && rawExt.all { it.isLetterOrDigit() }) rawExt else "jpg"
                val pageFile = File(comicDir, String.format("img_%04d.%s", index + 1, ext))
                if (fetchUrl.startsWith("file:")) {
                    val srcFile = java.io.File(java.net.URI.create(fetchUrl))
                    if (srcFile.exists() && srcFile.length() > 0) {
                        srcFile.copyTo(pageFile, overwrite = true)
                        onProgress((index + 1) / imageUrls.size.toFloat())
                        return pageFile
                    }
                }
                val requestBuilder = Request.Builder()
                    .url(fetchUrl)
                    .header("User-Agent", UA)
                fetchHeaders.forEach { (k, v) ->
                    requestBuilder.header(k, v)
                }
                if (fetchHeaders.keys.none { it.equals("Accept", ignoreCase = true) }) {
                    requestBuilder.header("Accept", "image/webp,image/jpeg,image/png,*/*;q=0.8")
                }
                val cookie = JsCookieJar.cookieHeader(context, url)
                if (cookie.isNotBlank() &&
                    fetchHeaders.keys.none { it.equals("Cookie", ignoreCase = true) }
                ) {
                    requestBuilder.header("Cookie", cookie)
                }
                if (!referer.isNullOrBlank() && fetchHeaders.keys.none { it.equals("Referer", ignoreCase = true) }) {
                    requestBuilder.header("Referer", referer)
                }
                val request = requestBuilder.build()
                client(context).newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("图片下载失败 HTTP ${response.code} (${url.take(80)})")
                    }
                    val raw = response.body?.bytes() ?: throw IOException("图片响应为空")
                    var bytes = ImageBytes.normalizeImage(raw, response.header("Content-Encoding"))
                    if (ImageBytes.isAvif(bytes) && !ImageBytes.decodeOk(bytes)) {
                        for (candidate in ImageBytes.webpVariants(url)) {
                            try {
                                val rb = Request.Builder().url(candidate)
                                    .header("User-Agent", UA)
                                    .header("Accept", "image/webp,image/jpeg,image/png,*/*;q=0.8")
                                val cookie2 = JsCookieJar.cookieHeader(context, candidate)
                                if (cookie2.isNotBlank()) rb.header("Cookie", cookie2)
                                if (!referer.isNullOrBlank()) rb.header("Referer", referer)
                                client(context).newCall(rb.build()).execute().use { r2 ->
                                    if (r2.isSuccessful) {
                                        val b2 = r2.body?.bytes()
                                        if (b2 != null) {
                                            val p2 = ImageBytes.normalizeImage(b2, r2.header("Content-Encoding"))
                                            if (!ImageBytes.isAvif(p2) && ImageBytes.decodeOk(p2)) {
                                                bytes = p2
                                                return@use
                                            }
                                        }
                                    }
                                }
                                if (!ImageBytes.isAvif(bytes) && ImageBytes.decodeOk(bytes)) break
                            } catch (e: Exception) {
                                // 尝试下一个候选
                            }
                        }
                    }
                    bytes = if (MhttuImageDecryptor.isEncryptedHost(
                            try { java.net.URL(url).host } catch (e: Exception) { "" }
                        )
                    ) {
                        MhttuImageDecryptor.decryptIfNeeded(bytes)
                    } else {
                        bytes
                    }
                    bytes = JsImageProcessor.transform(url, bytes) ?: bytes
                    pageFile.outputStream().use { output -> output.write(bytes) }
                }
                onProgress((index + 1) / imageUrls.size.toFloat())
                return pageFile
            }
            val downloaded = coroutineScope {
                (startIndex until imageUrls.size).map { index ->
                    async { semaphore.withPermit { downloadPage(index) } }
                }.awaitAll()
            }
            pageFiles.addAll(downloaded)

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
