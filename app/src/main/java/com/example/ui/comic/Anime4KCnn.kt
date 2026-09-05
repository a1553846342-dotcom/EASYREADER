package com.example.ui.comic

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Anime4K CNN CPU 求值器（第 20 条增强引擎）。
 *
 * 按 GLSL 语义逐层执行 [Anime4KCnnWeights] 的固定权重网络：
 * - 列主序 mat4×vec4：result_row = Σ_col w[col*4+row] · v[col]；
 * - go_0/go_1 = 上一层输出的正/负半波（激活打包）；
 * - 边缘 clamp 采样（等价 texOff）；
 * - Restore 末层输出 3 通道残差 + 原图；Upscale 末层 depth-to-space x2
 *   （4 通道 = 2x2 子像素，(oy%2)*2+(ox%2)），基准 = 原图双线性 2x。
 *
 * 性能：输入先钳制到安全分辨率（RESTORE ≤1280 / UPSCALE ≤900 长边），
 * 全程 FloatArray 单趟，由 ComicPageLoader 的 pixelOpGate 并发闸门串行化。
 */
internal object Anime4KCnn {

    internal class G(val tex: Int, val act: Int, val ox: Int, val oy: Int, val w: FloatArray)
    internal class Layer(val groups: Array<G>, val bias: FloatArray)

    /** 解析扁平权重布局（见 Anime4KCnnWeights 注释） */
    internal fun readFlat(flat: FloatArray): Array<Layer> {
        var cur = 0
        val nLayers = flat[cur++].toInt()
        val layers = arrayOfNulls<Layer>(nLayers)
        for (l in 0 until nLayers) {
            val nGroups = flat[cur++].toInt()
            val bias = FloatArray(4) { flat[cur + it] }
            cur += 4
            val groups = arrayOfNulls<G>(nGroups)
            for (g in 0 until nGroups) {
                val tex = flat[cur++].toInt()
                val act = flat[cur++].toInt()
                val ox = flat[cur++].toInt()
                val oy = flat[cur++].toInt()
                val w = FloatArray(16)
                for (i in 0 until 16) w[i] = flat[cur + i]
                cur += 16
                groups[g] = G(tex, act, ox, oy, w)
            }
            layers[l] = Layer(groups.requireNoNulls(), bias)
        }
        @Suppress("UNCHECKED_CAST")
        return layers as Array<Layer>
    }

    private val restoreLayers by lazy { readFlat(Anime4KCnnWeights.RESTORE_S) }
    private val upscaleLayers by lazy { readFlat(Anime4KCnnWeights.UPSCALE_S) }

    /* ── 基础采样与卷积 ── */

    /** clamp 边缘采样：返回 tex 平面 (x,y) 的 RGBA（act: 0 原始 / 1 正半波 / 2 负半波） */
    private inline fun sample(tex: FloatArray, w: Int, h: Int, x: Int, y: Int, act: Int, out: FloatArray) {
        val cx = x.coerceIn(0, w - 1)
        val cy = y.coerceIn(0, h - 1)
        val i = (cy * w + cx) * 4
        when (act) {
            1 -> { out[0] = max(0f, tex[i]); out[1] = max(0f, tex[i + 1]); out[2] = max(0f, tex[i + 2]); out[3] = max(0f, tex[i + 3]) }
            2 -> { out[0] = max(0f, -tex[i]); out[1] = max(0f, -tex[i + 1]); out[2] = max(0f, -tex[i + 2]); out[3] = max(0f, -tex[i + 3]) }
            else -> { out[0] = tex[i]; out[1] = tex[i + 1]; out[2] = tex[i + 2]; out[3] = tex[i + 3] }
        }
    }

