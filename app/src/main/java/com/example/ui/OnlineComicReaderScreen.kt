package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ReadingSession
import com.example.library.ImageBytes
import com.example.library.MhttuImageDecryptor
import com.example.source.js.JsCookieJar
import com.example.source.js.JsImageProcessor
import com.example.ui.components.AppLiquidButton
import com.example.ui.components.ChasingDots
import com.example.ui.comic.ComicPageRef
import com.example.ui.comic.ComicReaderCore
import com.example.ui.comic.ComicTocEntry
import com.example.ui.theme.MintPrimary
import coil.ImageLoader
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * 在线漫画阅读页（升级版）：
 * 由统一阅读引擎 [ComicReaderCore] 驱动，保留原有解密/代理/重试图片加载管线；
 * 新增：章节目录、上一章/下一章连续阅读、每漫画独立配置（bookKey）。
 */
@Composable
fun OnlineComicReaderScreen(
    title: String,
    imageUrls: List<String>,
    loading: Boolean,
    error: String?,
    referer: String? = null,
    imageHeaders: Map<String, Map<String, String>> = emptyMap(),
    resolveImage: (suspend (String) -> String?)? = null,
    resolveImageHeaders: (suspend (String) -> Map<String, String>)? = null,
    onRecordTime: (Long) -> Unit = {},
    onSessionEnd: (ReadingSession) -> Unit = {},
    onBack: () -> Unit,
    onRetry: () -> Unit,
    /* ── 升级新增（可选，保持旧调用兼容） ── */
    bookKey: String? = null,
    bookTitle: String? = null,
    chapters: List<ComicTocEntry> = emptyList(),
    currentChapterIndex: Int = -1,
    onJumpToChapter: ((Int) -> Unit)? = null,
    onPrevChapter: (() -> Unit)? = null,
    onNextChapter: (() -> Unit)? = null,
) {
    // 在线阅读计时：只在 App 前台 + 屏幕亮着时累计
    ReadingTimerEffect(
        bookId = null,
        bookTitle = bookTitle ?: title,
        onFlush = { seconds -> onRecordTime(seconds) },
        onSessionEnd = { session -> onSessionEnd(session) }
    )

    val context = LocalContext.current
    val currentResolveImage = rememberUpdatedState(resolveImage)
    val currentResolveImageHeaders = rememberUpdatedState(resolveImageHeaders)

    // 漫画阅读专用加载器：进程级单例（避免反复进出阅读器叠加 Coil 缓存与线程池）
    val imageLoader = remember {
        sharedResolveImage = currentResolveImage
        sharedResolveImageHeaders = currentResolveImageHeaders
        synchronized(ComicLoaderLock) {
            sharedComicLoader ?: buildComicImageLoader(context).also { sharedComicLoader = it }
        }
    }

    // 离开阅读器时释放回调引用（拦截器侧已 ?. 判空，飞行中的请求不受影响）
    DisposableEffect(Unit) {
        onDispose {
            sharedResolveImage = null
            sharedResolveImageHeaders = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when {
            loading && imageUrls.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ChasingDots(size = 52.dp, color = MintPrimary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("正在加载图片…", color = Color.White, fontSize = 14.sp)
                    }
                }
            }

            error != null && imageUrls.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = error,
                            color = Color(0xFFFF8A8A),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        AppLiquidButton(text = "重试", onClick = onRetry)
                    }
                }
            }

            imageUrls.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有可显示的图片", color = Color.White)
                }
            }

            else -> {
                val pages = remember(imageUrls) {
                    imageUrls.mapIndexed { i, url ->
                        ComicPageRef.Remote(
                            id = "u_${url.hashCode()}_$i",
                            url = url,
                            headers = imageHeaders[url].orEmpty(),
                            referer = referer
                        )
                    }
                }
                ComicReaderCore(
                    pages = pages,
                    title = bookTitle ?: title,
                    chapterTitle = title,
                    bookKey = bookKey,
                    initialPage = 0,
                    toc = chapters,
                    currentChapterIndex = currentChapterIndex,
                    onJumpToChapter = onJumpToChapter,
                    onPrevChapter = onPrevChapter,
                    onNextChapter = onNextChapter,
                    chapterNavLabel = "章",
                    remoteImageLoader = imageLoader,
                    onExit = onBack,
                )
            }
        }
    }
}

/* ── 进程级共享加载器（拦截器通过 State 引用读取最新解密回调） ── */

private val ComicLoaderLock = Any()
private var sharedComicLoader: ImageLoader? = null
private var sharedResolveImage: State<(suspend (String) -> String?)?>? = null
private var sharedResolveImageHeaders: State<(suspend (String) -> Map<String, String>)?>? = null

