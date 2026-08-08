# AGENTS.md - 电子书阅读器 App 项目接管与开发指南

> **提示**：本文件为接管本 Android 项目的 AI Agent 提供的全景架构指南、核心文件清单、数据流说明、ZLibrary 模块历史演进及当前真实 Bug 诊断日志。无须任何前置聊天历史上下文，读取本文件后即可理解项目并直接开始开发与调优。

---

## 〇、当前版本与近期变更（v0.72）

- **版本**：v0.72（versionCode 147）
- **UI 组件约定**：
  - 按钮一律使用 `AppActionButton`（四变体 Primary / Secondary / Tertiary / Destructive），颜色取自 `LocalAppButtonColors` / MaterialTheme token，禁止组件内硬编码主题色；`GradientActionButton` / `AppLiquidButton` 为兼容入口。
  - 开关统一使用 `AppLiquidSwitch` / `JellySwitch`（书源管理与设置页一致）。
  - 底部导航栏使用 `AppBottomTabBar`（悬浮液态玻璃胶囊，滚动收缩 + 主题色过渡）。
  - 弹窗内玻璃元素必须使用 `DialogLiquidGlass` 创建该窗口自己的 LiquidGlass Provider，禁止复用主窗口 Provider，否则会抛 “layouts are not part of the same hierarchy” 闪退。
- **构建体积**：release APK 约 7.6MB（原生库压缩 + R8 full mode + 资源收缩 + 仅 arm64-v8a）。
- **部署**：编译安装走 `android-adb-deploy` skill（build_apk.ps1 / install_apk.ps1），版本号必须递增，华为安装弹窗由脚本自动处理。

---

## 一、项目整体介绍

### 1.1 项目概况
- **项目名称**：Modern E-Book Reader (Android 电子书与漫画阅读器 & 多源书库)
- **开发语言**：Kotlin 2.0.21
- **UI 框架**：Jetpack Compose (Material Design 3)
- **最低 SDK 版本**：Android 7.0 (API 24)
- **目标 SDK 版本**：Android 15 (API 35)
- **核心依赖**：
  - **网络层**：OkHttp 4.12.0 + kotlinx-coroutines
  - **解析层**：Jsoup 1.18.3 (HTML 抓取解析)
  - **数据库**：Room 2.6.1 + KSP
  - **图片加载**：Coil Compose 2.7.0
  - **异步流**：Kotlin Flow / StateFlow / ViewModel
  - **液态玻璃**：Abdullajon1881/LiquidGlass + KMPLiquidGlass（backdrop 模块）
  - **底部弹窗**：skydoves/FlexibleBottomSheet；**动效**：skydoves/compose-animations、commandiron/ComposeLoading

### 1.2 目录结构全景

