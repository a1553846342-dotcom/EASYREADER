package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import dev.liquidglass.compose.GlassHighlight
import dev.liquidglass.compose.GlassRefraction
import dev.liquidglass.compose.GlassShape
import dev.liquidglass.compose.GlassStyle
import dev.liquidglass.compose.LiquidGlassProviderState
import dev.liquidglass.compose.liquidGlass
import kotlin.math.roundToInt

/**
 * 苹果 Liquid Glass 风格底部标签栏。
 *
 * 滚动收缩只做两件事：降低高度（68dp→52dp）+ 隐藏文字标签，
 * 图标永远在、永远可点，不会出现“先点一下展开”的问题。
 * 胶囊悬浮不贴边；收缩时左右留白增大（胶囊变窄）。
 * 折射参数比按钮轻（Tab 栏面积大，太强会晃眼），整条栏不接 gel-press，
 * 形变只保留在单个图标的选中态缩放上。
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
    collapseState: TabBarCollapseState = rememberTabBarCollapseState()
) {
    val colors = rememberAppButtonColors()
    // 主色/强调色跟随主题切换时平滑过渡（Color State Morph 同款弹簧参数）
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
    val animatedColors = colors.copy(primary = animatedPrimary, accent = animatedAccent)
    val glass = LocalLiquidGlassState.current

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
                    ambientColor = animatedColors.primary.copy(alpha = 0.18f),
                    spotColor = animatedColors.primary.copy(alpha = 0.18f)
                )
                .shadow(
                    elevation = 8.dp,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = 0.20f),
                    spotColor = Color.Black.copy(alpha = 0.20f)
                )
                .clip(shape)
                .background(animatedColors.primary.copy(alpha = 0.24f))
                .then(
                    if (glass != null) {
                        Modifier.liquidGlass(
                            glass,
                            GlassStyle.Regular.copy(
                                shape = GlassShape.Capsule,
                                blurRadius = 30.dp,
                                refraction = GlassRefraction(
                                    height = 8.dp,
                                    amount = 10.dp
                                ),
                                saturation = 1.35f,
                                tint = animatedColors.primary.copy(alpha = 0.12f),
                                highlight = GlassHighlight(
                                    width = 1.dp,
                                    alpha = 0.35f
                                ),
                                noiseAlpha = 0.02f,
                                chromaticAberration = 0.08f
                            )
                        )
                    } else {
                        Modifier
                    }
                )
                .iridescentBorder(
                    shape = shape,
                    colors = remember(animatedColors) {
                        listOf(
                            animatedColors.primary.copy(alpha = 0.30f),
                            animatedColors.accent.copy(alpha = 0.30f),
                            animatedColors.primary.copy(alpha = 0.30f)
                        )
                    },
                    width = 1.dp,
                    alpha = 0.30f
                ),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                TabIcon(
                    item = item,
                    selected = index == selectedIndex,
                    collapsed = collapseState.collapsed,
                    colors = animatedColors,
                    onClick = { onTabSelected(index) },
                    onPositioned = { rect -> tabPositions[index] = rect }
                )
            }
        }

        SelectionIndicator(
            targetRect = tabPositions[selectedIndex],
            color = animatedColors.primary
        )
    }
}

@Composable
private fun TabIcon(
    item: AppTabItem,
    selected: Boolean,
    collapsed: Boolean,
    colors: AppButtonColors,
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
    val iconColor = if (selected) colors.primary else Color.Black.copy(alpha = 0.45f)

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
