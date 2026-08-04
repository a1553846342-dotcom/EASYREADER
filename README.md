# EASYREADER · CIallo阅读

> 一个基于 Kotlin + Jetpack Compose 的现代 Android 电子书阅读器。
> 支持 TXT / EPUB / 漫画阅读、多源在线书库搜索、断点续传下载与书架管理。

当前版本：**v0.20**

---

## ✨ 核心功能

- **阅读器**
  - TXT / EPUB 解析阅读
  - CBZ / CBR 漫画阅读
  - 仿真翻页动画（2.5D / 3D）
  - 阅读进度保存、书签、高亮、TTS 朗读
- **在线书库**
  - Z-Library 原生搜索（隐藏 WebView 会话，可过 DiamWall 验证）
  - 登录 / Cookie 会话管理
  - 节点管理：默认节点 + 官网/备用入口自动扒取 + 自定义节点 + 一键检测切换
  - 自定义 JSON 书源：粘贴 / 文件导入、字段规则、headers/body、数组通配解析
- **下载与书架**
  - WorkManager 后台断点续传下载
  - 下载进度节流与毛玻璃下载卡片
  - 下载完成后自动校验并导入书架
- **体验**
  - 深色 / 护眼 / 自定义主题配色
  - 阅读统计、连续阅读天数、周报图表
  - 吉祥物动画反馈、开屏海报、帮助手册

---

## 🆕 v0.20 更新日志

从在线书库上线到现在的关键进程：

### 1. 在线书库与 Z-Library 搜索
- 新增原生书库页：书源选择、搜索框、结果列表
- 采用隐藏（游离）WebView 作为会话引擎，解决 DiamWall / Cloudflare 人机验证
- 支持软件内登录（账号密码注入 / Cookie 粘贴），Cookie 全局持久化
- 搜索稳定化：未找到立即结束、页面无响应自动重试、15 秒硬超时兜底，杜绝无限转圈

### 2. 节点管理
- 默认节点保留（1lib.sk），新增官网入口、备用入口一、备用入口二
- 支持从发布页自动扒取最新三个入口，确认后一键替换
- 每个节点可“检测”（真实搜索并解析），可“使用”即时切换并持久化
- 支持用户自定义节点，重启后自动恢复所选节点

### 3. 自定义 JSON 书源
- 支持粘贴 JSON 与本地文件导入，卡片管理（启用 / 停用 / 删除）
- 兼容 fields 平铺与嵌套两种写法，支持 headers、POST body、{keyword}/{id} 占位符
- JSON 路径解析支持 `books[].field` 数组通配遍历
- 搜索结果自带下载链接时可直接下载，不强制依赖详情规则

### 4. 下载与书架
- 下载任务自动导入书架，支持暂停 / 继续 / 取消 / 重试
- 断点续传（Range 请求），失败自动重试
- 下载进度节流（≥300ms 或 ≥1%），列表不再因进度刷新而卡顿
- 毛玻璃下载卡片：封面、书名、速度、剩余大小、动态进度

### 5. 封面与资源格式
- 封面优先使用可直连的 cdn-zlib.sk 域名，兼容 data-src / srcset / background-image
- 无封面时显示渐变占位（书名首字 + 格式）
- 结果卡片展示真实资源格式（PDF / EPUB / MOBI 等），不再显示“无简介”

### 6. 性能优化
- 阅读器滚动进度 1.5 秒防抖保存，退出页面兜底保存，不再每帧写数据库
- 搜索结果 Jsoup 解析移到后台线程，出结果不再掉帧
- 书架呼吸动画 30fps 驱动（视觉不变，绘制开销减半）
- 隐藏 WebView 空闲暂停、离开页面销毁并回收渲染进程
- 毛玻璃背板按需启用（仅弹窗 / 下载卡片可见时）
- 封面加载器全局单例、节点健康检查复用同一 OkHttpClient
- 下载进度、列表重组、数据流收集全面节流与隔离

### 7. 工程清理
- 完整项目审计（AUDIT_REPORT.md）
- 移除诊断页面、死代码、AI 开发垃圾文件与损坏资源
- 清理未使用依赖（retrofit / logging-interceptor / secrets 插件等）
- Debug APK 从约 32MB 降至约 23MB

### 8. 视觉与细节
- 应用图标恢复并更新
- 开屏海报支持自定义（file:// 解码修复），默认海报程序化绘制
- 帮助手册窗口四角圆角、顶栏不再模糊
- 全部功能动画（Tab 切换、吉祥物、翻页、玻璃卡片）原样保留

---

## 🛠 技术栈

- Kotlin 2.0.21 + Jetpack Compose（Material 3）
- OkHttp 4.12 + Jsoup + Moshi + Coil 2.7
- Room 2.6 + WorkManager 2.9
- Navigation Compose、Haze（毛玻璃）、Coroutines Flow

## 🚀 构建

环境要求：

- Android Studio（或命令行 Gradle）
- JDK 17+、Android SDK（compileSdk 35 / minSdk 24 / targetSdk 35）

命令行构建：

```bash
gradle :app:assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

> 首次构建请确保 `local.properties` 中配置了 `sdk.dir`（该文件不参与版本管理）。

## 📁 目录结构

```text
app/src/main/java/com/example/
├── MainActivity.kt          # 入口与导航
├── data/                    # Room 数据库、书籍解析、备份、偏好
├── download/                # 下载队列、断点续传 Worker
├── library/                 # 在线书库、隐藏 WebView 会话、节点管理
├── source/                  # 书源插件体系、Z-Library 引擎、JSON 书源
└── ui/                      # 全部 Compose 界面（书架/书库/阅读器/设置/统计）
```

---

**v0.10 → v0.20：从“基本可用”到“流畅、干净、可维护”。**
