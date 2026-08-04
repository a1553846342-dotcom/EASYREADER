package com.example.source

sealed class SourceException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    object LoginRequired : SourceException("需要登录后继续操作")
    data class NetworkError(val errorMsg: String, val throwable: Throwable? = null) : SourceException(errorMsg, throwable)
    data class ParseError(val errorMsg: String) : SourceException(errorMsg)
    object BookNotFound : SourceException("未能找到对应图书")
    data class Unknown(val errorMsg: String, val throwable: Throwable? = null) : SourceException(errorMsg, throwable)
}