```text
/app/src/main/java/com/example/
├── MainActivity.kt                       # 应用入口 Activity，设置 EdgeToEdge 与 Compose 主主题
├── MainViewModel.kt                      # 全局状态，包含主题/设置/初始化
│
├── data/                                 # 本地持久化与书籍解析数据层
│   ├── AppDatabase.kt                    # Room 数据库定义 (BookDao)
│   ├── BackupManager.kt                  # 书籍与设置备份导出导入
│   ├── Book.kt                           # Book 实体类 (包含 id, title, author, filePath, format 等)
│   ├── BookRepository.kt                # 书籍数据仓库，统一封装 Room DB 操作与本地文件管理
│   ├── ComicParser.kt                    # CBZ/CBR/Zip 漫画文件解析器
│   ├── EpubParser.kt                     # EPUB/TXT 文件结构解析与章节提取
│   ├── PreferencesManager.kt             # SharedPreferences 偏好配置 (阅读设置、主题等)
│   └── TtsManager.kt                     # Android 系统 TTS 朗读引擎封装
│
├── download/                             # 书籍离线下载管理模块
│   ├── DownloadManager.kt                # 下载队列管理器 (基于 Coroutines / WorkManager)
│   ├── DownloadProgressBroadcaster.kt    # 下载进度事件总线/广播
│   ├── DownloadRequest.kt                # 下载请求数据结构
│   ├── DownloadState.kt                  # 下载状态枚举 (Pending, Downloading, Completed, Failed)
│   ├── DownloadTaskDao.kt                # 下载任务 Room DAO
│   ├── DownloadTaskEntity.kt             # 下载任务本地持久化实体
│   └── DownloadWorker.kt                 # Android WorkManager 后台下载任务实现
│
├── library/                              # 在线书库 (搜索、多源聚合) UI & 业务逻辑
│   ├── LibraryError.kt                   # 书库异常枚举与错误信息转换
│   ├── LibraryFirstLaunchState.kt        # 书库首次启动提示/初始化状态
│   ├── LibraryScreen.kt                  # 在线书库主界面 (分类卡片、搜索框、搜索结果列表)
│   ├── LibraryUiState.kt                 # 书库 UI 状态集合 (Loading, Success, Error, SourceSwitch)
│   └── LibraryViewModel.kt               # 书库 ViewModel (管理 SourceManager, 搜索处理, 结果映射)
│
├── source/                               # 多书源核心抽象与插件系统
│   ├── AuthenticationState.kt            # 书源登录认证状态
│   ├── BookSource.kt                     # 书源统一接口定义 (Search, Detail, Download)
│   ├── DownloadInfo.kt                   # 下载链接与 Header 数据封装
│   ├── LoginCredential.kt                # 登录凭证结构
│   ├── SearchBook.kt                     # 统一搜索结果实体 (id, title, author, coverUrl, sourceId)
│   ├── SourceCapabilities.kt             # 书源能力定义 (支持搜索/登录/调试/分类)
│   ├── SourceConfig.kt                   # 书源配置实体
│   ├── SourceException.kt                # 书源相关异常体系
│   ├── SourceManager.kt                  # 动态书源注册与切换管理器 (ZLibrarySource, JsonBookSource, MockBookSource)
│   ├── SourceResult.kt                   # 书源操作 Result 包装类
│   ├── SourceViewModel.kt                # 书源管理界面 ViewModel
│   ├── importer/                         # 自定义 JSON 书源导入器
│   │   └── SourceImporter.kt
│   ├── impl/                             # 默认内置书源实现
│   │   ├── JsonBookSource.kt             # 支持 XPath/JsonPath 的自定义 JSON 规则书源
│   │   └── MockBookSource.kt             # 本地 Mock 测试书源
│   ├── parser/                           # JSON 节点解析器
│   │   └── JsonPathResolver.kt
│   ├── storage/                          # 书源持久化存储
│   │   ├── SharedPreferencesSourceStorage.kt
│   │   └── SourceStorage.kt
│   └── zlibrary/                         # ZLibrary 引擎专项集成包 (详见后文)
│       ├── DiamWallInterceptor.kt        # Cloudflare / DiamWall 人机验证拦截处理器
│       ├── EncryptedCookieJar.kt         # 加密 Persistent Cookie 存储
│       ├── EndpointHealthChecker.kt      # 节点健康度连通性检查器
│       ├── RemoteEndpointConfig.kt       # 远程动态节点配置
│       ├── RemoteEndpointProvider.kt     # 远程节点更新策略
│       ├── ZLibraryAccessChecker.kt      # ZLibrary 响应 HTML 识别与限制检查
│       ├── ZLibraryClient.kt             # ZLibrary 旧版 HTTP 客户端包装
│       ├── ZLibraryCredentialStorage.kt  # 账号密码与 Cookie 本地安全存储
│       ├── ZLibraryDomainResolver.kt     # ZLibrary 域名自动解析器
│       ├── ZLibraryEndpointProvider.kt   # 镜像节点提供者 (支持备用节点与 DoH 探测)
│       ├── ZLibraryParser.kt             # 旧版 ZLibrary HTML 解析器
│       ├── ZLibrarySource.kt             # ZLibrary 实现 BookSource 接口的核心入口
│       ├── ZLibraryStatus.kt             # ZLibrary 节点与连通状态枚举
│       ├── network/                      # 网络底层 (自定义 DNS、Session、HttpClient)
│       │   ├── ZLibraryDns.kt            # 支持 AliDNS / DNSPod / Cloudflare / Google DoH 的自定义 DNS 解析器
│       │   ├── ZLibraryHttpClient.kt     # 全局单例 OkHttpClient 配置
│       │   ├── ZLibraryNetworkLogger.kt  # 网络抓包诊断日志记录器
│       │   └── ZLibrarySessionManager.kt # 会话与 Cookie 维护管理器
│       └── parser/                       # ZLibrary 多布局自适应解析器
│           ├── DesktopLayoutParser.kt    # 桌面版 HTML 页面解析器
│           ├── LegacyLayoutParser.kt     # 旧版 ZLibrary HTML 解析器
│           ├── MobileLayoutParser.kt      # 移动版 HTML 页面解析器
│           ├── ZLibraryLayoutParser.kt   # 布局解析器统一接口
│           └── ZLibraryParserManager.kt # 解析器策略分配器 (detectLayoutName, parseSearchPage)
│
└── ui/                                   # 应用 Compose 前端 UI 界面
    ├── HomeScreen.kt                     # 底部导航主框架 (书架, 在线书库, 统计, 设置)
    ├── BookShelfScreen.kt                # 本地书架界面 (书籍列表、导入入口、继续阅读)
    ├── ReaderScreen.kt                   # EPUB/TXT 仿真翻页阅读器核心界面
    ├── ComicReaderScreen.kt              # 漫画阅读器界面
    ├── OnboardingScreen.kt               # 首次使用引导页
    ├── SplashScreen.kt                   # 启动过渡屏
    ├── StatisticsScreen.kt               # 阅读时长与统计图表界面
    ├── SettingsTabScreen.kt              # 设置与偏好调整
    ├── EndpointDiagnosticScreen.kt       # ZLibrary 节点连通性诊断 UI
    ├── components/                       # 公用 UI 组件 (按钮、图表、空状态、星空背景等)
    ├── help/                             # 帮助与文档 UI
    ├── mascot/                           # 吉祥物动画控制器与 Asset 逐帧渲染
    ├── pageturn/                         # 3D/2.5D 仿真翻页容器
    ├── source/                           # 书源管理与诊断相关 UI
    │   ├── SourceManagementScreen.kt     # 书源列表管理、导入、切换界面
    │   ├── SourceNetworkDiagnosticScreen.kt # ZLibrary 网络节点链路诊断屏
    │   ├── ZLibraryDebugTestScreen.kt    # ZLibrary 调试测试屏
    │   ├── ZLibraryLoginDialog.kt       # ZLibrary 登录弹窗
    │   └── ZLibraryRealDeviceDiagnosticScreen.kt # 真机全链路 Diagnostics 诊断屏
    └── theme/                            # Material 3 主题样式 (Color, Type, Theme, Modifiers)
```

