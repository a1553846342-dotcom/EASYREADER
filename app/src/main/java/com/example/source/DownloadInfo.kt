package com.example.source

import androidx.compose.runtime.Immutable

@Immutable
data class DownloadInfo(
    val url: String,
    val fileName: String,
    val format: String = "epub",
    val size: Long? = null,
    val headers: Map<String, String> = emptyMap(),
    val referer: String? = null
)
