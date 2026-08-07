package com.example.source

/**
 * A book source that can browse online comics chapter by chapter.
 */
interface ComicSource : BookSource {
    suspend fun getChapters(bookId: String): SourceResult<List<ComicChapter>>
    suspend fun getChapterImages(chapterId: String): SourceResult<List<String>>

    /**
     * 每个图片 URL 对应的额外请求头（Referer/Cookie/签名等）。
     * 默认无；JS 源通过 onImageLoad 提供。
     */
    suspend fun getChapterImageHeaders(
        chapterId: String,
        urls: List<String>
    ): Map<String, Map<String, String>> = emptyMap()

    /**
     * 懒加载解析：把源返回的“图片页 URL”解析成真实图片 URL（e-hentai 等），
     * 阅读器/下载器在真正加载某一页时按需调用并缓存。默认不支持。
     */
    suspend fun resolveChapterImage(url: String): String? = null

    /** 懒加载解析后，真实图片 URL 需要的请求头。 */
    suspend fun getResolvedHeaders(url: String): Map<String, String> = emptyMap()

    /**
     * 封面图请求头（Referer/Cookie 等）。JS 源通过 onThumbnailLoad 提供。
     */
    suspend fun getCoverHeaders(url: String): Map<String, String> = emptyMap()
}
