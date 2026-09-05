<div align="center">

<img src="./app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png" width="110"/>

# Ciallo阅读（EASYREADER）

**Android 阅读器 / 在线书库聚合下载器**

Kotlin · Jetpack Compose (Material 3) · MVVM · 单 Activity

<img src="./docs/demo-1.gif" width="270"/>
<img src="./docs/demo-2.gif" width="270"/>
<img src="./docs/demo-3.gif" width="270"/>

[下载 APK](https://github.com/a1553846342-dotcom/EASYREADER/releases) ·
[功能特性](#功能特性) ·
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

Android 端小说 / 漫画阅读器，内置多书源在线聚合搜索与下载。本地阅读、在线找书、离线管理一体化。

| 项目                     | 内容                                                            |
| ---------------------- | ------------------------------------------------------------- |
| 当前版本                   | 1.0.5                                                         |
| 最低系统                   | Android 7.0（API 24）                                           |
| compileSdk / targetSdk | 35                                                            |
| 语言                     | Kotlin 2.0                                                    |
| 架构                     | MVVM + StateFlow + Repository，单 Activity + Navigation Compose |
| 存储                     | Room（8 实体 / version 6）+ SharedPreferences                     |
| 测试                     | 全量 325+ 项（JVM / Robolectric）                                  |
| APK 体积                 | 约 23MB（含 ONNX Runtime 与量化气泡模型；不含 OCR 模型，按需下载）                 |

***

## 功能特性

### 📄 文件格式

| 格式                      | 解析实现     | 说明                                                                                               |
| ----------------------- | -------- | ------------------------------------------------------------------------------------------------ |
| TXT                     | 内置       | 大文件分段加载（超长章节入库前拆分、阅读时自动合并回逻辑章节），GBK / UTF-8 / UTF-16 编码自适应                                       |
| EPUB                    | 内置       | OPF / NCX / spine 解析；ZIP 文件名 UTF-8 / GBK / GB18030 三级回退；封面三级提取策略（规范路径 → 文件名 → 首图/最大图）            |
| MOBI / AZW3 / AZW / PRC | 内置，零依赖自研 | PDB 容器 + KF8 混合段识别；PalmDOC LZ77 与 HUFF/CDIC 哈夫曼字典解压（Kindle 正文压缩算法的纯 Kotlin 移植）；EXTH 元数据；DRM 检测提示 |
| DOCX                    | 内置       | 解 `word/document.xml`，按 `<w:p>` 段落抽取                                                             |
| FB2                     | 内置       | `<title-info>` 元数据 + `<section>` 章节切分                                                            |
| CBZ / ZIP 漫画            | 内置       | 自然排序（`page_2 < page_10`）；GBK 文件名回退                                                               |
| PDF                     | 按页位图渲染   | `PdfRenderer` 逐页渲染走漫画管线，翻页模式阅读；不做文本层提取                                                           |

### 📖 文字阅读器

- 五种翻页模式：仿真 3D 卷页 / 覆盖 / 平移 / 渐变 / 上下滚动

- 真实排版分页引擎：基于 Compose `Paragraph` 测量，分页点均为真实行边界；超大章节分块渐进测量 + LRU 分页缓存

- 排版：字号 / 行距 / 页边距 / 首行缩进可调；五套阅读主题；导入 TTF 自定义字体

- TTS 朗读（系统语音引擎，语速音量跟随系统）

- 自动滚屏（60fps，与 TTS 互斥）

- 书签、划线高亮、全文搜索、目录自动定位

- 阅读器内亮度调光（与系统独立）、护眼暖色滤镜（0–65%）、定时休息提醒

- 读完徽章、按书按章实时进度保存

### 🎨 漫画阅读器（本地 / 在线统一引擎）

- 阅读模式：单页 / 双页 / 条漫 / 无缝滚动 / 磁吸；阅读方向：左→右 / 右→左 / 上→下

- 翻页动画：无 / 平移 / 渐变 / 仿真卷页（vendored harism OpenGL 实现，支持双页书脊模式与刚体封面折页）

- 缩放：双击三档 / 长按临时放大 / 双指缩放平移；超大图 `BitmapRegionDecoder` 局部重解码

- 图像管线（纯 Kotlin 像素算法，处理顺序：裁边 → 拆片 → 旋转 → 色调 → 锐化 → 放大）：

  - 自动裁边：降采样 + 边缘基准色 + 连续段判定，白边 / 黑边 / 彩边 / 米色纸底统一处理；支持手动四角裁剪

  - 跨页拆片：列亮度中位数 + 窄带/等值平台结构检测装订缝

  - 滤镜：亮度 / 对比度 / 饱和度 / 色相 / Gamma / 阴影 / 黑白，实时预览

  - 增强：CAS 对比度自适应锐化 / Anime4K CNN 线条重建（固定权重 CPU 卷积网络）/ 2x Lanczos3 超分 / 边缘掩码锐化

- 场景系统：雨夜 / 落雪 / 樱花 / 萤火 / 海边 / 篝火 / 夏夜 —— CC0 真实环境音分层混音（2–4 轨交叉淡化循环）+ 物理粒子引擎，音效与特效独立开关

- 阅读背景：纯色 / 纸张纹理 / 沉浸动态（当前页量化直方图取主色）

- 预设系统：内置日漫 / 条漫 / 老漫画预设，可创建 / 复制 / 收藏 / 设为默认；每本漫画独立配置与页级进度恢复

- 自动阅读（翻页间隔 / 滚动速度可调）、音量键翻页（RTL 方向感知）、整本 / 单页旋转

### 🌐 漫画整页翻译（v1.0.5）

- **本地 OCR**：PP-OCRv6 检测 + 识别（ONNX，模型约 31MB 按需下载，hf-mirror 优先）；竖排文字旋转识别；长图分块检测（覆盖率较整页检测 +131%）

- **气泡分割**：YOLO-seg int8 量化模型（4MB，随 APK 内置）；译文按气泡轮廓形状渲染覆盖，光栅化最大内接矩形 + 背景色采样去墨 + 二分字号

- **译文锚定原文**：排版区 = 气泡安全区 ∩ 原文行包围盒，译文落回原文所在行位置

- **翻译引擎**（选中即用，失败自动降级在线）：

  - 自定义 AI 接口：OpenAI 兼容（DeepSeek / GLM / Kimi / Ollama / LM Studio 等）或 Gemini 格式；整页一次请求；译名表（glossary）跨页一致；严格 id 校验 + 自动重试

  - 在线翻译：腾讯交互翻译（国内直连、免配置、批量请求）；Google gtx 海外兜底

- 源语言：自动识别（假名 / 拉丁 / 汉字启发式）/ 日文 / 英文

- 译文缓存：逐页 JSON 磁盘缓存（LRU 64MB），缓存键含引擎 + 源语言标识，切换后同页自动重译；缓存明细管理（逐条 / 批量 / 全清，条目显示「原文→译文」）

- 内存管理：关闭翻译或退出阅读器即释放全部 ONNX 会话（实测回收 600MB+）；快速开关并发保护

### 🔍 书源体系

所有书源实现统一 `BookSource` / `ComicSource` 接口，可插拔聚合：

- **Z-Library**（深度原生集成）：

  - 六个实测可用节点内置（1lib.sk、z-lib.by、z-library.sk、zh.z-lib.by、zh.z-library.sk、en.z-lib.by）

  - **节点容灾**：4s 健康检查（60s 缓存，校验搜索功能而非仅连通性）；当前节点失效自动从候选池切换（六个导航站动态发现 ∪ 节点管理候选 ∪ 预置），登录态跨节点保持

  - **DiamWall PoW 自动求解**：SHA-1 / SHA-256 两种工作量证明 + Cookie 提取 + 重定向循环守卫；交互式挑战由隐藏 WebView（Chromium TLS 指纹）兜底

  - **抗污染 DNS**：私有/保留网段黑名单 + 四家 DoH 并行（AliDNS / DNSPod / Cloudflare / Google）+ 443 端口 TCP 探测排序 + 三级缓存

  - 桌面 / 移动 / 旧版 / 通用兜底四套布局解析器；eapi JSON 接口；账号 / Cookie 登录；EPUB / MOBI / PDF / AZW3 / TXT / FB2 多格式下载

  - 每日下载额度识别与提示（游客 5 次/天/IP，登录 10 次/天）

- **MangaDex**：官方 REST API 原生实现

- **Venera JS 漫画源**：QuickJS 运行社区 JS 源；内置加密桥（AES-ECB/CBC/CFB/OFB、RSA、HMAC、MD5/SHA 系列）、DOM 操作、图片像素级重排（支持分块乱序图床）；源列表可在线更新

- **Legado JSON 书源**：`@css:` / `@json:` 规则，`||` 回退 / `&&` 合并 / `##正则##` 替换连接符，POST 搜索，tocUrl 两步解析；导入导出

- **ehentai**：Cronet 网络栈（H\@H 浏览器级 TLS 指纹）

- 聚合搜索：多源并发；每源 6 条预览 + 展开全部，展开状态跨页面保持；繁简折叠归一化（854 对映射）+ 变体扩展

- 在线小说阅读：搜索结果为文字源时直接在线读正文（A−/A+、上下章）

- 书源调试日志（300 条环形缓冲，可查看 / 复制 / 清空）

### ⬇️ 下载

- WorkManager 后台任务，锁屏 / 切页不中断；3 路并发；暂停 / 继续 / 取消 / 重试

- 断点续传（HTTP Range，206 / 200 / 416 三态分派）

- 真实格式校验：魔数识别（PDF / FB2 / ZIP 家族 / MOBI / 文本启发式），声明格式与实际不符按实际改名放行；HTML 错误页 / 限额页 / 验证页拦截并给出中文原因

- 进度广播节流（≥300ms 或 ≥1%），流式 MD5，下载完成自动入库 + 封面缓存

- 漫画单章 / 批量下载

### 📚 书架与数据

- 分类管理（新建 / 删除 / PIN 密码锁定）、导入、移动；排序三态（默认 / 标题 / 最近阅读）

- 长按分享原文件（EPUB / 漫画 / MOBI / PDF / AZW3）

- 阅读统计：周 / 月 / 年总览、日历热力图、7 天趋势、周图表、高峰时段、连续打卡、每日目标（15–480 分钟）

- 存储管理：三区安全模型（缓存 / 用户数据 / 书籍数据），占用分区可视化（九个分量）；缓存明细逐文件管理（图片 / 译文 / 临时文件 / 封面 / 网页数据，单删 / 批删 / 全清）；删除用户数据需二次确认，下载进行中拦截

### ✨ 界面

- LiquidGlass 设计语言：真实背景采样毛玻璃、悬浮收缩 Tab 栏、亚克力弹窗、果冻开关、流体滑条（metaball）、玻璃卡片 3D 倾斜；玻璃画质三档（低 / 标准 / 极致）

- 主题：主色 / 强调色自定义即时生效；屏幕方向锁（跟随系统 / 竖屏 / 横屏）

- 开屏海报 + 纯净模式跳过 + 首次启动引导页；应用背景图自定义

- 平板适配：统一断点（compact < 600 / medium / expanded ≥ 840），弹窗 560dp、全屏页内容 720dp 居中，书架网格 3→6 列、搜索瀑布流 2→4 列自适应

- 吉祥物 Roxy（蓝发巫师少女）：待机 / 欢呼 / 奔跑 / 捧书 / 低落五套姿态动画

***

## 截图

| 书架主页                                | 书库 · 书源选择                             |
| ----------------------------------- | ------------------------------------- |
| ![书架主页](docs/screenshots/shot1.jpg) | ![书库书源选择](docs/screenshots/shot2.jpg) |

| 书源管理                                | 阅读统计                                | 设置页                                |
| ----------------------------------- | ----------------------------------- | ---------------------------------- |
| ![书源管理](docs/screenshots/shot3.jpg) | ![阅读统计](docs/screenshots/shot4.jpg) | ![设置页](docs/screenshots/shot5.jpg) |

***

## 安装

### Release 直装

从 [Releases](https://github.com/a1553846342-dotcom/EASYREADER/releases) 下载 `app-release.apk`，允许「安装未知来源应用」后安装。

### 源码编译

环境：JDK 17+，Android SDK（compileSdk 35 / minSdk 24 / targetSdk 35）。

```bash
git clone https://github.com/a1553846342-dotcom/EASYREADER.git
cd EASYREADER
echo "sdk.dir=/你的/Android/Sdk/路径" > local.properties
./gradlew :app:assembleRelease
```

输出：`app/build/outputs/apk/release/app-release.apk`。未配置签名环境变量时自动回退 debug 签名；正式分发请配置 `KEYSTORE_PATH / STORE_PASSWORD / KEY_PASSWORD`。

***

## 使用说明

| 操作                  | 路径                                  |
| ------------------- | ----------------------------------- |
| 切换书源                | 书库 → 顶部书源选择器                        |
| 更新 Venera 漫画源       | 设置 → 书源管理 → 更新源列表                   |
| 导入 Legado / JSON 书源 | 书源管理 → 导入（粘贴或选择 JSON 文件）            |
| 导入本地书               | 书架 → 「+」→ 选择文件                      |
| 阅读器设置               | 阅读器内点屏幕中央 → 设置面板（七标签页）              |
| Z-Library 登录        | 书源管理 → Z-Library → 登录（账号或 Cookie）   |
| 漫画翻译                | 阅读器设置 → 翻译 → 整页自动翻译（首次按提示下载 OCR 模型） |

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
│   ├── zlibrary/                # DiamWall PoW、抗污染 DNS、eapi、四套布局解析
│   └── js/                      # QuickJS 运行时 + 加密桥 + DOM 桥
└── ui/
    ├── comic/                   # 漫画引擎（模式/布局/管线/卷页/场景/设置面板）
    ├── pageturn/                # 文字翻页容器 + 分页引擎
    ├── components/              # 液态玻璃组件库（30+ 组件）
    ├── mascot/                  # Roxy 吉祥物动画
    └── adaptive/                # 统一宽度断点规范

fi/harism/curl/                  # OpenGL 仿真卷页（vendored，Apache-2.0）
eu/wewox/pagecurl/               # Compose 卷页（vendored）
net/engawapg/lib/zoomable/       # 缩放组件（vendored）
backdrop/                         # KMPLiquidGlass 背景采样（vendored）
liquidglass-core/ · liquidglass-compose/   # 液态玻璃渲染核心（vendored）
```

### 主要依赖

| 库                            | 用途               |
| ---------------------------- | ---------------- |
| Jetpack Compose / Material 3 | UI               |
| Room + WorkManager           | 持久化与后台下载         |
| OkHttp + Jsoup               | 网络与 HTML 解析      |
| Coil                         | 图片加载与封面缓存        |
| ONNX Runtime（Java API）       | OCR / 气泡分割推理     |
| quickjs-kt                   | Venera JS 漫画源运行时 |
| Cronet                       | ehentai 网络栈      |

***

## FAQ

**搜索不到结果？**
确认书源已启用、网络正常；Z-Library 首次搜索需自动过验证（数秒）；可切换节点或换关键词。

**Z-Library 提示需验证 / 503 / 513？**
应用自动解 DiamWall PoW，交互式挑战由 WebView 兜底；仍失败时节点容灾自动切换，也可在节点管理页手动切换。

**下载失败或文件打不开？**
下载自动校验真实格式，错误页不入库。失败任务可在下载中心重试；登录态过期需重新登录；未登录 IP 每日限额 5 次。

**漫画翻译需要联网吗？**
OCR 与气泡分割全本地（模型首次下载约 31MB）；在线翻译需联网；配置自定义 AI 接口后走自己的 API。

**模拟器上开翻译闪退？**
Release 包仅含 arm64 ONNX 库，x86\_64 模拟器经 ARM 转译运行 ONNX 会 SIGSEGV，属模拟器限制；Debug 包附带 x86\_64 库可正常使用。

**如何备份数据？**
数据存于应用私有目录，卸载即清除；JSON 导出与 WebDAV 同步在 Roadmap 中。

***

## Roadmap

- [ ] PDF 文本层解析（当前仅按页渲染）

- [ ] 阅读记录 JSON 导出 / 导入

- [ ] WebDAV 云同步

- [ ] 更多内置漫画源

- [ ] Compose Multiplatform（iOS 实验版）

***

## 贡献

- Issue：标题 `[模块] 问题描述`，正文附版本号、设备型号、复现步骤、日志（`adb logcat -b crash`）

- PR：从 `main` 拉分支（`fix/xxx` / `feat/xxx`），提交信息 `type: 描述`，提交前确保 `./gradlew :app:assembleRelease` 构建通过

***

## 免责声明与许可

本项目仅用于技术学习与交流。所有在线书源（Z-Library、MangaDex、Venera 社区源、ehentai 等）均为第三方服务，内容由对应版权方所有；请勿将本项目用于商业用途或传播侵权内容。

本仓库未附带开源许可证（All Rights Reserved），代码仅作学习交流；第三方组件遵循其自身许可证（Apache-2.0 / MIT / GPL-3.0，明细见 `docs/vendor-licenses/`）。漫画翻译管线移植自 [jedzqer/manga-translator-android](https://github.com/jedzqer/manga-translator-android)（MIT）；仿真卷页来自 [harism/android-pagecurl](https://github.com/harism/android-pagecurl)（Apache-2.0）；环境音为 CC0 素材；Venera 漫画源来自 [venera-app/venera-configs](https://github.com/venera-app/venera-configs)。

***

## 更新日志摘要

完整记录见 [CHANGELOG.md](CHANGELOG.md)。

**v1.0.5（2026-09-05）**

- 漫画整页翻译：本地 OCR（PP-OCRv6）+ 气泡分割（YOLO-seg int8）+ 双引擎（自定义 AI / 腾讯交互翻译）；译文按气泡形状覆盖并锚定原文行位置；逐页缓存带引擎 / 语言隔离与明细管理；关闭翻译即释放 ONNX 会话内存

- Z-Library 节点容灾：六实测节点内置，失效自动切换，登录态跨节点保持；DiamWall PoW 自动求解 + WebView 兜底

- PDF 按页位图渲染（漫画管线）

- 平板适配统一断点规范；聚合搜索状态保持

- 漫画阅读器 28 项体验修复 + 四轮终审（缩放手感 / 卷页物理 / 双页书脊 / FADE 淡化等）

- APK 瘦身 22.28MB → 9.62MB（翻译功能引入后回升至约 23MB）

**v1.0.1（2026-09-04）**

- 在线小说阅读器（搜索结果直接读正文）；聚合分类与标签

- Legado 兼容增强：POST 搜索、tocUrl 两步解析、调试日志

- 聚合搜索每源 6 条预览 + 展开全部；开屏 LOGO

**v1.0.0（2026-08-28）**

- 首个正式版：本地书籍 / 漫画阅读、书源聚合搜索与下载、阅读统计、LiquidGlass UI

