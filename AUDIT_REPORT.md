# 第一阶段审计报告（只读，未修改任何代码）

审计对象：`C:\Users\GuanXingRen\Downloads\novel-reader (1)\novel-reader`

## 0. 项目概况

- 主源码 Kotlin：108 个文件，约 18,089 行
- 资源文件：38 个（约 5.15 MB，其中大部分是已损坏/未引用的图片）
- 测试文件：29 个（位于 `src/test`，不打包进 APK）
- 根目录 AI 过程垃圾：约 45 个临时/调试文件
- Debug APK：约 32 MB
- Release 已开启 `minifyEnabled + shrinkResources + proguard`

---

## 1. 文件使用情况报告

### 1.1 保留（核心生产代码）

| 模块 | 文件 | 说明 |
|---|---|---|
| 入口 | MainActivity.kt / MainViewModel.kt | 导航 + 全局状态 |
| 数据 | AppDatabase / BookRepository / Book / BackupManager / PreferencesManager / TtsManager | 本地持久化与阅读 |
| 解析 | EpubParser / ComicParser | EPUB/TXT/漫画导入 |
| 下载 | DownloadManager / DownloadWorker / DownloadTask* / DownloadState / DownloadProgressBroadcaster / DownloadRequest | 断点续传下载 |
| 书库 | LibraryScreen / LibraryViewModel / LibraryUiState / LibraryError / ZLibraryNativeSession / ZLibraryNodeManager / ZLibraryNodeConfig / ZLibraryCoverLoader / DownloadGlassCard / LibraryLoginDialog | 在线搜索核心 |
| 书源 | BookSource / SourceManager / SourceViewModel / SourceConfig / SourceCapabilities / SourceResult / SourceException / SearchBook / DownloadInfo / LoginCredential / AuthenticationState / JsonBookSource / SourceImporter / JsonPathResolver / storage/* | 书源插件体系 |
| ZLibrary | ZLibrarySource / ZLibraryDns / ZLibraryHttpClient / DiamWallInterceptor / EncryptedCookieJar / ZLibraryCredentialStorage / ZLibraryParserManager / 4 个布局解析器 / CoverExtractor / ZLibraryAccessChecker / ZLibraryEndpointProvider / ZLibraryDomainResolver / EndpointHealthChecker / ZLibrarySessionManager / SystemProxyResolver | 网络与解析 |
| UI | HomeScreen / BookShelfScreen / ReaderScreen / ComicReaderScreen / StatisticsScreen / SettingsTabScreen / SplashScreen / OnboardingScreen / 主题 | 主界面 |
| UI 组件 | AppErrorSnackbar / CustomButtons / CustomSwitch / MascotEmptyState / WeeklyReadingChart / StarryNightBackground / 帮助手册组件 | 通用组件 |
| 吉祥物 | MascotSpriteSheet / MascotAnimationController / 4 个动画实现 | 动画系统 |
| 资源 | roxy_*.xml（5 个吉祥物向量）、ic_launcher 全套、splash_quotes.xml、strings.xml、themes.xml | 界面资源 |

### 1.2 可删除（主源码中已无生产引用）

| 文件 | 依据 |
|---|---|
| `ui/EndpointDiagnosticScreen.kt` | 全项目仅自引用 |
| `ui/source/SourceNetworkDiagnosticScreen.kt` | 入口已移除，仅自引用 |
| `ui/source/ZLibraryDebugTestScreen.kt` | 入口已移除，仅自引用 |
| `ui/source/ZLibraryRealDeviceDiagnosticScreen.kt` | 入口已移除，仅自引用 |
| `library/LibraryFirstLaunchState.kt` | 无任何引用 |
| `ui/components/HazeProgress.kt` | 0 引用 |
| `ui/mascot/MascotState.kt` | 仅自引用 |
| `ui/mascot/MascotAnimation.kt`（接口） | 仅自引用，动画实现未实现该接口 |
| `source/zlibrary/ZLibraryClient.kt` | 仅被待删调试屏和 ZLibrarySource 中从未使用的 `client` 属性引用 |
| `source/zlibrary/ZLibraryStatus.kt` | 仅被 ZLibrarySource.getZLibraryStatus() 使用，而该方法无任何调用方 |
| `source/zlibrary/ZLibrarySource` 中 `getZLibraryStatus / validateSession / client` 字段 | 生产路径未调用 |
| `source/impl/MockBookSource.kt` | 主源码无引用，仅测试引用（建议迁移到 src/test 或随测试保留） |
| `source/zlibrary/ZLibraryParser.kt` | 主源码无引用，仅测试引用（同上） |

### 1.3 风险文件（可能影响功能，需确认后处理）

| 文件 | 风险 |
|---|---|
| `res/drawable/anime_mascot_chibi.jpg` | 被 ReaderScreen 引用（默认封面占位），但文件头损坏，无法解码 |
| `res/drawable/splash_1/2/3.jpg`、`cozy_room_banner.jpg`、`empty_bookshelf_cat.jpg`、`user_roxy_icon.png` | 全部未引用且文件头损坏 |
| `source/zlibrary/RemoteEndpointProvider.kt` + `RemoteEndpointConfig.kt` | CONFIG_URL 仍是 `example.com`，实际为死代码；仅被 ZLibraryEndpointProvider 引用 |
| `source/zlibrary/EndpointHealthChecker.kt` | 仍保留真实搜索检测，但主搜索路径已改走 WebView；仅节点管理间接使用 |
| `.env.example` / secrets 插件 | 全项目无 BuildConfig / getSecret 引用，插件可整体移除 |
| `gradle/libs.versions.toml` | retrofit / converter-moshi / logging-interceptor / lottie / firebase 等大量未使用项 |

### 1.4 根目录 AI 过程垃圾（第六阶段删除）

`fix_*.py`（22 个）、`patch_*.py`（5 个）、`solve_*.py`（2 个）、`test_*.py`（4 个）、`print_pow.py`、`process_icon.py`、`patch_grid.kt`、`patch_home.kt`、`1lib_result.html`、`1lib_search.html`、`first.html`、`iframe.html`、`out.html`、`singlelogin_search.html`、`cookies.txt`、`1lib_verify_url.txt`、`diagnosis.md`、`finish.md`、`result.md`、`result_fix.md`、`metadata.json`

保留：`AGENTS.md`、`README.md`、`gradle.properties`、`settings.gradle.kts`、`build.gradle.kts`、`local.properties`、`debug.keystore`、`.env.example`（若移除 secrets 插件则一并删除）

---

## 2. 架构依赖图

```text
MainActivity (NavHost)
  ├─ SplashScreen / OnboardingScreen
  ├─ HomeScreen（Tab: 书架/书库/统计/设置）
  │    ├─ BookShelfScreen ── MainViewModel ── BookRepository ── Room(AppDatabase)
  │    ├─ LibraryScreen ── LibraryViewModel ── SourceManager
  │    │     ├─ ZLibraryNativeSession(WebView) ── ZLibraryParserManager ── 布局解析器
  │    │     ├─ ZLibraryNodeManager ── SharedPreferences
  │    │     └─ DownloadManager ── WorkManager ── DownloadWorker ── BookRepository
  │    ├─ StatisticsScreen ── MainViewModel
  │    └─ SettingsTabScreen ── MainViewModel / SourceViewModel / BackupManager
  ├─ SourceManagementScreen ── SourceViewModel ── SourceManager
  │    ├─ ZLibraryNodeManagementScreen ── ZLibraryNodeManager / EndpointHealthChecker
  │    └─ ZLibraryLoginDialog / LibraryLoginDialog
  ├─ ReaderScreen / ComicReaderScreen ── MainViewModel
  └─ 吉祥物 Overlay（全局）

SourceManager
  ├─ ZLibrarySource ── ZLibraryHttpClient / ZLibraryDns / ZLibrarySessionManager
  │                    └─ ZLibraryParserManager
  └─ JsonBookSource ── OkHttp + JsonPathResolver
```

### 2.1 依赖结论

- 未发现循环依赖。
- 无意义的中间层/重复实现：
  1. `ZLibraryClient`（旧客户端包装）已无人调用。
  2. OkHttp 网络层存在 3 个独立 client 实例：`ZLibraryHttpClient`、`EndpointHealthChecker`、`ZLibraryNodeManager.scrapeNodes`；建议统一收敛。
  3. `ZLibraryEndpointProvider / ZLibraryDomainResolver / RemoteEndpointProvider` 三层存在功能重叠（节点解析），且主搜索路径实际只使用 `ZLibraryNodeManager + ZLibraryNodeConfig`。
  4. 下载进度状态只有 `DownloadProgressBroadcaster` 一个数据源（状态管理无重复），但更新频率过高（见 3.3）。

---

## 3. 性能风险（审计级结论，第二阶段细修）

### 3.1 主线程阻塞

- 未发现 `runBlocking`、`Thread.sleep`（主线程）、`allowMainThreadQueries`、`GlobalScope`。
- 所有网络调用均在 `withContext(Dispatchers.IO)` 内；Room suspend 查询由 Room 自带 IO 执行器处理。
- `DownloadWorker` 的 `Thread.sleep(2000)` 位于 `Dispatchers.IO` 内（可改为 delay，低风险）。

### 3.2 Compose 重组

- 首页/书库/设置均为大文件（HomeScreen 56KB、LibraryScreen 50KB、SettingsTabScreen 42KB），存在粗粒度 `collectAsState`。
- `DownloadProgressBroadcaster.states` 是全局 `StateFlow<Map>`，LibraryScreen 整体收集；任何一本书的进度变化都会触发整个书库列表重组（见 3.3）。
- 图片列表未显式使用 `key()`/稳定数据类约束，存在列表项无谓重组风险。

### 3.3 下载进度更新频率（最高优先级）

`DownloadWorker` 每读取 8KB 就调用一次 `DownloadProgressBroadcaster.updateState()`，即每本书下载时每秒可能更新几十上百次，导致：
- 全列表重组、滑动掉帧；
- StateFlow 频繁整体替换 Map；
- DB 之外不必要的状态写放大。

建议：按时间（≥300ms）或进度增量（≥1%）节流，DB 进度只在暂停/完成/失败时写。

### 3.4 数据加载

- MainViewModel 以 Flow 收集 Room 数据，具备缓存基础；需确认首页四个 Tab 是否每次进入重复查询。
- 书库目前进入时只创建隐藏 WebView 会话（不再自动节点扫描），符合“用户点击搜索时才执行”的目标；`LibraryViewModel.init` 仍会初始化 SourceManager（轻量）。

---

## 4. APK 体积（第四阶段）

- 可删除资源（未引用且损坏）：`splash_1/2/3.jpg`、`cozy_room_banner.jpg`、`empty_bookshelf_cat.jpg`、`user_roxy_icon.png`，约 7.8 MB 原始体积。
- `anime_mascot_chibi.jpg` 需替换为有效图片或改用程序化占位。
- Release 已启用 R8 + 资源收缩；删除上述文件后 APK 预计下降数 MB。
- `src/test` 不会被打包，保留。

---

## 5. 依赖清理（第五阶段）

可移除：
- `retrofit`、`converter-moshi`（无 retrofit 代码）
- `logging-interceptor`（无 HttpLoggingInterceptor 使用）
- `secrets-gradle-plugin` 及 `.env.example`（无 BuildConfig/Secret 使用）
- `lottie-compose`、`firebase-*`、`google-services`（已注释，直接删除版本声明）

保留：
- okhttp / moshi / jsoup / coil / room / work / security-crypto / navigation / compose 全套 / coroutines / roborazzi（测试）

---

## 6. 执行顺序建议

1. 第二阶段：下载进度节流 + 列表重组优化（性能收益最大）
2. 第三阶段：书库懒加载确认（当前已基本达标）
3. 第四阶段：删除损坏/未引用资源 + 图片占位替换
4. 第五阶段：清理依赖与 secrets 插件
5. 第六阶段：删除根目录垃圾与未引用源码
6. 第七阶段：构建缓存清理（保留 wrapper/源码/配置）

> 所有删除动作均保留 git 之外的可恢复备份（移动到回收站/backup 目录），并在每阶段后编译 + 真机验证核心功能。
