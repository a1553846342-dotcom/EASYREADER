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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.example.ui.*
import com.example.ui.theme.MintPrimary
import com.example.ui.theme.MyApplicationTheme

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
class MainActivity : ComponentActivity() {
    private var mainViewModel: MainViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.library.ZLibraryNodeManager.restoreSelection(applicationContext)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val libraryViewModel: com.example.library.LibraryViewModel = viewModel()
            val sourceViewModel = remember(libraryViewModel) {
                com.example.source.SourceViewModel(
                    application = application,
                    sourceManager = libraryViewModel.sourceManager
                )
            }
            mainViewModel = viewModel

            // adb 触发逐源冒烟测试：adb shell am start -n com.aistudio.novelreader.kxmpzq/.MainActivity --ez smoke_test true
            LaunchedEffect(Unit) {
                if (intent?.getBooleanExtra("smoke_test", false) == true) {
                    libraryViewModel.runSourceSmokeTest(
                        filter = intent?.getStringExtra("smoke_source") ?: "",
                        keyword = intent?.getStringExtra("smoke_keyword") ?: ""
                    )
                }
            }

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

                    var selectedTab by rememberSaveable { mutableIntStateOf(1) }

                    SharedTransitionLayout { CompositionLocalProvider(LocalSharedTransitionScope provides this) { NavHost(
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
                                    val nextDest = if (viewModel.prefs.hasSeenOnboarding) "home" else "onboarding"
                                    navController.navigate(nextDest) {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("onboarding") {
                            com.example.ui.OnboardingScreen(
                                onFinished = {
                                    viewModel.prefs.hasSeenOnboarding = true
                                    navController.navigate("home") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("home") { CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            val books by viewModel.allBooks.collectAsState()
                            val categories by viewModel.allCategories.collectAsState()
                            val readingRecords by viewModel.allReadingRecords.collectAsState()

                            val libraryErrorMessage by libraryViewModel.errorMessage.collectAsState()
                            val mainImportMessage by viewModel.importStatusMessage.collectAsState()
                            val snackbarHostState = remember { SnackbarHostState() }

                            LaunchedEffect(libraryErrorMessage) {
                                libraryErrorMessage?.let {
                                    snackbarHostState.showSnackbar(
                                        message = it,
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }

                            LaunchedEffect(mainImportMessage) {
                                mainImportMessage?.let {
                                    if (it.contains("失败") || it.contains("出错")) {
                                        snackbarHostState.showSnackbar(
                                            message = it,
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            }

                            Scaffold(
                                snackbarHost = {
                                    SnackbarHost(
                                        hostState = snackbarHostState,
                                        modifier = Modifier.padding(bottom = 60.dp)
                                    ) { data ->
                                        com.example.ui.components.AppErrorSnackbar(
                                            message = data.visuals.message,
                                            onDismissClick = { data.dismiss() }
                                        )
                                    }
                                },
                                bottomBar = {
                                    // 标准 Material 不透明底栏，恢复与其他界面的正常观感
                                    NavigationBar(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ) {
                                        NavigationBarItem(
                                            selected = selectedTab == 0,
                                            onClick = { selectedTab = 0 },
                                            icon = { Icon(Icons.Filled.Search, contentDescription = "书库") },
                                            label = { Text("书库") },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MintPrimary,
                                                selectedTextColor = MintPrimary,
                                                indicatorColor = MintPrimary.copy(alpha = 0.15f)
                                            )
                                        )
                                        NavigationBarItem(
                                            selected = selectedTab == 1,
                                            onClick = { selectedTab = 1 },
                                            icon = { Icon(Icons.Filled.Book, contentDescription = "书架") },
                                            label = { Text("书架") },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MintPrimary,
                                                selectedTextColor = MintPrimary,
                                                indicatorColor = MintPrimary.copy(alpha = 0.15f)
                                            )
                                        )
                                        NavigationBarItem(
                                            selected = selectedTab == 2,
                                            onClick = { selectedTab = 2 },
                                            icon = { Icon(Icons.Filled.BarChart, contentDescription = "统计") },
                                            label = { Text("统计") },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MintPrimary,
                                                selectedTextColor = MintPrimary,
                                                indicatorColor = MintPrimary.copy(alpha = 0.15f)
                                            )
                                        )
                                        NavigationBarItem(
                                            selected = selectedTab == 3,
                                            onClick = { selectedTab = 3 },
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
                                // 内容区向下延伸到屏幕底部（底栏后方），滚动时直接没入底栏，
                                // 底栏上方不再存在任何固定不动的空白带。
                                Box(modifier = Modifier.padding(top = innerPadding.calculateTopPadding())) {
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
                                                0 -> com.example.library.LibraryScreen(
                                                    viewModel = libraryViewModel,
                                                    onBookImported = { 
                                                        selectedTab = 1 
                                                    },
                                                    onOpenSourceManagement = {
                                                        navController.navigate("source_management")
                                                    },
                                                    onImportLocalBook = {
                                                        selectedCategoryForImport = "全部"
                                                        fileLauncher.launch("*/*")
                                                    },
                                                    onOpenComic = { book ->
                                                        libraryViewModel.openComic(book)
                                                        navController.navigate("comic_chapters")
                                                    },
                                                    extraBottomPadding = innerPadding.calculateBottomPadding()
                                                )
                                            1 -> HomeScreen(
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
                                                totalReadTimeSecondsFlow = viewModel.totalReadTimeSeconds,
                                                streakDaysFlow = viewModel.streakDays,
                                                onDeleteBook = { book ->
                                                    viewModel.deleteBook(book)
                                                    com.example.ui.mascot.MascotAnimationController.play(com.example.ui.mascot.MascotEvent.DeleteBook)
                                                },
                                                onMoveBook = { book, newCategory ->
                                                    viewModel.moveBookToCategory(book, newCategory)
                                                    com.example.ui.mascot.MascotAnimationController.play(com.example.ui.mascot.MascotEvent.MoveBook)
                                                },
                                            )
                                            2 -> StatisticsScreen(
                                                books = books,
                                                totalReadTimeSecondsFlow = viewModel.totalReadTimeSeconds,
                                                readingRecords = readingRecords,
                                                onGoToShelf = { selectedTab = 1 }
                                            )
                                                3 -> SettingsTabScreen(
                                                    prefs = viewModel.prefs,
                                                    backupManager = viewModel.backupManager,
                                                    categories = categories,
                                                    extraBottomPadding = innerPadding.calculateBottomPadding(),
                                                    onAdultSourcesChange = { sourceViewModel.setAdultSourcesEnabled(it) },
                                                    onAddCategory = { name ->
                                                        viewModel.addCategory(name)
                                                    },
                                                onOpenSourceManager = {
                                                    navController.navigate("source_management")
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
 }
                        composable("settings") {
                            val categories by viewModel.allCategories.collectAsState()
                            SettingsTabScreen(
                                onOpenSourceManager = {
                                    navController.navigate("source_management")
                                },
                                prefs = viewModel.prefs,
                                backupManager = viewModel.backupManager,
                                categories = categories,
                                onAdultSourcesChange = { sourceViewModel.setAdultSourcesEnabled(it) },
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

                        composable(
                            "reader",
                            enterTransition = {
                                EnterTransition.None
                            },
                            exitTransition = { fadeOut(tween(220)) },
                            popEnterTransition = {
                                fadeIn(tween(240))
                            },
                            popExitTransition = {
                                fadeOut(tween(220)) +
                                    scaleOut(
                                        targetScale = 0.96f,
                                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                                    )
                            }
                        ) { CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
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
 }
                        composable(
                            "comic_reader",
                            enterTransition = {
                                fadeIn(tween(320)) +
                                    scaleIn(
                                        initialScale = 0.96f,
                                        animationSpec = tween(320, easing = FastOutSlowInEasing)
                                    )
                            },
                            exitTransition = { fadeOut(tween(220)) },
                            popEnterTransition = {
                                fadeIn(tween(300)) +
                                    scaleIn(
                                        initialScale = 0.98f,
                                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                                    )
                            },
                            popExitTransition = {
                                fadeOut(tween(220)) +
                                    scaleOut(
                                        targetScale = 0.96f,
                                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                                    )
                            }
                        ) { CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
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
                        } }

                        composable(
                            "comic_chapters",
                            enterTransition = {
                                fadeIn(tween(280)) + slideInHorizontally { it / 4 }
                            },
                            exitTransition = { fadeOut(tween(200)) },
                            popEnterTransition = { fadeIn(tween(260)) },
                            popExitTransition = { fadeOut(tween(200)) }
                        ) { CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            val comicBook by libraryViewModel.comicBook.collectAsState()
                            val comicChapters by libraryViewModel.comicChapters.collectAsState()
                            val comicChaptersLoading by libraryViewModel.comicChaptersLoading.collectAsState()
                            val comicChaptersError by libraryViewModel.comicChaptersError.collectAsState()
                            val comicDownloading by libraryViewModel.comicDownloading.collectAsState()
                            val comicDownloadProgress by libraryViewModel.comicDownloadProgress.collectAsState()
                            val comicPaused by libraryViewModel.comicPaused.collectAsState()
                            val comicMessage by libraryViewModel.comicMessage.collectAsState()
                            val comicContext = androidx.compose.ui.platform.LocalContext.current

                            LaunchedEffect(comicMessage) {
                                comicMessage?.let {
                                    android.widget.Toast.makeText(comicContext, it, android.widget.Toast.LENGTH_LONG).show()
                                    libraryViewModel.clearComicMessage()
                                }
                            }

                            ComicChaptersScreen(
                                book = comicBook,
                                chapters = comicChapters,
                                loading = comicChaptersLoading,
                                error = comicChaptersError,
                                downloadingChapters = comicDownloading,
                                downloadProgress = comicDownloadProgress,
                                pausedChapters = comicPaused,
                                onBack = { navController.popBackStack() },
                                onRetry = { comicBook?.let { libraryViewModel.openComic(it) } },
                                onChapterClick = { chapter ->
                                    libraryViewModel.loadChapterImages(chapter)
                                    navController.navigate("comic_reader_online")
                                },
                                onDownloadChapter = { chapter ->
                                    comicBook?.let { libraryViewModel.downloadComicChapter(it, chapter) }
                                },
                                onPauseDownload = { chapter ->
                                    libraryViewModel.pauseComicChapter(chapter.id)
                                },
                                onResumeDownload = { chapter ->
                                    comicBook?.let { libraryViewModel.downloadComicChapter(it, chapter) }
                                },
                                onCancelDownload = { chapter ->
                                    libraryViewModel.cancelComicChapter(chapter.id)
                                }
                            )
                        } }

                        composable(
                            "comic_reader_online",
                            enterTransition = {
                                fadeIn(tween(300)) +
                                    scaleIn(
                                        initialScale = 0.96f,
                                        animationSpec = tween(300)
                                    )
                            },
                            exitTransition = { fadeOut(tween(220)) },
                            popEnterTransition = { fadeIn(tween(280)) },
                            popExitTransition = { fadeOut(tween(220)) }
                        ) { CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            val comicBook by libraryViewModel.comicBook.collectAsState()
                            val activeChapter by libraryViewModel.activeComicChapter.collectAsState()
                            val images by libraryViewModel.comicChapterImages.collectAsState()
                            val imageHeaders by libraryViewModel.comicChapterHeaders.collectAsState()
                            val loading by libraryViewModel.comicChapterLoading.collectAsState()
                            val error by libraryViewModel.comicChapterError.collectAsState()

                            OnlineComicReaderScreen(
                                title = activeChapter?.title ?: comicBook?.title ?: "在线漫画",
                                imageUrls = images,
                                loading = loading,
                                error = error,
                                referer = if (comicBook?.sourceId == "mangadex") "https://mangadex.live/" else null,
                                imageHeaders = imageHeaders,
                                resolveImage = { url -> libraryViewModel.resolveComicImage(url) },
                                resolveImageHeaders = { url -> libraryViewModel.resolveComicImageHeaders(url) },
                                onBack = { navController.popBackStack() },
                                onRetry = { activeChapter?.let { libraryViewModel.loadChapterImages(it) } }
                            )
                        } }

                        composable("source_management") {
                            com.example.ui.source.SourceManagementScreen(
                                viewModel = sourceViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                    }
                }
            }
            com.example.ui.mascot.MascotOverlay()
        }
    }
}
}
}
