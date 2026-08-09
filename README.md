# 🚀 Ciallo阅读（EASYREADER）

> **“也许会是下一款最好的安卓阅读器。”**
>
> 本地 TXT/EPUB 秒开、在线书库聚合搜索、Venera 漫画源、液态玻璃 UI——
> 一套把“能看”做到“好看又能打”的 Kotlin + Jetpack Compose 阅读应用。

![License](https://img.shields.io/badge/License-All%20Rights%20Reserved-lightgrey)
![Release](https://img.shields.io/badge/Release-v0.90-orange)
![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B-brightgreen)
![Architecture](https://img.shields.io/badge/Architecture-MVVM-blue)
![UI](https://img.shields.io/badge/UI-Compose%20Material%203-8A2BE2)

---

## 1. 📖 项目简介

市面上阅读器不少，但大多要么丑、要么卡、要么书源常年失效。Ciallo阅读 从立项那天起就只想做一件事：**把“好用”和“好看”同时做到位**，让打开阅读器这件事本身变成享受。

为什么做这个项目？因为我们受够了老牌阅读器“能用但难用”的体验——翻页像 PPT、设置页像十年前的后台、加个书源还要研究半天规则。于是我们选了 Kotlin + Jetpack Compose 从零重写，把每一帧动画、每一个弹窗、每一条数据流都按“现代应用”的标准做，而不是按“电子书工具”的标准做。

和同类软件有什么不同？**Z-Library 深度原生接入**（自动过 DiamWall 验证、节点管理与一键检测、账号登录下载）、**Venera JS 漫画源生态**（拷贝漫画、comick、漫蛙吧、goDa 等开箱即用）、**自定义 JSON 书源**（兼容 Legado 规则）、以及**一整套液态玻璃设计语言**（悬浮收缩 Tab 栏、毛玻璃弹窗、果冻开关、Juno 滑块、凝胶按钮）。别人只做一个点，我们做了一整套。

目标用户是谁？受够了旧阅读器卡顿的深度书虫、每天追更的漫画党、喜欢折腾自定义书源的极客，以及单纯想找一个“打开就能安静看书”的普通人。

### 📋 项目基本信息

| 项目 | 内容 |
|---|---|
| 项目名称 | Ciallo阅读（EASYREADER） |
| 一句话简介 | 支持本地 TXT/EPUB/漫画导入、自定义书源、在线书库聚合搜索的安卓阅读器 |
| 技术栈 | Kotlin 2.0 + Jetpack Compose（Material 3）+ MVVM + Room + WorkManager |
| 最低支持系统 | Android 7.0（API 24）+ |
| 开发状态 | 个人项目 · 活跃开发中 |

---

## 2. ✨ 功能特性

### 📚 书籍格式支持

| 格式 | 支持情况 | 说明 |
|---|---|---|
| TXT | ✅ 完整支持 | 大文件秒开、按体积分段、自动章节识别、GBK/UTF-8 自适应 |
| EPUB | ✅ 完整支持 | 解析 OPF/NCX，保留目录、封面、元数据 |
| CBZ / CBR | ✅ 完整支持 | ZIP/RAR 漫画容器解析，按页阅读 |
| PDF / MOBI / AZW3 | 🚧 规划中 | 见 Roadmap |

### 📖 阅读体验细节

- **翻页动画（五种）**：仿真 3D 卷页（真实折角、阴影、纸背反光）、覆盖翻页、平移翻页、渐变淡出、上下滚动。每种都针对“翻页出戏”这个痛点单独调过手感。
- **排版自由**：字号、行距、页边距、首行缩进全部可调；阅读主题内置毛玻璃/白底/羊皮纸/夜间/护眼/纯黑六套。
- **护眼模式原理**：夜间模式走深色配色 + 降低屏幕亮度的刺眼感；护眼滤镜叠加暖色调色（0–65% 可调），配合 Juno 风格滑块实时预览强度，长时间夜读不刺眼。
- **定时休息**：15/30/45/60 分钟预设或自定义任意时长，到点弹窗提醒，强制你离开屏幕歇眼睛。
- **TTS 朗读**：系统引擎无缝接入，边听边看；支持书签、划线高亮、全文搜索、进度自动保存。
- **阅读统计**：每日/每周阅读时长图表、连续打卡、最近阅读横向轮播。

### 🔍 书源管理

- **自定义规则书源**：支持粘贴 JSON / 导入文件，兼容 Legado 规则与 JSON API；网络导入社区书源合集。
- **书源导入导出**：JSON 格式标准化，社区分享即贴即用。
- **隐藏彩蛋**：设置页连按六下「主色按钮实时联动效果」，开启「带你登大郎~~~」后自动更新并显示成人漫画源。
- **搜索聚合逻辑**：逐源搜索、每出一个源立即展示，源与源之间用渐变毛玻璃胶囊分隔，失败显示“超时/无结果”而不是干等。
- **内置 Z-Library**：自动过 DiamWall 新版 PoW 验证；节点管理（默认节点、官网/备用入口扒取、自定义节点、一键检测）；账号/Cookie 登录后直接下载。
- **内置 MangaDex 与 Venera 漫画源**：QuickJS 运行社区 JS 源，聚合搜索一次覆盖主流漫画站。

### 🗄️ 数据管理

- **阅读进度**：按书按章实时记录，重开即续。
- **书架管理**：分类、导入、移动、删除一键完成，封面自动抓取缓存。
- **本地备份**：数据存于应用私有目录；WebDAV 云同步与 JSON 导出在 Roadmap 中。

### 🎨 个性化设置

- **主题配色**：主色/强调色自由搭配，全软件颜色弹簧过渡，切换不重启。
- **屏幕方向**：跟随系统 / 锁定竖屏 / 锁定横屏，切换立即生效。
- **开屏海报**：自定义海报 + 纯净模式（直进软件，无任何开屏）。
- **字体**：系统字体即用，自定义字体包导入见 Roadmap。
- **TTS 朗读**：语速、音量跟随系统，阅读器内一键开关。

### 🌟 其他亮点

- **液态玻璃整套 UI**：悬浮收缩 Tab 栏（真实背景模糊 + 虹彩描边 + 自动对比色图标）、亚克力弹窗、果冻开关、凝胶按钮、ChasingDots 加载动画。
- **下载中心**：漫画下载切页面不中断、失败保留可重试、3 路并发、暂停/继续/取消；下载完自动校验防 HTML 假文件。
- **吉祥物动画**：mascot 与阅读事件联动，让 App 有点人情味。

---

## 3. 📸 应用截图 / 效果预览

| 书架主页 | 书库 · 选择书源 |
|---|---|
| ![书架主页](docs/screenshots/shot1.jpg) | ![书库书源选择](docs/screenshots/shot2.jpg) |

| 书源管理 | 阅读统计 | 设置页 |
|---|---|---|
| ![书源管理](docs/screenshots/shot3.jpg) | ![阅读统计](docs/screenshots/shot4.jpg) | ![设置页](docs/screenshots/shot5.jpg) |

---

## 4. 📲 安装方式

### ① Release / APK 直装（推荐）

1. 前往本仓库 **Releases** 下载 `app-release.apk`（约 7.6MB，arm64-v8a）。
2. 手机系统设置中允许「安装未知来源应用」。
3. 打开 APK 按提示安装；华为设备按系统流程确认风险提示即可。

### ② 源码自行编译

**环境要求**

- JDK 17+
- Android SDK（compileSdk 35 / minSdk 24 / targetSdk 35）
- 可访问 Google Maven / Maven Central 的网络

**构建步骤**

```bash
# 1. 克隆仓库（已内置 Gradle Wrapper，无需预装 Gradle）
git clone https://github.com/a1553846342-dotcom/EASYREADER.git
cd EASYREADER

# 2. 配置 SDK 路径（Windows / Linux / macOS 通用）
echo "sdk.dir=/你的/Android/Sdk/路径" > local.properties

# 3. 一键构建正式版 APK
./gradlew :app:assembleRelease
```

APK 输出路径：`app/build/outputs/apk/release/app-release.apk`

> 提示：仓库已内置 `debug.keystore`，未配置签名环境变量时自动回退 debug 签名，clone 下来即可构建；正式分发请配置 `KEYSTORE_PATH / STORE_PASSWORD / KEY_PASSWORD`。

---

## 5. 🧭 使用教程

### 如何添加书源

1. 底部 Tab 进入「书库」。
2. 顶部书源选择器切换 Z-Library / MangaDex / 聚合漫画（全部）。
3. 需要更多漫画源：设置 → 书源管理 →「更新 Venera 源」，自动拉取社区源。
4. 想用自己的规则：书源管理 →「粘贴 JSON」或「导入 JSON 书源文件」，格式兼容 Legado。
5. 解锁成人源：设置页连按六下「主色按钮实时联动效果」，开启「带你登大郎~~~」后自动更新并显示成人漫画源。

### 如何导入本地书籍

书架页点「+ 导入新书」→ 选择 TXT / EPUB / CBZ / CBR 文件，自动解析并入库，大文件也不卡。

### 如何配置阅读设置

阅读器内点屏幕中央呼出工具栏：字号、行距、页边距、主题、翻页模式、定时休息都在这里；全局护眼强度、夜间模式、屏幕方向在设置页统一管理。

### Z-Library 登录与下载

书源管理 → Z-Library →「去登录」输入账号密码或粘贴 Cookie；登录后搜索 → 点「下载」，断点续传自动接管。

---

## 6. 🏗️ 项目架构

### 目录结构

```text
app/src/main/java/com/example/
├── MainActivity.kt          # 单 Activity 入口与导航
├── MainViewModel.kt          # 全局状态（主题/护眼/方向锁/导入）
├── data/                     # Room、TXT/EPUB/漫画解析、偏好、TTS
├── download/                 # WorkManager 下载队列、断点续传、文件校验
├── library/                  # 在线书库 UI、下载中心、漫画导入
├── source/                   # 书源插件体系、Z-Library 引擎、JSON 书源、Venera JS 引擎
│   ├── zlibrary/             # DiamWall PoW、节点管理、会话/Cookie、多布局解析
│   └── js/                   # QuickJS 运行时、JS 消息桥
├── ui/                       # Compose 界面
│   ├── components/           # 液态玻璃按钮/开关/Tab 栏、JunoSlider、分段选择器等
│   ├── pageturn/             # 五种翻页容器
│   ├── mascot/               # 吉祥物动画
│   └── source/               # 书源管理、节点管理、登录弹窗
├── liquidglass-core/         # LiquidGlass 核心（vendored）
├── liquidglass-compose/      # LiquidGlass Compose 封装（vendored）
└── backdrop/                 # KMPLiquidGlass backdrop（vendored）
```

### 核心模块说明

- **架构**：MVVM + StateFlow + Repository；单一 Activity + Navigation Compose。
- **书源体系**：所有书源统一实现 `BookSource` 接口，网络层由 OkHttp 拦截器链处理 DiamWall/Cloudflare 验证。
- **下载**：WorkManager 后台任务，任务状态实时广播，页面切换不中断。
- **渲染**：LiquidGlass + KMPLiquidGlass 提供真实背景采样与毛玻璃；`backdrop`/`liquidglass-*` 均为 vendored 源码，无需外部 Maven 私有仓库。

### 主要开源库及用途

| 库 | 用途 |
|---|---|
| Jetpack Compose / Material 3 | 整套 UI |
| Room + WorkManager | 持久化与后台下载 |
| OkHttp + Jsoup + Moshi | 网络、HTML 解析、序列化 |
| Coil | 图片加载与封面缓存 |
| QuickJS（quickjs-kt） | Venera JS 漫画源运行时 |
| Cronet | ehentai H@H 浏览器级网络栈 |
| Abdullajon1881/LiquidGlass | 液态玻璃渲染引擎 |
| KMPLiquidGlass | backdrop 捕获与毛玻璃 |
| FlexibleBottomSheet / compose-animations | 底部弹窗与形态动画 |

---

## 7. ❓ 常见问题 FAQ

**Q1：为什么搜索不到结果？**
先确认书源已启用且网络正常；Z-Library 需要自动过验证（等待数秒）；漫画源建议用「聚合漫画（全部）」一次搜索；也可切换节点或更换关键词。

**Q2：Z-Library 提示“需验证 / HTTP 503”怎么办？**
应用会自动解 DiamWall 新版 PoW；若遇到交互式验证，稍后重试或切换其他节点。浏览器能打开不代表 App 直连顺畅，请保持代理/VPN 状态一致。

**Q3：下载失败或校验失败？**
下载会自动重试并校验文件（防 HTML 假文件）。失败任务保留在下载中心，点重试即可；登录态过期请重新登录。

**Q4：如何备份数据？**
当前数据存于应用私有目录；JSON 导出与 WebDAV 云同步在 Roadmap 中，卸载前请先导出书籍文件。

**Q5：闪退怎么排查？**
请到 Issues 提供版本号、设备型号、复现步骤；Android 端可用 `adb logcat -b crash` 抓取崩溃栈一并附上。

**Q6：支持 iOS 吗？**
当前为纯 Android 项目；Compose Multiplatform 化已列入 Roadmap，iOS 包工程量不小但可行。

**Q7：自定义 JSON 书源怎么写？**
书库 → 帮助手册 →「查看 JSON 模板」；规则兼容 Legado 的 `@css:` / `@json:` 语法。

**Q8：APK 为什么只有 arm64？**
为把体积压到 7.6MB，Release 只打包 arm64-v8a（覆盖近三年主流设备）；模拟器请使用 Debug 包或自行调整 `abiFilters`。

---

## 8. 🗺️ Roadmap / 后续计划

- [ ] PDF / MOBI / AZW3 格式支持
- [ ] 阅读记录 JSON 导出 / 导入
- [ ] WebDAV 云同步
- [ ] 自定义字体包导入
- [ ] 音量键翻页、屏幕常亮快捷开关
- [ ] 电子墨水模式、全局手势自定义
- [ ] 更多内置漫画源与成人源连通性优化
- [ ] Compose Multiplatform（iOS 实验版）

---

## 9. 🤝 贡献指南

### 提 Issue

- 标题格式：`[模块] 问题描述`，如 `[书库] 搜索无结果`。
- 正文包含：版本号、设备型号、复现步骤、日志或截图。

### 提 PR

1. Fork 本仓库，从 `main` 拉分支，命名 `fix/xxx` 或 `feat/xxx`。
2. 代码风格：Kotlin + Compose；组件放 `ui/components`；书源实现 `BookSource` 接口。
3. 提交信息格式：`type: 描述`（如 `fix: zlib 下载 503`）。
4. 提交前跑通 `./gradlew :app:assembleRelease`。

---

## 10. ⚠️ 免责声明

本项目仅用于技术学习与交流。所有在线书源（含 Venera 社区源、Z-Library、MangaDex 等）均来自第三方，内容版权归原作者/出版社所有；请勿将本项目用于商业用途或传播侵权内容。因使用本软件产生的一切法律问题与作者无关。

---

## 11. 🙏 鸣谢

- 液态玻璃引擎：[Abdullajon1881/LiquidGlass](https://github.com/Abdullajon1881/LiquidGlass)、[Kashif-E/KMPLiquidGlass](https://github.com/Kashif-E/KMPLiquidGlass)
- 底部弹窗：[skydoves/FlexibleBottomSheet](https://github.com/skydoves/FlexibleBottomSheet)
- 动效参考：[skydoves/compose-animations](https://github.com/skydoves/compose-animations)、[commandiron/ComposeLoading](https://github.com/commandiron/ComposeLoading)
- 滑块交互：[christianselig/JunoSlider](https://github.com/christianselig/JunoSlider)
- 缩放组件：[usuiat/Zoomable](https://github.com/usuiat/Zoomable)
- 翻页参考：GitHub `pagecurl`、`PTQFlipper`
- Venera 漫画源：[venera-app/venera-configs](https://github.com/venera-app/venera-configs)
- ehentai 取图思路：[delta-comic/delta-comic-plugin-ehentai](https://github.com/delta-comic/delta-comic-plugin-ehentai)

---

## 12. 📄 License

本仓库当前**未附带开源许可证（All Rights Reserved）**，代码仅作学习交流。第三方组件分别遵循其自身许可证（Apache-2.0 / MIT / GPL-3.0）。如需商用或二次分发，请联系作者获取授权。
