/*
 * MAX 画质专属特效集：
 *  - GravitySensor：全局重力传感器单例（引用计数启停，低通滤波）
 *  - Modifier.maxGravityParticles()：按钮/开关内部的"液态微粒"，
 *    粒子随设备倾角受重力运动、边界反弹，仅 MAX 档激活
 *  - Modifier.glassSheen()：玻璃卡表面周期性掠过的高光带（旋转渐变）
 *  - MaxJunoSlider：MAX 档专属滑条——玻璃凹槽轨道、渐变填充外发光、
 *    常驻光晕滑块 + 方向性彗尾 + 拖动挤压形变 + 百分比气泡
 *
 * 全部基于 Canvas/drawBehind 单节点绘制，无 RenderEffect/着色器依赖，
 * 规避部分机型链式着色器崩溃问题；仅在 MAX 档挂载。
 */
package com.example.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.random.Random

// ---------------------------------------------------------------------------
// 重力传感器单例
// ---------------------------------------------------------------------------

/**
 * 全局重力方向（屏幕坐标系）：(-1..1, -1..1)，(+x 向右，+y 向下)。
 * 引用计数为 0 时注销监听器省电；无传感器时保持默认竖直向下。
 */
object GravitySensor {
    private var refs = 0
    private var manager: SensorManager? = null
    private var listener: SensorEventListener? = null

    @Volatile
    private var gx = 0f
    @Volatile
    private var gy = 1f

    /** 当前重力方向（已低通滤波）。读侧任意线程。 */
    fun direction(): Pair<Float, Float> = gx to gy

    @Synchronized
    fun acquire(context: Context) {
        refs++
        if (refs > 1 || manager != null) return
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        manager = sm
        val l = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.values.size < 2) return
                val mag = hypot(event.values[0], event.values[1]).coerceAtLeast(1e-3f)
                // 设备坐标 → 屏幕坐标：竖屏持机时粒子应向屏幕下方坠落
                val tx = (-event.values[0] / mag).coerceIn(-1f, 1f)
                val ty = (event.values[1] / mag).coerceIn(-1f, 1f)
                // 低通滤波，避免抖动
                gx += (tx - gx) * 0.18f
                gy += (ty - gy) * 0.18f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        listener = l
        sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    @Synchronized
    fun release() {
        refs = (refs - 1).coerceAtLeast(0)
        if (refs == 0) {
            listener?.let { manager?.unregisterListener(it) }
            listener = null
            manager = null
            gx = 0f
            gy = 1f
        }
    }
}

// ---------------------------------------------------------------------------
// 重力粒子
// ---------------------------------------------------------------------------

private class MaxParticle(
    var fx: Float,
    var fy: Float,
    var vx: Float,
    var vy: Float,
    val radiusPx: Float,
    val alpha: Float
)

private class ParticleWorld {
    var width = 0f
    var height = 0f
}

/**
 * MAX 档专属：组件内部漂浮的"液态微粒"，随设备倾角受重力运动并反弹。
 * 绘制在内容之上；仅 MAX 档生效，其余档位是零开销的空 Modifier。
 */
