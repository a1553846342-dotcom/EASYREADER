package com.example.source.importer

import android.content.Context
import android.net.Uri
import com.example.source.*
import com.example.source.impl.JsonBookSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.UUID

object SourceImporter {

    fun importFromJsonString(jsonStr: String): SourceResult<JsonBookSource> {
        if (jsonStr.isBlank()) {
            return SourceResult.Error(SourceException.ParseError("JSON内容为空"))
        }

        try {
            val root = JSONObject(jsonStr)

            val name = root.optString("name", "").ifBlank { "未命名书源" }
            val id = root.optString("id", "").ifBlank { "custom_" + UUID.randomUUID().toString().substring(0, 8) }
            val baseUrl = root.optString("baseUrl", "")

            val searchObj = root.optJSONObject("search")
                ?: return SourceResult.Error(SourceException.ParseError("缺少 search 节点规则"))

            val searchUrl = searchObj.optString("url", "")
            if (searchUrl.isBlank()) {
                return SourceResult.Error(SourceException.ParseError("search 规则中缺少 url 字段"))
            }

            val searchMethod = searchObj.optString("method", "GET")
            val searchListPath = searchObj.optString("listPath", "books")

            // 兼容两种写法：search.fields 嵌套，或 title/author/cover 直接平铺在 search 下
            val searchFieldsObj = searchObj.optJSONObject("fields") ?: searchObj
            val searchFields = BookFieldRule(
                id = searchFieldsObj?.optString("id", "id") ?: "id",
                title = searchFieldsObj?.optString("title", "title") ?: "title",
                author = searchFieldsObj?.optString("author", "author"),
                cover = searchFieldsObj?.optString("cover", "cover"),
                description = searchFieldsObj?.optString("description", "description"),
                format = searchFieldsObj?.optString("format", "format"),
                downloadUrl = searchFieldsObj?.optString("downloadUrl", "downloadUrl")
            )

            val searchHeaders = parseHeaders(searchObj.optJSONObject("headers"))
            val searchBody = searchObj.optString("body", "").ifBlank { null }

            val searchRule = SearchRule(
                url = searchUrl,
                method = searchMethod,
                listPath = searchListPath,
                fields = searchFields,
                headers = searchHeaders,
                body = searchBody
            )

            var detailRule: DetailRule? = null
            val detailObj = root.optJSONObject("detail")
            if (detailObj != null) {
                val detailUrl = detailObj.optString("url", "")
                if (detailUrl.isNotBlank()) {
                    val detailMethod = detailObj.optString("method", "GET")
                    val detailFieldsObj = detailObj.optJSONObject("fields") ?: detailObj
                    val detailFields = BookFieldRule(
                        id = detailFieldsObj?.optString("id", "id") ?: "id",
                        title = detailFieldsObj?.optString("title", "title") ?: "title",
                        author = detailFieldsObj?.optString("author", "author"),
                        cover = detailFieldsObj?.optString("cover", "cover"),
                        description = detailFieldsObj?.optString("description", "description"),
                        format = detailFieldsObj?.optString("format", "format"),
                        downloadUrl = detailFieldsObj?.optString("downloadUrl", "downloadUrl")
                    )
                    val detailHeaders = parseHeaders(detailObj.optJSONObject("headers"))
                    val detailBody = detailObj.optString("body", "").ifBlank { null }
                    detailRule = DetailRule(
                        url = detailUrl,
                        method = detailMethod,
                        fields = detailFields,
                        headers = detailHeaders,
                        body = detailBody
                    )
                }
            }

            var downloadRule = DownloadRule()
            val downloadObj = root.optJSONObject("download")
            if (downloadObj != null) {
                val dlUrl = downloadObj.optString("url", null)
                val dlUrlField = downloadObj.optString("urlField", "downloadUrl")
                val dlFormat = downloadObj.optString("format", "epub")
                val dlHeaders = parseHeaders(downloadObj.optJSONObject("headers"))
                downloadRule = DownloadRule(
                    url = if (dlUrl.isNull_or_blank()) null else dlUrl,
                    urlField = if (dlUrlField.isNull_or_blank()) null else dlUrlField,
                    defaultFormat = dlFormat.ifBlank { "epub" },
                    headers = dlHeaders
                )
            }

            val config = SourceConfig(
                id = id,
                name = name,
                baseUrl = baseUrl,
                search = searchRule,
                detail = detailRule,
                download = downloadRule,
                enabled = true,
                isCustom = true
            )

            return SourceResult.Success(JsonBookSource(config))
        } catch (e: JSONException) {
            return SourceResult.Error(SourceException.ParseError("JSON解析语法错误: ${e.message}"))
        } catch (e: Exception) {
            return SourceResult.Error(SourceException.ParseError("解析书源失败: ${e.message}"))
        }
    }

    suspend fun importFromUri(context: Context, uri: Uri): SourceResult<Pair<JsonBookSource, String>> =
        withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext SourceResult.Error(SourceException.ParseError("无法打开选择的文件"))
                
                val jsonStr = inputStream.use { stream ->
                    BufferedReader(InputStreamReader(stream)).readText()
                }

                when (val result = importFromJsonString(jsonStr)) {
                    is SourceResult.Success -> SourceResult.Success(Pair(result.data, jsonStr))
                    is SourceResult.Error -> SourceResult.Error(result.exception)
                }
            } catch (e: Exception) {
                SourceResult.Error(SourceException.ParseError("读取文件失败: ${e.message}"))
            }
        }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()

    private fun parseHeaders(headersObj: JSONObject?): Map<String, String> {
        if (headersObj == null) return emptyMap()
        val result = mutableMapOf<String, String>()
        headersObj.keys().forEach { key ->
            val value = headersObj.optString(key).trim()
            if (value.isNotBlank()) {
                result[key] = value
            }
        }
        return result
    }
}
