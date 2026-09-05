# 二次精修：车轮评估表（开源方案采用决策）

日期：2026-08-29 ｜ 依据：GitHub 实读源码调研（详见最终报告「开源方案采用报告」）

判定标准（按任务书第十七/十八条）：实际视觉效果 + 交互体验 + 性能 + 稳定性 + 可维护性，
不以 star 数论英雄；能成熟解决且可安全本地化的优先本地化（vendoring / 算法移植）。

## 一、评估表

| # | 候选方案 | 解决什么问题 | 当前实现差距 | 开源实现优势（源码确认） | 采用 | 本地化方式 |
|---|---|---|---|---|---|---|
| 1 | panpf/zoomimage 1.6.0（Apache-2.0）— `limitScaleWithRubberBand` | 缩放阻尼回弹 | 硬 clamp，双指缩过界瞬间弹回，生硬 | 线性衰减公式：越界增量 ×(1−p)/2，硬顶 2×max，阻尼自然 | ✅ | 移植算法公式（~15 行纯函数） |
| 2 | saket/telephoto 0.19.0（Apache-2.0）— QuickZoom | 双击按住拖动连续缩放 | 长按固定 2.8x，倍数不可控 | 双击第二击按住上下拖：`zoomDelta=(1+dy×0.004)`，Google Photos 手感 | ✅ | 移植手势状态机 + 公式 |
| 3 | Compose 标准 `splineBasedDecay` + 撞墙即停（zoomimage/telephoto 同款） | 放大态松手惯性平移 | 无 fling，松手急停 | 官方样条衰减 + 越界立即 stop，符合系统手感 | ✅ | 标准库 API + 30 行封装 |
| 4 | zoomimage `DynamicScalesCalculator` | 双击缩放目标智能计算 | 固定 2.5x，宽页/高清页体验差 | `medium=max(3×fit, 填满容器, 原始像素1:1)` 三档循环，0.35 容差防抖 | ✅ | 移植算法（~20 行） |
| 5 | telephoto `canConsumePanChange` Pager 仲裁 | 放大态与 Pager 手势冲突 | 已有边缘检测方案工作正常 | consumedPan 精确计算更细腻 | ⛔(照搬复判) | 保留现有：20 轮设置切换 + 缩放 20 组压力实测零冲突（v3 附录 A）；telephoto 仲裁与其手势状态机深耦合，单独搬仲裁公式需重写整个缩放手势栈（照搬成本>收益：实测无一处可归因仲裁的缺陷） |
| 6 | zoomimage/telephoto/SSIV tile 引擎 | 超大图放大细节 | 2800px 全页解码，深放大发糊 | tile 金字塔 + 双层缓存 + 淡入 | ✅(简化) | 移植思想：高倍缩放时对可视区 BitmapRegionDecoder 原分辨率重解码（单 tile 版本，400→150 行，风险可控；完整 tile 金字塔与手势耦合深，本轮不搬） |
| 7 | Compose Pager 官方 snap 参数（foundation 1.7/1.8 源码） | 磁吸翻页手感 | 仅位置阈值 0.25 + 无速度判定 + 无速度续接 + 无边缘阻尼 | 速度 <400dp/s 看位置（0.5 阈值）、≥400dp/s 按速度方向；spring(0.85,380) + initialVelocity 续接 | ✅ | 参数与判定公式对齐官方 |
| 8 | iOS UIScrollView 橡胶带 c=0.55 | 磁吸首/末页边缘阻尼 | 无（硬位移） | `(1−1/(x·c/d+1))·d` 渐近阻尼 | ✅ | 公式移植 |
| 9 | harism/android-pagecurl（Apache-2.0）圆柱卷页 | PageCurl 真实卷曲 | wewox 折线镜像 = 平面翻折，非卷曲 | **2026-08-30 升级为整包照搬**：`fi.harism.curl` 四文件（CurlView/CurlMesh/CurlPage/CurlRenderer，2267 行）内容逐行一致（LF 归一化）vendoring + GLSurfaceView 集成（`ComicHarismCurl.kt`：快 tap 拦截 / RTL 倒序索引映射 / PageProvider 信箱合成 / 合成事件流自动翻页）；60 连翻零 ANR、PSS 218→209MB 无泄漏、自动阅读 6s/页验证通过。Canvas 条带版（ComicCurlEngine.kt）退役为参考实现 | ✅(整包vendoring) | 原版 GL 渲染（用户指示：照搬开源不手写） |
| 10 | Kotatsu `EdgeDetector.kt`（Apache-2.0） | 自动裁边误判 | 单像素亮度阈值：噪声点即停、彩色边框失效 | 分块扫描 + RGB tolerance(16) + dd 噪声容忍 + 单边 1/3 防御 | ✅ | 结构移植（适配白/黑/自动三模式） |
| 11 | Kotatsu `TrimTransformation.kt`（Apache-2.0）同色比较 | 彩色/米色边框 | 仅亮度比较 | 与边缘基准色逐通道比较（tolerance 20），彩边可裁 | ✅ | 算法融合进 #10 |
| 12 | ScanTailor `VertLineFinder`/`PageLayoutEstimator`（GPL-3.0）中央谷检测思想 | 跨页拆分误判/漏判 | 仅 aspect≥1.35 | 中央装订缝亮度谷/峰 + 两侧内容密度对比 | ✅(思想) | 只借鉴思想重写（GPL 代码不复制）；KCC 的 1.8 双阈值经验一并参考 |
| 13 | bloc97/Anime4K v4.0.1 GLSL（MIT） | 真神经网络增强 | Sobel 伪增强，代际差距 | 7 层 CNN shader、GLES 3.0 可跑、零模型文件 | ⏸ 缓行 | 结论：技术可行（MIT、minSdk 24 OK），但需 EGL/FBO 七 pass 管线（~1000 行 GL 代码），当前环境无法真机验证渲染正确性与性能；按任务书「不盲目为 AI 引入不可验证路径」，本轮先升级 CPU 路径（#15/16/17），GLSL 列为二期（完整评估见最终报告） |
| 14 | ncnn + Real-ESRGAN/waifu2x（BSD-3） | GAN 级超分 | — | realesr-animevideov3-x2 模型 2.4MB，但 so 10–15MB、单页 0.5–6s、JNI 工程 | ⛔ | 不采用（体积/延迟/发热损害阅读体验） |
| 15 | AMD FidelityFX CAS overshoot 限幅（MIT） | 锐化光晕 | CAS 无输出限幅，强锐化出白边 | `clamp(out, min3×3−ov, max3×3+ov)` 一行消灭 halo | ✅ | 公式移植 |
| 16 | FastLineDarken（AviSynth 经典形态学） | 线条加深、平坦区零扰动 | Sobel 平坦噪声区也有响应 | `exin(膨胀→腐蚀) → diff(阈值4,luma_cap191) → 按深度比例加深`，天然只作用线条 | ✅ | 算法移植（IntArray 重写） |
| 17 | LimitedSharpen 系 overshoot clamp | 防 halo（通用） | unsharp/Lanczos 无限幅 | 同 #15 公式，作用到 unsharp 与 Lanczos 输出 | ✅ | 公式移植 |
| 18 | AGSL RuntimeShader（API 33+） | GPU 滤镜 | — | 无纹理采样原语、需多 pass 位图搬运；33 以下不可用 | ⛔ | 不采用（minSdk 24 主路径不分裂）※#21 实测修正采样结论 |
| 19 | usuiat/Zoomable（engawapg 新家）**v2.13.0**（Apache-2.0） | 条漫缩放 | 曾误判"vendoring 的是无阻尼/仲裁旧版" | **2026-08-30 照搬复判：已克隆上游 v2.13.0（tag v2.13.0）与 vendored 八文件逐字节 diff = 0 行差异**——本地即最新版，阻尼（limitScaleWithRubberBand 同款）/手势仲裁/双击档位俱在 | ✅(已照搬) | 维持 vendoring v2.13.0（上游实读复核，无需替换；此前"旧版"结论系误判，特此更正） |
| 20 | eschao/android-PageFlip（Apache-2.0） | 卷页完整工程 | — | 圆柱+背面+双阴影最完整，OpenGL ES 2.0 管线；**2026-08-30 照搬复判：与 #9/#C.1 同类竞品**——卷页本体已整包 vendoring harism/android-pagecurl（四文件内容逐行一致（LF 归一化 diff=0）+ GLSurfaceView 集成 + 60 连翻压力通过），再引入第二套 GL 卷页工程为零增益纯冗余 | ⛔(已被照搬件覆盖) | 不采用：同类功能已有更好的照搬实现（harism）在役，escharo 的优势（更细的阴影参数）不构成替换已验证管线的理由（照搬成本>收益实测：重做集成+全量压力回归 vs 收益=0 新能力） |
| 21 | Compose graphicsLayer + RenderEffect（API 31+）/ AGSL RuntimeShader（API 33+）— LUT/色矩阵/卷积类滤镜 GPU 化（任务书 §六.8 遗留调研，2026-08-30 实测） | CPU 逐像素滤镜提速 | JVM 逐像素 287ms/页（中位，2000x2800 真实样张 ×10） | ①RenderEffect.createColorFilterEffect：色矩阵单 pass GPU 化，链式组合官方支持（[RenderEffect 文档](https://developer.android.com/reference/android/graphics/RenderEffect)、[官方文章](https://medium.com/androiddevelopers/blurring-the-lines-4fd33821b83c)）；②AGSL `uniform shader` + `eval()` **可采样**（仅限 BitmapShader/RenderNode 输入，[RuntimeShader 源码](https://android.googlesource.com/platform/frameworks/base/+/master/graphics/java/android/graphics/RuntimeShader.java)、[官方 AGSL 教程](https://medium.com/androiddevelopers/agsl-made-in-the-shade-r-7d06d14fe02a)、[shady 示例库 MIT](https://github.com/drinkthestars/shady)）——修正 #18「无采样原语」的旧判据，卷积类单 pass 可行；③实测（模拟器 API35 swiftshader **软件渲染，数据仅参考**）：ColorMatrix RenderEffect 79ms、AGSL（饱和+3x3 锐化一体）94ms——即使软件渲染也快 ~3×，真机 GPU 差距只会更大 | ⛔(主路径) | **不采用为主路径**：API 31+/33+ 覆盖率不满足 minSdk 24 主路径不分裂的硬约束；CPU 主路径保持。**二期可选优化分支**：`Build.VERSION.SDK_INT >= 31` 时 RenderEffect 离屏加速（色矩阵/饱和度类先行，AGSL 33+ 再叠卷积锐化），预期真机收益 >3×；分支代码已验证可行（GpuAbTestActivity 实测通过） |

## 二、License 合规备忘（本地化随代码标注）

- #1/#4 zoomimage、#2/#3 telephoto、#9 harism、#10/#11 Kotatsu：Apache-2.0 — 移植算法需在源文件头与 docs 记录来源项目与版权；Apache-2.0 允许闭源商用衍生，附 NOTICE 说明即可。
- #12 ScanTailor/KCC：GPL-3.0 — 只借鉴算法思想（亮度谷检测、阈值经验值），不复制任何代码行。
- #15 AMD CAS：MIT — 公式级移植，标注。
- #16 FastLineDarken：AviSynth 脚本生态（参照 VapourSynth 移植版思路）— 算法思想无版权负担，实现全部重写。
- 数学公式本身不受版权保护；所有移植均以「读源码理解 → 按本项目架构重写」方式进行，并在文件头注明 inspiration 来源。

## 三、优先级排序（体验提升 × 使用频率 × 成本）

1. 缩放手感（#1#2#3#4）——每次阅读必经，成本低
2. 磁吸翻页手感（#7#8）——高频交互，成本低
3. 裁边准确率（#10#11）——中频，成本低，效果可量化测试
4. 拆页准确率（#12）——中频，成本低
5. PageCurl 真实卷曲（#9）——视觉敏感，成本中，Roborazzi 可截图验证
6. 滤镜/增强质量（#15#16#17）——中频，成本低，单测可验证
7. 高倍缩放细节（#6）——低频痛点，成本中
8. Anime4K GLSL（#13）——缓行（不可验证环境下的高风险渲染路径）
