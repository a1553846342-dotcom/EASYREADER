package com.example.source

/**
 * A single chapter of an online comic source.
 */
data class ComicChapter(
    val id: String,
    val title: String,
    val volume: String? = null,
    val order: Float = 0f,
    val external: Boolean = false,
    val externalUrl: String? = null
)
