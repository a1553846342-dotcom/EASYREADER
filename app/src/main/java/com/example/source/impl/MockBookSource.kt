package com.example.source.impl

import android.content.Context
import com.example.source.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MockBookSource(private val context: Context) : BookSource {
    override val id: String = "mock-source"
    override val name: String = "测试书源 (Mock)"
    override val capabilities: SourceCapabilities = SourceCapabilities(environmentOnly = true)

    private var isLoggedInState: Boolean = false

    override suspend fun search(keyword: String): SourceResult<List<SearchBook>> {
        return withContext(Dispatchers.IO) {
            delay(500)
            val list = listOf(
                SearchBook(
                    id = "pg11",
                    sourceId = id,
                    title = "Alice's Adventures in Wonderland",
                    author = "Lewis Carroll",
                    cover = "https://www.gutenberg.org/cache/epub/11/pg11.cover.medium.jpg",
                    description = "A classic tale.",
                    format = "epub",
                    downloadUrl = "https://www.gutenberg.org/cache/epub/11/pg11-images.epub"
                ),
                SearchBook(
                    id = "pg1342",
                    sourceId = id,
                    title = "Pride and Prejudice",
                    author = "Jane Austen",
                    cover = "https://www.gutenberg.org/cache/epub/1342/pg1342.cover.medium.jpg",
                    description = "A classic romance novel.",
                    format = "epub",
                    downloadUrl = "https://www.gutenberg.org/cache/epub/1342/pg1342-images.epub"
                )
            ).filter { it.title.contains(keyword, ignoreCase = true) || it.author.contains(keyword, ignoreCase = true) || keyword.isBlank() }
            SourceResult.Success(list)
        }
    }

    override suspend fun getDetail(bookId: String): SourceResult<SearchBook> {
        return withContext(Dispatchers.IO) {
            delay(300)
            SourceResult.Success(
                SearchBook(
                    id = bookId,
                    sourceId = id,
                    title = "Mock Detail ($bookId)",
                    author = "Mock Author",
                    cover = null,
                    description = "Mock description for $bookId.",
                    format = "epub",
                    downloadUrl = "https://www.gutenberg.org/cache/epub/11/pg11-images.epub"
                )
            )
        }
    }

    override suspend fun getDownloadInfo(bookId: String): SourceResult<DownloadInfo> {
        return withContext(Dispatchers.IO) {
            delay(200)
            SourceResult.Success(
                DownloadInfo(
                    url = "https://www.gutenberg.org/cache/epub/11/pg11-images.epub",
                    fileName = "$bookId.epub",
                    format = "epub"
                )
            )
        }
    }

    override suspend fun login(credential: LoginCredential): SourceResult<Boolean> {
        isLoggedInState = true
        return SourceResult.Success(true)
    }

    override suspend fun logout() {
        isLoggedInState = false
    }

    override suspend fun isLoggedIn(): Boolean {
        return isLoggedInState
    }
}
