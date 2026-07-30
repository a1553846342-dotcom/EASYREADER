package com.example

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.example.ui.*
import com.example.ui.theme.MintPrimary
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private var mainViewModel: MainViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            mainViewModel = viewModel

            val autoNightMode by viewModel.autoNightMode.collectAsState()
            val blueLightFilter by viewModel.blueLightFilter.collectAsState()
            val blueLightAlpha by viewModel.blueLightAlpha.collectAsState()
            val colorPrimaryIndex by viewModel.colorPrimaryIndex.collectAsState()
            val colorSecondaryIndex by viewModel.colorSecondaryIndex.collectAsState()

            MyApplicationTheme(
                darkTheme = autoNightMode,
                colorPrimaryIndex = colorPrimaryIndex,
                colorSecondaryIndex = colorSecondaryIndex
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            if (blueLightFilter) {
                                // Real warm-orange color filter overlay drawn on top of the entire application
                                // Maximum opacity of 0.65f to allow reading comfortably at 100% slider value
                                val maxOpacity = 0.65f
                                drawRect(
                                    color = Color(0xFFFF9E0D),
                                    alpha = blueLightAlpha * maxOpacity
                                )
                            }
                        },
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    val orientationLock = viewModel.prefs.screenOrientationLock
                    DisposableEffect(orientationLock) {
                        requestedOrientation = when (orientationLock) {
                            1 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            2 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                        onDispose {}
                    }

                    val importMessage by viewModel.importStatusMessage.collectAsState()

                    LaunchedEffect(importMessage) {
                        importMessage?.let {
                            Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show()
                            viewModel.clearImportMessage()
                        }
                    }

                    var selectedCategoryForImport by remember { mutableStateOf("全部") }

                    val fileLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                    ) { uri: Uri? ->
                        uri?.let {
                            try {
                                contentResolver.takePersistableUriPermission(
                                    it,
                                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            } catch (e: Exception) { e.printStackTrace() }

                            var fileName = "book.txt"
                            try {
                                contentResolver.query(it, null, null, null, null)?.use { cursor ->
                                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                    if (cursor.moveToFirst() && nameIndex >= 0) {
                                        fileName = cursor.getString(nameIndex) ?: "book.txt"
                                    }
                                }
                            } catch (e: Throwable) {
                                e.printStackTrace()
                            }
                            if (fileName == "book.txt") {
                                fileName = it.lastPathSegment?.substringAfterLast('/') ?: "book.txt"
                            }
                            android.util.Log.d("BookImport", "[MainActivity] File selected: $fileName, uri: $it, category: $selectedCategoryForImport")
                            viewModel.importBook(it, fileName, selectedCategoryForImport)
                        }
                    }

                    var selectedTab by remember { mutableIntStateOf(0) }

                    NavHost(
                        navController = navController,
                        startDestination = "splash",
                        enterTransition = { fadeIn(tween(250)) },
                        exitTransition = { fadeOut(tween(250)) },
                        popEnterTransition = { fadeIn(tween(250)) },
                        popExitTransition = { fadeOut(tween(250)) }
                    ) {
                        composable("splash") {
                            SplashScreen(
                                prefs = viewModel.prefs,
                                onSplashFinished = {
                                    navController.navigate("home") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("home") {
                            val books by viewModel.allBooks.collectAsState()
                            val categories by viewModel.allCategories.collectAsState()
                            val readingRecords by viewModel.allReadingRecords.collectAsState()

                            Scaffold(
                                bottomBar = {
                                    NavigationBar(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ) {
                                        NavigationBarItem(
                                            selected = selectedTab == 0,
                                            onClick = { selectedTab = 0 },
                                            icon = { Icon(Icons.Filled.Book, contentDescription = "书架") },
                                            label = { Text("书架") },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MintPrimary,
                                                selectedTextColor = MintPrimary,
                                                indicatorColor = MintPrimary.copy(alpha = 0.15f)
                                            )
                                        )
                                        NavigationBarItem(
                                            selected = selectedTab == 1,
                                            onClick = { selectedTab = 1 },
                                            icon = { Icon(Icons.Filled.BarChart, contentDescription = "统计") },
                                            label = { Text("统计") },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MintPrimary,
                                                selectedTextColor = MintPrimary,
                                                indicatorColor = MintPrimary.copy(alpha = 0.15f)
                                            )
                                        )
                                        NavigationBarItem(
                                            selected = selectedTab == 2,
                                            onClick = { selectedTab = 2 },
                                            icon = { Icon(Icons.Filled.Settings, contentDescription = "设置") },
                                            label = { Text("设置") },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MintPrimary,
                                                selectedTextColor = MintPrimary,
                                                indicatorColor = MintPrimary.copy(alpha = 0.15f)
                                            )
                                        )
                                    }
                                }
                            ) { innerPadding ->
                                Box(modifier = Modifier.padding(innerPadding)) {
                                    AnimatedContent(
                                        targetState = selectedTab,
                                        transitionSpec = {
                                            if (targetState > initialState) {
                                                (slideInHorizontally { width -> width / 3 } + fadeIn(tween(250)))
                                                    .togetherWith(slideOutHorizontally { width -> -width / 3 } + fadeOut(tween(200)))
                                            } else {
                                                (slideInHorizontally { width -> -width / 3 } + fadeIn(tween(250)))
                                                    .togetherWith(slideOutHorizontally { width -> width / 3 } + fadeOut(tween(200)))
                                            }
                                        },
                                        label = "TabSwitch"
                                    ) { tab ->
                                        when (tab) {
                                            0 -> HomeScreen(
                                                books = books,
                                                categories = categories,
                                                onBookClick = { book ->
                                                    viewModel.selectBook(book)
                                                    if (book.isComic) {
                                                        navController.navigate("comic_reader")
                                                    } else {
                                                        navController.navigate("reader")
                                                    }
                                                },
                                                onImportClick = { category ->
                                                    selectedCategoryForImport = category
                                                    fileLauncher.launch("*/*")
                                                },
                                                onAddCategory = { name ->
                                                    viewModel.addCategory(name)
                                                },
                                                onSettingsClick = {
                                                    selectedTab = 2
                                                },
                                                onNavigateToShelf = {
                                                    selectedTab = 0
                                                },
                                                onNavigateToStats = {
                                                    selectedTab = 1
                                                },
                                                totalReadTimeSeconds = viewModel.prefs.totalReadTimeSeconds,
                                                streakDays = viewModel.prefs.calculateStreak(),
                                                onDeleteBook = { book ->
                                                    viewModel.deleteBook(book)
                                                    com.example.ui.mascot.MascotAnimationController.play(com.example.ui.mascot.MascotEvent.DeleteBook)
                                                },
                                                onMoveBook = { book, newCategory ->
                                                    viewModel.moveBookToCategory(book, newCategory)
                                                    com.example.ui.mascot.MascotAnimationController.play(com.example.ui.mascot.MascotEvent.MoveBook)
                                                },
                                            )
                                            1 -> StatisticsScreen(
                                                books = books,
                                                totalReadTimeSeconds = viewModel.prefs.totalReadTimeSeconds,
                                                readingRecords = readingRecords
                                            )
                                            2 -> SettingsTabScreen(
                                                prefs = viewModel.prefs,
                                                backupManager = viewModel.backupManager,
                                                categories = categories,
                                                onAddCategory = { name ->
                                                    viewModel.addCategory(name)
                                                },
                                                autoNightModeVal = autoNightMode,
                                                onAutoNightModeChange = { viewModel.updateAutoNightMode(it) },
                                                blueLightFilterVal = blueLightFilter,
                                                onBlueLightFilterChange = { viewModel.updateBlueLightFilter(it) },
                                                blueLightAlphaVal = blueLightAlpha,
                                                onBlueLightAlphaChange = { viewModel.updateBlueLightAlpha(it) },
                                                colorPrimaryIndexVal = colorPrimaryIndex,
                                                colorSecondaryIndexVal = colorSecondaryIndex,
                                                onColorThemeChange = { p, s -> viewModel.updateColorTheme(p, s) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        composable("settings") {
                            val categories by viewModel.allCategories.collectAsState()
                            SettingsTabScreen(
                                prefs = viewModel.prefs,
                                backupManager = viewModel.backupManager,
                                categories = categories,
                                onAddCategory = { name ->
                                    viewModel.addCategory(name)
                                },
                                onBack = {
                                    navController.popBackStack()
                                },
                                autoNightModeVal = autoNightMode,
                                onAutoNightModeChange = { viewModel.updateAutoNightMode(it) },
                                blueLightFilterVal = blueLightFilter,
                                onBlueLightFilterChange = { viewModel.updateBlueLightFilter(it) },
                                blueLightAlphaVal = blueLightAlpha,
                                onBlueLightAlphaChange = { viewModel.updateBlueLightAlpha(it) },
                                colorPrimaryIndexVal = colorPrimaryIndex,
                                colorSecondaryIndexVal = colorSecondaryIndex,
                                onColorThemeChange = { p, s -> viewModel.updateColorTheme(p, s) }
                            )
                        }

                        composable("reader") {
                            val selectedBook by viewModel.selectedBook.collectAsState()
                            val chapters by viewModel.chapters.collectAsState()
                            val bookmarks by viewModel.bookmarks.collectAsState()
                            val highlights by viewModel.highlights.collectAsState()
                            val searchResults by viewModel.searchResults.collectAsState()
                            val isSearching by viewModel.isSearching.collectAsState()

                            ReaderScreen(
                                book = selectedBook,
                                bookTitle = selectedBook?.title ?: "本地阅读",
                                chapters = chapters,
                                onBack = { navController.popBackStack() },
                                onUpdateProgress = { id, chapterIdx, offset, isFinished ->
                                    viewModel.updateProgress(id, chapterIdx, offset, isFinished)
                                },
                                prefs = viewModel.prefs,
                                ttsManager = viewModel.ttsManager,
                                highlights = highlights,
                                bookmarks = bookmarks,
                                onAddBookmark = { bookId, chIdx, offset, title, snippet ->
                                    viewModel.addBookmark(bookId, chIdx, offset, title, snippet)
                                    com.example.ui.mascot.MascotAnimationController.play(com.example.ui.mascot.MascotEvent.AddBookmark)
                                },
                                onDeleteBookmark = { id ->
                                    viewModel.deleteBookmark(id)
                                },
                                onAddHighlight = { bookId, chIdx, text, note, color ->
                                    viewModel.addHighlight(bookId, chIdx, text, note, color)
                                },
                                onDeleteHighlight = { id ->
                                    viewModel.deleteHighlight(id)
                                },
                                searchResults = searchResults,
                                isSearching = isSearching,
                                onSearch = { query ->
                                    viewModel.searchFullText(query)
                                },
                                onRecordTime = { seconds ->
                                    viewModel.recordTime(seconds)
                                },
                            )
                        }

                        composable("comic_reader") {
                            val selectedBook by viewModel.selectedBook.collectAsState()
                            val chapters by viewModel.chapters.collectAsState()

                            ComicReaderScreen(
                                book = selectedBook,
                                chapters = chapters,
                                onBack = { navController.popBackStack() },
                                onUpdateProgress = { id, pageIdx, offset, isFinished ->
                                    viewModel.updateProgress(id, pageIdx, offset, isFinished)
                                },
                                onRecordTime = { seconds ->
                                    viewModel.recordTime(seconds)
                                }
                            )
                        }
                    }
                    com.example.ui.mascot.MascotOverlay()
                }
            }
        }
    }
}
