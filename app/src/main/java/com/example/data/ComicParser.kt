package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

object ComicParser {

    private const val TAG = "BookImport"
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")

    fun isComicFile(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".cbz") ||
                lower.endsWith(".zip") ||
                lower.endsWith(".pdf") ||
                lower.endsWith(".cbr") ||
                lower.endsWith(".cb7") ||
                lower.endsWith(".rar") ||
                lower.endsWith(".7z")
    }

    suspend fun importComic(
        context: Context,
        uri: Uri,
        fileName: String,
        bookDao: BookDao
    ): Result<Book> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "[ComicParser] Starting importComic for $fileName, uri: $uri")
            val lowerName = fileName.lowercase()

            if (lowerName.endsWith(".cbr") || lowerName.endsWith(".rar") || lowerName.endsWith(".cb7") || lowerName.endsWith(".7z")) {
                return@withContext Result.failure(
                    Exception("CBR/CB7为RAR/7Z压缩格式，请转换为CBZ或ZIP格式后导入。")
                )
            }

            val comicDir = File(context.filesDir, "comics_${System.currentTimeMillis()}")
            if (!comicDir.exists()) comicDir.mkdirs()

            val pageFiles = mutableListOf<File>()

            if (lowerName.endsWith(".pdf")) {
                Log.d(TAG, "[ComicParser] Extracting PDF pages...")
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: return@withContext Result.failure(Exception("无法打开PDF文件"))
                pfd.use { descriptor ->
                    val renderer = PdfRenderer(descriptor)
                    for (i in 0 until renderer.pageCount) {
                        try {
                            val page = renderer.openPage(i)
                            val scale = minOf(1080f / page.width, 1920f / page.height, 2.0f)
                            val width = (page.width * scale).toInt().coerceAtLeast(1)
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            page.close()

                            val pageFile = File(comicDir, String.format("page_%04d.jpg", i + 1))
                            FileOutputStream(pageFile).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                            }
                            bitmap.recycle()
                            pageFiles.add(pageFile)
                        } catch (t: Throwable) {
                            Log.e(TAG, "[ComicParser] Error processing PDF page $i", t)
                        }
                    }
                    renderer.close()
                }
            } else if (lowerName.endsWith(".cbz") || lowerName.endsWith(".zip")) {
                Log.d(TAG, "[ComicParser] Extracting ZIP/CBZ images...")
                val charsetsToTry = listOf(Charset.forName("GBK"), Charset.forName("UTF-8"))
                var zipExtracted = false

                for (charset in charsetsToTry) {
                    if (zipExtracted) break
                    try {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            ZipInputStream(inputStream, charset).use { zip ->
                                var count = 0
                                while (true) {
                                    val entry = try {
                                        zip.nextEntry
                                    } catch (t: Throwable) {
                                        Log.w(TAG, "[ComicParser] Zip nextEntry error with charset $charset", t)
                                        null
                                    } ?: break

                                    val entryName = entry.name
                                    val ext = entryName.substringAfterLast('.', "").lowercase()
                                    if (!entry.isDirectory && ext in IMAGE_EXTENSIONS) {
                                        val cleanName = String.format("img_%04d_%s", count++, entryName.substringAfterLast('/').substringAfterLast('\\'))
                                        val outFile = File(comicDir, cleanName)
                                        FileOutputStream(outFile).use { out ->
                                            zip.copyTo(out)
                                        }
                                        pageFiles.add(outFile)
                                    }
                                    try { zip.closeEntry() } catch (_: Throwable) {}
                                }
                            }
                        }
                        if (pageFiles.isNotEmpty()) {
                            zipExtracted = true
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "[ComicParser] Failed unzipping with charset $charset", t)
                    }
                }
            } else {
                return@withContext Result.failure(Exception("暂不支持的文件格式"))
            }

            if (pageFiles.isEmpty()) {
                comicDir.deleteRecursively()
                return@withContext Result.failure(Exception("未在文件中找到有效漫画页面"))
            }

            // Natural alphanumeric sort
            pageFiles.sortWith { f1, f2 -> naturalOrderCompare(f1.name, f2.name) }

            // Extract Cover
            val coverDir = File(context.filesDir, "comic_covers")
            if (!coverDir.exists()) coverDir.mkdirs()
            val firstPage = pageFiles.first()
            val coverFile = File(coverDir, "cover_${System.currentTimeMillis()}.jpg")
            firstPage.copyTo(coverFile, overwrite = true)

            val cleanTitle = fileName.substringBeforeLast('.')
            val newBook = Book(
                title = cleanTitle,
                author = "漫画",
                filePath = comicDir.absolutePath,
                coverUri = coverFile.absolutePath,
                totalChapters = pageFiles.size,
                contentType = "COMIC"
            )

            val bookId = bookDao.insertBook(newBook).toInt()
            Log.d(TAG, "[ComicParser] Inserted comic book with id: $bookId, pages: ${pageFiles.size}")

            val chapters = pageFiles.mapIndexed { index, file ->
                Chapter(
                    bookId = bookId,
                    chapterOrder = index,
                    title = "第 ${index + 1} 页",
                    content = file.absolutePath
                )
            }
            bookDao.insertChapters(chapters)

            val savedBook = newBook.copy(id = bookId)
            Result.success(savedBook)
        } catch (t: Throwable) {
            Log.e(TAG, "[ComicParser] Top-level error during comic import", t)
            Result.failure(Exception(t.localizedMessage ?: "漫画解析失败"))
        }
    }

    private fun naturalOrderCompare(s1: String, s2: String): Int {
        val regex = Regex("(\\d+)|(\\D+)")
        val m1 = regex.findAll(s1).map { it.value }.toList()
        val m2 = regex.findAll(s2).map { it.value }.toList()
        val len = minOf(m1.size, m2.size)
        for (i in 0 until len) {
            val t1 = m1[i]
            val t2 = m2[i]
            if (t1 != t2) {
                val n1 = t1.toLongOrNull()
                val n2 = t2.toLongOrNull()
                if (n1 != null && n2 != null) {
                    val comp = n1.compareTo(n2)
                    if (comp != 0) return comp
                } else {
                    val comp = t1.compareTo(t2, ignoreCase = true)
                    if (comp != 0) return comp
                }
            }
        }
        return s1.length.compareTo(s2.length)
    }
}
