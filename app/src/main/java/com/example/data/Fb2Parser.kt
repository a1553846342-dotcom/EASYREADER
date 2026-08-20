package com.example.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * FB2（FictionBook）解析器：XML 结构 -> 书名/作者/分章正文/封面，
 * 入库结构与 EpubParser 一致（Book + Chapter）。
 */
object Fb2Parser {

    private const val TAG = "Fb2Parser"

    fun isFb2File(fileName: String): Boolean {
        return fileName.lowercase().endsWith(".fb2")
    }

    suspend fun importFb2(
        context: Context,
        uri: Uri,
        fileName: String,
        bookDao: BookDao
    ): Result<Book> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "[Fb2Parser] Starting FB2 import for $fileName")
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(Exception("无法读取 FB2 文件"))
            val text = decodeFb2Text(bytes)
            if (!text.contains("<FictionBook", ignoreCase = true)) {
                return@withContext Result.failure(Exception("不是有效的 FB2 文件"))
            }

            val doc = Jsoup.parse(text, "", Parser.xmlParser())
            val title = doc.selectFirst("book-title")?.text()?.trim()
                ?.ifBlank { null }
                ?: fileName.substringBeforeLast('.').ifBlank { fileName }
            val author = doc.selectFirst("author")?.text()?.trim()?.ifBlank { null } ?: "未知作者"

            val initialBook = Book(
                title = title,
                author = author,
                filePath = uri.toString(),
                contentType = "NOVEL",
                totalChapters = 0
            )
            val bookId = bookDao.insertBook(initialBook).toInt()

            // 只取第一个 <body>（正文），跳过 notes/comments 等附加 body
            val body = doc.select("body").firstOrNull()
            val sections = body?.select("section") ?: emptyList()
            val chapters = mutableListOf<Chapter>()
            var order = 0

            if (sections.isNotEmpty()) {
                sections.forEachIndexed { index, section ->
                    val sectionTitle = section.select("title").firstOrNull()?.text()?.trim()
                        ?.ifBlank { null }
                        ?: "第 ${index + 1} 章"
                    val contentText = section.select("p")
                        .filter { it.parent()?.tagName() != "title" }
                        .joinToString("\n\n") { it.text().trim() }
                        .trim()
                    if (contentText.isNotEmpty()) {
                        addChaptersWithSplit(chapters, bookId, order, sectionTitle, contentText)
                        order = chapters.size
                    }
                }
            } else {
                // 无 section 结构：整书文本按固定长度兜底分章
                val flatText = body?.select("p")
                    ?.joinToString("\n\n") { it.text().trim() }
                    ?.trim()
                    ?: ""
                if (flatText.isNotEmpty()) {
                    flatText.chunked(5000).forEachIndexed { index, part ->
                        chapters.add(
                            Chapter(
                                bookId = bookId,
                                chapterOrder = chapters.size,
                                title = "第 ${index + 1} 部分",
                                content = part
                            )
                        )
                    }
                }
            }

            if (chapters.isEmpty()) {
                bookDao.deleteBook(initialBook.copy(id = bookId))
                return@withContext Result.failure(Exception("FB2 文件中未找到有效正文内容"))
            }

            var coverUri: String? = null
            runCatching {
                val coverRef = doc.selectFirst("coverpage image")?.attr("href")?.trimStart('#')
                val binaryEl = coverRef?.let { ref ->
                    doc.selectFirst("binary[id=\"$ref\"]")
                }
                val base64 = binaryEl?.text()?.replace(Regex("\\s"), "")
                if (!base64.isNullOrBlank()) {
                    val coverBytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                    if (BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size) != null) {
                        val coverDir = File(context.filesDir, "fb2_covers")
                        if (!coverDir.exists()) coverDir.mkdirs()
                        val ext = detectImageExt(coverBytes) ?: "jpg"
                        val destCover = File(
                            coverDir,
                            "cover_${bookId}_${System.currentTimeMillis()}.$ext"
                        )
                        destCover.writeBytes(coverBytes)
                        coverUri = destCover.absolutePath
                    }
                }
            }

            bookDao.insertChapters(chapters)
            val finalBook = initialBook.copy(
                id = bookId,
                coverUri = coverUri,
                totalChapters = chapters.size
            )
            bookDao.updateBook(finalBook)
            Log.d(TAG, "[Fb2Parser] Successfully imported '${finalBook.title}' with ${chapters.size} chapters.")
            Result.success(finalBook)
        } catch (t: Throwable) {
            Log.e(TAG, "[Fb2Parser] Error during FB2 import", t)
            Result.failure(Exception(t.localizedMessage ?: "FB2 解析失败"))
        }
    }

    private fun decodeFb2Text(bytes: ByteArray): String {
        val sample = bytes.copyOf(minOf(bytes.size, 2048))
        val header = String(sample, StandardCharsets.ISO_8859_1)
        val encMatch = Regex(
            """<\?xml[^>]*encoding=["']([a-zA-Z0-9_-]+)["']""",
            RegexOption.IGNORE_CASE
        ).find(header)
        return try {
            encMatch?.groupValues?.get(1)?.let { name ->
                if (name.equals("utf-8", true) || name.equals("utf8", true)) {
                    return String(bytes, StandardCharsets.UTF_8)
                }
                return String(bytes, Charset.forName(name))
            }
            if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
                return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
            }
            val utf8 = String(bytes, StandardCharsets.UTF_8)
            if (!utf8.contains('\uFFFD')) {
                utf8
            } else {
                try {
                    String(bytes, Charset.forName("GBK"))
                } catch (_: Exception) {
                    utf8
                }
            }
        } catch (_: Exception) {
            String(bytes, StandardCharsets.UTF_8)
        }
    }

    private fun addChaptersWithSplit(
        chapters: MutableList<Chapter>,
        bookId: Int,
        startOrder: Int,
        title: String,
        content: String
    ) {
        if (content.length <= MAX_CHAPTER_LENGTH) {
            chapters.add(
                Chapter(
                    bookId = bookId,
                    chapterOrder = startOrder,
                    title = title,
                    content = content
                )
            )
            return
        }
        content.chunked(MAX_CHAPTER_LENGTH).forEachIndexed { index, part ->
            chapters.add(
                Chapter(
                    bookId = bookId,
                    chapterOrder = startOrder + index,
                    title = if (index == 0) title else "$title (续${index + 1})",
                    content = part
                )
            )
        }
    }

    private fun detectImageExt(bytes: ByteArray): String? {
        return when {
            bytes.size >= 3 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() -> "png"
            bytes.size >= 3 && bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte() -> "gif"
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpg"
            else -> null
        }
    }
}
