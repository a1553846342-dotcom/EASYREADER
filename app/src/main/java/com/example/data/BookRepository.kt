package com.example.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Pattern

class BookRepository(private val context: Context, private val bookDao: BookDao) {

    val allBooks: Flow<List<Book>> = bookDao.getAllBooks()
    val allCategories: Flow<List<CategoryEntity>> = bookDao.getAllCategories()

    private fun detectCharset(context: Context, uri: Uri): java.nio.charset.Charset {
        val buffer = ByteArray(8192)
        var bytesRead = 0
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                bytesRead = input.read(buffer)
            }
        } catch (e: Exception) {
            android.util.Log.e("BookImport", "Error reading bytes for encoding detection", e)
        }

        if (bytesRead <= 0) return java.nio.charset.StandardCharsets.UTF_8

        val decoder = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
        decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        decoder.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)

        return try {
            val byteBuffer = java.nio.ByteBuffer.wrap(buffer, 0, bytesRead)
            decoder.decode(byteBuffer)
            java.nio.charset.StandardCharsets.UTF_8
        } catch (e: Exception) {
            android.util.Log.d("BookImport", "UTF-8 decoding failed, falling back to GBK")
            try {
                java.nio.charset.Charset.forName("GBK")
            } catch (ex: Exception) {
                java.nio.charset.StandardCharsets.UTF_8
            }
        }
    }

    private fun hasChapterTitles(context: Context, uri: Uri, charset: java.nio.charset.Charset): Boolean {
        val chapterPattern = Pattern.compile("^第[0-9一二三四五六七八九十百千万]+[章回卷节\\s].*")
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, charset)).use { reader ->
                    var line: String?
                    var lineCount = 0
                    while (reader.readLine().also { line = it } != null) {
                        lineCount++
                        val cleanLine = line?.trim() ?: ""
                        if (cleanLine.isNotEmpty() && chapterPattern.matcher(cleanLine).matches()) {
                            android.util.Log.d("BookImport", "Found chapter pattern at line $lineCount: $cleanLine")
                            return@hasChapterTitles true
                        }
                        if (lineCount > 20000) break
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BookImport", "Error scanning for chapter titles", e)
        }
        return false
    }

    suspend fun importBookFromUri(uri: Uri, fileName: String): Result<Book> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("BookImport", "[BookRepository] Starting streaming import: $fileName, uri: $uri")
            if (ComicParser.isComicFile(fileName)) {
                return@withContext ComicParser.importComic(context, uri, fileName, bookDao)
            }

            val charset = detectCharset(context, uri)
            android.util.Log.d("BookImport", "Detected charset: ${charset.name()}")

            val hasChapters = hasChapterTitles(context, uri, charset)
            android.util.Log.d("BookImport", "File has chapter titles: $hasChapters")

            val cleanTitle = fileName.replace(".txt", "", ignoreCase = true)
            val initialBook = Book(
                title = cleanTitle,
                filePath = uri.toString(),
                totalChapters = 0
            )

            // Insert book first to obtain a valid bookId
            val bookId = bookDao.insertBook(initialBook).toInt()
            android.util.Log.d("BookImport", "Inserted initial book record with ID: $bookId")

            val chapterPattern = Pattern.compile("^第[0-9一二三四五六七八九十百千万]+[章回卷节\\s].*")
            var chapterCount = 0
            val batch = mutableListOf<Chapter>()
            val maxBatchSize = 50

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, charset)).use { reader ->
                    if (hasChapters) {
                        var currentTitle = "前言"
                        val currentContent = java.lang.StringBuilder()
                        var line: String?

                        while (reader.readLine().also { line = it } != null) {
                            val cleanLine = line!!.trim()
                            if (cleanLine.isNotEmpty() && chapterPattern.matcher(cleanLine).matches()) {
                                if (currentContent.isNotEmpty() || currentTitle != "前言") {
                                    val chapter = Chapter(
                                        bookId = bookId,
                                        chapterOrder = chapterCount++,
                                        title = currentTitle,
                                        content = currentContent.toString()
                                    )
                                    batch.add(chapter)
                                    if (batch.size >= maxBatchSize) {
                                        bookDao.insertChapters(batch)
                                        batch.clear()
                                    }
                                }
                                currentTitle = cleanLine
                                currentContent.setLength(0)
                            } else {
                                if (currentContent.isNotEmpty()) {
                                    currentContent.append("\n")
                                }
                                currentContent.append(line)
                                
                                // Force split if the chapter gets too large to prevent OOM
                                if (currentContent.length >= 20000) {
                                    val chapter = Chapter(
                                        bookId = bookId,
                                        chapterOrder = chapterCount++,
                                        title = if (currentTitle == "前言") "前言" else "$currentTitle (续)",
                                        content = currentContent.toString()
                                    )
                                    batch.add(chapter)
                                    if (batch.size >= maxBatchSize) {
                                        bookDao.insertChapters(batch)
                                        batch.clear()
                                    }
                                    currentContent.setLength(0)
                                }
                            }
                        }

                        // Add last chapter
                        if (currentContent.isNotEmpty() || currentTitle != "前言") {
                            val chapter = Chapter(
                                bookId = bookId,
                                chapterOrder = chapterCount++,
                                title = currentTitle,
                                content = currentContent.toString()
                            )
                            batch.add(chapter)
                        }
                    } else {
                        // Fallback: split by ~5000 characters
                        val currentContent = java.lang.StringBuilder()
                        var line: String?
                        val chunkSize = 5000

                        while (reader.readLine().also { line = it } != null) {
                            if (currentContent.isNotEmpty()) {
                                currentContent.append("\n")
                            }
                            currentContent.append(line)

                            if (currentContent.length >= chunkSize) {
                                val chapter = Chapter(
                                    bookId = bookId,
                                    chapterOrder = chapterCount++,
                                    title = "第 ${chapterCount} 部分",
                                    content = currentContent.toString()
                                )
                                batch.add(chapter)
                                if (batch.size >= maxBatchSize) {
                                    bookDao.insertChapters(batch)
                                    batch.clear()
                                }
                                currentContent.setLength(0)
                            }
                        }

                        // Add remaining chunk
                        if (currentContent.isNotEmpty()) {
                            val chapter = Chapter(
                                bookId = bookId,
                                chapterOrder = chapterCount++,
                                title = "第 ${chapterCount} 部分",
                                content = currentContent.toString()
                            )
                            batch.add(chapter)
                        }
                    }

                    // Flush any remaining chapters
                    if (batch.isNotEmpty()) {
                        bookDao.insertChapters(batch)
                        batch.clear()
                    }
                }
            }

            // Update total chapters
            val finalBook = initialBook.copy(id = bookId, totalChapters = chapterCount)
            bookDao.updateBook(finalBook)
            android.util.Log.d("BookImport", "Streaming import completed successfully. Total chapters: $chapterCount")
            Result.success(finalBook)
        } catch (t: Throwable) {
            android.util.Log.e("BookImport", "[BookRepository] Streaming import failed", t)
            Result.failure(Exception(t.localizedMessage ?: "文件流式导入出现错误"))
        }
    }

    suspend fun getChaptersMetadataList(bookId: Int): List<Chapter> = withContext(Dispatchers.IO) {
        bookDao.getChaptersMetadataList(bookId)
    }

    suspend fun getChaptersByOrders(bookId: Int, orders: List<Int>): List<Chapter> = withContext(Dispatchers.IO) {
        bookDao.getChaptersByOrders(bookId, orders)
    }

    suspend fun updateBookProgress(bookId: Int, chapterIndex: Int, scrollOffset: Int, isFinished: Boolean) {
        val book = bookDao.getBookById(bookId) ?: return
        val updated = book.copy(
            currentChapterIndex = chapterIndex,
            scrollOffset = scrollOffset,
            isFinished = isFinished,
            lastReadTime = System.currentTimeMillis()
        )
        bookDao.updateBook(updated)
    }

    suspend fun deleteBook(book: Book) {
        bookDao.deleteChaptersForBook(book.id)
        bookDao.deleteBook(book)
    }

    fun getChaptersForBook(bookId: Int) = bookDao.getChaptersForBook(bookId)

    fun getBookmarksForBook(bookId: Int) = bookDao.getBookmarksForBook(bookId)

    suspend fun addBookmark(bookmark: Bookmark) = bookDao.insertBookmark(bookmark)

    suspend fun deleteBookmark(id: Int) = bookDao.deleteBookmark(id)

    fun getHighlightsForBook(bookId: Int) = bookDao.getHighlightsForBook(bookId)

    suspend fun addHighlight(highlight: Highlight) = bookDao.insertHighlight(highlight)

    suspend fun deleteHighlight(id: Int) = bookDao.deleteHighlight(id)

    suspend fun addCategory(name: String) {
        bookDao.insertCategory(CategoryEntity(name = name))
    }

    suspend fun checkAndSeedDefaultBooks() = withContext(Dispatchers.IO) {
        if (bookDao.getBooksCount() > 0) return@withContext

        // 1. Seed Sample Novel
        val novelTitle = "示例小说：极简阅读体验指南"
        val novelBook = Book(
            title = novelTitle,
            author = "系统示例",
            filePath = "sample_novel",
            totalChapters = 2,
            contentType = "NOVEL"
        )
        val novelId = bookDao.insertBook(novelBook).toInt()
        val novelChapters = listOf(
            Chapter(
                bookId = novelId,
                chapterOrder = 0,
                title = "第一章 欢迎使用极简阅读器",
                content = "欢迎使用极简阅读器！\n\n这是一款专为小说与漫画爱好者打造的高颜值、轻量级阅读应用。\n\n【核心特色】\n1. 支持 TXT 小说与 CBZ / ZIP / PDF 漫画格式。\n2. 支持书签、高亮划线、进度同步与阅读统计。\n3. 极简护眼模式与自定义排版样式。\n\n点击屏幕中央可呼出阅读菜单，进行字号与背景调节。"
            ),
            Chapter(
                bookId = novelId,
                chapterOrder = 1,
                title = "第二章 快捷操作技巧",
                content = "【常用操作说明】\n\n1. 导入新书：在书库或首页点击‘导入新书’按钮，选择本地 TXT 文本或 CBZ/ZIP/PDF 漫画文件即可。\n2. 分类管理：可以在书库页面自由创建自定义分类标签并归类。\n3. 朗读与统计：支持语音朗读（TTS）与周阅读时长统计柱状图。\n\n祝您阅读愉快！"
            )
        )
        bookDao.insertChapters(novelChapters)

        // 2. Seed Sample Comic
        try {
            val comicDir = java.io.File(context.filesDir, "sample_comic")
            if (!comicDir.exists()) comicDir.mkdirs()

            // Page 1 Image
            val p1File = java.io.File(comicDir, "page1.jpg")
            val b1 = android.graphics.Bitmap.createBitmap(1080, 1600, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas1 = android.graphics.Canvas(b1)
            canvas1.drawColor(android.graphics.Color.parseColor("#12181F"))
            val paintText1 = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 54f
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val paintSub1 = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#7FD8C8")
                textSize = 36f
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            canvas1.drawText("示例漫画：应用功能体验", 540f, 600f, paintText1)
            canvas1.drawText("【第 1 页】支持双指缩放与双击放大", 540f, 720f, paintSub1)
            canvas1.drawText("点击屏幕中央可呼出阅读控制栏", 540f, 840f, paintSub1)
            java.io.FileOutputStream(p1File).use { out ->
                b1.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            }
            b1.recycle()

            // Page 2 Image
            val p2File = java.io.File(comicDir, "page2.jpg")
            val b2 = android.graphics.Bitmap.createBitmap(1080, 1600, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas2 = android.graphics.Canvas(b2)
            canvas2.drawColor(android.graphics.Color.parseColor("#12181F"))
            canvas2.drawText("示例漫画：模式切换", 540f, 600f, paintText1)
            canvas2.drawText("【第 2 页】支持横向/纵向条漫模式与日漫右至左", 540f, 720f, paintSub1)
            canvas2.drawText("导入 CBZ / ZIP / PDF 即可开始阅读", 540f, 840f, paintSub1)
            java.io.FileOutputStream(p2File).use { out ->
                b2.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            }
            b2.recycle()

            val comicBook = Book(
                title = "示例漫画：应用功能指南",
                author = "系统示例",
                filePath = comicDir.absolutePath,
                coverUri = p1File.absolutePath,
                totalChapters = 2,
                contentType = "COMIC"
            )
            val comicId = bookDao.insertBook(comicBook).toInt()
            val comicChapters = listOf(
                Chapter(bookId = comicId, chapterOrder = 0, title = "第 1 页", content = p1File.absolutePath),
                Chapter(bookId = comicId, chapterOrder = 1, title = "第 2 页", content = p2File.absolutePath)
            )
            bookDao.insertChapters(comicChapters)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