fun Modifier.maxGravityParticles(
    count: Int = 10,
    color: Color = Color.White,
    maxAlpha: Float = 0.50f,
    maxRadiusDp: Float = 2.6f
): Modifier = composed {
    if (LocalRenderQuality.current != RenderQuality.MAX) return@composed Modifier

    val context = LocalContext.current
    val density = LocalDensity.current
    DisposableEffect(Unit) {
        GravitySensor.acquire(context)
        onDispose { GravitySensor.release() }
    }

    val parts = remember(count) {
        val rnd = Random(count * 7919)
        List(count) {
            MaxParticle(
                fx = rnd.nextFloat(),
                fy = rnd.nextFloat(),
                vx = (rnd.nextFloat() - 0.5f) * 40f,
                vy = (rnd.nextFloat() - 0.5f) * 40f,
                radiusPx = (0.9f + rnd.nextFloat() * (maxRadiusDp - 0.9f)) * density.density,
                alpha = 0.35f + rnd.nextFloat() * 0.65f
            )
        }
    }
    val world = remember { ParticleWorld() }
    val tick = remember { mutableLongStateOf(0L) }

    LaunchedEffect(parts) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (lastNanos == 0L) 1f / 60f else ((now - lastNanos) / 1_000_000_000f).coerceIn(1f / 240f, 0.06f)
                lastNanos = now
                val (gx, gy) = GravitySensor.direction()
                val gScale = density.density * 900f
                val w = world.width
                val h = world.height
                for (p in parts) {
                    p.vx += gx * gScale * dt
                    p.vy += gy * gScale * dt
                    p.vx *= 0.986f
                    p.vy *= 0.986f
                    p.fx += p.vx * dt
                    p.fy += p.vy * dt
                    if (w > 0f && h > 0f) {
                        if (p.fx < p.radiusPx) {
                            p.fx = p.radiusPx; p.vx = -p.vx * 0.55f
                        } else if (p.fx > w - p.radiusPx) {
                            p.fx = w - p.radiusPx; p.vx = -p.vx * 0.55f
                        }
                        if (p.fy < p.radiusPx) {
                            p.fy = p.radiusPx; p.vy = -p.vy * 0.55f
                        } else if (p.fy > h - p.radiusPx) {
                            p.fy = h - p.radiusPx; p.vy = -p.vy * 0.55f
                        }
                    }
                }
            }
            tick.longValue++
        }
    }

    this
        .onSizeChanged { world.width = it.width.toFloat(); world.height = it.height.toFloat() }
        .drawWithContent {
            drawContent()
            @Suppress("UNUSED_EXPRESSION") tick.longValue
            for (p in parts) {
                drawCircle(
                    color = color.copy(alpha = p.alpha * maxAlpha),
                    radius = p.radiusPx,
                    center = Offset(p.fx, p.fy)
                )
                // 微高光点：让粒子有"液珠"立体感
                drawCircle(
                    color = Color.White.copy(alpha = p.alpha * maxAlpha * 0.8f),
                    radius = p.radiusPx * 0.38f,
                    center = Offset(p.fx - p.radiusPx * 0.3f, p.fy - p.radiusPx * 0.3f)
                )
            }
        }
}

// ---------------------------------------------------------------------------
// 玻璃流光 sheen
// ---------------------------------------------------------------------------

/**
 * MAX 档专属：每 ~6.5s 一道柔和光带斜向掠过卡面。
 * 必须挂在卡片 `.clip(shape)` 之后（自动被卡片圆角裁剪）。
 */
fun Modifier.glassSheen(): Modifier = composed {
    if (LocalRenderQuality.current != RenderQuality.MAX) return@composed Modifier
    val transition = rememberInfiniteTransition(label = "glassSheen")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6500, easing = LinearEasing), RepeatMode.Restart),
        label = "glassSheenPhase"
    )
    this.drawBehind {
        val travel = size.width * 2f + size.height
        val bandWidth = size.width * 0.55f
        val x = -size.width * 0.75f + phase * travel
        rotate(degrees = -24f, pivot = center) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.085f),
                        Color.White.copy(alpha = 0.13f),
                        Color.Transparent
                    ),
                    startX = x,
                    endX = x + bandWidth
                ),
                topLeft = Offset(x, -size.height),
                size = Size(bandWidth, size.height * 3f)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// MAX 滑条
// ---------------------------------------------------------------------------

/**
 * MAX 档专属滑条（替代 JunoSlider）：
 * 玻璃凹槽轨道 + 渐变填充外发光 + 常驻光晕滑块（拖动挤压形变）
 * + 方向性彗尾 + 百分比气泡。参数与 [JunoSlider] 完全一致可直接互换。
 */
