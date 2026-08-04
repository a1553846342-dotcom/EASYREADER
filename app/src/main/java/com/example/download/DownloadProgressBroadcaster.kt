package com.example.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DownloadProgressBroadcaster {
    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    fun updateState(bookId: String, state: DownloadState) {
        val current = _states.value.toMutableMap()
        current[bookId] = state
        _states.value = current
    }

    fun removeState(bookId: String) {
        val current = _states.value.toMutableMap()
        current.remove(bookId)
        _states.value = current
    }

    fun getState(bookId: String): DownloadState {
        return _states.value[bookId] ?: DownloadState.Idle
    }
}