---

## 二、完整架构说明

### 2.1 层次架构图

```text
+-----------------------------------------------------------------------+
|                             UI Layer                                  |
|  (LibraryScreen / BookShelfScreen / SourceManagementScreen / Reader)  |
+-----------------------------------------------------------------------+
                                   |
                                   v
+-----------------------------------------------------------------------+
|                          ViewModel Layer                              |
|           (LibraryViewModel / SourceViewModel / MainViewModel)        |
+-----------------------------------------------------------------------+
                                   |
                                   v
+-----------------------------------------------------------------------+
|                           Repository / Manager                        |
|             (SourceManager / BookRepository / DownloadManager)        |
+-----------------------------------------------------------------------+
                                   |
              +--------------------+--------------------+
              |                                         |
              v                                         v
+---------------------------+             +---------------------------+
|      ZLibrarySource       |             |      JsonBookSource       |
| (ZLibrary 专项网络与解析)   |             | (自定义 JSON 规则解析书源)  |
+---------------------------+             +---------------------------+
              |
              v
+-----------------------------------------------------------------------+
|                            Network Layer                              |
|  (ZLibraryHttpClient -> ZLibraryDns [DoH] -> EncryptedCookieJar)      |
+-----------------------------------------------------------------------+
                                   |
                                   v
+-----------------------------------------------------------------------+
|                            Parser Layer                               |
| (ZLibraryParserManager -> Desktop / Mobile / Legacy Layout Parsers)   |
+-----------------------------------------------------------------------+
                                   |
                                   v
+-----------------------------------------------------------------------+
|                          Storage / Database                           |
|        (Room DB: AppDatabase / Encrypted SharedPreferences)           |
+-----------------------------------------------------------------------+
```

