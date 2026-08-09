# Ciallo阅读 (EASYREADER)

> 一个基于 Kotlin + Jetpack Compose 的现代 Android 电子书阅读器。
> 支持 TXT / EPUB / 漫画阅读、多源在线书库搜索、断点续传下载与书架管理。
> 当前版本：**v0.90**

---

## 核心功能

- **阅读器**
  - TXT / EPUB 解析阅读
  - CBZ / CBR 漫画阅读
  - 3D / 2.5D 仿生翻页动画
  - 阅读进度保存、书签、夜间模式、TTS 朗读
  - 在线/本地漫画阅读器：双指捏合缩放、双击放大还原、一指控缩放
- **在线书库**
  - Z-Library 原生搜索（隐藏 WebView 会话，可过 DiamWall 验证）、登录 / Cookie 管理
  - 节点管理：默认节点 + 官网 / 备用入口自动扒取 + 自定义节点 + 一键检测切换
  - MangaDex 漫画源
  - ehentai 漫画源：图片页直抓 + H@H 图床 Cronet 加载与本地缓存，失败自动重试
  - Venera JS 漫画源：拷贝漫画、comick、漫蛙吧、goDa、漫画人、MYCOMIC 等
  - 聚合搜索：每出一个源立即展示，渐变毛玻璃胶囊分隔，失败显示“链接超时 / 无结果”
  - 搜索历史（点击搜索框展开 / 收起，窗帘式动画）
  - 自定义 JSON 书源：粘贴 / 文件导入，兼容 Legado 规则与 JSON API
  - 搜索卡片显示真实作者、作品编号（#jm号 / ehentai gid / nhentai id）与语言
- **下载与书架**
  - 漫画下载由应用级任务中心管理：切换页面不中断，失败保留可重试，3 路并发下载
  - 下载断点续传，支持暂停 / 继续 / 取消
  - 毛玻璃下载卡片：封面、书名、进度、速度、剩余大小
  - 下载完成自动校验并入库书架
- **UI / 体验**
  - 全局四变体液态玻璃按钮：Primary（实心渐变+玻璃高光+gel-press）/ Secondary（色调填充）/ Tertiary（幽灵描边）/ Destructive（语义色）
  - 液态玻璃开关（书源管理、设置页统一）
  - 悬浮收缩玻璃底部导航栏：滚动时隐藏文字标签并收缩，图标永远可点，选中指示器弹簧位移，颜色跟随主题主色调平滑过渡
  - 亚克力立牌质感弹窗（径向遮罩 + 虹彩描边 + 颗粒噪点 + 双层阴影）
  - 主题配色方案（主色 / 强调色实时联动，Color State Morph 过渡）
  - 吉祥物动画、开屏海报、阅读统计图表
  - 帮助手册与书源管理

---

## v0.35 → v0.90 主要更新

### 全局 UI 现代化
- 新增统一按钮系统 `AppActionButton`：四变体结构分层（渐变 / 色调填充 / 描边 / 语义色），全部颜色取自主题 token，disabled 统一 0.4 透明度，内置 loading 转圈与 gel-press 按压动效
- 所有操作按钮迁移：书源管理、节点管理、章节页、登录弹窗、下载中心、引导页、书架、设置页
- 底部导航栏重做：悬浮液态玻璃胶囊、滚动收缩（68dp→52dp、隐藏文字、左右留白增大）、选中指示器弹簧追踪、Tab 栏颜色跟随设置主色调平滑过渡
- 设置页开关（纯净模式 / 夜间模式 / 护眼滤镜 / 高级内容）统一为液态玻璃开关
- 亚克力立牌弹窗体系：新建分类、选择分类、阅读统计长按、下载管理、登录弹窗等统一质感
- 搜索历史窗帘式展开/收起动画；ChasingDots 加载动画随强调色变化
- Play / Pause Morph 应用于漫画下载暂停/继续；Color State Morph 应用于主题色选择与实时联动

### 稳定性修复
- 修复液态玻璃跨窗口闪退：每个 Dialog / 底部弹层使用独立 LiquidGlass Provider（`DialogLiquidGlass`），不再复用主窗口 Provider，彻底解决 “layouts are not part of the same hierarchy” 崩溃
- Z-Library 节点管理“扒取节点”闪退修复（替换弹窗内玻璃按钮跨窗口问题）
- 阅读器黑屏替换为加载动画 + 失败重试；本地缓存图片 file:// 闪退修复
- 书源切换、聚合搜索、下载任务稳定性优化

### 性能与体积
- 原生库压缩打包（`useLegacyPackaging`），cronet/quickjs 从 10.7MB → 7.6MB
- R8 full mode + 资源收缩 + 仅 arm64-v8a ABI + 中英文资源裁剪
- 清理临时文件、调试产物与无用缓存

---

## 技术栈

- Kotlin 2.0.21 + Jetpack Compose（Material 3）
- OkHttp + Jsoup + Moshi + Coil
- Room + WorkManager + DataStore 偏好
- Navigation Compose + Haze（毛玻璃）
- QuickJS（Venera JS 漫画源）+ Cronet（浏览器级 TLS / H@H）
- KMPLiquidGlass（backdrop）+ Abdullajon1881/LiquidGlass（液态玻璃按钮/开关）
- FlexibleBottomSheet + compose-animations（Morph 动效）

## 构建

环境要求：
- Android Studio（或命令行 Gradle）
- JDK 17+、Android SDK（compileSdk 35 / minSdk 24 / targetSdk 35）

正式版构建（R8 混淆 + 资源压缩 + 原生库压缩）：

```bash
gradle :app:assembleRelease
```

APK 输出：`app/build/outputs/apk/release/app-release.apk`（约 7.6MB）

调试版构建：

```bash
gradle :app:assembleDebug
```

> 注意：release APK 仅包含 `arm64-v8a` 原生库以极致瘦身；如需兼容其它 ABI，请调整 `app/build.gradle.kts` 中 `defaultConfig.ndk.abiFilters`。首次构建请确认 `local.properties` 中已配置 `sdk.dir`（该文件不参与版本管理）。

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

## 致谢

- 翻页与卷角效果参考 GitHub `pagecurl`、`PTQFlipper`
- 缩放组件完整引入 [usuiat/Zoomable](https://github.com/usuiat/Zoomable)（Apache-2.0）
- Venera JS 运行时与社区漫画源来自 [venera-app/venera-configs](https://github.com/venera-app/venera-configs)（GPL-3.0）
- ehentai 取图思路参考 [delta-comic/delta-comic-plugin-ehentai](https://github.com/delta-comic/delta-comic-plugin-ehentai)（GPL-3.0）
- 液态玻璃引擎：[Abdullajon1881/LiquidGlass](https://github.com/Abdullajon1881/LiquidGlass)、[Kashif-E/KMPLiquidGlass](https://github.com/Kashif-E/KMPLiquidGlass)
- 底部弹窗：[skydoves/FlexibleBottomSheet](https://github.com/skydoves/FlexibleBottomSheet)
- 动效参考：[skydoves/compose-animations](https://github.com/skydoves/compose-animations)、[commandiron/ComposeLoading](https://github.com/commandiron/ComposeLoading)
