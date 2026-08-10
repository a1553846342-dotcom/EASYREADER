package com.example.source

import androidx.compose.runtime.Immutable

/**
 * 一本书的某个可下载格式。
 *
 * [downloadUrl] 非空表示该格式已有可直接下载的链接
 * （eapi 的 downloadLink 或 HTML 详情页 /dl/ 直链）。
 */
@Immutable
data class BookFormat(
    val format: String,
    val downloadUrl: String? = null,
    val size: Long? = null,
    val sizeText: String? = null,
    /** eapi 格式变体自己的数字 id（下载该格式时需要）。 */
    val eapiId: String? = null,
    /** eapi 格式变体自己的短 hash（下载该格式时需要）。 */
    val eapiHash: String? = null
)