### 2.2 各层职责与边界

1. **UI Layer (`com.example.ui`, `com.example.library`)**
   - **职责**：绘制纯 Compose UI，捕获用户交互（如输入关键词“三体”、点击搜索、切换节点）。
   - **输入**：用户触摸事件、`UiState` 观察流。
   - **输出**：触发 ViewModel 方法（如 `viewModel.search("三体")`）。

2. **ViewModel Layer (`LibraryViewModel`, `SourceViewModel`)**
   - **职责**：响应 UI 动作，调度 `SourceManager` 执行具体操作，管理 UI 状态（`StateFlow<LibraryUiState>`）。
   - **输入**：UI 传递的搜索词、选中的 `sourceId`。
   - **输出**：`LibraryUiState.Success(books)` 或 `LibraryUiState.Error(msg)`。

3. **Source 插件层 (`SourceManager`, `ZLibrarySource`, `JsonBookSource`)**
   - **职责**：将统一的 `BookSource` 接口命令转换为特定的网络请求和数据提取。
   - **输入**：搜索关键词、页码。
   - **输出**：统一的 `List<SearchBook>` 或 `DownloadInfo`。

4. **Network & DNS 层 (`ZLibraryHttpClient`, `ZLibraryDns`, `ZLibrarySessionManager`)**
   - **职责**：发起真实 HTTP 请求。`ZLibraryDns` 内置 AliDNS、DNSPod、Cloudflare、Google DoH，突破 Android 系统 DNS 污染。
   - **输入**：URL、Request Header、Cookies。
   - **输出**：`Response` (HTML/JSON Body)。

5. **Parser 层 (`ZLibraryParserManager`, `DesktopLayoutParser`, `MobileLayoutParser`)**
   - **职责**：使用 Jsoup 分析 HTML，提取书籍名称、作者、封面 URL、下载 Path。
   - **输入**：HTML 字符串。
   - **输出**：`List<SearchBook>`。

6. **Storage 层 (`BookRepository`, `AppDatabase`, `ZLibraryCredentialStorage`)**
   - **职责**：本地书籍存取、阅读进度保存、密钥与账号 Cookie 持久化。

---

## 三、核心文件逐文件说明

以下汇总所有在线书库、ZLibrary、书源管理及下载相关的核心代码文件：

### 1. `ui/LibraryScreen.kt`
- **路径**：`app/src/main/java/com/example/library/LibraryScreen.kt`
- **作用**：在线书库主视图，提供搜索框、书源选择 Chip、搜索结果列表呈现。
- **主要类/组件**：`LibraryScreen()`
- **被谁调用**：`HomeScreen.kt` (TabBar 切换)
- **调用谁**：`LibraryViewModel`
- **当前状态**：UI 完备，能正常展示 `LibraryUiState` 传入的 `List<SearchBook>`。

### 2. `library/LibraryViewModel.kt`
- **路径**：`app/src/main/java/com/example/library/LibraryViewModel.kt`
- **作用**：在线书库 ViewModel，桥接 UI 与 `SourceManager`。
- **主要类**：`LibraryViewModel`
- **关键方法**：`search(query: String)`、`selectSource(sourceId: String)`
- **调用谁**：`SourceManager.getActiveSource()` -> `BookSource.search()`
- **当前状态**：正常运作，支持 Coroutines 异步调度。

