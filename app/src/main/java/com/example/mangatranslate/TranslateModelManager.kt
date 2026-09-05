package com.example.mangatranslate

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 翻译模型下载管理器：det/rec 两个 ONNX（共约 31MB）按需下载到
 * filesDir/manga_translate_models/，多源容灾（hf-mirror 优先照顾国内直连，
 * huggingface.co 兜底），断点安全（tmp 文件 + 原子重命名 + 尺寸校验）。
 *
 * 模型不打包进 APK（保持 9.9MB 瘦身成果），首次开启翻译时在设置面板里下载。
 */
object TranslateModelManager {

    data class ModelSpec(
        val fileName: String,
        /** 依次尝试的下载源（host 全部为 https 公网 CDN）。 */
        val urls: List<String>,
        val minBytes: Long,
        val label: String,
    )

    val detModel = ModelSpec(
        fileName = "ppocr_det.onnx",
        urls = listOf(
            "https://hf-mirror.com/PaddlePaddle/PP-OCRv6_small_det_onnx/resolve/main/inference.onnx",
            "https://huggingface.co/PaddlePaddle/PP-OCRv6_small_det_onnx/resolve/main/inference.onnx",
        ),
        minBytes = 9_000_000L,
        label = "文字检测模型",
    )
    val recModel = ModelSpec(
        fileName = "ppocr_rec.onnx",
        urls = listOf(
            "https://hf-mirror.com/PaddlePaddle/PP-OCRv6_small_rec_onnx/resolve/main/inference.onnx",
            "https://huggingface.co/PaddlePaddle/PP-OCRv6_small_rec_onnx/resolve/main/inference.onnx",
        ),
        minBytes = 19_000_000L,
        label = "文字识别模型",
    )

    sealed interface DownloadState {
        data object NotDownloaded : DownloadState
        data class Downloading(val which: String, val progress: Float) : DownloadState
        data object Ready : DownloadState
        data class Failed(val reason: String) : DownloadState
    }

    private val _state = MutableStateFlow<DownloadState>(DownloadState.NotDownloaded)
    val state: StateFlow<DownloadState> = _state
    private val mutex = Mutex()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun modelDir(context: Context): File =
        File(context.filesDir, "manga_translate_models").apply { mkdirs() }

    fun detFile(context: Context): File = File(modelDir(context), detModel.fileName)
    fun recFile(context: Context): File = File(modelDir(context), recModel.fileName)

    fun isReady(context: Context): Boolean =
        detFile(context).isFileAndBig(detModel.minBytes) && recFile(context).isReadySize(recModel.minBytes)

    private fun File.isFileAndBig(min: Long): Boolean = isFile && length() >= min
    private fun File.isReadySize(min: Long): Boolean = isFileAndBig(min)

    fun totalBytes(context: Context): Long =
        (detFile(context).takeIf { it.isFile }?.length() ?: 0L) +
            (recFile(context).takeIf { it.isFile }?.length() ?: 0L)

    /**
     * 下载缺失模型（已就位则跳过）。进度以两模型合计汇报。
     * 返回 null = 全部就绪；否则返回失败原因。
     */
    suspend fun ensureDownloaded(
        context: Context,
        onProgress: (Float) -> Unit = {},
    ): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val appContext = context.applicationContext
            if (isReady(appContext)) {
                _state.value = DownloadState.Ready
                return@withContext null
            }
            val totalMin = (detModel.minBytes + recModel.minBytes).toFloat()
            var doneBytes = 0L
            for (spec in listOf(detModel, recModel)) {
                val target = File(modelDir(appContext), spec.fileName)
                if (target.isFileAndBig(spec.minBytes)) {
                    doneBytes += target.length()
                    continue
                }
                val err = downloadOne(spec, target) { fileFraction ->
                    onProgress(((doneBytes + fileFraction * spec.minBytes) / totalMin).coerceIn(0f, 1f))
                }
                if (err != null) {
                    _state.value = DownloadState.Failed(err)
                    return@withContext err
                }
                doneBytes += target.length()
                onProgress((doneBytes / totalMin).coerceIn(0f, 1f))
            }
            _state.value = DownloadState.Ready
            null
        }
    }

    private suspend fun downloadOne(
        spec: ModelSpec,
        target: File,
        onProgress: (Float) -> Unit,
    ): String? = withContext(Dispatchers.IO) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        var lastError: String? = null
        for (url in spec.urls) {
            val attempt = attemptDownload(url, spec, tmp, onProgress)
            if (attempt != null) {
                lastError = attempt
                continue
            }
            if (tmp.length() < spec.minBytes) {
                lastError = "${spec.label}下载不完整（${"%.1f".format(tmp.length() / 1e6)}MB < ${spec.minBytes / 1_000_000}MB）"
                runCatching { tmp.delete() }
                continue
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                runCatching { tmp.delete() }
            }
            onProgress(1f)
            return@withContext null
        }
        lastError ?: "未知错误"
    }

    /** 单源尝试：返回 null = 下载成功；否则失败原因（tmp 已清理）。 */
    private fun attemptDownload(
        url: String,
        spec: ModelSpec,
        tmp: File,
        onProgress: (Float) -> Unit,
    ): String? {
        try {
            _state.value = DownloadState.Downloading(spec.label, 0f)
            val request = Request.Builder()
                .url(validateUrl(url))
                .header("User-Agent", "Mozilla/5.0 (Linux; Android) CialloReader/1.0")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return "HTTP ${response.code}（${url.substringAfter("//").take(28)}…）"
                val body = response.body ?: return "空响应体"
                tmp.outputStream().use { out ->
                    body.byteStream().use { ins ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        var written = 0L
                        val declared = body.contentLength()
                        while (ins.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                            written += read
                            val frac = if (declared > 0) written.toFloat() / declared
                            else (written.toFloat() / spec.minBytes).coerceIn(0f, 0.99f)
                            onProgress(frac)
                            _state.value = DownloadState.Downloading(spec.label, frac)
                        }
                        out.flush()
                    }
                }
                return null
            }
        } catch (e: Exception) {
            runCatching { tmp.delete() }
            return e.message ?: e.javaClass.simpleName
        }
    }

    /** 仅允许 https 公网 CDN（Mimosa 约束：拒绝 localhost/环回/私网/保留地址）。 */
    private fun validateUrl(raw: String): String {
        val u = java.net.URI(raw)
        require(u.scheme == "https") { "仅允许 https 下载源" }
        val host = u.host?.lowercase() ?: error("URL 缺少 host")
        val notAllowed = host == "localhost" ||
            host == "127.0.0.1" || host == "::1" || host == "[::1]" ||
            host.endsWith(".localhost") ||
            host == "0.0.0.0" ||
            host.endsWith(".internal") || host.endsWith(".local")
        require(!notAllowed) { "拒绝非公网下载源: $host" }
        val ip = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$").find(host)
        if (ip != null) {
            val o = ip.destructured.toList().map { it.toInt() }
            val priv = o[0] == 10 || (o[0] == 172 && o[1] in 16..31) || (o[0] == 192 && o[1] == 168) ||
                o[0] == 169 && o[1] == 254 || o[0] >= 224
            require(!priv) { "拒绝私网/保留地址下载源: $host" }
        }
        return raw
    }

    fun deleteModels(context: Context) {
        modelDir(context).listFiles()?.forEach { runCatching { it.delete() } }
        _state.value = DownloadState.NotDownloaded
    }
}
