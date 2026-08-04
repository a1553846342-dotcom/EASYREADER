package com.example.download

import androidx.compose.runtime.Immutable

@Immutable
sealed class DownloadState {
    object Idle : DownloadState()
    object Pending : DownloadState()
    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val progress: Float
    ) : DownloadState()
    data class Paused(
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : DownloadState()
    data class Success(val path: String) : DownloadState()
    data class Error(val message: String) : DownloadState()
}
