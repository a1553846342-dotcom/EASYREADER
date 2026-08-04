package com.example.source.impl

import com.example.source.*
import com.example.source.parser.JsonPathResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class JsonBookSource(
    val config: SourceConfig,
    private val client: OkHttpClient = defaultClient
) : BookSource {

    /** 搜索结果缓存：让 getDownloadInfo 直接复用列表里的下载链接/标题，避免必须配置 detail。 */
    private val searchItemCache = mutableMapOf<String, SearchBook>()
    private val searchRawCache = mutableMapOf<String, JSONObject>()

    companion object {
        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    override val id: String = config.id
    override val name: String = config.name
    override val capabilities: SourceCapabilities = SourceCapabilities(supportImport = true)

    override suspend fun search(keyword: String): SourceResult<List<SearchBook>> {
        return withContext(Dispatchers.IO) {
            try {
                val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
                val requestUrl = config.search.url.replace("{keyword}", encodedKeyword)
                val fullUrl = resolveUrl(requestUrl)

                val requestBuilder = Request.Builder().url(fullUrl)
                config.search.headers.forEach { (k, v) -> requestBuilder.header(k, v) }
                val request = buildRequest(requestBuilder, config.search.method, config.search.body, encodedKeyword)

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext SourceResult.Error(
                        SourceException.NetworkError("HTTP error status code: ${response.code}")
                    )
                }

                val bodyStr = response.body?.string() ?: ""
                val jsonItems = JsonPathResolver.resolveArray(bodyStr, config.search.listPath)

                jsonItems.forEach { jsonObj ->
                    parseSearchBook(jsonObj, config.search.fields)?.let { book ->
                        searchItemCache[book.id] = book
                        searchRawCache[book.id] = jsonObj
                    }
                }

                val books = jsonItems.mapNotNull { jsonObj ->
                    parseSearchBook(jsonObj, config.search.fields)
                }

                SourceResult.Success(books)
            } catch (e: IOException) {
                SourceResult.Error(SourceException.NetworkError("网络连接错误: ${e.message}", e))
            } catch (e: JSONException) {
                SourceResult.Error(SourceException.ParseError("JSON解析异常: ${e.message}"))
            } catch (e: Exception) {
                SourceResult.Error(SourceException.Unknown("未知错误: ${e.message}", e))
            }
        }
    }

    override suspend fun getDetail(bookId: String): SourceResult<SearchBook> {
        return withContext(Dispatchers.IO) {
            val detailRule = config.detail
            if (detailRule == null) {
                // Return a basic placeholder book if detail rule is omitted
                return@withContext SourceResult.Success(
                    SearchBook(
                        id = bookId,
                        sourceId = id,
                        title = "Book $bookId",
                        author = "Unknown"
                    )
                )
            }

            try {
                val encodedId = URLEncoder.encode(bookId, "UTF-8")
                val requestUrl = detailRule.url.replace("{id}", encodedId)
                val fullUrl = resolveUrl(requestUrl)

                val requestBuilder = Request.Builder().url(fullUrl)
                detailRule.headers.forEach { (k, v) -> requestBuilder.header(k, v) }
                val request = buildRequest(requestBuilder, detailRule.method, detailRule.body, encodedId)

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext SourceResult.Error(
                        SourceException.NetworkError("HTTP error status code: ${response.code}")
                    )
                }

                val bodyStr = response.body?.string() ?: ""
                val jsonObj = try {
                    JSONObject(bodyStr)
                } catch (e: Exception) {
                    return@withContext SourceResult.Error(SourceException.ParseError("无效的详情 JSON"))
                }

                val book = parseSearchBook(jsonObj, detailRule.fields, defaultId = bookId)
                if (book != null) {
                    SourceResult.Success(book)
                } else {
                    SourceResult.Error(SourceException.BookNotFound)
                }
            } catch (e: IOException) {
                SourceResult.Error(SourceException.NetworkError("网络连接错误: ${e.message}", e))
            } catch (e: JSONException) {
                SourceResult.Error(SourceException.ParseError("JSON解析异常: ${e.message}"))
            } catch (e: Exception) {
                SourceResult.Error(SourceException.Unknown("未知错误: ${e.message}", e))
            }
        }
    }

    override suspend fun getDownloadInfo(bookId: String): SourceResult<DownloadInfo> {
        return withContext(Dispatchers.IO) {
            val downloadRule = config.download
            val downloadUrlTemplate = downloadRule.url
            val cachedBook = searchItemCache[bookId]
            val cachedRaw = searchRawCache[bookId]
            val encodedId = URLEncoder.encode(bookId, "UTF-8")
            val fallbackDetail = getDetail(bookId).getOrNull()

            val targetUrl = if (!downloadUrlTemplate.isNullOrEmpty()) {
                resolveUrl(downloadUrlTemplate.replace("{id}", encodedId))
            } else if (!cachedBook?.downloadUrl.isNullOrBlank()) {
                resolveUrl(cachedBook!!.downloadUrl!!)
            } else if (!downloadRule.urlField.isNullOrBlank() && cachedRaw != null) {
                val raw = JsonPathResolver.getString(cachedRaw, downloadRule.urlField)
                if (!raw.isNullOrBlank()) resolveUrl(raw) else ""
            } else {
                // Fetch detail to locate downloadUrl field
                fallbackDetail?.downloadUrl ?: ""
            }

            val displayTitle = cachedBook?.title
                ?: fallbackDetail?.title
                ?: bookId
            val cleanTitle = displayTitle
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .trim()
                .ifBlank { bookId }

            if (!validateUrl(targetUrl)) {
                return@withContext SourceResult.Error(SourceException.ParseError("非法或不受支持的下载链接: $targetUrl"))
            }

            SourceResult.Success(
                DownloadInfo(
                    url = targetUrl,
                    fileName = "$cleanTitle.${downloadRule.defaultFormat}",
                    format = downloadRule.defaultFormat,
                    headers = downloadRule.headers,
                    referer = config.baseUrl.trimEnd('/').ifBlank { null }
                )
            )
        }
    }

    override suspend fun login(credential: LoginCredential): SourceResult<Boolean> {
        return SourceResult.Success(false)
    }

    override suspend fun logout() {}

    override suspend fun isLoggedIn(): Boolean = false

    private fun parseSearchBook(jsonObj: JSONObject, fields: BookFieldRule, defaultId: String = ""): SearchBook? {
        val extractedId = JsonPathResolver.getString(jsonObj, fields.id) ?: defaultId
        val title = JsonPathResolver.getString(jsonObj, fields.title) ?: return null
        val author = JsonPathResolver.getString(jsonObj, fields.author) ?: "未知"
        val rawCover = JsonPathResolver.getString(jsonObj, fields.cover)
        val cover = if (!rawCover.isNullOrEmpty()) resolveUrl(rawCover) else null
        val description = JsonPathResolver.getString(jsonObj, fields.description)
        val format = JsonPathResolver.getString(jsonObj, fields.format) ?: config.download.defaultFormat
        val rawDownloadUrl = JsonPathResolver.getString(jsonObj, fields.downloadUrl)
        val downloadUrl = if (!rawDownloadUrl.isNullOrEmpty()) resolveUrl(rawDownloadUrl) else null

        return SearchBook(
            id = extractedId,
            sourceId = id,
            title = title,
            author = author,
            cover = cover,
            description = description,
            format = format,
            downloadUrl = downloadUrl
        )
    }

    private fun validateUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.trim().lowercase()
        if (lower.startsWith("javascript:") || lower.startsWith("file:")) return false
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    private fun resolveUrl(url: String): String {
        val lower = url.trim().lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("javascript:") || lower.startsWith("file:")) return url
        val base = config.baseUrl.trimEnd('/')
        val path = url.trimStart('/')
        return if (base.isEmpty()) url else "$base/$path"
    }

    private fun buildRequest(
        builder: okhttp3.Request.Builder,
        method: String,
        bodyTemplate: String?,
        encodedParam: String
    ): Request {
        val upperMethod = method.uppercase()
        return if (upperMethod == "POST" || upperMethod == "PUT" || upperMethod == "PATCH") {
            val jsonBody = bodyTemplate
                ?.replace("{keyword}", encodedParam)
                ?.replace("{id}", encodedParam)
                ?.ifBlank { null }
                ?: "{}"
            builder.method(
                upperMethod,
                jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
            ).build()
        } else {
            builder.get().build()
        }
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
}
