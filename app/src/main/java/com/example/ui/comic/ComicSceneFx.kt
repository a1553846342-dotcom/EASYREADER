package com.example.ui.comic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 场景粒子引擎（新反馈第 8 条重做）。
 *
 * 移植与参照（完整 clone 于 .tmp-repos/，版权与来源见各仓库 LICENSE）：
 * - **PiotrPrus/ParticleEmitter**（MIT）— Canvas 粒子引擎核心：逐帧增量 Euler 积分
 *   `v += a·dt; p += v·dt`、重力矢量（强度+角度）、发射速率累积器、边缘行为
 *   （穿越/回绕/回收）、生命周期 + alpha/scale 曲线、withFrameNanos +
 *   Dispatchers.Default 的模拟线程模型（CanvasEmitter.kt 的结构整体移植）。
 * - **MatteoBattilana/WeatherView**（Apache-2.0）— 雨滴=沿速度矢量的
 *   透明→白→白→透明渐变划线（运动模糊）+ 雪花=径向渐变软圆（sigmoid 深度控制
 *   透明带）的真实感渲染（WeatherConfettoGenerator.kt 交叉参照）。
 * - **jhammann/sakura**（MIT）— 花瓣"fall 线性下落 + blow 慢速定风向横漂 +
 *   sway 非对称旋转摆动（-5°→28°→3°、周期 2-4s）+ 生命期 0.9→0.2 淡出"的
 *   三运动叠加模型（src/sakura.js + dist/sakura.css 关键帧移植到
 *   withFrameNanos 驱动的逐帧函数）。
 * - **skydoves/compose-animations**（Apache-2.0）— "Wave Field" 多层正弦海浪：
 *   逐层频率爬升/振幅衰减/相位视差 + 二次谐波，参数取自 AnimationExample17。
 * - **fidloo/flux**（Apache-2.0）— 海边/夏夜的天空渐变铺垫与云层漂移氛围层
 *   （DynamicWeatherLandscape/Clouds 的分层构图参照；云改为程序化椭圆雾团，
 *   不依赖图片资源）。
 *
 * 与阅读内容的合成约束：全部层使用半透明（天空/海浪为氛围层，α≤0.5），
 * 漫画画面始终可读；发光体（萤火/火星/星）用 BlendMode.Plus 叠加。
 */
private class FxParticle(
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var size: Float = 4f,
    var alphaBase: Float = 0.6f,
    var life: Float = 0f,
    var maxLife: Float = 10f,
    var seed: Float = 0f,
    var depth: Float = 1f,      // 景深 0(远)..1(近)
) {
    val alphaFrac: Float get() = (life / maxLife).coerceIn(0f, 1f)
}

/** 雨滴落地涟漪（独立短命实体） */
private class FxRipple(var x: Float = 0f, var y: Float = 0f, var t: Float = 0f, var dur: Float = 0.7f, var r: Float = 10f, var seed: Float = 0f)

/* ── Wave Field（skydoves 参数直取） ── */
private const val WAVE_LAYERS = 4
private const val WAVE_BASE_FREQ = 0.010f
private const val WAVE_FREQ_RAMP = 0.18f
private const val WAVE_AMPL_FALL_OFF = 0.08f
private const val WAVE_PHASE_RAMP = 0.35f
private const val WAVE_PHASE_SPEED = 2.2f
private const val WAVE_SECONDARY_RATIO = 2.7f
private const val WAVE_SECONDARY_AMP = 0.35f
private val WaveFront = Color(0xFF7EC9EB)
private val WaveBack = Color(0xFF1A237E)

private fun waveY(x: Float, baseline: Float, frequency: Float, amplitude: Float, phase: Float): Float {
    val primary = sin(x * frequency + phase) * amplitude
    val secondary = sin(x * frequency * WAVE_SECONDARY_RATIO + phase * 1.7f) * amplitude * WAVE_SECONDARY_AMP
    return baseline + primary + secondary
}

