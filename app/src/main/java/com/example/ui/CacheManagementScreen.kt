@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.ui

import android.os.StatFs
import androidx.compose.foundation.clickable
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Storage
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
import com.example.ui.components.AppIconButton
import com.example.ui.theme.MintPrimary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import androidx.compose.foundation.layout.widthIn
import com.example.ui.adaptive.AdaptiveSpec

/** 四类书籍封面目录（漫画/EPUB/FB2/MOBI 解析产物）。 */
private val COVER_DIRS = listOf("comic_covers", "epub_covers", "fb2_covers", "mobi_covers")

/**
 * 存储行数据。zone 划分即安全模型：
 * 0=缓存（删除后自动重建，即时清理） 1=用户数据（不可再生或含状态，删除需确认） 2=应用核心数据（只读统计）
 */
private data class StorageRow(
    val key: String,
    val name: String,
    val desc: String,
    val size: Long,
    val zone: Int,
    val deletable: Boolean,
    val confirmTip: String? = null,
    val countLabel: String? = null
)

/**
 * 缓存管理页面：让用户看清应用存储占用，并能放心清理不紧要的文件。
 *
 * 安全模型（对标成熟应用的存储管理）：
 * - 「一键清理缓存」只作用于缓存区（网页图片 / 图片加载缓存 / 临时文件），即时执行，绝不触碰用户数据；
 * - 离线书籍、离线漫画、封面图、网页浏览数据属于用户数据，删除必须经确认对话框量化后果；
 * - 数据库与设置只读统计，不提供清理入口；
 * - 所有统计带异常保护（权限/IO 异常按 0 处理，不让扫描协程卡死），清理后实测释放量并重扫刷新；
 * - 清理期间按钮禁用防重复触发；临时文件清理保留进行中的分享/导入。
 *
 * @param onBack 返回上一页回调
 */
