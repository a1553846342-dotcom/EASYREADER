package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kashif_e.backdrop.Backdrop
import kotlin.math.roundToInt

/**
 * 苹果 Liquid Glass 风格底部标签栏（上一版结构 + 真实验证参数增强）。
 *
 * 背景采用书源选择弹窗同款手法：MainActivity 用 layerBackdrop 捕获页面真实内容，
 * Tab 栏作为兄弟节点用 drawBackdrop(blur) 采样，得到真实 Gaussian Blur 磨砂。
 */

data class AppTabItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val badgeCount: Int? = null
)

/** 滚动收缩状态：由外部列表的 NestedScrollConnection 驱动，多个 Tab 页共享一份。 */
class TabBarCollapseState {
    var collapsed by mutableStateOf(false)
        private set

    fun connection(): NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            if (available.y < -4f) collapsed = true
            if (available.y > 4f) collapsed = false
            return Offset.Zero
        }
    }
}

@Composable
fun rememberTabBarCollapseState(): TabBarCollapseState = remember { TabBarCollapseState() }

@Composable
fun AppBottomTabBar(
    items: List<AppTabItem>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    collapseState: TabBarCollapseState = rememberTabBarCollapseState(),
    backdrop: Backdrop? = null
) {
    val colors = rememberAppButtonColors()
    // 主题色跟随设置主色调平滑过渡（不固化在 remember 里）
    val animatedPrimary by animateColorAsState(
        targetValue = colors.primary,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "tabBarPrimary"
    )
    val animatedAccent by animateColorAsState(
        targetValue = colors.accent,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "tabBarAccent"
    )
    // 根据 Tab 栏“有效底色”（内容面 + 30% 主题色）自动取对比色，
    // 并用与全软件一致的弹簧动画过渡。
    val barBase = lerp(MaterialTheme.colorScheme.surface, animatedPrimary, 0.30f)
    val contrast by animateColorAsState(
        targetValue = barBase.contrastColor(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "tabBarContrast"
    )

    val barHeight by animateDpAsState(
        targetValue = if (collapseState.collapsed) 52.dp else 68.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "tabBarHeight"
    )
    val horizontalMargin by animateDpAsState(
        targetValue = if (collapseState.collapsed) 48.dp else 16.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "tabBarMargin"
    )

    val tabPositions = remember { mutableStateMapOf<Int, Rect>() }
    val shape = RoundedCornerShape(50)

    // 主题 key：主题色变化时强制重建玻璃样式/着色器
    key(animatedPrimary, animatedAccent) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalMargin, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .shadow(
                        elevation = 24.dp,
                        shape = shape,
                        ambientColor = animatedPrimary.copy(alpha = 0.18f),
                        spotColor = animatedPrimary.copy(alpha = 0.18f)
                    )
                    .shadow(
                        elevation = 8.dp,
                        shape = shape,
                        ambientColor = Color.Black.copy(alpha = 0.20f),
                        spotColor = Color.Black.copy(alpha = 0.20f)
                    )
                    .clip(shape)
                    .then(
                        if (backdrop != null) {
                            // 书源选择弹窗同款模糊：真实内容采样 + Gaussian Blur
                            Modifier.liquidGlass(
                                backdrop = backdrop,
                                shape = shape,
                                surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                                blurRadius = 8.dp,
                                refraction = false
                            )
                        } else {
                            Modifier
                        }
                    )
                    .background(animatedPrimary.copy(alpha = 0.30f), shape)
                    .iridescentBorder(
                        shape = shape,
                        colors = listOf(
                            animatedPrimary,
                            animatedAccent,
                            animatedPrimary
                        ),
                        width = 1.5.dp,
                        alpha = 0.45f
                    ),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    TabIcon(
                        item = item,
                        selected = index == selectedIndex,
                        collapsed = collapseState.collapsed,
                        contrast = contrast,
                        onClick = { onTabSelected(index) },
                        onPositioned = { rect -> tabPositions[index] = rect }
                    )
                }
            }

            SelectionIndicator(
                targetRect = tabPositions[selectedIndex],
                color = contrast
            )
        }
    }
}

/** 计算颜色的相对亮度，返回高对比前景色（白 / 深灰）。 */
private fun Color.luminance(): Double {
    fun linear(c: Float): Double {
        val v = c
        return if (v <= 0.03928f) {
            v / 12.92
        } else {
            Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4)
        }
    }
    return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
}

private fun Color.contrastColor(): Color =
    if (luminance() > 0.5) Color(0xFF1A1A1E) else Color.White

@Composable
private fun TabIcon(
    item: AppTabItem,
    selected: Boolean,
    collapsed: Boolean,
    contrast: Color,
    onClick: () -> Unit,
    onPositioned: (Rect) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tabIconScale"
    )
    val iconColor = if (selected) contrast else contrast.copy(alpha = 0.55f)

    Column(
        modifier = Modifier
            .onGloballyPositioned { coords ->
                onPositioned(
                    Rect(
                        offset = coords.positionInParent(),
                        size = Size(
                            coords.size.width.toFloat(),
                            coords.size.height.toFloat()
                        )
                    )
                )
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Icon(
                imageVector = if (selected) item.selectedIcon else item.icon,
                contentDescription = item.label,
                tint = iconColor,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
            item.badgeCount?.let { count ->
                TabBadge(count = count, modifier = Modifier.align(Alignment.TopEnd))
            }
        }

        AnimatedVisibility(
            visible = !collapsed,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Text(
                text = item.label,
                fontSize = 10.sp,
                color = iconColor,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun TabBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(if (count > 0) 16.dp else 8.dp)
            .background(Color(0xFFE5484D), shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (count > 0) {
            Text(
                text = if (count > 99) "99+" else count.toString(),
                fontSize = 8.sp,
                color = Color.White
            )
        }
    }
}

@Composable
private fun SelectionIndicator(targetRect: Rect?, color: Color) {
    if (targetRect == null) return
    val density = LocalDensity.current

    val animatedOffsetX by animateFloatAsState(
        targetValue = targetRect.left,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "indicatorX"
    )
    val animatedWidth by animateFloatAsState(
        targetValue = targetRect.width,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "indicatorW"
    )
    val indicatorWidthPx = animatedWidth * 0.40f
    val centeredX = animatedOffsetX + (animatedWidth - indicatorWidthPx) / 2f

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = with(density) { centeredX.toDp().roundToPx() },
                    y = with(density) { 2.dp.toPx().roundToInt() }
                )
            }
            .width(with(density) { indicatorWidthPx.toDp() })
            .height(3.dp)
            .background(color, RoundedCornerShape(50))
    )
}
