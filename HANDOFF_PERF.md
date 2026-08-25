# 性能优化接手说明（阅读器 App）

## 项目

- 路径：`C:\Users\GuanXingRen\Downloads\novel-reader (1)\novel-reader`
- 版本：0.99.19（versionCode 191）
- 构建（离线）：
  ```powershell
  $env:JAVA_HOME="D:\android studio\jbr"
  & "$env:USERPROFILE\.gradle\wrapper\dists\gradle-9.3.0-bin\79n14ral3mx1ozqr3csh2u872\gradle-9.3.0\bin\gradle.bat" -p "C:\Users\GuanXingRen\Downloads\novel-reader (1)\novel-reader" :app:assembleRelease --offline --console=plain
  ```
- 关键构建配置：JavaCompile **开启**（Room 需要）；`gradle.properties` 有 `android.experimental.enableJdkImageTransform=false` 与 `org.gradle.java.home=D:/android studio/jbr`（带 jlink）。不要动这两条。

## 现状（已完成）

- 玻璃卡视觉 = ce29d39 7 层（真实模糊+棱镜描边+光束+倒角+双层阴影+噪点+凝胶）。**已回退半分辨率模糊，当前全分辨率。**
- 统计页已改 LazyColumn（布局不变，屏幕外卡片/图表不参与绘制）。
- 已修：下载 HTML 误判（校验器认 ZIP 魔数）、书架长按删除项出屏、TXT 乱码等。

## 待解决：低端机卡顿（重点）

现象：统计页/设置页滚动卡（导航进去也偏慢）。用户要求：**卡片效果一个都不能删、视觉零变化**，只允许换更省算力的算法。

实测（Huawei 低端机，gfxinfo）：
- 统计页：掉帧 20.29%，90 分位 28ms；设置页：掉帧 27.68%，90 分位 25ms。
- 两者 `Number Slow issue draw commands` 都很高（统计 197、设置 627）→ **瓶颈是每帧绘制命令录制（CPU 侧），GPU 其次（99 分位约 21-24ms）**。
- 已试：backdrop 模糊半分辨率 → 视觉影响大、帧数几乎没提升 → **已回退，别再试这条**。

## 卡顿来源推断

每张 GlassCard 的 modifier 链（`app/src/main/java/com/example/ui/components/GlassCard.kt`）：
双层 `Modifier.shadow`（2 个 graphicsLayer）→ `clip` → `liquidGlass`（每帧 recordLayer 采样+22dp 模糊）→ `drawWithCache` 三层渐变 → `filmGrain` → `iridescentBorder`（sweep 渐变描边，Skia 上较贵）→ `crystalInnerBevel`。

统计页 5 张卡、设置页约 9 张卡同时参与每帧录制。

## 建议方向（按性价比，均需视觉零变化）

1. **装饰层渲染缓存**：把每张卡静态装饰（渐变/噪点/描边/内倒角）预录进一个 GraphicsLayer，只在尺寸/主题/按压变化时重建，滚动时只 drawLayer 一次——把每卡 ~6 批绘制命令压到 1 批。
2. **双层阴影合并/降成本**：两张卡各 2 个 shadow 层，共 18+ 层。若无法保真合并，考虑用一张自绘软阴影替代其中一层（需肉眼比对）。
3. **`iridescentBorder` sweep 渐变描边**是否可用更廉价的预渲染纹理替代（视觉同）。
4. 设置页/统计页滚动时的重组热点排查（gfxinfo 里 `High input latency` 也偏高）。

## 验证

- 编译通过 + 装机后 `adb shell dumpsys gfxinfo com.aistudio.novelreader.kxmpzq` 对比掉帧率。
- 前后截图逐像素对比确认视觉零变化。
- 用户手机序列：39HUN24525G05831（华为，连接易断，重试 adb）。

## 本轮改动记录（2026-08，待装机验证）

诊断修正：卡片因 `DrawBackdropNode.onGloballyPositioned` 随滚动位置变化**每帧整体重绘重录**
（背景采样必须跟随位移，保正确性），因此每帧成本 = 采样重录 + 全部静态装饰重录 +
Shadow/InnerShadow/Highlight 三个离屏层无条件重录 + 卡内无层子节点逐个重录。以下改动全部
针对"参数未变却重复录制/光栅化"的浪费，绘制输出与原先一致：

