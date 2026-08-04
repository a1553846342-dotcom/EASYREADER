package com.example.source

data class BookFieldRule(
    val id: String = "id",
    val title: String = "title",
    val author: String? = "author",
    val cover: String? = "cover",
    val description: String? = "description",
    val format: String? = "format",
    val downloadUrl: String? = "downloadUrl"
)

data class SearchRule(
    val url: String, // e.g. https://example.com/search?q={keyword}
    val method: String = "GET",
    val listPath: String = "books", // e.g. "data.books"
    val fields: BookFieldRule = BookFieldRule(),
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null
)

data class DetailRule(
    val url: String, // e.g. https://example.com/book/{id}
    val method: String = "GET",
    val fields: BookFieldRule = BookFieldRule(),
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null
)

data class DownloadRule(
    val url: String? = null, // e.g. https://example.com/download/{id}
    val urlField: String? = "downloadUrl", // if url in JSON item
    val defaultFormat: String = "epub",
    val headers: Map<String, String> = emptyMap()
)

data class SourceConfig(
    val id: String,
    val name: String,
    val baseUrl: String = "",
    val search: SearchRule,
    val detail: DetailRule? = null,
    val download: DownloadRule = DownloadRule(),
    val enabled: Boolean = true,
    val priority: Int = 0,
    val isCustom: Boolean = true
)
