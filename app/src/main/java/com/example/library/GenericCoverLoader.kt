package com.example.library

import android.content.Context
import coil.ImageLoader
import com.example.source.zlibrary.network.SystemProxyResolver
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Protocol

/**
 * 通用封面加载器：只带浏览器 UA，不附加任何站点专用 Referer/Cookie，
 * 供 MangaDex、JS 源等非 ZLibrary 封面使用。
 */
object GenericCoverLoader {
    @Volatile
    private var cachedLoader: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return cachedLoader ?: synchronized(this) {
            cachedLoader ?: buildLoader(context.applicationContext).also { cachedLoader = it }
        }
    }

    private fun buildLoader(context: Context): ImageLoader {
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .protocols(listOf(Protocol.HTTP_1_1))
            .addInterceptor { chain ->
                val req = chain.request()
                chain.proceed(
                    req.newBuilder()
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.5359.128 Mobile Safari/537.36"
                        )
                        .build()
                )
            }
        SystemProxyResolver.resolve(context)?.let { clientBuilder.proxy(it) }
        return ImageLoader.Builder(context)
            .okHttpClient(clientBuilder.build())
            .crossfade(true)
            .build()
    }
}
