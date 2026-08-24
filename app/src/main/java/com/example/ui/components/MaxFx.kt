/*
 * MAX 画质专属特效集：
 *  - GravitySensor：全局重力传感器单例（引用计数启停，低通滤波）
 *  - Modifier.maxGravityParticles()：按钮/开关内部的"液态微粒"，
 *    随设备倾角受重力运动、边界反弹，仅 MAX 档激活
 *  - Modifier.glassSheen()：玻璃卡表面周期性掠过的高光带（旋转渐变）
 *
 * 全部基于 Canvas/drawBehind 单节点绘制，无 RenderEffect/着色器依赖；
 * 仅在 MAX 档挂载；组件离开组合树后传感器与帧循环自动停止。
 */
package com.example.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.hypot
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

    /** 当前重力方向（已低通滤波）。任意线程可读。 */
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
                val dt = if (lastNanos == 0L) 1f / 60f
                else ((now - lastNanos) / 1_000_000_000f).coerceIn(1f / 240f, 0.06f)
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
