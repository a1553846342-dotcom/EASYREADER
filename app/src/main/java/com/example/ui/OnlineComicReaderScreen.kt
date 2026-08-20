package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.ImageLoader
import coil.request.ImageRequest
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.library.MhttuImageDecryptor
import com.example.library.ImageBytes
import com.example.data.ReadingSession
import com.example.source.js.JsImageProcessor
import com.example.source.js.JsCookieJar
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import com.example.ui.components.ChasingDots
import com.example.ui.components.AppLiquidButton
import com.example.ui.theme.MintPrimary
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.engawapg.lib.zoomable.ExperimentalZoomableApi
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import net.engawapg.lib.zoomable.zoomableWithScroll

private enum class ComicReadingMode2 { HORIZONTAL, VERTICAL }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalZoomableApi::class)
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
    onRetry: () -> Unit
) {
    var readingMode by remember { mutableStateOf(ComicReadingMode2.HORIZONTAL) }
    var isRightToLeft by remember { mutableStateOf(true) }
    var showBars by remember { mutableStateOf(true) }
    var currentPage by remember { mutableIntStateOf(0) }

    // 在线阅读计时：只在 App 前台 + 屏幕亮着时累计（修复后台/锁屏虚增时长 bug）
    ReadingTimerEffect(
        bookId = null,
        bookTitle = title,
        onFlush = { seconds -> onRecordTime(seconds) },
        onSessionEnd = { session -> onSessionEnd(session) }
    )

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentResolveImage by rememberUpdatedState(resolveImage)
    val currentResolveImageHeaders by rememberUpdatedState(resolveImageHeaders)

    // 漫画阅读专用加载器：对 tu.mhttu.cc 加密图床自动 AES 解密
    val imageLoader = remember {
        val client = OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .addInterceptor { chain ->
                var original = chain.request()
                if (currentResolveImage != null) {
                    val pageUrl = original.url.toString()
                    val resolved = runBlocking { currentResolveImage!!(pageUrl) }
                    if (!resolved.isNullOrBlank() && resolved != pageUrl) {
                        if (resolved.startsWith("file:")) {
                            // H@H 图片已由 Cronet 下载缓存到本地，直接作为响应返回，避免 OkHttp 请求 file://
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
                            val rh = runBlocking { currentResolveImageHeaders?.invoke(resolved) }.orEmpty()
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
        ImageLoader.Builder(context).okHttpClient(client).crossfade(true).build()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when {
            loading && imageUrls.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ChasingDots(
                            size = 52.dp,
                            color = MintPrimary
                        )
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
                        AppLiquidButton(
                            text = "重试",
                            onClick = onRetry
                        )
                    }
                }
            }

            imageUrls.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有可显示的图片", color = Color.White)
                }
            }

            else -> {
                val pagerState = rememberPagerState(initialPage = 0) { imageUrls.size }
                val listState = rememberLazyListState()

                if (readingMode == ComicReadingMode2.HORIZONTAL) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        OnlineComicPage(
                            url = imageUrls[page],
                            context = context,
                            referer = referer,
                            headers = imageHeaders[imageUrls[page]].orEmpty(),
                            imageLoader = imageLoader,
                            zoomable = true,
                            onTapLeft = {
                                if (isRightToLeft) {
                                    if (pagerState.currentPage < pagerState.pageCount - 1) {
                                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                    }
                                } else if (pagerState.currentPage > 0) {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                }
                            },
                            onTapRight = {
                                if (isRightToLeft) {
                                    if (pagerState.currentPage > 0) {
                                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                    }
                                } else if (pagerState.currentPage < pagerState.pageCount - 1) {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                }
                            },
                            onTapCenter = { showBars = !showBars }
                        )
                    }
                    LaunchedEffect(pagerState.currentPage) {
                        currentPage = pagerState.currentPage
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .zoomableWithScroll(
                                zoomState = rememberZoomState(),
                                onTap = { showBars = !showBars }
                            )
                    ) {
                        items(imageUrls.size) { page ->
                            OnlineComicPage(
                                url = imageUrls[page],
                                context = context,
                                referer = referer,
                                headers = imageHeaders[imageUrls[page]].orEmpty(),
                                imageLoader = imageLoader,
                                zoomable = false
                            )
                        }
                    }
                    LaunchedEffect(listState.firstVisibleItemIndex) {
                        currentPage = listState.firstVisibleItemIndex
                    }
                }

                // 顶部 / 底部控制栏
                AnimatedVisibility(
                    visible = showBars,
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    TopAppBar(
                        title = {
                            Text(
                                text = title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = Color.White
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                            }
                        },
                        actions = {
                            IconButton(onClick = { readingMode = ComicReadingMode2.HORIZONTAL }) {
                                Icon(
                                    Icons.Filled.ViewWeek,
                                    contentDescription = "横向",
                                    tint = if (readingMode == ComicReadingMode2.HORIZONTAL) MintPrimary else Color.White
                                )
                            }
                            IconButton(onClick = { readingMode = ComicReadingMode2.VERTICAL }) {
                                Icon(
                                    Icons.Filled.ViewDay,
                                    contentDescription = "纵向",
                                    tint = if (readingMode == ComicReadingMode2.VERTICAL) MintPrimary else Color.White
                                )
                            }
                            IconButton(onClick = { isRightToLeft = !isRightToLeft }) {
                                Icon(
                                    Icons.Filled.MenuBook,
                                    contentDescription = "阅读方向",
                                    tint = if (isRightToLeft) MintPrimary else Color.White
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Black.copy(alpha = 0.75f)
                        )
                    )
                }

                AnimatedVisibility(
                    visible = showBars,
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Surface(color = Color.Black.copy(alpha = 0.75f)) {
                        Text(
                            text = "${currentPage + 1} / ${imageUrls.size}",
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineComicPage(
    url: String,
    context: android.content.Context,
    referer: String?,
    headers: Map<String, String>,
    imageLoader: coil.ImageLoader,
    zoomable: Boolean,
    onTapLeft: (() -> Unit)? = null,
    onTapRight: (() -> Unit)? = null,
    onTapCenter: () -> Unit = {}
) {
    var pageWidth by remember { mutableIntStateOf(0) }
    var attempt by remember(url) { mutableIntStateOf(0) }
    var autoRetried by remember(url) { mutableStateOf(false) }
    val zoomState = rememberZoomState()
    val request = remember(url, referer, headers, attempt) {
        val builder = ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
        headers.forEach { (k, v) -> builder.addHeader(k, v) }
        if (!referer.isNullOrBlank()) {
            builder.addHeader("Referer", referer)
        }
        builder.build()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { pageWidth = it.width },
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = request,
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = if (zoomable) {
                Modifier
                    .fillMaxSize()
                    .zoomable(
                        zoomState = zoomState,
                        onTap = { tapOffset ->
                            if (onTapLeft != null || onTapRight != null) {
                                val width = pageWidth.toFloat()
                                when {
                                    tapOffset.x < width * 0.35f -> onTapLeft?.invoke()
                                    tapOffset.x > width * 0.65f -> onTapRight?.invoke()
                                    else -> onTapCenter()
                                }
                            } else {
                                onTapCenter()
                            }
                        }
                    )
            } else {
                Modifier.fillMaxSize()
            }
        ) {
            when (painter.state) {
                is AsyncImagePainter.State.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ChasingDots(size = 30.dp, color = MintPrimary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "加载图片中…",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                is AsyncImagePainter.State.Error -> {
                    LaunchedEffect(Unit) {
                        if (!autoRetried) {
                            autoRetried = true
                            kotlinx.coroutines.delay(1500)
                            attempt++
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "图片加载失败",
                                color = Color(0xFFFF8A8A),
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { attempt++ }) {
                                Text("重试", color = MintPrimary)
                            }
                        }
                    }
                }
                else -> SubcomposeAsyncImageContent()
            }
        }
    }
}
