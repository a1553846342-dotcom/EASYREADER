package com.example.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.R

/**
 * 全 App 字体族的**唯一出口**。
 *
 * 背景：此前 `FontFamily.Default / Serif / SansSerif / Monospace` 散落在
 * HomeScreen / LibraryScreen / TabScreenHeader / JsonSourceGuideCard /
 * ReaderScreen / Type.kt 等 8 个文件里共 30 余处硬编码。这些「通用字族」在
 * Android 上会解析到**各厂商自己的系统字体**（默认为 sans-serif，而 MIUI 把它
 * 指向 MiSans、HarmonyOS 指向 HarmonyOS Sans、OneUI 指向 OneUI Sans…），
 * 字形、字重、中文基线都不一样 —— 这是「同一份设计稿在不同手机上长得不一样」
 * 里最容易被忽略、却又最普遍的一条。
 *
 * 收敛分两步完成：
 *  1. **把出口收敛到这里**：语义名 → 同一个 androidx 常量，运行时行为与改前
 *     完全一致，不存在视觉回归风险；
 *  2. **把「阅读正文字族」换成真正打包进 APK 的字体**（本次改动，见下）。
 *
 * ## 本次改动：阅读正文改为自带字体
 *
 * 阅读器正文（`prefs.fontFamilyIndex` 的 0 / 1 / 2 三档）改为使用
 * `res/font/` 中随 APK 分发的 **Noto Serif SC / Noto Sans SC** 子集：
 *
 * - 授权：SIL OFL 1.1，可商用、可再分发（见 `docs/THIRD_PARTY_NOTICES_FONT.md`）；
 * - 字符集：GB2312 一级汉字 3755 个 + 拉丁 / 数字 / 常用中英文标点；
 * - 生僻字不在子集中，由 Android 的 Typeface fallback 自动补齐（自定义字体同样
 *   走 fallback 链），**不会出现豆腐块**；
 * - 体积：两个子集合计约 2.0 MB（未压缩），压缩进 APK 后增量约 1.5 MB。
 *
 * **UI 界面字体保持原样**（仍走系统通用族）：本次只解决「阅读正文跨机型不一致」，
 * 不动全 App 的中文观感，避免大范围视觉回归。
 *
 * 注意：**不要**在业务代码里重新出现 `FontFamily.XXX` 字面量，一律走这里。
 */
object AppFonts {

    // ------------------------------------------------------------------
    // 〇、打包字体装配（内部，必须最先声明：下面的公开字族都引用它们）
    // ------------------------------------------------------------------

    /** 界面 / 阅读共用的 Noto Sans SC（Normal + Bold 两个槽位指向同一文件）。 */
    private val uiSans: FontFamily = runCatching {
        require(R.font.noto_sans_sc_regular != 0) { "font resource stripped" }
        FontFamily(
            Font(resId = R.font.noto_sans_sc_regular, weight = FontWeight.Normal),
            // 子集无独立 Bold 文件：注册同文件到 Bold 槽位，粗体走合成加粗
            Font(resId = R.font.noto_sans_sc_regular, weight = FontWeight.Bold)
        )
    }.getOrDefault(FontFamily.SansSerif)

    /** 界面 / 阅读共用的 Noto Serif SC（Normal + Bold）。 */
    private val uiSerif: FontFamily = runCatching {
        require(R.font.noto_serif_sc_regular != 0) { "font resource stripped" }
        FontFamily(
            Font(resId = R.font.noto_serif_sc_regular, weight = FontWeight.Normal),
            Font(resId = R.font.noto_serif_sc_regular, weight = FontWeight.Bold)
        )
    }.getOrDefault(FontFamily.Serif)