### 3. `source/SourceManager.kt`
- **路径**：`app/src/main/java/com/example/source/SourceManager.kt`
- **作用**：书源注册与切换中心。内置 `ZLibrarySource`、`JsonBookSource` 等。
- **主要类**：`SourceManager`
- **关键方法**：`registerSource()`, `getActiveSource()`, `getAllSources()`
- **当前状态**：正常，默认选中的主书源为 `"zlibrary"` (`ZLibrarySource`)。

### 4. `source/BookSource.kt`
- **路径**：`app/src/main/java/com/example/source/BookSource.kt`
- **作用**：所有书源的统一标准接口。
- **主要接口**：`interface BookSource`
- **关键方法**：
  - `search(query: String, page: Int): SourceResult<List<SearchBook>>`
  - `getDetail(bookId: String): SourceResult<SearchBook>`
  - `getDownloadInfo(bookId: String): SourceResult<DownloadInfo>`

### 5. `source/zlibrary/ZLibrarySource.kt`
- **路径**：`app/src/main/java/com/example/source/zlibrary/ZLibrarySource.kt`
- **作用**：ZLibrary 核心书源实现。组织 Endpoint 动态获取、网络请求发起、解析调用与异常捕获。
- **主要类**：`ZLibrarySource` (实现 `BookSource`)
- **关键方法**：
  - `search(query, page)`：调用 `endpointProvider.getEndpoint()` 拿到有效域名，拼接 `https://<domain>/s/<encodedQuery>` 发起 GET，交给 `ZLibraryParserManager` 解析。
  - `getDownloadInfo(bookId)`：获取书籍下载真实 URL。
- **调用**：`ZLibraryEndpointProvider`, `ZLibraryHttpClient`, `ZLibraryParserManager`
- **当前状态**：已接入自定义 DoH 解析与多 Layout 解析器，但因网络环境/Cloudflare 人机拦截，真机请求需彻底通畅。

### 6. `source/zlibrary/network/ZLibraryDns.kt`
- **路径**：`app/src/main/java/com/example/source/zlibrary/network/ZLibraryDns.kt`
- **作用**：自定义 OkHttp DNS 解析器。解决部分地区/网络 Android 系统 DNS 污染 `1lib.sk` 等域名的问题。
- **主要类**：`ZLibraryDns` (实现 `okhttp3.Dns`)
- **关键方法**：
  - `lookup(hostname)`：首先尝试系统 DNS；若失败或被污染，依次发起 DoH (AliDNS `223.5.5.5` -> DNSPod `1.12.12.12` -> Cloudflare `1.1.1.1` -> Google `8.8.8.8`) JSON 查询，解析出真实 A 记录 IP。
- **被谁调用**：`ZLibraryHttpClient.okHttpClient`

### 7. `source/zlibrary/network/ZLibraryHttpClient.kt`
- **路径**：`app/src/main/java/com/example/source/zlibrary/network/ZLibraryHttpClient.kt`
- **作用**：配置全局统一的 OkHttpClient，包含 SSL 信任、超时、User-Agent 伪装、CookieJar、DoH DNS。
- **主要类/对象**：`ZLibraryHttpClient`
- **关键属性**：`val okHttpClient: OkHttpClient`

### 8. `source/zlibrary/network/ZLibrarySessionManager.kt`
- **路径**：`app/src/main/java/com/example/source/zlibrary/network/ZLibrarySessionManager.kt`
- **作用**：维持 ZLibrary 登录态、Session Cookie 校验与重新登录逻辑。

### 9. `source/zlibrary/ZLibraryEndpointProvider.kt`
- **路径**：`app/src/main/java/com/example/source/zlibrary/ZLibraryEndpointProvider.kt`
- **作用**：提供与维护 ZLibrary 可用镜像节点候选池（如 `1lib.sk`, `z-library.rs`, `zlibrary-global.se` 等）。