1. **backdrop 库三节点跳过冗余 record**（vendored 源码补丁）：
   - 新增 `backdrop/src/androidMain/.../LayerRecordKey.kt`：以解析后像素值+形状+样式为缓存键；
   - `ShadowModifier.kt` / `InnerShadowModifier.kt` / `HighlightModifier.kt`：
     键未变时跳过 `layer.record()` 与 Outline 创建（InnerShadow 的 renderEffect 同键守卫），
     onDetach 重置键。滚动中每卡每帧省 3 次离屏层重录 + MaskFilter/BlurEffect 重光栅化。
2. **GlassCard 装饰层预录缓存**（=原方向 1）：
   - 物理光路（光束/倒角带/焦散）预录进 `lightPathLayer`；
   - 棱镜描边 + 内倒角合并预录进 `edgeLayer`（共享一次 Outline 创建）；
   - filmGrain 因 Overlay 混合依赖下层像素保持原位直绘，层序不变（渐变→噪点→描边）；
   - SrcOver 结合律保证合成结果与直绘一致；仅尺寸/主题色/按压变化时重录。
3. **GlassCard 内容包独立 RenderNode**（`.graphicsLayer {}`）：
   卡片每帧重录 DL 时，卡内文本/控件只以一次 drawLayer 重放，不再逐节点重录。

4. **Tab 栏实时捕获裁剪到底部条带**（第二轮新增，最大残留项）：
   - `LayerBackdrop` 新增 `captureStripHeightPx`（0=全量）；
   - `LayerBackdropNode` 按条带尺寸录制并用 `layer.topLeft=(0, stripTop)` 维持原坐标系——
     消费方（AppBottomTabBar 的 drawBackdrop）坐标数学与像素内容完全不变；
   - MainActivity 给 tabBackdrop 设 150dp 条带。滚动时每帧捕获从全屏 1080×2400 降为
     1080×约450，重录+重栅格化降 ~5 倍；bgBackdrop（卡片采样源，需全屏采样）不动。
   - 已知边界：若有其他消费者从 tabBackdrop 采样条带以上区域会拿到透明（当前仅底栏消费，已核实）。

5. **玻璃卡模糊"整屏只烘焙一次"——⚠️ 已回退，禁止直接再启用（第三轮）**：
   - 方案：新文件 `backdrop/.../backdrops/PreBlurredBackdrop.kt`，把静态背景源经
     colorControls+vibrancy+blur 整屏执行一次后 `toImageBitmap()` 读回位图，
     GlassCard 采样退化为平移贴图（`Modifier.liquidGlassStatic` + `LocalPreBlurredGlass`）。
   - **实机结果：用户反馈液态玻璃效果完全异常（"效果和之前版本不一样"），已整体回退接线。**
   - 探针自检（单效果 DECAL 模糊）通过了但实机仍异常，说明失效点在探针盲区，最可能：
     ① 该机型快照管线对**链式 RenderEffect**（createChainEffect）不生效或输出错误
       （探针只测了单效果，未覆盖链式场景）；
     ② 自定义背景图走 rememberAsyncImagePainter 异步加载，启动 800ms 烘焙时可能尚未解码，
       位图缺图且此后不再重烘；
     ③ toImageBitmap 读回的色彩/预乘处理与屏上渲染存在设备差异。
   - 现状：MainActivity 不再提供 `LocalPreBlurredGlass`（默认 null），GlassCard 走原实时路径；
     vendor 文件与 app 侧 `liquidGlassStatic`/`rememberGlassFxChain` 保留但未接线。
   - 若将来重试：探针必须覆盖链式效果；烘焙前等待背景 Image 加载完成；并真机截图像素对比后才可上线。

## 四档渲染画质（设置 → 画面与性能，用户可调）

新增 `ui/components/GlassQuality.kt`：`RenderQuality` 枚举 + `LocalRenderQuality`。
持久化：prefs `render_quality`（默认 2=高）；MainViewModel `renderQuality` StateFlow +
`updateRenderQuality()`；MainActivity 下发 CompositionLocal 并按档位门控 backdrop 捕获层。

