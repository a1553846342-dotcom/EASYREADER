# 小说 & 漫画阅读器 App 开发设计与技术交接手册 (Handoff Guide)

本手册旨在详细记录该阅读器应用的系统架构、核心功能模块、UI/UX 动画、以及为解决大文件崩溃（OOM、ANR）而实施的关键技术优化。适合后续 AI 或开发者无缝接管并继续开发。

---

## 📖 项目简介
这是一款专为 Android 打造的高性能、沉浸式、高颜值**小说与漫画双栖阅读器**。界面采用精美的 **Material Design 3 (M3)** 设计语言，融合了细腻的自定义过渡动画（如呼吸悬浮、果冻触感缩放、极光流光特效等），支持多种翻页模式、TTS 文本转语音、书签高亮、全文本搜索以及多维度阅读数据统计。

---

## 🛠️ 技术栈与架构设计

应用严格遵循 **MVVM (Model-View-ViewModel)** 架构，确保数据单向流动 (UDF)，保障了业务逻辑与 UI 层面的解耦。

*   **核心开发语言**：Kotlin
*   **UI 渲染框架**：Jetpack Compose (全响应式声明式 UI)
*   **数据持久化**：SQLite / Room Database (KSP 生成)
*   **多线程与异步**：Kotlin Coroutines & Flow (实现完全的非阻塞式数据读取与流式加载)
*   **图像加载**：Coil (用于流畅加载漫画图片与封面)
*   **设计系统**：Material Theme 3 + 沉浸式 Edge-to-Edge 处理 (enableEdgeToEdge + WindowInsets 完美避开系统状态栏与导航栏)

---

## 📂 核心代码目录结构与职责

```text
/app/src/main/java/com/example/
├── MainActivity.kt               # 应用唯一入口 Activity，负责 Compose 主题加载与全屏 Navigation 路由分发
├── MainViewModel.kt              # 核心全局状态中心。接管书籍加载、章节目录状态、阅读进度更新与全文本检索
└── data/                         # 数据持久化与核心解析模块
    ├── Book.kt                   # 数据库实体类定义 (Book, Chapter, Bookmark, Highlight, CategoryEntity)
    ├── AppDatabase.kt            # Room 数据库配置、 migration、 复杂查询 DAO (含低内存占用的 Metadata 提取与分页加载)
    ├── BookRepository.kt         # 数据仓库层。实现大文件高效流式解析、字符编码自动探测、章节增量落库
    ├── ComicParser.kt            # 漫画文件/目录解析器
    ├── PreferencesManager.kt     # 基于 SharedPreferences 的轻量级用户阅读偏好持久化 (字体、翻页模式、护眼等)
    └── TtsManager.kt             # TTS 系统级朗读管理器 (状态控制、语速语调、按段播放)
└── ui/                           # 视觉表现与视图层
    ├── HomeScreen.kt             # 主页，包含精致的“我的书架”、按分类筛选、统计卡片入口及动态书架网格
    ├── ReaderScreen.kt           # 小说阅读核心界面。承载翻页动画、菜单覆层、高亮划线、实时进度反馈与偏好设置
    ├── ComicReaderScreen.kt      # 漫画阅读核心界面。支持缩放 (Zoomable)、垂直/水平多重滚动偏好
    ├── StatisticsScreen.kt       # 统计面版，内嵌 WeeklyReadingChart，展示优雅的可视化阅读时长趋势
    ├── SplashScreen.kt           # 启动极简文学名言屏
    ├── components/               # 可复用高颜值原子组件
    │   ├── StarryNightBackground.kt # 动态星空流光磨砂背景画布 (Canvas 绘制 + 贝塞尔曲线过渡)
    │   ├── CustomButtons.kt         # 封装了微动微缩动画的微交互按钮
    │   └── WeeklyReadingChart.kt    # 自定义绘制的周度阅读柱状图表
    └── theme/                    # 视觉外观规范定义
        ├── Theme.kt / Color.kt   # M3 动态色彩、深浅色模式与护眼色值
        └── ModifierExtensions.kt # 针对 Compose 手动优化的极致流畅压按物理动效 Modifier (.clickableWithFeedback)
```

---

## ⚡ 核心架构优化（大体积小说全面防崩溃方案）

为彻底解决“大体积小说在导入、解析和渲染时出现 OOM (内存溢出)、ANR (主线程无响应) 以及界面卡顿”的问题，系统重构并实现了以下**全链路流式架构**：

### 1. 流式读取与增量落库（Streaming Import）
*   **痛点**：传统解析方式习惯一次性将数 MB 的 TXT 文件全部 `readText()` / `readBytes()` 读取到 JVM 堆内存，极其容易造成大文件 OOM。
*   **方案**：在 `BookRepository.importBookFromUri` 中：
    *   首先通过**流式缓冲采样**探测编码（UTF-8 或 GBK），防止全量读取。
    *   采用 `BufferedReader` 配合 `InputStreamReader` 进行**逐行流式扫描** (`readLine`)。
    *   通过高效的**增量批处理落库 (Batch Insert)** 机制，将扫描出的章节暂存到 `batch` 列表，每达到 50 章自动执行一次 Room `insertChapters` 事务。这大幅减少了内存占用并缩短了高频数据库 IO 锁的时间，保证了导入大文件时的系统流畅度。