/** 构建漫画专用加载器：tu.mhttu.cc 自动 AES 解密、代理路由、AVIF 变体回退、3 次重试 */
private fun buildComicImageLoader(context: android.content.Context): ImageLoader {
    val client = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .proxySelector(com.example.source.js.JsSourceProxy.selector(context))
        .addInterceptor { chain ->
            var original = chain.request()
            val resolveImg = sharedResolveImage
            if (resolveImg?.value != null) {
                val pageUrl = original.url.toString()
                val resolved = kotlinx.coroutines.runBlocking { resolveImg.value!!(pageUrl) }
                if (!resolved.isNullOrBlank() && resolved != pageUrl) {
                    if (resolved.startsWith("file:")) {
                        // H@H 图片已由 Cronet 下载缓存到本地，直接作为响应返回
                        val f = java.io.File(java.net.URI.create(resolved))
                        if (f.exists() && f.length() > 0) {
                            val bytes = f.readBytes()
                            return@addInterceptor okhttp3.Response.Builder()
                                .request(original)
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .header("Content-Type", "image/*")
                                .body(bytes.toResponseBody("image/*".toMediaType()))
                                .build()
                        }
                    } else {
                        val rb = original.newBuilder().url(resolved)
                        val rh = kotlinx.coroutines.runBlocking {
                            sharedResolveImageHeaders?.value?.invoke(resolved)
                        }.orEmpty()
                        rh.forEach { (k, v) -> rb.header(k, v) }
                        original = rb.build()
                    }
                }
            }
            val builder = original.newBuilder()
            if (original.headers.names().none { it.equals("User-Agent", ignoreCase = true) }) {
                builder.header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.5359.128 Mobile Safari/537.36"
                )
            }
            if (original.headers.names().none { it.equals("Cookie", ignoreCase = true) }) {
                val cookie = JsCookieJar.cookieHeader(context, original.url.toString())
                if (cookie.isNotBlank()) builder.header("Cookie", cookie)
            }
            // 不声明 avif，避免部分图源 CDN 返回 BitmapFactory/Coil 解不了的 AVIF
            if (original.headers.names().none { it.equals("Accept", ignoreCase = true) }) {
                builder.header("Accept", "image/webp,image/jpeg,image/png,*/*;q=0.8")
            }
            var response: okhttp3.Response? = null
            var lastError: Exception? = null
            for (attempt in 1..3) {
                try {
                    response = chain.proceed(builder.build())
                    break
                } catch (e: Exception) {
                    lastError = e
                    Thread.sleep(1000L * attempt)
                }
            }
            if (response == null) throw (lastError ?: Exception("图片请求失败"))
            val finalResponse = response!!
            val raw = finalResponse.body?.bytes() ?: return@addInterceptor finalResponse
            val host = response.request.url.host
            var processed = ImageBytes.normalizeImage(raw, finalResponse.header("Content-Encoding"))
            // 平台解不了 AVIF 时，自动尝试 hitomi 类 CDN 的 webp 变体
            if (ImageBytes.isAvif(processed) && !ImageBytes.decodeOk(processed)) {
                for (candidate in ImageBytes.webpVariants(response.request.url.toString())) {
                    try {
                        val r2 = chain.proceed(response.request.newBuilder().url(candidate).build())
                        val b2 = r2.body?.bytes() ?: continue
                        val p2 = ImageBytes.normalizeImage(b2, r2.header("Content-Encoding"))
                        if (!ImageBytes.isAvif(p2) && ImageBytes.decodeOk(p2)) {
                            processed = p2
                            break
                        }
                    } catch (e: Exception) {
                        // 尝试下一个候选
                    }
                }
            }
            processed = if (MhttuImageDecryptor.isEncryptedHost(host)) {
                MhttuImageDecryptor.decryptIfNeeded(processed)
            } else {
                processed
            }
            processed = JsImageProcessor.transform(response.request.url.toString(), processed) ?: processed
            finalResponse.newBuilder()
                .body(processed.toResponseBody(finalResponse.body?.contentType()))
                .build()
        }
        .build()
    // 内存缓存压到 10%：结果位图由阅读引擎 (ComicPageLoader) 另行缓存，避免双份占用
    return ImageLoader.Builder(context)
        .okHttpClient(client)
        .crossfade(true)
        // EXIF 方向归一化（第六轮第 4 条现象三）：远程 JPEG 带 90°/270° 标签时
        // 不处理会横显——与本地解码（ComicPageLoader.decodeLocal）行为对齐
        .bitmapFactoryExifOrientationPolicy(coil.decode.ExifOrientationPolicy.RESPECT_ALL)
        .memoryCache { coil.memory.MemoryCache.Builder(context).maxSizePercent(0.10).build() }
        .build()
}