| 档位 | 玻璃卡 | 底栏 | 动效 |
|---|---|---|---|
| 0 流畅 | 半透明基底(0.92)+单色描边+单层阴影+光路渐变；无模糊/噪点/棱镜边 | 近实心底+细描边+单层阴影 | 吉祥物循环动画停用、封面轮播自动滚动停用 |
| 1 均衡 | 半透明基底(0.78)+噪点+棱镜描边+内倒角+光路渐变；无实时模糊 | 半透明底(0.78)+虹彩描边 | 正常 |
| 2 高（默认） | **与历史版本完全一致**：22dp 实时模糊+七层装饰+双阴影 | 实时毛玻璃 8dp+虹彩描边 | 正常 |
| 3 极致 | 高之上追加：折射透镜(refraction=true, AGSL, API<33 自动降级为无)、blur 26dp、饱和 1.45、棱镜边 alpha×1.35、按压凝胶缩放 0.97 | 毛玻璃 14dp、底更透(0.38)、描边加粗提亮 | 正常 |

- 关键门控：低于"高"时 MainActivity 不挂 bgBackdrop/tabBackdrop 的 layerBackdrop，
  LocalGlassBackdrop 为 null —— 低/中档滚动时**零捕获开销**。
- 切换立即生效（StateFlow 驱动重组）；两个 SettingsTabScreen 调用点（home Tab 与独立路由）都已接线。
- 注意：改 GlassCard 装饰缓存键时需同步 `GlassDecoKey.qualityId`。

明确不做（原因）：
- 半分辨率模糊（HANDOFF 已禁）；双层 shadow 合并（无法保真，需肉眼比对再说）；
- 统计页封面轮播、MascotEmptyState 无限动画（用户要求不动 UI 动画；未读值的动画本就不触发重绘）；
- LazyColumn item 再包 graphicsLayer（卡片已有 4 层嵌套 RenderNode，收益趋零）。

已知残留（理论极小差异）：装饰进离屏层引入 8bit 预乘中间量化（±1/255 级别，不可见）。

## MAX 档崩溃修复与特效重构（后续轮次）

**闪退根因**：MAX 开启的折射透镜走 AGSL 链式 RuntimeShader，该华为驱动在构建/挂载阶段
抛异常；画质持久化导致启动即崩 → 死循环变砖。三重修复：
1. vendor `DrawBackdropNode.updateEffects` 包 try/catch——着色器构建失败优雅降级为无效果；
2. 启动看门狗（MainActivity.onCreate）："极致"档 20 秒内连崩两次自动降回"高"；
3. 折射参数驯化 16/28dp → 10/18dp（保留启用于 MAX）。

**MAX 特效新增**：卡片流光 sheen（`Modifier.glassSheen`，6.5s 掠过光带）、光束/棱镜边增强、
按压凝胶缩放；开关轨道与主按钮内重力粒子（`GravitySensor` + `maxGravityParticles`，
随设备倾角运动反弹，仅 MAX 挂载）；MaxJunoSlider 玻璃滑条（外发光填充、常驻光晕滑块、
方向性彗尾、拖动挤压）；分段选择器选中态主题色光晕。

## UI 客观审查（两轮）

Round1：三种开关补 Role.Switch/toggleableState 语义；两处滑条补 progressBarRangeInfo+
stateDescription；设置页分区标题统一为 SettingsSectionHeader（两处 16sp→14sp）；
三处 Divider 迁移 HorizontalDivider+outlineVariant。
Round2：SegmentedPillSelector/JunoSlider 触控目标提至 48dp；CustomSwitch/JellySwitch/
AppLiquidSwitch 关闭态灰阶由硬编码改为主题 outlineVariant/surfaceVariant（修暗色缺陷）。

验证工具：项目根 `verify_perf.ps1`（install / gfxreset / gfx / shot / diff 子命令）。

## UI/UX 全面重构（设计攻坚阶段，40+ commits）

### 滑条体系（FluidSlider 复刻 + 自研 InkSlider）
- **FluidSlider**：纯 Canvas 忠实复刻 Ramotion FluidSlider——胶囊轨道、按下气泡 Overshoot 升起、
  metaball 液态连接（bottomCircle 圆心必须在 `vOff+botCD/2`）、数值圆盘、OvershootEasing。
  已修：maxMove coerceAtLeast(1f) 防窄容器反转；触控区扩大 ±0.5×barH。
