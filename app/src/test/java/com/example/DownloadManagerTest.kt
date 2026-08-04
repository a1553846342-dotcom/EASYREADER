package com.example

import com.example.download.*
import org.junit.Assert.*
import org.junit.Test

class DownloadManagerUnitTest {

    @Test
    fun testDownloadProgressBroadcasterStateTransitions() {
        val bookId = "test_book_1"

        // Initial state
        assertEquals(DownloadState.Idle, DownloadProgressBroadcaster.getState(bookId))

        // Update to Pending
        DownloadProgressBroadcaster.updateState(bookId, DownloadState.Pending)
        assertEquals(DownloadState.Pending, DownloadProgressBroadcaster.getState(bookId))

        // Update to Downloading
        val downloadingState = DownloadState.Downloading(500L, 1000L, 0.5f)
        DownloadProgressBroadcaster.updateState(bookId, downloadingState)
        val currentState = DownloadProgressBroadcaster.getState(bookId)
        assertTrue(currentState is DownloadState.Downloading)
        assertEquals(0.5f, (currentState as DownloadState.Downloading).progress, 0.001f)

        // Update to Paused
        val pausedState = DownloadState.Paused(500L, 1000L)
        DownloadProgressBroadcaster.updateState(bookId, pausedState)
        assertTrue(DownloadProgressBroadcaster.getState(bookId) is DownloadState.Paused)

        // Update to Success
        val successState = DownloadState.Success("/path/to/file.epub")
        DownloadProgressBroadcaster.updateState(bookId, successState)
        assertTrue(DownloadProgressBroadcaster.getState(bookId) is DownloadState.Success)

        // Remove state
        DownloadProgressBroadcaster.removeState(bookId)
        assertEquals(DownloadState.Idle, DownloadProgressBroadcaster.getState(bookId))
    }

    @Test
    fun testDownloadTypeConverters() {
        val converters = DownloadTypeConverters()

        assertEquals("DOWNLOADING", converters.fromStatus(DownloadStatus.DOWNLOADING))
        assertEquals(DownloadStatus.PAUSED, converters.toStatus("PAUSED"))
        assertEquals(DownloadStatus.COMPLETED, converters.toStatus("COMPLETED"))
        // Fallback on invalid string
        assertEquals(DownloadStatus.FAILED, converters.toStatus("INVALID_STATUS"))
    }
}
