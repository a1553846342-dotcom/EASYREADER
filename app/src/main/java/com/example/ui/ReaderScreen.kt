package com.example.ui



import android.app.Activity

import android.net.Uri

import android.view.WindowManager

import android.widget.Toast

import androidx.activity.compose.rememberLauncherForActivityResult

import androidx.activity.result.contract.ActivityResultContracts

import androidx.core.view.WindowCompat

import androidx.core.view.WindowInsetsCompat

import androidx.core.view.WindowInsetsControllerCompat

import androidx.compose.animation.*

import androidx.compose.animation.core.Animatable

import androidx.compose.animation.core.Spring

import androidx.compose.animation.core.spring

import androidx.compose.animation.core.tween

import androidx.compose.animation.core.EaseOut

import androidx.compose.animation.core.rememberInfiniteTransition

import androidx.compose.animation.core.animateFloat

import androidx.compose.animation.core.infiniteRepeatable

import androidx.compose.animation.core.RepeatMode

import androidx.compose.animation.core.LinearEasing

import androidx.compose.foundation.Canvas

import androidx.compose.foundation.Image

import androidx.compose.ui.res.painterResource

import coil.compose.AsyncImage

import com.example.R

import androidx.compose.ui.layout.ContentScale

import androidx.compose.ui.draw.shadow

import androidx.compose.foundation.background

import androidx.compose.foundation.border

import androidx.compose.foundation.clickable

import androidx.compose.foundation.gestures.awaitEachGesture

import androidx.compose.foundation.gestures.awaitFirstDown

import androidx.compose.foundation.gestures.detectTapGestures

import androidx.compose.foundation.gestures.detectTransformGestures

import androidx.compose.foundation.interaction.MutableInteractionSource

import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.ui.draw.alpha

import androidx.compose.ui.draw.clip

import androidx.compose.foundation.lazy.itemsIndexed

import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.text.selection.SelectionContainer

import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.layout.safeDrawingPadding

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.filled.*

import androidx.compose.material.icons.filled.*

import androidx.compose.material3.*
import com.ramotion.fluidslider.FluidSlider

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clipToBounds

import androidx.compose.ui.draw.drawBehind

import androidx.compose.ui.geometry.Offset

import androidx.compose.ui.graphics.Brush

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.graphics.Path

import androidx.compose.ui.graphics.drawscope.Stroke

import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.ui.input.pointer.positionChange

import androidx.compose.ui.input.nestedscroll.NestedScrollConnection

import androidx.compose.ui.input.nestedscroll.NestedScrollSource

import androidx.compose.ui.input.nestedscroll.nestedScroll

import androidx.compose.ui.layout.onSizeChanged

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.platform.LocalDensity

import androidx.compose.ui.platform.LocalFontFamilyResolver

import androidx.compose.ui.text.PlatformTextStyle

import androidx.compose.ui.text.Paragraph

import androidx.compose.ui.text.TextStyle

import androidx.compose.ui.text.font.FontFamily

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.Constraints

import androidx.compose.ui.unit.IntSize

import androidx.compose.ui.unit.TextUnit

import androidx.compose.ui.unit.TextUnitType

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp

import com.example.data.*

import com.example.ui.pageturn.PageCurlReaderContainer

import com.example.ui.pageturn.PageTurnContainer

import com.example.ui.pageturn.PageTurnType


import com.example.ui.mascot.MascotAnimationController

import com.example.ui.mascot.MascotEvent
import com.example.ui.mascot.MascotSpriteSheet

import com.swapnil.squishyswitch.presentation.SquishyToggleSwitch

import com.example.ui.theme.MintPrimary

import com.example.ui.components.AppIconButton

import com.example.ui.components.AppActionButton

import com.example.ui.components.AppButtonSize

import com.example.ui.components.AppButtonVariant

import com.example.ui.theme.clickableWithFeedback

import com.example.ui.theme.MintGold

import com.example.ui.theme.MintPrimary

import com.example.ui.theme.MintSecondary

import com.example.ui.theme.onColor

import kotlinx.coroutines.delay

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.launch

import kotlinx.coroutines.withContext

import java.text.SimpleDateFormat

import java.util.*

import kotlin.math.abs



@OptIn(ExperimentalMaterial3Api::class)

@Composable

@androidx.compose.animation.ExperimentalSharedTransitionApi

