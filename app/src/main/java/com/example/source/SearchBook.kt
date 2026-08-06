package com.example.source

import androidx.compose.runtime.Immutable

@Immutable
data class SearchBook(
    val id: String,
    val sourceId: String,
    val title: String,
    val author: String,
    val cover: String? = null,
    val description: String? = null,
    val format: String = "epub",
    val language: String? = null,
    val size: Long? = null,
    val downloadUrl: String? = null
)
