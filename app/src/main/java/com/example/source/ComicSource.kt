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
     * 封面图请求头（Referer/Cookie 等）。JS 源通过 onThumbnailLoad 提供。
     */
    suspend fun getCoverHeaders(url: String): Map<String, String> = emptyMap()
}
