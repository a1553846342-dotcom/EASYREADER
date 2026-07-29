package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextAlign
import com.example.ui.components.AppButton
import com.example.ui.components.AppIconButton
import com.example.ui.theme.clickableWithFeedback
import com.example.data.PreferencesManager
import com.example.data.Book
import com.example.data.CategoryEntity
import com.example.ui.theme.MintPrimary
import com.example.ui.theme.MintSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookShelfScreen(
    books: List<Book>,
    categories: List<CategoryEntity>,
    onBookClick: (Book) -> Unit,
    onDeleteBook: (Book) -> Unit,
    onImportClick: () -> Unit,
    onSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    var selectedCategory by remember { mutableStateOf("全部") }
    var bookToDelete by remember { mutableStateOf<Book?>(null) }

    val posterLauncher = rememberLauncherForActivityResult(
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
                prefs.customSplashPosterUri = localUriStr
                Toast.makeText(context, "开屏海报已更新！重新打开App即可生效", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "图片选择失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val filteredBooks = remember(books, selectedCategory) {
        if (selectedCategory == "全部") books else books.filter { it.category == selectedCategory }
    }

    if (bookToDelete != null) {
        AlertDialog(
            onDismissRequest = { bookToDelete = null },
            title = { Text("删除图书", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = { Text("删除这本书？", fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        bookToDelete?.let { onDeleteBook(it) }
                        bookToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = CircleShape
                ) {
                    Text("删除", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { bookToDelete = null }) {
                    Text("取消")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("本地书架", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                },
                actions = {
                    AppButton(
                        onClick = onImportClick,
                        containerColor = MintPrimary,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("导入图书", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    AppIconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置", tint = MintPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Category Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val categoryNames = listOf("全部") + categories.map { it.name }
                categoryNames.forEach { name ->
                    val isSelected = selectedCategory == name
                    val backgroundColor by animateColorAsState(
                        targetValue = if (isSelected) MintPrimary.copy(alpha = 0.15f) else Color.Transparent,
                        animationSpec = tween(durationMillis = 200),
                        label = "category_bg_anim"
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) MintPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        animationSpec = tween(durationMillis = 200),
                        label = "category_text_anim"
                    )

                    Box(
                        modifier = Modifier
                            .background(backgroundColor, shape = RoundedCornerShape(16.dp))
                            .clickableWithFeedback { selectedCategory = name }
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name,
                            fontSize = if (isSelected) 16.sp else 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = textColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredBooks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Book,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "还没书",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        AppButton(
                            onClick = onImportClick
                        ) {
                            Text("导入", color = Color.White)
                        }
                    }
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val columns = when {
                        maxWidth > 900.dp -> 5
                        maxWidth > 600.dp -> 4
                        else -> 2
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(filteredBooks, key = { it.id }) { book ->
                            BookCardItem(
                                book = book,
                                onClick = { onBookClick(book) },
                                onDelete = { bookToDelete = book }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BookCardItem(
    book: Book,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(16.dp))
            .clickableWithFeedback { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Optional cover image background if coverUri is present
            if (!book.coverUri.isNullOrEmpty()) {
                val coverFile = java.io.File(book.coverUri)
                if (coverFile.exists()) {
                    coil.compose.AsyncImage(
                        model = coverFile,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.05f)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        alpha = 0.22f
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (book.isComic) Color(0xFFFF6B6B).copy(alpha = 0.18f) else MintPrimary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (book.isComic) "漫画" else "TXT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (book.isComic) Color(0xFFFF6B6B) else MintPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    AppIconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = book.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = book.author,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Column {
                    val progressText = if (book.totalChapters > 0) {
                        "已读 ${((book.currentChapterIndex + 1).toFloat() / book.totalChapters * 100).toInt()}%"
                    } else "未读"

                    LinearProgressIndicator(
                        progress = {
                            if (book.totalChapters > 0) {
                                (book.currentChapterIndex + 1).toFloat() / book.totalChapters
                            } else 0f
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = if (book.isComic) Color(0xFFFF6B6B) else MintPrimary,
                        trackColor = (if (book.isComic) Color(0xFFFF6B6B) else MintPrimary).copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = progressText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (book.isComic) Color(0xFFFF6B6B) else MintSecondary
                    )
                }
            }
        }
    }
}
