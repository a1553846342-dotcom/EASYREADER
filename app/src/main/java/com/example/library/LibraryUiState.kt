package com.example.library

import androidx.compose.runtime.Immutable
import com.example.source.SearchBook

@Immutable
sealed class LibraryUiState {
    object Loading : LibraryUiState()
    object Empty : LibraryUiState()
    object Ready : LibraryUiState()
    object Searching : LibraryUiState()
    data class SearchResults(val results: List<SearchBook>) : LibraryUiState()
    data class AggregateGroup(
        val sourceId: String,
        val sourceName: String,
        val books: List<SearchBook>,
        val error: String?,
        val loading: Boolean = false
    )
    data class AggregateResults(
        val groups: List<AggregateGroup>,
        val running: Boolean
    ) : LibraryUiState()
    data class Downloading(val taskId: String, val progress: Int) : LibraryUiState()
    data class Error(val error: LibraryError) : LibraryUiState()
}
