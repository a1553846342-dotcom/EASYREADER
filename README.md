# Ciallo阅读 (EASYREADER)

> 一个基于 Kotlin + Jetpack Compose 的现代 Android 电子书阅读器。
> 支持 TXT / EPUB / 漫画阅读、多源在线书库搜索、断点续传下载与书架管理。

当前版本：**v0.30**

---

## 核心功能

- **阅读器**
  - TXT / EPUB 解析阅读
  - CBZ / CBR 漫画阅读
  - 3D / 2.5D 仿真翻页动画
  - 阅读进度保存、书签、夜间模式、TTS 朗读
- **在线书库**
  - Z-Library 原生搜索（隐藏 WebView 会话，可过 DiamWall 验证）+ 登录 / Cookie 管理
  - 节点管理：默认节点 + 官网 / 备用入口自动扒取 + 自定义节点 + 一键检测切换
  - MangaDex 漫画源
  - Venera JS 漫画源：拷贝漫画、comick、漫蛙吧、GoDa、漫画人、MYCOMIC 等
  - 聚合搜索：每出一个源立即展示，渐变毛玻璃源胶囊分隔，失败显示“链接超时 / 无结果”
  - 搜索历史（点击搜索框展开 / 收起）
  - 自定义 JSON 书源：粘贴 / 文件导入，兼容 Legado 规则与 JSON API
- **下载与书架**
  - 书籍 / 漫画断点续传下载，支持暂停 / 继续 / 取消
  - 书库右上角下载面板与漫画章节页共用同一套下载状态
  - 毛玻璃下载卡片：封面、书名、进度、速度、剩余大小
  - 下载完成自动校验并导入书架
- **漫画阅读体验**
  - 在线与本地阅读器：双指捏合缩放、双击放大 / 还原、一指缩放（双击按住拖动）
  - 横向逐页缩放，纵向整列缩放
- **体验**
  - 深色 / 护眼 / 自定义主题配色
  - 吉祥物动画、开屏海报、阅读统计图表
  - 帮助手册与书源管理

---

## v0.21 → v0.30 主要更新

### 在线书库
- 书库从“基本可用”重做为完整在线书库：Z-Library 原生搜索、登录 / Cookie 会话、节点管理（官网入口自动扒取 + 自定义节点 + 一键检测）
- 新增 MangaDex 漫画源，以及基于 QuickJS + Cronet 的 Venera JS 漫画源体系（拷贝漫画、comick、漫蛙吧等社区源，支持源仓库刷新与成人源开关）
- 聚合搜索改为流式展示：哪个源先搜完就先显示哪个源，源与源之间用渐变毛玻璃胶囊分隔，每个源独立显示“搜索中 / 结果 / 链接超时 / 无结果”
- 新增搜索历史：点击搜索框以“窗帘”动画展开 / 收起
- 修复 JS 源调用 `this` 绑定问题；拷贝漫画动态 API 域名真正持久化，失败自动切换候选域名并重试；源脚本补丁版本升级自动重下，避免坏脚本导致源消失

### 漫画下载
- 章节下载支持断点续传，暂停后保留进度与已下载图片，继续时接着下
- 章节页与书库右上角下载面板统一提供 暂停 / 继续 / 取消，共用同一套下载状态
- 下载完成自动导入书架

### 漫画阅读器
- 在线阅读器与本地阅读器统一支持双指捏合、双击放大 / 还原、一指缩放
- 横向 Pager 逐页缩放，纵向 LazyColumn 整列缩放（不再只放大单张图）
- 缩放能力完整引入 GitHub [usuiat/Zoomable](https://github.com/usuiat/Zoomable)（Apache-2.0），手势与翻页 / 滚动互不冲突

### 体验与视觉
- 加载动画替换为 SpinKit 风格 ChasingDots（原样实现）
- 书库下载进度卡片、聚合源胶囊等毛玻璃视觉统一
- 成人内容开关、搜索历史、书源管理等交互重构

### 性能与体积
- v0.30 release APK 从约 27MB 压缩至约 **10MB**：仅保留 arm64-v8a 原生库 + 中英资源
- 持续启用 R8 混淆 + 资源压缩，`extractNativeLibs=false` 降低安装体积
- 删除无用调试 / 诊断页面与死代码

---

## 技术栈

- Kotlin + Jetpack Compose（Material 3）
- OkHttp + Jsoup + Moshi + Coil
- Room + WorkManager
- Navigation Compose + Haze（毛玻璃）
- QuickJS（Venera JS 漫画源）+ Cronet（浏览器级 TLS）

## 构建

环境要求：

- Android Studio（或命令行 Gradle）
- JDK 17+、Android SDK（compileSdk 35 / minSdk 24 / targetSdk 35）

正式版构建（R8 混淆 + 资源压缩）：

```bash
gradle :app:assembleRelease
```

APK 输出：`app/build/outputs/apk/release/app-release.apk`

调试版构建：

```bash
gradle :app:assembleDebug
```

> 注意：v0.30 的 release APK 仅包含 `arm64-v8a` 原生库以极致瘦身；
> 如需兼容其它 ABI，请移除 `app/build.gradle.kts` 中 `defaultConfig.ndk.abiFilters` 配置。
> 首次构建请确认 `local.properties` 中已配置 `sdk.dir`（该文件不参与版本管理）。

## 目录结构

```text
app/src/main/java/com/example/
├── MainActivity.kt          # 入口与导航
├── data/                    # Room 数据库、书籍解析、备份、偏好
├── download/                # 下载队列、断点续传 Worker
├── library/                 # 在线书库、隐藏 WebView 会话、节点管理
├── source/                  # 书源插件体系、Z-Library 引擎、JSON 书源、Venera JS 源
├── ui/                      # 全部 Compose 界面（书架 / 书库 / 阅读器 / 设置 / 统计）
│   ├── components/          # 公共 UI 组件（按钮、图表、空状态、加载动画等）
│   ├── help/                # 帮助手册
│   ├── mascot/              # 吉祥物动画
│   ├── pageturn/            # 3D / 2.5D 仿真翻页容器
│   └── source/              # 书源管理与登录 UI
└── net/engawapg/lib/zoomable/  # GitHub usuiat/Zoomable 完整源码（Apache-2.0）
```

---

## 致谢

- 翻页与卷页效果参考 GitHub `pagecurl`、`PTQFlipper`
- 缩放组件完整引入 [usuiat/Zoomable](https://github.com/usuiat/Zoomable)（Apache-2.0）
- Venera JS 运行时与社区漫画源来自 [venera-app/venera-configs](https://github.com/venera-app/venera-configs)（GPL-3.0）
