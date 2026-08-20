package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * DOCX 解析器：从 zip 里取 word/document.xml，按 Heading1/Heading2 分章，
 * 提取 <w:t> 文本入库（结构与 EpubParser 一致）。
 */
object DocxParser {

    private const val TAG = "DocxParser"

    fun isDocxFile(fileName: String): Boolean {
        return fileName.lowercase().endsWith(".docx")
    }

    suspend fun importDocx(
        context: Context,
        uri: Uri,
        fileName: String,
        bookDao: BookDao
    ): Result<Book> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "[DocxParser] Starting DOCX import for $fileName")
            val xml = readDocumentXml(context, uri)
                ?: return@withContext Result.failure(Exception("无法读取 DOCX 文件（缺少 word/document.xml）"))

            val paragraphs = parseParagraphs(xml)
            if (paragraphs.isEmpty()) {
                return@withContext Result.failure(Exception("DOCX 文件中未找到有效正文内容"))
            }

            val title = paragraphs.firstOrNull { it.isHeading }?.text
                ?.ifBlank { null }
                ?: fileName.substringBeforeLast('.').ifBlank { fileName }

            val initialBook = Book(
                title = title,
                author = "未知作者",
                filePath = uri.toString(),
                contentType = "NOVEL",
                totalChapters = 0
            )
            val bookId = bookDao.insertBook(initialBook).toInt()

            val chapters = mutableListOf<Chapter>()
            var order = 0
            var currentTitle = "第 1 章"
            val currentContent = StringBuilder()

            fun flushChapter() {
                val content = currentContent.toString().trim()
                if (content.isNotEmpty()) {
                    addChaptersWithSplit(chapters, bookId, order, currentTitle, content)
                    order = chapters.size
                }
                currentContent.setLength(0)
            }

            val hasHeadings = paragraphs.any { it.isHeading }
            for (para in paragraphs) {
                if (para.isHeading && hasHeadings) {
                    flushChapter()
                    currentTitle = para.text.ifBlank { "第 ${chapters.size + 1} 章" }
                } else if (para.text.isNotBlank()) {
                    if (currentContent.isNotEmpty()) currentContent.append("\n\n")
                    currentContent.append(para.text.trim())
                }
            }
            flushChapter()

            if (chapters.isEmpty()) {
                // 无标题结构：按固定长度兜底
                val flatText = paragraphs.joinToString("\n\n") { it.text.trim() }
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

            if (chapters.isEmpty()) {
                bookDao.deleteBook(initialBook.copy(id = bookId))
                return@withContext Result.failure(Exception("DOCX 文件中未找到有效正文内容"))
            }

            bookDao.insertChapters(chapters)
            val finalBook = initialBook.copy(
                id = bookId,
                totalChapters = chapters.size
            )
            bookDao.updateBook(finalBook)
            Log.d(TAG, "[DocxParser] Successfully imported '${finalBook.title}' with ${chapters.size} chapters.")
            Result.success(finalBook)
        } catch (t: Throwable) {
            Log.e(TAG, "[DocxParser] Error during DOCX import", t)
            Result.failure(Exception(t.localizedMessage ?: "DOCX 解析失败"))
        }
    }

    private data class DocxParagraph(val text: String, val isHeading: Boolean)

    private fun readDocumentXml(context: Context, uri: Uri): String? {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { stream ->
            ZipInputStream(stream).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        val out = ByteArrayOutputStream()
                        zip.copyTo(out)
                        return String(out.toByteArray(), StandardCharsets.UTF_8)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return null
    }

    private fun parseParagraphs(xml: String): List<DocxParagraph> {
        val result = mutableListOf<DocxParagraph>()
        val paraRegex = Regex("""<w:p[ >].*?</w:p>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val headingRegex = Regex("""<w:pStyle w:val="(Heading[1-6][^"]*)"/""", RegexOption.IGNORE_CASE)
        for (m in paraRegex.findAll(xml)) {
            val raw = m.value
            val isHeading = headingRegex.containsMatchIn(raw)
            val text = extractText(raw)
            if (text.isNotBlank() || isHeading) {
                result.add(DocxParagraph(text, isHeading))
            }
        }
        return result
    }

    private fun extractText(raw: String): String {
        val sb = StringBuilder()
        val tRegex = Regex("""<w:t[^>]*>(.*?)</w:t>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        for (m in tRegex.findAll(raw)) {
            sb.append(m.groupValues[1])
        }
        var text = sb.toString()
        text = text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace(Regex("""&#(\d+);""")) { match ->
                match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value
            }
        return text.replace(Regex("""\s+"""), " ").trim()
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
}
