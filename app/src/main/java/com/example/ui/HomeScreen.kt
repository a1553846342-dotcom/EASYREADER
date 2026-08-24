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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
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
import com.example.ui.components.AppIconButton
import com.example.ui.components.GlassCard
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
    totalReadTimeSecondsFlow: kotlinx.coroutines.flow.StateFlow<Long>,
    streakDaysFlow: kotlinx.coroutines.flow.StateFlow<Int>,
    onDeleteBook: (Book) -> Unit,
    onMoveBook: (Book, String) -> Unit
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
    var selectedCategory by remember { mutableStateOf("全部") }
    var bookToDelete by remember { mutableStateOf<Book?>(null) }
    var bookToMove by remember { mutableStateOf<Book?>(null) }
    var longPressBook by remember { mutableStateOf<Book?>(null) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryText by remember { mutableStateOf("") }
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
    val filteredBooks = remember(books, selectedCategory, debouncedQuery) {
        books.filter { book ->
            val matchesCategory = (selectedCategory == "全部") || (book.category == selectedCategory)
            val matchesSearch = book.title.contains(debouncedQuery, ignoreCase = true) || 
                                book.author.contains(debouncedQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
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
                TextButton(
                    onClick = {
                        if (newCategoryText.isNotBlank()) {
                            onAddCategory(newCategoryText.trim())
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

    StarryNightBackground(showLamp = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 1. TOP BAR GREETINGS & SEARCH
            val isDark = MaterialTheme.colorScheme.background == com.example.ui.theme.DarkCharcoal
            val barBgColor = if (isDark) Color(0xFF222428) else Color.White
            val searchPlaceholderColor = if (isDark) Color.LightGray.copy(alpha = 0.6f) else Color.Gray
            val searchTextColor = if (isDark) Color.White else Color.DarkGray
            val iconBtnBgColor = if (isDark) Color(0xFF2B2D31) else Color(0xFFF4F4F4)
            val iconCloseTint = if (isDark) Color.LightGray else Color.DarkGray

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isSearchExpanded) {
                    Column(
                        modifier = Modifier.animateContentSize()
                    ) {
                        Text(
                            text = "我的书架",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = glassTitleColor(),
                            fontFamily = FontFamily.Serif
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "欢迎回到私人数字书库",
                            fontSize = 12.sp,
                            color = glassTitleColor().copy(alpha = 0.75f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Smoothly animated Expandable Search bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.weight(if (isSearchExpanded) 1f else 0.5f)
                ) {
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
            }
            }

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
                        onClick = { onImportClick("全部") },
                        modifier = Modifier.testTag("empty_bookshelf_import_button")
                    ) {
                        Text("或者 导入本地 TXT / EPUB / Comic 文件", color = MintPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // RICH PREMIUM CONTENT STATE
                val screenWidthDp = LocalConfiguration.current.screenWidthDp
                val cols = if (screenWidthDp > 600) 4 else 3

                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "hero_section") {
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

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = { onImportClick(selectedCategory) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.Add, contentDescription = null, tint = MintPrimary, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("导入新书", color = MintPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    AppIconButton(
                                        onClick = { showAddCategoryDialog = true },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Add,
                                            contentDescription = "新建分类",
                                            tint = MintPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            // Category tabs beautifully integrated inside Starry space
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val categoryNames = listOf("全部") + categories.map { it.name }
                                categoryNames.forEach { name ->
                                    val isSelected = selectedCategory == name
                                    val bgAlphaState = animateFloatAsState(
                                        targetValue = if (isSelected) 0.2f else 0.05f,
                                        label = "category_bg_anim"
                                    )
                                    val borderAlphaState = animateFloatAsState(
                                        targetValue = if (isSelected) 0.5f else 0.15f,
                                        label = "category_border_anim"
                                    )
                                    val textColor = if (isSelected) MintSecondary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    val baseColor = if (isSelected) MintSecondary else MaterialTheme.colorScheme.onSurface

                                    Box(
                                        modifier = Modifier
                                            .drawBehind {
                                                drawRoundRect(
                                                    color = baseColor.copy(alpha = bgAlphaState.value),
                                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
                                                )
                                            }
                                            .border(1.dp, baseColor.copy(alpha = borderAlphaState.value), RoundedCornerShape(16.dp))
                                            .clickableWithFeedback { selectedCategory = name }
                                            .padding(horizontal = 14.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = name,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = textColor,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (filteredBooks.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "empty_state") {
                            com.example.ui.components.MascotEmptyState(
                                mascotResId = com.example.ui.mascot.MascotSpriteSheet.sadDrawable,
                                title = "「分类下没有图书」",
                                description = "在分类『$selectedCategory』下还没有任何图书哦。您可以点击下方按钮导入新书到该分类！",
                                actionLabel = "在此分类下导入新书",
                                onActionClick = { onImportClick(selectedCategory) },
                                testTagPrefix = "category_empty_state"
                            )
                        }
                    } else {
                        items(items = filteredBooks, key = { it.id }) { book ->
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
                                    .shadow(4.dp, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(colors = coverGradColors))
                                    .border(1.dp, Color.Black.copy(alpha = 0.05f), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))

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
                                        TodayReadTimeText(totalReadTimeSecondsFlow)
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
    }

@Composable
private fun TodayReadTimeText(totalReadTimeFlow: kotlinx.coroutines.flow.StateFlow<Long>) {
    val totalSeconds by totalReadTimeFlow.collectAsState()
    val minutes = (totalSeconds / 60).coerceAtLeast(1)

    Text(
        text = "今日已阅读 $minutes 分钟",
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
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
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    // 分享用 Activity 级作用域：弹窗关闭也不中断正在准备的分享
    val hostActivity = LocalContext.current as? androidx.activity.ComponentActivity
    val shareScope = hostActivity?.lifecycleScope ?: rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val backdrop = rememberGlassPanelBackdrop()
    val iridescentColors = rememberIridescentColors()

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
        if (coverData == null) {
            null
        } else {
            coil.request.ImageRequest.Builder(context)
                .data(coverData)
                .memoryCacheKey(book.coverUri)
                .diskCacheKey(book.coverUri)
                .crossfade(true)
                .build()
        }
    }

    var visible by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val dismiss = {
        if (!dismissed) {
            dismissed = true
            visible = false
        }
    }
    LaunchedEffect(dismissed) {
        if (dismissed) {
            kotlinx.coroutines.delay(280)
            onDismiss()
        }
    }

    val blurPx = with(androidx.compose.ui.platform.LocalDensity.current) { 18.dp.toPx() }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val sheetContext = androidx.compose.ui.platform.LocalContext.current
    val sheetDensity = androidx.compose.ui.platform.LocalDensity.current
    val reduceEffects = remember {
        val resolver = sheetContext.contentResolver
        val reduceTransparency = try {
            android.provider.Settings.Global.getInt(resolver, "reduce_transparency", 0) == 1
        } catch (_: Exception) {
            false
        }
        val animationsOff = try {
            android.provider.Settings.Global.getFloat(
                resolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Exception) {
            false
        }
        reduceTransparency || animationsOff
    }

    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        // 透明窗口 + 实时模糊宿主窗口（decorView RenderEffect）
        GlassDialogWindowEffect(activity = activity, blurRadiusPx = blurPx)
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(160)) +
                slideInVertically(tween(340), initialOffsetY = { it }),
            exit = fadeOut(tween(150)) +
                slideOutVertically(tween(260), targetOffsetY = { it })
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 径向渐变遮罩：中心亮、四周暗，聚光灯打在立牌上
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .radialGlassScrim()
                )
                // 点击空白处关闭
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = dismiss
                        )
                )
                // 液态玻璃弹窗本体
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .offset { IntOffset(0, dragOffsetY.roundToInt()) }
                        .zIndex(1f)
                        // 双层阴影：环境阴影（品牌色）+ 贴地接触阴影
                        .shadow(
                            elevation = 32.dp,
                            shape = sheetShape,
                            ambientColor = iridescentColors.first().copy(alpha = 0.12f),
                            spotColor = iridescentColors.first().copy(alpha = 0.12f)
                        )
                        .shadow(
                            elevation = 8.dp,
                            shape = sheetShape,
                            ambientColor = Color.Black.copy(alpha = 0.20f),
                            spotColor = Color.Black.copy(alpha = 0.20f)
                        )
                        .liquidGlass(
                            backdrop = backdrop,
                            shape = sheetShape,
                            surfaceColor = MaterialTheme.colorScheme.surface.copy(
                                alpha = if (reduceEffects) 0.72f else 0.58f
                            ),
                            blurRadius = 12.dp,
                            refraction = false
                        )
                        .clip(sheetShape)
                        .filmGrain(alpha = 0.04f)
                        .iridescentBorder(
                            shape = sheetShape,
                            colors = iridescentColors,
                            width = 2.dp,
                            alpha = 0.22f
                        )
                        .navigationBarsPadding()
                ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 6.dp)
                .pointerInput(Unit) {
                    val dismissThreshold = with(sheetDensity) { 120.dp.toPx() }
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragOffsetY > dismissThreshold) {
                                dismiss()
                            } else {
                                dragOffsetY = 0f
                            }
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 用固定上限而非 weight：父级是 wrap 高度 + heightIn 时 weight 滚动会失效，
                // 导致最后一项（删除图书）被顶出屏幕。固定上限保证任何屏幕都能滚动到。
                .heightIn(max = 340.dp)
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
                // 大文件准备分享的加载反馈
                ListItem(
                    headlineContent = {
                        Text(
                            text = "正在准备分享…",
                            fontWeight = FontWeight.Medium,
                            color = onSurface
                        )
                    },
                    leadingContent = {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = primary
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.06f)
                    ),
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = pressScale
                            scaleY = pressScale
                        }
                )
            } else {
                ListItem(
                    headlineContent = {
                        Text(
                            text = label,
                            fontWeight = FontWeight.Medium,
                            color = if (label == "删除图书") tint else onSurface
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = tint
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.06f)
                    ),
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = pressScale
                            scaleY = pressScale
                        }
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
                                        val error = com.example.library.BookShareHelper.shareBook(context, book)
                                        sharing = false
                                        if (error != null) {
                                            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                else -> onDelete()
                            }
                        }
                )
            }
        }
        Spacer(modifier = Modifier.navigationBarsPadding())
        }
            }
        }
    }
    }
}
