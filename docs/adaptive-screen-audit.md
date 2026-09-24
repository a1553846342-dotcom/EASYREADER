# 多形态屏幕适配体检报告

> 范围：小屏(≤360dp) / 基准 / 平板 / 折叠展开 / 横屏 / 分屏
> 判据：**只在确实出问题处改**；禁止靠缩字号、缩间距来"挤进小屏"（撞无障碍红线）；
> 禁止降低视觉还原度。

## 一、体检方式

用 `adb shell wm size` + `wm density` 把同一台模拟器切成五种形态，
每种形态冷启动后截 7 个页面（书架 / 书库 / 统计 / 设置 / 阅读 / 居中弹窗 / 底部面板），
共 **35 组 before/after 截图**落在 `_review/adapt_*_{before,after}.png`。

| 形态 | wm size | wm density | 实际宽度 |
|---|---|---|---|
| 小屏 small | 720x1280 | 320 | 360dp |
| 基准 base | 1080x2400 | 440 | 393dp |
| 平板 tablet | 1920x2400 | 320 | 960dp |
| 横屏 land | 2400x1080 | ~420 | 914dp |
| 分屏 split | 1080x1200 | ~420 | 411dp |

工具脚本：`_review/adapt_shot.py`、`_review/adapt_shot2.py`、`_review/adapt_contact.py`、
差异量化 `_review/adapt_cmp.py`。

⚠️ **before/after 的像素差不能用来判断"改没改代码"**：App 有星空背景动画、
页面内容会随状态变化，实测连"基准形态"自身的前后两张都差 47%。
本轮是否改动以**代码与资源文件**为准（见第三节）。

## 二、体检结论

**五种形态下没有发现需要改数值的布局问题**——没有内容溢出屏幕、没有被截断的控件、
没有被遮挡的关键操作。观感在五种形态下都成立。

这一结论与项目既有机制吻合：`AdaptiveSpec` 已提供 dialog 560 / sheet 560（横屏 840）/
page 720 三档断点，宽屏一律"居中限宽"而不是拉伸满屏，因此平板与折叠展开不会把内容拉散；
小屏侧则因为项目从未使用写死的绝对 px 定位，360dp 下也不挤。

## 三、本轮实际改动：补机制，不改数值

既然实测无需调参，本轮做的是**把调参能力从 Kotlin 常量下沉到资源**，
让以后按形态差异化不必再改代码。

### 新增资源
| 文件 | 内容 |
|---|---|
| `res/values/dimens.xml` | `adaptive_dialog_max_width` 560dp、`adaptive_sheet_max_width` 560dp、`adaptive_sheet_max_width_landscape` 840dp、`adaptive_page_max_width` 720dp |
| `res/values-sw600dp/dimens.xml` | 同上数值（≥600dp：折叠展开/小平板的覆盖入口） |
| `res/values-sw840dp/dimens.xml` | 同上数值（≥840dp：平板/横屏大窗的覆盖入口） |

三个目录当前**数值完全一致**——这是有意为之：本轮没有需要差异化的数值，
建立覆盖点是避免将来临时建目录。

### 代码
`ui/adaptive/AdaptiveSpec.kt`（4.6KB → 7.8KB）：
- 新增 `AdaptiveSizing` 数据类 + `rememberAdaptiveSizing()`：组合期按当前
  `LocalConfiguration` 从 res/dimen 读四个宽度，资源缺失时回退 Kotlin 常量，永不抛异常。
  以 configuration 为 key 记忆，折叠开合/分屏/旋转（`configChanges` 已声明不重建）会自动重算。
- 新增 `Modifier.adaptivePageWidth()`、`AdaptivePageContent()`：全屏滚动页
  （设置/统计/缓存管理）在宽屏上居中限宽 720dp，手机全宽不变。
- 保留 `adaptiveDialogWidth()` / `adaptiveSheetWidth()` / `AdaptiveSheetContent()` 原语义。

⚠️ **同步纪律**（写进两处注释）：`AdaptiveSpec` 常量是非组合上下文（Modifier 工厂）的
编译期兜底，与 res/dimen 两条路径数值必须一致，改任一侧要同步另一侧。

## 四、门禁

- `python .workbuddy/build.py check` → **BUILD SUCCESSFUL**
- `python .workbuddy/audit_shelf.py` → **68 项通过，0 项失败**

## 五、遗留与未覆盖

1. **真实折叠屏开合**（`smallestScreenSize` 触发）没能验：`wm size` 只能改静态分辨率，
   模拟不出铰链开合的连续变化。需真机或云真机补。
2. **厂商 ROM 的分屏行为**（华为/三星多窗口）模拟器复现不了，需云真机补。
3. 本次未做真机回归——最终串行 QA 阶段统一补（届时设备独占，避免多 agent 争用导致的误判）。