    // ------------------------------------------------------------------
    // 一、界面字族（UI chrome）：2026-09-24 起同样使用打包字体
    // ------------------------------------------------------------------
    //
    // 背景：v1.1.0 之前界面文字走 `FontFamily.Default` 等系统通用族，会解析到
    // 各厂商自己的字体（HarmonyOS Sans / MiSans / OneUI Sans…），字形、字重、
    // 基线全部不同 —— 实机对比确认「同一页面两台手机字体不一样」正是源于此。
    //
    // 现在界面字族与阅读正文字族共用 `res/font/` 里的 Noto CJK 子集：
    //  - 跨机型字形完全一致；
    //  - 子集未覆盖的生僻字 / emoji 由 Android 的 Typeface fallback 链补齐，
    //    不会出现豆腐块；
    //  - Noto 子集只有 Regular 一个字重文件，Bold / SemiBold / Medium 通过
    //    注册同文件的 Bold 槽位走**合成粗体**（faux bold），字重观感略轻于
    //    厂商真粗体，属于统一字形的代价。
    //
    // 注意：**不要**在业务代码里重新出现 `FontFamily.XXX` 字面量，一律走这里。

    /** 界面默认体（正文、按钮、列表等一切 chrome）= Noto Sans SC。 */
    val Default: FontFamily = uiSans

    /** 衬线体：书籍标题、封面书名、四 Tab 页大标题 = Noto Serif SC。 */
    val Serif: FontFamily = uiSerif

    /** 黑体 / 无衬线 = Noto Sans SC（与 [Default] 同族）。 */
    val SansSerif: FontFamily = uiSans

    /** 等宽体：JSON 书源示例、代码、对齐要求高的数值。保持系统等宽族（跨机型差异小、省体积）。 */
    val Monospace: FontFamily = FontFamily.Monospace

    // ------------------------------------------------------------------
    // 二、阅读正文字族：与界面同源的 Noto CJK 子集
    // ------------------------------------------------------------------

    /** 阅读用衬线体：`res/font/noto_serif_sc_regular.otf`。加载失败退回系统衬线族。 */
    val ReadingSerif: FontFamily = uiSerif

    /** 阅读用黑体：`res/font/noto_sans_sc_regular.otf`。加载失败退回系统无衬线族。 */
    val ReadingSansSerif: FontFamily = uiSans

    // ------------------------------------------------------------------
    // 三、阅读器「字体」设置档位表
    // ------------------------------------------------------------------

    /**
     * 阅读器「字体」设置里的**内置档位**表（id 与 `prefs.fontFamilyIndex` 一一对应，
     * **不可重排**）。
     *
     * 注意：`Triple.first` 是写进 SharedPreferences 的存档值，已有用户的
     * `fontFamilyIndex` 就靠它解读 —— 只准往后追加，绝不能调整顺序或改名。
     * 「自定义字体」档（id = 4）在 ReaderScreen 里单独渲染（要带「+导入」按钮），
     * 不在此表中。
     *
     * 档位映射（0 / 1 / 2 走打包字体，3 等宽保留系统通用族）：
     *  - 0 默认字体 → Noto Sans SC（与改前的系统 sans-serif 观感一致）
     *  - 1 衬线体   → Noto Serif SC
     *  - 2 黑体     → Noto Sans SC
     *  - 3 等宽体   → 系统等宽族（等宽场景对字形一致性要求低，且省体积）
     */
    fun readingFontFamilies(): List<Triple<Int, String, FontFamily>> =
        listOf(
            Triple(0, "默认字体", ReadingSansSerif),
            Triple(1, "衬线体", ReadingSerif),
            Triple(2, "黑体", ReadingSansSerif),
            Triple(3, "等宽体", Monospace)
        )

    /**
     * 按 `prefs.fontFamilyIndex` 取实际字体族。
     *
     * @param index 用户选择的档位 id
     * @param custom 用户自定义字体；index == 4 时使用（原样走 `Typeface.createFromFile`）
     */
    fun readingFontFamily(index: Int, custom: FontFamily?): FontFamily =
        when (index) {
            1 -> ReadingSerif
            2 -> ReadingSansSerif
            3 -> Monospace
            4 -> custom ?: ReadingSansSerif
            else -> ReadingSansSerif
        }
}
