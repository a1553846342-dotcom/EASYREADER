package com.example.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.regex.Pattern

class BookRepository(private val context: Context, private val bookDao: BookDao) {

    private val unsupportedBinaryExtensions = listOf(
        ".pdf", ".kfx", ".djvu",
        ".doc", ".rtf", ".chm"
    )

    val allBooks: Flow<List<Book>> = bookDao.getAllBooks()
    val allCategories: Flow<List<CategoryEntity>> = bookDao.getAllCategories()
    val allReadingRecords: Flow<List<ReadingRecord>> = bookDao.getAllReadingRecordsFlow()
    val allReadingSessions: Flow<List<ReadingSession>> = bookDao.getAllReadingSessionsFlow()

    private fun detectCharset(context: Context, uri: Uri): java.nio.charset.Charset {
        // 采样更大范围（256KB），并做严格解码校验 + BOM 识别，
        // 避免“文件前半段是 ASCII 后半段是 GBK”时被误判成 UTF-8 导致乱码。
        val buffer = ByteArray(256 * 1024)
        var bytesRead = 0
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                bytesRead = input.read(buffer)
            }
        } catch (e: Exception) {
            android.util.Log.e("BookImport", "Error reading bytes for encoding detection", e)
        }

        if (bytesRead <= 0) return java.nio.charset.StandardCharsets.UTF_8

        val data = if (bytesRead < buffer.size) buffer.copyOf(bytesRead) else buffer

        // BOM 优先
        if (data.size >= 3 && data[0] == 0xEF.toByte() && data[1] == 0xBB.toByte() && data[2] == 0xBF.toByte()) {
            return java.nio.charset.StandardCharsets.UTF_8
        }
        if (data.size >= 2 &&
            ((data[0] == 0xFF.toByte() && data[1] == 0xFE.toByte()) ||
                (data[0] == 0xFE.toByte() && data[1] == 0xFF.toByte()))
        ) {
            return java.nio.charset.Charset.forName(
                if (data[0] == 0xFF.toByte()) "UTF-16LE" else "UTF-16BE"
            )
        }

        fun decodesCleanly(charset: java.nio.charset.Charset): Boolean {
            return try {
                val decoder = charset.newDecoder()
                decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                decoder.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                decoder.decode(java.nio.ByteBuffer.wrap(data))
                true
            } catch (e: Exception) {
                false
            }
        }

        if (decodesCleanly(java.nio.charset.StandardCharsets.UTF_8)) {
            return java.nio.charset.StandardCharsets.UTF_8
        }
        // UTF-8 严格解码失败：优先 GB18030（GBK 超集），避免中文乱码
        return try {
            val gb = java.nio.charset.Charset.forName("GB18030")
            if (decodesCleanly(gb)) gb else java.nio.charset.StandardCharsets.UTF_8
        } catch (e: Exception) {
            java.nio.charset.StandardCharsets.UTF_8
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

    suspend fun importBookFromUri(
        uri: Uri,
        fileName: String,
        forcePdfPlaceholder: Boolean = false
    ): Result<Book> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("BookImport", "[BookRepository] Starting streaming import: $fileName, uri: $uri")
            // 同一路径已入库：直接返回已有书籍，避免重复下载后书架出现重复书
            bookDao.getBookByFilePath(uri.toString())?.let { existing ->
                android.util.Log.d("BookImport", "[BookRepository] Same file already imported, skip duplicate: ${existing.title}")
                return@withContext Result.success(existing)
            }
            if (EpubParser.isEpubFile(fileName)) {
                return@withContext EpubParser.importEpub(context, uri, fileName, bookDao)
            }
            // 下载管道里的 PDF 小说按“暂不支持阅读”占位书入库，
            // 避免被 ComicParser 当漫画逐页渲染（文本 PDF 会显示乱码）
            if (forcePdfPlaceholder && fileName.lowercase().endsWith(".pdf")) {
                return@withContext importUnsupportedFormat(uri, fileName)
            }
            if (ComicParser.isComicFile(fileName)) {
                return@withContext ComicParser.importComic(context, uri, fileName, bookDao)
            }
            // MOBI / AZW3 / AZW：支持解析正文章节并直接阅读
            if (MobiParser.isMobiFile(fileName)) {
                return@withContext MobiParser.importMobi(context, uri, fileName, bookDao)
            }
            // FB2 / DOCX：XML 电子书与 Word 文档正文提取
            if (Fb2Parser.isFb2File(fileName)) {
                return@withContext Fb2Parser.importFb2(context, uri, fileName, bookDao)
            }
            if (DocxParser.isDocxFile(fileName)) {
                return@withContext DocxParser.importDocx(context, uri, fileName, bookDao)
            }
            if (unsupportedBinaryExtensions.any { fileName.lowercase().endsWith(it) }) {
                return@withContext importUnsupportedFormat(uri, fileName)
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

            // ????? insertChapters ?? WAL ???????????????
            // ?? checkpoint ???????????? -wal?????????????
            runCatching {
                AppDatabase.getDatabase(context).openHelper.writableDatabase
                    .execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
            }.onFailure {
                android.util.Log.w("BookImport", "WAL checkpoint failed (non-fatal): ${it.message}")
            }

            Result.success(finalBook)
        } catch (t: Throwable) {
            android.util.Log.e("BookImport", "[BookRepository] Streaming import failed", t)
            Result.failure(Exception(t.localizedMessage ?: "文件流式导入出现错误"))
        }
    }

    /** PDF/MOBI 等暂不支持阅读的格式：只登记书架，不按文本解析（避免大文件被读成超大垃圾章节导致打开卡顿）。 */
    private suspend fun importUnsupportedFormat(uri: Uri, fileName: String): Result<Book> =
        withContext(Dispatchers.IO) {
            try {
                val cleanTitle = fileName.substringBeforeLast('.').ifBlank { fileName }
                val book = Book(
                    title = cleanTitle,
                    filePath = uri.toString(),
                    totalChapters = 1
                )
                val bookId = bookDao.insertBook(book).toInt()
                bookDao.insertChapters(
                    listOf(
                        Chapter(
                            bookId = bookId,
                            chapterOrder = 0,
                            title = UNSUPPORTED_CHAPTER_TITLE,
                            content = ""
                        )
                    )
                )
                Result.success(book.copy(id = bookId, totalChapters = 1))
            } catch (t: Throwable) {
                android.util.Log.e("BookImport", "[BookRepository] Unsupported format import failed", t)
                Result.failure(Exception(t.localizedMessage ?: "导入失败"))
            }
        }

    /**
     * 存量数据修复：把已入库的超大章节（如之前下载的单章大书）拆成小章节，
     * 与本地导入书一致，打开阅读器不再卡顿/闪退。
     */
    suspend fun splitOversizedChaptersInLibrary() = withContext(Dispatchers.IO) {
        try {
            val books = bookDao.getAllBooks().first()
            for (book in books) {
                val chapters = bookDao.getChaptersListForBook(book.id)
                if (chapters.isEmpty() || chapters.none { it.content.length > MAX_CHAPTER_LENGTH }) {
                    continue
                }

                val replacement = mutableListOf<Chapter>()
                var order = 0
                for (ch in chapters) {
                    if (ch.content.length <= MAX_CHAPTER_LENGTH) {
                        replacement.add(ch.copy(chapterOrder = order++))
                    } else {
                        val parts = ch.content.chunked(MAX_CHAPTER_LENGTH)
                        parts.forEachIndexed { index, part ->
                            replacement.add(
                                Chapter(
                                    bookId = ch.bookId,
                                    chapterOrder = order++,
                                    title = if (index == 0) ch.title else "${ch.title} (续${index + 1})",
                                    content = part
                                )
                            )
                        }
                    }
                }

                bookDao.deleteChaptersForBook(book.id)
                bookDao.insertChapters(replacement)
                bookDao.updateBook(book.copy(totalChapters = replacement.size))
                android.util.Log.i("BookImport", "Split oversized chapters for '${book.title}': ${chapters.size} -> ${replacement.size}")
            }
            // ????????+??WAL ??????????????? checkpoint ?????
            // ?????????????
            runCatching {
                AppDatabase.getDatabase(context).openHelper.writableDatabase
                    .execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
            }
        } catch (t: Throwable) {
            android.util.Log.e("BookImport", "splitOversizedChaptersInLibrary failed", t)
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
        bookDao.nullifyBookIdInReadingRecords(book.id)
        bookDao.deleteReadingSessionsForBook(book.id)
        bookDao.deleteChaptersForBook(book.id)
        bookDao.deleteBook(book)
        deleteBookFiles(book)
    }

    suspend fun addReadingSession(session: ReadingSession) {
        bookDao.insertReadingSession(session)
    }

    suspend fun getReadingSessionsForDate(dateStr: String): List<ReadingSession> {
        return bookDao.getReadingSessionsForDate(dateStr)
    }

    /** 删除书籍时彻底清理磁盘文件（书本体、封面、下载任务与残留缓存），避免“删了但内存还在涨”。 */
    private suspend fun deleteBookFiles(book: Book) {
        // 1) 书本体：TXT/EPUB 文件或漫画目录
        runCatching {
            val f = File(book.filePath.removePrefix("file://"))
            if (f.exists()) {
                if (f.isDirectory) f.deleteRecursively() else f.delete()
            }
        }
        // 2) 封面文件（漫画/EPUB 封面缓存）
        book.coverUri?.let {
            runCatching {
                val cf = File(it.removePrefix("file://"))
                if (cf.exists()) cf.delete()
            }
        }
        // 3) 对应的下载任务记录 + downloads 目录文件
        runCatching {
            val taskDao = AppDatabase.getDatabase(context).downloadTaskDao()
            val downloadsDir = File(context.filesDir, "downloads")
            val safeTitle = com.example.download.DownloadManager.sanitizeFileName(book.title)
            val tasks = taskDao.getAllTasksSync()
            tasks.filter { task ->
                task.filePath == book.filePath ||
                    (safeTitle.isNotBlank() && File(task.filePath).name.contains(safeTitle))
            }.forEach { task ->
                taskDao.deleteTaskById(task.id)
                runCatching { File(task.filePath).delete() }
                runCatching {
                    File(
                        downloadsDir,
                        "${com.example.download.DownloadManager.sanitizeFileName(task.id)}.tmp"
                    ).delete()
                }
            }
            // 4) downloads 目录中按标题匹配的孤儿文件
            if (safeTitle.isNotBlank()) {
                downloadsDir.listFiles()?.forEach { f ->
                    if (f.name.contains(safeTitle)) runCatching { f.delete() }
                }
            }
        }
    }

    suspend fun deleteReadingRecord(id: Int) {
        bookDao.deleteReadingRecord(id)
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
        // 1. Remove legacy test / placeholder books
        try {
            val allBooks = bookDao.getAllBooksSync()
            val oldTestPaths = setOf("sample_test_novel", "sample_comic", "sample_epub3.epub")
            for (book in allBooks) {
                if (book.filePath in oldTestPaths ||
                    book.title.contains("5页测试小说") ||
                    book.title.contains("示例漫画") ||
                    book.title.contains("示例 EPUB")
                ) {
                    bookDao.deleteChaptersForBook(book.id)
                    bookDao.nullifyBookIdInReadingRecords(book.id)
                    bookDao.deleteBook(book)
                }
            }
            val comicDir = java.io.File(context.filesDir, "sample_comic")
            if (comicDir.exists()) comicDir.deleteRecursively()
            val epubFile = java.io.File(context.cacheDir, "sample_epub3.epub")
            if (epubFile.exists()) epubFile.delete()
        } catch (e: Exception) {
            android.util.Log.e("BookRepository", "Error cleaning old test books", e)
        }

        // 2. Seed 《Ciallo阅读使用指南》 as default book
        val guideFilePath = "ciallo_guide_novel"
        val existingGuideCount = bookDao.getBookCountByFilePath(guideFilePath)

        if (existingGuideCount == 0) {
            val guideTitle = "《Ciallo阅读使用指南》"
            val guideBook = Book(
                title = guideTitle,
                author = "Ciallo阅读器团队",
                filePath = guideFilePath,
                totalChapters = 6,
                contentType = "NOVEL"
            )
            val bookId = bookDao.insertBook(guideBook).toInt()
            val guideChapters = listOf(
                Chapter(
                    bookId = bookId,
                    chapterOrder = 0,
                    title = "第一章：欢迎使用 Ciallo 阅读器",
                    content = "欢迎使用 Ciallo 阅读器！\n\nCiallo 阅读器是一款专为二次元与小说/漫画爱好者打造的极简、流畅且充满陪伴感的高品质阅读应用。\n\n无论你是喜爱阅读长篇网络小说、经典文学著作，还是习惯追更日漫与条漫，Ciallo 阅读器都能为你提供极佳的阅读排版体验与智能贴心的辅助功能。\n\n本指南将带你快速了解 Ciallo 阅读器的各项核心功能与使用技巧，帮助你开启一段惬意的阅读之旅。"
                ),
                Chapter(
                    bookId = bookId,
                    chapterOrder = 1,
                    title = "第二章：书架管理与图书导入",
                    content = "【图书导入与支持格式】\n1. 点击书架右上角或底部的「+」导入按钮，即可从手机本地文件选择并导入图书。\n2. 应用原生支持 TXT 纯文本小说、EPUB 电子书以及 CBZ/ZIP/PDF 格式的漫画文件。\n\n【分类与搜索】\n• 可以在书架顶栏搜索框中快速搜索书名或作者名。\n• 支持创建自定义分类标签（如：奇幻、科幻、漫画、轻小说），长按书籍卡片即可方便地归类或编辑书籍信息。\n• 最近阅读区域将置顶显示你近期读过的书籍，方便一键继续阅读。"
                ),
                Chapter(
                    bookId = bookId,
                    chapterOrder = 2,
                    title = "第三章：小说阅读与个性化排版",
                    content = "【精确排版引擎】\nCiallo 阅读器采用了基于真实控件高度与逐行测量的动态排版算法。无论在竖屏还是横屏下切换，文字都不会出现被半截切断或丢失漏行的现象，同时会自动保持位置锚点衔接。\n\n【版式与主题定制】\n• 点击屏幕中央区域唤出阅读控制栏，点击「设置」图标即可调整：\n  - 字号大小与行间距\n  - 首行缩进与段落边距\n  - 阅读背景主题：包含羊皮纸、夜间深色、护眼绿、极简白等多款精心调配的色彩组合\n  - 字体切换：支持自定义系统字体与优雅衬线/无衬线体选择。"
                ),
                Chapter(
                    bookId = bookId,
                    chapterOrder = 3,
                    title = "第四章：仿真翻页与书签互动",
                    content = "【多样化翻页模式】\n应用内置了多种高帧率平滑翻页效果，可在阅读设置中自由切换：\n1. 仿真翻页：还原纸质书的卷角与平滑弯曲视差。\n2. 覆盖/平移：现代优雅的推移效果。\n3. 淡入淡出：柔和不刺眼的渐变过渡。\n4. 连续滚动：适合快速浏览的长图文模式。\n\n【书签与划词高亮】\n• 点击顶部控制栏的书签图标，或长按页面右上角即可快速添加书签。\n• 在正文中长按并拖动选取文字，可呼出高亮与笔记菜单，记录你的阅读心得与精彩名句。"
                ),
                Chapter(
                    bookId = bookId,
                    chapterOrder = 4,
                    title = "第五章：漫画阅读器使用技巧",
                    content = "【漫画专享优化】\n当你打开 CBZ / ZIP / PDF 漫画或图集时，Ciallo 阅读器会自动切换至专属漫画引擎：\n1. 支持双指自由缩放与双击快速放大图像，细节一览无余。\n2. 支持切换「横向翻页」与「纵向条漫」模式，满足不同漫画排版需求。\n3. 支持调整读向：可切换日漫（右至左）或欧美漫（左至右）阅读顺序。"
                ),
                Chapter(
                    bookId = bookId,
                    chapterOrder = 5,
                    title = "第六章：Roxy 助手与阅读统计",
                    content = "【看板娘 Roxy 动态陪伴】\n在阅读界面与应用主页中，可爱贴心的魔法少女 Roxy 会静静陪伴着你：\n• 添加书签或完成阅读目标时，Roxy 会展示萌趣的交互与魔法动画。\n• 互动响应流畅，并在连续触发时具备打断重播平滑过渡。\n\n【阅读统计与成就】\n进入「统计」标签页，可以直观查看你的总阅读时长、阅读天数、章节进度分布以及每日阅读趋势图表，记录你读过的点点滴滴。\n\n祝你阅读愉快！—— Ciallo 阅读器团队"
                )
            )
            bookDao.insertChapters(guideChapters)
        }
    }
}