fun ReaderScreen(

    book: Book?,

    bookTitle: String,

    chapters: List<Chapter>,

    onBack: () -> Unit,

    onUpdateProgress: (Int, Int, Int, Boolean) -> Unit,

    prefs: PreferencesManager,

    ttsManager: TtsManager,

    highlights: List<Highlight>,

    bookmarks: List<Bookmark>,

    onAddBookmark: (Int, Int, Int, String, String) -> Unit,

    onDeleteBookmark: (Int) -> Unit,

    onAddHighlight: (Int, Int, String, String, String) -> Unit,

    onDeleteHighlight: (Int) -> Unit,

    searchResults: List<SearchResultItem>,

    isSearching: Boolean,

    onSearch: (String) -> Unit,

    onRecordTime: (Long) -> Unit,

    onSessionEnd: (ReadingSession) -> Unit = {}

) {

    val context = LocalContext.current

    val sharedTransitionScope = com.example.LocalSharedTransitionScope.current

    val animatedVisibilityScope = com.example.LocalNavAnimatedVisibilityScope.current

    val scope = rememberCoroutineScope()

    val transitionSlidePx = with(LocalDensity.current) { 20.dp.toPx() }



    var showTocSheet by remember { mutableStateOf(false) }

    var showSettingsSheet by remember { mutableStateOf(false) }

    var showSearchDialog by remember { mutableStateOf(false) }

    var showAnnotationsSheet by remember { mutableStateOf(false) }

    var showEasterEgg by remember { mutableStateOf(false) }

    var showTtsBar by remember { mutableStateOf(false) }

    var showRestDialog by remember { mutableStateOf(false) }



    // 自动滚屏（解放双手模式）

    var isAutoScrolling by remember { mutableStateOf(false) }

    var autoScrollSpeed by remember { mutableFloatStateOf(1f) } // 0.5=慢 / 1=中 / 2=快



    var currentChapterIndex by remember(book) { mutableIntStateOf(book?.currentChapterIndex ?: 0) }

    var showBars by remember { mutableStateOf(false) }



    var previousPosition by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    var showReturnChip by remember { mutableStateOf(false) }



    var fontSize by remember { mutableFloatStateOf(prefs.fontSize) }

    var lineHeight by remember { mutableFloatStateOf(prefs.lineHeight) }

    var marginHorizontal by remember { mutableIntStateOf(prefs.marginHorizontal) }

    var firstLineIndent by remember { mutableStateOf(prefs.firstLineIndent) }

    var readerTheme by remember { mutableIntStateOf(prefs.readerTheme) }

    var fontFamilyIndex by remember { mutableIntStateOf(prefs.fontFamilyIndex) }

    var pageTurnMode by remember { mutableIntStateOf(prefs.pageTurnMode) }

    var readerBrightness by remember { mutableFloatStateOf(prefs.readerBrightness) }

    val isScrollMode = pageTurnMode == PageTurnType.SCROLL.id

    var currentSubPageIndex by remember { mutableIntStateOf(0) }

    var currentCharOffset by remember { mutableIntStateOf(0) }



    val isTtsPlaying by ttsManager.isPlaying.collectAsState()

    var searchKeyword by remember { mutableStateOf("") }



    var customPosterUri by remember { mutableStateOf(prefs.customSplashPosterUri) }

    // 自定义字体文件选择器
    val fontFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        val input = context.contentResolver.openInputStream(it)
                        val file = java.io.File(context.filesDir, "custom_font.ttf")
                        input?.use { inp -> file.outputStream().use { out -> inp.copyTo(out) } }
                        prefs.customFontPath = file.absolutePath
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "自定义字体已导入", Toast.LENGTH_SHORT).show()
                        }
                    } catch (_: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "字体导入失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    val readerPosterLauncher = rememberLauncherForActivityResult(

        contract = ActivityResultContracts.GetContent()

    ) { uri: Uri? ->

        uri?.let {

            try {

                val inputStream = context.contentResolver.openInputStream(it)

                val file = java.io.File(context.filesDir, "custom_poster.jpg")

                inputStream?.use { input ->

                    file.outputStream().use { output ->

                        input.copyTo(output)

                    }

                }

                val localUriStr = Uri.fromFile(file).toString()

                customPosterUri = localUriStr

                prefs.customSplashPosterUri = localUriStr

                Toast.makeText(context, "开屏海报已更新", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {

                Toast.makeText(context, "图片设置失败", Toast.LENGTH_SHORT).show()

            }

        }

    }



    val currentChapter = chapters.getOrNull(currentChapterIndex)

    val scrollState = rememberScrollState()



    // ── 自动滚屏引擎（仅滚动模式生效，逐帧平滑推进）──

    LaunchedEffect(isAutoScrolling, autoScrollSpeed, isScrollMode) {

        if (!isAutoScrolling || !isScrollMode) return@LaunchedEffect

        while (isAutoScrolling && scrollState.value < scrollState.maxValue) {

            scrollState.dispatchRawDelta(1.2f * autoScrollSpeed)

            delay(16) // ~60fps

        }

        // 滚到底自动关闭

        if (scrollState.value >= scrollState.maxValue) {

            isAutoScrolling = false

        }

    }



    // 滚动阅读：章末/章首继续拖拽切章的阈值与状态

    val scrollThresholdPx = with(LocalDensity.current) { 120.dp.toPx() }

    var overscrollPx by remember { mutableFloatStateOf(0f) }

    var overscrollDirection by remember { mutableIntStateOf(0) } // 1=下一章 -1=上一章 0=无

    // 越界拉动时正文的实时位移（跟手反馈），松手未达阈值时弹簧归零

    val overscrollOffset = remember { Animatable(0f) }

    // 切章动画进行中：期间不再累积越界，避免连跳两章

    var switchingChapter by remember { mutableStateOf(false) }

    // 新章节入场动画（淡入 + 从对应方向滑入），记录上次切章方向决定滑动方向

    var lastChapterSwitchDir by remember { mutableIntStateOf(1) }

    val chapterEntryAlpha = remember { Animatable(1f) }

    val chapterEntryOffsetY = remember { Animatable(0f) }

    var skipFirstChapterEntry by remember { mutableStateOf(true) }

    val chapterEntryPullPx = with(LocalDensity.current) { 42.dp.toPx() }

    // 翻页<->滚动模式切换时的位置锚点

    var pendingScrollRatio by remember { mutableFloatStateOf(-1f) }

    var pendingCharTarget by remember { mutableIntStateOf(-1) }



    // 切章触发（只在“松手且拉满阈值”时调用一次）：

    // 拉穿动画 → 切换章节 → 新章节入场动画

    val triggerSwitch: (Int) -> Unit = { dir ->

        if (!switchingChapter) {

            overscrollPx = 0f

            overscrollDirection = 0

            switchingChapter = true

            lastChapterSwitchDir = dir

            scope.launch {

                // 拉穿动画：正文先跟着手指方向冲一段（有翻页的“拉动感”）

                val pullSign = if (dir == 1) -1f else 1f

                overscrollOffset.animateTo(pullSign * 130f, tween(110))

                when (dir) {

                    1 -> if (currentChapterIndex < chapters.size - 1) {

                        currentChapterIndex++

                        scrollState.scrollTo(0)

                    }

                    -1 -> if (currentChapterIndex > 0) {

                        currentChapterIndex--

                        scrollState.scrollTo(0)

                    }

                }

                switchingChapter = false

            }

        }

    }



    // 松手/方向归零后，正文位移用弹簧平滑归零（拉穿切章时由 switchingChapter 延迟到切章后）

    LaunchedEffect(overscrollDirection, switchingChapter) {

        if (overscrollDirection == 0 && !switchingChapter) {

            overscrollOffset.animateTo(

                0f,

                spring(

                    dampingRatio = Spring.DampingRatioMediumBouncy,

                    stiffness = Spring.StiffnessMediumLow

                )

            )

        }

    }



    // 新章节入场：淡入 + 按切章方向滑入（复用全软件统一弹簧）

    LaunchedEffect(currentChapterIndex, isScrollMode) {

        if (!isScrollMode) return@LaunchedEffect

        if (skipFirstChapterEntry) {

            skipFirstChapterEntry = false

            return@LaunchedEffect

        }

        chapterEntryAlpha.snapTo(0f)

        chapterEntryOffsetY.snapTo(if (lastChapterSwitchDir == 1) chapterEntryPullPx else -chapterEntryPullPx)

        launch {

            chapterEntryAlpha.animateTo(1f, tween(220))

        }

        launch {

            chapterEntryOffsetY.animateTo(

                0f,

                spring(

                    dampingRatio = Spring.DampingRatioMediumBouncy,

                    stiffness = Spring.StiffnessMediumLow

                )

            )

        }

    }



    DisposableEffect(prefs.keepScreenOn) {

        val activity = context as? Activity

        if (prefs.keepScreenOn) {

            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        } else {

            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        }

        onDispose {

            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        }

    }



    // 沉浸式阅读：整个阅读过程隐藏系统状态栏（松手/滑动都不会再“弹出状态栏”），

    // 阅读菜单自带 safeDrawing 顶部留白，不需要系统状态栏；退出阅读器时恢复。

    DisposableEffect(Unit) {

        onDispose {

            val w = (context as? Activity)?.window ?: return@onDispose

            WindowCompat.getInsetsController(w, w.decorView)

                .show(WindowInsetsCompat.Type.statusBars())

        }

    }



    // 阅读计时：只在 App 前台 + 屏幕亮着时累计（修复后台/锁屏虚增时长 bug），

    // 每 30 秒上报，离开时写一条阅读会话供日历时段/高峰时段统计。

    ReadingTimerEffect(

        bookId = book?.id,

        bookTitle = currentChapter?.title ?: bookTitle,

        onFlush = { seconds -> onRecordTime(seconds) },

        onSessionEnd = { session -> onSessionEnd(session) },

        onRestTick = { elapsedSec ->

            val restMins = prefs.restReminderMinutes

            if (restMins > 0 && elapsedSec > 0 && elapsedSec % (restMins * 60) == 0L) {

                showRestDialog = true

            }

        }

    )



    LaunchedEffect(book, chapters) {

        if (book != null && chapters.isNotEmpty() && book.scrollOffset > 0) {

            if (currentChapterIndex == book.currentChapterIndex) {

                if (isScrollMode) {

                    scrollState.scrollTo(book.scrollOffset)

                } else {

                    currentCharOffset = book.scrollOffset

                }

            }

        }

    }



    // 翻页/模式切换：立即保存进度

    LaunchedEffect(currentChapterIndex, isScrollMode) {

        if (book != null && chapters.isNotEmpty()) {

            val offsetToSave = if (isScrollMode) scrollState.value else currentCharOffset

            val isFinished = currentChapterIndex == chapters.size - 1 && (if (isScrollMode) scrollState.value > 100 else true)

            onUpdateProgress(book.id, currentChapterIndex, offsetToSave, isFinished)

        }

    }



    // 滚动模式：1.5 秒防抖保存，避免滚动时每帧写数据库导致掉帧

    LaunchedEffect(scrollState.value) {

        if (isScrollMode && book != null && chapters.isNotEmpty()) {

            delay(1500)

            val offsetToSave = scrollState.value

            val isFinished = currentChapterIndex == chapters.size - 1 && offsetToSave > 100

            onUpdateProgress(book.id, currentChapterIndex, offsetToSave, isFinished)

        }

    }



    // 翻页模式：字符偏移变化时保存（离散翻页，非连续滚动）

    LaunchedEffect(currentCharOffset) {

        if (!isScrollMode && book != null && chapters.isNotEmpty()) {

            val offsetToSave = currentCharOffset

            val isFinished = currentChapterIndex == chapters.size - 1

            onUpdateProgress(book.id, currentChapterIndex, offsetToSave, isFinished)

        }

    }



    // 退出阅读页时兜底保存一次最新进度

    DisposableEffect(Unit) {

        onDispose {

            if (book != null && chapters.isNotEmpty()) {

                val offsetToSave = if (isScrollMode) scrollState.value else currentCharOffset

                val isFinished = currentChapterIndex == chapters.size - 1 && (if (isScrollMode) scrollState.value > 100 else true)

                onUpdateProgress(book.id, currentChapterIndex, offsetToSave, isFinished)

            }

        }

    }



    // 书签切换（滚动模式停用下拉手势后，菜单按钮作为唯一入口）

    val toggleBookmark: () -> Unit = {

        val currentBookId = book?.id ?: 0

        val existingBookmark = bookmarks.find {

            (it.bookId == currentBookId || it.bookId == 0) && it.chapterIndex == currentChapterIndex

        }

        if (existingBookmark != null) {

            onDeleteBookmark(existingBookmark.id)

            Toast.makeText(context, "已取消书签", Toast.LENGTH_SHORT).show()

        } else {

            currentChapter?.let { ch ->

                onAddBookmark(currentBookId, currentChapterIndex, scrollState.value, ch.title, ch.content.take(60))

            }

        }

    }



    // 切换阅读模式：翻页 <-> 滚动时按比例映射当前阅读位置，切完后由 LaunchedEffect 锚定

    val switchPageMode: (Int) -> Unit = { newModeId ->

        val oldScroll = pageTurnMode == PageTurnType.SCROLL.id

        val newScroll = newModeId == PageTurnType.SCROLL.id

        if (oldScroll != newScroll) {

            val contentLen = currentChapter?.content?.length ?: 0

            if (newScroll) {

                val ratio = if (contentLen > 0) currentCharOffset.toFloat() / contentLen else 0f

                pendingScrollRatio = ratio

            } else {

                val ratio = if (scrollState.maxValue > 0) {

                    scrollState.value.toFloat() / scrollState.maxValue

                } else {

                    0f

                }

                pendingCharTarget = (contentLen * ratio).toInt()

            }

        }

        pageTurnMode = newModeId

        prefs.pageTurnMode = newModeId

        // 切换翻页模式时关闭自动滚屏（仅滚动模式支持）
        isAutoScrolling = false

    }



    val (bgColor, textColor) = when (readerTheme) {

        0 -> Color.White to Color(0xFF18191C) // Pure Light

        1 -> Color.White to Color(0xFF18191C) // Default White

        2 -> Color(0xFFFBF0D9) to Color(0xFF5F4B32) // Sepia

        3 -> Color(0xFF18191C) to Color(0xFFD4D4D4) // Dark

        4 -> Color(0xFFE8F5E9) to Color(0xFF1B5E20) // Eye Green

        5 -> Color.Black to Color(0xFFE0E0E0) // OLED Black

        else -> Color.White to Color(0xFF18191C)

    }



    // 自定义字体加载（TTF 文件）
    var customTypeface by remember { mutableStateOf<android.graphics.Typeface?>(null) }
    LaunchedEffect(prefs.customFontPath) {
        val path = prefs.customFontPath
        if (path.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                try { customTypeface = android.graphics.Typeface.createFromFile(path) } catch (_: Exception) {}
            }
        }
    }

    val selectedFontFamily = when (fontFamilyIndex) {

        1 -> FontFamily.Serif

        2 -> FontFamily.SansSerif

        3 -> FontFamily.Monospace

        4 -> customTypeface?.let { FontFamily(it) } ?: FontFamily.Default

        else -> FontFamily.Default

    }



    // 小章节同步准备（秒开无闪烁）；超大章节后台准备，避免主线程被几 MB 文本卡住

    val smallChapter = (currentChapter?.content?.length ?: 0) <= LARGE_CHAPTER_THRESHOLD

    var formattedContent by remember(currentChapter, firstLineIndent) {

        mutableStateOf(

            if (smallChapter) formatForReader(currentChapter?.content ?: "", firstLineIndent) else ""

        )

    }

    var scrollChunks by remember(currentChapter, firstLineIndent) {

        val formatted = if (smallChapter) formatForReader(currentChapter?.content ?: "", firstLineIndent) else ""

        mutableStateOf(if (smallChapter) chunkForScroll(formatted) else emptyList())

    }

    var contentReady by remember(currentChapter, firstLineIndent) { mutableStateOf(smallChapter) }

    LaunchedEffect(currentChapter, firstLineIndent) {

        if (smallChapter) return@LaunchedEffect

        contentReady = false

        val (formatted, chunks) = withContext(Dispatchers.Default) {

            val text = currentChapter?.content ?: ""

            val formatted = formatForReader(text, firstLineIndent)

            formatted to chunkForScroll(formatted)

        }

        formattedContent = formatted

        scrollChunks = chunks

        contentReady = true

    }



    // 翻页 <-> 滚动模式切换后，把当前阅读位置按比例映射到新模式（同一章内锚定）

    LaunchedEffect(isScrollMode, contentReady, scrollState.maxValue) {

        if (isScrollMode && contentReady && scrollState.maxValue > 0 && pendingScrollRatio >= 0f) {

            val ratio = pendingScrollRatio.coerceIn(0f, 1f)

            pendingScrollRatio = -1f

            val target = (scrollState.maxValue * ratio).toInt().coerceIn(0, scrollState.maxValue)

            scrollState.scrollTo(target)

        }

    }

    LaunchedEffect(isScrollMode, contentReady) {

        if (!isScrollMode && contentReady && pendingCharTarget >= 0) {

            currentCharOffset = pendingCharTarget

            pendingCharTarget = -1

        }

    }



    // 共享封面转场：封面先随共享元素缩放进场，随后正文从右侧轻轻滑入叠在封面上，

    // 封面再缓慢淡成水印，避免“加载完正文瞬间盖掉封面”的生硬感。

    var transitionStarted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {

        kotlinx.coroutines.delay(140)

        transitionStarted = true

    }

    val contentAlpha by androidx.compose.animation.core.animateFloatAsState(

        targetValue = if (transitionStarted) 1f else 0f,

        animationSpec = tween(420, easing = androidx.compose.animation.core.FastOutSlowInEasing),

        label = "readerContentAlpha"

    )

    val coverAlpha by androidx.compose.animation.core.animateFloatAsState(

        targetValue = if (transitionStarted) 0.06f else 1f,

        animationSpec = tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing),

        label = "readerCoverAlpha"

    )

    val bgAlpha by androidx.compose.animation.core.animateFloatAsState(

        targetValue = if (transitionStarted) 1f else 0f,

        animationSpec = tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing),

        label = "readerBgAlpha"

    )



    Box(

        modifier = Modifier

            .fillMaxSize()

            .background(bgColor.copy(alpha = bgAlpha))

    ) {

        if (book != null && sharedTransitionScope != null && animatedVisibilityScope != null) {

            with(sharedTransitionScope) {

                val imageRequest = if (!book.coverUri.isNullOrEmpty() && book.isCoverValid) {

                    if (book.coverUri!!.startsWith("content://")) android.net.Uri.parse(book.coverUri)

                    else java.io.File(book.coverUri!!)

                } else null

                if (imageRequest != null) {

                    coil.compose.AsyncImage(

                        model = imageRequest,

                        contentDescription = "Shared Cover",

                        modifier = Modifier

                            .fillMaxSize()

                            .sharedElement(

                                state = rememberSharedContentState(key = "book_cover_${book.id}"),

                                animatedVisibilityScope = animatedVisibilityScope,

                                boundsTransform = { _, _ ->

                                    tween(420, easing = androidx.compose.animation.core.FastOutSlowInEasing)

                                }

                            )

                            .clip(

                                RoundedCornerShape(

                                    topStart = 4.dp,

                                    bottomStart = 4.dp,

                                    topEnd = 12.dp,

                                    bottomEnd = 12.dp

                                )

                            )

                            .alpha(coverAlpha),

                        contentScale = androidx.compose.ui.layout.ContentScale.Crop

                    )

                }

            }

        }

        BoxWithConstraints(

            modifier = Modifier

                .fillMaxSize()

                .graphicsLayer {

                    alpha = contentAlpha

                    translationX = (1f - contentAlpha) * transitionSlidePx

                }

        ) {

            var pageContainerSize by remember { mutableStateOf<IntSize?>(null) }

            val density = LocalDensity.current

            val navBarsBottomPx = WindowInsets.navigationBars.getBottom(density)

            val statusBarsTopPx = WindowInsets.statusBars.getTop(density)



            val fallbackWidthPx = with(density) { (maxWidth - marginHorizontal.dp * 2).toPx().toInt() }.coerceAtLeast(100)

            val headerFooterPaddingPx = with(density) {

                var pad = 24.dp.toPx().toInt()

                if (prefs.showOverlayHeaderFooter) {

                    pad += 48.dp.toPx().toInt()

                }

                pad

            }

            val fallbackHeightPx = with(density) {

                (maxHeight.toPx().toInt() - statusBarsTopPx - navBarsBottomPx - headerFooterPaddingPx).coerceAtLeast(100)

            }



            val containerWidthPx = pageContainerSize?.let {

                (it.width - with(density) { (marginHorizontal.dp * 2).toPx().toInt() }).coerceAtLeast(100)

            } ?: fallbackWidthPx



            val containerHeightPx = pageContainerSize?.let {

                (it.height - with(density) { (PAGE_VERTICAL_PADDING_DP * 2).dp.toPx().toInt() }).coerceAtLeast(100)

            } ?: fallbackHeightPx



            val bodyTextStyle = MaterialTheme.typography.bodyLarge.copy(

                fontSize = fontSize.sp,

                lineHeight = lineHeight.sp,

                fontFamily = selectedFontFamily,

                color = textColor.copy(alpha = 0.92f),

                platformStyle = PlatformTextStyle(includeFontPadding = false)

            )



            val titleStyle = MaterialTheme.typography.headlineSmall.copy(

                fontWeight = FontWeight.Bold,

                color = textColor,

                platformStyle = PlatformTextStyle(includeFontPadding = false)

            )

            val textWidthPx = containerWidthPx

            val textHeightPx = containerHeightPx - with(density) { PAGE_TEXT_BOTTOM_PADDING_DP.dp.toPx().toInt() }.coerceAtLeast(16)

            val fontFamilyResolver = LocalFontFamilyResolver.current

            val currentTitleReservePx = remember(currentChapter?.title, textWidthPx, titleStyle, density, fontFamilyResolver) {

                measureTitleReservePx(

                    title = currentChapter?.title,

                    widthPx = textWidthPx,

                    maxHeightPx = textHeightPx,

                    titleStyle = titleStyle,

                    density = density,

                    fontFamilyResolver = fontFamilyResolver

                )

            }



            val pagesList = rememberChapterPages(

                content = formattedContent,

                widthPx = textWidthPx,

                heightPx = textHeightPx,

                bodyStyle = bodyTextStyle,

                titleReservePx = currentTitleReservePx,

                isScrollMode = isScrollMode

            )



            // 读完庆祝：最后一章最后一页时触发吉祥物庆祝动画（每次打开书只触发一次）

            var celebratedBookComplete by remember(book) { mutableStateOf(false) }

            LaunchedEffect(currentChapterIndex, currentSubPageIndex, pagesList.size, isScrollMode) {

                if (celebratedBookComplete || isScrollMode || pagesList.isEmpty()) return@LaunchedEffect

                val atLastPageOfLastChapter = currentChapterIndex >= chapters.size - 1 &&

                        currentSubPageIndex >= pagesList.size - 1

                if (atLastPageOfLastChapter && !celebratedBookComplete) {

                    celebratedBookComplete = true

                    MascotAnimationController.play(MascotEvent.BookComplete)

                }

            }



            val nextChapter = chapters.getOrNull(currentChapterIndex + 1)

            val nextTitleReservePx = remember(nextChapter?.title, textWidthPx, titleStyle, density, fontFamilyResolver) {

                measureTitleReservePx(

                    title = nextChapter?.title,

                    widthPx = textWidthPx,

                    maxHeightPx = textHeightPx,

                    titleStyle = titleStyle,

                    density = density,

                    fontFamilyResolver = fontFamilyResolver

                )

            }

            val nextChapterFormattedContent = remember(nextChapter, firstLineIndent) {

                val text = nextChapter?.content ?: ""

                if (firstLineIndent) {

                    text.split("\n").joinToString("\n") { line ->

                        if (line.isNotBlank() && !line.startsWith("\u3000\u3000")) "\u3000\u3000$line" else line

                    }

                } else {

                    text

                }

            }

            val nextChapterPages = rememberChapterPages(

                content = nextChapterFormattedContent,

                widthPx = textWidthPx,

                heightPx = textHeightPx,

                bodyStyle = bodyTextStyle,

                titleReservePx = nextTitleReservePx,

                isScrollMode = isScrollMode

            )



            val prevChapter = chapters.getOrNull(currentChapterIndex - 1)

            val prevTitleReservePx = remember(prevChapter?.title, textWidthPx, titleStyle, density, fontFamilyResolver) {

                measureTitleReservePx(

                    title = prevChapter?.title,

                    widthPx = textWidthPx,

                    maxHeightPx = textHeightPx,

                    titleStyle = titleStyle,

                    density = density,

                    fontFamilyResolver = fontFamilyResolver

                )

            }

            val prevChapterFormattedContent = remember(prevChapter, firstLineIndent) {

                val text = prevChapter?.content ?: ""

                if (firstLineIndent) {

                    text.split("\n").joinToString("\n") { line ->

                        if (line.isNotBlank() && !line.startsWith("\u3000\u3000")) "\u3000\u3000$line" else line

                    }

                } else {

                    text

                }

            }

            val prevChapterPages = rememberChapterPages(

                content = prevChapterFormattedContent,

                widthPx = textWidthPx,

                heightPx = textHeightPx,

                bodyStyle = bodyTextStyle,

                titleReservePx = prevTitleReservePx,

                isScrollMode = isScrollMode

            )



            val activeSubPageIndex = currentSubPageIndex.coerceIn(0, (pagesList.size - 1).coerceAtLeast(0))



            // Re-anchor subpage index based on currentCharOffset when pagesList recalculates (e.g. screen rotation / layout change)

            LaunchedEffect(pagesList) {

                if (!isScrollMode && pagesList.isNotEmpty()) {

                    var accumulated = 0

                    var targetPageIndex = 0

                    for (index in pagesList.indices) {

                        val pageLen = pagesList[index].length

                        if (accumulated + pageLen > currentCharOffset || index == pagesList.lastIndex) {

                            targetPageIndex = index

                            break

                        }

                        accumulated += pageLen

                    }

                    currentSubPageIndex = targetPageIndex

                }

            }



            val updateSubPage = { newIndex: Int ->

                val clamped = newIndex.coerceIn(0, (pagesList.size - 1).coerceAtLeast(0))

                currentSubPageIndex = clamped

                var offset = 0

                for (i in 0 until clamped) {

                    offset += pagesList.getOrNull(i)?.length ?: 0

                }

                currentCharOffset = offset

            }



            val handleNextPage = {

                if (isScrollMode) {

                    if (currentChapterIndex < chapters.size - 1) {

                        currentChapterIndex++

                        scope.launch { scrollState.scrollTo(0) }

                    }

                } else {

                    if (activeSubPageIndex < pagesList.size - 1) {

                        updateSubPage(activeSubPageIndex + 1)

                    } else if (currentChapterIndex < chapters.size - 1) {

                        currentChapterIndex++

                        currentCharOffset = 0

                        currentSubPageIndex = 0

                        scope.launch { scrollState.scrollTo(0) }

                    }

                }

            }



            val handlePrevPage = {

                if (isScrollMode) {

                    if (currentChapterIndex > 0) {

                        currentChapterIndex--

                        scope.launch { scrollState.scrollTo(0) }

                    }

                } else {

                    if (activeSubPageIndex > 0) {

                        updateSubPage(activeSubPageIndex - 1)

                    } else if (currentChapterIndex > 0) {

                        currentChapterIndex--

                        currentCharOffset = Int.MAX_VALUE

                        currentSubPageIndex = 9999

                        scope.launch { scrollState.scrollTo(0) }

                    }

                }

            }



            if (chapters.isEmpty() || !contentReady || (!isScrollMode && pagesList.isEmpty())) {

                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

                    CircularProgressIndicator(color = MintPrimary)

                }

            } else if (currentChapter?.title == UNSUPPORTED_CHAPTER_TITLE &&

                currentChapter?.content?.isBlank() == true

            ) {

                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

                    Text(

                        text = "该文件格式暂不支持阅读（PDF / MOBI / DOCX 等）\n请导入 EPUB、TXT 或漫画格式",

                        color = textColor.copy(alpha = 0.75f),

                        fontSize = 14.sp,

                        modifier = Modifier.padding(32.dp)

                    )

                }

            } else {

                currentChapter?.let { chapter ->

                    Box(

                        modifier = Modifier.fillMaxSize()

                    ) {

                            Column(

                                modifier = Modifier

                                    .fillMaxSize()

                                    // 状态栏隐藏后仍保留刘海/导航栏留白，菜单与正文都不贴边

                                    .safeDrawingPadding()

                            ) {

                            if (prefs.showOverlayHeaderFooter && !showBars) {

                                Row(

                                    modifier = Modifier

                                        .fillMaxWidth()

                                        .padding(horizontal = marginHorizontal.dp, vertical = 6.dp),

                                    horizontalArrangement = Arrangement.SpaceBetween

                                ) {

                                    Text(chapter.title, fontSize = 10.sp, color = textColor.copy(alpha = 0.5f), maxLines = 1)

                                    val timeStr = remember { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) }

                                    Text(timeStr, fontSize = 10.sp, color = textColor.copy(alpha = 0.5f))

                                }

                            }



                            Box(

                                modifier = Modifier

                                    .weight(1f)

                                    .fillMaxWidth()

                                    .onSizeChanged { size ->

                                        if (size.width > 0 && size.height > 0) {

                                            pageContainerSize = size

                                        }

                                    }

                            ) {

                                // C1 pagecurl 引擎插槽化：三个页面槽提取为局部 Composable，

                                // SIMULATE 档走 pagecurl 卷页引擎，其余档位保持原容器

                                val currentSlot: @Composable () -> Unit = {

                                        if (isScrollMode) {

                                            Column(

                                                modifier = Modifier

                                                    .fillMaxSize()

                                                    .graphicsLayer {

                                                        // 越界拉动跟手位移 + 新章节入场动画叠加

                                                        translationY = overscrollOffset.value + chapterEntryOffsetY.value

                                                        alpha = chapterEntryAlpha.value

                                                    }

                                                    // 滚动模式统一手势（与 PageTurnContainer 拉下书签手势同款 awaitEachGesture 写法）：

                                                    //  - 章节中部拖拽一律不消费，完全交给 verticalScroll，互不干扰；

                                                    //  - 仅在章首/章尾继续越界拖动时接管（消费增量），累积到阈值切章；

                                                    //  - 纯点击（无位移）唤起/关闭菜单。

                                                    // 注意：本手势必须放在 verticalScroll 之前（外层），

                                                    // 才能先于滚动组件接管边界拖拽，避免两套手势互相打架。

                                                    .pointerInput(scrollState, currentChapterIndex, chapters.size) {

                                                        awaitEachGesture {

                                                            val down = awaitFirstDown(requireUnconsumed = false)

                                                            var boundaryPulling = false

                                                            while (true) {

                                                                val event = awaitPointerEvent()

                                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                                                if (!change.pressed) break

                                                                val deltaY = change.positionChange().y

                                                                val atBottom = scrollState.value >= scrollState.maxValue

                                                                val atTop = scrollState.value <= 0

                                                                val pullNext = atBottom && deltaY < 0 &&

                                                                    currentChapterIndex < chapters.size - 1 && !switchingChapter

                                                                val pullPrev = atTop && deltaY > 0 &&

                                                                    currentChapterIndex > 0 && !switchingChapter

                                                                if (pullNext || pullPrev) {

                                                                    boundaryPulling = true

                                                                    change.consume()

                                                                    overscrollPx += abs(deltaY)

                                                                    overscrollDirection = if (pullNext) 1 else -1

                                                                    scope.launch {

                                                                        overscrollOffset.snapTo(

                                                                            if (pullNext) -overscrollPx * 0.35f

                                                                            else overscrollPx * 0.35f

                                                                        )

                                                                    }

                                                                    // 只累积，不在这里切章——等松手再判定（避免“没松手就翻页”）

                                                                } else if (boundaryPulling) {

                                                                    // 手指反向离开边界：取消越界，交还滚动

                                                                    boundaryPulling = false

                                                                    overscrollPx = 0f

                                                                    overscrollDirection = 0

                                                                }

                                                            }

                                                            // 手势结束（松手）：边界拉满阈值 → 切章（拉穿动画 + 新章入场）。

                                                            // 点击唤菜单交给独立的 detectTapGestures，滑动绝不会被误判成点击。

                                                            if (boundaryPulling &&

                                                                overscrollPx >= scrollThresholdPx &&

                                                                !switchingChapter

                                                            ) {

                                                                triggerSwitch(overscrollDirection)

                                                            }

                                                            overscrollPx = 0f

                                                            overscrollDirection = 0

                                                        }

                                                    }

                                                    // 标准点击判定（自带 touchSlop 过滤）：只有真正的点按才唤起/关闭菜单

                                                    .pointerInput(Unit) {

                                                        detectTapGestures(onTap = { showBars = !showBars })

                                                    }

                                                    .verticalScroll(scrollState)

                                                    .padding(horizontal = marginHorizontal.dp, vertical = 16.dp)

                                            ) {

                                                if (currentChapterIndex > 0 && !showBars) {

                                                    Text(

                                                        text = "↓ 已到本章开头 · 继续下拉返回上一章",

                                                        fontSize = 12.sp,

                                                        color = textColor.copy(alpha = 0.45f),

                                                        modifier = Modifier

                                                            .fillMaxWidth()

                                                            .padding(bottom = 12.dp)

                                                    )

                                                }

                                                Text(

                                                    text = chapter.title,

                                                    style = MaterialTheme.typography.headlineSmall,

                                                    fontWeight = FontWeight.Bold,

                                                    color = textColor,

                                                    modifier = Modifier.padding(bottom = 20.dp, top = if (!showBars) 12.dp else 0.dp)

                                                )



                                                SelectionContainer {

                                                    Column {

                                                        // 大章节按段切块渲染，避免单个巨型 Text 排版导致 ANR；

                                                        // 仍为同一滚动容器，像素滚动位置与原文选择行为保持不变。

                                                        scrollChunks.forEach { chunk ->

                                                            Text(

                                                                text = chunk,

                                                                style = MaterialTheme.typography.bodyLarge.copy(

                                                                    fontSize = fontSize.sp,

                                                                    lineHeight = lineHeight.sp,

                                                                    fontFamily = selectedFontFamily,

                                                                    color = textColor.copy(alpha = 0.92f),

                                                                    platformStyle = PlatformTextStyle(includeFontPadding = false)

                                                                )

                                                            )

                                                        }

                                                    }

                                                }



                                                Spacer(modifier = Modifier.height(20.dp))

                                                Text(

                                                    text = if (currentChapterIndex < chapters.size - 1) {

                                                        "—— 本章完 · 继续上滑进入下一章 ——"

                                                    } else {

                                                        "—— 全书完 ——"

                                                    },

                                                    fontSize = 13.sp,

                                                    color = textColor.copy(alpha = 0.5f),

                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,

                                                    modifier = Modifier.fillMaxWidth()

                                                )

                                            }

                                        } else {

                                            RenderSinglePage(

                                                pageIndex = activeSubPageIndex,

                                                pageText = pagesList.getOrNull(activeSubPageIndex) ?: "",

                                                chapterTitle = currentChapter?.title,

                                                bgColor = bgColor,

                                                textColor = textColor,

                                                bodyStyle = bodyTextStyle,

                                                titleStyle = titleStyle,

                                                titleReservePx = currentTitleReservePx,

                                                marginHorizontal = marginHorizontal,

                                                showBars = showBars

                                            )

                                        }

                                    }



                                    val nextSlot: @Composable () -> Unit = {

                                        if (!isScrollMode) {

                                            if (activeSubPageIndex < pagesList.size - 1) {

                                                RenderSinglePage(

                                                    pageIndex = activeSubPageIndex + 1,

                                                    pageText = pagesList.getOrNull(activeSubPageIndex + 1) ?: "",

                                                    chapterTitle = currentChapter?.title,

                                                    bgColor = bgColor,

                                                    textColor = textColor,

                                                    bodyStyle = bodyTextStyle,

                                                    titleStyle = titleStyle,

                                                    titleReservePx = currentTitleReservePx,

                                                    marginHorizontal = marginHorizontal,

                                                    showBars = showBars

                                                )

                                            } else if (nextChapter != null) {

                                                RenderSinglePage(

                                                    pageIndex = 0,

                                                    pageText = nextChapterPages.firstOrNull() ?: "",

                                                    chapterTitle = nextChapter.title,

                                                    bgColor = bgColor,

                                                    textColor = textColor,

                                                    bodyStyle = bodyTextStyle,

                                                    titleStyle = titleStyle,

                                                    titleReservePx = nextTitleReservePx,

                                                    marginHorizontal = marginHorizontal,

                                                    showBars = showBars

                                                )

                                            } else {

                                                RenderSinglePage(

                                                    pageIndex = 0,

                                                    pageText = "已经是最后一页了",

                                                    chapterTitle = null,

                                                    bgColor = bgColor,

                                                    textColor = textColor,

                                                    bodyStyle = bodyTextStyle,

                                                    titleStyle = titleStyle,

                                                    titleReservePx = currentTitleReservePx,

                                                    marginHorizontal = marginHorizontal,

                                                    showBars = showBars

                                                )

                                            }

                                        }

                                    }



                                    val prevSlot: @Composable () -> Unit = {

                                        if (!isScrollMode) {

                                            if (activeSubPageIndex > 0) {

                                                RenderSinglePage(

                                                    pageIndex = activeSubPageIndex - 1,

                                                    pageText = pagesList.getOrNull(activeSubPageIndex - 1) ?: "",

                                                    chapterTitle = currentChapter?.title,

                                                    bgColor = bgColor,

                                                    textColor = textColor,

                                                    bodyStyle = bodyTextStyle,

                                                    titleStyle = titleStyle,

                                                    titleReservePx = currentTitleReservePx,

                                                    marginHorizontal = marginHorizontal,

                                                    showBars = showBars

                                                )

                                            } else if (prevChapter != null) {

                                                val lastIdx = (prevChapterPages.size - 1).coerceAtLeast(0)

                                                RenderSinglePage(

                                                    pageIndex = lastIdx,

                                                    pageText = prevChapterPages.getOrNull(lastIdx) ?: "",

                                                    chapterTitle = prevChapter.title,

                                                    bgColor = bgColor,

                                                    textColor = textColor,

                                                    bodyStyle = bodyTextStyle,

                                                    titleStyle = titleStyle,

                                                    titleReservePx = prevTitleReservePx,

                                                    marginHorizontal = marginHorizontal,

                                                    showBars = showBars

                                                )

                                            } else {

                                                RenderSinglePage(

                                                    pageIndex = 0,

                                                    pageText = "已经是第一页了",

                                                    chapterTitle = null,

                                                    bgColor = bgColor,

                                                    textColor = textColor,

                                                    bodyStyle = bodyTextStyle,

                                                    titleStyle = titleStyle,

                                                    titleReservePx = currentTitleReservePx,

                                                    marginHorizontal = marginHorizontal,

                                                    showBars = showBars

                                                )

                                            }

                                        }

                                    }



                                    if (!isScrollMode && pageTurnMode == PageTurnType.SIMULATE.id) {

                                        // C1 pagecurl 引擎（SIMULATE 专用）：三页窗口 + 中央点击唤出菜单

                                        PageCurlReaderContainer(

                                            currentContent = currentSlot,

                                            nextContent = nextSlot,

                                            prevContent = prevSlot,

                                            onNextPage = handleNextPage,

                                            onPrevPage = handlePrevPage,

                                            onClickCenter = { showBars = !showBars },

                                            onToggleBookmark = { toggleBookmark() },

                                            isCurrentBookmarked = bookmarks.any {
                                                (it.bookId == (book?.id ?: 0) || it.bookId == 0) &&
                                                        it.chapterIndex == currentChapterIndex
                                            },

                                            menuVisible = showBars

                                        )

                                    } else {

                                        PageTurnContainer(

                                            pageTurnMode = pageTurnMode,

                                            pageKey = "$currentChapterIndex-$activeSubPageIndex",

                                            menuVisible = showBars,

                                            currentContent = currentSlot,

                                            nextContent = nextSlot,

                                            prevContent = prevSlot,

                                            onNextPage = handleNextPage,

                                            onPrevPage = handlePrevPage,

                                            onClickCenter = { showBars = !showBars },

                                            onClickLeft = handlePrevPage,

                                            onClickRight = handleNextPage,

                                            isBookmarked = bookmarks.any { (it.bookId == (book?.id ?: 0) || it.bookId == 0) && it.chapterIndex == currentChapterIndex },

                                            onToggleBookmark = toggleBookmark

                                        )

                                    }



                                // 滚动模式：章首/章尾继续拖拽时的切章提示

                                // （方向修正：章尾手指上滑→下一章；章首手指下拉→上一章）

                                androidx.compose.animation.AnimatedVisibility(

                                    visible = isScrollMode && overscrollDirection != 0 && !switchingChapter,

                                    enter = fadeIn(tween(120)) + slideInVertically(

                                        initialOffsetY = { if (overscrollDirection == 1) it / 2 else -it / 2 }

                                    ),

                                    exit = fadeOut(tween(100)),

                                    modifier = Modifier

                                        .align(if (overscrollDirection == 1) Alignment.BottomCenter else Alignment.TopCenter)

                                ) {

                                    val isNext = overscrollDirection == 1

                                    val pullProgress = (overscrollPx / scrollThresholdPx).coerceIn(0f, 1f)

                                    Surface(

                                        shape = RoundedCornerShape(16.dp),

                                        color = MintPrimary.copy(alpha = 0.92f),

                                        shadowElevation = 6.dp,

                                        modifier = Modifier.padding(16.dp)

                                    ) {

                                        Column(

                                            horizontalAlignment = Alignment.CenterHorizontally,

                                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)

                                        ) {

                                            Row(verticalAlignment = Alignment.CenterVertically) {

                                                Icon(

                                                    imageVector = if (isNext) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,

                                                    contentDescription = null,

                                                    tint = Color.White,

                                                    modifier = Modifier.size(16.dp)

                                                )

                                                Spacer(modifier = Modifier.width(6.dp))

                                                Text(

                                                    text = when {

                                                        pullProgress >= 1f && isNext -> "松开切换下一章"

                                                        pullProgress >= 1f -> "松开返回上一章"

                                                        isNext -> "继续上滑进入下一章"

                                                        else -> "继续下拉返回上一章"

                                                    },

                                                    color = Color.White,

                                                    fontSize = 13.sp,

                                                    fontWeight = FontWeight.Bold

                                                )

                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            // 拉取进度条：跟手填充，拉满 100% 后松手即切章

                                            Box(

                                                modifier = Modifier

                                                    .width(120.dp)

                                                    .height(3.dp)

                                                    .clip(RoundedCornerShape(2.dp))

                                                    .background(Color.White.copy(alpha = 0.3f))

                                            ) {

                                                Box(

                                                    modifier = Modifier

                                                        .fillMaxHeight()

                                                        .fillMaxWidth(pullProgress)

                                                        .background(Color.White.copy(alpha = 0.9f))

                                                )

                                            }

                                        }

                                    }

                                }



                                val isCurrentBookmarked = bookmarks.any { (it.bookId == (book?.id ?: 0) || it.bookId == 0) && it.chapterIndex == currentChapterIndex }

                                androidx.compose.animation.AnimatedVisibility(

                                    visible = isCurrentBookmarked,

                                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),

                                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),

                                    modifier = Modifier

                                        .align(Alignment.TopEnd)

                                        .padding(end = 20.dp)

                                ) {

                                    BookmarkHangingRibbon()

                                }

                            }



                            if (prefs.showOverlayHeaderFooter && !showBars) {

                                val pct = if (chapters.isNotEmpty()) ((currentChapterIndex + 1).toFloat() / chapters.size * 100).toInt() else 0

                                Row(

                                    modifier = Modifier

                                        .fillMaxWidth()

                                        .padding(horizontal = marginHorizontal.dp, vertical = 6.dp),

                                    horizontalArrangement = Arrangement.SpaceBetween

                                ) {

                                    val pageProgressText = if (isScrollMode) {

                                        "第 ${currentChapterIndex + 1}/${chapters.size} 章"

                                    } else {

                                        "第 ${activeSubPageIndex + 1}/${pagesList.size} 页 · 第 ${currentChapterIndex + 1}/${chapters.size} 章"

                                    }

                                    Text(pageProgressText, fontSize = 10.sp, color = textColor.copy(alpha = 0.5f))

                                    Text("$pct%", fontSize = 10.sp, color = textColor.copy(alpha = 0.5f))

                                }

                            }

                        }



                        if (showTtsBar) {

                            Card(

                                modifier = Modifier

                                    .align(Alignment.BottomCenter)

                                    .padding(16.dp)

                                    .fillMaxWidth(),

                                shape = RoundedCornerShape(20.dp),

                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),

                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)

                            ) {

                                Column(modifier = Modifier.padding(16.dp)) {

                                    Row(

                                        modifier = Modifier.fillMaxWidth(),

                                        horizontalArrangement = Arrangement.SpaceBetween,

                                        verticalAlignment = Alignment.CenterVertically

                                    ) {

                                        Text("朗读播放器", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                                        AppIconButton(onClick = { showTtsBar = false }) {

                                            Icon(Icons.Filled.Close, contentDescription = "关闭")

                                        }

                                    }



                                    Row(

                                        modifier = Modifier.fillMaxWidth(),

                                        horizontalArrangement = Arrangement.SpaceEvenly,

                                        verticalAlignment = Alignment.CenterVertically

                                    ) {

                                        AppIconButton(onClick = { ttsManager.previousParagraph() }) {

                                            Icon(Icons.Filled.SkipPrevious, contentDescription = "上一段")

                                        }



                                        Box(

                                            modifier = Modifier

                                                .size(56.dp)

                                                .background(MintPrimary, CircleShape)

                                                .clickableWithFeedback {

                                                    if (isTtsPlaying) {

                                                        ttsManager.pause()

                                                    } else {

                                                        currentChapter?.let { ch ->

                                                            ttsManager.startReading(ch.content, speed = prefs.ttsSpeed, pitch = prefs.ttsPitch)

                                                        }

                                                    }

                                                },

                                            contentAlignment = Alignment.Center

                                        ) {

                                            Icon(

                                                if (isTtsPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,

                                                contentDescription = "播放暂停",

                                                tint = Color.White

                                            )

                                        }



                                        AppIconButton(onClick = { ttsManager.nextParagraph() }) {

                                            Icon(Icons.Filled.SkipNext, contentDescription = "下一段")

                                        }

                                    }

                                }

                            }

                        }



                        if (showReturnChip && previousPosition != null) {

                            Surface(

                                modifier = Modifier

                                    .align(Alignment.TopCenter)

                                    .padding(top = 80.dp)

                                    .clickableWithFeedback {

                                        previousPosition?.let { (ch, offset) ->

                                            currentChapterIndex = ch

                                            scope.launch { scrollState.scrollTo(offset) }

                                        }

                                        showReturnChip = false

                                    },

                                shape = RoundedCornerShape(20.dp),

                                color = MintPrimary,

                                shadowElevation = 6.dp

                            ) {

                                Row(

                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),

                                    verticalAlignment = Alignment.CenterVertically

                                ) {

                                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, tint = Color.White)

                                    Spacer(modifier = Modifier.width(6.dp))

                                    Text("返回上次处", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                                }

                            }

                        }

                    }

                }

            }



        // 自动滚屏运行指示器（点击停止）

        androidx.compose.animation.AnimatedVisibility(

            visible = isAutoScrolling,

            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 2 },

            exit = fadeOut(tween(150)),

            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)

        ) {

            Surface(

                onClick = { isAutoScrolling = false },

                shape = RoundedCornerShape(20.dp),

                color = MintPrimary.copy(alpha = 0.9f),

                shadowElevation = 4.dp

            ) {

                Row(

                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),

                    verticalAlignment = Alignment.CenterVertically

                ) {

                    Icon(Icons.Filled.UnfoldMore, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))

                    Spacer(modifier = Modifier.width(6.dp))

                    Text("自动滚屏中 · 点击停止", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)

                }

            }

        }



        // 滚动模式：滚过 20% 时显示"回到顶部"按钮

        androidx.compose.animation.AnimatedVisibility(

            visible = isScrollMode && !isAutoScrolling && scrollState.value > scrollState.maxValue * 0.2f,

            enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.8f),

            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.8f),

            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 80.dp)

        ) {

            Surface(

                onClick = { scope.launch { scrollState.animateScrollTo(0) } },

                shape = CircleShape,

                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),

                border = androidx.compose.foundation.BorderStroke(1.dp, MintPrimary.copy(alpha = 0.5f)),

                shadowElevation = 4.dp,

                modifier = Modifier.size(44.dp)

            ) {

                Box(contentAlignment = Alignment.Center) {

                    Icon(

                        Icons.Filled.KeyboardArrowUp,

                        contentDescription = "回到顶部",

                        tint = MintPrimary,

                        modifier = Modifier.size(22.dp)

                    )

                }

            }

        }



        // 亮度遮罩：夜间阅读降低刺眼感（不拦截触摸，不影响菜单栏）

        if (readerBrightness < 0.99f) {

            Box(

                modifier = Modifier

                    .fillMaxSize()

                    .background(Color.Black.copy(alpha = (1f - readerBrightness) * 0.6f))

            )

        }



        // Reader Overlay (Background Mask)

        AnimatedVisibility(

            visible = showBars,

            enter = fadeIn(tween(300)),

            exit = fadeOut(tween(300)),

            modifier = Modifier.fillMaxSize()

        ) {

            Box(

                modifier = Modifier

                    .fillMaxSize()

                    .background(Color.Black.copy(alpha = 0.6f))

                    // 菜单打开时点击空白处只关闭菜单，绝不触发翻页/切章

                    .clickable(

                        interactionSource = remember { MutableInteractionSource() },

                        indication = null

                    ) {

                        showBars = false

                    }

            )

        }



        // Top and Bottom Bars

        // 栏内文字/图标按栏背景实时取对比色（bgColor.onColor()），

        // 无论选什么阅读主题/自定义背景都不会出现“黑字压黑底”。

        val barContentColor = bgColor.onColor()

        Box(modifier = Modifier.fillMaxSize()) {

            Box(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {

                AnimatedVisibility(

                                    visible = showBars,

                                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),

                                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()

                                ) {

                                    // ── 定制阅读器顶栏：圆角浮层 + 章节进度 + 主题自适应色 ──

                                    Column(

                                        modifier = Modifier

                                            .fillMaxWidth()

                                            .background(bgColor.copy(alpha = 0.97f))

                                            .drawBehind {

                                                drawLine(

                                                    color = barContentColor.copy(alpha = 0.08f),

                                                    start = Offset(0f, size.height - 0.5f),

                                                    end = Offset(size.width, size.height - 0.5f),

                                                    strokeWidth = 0.8f

                                                )

                                            }

                                            .statusBarsPadding()

                                    ) {

                                        Row(

                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),

                                            verticalAlignment = Alignment.CenterVertically

                                        ) {

                                            AppIconButton(onClick = onBack) {

                                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = barContentColor, modifier = Modifier.size(22.dp))

                                            }

                                            Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {

                                                Text(

                                                    currentChapter?.title ?: bookTitle,

                                                    maxLines = 1,

                                                    fontWeight = FontWeight.SemiBold,

                                                    fontSize = 16.sp,

                                                    color = barContentColor,

                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis

                                                )

                                                Spacer(modifier = Modifier.height(1.dp))

                                                Text(

                                                    text = "${currentChapterIndex + 1}/${chapters.size} 章",

                                                    fontSize = 11.sp,

                                                    color = barContentColor.copy(alpha = 0.55f)

                                                )

                                            }

                                            val currentBookmarked = bookmarks.any {

                                                (it.bookId == (book?.id ?: 0) || it.bookId == 0) &&

                                                    it.chapterIndex == currentChapterIndex

                                            }

                                            AppIconButton(onClick = { toggleBookmark() }) {

                                                Icon(

                                                    imageVector = if (currentBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,

                                                    contentDescription = "书签",

                                                    tint = if (currentBookmarked) MintGold else barContentColor,

                                                    modifier = Modifier.size(20.dp)

                                                )

                                            }

                                            AppIconButton(onClick = {

                                                showTtsBar = true

                                                if (isAutoScrolling) isAutoScrolling = false // 与 TTS 互斥

                                                if (!isTtsPlaying) {

                                                    currentChapter?.let { ch ->

                                                        ttsManager.startReading(ch.content, speed = prefs.ttsSpeed, pitch = prefs.ttsPitch)

                                                        Toast.makeText(context, "已开启语音听书", Toast.LENGTH_SHORT).show()

                                                    }

                                                }

                                            }) {

                                                Icon(

                                                    imageVector = if (isTtsPlaying) Icons.AutoMirrored.Filled.VolumeUp else Icons.Filled.Headphones,

                                                    contentDescription = "听书",

                                                    tint = if (isTtsPlaying) MintGold else barContentColor,

                                                    modifier = Modifier.size(20.dp)

                                                )

                                            }

                                            AppIconButton(onClick = { showTocSheet = true }) {
                                                Icon(Icons.Filled.Menu, "目录", tint = barContentColor, modifier = Modifier.size(20.dp))
                                            }

                                            // ── 更多菜单：低频操作收纳，顶栏只留高频 4 键 ──
                                            var showReaderMoreMenu by remember { mutableStateOf(false) }
                                            Box {
                                                AppIconButton(onClick = { showReaderMoreMenu = true }) {
                                                    Icon(Icons.Filled.MoreVert, "更多", tint = barContentColor, modifier = Modifier.size(20.dp))
                                                }
                                                DropdownMenu(
                                                    expanded = showReaderMoreMenu,
                                                    onDismissRequest = { showReaderMoreMenu = false }
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text("全文搜索") },
                                                        leadingIcon = { Icon(Icons.Filled.Search, null, modifier = Modifier.size(18.dp)) },
                                                        onClick = { showReaderMoreMenu = false; showSearchDialog = true }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("书签列表") },
                                                        leadingIcon = { Icon(Icons.Filled.Bookmarks, null, tint = MintPrimary, modifier = Modifier.size(18.dp)) },
                                                        onClick = { showReaderMoreMenu = false; showAnnotationsSheet = true }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(if (isAutoScrolling) "停止自动滚屏" else "自动滚屏") },
                                                        leadingIcon = { Icon(Icons.Filled.UnfoldMore, null, tint = if (isAutoScrolling) MintGold else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) },
                                                        onClick = {
                                                            showReaderMoreMenu = false
                                                            if (!isScrollMode) {
                                                                Toast.makeText(context, "自动滚屏需切换到滚动模式", Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                isAutoScrolling = !isAutoScrolling
                                                                if (isAutoScrolling && isTtsPlaying) ttsManager.pause()
                                                            }
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("排版设置") },
                                                        leadingIcon = { Icon(Icons.Filled.Settings, null, modifier = Modifier.size(18.dp)) },
                                                        onClick = { showReaderMoreMenu = false; showSettingsSheet = true }
                                                    )
                                                }
                                            }

                                        }

                                    }

                                }

            }

            Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {

                AnimatedVisibility(

                                    visible = showBars && chapters.isNotEmpty(),

                                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),

                                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()

                                ) {

                                    // ── 定制阅读器底栏：与顶栏同风格 ──

                                    Column(

                                        modifier = Modifier

                                            .fillMaxWidth()

                                            .background(bgColor.copy(alpha = 0.97f))

                                            .drawBehind {

                                                drawLine(

                                                    color = barContentColor.copy(alpha = 0.08f),

                                                    start = Offset(0f, 0f),

                                                    end = Offset(size.width, 0f),

                                                    strokeWidth = 0.8f

                                                )

                                            }

                                            .navigationBarsPadding()

                                    ) {

                                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {

                                            Row(verticalAlignment = Alignment.CenterVertically) {

                                                TextButton(

                                                    onClick = {

                                                        if (currentChapterIndex > 0) {

                                                            currentChapterIndex--

                                                            scope.launch { scrollState.scrollTo(0) }

                                                        }

                                                    },

                                                    enabled = currentChapterIndex > 0

                                                ) {

                                                    Text("上一章", fontWeight = FontWeight.Bold, color = barContentColor)

                                                }

                

                                                FluidSlider(

                                                    position = currentChapterIndex.toFloat() /

                                                        (chapters.size - 1).coerceAtLeast(1).toFloat(),

                                                    onPositionChange = {

                                                        currentChapterIndex = Math.round(it * (chapters.size - 1).coerceAtLeast(1).toFloat())

                                                        scope.launch { scrollState.scrollTo(0) }

                                                    },

                                                    modifier = Modifier.weight(1f),

                                                    barHeightDp = 26,

                                                    bubbleText = "${currentChapterIndex + 1}",

                                                    startText = null,

                                                    endText = null,

                                                    colorBar = barContentColor

                                                 )

                

                                                TextButton(

                                                    onClick = {

                                                        if (currentChapterIndex < chapters.size - 1) {

                                                            currentChapterIndex++

                                                            scope.launch { scrollState.scrollTo(0) }

                                                        }

                                                    },

                                                    enabled = currentChapterIndex < chapters.size - 1

                                                ) {

                                                    Text("下一章", fontWeight = FontWeight.Bold, color = barContentColor)

                                                }

                                            }

                

                                            Row(

                                                modifier = Modifier.fillMaxWidth(),

                                                horizontalArrangement = Arrangement.SpaceBetween,

                                                verticalAlignment = Alignment.CenterVertically

                                            ) {

                                                Text(

                                                    "第 ${currentChapterIndex + 1} / ${chapters.size} 章",

                                                    fontSize = 12.sp,

                                                    color = barContentColor

                                                )

                

                                                FilledTonalButton(

                                                    onClick = {

                                                        showTtsBar = true

                                                        if (isAutoScrolling) isAutoScrolling = false // 与 TTS 互斥

                                                        if (!isTtsPlaying) {

                                                            currentChapter?.let { ch ->

                                                                ttsManager.startReading(ch.content, speed = prefs.ttsSpeed, pitch = prefs.ttsPitch)

                                                                Toast.makeText(context, "开启语音听书：${ch.title}", Toast.LENGTH_SHORT).show()

                                                            }

                                                        }

                                                    },

                                                    colors = ButtonDefaults.filledTonalButtonColors(

                                                        containerColor = if (isTtsPlaying) MintGold.copy(alpha = 0.25f) else barContentColor.copy(alpha = 0.10f),

                                                        contentColor = if (isTtsPlaying) MintGold else barContentColor

                                                    ),

                                                    shape = RoundedCornerShape(20.dp),

                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)

                                                ) {

                                                    Icon(

                                                        imageVector = if (isTtsPlaying) Icons.AutoMirrored.Filled.VolumeUp else Icons.Filled.Headphones,

                                                        contentDescription = "听书模式",

                                                        modifier = Modifier.size(18.dp)

                                                    )

                                                    Spacer(modifier = Modifier.width(6.dp))

                                                    Text(if (isTtsPlaying) "朗读中..." else "听书模式", fontSize = 13.sp, fontWeight = FontWeight.Bold)

                                                }

                                            }

                                        }

                                    }

                                }

            }

        }



        }

    }



    if (showTocSheet) {

        var tocFilter by remember { mutableStateOf("") }

        val filteredChapters = remember(chapters, tocFilter) {

            if (tocFilter.isBlank()) chapters else chapters.filter { it.title.contains(tocFilter, ignoreCase = true) }

        }

        // 打开目录时自动定位到当前章节

        val tocListState = rememberLazyListState()

        LaunchedEffect(showTocSheet, tocFilter) {

            if (tocFilter.isBlank() && filteredChapters.isNotEmpty()) {

                val targetIdx = filteredChapters.indexOfFirst { it.chapterOrder == currentChapterIndex }

                if (targetIdx > 0) tocListState.scrollToItem(index = targetIdx)

            }

        }



        ModalBottomSheet(

            onDismissRequest = { showTocSheet = false },

            containerColor = MaterialTheme.colorScheme.surface

        ) {

            Column(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 32.dp)) {

                Text("目录 (${chapters.size}章)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(12.dp))



                OutlinedTextField(

                    value = tocFilter,

                    onValueChange = { tocFilter = it },

                    placeholder = { Text("搜索章节...") },

                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },

                    singleLine = true,

                    modifier = Modifier.fillMaxWidth()

                )



                Spacer(modifier = Modifier.height(12.dp))



                LazyColumn(state = tocListState) {

                    itemsIndexed(filteredChapters) { _, chapter ->

                        val index = chapter.chapterOrder

                        TextButton(

                            onClick = {

                                previousPosition = currentChapterIndex to scrollState.value

                                showReturnChip = true

                                currentChapterIndex = index

                                showTocSheet = false

                                scope.launch { scrollState.scrollTo(0) }

                            },

                            modifier = Modifier.fillMaxWidth()

                        ) {

                            Text(

                                text = chapter.title,

                                color = if (index == currentChapterIndex) MintPrimary else MaterialTheme.colorScheme.onSurface,

                                fontWeight = if (index == currentChapterIndex) FontWeight.Bold else FontWeight.Normal,

                                maxLines = 1,

                                modifier = Modifier.fillMaxWidth(),

                                textAlign = androidx.compose.ui.text.style.TextAlign.Start

                            )

                        }

                    }

                }

            }

        }

    }



    if (showSettingsSheet) {

        ModalBottomSheet(

            onDismissRequest = { showSettingsSheet = false },

            containerColor = MaterialTheme.colorScheme.surface

        ) {

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState())) {

                /* ── 头部 ── */
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Tune,
                        contentDescription = null,
                        tint = MintPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("阅读排版", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }

                /* ── 分组：文字 ── */
                Text(
                    "文 字",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("字号", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.weight(1f))
                    Text("${fontSize.toInt()} sp", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MintPrimary)
                }
                FluidSlider(
                    position = (fontSize - 12f) / 24f,
                    onPositionChange = { fontSize = 12f + it * 24f; prefs.fontSize = fontSize },
                    modifier = Modifier.fillMaxWidth(),
                    barHeightDp = 30,
                    bubbleText = "${fontSize.toInt()}",
                    startText = null,
                    endText = null,
                    colorBar = MintPrimary
                )
                Spacer(modifier = Modifier.height(18.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("行间距", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.weight(1f))
                    Text("${lineHeight.toInt()} sp", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MintPrimary)
                }
                FluidSlider(
                    position = (lineHeight - 20f) / 28f,
                    onPositionChange = { lineHeight = 20f + it * 28f; prefs.lineHeight = lineHeight },
                    modifier = Modifier.fillMaxWidth(),
                    barHeightDp = 30,
                    bubbleText = "${lineHeight.toInt()}",
                    startText = null,
                    endText = null,
                    colorBar = MintPrimary
                )
                Spacer(modifier = Modifier.height(18.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("页边距", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.weight(1f))
                    Text("${marginHorizontal} dp", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MintPrimary)
                }
                FluidSlider(
                    position = (marginHorizontal - 8f) / 40f,
                    onPositionChange = { marginHorizontal = Math.round(8f + it * 40f); prefs.marginHorizontal = marginHorizontal },
                    modifier = Modifier.fillMaxWidth(),
                    barHeightDp = 30,
                    bubbleText = "$marginHorizontal",
                    startText = null,
                    endText = null,
                    colorBar = MintPrimary
                )

                Spacer(modifier = Modifier.height(24.dp))

                /* ── 分组：显示 ── */
                Text(
                    "显 示",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("亮度", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.weight(1f))
                    Text("${(readerBrightness * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MintGold)
                }
                FluidSlider(
                    position = (readerBrightness - 0.2f) / 0.8f,
                    onPositionChange = { readerBrightness = 0.2f + it * 0.8f; prefs.readerBrightness = readerBrightness },
                    modifier = Modifier.fillMaxWidth(),
                    barHeightDp = 30,
                    bubbleText = "${(readerBrightness * 100).toInt()}%",
                    startText = null,
                    endText = null,
                    colorBar = MintGold
                )
                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("首行缩进", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    SquishyToggleSwitch(
                        color = MintPrimary,
                        checked = firstLineIndent,
                        onCheckedChange = { firstLineIndent = it; prefs.firstLineIndent = it }
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))

                Text("字体", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val fonts = listOf(0 to "默认", 1 to "衬线", 2 to "黑体", 3 to "等宽")
                    fonts.forEach { (idx, name) ->
                        FilterChip(
                            selected = fontFamilyIndex == idx,
                            onClick = { fontFamilyIndex = idx; prefs.fontFamilyIndex = idx },
                            label = { Text(name, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    FilterChip(
                        selected = fontFamilyIndex == 4,
                        onClick = { fontFileLauncher.launch("font/ttf") },
                        label = { Text(if (prefs.customFontPath.isNotEmpty()) "✓自定义" else "+导入", fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                /* ── 分组：主题 ── */
                Text(
                    "主 题",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val themes = listOf(0 to "薄荷", 1 to "白底", 2 to "羊皮", 3 to "夜间", 4 to "护眼", 5 to "纯黑")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        themes.take(3).forEach { (id, name) ->
                            FilterChip(
                                selected = readerTheme == id,
                                onClick = { readerTheme = id; prefs.readerTheme = id },
                                label = { Text(name, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        themes.drop(3).forEach { (id, name) ->
                            FilterChip(
                                selected = readerTheme == id,
                                onClick = { readerTheme = id; prefs.readerTheme = id },
                                label = { Text(name, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                /* ── 分组：翻页 ── */
                Text(
                    "翻 页",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PageTurnType.entries.take(3).forEach { modeType ->
                            FilterChip(
                                selected = pageTurnMode == modeType.id,
                                onClick = { switchPageMode(modeType.id) },
                                label = { Text(modeType.title.replace("翻页", "").replace("卷页", "").replace("渐变", ""), fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PageTurnType.entries.drop(3).forEach { modeType ->
                            FilterChip(
                                selected = pageTurnMode == modeType.id,
                                onClick = { switchPageMode(modeType.id) },
                                label = { Text(modeType.title.replace("翻页", "").replace("卷页", "").replace("渐变", ""), fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

        }

    }



    if (showSearchDialog) {

        AlertDialog(

            onDismissRequest = { showSearchDialog = false },

            title = { Text("全文搜索") },

            text = {

                Column {

                    OutlinedTextField(

                        value = searchKeyword,

                        onValueChange = {

                            searchKeyword = it

                            onSearch(it)

                        },

                        placeholder = { Text("输入关键词...") },

                        singleLine = true,

                        modifier = Modifier.fillMaxWidth()

                    )



                    Spacer(modifier = Modifier.height(12.dp))



                    if (isSearching) {

                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))

                    } else if (searchResults.isEmpty() && searchKeyword.isNotBlank()) {

                        Text("未找到匹配", color = MaterialTheme.colorScheme.onSurfaceVariant)

                    } else {

                        LazyColumn(modifier = Modifier.height(240.dp)) {

                            itemsIndexed(searchResults) { _, item ->

                                Card(

                                    modifier = Modifier

                                        .fillMaxWidth()

                                        .padding(vertical = 4.dp)

                                        .clickableWithFeedback {

                                            previousPosition = currentChapterIndex to scrollState.value

                                            showReturnChip = true

                                            currentChapterIndex = item.chapterIndex

                                            showSearchDialog = false

                                            scope.launch { scrollState.scrollTo(0) }

                                        },

                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)

                                ) {

                                    Column(modifier = Modifier.padding(8.dp)) {

                                        Text(item.chapterTitle, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MintPrimary)

                                        Text(item.snippet, fontSize = 11.sp, maxLines = 2)

                                    }

                                }

                            }

                        }

                    }

                }

            },

            confirmButton = {

                TextButton(onClick = { showSearchDialog = false }) {

                    Text("关闭")

                }

            }

        )

    }



    if (showAnnotationsSheet) {

        ModalBottomSheet(

            onDismissRequest = { showAnnotationsSheet = false },

            containerColor = MaterialTheme.colorScheme.surface

        ) {

            Column(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 32.dp)) {

                Text("书签记录 (${bookmarks.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(12.dp))



                if (bookmarks.isEmpty()) {

                    Text("无书签", color = MaterialTheme.colorScheme.onSurfaceVariant)

                } else {

                    LazyColumn(modifier = Modifier.height(280.dp)) {

                        itemsIndexed(bookmarks) { _, bm ->

                            Row(

                                modifier = Modifier

                                    .fillMaxWidth()

                                    .clickableWithFeedback {

                                        previousPosition = currentChapterIndex to scrollState.value

                                        showReturnChip = true

                                        currentChapterIndex = bm.chapterIndex

                                        showAnnotationsSheet = false

                                        scope.launch { scrollState.scrollTo(bm.scrollOffset) }

                                    }

                                    .padding(vertical = 8.dp),

                                horizontalArrangement = Arrangement.SpaceBetween,

                                verticalAlignment = Alignment.CenterVertically

                            ) {

                                Column(modifier = Modifier.weight(1f)) {

                                    Text(bm.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                                    Text(bm.snippet, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)

                                }

                                AppIconButton(onClick = { onDeleteBookmark(bm.id) }) {

                                    Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)

                                }

                            }

                        }

                    }

                }

            }

        }

    }



    AnimatedVisibility(

        visible = showRestDialog,

        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),

        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),

        modifier = Modifier

            .fillMaxWidth()

            .padding(16.dp)

            .statusBarsPadding()

    ) {

        val infiniteTransition = rememberInfiniteTransition(label = "glow")

        val glowAlpha by infiniteTransition.animateFloat(

            initialValue = 0.4f,

            targetValue = 0.9f,

            animationSpec = infiniteRepeatable(

                animation = tween(1500, easing = LinearEasing),

                repeatMode = RepeatMode.Reverse

            ),

            label = "glowAlpha"

        )

        Surface(

            modifier = Modifier

                .fillMaxWidth()

                .shadow(12.dp, RoundedCornerShape(16.dp))

                .border(2.dp, MintPrimary.copy(alpha = glowAlpha), RoundedCornerShape(16.dp)),

            shape = RoundedCornerShape(16.dp),

            color = MaterialTheme.colorScheme.surface

        ) {

            Row(

                modifier = Modifier.padding(16.dp),

                verticalAlignment = Alignment.CenterVertically,

                horizontalArrangement = Arrangement.SpaceBetween

            ) {

                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {

                    Icon(

                        imageVector = Icons.Filled.Timer,

                        contentDescription = null,

                        tint = MintPrimary,

                        modifier = Modifier.size(28.dp)

                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {

                        Text(

                            text = "定时休息提醒",

                            fontWeight = FontWeight.Bold,

                            fontSize = 15.sp,

                            color = MaterialTheme.colorScheme.onSurface

                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(

                            text = "已持续阅读 ${prefs.restReminderMinutes} 分钟，建议远眺片刻！",

                            fontSize = 13.sp,

                            color = MaterialTheme.colorScheme.onSurfaceVariant

                        )

                    }

                }

                Spacer(modifier = Modifier.width(8.dp))

                AppActionButton(

                    text = "好的",

                    onClick = { showRestDialog = false },

                    variant = AppButtonVariant.Secondary,

                    buttonSize = AppButtonSize.Small

                )

            }

        }

    }



    // Cute Anime Easter Egg UI Overlay

    AnimatedVisibility(

        visible = showEasterEgg,

        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(animationSpec = tween(400, easing = EaseOut)),

        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(animationSpec = tween(400, easing = EaseOut)),

        modifier = Modifier.fillMaxSize()

    ) {

        Box(modifier = Modifier.fillMaxSize()) {

            Image(

                painter = painterResource(id = MascotSpriteSheet.celebrateDrawable),

                contentDescription = "Anime Mascot Easter Egg",

                modifier = Modifier

                    .align(Alignment.BottomEnd)

                    .padding(end = 24.dp, bottom = 48.dp)

                    .size(160.dp),

                contentScale = ContentScale.Inside

            )

            // Speech bubble

            Surface(

                shape = RoundedCornerShape(16.dp),

                color = MaterialTheme.colorScheme.primaryContainer,

                modifier = Modifier

                    .align(Alignment.BottomEnd)

                    .padding(end = 120.dp, bottom = 160.dp)

                    .shadow(8.dp, RoundedCornerShape(16.dp))

            ) {

                Text(

                    text = "Ciallo～(∠・ω< )⌒★",

                    modifier = Modifier.padding(16.dp, 8.dp),

                    color = MaterialTheme.colorScheme.onPrimaryContainer,

                    fontWeight = FontWeight.Bold,

                    fontSize = 14.sp

                )

            }

        }

    }

}



@Composable

private fun BookmarkHangingRibbon(

    modifier: Modifier = Modifier

) {

    Canvas(

        modifier = modifier

            .width(20.dp)

            .height(34.dp)

            .graphicsLayer {

                shadowElevation = 6f

            }

    ) {

        val w = size.width

        val h = size.height



        val ribbonPath = Path().apply {

            moveTo(0f, 0f)

            lineTo(w, 0f)

            lineTo(w, h)

            lineTo(w / 2f, h - 8.dp.toPx()) // Triangular V-notch

            lineTo(0f, h)

            close()

        }



        drawPath(

            path = ribbonPath,

            brush = Brush.verticalGradient(

                colors = listOf(

                    MintGold,

                    Color(0xFFFFB300),

                    Color(0xFFE65100)

                )

            )

        )



        drawPath(

            path = ribbonPath,

            color = Color.White.copy(alpha = 0.6f),

            style = Stroke(width = 1.dp.toPx())

        )

    }

}





/** 首行缩进格式化（与旧逻辑一致）。 */

private fun formatForReader(text: String, firstLineIndent: Boolean): String {

    return if (firstLineIndent) {

        text.split("\n").joinToString("\n") { line ->

            if (line.isNotBlank() && !line.startsWith("\u3000\u3000")) "\u3000\u3000$line" else line

        }

    } else {

        text

    }

}



/** 把超大章节切成不超过 maxChars 的文本块（优先在换行处切），保持滚动位置语义不变。 */

private fun chunkForScroll(content: String, maxChars: Int = 40000): List<String> {

    if (content.length <= maxChars) return listOf(content)

    val chunks = mutableListOf<String>()

    var start = 0

    while (start < content.length) {

        val end = minOf(content.length, start + maxChars)

        if (end < content.length) {

            val newline = content.lastIndexOf('\n', end)

            if (newline > start + maxChars / 2) {

                chunks.add(content.substring(start, newline))

                start = newline + 1

                continue

            }

        }

        chunks.add(content.substring(start, end))

        start = end

    }

    return chunks

}



/**

 * Measures how much vertical space the chapter title occupies on the first page so the

 * pagination and RenderSinglePage agree on the same first-page height. Returns 0 when

 * there is no title, and is capped so a very long title can never starve the body text.

 */

private fun measureTitleReservePx(

    title: String?,

    widthPx: Int,

    maxHeightPx: Int,

    titleStyle: TextStyle,

    density: androidx.compose.ui.unit.Density,

    fontFamilyResolver: androidx.compose.ui.text.font.FontFamily.Resolver

): Int {

    if (title.isNullOrEmpty() || widthPx <= 0) return 0

    val fontSizePx = with(density) { titleStyle.fontSize.toPx() }.coerceAtLeast(8f)

    val lineHeightPx = with(density) { titleStyle.lineHeight.toPx() }.coerceAtLeast(fontSizePx * 1.2f)

    val style = titleStyle.copy(

        fontSize = TextUnit(fontSizePx, TextUnitType.Sp),

        lineHeight = TextUnit(lineHeightPx, TextUnitType.Sp)

    )

    val paragraph = Paragraph(

        text = title,

        style = style,

        constraints = Constraints(maxWidth = widthPx),

        density = density,

        fontFamilyResolver = fontFamilyResolver

    )

    val titleHeight = if (paragraph.lineCount == 0) {

        lineHeightPx

    } else {

        paragraph.getLineBottom(paragraph.lineCount - 1) - paragraph.getLineTop(0)

    }.toInt()

    val blockPaddingPx = with(density) { TITLE_BLOCK_PADDING_DP.dp.toPx().toInt() }

    return (titleHeight + blockPaddingPx).coerceIn(0, (maxHeightPx * 0.4f).toInt().coerceAtLeast(60))

}



@Composable

private fun RenderSinglePage(

    pageIndex: Int,

    pageText: String,

    chapterTitle: String?,

    bgColor: Color,

    textColor: Color,

    bodyStyle: TextStyle,

    titleStyle: TextStyle,

    titleReservePx: Int,

    marginHorizontal: Int,

    showBars: Boolean

) {

    Column(

        modifier = Modifier

            .fillMaxSize()

            .background(bgColor)

            .padding(horizontal = marginHorizontal.dp, vertical = PAGE_VERTICAL_PADDING_DP.dp)

    ) {

        if (pageIndex == 0 && !chapterTitle.isNullOrEmpty()) {

            Box(

                modifier = Modifier

                    .fillMaxWidth()

                    .height(with(LocalDensity.current) { titleReservePx.toDp() })

                    .clipToBounds()

            ) {

                Text(

                    text = chapterTitle,

                    style = titleStyle,

                    modifier = Modifier

                        .fillMaxSize()

                        .clipToBounds()

                )

            }

        }



        Box(modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()) {

            Text(

                text = pageText,

                style = bodyStyle,

                modifier = Modifier

                    .fillMaxWidth()

                    .fillMaxHeight()

                    .padding(bottom = PAGE_TEXT_BOTTOM_PADDING_DP.dp)

                    .clipToBounds()

            )

        }

    }

}


