package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.Book
import com.example.data.CategoryEntity
import com.example.ui.components.AppButton
import com.example.ui.components.AppIconButton
import com.example.ui.components.GlassCard
import com.example.ui.components.StarryNightBackground
import com.example.ui.theme.MintGold
import com.example.ui.theme.MintPrimary
import com.example.ui.theme.MintSecondary
import com.example.ui.theme.clickableWithFeedback

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    books: List<Book>,
    categories: List<CategoryEntity>,
    onBookClick: (Book) -> Unit,
    onImportClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNavigateToShelf: () -> Unit,
    onNavigateToStats: () -> Unit,
    totalReadTimeSeconds: Long,
    streakDays: Int = 0,
    onDeleteBook: (Book) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("全部") }
    var bookToDelete by remember { mutableStateOf<Book?>(null) }

    // Unified animation driver for book breathing
    val globalBreathingTransition = rememberInfiniteTransition(label = "global_breathing")
    val globalBreathingProgress by globalBreathingTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    // Filter books by category and search query
    val filteredBooks = remember(books, selectedCategory, searchQuery) {
        books.filter { book ->
            val matchesCategory = (selectedCategory == "全部") || (book.category == selectedCategory)
            val matchesSearch = book.title.contains(searchQuery, ignoreCase = true) || 
                                book.author.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    // Delete dialog
    if (bookToDelete != null) {
        AlertDialog(
            onDismissRequest = { bookToDelete = null },
            title = { Text("移出此书", fontWeight = FontWeight.Bold, color = Color.White) },
            text = { Text("确认要将《${bookToDelete?.title}》从书架中移出吗？", color = Color.White.copy(alpha = 0.8f)) },
            containerColor = Color(0xFF1B143F),
            shape = RoundedCornerShape(24.dp),
            confirmButton = {
                TextButton(
                    onClick = {
                        bookToDelete?.let { onDeleteBook(it) }
                        bookToDelete = null
                    }
                ) {
                    Text("确认移出", color = Color(0xFFFF5E7E), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { bookToDelete = null }) {
                    Text("取消", color = Color.Gray)
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
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .shadow(8.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                color = Color.White
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
                            color = Color.DarkGray,
                            fontFamily = FontFamily.Serif
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "欢迎回到私人数字书库",
                            fontSize = 12.sp,
                            color = Color.Gray,
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
                            placeholder = { Text("搜索书籍...", color = Color.Gray, fontSize = 12.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.DarkGray,
                                unfocusedTextColor = Color.DarkGray,
                                focusedBorderColor = MintPrimary,
                                unfocusedBorderColor = Color.LightGray,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .padding(end = 8.dp),
                            trailingIcon = {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    isSearchExpanded = false
                                }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Color.DarkGray)
                                }
                            }
                        )
                    }

                    if (!isSearchExpanded) {
                        AppIconButton(
                            onClick = { isSearchExpanded = true },
                            modifier = Modifier
                                .shadow(2.dp, CircleShape).background(Color(0xFFF4F4F4), CircleShape)
                                
                        ) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = "搜索",
                                tint = MintPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        AppIconButton(
                            onClick = onSettingsClick,
                            modifier = Modifier
                                .shadow(2.dp, CircleShape).background(Color(0xFFF4F4F4), CircleShape)
                                
                        ) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = "设置",
                                tint = MintPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
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
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(16.dp, RoundedCornerShape(24.dp))
                    ) {
                        AsyncImage(
                            model = R.drawable.empty_bookshelf_cat,
                            contentDescription = "Empty Bookshelf Decor",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Inside
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "「书架暂无书籍」",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.DarkGray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            fontFamily = FontFamily.Serif
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "导入本地 TXT 格式电子书开始阅读",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        AppButton(
                            onClick = onImportClick,
                            containerColor = MintPrimary,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .height(48.dp)
                                .padding(horizontal = 24.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("导入新书", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        }
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
                        // 2. HERO: RECENTLY READ BIG BOOK CARD
                        val currentlyReading = books.maxByOrNull { it.lastReadTime } ?: books.first()

                        val heroHasValidCover = !currentlyReading.coverUri.isNullOrEmpty() && (
                            currentlyReading.coverUri!!.startsWith("content://") ||
                            (currentlyReading.coverUri!!.startsWith("file://") && java.io.File(currentlyReading.coverUri!!.substring(7)).exists()) ||
                            java.io.File(currentlyReading.coverUri!!).exists()
                        )

                        val heroCoverData = remember(currentlyReading.coverUri) {
                            if (currentlyReading.coverUri.isNullOrEmpty()) null
                            else if (currentlyReading.coverUri!!.startsWith("content://")) {
                                android.net.Uri.parse(currentlyReading.coverUri!!)
                            } else {
                                val path = if (currentlyReading.coverUri!!.startsWith("file://")) currentlyReading.coverUri!!.substring(7) else currentlyReading.coverUri!!
                                java.io.File(path)
                            }
                        }

                        val context = androidx.compose.ui.platform.LocalContext.current
                        val heroImageRequest = remember(currentlyReading.coverUri, heroCoverData) {
                            if (heroCoverData == null) null else {
                                val fileTimestamp = if (heroCoverData is java.io.File) heroCoverData.lastModified() else System.currentTimeMillis()
                                coil.request.ImageRequest.Builder(context)
                                    .data(heroCoverData)
                                    .memoryCacheKey("${currentlyReading.coverUri}_$fileTimestamp")
                                    .diskCacheKey("${currentlyReading.coverUri}_$fileTimestamp")
                                    .listener(
                                        onSuccess = { _, _ ->
                                            android.util.Log.d("EpubParser", "[COVER] UI Cover hero load success: ${currentlyReading.coverUri}")
                                        },
                                        onError = { _, result ->
                                            android.util.Log.e("EpubParser", "[COVER] UI Cover hero load error for ${currentlyReading.coverUri}: ${result.throwable.message}")
                                        }
                                    )
                                    .build()
                            }
                        }

                        Column {
                            Text(
                                text = "正在阅读",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.DarkGray,
                                fontFamily = FontFamily.Serif,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(12.dp, RoundedCornerShape(20.dp))
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
                                                    color = Color.DarkGray,
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
                                            color = Color.DarkGray,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            fontFamily = FontFamily.Serif
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "著  ${currentlyReading.author}",
                                            fontSize = 13.sp,
                                            color = Color.Gray
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

                                        AppButton(
                                            onClick = { onBookClick(currentlyReading) },
                                            containerColor = MintPrimary,
                                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Text("继续阅读", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
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
                                    color = Color.DarkGray,
                                    fontFamily = FontFamily.Serif
                                )

                                TextButton(
                                    onClick = onImportClick,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Add, contentDescription = null, tint = MintPrimary, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("导入新书", color = MintPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                    val textColor = if (isSelected) MintPrimary else Color.White.copy(alpha = 0.5f)

                                    Box(
                                        modifier = Modifier
                                            .drawBehind {
                                                drawRoundRect(
                                                    color = Color.White.copy(alpha = bgAlphaState.value),
                                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
                                                )
                                            }
                                            .border(1.dp, Color.White.copy(alpha = borderAlphaState.value), RoundedCornerShape(16.dp))
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
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "在此分类下没有找到书籍哦",
                                    color = Color.Gray,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        items(items = filteredBooks, key = { it.id }) { book ->
                            val hasValidCover = !book.coverUri.isNullOrEmpty() && (
                                book.coverUri!!.startsWith("content://") ||
                                (book.coverUri!!.startsWith("file://") && java.io.File(book.coverUri!!.substring(7)).exists()) ||
                                java.io.File(book.coverUri!!).exists()
                            )

                            val coverData = remember(book.coverUri) {
                                if (book.coverUri.isNullOrEmpty()) null
                                else if (book.coverUri!!.startsWith("content://")) {
                                    android.net.Uri.parse(book.coverUri!!)
                                } else {
                                    val path = if (book.coverUri!!.startsWith("file://")) book.coverUri!!.substring(7) else book.coverUri!!
                                    java.io.File(path)
                                }
                            }

                            val context = androidx.compose.ui.platform.LocalContext.current
                            val imageRequest = remember(book.coverUri, coverData) {
                                if (coverData == null) null else {
                                    val fileTimestamp = if (coverData is java.io.File) coverData.lastModified() else System.currentTimeMillis()
                                    coil.request.ImageRequest.Builder(context)
                                        .data(coverData)
                                        .memoryCacheKey("${book.coverUri}_$fileTimestamp")
                                        .diskCacheKey("${book.coverUri}_$fileTimestamp")
                                        .listener(
                                            onSuccess = { _, _ ->
                                                android.util.Log.d("EpubParser", "[COVER] UI Cover grid load success: ${book.coverUri}")
                                            },
                                            onError = { _, result ->
                                                android.util.Log.e("EpubParser", "[COVER] UI Cover grid load error for ${book.coverUri}: ${result.throwable.message}")
                                            }
                                        )
                                        .build()
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .graphicsLayer {
                                        val phase = (book.id.hashCode() % 1000) / 1000f * 2f * Math.PI.toFloat()
                                        translationY = (kotlin.math.sin(globalBreathingProgress + phase) * 3.dp.toPx()).toFloat()
                                    }
                                    .combinedClickable(
                                        onClick = { onBookClick(book) },
                                        onLongClick = { bookToDelete = book }
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(0.72f)
                                        .fillMaxWidth()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color(0xFFF4F4F4),
                                                    Color(0xFFE3E5E7),
                                                    MintSecondary.copy(alpha = 0.3f)
                                                )
                                            ),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .border(1.dp, Color.Black.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                                        .shadow(4.dp, RoundedCornerShape(10.dp))
                                        .clip(RoundedCornerShape(10.dp)),
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
                                                color = Color.DarkGray,
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
                                    color = Color.DarkGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    item(span = { GridItemSpan(maxLineSpan) }, key = "bottom_stats") {
                        // 4. BOTTOM INFO CARD: READING STATISTICS PREVIEW
                        Column(modifier = Modifier.padding(bottom = 24.dp)) {
                            Text(
                                text = "阅读统计",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.DarkGray,
                                fontFamily = FontFamily.Serif,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickableWithFeedback { onNavigateToStats() }
                                    .shadow(8.dp, RoundedCornerShape(16.dp))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        val minutes = (totalReadTimeSeconds / 60).coerceAtLeast(1)
                                        Text(
                                            text = "今日已阅读 $minutes 分钟",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "保持阅读，遇见更好的自己。",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.5f)
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
    }
