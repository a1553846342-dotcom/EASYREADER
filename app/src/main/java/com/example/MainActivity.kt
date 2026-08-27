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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import dev.liquidglass.compose.liquidGlassProvider
import dev.liquidglass.compose.rememberLiquidGlassProviderState
import com.example.ui.components.LocalLiquidGlassState
import com.example.ui.components.LocalGlassBackdrop
import com.example.ui.components.CardTweaks
import com.example.ui.components.LocalCardTweaks
import com.example.ui.components.readCardTweaks
import com.example.ui.components.AppBottomTabBar
import com.example.ui.components.AppTabItem
import com.example.ui.components.rememberTabBarCollapseState
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import com.kashif_e.backdrop.backdrops.layerBackdrop
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.example.ui.*
import com.example.ui.theme.MintPrimary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.luminance
import coil.compose.rememberAsyncImagePainter

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
class MainActivity : ComponentActivity() {
    private var mainViewModel: MainViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 启动看门狗：若"极致"画质在 20 秒内连续两次发生崩溃，自动降回"高"，
        // 防止实验性着色器效果导致"一崩就再也打不开"的死循环变砖。
        runCatching {
            val boot = getSharedPreferences("novel_reader_prefs", MODE_PRIVATE)
            if (boot.getInt("render_quality", 2) == 3) {
                val now = System.currentTimeMillis()
                val last = boot.getLong("boot_guard_last", 0L)
                var cnt = boot.getInt("boot_guard_cnt", 0)
                cnt = if (last > 0 && now - last < 20_000L) cnt + 1 else 1
                boot.edit().putLong("boot_guard_last", now).putInt("boot_guard_cnt", cnt).apply()
                if (cnt >= 2) {
                    boot.edit()
                        .putInt("render_quality", 2)
                        .putInt("boot_guard_cnt", 0)
                        .putLong("boot_guard_last", 0L)
                        .apply()
                } else {
                    // 存活满 20 秒即解除武装（正常使用不计入）
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        boot.edit().putLong("boot_guard_last", 0L).apply()
                    }, 20_000L)
                }
            }
        }
        com.example.library.ZLibraryNodeManager.restoreSelection(applicationContext)
        // 卡片微调 v2 默认档：一次性迁移（幂等）必须在 setContent 之前完成，
        // 避免在组合期执行持久化副作用
        com.example.data.PreferencesManager(this).migrateCardTweaksDefaultsV2()
        // 冷启动兜底：清空书架分享的"用完即焚"临时文件，防止异常残留堆积
        Thread {
            com.example.library.BookShareHelper.cleanupTempShareDir(applicationContext)
        }.start()
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
                        keyword = intent?.getStringExtra("smoke_keyword") ?: "",
                        smokeUser = intent?.getStringExtra("smoke_user"),
                        smokePassword = intent?.getStringExtra("smoke_pass")
                    )
                }
            }

            val autoNightMode by viewModel.autoNightMode.collectAsState()
            val blueLightFilter by viewModel.blueLightFilter.collectAsState()
            val blueLightAlpha by viewModel.blueLightAlpha.collectAsState()
            val colorPrimaryIndex by viewModel.colorPrimaryIndex.collectAsState()
            val colorSecondaryIndex by viewModel.colorSecondaryIndex.collectAsState()
            val orientationLock by viewModel.screenOrientationLock.collectAsState()

            // 卡片微调：设置页「自定义卡片参数」实时写入的共享状态，注入所有 GlassCard
            // （v2 默认档迁移已在 onCreate 中完成）
            val cardTweaks = remember { mutableStateOf(viewModel.prefs.readCardTweaks()) }
            // 滚动惯性倾斜：全 App 单一信号源（任务书「整卡倾斜」§2）
            val scrollTilt = remember { com.example.ui.components.ScrollTiltController() }

            MyApplicationTheme(
                darkTheme = autoNightMode,
                colorPrimaryIndex = colorPrimaryIndex,
                colorSecondaryIndex = colorSecondaryIndex
            ) {
                val liquidGlass = rememberLiquidGlassProviderState()
                CompositionLocalProvider(
                    LocalLiquidGlassState provides liquidGlass,
                    com.example.ui.components.LocalScrollTilt provides scrollTilt
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .liquidGlassProvider(liquidGlass)
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
                        val bgConfig by AppBackgroundController.config.collectAsState()
                        val bgActive = bgConfig.mode == 1 && !bgConfig.uri.isNullOrBlank()
                        // 渲染画质档位：设置页可调，主要影响玻璃效果强度与动效数量。
                        // "高"为默认，与历史版本视觉完全一致；低于"高"不挂 backdrop 捕获层。
                        val renderQualityIdx by viewModel.renderQuality.collectAsState()
                        val renderQuality = com.example.ui.components.RenderQuality.of(renderQualityIdx)
                        val glassEnabled = renderQuality.realtimeGlass
                        // 滚动惯性倾斜帧循环：仅 MAX 档挂载（该档本就常驻极光等无限动效；
                        // 低档不再引入常驻 ticker，保住「流畅」档的帧空闲与省电）
                        if (renderQuality == com.example.ui.components.RenderQuality.MAX) {
                            com.example.ui.components.ScrollTiltHost(scrollTilt)
                        } else {
                            scrollTilt.reset()
                        }
                        // 页面有效背景平均亮度（已含遮罩）：标题文字据此实时取对比色
                        val bgTone: Float = if (bgActive) {
                            val appContext = LocalContext.current.applicationContext
                            val loaded by produceState(0.5f, bgConfig.uri, bgConfig.dim) {
                                value = loadBackgroundAvgLuminance(
                                    appContext,
                                    bgConfig.uri.orEmpty(),
                                    bgConfig.dim
                                )
                            }
                            loaded
                        } else {
                            MaterialTheme.colorScheme.background.luminance()
                        }
                        // 玻璃采样源：把背景层挂上 layerBackdrop（与底部 Tab 栏同机制的真实内容采样），
                        // 页面内容卡（GlassCard）据此复用 Tab 栏同一套 KMPLiquidGlass 模糊实现。
                        // 注：曾试验"整屏预烘焙模糊位图"方案（PreBlurredBackdrop），实机上导致玻璃
                        // 效果异常（疑似该机型快照管线不应用链式 RenderEffect / 背景图异步加载竞态），
                        // 已回退实时模糊路径；相关代码保留在 backdrop 库中但不再接线。
                        val bgBackdrop = rememberLayerBackdrop()
                        Box(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                                    .then(
                                        if (glassEnabled) Modifier.layerBackdrop(bgBackdrop) else Modifier
                                    )
                            ) {
                                if (bgActive) {
                                    Image(
                                        painter = rememberAsyncImagePainter(bgConfig.uri),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (bgConfig.dim > 0) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = bgConfig.dim / 100f))
                                        )
                                    }
                                }
                            }
                            CompositionLocalProvider(
                                LocalAppBackgroundActive provides bgActive,
                                LocalBackgroundTone provides bgTone,
                                LocalGlassBackdrop provides (bgBackdrop.takeIf { glassEnabled }),
                                com.example.ui.components.LocalRenderQuality provides renderQuality,
                                LocalCardTweaks provides cardTweaks.value
                            ) {
                        val navController = rememberNavController()
                        // 启动时用持久化配置初始化背景（设置页改动会通过 AppBackgroundController 实时更新）
                        LaunchedEffect(Unit) {
                            AppBackgroundController.update(
                                viewModel.prefs.appBackgroundMode,
                                viewModel.prefs.customAppBackgroundUri,
                                viewModel.prefs.appBackgroundDim
                            )
                        }

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
                            val readingSessions by viewModel.allReadingSessions.collectAsState()
                            val tabBarCollapseState = rememberTabBarCollapseState()
                            // Tab 栏专用背景采样（书源选择弹窗同款手法）：
                            // layerBackdrop 捕获页面真实内容，Tab 栏 drawBackdrop 模糊它。
                            // 画质档位低于"高"时不捕获（底栏走半透明底），滚动零捕获开销。
                            val tabBackdrop = rememberLayerBackdrop()
                            // 底栏玻璃只采样底部条带，捕获层裁剪到该区域（topLeft 保持坐标系不变），
                            // 滚动时不再对整页内容做全屏重录与重栅格化。
                            // 高度覆盖底栏最大高度(68dp)+导航栏内缩+模糊/阴影外扩，取 150dp 富余。
                            val stripDensity = LocalDensity.current
                            SideEffect {
                                if (renderQuality.realtimeGlass) {
                                    tabBackdrop.captureStripHeightPx = with(stripDensity) { 150.dp.toPx().toInt() }
                                }
                            }

                            val tabItems = remember {
                                listOf(
                                    AppTabItem("书库", Icons.Outlined.Search, Icons.Filled.Search),
                                    AppTabItem("书架", Icons.Outlined.BookmarkBorder, Icons.Filled.Bookmark),
                                    AppTabItem("统计", Icons.Outlined.BarChart, Icons.Filled.BarChart),
                                    AppTabItem("设置", Icons.Outlined.Settings, Icons.Filled.Settings)
                                )
                            }

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

                            Box(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (renderQuality.realtimeGlass) Modifier.layerBackdrop(tabBackdrop)
                                        else Modifier
                                    )
                            ) {
                            Scaffold(
                                containerColor = if (LocalAppBackgroundActive.current) {
                                    Color.Transparent
                                } else {
                                    MaterialTheme.colorScheme.background
                                },
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
                            ) { innerPadding ->
                                // 内容区向下延伸到屏幕底部（底栏后方），滚动时直接没入底栏，
                                // 底栏上方不再存在任何固定不动的空白带。
                                Box(
                                    modifier = Modifier
                                        .padding(top = innerPadding.calculateTopPadding())
                                        .nestedScroll(tabBarCollapseState.connection())
                                ) {
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
                                                    extraBottomPadding = 96.dp
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
                                                onDeleteCategory = { category ->
                                                    viewModel.deleteCategory(category)
                                                },
                                            )
                                            2 -> {
                                                var dailyGoalState by remember { mutableIntStateOf(viewModel.prefs.dailyGoalMinutes) }
                                                StatisticsScreen(
                                                    books = books,
                                                    totalReadTimeSecondsFlow = viewModel.totalReadTimeSeconds,
                                                    readingRecords = readingRecords,
                                                    readingSessions = readingSessions,
                                                    dailyGoalMinutes = dailyGoalState,
                                                    onGoalChange = {
                                                        viewModel.prefs.dailyGoalMinutes = it
                                                        dailyGoalState = it  // 触发重组刷新目标环
                                                    },
                                                onGoToShelf = { selectedTab = 1 },
                                                onDeleteRecord = { viewModel.deleteReadingRecord(it.id) },
                                                recordCovers = libraryViewModel.recordCovers.collectAsState().value,
                                                recordBooks = libraryViewModel.recordBooks.collectAsState().value,
                                                onResolveRecordCovers = { libraryViewModel.resolveMissingRecordCovers(it) },
                                                onOpenRecordDetail = { book ->
                                                    libraryViewModel.openComic(book)
                                                    navController.navigate("comic_chapters")
                                                },
                                                onOpenBook = { book ->
                                                    viewModel.selectBook(book)
                                                    if (book.isComic) {
                                                        navController.navigate("comic_reader")
                                                    } else {
                                                        navController.navigate("reader")
                                                    }
                                                }
                                                )
                                            }
                                            3 -> SettingsTabScreen(
                                                    prefs = viewModel.prefs,
                                                    backupManager = viewModel.backupManager,
                                                    categories = categories,
                                                    extraBottomPadding = 96.dp,
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
                                                onColorThemeChange = { p, s -> viewModel.updateColorTheme(p, s) },
                                                orientationLockVal = orientationLock,
                                                onOrientationLockChange = { viewModel.updateScreenOrientationLock(it) },
                                                renderQualityVal = renderQualityIdx,
                                                onRenderQualityChange = { viewModel.updateRenderQuality(it) },
                                                cardTweaksState = cardTweaks
                                            )
                                        }
                                    }
                                }
                            }
                            }
                            AppBottomTabBar(
                                items = tabItems,
                                selectedIndex = selectedTab,
                                onTabSelected = { selectedTab = it },
                                collapseState = tabBarCollapseState,
                                backdrop = tabBackdrop.takeIf { renderQuality.realtimeGlass },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
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
                                onColorThemeChange = { p, s -> viewModel.updateColorTheme(p, s) },
                                orientationLockVal = orientationLock,
                                onOrientationLockChange = { viewModel.updateScreenOrientationLock(it) },
                                renderQualityVal = renderQualityIdx,
                                onRenderQualityChange = { viewModel.updateRenderQuality(it) },
                                cardTweaksState = cardTweaks
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
                                onSessionEnd = { session ->
                                    viewModel.addReadingSession(session)
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
                                },
                                onSessionEnd = { session ->
                                    viewModel.addReadingSession(session)
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
                                onDownloadAll = {
                                    comicChapters.forEach { chapter ->
                                        comicBook?.let { libraryViewModel.downloadComicChapter(it, chapter) }
                                    }
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
                                onRecordTime = { seconds ->
                                    viewModel.recordTime(seconds, comicBook?.title ?: activeChapter?.title)
                                },
                                onSessionEnd = { session ->
                                    viewModel.addReadingSession(session)
                                },
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
}
}
}