/* ── sakura sway 拟合（jhammann 关键帧 -5°→28°→3°、周期 2-4s 的非对称摆动） ── */
private fun sakuraSwayDeg(t: Float, period: Float, seed: Float): Float {
    val ph = 2f * PI.toFloat() * (t / period + seed)
    // 基波 + 二次谐波构造非对称（峰值偏前 40%），叠加中位偏移——与 CSS 关键帧同族
    return 8f + 16f * sin(ph) + 7f * sin(2f * ph + 0.9f)
}

/** 预生成的樱花花瓣形状（尺寸变体，避免每帧 Path 分配） */
private val sakuraPetalShapes: List<Path> = (0 until 24).map { i ->
    val sp = i / 24f
    val rx = 4.6f + sp * 3.2f
    Path().apply {
        moveTo(-rx, 0f)
        cubicTo(-rx * 0.4f, -rx * 0.75f, rx * 0.5f, -rx * 0.6f, rx, 0f)
        cubicTo(rx * 0.5f, rx * 0.6f, -rx * 0.4f, rx * 0.75f, -rx, 0f)
        close()
    }
}

@Composable
fun ComicSceneEffectOverlay(scene: ComicScene, modifier: Modifier = Modifier) {
    if (scene == ComicScene.NONE) return
    val currentScene by rememberUpdatedState(scene)
    // 模拟状态：场景变化时整体重建（引擎持粒子数组，帧内原地变异，零分配）
    var engine by remember { mutableStateOf(ComicSceneFxEngine(currentScene)) }
    LaunchedEffect(currentScene) { engine = ComicSceneFxEngine(currentScene) }
    var frameTick by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
                    // 模拟在 Default 线程（ParticleEmitter CanvasEmitter 同款线程模型）
                    // — 这里是同步小规模（≤160 粒子）模拟，直接在帧内完成，避免
                    // 线程切换延迟吃掉帧预算；draw 阶段只读
                    engine.step(dt, now / 1_000_000_000f)
                    frameTick = now
                }
                last = now
            }
        }
    }

    Canvas(modifier.fillMaxSize()) {
        @Suppress("UNUSED_EXPRESSION") frameTick // 触发重绘的时间戳
        engine.draw(this, currentScene)
    }
}

/** 场景引擎：粒子状态 + 逐场景 step/draw */
private class ComicSceneFxEngine(initialScene: ComicScene) {
    private val rnd = Random(initialScene.ordinal * 7919 + 13)
    val particles = ArrayList<FxParticle>(160)
    val ripples = ArrayList<FxRipple>(24)
    private var w = 0f
    private var h = 0f
    private var time = 0f
    private var scene: ComicScene = initialScene
    private var spawned = false

    /** 主导风（px/s）：场景级慢变阵风（blow 模型：定向下限 + 缓慢起伏） */
    private var wind = 0f

    fun step(dt: Float, absTime: Float) {
        time += dt
        wind = sin(absTime * 0.11f) * 22f + sin(absTime * 0.043f) * 30f
        if (!spawned) return
        when (scene) {
            ComicScene.RAIN -> stepRain(dt)
            ComicScene.SNOW -> stepSnow(dt)
            ComicScene.SAKURA -> stepSakura(dt)
            ComicScene.FIREFLY -> stepFirefly(dt)
            ComicScene.CAMPFIRE -> stepCampfire(dt)
            ComicScene.OCEAN, ComicScene.NIGHT -> stepDrift(dt)
            ComicScene.NONE -> Unit
        }
    }

    /** 视口尺寸首次到达 / 变化时布种 */
    private fun ensureSpawn(vw: Float, vh: Float) {
        if (spawned && abs(vw - w) < 1f && abs(vh - h) < 1f) return
        w = vw; h = vh
        spawned = true
        particles.clear()
        ripples.clear()
        val count = when (scene) {
            ComicScene.RAIN -> 130
            ComicScene.SNOW -> 90
            ComicScene.SAKURA -> 44
            ComicScene.FIREFLY -> 26
            ComicScene.CAMPFIRE -> 34
            ComicScene.OCEAN -> 16
            ComicScene.NIGHT -> 46
            ComicScene.NONE -> 0
        }
        repeat(count) {
            val p = FxParticle(
                x = rnd.nextFloat() * w,
                y = rnd.nextFloat() * h,
                depth = when (scene) {
                    ComicScene.RAIN, ComicScene.SNOW -> (it % 3) / 2f * 0.8f + 0.2f  // 三层景深
                    else -> rnd.nextFloat()
                },
                seed = rnd.nextFloat(),
                maxLife = when (scene) {
                    ComicScene.SAKURA -> 14f + rnd.nextFloat() * 8f
                    ComicScene.CAMPFIRE -> 1.2f + rnd.nextFloat() * 1.4f
                    ComicScene.FIREFLY, ComicScene.OCEAN, ComicScene.NIGHT -> 8f + rnd.nextFloat() * 8f
                    else -> 30f
                },
            )
            p.life = rnd.nextFloat() * p.maxLife
            particles.add(p)
        }
    }