### 2. 内存与 UI 懒加载架构 (Lazy-Loading Chapters)
*   **痛点**：若书籍有数千章，一次性从 Room 加载全部章节内容到 `MainViewModel` 的 `StateFlow`，会导致大量的 `String` 对象常驻内存，使手机堆内存飙升，造成 OOM 并卡死列表渲染。
*   **方案**：
    *   **章节元数据化 (Metadata Projection)**：
        在 `AppDatabase.kt` 中设计了针对大书的优化查询 `getChaptersMetadataList(bookId)`。它通过投影查询只获取章节的 `id`、`title`、`chapterOrder` 等，并将 `content` 置为空。这使得无论书籍多大，其章节目录均仅占用几百 KB 的指针空间。
    *   **滑动窗口缓冲 lazy-load**：
        在 `MainViewModel` 中，采用**活动滑动窗口算法**。在小说启动或用户滑动切换章节时，主线程通过 `loadActiveChaptersContent` 异步且仅在后台线程加载当前章节、前一章和后一章（Window Size = 3）的完整内容。
    *   未进入滑动窗口的章节在内存中始终保持无内容（Empty String）的元数据状态。这使得 UI 渲染器只对活跃页面进行重绘，极大地保护了 JVM Heap，彻底抹平了滑动翻页、大章节跳转时的内存尖峰。

### 3. 后台安全全文本搜索
*   **痛点**：在包含数百万字的书籍中进行模糊搜索，如果同步在内存中执行，会直接阻塞主线程引发 ANR。
*   **方案**：在 `MainViewModel.searchFullText` 中，搜索工作被彻底调度到 `Dispatchers.IO` 后台协程上下文。直接通过数据库层 `getChaptersListForBook` 拉取原始字段，并利用字符串高能过滤函数在后台完成匹配，完成后派发结果回主线程，确保前台 UI 完全零卡顿。

---

## 🎨 动效与视觉表现（“软件的灵魂”）

为了在性能优化的同时绝不削减任何提升用户体验的细腻动画，应用做出了以下精细化性能重构：

### 1. 极致流畅的呼吸懸浮 (Smooth Breathing Float)
*   **痛点**：在书架界面（`HomeScreen.kt`），为了让每一本书拥有悬浮在纸面上的优雅微动效果，原先频繁对动画值进行直接的 UI 结构性重构，容易导致频繁 Recomposition（重绘）。
*   **优化**：将其重构为对底层 `graphicsLayer` 的直接作用：
    ```kotlin
    .graphicsLayer {
        translationY = translateYState.value.dp.toPx()
    }
    ```
    直接在 Compose 的 **Draw / Graphics Layer 阶段**修改变换矩阵，不触发整个 Composable 树的 Measure 与 Layout 阶段，完美避开了 GPU 重绘瓶颈，即使在低端设备上也能实现满帧 60/120 FPS 的顺滑浮动。

### 2. 果冻物理按压动效 Modifier
*   在 `ModifierExtensions.kt` 中，基于 `interactionSource` 重写了通用的按压弹性微缩动画。
*   将传统 `.clickable` 重塑为 `.clickableWithFeedback`，移除了容易引发系统级绘制卡顿的冗余涟漪，采用完全基于后台 GPU 加速的 `scale` 及微量 Glow 白光层，给用户以灵敏、跟手的完美微交互体验。

### 3. 极光星空 Canvas (StarryNightBackground)
*   采用精密的星盘 Canvas 算法，在不影响前台滑动的前提下，通过流式贝塞尔曲线优雅漂移星光。完全采用异步线程池定时更新，实现了极致的视觉深邃感。

---

## 🚀 交付下任 AI/开发者：待办与升级路径 (Next-Step Directions)

1.  **分片缓存持久化扩展**：
    由于目前我们实现了在 ViewModel 内存层面的“滑动窗口缓存”，后续若需支持多端或更严格的应用生命周期恢复，可在 Room 中进一步配合缓存淘汰算法（LruCache）构建基于 SQLite 的本地冷缓存。
2.  **加入分词索引 (FTS4/FTS5) 全文检索**：
    当前搜索是在后台线程逐章匹配，后续大书增多后，可以引入 Room 内置的 `FTS4` 虚拟表进行全文索引，利用预分词提高检索效率。
3.  **大漫画多线程预解码**：
    当前的 `ComicReaderScreen` 表现优秀，后续如果大漫画（如单图 20MB 的超高清原画）加载时感到吃力，建议在 `Coil` 中启用 `Bitmap` 池化分配（`inBitmap`）与超大图切割机制。

---
*感谢您接棒本项目！让我们继续保持“动画是软件的灵魂”的信仰，将其打造成最流畅高雅的阅读神器。*
