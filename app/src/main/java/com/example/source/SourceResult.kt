package com.example.source

sealed class SourceResult<out T> {
    data class Success<out T>(val data: T) : SourceResult<T>()
    data class Error(val exception: SourceException) : SourceResult<Nothing>()

    inline fun <R> map(transform: (T) -> R): SourceResult<R> {
        return when (this) {
            is Success -> Success(transform(data))
            is Error -> Error(exception)
        }
    }

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }
}