    /* ── 雨：重力加速 + 终端速度 + 风（WeatherView 运动模糊渲染在 draw 侧） ── */
    private fun stepRain(dt: Float) {
        for (p in particles) {
            p.life += dt
            val term = 420f + 640f * p.depth                       // 终端速度（景深）
            val g = 2400f * (0.5f + p.depth)                        // 重力（ParticleEmitter a·dt）
            p.vy = min(p.vy + g * dt, term)
            p.vx = wind * (0.5f + p.depth) + sin(time * 0.7f + p.seed * 9f) * 18f
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.y > h) {
                // 回收：顶部重生（EdgeBehavior.Wrap 语义）
                if (ripples.size < 22 && rnd.nextFloat() < 0.5f) {
                    ripples.add(FxRipple(p.x, h - 3f - rnd.nextFloat() * 9f, 0f, 0.55f + rnd.nextFloat() * 0.4f, 4f + rnd.nextFloat() * 8f, p.seed))
                }
                respawnTop(p)
            }
            if (p.x < -40f) p.x = w + 20f
            if (p.x > w + 40f) p.x = -20f
        }
        stepRipples(dt)
    }

    private fun respawnTop(p: FxParticle) {
        p.y = -30f - rnd.nextFloat() * 80f
        p.x = rnd.nextFloat() * (w + 120f) - 60f
        p.vy = 40f
        p.life = 0f
    }

    private fun stepRipples(dt: Float) {
        val it = ripples.iterator()
        while (it.hasNext()) {
            val r = it.next()
            r.t += dt
            if (r.t > r.dur) it.remove()
        }
    }

    /* ── 雪：低重力 + 终端小 + 横向摆动（sin 漂移，非直线） ── */
    private fun stepSnow(dt: Float) {
        for (p in particles) {
            p.life += dt
            val term = 24f + 70f * p.depth
            p.vy = min(p.vy + 140f * dt, term)
            p.vx = wind * 0.4f + sin(time * (0.5f + p.seed) + p.seed * 20f) * (10f + 26f * p.depth)
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.y > h + 20f) {
                p.y = -16f
                p.x = rnd.nextFloat() * w
                p.vy = 0f
                p.life = 0f
            }
            if (p.x < -30f) p.x = w + 10f
            if (p.x > w + 30f) p.x = -10f
        }
    }

    /* ── 樱花（jhammann/sakura 三运动叠加）：fall + blow + sway ── */
    private fun stepSakura(dt: Float) {
        for (p in particles) {
            p.life += dt
            if (p.life > p.maxLife) {
                p.life = 0f
                p.y = -24f
                p.x = rnd.nextFloat() * w
            }
            // fall：近似线性的缓慢下落（fallSpeed 语义）
            p.vy = 26f + 44f * p.depth
            // blow：慢速定向横漂（soft/medium-left/right 语义 → wind + 每瓣固定偏向）
            val blowDir = if (p.seed > 0.5f) 1f else -1f
            val blow = wind * 0.6f + blowDir * (12f + 30f * p.seed)
            // sway：横向摆动（2-4s 周期）
            val swayPeriod = 2f + p.seed * 2f
            p.vx = blow + cos(time * 2f * PI.toFloat() / swayPeriod + p.seed * 12f) * 26f
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.x < -40f) p.x = w + 20f
            if (p.x > w + 40f) p.x = -20f
            if (p.y > h + 30f) {
                p.y = -24f
                p.x = rnd.nextFloat() * w
                p.life = 0f
            }
        }
    }

    /* ── 萤火：随机游走（Ornstein-Uhlenbeck 型速度扰动 + 阻尼 + 软边界回航） ── */
    private fun stepFirefly(dt: Float) {
        for (p in particles) {
            p.life += dt
            if (p.life > p.maxLife) {
                p.life = 0f
                p.x = rnd.nextFloat() * w
                p.y = rnd.nextFloat() * h
            }
            // 随机加速度 + 轻阻尼（随机游走，不是直线）
            val sigma = 140f * (0.5f + p.seed)
            p.vx += (rnd.nextFloat() - 0.5f) * sigma * dt
            p.vy += (rnd.nextFloat() - 0.5f) * sigma * dt
            p.vx *= (1f - 0.6f * dt)
            p.vy *= (1f - 0.6f * dt)
            val sp = kotlin.math.hypot(p.vx, p.vy)
            if (sp > 46f) { p.vx *= 46f / sp; p.vy *= 46f / sp }
            // 软边界回航（离边 <60px 时向内转向力）
            if (p.x < 60f) p.vx += 60f * dt * 3f
            if (p.x > w - 60f) p.vx -= 60f * dt * 3f
            if (p.y < 60f) p.vy += 60f * dt * 3f
            if (p.y > h - 60f) p.vy -= 60f * dt * 3f
            p.x += p.vx * dt
            p.y += p.vy * dt
        }
    }

    /* ── 篝火火星（ParticleEmitter 上抛+回拉重力+生命淡出；火焰本体在 draw 侧形变） ── */
    private fun stepCampfire(dt: Float) {
        val cx = w * 0.5f
        for (p in particles) {
            p.life += dt
            if (p.life > p.maxLife) {
                p.life = 0f
                p.maxLife = 1.2f + rnd.nextFloat() * 1.4f
                p.x = cx + (rnd.nextFloat() - 0.5f) * w * 0.12f
                p.y = h * 0.94f - rnd.nextFloat() * 30f
                p.vy = -(110f + rnd.nextFloat() * 120f) * (0.6f + p.depth)
                p.vx = (rnd.nextFloat() - 0.5f) * 36f
                p.seed = rnd.nextFloat()
            }
            p.vy += 46f * dt            // 轻重力（上抛后减速、飘落）
            p.vx += sin(time * 2.2f + p.seed * 17f) * 30f * dt   // 火焰热扰动摇摆
            p.x += p.vx * dt
            p.y += p.vy * dt
        }
    }

    /* ── 海边飞沫/夏夜星尘的缓漂（flux 云漂移语义的轻量粒子化） ── */
    private fun stepDrift(dt: Float) {
        for (p in particles) {
            p.life += dt
            if (p.life > p.maxLife) p.life = 0f
            p.vx = wind * 0.25f + sin(time * 0.16f + p.seed * 9f) * 14f
            p.x += p.vx * dt
            p.y += (if (scene == ComicScene.OCEAN) -6f else 3f) * p.depth * dt * (if (scene == ComicScene.OCEAN) 8f else 2f) +
                sin(time * 0.4f + p.seed * 31f) * 4f * dt
            if (p.x < -30f) p.x = w + 20f
            if (p.x > w + 30f) p.x = -20f
            if (p.y < -30f) p.y = h + 20f
            if (p.y > h + 30f) p.y = -20f
        }
    }

    /* ════════════════ 绘制 ════════════════ */

    fun draw(scope: DrawScope, scene: ComicScene) {
        ensureSpawn(scope.size.width, scope.size.height)
        if (!spawned) return
        if (this.scene != scene) this.scene = scene
        with(scope) {
            when (scene) {
                ComicScene.RAIN -> drawRain()
                ComicScene.SNOW -> drawSnow()
                ComicScene.SAKURA -> drawSakura()
                ComicScene.FIREFLY -> drawFireflies()
                ComicScene.CAMPFIRE -> drawCampfire()
                ComicScene.OCEAN -> drawOcean()
                ComicScene.NIGHT -> drawNight()
                ComicScene.NONE -> Unit
            }
        }
    }

    /** 雨（WeatherView 运动模糊）：沿速度矢量的渐变软尖划线 */
    private fun DrawScope.drawRain() {
        for (p in particles) {
            val stretch = (10f + 14f * p.depth) * (0.7f + 0.6f * p.seed)
            val len = kotlin.math.hypot(p.vx, p.vy).coerceAtLeast(1f)
            val ux = p.vx / len
            val uy = p.vy / len
            val x1 = p.x - ux * stretch
            val y1 = p.y - uy * stretch
            val x2 = p.x + ux * stretch
            val y2 = p.y + uy * stretch
            val a = (0.24f + 0.4f * p.depth) * (0.7f + 0.3f * p.seed)
            drawLine(
                brush = Brush.linearGradient(
                    0f to Color(0xFFB8C8DC).copy(alpha = 0f),
                    0.45f to Color(0xFFD6E4F2).copy(alpha = a),
                    0.55f to Color(0xFFE8F0FA).copy(alpha = a),
                    1f to Color(0xFFB8C8DC).copy(alpha = 0f),
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                ),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = (0.9f + 0.9f * p.depth) * (0.8f + 0.4f * p.seed),
            )
        }
        // 落地涟漪（扩散椭圆 + 渐隐）
        for (r in ripples) {
            val t = r.t / r.dur
            drawOval(
                color = Color(0xFF9EC4DE).copy(alpha = (1f - t) * 0.30f),
                topLeft = Offset(r.x - r.r * (0.5f + t), r.y - r.r * (0.2f + t * 0.4f)),
                size = androidx.compose.ui.geometry.Size(r.r * (1f + 2f * t), r.r * (0.4f + 0.8f * t)),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.1f * (1f - t) + 0.3f),
            )
        }
    }

    /** 雪（WeatherView 径向渐变 + 第四轮景深分层保留） */
    private fun DrawScope.drawSnow() {
        for (p in particles) {
            val near = p.depth > 0.6f
            val radius = if (near) 3.2f + p.seed * 3.4f else 1.3f + p.seed * 2.1f
            if (near) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0x40FFFFFF), Color.Transparent),
                        center = Offset(p.x, p.y), radius = radius * 2.2f,
                    ),
                    radius = radius * 2.2f, center = Offset(p.x, p.y),
                )
                drawCircle(Color.White.copy(alpha = 0.65f + p.seed * 0.3f), radius, Offset(p.x, p.y))
            } else {
                // 远景：径向渐变软圆（透明带随深度 sigmoid 前移——WeatherView 模型）
                val sigmoid = 1f / (1f + kotlin.math.exp(-(p.depth * 2f - 1f)))
                val coreStop = 0.15f + sigmoid * 0.30f
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to Color(0xFFC9D9EA).copy(alpha = 0.16f + p.seed * 0.26f),
                        coreStop to Color(0xFFC9D9EA).copy(alpha = (0.16f + p.seed * 0.26f) * 0.6f),
                        1f to Color.Transparent,
                        center = Offset(p.x, p.y), radius = radius * 1.6f,
                    ),
                    radius = radius * 1.6f, center = Offset(p.x, p.y),
                )
            }
        }
    }

    /** 樱花：花瓣形 + sway 旋转 + 生命淡出（sakura 0.9→0.2） */
    private fun DrawScope.drawSakura() {
        val shapes = sakuraPetalShapes
        for (p in particles) {
            val lifeFrac = p.alphaFrac
            // 生命期 0.9 → 0.2 淡出（fall 关键帧语义）
            val alpha = (0.9f - 0.7f * lifeFrac) * (0.55f + 0.45f * p.depth)
            val rot = sakuraSwayDeg(time, 2f + p.seed * 2f, p.seed)
            val idx = ((p.seed * shapes.size).toInt()) % shapes.size
            val pink = if (p.seed > 0.5f) Color(0xFFF6C6D9) else Color(0xFFFADCE8)
            withTransform({ translate(p.x, p.y); rotate(rot) }) {
                drawPath(shapes[idx], pink.copy(alpha = alpha))
            }
        }
    }

    /** 萤火：呼吸发光（Plus 混合）+ 随机游走轨迹 */
    private fun DrawScope.drawFireflies() {
        for (p in particles) {
            // 呼吸感亮度（非固定亮度）
            val pulse = 0.5f + 0.5f * sin(time * (1.1f + p.seed * 2.2f) + p.seed * 30f)
            val glowA = 0.12f + 0.75f * pulse
            val glowR = 7f + 6f * pulse + p.depth * 4f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFD8FF9E).copy(alpha = glowA), Color.Transparent),
                    center = Offset(p.x, p.y), radius = glowR * 2.4f,
                ),
                radius = glowR * 2.4f, center = Offset(p.x, p.y),
                blendMode = BlendMode.Plus,
            )
            drawCircle(Color(0xFFF4FFD0).copy(alpha = 0.35f + 0.6f * pulse), 1.6f + p.depth, Offset(p.x, p.y), blendMode = BlendMode.Plus)
        }
    }

    /** 篝火：火焰本体 Path 形变（withFrameNanos 驱动的逐帧 Path 变形）+ 发光火星 */
    private fun DrawScope.drawCampfire() {
        val cx = w * 0.5f
        val baseY = h * 0.965f
        // 火焰本体：3 层舌尖，控制点随时间正弦摆动（skydoves Canvas 物理动效思路）
        val tongues = listOf(0xFFE25822.toInt(), 0xFFF77622.toInt(), 0xFFFFC53D.toInt())
        tongues.forEachIndexed { layer, color ->
            val tW = w * (0.10f + 0.055f * layer)          // 逐层收窄
            val tH = (110f + 50f * (2 - layer)) * (1f + 0.08f * sin(time * 5.1f + layer * 2.1f))
            val swayX = sin(time * (3.4f + layer * 0.8f) + layer * 1.7f) * (14f + 7f * (2 - layer))
            val flicker = 0.82f + 0.18f * sin(time * 9.3f + layer * 4.4f)
            val path = Path()
            path.moveTo(cx - tW, baseY)
            path.quadraticBezierTo(
                cx - tW * 0.8f + swayX * 0.4f, baseY - tH * 0.45f,
                cx + swayX, baseY - tH * flicker,
            )
            path.quadraticBezierTo(
                cx + tW * 0.8f + swayX * 0.4f, baseY - tH * 0.45f,
                cx + tW, baseY,
            )
            path.close()
            drawPath(path, Color(color).copy(alpha = (0.5f - 0.12f * layer)))
        }
        // 火焰辉光
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0x30FFB25E), Color.Transparent),
                center = Offset(cx, baseY - 40f), radius = w * 0.32f,
            ),
            radius = w * 0.32f, center = Offset(cx, baseY - 40f),
        )
        // 火星：上飘 + 发光 + 渐隐（生命 (1-frac) 淡出）
        for (p in particles) {
            val fade = (1f - p.alphaFrac).coerceIn(0f, 1f)
            val a = 0.75f * fade * fade
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFFFB25E).copy(alpha = a), Color.Transparent),
                    center = Offset(p.x, p.y), radius = (2.2f + 2.6f * p.depth),
                ),
                radius = 2.2f + 2.6f * p.depth, center = Offset(p.x, p.y),
                blendMode = BlendMode.Plus,
            )
            drawCircle(Color(0xFFFFD9A0).copy(alpha = a), 1.1f + p.depth * 0.9f, Offset(p.x, p.y), blendMode = BlendMode.Plus)
        }
    }

    /** 海边：Wave Field 多层正弦水面 + 飞沫 + 黄昏氛围天（flux 构图） */
    private fun DrawScope.drawOcean() {
        val waveTop = h * 0.80f
        // 天空氛围（flux：黄昏渐变，克制 α 不盖阅读内容）
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF0A0E27).copy(alpha = 0.16f), Color(0xFF2E3A67).copy(alpha = 0.05f), Color.Transparent),
                startY = 0f, endY = waveTop,
            ),
            size = size,
        )
        // 水面（skydoves Wave Field：4 层 + 频率/振幅/相位爬升视差 + 二次谐波）
        val baseline0 = h * 0.97f
        val stepPx = 6f
        for (layer in (WAVE_LAYERS - 1) downTo 0) {
            val layerT = layer / (WAVE_LAYERS - 1).toFloat()
            val freq = WAVE_BASE_FREQ * (1f + layer * WAVE_FREQ_RAMP)
            val ampl = (h * 0.018f) * (1f - layer * WAVE_AMPL_FALL_OFF).coerceAtLeast(0.05f)
            val phase = time * WAVE_PHASE_SPEED * (1f + layer * WAVE_PHASE_RAMP) + layer * (PI.toFloat() / WAVE_LAYERS)
            val baseColor = lerp(WaveFront, WaveBack, layerT)
            // 氛围层：整体 α 压低（0.34 前 → 0.12 后），漫画保持可读
            val layerColor = baseColor.copy(alpha = 0.12f + 0.22f * (1f - layerT))
            val path = Path()
            var x = 0f
            path.moveTo(0f, waveY(x, baseline0 - layer * h * 0.012f, freq, ampl, phase))
            while (x < w) {
                x = min(x + stepPx, w)
                path.lineTo(x, waveY(x, baseline0 - layer * h * 0.012f, freq, ampl, phase))
            }
            path.lineTo(w, h)
            path.lineTo(0f, h)
            path.close()
            drawPath(path, layerColor)
        }
        // 飞沫光点（近水面缓漂）
        for (p in particles) {
            val a = 0.10f + 0.14f * (0.5f + 0.5f * sin(time * 0.8f + p.seed * 21f))
            drawCircle(Color(0xFFDDE8F2).copy(alpha = a), 1f + p.seed * 1.8f, Offset(p.x, p.y * 0.2f + waveTop))
        }
    }

    /** 夏夜：星光层次（呼吸闪烁）+ 萤火点缀 + 薄云漂移（flux 分层构图） */
    private fun DrawScope.drawNight() {
        // 夜幕氛围（顶部深蓝，克制 α）
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF070B1E).copy(alpha = 0.18f), Color(0xFF101B3A).copy(alpha = 0.06f), Color.Transparent),
                startY = 0f, endY = h * 0.7f,
            ),
            size = size,
        )
        for (p in particles) {
            val twinkle = 0.5f + 0.5f * sin(time * (0.8f + p.seed * 2.6f) + p.seed * 40f)
            val layerBright = 0.25f + 0.6f * p.depth
            val a = layerBright * (0.35f + 0.65f * twinkle)
            if (p.seed > 0.28f) {
                // 星：小亮点 + 闪烁（个别大星带十字光芒）
                drawCircle(Color(0xFFEAF2FF).copy(alpha = a), 0.8f + p.depth * 1.6f, Offset(p.x, p.y), blendMode = BlendMode.Plus)
                if (p.seed > 0.93f) {
                    val len = 4f + 5f * twinkle
                    drawLine(Color(0xFFEAF2FF).copy(alpha = a * 0.6f), Offset(p.x - len, p.y), Offset(p.x + len, p.y), 0.8f)
                    drawLine(Color(0xFFEAF2FF).copy(alpha = a * 0.6f), Offset(p.x, p.y - len), Offset(p.x, p.y + len), 0.8f)
                }
            } else if (p.seed > 0.12f) {
                // 萤火点缀（下半屏，呼吸发光）
                val y = p.y * 0.5f + h * 0.5f
                val glowA = (0.1f + 0.5f * twinkle) * p.depth
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFFD8FF9E).copy(alpha = glowA), Color.Transparent),
                        center = Offset(p.x, y), radius = 9f,
                    ),
                    radius = 9f, center = Offset(p.x, y), blendMode = BlendMode.Plus,
                )
            }
        }
        // 薄云：2-3 团椭圆雾缓慢漂移（flux Clouds 意象的程序化实现）
        val cloudCount = 3
        for (i in 0 until cloudCount) {
            val seedC = i * 0.37f + 0.11f
            val cy = h * (0.10f + 0.09f * i)
            val speed = (6f + 10f * seedC)
            val cxPos = ((time * speed + seedC * w * 3f) % (w + 420f)) - 210f
            val cw = w * (0.30f + 0.12f * i)
            drawOval(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF93A7CE).copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(cxPos, cy), radius = cw * 0.5f,
                ),
                topLeft = Offset(cxPos - cw * 0.5f, cy - 36f),
                size = androidx.compose.ui.geometry.Size(cw, 72f),
            )
        }
    }
}