- **InkSlider**（已删除）：被 FluidSlider 完全替代。
- **排版面板 3 个 Material3 Slider**：统一 MintPrimary 主题色。
- **阅读器底栏章节 Slider**：barContentColor 主题色。

### 开关体系（全统一为 SquishyToggleSwitch）
- 从 [Swapnil-J-Patil/Switch-Animation-Jetpack-Compose](https://github.com/Swapnil-J-Patil/Switch-Animation-Jetpack-Compose/) 原样拷贝并适配：
  受控模式(checked/onCheckedChange)、Role.Switch 语义、触觉反馈、动画提速 1300→550ms。
- 替换位置：设置页×4、书源管理×1、排版面板×1。JellySwitch 仅作无玻璃回退。

### 阅读器重构
- **顶栏**：定制 Column（非 TopAppBar），章节标题+进度副标，hairline 底线，
  图标 20dp 统一。移除低频操作（搜索/目录/标注），保留 书签/听书/排版 三键。
- **底栏**：与顶栏同风格，navigationBarsPadding。
- **新增功能**：
  - 自动滚屏模式（顶栏⬍按钮，60fps dispatchRawDelta，浮停指示器点击停止）；
  - 目录自动定位当前章节（LazyListState.scrollToItem）；
  - 字体切换（默认/衬线/黑体/等宽 FilterChip）；
  - TTS 按钮/章节滑条主题化 barContentColor。

### MAX 卡片四层特效
1. `maxCardAura`：虹彩呼吸辉光（3层描边外扩，sin alpha 波动，primary↔secondary 色相插值）；
2. `chromaFlowEdge`：边缘光弧巡游（sweepGradient + rotate(phase×360°)——**注意 phase 必须作用于绘制**）；
3. `glassSheen`：高光带扫过（已验证正确）；
4. `shimmerPearl`：珠光漂移（radialGradient 光斑 cx/cy 缓移）。
全部仅在 RenderQuality.MAX 时激活，其余档位返回 this（零开销）。

### 新增功能页
- **缓存管理** (`CacheManagementScreen`)：总览大字→比例色带+图例→五分类逐项🗑→一键清理。
  scanTrigger 驱动重扫；Dispatchers.IO + Main 切换；per-category delete。
- **每日阅读目标**：`prefs.dailyGoalMinutes`(5-480)，统计页目标环按此计算，±15min 步进器调整。
- **设置关于区块**：App 名 + PackageManager 版本号 + 标语。

### 全局一致性
- IconButton → AppIconButton：全局审计通过（0 raw remaining）；
- 触觉反馈：`clickableWithFeedback` 补充 haptic tick（29+ 调用点受益）；
- 搜索防抖：HomeScreen 300ms debounce；
- 除零保护：chapters.size/maxMove 全部加 guard；
- contentDescription=null 均为装饰性图标（合规）。

### ⚠️ 已知考虑事项（非 bug，后续版本可改进）

| 事项 | 影响 | 建议方案 |
|---|---|---|
| FluidSlider 气泡文字在大字体(fontScale>1.3)时可能溢出 | 低（仅超大系统字体用户） | 将 fontSize 改为固定 dp 或按 fontScale 调整气泡尺寸 |
| 年视图热力图 372 个 Box 非懒加载 | 中（低端机年视图可能卡顿） | 重构为单次 Canvas 绘制 |
| 音量键翻页未实现 | 功能缺失 | 需 Activity.dispatchKeyEvent + ViewModel 回调桥接 |
| 统计导出 PDF 未实现 | 功能缺失 | 需要 FileProvider 配置 + 布局引擎 |
| MAX 特效 6 个 InfiniteTransition/卡 | 性能（多卡片页面） | 可合并为单动画时钟或加可见性检测 |

### 🔧 开发注意事项

1. **FluidSlider 是受控组件**：`position` 参数由调用方持有，内部 `localFraction` 仅在拖动时同步。
2. **SquishyToggleSwitch 双模式**：传入 checked 即受控；不传则内部维护。受控模式下动画由 flip() 驱动。
3. **chromaFlowEdge 的 phase 必须作用于绘制**——曾因 phase 只计算未使用导致巡游动画静止。
4. **maxMove 必须 coerceAtLeast(1f)**——否则窄容器下拖动方向反转。
5. **dailyGoalMinutes 使用 mutableIntStateOf**——直接读 prefs 不会触发重组。
