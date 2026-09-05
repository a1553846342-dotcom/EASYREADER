package com.example.source.anilist

import java.text.Normalizer

/**
 * 标题归一化（第七轮第 7 条第 11 点：搜索需做基本的标准化；
 * 第十一轮第 6 条：新增繁→简汉字折叠）。
 *
 * 统一处理大小写、全角半角（NFKC）、常见中英日标点与空白，并把繁体/日式
 * 汉字折叠为简体（"無職転生" ↔ "无职转生" 同一作品两种书写可互匹配）：
 * 中文 / 日文 / 英文 / 罗马音进入同一匹配流程，不依赖原始字符串 contains()。
 *
 * 两种形态：
 * - [normalize]：标点→空格 + 压缩空白（拉丁词间保留单空格）；
 * - [compact]：在 normalize 基础上去掉全部空白（"Mushoku Tensei" ↔ "mushokutensei"，
 *   以及无空格罗马音/中文连续书写场景）。
 *
 * 纯函数，无 Android 依赖，可单测。查询侧与内置标题库导入侧共用本函数，
 * 两端归一化结果必然一致。
 */
object TitleNormalizer {

    /** 常见中英日标点 → 空格（归一前先 NFKC，全角标点已变半角） */
    private val PUNCTUATION = Regex("[!-/:-@\\[-`{-~　。、・「」『』（）【】《》〈〉“”‘’…―ー･：；！？，．]")

    fun normalize(title: String): String {
        if (title.isBlank()) return ""
        val nfkc = Normalizer.normalize(title, Normalizer.Form.NFKC)
        val lowered = nfkc.lowercase()
        val spaced = PUNCTUATION.replace(lowered, " ")
        // 第十一轮第 6 条：繁→简折叠（逐字符；拉丁/假名无映射原样通过）
        val folded = buildString(spaced.length) {
            for (c in spaced) append(CjkFoldMap.fold(c))
        }
        return folded.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    }

    fun compact(title: String): String = normalize(title).replace(" ", "")

    /** 判断是否值得作为搜索变体（太短的噪音词会污染结果） */
    fun usableVariant(raw: String): Boolean {
        val n = normalize(raw)
        return n.length >= 2
    }
}
