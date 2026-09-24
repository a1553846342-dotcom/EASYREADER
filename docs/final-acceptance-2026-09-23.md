# 最终验收报告（跨机型适配 + 仿真翻页修复）

日期：2026-09-23　包名：`com.aistudio.novelreader.kxmpzq`　构建：`build.py release`（offline）

---

## 一、门禁

| 项 | 结果 |
|---|---|
| `python .workbuddy/build.py check` | **BUILD SUCCESSFUL** |
| `python .workbuddy/build.py release` | **BUILD SUCCESSFUL**，`adb install -r` → Success |
| `python .workbuddy/audit_shelf.py` | **68 项通过，0 项失败** |

## 二、APK 体量（用户要求新增 ≤ 3MB）

| | 字节 | MB |
|---|---|---|
| 基线 `baseline_app-release.apk` | 20,796,927 | 19.833 |
| 最终 `app-release.apk` | 22,535,867 | 21.491 |
| **增量** | **1,738,940** | **1.658** ✅ |

增量归因（zip 条目逐项比对）：字体 1,720,939 B（deflate 后）+ 资源表 256 B + 其余 15,559 B。
即 **99.1% 就是字体本身**，其余改动（自绘阴影、blur 降级、适配机制）零体积代价。

包内字体条目实证：
```
res/9h.otf  raw=1,202,520  deflate=985,296   (Noto Serif SC 子集)
res/oN.otf  raw=  859,944  deflate=735,643   (Noto Sans SC  子集)
```

---

## 三、用户报的 Bug：仿真翻页背面不透明纯色 —— 已修复并实测

### 根因（注意：不是最初定位的那处）
`ReaderScreen.kt:2143` 的分派是：`SIMULATE(0)` 走 **`PageCurlReaderContainer`**（wewox/pagecurl 引擎），
`PageTurnContainer` 只负责 覆盖/平移/渐变/滚动。所以最初按 `PageTurnContainer.kt:804` 定位的是**死分支**。

真凶在 `PageCurlReaderContainer.kt:96-97`：
```kotlin
backPageColor = Color(0xFFE8E4DC)   // 写死米黄，不跟随阅读主题
backPageContentAlpha = 0f           // 引擎内部 → drawRect(色.copy(alpha = 1 - 0))
```
`alpha = 1 - 0 = 1` ⇒ **在镜像内容之上糊了 100% 不透明纯色**，背面一个字都没有。

### 修法
- `backPageContentAlpha` → `0.58f`（纸 42% + 镜像字 58%），`backPageColor` 改由
  `pageturn/PaperPalette.kt` 从阅读主题背景色推导。
- 同款隐患分支 `PageTurnContainer.kt` 的 `CurlFlapBackside` 同步修（不透明底 → 0.90/0.93
  半透明纸基；镜像字 alpha 0.42 → 0.88；纸色/高光/描边全部随主题）。
- **未删任何效果**：折角路径 `buildFlapPath`、投影 `shadowStripPath`、内侧折痕阴影（6 段渐变）、
  纸边高光、环境光遮蔽径向渐变、厚度双笔描边 —— 逐项保留。

### 实测（最终合并包，三档主题）
| 主题 | 背景 RGB | 背面均值 RGB | 亮度 std | 是否旧米黄 (232,228,220) |
|---|---|---|---|---|
| 夜间 | (24,25,28) | **(34.1, 35.8, 37.0)** | **31.57** | 否 |
| 默认白 | (255,255,255) | **(235.3, 235.4, 235.6)** | **39.98** | 否 |
| 羊皮纸 | (251,240,217) | **(234.4, 224.8, 204.7)** | **30.62** | 否 |

- 旧实现 std ≈ 0（纯色）；判据灵敏度自证：同手法卷到**空白页**时实测 std=0.00、唯一色=1。
- 左/右缘亮度剖面三档全部单调渐变（如默认白右缘 215→255），**折痕与投影梯度完整**。
- 截图：`_review/qa_back_{night,light,sepia}.png`

---

## 四、跨机型适配

详细报告见 `docs/cross-device-ui-audit.md`（条款逐条对照表）。

