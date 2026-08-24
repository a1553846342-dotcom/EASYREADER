package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.BackupManager
import com.example.data.CategoryEntity
import com.example.data.PreferencesManager
import com.example.ui.pageturn.PageTurnType
import com.example.ui.components.AppButton
import com.example.ui.components.AppActionButton
import com.example.ui.components.AppButtonSize
import com.example.ui.components.AppButtonVariant
import com.example.ui.components.ColorMorphSwatch
import com.example.ui.components.AppLiquidButton
import com.example.ui.components.AppLiquidSwitch
import com.example.ui.components.SegmentedPillSelector
import com.example.ui.components.DialogLiquidGlass
import com.example.ui.components.GlassCard
import com.example.ui.components.LocalRenderQuality
import com.example.ui.components.MaxJunoSlider
import com.example.ui.components.RenderQuality
import com.example.ui.components.SegmentedPillSelector
import com.example.ui.components.PageTurnSelectorRow
import com.example.ui.components.CustomMinutesDialog
import com.example.ui.components.JunoSlider
import com.example.ui.components.AppIconButton
import com.example.ui.theme.MintPrimary
import com.example.ui.theme.MintSecondary
import com.example.ui.theme.clickableWithFeedback
import com.example.ui.theme.onColor
import com.example.ui.theme.glassTitleColor
import com.example.ui.help.LibraryHelpBottomSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTabScreen(
    prefs: PreferencesManager,
    backupManager: BackupManager,
    categories: List<CategoryEntity>,
    onAddCategory: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    onOpenSourceManager: (() -> Unit)? = null,
    extraBottomPadding: Dp = 0.dp,
    autoNightModeVal: Boolean = prefs.autoNightMode,
    onAutoNightModeChange: (Boolean) -> Unit = { prefs.autoNightMode = it },
    blueLightFilterVal: Boolean = prefs.blueLightFilter,
    onBlueLightFilterChange: (Boolean) -> Unit = { prefs.blueLightFilter = it },
    blueLightAlphaVal: Float = prefs.blueLightAlpha,
    onBlueLightAlphaChange: (Float) -> Unit = { prefs.blueLightAlpha = it },
    colorPrimaryIndexVal: Int = prefs.colorPrimaryIndex,
    colorSecondaryIndexVal: Int = prefs.colorSecondaryIndex,
    onColorThemeChange: (Int, Int) -> Unit = { p, s -> prefs.colorPrimaryIndex = p; prefs.colorSecondaryIndex = s },
    onAdultSourcesChange: (Boolean) -> Unit = {},
    orientationLockVal: Int = prefs.screenOrientationLock,
    onOrientationLockChange: (Int) -> Unit = { prefs.screenOrientationLock = it },
    renderQualityVal: Int = prefs.renderQuality,
    onRenderQualityChange: (Int) -> Unit = { prefs.renderQuality = it }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var restReminderMinutes by remember { mutableStateOf(prefs.restReminderMinutes) }
    var showCustomMinutes by remember { mutableStateOf(false) }
    var autoNightMode by remember(autoNightModeVal) { mutableStateOf(autoNightModeVal) }
    var blueLightFilter by remember(blueLightFilterVal) { mutableStateOf(blueLightFilterVal) }
    var blueLightAlpha by remember(blueLightAlphaVal) { mutableStateOf(blueLightAlphaVal) }
    var pageTurnMode by remember { mutableStateOf(prefs.pageTurnMode) }
    var colorPrimaryIndex by remember(colorPrimaryIndexVal) { mutableStateOf(colorPrimaryIndexVal) }
    var colorSecondaryIndex by remember(colorSecondaryIndexVal) { mutableStateOf(colorSecondaryIndexVal) }
    var liveButtonTapCount by remember { mutableStateOf(0) }
    var liveButtonLastTapMs by remember { mutableStateOf(0L) }
    var showAdultSourceCard by remember { mutableStateOf(false) }
    var showAdultSources by remember { mutableStateOf(prefs.showAdultSources) }

    var splashPosterUri by remember { mutableStateOf(prefs.customSplashPosterUri) }
    var splashPureMode by remember { mutableStateOf(prefs.splashPureMode) }
    var appBgMode by remember { mutableIntStateOf(prefs.appBackgroundMode) }
    var appBgUri by remember { mutableStateOf(prefs.customAppBackgroundUri) }
    var appBgDim by remember { mutableIntStateOf(prefs.appBackgroundDim) }

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryText by remember { mutableStateOf("") }
    var showHelpBottomSheet by remember { mutableStateOf(false) }

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
                splashPosterUri = localUriStr
                prefs.customSplashPosterUri = localUriStr
                Toast.makeText(context, "海报已设置", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "图片设置失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 软件背景：选择图片后复制到私有目录（横竖屏统一 Crop 填充，不拉伸变形）
    val bgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val file = java.io.File(context.filesDir, "custom_app_bg.jpg")
                inputStream?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                val localUriStr = Uri.fromFile(file).toString()
                appBgUri = localUriStr
                prefs.customAppBackgroundUri = localUriStr
                prefs.appBackgroundMode = 1
                appBgMode = 1
                AppBackgroundController.update(1, localUriStr, appBgDim)
                Toast.makeText(context, "背景已设置（横竖屏自动裁剪填充）", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "背景设置失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (LocalAppBackgroundActive.current) Color.Transparent
                else MaterialTheme.colorScheme.background
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                // 仿书架页顶部栏：毛玻璃卡 + Serif 标题层级（HomeScreen 顶部栏同款）
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onBack != null) {
                            AppIconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "设置",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = glassTitleColor(),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "SETTINGS & PREFERENCES",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = glassTitleColor().copy(alpha = 0.75f),
                                letterSpacing = 1.5.sp
                            )
                        }
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + extraBottomPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {


                // Section 1: Splash Poster
                item {
                    SettingsSectionHeader("开屏海报设置")
                }

                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("自定义开屏海报", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(
                                        if (splashPosterUri.isNullOrEmpty()) "从手机相册选择启动页海报" else "已更换自定义相册海报",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row {
                                    if (!splashPosterUri.isNullOrEmpty()) {
                                        TextButton(
                                            onClick = {
                                                splashPosterUri = null
                                                prefs.customSplashPosterUri = null
                                                Toast.makeText(context, "已还原默认海报", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.padding(end = 4.dp)
                                        ) {
                                            Text("清除")
                                        }
                                    }
                                    AppActionButton(
                                        text = "选择相册图片",
                                        onClick = { posterLauncher.launch("image/*") },
                                        variant = AppButtonVariant.Primary,
                                        buttonSize = AppButtonSize.Small
                                    )
                                }
                            }

                            if (!splashPosterUri.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = splashPosterUri,
                                        contentDescription = "海报预览",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("纯净模式", fontWeight = FontWeight.SemiBold)
                                AppLiquidSwitch(
                                    checked = splashPureMode,
                                    onCheckedChange = {
                                        splashPureMode = it
                                        prefs.splashPureMode = it
                                    }
                                )
                            }
                        }
                    }
                }

                // Section: 软件背景
                item {
                    SettingsSectionHeader("软件背景设置")
                }

                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("软件背景", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                "选择默认主题色，或自定义一张背景图（横竖屏自动裁剪铺满）",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SegmentedPillSelector(
                                options = listOf(0 to "默认", 1 to "自定义"),
                                selected = appBgMode,
                                onSelect = { mode ->
                                    appBgMode = mode
                                    prefs.appBackgroundMode = mode
                                    AppBackgroundController.update(
                                        mode,
                                        if (mode == 1) appBgUri else null,
                                        if (mode == 1) appBgDim else 0
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (appBgMode == 1) {
                                Spacer(modifier = Modifier.height(12.dp))
                                if (!appBgUri.isNullOrEmpty()) {
                                    // 预览模拟卡：文字颜色实时按卡片底色取对比色
                                    val previewCardColor = Color.White.copy(alpha = 0.92f)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(160.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                    ) {
                                        // 实时预览：背景图 + 遮罩 + 模拟内容卡片，直观感受可读性
                                        AsyncImage(
                                            model = appBgUri,
                                            contentDescription = "背景预览",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = appBgDim / 100f))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .fillMaxWidth()
                                                .padding(20.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(previewCardColor)
                                                .padding(12.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    "预览",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = previewCardColor.onColor()
                                                )
                                                Text(
                                                    "背景遮罩 ${appBgDim}% · 文字与卡片保持清晰",
                                                    fontSize = 11.sp,
                                                    color = previewCardColor.onColor().copy(alpha = 0.65f)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("背景遮罩强度", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Spacer(modifier = Modifier.weight(1f))
                                        Text("$appBgDim%", fontSize = 12.sp, color = MintPrimary, fontWeight = FontWeight.Bold)
                                    }
                                    if (LocalRenderQuality.current == RenderQuality.MAX) {
                                        MaxJunoSlider(
                                            value = appBgDim / 50f,
                                            onValueChange = {
                                                appBgDim = (it * 50).toInt().coerceIn(0, 50)
                                                prefs.appBackgroundDim = appBgDim
                                                if (appBgMode == 1 && !appBgUri.isNullOrEmpty()) {
                                                    AppBackgroundController.update(1, appBgUri, appBgDim)
                                                }
                                            }
                                        )
                                    } else {
                                        JunoSlider(
                                            value = appBgDim / 50f,
                                            onValueChange = {
                                                appBgDim = (it * 50).toInt().coerceIn(0, 50)
                                                prefs.appBackgroundDim = appBgDim
                                                if (appBgMode == 1 && !appBgUri.isNullOrEmpty()) {
                                                    AppBackgroundController.update(1, appBgUri, appBgDim)
                                                }
                                            }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    AppActionButton(
                                        text = if (appBgUri.isNullOrEmpty()) "选择背景图片" else "更换图片",
                                        onClick = { bgLauncher.launch("image/*") },
                                        variant = AppButtonVariant.Primary,
                                        buttonSize = AppButtonSize.Small,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (!appBgUri.isNullOrEmpty()) {
                                        Spacer(modifier = Modifier.width(10.dp))
                                        AppActionButton(
                                            text = "恢复默认",
                                            onClick = {
                                                appBgUri = null
                                                prefs.customAppBackgroundUri = null
                                                prefs.appBackgroundMode = 0
                                                appBgMode = 0
                                                AppBackgroundController.update(0, null, appBgDim)
                                                Toast.makeText(context, "已恢复默认背景", Toast.LENGTH_SHORT).show()
                                            },
                                            variant = AppButtonVariant.Secondary,
                                            buttonSize = AppButtonSize.Small,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Section: 外观与主题自定义
                item {
                    SettingsSectionHeader("外观与主题")
                }

                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Palette, contentDescription = null, tint = MintPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("主题配色方案", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Spacer(modifier = Modifier.height(14.dp))

                            Text("主色调 (Primary)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                val colorNames = listOf("蓝", "紫", "绿", "粉", "橙")
                                com.example.ui.theme.BasePrimaryColors.forEachIndexed { index, color ->
                                    val selected = colorPrimaryIndex == index
                                    ColorMorphSwatch(
                                        color = color,
                                        selected = selected,
                                        onClick = {
                                            colorPrimaryIndex = index
                                            onColorThemeChange(index, colorSecondaryIndex)
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text("强调色 / 副色 (Secondary)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                val colorNames = listOf("蓝", "紫", "绿", "粉", "橙")
                                com.example.ui.theme.BaseSecondaryColors.forEachIndexed { index, color ->
                                    val selected = colorSecondaryIndex == index
                                    ColorMorphSwatch(
                                        color = color,
                                        selected = selected,
                                        onClick = {
                                            colorSecondaryIndex = index
                                            onColorThemeChange(colorPrimaryIndex, index)
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Text("实时配色预览卡片", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            GlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
            Text("Ciallo 阅览室", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.secondary
                                        ) {
                                            Text("副色标签", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    AppLiquidButton(
                                        text = "主色按钮实时联动效果",
                                        onClick = {
                                            val now = System.currentTimeMillis()
                                            liveButtonTapCount = if (now - liveButtonLastTapMs < 3000) {
                                                liveButtonTapCount + 1
                                            } else {
                                                1
                                            }
                                            liveButtonLastTapMs = now
                                            if (liveButtonTapCount >= 6) {
                                                showAdultSourceCard = true
                                                liveButtonTapCount = 0
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                if (showAdultSourceCard) {
                    item {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Lock, contentDescription = null, tint = MintPrimary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("高级内容", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("带你登大郎~~~", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    }
                                    AppLiquidSwitch(
                                        checked = showAdultSources,
                                        onCheckedChange = {
                                            showAdultSources = it
                                            prefs.showAdultSources = it
                                            onAdultSourcesChange(it)
                                            Toast.makeText(context, if (it) "正在更新成人源…" else "成人源已隐藏", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 2: Orientation Lock
                item {
                    SettingsSectionHeader("屏幕方向")
                }

                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.ScreenRotation, contentDescription = null, tint = MintPrimary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("方向锁定", fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            SegmentedPillSelector(
                                options = listOf(0 to "跟随系统", 1 to "锁定竖屏", 2 to "锁定横屏"),
                                selected = orientationLockVal,
                                onSelect = onOrientationLockChange
                            )
                        }
                    }
                }

                // Section 2.5: 画面与性能（渲染画质四档）
                item {
                    SettingsSectionHeader("画面与性能")
                }

                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MintPrimary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("渲染画质", fontWeight = FontWeight.SemiBold)
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = when (renderQualityVal) {
                                    0 -> "流畅：极简玻璃质感，任何设备都能满帧滚动"
                                    1 -> "均衡：保留玻璃质感与细节，关闭实时模糊"
                                    2 -> "高：完整液态玻璃实时模糊（默认推荐）"
                                    else -> "极致：折射透镜 + 更浓郁的玻璃色彩，旗舰机专属"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            SegmentedPillSelector(
                                options = listOf(
                                    0 to "流畅",
                                    1 to "均衡",
                                    2 to "高",
                                    3 to "极致"
                                ),
                                selected = renderQualityVal.coerceIn(0, 3),
                                onSelect = onRenderQualityChange
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "切换立即生效；主要影响界面玻璃效果与动效数量，不影响阅读排版。",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Section 3: Page Turn Effect
                item {
                    SettingsSectionHeader("翻页效果")
                }

                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PageTurnType.entries.forEach { turnType ->
                                PageTurnSelectorRow(
                                    title = turnType.title,
                                    description = turnType.description,
                                    selected = pageTurnMode == turnType.id,
                                    onClick = {
                                        pageTurnMode = turnType.id
                                        prefs.pageTurnMode = turnType.id
                                    }
                                )
                            }
                        }
                    }
                }

                // Section 5: Health & Eye Protection
                item {
                    SettingsSectionHeader("护眼与提醒")
                }

                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Timer, contentDescription = null, tint = MintPrimary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("定时休息", fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            SegmentedPillSelector(
                                options = listOf(0 to "关", 15 to "15分", 30 to "30分", 45 to "45分", 60 to "60分"),
                                selected = if (restReminderMinutes in listOf(0, 15, 30, 45, 60)) restReminderMinutes else -1,
                                onSelect = {
                                    restReminderMinutes = it
                                    prefs.restReminderMinutes = it
                                }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (restReminderMinutes in listOf(0, 15, 30, 45, 60)) {
                                        "自定义提醒时间"
                                    } else {
                                        "当前自定义：$restReminderMinutes 分钟"
                                    },
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                AppActionButton(
                                    text = "自定义",
                                    onClick = { showCustomMinutes = true },
                                    variant = AppButtonVariant.Secondary,
                                    buttonSize = AppButtonSize.Small
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Nightlight, contentDescription = null, tint = MintPrimary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("夜间模式", fontWeight = FontWeight.SemiBold)
                                }
                                AppLiquidSwitch(
                                    checked = autoNightMode,
                                    onCheckedChange = {
                                        autoNightMode = it
                                        onAutoNightModeChange(it)
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.RemoveRedEye, contentDescription = null, tint = MintPrimary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("护眼滤镜", fontWeight = FontWeight.SemiBold)
                                }
                                AppLiquidSwitch(
                                    checked = blueLightFilter,
                                    onCheckedChange = {
                                        blueLightFilter = it
                                        onBlueLightFilterChange(it)
                                    }
                                )
                            }

                            if (blueLightFilter) {
                                Spacer(modifier = Modifier.height(10.dp))
                                if (LocalRenderQuality.current == RenderQuality.MAX) {
                                    MaxJunoSlider(
                                        value = blueLightAlpha,
                                        onValueChange = {
                                            blueLightAlpha = it
                                            onBlueLightAlphaChange(it)
                                        }
                                    )
                                } else {
                                    JunoSlider(
                                        value = blueLightAlpha,
                                        onValueChange = {
                                            blueLightAlpha = it
                                            onBlueLightAlphaChange(it)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 6: Library Help & Manual
                item {
                    SettingsSectionHeader("帮助手册")
                }

                item {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickableWithFeedback { showHelpBottomSheet = true }
                            .testTag("library_help_entry_card"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.HelpCenter,
                                    contentDescription = "书库帮助中心",
                                    tint = MintPrimary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("书库使用手册", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("书源说明、下载管理与常见问题", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowRight,
                                contentDescription = "打开帮助",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }

        if (showHelpBottomSheet) {
            LibraryHelpBottomSheet(
                onDismissRequest = { showHelpBottomSheet = false },
                onOpenSourceManager = { onOpenSourceManager?.invoke() }
            )
        }
    }

    if (showCustomMinutes) {
        CustomMinutesDialog(
            current = if (restReminderMinutes in listOf(0, 15, 30, 45, 60)) 15 else restReminderMinutes,
            onConfirm = {
                restReminderMinutes = it
                prefs.restReminderMinutes = it
                showCustomMinutes = false
            },
            onDismiss = { showCustomMinutes = false }
        )
    }

    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("新建分类", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newCategoryText,
                    onValueChange = { newCategoryText = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                DialogLiquidGlass(fillMaxSize = false) {
                    AppActionButton(
                        text = "新建",
                        onClick = {
                            if (newCategoryText.isNotBlank()) {
                                onAddCategory(newCategoryText.trim())
                                newCategoryText = ""
                                showAddCategoryDialog = false
                                Toast.makeText(context, "新建成功", Toast.LENGTH_SHORT).show()
                            }
                        },
                        variant = AppButtonVariant.Primary,
                        buttonSize = AppButtonSize.Small
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text("取消")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/**
 * 设置页分区标题（统一 14sp/Bold/adaptiveTitleColor）。
 * 抽取自此前散落 8 处的相同 Text 模式；其中两处原为 16sp，已按多数派统一为 14sp。
 */
@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = adaptiveTitleColor()
    )
}
