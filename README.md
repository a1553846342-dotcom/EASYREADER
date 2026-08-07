# Ciallo阅读 (EASYREADER)

> 一个基于 Kotlin + Jetpack Compose 的现代 Android 电子书阅读器。
> 支持 TXT / EPUB / 漫画阅读、多源在线书库搜索、断点续传下载与书架管理。

当前版本：**v0.35**

---

## 核心功能

- **阅读器**
  - TXT / EPUB 解析阅读
  - CBZ / CBR 漫画阅读
  - 3D / 2.5D 仿生翻页动画
  - 阅读进度保存、书签、夜间模式、TTS 朗读
- **在线书库**
  - Z-Library 原生搜索（隐藏 WebView 会话，可过 DiamWall 验证）、登录 / Cookie 管理
  - 节点管理：默认节点 + 官网 / 备用入口自动扒取 + 自定义节点 + 一键检测切换
  - MangaDex 漫画源
  - ehentai 漫画源：图片页直抓 + H@H 图床 Cronet 加载与本地缓存，失败自动重试
  - Venera JS 漫画源：拷贝漫画、comick、漫蛙吧、GoDa、漫画人、MYCOMIC 等
  - 聚合搜索：每出一个源立即展示，渐变毛玻璃胶囊分隔，失败显示“链接超时 / 无结果”
  - 搜索历史（点击搜索框展开 / 收起）
  - 自定义 JSON 书源：粘贴 / 文件导入，兼容 Legado 规则与 JSON API
  - 搜索结果卡片显示真实作者、作品编号（#jm号 / ehentai gid / nhentai id）与语言
- **下载与书架**
  - 漫画下载由应用级任务中心管理：切页面不中断，失败保留可重试，3 路并发下载
  - 下载断点续传，支持暂停 / 继续 / 取消
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

## v0.30 → v0.35 主要更新

### 漫画源与阅读
- ehentai 全链路修复：详情页改用 `hc=1&nw=session` + Referer，取图直接抓图片页 `#img`（参考 delta-comic 实现），不再依赖 api.e-hentai.org
- H@H 图床图片改用 Cronet（Chromium 网络栈）下载并本地缓存，规避 OkHttp 握手不兼容；阅读器按页懒加载，翻页秒开
- 图片加载失败自动重试（最多 3 次，自动换新 keystamp 链接）；源初始化后台预热连接，缓解代理冷启动超时
- 修复脚本显式 Cookie 被本地 CookieJar 覆盖的底层 bug
- picacg 登录持久化与搜索全链路修复；禁漫天堂 CDN 节点自动切换 + gzip 解压修复；hitomi AVIF → WebP 回退
- 全部 JS 源网络错误自动重试；阅读器黑屏替换为加载动画 + 失败重试按钮
- 搜索卡片显示真实作者（artist / cosplayer / group 标签提取）、作品编号与语言

### 漫画下载
- 重构为应用级下载中心 ComicDownloadManager：切换页面 / 重建界面不中断下载
- 下载失败任务保留在下载卡片中，显示失败原因 + 重试按钮（断点续传）
- 支持暂停 / 继续 / 取消，3 路并发下载，大章节速度明显提升
- 本地缓存复用：在线阅读过的图片下载时直接复用，不重复拉取

### 阅读器与体验
- 修复本地缓存图片 file:// 加载导致的闪退
- 图片加载统一 HTTP/1.1 + 多次重试
- 书架“我的书架”头部无用图标清理

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

> 注意：v0.35 的 release APK 仅包含 `arm64-v8a` 原生库以极致瘦身。
> 如需兼容其它 ABI，请移除 `app/build.gradle.kts` 中 `defaultConfig.ndk.abiFilters` 配置。
> 首次构建请确认 `local.properties` 中已配置 `sdk.dir`（该文件不参与版本管理）。

## 目录结构

```text
app/src/main/java/com/example/
├── MainActivity.kt          # 入口与导航
├── data/                    # Room 数据库、书籍解析、备份、偏好
├── download/                # 下载队列、断点续传 Worker
├── library/                 # 在线书库、下载任务中心、漫画下载导入
├── source/                  # 书源插件体系、Z-Library 引擎、JSON 书源、Venera JS 源
├── ui/                      # 全部 Compose 界面（书架 / 书库 / 阅读器 / 设置 / 统计）
│   ├── components/          # 公共 UI 组件（按钮、图表、空状态、加载动画等）
│   ├── help/                # 帮助手册
│   ├── mascot/              # 吉祥物动画
│   ├── pageturn/            # 3D / 2.5D 仿生翻页容器
│   └── source/              # 书源管理与登录 UI
└── net/engawapg/lib/zoomable/  # GitHub usuiat/Zoomable 完整源码（Apache-2.0）
```

---

## 致谢

- 翻页与卷角效果参考 GitHub `pagecurl`、`PTQFlipper`
- 缩放组件完整引入 [usuiat/Zoomable](https://github.com/usuiat/Zoomable)（Apache-2.0）
- Venera JS 运行时与社区漫画源来自 [venera-app/venera-configs](https://github.com/venera-app/venera-configs)（GPL-3.0）
- ehentai 取图思路参考 [delta-comic/delta-comic-plugin-ehentai](https://github.com/delta-comic/delta-comic-plugin-ehentai)（AGPL-3.0）
