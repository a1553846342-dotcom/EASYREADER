<div align="center">

<img src="./app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.webp" width="110"/>

# Ciallo阅读（EASYREADER）

**Android 阅读器 / 在线书库聚合下载器**

Kotlin · Jetpack Compose (Material 3) · MVVM · 单 Activity

<img src="./docs/demo-1.gif" width="270"/>
<img src="./docs/demo-2.gif" width="270"/>
<img src="./docs/demo-3.gif" width="270"/>

[下载 APK](https://github.com/a1553846342-dotcom/EASYREADER/releases) ·
[功能特性](#功能特性) ·
[使用教程](#使用教程) ·
[FAQ](#faq) ·
[提交 Issue](https://github.com/a1553846342-dotcom/EASYREADER/issues)

![Android](https://img.shields.io/badge/Android-API%2024%2B-green)
![Release](https://img.shields.io/badge/Release-v1.0.5-orange)
![Architecture](https://img.shields.io/badge/Architecture-MVVM-blue)
![UI](https://img.shields.io/badge/UI-Compose%20M3-8A2BE2)
![License](https://img.shields.io/badge/License-All%20Rights%20Reserved-lightgrey)

</div>

***

## 概述

Android 端小说 / 漫画阅读器，内置多书源在线聚合搜索与下载。本地阅读、在线找书、离线管理一体化：搜索到的书不是"只能看看"，而是直接下载到书架离线阅读。

| 项目                     | 内容                                                                  |
| ---------------------- | ------------------------------------------------------------------- |
| 一句话简介                  | 支持本地 TXT/EPUB/MOBI/AZW3/漫画导入、自定义书源、在线书库聚合搜索与多格式下载的安卓阅读器兼资源下载器       |
| 当前版本                   | 1.0.5                                                               |
| 开发状态                   | 个人项目 · 活跃开发中                                                        |
| 最低系统                   | Android 7.0（API 24）                                                 |
| compileSdk / targetSdk | 35                                                                  |
| 技术栈                    | Kotlin 2.0 + Jetpack Compose（Material 3）+ MVVM + Room + WorkManager |
| 架构                     | MVVM + StateFlow + Repository，单 Activity + Navigation Compose       |
| 存储                     | Room（8 实体 / version 6）+ SharedPreferences                           |
| 测试                     | 全量 325+ 项（JVM / Robolectric）                                        |
| APK 体积                 | 约 23MB（含 ONNX Runtime 与量化气泡模型；不含 OCR 模型，按需下载）                       |

***

## 功能特性

### 📄 文件格式

| 格式                      | 支持情况     | 说明                                                                                                      |
| ----------------------- | -------- | ------------------------------------------------------------------------------------------------------- |
| TXT                     | 完整       | 大文件秒开、按体积分段、自动章节识别、GBK / UTF-8 / UTF-16 编码自适应                                                           |
| EPUB                    | 完整       | OPF / NCX / spine 解析，保留目录、封面、元数据；ZIP 文件名 UTF-8 / GBK / GB18030 三级回退；封面三级提取策略（规范路径 → 文件名 → 首图/最大图）       |
| MOBI / AZW3 / AZW / PRC | 完整，零依赖自研 | PDB 容器 + KF8 混合段识别；PalmDOC LZ77 与 HUFF/CDIC 哈夫曼字典解压（Kindle 正文压缩算法的纯 Kotlin 移植）；EXTH 元数据；封面提取；DRM 检测占位提示 |
| DOCX                    | 完整       | 解 `word/document.xml`，按 `<w:p>` 段落抽取                                                                    |
| FB2                     | 完整       | `<title-info>` 元数据 + `<section>` 章节切分                                                                   |
| CBZ / ZIP 漫画            | 完整       | 自然排序（`page_2 < page_10`）；GBK 文件名回退；CBR/CB7（RAR/7Z 容器）提示转 CBZ/ZIP 后导入                                    |
| PDF                     | 按页位图渲染   | `PdfRenderer` 逐页渲染走漫画管线，翻页模式阅读；文本层解析在 Roadmap                                                           |

### 📖 文字阅读器

- **翻页动画（五种）**：仿真 3D 卷页（真实折角、阴影、纸背反光）/ 覆盖 / 平移 / 渐变淡出 / 上下滚动，每种单独调过手感

- **真实排版分页引擎**：基于 Compose `Paragraph` 测量，分页点均为真实行边界，段落跨页不丢行；超大章节（>20 万字符）分块渐进测量 + LRU 分页缓存，首屏几乎即时

- **排版自由**：字号（含 A−/A+ 步进）/ 行距 / 页边距 / 首行缩进全部可调；五套阅读主题（白底 / 羊皮纸 / 夜间 / 护眼 / 纯黑）预览卡；字体默认 / 衬线 / 黑体 / 等宽可选，支持导入 TTF

- **「阅读排版」半透明浮层**：调参时可实时对照书页效果，亮度调光内置其中（与系统亮度分离）

- **FluidSlider 流体滑条全局统一**：按下时气泡从轨道弹出（Overshoot 回弹）、metaball 液态连接、数值显示在气泡内；阅读器字号 / 行距 / 页边距 / 亮度、章节拖动、漫画页码、护眼强度全部为同一套 goo 风格，经手势仲裁（松手提交、标签淡出、连续滑动不中断）

- **自动滚屏**：60fps 平滑滚动；顶栏一键开启 / 关闭，底部浮停指示器点击停止；滚过 20% 出现回顶悬浮按钮；模式切换自动关闭；与 TTS 听书互斥

- **卷页下拉书签**：仿真卷页模式下拉页面出现蓄力书签卡片（方向仲裁 + 阻尼回弹），松手即收藏当前页；普通模式书签走顶栏，图标区分「加书签 / 书签列表」

- **TTS 朗读**：系统引擎无缝接入，语速音量跟随系统，边听边看

- **书签、划线高亮、全文搜索**；目录自动定位（打开章节目录自动跳到当前阅读位置，千章大书秒定位）

- **护眼模式**：夜间模式走深色配色 + 降低屏幕亮度刺眼感；护眼滤镜叠加暖色调色（0–65% 流体滑条实时预览）

- **定时休息**：15 / 30 / 45 / 60 分钟预设或自定义任意时长，到点弹窗提醒

- **读完徽章**：读到最后一章最后一页后书架卡片挂金色「已读完」标记；重开停在末页不误触发庆祝动画

- 阅读进度按书按章实时保存，重开即续

### 🎨 漫画阅读器（本地 / 在线统一引擎，v1.0.5 整体重做）

v1.0.5 从零重做了整套漫画引擎：经历 28 条修复升级（两轮 AI 执行 + 四路独立子代理终审，均分 8.6–9.1）→ 六条实测反馈根治（四族共享根因分析）→ 三个反馈返工轮，漫画域单测 137 项全绿。

#### 阅读模式与布局

- **阅读模式**：单页 / 双页 / 条漫 / 无缝滚动 / 磁吸；阅读方向：左→右 / 右→左 / 上→下（影响点按区、滑动、双页排列与动画方向）

- **`ComicScrollStrategy`** **策略类**：条漫模式保留用户自定义间距 + 可关磁吸；无缝模式强制 0 间距 + 像素级进度上报 + 宽预取窗口

- **双页排版**：首页单独显示、顶 / 中 / 底对齐、双页位置修正（X/Y）、跨页临时合页

#### 翻页动画引擎

- **无 / 平移（SLIDE）/ 渐变（FADE）/ 仿真卷页（CURL）** 三引擎 + 组合（卷页容器另五种模式见文字阅读器）

- **FADE 真交叉淡化**：离场页恒为底、进场页自 0 淡入，`fadeCancelOffset` 按引擎镜像分轴抵消 LTR / RTL / TTB 布局位移——无黑场下陷、无渐变段漂移（旧实现 RTL 下曾漂移 111px，三轮录屏逐帧复审修复）

- **SLIDE 越界橡胶回弹**：reverseLayout 首末页外侧方向 sign 归一化，RTL 下回弹不再吞掉前进手势

- **CURL 仿真卷页**（vendored harism OpenGL 引擎，1300 行整合层）：

  - **双页书脊模式**（CURL+DOUBLE）：左右半页、书脊 = 屏幕中线、只有抓取侧绕轴卷曲；`buildCurlFlatUnits` 把跨页 spread 展开为逐页序列（单槽补空位保持偶数对齐）；一次卷曲推进整 spread

  - **刚体封面**：首 / 末页刚体平折（StPageFlip 软硬页模型），封面像真书一样整体翻动

  - **透纸背面**：翻起后背面 = 本页正面 1/6 降采样轻模糊 + 20% alpha 叠纸底，「单面印刷透纸」观感

  - **半透明 GL**：EGL alpha 通道 + clear alpha=0，阅读背景 = GL 之后的静态 Compose 层，卷页时背景稳定不撕裂

  - **慢网络加载占位**：远端页未就绪时显示纸面 + 加载指示图形（暗环 + 亮弧扫掠，语义同 CircularProgressIndicator），加载完成自动重建真实页纹理

  - **自动翻页事件流**：合成触摸事件直通 harism（按 downTime 匹配，自动翻页也走真实卷曲物理）

- **引擎切换 240ms 挂钟淡入**（GL 引擎豁免——graphicsLayer 对 GLSurfaceView 会引发 Surface 合成异常）

#### 缩放与手势

- **双击三档缩放**（1x → 填满 → 1:1）、**长按临时放大（QuickZoom）**、**双指缩放平移**；超大图 `BitmapRegionDecoder` 按可视区域局部重解码

- **Fit 七档**：适应宽度 / 适应高度 / 填满 / 整页 fit-inside / 原始 / 自定义（基础档 × 系数，可存命名预设）

- **View 层手势仲裁**（CURL 下）：双击（320ms 窗口）/ 长按（500ms）/ 双指命中即唤起 Compose 缩放覆盖层（复用全套缩放手势），单指拖拽 / 快 tap 走 harism 原路径——保证 GL 视图真实触摸不被 Compose 层截获

- **音量键翻页**：阅读器存活期拦截 + RTL 方向感知（VOL\_UP 恒为「下一页」的逻辑方向）

- **触觉反馈**：翻页落定 / 磁吸吸附 / 卷曲落定 / 裁边框贴边四类事件震动反馈

#### 图像处理管线（纯 Kotlin 手写像素算法，1000 行）

处理顺序：**裁边 → 拆片 → 旋转 → 色调 → 锐化/增强 → 放大**：

- **自动裁边 v2**：降采样（最长边 768）→ 边缘环 3px RGB 中位数为基准色 → 逐通道容差判定 → **连续段（run）判定**（run ≥ 该方向采样数 5%，对孤立灰尘噪声免疫）→ 单边 1/3 防御（扫过 1/3 无内容视为全内容）→ 全局 30% 保护。白边 / 黑边 / 彩边 / 米色纸底统一处理；支持手动四角裁剪（不修改原文件）

- **跨页拆片**：降采样到宽 256 → 列亮度**中位数**定位装订缝（中位数 = 贯穿全高的缝值，列均值会被画面块平均成假缝）→ 中央带搜索「窄带 + 两侧等值平台」结构 → 左右方差排除空页；aspect ≥ 1.8 无条件拆

- **色调 LUT**：亮度 / 对比度 / Gamma / 阴影（提亮暗部二次权重、压暗时纯黑不动防压死线稿）逐通道查找表；饱和度 + 色相走标准 SVG feColorMatrix 矩阵

- **Unsharp Mask**：3×3 卷积 + overshoot 限幅 ±16（FidelityFX CAS 量级）消白边 halo

- **CAS 对比度自适应锐化**：平面区强提升、边缘区弱提升（`amp = amount × (1 - 极差/255 × 0.8)`）

- **Anime4K CNN 线条重建**（CPU 卷积网络，2562 权重由脚本从上游 GLSL 机器提取零手抄）：bilateralLite 预降噪 → Restore\_CNN\_S（残差语义：输出 = 平面 + 增量 × 深度）→ Lanczos 回原尺寸 → 边缘掩码 CAS 收尾；行条带多核并行（全尺寸页 4.6s → 1.4s）；高分辨率页（≥2400px）自适应跳过无效 2x

- **超分辨率**：Lanczos3（a=3 两级 pass）2x 重建 + CAS；Waifu2x 类（bilinear 2x + 颜色相似度加权双边滤波）

- **尺寸护栏**：逐像素操作前 >2M 像素先等比降到 \~2M，防卡顿 / OOM

#### 场景系统（氛围模式）

- **雨夜 / 落雪 / 樱花 / 萤火 / 海边 / 篝火 / 夏夜** 七场景

- **CC0 真实环境音分层混音**：2–4 轨叠加 + 等功率循环交叉淡化 + 首尾响度均衡（Python 工程预处理产出，合成器仅兜底），音效与特效独立开关

- **物理粒子引擎**：ParticleEmitter Euler 积分 + WeatherView 运动模糊渲染；樱花三运动叠加（重力 + 摆动 + 旋转）、海浪 Wave Field 场、天空 flux 分层、雪粒子三重分层

#### 缓存与性能

- **解码限幅 2800px** + 多级 LRU 页缓存 + 相邻页预载（带旧任务取消的节流）+ 垂直前瞻预载（无缝窗口 4 页 / 条漫 2 页，差异化）

- **缓存命中首帧即 Ready**：`rememberPageBitmap` 组合期播种，磁吸模式黑屏 0.7–1.2s 根治

- **displayGeneration 代校验**：布局 / 配置 / 页码变化即递增，异步解码结果提交前校验——根治快速翻页闪回旧页

- **EXIF 三处归一化**（本地解码 / 尺寸探测 / 区域重解码 + Coil RESPECT\_ALL）——「图片偶尔横向」根因修复

- **远程位图软化**：Coil 远程解码强制软件位图 + 跨线程兜底转 ARGB\_8888——修复「CURL 远程翻页不推进 + 整屏白」致命崩溃

- **实测帧率**：SLIDE janky 4.65%（p50=18ms）、无缝 fling p50=24ms、CURL 录屏实测 41fps

#### 其他

- **阅读背景**：纯色 / 纸张纹理 / 沉浸动态（当前页量化直方图取主色，挂钟驱动逐帧插值——不依赖 vsync 时间戳）

- **预设系统**：内置日漫 / 条漫 / 老漫画预设，可创建 / 复制 / 重命名 / 收藏置顶 / 设为默认；设置按钮长按 = 快捷应用收藏预设；每本漫画独立配置与页级进度恢复

- **自动阅读**：自动翻页（间隔可调）/ 自动滚动（速度可调）；整本 / 单页旋转

- **设置面板六标签页**：翻页 / 显示 / 图像 / 翻译 / 主题 / 自动 / 手势；真毛玻璃（layerBackdrop 采样 + blur22dp + 饱和 1.15；CURL 引擎下自动回退纯半透明）

### 🌐 漫画整页翻译（v1.0.5）

- **本地 OCR**：PP-OCRv6 检测 + 识别（ONNX，模型约 31MB 按需下载，hf-mirror 优先）；竖排文字旋转识别；长图分块检测（覆盖率较整页检测 +131%）

- **气泡分割**：YOLO-seg int8 量化模型（11.8MB→4.0MB，随 APK 内置）；letterbox 预处理 + 最大连通域 + 扫描线轮廓提取 + 类内 NMS

- **译文渲染**：按气泡轮廓形状渲染覆盖——光栅化找最大内接矩形（柱状图评分）+ 背景色采样去墨 + 对比色文字 + 二分字号填满

- **译文锚定原文**：排版区 = 气泡安全区 ∩ 原文行包围盒，译文落回原文所在行位置，多行 / 偏置气泡读感与原版对齐

- **翻译引擎**（引擎多选二级导航，选中即用，失败自动降级在线）：

  - 自定义 AI 接口：OpenAI 兼容（DeepSeek / GLM / Kimi / Ollama / LM Studio 局域网自建等）或 Gemini 格式；整页气泡一次请求；译名表（glossary）跨页一致（人名前后一致）；严格 id 校验 + 自动重试 3 次

  - 在线翻译：腾讯交互翻译（国内直连、免配置、免 Key、批量多句一次请求）；Google gtx 海外网络兜底

- **源语言**：自动识别（假名 / 拉丁 / 汉字启发式）/ 日文 / 英文

- **译文缓存**：逐页 JSON 磁盘缓存（LRU 64MB），翻回已译页立即显示；缓存键含引擎 + 源语言标识，切换后同页自动重译；缓存明细管理（逐条 / 批量 / 全清，条目显示「原文→译文 + 区域数」）

- **内存管理**：关闭翻译或退出阅读器即释放全部 ONNX 会话（实测回收 600MB+）；快速开关并发保护

- 与预载窗口同键调度（当前 ±1 页预取）；烘焙位图与 CURL 纹理同步刷新；关闭开关自动逐页还原原文

### 🔍 书源体系

所有书源实现统一 `BookSource` / `ComicSource` 接口，可插拔聚合：

- **Z-Library**（深度原生集成，全链路无需浏览器：搜索 → 详情 → 自动过验证 → 解析真实下载链接 → 断点续传 → 校验 → 自动入库）：

  - 六个实测可用节点内置（1lib.sk、z-lib.by、z-library.sk、zh.z-lib.by、zh.z-library.sk、en.z-lib.by）

  - **节点容灾**：4s 健康检查（60s 缓存，校验搜索功能而非仅连通性，防停放域名）；当前节点失效自动从候选池切换（六个导航站动态发现 ∪ 节点管理候选 ∪ 预置），登录态跨节点保持

  - **节点管理**：默认节点、官网 / 备用入口扒取（六个发现源）、自定义节点、一键检测、抓取结果合并入池

  - **DiamWall PoW 自动求解**：SHA-1 / SHA-256 两种工作量证明 + Cookie 提取 + 重定向循环守卫；交互式挑战由隐藏 WebView（Chromium TLS 指纹）兜底

  - **抗污染 DNS**：私有/保留网段黑名单 + 四家 DoH 并行（AliDNS / DNSPod / Cloudflare / Google）+ 443 端口 TCP 探测排序 + 三级缓存（5min 正向 / 30s 负 / 24h 可达记忆）

  - 桌面 / 移动 / 旧版 / bookcard / 通用兜底五套布局解析器；eapi JSON 接口；账号 / Cookie 登录；EPUB / MOBI / PDF / AZW3 / TXT / FB2 多格式选择下载（默认格式与用户选的格式分别走自研链路与 eapi 多格式链路）

  - 每日下载额度识别与提示（游客 5 次/天/IP，登录 10 次/天），不再误报「HTML 错误页」

- **MangaDex**：官方 REST API 原生实现，聚合搜索

- **Venera JS 漫画源**：QuickJS 运行社区 JS 源（拷贝漫画、comick 等）；内置加密桥（AES-ECB/CBC/CFB/OFB、RSA、HMAC、MD5/SHA 系列）、DOM 操作、图片像素级重排（支持禁漫类分块乱序图床）；源列表可在线更新

- **Legado JSON 书源**：`@css:` / `@json:` 规则，`class./id./tag.` 段语法，`||` 回退 / `&&` 合并 / `##正则##` 替换连接符，POST 搜索，tocUrl 两步解析；JSON 导入导出，社区分享即贴即用

- **ehentai**：Cronet 网络栈（H\@H 浏览器级 TLS 指纹）

- **隐藏彩蛋**：设置页连按六下「主色按钮实时联动效果」，开启「带你登大郎\~\~\~」后自动更新并显示成人漫画源

- **聚合搜索逻辑**：多源并发，逐源搜索每出一个源立即展示（不干等）；源与源之间渐变毛玻璃胶囊分隔；失败显示「超时 / 无结果」；每源 6 条预览 + 展开全部，展开状态与滚动位置跨页面保持；繁简折叠归一化（854 对映射）+ 精确失败回退子串匹配 + 变体扩展（可开关）

- **在线小说阅读**：搜索结果为文字源时直接在线读正文（A−/A+ 字号、上下章切换）

- **书源调试日志**：300 条环形缓冲，书源管理页可查看 / 复制 / 清空，网络失败时展示真实原因（HTTP 状态码 / DNS / TLS / 超时）

### ⬇️ 资源下载器

- **WorkManager 后台任务**：切页面、切 Tab、锁屏都不中断；下载中心常驻，随时回来查看

- **3 路并发 + 暂停 / 继续 / 取消 / 失败重试**：失败任务不会从列表消失，一键重试（5xx / IO 错误最多 3 次，间隔 2s）

- **断点续传**：HTTP Range，206 追加续写 / 200 重下 / 416 本地完整性校验三态分派

- **真实格式校验**：按文件内容（魔数 / 编码启发式）识别真实格式，声明格式与实际不符按实际改名放行（下的是 mobi 却按 epub 声明也能正确入库）；HTML 错误页（ZIP 排除法防误伤 EPUB 内 nav.xhtml）、每日限额页、DiamWall 验证页均能识别并给出明确中文提示

- **毛玻璃下载卡片**：实时显示封面、进度、速度、剩余大小；下载中 / 暂停 / 失败均提供操作按钮

- **进度广播节流**（≥300ms 或 ≥1% 变化，避免列表持续重组）；流式 MD5

- **下载完成自动入库 + 封面缓存**：书架不再出现「无封面」占位

- **漫画单章 / 批量下载**：章节列表一键勾选

- **书架分享原文件**：长按书架书籍分享 EPUB / 漫画 / MOBI / PDF / AZW3 原文件（零拷贝或临时缓存用完即焚，不长期占用双份存储）

### 📚 书架与数据

- **书架管理**：分类（新建 / 删除带确认 / PIN 密码锁定）、导入、移动；排序三态（默认 / 标题 / 最近阅读）；书籍卡片显示章节进度条（第 X/Y 章 + 进度指示）；长按呼出 Acrylic 半屏操作菜单，小屏上内容完整可达

- **阅读统计**：周 / 月 / 年总览、日历热力图（月/年视图）、7 天趋势图、周图表（柱状/折线）、高峰时段分布、连续打卡、每日阅读目标（15–480 分钟自定义）、阅读报告一键分享

- **存储管理**：三区安全模型（缓存 / 用户数据 / 书籍数据），占用分区可视化（缓存 / 离线内容 / 封面 / 数据库等九个分量）；缓存一键清理零风险；缓存明细逐文件管理（图片加载 / 译文 / 临时文件 / 翻译模型 / 离线书籍 / 离线漫画 / 封面 / 网页数据，单删 / 批删 / 全清）；删除用户数据需二次确认且量化后果，下载进行中自动拦截

- 本地数据存于应用私有目录；WebDAV 云同步与 JSON 导出在 Roadmap 中

### 🎨 个性化设置

- **主题配色**：主色 / 强调色自由搭配，全软件颜色弹簧过渡，切换不重启

- **屏幕方向**：跟随系统 / 锁定竖屏 / 锁定横屏，立即生效

- **开屏海报**：自定义海报 + 纯净模式（直进软件，无任何开屏）+ 首次启动引导页

- **软件背景**：默认主题色或自定义背景图，横竖屏自动裁剪铺满

- **字体**：系统字体即用，阅读器内直接导入 TTF

- **TTS**：语速、音量跟随系统，阅读器内一键开关

### ✨ 界面与动效

- **LiquidGlass 设计语言**：悬浮收缩 Tab 栏（真实背景模糊 + 虹彩描边 + 自动对比色图标）、亚克力弹窗、果冻开关、凝胶按钮、ChasingDots 加载动画；玻璃画质三档（低 / 标准 / 极致）

- **MAX 极光特效包**（「极致」渲染档专属）：三色极光辉光流边、呼吸光晕、顶部高光跟随、内容层视差、入场弹簧动效；配合「自定义卡片参数」实时调参面板（折射 / 压痕 / 倾斜 / 光效逐项可调、立即生效）；标题文字颜色按自定义壁纸明暗自动取对比色

- **整卡 3D 视觉与物理反馈**：玻璃卡片随按压跷跷板倾斜、随滚动手势惯性摆动并极软回正；固定光源的 AGSL 法线光照压痕（Android 13+，低版本自动降级渐变模拟），整套 UI 有真实「玻璃厚度」

- **平板适配**：统一断点（compact < 600 / medium / expanded ≥ 840），弹窗 560dp、全屏页内容 720dp 居中，书架网格 3→6 列、搜索瀑布流 2→4 列自适应

- **吉祥物 Roxy**（蓝发巫师少女）：待机 / 欢呼 / 奔跑 / 捧书 / 低落五套姿态按场景自动切换，配合呼吸、漂浮、弹跳、抖动微动效

***

## 截图

| 书架主页                                | 书库 · 选择书源                             |
| ----------------------------------- | ------------------------------------- |
| ![书架主页](docs/screenshots/shot1.jpg) | ![书库书源选择](docs/screenshots/shot2.jpg) |

| 书源管理                                | 阅读统计                                | 设置页                                |
| ----------------------------------- | ----------------------------------- | ---------------------------------- |
| ![书源管理](docs/screenshots/shot3.jpg) | ![阅读统计](docs/screenshots/shot4.jpg) | ![设置页](docs/screenshots/shot5.jpg) |

***

## 安装

### Release 直装

前往 [Releases](https://github.com/a1553846342-dotcom/EASYREADER/releases) 下载 `app-release.apk`（arm64-v8a，覆盖近三年主流设备），系统设置允许「安装未知来源应用」后安装；华为设备按系统流程确认风险提示即可。

### 源码编译

环境：JDK 17+，Android SDK（compileSdk 35 / minSdk 24 / targetSdk 35），可访问 Google Maven / Maven Central 的网络。

```bash
git clone https://github.com/a1553846342-dotcom/EASYREADER.git
cd EASYREADER
echo "sdk.dir=/你的/Android/Sdk/路径" > local.properties
./gradlew :app:assembleRelease
```

输出：`app/build/outputs/apk/release/app-release.apk`。`debug.keystore` 不入库，首次构建自动生成本地调试密钥；未配置签名环境变量时自动回退 debug 签名，clone 下来即可构建。正式分发请配置 `KEYSTORE_PATH / STORE_PASSWORD / KEY_PASSWORD`。

***

## 使用教程

### 如何添加书源

1. 底部 Tab 进入「书库」，顶部书源选择器切换 Z-Library / MangaDex / 聚合漫画（全部）/ 聚合小说。
2. 需要更多漫画源：设置 → 书源管理 →「更新 Venera 源」，自动拉取社区源。
3. 想用自己的规则：书源管理 →「粘贴 JSON」或「导入 JSON 书源文件」，格式兼容 Legado（`@css:` / `@json:` 语法）；书库 → 帮助手册内有 JSON 模板。
4. **解锁成人源**：设置页连按六下「主色按钮实时联动效果」，开启「带你登大郎\~\~\~」后自动更新并显示特殊漫画源。

### 如何导入本地书籍

书架页点「+ 导入新书」→ 选择 TXT / EPUB / MOBI / AZW3 / CBZ 文件，自动解析入库，大文件也不卡。

### 如何配置阅读设置

阅读器内点屏幕中央呼出工具栏 →「阅读排版」：亮度、首行缩进、字号步进（A−/A+）、行距、页边距、五套主题预览卡、五种翻页模式在同一面板，半透明设计可边调边对照书页；支持直接导入 TTF。全局护眼强度、夜间模式、屏幕方向、渲染画质在设置页统一管理。

### Z-Library 登录与下载

书源管理 → Z-Library →「去登录」输入账号密码或粘贴 Cookie；登录后搜索 → 点「下载」，断点续传自动接管。若遇「需验证 / 503」，应用自动解 DiamWall PoW 并在节点失效时自动切换。

### 漫画翻译

阅读器设置 → 翻译 → 引擎卡片点击选用（自定义 AI / 在线翻译）→ 开启「整页自动翻译」，首次使用按提示下载 OCR 模型（约 31MB）。

***

## 项目结构

```text
app/src/main/java/com/example/
├── MainActivity.kt              # 单 Activity 入口 + Navigation
├── MainViewModel.kt             # 全局状态（主题/护眼/方向/导入流程）
├── data/                        # Room、解析器（Epub/Mobi/Docx/Fb2/Comic/TXT）、TTS、备份
│   └── ChapterMerger.kt         # 拆分章节 → 逻辑章节三表映射
├── download/                    # WorkManager 队列、断点续传、魔数校验
├── library/                     # 书库 UI、下载中心、Z-Library 节点/会话/节点管理
├── mangatranslate/              # OCR、气泡分割、翻译引擎、译文缓存
├── source/                      # 书源插件体系
│   ├── parser/                  # JSONPath 子集 + Legado 规则解释器
│   ├── zlibrary/                # DiamWall PoW、抗污染 DNS、eapi、五套布局解析
│   ├── anilist/                # 标题归一化（繁简折叠）与多语言搜索
│   └── js/                      # QuickJS 运行时 + 加密桥 + DOM 桥
└── ui/
    ├── comic/                   # 漫画引擎（模式/布局/管线/卷页/场景/设置面板）
    ├── pageturn/                # 文字翻页容器 + 分页引擎
    ├── components/              # 液态玻璃组件库（30+ 组件）
    ├── mascot/                  # Roxy 吉祥物动画
    ├── adaptive/                # 统一宽度断点规范
    └── source/                  # 书源管理、节点管理、登录弹窗

fi/harism/curl/                  # OpenGL 仿真卷页（vendored，Apache-2.0）
eu/wewox/pagecurl/               # Compose 卷页（vendored）
net/engawapg/lib/zoomable/       # 缩放组件（vendored）
backdrop/                         # KMPLiquidGlass 背景采样（vendored）
liquidglass-core/ · liquidglass-compose/   # 液态玻璃渲染核心（vendored）
```

### 核心模块说明

- 网络层由 OkHttp 拦截器链处理 DiamWall / Cloudflare 验证；下载任务状态实时广播

- `backdrop` / `liquidglass-*` 均为 vendored 源码，无需外部 Maven 私有仓库

- 书源实现 `BookSource` / `ComicSource` 接口即可接入聚合，存储走 SharedPreferences

### 主要依赖

| 库                                           | 用途                   |
| ------------------------------------------- | -------------------- |
| Jetpack Compose / Material 3                | 整套 UI                |
| Room + WorkManager                          | 持久化与后台下载             |
| OkHttp + Jsoup                              | 网络与 HTML 解析          |
| Coil                                        | 图片加载与封面缓存            |
| ONNX Runtime（Java API）                      | 漫画翻译 OCR / 气泡分割推理    |
| quickjs-kt                                  | Venera JS 漫画源运行时     |
| Cronet                                      | ehentai H\@H 浏览器级网络栈 |
| Abdullajon1881/LiquidGlass + KMPLiquidGlass | 液态玻璃渲染引擎（vendored）   |
| FlexibleBottomSheet / compose-animations    | 底部弹窗与形态动画            |

***

## FAQ

**Q1：为什么搜索不到结果？**
先确认书源已启用且网络正常；Z-Library 首次搜索需自动过验证（等待数秒）；漫画源建议用「聚合漫画（全部）」一次搜索；可切换节点或更换关键词。

**Q2：Z-Library 提示「需验证 / HTTP 503」怎么办？**
应用会自动解 DiamWall 新版 PoW；交互式验证由 WebView 兜底；仍失败时节点容灾自动切换，也可在节点管理页手动切换。注意浏览器能打开不代表 App 直连顺畅，请保持代理 / VPN 状态一致。

**Q3：下载失败或文件打不开？**
下载自动校验真实格式（防 HTML 假文件），错误页不入库。失败任务保留在下载中心，点重试即可；登录态过期请重新登录；未登录 IP 每日限额 5 次。

**Q4：漫画翻译需要联网吗？**
OCR 与气泡分割全本地（模型首次下载约 31MB）；在线翻译需联网；配置自定义 AI 接口后走自己的 API。

**Q5：模拟器上开翻译闪退？**
Release 包仅含 arm64 ONNX 库，x86\_64 模拟器经 ARM 转译运行 ONNX 会 SIGSEGV，属模拟器限制；Debug 包附带 x86\_64 库可正常使用。

**Q6：自定义 JSON 书源怎么写？**
书库 → 帮助手册 →「查看 JSON 模板」；规则兼容 Legado 的 `@css:` / `@json:` 语法。

**Q7：闪退怎么排查？**
到 Issues 提供版本号、设备型号、复现步骤；可用 `adb logcat -b crash` 抓取崩溃栈一并附上。

**Q8：支持 iOS 吗？**
当前为纯 Android 项目；Compose Multiplatform 化已列入 Roadmap。

**Q9：如何备份数据？**
数据存于应用私有目录，卸载即清除，卸载前请先导出书籍文件；JSON 导出与 WebDAV 同步在 Roadmap 中。

***

## Roadmap

- [x] MOBI / AZW3 / AZW 格式支持（正文解析、封面提取、DRM 检测）

- [x] 自定义字体导入（阅读器「阅读排版」内直接导入 TTF）

- [x] 漫画音量键翻页（方向感知）

- [ ] PDF 文本层解析（当前仅按页渲染）

- [ ] 阅读记录 JSON 导出 / 导入

- [ ] WebDAV 云同步

- [ ] 文字阅读器音量键翻页、屏幕常亮快捷开关

- [ ] 电子墨水模式、全局手势自定义

- [ ] 更多内置漫画源与特殊漫画源连通性优化

- [ ] Compose Multiplatform（iOS 实验版）

***

## 贡献

- Issue：标题 `[模块] 问题描述`（如 `[书库] 搜索无结果`），正文附版本号、设备型号、复现步骤、日志或截图

- PR：从 `main` 拉分支（`fix/xxx` / `feat/xxx`）；组件放 `ui/components`，书源实现 `BookSource` 接口；提交信息 `type: 描述`（如 `fix: zlib 下载 503`）；提交前跑通 `./gradlew :app:assembleRelease`

***

## 免责声明与许可

本项目仅用于技术学习与交流。所有在线书源（Z-Library、MangaDex、Venera 社区源、ehentai 等）均为第三方服务，内容由对应版权方所有；请勿将本项目用于商业用途或传播侵权内容。因使用本软件产生的一切法律问题与作者无关。

本仓库未附带开源许可证（All Rights Reserved），代码仅作学习交流；如需商用或二次分发，请联系作者获取授权。第三方组件遵循其自身许可证（Apache-2.0 / MIT / GPL-3.0，明细见 `docs/vendor-licenses/`）。

***

## 鸣谢

- 液态玻璃引擎：[Abdullajon1881/LiquidGlass](https://github.com/Abdullajon1881/LiquidGlass)、[Kashif-E/KMPLiquidGlass](https://github.com/Kashif-E/KMPLiquidGlass)

- 漫画翻译管线：[jedzqer/manga-translator-android](https://github.com/jedzqer/manga-translator-android)（MIT）

- 仿真卷页：[harism/android-pagecurl](https://github.com/harism/android-pagecurl)（Apache-2.0）

- 底部弹窗：[skydoves/FlexibleBottomSheet](https://github.com/skydoves/FlexibleBottomSheet)

- 动效参考：[skydoves/compose-animations](https://github.com/skydoves/compose-animations)、[commandiron/ComposeLoading](https://github.com/commandiron/ComposeLoading)

- 滑块交互：[christianselig/JunoSlider](https://github.com/christianselig/JunoSlider)

- 缩放组件：[usuiat/Zoomable](https://github.com/usuiat/Zoomable)

- 翻页参考：GitHub `pagecurl`、`PTQFlipper`

- Venera 漫画源：[venera-app/venera-configs](https://github.com/venera-app/venera-configs)

- ehentai 取图思路：[delta-comic/delta-comic-plugin-ehentai](https://github.com/delta-comic/delta-comic-plugin-ehentai)

- 环境音素材：CC0（来源明细见 `app/src/main/assets/ambient/CREDITS.md`）

***

## 更新日志摘要

逐项变更的完整记录见 [CHANGELOG.md](CHANGELOG.md)。

**v1.0.5（2026-09-05）— 漫画阅读器整体重做 + 漫画整页翻译**

本次从零重做漫画引擎（28 条修复升级 + 四路独立终审 + 六条实测反馈根治 + 三个返工轮，漫画域单测 137 项全绿），并新增漫画整页翻译（六轮迭代）。

漫画阅读器重做：

- **CURL 仿真卷页重做**：双页书脊模式（跨页 spread 展开 + 一次卷曲推进整 spread）+ 刚体封面平折 + 透纸背面（正面 1/6 模糊 + 20% alpha）+ 半透明 GL（背景层稳定）+ 慢网络加载占位图形 + 自动翻页走真实卷曲物理（旧版从未真正卷页过）

- **翻页动画修复**：FADE 真交叉淡化（根治 RTL 漂移 111px 与黑场下陷）、SLIDE 越界橡胶回弹（RTL 不再吞手势）、引擎切换 240ms 挂钟淡入、CURL 大跨度跳转 170ms 硬上限（先同步解码目标页再切换）

- **缓存与性能**：displayGeneration 代校验（根治快速翻页闪回）、缓存命中首帧即 Ready（磁吸黑屏 0.7–1.2s 根治）、垂直前瞻预载（无缝 4 页 / 条漫 2 页）、远程位图软化（修复 CURL 远程翻页崩溃）、EXIF 三处归一化（图片横向根因）、Anime4K 多核并行（4.6s → 1.4s）

- **图像管线增强**：自动裁边 v2（中位数基准色 + 连续段判定，抗灰尘噪声）、跨页拆片（列亮度中位数检测装订缝）、CAS / Unsharp Mask（overshoot 限幅 ±16）、Anime4K CNN 残差语义修复（白图压暗 bug）、沉浸式主色调色（降饱和 65% + 亮度 64）

- **场景系统**：CC0 环境音分层混音（2–4 轨交叉淡化 + 响度均衡）+ 物理粒子引擎（Euler 积分 + 运动模糊、樱花三运动叠加、海浪 Wave Field）

- **交互**：音量键翻页（RTL 方向感知）、四类触觉反馈、View 层手势仲裁（GL 触摸不被截获）、真毛玻璃设置面板（CURL 回退半透明）

- 验证体系：四路独立子代理终审（均分 8.6–9.1）、v3 录屏逐帧复审、交互矩阵 15/15 模式×方向全绿、gfxinfo / 录屏实测帧率

漫画整页翻译：

- 本地 OCR（PP-OCRv6，31MB 按需下载）+ 气泡分割（YOLO-seg int8 量化，11.8MB→4.0MB）；竖排旋转识别；长图分块检测（覆盖率 +131%）

- 双引擎：自定义 AI（OpenAI / Gemini 兼容，整页一次请求、译名表跨页一致）+ 腾讯交互翻译（国内直连免配置），AI 失败自动降级

- 译文按气泡形状渲染覆盖（最大内接矩形 + 背景色采样去墨 + 二分字号）并锚定原文行位置；逐页缓存（LRU 64MB）带引擎 / 语言隔离与全明细管理；关闭翻译即释放全部 ONNX 会话内存（实测回收 600MB+）

Z-Library 与书源：

- 节点容灾：六实测节点内置，失效自动切换，登录态跨节点保持；DiamWall PoW 自动求解 + WebView 兜底；抗污染 DNS（四家 DoH 并行 + TCP 探测排序 + 三级缓存）

- PDF 按页位图渲染（漫画管线）；DOCX / FB2 解析器

- 平板适配统一断点规范；聚合搜索展开状态与滚动位置跨页面保持

其他：

- APK 瘦身 22.28MB → 9.62MB（翻译功能引入 ONNX 后回升至约 23MB）

- 阅读器设置面板冗余文案精简；README 重构

**v1.0.1（2026-08-29）**

- 在线小说阅读器（搜索结果直接读正文）；漫画 / 小说分类聚合

- Legado 兼容增强：POST 搜索、tocUrl 两步解析、调试日志

- 聚合搜索每源 6 条预览 + 展开全部；开屏 LOGO

**v1.0.0（2026-08-28）**

- 首个正式版：本地书籍 / 漫画阅读、书源聚合搜索与下载、阅读统计、LiquidGlass UI、MOBI/AZW3 自研解析、仿真卷页、场景系统、Anime4K 增强

