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

/**
 * HTML(CSS 选择器) 书源规则 —— 允许用户在 App 内粘贴 JSON 定义任意 HTML 站点，
 * 无需修改代码即可添加小说/漫画源。
 */
data class HtmlSearchRule(
    val url: String,                    // 搜索地址，支持 {keyword} {page}
    val listSelector: String,           // 结果列表项 CSS
    val titleSelector: String = "",     // 标题，支持 "css@text" / "css@attr"
    val authorSelector: String = "",
    val coverSelector: String = "",
    val detailUrlSelector: String = "", // 详情链接，默认 "a@href"
    val introSelector: String = "",     // 简介（Legado ruleSearch.intro）
    val charset: String? = null         // 页面编码（gbk / gb2312 / utf-8），默认自动
)

data class HtmlChapterRule(
    val url: String,                    // 目录页地址，支持 {id}
    val listSelector: String,           // 章节列表项 CSS
    val nameSelector: String = "text",
    val hrefSelector: String = "href"
)

data class HtmlContentRule(
    val url: String,                    // 阅读页地址，支持 {chapterUrl}
    val imageSelector: String           // 图片选择器，如 "img.page@src" 或 "img@data-src"
)

data class SourceConfig(
    val id: String,
    val name: String,
    val baseUrl: String = "",
    val search: SearchRule,
    val detail: DetailRule? = null,
    val download: DownloadRule = DownloadRule(),
    val htmlSearch: HtmlSearchRule? = null,
    val htmlChapters: HtmlChapterRule? = null,
    val htmlContent: HtmlContentRule? = null,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val isCustom: Boolean = true,
    val insecureTls: Boolean = false
)
