package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.Book
import com.example.data.CategoryEntity
import com.example.ui.components.AppButton
import com.example.ui.components.AppActionButton
import com.example.ui.components.AppButtonSize
import com.example.ui.components.AppButtonVariant
import com.example.ui.components.AcrylicBottomOverlay
import com.example.ui.components.AppIconButton
import com.example.ui.components.GlassCard
import com.example.ui.components.TabScreenHeader
import com.example.ui.components.rememberHeaderCollapsed
import com.example.ui.components.scrollTiltSource
import com.example.ui.components.AcrylicDialog
import com.example.ui.components.StarryNightBackground
import com.example.ui.components.GlassDialogWindowEffect
import com.example.ui.components.filmGrain
import com.example.ui.components.iridescentBorder
import com.example.ui.components.liquidGlass
import com.example.ui.components.radialGlassScrim
import com.example.ui.components.rememberGlassPanelBackdrop
import com.example.ui.components.rememberIridescentColors
import com.example.ui.theme.MintGold
import com.example.ui.theme.MintPrimary
import com.example.ui.theme.MintSecondary
import com.example.ui.theme.clickableWithFeedback
import com.example.ui.theme.glassTitleColor
import kotlin.math.roundToInt
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
@androidx.compose.animation.ExperimentalSharedTransitionApi
fun HomeScreen(
    books: List<Book>,
    categories: List<CategoryEntity>,
    onBookClick: (Book) -> Unit,
    onImportClick: (String) -> Unit,
    onAddCategory: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onNavigateToShelf: () -> Unit,
    onNavigateToStats: () -> Unit,
    /** 今日已阅读秒数（daily_read_time_<今天>；第七轮第 4 条：卡片显示"今日"口径） */
    todayReadSecondsFlow: kotlinx.coroutines.flow.StateFlow<Long>,
    streakDaysFlow: kotlinx.coroutines.flow.StateFlow<Int>,
    onDeleteBook: (Book) -> Unit,
    onMoveBook: (Book, String) -> Unit,
    onDeleteCategory: ((com.example.data.CategoryEntity, (Boolean) -> Unit) -> Unit)? = null,
    /* ── 隐私模式（第七轮第 6 条）：状态由 MainActivity 从 MainViewModel 注入 ── */
    privacyModeEnabled: Boolean = false,
    protectedCategoryNames: Set<String> = emptySet(),
    unlockedCategoryIds: Set<Int> = emptySet(),
    onUnlockCategory: ((com.example.data.CategoryEntity, String) -> Boolean)? = null,
    onToggleCategoryProtected: ((com.example.data.CategoryEntity, Boolean) -> Unit)? = null,
) {
    val sharedTransitionScope = com.example.LocalSharedTransitionScope.current
    val animatedVisibilityScope = com.example.LocalNavAnimatedVisibilityScope.current
    val streakDays by streakDaysFlow.collectAsState()

    val currentlyReading = remember(books) {
        val result = if (books.isEmpty()) {
            Book(id = -1, title = "Empty", filePath = "")
        } else {
            books.maxByOrNull { it.lastReadTime } ?: books.first()
        }
        result
    }

    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }

    // 搜索防抖 300ms：避免大书库逐键触发全量过滤
    LaunchedEffect(searchQuery) {
        kotlinx.coroutines.delay(300)
        debouncedQuery = searchQuery
    }
    var selectedCategory by remember { mutableStateOf(com.example.data.DEFAULT_CATEGORY) }
    // 第七轮第 6.1 条：不再有聚合视图"全部"——所选分类必须是真实存在的分类行；
    // 分类列表异步到达/删除后失效时回退到"默认"
    LaunchedEffect(categories) {
        val names = categories.map { it.name }
        if (names.isNotEmpty() && selectedCategory !in names) {
            selectedCategory = if (names.contains(com.example.data.DEFAULT_CATEGORY)) {
                com.example.data.DEFAULT_CATEGORY
            } else names.first()
        }
    }
    var bookToDelete by remember { mutableStateOf<Book?>(null) }
    var bookToMove by remember { mutableStateOf<Book?>(null) }
    var longPressBook by remember { mutableStateOf<Book?>(null) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryText by remember { mutableStateOf("") }
    // 隐私交互状态（第七轮第 6.3 条）：长按分类 → 分类操作面板；受保护分类进入 → PIN 验证
    var categorySheetFor by remember { mutableStateOf<com.example.data.CategoryEntity?>(null) }
    var pinVerifyFor by remember { mutableStateOf<com.example.data.CategoryEntity?>(null) }

    // 第七轮第 6.3 条验收缺口修复：受保护分类在"锁定"状态（隐私模式开启、标记保护、
    // 本进程未通过 PIN 解锁）时内容不得可见——包括重启后初始选中恰好是受保护分类的
    // 场景（旧逻辑只挡"切换进入"的点击，不挡默认选中直达）。
    val selectedCategoryLocked = remember(
        selectedCategory, privacyModeEnabled, protectedCategoryNames, unlockedCategoryIds, categories
    ) {
        if (!privacyModeEnabled) false
        else if (selectedCategory !in protectedCategoryNames) false
        else categories.find { it.name == selectedCategory }?.id !in unlockedCategoryIds
    }
    // 书架呼吸动画：30fps 自定义驱动（4 秒周期），视觉与 60fps 一致但绘制开销减半；
    // 无书时不启动，避免动画时钟空转。
    val hasBreathingBooks = books.isNotEmpty()
    val breathingProgress = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(hasBreathingBooks) {
        if (!hasBreathingBooks) return@LaunchedEffect
        val twoPi = (2.0 * Math.PI).toFloat()
        val step = twoPi / 120f // 4000ms / 33ms ≈ 121 步
        while (true) {
            delay(33)
            breathingProgress.floatValue = (breathingProgress.floatValue + step) % twoPi
        }
    }

    // Filter books by category and search query (debounced)
    // 第七轮第 6.1/6.2 条：分类互斥单归属——选中分类只显示归属它的书；
    // 6.3：锁定状态下的受保护分类不显示内容（PIN 解锁后才可见）
    val filteredBooks = remember(books, selectedCategory, selectedCategoryLocked, debouncedQuery) {
        if (selectedCategoryLocked) emptyList()
        else books.filter { book ->
            val matchesCategory = book.category == selectedCategory
            val matchesSearch = book.title.contains(debouncedQuery, ignoreCase = true) ||
                                book.author.contains(debouncedQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    // 书架排序：0=默认导入顺序 1=按标题 A-Z 2=最近阅读优先
    var sortBy by remember { mutableIntStateOf(0) }
    var categoryToDelete by remember { mutableStateOf<com.example.data.CategoryEntity?>(null) }
    val sortedBooks = remember(filteredBooks, sortBy) {
        when (sortBy) {
            1 -> filteredBooks.sortedBy { it.title.lowercase() }
            2 -> filteredBooks.sortedByDescending { it.lastReadTime }
            else -> filteredBooks
        }
    }

    // 分类删除确认对话框（第七轮第 6.1 条："默认"分类不可删除；删除时书籍迁回默认）
    if (categoryToDelete != null) {
        val cat = categoryToDelete!!
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val isDefaultCategory = cat.name == com.example.data.DEFAULT_CATEGORY
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text(if (isDefaultCategory) "无法删除" else "删除分类", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (isDefaultCategory) "默认分类不可删除。"
                    else "确定要删除分类「${cat.name}」吗？其中的书籍会移动到「默认」分类。"
                )
            },
            confirmButton = {
                if (!isDefaultCategory) {
                    TextButton(onClick = {
                        onDeleteCategory?.invoke(cat) { deleted ->
                            if (!deleted) {
                                android.widget.Toast.makeText(
                                    ctx,
                                    "默认分类不可删除",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        if (selectedCategory == cat.name) selectedCategory = com.example.data.DEFAULT_CATEGORY
                        categoryToDelete = null
                    }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                } else {
                    TextButton(onClick = { categoryToDelete = null }) { Text("知道了") }
                }
            },
            dismissButton = {
                if (!isDefaultCategory) {
                    TextButton(onClick = { categoryToDelete = null }) { Text("取消") }
                }
            }
        )
    }

    // Move dialog
    if (bookToMove != null) {
        AcrylicDialog(
            onDismissRequest = { bookToMove = null },
            title = { Text("选择目标分类", fontWeight = FontWeight.Bold) },
            text = {
                androidx.compose.foundation.layout.Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    val availableCategories = categories.filter { it.name != bookToMove?.category }
                    if (availableCategories.isEmpty()) {
                        Text(
                            "没有其他可用的分类。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        availableCategories.forEach { cat ->
                            TextButton(
                                onClick = {
                                    onMoveBook(bookToMove!!, cat.name)
                                    bookToMove = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(cat.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { bookToMove = null }) {
                    Text("取消")
                }
            }
        )
    }

    // Delete dialog
    if (bookToDelete != null) {
        AcrylicDialog(
            onDismissRequest = { bookToDelete = null },
            title = { Text("移出此书", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "确认要将《${bookToDelete?.title}》从书架中移出吗？",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        bookToDelete?.let { onDeleteBook(it) }
                        bookToDelete = null
                    }
                ) {
                    Text("确认移出", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { bookToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    // New Category Dialog
    if (showAddCategoryDialog) {
        AcrylicDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("新建分类", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newCategoryText,
                    onValueChange = { newCategoryText = it },
                    label = { Text("名称", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = MintPrimary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedIndicatorColor = MintPrimary,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                        cursorColor = MintPrimary,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                TextButton(
                    onClick = {
                        if (newCategoryText.isNotBlank()) {
                            onAddCategory(newCategoryText.trim())
                            // 第十一轮第 2 条：与设置页一致的操作反馈（此前静默关闭，
                            // 用户无法区分"创建成功"与"点击无效"）
                            android.widget.Toast.makeText(
                                ctx, "已创建分类「${newCategoryText.trim()}」",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            newCategoryText = ""
                            showAddCategoryDialog = false
                        }
                    }
                ) {
                    Text("新建", color = MintPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 主书架滚动 → 卡片惯性倾斜信号源（任务书「整卡倾斜」§2）；同时驱动头部折叠（原声明在内容分支内，头部够不到，上提）
    val homeGridState = rememberLazyGridState()
    homeGridState.scrollTiltSource()
    val homeHeaderCollapsed = rememberHeaderCollapsed(homeGridState)

    StarryNightBackground(showLamp = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 1. TOP BAR GREETINGS & SEARCH
            val isDark = MaterialTheme.colorScheme.background == com.example.ui.theme.DarkCharcoal
            val barBgColor = if (isDark) Color(0xFF222428) else Color.White
            val searchPlaceholderColor = if (isDark) Color.LightGray.copy(alpha = 0.6f) else Color.Gray
            val searchTextColor = if (isDark) Color.White else Color.DarkGray
            val iconBtnBgColor = if (isDark) Color(0xFF2B2D31) else Color(0xFFF4F4F4)
            val iconCloseTint = if (isDark) Color.LightGray else Color.DarkGray

            TabScreenHeader(
                collapsed = homeHeaderCollapsed,
                title = "我的书架",
                // 与其它三页页头副标题统一英文风格（LIBRARY & SEARCH / STATISTICS & INSIGHTS / SETTINGS & PREFERENCES）
                subtitle = "BOOKSHELF & READING",
                titleColor = glassTitleColor(),
                titleVisible = !isSearchExpanded,
                trailing = {
                    AnimatedVisibility(
                        visible = isSearchExpanded,
                        enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
                        exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut()
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("输入书名或作者", color = searchPlaceholderColor, fontSize = 13.sp) },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 13.sp,
                                color = searchTextColor
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = searchTextColor,
                                unfocusedTextColor = searchTextColor,
                                focusedBorderColor = MintPrimary,
                                unfocusedBorderColor = if (isDark) Color(0xFF383A40) else Color.LightGray,
                                focusedContainerColor = if (isDark) Color(0xFF2B2D31) else Color.White,
                                unfocusedContainerColor = if (isDark) Color(0xFF2B2D31) else Color.White
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(end = 8.dp),
                            trailingIcon = {
                                AppIconButton(onClick = {
                                    searchQuery = ""
                                    isSearchExpanded = false
                                }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear", tint = iconCloseTint)
                                }
                            }
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // MAIN INTERFACE SCROLLABLE LAYOUT
            if (books.isEmpty()) {
                // EXQUISITE EMPTY STATE
                var isVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    isVisible = true
                }
                val alpha by animateFloatAsState(
                    targetValue = if (isVisible) 1f else 0f,
                    animationSpec = tween(durationMillis = 400, easing = EaseOut),
                    label = "empty_alpha"
                )
                val offsetY by animateDpAsState(
                    targetValue = if (isVisible) 0.dp else (-8).dp,
                    animationSpec = tween(durationMillis = 400, easing = EaseOut),
                    label = "empty_offset"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .alpha(alpha)
                        .offset(y = offsetY),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    com.example.ui.components.MascotEmptyState(
                        mascotResId = com.example.ui.mascot.MascotSpriteSheet.sadDrawable,
                        title = "「书架空空如也」",
                        description = "您的书架还没有任何书哦！Roxy 觉得有点寂寞。您可以点击下方按钮导入本地电子书，或者直接去在线书库挑选精彩的小说！",
                        actionLabel = "立即前往在线书库",
                        onActionClick = onNavigateToShelf,
                        testTagPrefix = "books_empty_state"
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    TextButton(
                        onClick = { onImportClick(com.example.data.DEFAULT_CATEGORY) },
                        modifier = Modifier.testTag("empty_bookshelf_import_button")
                    ) {
                        Text("或者 导入本地 TXT / EPUB / Comic 文件", color = MintPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // RICH PREMIUM CONTENT STATE
                val screenWidthDp = LocalConfiguration.current.screenWidthDp
                // 多设备一致：按宽度自适应列数（手机3列 / 折叠展开与小平板4列 / 宽平板最多6列），
                // 不再只有 3/4 两档——宽屏拉伸大卡片的问题源头
                val cols = ((screenWidthDp + 24) / 150).coerceIn(3, 6)

                // 第七轮第 6.3 条：Hero"正在阅读"卡片同样是内容泄漏面——
                // 其书籍所在分类处于锁定态时整卡隐藏（PIN 解锁后恢复）
                val heroCategoryLocked = remember(
                    currentlyReading.category, privacyModeEnabled,
                    protectedCategoryNames, unlockedCategoryIds, categories
                ) {
                    if (!privacyModeEnabled) false
                    else if (currentlyReading.category !in protectedCategoryNames) false
                    else categories.find { it.name == currentlyReading.category }?.id !in unlockedCategoryIds
                }

                // 第九轮修复⑤续：曾在此处叠加"内容纱罩"全尺寸渐变矩形（浅色主题下
                // 呈半透明白色大矩形盖住整个内容区，用户反馈遮挡/点击异常）——已从
                // 视图层彻底移除，书籍网格直接承载在页面背景之上。
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    state = homeGridState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!heroCategoryLocked) item(span = { GridItemSpan(maxLineSpan) }, key = "hero_section") {
                        // 2. HERO: RECENTLY READ BIG BOOK CARD (pre-calculated and remembered at top)

                        val heroCoverData = remember(currentlyReading.coverUri, currentlyReading.isCoverValid) {
                            if (currentlyReading.coverUri.isNullOrEmpty()) null
                            else if (currentlyReading.coverUri!!.startsWith("content://")) {
                                android.net.Uri.parse(currentlyReading.coverUri!!)
                            } else if (currentlyReading.isCoverValid) {
                                val path = if (currentlyReading.coverUri!!.startsWith("file://")) currentlyReading.coverUri!!.substring(7) else currentlyReading.coverUri!!
                                java.io.File(path)
                            } else {
                                null
                            }
                        }
                        val heroHasValidCover = heroCoverData != null

                        val context = androidx.compose.ui.platform.LocalContext.current
                        val heroImageRequest = remember(currentlyReading.coverUri, heroCoverData) {
                            if (heroCoverData == null) null else {
                                coil.request.ImageRequest.Builder(context)
                                    .data(heroCoverData)
                                    .memoryCacheKey(currentlyReading.coverUri)
                                    .diskCacheKey(currentlyReading.coverUri)
                                    .crossfade(true)
                                    .build()
                            }
                        }

                        Column {
                            Text(
                                text = "正在阅读",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = adaptiveTitleColor(),
                                fontFamily = FontFamily.Serif,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Large exquisite procedural book cover with light novel style
                                    Box(
                                        modifier = Modifier
                                            .size(width = 110.dp, height = 155.dp)
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        MintSecondary,
                                                        Color(0xFF1B143F),
                                                        MintPrimary
                                                    )
                                                ),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                            .shadow(6.dp, RoundedCornerShape(12.dp))
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (heroHasValidCover && heroImageRequest != null) {
                                            AsyncImage(
                                                model = heroImageRequest,
                                                contentDescription = currentlyReading.title,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.horizontalGradient(
                                                            colors = listOf(
                                                                Color.Black.copy(alpha = 0.15f),
                                                                Color.Transparent,
                                                                Color.Black.copy(alpha = 0.05f)
                                                            ),
                                                            startX = 0f,
                                                            endX = 40f
                                                        )
                                                    )
                                            )
                                        } else {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                                modifier = Modifier.padding(12.dp)
                                            ) {
                                                Icon(
                                                    Icons.Filled.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = MintGold,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Text(
                                                    text = currentlyReading.title,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Center,
                                                    lineHeight = 16.sp
                                                )
                                            }
                                        }

                                        // Decorative spine overlay on top of everything
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .width(6.dp)
                                                .background(Color.Black.copy(alpha = 0.15f))
                                                .align(Alignment.CenterStart)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = currentlyReading.title,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            fontFamily = FontFamily.Serif
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "著  ${currentlyReading.author}",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))

                                        // Calculate progress percentage safely
                                        val progressPercent = if (currentlyReading.totalChapters > 0) {
                                            (currentlyReading.currentChapterIndex * 100) / currentlyReading.totalChapters
                                        } else {
                                            32 // Beautiful fallback progress
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            LinearProgressIndicator(
                                                progress = { progressPercent / 100f },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(6.dp)
                                                    .clip(CircleShape),
                                                color = MintPrimary,
                                                trackColor = Color.Black.copy(alpha = 0.05f),
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "已读 $progressPercent%",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MintGold
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        AppActionButton(
                                            text = "继续阅读",
                                            onClick = { onBookClick(currentlyReading) },
                                            variant = AppButtonVariant.Primary,
                                            buttonSize = AppButtonSize.Small,
                                            modifier = Modifier.align(Alignment.End)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(span = { GridItemSpan(maxLineSpan) }, key = "bookshelf_header") {
                        // 3. MAIN SECTION: MY BOOKSHELF
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "我的书架",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = adaptiveTitleColor(),
                                    fontFamily = FontFamily.Serif
                                )

                                // 第七轮第 5.1 条：三个次级操作统一为有质感的玻璃小胶囊
                                // （图标+文字+轻背景描边），与标题形成明确主次——不过重、不抢内容
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    HomeActionChip(
                                        icon = Icons.Filled.Sort,
                                        label = when (sortBy) { 0 -> "默认"; 1 -> "A-Z"; else -> "最近" },
                                        emphasized = sortBy > 0,
                                    ) { sortBy = (sortBy + 1) % 3 }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    HomeActionChip(
                                        icon = Icons.Filled.Add,
                                        label = "导入新书",
                                        emphasized = true,
                                    ) { onImportClick(selectedCategory) }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    HomeActionChip(
                                        icon = Icons.Filled.Category,
                                        label = "新建分类",
                                        emphasized = false,
                                    ) { showAddCategoryDialog = true }
                                }
                            }

                            // 第七轮第 5.2/6.1 条：分类 Tab——可横向滚动的真实分类列表
                            // （"全部"聚合视图已移除，"默认"为不可删除的真实分类）。
                            // 受保护分类带锁标记，进入需 PIN 验证；长按弹出分类操作面板。
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                categories.forEach { cat ->
                                    val isSelected = selectedCategory == cat.name
                                    val isProtected = privacyModeEnabled &&
                                        protectedCategoryNames.contains(cat.name)
                                    val isUnlocked = unlockedCategoryIds.contains(cat.id)
                                    val locked = isProtected && !isUnlocked
                                    HomeCategoryChip(
                                        name = cat.name,
                                        selected = isSelected,
                                        locked = locked,
                                        onClick = {
                                            when {
                                                // 受保护且未解锁 → 先验证 PIN，通过后才进入
                                                locked -> pinVerifyFor = cat
                                                // 已解锁或普通分类 → 直接切换
                                                else -> selectedCategory = cat.name
                                            }
                                        },
                                        onLongClick = { categorySheetFor = cat },
                                    )
                                }
                            }
                        }
                    }

if (sortedBooks.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "empty_state") {
                            if (selectedCategoryLocked) {
                                // 6.3：受保护分类锁定态——不显示内容，引导验证密码
                                // 第九轮修复⑤：不再整块白色大卡（用户反馈"书架下面盖了
                                // 一层透明白色矩形"）——改为克制的行内玻璃胶囊，锁定
                                // 提示+验证按钮一行呈现，与书架玻璃语言一致。
                                val lockedCat = categories.find { it.name == selectedCategory }
                                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.42f))
                                            .border(
                                                0.5.dp,
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                                                RoundedCornerShape(18.dp)
                                            )
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.Lock,
                                            contentDescription = null,
                                            tint = MintPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = "分类『$selectedCategory』已锁定",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = adaptiveTitleColor()
                                            )
                                            Text(
                                                text = "验证隐私密码后即可查看其中的书籍",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(MintPrimary.copy(alpha = 0.14f))
                                                .border(
                                                    0.5.dp,
                                                    MintPrimary.copy(alpha = 0.45f),
                                                    RoundedCornerShape(14.dp)
                                                )
                                                .clickableWithFeedback { lockedCat?.let { pinVerifyFor = it } }
                                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                "验证密码查看",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MintPrimary
                                            )
                                        }
                                    }
                                }
                            } else {
                                com.example.ui.components.MascotEmptyState(
                                    mascotResId = com.example.ui.mascot.MascotSpriteSheet.sadDrawable,
                                    title = "「分类下没有图书」",
                                    description = "在分类『$selectedCategory』下还没有任何图书哦。您可以点击下方按钮导入新书到该分类！",
                                    actionLabel = "在此分类下导入新书",
                                    onActionClick = { onImportClick(selectedCategory) },
                                    testTagPrefix = "category_empty_state"
                                )
                            }
                        }
                    } else {
                        items(items = sortedBooks, key = { it.id }) { book ->
                            val coverData = remember(book.coverUri, book.isCoverValid) {
                                if (book.coverUri.isNullOrEmpty()) null
                                else if (book.coverUri!!.startsWith("content://")) {
                                    android.net.Uri.parse(book.coverUri!!)
                                } else if (book.isCoverValid) {
                                    val path = if (book.coverUri!!.startsWith("file://")) book.coverUri!!.substring(7) else book.coverUri!!
                                    java.io.File(path)
                                } else {
                                    null
                                }
                            }
                            val hasValidCover = coverData != null

                            val context = androidx.compose.ui.platform.LocalContext.current
                            val imageRequest = remember(book.coverUri, coverData) {
                                if (coverData == null) null else {
                                    coil.request.ImageRequest.Builder(context)
                                        .data(coverData)
                                        .memoryCacheKey(book.coverUri)
                                        .diskCacheKey(book.coverUri)
                                        .crossfade(true)
                                        .build()
                                }
                            }

                            var isPressed by remember { mutableStateOf(false) }
                            var isLongPressed by remember { mutableStateOf(false) }

                            val scaleAnim by animateFloatAsState(
                                targetValue = if (isLongPressed) 1.05f else 1f,
                                animationSpec = tween(durationMillis = 200),
                                label = "book_scale"
                            )
                            val translationZAnim by animateFloatAsState(
                                targetValue = if (isLongPressed) -8f else 0f,
                                animationSpec = tween(durationMillis = 150),
                                label = "book_elevation"
                            )

                            Box(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier
                                        .graphicsLayer {
                                            val phase = (book.id.hashCode() % 1000) / 1000f * 2f * Math.PI.toFloat()
                                            translationY = translationZAnim.dp.toPx() +
                                                (kotlin.math.sin(breathingProgress.floatValue + phase) * 3.dp.toPx()).toFloat()
                                            scaleX = scaleAnim
                                            scaleY = scaleAnim
                                        }
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onPress = {
                                                    isPressed = true
                                                    if (tryAwaitRelease()) {
                                                        isPressed = false
                                                        isLongPressed = false
                                                    } else {
                                                        isPressed = false
                                                    }
                                                },
                                                onLongPress = {
                                                    isLongPressed = true
                                                    longPressBook = book
                                                },
                                                onTap = { onBookClick(book) }
                                            )
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                val isThemeDark = MaterialTheme.colorScheme.background == com.example.ui.theme.DarkCharcoal
                                val coverGradColors = if (isThemeDark) {
                                    listOf(
                                        Color(0xFF2B2D31),
                                        Color(0xFF1E2022),
                                        MintSecondary.copy(alpha = 0.15f)
                                    )
                                } else {
                                    listOf(
                                        Color(0xFFF4F4F4),
                                        Color(0xFFE3E5E7),
                                        MintSecondary.copy(alpha = 0.3f)
                                    )
                                }
                                
                                var boxModifier = Modifier
                                    .aspectRatio(0.72f)
                                    .fillMaxWidth()
                                if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                    with(sharedTransitionScope) {
                                        boxModifier = boxModifier.sharedElement(
                                            state = rememberSharedContentState(key = "book_cover_${book.id}"),
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            boundsTransform = { _, _ ->
                                                tween(420, easing = FastOutSlowInEasing)
                                            }
                                        )
                                    }
                                }
                                boxModifier = boxModifier
                                    // 第七轮第 5.4 条：封面卡片质感——更大圆角 + 更柔和的
                                    // 双层阴影（环境 + 投射），"书架上的书"更立体安静
                                    .shadow(
                                        elevation = 8.dp,
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                                        ambientColor = Color.Black.copy(alpha = 0.10f),
                                        spotColor = Color.Black.copy(alpha = 0.16f)
                                    )
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(colors = coverGradColors))
                                    .border(1.dp, Color.Black.copy(alpha = 0.07f), androidx.compose.foundation.shape.RoundedCornerShape(14.dp))

                                Box(
                                    modifier = boxModifier,
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (hasValidCover && imageRequest != null) {
                                        AsyncImage(
                                            model = imageRequest,
                                            contentDescription = book.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        // Subtle vertical shadow for paper depth
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.horizontalGradient(
                                                        colors = listOf(
                                                            Color.Black.copy(alpha = 0.15f),
                                                            Color.Transparent,
                                                            Color.Black.copy(alpha = 0.05f)
                                                        ),
                                                        startX = 0f,
                                                        endX = 40f
                                                    )
                                                )
                                        )
                                    } else {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxHeight().padding(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.MenuBook,
                                                contentDescription = null,
                                                tint = MintPrimary.copy(alpha = 0.6f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            
                                            Text(
                                                text = book.title,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center,
                                                lineHeight = 14.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            )

                                            Text(
                                                text = if (book.isComic) "漫画" else "TXT",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (book.isComic) Color(0xFFFF6B6B) else MintGold.copy(alpha = 0.8f)
                                            )
                                        }
                                    }

                                    // Cover Spine shadow overlay on top of everything
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(4.dp)
                                            .background(Color.Black.copy(alpha = 0.2f))
                                            .align(Alignment.CenterStart)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                Text(
                                    text = book.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // 读完标记：金色 ✓ + "已读完"
                                if (book.isFinished) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = MintGold,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            "已读完",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MintGold
                                        )
                                    }
                                }

                                // 阅读进度条（已开始阅读且未完成时显示）
                                if (book.totalChapters > 0 && book.currentChapterIndex > 0 && !book.isFinished) {
                                    val progress = book.currentChapterIndex.toFloat() / book.totalChapters
                                    LinearProgressIndicator(
                                        progress = { progress.coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp)
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = MintPrimary.copy(alpha = 0.85f),
                                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                                    )
                                    Text(
                                        text = "${book.currentChapterIndex}/${book.totalChapters} 章",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                                    )
                                }
                            }
                            } // Close Box
                        }
                    }

                    item(span = { GridItemSpan(maxLineSpan) }, key = "bottom_stats") {
                        // 4. BOTTOM INFO CARD: READING STATISTICS PREVIEW
                        Column(modifier = Modifier.padding(bottom = 24.dp)) {
                            Text(
                                text = "阅读统计",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = adaptiveTitleColor(),
                                fontFamily = FontFamily.Serif,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickableWithFeedback { onNavigateToStats() },
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        TodayReadTimeText(todayReadSecondsFlow)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "保持阅读，遇见更好的自己。",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .background(MintGold.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                            .border(1.dp, MintGold.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Filled.Star,
                                                contentDescription = null,
                                                tint = MintGold,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "连续 ${streakDays} 天",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MintGold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        longPressBook?.let { book ->
            BookActionSheet(
                book = book,
                onDismiss = { longPressBook = null },
                onOpenDetail = {
                    longPressBook = null
                    onBookClick(book)
                },
                onMove = {
                    longPressBook = null
                    bookToMove = book
                },
                onDelete = {
                    longPressBook = null
                    bookToDelete = book
                }
            )
        }

        /* ── 分类操作面板（长按分类；第七轮第 6.3 条） ── */
        categorySheetFor?.let { sheetCat ->
            // 第九轮修复②：面板展示/开关初值必须取实时分类（categories 由 Room Flow
            // 驱动）——此前直接用长按瞬间的 cat 快照，开关拨动后 DB 已更新而面板仍
            // 显示旧值（"开关不动、书架却已锁"的根因）。
            val latestSheetCat = categories.find { it.id == sheetCat.id } ?: sheetCat
            androidx.activity.compose.BackHandler { categorySheetFor = null }
            CategoryActionSheet(
                category = latestSheetCat,
                privacyModeEnabled = privacyModeEnabled,
                onToggleProtected = { protected ->
                    onToggleCategoryProtected?.invoke(latestSheetCat, protected)
                },
                onDeleteRequest = {
                    categorySheetFor = null
                    categoryToDelete = latestSheetCat
                },
                onGoToSettings = onSettingsClick,
                onDismiss = { categorySheetFor = null },
            )
        }

        /* ── 受保护分类的 PIN 验证（进入前；第七轮第 6.3/6.4 条） ── */
        pinVerifyFor?.let { cat ->
            val latestVerifyCat by rememberUpdatedState(cat)
            com.example.ui.privacy.PrivacyPinOverlay(
                mode = com.example.ui.privacy.PinEntryMode.VERIFY,
                onPinSet = { },
                onPinVerified = { pin ->
                    // 解锁成功即刻选中该分类（StateFlow → Compose 的传播晚于本同步回调）
                    val ok = onUnlockCategory?.invoke(latestVerifyCat, pin) ?: false
                    if (ok) selectedCategory = latestVerifyCat.name
                    ok
                },
                onDismiss = { pinVerifyFor = null },
            )
        }
    }

@Composable
private fun TodayReadTimeText(todaySecondsFlow: kotlinx.coroutines.flow.StateFlow<Long>) {
    // 第七轮第 4 条修复：数据源改为"今日"阅读秒数（daily_read_time_<今天>），
    // 旧版误用全生命周期累计值标"今日"；0 分钟如实显示（不再 coerceAtLeast(1)）
    val todaySeconds by todaySecondsFlow.collectAsState()
    val minutes = todayReadMinutes(todaySeconds)

    Text(
        text = "今日已阅读 $minutes 分钟",
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/** 今日阅读秒数 → 显示分钟（四舍五入到最近分钟；纯函数可单测）。
 *  验收口径：精确阅读 5 分钟（≈300s±2s）→ 显示 5，误差远小于 ±10 秒容差。 */
internal fun todayReadMinutes(todaySeconds: Long): Int =
    kotlin.math.round(todaySeconds / 60.0).toInt()

/* ══════════════ 第七轮第 5 条：书架操作胶囊 / 分类 Chip ══════════════ */

/**
 * 书架顶部的次级操作胶囊（排列方式 / 导入新书 / 新建分类）：
 * 图标 + 文字 + 轻背景描边（与设置面板的毛玻璃语言同源），
 * 克制的尺寸——次级操作不抢"我的书架"标题和书籍内容的视觉主次。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = tween(120), label = "homeChipPress"
    )
    Row(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (emphasized) 0.14f else 0.08f))
            .border(
                0.5.dp,
                if (emphasized) MintPrimary.copy(alpha = 0.40f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                RoundedCornerShape(18.dp)
            )
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (emphasized) MintPrimary else adaptiveTitleColor().copy(alpha = 0.60f),
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (emphasized) MintPrimary else adaptiveTitleColor().copy(alpha = 0.72f),
            maxLines = 1
        )
    }
}

/**
 * 分类 Chip（第七轮第 5.2/6.3 条）：可横向滚动的书架导航。
 * 选中 = 品牌薄荷半透明填充 + 细边框 + 加粗；未选中弱化。
 * 受保护且未解锁的分类带锁图标；长按弹出分类操作面板（设密码 / 删除）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeCategoryChip(
    name: String,
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val baseColor = if (selected) MintPrimary else MaterialTheme.colorScheme.onSurface
    val bgAlpha by animateFloatAsState(
        targetValue = if (selected) 0.16f else 0.06f,
        animationSpec = tween(160), label = "catChipBg"
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (selected) 0.50f else 0.13f,
        animationSpec = tween(160), label = "catChipBorder"
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(17.dp))
            .background(baseColor.copy(alpha = bgAlpha))
            .border(0.5.dp, baseColor.copy(alpha = borderAlpha), RoundedCornerShape(17.dp))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MintPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
        )
        if (locked) {
            Spacer(modifier = Modifier.width(5.dp))
            Icon(
                Icons.Filled.Lock,
                contentDescription = "已加密",
                tint = if (selected) MintPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

/**
 * 分类操作面板（长按分类；第七轮第 6.3 条）：
 * - 密码保护开关（受 6.4 隐私模式总开关约束——未开启时引导去设置开启）；
 * - 删除分类（"默认"分类给出"不可删除"提示）。
 */
@Composable
private fun CategoryActionSheet(
    category: com.example.data.CategoryEntity,
    privacyModeEnabled: Boolean,
    onToggleProtected: (Boolean) -> Unit,
    onDeleteRequest: () -> Unit,
    onGoToSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isDefault = category.name == com.example.data.DEFAULT_CATEGORY
    Box(
        Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // 第十一轮第 4 条：长按菜单出现动画——遮罩淡入 + 面板自底部上滑，
        // 与下载中心底部面板（AcrylicBottomOverlay）同款节奏
        com.example.ui.components.ScrimEntrance {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x59000000))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() }
            )
        }
        com.example.ui.components.BottomSheetEntrance {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                // 任务四修复：悬浮 Tab 栏渲染在 MainActivity 层级（本面板之上），
                // 原 28dp 底边距使「删除分类」行落在 Tab 栏后面被挡——抬高面板
                // 至 Tab 栏上方（96dp 为全 App 底部避让惯例，见 LibraryScreen
                // extraBottomPadding），另加 8dp 视觉间隙
                .padding(horizontal = 20.dp)
                .padding(top = 28.dp, bottom = 104.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { },
            shape = RoundedCornerShape(24.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (category.isProtected) Icons.Filled.Lock else Icons.Filled.Folder,
                        contentDescription = null,
                        tint = if (category.isProtected) MintPrimary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        category.name,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (privacyModeEnabled) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "密码保护",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "进入此分类需要验证密码 · 无痕阅读",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            )
                        }
                        com.example.ui.components.AppSwitch(
                            checked = category.isProtected,
                            onCheckedChange = onToggleProtected
                        )
                    }
                } else {
                    // 6.4 总开关约束：隐私模式未开启时不能对分类设密码——引导去开启
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                            .clickable {
                                onDismiss()
                                onGoToSettings()
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "设置密码保护",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "需先开启隐私模式 ›",
                            fontSize = 11.sp,
                            color = MintPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isDefault) MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                        )
                        .clickable(enabled = !isDefault) { onDeleteRequest() }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = if (isDefault) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        if (isDefault) "删除分类（默认分类不可删除）" else "删除分类",
                        fontSize = 13.sp,
                        color = if (isDefault) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        } // BottomSheetEntrance 结束
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookActionSheet(
    book: Book,
    onDismiss: () -> Unit,
    onOpenDetail: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    // 分享用 Activity 级作用域：弹窗关闭也不中断正在准备的分享
    val hostActivity = LocalContext.current as? androidx.activity.ComponentActivity
    val shareScope = hostActivity?.lifecycleScope ?: rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }

    val coverData = remember(book.coverUri, book.isCoverValid) {
        if (book.coverUri.isNullOrEmpty()) {
            null
        } else if (book.coverUri!!.startsWith("content://")) {
            android.net.Uri.parse(book.coverUri!!)
        } else if (book.isCoverValid) {
            val path = if (book.coverUri!!.startsWith("file://")) {
                book.coverUri!!.substring(7)
            } else {
                book.coverUri!!
            }
            java.io.File(path)
        } else {
            null
        }
    }
    val imageRequest = remember(book.coverUri, coverData) {
        if (coverData == null) null else coil.request.ImageRequest.Builder(context)
            .data(coverData)
            .memoryCacheKey(book.coverUri)
            .diskCacheKey(book.coverUri)
            .crossfade(true)
            .build()
    }

    /* 下滑关闭手势状态 */
    var dragOffsetY by remember { mutableStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val dismissThreshold = with(density) { 120.dp.toPx() }

    /* 面板高度硬约束：50% 屏高 —— 菜单列内部滚动，
       "删除图书"在任何设备上都物理不可能出界 */
    val maxPanelH = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.5f

    /* 下载管理中心同款容器：亚克力底部悬浮面板 */
    AcrylicBottomOverlay(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxPanelH)
                .offset { IntOffset(0, dragOffsetY.roundToInt()) }
        ) {
            Column {
                // 拖拽把手
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (dragOffsetY > dismissThreshold) onDismiss()
                                    else dragOffsetY = 0f
                                },
                                onDragCancel = { dragOffsetY = 0f },
                                onVerticalDrag = { _, dragAmount ->
                                    dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFB9B9BE))
                    )
                }

                // 封面 + 书名/作者
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(78.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Gray.copy(alpha = 0.2f))
                    ) {
                        if (imageRequest != null) {
                            AsyncImage(
                                model = imageRequest,
                                contentDescription = book.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = book.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = book.author,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                // 操作菜单（内部排版与原版一致；weight 弹性填充剩余空间）
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    val primary = MaterialTheme.colorScheme.primary
                    val error = MaterialTheme.colorScheme.error
                    val onSurface = MaterialTheme.colorScheme.onSurface
                    listOf(
                        Triple("打开详情", Icons.Default.MenuBook, primary),
                        Triple("移动到其他书架", Icons.Default.Folder, primary),
                        Triple("分享图书", Icons.Default.Share, primary),
                        Triple("删除图书", Icons.Default.Delete, error)
                    ).forEach { (label, icon, tint) ->
                        val interaction = remember { MutableInteractionSource() }
                        val pressed by interaction.collectIsPressedAsState()
                        val pressScale by animateFloatAsState(
                            targetValue = if (pressed) 0.97f else 1f,
                            label = "press"
                        )
                        if (label == "分享图书" && sharing) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                                    .padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = primary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("正在准备分享…", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = onSurface)
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                                    .clickable(
                                        interactionSource = interaction,
                                        indication = null,
                                        enabled = !(label == "分享图书" && sharing)
                                    ) {
                                        when (label) {
                                            "打开详情" -> onOpenDetail()
                                            "移动到其他书架" -> onMove()
                                            "分享图书" -> {
                                                if (sharing) return@clickable
                                                sharing = true
                                                shareScope.launch {
                                                    val err = com.example.library.BookShareHelper.shareBook(context, book)
                                                    sharing = false
                                                    if (err != null) Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            else -> onDelete()
                                        }
                                    }
                                    .padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = label,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (label == "删除图书") tint else onSurface
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.navigationBarsPadding())
                }
            }
        }
    }
}
