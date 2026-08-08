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
import com.example.ui.components.DialogLiquidGlass
import com.example.ui.components.AppIconButton
import com.example.ui.theme.MintPrimary
import com.example.ui.theme.MintSecondary
import com.example.ui.theme.clickableWithFeedback
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
    onAdultSourcesChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var restReminderMinutes by remember { mutableStateOf(prefs.restReminderMinutes) }
    var autoNightMode by remember(autoNightModeVal) { mutableStateOf(autoNightModeVal) }
    var blueLightFilter by remember(blueLightFilterVal) { mutableStateOf(blueLightFilterVal) }
    var blueLightAlpha by remember(blueLightAlphaVal) { mutableStateOf(blueLightAlphaVal) }
    var pageTurnMode by remember { mutableStateOf(prefs.pageTurnMode) }
    var orientationLock by remember { mutableStateOf(prefs.screenOrientationLock) }
    var colorPrimaryIndex by remember(colorPrimaryIndexVal) { mutableStateOf(colorPrimaryIndexVal) }
    var colorSecondaryIndex by remember(colorSecondaryIndexVal) { mutableStateOf(colorSecondaryIndexVal) }
    var liveButtonTapCount by remember { mutableStateOf(0) }
    var liveButtonLastTapMs by remember { mutableStateOf(0L) }
    var showAdultSourceCard by remember { mutableStateOf(false) }
    var showAdultSources by remember { mutableStateOf(prefs.showAdultSources) }

    var splashPosterUri by remember { mutableStateOf(prefs.customSplashPosterUri) }
    var splashPureMode by remember { mutableStateOf(prefs.splashPureMode) }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text("设置", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground)
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            AppIconButton(onClick = onBack) {
                                Icon(
                                    Icons.Filled.ArrowBack,
                                    contentDescription = "返回",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.graphicsLayer { shadowElevation = 1f }
                )
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
                    Text("开屏海报设置", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MintPrimary)
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                            Divider(color = Color.LightGray.copy(alpha = 0.2f))
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

                // Section: 外观与主题自定义
                item {
                    Text("外观与主题", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MintPrimary)
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
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
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                    Text("屏幕方向", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MintPrimary)
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                listOf(0 to "跟随系统", 1 to "锁定竖屏", 2 to "锁定横屏").forEach { (mode, label) ->
                                    FilterChip(
                                        selected = orientationLock == mode,
                                        onClick = {
                                            orientationLock = mode
                                            prefs.screenOrientationLock = mode
                                        },
                                        shape = CircleShape,
                                        label = { Text(label, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 3: Page Turn Effect
                item {
                    Text("翻页效果", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MintPrimary)
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            PageTurnType.entries.forEachIndexed { index, turnType ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickableWithFeedback {
                                            pageTurnMode = turnType.id
                                            prefs.pageTurnMode = turnType.id
                                        }
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(turnType.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    RadioButton(
                                        selected = pageTurnMode == turnType.id,
                                        onClick = {
                                            pageTurnMode = turnType.id
                                            prefs.pageTurnMode = turnType.id
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = MintPrimary)
                                    )
                                }
                                if (index != PageTurnType.entries.size - 1) {
                                    Divider(color = Color.LightGray.copy(alpha = 0.2f))
                                }
                            }
                        }
                    }
                }

                // Section 4: Backup
                item {
                    Text("本地备份", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MintPrimary)
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = MintPrimary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("导出备份", fontWeight = FontWeight.SemiBold)
                                }
                                AppLiquidButton(
                                    text = "导出",
                                    onClick = {
                                        scope.launch {
                                            backupManager.exportBackupJson()
                                            Toast.makeText(context, "备份已导出", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = Color.LightGray.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = MintSecondary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("恢复备份", fontWeight = FontWeight.SemiBold)
                                }
                                AppLiquidButton(
                                    text = "恢复",
                                    onClick = {
                                        scope.launch {
                                            val json = backupManager.exportBackupJson()
                                            val success = backupManager.restoreBackupJson(json)
                                            if (success) {
                                                Toast.makeText(context, "恢复成功", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Section 5: Health & Eye Protection
                item {
                    Text("护眼与提醒", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MintPrimary)
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val presets = listOf(0 to "关", 15 to "15分", 30 to "30分", 45 to "45分", 60 to "60分")
                                presets.forEach { (min, label) ->
                                    FilterChip(
                                        selected = restReminderMinutes == min,
                                        onClick = {
                                            restReminderMinutes = min
                                            prefs.restReminderMinutes = min
                                        },
                                        shape = CircleShape,
                                        label = { Text(label, fontSize = 11.sp) }
                                    )
                                }
                                if (restReminderMinutes !in listOf(0, 15, 30, 45, 60)) {
                                    FilterChip(
                                        selected = true,
                                        onClick = {},
                                        shape = CircleShape,
                                        label = { Text("${restReminderMinutes}分(自定义)", fontSize = 11.sp) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                var customText by remember(restReminderMinutes) { mutableStateOf(if (restReminderMinutes !in listOf(0, 15, 30, 45, 60)) restReminderMinutes.toString() else "") }
                                OutlinedTextField(
                                    value = customText,
                                    onValueChange = { customText = it.filter { char -> char.isDigit() } },
                                    label = { Text("自定义时间 (分钟)", fontSize = 12.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MintPrimary,
                                        unfocusedBorderColor = Color.LightGray.copy(alpha = 0.5f),
                                        focusedLabelColor = MintPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                AppLiquidButton(
                                    text = "设置",
                                    onClick = {
                                        val mins = customText.toIntOrNull() ?: 0
                                        if (mins > 0) {
                                            restReminderMinutes = mins
                                            prefs.restReminderMinutes = mins
                                        }
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = Color.LightGray.copy(alpha = 0.2f))
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
                            Divider(color = Color.LightGray.copy(alpha = 0.2f))
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
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("强度: ${(blueLightAlpha * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Slider(
                                    value = blueLightAlpha,
                                    onValueChange = {
                                        blueLightAlpha = it
                                        onBlueLightAlphaChange(it)
                                    },
                                    valueRange = 0.0f..1.0f,
                                    colors = SliderDefaults.colors(thumbColor = MintPrimary, activeTrackColor = MintPrimary)
                                )
                            }
                        }
                    }
                }

                // Section 6: Library Help & Manual
                item {
                    Text("帮助手册", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MintPrimary)
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickableWithFeedback { showHelpBottomSheet = true }
                            .testTag("library_help_entry_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
