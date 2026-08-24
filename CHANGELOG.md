# Changelog

All notable changes to Ciallo 阅读 are documented in this file.

## [Unreleased]

### ✨ 新功能

- **自动滚屏阅读模式**：顶栏一键开启，60fps 平滑滚动，浮停指示器点击停止；与 TTS 听书互斥
- **目录自动定位**：打开章节目录自动跳转到当前阅读位置（千章大书秒定位）
- **字体切换**：阅读排版面板支持 默认/衬线/黑体/等宽 四种字体一键切换
- **缓存管理页**：设置→存储管理→缓存管理；总览大字+比例色带图例+五分类逐项删除+一键清理全部
- **每日阅读目标**：统计页目标环可视化进度，±15 分钟步进器自定义目标值（15–480 分钟），持久化保存
- **阅读报告分享**：统计页一键分享本周/月/年阅读报告到社交平台（总时长/天数/连续打卡/目标完成度）
- **FluidSlider 流体滑条**：Ramotion FluidSlider 风格——按下白色气泡 Overshoot 弹出、metaball 液态连接、数值显示在气泡内

### 🔧 Bug 修复

- 开关弹性动画丢失：移除覆盖动画的 snapTo 调用，Animatable 初始值跟随 checked 状态
- 滑条液态黏连断裂：底池巨圆圆心位置修正至 vOff + botCD/2（原版布局）
- 滑条两端矩形凹口：metaball 连接点钳位到胶囊直线段范围内
- 滑条窄容器拖动反转：手势区 maxMove 增加 coerceAtLeast(1f) 保护
- 进度文本除零：chapters.size 为空时不再产生 Int.MAX_VALUE%
- ChromaFlow 巡游动画静止：phase 动画值现在通过 rotate() 实际作用于渐变旋转
- FluidSlider View 白色背景：AndroidView 嵌入时设置透明背景
- 排版预览白框：pageBg 从不透明白改为半透明 onSurface

### 🎨 视觉优化

- **SquishyToggleSwitch**：动画提速 1300ms → 550ms（保持弹性四阶段视觉语言）
- **轨道颜色过渡**：1200ms → 250ms（即时响应感）
- **FluidSlider 气泡**：新增底部微阴影（立体感）+ 左上高光渐变（泡泡反光）+ 薄色环设计
- **阅读器顶栏重构**：定制 Column 替代 TopAppBar，章节标题 + 进度副标 + hairline 分割线
- **阅读器底栏重构**：与顶栏同风格（bgColor 背景 + navigationBarsPadding）
- **排版面板滑条**：字号/行距/边距三个 Slider 统一 MintPrimary 主题色
- **TTS 听书按钮**：颜色从 MaterialTheme.primaryContainer 改为 barContentColor 自适应
- **章节导航滑条**：thumb/active/inactive 轨道色跟随阅读主题实时适配

### 🌐 全局一致性

- **IconButton → AppIconButton**：全局审计完成（ComicReader/OnlineComic/HomeScreen/AppErrorSnackbar/LibraryHelpBottomSheet 等 5 处残留修复）
- **Switch 统一**：设置页/书源管理/排版面板全部替换为 SquishyToggleSwitch 或 AppLiquidSwitch
- **触觉反馈全覆盖**：clickableWithFeedback 补充 HapticFeedbackType.TextHandleMove（29+ 调用点受益）

### ♿ 可访问性

- FluidSlider 新增 customActions：TalkBack 用户可通过无障碍操作菜单 ±5% 步进调节
- SquishyToggleSwitch 补充 Role.Switch + toggleableState 语义
- 所有 contentDescription=null 均为装饰性图标（符合 Material 规范）

### 📚 第三方库集成

| 库 | 来源 | 用途 |
|---|---|---|
| pagecurl | [oleksandrbalan/pagecurl](https://github.com/oleksandrbalan/pagecurl) | 阅读器 SIMULATE 翻页引擎 |
| SquishyToggleSwitch | [Swapnil-J-Patil/Switch-Animation](https://github.com/Swapnil-J-Patil/Switch-Animation-Jetpack-Compose/) | 弹性挤压开关 |
| ChromaFlow | [M1n9yu23/ChromaFlow](https://github.com/M1n9yu23/ChromaFlow)（概念参考） | 边缘光弧巡游效果 |
| ShimmerFy | [tusharhow/Shimmerfy](https://github.com/tusharhow/Shimmerfy)（概念参考） | 卡面珠光微光层 |
