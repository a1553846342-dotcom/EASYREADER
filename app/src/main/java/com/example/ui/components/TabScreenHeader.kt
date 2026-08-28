package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 滚动联动折叠头部——四个 Tab 页（书库/书架/统计/设置）共享。
 *
 * 根因修复（见《根因诊断报告》）：此前四页头部是复制粘贴的 88~92dp 固定玻璃卡，
 * 与滚动状态零联动，常驻挤压约 11% 屏高。
 *
 * 状态机（代理C）：Expanded（默认，标题24sp+副标题）→ Collapsed（滚过约 20dp 即收起：
 * 标题缩至 19sp、副标题高度与透明度归零、内外 padding 收紧）→ 回到顶部恢复 Expanded。
 * 收起/展开由 [rememberHeaderCollapsed] 的 derivedStateOf 派生——只在状态翻转时重组，
 * 滚动过程中零监听开销；高度动画 220ms 一次性补间，无逐帧常驻计算。
 *
 * 视觉效果：滚动后头部从 ~88dp 收缩到 ~48dp，为列表让出约 10% 可视高度；
 * 标题始终保留（位置感不丢失），副标题只属于 Expanded 态。
 */

/** 折叠判定：滚过首项顶部 60px（约 20dp）即收起，回到顶部恢复。derivedStateOf 只在布尔翻转时通知。 */
@Composable
fun rememberHeaderCollapsed(state: LazyListState, forceCollapsed: Boolean = false): Boolean {
    val scrolled by remember(state) {
        derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 60 }
    }
    return scrolled || forceCollapsed
}

@Composable
fun rememberHeaderCollapsed(state: LazyGridState, forceCollapsed: Boolean = false): Boolean {
    val scrolled by remember(state) {
        derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 60 }
    }
    return scrolled || forceCollapsed
}

@Composable
fun rememberHeaderCollapsed(state: LazyStaggeredGridState, forceCollapsed: Boolean = false): Boolean {
    val scrolled by remember(state) {
        derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 60 }
    }
    return scrolled || forceCollapsed
}

/**
 * @param collapsed   是否收起（由 [rememberHeaderCollapsed] 派生）
 * @param titleColor  标题色（各页传 glassTitleColor()，与背景明暗联动）
 * @param titleVisible false 时不组合标题列（书架页搜索展开态占用整行）
 * @param leading     标题前的槽位（如设置页返回键）
 * @param trailing    标题后的槽位（如书库下载入口、书架搜索框）
 */
@Composable
fun TabScreenHeader(
    collapsed: Boolean,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    titleColor: Color = Color.Unspecified,
    titleVisible: Boolean = true,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val outerV by animateDpAsState(if (collapsed) 6.dp else 10.dp, tween(220), label = "hdrOuterV")
    val innerV by animateDpAsState(if (collapsed) 7.dp else 12.dp, tween(220), label = "hdrInnerV")
    val titleSize by animateFloatAsState(if (collapsed) 19f else 24f, tween(220), label = "hdrTitle")
    val subAlpha by animateFloatAsState(if (collapsed) 0f else 1f, tween(180), label = "hdrSubAlpha")
    val subH by animateDpAsState(if (collapsed) 0.dp else 17.dp, tween(220), label = "hdrSubH")

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = outerV),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = innerV),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading?.invoke(this)
            if (titleVisible && title != null) {
                if (leading != null) Spacer(Modifier.width(2.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = titleSize.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        fontFamily = FontFamily.Serif
                    )
                    // 副标题用高度+透明度双通道收起：收起态不占布局空间
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = titleColor.copy(alpha = 0.75f),
                            letterSpacing = 1.5.sp,
                            modifier = Modifier
                                .height(subH)
                                .alpha(subAlpha)
                        )
                    }
                }
            }
            trailing?.invoke(this)
        }
    }
}