### 已修
| 项 | 位置 |
|---|---|
| 系统强制深色重绘 | `themes.xml` `forceDarkAllowed=false` + `MainActivity` `isNavigationBarContrastEnforced=false`（API29+ guard） |
| 字体走厂商系统字体 | 新增 `res/font/` 打包 Noto Serif/Sans SC（**SIL OFL 1.1**），出口收敛到 `ui/theme/AppFonts.kt`；阅读档位 0/1/2 走打包字体，等宽保持通用族，自定义 TTF 原样 |
| elevation 阴影跨 ROM 不一致 | 新增 `Modifier.consistentShadow()`（Skia `BlurMaskFilter`，**API 21+ 全可用**），13 处品牌关键面改自绘 |
| `Modifier.blur` 低版本失效/**崩溃** | `ComicChaptersScreen.kt:549` 原直接调 `RenderEffect.createBlurEffect`，**API<31 会 VerifyError 闪退**，已加判级 + 磨砂降级；`LiquidGlass` 加 `frostedGlassFallback()` 覆盖 8 个玻璃调用点 |
| 动画被系统关闭时卡死 | `SettingsControls.kt:460` 拖动态加 `try/finally`；`SquishyToggle.kt:81` 多条动画链并发抢 Animatable → 改单一驱动；`WeeklyReadingChart` 补 `LocalReduceMotion` 分支 |
| 挖孔安全区 | `displayCutout` 取 max |
| 动画缩放判定精度 | `ui/feedback/Motion.kt`（原只认 `ANIMATOR_DURATION_SCALE == 0f`） |
| 宽度断点资源化 | `res/values/dimens.xml` + `values-sw600dp/` + `values-sw840dp/` + `rememberAdaptiveSizing()` |

### 判定 N/A（附理由）
- **动态取色**：全项目 0 处 `dynamicColorScheme`，配色为固定品牌调色板。
- **原生控件皮肤化**：`res/` 下无 `layout`、无 `AndroidView`、无原生 Switch/SeekBar，纯 Compose 自绘。
- **资源限定符**：项目已有 `ui/adaptive/AdaptiveSpec.kt` 断点体系，本轮进一步下沉到 res/dimen。
- **WebView 内核差异**：唯一 WebView 是 Z-Library 的游离隐藏实例，从不挂载到视图层级。

### 刻意不改（附理由）
- `ShelfSelectionHost.kt` 3 处 `shadowElevation`（拖拽幽灵卡 / 飞行幽灵卡 / 心形迸发）：
  阴影位于**带 scale+rotation+alpha 的 graphicsLayer 内部**，自绘放外层不跟随缩放旋转、
  放内层被离屏合成裁掉；且属手指下的瞬态动效，跨机型阴影浓淡几乎不可感知。
- 9 处 1~6dp 次要装饰 elevation：改为自绘等于 19 处视觉重写，风险大于收益。

### 屏幕形态
五形态（小屏 360dp / 基准 393dp / 平板 960dp / 横屏 914dp / 分屏 411dp）× 7 页面各 35 张截图，
**实测无需要改数值的布局问题**；本轮只把宽度数值下沉到 res/dimen 建立差异化入口，不改变观感。
报告：`docs/adaptive-screen-audit.md`

---

## 五、回归

| 用例 | 结果 |
|---|---|
| `bug1` 新分类打开/退出/再点/长按选中 | ✅ |
| `bug2` 退出阅读页仍停在最近分类 | ✅ |
| `bug3` 收藏底栏 4 项 + 选两本移动两本 | ✅ |
| `drop_verify books` 拖书到分类 | ✅ |
| `drop_verify favs` 拖收藏到分类 | ✅ |
| `drop_verify multi` 两本各自归位 | ✅ |
| `drop_verify after` 移动后原位点击/长按 | ✅ |
| `drop_verify hidden` 拖动时原位隐藏 | ✅ |
| 字体档位 in-app（衬线/黑体） | ✅ 无豆腐块（`_review/final_font_{serif,sans}.png`） |

⚠️ 首次串行跑时 `bug2`/`bug3` 报 ❌，逐项复核为**脚本间状态污染**（前序脚本把书移走 / App 停在
空分类），单独重跑即 ✅。已修正三处脚本脆弱性：`bug3.fav_cards` 取消 `y>1150` 过滤、
`qa_lib.read_prefs` 加 root/在线重试、`qa_v3_final.find_book` 先归位到「默认」并滚回顶部。

---

## 六、未完成 / 需真机补充

1. **厂商 ROM 强制深色下的原生 Toast / 分享面板观感** —— 模拟器复现不了 MIUI/OneUI 行为，需云真机。
2. **真实折叠屏铰链开合**（`smallestScreenSize` 连续变化）—— `wm size` 只能改静态分辨率。
3. **厂商分屏行为**（华为/三星多窗口）—— 模拟器无此实现。
4. `ReaderPagination.kt:92` 行距下限（`coerceAtLeast(fontSizePx*1.2f)`）与实际渲染用的
   用户 `lineHeight.sp` 不一致；用户把行距调到低于「字号×1.2」时分页测量会偏差 ±1 行。
   属排版引擎改动，本次未动。

## 七、过程中的事故（如实记录）

- 首个工程师任务 → 完成；渲染一致性 → 完成；QA 第一轮 → 完成。
- 屏幕形态 agent：跑 66 分钟后 **502（socket hang up）**，重试 **429 额度耗尽**，两次均未回报。
  其代码改动与 33 张截图**已落盘未丢**，报告由主理人补写。
- 并行三个 agent 时**共用一台模拟器**导致 adb 争用（前台被 Chrome 抢占、dump 返回空）；
  且 gradle 缓存锁会因并发构建报瞬时 `FileNotFoundException`。教训：并行要按
  **文件所有权 + 设备所有权**双向切分，不能只切文件。