@Composable
fun CacheManagementScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var storageRows by remember { mutableStateOf<List<StorageRow>>(emptyList()) }
    var isScanning by remember { mutableStateOf(true) }
    var isClearing by remember { mutableStateOf(false) }
    var appTotalSize by remember { mutableLongStateOf(0L) }
    var otherSize by remember { mutableLongStateOf(0L) }
    var freeBytes by remember { mutableLongStateOf(0L) }
    var deviceTotalBytes by remember { mutableLongStateOf(0L) }
    var hasActiveDownload by remember { mutableStateOf(false) }
    var hasActiveComicDownload by remember { mutableStateOf(false) }
    var scanTrigger by remember { mutableIntStateOf(0) }
    var confirmRow by remember { mutableStateOf<StorageRow?>(null) }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> String.format(Locale.getDefault(), "%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1_048_576.0)
        bytes >= 1_024 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1_024.0)
        else -> "$bytes B"
    }

    // 带异常保护的目录大小统计：任何权限/IO 异常都按 0 处理，绝不让扫描协程崩溃卡住
    fun dirSize(target: File?): Long {
        if (target == null || !target.exists()) return 0L
        return runCatching {
            target.walkBottomUp().filter { it.isFile }.sumOf { runCatching { it.length() }.getOrDefault(0L) }
        }.getOrDefault(0L)
    }

    // 目录内最新文件的修改时间：目录 mtime 只在直接子项增删时更新，解析中的 epub_* 目录需要递归判断
    fun newestFileTime(target: File): Long = runCatching {
        target.walkTopDown().filter { it.isFile }.maxOfOrNull { it.lastModified() } ?: target.lastModified()
    }.getOrDefault(target.lastModified())

    // 删除执行前的现场复核（扫描快照会过期）：书籍下载以 30 分钟内的 .tmp 为准，
    // 漫画页图片写入后不再修改，2 分钟内有新文件即认为下载仍在进行
    fun hasActiveWrite(key: String): Boolean {
        val now = System.currentTimeMillis()
        return when (key) {
            "downloads" -> File(context.filesDir, "downloads")
                .listFiles { f -> f.isFile && f.name.endsWith(".tmp") && f.lastModified() > now - 30 * 60_000L }
                ?.isNotEmpty() == true
            "comics" -> context.filesDir.listFiles { f -> f.isDirectory && f.name.startsWith("comics_") }?.any { d ->
                d.walkTopDown().any { f -> f.isFile && f.lastModified() > now - 2 * 60_000L }
            } == true
            else -> false
        }
    }

    // ── 各清理项对应的磁盘目标 ──
    fun targetsFor(key: String): List<File> = when (key) {
        "ehimg" -> listOf(File(context.cacheDir, "ehimg"))
        "image_cache" -> listOf(File(context.cacheDir, "image_cache"))
        "temp" -> context.cacheDir.listFiles()
            ?.filter { it.name != "ehimg" && it.name != "image_cache" && it.name != "manga_translate_v1" }
            ?: emptyList()
        "downloads" -> listOf(File(context.filesDir, "downloads"))
        "comics" -> context.filesDir.listFiles { f -> f.isDirectory && f.name.startsWith("comics_") }?.toList() ?: emptyList()
        "covers" -> COVER_DIRS.map { File(context.filesDir, it) }
        "webview" -> listOf(File(context.dataDir, "app_webview"))
        "manga_tr_cache" -> listOf(com.example.mangatranslate.TranslationCache.dir(context))
        "manga_tr_models" -> listOf(com.example.mangatranslate.TranslateModelManager.modelDir(context))
        else -> emptyList()
    }

    // 临时文件清理的保守策略：Coil 缓存目录有自己的入口不重复动；
    // share_temp 保留 15 分钟内的文件（分享面板里停留或目标应用冷启动都可能超过几分钟）；
    // epub_* 解压目录内最近 2 分钟仍有文件更新的说明可能正在导入
    fun clearTargets(key: String) {
        val now = System.currentTimeMillis()
        when (key) {
            "temp" -> context.cacheDir.listFiles()?.forEach { f ->
                when {
                    f.name == "ehimg" || f.name == "image_cache" || f.name == "manga_translate_v1" -> Unit
                    f.name == "share_temp" && f.isDirectory ->
                        f.listFiles()?.forEach { c -> if (c.lastModified() < now - 15 * 60_000L) runCatching { c.deleteRecursively() } }
                    f.name.startsWith("epub_") && f.isDirectory && newestFileTime(f) > now - 2 * 60_000L -> Unit
                    else -> runCatching { f.deleteRecursively() }
                }
            }
            else -> targetsFor(key).forEach { runCatching { it.deleteRecursively() } }
        }
    }

    // 删除并统计实际释放量（删除前后实测差），完成后重扫刷新全部数字
    fun clearRow(key: String) {
        if (isClearing) return
        scope.launch {
            isClearing = true
            try {
                val freed = withContext(Dispatchers.IO) {
                    // 快照可能过期，删除前现场复核；-1 表示因下载进行中放弃本次删除
                    if (hasActiveWrite(key)) return@withContext -1L
                    val before = targetsFor(key).sumOf { dirSize(it) }
                    runCatching { clearTargets(key) }
                    val after = targetsFor(key).sumOf { dirSize(it) }
                    (before - after).coerceAtLeast(0L)
                }
                val msg = when {
                    freed < 0 -> "检测到下载正在进行，已取消删除"
                    freed > 0 -> "已释放 ${formatSize(freed)}"
                    else -> "没有可释放的内容"
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                scanTrigger++
            } finally {
                isClearing = false
            }
        }
    }

    // 一键清理只作用于「缓存」区三项，用户数据与设置永远不在作用范围内
    fun clearAllCache() {
        if (isClearing) return
        scope.launch {
            isClearing = true
            try {
                val freed = withContext(Dispatchers.IO) {
                    val keys = listOf("ehimg", "image_cache", "temp")
                    val before = keys.sumOf { key -> targetsFor(key).sumOf { dirSize(it) } }
                    keys.forEach { key -> runCatching { clearTargets(key) } }
                    val after = keys.sumOf { key -> targetsFor(key).sumOf { dirSize(it) } }
                    (before - after).coerceAtLeast(0L)
                }
                val msg = if (freed > 0) "已释放 ${formatSize(freed)}" else "没有可释放的内容"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                scanTrigger++
            } finally {
                isClearing = false
            }
        }
    }

    // ── 扫描：统计口径 = filesDir + cacheDir + 数据库/配置目录，与应用真实占用一致 ──
    LaunchedEffect(scanTrigger) {
        isScanning = true
        val rows = mutableListOf<StorageRow>()
        var appTotal = 0L
        var other = 0L
        var free = 0L
        var deviceTotal = 0L
        var activeDownload = false
        var activeComic = false
        try {
            withContext(Dispatchers.IO) {
                val filesDir = context.filesDir
                val cacheDir = context.cacheDir
                val dataDir = context.dataDir

                val ehimgSize = dirSize(File(cacheDir, "ehimg"))
                val imageCacheSize = dirSize(File(cacheDir, "image_cache"))
                val cacheAll = dirSize(cacheDir)
                // 与 targetsFor("temp") 同构的直接求和，保证「扫描口径 == 清理口径」
                val tempSize = cacheDir.listFiles()?.filter { it.name != "ehimg" && it.name != "image_cache" }?.sumOf { dirSize(it) } ?: 0L

                val downloadsDir = File(filesDir, "downloads")
                val downloadsSize = dirSize(downloadsDir)
                val comicDirs = filesDir.listFiles { f -> f.isDirectory && f.name.startsWith("comics_") }?.toList() ?: emptyList()
                val comicsSize = comicDirs.sumOf { dirSize(it) }
                val coversSize = COVER_DIRS.sumOf { dirSize(File(filesDir, it)) }
                val personalFiles = filesDir.listFiles { f -> f.isFile && (f.name == "custom_font.ttf" || f.name.startsWith("custom_poster_") || f.name.startsWith("custom_app_bg_")) }?.toList() ?: emptyList()
                val personalSize = personalFiles.sumOf { runCatching { it.length() }.getOrDefault(0L) }
                val sourcesSize = dirSize(File(filesDir, "js_sources")) + (File(filesDir, "novel_reader_backup.json").takeIf { it.exists() }?.length() ?: 0L)
                val webviewSize = dirSize(File(dataDir, "app_webview"))
                val appDataSize = listOf("databases", "shared_prefs", "no_backup").sumOf { dirSize(File(dataDir, it)) }

                // filesDir 中未归类的杂项（历史遗留示例数据等），归入「其他」让总数自洽
                filesDir.listFiles()?.forEach { f ->
                    val known = (f.isDirectory && (f.name == "downloads" || f.name.startsWith("comics_") || f.name in COVER_DIRS || f.name == "js_sources")) ||
                        (f.isFile && (f.name == "custom_font.ttf" || f.name == "novel_reader_backup.json" || f.name.startsWith("custom_poster_") || f.name.startsWith("custom_app_bg_")))
                    if (!known) other += dirSize(f)
                }

                appTotal = cacheAll + downloadsSize + comicsSize + coversSize + personalSize + sourcesSize + other + webviewSize + appDataSize
                runCatching { StatFs(filesDir.path) }.getOrNull()?.let {
                    free = it.availableBytes
                    deviceTotal = it.totalBytes
                }

                val now = System.currentTimeMillis()
                activeDownload = downloadsDir
                    .listFiles { f -> f.isFile && f.name.endsWith(".tmp") && f.lastModified() > now - 30 * 60_000L }
                    ?.isNotEmpty() == true
                activeComic = comicDirs.any { d ->
                    d.walkTopDown().any { f -> f.isFile && f.lastModified() > now - 2 * 60_000L }
                }
                val bookCount = downloadsDir.listFiles { f -> f.isFile && !f.name.endsWith(".tmp") }?.size ?: 0
                val activeDownloadTip = if (activeDownload)
                    "\n\n注意：检测到有下载正在进行，暂不能删除；请完成或取消下载后再试。" else ""
                val activeComicTip = if (activeComic)
                    "\n\n注意：检测到有漫画下载正在进行，暂不能删除；请完成或取消下载后再试。" else ""

                rows += StorageRow("ehimg", "网页图片缓存", "在线书源图片，删除后浏览时自动重新加载", ehimgSize, 0, true)
                rows += StorageRow("image_cache", "图片加载缓存", "封面与在线图片的通用缓存，自动重建", imageCacheSize, 0, true)
                rows += StorageRow("temp", "临时文件", "导入解压、分享等临时数据；正在使用中的文件会自动保留", tempSize, 0, true)
                // 漫画翻译（第十五轮）：译文缓存（可再生=重译即可）与离线模型（可重下）
                val trCacheSize = runCatching { com.example.mangatranslate.TranslationCache.totalBytes(context) }.getOrDefault(0L)
                val trModelsSize = runCatching { com.example.mangatranslate.TranslateModelManager.totalBytes(context) }.getOrDefault(0L)
                val trCacheCount = runCatching {
                    com.example.mangatranslate.TranslationCache.dir(context).listFiles()?.count { it.isFile } ?: 0
                }.getOrDefault(0)
                if (trCacheSize > 0) {
                    rows += StorageRow("manga_tr_cache", "漫画译文缓存", "已翻译页面的译文，点击可逐页/批量清理", trCacheSize, 0, true,
                        null, if (trCacheCount > 0) "共 $trCacheCount 页" else null)
                }
                if (trModelsSize > 0) {
                    rows += StorageRow("manga_tr_models", "漫画翻译模型", "离线 OCR 模型（约 31MB），删除后可在阅读设置里重新下载", trModelsSize, 0, true)
                }

                rows += StorageRow("downloads", "离线书籍", "下载的书籍原文件，属于你的离线内容", downloadsSize, 1, true,
                    "书架中使用这些文件的书将无法打开；若书源失效，将无法重新下载。$activeDownloadTip",
                    if (bookCount > 0) "共 $bookCount 本" else null)
                rows += StorageRow("comics", "离线漫画", "已下载的漫画页面与封面，属于你的离线内容", comicsSize, 1, true,
                    "已下载的漫画页会被删除，之后无法离线阅读。$activeComicTip",
                    if (comicDirs.isNotEmpty()) "共 ${comicDirs.size} 部" else null)
                rows += StorageRow("covers", "封面图片", "书籍与漫画的封面图，删除后无法自动恢复", coversSize, 1, true,
                    "书架封面将显示为空白，重新导入对应书籍后才能恢复。")
                rows += StorageRow("webview", "网页浏览数据", "网页书源的浏览器数据，删除后需重新登录书源网站", webviewSize, 1, true,
                    "网页书源的登录状态与浏览数据将被清除，之后需要重新登录。")
                rows += StorageRow("personalization", "个性化文件", "导入的字体、启动海报与软件背景，可在设置中更换", personalSize, 1, false)
                rows += StorageRow("sources", "书源与备份", "已安装的书源脚本与设置备份", sourcesSize, 1, false)
                rows += StorageRow("appdata", "书籍数据与设置", "书架、阅读进度与全部设置，不可在此清理", appDataSize, 2, false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        } finally {
            // 协程被取消（快速重扫/离开页面）时不落状态，避免旧扫描覆盖新扫描或闪空数据
            if (isActive) {
                storageRows = rows
                appTotalSize = appTotal
                otherSize = other
                freeBytes = free
                deviceTotalBytes = deviceTotal
                hasActiveDownload = activeDownload
                hasActiveComicDownload = activeComic
                isScanning = false
            }
        }
    }

    val cacheTotal = storageRows.filter { it.zone == 0 }.sumOf { it.size }
    // 第十八轮：全部类别明细（手机内存管理式：单删/批删/全选/全清 + 弹出动画）
    var detailRow by remember { mutableStateOf<StorageRow?>(null) }
    detailRow?.let { row ->
        StorageDetailDialog(
            rowKey = row.key,
            title = "${row.name} · 明细",
            targets = targetsFor(row.key),
            onDismiss = { detailRow = null },
            onChanged = { scanTrigger++ },
        )
    }

    // 与「书库使用手册」一致的 ModalBottomSheet 结构与观感
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onBack,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxHeight(0.9f)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
Column(modifier = Modifier.widthIn(max = AdaptiveSpec.sheetMaxWidth).fillMaxSize()) {
            // 顶部标题栏（同手册页：64dp + 图标 + 标题 + 关闭）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CleaningServices,
                            contentDescription = "缓存管理",
                            tint = MintPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("缓存管理", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    AppIconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, "关闭", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
LazyColumn(
                modifier = Modifier
                .widthIn(max = AdaptiveSpec.pageContentMaxWidth).fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item(key = "overview") {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (isScanning) {
                                CircularProgressIndicator(Modifier.size(32.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("正在计算…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text(formatSize(appTotalSize), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("应用总占用", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                // 占比条：缓存 / 用户数据 / 书籍数据与设置（+ 其他杂项，让总数与分段自洽）
                                val userTotal = storageRows.filter { it.zone == 1 }.sumOf { it.size }
                                val appDataTotal = storageRows.filter { it.zone == 2 }.sumOf { it.size }
                                if (appTotalSize > 0) {
                                    Spacer(Modifier.height(16.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    ) {
                                        segmentedBar(cacheTotal, userTotal, appDataTotal, otherSize, appTotalSize)
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val legend = buildList {
                                        add(Triple("缓存（可清理）", cacheTotal, Color(0xFF00B894)))
                                        add(Triple("用户数据", userTotal, Color(0xFFE17055)))
                                        add(Triple("书籍数据与设置", appDataTotal, Color(0xFF74B9FF)))
                                        if (otherSize > 0) add(Triple("其他", otherSize, Color(0xFF90A4AE)))
                                    }
                                    legend.forEach { (label, size, color) ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(8.dp).background(color, CircleShape))
                                            Spacer(Modifier.width(6.dp))
                                            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.weight(1f))
                                            Text(formatSize(size), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }

                                if (freeBytes > 0) {
                                    Spacer(Modifier.height(12.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    Spacer(Modifier.height(10.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Filled.Storage, contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "设备剩余 ${formatSize(freeBytes)} / 共 ${formatSize(deviceTotalBytes)}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "clear_all") {
                    Button(
                        onClick = { clearAllCache() },
                        enabled = !isScanning && !isClearing && cacheTotal > 0,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isClearing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("正在清理…", fontWeight = FontWeight.SemiBold)
                        } else {
                            Text(
                                if (cacheTotal > 0) "一键清理缓存 · ${formatSize(cacheTotal)}" else "一键清理缓存",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // 三个分区：缓存（放心清）→ 用户数据（确认后删）→ 书籍数据与设置（只读）；空区整段隐藏
                val zoneMeta = listOf(
                    Triple(0, "缓存", "可放心清理：删除后会自动重建；正在分享或导入中的文件会保留"),
                    Triple(1, "用户数据", "重要内容，删除需确认且无法恢复；不会被「一键清理缓存」删除"),
                    Triple(2, "书籍数据与设置", "应用核心数据，仅作统计展示")
                )
                zoneMeta.forEach { (zone, title, caption) ->
                    val zoneRows = storageRows.filter { it.zone == zone && it.size > 0 }
                    if (zoneRows.isNotEmpty()) {
                        item(key = "zone_header_$zone") {
                            Column(Modifier.padding(top = 2.dp)) {
                                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(2.dp))
                                Text(caption, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(zoneRows, key = { it.key }) { row ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth().then(
                                    if (row.deletable) Modifier.clickable { detailRow = row }
                                    else Modifier
                                )
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            if (row.deletable) "${row.name}（点击管理明细）" else row.name,
                                            fontWeight = FontWeight.SemiBold, fontSize = 15.sp
                                        )
                                        Text(row.desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        formatSize(row.size), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (row.deletable) {
                                        Spacer(Modifier.width(12.dp))
                                        AppIconButton(
                                            onClick = { if (row.confirmTip != null) confirmRow = row else clearRow(row.key) },
                                            enabled = !isScanning && !isClearing
                                        ) {
                                            Icon(
                                                Icons.Default.Delete, "清理${row.name}",
                                                // 颜色语言：缓存=中性灰（随便删），用户数据=警示红（需确认）
                                                tint = when {
                                                    row.zone == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                                                    else -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                                },
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
        }
        }
    }

    // 用户数据删除确认：一句话说清删什么、多少、多大、后果
    confirmRow?.let { row ->
        AlertDialog(
            onDismissRequest = { confirmRow = null },
            title = { Text("删除「${row.name}」") },
            text = {
                val countPart = row.countLabel?.let { "$it，" } ?: ""
                Text("将删除「${row.name}」${countPart}约 ${formatSize(row.size)}。\n\n${row.confirmTip}")
            },
            confirmButton = {
                // 有下载进行中时不允许删除对应离线内容，避免与写入中的文件竞态（执行前还有二次复核兜底）
                val deleteEnabled = !((row.key == "downloads" && hasActiveDownload) ||
                    (row.key == "comics" && hasActiveComicDownload))
                TextButton(
                    onClick = { confirmRow = null; clearRow(row.key) },
                    enabled = deleteEnabled
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRow = null }) { Text("取消") }
            }
        )
    }
}

/** 总览占比条：缓存/用户数据/书籍数据与设置，可选「其他」段。 */
@Composable
private fun RowScope.segmentedBar(
    cacheTotal: Long,
    userTotal: Long,
    appDataTotal: Long,
    otherTotal: Long,
    grandTotal: Long
) {
    val segments = buildList {
        add(Triple(cacheTotal, Color(0xFF00B894), "cache"))
        add(Triple(userTotal, Color(0xFFE17055), "user"))
        add(Triple(appDataTotal, Color(0xFF74B9FF), "appdata"))
        if (otherTotal > 0) add(Triple(otherTotal, Color(0xFF90A4AE), "other"))
    }
    segments.forEach { (size, color, _) ->
        Box(
            Modifier
                .weight((size.toFloat() / grandTotal).coerceAtLeast(0.005f))
                .fillMaxHeight()
                .background(color)
        )
    }
}
