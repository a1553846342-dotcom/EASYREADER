# 漫画阅读器二次精修 — 开源算法来源与致谢（NOTICE）

本轮精修以「读源码理解 → 按本项目架构重写」的方式移植了以下开源项目的算法思想，
并按用户指示（2026-08-30）将卷页等核心效果改为**原样 vendoring 开源原码**（内容逐行一致、行尾 LF 归一化，
保留原包名与版权头）。所有实现来源按各自许可证要求记录致谢：

## Apache-2.0 — 原样 vendoring（源码内容逐行一致、行尾 LF 归一化，保留原包名 `fi.harism.curl`）

- **harism/android-pagecurl** (https://github.com/harism/android-pagecurl) — Copyright 2012 Harri Smatt
  - 卷页本体整包 vendoring：`CurlView.java` / `CurlMesh.java` / `CurlPage.java` / `CurlRenderer.java`
    （GLSurfaceView + OpenGL ES 圆柱投影 + drop/self 双阴影，业界经典仿真卷页）
  - 克隆源：`/c/temp_r5/android-pagecurl`（master 快照；四文件 2267 行，Demo Activity 未引入）
  - 随附 `LICENSE-APACHE2.txt` 与 `NOTICE`（Apache-2.0 §4 要求）
  - 用于：`app/src/main/java/fi/harism/curl/`；集成层 `ComicHarismCurl.kt`
    （快 tap 拦截 / 索引映射 harismIdx = N−1−ourIdx（RTL）/ PageProvider 桥接 / 合成事件流自动翻页）
  - 上一阶段的 Canvas 条带重写版保留于 `ComicCurlEngine.kt`（当前不再接入渲染路径，仅作参考实现）

## Apache-2.0 — 算法移植（读源码理解 → 按本项目架构重写）

- **panpf/zoomimage** (https://github.com/panpf/zoomimage)
  - `limitScaleWithRubberBand` 缩放阻尼回弹公式（越界增量 ×(1−p)/2 衰减、2× 硬顶）
  - `DynamicScalesCalculator` 双击动态档位（medium = max(3×, 填满容器, 原始像素 1:1)）
  - 松手优先级（回弹 > fling）与 fling 撞墙即停模式
  - 用于：`ComicZoomGesture.kt`
- **saket/telephoto** (https://github.com/saket/telephoto)
  - QuickZoom 手势（双击第二击按住拖动，zoomDelta = 1 + dy×0.004/px）
  - tile 引擎「可视区按需解码」思想（本项目实现为单 tile 区域重解码）
  - 用于：`ComicZoomGesture.kt`、`ComicPageLoader.decodeRegion`
- **harism/android-pagecurl**（数学来源，见上方 vendoring 条目）
  - 圆柱卷页投影数学（弧长 s ∈ [0, πR]；x′ = F + R·sin(s/R)；明暗因子 0.1 + 0.9·√(sinθ+1)；
    背面条带镜像 + drop/self 双阴影结构）
  - 用于：`ComicCurlEngine.kt`（Canvas 条带渲染重写，参考实现）
- **Kotatsu (KotatsuApp/Kotatsu)** — `EdgeDetector.kt` / `TrimTransformation.kt`
  - 分块扫描裁边结构（RGB 逐通道容差 16、行/列密度噪声容忍、单边 1/3 防御）
  - 用于：`ComicImagePipeline.detectContentRect` v2
- **eschao/android-PageFlip**（卷页阴影双层结构参考）、**moritz-wundke/android-page-curl**
  （证明 2D Canvas 条带路线可行）

## MIT

- **bloc97/Anime4K** (https://github.com/bloc97/Anime4K) — Copyright (c) 2019-2021 bloc97
  - Clamp_Highlights / overshoot 限幅思想：`clamp(out, min3×3 − ov, max3×3 + ov)`
  - 用于：`ComicImagePipeline` 的 CAS / Unsharp / 增强（本轮 GLSL CNN 管线未引入，
    完整评估见最终报告；若二期引入将随源码携带 MIT 声明）
- **AMD FidelityFX-CAS** — overshoot 限幅常量（≈16/255）参考

## 算法思想借鉴（未复制代码，无许可义务）

- **ScanTailor Advanced**（GPL-3.0）— `VertLineFinder` / `PageLayoutEstimator`：
  中央装订缝「亮度谷/峰 + 两侧内容密度」检测思想（`detectCenterGutter` 为独立实现）
- **ciromattia/kcc**（GPL-3.0）— 跨页判定双阈值经验（1.35 疑似 / 1.8 确认）
- **AviSynth FastLineDarken 生态** — 形态学线条加深（闭运算背景场 + 深度比例加深 +
  luma_cap 191 / threshold 4 经验参数），实现为独立 IntArray 代码
- **iOS UIScrollView** — 橡胶带阻尼公式 (1 − 1/(x·c/d + 1))·d，c=0.55（用于磁吸边缘）
- **androidx.compose.foundation Pager**（Apache-2.0）— 官方 snap 参数：
  速度阈值 400dp/s、位置阈值 0.5、spring(0.85, 380) + initialVelocity 速度续接

## 已在项目中 vendoring 的库（上一阶段引入，继续使用）

- `eu.wewox.pagecurl`（oleksandrbalan/pagecurl 系，Apache-2.0）— TXT 阅读页仿真翻页仍在使用；
  漫画阅读器已切换为自研圆柱卷页引擎
- `net.engawapg.lib.zoomable`（Apache-2.0）— 条漫/无缝滚动列表缩放仍在使用

## 本轮修复升级新增（第 20/21 条）

- **bloc97/Anime4K**（MIT License, Copyright (c) 2019-2021 bloc97）—
  `Anime4KCnnWeights.kt` 的全部卷积权重由脚本从上游 GLSL shader
  （`glsl/restore/Anime4K_Restore_CNN_S.glsl`、`glsl/upscale/Anime4K_Upscale_CNN_x2_S.glsl`）
  机器提取，逐字对应；求值器 `Anime4KCnn.kt` 为本项目按 GLSL 语义的 Kotlin 实现。
  上游源码：https://github.com/bloc97/Anime4K
- **Nodlik/StPageFlip**（MIT）— 双页"书脊固定 + 单页绕轴转动 + 软硬页区分"几何模型
  （FlipCalculation 的刚体旋转/硬页不弯折语义）移植到 harism CurlView 的
  SHOW_TWO_PAGES + spread 步进 + RigidPageDecider；未复制其 DOM/Canvas 渲染代码。
  上游源码：https://github.com/Nodlik/StPageFlip
- **环境音真实录音**（全部 CC0 1.0，逐文件来源/作者见
  `app/src/main/assets/ambient/CREDITS.md`）：
  scene_rain（Ylmir）、scene_snow（TinyWorlds）、scene_sakura（isaiah658）、
  scene_firefly（Wolfgang_）、scene_ocean（RandomMind）、scene_campfire（PagDev）、
  scene_night（Siobhan Leachman / Ambrosia10）。均取自 BrenoBertucci/Terrarium
  整理的已验证 CC0 素材集（该集合对每个素材做了循环交叉淡化处理）。
