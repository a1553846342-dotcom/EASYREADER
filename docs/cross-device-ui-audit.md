# Android 跨机型 UI 差异深度排查报告

- 项目：`C:\Users\GuanXingRen\Downloads\novel-reader (1)\novel-reader`（包名 `com.aistudio.novelreader.kxmpzq`，应用名「Ciallo 阅读」）
- 技术栈：**纯 Jetpack Compose（Material3）**，`Activity` 只有一个，无 Fragment、无 `res/layout`
- 排查日期：2026-09-23
- 编译门禁：`python .workbuddy/build.py check` → **BUILD SUCCESSFUL**
- 书架门禁：`python .workbuddy/audit_shelf.py` → **68 项通过，0 项失败**

---

## 0. 结论速览

| # | 手册条款 | 结论 |
|---|---------|------|
| 1 | 字体未打包，走系统解析 | **存在问题** · 出口已收敛，打包待定（见 §1） |
| 2 | 深色模式强制重绘 | **存在问题** · **已修** |
| 3 | 系统语义色 / 动态取色 | **不存在** · N/A |
| 4 | 原生控件皮肤化 | **大部分 N/A**，仅 Toast 残留（见 §4） |
| 5 | 布局尺寸与资源限定符 | **已用量身 Asp Spec 体系解决** · N/A |
| 6 | 安全区 | **部分修复**（挖孔已补），折叠屏/分屏需 QA |
| 7 | 阴影 / 圆角 / 模糊 | **大部分自绘** · 低风险，1 处在观察名单 |
| 8 | 动画缩放被关闭 | **已有机制** · 判定精度 **已修** |
| 9 | WebView | **N/A**（隐藏式，不渲染 UI） |
| A | 仿真翻页背面不透明纯色（用户报的 Bug） | **已修**，且定位到真正的根因在 `PageCurlReaderContainer`，详见 §A |

---

## §A. 任务 A：仿真翻页背面渲染成不透明纯色（用户直接报的 Bug）

### A-1. 重要更正：真正的根因不在最初定位的那个文件

最初指向的是 `PageTurnContainer.kt:804-807`。**但那处代码当前根本走不到**：

`ReaderScreen.kt:2143` 的分派逻辑是

```kotlin
if (!isScrollMode && pageTurnMode == PageTurnType.SIMULATE.id) {
    PageCurlReaderContainer(...)   // ← 仿真翻页实际走这里
} else {
    PageTurnContainer(pageTurnMode = pageTurnMode, ...)   // 覆盖/平移/渐变/滚动
}
```

即：**「仿真翻页」= `PageCurlReaderContainer`（wewox/pagecurl 引擎）**，而
`PageTurnContainer` 内部的 `Simulate3DCurlLayout` / `CurlFlapBackside` 在当前
代码路径下是**到不了的分支**（它只在 `pageTurnMode == 0` 时被 `PageTurnContainer`
选中，而此时 ReaderScreen 已经把它让给了 `PageCurlReaderContainer`）。

用户亲眼看到的症状来自 pagecurl 引擎：

```kotlin
// PageCurlReaderContainer.kt:96-97（修改前）
backPageColor = Color(0xFFE8E4DC),
backPageContentAlpha = 0f,
```

而引擎内部（`app/src/main/java/eu/wewox/pagecurl/page/CurlDraw.kt:179-180`）是这么画的：

```kotlin
val overlayAlpha = 1f - config.backPageContentAlpha
drawRect(config.backPageColor.copy(alpha = overlayAlpha))
```

`backPageContentAlpha = 0f` → `overlayAlpha = 1.0` → **在镜像内容之上糊了一层 100% 不透明的
纯色** —— 这就是用户那句"渲染成不透明纯色"的字面本尊：背面不只不透明，而且**一个字都没有**。

> 两个文件我都修了：`PageCurlReaderContainer` 修用户看得见的那个，
> `PageTurnContainer` 修那个同款的、随时可能被重新接线的潜在坏分支。

### A-2. 修复内容

| 文件:行 | 修改前 | 修改后 | 效果 |
|---|---|---|---|
| `pageturn/PageCurlReaderContainer.kt:96-97` | `backPageColor=Color(0xFFE8E4DC)`、`backPageContentAlpha=0f` | `paper.face` + `BACK_PAGE_CONTENT_ALPHA = 0.58f` | 背面从"纯色板"变成"纸透出 58% 的镜像字" |
| `pageturn/PageTurnContainer.kt:806` | `drawRect(Color(0xFFF5F0E6))`（100% 不透明底） | `drawRect(paper.face.copy(alpha = paper.baseAlpha))`，`baseAlpha = 0.90/0.93` | 让底下的页面透出约 7~10%，真纸是透光的 |
| `pageturn/PageTurnContainer.kt:814` | `alpha = 0.42f` | `BACKSIDE_CONTENT_ALPHA = 0.88f` | 背面读起来像真印着字，而不是鬼影 |
| `pageturn/PageTurnContainer.kt:828` | `Color(0xFFF5F0E6).copy(alpha=0.16f)` | `paper.face.copy(alpha = 0.15f)` | 纸色随主题，仍叠在内容**上方** |
| `pageturn/PageTurnContainer.kt:874/879` | `0xFFFDF8EC` 高光 / `0xFFB9AB90` 描边 | `paper.edgeHighlight` / `paper.edgeStroke` | 夜里不再出现米黄色纸边 |
| `pageturn/PaperPalette.kt`（新增） | — | 由阅读背景色推导整套纸面/高光/描边 | 6 套主题全部自适应 |
| `ui/ReaderScreen.kt:2147 / 2178` | 无 `paperColor` | `paperColor = bgColor` | 两个卷页入口都跟随主题 |