    /** 单层卷积（src0=原图平面，src1=上一层输出；返回新 4 通道平面）。
     *  第六轮第 5 条：行条带多核并行（读 src ±1 行越界只读安全，out 独立缓冲）。 */
    private fun convLayer(layer: Layer, src0: FloatArray, src1: FloatArray, w: Int, h: Int): FloatArray {
        val out = FloatArray(w * h * 4)
        val v = FloatArray(4)
        ComicImagePipeline.parallelStripes(h) { y0, y1 ->
            for (y in y0 until y1) {
                for (x in 0 until w) {
                    var r0 = layer.bias[0]; var r1 = layer.bias[1]; var r2 = layer.bias[2]; var r3 = layer.bias[3]
                    for (g in layer.groups) {
                        sample(if (g.tex == 0) src0 else src1, w, h, x + g.ox, y + g.oy, g.act, v)
                        val wq = g.w
                        r0 += wq[0] * v[0] + wq[4] * v[1] + wq[8] * v[2] + wq[12] * v[3]
                        r1 += wq[1] * v[0] + wq[5] * v[1] + wq[9] * v[2] + wq[13] * v[3]
                        r2 += wq[2] * v[0] + wq[6] * v[1] + wq[10] * v[2] + wq[14] * v[3]
                        r3 += wq[3] * v[0] + wq[7] * v[1] + wq[11] * v[2] + wq[15] * v[3]
                    }
                    val o = (y * w + x) * 4
                    out[o] = r0; out[o + 1] = r1; out[o + 2] = r2; out[o + 3] = r3
                }
            }
        }
        return out
    }

    private fun runNetwork(layers: Array<Layer>, src0: FloatArray, w: Int, h: Int): FloatArray {
        var prev = src0
        for (l in layers) prev = convLayer(l, src0, prev, w, h)
        return prev
    }

    private fun bitmapToPlane(src: Bitmap): FloatArray {
        val w = src.width; val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val plane = FloatArray(w * h * 4)
        for (i in px.indices) {
            val p = px[i]
            val o = i * 4
            plane[o] = ((p shr 16) and 0xFF) / 255f
            plane[o + 1] = ((p shr 8) and 0xFF) / 255f
            plane[o + 2] = (p and 0xFF) / 255f
            plane[o + 3] = 1f   // GLSL MAIN 纹理的 alpha（mpv 挂钩下恒 1）
        }
        return plane
    }

    private fun clamp255(v: Float): Int = (v * 255f).roundToInt().coerceIn(0, 255)

    /* ── 公开 API ── */

    /**
     * Anime4K Restore（轻量档）：线条重建/降噪，不放大。
     * CNN 在 ≤[maxEdge] 的钳制分辨率上运行，残差按 strength 缩放后叠加回原图。
     * 第 5 条：不再内部双线性放大回原尺寸（旧版 1280 钳制→CNN→双线性回 2800
     * 的往返会把重建出的细节整段抹掉，净效果≈轻微模糊——"看不出增强"的病根）；
     * 钳制时返回 CNN 分辨率的结果，由调用方（ComicImagePipeline.anime4kRestore）
     * 负责 Lanczos 回原尺寸 + 全分辨率 CAS 收尾。
     */
    fun restore(src: Bitmap, strength: Float, maxEdge: Int = 1600): Bitmap {
        val w0 = src.width; val h0 = src.height
        if (w0 < 8 || h0 < 8) return src
        val long = max(w0, h0)
        val work = if (long > maxEdge) {
            val s = maxEdge.toFloat() / long
            Bitmap.createScaledBitmap(src, (w0 * s).toInt().coerceAtLeast(8), (h0 * s).toInt().coerceAtLeast(8), true)
        } else src
        val w = work.width; val h = work.height
        val plane = bitmapToPlane(work)
        val out = runNetwork(restoreLayers, plane, w, h)
        // 末层语义（Anime4K v4.0 Restore GLSL：SAVE = conv + HOOKED_tex）：
        // runNetwork 返回的是"卷积增量"，完整输出 = 原图 + 增量，strength 缩放增量。
        // 第六轮第 5 条（视觉终审）：CNN 残差全量应用在噪声底上产生"整体提亮+
        // 线条变薄"的负向观感（代理判定 worse than original）——残差固定 0.6
        // 上限系数，弱化网络对底色的整体重投影，主要效果交给下游线重建。
        val px = IntArray(w * h)
        for (i in px.indices) {
            val o = i * 4
            val d = 0.6f * (if (strength >= 0.999f) 1f else strength)
            val r = plane[o] + out[o] * d
            val g = plane[o + 1] + out[o + 1] * d
            val b = plane[o + 2] + out[o + 2] * d
            px[i] = (0xFF shl 24) or (clamp255(r) shl 16) or (clamp255(g) shl 8) or clamp255(b)
        }
        val restored = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        restored.setPixels(px, 0, w, 0, 0, w, h)
        // 第六轮第 5 条：回程用精确目标尺寸（比例缩放的舍入会 ±2px）
        return if (w != w0 || h != h0) ComicImagePipeline.lanczosScaleTo(restored, w0, h0) else restored
    }

