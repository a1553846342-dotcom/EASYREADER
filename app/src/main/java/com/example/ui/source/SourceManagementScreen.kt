package com.example.ui.source

import android.net.Uri
import android.webkit.CookieManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppButton
import com.example.ui.components.AppIconButton
import com.example.ui.components.AcrylicDialog
import com.example.ui.components.AppIconButton
import com.example.ui.components.GradientActionButton
import com.example.ui.components.AppIconButton
import com.swapnil.squishyswitch.presentation.SquishyToggleSwitch
import com.example.ui.components.AppIconButton
import com.example.ui.components.AppButtonVariant
import com.example.ui.components.AppIconButton
import com.example.ui.components.AppActionButton
import com.example.ui.components.AppIconButton
import com.example.ui.components.AppButtonSize
import com.example.ui.components.AppIconButton
import com.example.ui.components.SourceAvatar
import com.example.ui.components.AppIconButton
import com.example.library.LibraryLoginDialog
import com.example.library.ZLibraryNodeConfig
import com.example.source.BookSource
import com.example.source.isNovelSource
import com.example.source.LoginCredential
import com.example.source.SourceResult
import com.example.source.SourceViewModel
import com.example.source.importer.SourceImporter
import com.example.source.zlibrary.ZLibrarySource
import com.example.ui.theme.MintPrimary
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceManagementScreen(
    viewModel: SourceViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val allSources by viewModel.allSources.collectAsState()
    val activeSource by viewModel.activeSource.collectAsState()
    val enabledStates by viewModel.enabledStates.collectAsState()
    val importStatus by viewModel.importStatus.collectAsState()

    var showPasteDialog by remember { mutableStateOf(false) }
    var showNetworkDialog by remember { mutableStateOf(false) }
    var loginSource by remember { mutableStateOf<BookSource?>(null) }
    var showNodeManagement by remember { mutableStateOf(false) }
    var pasteJsonText by remember { mutableStateOf("") }
    var networkUrl by remember { mutableStateOf("") }
    var showDebugLog by remember { mutableStateOf(false) }
    var debugLogText by remember { mutableStateOf("") }

    if (showNodeManagement) {
        ZLibraryNodeManagementScreen(onBack = { showNodeManagement = false })
        return
    }

    // 书源管理进入动画：与登录卡片一致的 Q 弹缩放 + 上滑 + 淡入
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { contentVisible = true }

    // 登录状态检测：负责提示用户进入书库前是否已配置账号
    var loggedIn by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var loginMessage by remember { mutableStateOf("") }
    var loginLoading by remember { mutableStateOf(false) }
    val hazeState = remember { HazeState() }

    val zlibSource = allSources.firstOrNull { it.id == "zlibrary" } as? ZLibrarySource

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit, zlibSource) {
        val cookies = CookieManager.getInstance().getCookie("https://${ZLibraryNodeConfig.domain}/") ?: ""
        loggedIn = cookies.contains("remix_userid") || cookies.contains("remix_userkey") ||
            zlibSource?.credentialStorage?.isLoggedIn() == true
    }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importSourceFromUri(it)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(importStatus) {
        importStatus?.let {
            if (it.contains("失败") || it.contains("错误")) {
                snackbarHostState.showSnackbar(
                    message = it,
                    duration = SnackbarDuration.Short
                )
            } else {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            }
            viewModel.clearImportStatus()
        }
    }

    val builtinSources = allSources.filter { !viewModel.isCustomSource(it.id) }
    val customSources = allSources.filter { viewModel.isCustomSource(it.id) }

    AnimatedVisibility(
        visible = contentVisible,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(260)) +
            scaleIn(
                initialScale = 0.94f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) +
            slideInVertically(
                initialOffsetY = { it / 12 },
                animationSpec = tween(280)
            )
    ) {
        Scaffold(
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    com.example.ui.components.AppErrorSnackbar(
                        message = data.visuals.message,
                        onDismissClick = { data.dismiss() }
                    )
                }
            },
            topBar = {
                TopAppBar(
                    title = { Text("书源管理", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        AppIconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .then(
                        if (showLoginDialog) Modifier.haze(hazeState) else Modifier
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // Z-Library 登录状态检测卡片：未登录时提醒并提供跳转登录按钮
                if (zlibSource != null) {
                    item {
                        ZLibraryLoginStatusCard(
                            loggedIn = loggedIn,
                            onLoginClick = {
                                loginMessage = ""
                                showLoginDialog = true
                            }
                        )
                    }
                }
            // Builtin sources
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = "Venera 源仓库",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = "内置 JS 引擎加载社区漫画源，可随时刷新更新",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Link,
                                        contentDescription = null,
                                        tint = MintPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            trailingContent = {
                                AssistChip(
                                    onClick = { viewModel.refreshJsSources() },
                                    label = { Text("社区源", fontSize = 12.sp) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MintPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                )
                            }
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GradientActionButton(
                                text = "更新 Venera 源",
                                onClick = { viewModel.refreshJsSources() },
                                icon = Icons.Default.Refresh
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = "调试日志",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        },
                        supportingContent = {
                            Text(
                                text = "查看/复制最近的书源请求记录与真实报错（HTTP 状态码、超时、解析失败），便于排查问题",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Code,
                                    contentDescription = null,
                                    tint = MintPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        trailingContent = {
                            AssistChip(
                                onClick = {
                                    debugLogText = com.example.source.SourceLog.dump()
                                    showDebugLog = true
                                },
                                label = { Text("查看", fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MintPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                    )
                }
            }

            item {
                Text(
                    text = "内置书源",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                )
            }

            if (builtinSources.isEmpty()) {
                item {
                    Text("暂无内置书源", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        builtinSources.chunked(5).forEach { group ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 1.dp,
                                tonalElevation = 0.dp
                            ) {
                                Column {
                                    group.forEachIndexed { index, source ->
                                        SourceItemCard(
                                            source = source,
                                            isActive = activeSource?.id == source.id,
                                            isEnabled = enabledStates[source.id] ?: true,
                                            isCustom = false,
                                            showDivider = index < group.lastIndex,
                                            onToggleEnable = { enabled ->
                                                if (enabled) viewModel.enableSource(source.id)
                                                else viewModel.disableSource(source.id)
                                            },
                                            onSelectActive = {
                                                viewModel.setActiveSource(source.id)
                                            },
                                            onOpenLogin = if (source.capabilities.requiresLogin && source.id != "zlibrary") {
                                                { loginSource = source }
                                            } else null,
                                            onOpenNodeManager = if (source.id == "zlibrary") {
                                                { showNodeManagement = true }
                                            } else null,
                                            onDelete = {}
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Custom sources
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "自定义书源",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            if (customSources.isEmpty()) {
                item {
                    com.example.ui.components.MascotEmptyState(
                        mascotResId = com.example.ui.mascot.MascotSpriteSheet.sadDrawable,
                        title = "暂无自定义书源",
                        description = "导入 JSON 配置文件添加自定义书源",
                        actionLabel = "导入 JSON 书源文件",
                        onActionClick = { fileLauncher.launch("*/*") },
                        testTagPrefix = "sources_empty_state"
                    )
                }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        customSources.chunked(5).forEach { group ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 1.dp,
                                tonalElevation = 0.dp
                            ) {
                                Column {
                                    group.forEachIndexed { index, source ->
                                        SourceItemCard(
                                            source = source,
                                            isActive = activeSource?.id == source.id,
                                            isEnabled = enabledStates[source.id] ?: true,
                                            isCustom = true,
                                            showDivider = index < group.lastIndex,
                                            onToggleEnable = { enabled ->
                                                if (enabled) viewModel.enableSource(source.id)
                                                else viewModel.disableSource(source.id)
                                            },
                                            onSelectActive = {
                                                viewModel.setActiveSource(source.id)
                                            },
                                            onDelete = {
                                                viewModel.removeSource(source.id)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 粘贴 JSON 入口：放在“导入书源文件”按钮下方，样式保持一致
            item {
                GradientActionButton(
                    text = "粘贴 JSON",
                    onClick = { showPasteDialog = true },
                    icon = Icons.Default.Code,
                    variant = AppButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 网络导入：可直接导入 GitHub 社区书源合集
            item {
                GradientActionButton(
                    text = "网络导入（社区书源合集）",
                    onClick = { showNetworkDialog = true },
                    icon = Icons.Default.Link,
                    variant = AppButtonVariant.Tertiary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    }

    if (showDebugLog) {
        val clipboard = LocalClipboardManager.current
        AcrylicDialog(
            onDismissRequest = { showDebugLog = false },
            title = { Text("书源调试日志") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "每次搜索/目录/正文请求的完整记录。反馈问题时请点击「复制全部」并把内容发给开发者。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = debugLogText,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                GradientActionButton(
                    text = "复制全部",
                    onClick = {
                        clipboard.setText(AnnotatedString(debugLogText))
                        Toast.makeText(context, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    }
                )
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        com.example.source.SourceLog.clear()
                        debugLogText = "（已清空）"
                    }) {
                        Text("清空")
                    }
                    TextButton(onClick = { showDebugLog = false }) {
                        Text("关闭")
                    }
                }
            }
        )
    }

    if (showPasteDialog) {
        AcrylicDialog(
            onDismissRequest = { showPasteDialog = false },
            title = { Text("粘贴 JSON 书源配置") },
            text = {
                OutlinedTextField(
                    value = pasteJsonText,
                    onValueChange = { pasteJsonText = it },
                    placeholder = { Text("{\n  \"name\": \"我的书源\",\n  \"search\": { ... }\n}") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                GradientActionButton(
                    text = "确定导入",
                    onClick = {
                        if (pasteJsonText.isNotBlank()) {
                            viewModel.importSourceFromJsonString(pasteJsonText)
                            pasteJsonText = ""
                            showPasteDialog = false
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showNetworkDialog) {
        AcrylicDialog(
            onDismissRequest = { showNetworkDialog = false },
            title = { Text("网络导入书源") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "粘贴任意「阅读」书源合集 JSON 地址（shuyuan 文件），系统会自动转换并批量导入，不兼容的会跳过。",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = networkUrl,
                        onValueChange = { networkUrl = it },
                        placeholder = { Text("https://.../shuyuan") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Text("快速选择社区源：", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    SourceImporter.PRESET_SOURCE_URLS.forEach { (url, label) ->
                        TextButton(
                            onClick = { networkUrl = url },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                color = MintPrimary,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            confirmButton = {
                GradientActionButton(
                    text = "开始导入",
                    onClick = {
                        if (networkUrl.isNotBlank()) {
                            viewModel.importSourceFromUrl(networkUrl.trim())
                            networkUrl = ""
                            showNetworkDialog = false
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showNetworkDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showLoginDialog) {
        LibraryLoginDialog(
            message = loginMessage,
            loading = loginLoading,
            onLogin = { email, pass ->
                loginLoading = true
                loginMessage = "登录中…"
                scope.launch {
                    val src = zlibSource
                    if (src == null) {
                        loginLoading = false
                        loginMessage = "Z-Library 源不可用"
                        return@launch
                    }
                    when (val result = src.login(LoginCredential(username = email, password = pass))) {
                        is SourceResult.Success -> {
                            loginLoading = false
                            loginMessage = "登录成功"
                            loggedIn = true
                            showLoginDialog = false
                        }
                        is SourceResult.Error -> {
                            loginLoading = false
                            loginMessage = result.exception.message ?: "登录失败，请检查账号密码"
                        }
                    }
                }
            },
            onDismiss = { if (!loginLoading) showLoginDialog = false },
            hazeState = hazeState
        )
    }

    loginSource?.let { src ->
        ZLibraryLoginDialog(
            source = src,
            onDismiss = { loginSource = null },
            onSuccess = { loginSource = null }
        )
    }
}

@Composable
private fun ZLibraryLoginStatusCard(
    loggedIn: Boolean,
    onLoginClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        tonalElevation = 0.dp
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = "Z-Library",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            },
            supportingContent = {
                Text(
                    text = if (loggedIn) "账号已登录，可正常下载书籍"
                    else "尚未登录，下载前需登录账号",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (loggedIn) MintPrimary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (loggedIn) Icons.Default.CheckCircle else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (loggedIn) MintPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            trailingContent = {
                GradientActionButton(
                    text = if (loggedIn) "重新登录" else "去登录",
                    onClick = onLoginClick,
                    variant = AppButtonVariant.Secondary
                )
            }
        )
    }
}

@Composable
fun SourceItemCard(
    source: BookSource,
    isActive: Boolean,
    isEnabled: Boolean,
    isCustom: Boolean,
    showDivider: Boolean = true,
    onToggleEnable: (Boolean) -> Unit,
    onSelectActive: () -> Unit,
    onOpenLogin: (() -> Unit)? = null,
    onOpenNodeManager: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SourceAvatar(
                sourceId = source.id,
                sourceName = source.name,
                size = 32.dp,
                modifier = Modifier.padding(end = 12.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = source.name,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    )
                    if (source.isNovelSource) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "小说",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (isActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = { Text("当前使用", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "ID: ${source.id}${if (source.capabilities.requiresLogin) " · 需要登录" else ""}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onOpenLogin != null) {
                                            AppActionButton(
                            text = "登录",
                            onClick = onOpenLogin,
                            variant = AppButtonVariant.Secondary,
                            buttonSize = AppButtonSize.Small
                        )
                }

                if (onOpenNodeManager != null) {
                                            AppActionButton(
                            text = "节点",
                            onClick = onOpenNodeManager,
                            variant = AppButtonVariant.Secondary,
                            buttonSize = AppButtonSize.Small
                        )
                }

                if (isEnabled && !isActive) {
                                            AppActionButton(
                            text = "使用",
                            onClick = onSelectActive,
                            variant = AppButtonVariant.Secondary,
                            buttonSize = AppButtonSize.Small
                        )
                }

                SquishyToggleSwitch(
                    color = MintPrimary,
                    checked = isEnabled,
                    onCheckedChange = { onToggleEnable(it) }
                )

                if (isCustom) {
                    Spacer(modifier = Modifier.width(4.dp))
                    AppIconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除书源",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
            )
        }
    }
}
