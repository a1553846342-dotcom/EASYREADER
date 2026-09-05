package com.example.source.zlibrary

/**
 * 远程节点配置（zlib_endpoints.json 的一行）。
 * 第十一轮瘦身：去掉 Moshi @JsonClass（随 KotlinJsonAdapterFactory 一起移除
 * moshi-kotlin / kotlin-reflect 依赖），序列化改为 org.json 手写。
 */
data class RemoteEndpointConfig(
    val url: String,
    val priority: Int,
    val updatedAt: Long,
    val version: String
)
