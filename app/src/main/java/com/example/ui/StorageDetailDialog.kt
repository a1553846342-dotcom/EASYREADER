package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 通用存储明细对话框（第十八轮）：手机内存管理式——任意缓存/数据类别的
 * 逐文件/逐目录列表（可读名+大小），单删、批删、全选、全清。
 * 漫画译文缓存条目附加智能标签（原文→译文，复用 TranslationCacheIndex）。
 *
 * 窗口弹出动画：缩放 0.92→1 + 淡入（180ms）。
 */
data class StorageFileEntry(
    val file: File,
    val size: Long,
    val label: String,
    val sublabel: String = "",
    val deletable: Boolean = true,
)

object StorageDetailIndex {

    /** 把类别 key 展开为逐文件/逐目录条目（含可读标签）。 */
    suspend fun scan(key: String, targets: List<File>): List<StorageFileEntry> =
        withContext(Dispatchers.IO) {
            val out = ArrayList<StorageFileEntry>()
            for (dir in targets) {
                if (!dir.exists()) continue
                if (dir.isFile) {
                    out.add(StorageFileEntry(dir, dir.length(), dir.name))
                    continue
                }
                val children = dir.listFiles() ?: continue
                for (f in children.sortedByDescending { it.length() }) {
                    val size = if (f.isDirectory) f.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
                    else f.length()
                    if (size <= 0L && f.isFile) continue
                    when (key) {
                        "manga_tr_cache" -> {
                            // 译文缓存：复用智能标签（原文→译文）
                            val info = TranslationCacheIndex.describe(f)
                            out.add(StorageFileEntry(f, size, info.first, info.second))
                        }
                        else -> {
                            val name = f.name
                            val sub = when {
                                f.isDirectory -> "目录 · ${f.listFiles()?.size ?: 0} 项"
                                else -> when (f.extension.lowercase()) {
                                    "json" -> "JSON 数据"
                                    "txt" -> "文本"
                                    "epub" -> "EPUB 书籍"
                                    "azw3", "mobi" -> "Kindle 书籍"
                                    "png", "jpg", "webp" -> "图片"
                                    "ogg" -> "音频"
                                    "onnx" -> "模型"
                                    else -> "文件"
                                }
                            }
                            out.add(StorageFileEntry(f, size, name, sub))
                        }
                    }
                }
            }
            out.sortedByDescending { it.size }
        }

    suspend fun delete(files: List<File>): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        files.forEach { f ->
            runCatching {
                freed += if (f.isDirectory) {
                    val s = f.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
                    f.deleteRecursively(); s
                } else {
                    val s = f.length(); f.delete(); s
                }
            }
        }
        freed
    }
}

/** 明细对话框：任意类别通用。rowKey 用于译文缓存智能标签分支。 */
@Composable
fun StorageDetailDialog(
    rowKey: String,
    title: String,
    targets: List<File>,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<StorageFileEntry>>(emptyList()) }
    var scanning by remember { mutableStateOf(true) }
    val checked = remember { mutableStateMapOf<File, Boolean>() }
    // 弹出动画：0.92→1 缩放 + 淡入
    var appeared by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (appeared) 1f else 0.92f, tween(180), label = "detailScale")
    val alpha by animateFloatAsState(if (appeared) 1f else 0f, tween(180), label = "detailAlpha")

    fun refresh() {
        scope.launch {
            scanning = true
            entries = StorageDetailIndex.scan(rowKey, targets)
            checked.keys.retainAll(entries.map { it.file }.toSet())
            scanning = false
        }
    }
    LaunchedEffect(Unit) {
        refresh()
        appeared = true
    }

    val selectedFiles = entries.filter { checked[it.file] == true }.map { it.file }
    val totalSize = entries.sumOf { it.size }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    this.alpha = alpha
                },
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Folder, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Filled.Close, contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(onClick = onDismiss),
                    )
                }
                Text(
                    if (scanning) "扫描中…" else "${entries.size} 项 · 共 ${formatCacheSize(totalSize)}",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(6.dp))

                if (!scanning && entries.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Text("暂无可管理的内容", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxWidth().height(340.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        item(key = "select_all") {
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        val allOn = entries.isNotEmpty() && entries.all { checked[it.file] == true }
                                        entries.forEach { if (it.deletable) checked[it.file] = !allOn }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (entries.isNotEmpty() && entries.all { checked[it.file] == true || !it.deletable }) "取消全选"
                                    else "全选",
                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.weight(1f))
                                if (selectedFiles.isNotEmpty()) {
                                    Text(
                                        "已选 ${selectedFiles.size} 项 / ${formatCacheSize(selectedFiles.sumOf { it.length() })}",
                                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        items(entries, key = { it.file.absolutePath }) { entry ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .then(
                                        if (entry.deletable) Modifier.clickable {
                                            checked[entry.file] = !(checked[entry.file] ?: false)
                                        } else Modifier
                                    )
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (entry.deletable) {
                                    Box(
                                        Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (checked[entry.file] == true) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (checked[entry.file] == true) {
                                            Text("✓", color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp)
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        entry.label, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    if (entry.sublabel.isNotEmpty()) {
                                        Text(
                                            entry.sublabel, fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                Text(
                                    formatCacheSize(entry.size), fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                if (entry.deletable) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        Icons.Filled.Delete, contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable {
                                                scope.launch {
                                                    StorageDetailIndex.delete(listOf(entry.file))
                                                    onChanged()
                                                    refresh()
                                                }
                                            },
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(
                        onClick = {
                            scope.launch {
                                StorageDetailIndex.delete(entries.filter { it.deletable }.map { it.file })
                                onChanged()
                                refresh()
                            }
                        },
                        enabled = entries.any { it.deletable } && !scanning,
                    ) { Text("全部清理", color = MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("关闭") }
                    TextButton(
                        onClick = {
                            scope.launch {
                                StorageDetailIndex.delete(selectedFiles)
                                onChanged()
                                refresh()
                            }
                        },
                        enabled = selectedFiles.isNotEmpty(),
                    ) { Text("删除所选（${selectedFiles.size}）") }
                }
            }
        }
    }
}