@Composable
fun MaxJunoSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }
    var widthPx by remember { mutableIntStateOf(0) }
    val currentWidth by rememberUpdatedState(widthPx)
    var direction by remember { mutableFloatStateOf(1f) }

    val trackHeight by animateDpAsState(
        targetValue = if (dragging) 24.dp else 12.dp,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "maxTrackHeight"
    )
    val shape = RoundedCornerShape(percent = 50)
    val fraction = value.coerceIn(0f, 1f)

    // 彗尾长度随拖动出现/收回
    val trailLen by animateDpAsState(
        targetValue = if (dragging) 42.dp else 0.dp,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "maxTrail"
    )
    // 拖动挤压
    val squeeze by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = springLike(),
        label = "maxSqueeze"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragging = true
                    if (currentWidth > 0) {
                        onValueChange((down.position.x / currentWidth).coerceIn(0f, 1f))
                    }
                    var lastX = down.position.x
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        direction = if (change.position.x >= lastX) 1f else -1f
                        lastX = change.position.x
                        if (currentWidth > 0) {
                            onValueChange((change.position.x / currentWidth).coerceIn(0f, 1f))
                        }
                    }
                    dragging = false
                }
            }
            // 可访问性：向读屏暴露当前进度与百分比
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                stateDescription = "${(fraction * 100).roundToInt()}%"
            }
    ) {
        // ── 玻璃凹槽轨道 ──
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(trackHeight)
                .clip(shape)
                .background(Color(0xFF101014).copy(alpha = 0.58f))
                .drawBehind {
                    // 内阴影：上沿暗带
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.50f), Color.Transparent),
                            startY = 0f,
                            endY = size.height
                        ),
                        cornerRadius = CornerRadius(size.height / 2f)
                    )
                    // 下沿微亮线：凹槽受光
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.14f)),
                            startY = size.height * 0.55f,
                            endY = size.height
                        ),
                        cornerRadius = CornerRadius(size.height / 2f)
                    )
                }
        ) {
            // ── 渐变填充 + 外发光 ──
            val fillFraction = fraction.coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(fillFraction)
                    .drawWithContent {
                        // 外发光：两层扩张圆角矩形模拟柔光
                        val cr = CornerRadius(size.height / 2f)
                        drawRoundRect(
                            color = primary.copy(alpha = 0.10f + 0.08f * squeeze),
                            topLeft = Offset(-7f, -7f),
                            size = Size(size.width + 14f, size.height + 14f),
                            cornerRadius = CornerRadius(cr.x + 7f)
                        )
                        drawRoundRect(
                            color = secondary.copy(alpha = 0.14f + 0.10f * squeeze),
                            topLeft = Offset(-3f, -3f),
                            size = Size(size.width + 6f, size.height + 6f),
                            cornerRadius = CornerRadius(cr.x + 3f)
                        )
                        drawContent()
                    }
                    .background(
                        Brush.horizontalGradient(listOf(primary, secondary)),
                        shape
                    )
            )
        }

        // ── 彗尾 + 光晕滑块（整层手绘，单节点）──
        val thumbPx = with(density) { 22.dp.toPx() }
        val trailPx = with(density) { trailLen.toPx() }
        val squeezeF = squeeze
        val dir = direction
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    if (widthPx <= 0) return@drawBehind
                    val half = thumbPx / 2f
                    val cx = (fraction * widthPx).coerceIn(half, widthPx - half)
                    val cy = size.height / 2f

                    // 彗尾：沿运动反方向的渐隐光珠
                    if (trailPx > 1f) {
                        for (i in 1..5) {
                            val t = i / 5f
                            val off = t * trailPx * dir
                            drawCircle(
                                color = Color.White.copy(alpha = 0.30f * (1f - t)),
                                radius = thumbPx * 0.30f * (1f - t * 0.75f),
                                center = Offset(cx - off, cy)
                            )
                        }
                    }

                    // 光晕
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primary.copy(alpha = 0.40f + 0.20f * squeezeF),
                                Color.Transparent
                            ),
                            center = Offset(cx, cy),
                            radius = thumbPx * (1.15f + 0.35f * squeezeF)
                        ),
                        radius = thumbPx * (1.15f + 0.35f * squeezeF),
                        center = Offset(cx, cy)
                    )

                    // 核心：白玉球（拖动时横向拉伸）
                    drawCircle(color = Color.Black.copy(alpha = 0.25f), radius = half * 1.04f, center = Offset(cx, cy + 1.5f))
                    drawCircle(color = Color.White, radius = half, center = Offset(cx, cy))
                    drawCircle(
                        brush = Brush.linearGradient(
                            listOf(Color.White, Color.White.copy(alpha = 0.86f)),
                            start = Offset(cx, cy - half),
                            end = Offset(cx, cy + half)
                        ),
                        radius = half,
                        center = Offset(cx, cy)
                    )
                    drawCircle(
                        color = primary.copy(alpha = 0.85f),
                        radius = half,
                        center = Offset(cx, cy),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f * density.density)
                    )
                    // 高光点
                    drawCircle(
                        color = Color.White.copy(alpha = 0.9f),
                        radius = half * 0.28f,
                        center = Offset(cx - half * 0.32f, cy - half * 0.34f)
                    )

                    @Suppress("UNUSED_EXPRESSION") squeezeF
                    @Suppress("UNUSED_EXPRESSION") dir
                }
        )

        // ── 百分比气泡 ──
        AnimatedVisibility(
            visible = dragging,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f)
        ) {
            Text(
                text = "${(fraction * 100).toInt()}%",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .offset(y = 2.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.horizontalGradient(listOf(primary, secondary)))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }

        // 占位结束
    }
}

private fun springLike() = androidx.compose.animation.core.spring<Float>(
    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
)