### A-3. **没有任何效果被删掉**（逐项保留声明）

`CurlFlapBackside` 里原有的每一层都还在，只改了颜色来源与两个 alpha：

| 层 | 位置 | 状态 |
|---|---|---|
| 折角几何路径 `buildFlapPath()` | `PageTurnContainer.kt:918` | ✅ 未动 |
| 翻动边缘投影 `shadowStripPath()` | `:898` / 调用点 `:718` | ✅ 未动 |
| 内侧折痕阴影（6 段 linearGradient） | `:832-846` | ✅ 未动（只未改色） |
| 内侧纸边高光 | `:598-613` | ✅ 未动 |
| 环境光遮蔽 `drawCircle` 径向渐变 | `:849-863` | ✅ 未动 |
| 纸张厚度描边（2.4f + 0.9f 双笔） | `:865-881` | ✅ 保留，仅换色 |
| 镜像内容的 `scaleX=-1` + `rotationZ` 变换 | `:810-824` | ✅ 未动 |

### A-4. 为什么纸张「半透<｜hy_place▁holder▁no▁813｜> 0.90」而不是更透

真实书籍 behavior：纸张本身透光，但背面看到的是**同一张纸正面的墨迹透过来**，
而不是底下那一页的内容。所以：

- 纸基留 7~10% 的透光度 → 纸感、不发死；
- 镜像内容提到 0.88 → 背面确实"有字"。

再高会变成"背面像印错了正的"，再低就退回用户抱怨的原状。深色主题下
（`baseAlpha = 0.93`）透得更少一点：暗底底下透光显脏。

### A-5. 如何验证（QA）

| 场景 | 期望 |
|---|---|
| 白色主题（id 0/1）仿真翻页慢拖 | 背面是**微暖的白纸**，能清楚看见镜像字 |
| 羊皮（2）/ 护眼绿（4） | 纸色跟着主题走（米黄/浅绿），不是统一米色 |
| 夜间（3）/ OLED（5） | 纸变深灰/近黑，**边缘描边提亮**而不是消失，绝不出现米黄色 |
| 沿对角线从右下角慢拖 | 折角路径、柱状明暗、折痕阴影、厚度描边全都在 |
| 快速翻页 | 跟修改前一样顺，不掉帧 |

---

## §1. 字体未打包，走系统解析

### 1-1. 是否存在问题：**是**

`FontFamily.Default / Serif / SansSerif / Monospace` 在 Android 上会解析到
**各厂商自己的系统字体**：MIUI → MiSans，HarmonyOS → HarmonyOS Sans，
OneUI → OneUI Sans，ColorOS → OPPO Sans。`sans-serif` 这个通用名被 OEM 重定向了，
因此**字形、字重、中文基线、行距全部不一致**。

原分布（共 30 处硬编码，跨 8 个文件）：

| 文件 | 原行号 | 处数 |
|---|---|---|
| `ui/theme/Type.kt` | 26/33/40/49/56/63/72/79/86/95/103/110/119/126/133 | 15 |
| `ui/HomeScreen.kt` | 1149/1252/1318/1813/1975 | 5 |
| `ui/ReaderScreen.kt` | 934-946 / 3560-3563 / 3577 | 10 |
| `ui/help/JsonSourceGuideCard.kt` | 82/121 | 2 |
| `library/LibraryScreen.kt` | 1154 | 1 |
| `ui/components/TabScreenHeader.kt` | 124 | 1 |
| `ui/ReaderPagination.kt` | 99 | 1 |

### 1-2. **已修**：出口收敛（本次改动，零视觉回归）

新增 `app/src/main/java/com/example/ui/theme/AppFonts.kt`，语义名 → 同一个
androidx 常量，**运行时行为与改前逐字节一致**，因此不存在任何视觉回归风险：

```kotlin
object AppFonts {
    val Default: FontFamily   = FontFamily.Default      // 界面默认体
    val Serif: FontFamily     = FontFamily.Serif        // 装饰性标题
    val SansSerif: FontFamily = FontFamily.SansSerif    // 黑体
    val Monospace: FontFamily = FontFamily.Monospace    // JSON/代码/对齐数值

    fun readingFontFamilies(): List<Triple<Int, String, FontFamily>>  // 阅读器内置字体档位表
    fun readingFontFamily(index: Int, custom: FontFamily?): FontFamily // 按 prefs 索引取形容词族
}
```

全量替换已完成：25 处字面量纯替换 + 5 处逻辑重构（`selectedFontFamily`、设置面板档位表等），并且：

