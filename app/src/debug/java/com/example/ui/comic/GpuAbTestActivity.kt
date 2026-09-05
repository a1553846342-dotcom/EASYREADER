package com.example.ui.comic

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Bundle
import android.view.Surface
import java.io.File
import kotlin.math.abs
import kotlin.system.measureTimeMillis

/**
 * 仅 debug 变体：GPU Shader 滤镜迁移 A/B 测量（任务书 P2 遗留项）。
 * 同一真实样张（app files 内 4000x5600 hires 页，CPU 侧先按管线 2800 上限降采样）
 * 分别走 CPU 管线 / RenderEffect ColorMatrix / AGSL RuntimeShader（色相+卷积锐化），
 * 各 ×10 取中位，结果写 logcat（tag GpuAbTest）。
 * 注意：模拟器为 swiftshader 软件渲染，GPU 侧数据仅作参考，须明确标注。
 */
class GpuAbTestActivity : Activity() {

    private val agslSrc = """
        uniform shader imgData;
        half4 main(float2 p) {
            half4 c = imgData.eval(p);
            half4 blur = (imgData.eval(p + float2(1.0, 0.0)) + imgData.eval(p - float2(1.0, 0.0)) +
                          imgData.eval(p + float2(0.0, 1.0)) + imgData.eval(p - float2(0.0, 1.0))) * 0.25;
            c = c + (c - blur) * 0.6;
            float l = dot(c.rgb, half3(0.299, 0.587, 0.114));
            c.rgb = mix(half3(l), c.rgb, 0.7);
            return c;
        }
    """.trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread { runAb(); finish() }.start()
    }

    private fun median(xs: List<Long>) = xs.sorted()[xs.size / 2]

    private fun runAb() {
        val log = { s: String -> android.util.Log.i("GpuAbTest", s) }
        // 1. 加载真实样张（与阅读器一致的目录）
        val dir = File(filesDir, "comics_1788008756271")
        val src = dir.listFiles()?.firstOrNull { it.name.startsWith("img_0005") }?.let {
            BitmapFactory.decodeFile(it.absolutePath)
        }
        if (src == null) { log("SAMPLE_MISSING dir=${dir.exists()}"); return }
        log("SAMPLE ${src.width}x${src.height}")

        // CPU 侧输入 = 管线上限 2800 长边（与阅读器 capEdge 一致）
        val scale = 2800f / maxOf(src.width, src.height)
        val cpuIn = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true) else src
        log("CPU_INPUT ${cpuIn.width}x${cpuIn.height}")

        val tone = ComicImagePipeline.Toning(
            brightness = 20, contrast = 15, saturation = -30, sharpen = 40,
        )
        val geo = ComicImagePipeline.Geometry()

        // —— CPU 管线 ×10 ——
        val cpuTimes = ArrayList<Long>()
        var cpuOut: Bitmap? = null
        repeat(10) {
            val t = measureTimeMillis { cpuOut = ComicImagePipeline.process(cpuIn, geo, tone) }
            cpuTimes.add(t)
        }
        log("CPU_PIPELINE_MS med=${median(cpuTimes)} all=${cpuTimes}")

        // —— GPU 侧输入与 CPU 同尺寸 ——
        val w = cpuIn.width; val h = cpuIn.height
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            // RenderEffect ColorMatrix（API 31+ 路径，saturation 0.7）
            val cm = android.graphics.ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0.7f) })
            val gpuTimesCM = ArrayList<Long>()
            repeat(10) {
                val t = measureTimeMillis {
                    renderWithEffect(w, h, cpuIn, RenderEffect.createColorFilterEffect(cm))
                }
                gpuTimesCM.add(t)
            }
            log("GPU_COLORMATRIX_MS med=${median(gpuTimesCM)} all=${gpuTimesCM}")

            // AGSL RuntimeShader（API 33+，色相/饱和 + 3x3 卷积锐化一体）
            val shader = RuntimeShader(agslSrc)
            val gpuTimesAgsl = ArrayList<Long>()
            repeat(10) {
                val t = measureTimeMillis {
                    renderWithEffect(w, h, cpuIn, RenderEffect.createRuntimeShaderEffect(shader, "imgData"))
                }
                gpuTimesAgsl.add(t)
            }
            log("GPU_AGSL_MS med=${median(gpuTimesAgsl)} all=${gpuTimesAgsl}")
        } else {
            log("GPU_SKIP sdk=${android.os.Build.VERSION.SDK_INT}")
        }
        log("AB_DONE renderer=swiftshader_software(emulator) NOTE_GPU_DATA_REFERENCE_ONLY")
    }

    /** 离屏硬件渲染 + 读回（ImageReader），RenderNode 上挂 RenderEffect */
    private fun renderWithEffect(w: Int, h: Int, src: Bitmap, effect: android.graphics.RenderEffect?): Bitmap? {
        val reader = ImageReader.newInstance(w, h, android.graphics.PixelFormat.RGBA_8888, 2,
            HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE
                or HardwareBuffer.USAGE_CPU_READ_RARELY)
        val renderer = HardwareRenderer()
        try {
            renderer.setSurface(reader.surface)
            val node = android.graphics.RenderNode("fx")
            node.setPosition(0, 0, w, h)
            node.setRenderEffect(effect)
            val canvas = node.beginRecording()
            canvas.drawBitmap(src, 0f, 0f, null)
            node.endRecording()
            renderer.setContentRoot(node)
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            val image = reader.acquireLatestImage() ?: return null
            val plane = image.planes[0]
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(plane.buffer)
            image.close()
            return bmp
        } finally {
            renderer.destroy()
            reader.close()
        }
    }
}
