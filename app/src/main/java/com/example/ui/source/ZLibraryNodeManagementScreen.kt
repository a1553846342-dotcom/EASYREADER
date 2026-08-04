package com.example.ui.source

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.library.ZLibraryNodeManager
import com.example.source.SearchBook
import com.example.source.zlibrary.parser.ZLibraryParserManager
import com.example.ui.theme.MintPrimary
import kotlinx.coroutines.launch
import org.json.JSONTokener

/**
 * Z-Library 节点管理窗口：
 * - 默认节点（1lib.sk）+ 官网/备用入口三个节点 + 用户自定义节点；
 * - 每个节点可“检测”（游离 WebView 真实搜索“三体”并解析）；
 * - “扒取节点”按钮抓取 https://z.wwwnav.com/rkfby.html 的三个入口，确认后替换；
 * - 切换节点立即生效并持久化。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZLibraryNodeManagementScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { contentVisible = true }

    var scrapedNodes by remember { mutableStateOf(ZLibraryNodeManager.getScrapedNodes(context)) }
    var customNodes by remember { mutableStateOf(ZLibraryNodeManager.getCustomNodes(context)) }
    var selectedNode by remember { mutableStateOf(ZLibraryNodeManager.getSelectedNode(context)) }
    var customInput by remember { mutableStateOf("") }
    var scraping by remember { mutableStateOf(false) }
    var showReplaceDialog by remember { mutableStateOf(false) }
    var foundNodes by remember { mutableStateOf<List<String>>(emptyList()) }

    val statuses = remember { mutableStateMapOf<String, String>() }
    var testingNode by remember { mutableStateOf<String?>(null) }
    val webView = remember { mutableStateOf<WebView?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    LaunchedEffect(Unit) {
        webView.value = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // 节点检测只需解析结果，不下载页面图片，避免触发站点风控
            settings.blockNetworkImage = true
            CookieManager.getInstance().setAcceptCookie(true)
        }
    }

    // 离开节点管理时释放测试 WebView
    DisposableEffect(Unit) {
        onDispose {
            webView.value?.let { wv ->
                try { wv.stopLoading() } catch (_: Exception) {}
                try { wv.destroy() } catch (_: Exception) {}
            }
            webView.value = null
        }
    }

    fun testNode(node: String) {
        if (testingNode != null) return
        testingNode = node
        statuses[node] = "测试中…"
        val wv = webView.value
        if (wv == null) {
            statuses[node] = "初始化失败"
            testingNode = null
            return
        }
        var tries = 0
        val url = "https://$node/s/" + Uri.encode("三体")

        fun parseOnce() {
            wv.evaluateJavascript("(function(){return document.documentElement.outerHTML;})();") { htmlJson ->
                if (testingNode != node) return@evaluateJavascript
                if (htmlJson == null) return@evaluateJavascript
                val html = try {
                    JSONTokener(htmlJson).nextValue() as String
                } catch (e: Exception) {
                    htmlJson
                }
                val isChallenge = html.contains("Verifying your browser", ignoreCase = true) ||
                    html.contains("Checking your browser", ignoreCase = true) ||
                    html.contains("cpt.lib") ||
                    html.contains("solve this captcha", ignoreCase = true)
                if (isChallenge) {
                    tries++
                    if (tries >= 6) {
                        statuses[node] = "需验证（无法自动搜索）"
                        testingNode = null
                    } else {
                        mainHandler.postDelayed({ parseOnce() }, 2000)
                    }
                    return@evaluateJavascript
                }
                val books: List<SearchBook> = try {
                    ZLibraryParserManager.parseSearchPage(html, "https://$node", "zlibrary")
                } catch (e: Exception) {
                    emptyList()
                }
                if (books.isNotEmpty()) {
                    statuses[node] = "可用（搜到 ${books.size} 本）"
                    testingNode = null
                } else {
                    tries++
                    if (tries >= 6) {
                        statuses[node] = "不可用"
                        testingNode = null
                    } else {
                        mainHandler.postDelayed({ parseOnce() }, 2000)
                    }
                }
            }
        }

        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (testingNode == node) parseOnce()
            }
        }
        mainHandler.postDelayed({ if (testingNode == node) parseOnce() }, 2500)
        wv.loadUrl(url)
    }

    fun selectNode(node: String) {
        ZLibraryNodeManager.selectNode(context, node)
        selectedNode = node
        Toast.makeText(context, "已切换到 $node", Toast.LENGTH_SHORT).show()
    }

    val defaultNode = ZLibraryNodeManager.DEFAULT_NODE
    val builtinNodes = (listOf(defaultNode) + scrapedNodes).distinct()
    val customNodeList = customNodes.filterNot { it in builtinNodes }

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
            topBar = {
                TopAppBar(
                    title = { Text("节点管理", fontWeight = FontWeight.Bold) },
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
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "当前节点：$selectedNode",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Button(
                                    onClick = {
                                        scope.launch {
                                            scraping = true
                                            val nodes = ZLibraryNodeManager.scrapeNodes(context)
                                            scraping = false
                                            if (nodes.isNotEmpty()) {
                                                foundNodes = nodes
                                                showReplaceDialog = true
                                            } else {
                                                Toast.makeText(context, "扒取失败，请检查网络后重试", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    enabled = !scraping,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                                ) {
                                    if (scraping) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (scraping) "扒取中…" else "扒取节点")
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "从 z.wwwnav.com 抓取官网/备用入口，检测后点“使用”切换，立即生效。",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Text(
                        "节点列表",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                items(builtinNodes, key = { it }) { node ->
                    val isDefault = node == defaultNode
                    val scrapedIndex = scrapedNodes.indexOf(node)
                    val label = when {
                        isDefault -> "默认节点"
                        scrapedIndex == 0 -> "官网入口"
                        scrapedIndex == 1 -> "备用入口一"
                        scrapedIndex == 2 -> "备用入口二"
                        else -> "节点"
                    }
                    NodeCard(
                        node = node,
                        label = label,
                        isSelected = selectedNode == node,
                        status = statuses[node] ?: "未测试",
                        isTesting = testingNode == node,
                        canDelete = false,
                        onTest = { testNode(node) },
                        onSelect = { selectNode(node) },
                        onDelete = {
                            ZLibraryNodeManager.removeCustomNode(context, node)
                            customNodes = ZLibraryNodeManager.getCustomNodes(context)
                            selectedNode = ZLibraryNodeManager.getSelectedNode(context)
                            statuses.remove(node)
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "自定义节点",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                items(customNodeList, key = { it }) { node ->
                    NodeCard(
                        node = node,
                        label = "自定义节点",
                        isSelected = selectedNode == node,
                        status = statuses[node] ?: "未测试",
                        isTesting = testingNode == node,
                        canDelete = true,
                        onTest = { testNode(node) },
                        onSelect = { selectNode(node) },
                        onDelete = {
                            ZLibraryNodeManager.removeCustomNode(context, node)
                            customNodes = ZLibraryNodeManager.getCustomNodes(context)
                            selectedNode = ZLibraryNodeManager.getSelectedNode(context)
                            statuses.remove(node)
                        }
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = customInput,
                            onValueChange = { customInput = it },
                            placeholder = { Text("自定义节点域名") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Button(
                            onClick = {
                                if (ZLibraryNodeManager.addCustomNode(context, customInput)) {
                                    customNodes = ZLibraryNodeManager.getCustomNodes(context)
                                    customInput = ""
                                    Toast.makeText(context, "已添加自定义节点", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "请输入有效的域名", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("添加")
                        }
                    }
                }
            }
        }
    }

    if (showReplaceDialog) {
        AlertDialog(
            onDismissRequest = { showReplaceDialog = false },
            title = { Text("找到 ${foundNodes.size} 个节点") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("是否替换为以下节点？")
                    foundNodes.forEach { node ->
                        Text("•  $node", fontWeight = FontWeight.Medium)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        ZLibraryNodeManager.saveScrapedNodes(context, foundNodes)
                        scrapedNodes = ZLibraryNodeManager.getScrapedNodes(context)
                        showReplaceDialog = false
                        Toast.makeText(context, "已替换节点", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                ) {
                    Text("替换")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun NodeCard(
    node: String,
    label: String,
    isSelected: Boolean,
    status: String,
    isTesting: Boolean,
    canDelete: Boolean,
    onTest: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val isOk = status.startsWith("可用")
    val isFail = status == "不可用" || status.startsWith("需验证") || status == "初始化失败"
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MintPrimary.copy(alpha = 0.15f)
                isOk -> MintPrimary.copy(alpha = 0.10f)
                isFail -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(node, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = MintPrimary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "当前使用",
                                    color = MintPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MintPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        } else if (isOk) {
                            Icon(Icons.Default.CheckCircle, null, tint = MintPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                        } else if (isFail) {
                            Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("$label · $status", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onTest,
                    enabled = !isTesting,
                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("检测")
                }
                OutlinedButton(
                    onClick = onSelect,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(if (isSelected) "使用中" else "使用")
                }
                if (canDelete) {
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除节点",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