### 10. `source/zlibrary/parser/ZLibraryParserManager.kt`
- **路径**：`app/src/main/java/com/example/source/zlibrary/parser/ZLibraryParserManager.kt`
- **作用**：自动检测 ZLibrary 响应 HTML 的布局类型（`DesktopLayoutParser` / `MobileLayoutParser` / `LegacyLayoutParser`），并解析出 `List<SearchBook>`。
- **关键方法**：`detectLayoutName(html)`, `parseSearchPage(html, baseUrl)`

### 11. `ui/source/ZLibraryRealDeviceDiagnosticScreen.kt`
- **路径**：`app/src/main/java/com/example/ui/source/ZLibraryRealDeviceDiagnosticScreen.kt`
- **作用**：真机一键全链路诊断屏。逐步分步测试：
  - Step 1: ConnectivityManager 网络状态
  - Step 2: System DNS / A 记录 / AAAA 记录 / DoH 解析测试
  - Step 3: TCP 443 端口 Socket 连通测试
  - Step 4: TLS SSLSocket 握手测试
  - Step 5: OkHttp 直连 `https://1lib.sk/` 首页获取
  - Step 6: OkHttp GET `/s/三体` 搜索请求与 HTML Parser 解析测试
  - 输出完整 Exception StackTrace 日志。

---

## 四、完整数据流

### 4.1 搜索书籍完整数据流

```text
[用户点击搜索 "三体"]
        │
        ▼
1. UI 层 (LibraryScreen)
   - 触发 LibraryViewModel.search("三体")
        │
        ▼
2. ViewModel 层 (LibraryViewModel)
   - 将 uiState 置为 LibraryUiState.Loading
   - 调用 SourceManager.getActiveSource().search("三体", page = 1)
        │
        ▼
3. Source 层 (ZLibrarySource)
   - 调用 endpointProvider.getEndpoint() 拿到当前可用域名 (例如 "1lib.sk")
   - 构造目标 URL: "https://1lib.sk/s/%E4%B8%89%E4%BD%93"
   - 使用 ZLibraryHttpClient.okHttpClient 发起 HTTP GET 请求
        │
        ▼
4. Network & DNS 层 (ZLibraryHttpClient / ZLibraryDns)
   - ZLibraryDns 拦截 "1lib.sk"
   - 优先查 System DNS；若失败，触发 DoH (AliDNS/DNSPod) 获得真实 IP (如 190.x.x.x)
   - 完成 TCP / TLS 443 握手，发送 Chrome User-Agent 请求头
   - 接收服务器返回 200 OK HTML body
        │
        ▼
5. Parser 层 (ZLibraryParserManager)
   - ZLibraryParserManager.parseSearchPage(html, baseUrl)
   - 策略切换至 Mobile/Desktop Parser，选择 `.bookCard` 或 `table.zbook` 选择器
   - 提取 title ("三体"), author ("刘慈欣"), coverUrl, bookDetailPath
   - 返回 List<SearchBook>
        │
        ▼
6. ViewModel -> UI 渲染 (LibraryScreen)
   - LibraryViewModel 接收 SourceResult.Success(books)
   - 更新 _uiState.value = LibraryUiState.Success(books)
   - LibraryScreen 收到 StateFlow 变化，LazyColumn 渲染书籍卡片列表
```

### 4.2 下载书籍数据流
1. 用户在搜索结果或详情页点击“下载”。
2. `LibraryViewModel` 调用 `ZLibrarySource.getDownloadInfo(bookId)`。
3. `ZLibrarySource` 请求 `/dl/<id>/<hash>` 提取真实文件重定向 URL。
4. `DownloadManager` 创建 `DownloadRequest`，提交给 `DownloadWorker` (WorkManager) 后台下载。
5. 下载完成后通知 `BookRepository` 扫描该文件并写入 Room 数据库。

---

## 五、ZLibrary 专项历史修改记录

