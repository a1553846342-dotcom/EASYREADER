package com.example.library

sealed class LibraryError : Exception() {
    object NetworkUnavailable : LibraryError()
    object SourceUnavailable : LibraryError()
    object AuthenticationRequired : LibraryError()
    object CloudflareBlocked : LibraryError()
    object InvalidFile : LibraryError()
    data class ParseFailed(override val message: String) : LibraryError()
    data class Unknown(override val message: String, val causeException: Throwable? = null) : LibraryError()
}
