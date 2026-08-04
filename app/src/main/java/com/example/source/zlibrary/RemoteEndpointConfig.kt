package com.example.source.zlibrary

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RemoteEndpointConfig(
    val url: String,
    val priority: Int,
    val updatedAt: Long,
    val version: String
)