| 阶段 | 修改主要文件 | 设计目标 | 实际效果与局限 |
|---|---|---|---|
| **Phase 11.5** | `EndpointHealthChecker.kt`<br>`RemoteEndpointProvider.kt` | 增加多节点自动健康检查与动态域名切换 | 仅仅检查了域名 TCP 端口，未能保证 HTTP 搜索接口可用性 |
| **Phase 12** | `EncryptedCookieJar.kt`<br>`ZLibrarySessionManager.kt`<br>`Desktop/MobileLayoutParser.kt` | 引入加密 Cookie 持久化，重构多布局适配 Parser | Parser 在获取到完整 HTML 时解析准确，但依赖底层 HTTP 能成功拿到 HTML |
| **Phase 13** | `DiamWallInterceptor.kt`<br>`ZLibraryAccessChecker.kt` | 识别 Cloudflare / DiamWall 人机验证屏 | 能够检测出 403 / 503 验证页面，但 OkHttp 无法自动解 JS Challenge |
| **Phase 14 & 14.2** | `ZLibraryDns.kt`<br>`ZLibraryRealDeviceDiagnosticScreen.kt` | 诊断底层 DNS/TLS，增加 AliDNS/DNSPod/Cloudflare DoH 解析 | 成功排查并定位了部分 Android 设备上 System DNS 解析 `1lib.sk` 失败的问题 |
| **Phase 14.3** | `ZLibraryHttpClient.kt`<br>`ZLibraryEndpointProvider.kt` | 将 DoH 绑定至全局 OkHttp，优化 candidate 测试 | 解决了网络层 DNS 拦截问题，提高了请求成功率 |
| **Phase 15** | `AndroidManifest.xml`<br>`RemoteEndpointProvider.kt` | 补充 Missing Network Permissions，清理占位符请求 | 修复了最底层因缺少 `INTERNET` 与 `ACCESS_NETWORK_STATE` 导致系统静默拒绝一切网络 I/O 的重大根因 |

---

## 六、当前真实 Bug 描述

### 6.1 现象与对比
- **手机 Chrome 浏览器**：在真机上用 Chrome 打开 `https://1lib.sk/`，搜索“三体”，可以正常加载页面并显示书籍结果。
- **App 内**：进入“在线书库”，选择 ZLibrary，输入“三体”搜索，界面提示连接失败或返回 0 条结果。

### 6.2 根因分析（重大突破与已修复部分）
1. **[已修复] AndroidManifest.xml 缺少网络权限**：
   - **根本原因**：之前该项目作为本地离线电子书阅读器时未声明 `android.permission.INTERNET` 与 `android.permission.ACCESS_NETWORK_STATE` 权限。
   - **直接后果**：Android OS 系统层直接静默拦截并拒绝所有应用发起的 Socket、DNS 查找与 OkHttp 请求，表现为延迟 0~1ms、DNS ❌ / TLS ❌ / HTTP ❌（因为网络 I/O 在底盘被系统直接拦截，未真正到达网络网卡）。
   - **修复动作**：已在 `AndroidManifest.xml` 中成功补全这两项权限。
2. **潜在后续问题（系统 DNS 污染 / Cloudflare JS 盾）**：
   - **DNS 污染**：在部分受限网络下，系统 DNS 对 `1lib.sk` 可能解析出被污染的虚假 IP，导致 DNS 显示正常但 TLS 握手失败（DNS ✅ / TLS ❌）。若真机出现此现象，需把 `ZLibraryDns.kt` 改为强制优先 DoH（AliDNS/DNSPod/Cloudflare）。
   - **Cloudflare JS Challenge**：若直连成功但返回 403 / 503 HTML，说明触发了 Cloudflare 人机验证，纯 OkHttp 无法运行 JS 脚本，此时需配合 WebView 抓取 Cookie 或代理加载。

---

## 七、当前怀疑点与排查验证方法

