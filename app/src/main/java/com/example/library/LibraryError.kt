package com.example.library

sealed class LibraryError : Exception() {
    object NetworkUnavailable : LibraryError()
    /** 网络请求失败，携带真实原因（HTTP 状态码 / DNS / TLS / 超时等），供界面展示与调试。 */
    data class NetworkDetail(override val message: String) : LibraryError()
    object SourceUnavailable : LibraryError()
    object AuthenticationRequired : LibraryError()
    object CloudflareBlocked : LibraryError()
    object InvalidFile : LibraryError()
    data class ParseFailed(override val message: String) : LibraryError()
    data class Unknown(override val message: String, val causeException: Throwable? = null) : LibraryError()
}
