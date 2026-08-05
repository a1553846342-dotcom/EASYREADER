package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.StringReader
import java.net.URLDecoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

object EpubParser {

    private const val TAG = "EpubParser"

    fun isEpubFile(fileName: String): Boolean {
        return fileName.lowercase().endsWith(".epub")
    }

    suspend fun importEpub(
        context: Context,
        uri: Uri,
        fileName: String,
        bookDao: BookDao
    ): Result<Book> = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "epub_${System.currentTimeMillis()}")
        try {
            Log.d(TAG, "[EpubParser] Starting EPUB import for $fileName, uri: $uri")
            if (!tempDir.exists()) tempDir.mkdirs()

            // 1. Unzip EPUB contents into tempDir
            val unzipped = unzipEpub(context, uri, tempDir)
            if (!unzipped) {
                tempDir.deleteRecursively()
                return@withContext Result.failure(Exception("无法解压 EPUB 文件"))
            }

            // 2. Read META-INF/container.xml to find the OPF file path
            val containerFile = File(tempDir, "META-INF/container.xml")
            if (!containerFile.exists()) {
                tempDir.deleteRecursively()
                return@withContext Result.failure(Exception("EPUB 缺少 META-INF/container.xml"))
            }

            val opfRelativePath = parseContainerXml(containerFile)
            if (opfRelativePath.isNullOrBlank()) {
                tempDir.deleteRecursively()
                return@withContext Result.failure(Exception("无法解析 EPUB container.xml 中的 OPF 路径"))
            }

            val opfFile = File(tempDir, decodeUrl(opfRelativePath))
            if (!opfFile.exists()) {
                tempDir.deleteRecursively()
                return@withContext Result.failure(Exception("找不到 OPF 文件: $opfRelativePath"))
            }

            val opfDir = opfFile.parentFile ?: tempDir

            // 3. Parse OPF file (metadata, manifest, spine)
            val opfData = parseOpfXml(opfFile)
            val title = opfData.title.ifBlank { fileName.substringBeforeLast('.') }
            val author = opfData.author.ifBlank { "未知作者" }

            // 4. Create Book entry in database first
            val initialBook = Book(
                title = title,
                author = author,
                filePath = uri.toString(),
                contentType = "NOVEL",
                totalChapters = 0
            )

            val bookId = bookDao.insertBook(initialBook).toInt()
            Log.d(TAG, "[EpubParser] Inserted initial book record with ID: $bookId")

            val chapters = mutableListOf<Chapter>()
            var chapterOrder = 0

            // 5. Load XHTML files in spine order
            for (idref in opfData.spineItemRefs) {
                val href = opfData.manifestItems[idref] ?: continue
                val xhtmlFile = File(opfDir, decodeUrl(href))
                if (!xhtmlFile.exists()) {
                    Log.w(TAG, "[EpubParser] Spine item file missing: ${xhtmlFile.absolutePath}")
                    continue
                }

                val rawContent = readTextWithCharsetDetection(xhtmlFile)
                if (rawContent.isBlank()) continue

                val chapterTitle = extractChapterTitle(rawContent, opfData.tocTitles[href])
                val cleanText = extractCleanTextFromHtml(rawContent)

                if (cleanText.isNotBlank()) {
                    chapters.add(
                        Chapter(
                            bookId = bookId,
                            chapterOrder = chapterOrder++,
                            title = chapterTitle,
                            content = cleanText
                        )
                    )
                }
            }

            // 拆分超大章节：与本地文本导入一致，避免打开大书时整章加载导致卡顿/闪退
            val splitChapters = mutableListOf<Chapter>()
            var splitOrder = 0
            for (ch in chapters) {
                if (ch.content.length <= MAX_CHAPTER_LENGTH) {
                    splitChapters.add(ch.copy(chapterOrder = splitOrder++))
                } else {
                    val parts = ch.content.chunked(MAX_CHAPTER_LENGTH)
                    parts.forEachIndexed { index, part ->
                        splitChapters.add(
                            Chapter(
                                bookId = ch.bookId,
                                chapterOrder = splitOrder++,
                                title = if (index == 0) ch.title else "${ch.title} (续${index + 1})",
                                content = part
                            )
                        )
                    }
                }
            }
            chapters.clear()
            chapters.addAll(splitChapters)

            if (chapters.isEmpty()) {
                tempDir.deleteRecursively()
                bookDao.deleteBook(initialBook.copy(id = bookId))
                return@withContext Result.failure(Exception("EPUB 文件中未找到有效的正文内容"))
            }

            // 6. Handle Cover Image if available
            Log.d(TAG, "[COVER] === START, file=$fileName ===")
            Log.d(TAG, "[COVER] 开始解析: $fileName")
            Log.d(TAG, "[COVER] container.xml 定位到 OPF 路径: ${opfFile.absolutePath}")
            Log.d(TAG, "[COVER] 检测到 EPUB 版本: ${opfData.epubVersion}")
            Log.d(TAG, "[COVER] EPUB3 properties=cover-image 匹配结果: ${if (!opfData.epub3CoverHref.isNullOrBlank()) "找到" else "未找到"}, item id=${opfData.epub3CoverId ?: ""}, href=${opfData.epub3CoverHref ?: ""}")
            Log.d(TAG, "[COVER] EPUB2 meta name=cover 匹配结果: ${if (!opfData.epub2CoverId.isNullOrBlank()) "找到" else "未找到"}, content(id)=${opfData.epub2CoverId ?: ""}")
            Log.d(TAG, "[COVER] manifest 中按 id 查找 href 结果: ${opfData.epub2CoverHref ?: ""}")
            val fullParsedPath = if (!opfData.coverHref.isNullOrBlank()) File(opfDir, decodeUrl(opfData.coverHref)).absolutePath else ""
            Log.d(TAG, "[COVER] href 相对 OPF 目录解析后的完整路径: $fullParsedPath")

            var finalCoverFile: File? = null
            var coverSource = "none"

            // Layer 1: Spec-based extraction
            val (specCoverFile, matchedEntryName) = findMatchingCoverFile(opfDir, tempDir, opfData.coverHref)
            Log.d(TAG, "[COVER] zip 内查找该路径结果: ${if (specCoverFile != null) "找到" else "未找到"}, 实际匹配的 entry 名: ${matchedEntryName ?: ""}")

            if (specCoverFile != null && specCoverFile.exists() && specCoverFile.isFile) {
                // Verify decoding
                val bytes = specCoverFile.readBytes()
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    Log.d(TAG, "[COVER] 规格封面解码成功: ${bitmap.width}x${bitmap.height}")
                    finalCoverFile = specCoverFile
                    coverSource = "spec"
                } else {
                    Log.e(TAG, "[COVER] 规格封面解码失败，将进入暴力兜底")
                }
            }

            // Layer 2: Violent Fallback
            if (finalCoverFile == null) {
                Log.d(TAG, "[COVER] 规格解析未获取到有效封面，执行暴力兜底流程...")
                val imageExtensions = setOf("jpg", "jpeg", "png", "webp")
                val allImages = tempDir.walkTopDown().filter { file ->
                    file.isFile && file.extension.lowercase() in imageExtensions
                }.toList()

                Log.d(TAG, "[COVER] 暴力扫描全部图片文件，共找到 ${allImages.size} 个图片")
                for (img in allImages) {
                    Log.d(TAG, "[COVER] 扫描到图片: ${img.absolutePath}, 大小: ${img.length()} 字节")
                }

                if (allImages.isNotEmpty()) {
                    var selectedImgFile: File? = null

                    // Priority 1: Filename contains "cover" (case-insensitive)
                    val coverMatch = allImages.firstOrNull { file ->
                        file.nameWithoutExtension.lowercase().contains("cover")
                    }
                    if (coverMatch != null) {
                        Log.d(TAG, "[COVER] 命中 优先级1 (包含'cover'的文件名): ${coverMatch.absolutePath}")
                        val bytes = coverMatch.readBytes()
                        if (android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null) {
                            selectedImgFile = coverMatch
                        } else {
                            Log.e(TAG, "[COVER] 优先级1 图片解码失败")
                        }
                    }

                    // Priority 2: First image tag inside the first spine XHTML file
                    if (selectedImgFile == null && opfData.spineItemRefs.isNotEmpty()) {
                        val firstSpineId = opfData.spineItemRefs.first()
                        val firstSpineHref = opfData.manifestItems[firstSpineId]
                        if (!firstSpineHref.isNullOrBlank()) {
                            val xhtmlFile = File(opfDir, decodeUrl(firstSpineHref))
                            if (xhtmlFile.exists() && xhtmlFile.isFile) {
                                val xhtmlText = xhtmlFile.readText()
                                val imgPattern = java.util.regex.Pattern.compile("<(?:img|image)[^>]+(?:src|href)=[\"']([^\"']+)[\"']", java.util.regex.Pattern.CASE_INSENSITIVE)
                                val matcher = imgPattern.matcher(xhtmlText)
                                while (matcher.find()) {
                                    val relSrc = matcher.group(1)
                                    if (!relSrc.isNullOrBlank()) {
                                        val cleanSrc = decodeUrl(relSrc.substringBefore('#').substringBefore('?'))
                                        val spineDir = xhtmlFile.parentFile ?: tempDir
                                        val candidateFile = File(spineDir, cleanSrc)
                                        val foundMatch = allImages.firstOrNull {
                                            it.absolutePath == candidateFile.absolutePath || it.name.lowercase() == File(cleanSrc).name.lowercase()
                                        }
                                        if (foundMatch != null && foundMatch.exists()) {
                                            Log.d(TAG, "[COVER] 命中 优先级2 (首章节首图): ${foundMatch.absolutePath}")
                                            val bytes = foundMatch.readBytes()
                                            if (android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null) {
                                                selectedImgFile = foundMatch
                                                break
                                            } else {
                                                Log.e(TAG, "[COVER] 优先级2 图片解码失败")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Priority 3: Largest image file by size
                    if (selectedImgFile == null) {
                        val largestImg = allImages.maxByOrNull { it.length() }
                        if (largestImg != null) {
                            Log.d(TAG, "[COVER] 命中 优先级3 (最大体积图): ${largestImg.absolutePath}, 大小: ${largestImg.length()} 字节")
                            val bytes = largestImg.readBytes()
                            if (android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null) {
                                selectedImgFile = largestImg
                            } else {
                                Log.e(TAG, "[COVER] 优先级3 图片解码失败")
                            }
                        }
                    }

                    if (selectedImgFile != null) {
                        finalCoverFile = selectedImgFile
                        coverSource = "fallback"
                    }
                }
            }

            Log.d(TAG, "[COVER] 封面来源: $coverSource")

            var coverUri: String? = null
            if (finalCoverFile != null) {
                val bytes = finalCoverFile.readBytes()
                Log.d(TAG, "[COVER] 最终封面图片字节大小: ${bytes.size}")
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    Log.d(TAG, "[COVER] 最终封面图片解码成功: ${bitmap.width}x${bitmap.height}")
                } else {
                    Log.e(TAG, "[COVER] 最终封面图片解码失败")
                }

                val coverDir = File(context.filesDir, "epub_covers")
                if (!coverDir.exists()) coverDir.mkdirs()
                val ext = finalCoverFile.extension.ifBlank { "jpg" }
                val destCover = File(coverDir, "cover_${bookId}_${System.currentTimeMillis()}.$ext")
                finalCoverFile.copyTo(destCover, overwrite = true)
                coverUri = destCover.absolutePath
                Log.d(TAG, "[COVER] 缓存文件写入路径: ${destCover.absolutePath}, 写入是否成功: ${destCover.exists()}, 文件大小: ${destCover.length()}")
            } else {
                Log.w(TAG, "[COVER] 未能在 zip 内找到任何封面图片文件")
            }

            Log.d(TAG, "[COVER] Book.coverPath 最终赋值: $coverUri")

            // 7. Save chapters and update book metadata
            bookDao.insertChapters(chapters)
            val finalBook = initialBook.copy(
                id = bookId,
                coverUri = coverUri,
                totalChapters = chapters.size
            )
            bookDao.updateBook(finalBook)
            Log.d(TAG, "[COVER] 写入数据库结果: 成功")

            Log.d(TAG, "[EpubParser] Successfully imported EPUB book '${finalBook.title}' with ${chapters.size} chapters.")
            tempDir.deleteRecursively()
            Result.success(finalBook)
        } catch (t: Throwable) {
            Log.e(TAG, "[EpubParser] Error during EPUB import", t)
            tempDir.deleteRecursively()
            Result.failure(Exception(t.localizedMessage ?: "EPUB 解析失败"))
        }
    }

    private fun unzipEpub(context: Context, uri: Uri, destDir: File): Boolean {
        val charsets = listOf(StandardCharsets.UTF_8, Charset.forName("GBK"), Charset.forName("GB18030"))
        for (charset in charsets) {
            try {
                var success = false
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    ZipInputStream(inputStream, charset).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            val outFile = File(destDir, entry.name)
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { out ->
                                    zip.copyTo(out)
                                }
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                        success = true
                    }
                }
                if (success) {
                    val container = File(destDir, "META-INF/container.xml")
                    if (container.exists()) {
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "[EpubParser] Unzip with charset $charset failed, trying fallback", e)
            }
        }
        return false
    }

    private fun parseContainerXml(containerFile: File): String? {
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(containerFile.inputStream(), "UTF-8")

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                    val fullPath = parser.getAttributeValue(null, "full-path")
                    if (!fullPath.isNullOrBlank()) {
                        return fullPath
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "[EpubParser] Error parsing container.xml", e)
        }
        return null
    }

    private data class OpfData(
        val title: String,
        val author: String,
        val coverHref: String?,
        val epubVersion: String,
        val epub3CoverHref: String? = null,
        val epub3CoverId: String? = null,
        val epub2CoverId: String? = null,
        val epub2CoverHref: String? = null,
        val manifestItems: Map<String, String>, // id -> href
        val spineItemRefs: List<String>,       // list of idref
        val tocTitles: Map<String, String> = emptyMap() // href -> title
    )

    private fun findMatchingCoverFile(opfDir: File, tempDir: File, rawCoverHref: String?): Pair<File?, String?> {
        if (rawCoverHref.isNullOrBlank()) return Pair(null, null)
        val cleanHref = decodeUrl(rawCoverHref.trimStart('/').substringBefore('?').substringBefore('#'))

        // 1. Direct match in opfDir
        val fileOpf = File(opfDir, cleanHref)
        if (fileOpf.exists() && fileOpf.isFile) {
            return Pair(fileOpf, fileOpf.relativeToOrNull(tempDir)?.path ?: fileOpf.name)
        }

        // 2. Direct match in tempDir
        val fileTemp = File(tempDir, cleanHref)
        if (fileTemp.exists() && fileTemp.isFile) {
            return Pair(fileTemp, fileTemp.relativeToOrNull(tempDir)?.path ?: fileTemp.name)
        }

        val cleanLower = cleanHref.lowercase()
        val cleanNameLower = File(cleanHref).name.lowercase()

        // 3. Case-insensitive search in opfDir
        val matchOpf = opfDir.walkTopDown().firstOrNull { file ->
            if (!file.isFile) return@firstOrNull false
            val relPath = file.relativeToOrNull(opfDir)?.path?.replace('\\', '/')?.lowercase() ?: ""
            relPath == cleanLower || file.name.lowercase() == cleanNameLower
        }
        if (matchOpf != null) {
            return Pair(matchOpf, matchOpf.relativeToOrNull(tempDir)?.path ?: matchOpf.name)
        }

        // 4. Case-insensitive search in tempDir
        val matchTemp = tempDir.walkTopDown().firstOrNull { file ->
            if (!file.isFile) return@firstOrNull false
            val relPath = file.relativeToOrNull(tempDir)?.path?.replace('\\', '/')?.lowercase() ?: ""
            relPath == cleanLower || file.name.lowercase() == cleanNameLower
        }
        if (matchTemp != null) {
            return Pair(matchTemp, matchTemp.relativeToOrNull(tempDir)?.path ?: matchTemp.name)
        }

        return Pair(null, null)
    }

    private fun parseOpfXml(opfFile: File): OpfData {
        var title = ""
        var author = ""
        var coverId: String? = null
        var epub3CoverHref: String? = null
        var epub3CoverId: String? = null
        var epub2CoverHref: String? = null
        var guideCoverHref: String? = null
        var epubVersion = "2"
        val manifest = mutableMapOf<String, String>()
        val spine = mutableListOf<String>()

        try {
            val xmlText = readTextWithCharsetDetection(opfFile)
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlText))

            var eventType = parser.eventType
            var currentTag = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                        val tagLower = currentTag.lowercase()
                        if (tagLower == "package") {
                            for (i in 0 until parser.attributeCount) {
                                if (parser.getAttributeName(i).lowercase() == "version") {
                                    val verVal = parser.getAttributeValue(i)
                                    if (verVal.startsWith("3")) {
                                        epubVersion = "3"
                                    }
                                }
                            }
                        }
                        when (tagLower) {
                            "item" -> {
                                var id: String? = null
                                var href: String? = null
                                var properties: String? = null
                                for (i in 0 until parser.attributeCount) {
                                    val attrName = parser.getAttributeName(i).lowercase()
                                    val attrVal = parser.getAttributeValue(i)
                                    if (attrName == "id" || attrName.endsWith(":id")) id = attrVal
                                    if (attrName == "href" || attrName.endsWith(":href")) href = attrVal
                                    if (attrName == "properties" || attrName.endsWith(":properties")) properties = attrVal
                                }
                                if (!id.isNullOrBlank() && !href.isNullOrBlank()) {
                                    manifest[id] = href
                                    if (properties?.contains("cover-image") == true) {
                                        epub3CoverHref = href
                                        epub3CoverId = id
                                    }
                                }
                            }
                            "itemref" -> {
                                var idref: String? = null
                                for (i in 0 until parser.attributeCount) {
                                    val attrName = parser.getAttributeName(i).lowercase()
                                    if (attrName == "idref" || attrName.endsWith(":idref")) idref = parser.getAttributeValue(i)
                                }
                                if (!idref.isNullOrBlank()) {
                                    spine.add(idref)
                                }
                            }
                            "meta" -> {
                                var name: String? = null
                                var content: String? = null
                                var property: String? = null
                                for (i in 0 until parser.attributeCount) {
                                    val attrName = parser.getAttributeName(i).lowercase()
                                    val attrVal = parser.getAttributeValue(i)
                                    if (attrName == "name" || attrName.endsWith(":name")) name = attrVal
                                    if (attrName == "content" || attrName.endsWith(":content")) content = attrVal
                                    if (attrName == "property" || attrName.endsWith(":property")) property = attrVal
                                }
                                if (name.equals("cover", ignoreCase = true) && !content.isNullOrBlank()) {
                                    coverId = content
                                } else if (property.equals("cover-image", ignoreCase = true) && !content.isNullOrBlank()) {
                                    epub3CoverHref = content
                                }
                            }
                            "reference" -> {
                                var type: String? = null
                                var href: String? = null
                                for (i in 0 until parser.attributeCount) {
                                    val attrName = parser.getAttributeName(i).lowercase()
                                    val attrVal = parser.getAttributeValue(i)
                                    if (attrName == "type" || attrName.endsWith(":type")) type = attrVal
                                    if (attrName == "href" || attrName.endsWith(":href")) href = attrVal
                                }
                                if (type.equals("cover", ignoreCase = true) && !href.isNullOrBlank()) {
                                    guideCoverHref = href
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim() ?: ""
                        if (text.isNotBlank()) {
                            when (currentTag.lowercase()) {
                                "dc:title", "title" -> if (title.isBlank()) title = text
                                "dc:creator", "creator" -> if (author.isBlank()) author = text
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            if (coverId != null && epub2CoverHref == null) {
                epub2CoverHref = manifest[coverId]
            }
        } catch (e: Exception) {
            Log.e(TAG, "[EpubParser] Error parsing OPF XML", e)
        }

        // Priority resolution
        var finalCoverHref: String? = null
        if (!epub3CoverHref.isNullOrBlank()) {
            finalCoverHref = epub3CoverHref
            Log.d(TAG, "[EpubParser] Found EPUB3 cover-image in manifest/meta: $finalCoverHref")
        } else if (!epub2CoverHref.isNullOrBlank()) {
            finalCoverHref = epub2CoverHref
            Log.d(TAG, "[EpubParser] Found EPUB2 meta cover id '$coverId' -> href: $finalCoverHref")
        } else if (!guideCoverHref.isNullOrBlank()) {
            Log.d(TAG, "[EpubParser] [Fallback Branch] Trying guide reference cover fallback: $guideCoverHref")
            if (guideCoverHref.endsWith(".xhtml", ignoreCase = true) || guideCoverHref.endsWith(".html", ignoreCase = true)) {
                val guideFile = File(opfFile.parentFile, decodeUrl(guideCoverHref))
                if (guideFile.exists() && guideFile.isFile) {
                    val htmlText = guideFile.readText()
                    val imgMatcher = java.util.regex.Pattern.compile("<(?:img|image)[^>]+(?:src|href)=[\"']([^\"']+)[\"']", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(htmlText)
                    if (imgMatcher.find()) {
                        val extractedImg = imgMatcher.group(1)
                        if (!extractedImg.isNullOrBlank()) {
                            val guideDirRel = guideCoverHref.substringBeforeLast('/', "")
                            finalCoverHref = if (guideDirRel.isNotEmpty()) "$guideDirRel/$extractedImg" else extractedImg
                            Log.d(TAG, "[EpubParser] [Fallback Branch] Extracted image from guide HTML: $finalCoverHref")
                        }
                    }
                }
            } else {
                finalCoverHref = guideCoverHref
            }
        } else {
            Log.d(TAG, "[EpubParser] No cover image declaration found (EPUB3 / EPUB2 / Guide all empty).")
        }

        return OpfData(
            title = title,
            author = author,
            coverHref = finalCoverHref,
            epubVersion = epubVersion,
            epub3CoverHref = epub3CoverHref,
            epub3CoverId = epub3CoverId,
            epub2CoverId = coverId,
            epub2CoverHref = epub2CoverHref,
            manifestItems = manifest,
            spineItemRefs = spine
        )
    }

    private fun readTextWithCharsetDetection(file: File): String {
        return try {
            val bytes = file.readBytes()
            if (bytes.isEmpty()) return ""

            // Handle UTF-8 BOM
            if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
                return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
            }

            // Check XML/HTML declaration encoding
            val sampleSize = minOf(bytes.size, 2048)
            val headerSample = String(bytes, 0, sampleSize, StandardCharsets.ISO_8859_1)
            val encodingMatch = Regex("""(?:xml.*encoding|charset)\s*=\s*["']?([a-zA-Z0-9_-]+)["']?""", RegexOption.IGNORE_CASE)
                .find(headerSample)

            val charsetName = encodingMatch?.groupValues?.getOrNull(1)
            if (!charsetName.isNullOrBlank()) {
                try {
                    val cs = Charset.forName(charsetName)
                    return String(bytes, cs)
                } catch (_: Exception) { }
            }

            // Check if bytes are valid UTF-8
            if (isValidUtf8(bytes)) {
                String(bytes, StandardCharsets.UTF_8)
            } else {
                // Fallback to GBK / GB18030 for Chinese books
                try {
                    String(bytes, Charset.forName("GBK"))
                } catch (_: Exception) {
                    String(bytes, StandardCharsets.UTF_8)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[EpubParser] Error reading text file ${file.name}", e)
            file.readText(StandardCharsets.UTF_8)
        }
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean {
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            if (b <= 0x7F) {
                i++
            } else if (b in 0xC2..0xDF) {
                if (i + 1 >= bytes.size) return false
                val b2 = bytes[i + 1].toInt() and 0xFF
                if (b2 !in 0x80..0xBF) return false
                i += 2
            } else if (b in 0xE0..0xEF) {
                if (i + 2 >= bytes.size) return false
                val b2 = bytes[i + 1].toInt() and 0xFF
                val b3 = bytes[i + 2].toInt() and 0xFF
                if (b2 !in 0x80..0xBF || b3 !in 0x80..0xBF) return false
                i += 3
            } else if (b in 0xF0..0xF4) {
                if (i + 3 >= bytes.size) return false
                val b2 = bytes[i + 1].toInt() and 0xFF
                val b3 = bytes[i + 2].toInt() and 0xFF
                val b4 = bytes[i + 3].toInt() and 0xFF
                if (b2 !in 0x80..0xBF || b3 !in 0x80..0xBF || b4 !in 0x80..0xBF) return false
                i += 4
            } else {
                return false
            }
        }
        return true
    }

    private fun extractChapterTitle(rawHtml: String, tocTitle: String?): String {
        if (!tocTitle.isNullOrBlank()) return tocTitle

        // Try <h1>, <h2>, <h3>, <title> tags
        val titleRegex = Regex("""<(?:h1|h2|h3|title)[^>]*>(.*?)</(?:h1|h2|h3|title)>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val titleTagMatch = titleRegex.find(rawHtml)

        if (titleTagMatch != null) {
            val clean = extractCleanTextFromHtml(titleTagMatch.groupValues[1]).trim()
            if (clean.isNotBlank() && clean.length < 50) {
                return clean
            }
        }

        // Try first non-empty line of extracted text
        val cleanText = extractCleanTextFromHtml(rawHtml)
        val firstLine = cleanText.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: ""
        if (firstLine.isNotBlank()) {
            return if (firstLine.length > 30) firstLine.take(30) + "..." else firstLine
        }

        return "未命名章节"
    }

    private fun extractCleanTextFromHtml(html: String): String {
        if (html.isBlank()) return ""

        // 1. Remove <script>, <style>, <head>, and HTML comments
        var text = html.replace(Regex("""(?s)<head.*?>.*?</head>"""), "")
            .replace(Regex("""(?s)<script.*?>.*?</script>"""), "")
            .replace(Regex("""(?s)<style.*?>.*?</style>"""), "")
            .replace(Regex("""(?s)<!--.*?-->"""), "")

        // 2. Convert paragraph/block tags to newlines
        text = text.replace(Regex("""(?i)<(?:p|div|br|h[1-6]|li|tr)[^>]*>"""), "\n")
            .replace(Regex("""(?i)</(?:p|div|h[1-6]|li|tr)>"""), "\n")

        // 3. Strip all remaining HTML tags
        text = text.replace(Regex("""<[^>]+>"""), "")

        // 4. Unescape HTML entities
        text = unescapeHtml(text)

        // 5. Clean up redundant empty lines
        val lines = text.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return lines.joinToString("\n\n")
    }

    private fun unescapeHtml(input: String): String {
        var text = input
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&cent;", "¢")
            .replace("&pound;", "£")
            .replace("&yen;", "¥")
            .replace("&euro;", "€")
            .replace("&copy;", "©")
            .replace("&reg;", "®")

        // Numeric entities
        text = Regex("""&#(\d+);""").replace(text) { match ->
            val code = match.groupValues[1].toIntOrNull()
            if (code != null) code.toChar().toString() else match.value
        }

        // Hex entities
        text = Regex("""&#x([0-9a-fA-F]+);""").replace(text) { match ->
            val code = match.groupValues[1].toIntOrNull(16)
            if (code != null) code.toChar().toString() else match.value
        }

        return text
    }

    private fun decodeUrl(path: String): String {
        return try {
            URLDecoder.decode(path, "UTF-8")
        } catch (_: Exception) {
            path
        }
    }

    fun createSampleEpubFile(context: Context, isEpub3: Boolean): File {
        val fileName = if (isEpub3) "sample_epub3.epub" else "sample_epub2.epub"
        val epubFile = File(context.cacheDir, fileName)
        if (epubFile.exists()) epubFile.delete()

        val coverBmp = android.graphics.Bitmap.createBitmap(600, 800, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(coverBmp)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        paint.color = if (isEpub3) android.graphics.Color.parseColor("#1E3A8A") else android.graphics.Color.parseColor("#831843")
        canvas.drawRect(0f, 0f, 600f, 800f, paint)

        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 12f
        paint.color = android.graphics.Color.parseColor("#F59E0B")
        canvas.drawRect(24f, 24f, 576f, 776f, paint)

        paint.style = android.graphics.Paint.Style.FILL
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 44f
        paint.textAlign = android.graphics.Paint.Align.CENTER
        canvas.drawText(if (isEpub3) "EPUB3 示例图书" else "EPUB2 示例图书", 300f, 360f, paint)

        paint.textSize = 30f
        paint.color = android.graphics.Color.parseColor("#FDE68A")
        canvas.drawText("（含封面图片）", 300f, 440f, paint)

        val coverBytesStream = java.io.ByteArrayOutputStream()
        coverBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, coverBytesStream)
        val coverBytes = coverBytesStream.toByteArray()

        val opfXml = if (isEpub3) {
            """<?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="pub-id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="pub-id">urn:uuid:12345-epub3</dc:identifier>
                <dc:title>示例 EPUB3 电子书（带封面）</dc:title>
                <dc:creator>测试作者</dc:creator>
              </metadata>
              <manifest>
                <item id="cover-img" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>
                <item id="chap1" href="chap1.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine>
                <itemref idref="chap1"/>
              </spine>
            </package>""".trimIndent()
        } else {
            """<?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="pub-id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="pub-id">urn:uuid:67890-epub2</dc:identifier>
                <dc:title>示例 EPUB2 电子书（带封面）</dc:title>
                <dc:creator>测试作者</dc:creator>
                <meta name="cover" content="cover-image-id"/>
              </metadata>
              <manifest>
                <item id="cover-image-id" href="images/cover.jpg" media-type="image/jpeg"/>
                <item id="chap1" href="chap1.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine>
                <itemref idref="chap1"/>
              </spine>
            </package>""".trimIndent()
        }

        val containerXml = """<?xml version="1.0"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
          </rootfiles>
        </container>""".trimIndent()

        val chap1Xhtml = """<?xml version="1.0" encoding="utf-8"?>
        <!DOCTYPE html>
        <html xmlns="http://www.w3.org/1999/xhtml">
        <head><title>第一章 翻页验证与封面显示</title></head>
        <body>
          <h1>第一章 翻页验证与封面显示</h1>
          <p>这是一本专门用于验证 EPUB 封面提取与 3D 仿真翻页效果的测试电子书。</p>
          <p>请观察书架页面是否正确展示了本书的彩色封面，并在阅读界面测试平滑卷曲翻页。</p>
        </body>
        </html>""".trimIndent()

        java.util.zip.ZipOutputStream(FileOutputStream(epubFile)).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("mimetype"))
            zos.write("application/epub+zip".toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(java.util.zip.ZipEntry("META-INF/container.xml"))
            zos.write(containerXml.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(java.util.zip.ZipEntry("OEBPS/content.opf"))
            zos.write(opfXml.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(java.util.zip.ZipEntry("OEBPS/chap1.xhtml"))
            zos.write(chap1Xhtml.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(java.util.zip.ZipEntry("OEBPS/images/cover.jpg"))
            zos.write(coverBytes)
            zos.closeEntry()
        }

        return epubFile
    }
}