| 序号 | 怀疑点 | 对应文件 | 验证与排查方法 |
|---|---|---|---|
| 1 | **DNS 解析失败** | `ZLibraryDns.kt` | 打开 App 内“源管理 -> 真机全链路诊断”，观察 Step 1 系统 DNS 与 DoH 结果。若 DoH 能出 IP 但系统 DNS 失败，确认是 DNS 污染。 |
| 2 | **Cloudflare JS 盾/Cookie 缺失** | `ZLibraryHttpClient.kt`<br>`ZLibraryAccessChecker.kt` | 查看诊断屏输出的 HTML 源码片段。若包含 `Just a moment...` 或 `Enable JavaScript`，说明需要 JS 运行环境或 Cookie 注入。 |
| 3 | **Jsoup Parser 节点未匹配** | `ZLibraryParserManager.kt` | 查看诊断屏 Step 6 解析结果。若 HTTP Code 为 200，但解析出 0 本书，说明 ZLibrary 页面 DOM 结构变更，需调整 CSS 选择器。 |
| 4 | **UI 状态未刷新** | `LibraryViewModel.kt` | 检查 ViewModel 是否正确捕获 `SourceResult.Success` 并更新 `_uiState`。 |

---

## 八、已知错误/虚假架构模式（请避免重复犯错）

> **警告**：接手项目的 AI 必须明确以下模式是“假解决方案”，切勿重复引入：

1. **假健康检测**：仅用 Socket 连通 443 端口就判定节点可用。必须发真实 GET 请求并能正常解析出图书才算可用。
2. **Mock / Robolectric 本地单元测试**：使用本地静态 HTML 文件断言 Parser 成功，这无法证明真机网络和实时 API 能通。
3. **纯 Header 伪装**：盲目增加 `User-Agent`、`Accept-Language` 请求头。如果服务端启用了 JS Challenge，纯 Header 伪装毫无作用。
4. **诊断工具与实际请求脱节**：在诊断屏里写了一套 DoH 代码，但 `ZLibrarySource` 实际请求依然用默认 OkHttp。**必须保证所有模块统一使用 `ZLibraryHttpClient.okHttpClient`**。

---

## 九、下一 AI 接管工作建议

请按照以下步骤有序推进修复：

1. **第一步：使用真机全链路诊断屏**
   - 在应用内进入 `源管理` -> `ZLibrary` 诊断按钮，运行 `ZLibraryRealDeviceDiagnosticScreen`。
   - 查看 Step 1 ~ Step 6 到底停在哪个环节，观察 Exception StackTrace 文本。

2. **第二步：针对性修复**
   - **若是 DNS 问题**：检查 `ZLibraryDns.kt` 是否正常被 `ZLibraryHttpClient` 引用。
   - **若是 Cloudflare 人机验证/JS 盾**：考虑在 `ZLibrarySource` 引入轻量级 Android `WebView` 混淆加载/获取 Cloudflare Cookie，或通过 WebView 拦截 HTML 后再交由 Jsoup 解析。
   - **若是 DOM 选择器问题**：根据诊断屏抓取的真实 HTML，更新 `DesktopLayoutParser.kt` 或 `MobileLayoutParser.kt` 中的 Jsoup CSS 选择器（如 `.item-book`, `.book-card`）。

3. **第三步：真机实测**
   - 输入“三体”搜索，确保界面正常输出《三体》刘慈欣的书籍列表与封面。

---

## 十、修改安全边界

为保持项目的稳定与可维护性，请遵守以下安全边界：

### 1. 严格禁止修改的模块（除非有明确重构需求）
- `ui/ReaderScreen.kt` & `ui/pageturn/`：阅读器核心渲染与仿真翻页引擎。
- `data/BookRepository.kt` & `data/AppDatabase.kt`：本地数据库与书架管理。
- `data/EpubParser.kt` & `data/ComicParser.kt`：本地电子书/漫画解压与解析。

### 2. 允许自由重构/优化的模块
- `source/zlibrary/` 包下所有网络、解析、域名提供者与 Session 文件。
- `ui/source/` 包下所有诊断与书源配置界面。
- `source/impl/` 扩展书源实现。

---

`AGENTS.md` 建立完成。下一任 Agent 读取本文件后可立即无缝对接开发。