- `ReaderScreen.selectedFontFamily` 改走 `AppFonts.readingFontFamily(...)` 并用 `remember` 缓存；
- 「字体」设置面板的档位表改走 `AppFonts.readingFontFamilies()`；
- **存档 id 语义完全没动**（0=默认 / 1=衬线 / 2=黑体 / 3=等宽 / 4=自定义），
  老用户的 `prefs.fontFamilyIndex` 解读结果逐位一致；
- 用户自定义字体入口 `prefs.customFontPath` + `Typeface.createFromFile` **原样保留**。

从此"整体换字体"只需改 `AppFonts.kt` 一个文件。

### 1-3. **未修**：把字体打进 APK（体量 vs. 收益，需要产品/设计拍板）

#### 实测数据（本机 `pyftsubset` 真跑出来的，不是估的）

源字体：Noto Sans SC Regular（思源黑体简体，SIL OFL 1.1，可商用）

| 子集方案 | 字符集 | 子集后体积 | APK 增量估算（TTF，APK 内 deflate） |
|---|---|---|---|
| 拉丁 + 数字 + 标点 | 95 字形 | **7.6 KB** | ~5 KB |
| GB2312 **一级字库 3755 字** + 拉丁 + 中文标点 | 3850 字形 | **778 KB** | ~0.5 MB |
| GB2312 **全 6763 字** + 拉丁 + 中文标点 | 6870 字形 | **1.43 MB** | ~0.9 MB |
| 全量 Noto Sans SC Regular | 65535+ 字形 | 8.33 MB（OTF） | ~5 MB |

> 双字重（Regular + Medium 或 Bold）请乘 2：常用字双字重 ≈ 1.5 MB，
> GB2312 双字重 ≈ 2.9 MB —— **都落在 5 MB 预算内**。所以「体量」其实不是本次的拦路虎。

#### 为什么不顺手就打？

真正的拦路虎是**三个没法由工程师单独拍板的取舍**：

1. **不知道设计字体是哪一个。** 打包 Noto Sans SC ≠ 还原设计稿，只是換成了
   「另一个确定的字体」。要真正消灭跨机型差异，得拿到 UI 设计稿实际用的那个
   字体文件（且要有可再分发授权）。
2. **覆盖不全反而更糟。** 小说里的人名地名常常有生僻字、GBK 扩展字、日式汉字变体。
   一旦打进去的子集缺字，Android 会**逐字回退到系统字体** —— 结果是一句话里
   一半 Noto、一半 MiSans，**字形混着排**。这比统一走系统字体还难看，且直接违反
   「中文可读性不能变差」。
3. **会改掉全 App 的中文观感。** 主流中文 App 刻意用系统字体是有理由的：
   MiSans / HarmonyOS Sans 本身就是为屏幕阅读调过的正文字体，强行统一反而可能在
   大多数机型上**降低**视觉还原度 —— 这正是用户明令禁止的那类 trade-off。

#### 建议的推进路径（按性价比排序）

| 方案 | 做法 | 预估成本 | 备注 |
|---|---|---|---|
| **方案一（推荐）** | 只把**阅读正文**换 bundled 字体，界面 chrome 仍用系统字体。<br>`AppFonts.Default` 保持不变，`AppFonts.readingFontFamily(0, …)` 从 `res/font/` 加载。 | 0.5–1.0 MB<br>开发 0.5 天 + 设计走查 0.5 天 | 微信读书/掌阅/起点的通行做法：UI 跟随系统，正文由 App 定。<br>**但阅读页默认观感会变，必须设计签字。** |
| 方案二 | 全 App 统一 bundled 字体 | 1.5–2.9 MB（双字重）<br>开发 0.5 天 + **全量走查 2 天** | 改动面覆盖每一个页面，风险最高 |
| 方案三 | 只打拉丁 + 数字部分 | ~5 KB | **对本 App 基本无效**：正文是中文，中文仍然回退系统字体，差异照旧。只能解决数字/英文的一致性。 |

前置条件：**设计提供字体文件 + 确认授权 + 确认覆盖字符集**。拿到这三样之后，改动只在
`AppFonts.kt` 一处（本次已经把出口收敛好了），实施成本很低。

### 1-4. 附带核实：固定行高会不会在不同字体下重叠 → **不会**

- `ReaderPagination.kt:92`：`lineHeightPx = lineHeight.toPx().coerceAtLeast(fontSizePx * 1.2f)`
  —— 有 **1.2× fontSize 的下限**。
- `ReaderScreen.kt:1223-1235`：`platformStyle = PlatformTextStyle(includeFontPadding = false)`
  —— 已经关掉了各 ROM `FallbackLineSpacing` 差异导致的额外空距。

但由于有下限钳制，存在一个**已知的小偏差**：

- 用户在阅读设置里把「行距」调到低于 `字号 × 1.2` 时，`Text` 实际按用户值渲染，
  而分页测量按钳制后的值算 → 每页行数可能出现 ±1 行的偏差（多则末行被裁、少则底部留白）。
- 「行距」滑杆范围 20~48sp，「字号」上限见设置面板；二者交叉区才会触发。
- **本次不动**：要修就得同步改 `RenderSinglePage` 的装饰排版（两处都要钳），
  属于排版引擎改动，需要单独立项 + 走查。

