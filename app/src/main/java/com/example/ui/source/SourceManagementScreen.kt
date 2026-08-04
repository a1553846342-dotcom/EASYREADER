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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppButton
import com.example.library.LibraryLoginDialog
import com.example.library.ZLibraryNativeSession
import com.example.library.ZLibraryNodeConfig
import com.example.source.BookSource
import com.example.source.SourceViewModel
import com.example.source.zlibrary.ZLibrarySource
import com.example.ui.theme.MintPrimary
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceManagementScreen(
    viewModel: SourceViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val allSources by viewModel.allSources.collectAsState()
    val activeSource by viewModel.activeSource.collectAsState()
    val importStatus by viewModel.importStatus.collectAsState()

    var showPasteDialog by remember { mutableStateOf(false) }
    var loginSource by remember { mutableStateOf<BookSource?>(null) }
    var showNodeManagement by remember { mutableStateOf(false) }
    var pasteJsonText by remember { mutableStateOf("") }

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

    val loginSession = remember {
        ZLibraryNativeSession(
            onSearchResults = { _, _ -> },
            onRealDownloadUrl = { },
            onLoginResult = { ok, msg ->
                loginLoading = false
                loginMessage = msg
                if (ok) {
                    loggedIn = true
                    showLoginDialog = false
                }
            }
        )
    }

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
                        IconButton(onClick = onBack) {
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
                Text(
                    text = "内置书源",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (builtinSources.isEmpty()) {
                item {
                    Text("暂无内置书源", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            } else {
                items(builtinSources, key = { it.id }) { source ->
                    SourceItemCard(
                        source = source,
                        isActive = activeSource?.id == source.id,
                        isEnabled = viewModel.isSourceEnabled(source.id),
                        isCustom = false,
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

            // Custom sources
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "自定义书源",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (customSources.isEmpty()) {
                item {
                    com.example.ui.components.MascotEmptyState(
                        mascotResId = com.example.ui.mascot.MascotSpriteSheet.idleDrawable,
                        title = "暂无自定义书源",
                        description = "导入 JSON 配置文件添加自定义书源",
                        actionLabel = "导入 JSON 书源文件",
                        onActionClick = { fileLauncher.launch("*/*") },
                        testTagPrefix = "sources_empty_state"
                    )
                }
            } else {
                items(customSources, key = { it.id }) { source ->
                    SourceItemCard(
                        source = source,
                        isActive = activeSource?.id == source.id,
                        isEnabled = viewModel.isSourceEnabled(source.id),
                        isCustom = true,
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

            // 粘贴 JSON 入口：放在“导入书源文件”按钮下方，样式保持一致
            item {
                AppButton(
                    onClick = { showPasteDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    containerColor = MintPrimary,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("粘贴 JSON", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    }

    if (showPasteDialog) {
        AlertDialog(
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
                Button(
                    onClick = {
                        if (pasteJsonText.isNotBlank()) {
                            viewModel.importSourceFromJsonString(pasteJsonText)
                            pasteJsonText = ""
                            showPasteDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                ) {
                    Text("确定导入")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) {
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
                loginSession.ensureCreated(context)
                loginSession.login(email, pass)
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (loggedIn) MintPrimary.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (loggedIn) MintPrimary.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (loggedIn) Icons.Default.CheckCircle else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (loggedIn) MintPrimary else MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (loggedIn) "Z-Library 已登录" else "Z-Library 尚未登录",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (loggedIn) "账号可用，进入书库可正常下载书籍"
                        else "进入书库下载前需要登录账号，搜索不受影响",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onLoginClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MintPrimary),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (loggedIn) "重新登录" else "去登录", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SourceItemCard(
    source: BookSource,
    isActive: Boolean,
    isEnabled: Boolean,
    isCustom: Boolean,
    onToggleEnable: (Boolean) -> Unit,
    onSelectActive: () -> Unit,
    onOpenLogin: (() -> Unit)? = null,
    onOpenNodeManager: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = source.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MintPrimary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "当前使用",
                                color = MintPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ID: ${source.id}${if (source.capabilities.requiresLogin) " • 需要登录" else ""}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onOpenLogin != null) {
                    TextButton(onClick = onOpenLogin) {
                        Text("账号登录", fontSize = 13.sp)
                    }
                }

                if (onOpenNodeManager != null) {
                    TextButton(onClick = onOpenNodeManager) {
                        Text("节点管理", fontSize = 13.sp)
                    }
                }

                if (isEnabled && !isActive) {
                    TextButton(onClick = onSelectActive) {
                        Text("使用", fontSize = 13.sp)
                    }
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggleEnable,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MintPrimary,
                        checkedTrackColor = MintPrimary.copy(alpha = 0.3f)
                    )
                )

                if (isCustom) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除书源",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