    /**
     * Anime4K Upscale x2（完整档）：CNN 2x 超分（深度到空间 + 双线性基准 + 亮度残差）。
     * 第 5 条：[maxSrcEdge] 由调用方按"目标尺寸的一半"显式传入（输出恒 ≥ 原分辨率）；
     * 旧默认 900 使 2800px 原图先缩到 900 再 2x=1800——"超分"实际是降级，病根之一。
     * 预算上限 1600（输入 1.7M px 时网络平面 ~28MB×3，内存护栏）。
     */
    fun upscale2x(src: Bitmap, strength: Float, maxSrcEdge: Int = 1600): Bitmap {
        val w0 = src.width; val h0 = src.height
        if (w0 < 8 || h0 < 8) return src
        val long = max(w0, h0)
        val work = if (long > maxSrcEdge) {
            val s = maxSrcEdge.toFloat() / long
            Bitmap.createScaledBitmap(src, (w0 * s).toInt().coerceAtLeast(8), (h0 * s).toInt().coerceAtLeast(8), true)
        } else src
        val w = work.width; val h = work.height
        val plane = bitmapToPlane(work)
        val conv = runNetwork(upscaleLayers, plane, w, h)
        val d = if (strength >= 0.999f) 1f else strength
        // depth-to-space：out(ox,oy) = bilinear(src, ox/2, oy/2) + conv[cx,cy][(oy%2)*2+(ox%2)]（强度缩放）
        val ow = w * 2; val oh = h * 2
        val px = IntArray(ow * oh)
        for (oy in 0 until oh) {
            val cy = oy ushr 1
            val fy = (oy + 0.5f) / 2f - 0.5f
            val sy0 = fy.toInt(); val sy1 = min(sy0 + 1, h - 1)
            val wy = fy - sy0
            val sy0c = max(0, sy0)
            for (ox in 0 until ow) {
                val cx = ox ushr 1
                val fx = (ox + 0.5f) / 2f - 0.5f
                val sx0 = fx.toInt(); val sx1 = min(sx0 + 1, w - 1)
                val wx = fx - sx0
                val sx0c = max(0, sx0)
                fun bil(ch: Int): Float {
                    val i00 = (sy0c * w + sx0c) * 4 + ch
                    val i01 = (sy0c * w + sx1) * 4 + ch
                    val i10 = (sy1 * w + sx0c) * 4 + ch
                    val i11 = (sy1 * w + sx1) * 4 + ch
                    val top = plane[i00] + (plane[i01] - plane[i00]) * wx
                    val bot = plane[i10] + (plane[i11] - plane[i10]) * wx
                    return top + (bot - top) * wy
                }
                val ci = (cy * w + cx) * 4 + ((oy and 1) * 2 + (ox and 1))
                val residual = conv[ci] * d
                val r = bil(0) + residual
                val g = bil(1) + residual
                val b = bil(2) + residual
                px[oy * ow + ox] = (0xFF shl 24) or (clamp255(r) shl 16) or (clamp255(g) shl 8) or clamp255(b)
            }
        }
        val outBmp = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
        outBmp.setPixels(px, 0, ow, 0, 0, ow, oh)
        return outBmp
    }
}
