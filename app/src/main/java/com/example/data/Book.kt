package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ContentType {
    NOVEL,
    COMIC
}

@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val author: String = "未知作者",
    val filePath: String,
    val coverUri: String? = null,
    val category: String = "未分类",
    val currentChapterIndex: Int = 0,
    val scrollOffset: Int = 0,
    val isFinished: Boolean = false,
    val totalChapters: Int = 0,
    val contentType: String = "NOVEL",
    val addedTime: Long = System.currentTimeMillis(),
    val lastReadTime: Long = System.currentTimeMillis()
) {
    val isComic: Boolean
        get() = contentType == "COMIC"
}

@Entity(tableName = "chapters")
data class Chapter(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookId: Int,
    val chapterOrder: Int,
    val title: String,
    val content: String,
    val startCharIndex: Long = 0,
    val endCharIndex: Long = 0
)

@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["bookId", "chapterIndex"], unique = true)]
)
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookId: Int,
    val chapterIndex: Int,
    val scrollOffset: Int,
    val title: String,
    val snippet: String,
    val createdTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "highlights")
data class Highlight(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookId: Int,
    val chapterIndex: Int,
    val selectedText: String,
    val note: String = "",
    val colorHex: String = "#7FD8C8",
    val createdTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)

data class SearchResultItem(
    val chapterIndex: Int,
    val chapterTitle: String,
    val snippet: String
)