### 1-5. 附带核实：`fontScale` 钳制的覆盖面 → 全覆盖（含阅读页），注释已勘误

`MainActivity.kt:202-209` 把 `fontScale` 夹在 `[0.85, 1.15]`，挂在根的
`CompositionLocalProvider` 上 → **覆盖全部界面，包括阅读页**。

原注释写的是「阅读器正文字号由 ReaderScreen 自管，不受此影响」——这句**不准确**：
阅读正文用的是 `fontSize.sp`，`.sp` 解析同样走 `LocalDensity.fontScale`，
所以阅读正文也在这个钳制范围内。实际换算：

```
正文实际 px = prefs.fontSize.sp × 系统 density × clamp(系统 fontScale, 0.85, 1.15)
```

「阅读字号独立」指的是它有自己的 `prefs.fontSize` 设置项，不是说它绕过了钳制。
**注释已改正**（`MainActivity.kt` A1 段），**行为未改** —— 放开钳制会让大量写死
高度的容器被撑爆（每天的 open_gpu compiler 16dp 角标、日历格 44dp、封面 155dp…），
那是系统性返工，不该在本次整改里顺手做。

如果将来要支持「大字体用户也想看大字」，建议做阅读页的
**「跟随系统字体大小」开关**（在钳制之外单独给阅读正文放行），预估 1 天 +
需要 QAFont Scale 1.3x 走查。

### 1-6. 附带核实：「显示大小（density）」路径 → 正常

`Density(density = systemDensity.density, fontScale = clamp(systemDensity.fontScale))`
、`remember(systemDensity)` —— 只屏蔽了 fontScale，**density 完全透传**。
Manifest 已声明 `configChanges` 含 `density`，改显示大小会触发
`onConfigurationChanged` → Compose 重组 → `LocalDensity` 更新 → `remember` 重建。
✅ 无问题。

---

## §2. 深色模式 / 强制深色重绘 —— **已修**

原 `res/values/themes.xml`：

```xml
<style name="Theme.MyApplication" parent="android:Theme.Material.Light.NoActionBar">
    <item name="android:statusBarColor">@android:color/transparent</item>
    <item name="android:navigationBarColor">@android:color/transparent</item>
</style>
```

三个问题中的两个成立：

| 子项 | 是否存在 | 处理 |
|---|---|---|
| (a) 缺 `android:forceDarkAllowed="false"` | **是** | ✅ 已修 |
| (b) 缺 `values-night` | 是 | **不修**（见下） |
| (c) 父主题是 Light | 是 | **不修**（见下） |

### 已修

```xml
<item name="android:forceDarkAllowed">false</item>
```

Compose 内容本身不受 force-dark 影响，但**原生窗口层会**：Toast、文本选择手柄/菜单、
输入法上方高亮、分享面板。App 自己有 `autoNightMode` 开关，被系统再反一次色
= 两套深色叠加，原生层会糊成色块。显式关闭后系统不再插手。
属性是 API 29 引入，写在默认 `values/` 下时老系统直接忽略，**不会崩、无副作用**。

