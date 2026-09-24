package com.example.ui.favorite

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.favorite.ChapterReadState
import com.example.ui.theme.MintPrimary

/** 章节的视觉三态（读状态与下载态是两个独立维度，互不冲突）。 */
enum class ChapterVisual { UNREAD, READING, READ }

fun ChapterReadState.toVisual(): ChapterVisual = when (this) {
    ChapterReadState.READ -> ChapterVisual.READ
    ChapterReadState.READING -> ChapterVisual.READING
    ChapterReadState.UNREAD -> ChapterVisual.UNREAD
}

data class ChapterRowState(
    val visual: ChapterVisual = ChapterVisual.UNREAD,
    /** 上次读到第几页（0 起），阅读中时显示「上次读到第 N 页」 */
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    /** 上次打开之后新增的章节 → 右侧「新」小红点 */
    val isNew: Boolean = false,
    /** 已下载到本地 */
    val downloaded: Boolean = false,
    val downloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val external: Boolean = false,
) {
    val progressFloat: Float
        get() = if (pageCount > 0) (pageIndex + 1).toFloat() / pageCount.toFloat() else 0f
}

/**
 * 章节列表行：阅读状态 + 下载状态（漫画主页面与阅读器章节抽屉复用同一组件）。
 *
 * 已读置灰的可访问性约束：
 * - 用**中性灰**而不是低透明度，保证标题对比度 ≥ 4.5:1；
 * - 不只靠颜色区分：右侧对勾是第二线索。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChapterStatusRow(
    title: String,
    state: ChapterRowState,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverUrl: String? = null,
    onDownload: (() -> Unit)? = null,
    highlighted: Boolean = false,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.4f
    val readGray = if (isDark) Color(0xFFB3B3B3) else Color(0xFF6B6B6B)
    val titleColor = when (state.visual) {
        ChapterVisual.READ -> readGray
        ChapterVisual.READING -> MintPrimary
        ChapterVisual.UNREAD -> MaterialTheme.colorScheme.onSurface
    }
    val highlight by animateFloatAsState(
        targetValue = if (highlighted) 1f else 0f,
        animationSpec = tween(320),
        label = "chapter_highlight",
    )
    val grayed = state.visual == ChapterVisual.READ

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (highlight > 0f) MintPrimary.copy(alpha = 0.10f * highlight)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
            )
            .border(
                1.dp,
                if (grayed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                RoundedCornerShape(14.dp),
            )
            .semantics {
                contentDescription = buildString {
                    append(title)
                    append("，")
                    append(
                        when (state.visual) {
                            ChapterVisual.READ -> "已读"
                            ChapterVisual.READING -> "阅读中"
                            ChapterVisual.UNREAD -> "未读"
                        }
                    )
                    if (state.downloaded) append("，已下载")
                    if (state.isNew) append("，新章节")
                }
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 缩略图（已读 → 黑白化）
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 54.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = if (grayed) {
                        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                    } else null,
                    modifier = Modifier.size(width = 40.dp, height = 54.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = if (state.visual == ChapterVisual.READ) FontWeight.Normal else FontWeight.Medium,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (state.isNew) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFF4D4F), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Text("新", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
            when {
                state.visual == ChapterVisual.READING && state.pageCount > 0 -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "上次读到第 ${state.pageIndex + 1} 页 · 共 ${state.pageCount} 页",
                        fontSize = 11.sp,
                        color = MintPrimary.copy(alpha = 0.85f),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { state.progressFloat.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape),
                        color = MintPrimary,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                    )
                }
                state.visual == ChapterVisual.READ -> {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("已读", fontSize = 11.sp, color = readGray)
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            if (state.downloading) {
                LinearProgressIndicator(
                    progress = { state.downloadProgress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .width(28.dp)
                        .height(3.dp)
                        .clip(CircleShape),
                    color = MintPrimary,
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            // 已下载图标在"已读置灰"状态下仍保持清晰可见（不用灰化图标）
            if (state.downloaded) {
                Icon(
                    Icons.Filled.DownloadDone,
                    contentDescription = "已下载",
                    tint = MintPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
            } else if (onDownload != null && !state.external) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MintPrimary.copy(alpha = 0.12f))
                        .combinedClickable(onClick = onDownload),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = "下载本章",
                        tint = MintPrimary,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            if (state.visual == ChapterVisual.READ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "已读",
                    tint = readGray,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** 供外部复用的「阅读中」高亮底色（进入页面自动滚动到该行并闪一下）。 */
@Composable
fun rememberChapterHighlight(key: String?, targetKey: String?): Boolean {
    var shown by remember(key) { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(key) {
        shown = key != null && key == targetKey
        if (shown) {
            kotlinx.coroutines.delay(900)
            shown = false
        }
    }
    return shown
}

/** 亮度工具（避免依赖主题私有常量）。 */
private fun Color.luminance(): Float =
    0.2126f * red + 0.7152f * green + 0.0722f * blue