同时 **`MainActivity.onCreate` 增加了导航栏对比度豁免**（同为"系统强制画一层"类问题）：

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    runCatching { window.isNavigationBarContrastEnforced = false }
}
```

部分 ROM（OneUI / ColorOS 居多）会在透明导航栏上再糊一层 ~20% 灰的黑纱，
导致同一个界面在不同机型上"底部一条脏边"。

### 不修 (b) `values-night`

加 `values-night` 的目的是给夜间模式提供不同的原生色。但本 App 的明暗不由
`res` 主题决定，而由 `MainViewModel.autoNightMode` 驱动 Compose
`MaterialTheme` —— **资源侧的 `values-night/` 无从参与**。且本文件只有
statusBarColor / navigationBarColor 两个 transparent 条目，夜间版内容完全一样，
加了只是多一份冗余。

### 不修 (c) 父主题改 Dark

改 Dark 会让系统按"这是个深色 App"来绘制原生层（Toast 变深）。看起来更合理，
但它牵扯三处风险，**性价比低于收益**：

1. `MainActivity.kt:167-175` 已用 `WindowInsetsControllerCompat`
   手动设置了 `isAppearanceLightStatusBars/NavigationBars`（跟随 `autoNightMode`）。
   改父主题会改变这套 API 的**默认值**，容易在"用户在 App 里开夜间、系统是白天"
   的场景下把状态栏图标变成深色压深色背景 —— 即铁律要避免的那类回归。
2. App 是明暗双模（跟随用户开关），静态声明 Dark 只有一半时间是对的。
   要真正正确需要接 `AppCompatDelegate.setDefaultNightMode()` 并引入 `androidx.appcompat`
   依赖 —— 这是**新增依赖**，需要单独评估（离线缓存里不一定有）。
3. 收益面很窄：全 App 剩下的原生 UI 只有 Toast 和隐藏 WebView。

**预估成本**：引入 appcompat + 重写「亮度与否」villeạch 微适配 + 真机走查 ≈ 1.5 天，
建议在下一个迭代独立做，不要混在本次整改里。

**如何验证**：MIUI/HyperOS（最激进的强制深色）上，开发者选项→把
「启用深色模式的 Battery saver 强制」打开，看 App 内 Toast / 长按选词菜单
是否还是正常配色。

---

## §3. 系统语义色 / 动态取色 —— **N/A（不存在问题）**

| 检查项 | 结果 |
|---|---|
| `dynamicColorScheme` / `dynamicLightColorScheme` / `dynamicDarkColorScheme` | ❌ 全项目 **0 处命中** |
| Material You / `Material3.dynamicDarkColorScheme` | ❌ 0 处 |
| 从 `LocalContext` 读系统主题色 | ❌ 0 处 |
| `?attr/...` 引用 / `obtainStyledAttributes` | ❌ 0 处 |

`ui/theme/Theme.kt` 的配色来源是**固定的品牌调色板**：

```kotlin
val BasePrimaryColors = listOf(Color(0xFF2563EB) /*蓝*/, Color(0xFF7C3AED) /*紫*/, …)
val lightColorScheme = lightColorScheme(primary = primaryColor, …)
val darkColorScheme  = darkColorScheme(primary = primaryColor, …)
```

`colorScheme` 由 `darkTheme` 参数（= App 自己的 `autoNightMode`）二选一，
**完全不碰 `isSystemInDarkTheme()`**（`MyApplicationTheme` 的默认形参里有，
但 `MainActivity` 显式传了 `autoNightMode`，所以系统 uimode 进不来）。

**结论**：不存在被壁纸/OEM 取色污染品牌色的路径。**不需要做成显式开关**（本来就是关的）。

---

## §4. 原生控件皮肤化 —— **N/A（本项目基本无原生控件）**

| 检查项 | 结果 |
|---|---|
| `res/layout/` 目录 | ❌ **不存在**（`res/` 下只有 `drawable/`、`drawable-nodpi/`、`mipmap-*/`、`raw/`、`values/`、`xml/`） |
| `AndroidView(` | ❌ 0 处 |
| `androidx.appcompat.*` | ❌ 0 处 |
| `android.widget.*` | 仅 `Toast`（3 处） |
| `android.webkit.WebView` | 1 处，且为**游离隐藏 WebView**（见 §9） |
| 被厂商重皮肤的 Switch/CheckBox/SeekBar/ProgressBar/Dialog | ❌ 0 处 —— 全部为 Compose 实现 |

具体的可视化总管 complains 全部是 Compose/Material3：

- `SquishyToggle`（`com.swapnil.squishyswitch`）是纯 Compose Canvas 实现，非原生
  CompoundButton；
- 滑杆用 `com.ramotion.fluidslider.FluidSlider`（Canvas 绘制）+ Material3 Slider；
- 所有 Switch/BottomSheet/Dialog 走 Material3。

**唯一残留**：`Toast`（`LibraryLoginDialog.kt:248`、`LibraryScreen.kt:124`、
`LibraryViewModel.kt:547/557`）。Toast 的外观由 ROM 定义，各行不一致 —— 但它是
**系统级浮层**，改成 Compose 自绘需要自己维护队列/动画/权限。风险/收益比太差，
本次不动。若要统一，建议换成 Material3 `Snackbar`（App 已有 `SnackbarHost` 基建，
`ShelfSelectionHost` 在用），预估 0.5 天 + 交互回归。

---

## §5. 布局尺寸与资源限定符 —— **N/A（已用 Compose 侧断点体系解决）**

### 5-1. 资源限定符确实一个都没有

`app/src/main/res/` 下**不存在** `values-sw360dp/`、`values-sw400dp/`、`values-sw600dp/`
、`values-land/`、`values-night/` 等任何限定符目录。

**但这是合理的**：本项目没有 `res/layout`，所有布局都在 Kotlin 里，
「资源限定符」这套 View 体系的适配手段在这里**天然用不上**。改用与之等价的
Compose 侧方案即可。

### 5-2. 项目已有完备的自适应体系 `ui/adaptive/AdaptiveSpec.kt`

```kotlin
enum class WindowWidthClass { COMPACT, MEDIUM, EXPANDED }

fun rememberWindowWidthClass(): WindowWidthClass   // <600dp / 600-839dp / >=840dp

object AdaptiveSpec {
    val dialogMaxWidth: Dp = 560.dp           // 弹窗（平板居中限宽）
    val sheetMaxWidth: Dp = 560.dp            // 底部面板竖屏
    val sheetMaxWidthLandscape: Dp = 840.dp   // 底部面板横屏
    val pageContentMaxWidth: Dp = 720.dp      // 全屏滚动页内容
}
fun Modifier.adaptiveDialogWidth(fraction: Float = 0.86f)
fun Modifier.adaptiveSheetWidth()
```

`LibraryScreen.kt:656` 的注释也印证了这套改造已经过一轮专项整改
（「此前此处自行判断 screenWidthDp，改用全 App 统一断点体系 AdaptiveSpec」）。

配套：
- `HomeScreen.kt:1067-1070`：`cols = ((screenWidthDp + 24) / 150).coerceIn(3, 6)`
  —— 书架列数按宽度自适应，且用 `coerceIn` 兜底，不会在超小/超大屏上失真；
- Manifest 已声明 `configChanges` 含 `orientation|screenSize|screenLayout|
  smallestScreenSize|density|fontScale|keyboardHidden|uiMode`
  → 折叠屏开合 / 分屏 / 旋转**不重建 Activity**，且 `LocalConfiguration`
  实时反映当前窗口，`rememberWindowWidthClass()` 的 key 就是
  `configuration.screenWidthDp` → 配置变化时**会自动重算**。✅

### 5-3. 硬编码位置 → 已排查，无高危

| 模式 | 命中 | 判定 |
|---|---|---|
| `resources.displayMetrics` | 2 处 | `ComicHarismCurl.kt:1238`（第三方 native 控制器传 density，必需）、`FluidSlider.kt:291`（Canvas 自绘换算，必需） → 均无替代方案，**非问题** |
| `Configuration.screenWidthDp` | `AdaptiveSpec.kt:39-70`、`HomeScreen.kt:1067` | 正确的 Compose 侧断点方式，**非问题** |
| 绝对 dp 定位 / 硬编码 px | 0 处 | ✅ |

**结论**：不需要补 `values-sw*` 限定符，也不会发生"为解决适配把间距改小导致视觉降级"
（这一条恰好就是 `AdaptiveSpec` 注释里写着要避免的事）。

---

## §6. 安全区 —— **部分修复**

### 6-1. 现状

`ui/theme/AppInsets.kt` 定义两个 CompositionLocal：

```kotlin
val LocalAppBottomInset         // 系统导航栏 + 悬浮 Tab 栏 92dp + 8dp 间隙
val LocalAppBottomInsetNoTabBar // 底栏隐藏时：只让开系统导航栏
```

由 `MainActivity.kt:275/303` 统一下发，来源是：

```kotlin
WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
```

用的是 Compose WindowInsets（底层即 `WindowInsetsCompat`），不是硬编码 padding，
且已经把"三键导航（≈48dp）vs 手势导航（≈0）、悬浮 Tab 栏隐藏"都区分开了。
消费方 5 个文件。✅ 这一部分是好的。

### 6-2. **已修**：补 displayCutout（挖孔 / 刘海）

原 `ReaderScreen.kt:1172-1174` 只读 `statusBars` / `navigationBars`，
**没有读 `displayCutout`**。这会造成两类问题：

- 横屏时刘海可能移到**侧边**，此时它不属于 statusBars；
- 部分机型存在**底部挖孔**（比 navigationBars 更靠内）。

正文会滑到挖孔底下被裁掉。已改为取**较大值**：

```kotlin
val cutoutInsets = WindowInsets.displayCutout
val navBarsBottomPx = maxOf(navigationBarsInsets.getBottom(density), cutoutInsets.getBottom(density))
val statusBarsTopPx = maxOf(statusBarsInsets.getTop(density), cutoutInsets.getTop(density))
```

取 `maxOf` 是有意的：**只会让空间更安全，绝不会变小**。竖屏大多数机型上
statusBars 已经包含刘海，两者相等 → 什么都不变；只有真正存在额外 cutout
的机型/方向才会多让一点出来。

### 6-3. **未修**（需 QA 真机验证，方能进一步放宽）

| 场景 | 现状 | 备注 |
|---|---|---|
| 三键导航 vs 手势导航 | ✅ 由 `WindowInsets.navigationBars` 自动处理 | — |
| 横屏 | ⚠️ 本次只补了 cutout 的 max，**没有横屏专属布局** | 需 QA 在横屏下完整走查一层 **QA verify** |
| 分屏 / 多窗口 | ⚠️ 依赖 `LocalConfiguration` 实时性 | `configChanges` 已含 `screenSize|smallestScreenSize`，理论上 OK，**待验证** |
| 折叠屏开合 | ⚠️ 同上 | 不重建 Activity + Compose 重算，**待验证** |

这三类的下一步动作都是**真机验证**，不是改代码 —— 按分工交给 QA。

---

## §7. 阴影 / 圆角 / 模糊 —— **低风险 / 大部分 N/A**

### 7-1. `Modifier.blur`：全项目仅 1 处，且**已有降级**

```kotlin
// ComicReaderChrome.kt:104-113
internal fun Modifier.comicPanelGlass(backdrop: Backdrop?, shape: Shape): Modifier =
    if (backdrop == null) background(PanelBg)          // ← 降级：纯半透明
    else drawPlainBackdrop(backdrop = backdrop, shape = { shape }, effects = {
        colorControls(saturation = 1.18f)
        blur(radius = 26.dp.toPx())
    }).background(PanelBgGlass)
```

- 这里的 `blur` 来自 `backdrop` 库（不经手 `Modifier.blur`，而是自己管 RenderEffect /
  AGSL 着色器），比 Compose 官方 `Modifier.blur` 的版本行为更可控；
- **`backdrop == null` 时已经回退 `background(PanelBg)`**（注释写明了：CURL 的 GL 层
  采不到时为 null）—— 即手册要求的「必须有降级，不能只是不显示」**已经具备**；
- 其余 64 处含 "blur" 的匹配全是 `blurRadiusDp` 之类的参数名，不是真的模糊调用。

✅ **无问题**。

### 7-2. `elevation` 阴影：19 处，但品牌关键面都已经是自绘

| 类别 | 位置 | 判定 |
|---|---|---|
| 自绘 Canvas 阴影（跨机型完全一致） | `PageTurnContainer.kt:718/598`（卷页投影 + 纸边高光 + 厚度描边） | ✅ 本来就是自绘，**本次还保留了全部图层** |
| `Modifier.shadow(半径, 形状)` | `AppBottomTabBar.kt:183`、`SettingsControls.kt:140`、`WeeklyReadingChart.kt:275`、`LibraryScreen.kt:2622` | `Modifier.shadow` 由 Compose 自己按 shape outline 绘制，**不经过 View 的 RenderNode elevation**，行为一致 ✅ |
| `shadowElevation =`（Material Surface/Card） | `ReaderScreen.kt:2274/2592/2639/2689/4155`、`CacheManagementScreen.kt:765`、`SourceManagementScreen.kt:366/431/676`、`AppButton.kt:174`、`ShelfSelectionHost.kt:934/1089/1202`、`SquishyToggle.kt:153` | ⚠️ 走 RenderNode elevation，Android 12 起阴影算法有微调；多为 1~6dp 的弱阴影，**肉眼差异极小** |

**结论**：本次**不改**这些 elevation —— 逐个换成自绘渐变意味着 19 处视觉重写，
必然改变现有观感（用户明令禁止），而其收益只是消除一个「肉眼难辨」的差异。
性价比严重不对等。

**观察名单**（若将来要收口，优先这两处，因为面积最大）：

1. `AppBottomTabBar.kt:183` —— 悬浮底部栏，`Modifier.shadow` 已经 OK，若嫌不够，
   可叠加一层自绘渐变让阴影在浅色壁纸上更实；
2. `ShelfSelectionHost.kt:1202` —— `shadowElevation = 10f * alpha`，拖拽时的卡片
   抬起阴影，动起来的阴影差异最容易被看到。

预估：每处 0.5~1 天（含双机型对比走查）。

---

## §8. 动画缩放被用户关闭 —— **判定精度已修**

### 8-1. 覆盖面：已有统一机制 ✅

`ui/feedback/Motion.kt` 定义 `LocalReduceMotion`，由
`MainActivity.kt:312` 全 App 注入：

```kotlin
LocalReduceMotion provides systemReduceMotion()
```

消费方Shelf / 收藏 / Home 都已接入（`ShelfChips.kt`、`ShelfSelectionHost.kt`、
`FavoriteHeart.kt`、`FavoritesPanel.kt`、`HomeScreen.kt:2151`），
用法统一为 `tween(if (reduceMotion) 0 else XXX)` / `EnterTransition.None`。
✅ 不存在"两套 reduceMotion"。

### 8-2. **已修**：判定从「只认 `== 0f`」放宽

修改前：

```kotlin
val scale = Settings.Global.getFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
scale == 0f
```

两个缺陷：
1. 只看 `ANIMATOR_DURATION_SCALE`，**漏掉** `TRANSITION_ANIMATION_SCALE` /
   `WINDOW_ANIMATION_SCALE`（不同 ROM 写得不一样，有的全写，有的只写一个）；
2. `== 0f` 严格相等，部分 ROM 写 0.001 / 0.005，会判漏。

修改后：三个 scale **任一**被关到 `<= 0.01f` 即视为 reduceMotion。

刻意**不**把 0.5（「动画 0.5x」档）算作 reduceMotion —— 0.5x 视觉上仍有过渡，
判定为 reduceMotion 会让一串效果凭空消失，等于为了适配牺牲视觉还原度。

### 8-3. 核实：「动画完成回调」是否被当成唯一触发条件 → **不是**

逐处核查了最容易出事的 Animatable/LaunchedEffect：

| 位置 | 风险模式 | 结论 |
|---|---|---|
| `PageCurlReaderContainer.kt:151-161` `tapTurn()` | 显式做了 `job.cancel() + job.join()` 再 `snapTo(1)`，并有注释说明是在治僵尸 finally 竞态 | ✅ 不依赖回调才能推进 |
| `PageCurlReaderContainer.kt:129-136` `LaunchedEffect(state.current)` | 监听的是**状态值**`state.current`，不是动画回调 | ✅ |
| `PageTurnContainer.kt` `withTimeoutOrNull(…)` 包裹 animateTo | 有超时兜底，回调不来也会走 timeout 分支 | ✅ |
| `PageCurlReaderContainer.kt` 长按判定 `withTimeoutOrNull(longPressDeadline - lastUptime)` | 基于**墙上时钟** `uptimeMillis`，不是动画帧回调 | ✅ |

**结论**：动画时长被改成 0 时不会产生「功能卡死」。reduce-motion 下动画走 `tween(0)`
仍然会完成 → 回调照常触发。

---

## §9. WebView —— **N/A**

全项目唯一的 WebView 是 `library/ZLibraryNativeSession.kt` 的
**游离隐藏 WebView**：

```kotlin
// ZLibraryNativeSession.kt:110-116
attach(WebView(context.applicationContext))   // 不挂载到任何视图层级
```

它的用途是：在后台加载 Z-Library 搜索页 → 轮询 DOM → 解析成 `SearchBook` 列表 →
把封面 URL / 真实下载 URL 交给 OkHttp。

- **从不挂载到视图层级** → 用户永远看不到它 → 不存在"不同 WebView 内核渲染出不同
  界面"的问题；
- 不需要 webkit runtime shim，无降级 UI；
- `performDestroy()` 在离开书库时销毁（`LibraryScreen.kt:241`）。

**结论**：视觉层面 **N/A**。
（非视觉风险：不同机型 WebView 内核版本不同可能导致 DOM 结构解析失败，属于书源
可用性范畴，不在本报告范围内。）

---

## 改动文件清单

### 新增（2 个）

| 文件 | 说明 |
|---|---|
| `app/src/main/java/com/example/ui/pageturn/PaperPalette.kt` | 由阅读主题背景色推导纸张配色（纸面 / 厚度高光 / 描边 / 纸基 alpha），供两套卷页引擎共用 |
| `app/src/main/java/com/example/ui/theme/AppFonts.kt` | 全 App 字体族唯一出口 + 阅读器字体档位表 |

### 修改（9 个）

| 文件 | 改动摘要 |
|---|---|
| `app/src/main/java/com/example/ui/pageturn/PageCurlReaderContainer.kt` | **用户报的 Bug 根因**：`backPageColor` 写死→主题推导；`backPageContentAlpha` 0f→0.58f；新增 `paperColor` 参数与 `BACK_PAGE_CONTENT_ALPHA` 常量 |
| `app/src/main/java/com/example/ui/pageturn/PageTurnContainer.kt` | 不透明纸底→半透（0.90/0.93）；镜像内容 alpha 0.42→0.88；纸色/高光/描边全改主题推导；`PageTurnContainer`/`Simulate3DCurlLayout`/`CurlFlapBackside` 增加 `paperColor`→`PaperPalette` 传参链 |
| `app/src/main/java/com/example/ui/ReaderScreen.kt` | 两个卷页入口传 `paperColor = bgColor`；安全区补 `displayCutout` 取 max；字体族改走 `AppFonts`；字体设置面板档位表改走 `AppFonts.readingFontFamilies()` |
| `app/src/main/java/com/example/ui/theme/AppFonts.kt` | （新增，见上） |
| `app/src/main/java/com/example/ui/theme/Type.kt` | `FontFamily.Default` → `AppFonts.Default`（15 处） |
| `app/src/main/java/com/example/ui/HomeScreen.kt` | `FontFamily.Serif` → `AppFonts.Serif`（5 处）+ import |
| `app/src/main/java/com/example/library/LibraryScreen.kt` | `FontFamily.Serif` → `AppFonts.Serif`（1 处）+ import |
| `app/src/main/java/com/example/ui/components/TabScreenHeader.kt` | `FontFamily.Serif` → `AppFonts.Serif`（1 处）+ import |
| `app/src/main/java/com/example/ui/help/JsonSourceGuideCard.kt` | `FontFamily.Monospace` → `AppFonts.Monospace`（2 处）+ import |
| `app/src/main/java/com/example/ui/ReaderPagination.kt` | `FontFamily.Default` → `AppFonts.Default`（1 处）+ import |
| `app/src/main/res/values/themes.xml` | 新增 `android:forceDarkAllowed=false` + 决策说明注释 |
| `app/src/main/java/com/example/MainActivity.kt` | `window.isNavigationBarContrastEnforced = false`（API 29+）；A1 段 fontScale 钳制的覆盖面注释**勘误**（行为未改） |
| `app/src/main/java/com/example/ui/feedback/Motion.kt` | `systemReduceMotion()` 从"只认 `ANIMATOR_DURATION_SCALE == 0f`"放宽到"三个动画 scale 任一 `<= 0.01f`" + 决策说明注释 |

> 合计：新增 2 个文件，修改 11 个文件。

---

## 门禁自检结果

| 检查 | 命令 | 结果 |
|---|---|---|
| Kotlin 编译 | `python .workbuddy/build.py check` | **BUILD SUCCESSFUL**（仅有 3 条 `animateItemPlacement` 已弃用的 warning，改动前就有） |
| 书架 68 项 | `python .workbuddy/audit_shelf.py` | **68 项通过，0 项失败** |

---

## 遗留 / 待 QA 真机验证

1. **折叠屏开合 / 分屏 / 多窗口 / 横屏** —— 代码路径理论上 OK（`configChanges` 齐全 +
   Compose 按 `LocalConfiguration` 重算），但必须上真机。
2. **强制深色** —— 在 MIUI / HyperOS 上打开激进深色开关，验证 Toast / 长按选词菜单。
3. **仿真翻页纸面** —— 6 套主题逐个慢拖验证 paperColor 推导效果（尤其夜间/OLED 的
   边缘描边是否够亮但不刺眼）。
4. **刘海 / 挖孔机型** —— 验证本次补的 `displayCutout` max 是否真的改善了横屏正文被裁。
5. **字体打包** —— 等设计提供字体文件 + 授权后，改动只在 `AppFonts.kt` 一处（§1-3）。
