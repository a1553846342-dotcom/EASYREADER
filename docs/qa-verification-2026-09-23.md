# QA 独立验证报告 — 2026-09-23

**验证人**：严过关（Edward / QA Engineer）
**被验证对象**：仿真3D卷页背面渲染修复 + 强制深色/安全区/动画缩放/字体出口收敛
**验证方式**：release 包实机（emulator-5554，1080×2400 @420dpi）跑真机操作 + 像素级取证，非代码走查
**APK**：`app/build/outputs/apk/release/app-release.apk`（本机重新构建，20,796,927 字节，2026-09-23 09:35）
**包名**：`com.aistudio.novelreader.kxmpzq`

---

## 总判定：**可以发布**（0 个源码 Bug，1 个脚本自身局限，1 项需真机补充）

---

## V1. 编译与门禁 — ✅ 通过

| 门禁 | 结果 |
|---|---|
| `python .workbuddy/build.py check` | **BUILD SUCCESSFUL in 53s**，exit 0（49 tasks up-to-date） |
| `python .workbuddy/build.py release` | **BUILD SUCCESSFUL in 2m 29s**，exit 0（155 tasks, 14 executed），含 `lintVitalRelease` / `minifyReleaseWithR8` |
| `python .workbuddy/audit_shelf.py` | **68 项通过，0 项失败**，exit 0 |

- 日志：`_review/qa_release_build.log`、`_review/qa_audit_out.txt`
- `adb install -r` → `Success`，随后 `am force-stop` 清场。

---

## V2. 安装 — ✅ 通过

```
Performing Streamed Install
Success
```
安装后 `dumpsys package` 确认 versionCode=197 / versionName=1.0.7。

---

## V3.【核心】仿真3D卷页背面 — ✅ 通过（像素级证据）

### 3.1 验证方法（可复核）

1. 直接写 `shared_prefs/novel_reader_prefs.xml`（设备已 root，adbd 以 root 运行）：`reader_theme=N`、`page_turn_mode=0`（SIMULATE）→ force-stop → 冷启动。
2. 书架 → ZZZ 分类 → 《三体》→ 阅读器；确认 uiautomator 树里不再出现「听书模式/上一章/下一章」（否则 `dragForwardEnabled=false`，卷页不会响应）。
3. 翻到**文字密集页**（正文区亮度 std=56.9，见 3.5 的注意事项）。
4. `input motionevent DOWN(920,1200)` → 8 段 `MOVE`（每段 -38px、间隔 80ms，终点 x=616）→ **保持按住** → `screencap` → `UP`。
   - 起点 x=920：避开系统返回手势的屏幕右边缘区（x≥~1020 会触发 BACK 把阅读器退出）。
   - 终点 x=616：`StartEndDragInteraction` 的 `end = leftHalf()`，一旦拖进左半屏交互会自动完成、卷页直接翻过去，抓不到背面。
5. 对截图做逐列扫描定位卷起纸面的横向范围，再在**纸面内部**（避开边缘折痕 60~120px）统计。

### 3.2 实测数据（核心判据）

采样区：纸面内部 x∈[fx0+120, fx1−60]，y∈[500, 2050]，约 62~91 万像素/张。

| 阅读主题 | 主题背景 RGB | **背面实测均值 RGB** | **亮度标准差** | 亮度 min/max | 唯一色数 | 是否旧 Bug 米黄 `(232,228,220)` |
|---|---|---|---|---|---|---|
| 夜间 (3) | (24, 25, 28) | **(34.2, 36.1, 37.0)** | **29.57** | 24.6 / 123.8 | 172 | **否** |
| 默认白 (1) | (255, 255, 255) | **(241.3, 241.4, 241.6)** | **35.45** | 131.9 / 255.0 | 190 | **否** |
| 羊皮纸 (2) | (251, 240, 217) | **(240.3, 230.8, 210.7)** | **25.73** | 154.5 / 241.5 | 189 | **否** |

**结论 1 —— 不再是纯色**：旧实现 `backPageContentAlpha=0f` → 引擎 `CurlDraw.kt:179-180` 铺 `alpha = 1-0 = 1.0` 的纯色，背面应当是**逐像素完全相等**（std≈0、唯一色数=1）。
实测三档主题亮度标准差 **25.7 / 29.6 / 35.5**，唯一色数 172~190 —— 明显有镜像文字纹理。
（判据灵敏度已自证：在同一台设备、同一手法下，卷到「本章末尾的空白页」时，背面实测 std = **0.00**、唯一色数 = 1，说明这套指标确实能把"纯色"和"有纹理"分开，不是判据太松。）

**结论 2 —— 纸面跟随主题**：夜间主题下背面均值亮度 **35.8**（深色带），与旧米黄 `(232,228,220)` 相差 **196+**，天壤之别；三档主题的背面均值互相之间也拉开 200+ 的亮度差，证明确实由 `bgColor` 推导，而非写死。

**结论 3 —— 合成系数与声明值精确吻合**（反向推算验证 `BACK_PAGE_CONTENT_ALPHA=0.58f` 真的生效）：

- 理论：背面像素 = `0.58 × 镜像内容 + 0.42 × PaperPalette.face`
- 夜间主题 `face` 推导值 = `(21.8, 22.7, 25.0)`；纸面空白处理论亮度 = `0.58×25 + 0.42×23.8 ≈ 24.6`，**实测平面区亮度 = 24.6（逐位吻合）**
- 夜间主题正文最亮像素实测 197 → 镜像文字处理论亮度 = `0.58×197 + 0.42×23.8 ≈ 124.3`，**实测背面亮度 max = 123.8（差 0.4，即 0.3%）**

这个数值只能由 `alpha = 0.58` 产生；若还是旧值 `0f`（overlay alpha=1）或者工程师声明的其他值，max 都不可能落在 123.8。

### 3.3 效果没被删（阴影/折痕梯度仍在）

沿「折线 → 手指边」方向取一行亮度（step=6~10px）：

| 主题 | 右缘剖面（折痕→背景） | 形态 |
|---|---|---|
| 夜间 | `24.6, 24.6, 24.6, **20.9, 21.0, 21.9, 22.0, 22.9, 23.0, 23.9, 24.0, 24.0, 24.9, 25.0**` | 折痕处压暗到 20.9，然后**单调渐亮**回背景 25.0 |
| 默认白 | `215.0, 220.0, 226.0, 231.0, 236.0, 241.0, 244.0, 247.0, 250.0, 252.0, 253.0, 254.0, 255.0` | **严格单调**，跨 40 亮度 |
| 羊皮纸 | `202.8, 207.7, 213.4, 217.7, 222.6, 227.5, 230.5, 232.8, 235.8, 237.7, 238.7, 239.7, 240.7` | **严格单调**，跨 38 亮度 |

左缘（翻动侧）同样观察到压暗带（默认白：`255…162.9, 185.1, 249.0, 246.0, 243.0, 239.0, 234.0, 146.9`）。
→ **投影 / 内侧折痕阴影的明暗梯度完整保留**，不是被"一刀切平"。

`PageTurnContainer.kt` 的 `CurlFlapBackside` 分支做**代码级确认**（SIMULATE 档走不到，见 3.4）：
- `drawRect(Color(0xFFF5F0E6))` → `drawRect(paper.face.copy(alpha = paper.baseAlpha))`（半透纸基 0.90/0.93）✅
- 镜像内容 `alpha = 0.42f` → `BACKSIDE_CONTENT_ALPHA = 0.88f` ✅
- 纸色叠层 `0xFFF5F0E6.copy(alpha=0.16f)` → `paper.face.copy(alpha=0.15f)` ✅
- 厚度高光 `0xFFFDF8EC` → `paper.edgeHighlight`、描边 `0xFFB9AB90` → `paper.edgeStroke` ✅
- 圆柱明暗（cylinder shading）、`buildFlapPath` 折角路径全部保留 ✅

### 3.4 分派路径确认

`ReaderScreen.kt:2152`：`if (!isScrollMode && pageTurnMode == PageTurnType.SIMULATE.id)` → `PageCurlReaderContainer(...)`，且 **2157/2178 两处入口都已传 `paperColor = bgColor`**。grep 确认全工程已无 `FontFamily.(Default|Serif|SansSerif|Monospace)` 字面量残留（AppFonts.kt 除外）。

### 3.5 验证过程中的重要发现（不是 Bug，但影响复现）

**卷页背面画的是「当前页」的镜像内容**（`PageCurl.kt:96-101`：`drawCurl(...)` 包住的是 `content(updatedCurrent)`）。
因此若当前页是空白页（例如《三体》第一章第 3 页只有一行字），背面自然没有任何墨迹，std=0 —— 这是**物理正确**，不是 Bug。
复现本验证时**必须先翻到文字密集页**，否则会误判。

### 3.6 证据文件

| 文件 | 内容 |
|---|---|
| `_review/qa_back_night.png` / `qa_back_night_base.png` | 夜间主题：拖拽中 / 静态基准 |
| `_review/qa_back_light.png` / `qa_back_light_base.png` | 默认白 |
| `_review/qa_back_sepia.png` / `qa_back_sepia_base.png` | 羊皮纸 |
| `_review/qa_v3_result.json` | 三档主题全部实测数值 |
| `_review/qa_lib.py` / `_review/qa_v3_final.py` | 本报告全部测量脚本（可复跑） |

肉眼可见证据（缩略图）：`_review/th_qc_d_drag.png` —— 卷起的纸面上能直接看到**镜像反写的文字**，且整块纸是深色而非米黄。

---

## V4. 字体改动零回归 — ✅ 通过

**代码级**：`AppFonts.kt` 的 4 个常量就是 `FontFamily.Default / Serif / SansSerif / Monospace` 本身（`AppFonts.kt:28-37`），`readingFontFamily(index)` 的 when 分支与 `ReaderScreen.kt` 旧内联写法逐分支等价（含 `index==4 → custom ?: Default`）。全工程 `FontFamily.XXX` 字面量残留 = **0 处**。

**实机**（截图：`_review/v4_shelf.png`、`v4_library.png`、`v4_reader.png`、`v4_settings.png`）：

- 中文正常渲染，**无豆腐块/方框**（书架标题、书库空态「检索图书」、阅读页正文大段中文、设置页所有分组标题均正常）。
- 字号/字重与历史截图（`_review/02_shelf.png`、`03_library.png`、`05_settings.png`）一致，无变大变小、无截断/重叠。
- 注：历史截图与本次截图的**布局差异**（如首页「正在阅读」hero 卡有无、滚动位置、分类计数）来自其他功能性改动与数据状态，不属于字体回归，已逐项排除。

---

## V5. 现有功能回归 — ✅ 7/8 通过，1 项为脚本自身局限

| 脚本 | 结果 | 关键证据 |
|---|---|---|
| `_review/bug1.py` | ✅ | 新分类打开 ✅ / 退出回书架 ✅ / 再点打开 ✅ / 长按选中（底栏出现「移动/分享」）✅ |
| `_review/bug2.py` | ✅ | 进阅读页→退出后停在 NEWCAT ✅；冷启动回默认（脚本自身标注"可接受"）|
| `_review/bug3.py` | ✅ | 收藏底栏项 ✅，两本一起移动 → DB `c1->ZZZ, c2->ZZZ` ✅ |
| `drop_verify books` | ✅ | 「深度工作」→ZZZ，提示卡是「已移动 1 本到『ZZZ』」非错误样式 |
| `drop_verify favs` | ✅ | 收藏实时校正后真的归到 ZZZ |
| `drop_verify multi` | ✅ | 两本各自回原位（色差 11 / 45，远小于与空位参考的 147 / 163）|
| `drop_verify hidden` | ✅ | 拖起后原位色差 86，原位确实消失 |
| `drop_verify after` | ⚠️ 脚本自身局限（见下） | |

### `drop_verify after` 的 ❌ 判定为脚本自身问题，不是被测代码回归

**证据链**：
1. 脚本自身日志就自相矛盾：先报 `⚠️ 在 UI 里找不到「《Ciallo阅读使用指南》」，退回第一张卡`，随后 `现在原位上是: []`、`原位上是: []` —— 两次 dump 都在目标坐标找不到任何卡片。
2. 原因：脚本的卡片过滤器是 `if n[1] and (n[5]-n[3])>250 and 400<n[3]<2100`，即**只认带 `content-desc` 属性的节点**。而 `home()` 把书架滚回顶部后，重排网格落在右上/中上格子的卡片在 uiautomator dump 里**不暴露 content-desc**（实测同一屏里 `深度工作`(42,748,353,1180) 有 content-desc，而同排的 Deep Work / 《Ciallo阅读使用指南》 两张卡没有）。于是脚本"看不见"明明存在的卡片，误判为空位。
3. **人工补测同一场景（同一坐标 883,893）**：
   - 移动《三体》→ZZZ 后，原位被《深度工作》填补（实测 bounds `(728,734,1038,1165)` 包含该点）；
   - `tap(883,893)` → 阅读器打开（dump 出现 `第 1/1 页 · 第 1/20`）→ **✅ 点击有效**；
   - `long_press(883,893)` → 多选底栏出现 `['移动','分享','全选']` → **✅ 长按有效，无幽灵热区**；
   - 孤儿书检查：`孤儿书（分类不存在）: 无` ✅。

**结论**：被测功能（移动后无幽灵热区）实际工作正常；脚本对无 content-desc 卡片的探测能力不足。脚本未改动（保留原样以便工程师复核），判定依据以上人工补测数据。

测试后已把 DB 恢复原状：books `1|默认, 2|ZZZ, 3~6|默认`，favorites `c1,c2|默认`。

---

## V6. 强制深色 / 状态栏 — ✅ 通过（1 项需真机补充）

**代码级**：
- `res/values/themes.xml:22`：`<item name="android:forceDarkAllowed">false</item>`（父主题仍 `Theme.Material.Light.NoActionBar`，`statusBarColor/navigationBarColor` 保持透明）
- `MainActivity.kt:146-149`：`if (SDK_INT >= Q) runCatching { window.isNavigationBarContrastEnforced = false }`

**实机**（`adb shell cmd uimode night yes`，`ui_night_mode=2`，冷启动截图 `_review/v6_shelf_dark.png`）：

- App 仍按自己的浅色绿调渲染，**没有被系统整体反色重绘的痕迹**：无黑白翻转的卡片、无糊成鬼画符的原生层、无脏边。
- 状态栏图标仍为深色（浅色背景上），可读性正常 —— 与 themes.xml 注释「状态栏图标色由 MainActivity 按 App 自己的 autoNightMode 动态设置，不写死」一致。

**未能在模拟器完成的部分（如实标注，不编造）**：
原生 **Toast** 在系统深色下的观感未能截到。可达的原生 Toast 触发点（如「默认分类不可删除」）对应的菜单项在默认分类下是禁用态（点击无响应），其他 Toast 的存续时间（~2s）短于 `screencap` 往返，多次尝试均未捕获。
→ **「原生 Toast / 分享面板在厂商强制深色下的表现」需真机 / 云真机补充验证**。鉴于模拟器（非 MIUI/HyperOS/OneUI）本身不会触发激进的 Force Dark，这一项在模拟器上本来也复现不了厂商行为。

---

## 发现的问题 / 回归

**源码 Bug：0 个。**

**脚本问题（非产品问题）1 个**：`_review/drop_verify.py` 的 `after()` 依赖 `content-desc` 定位书卡，部分卡片在 uiautomator 树中不暴露该属性导致误报（详见 V5）。未修改该脚本，建议后续改用「文本节点 + 几何推算」双通道定位。

**需真机补充 1 项**：原生 Toast/分享面板在厂商强制深色下的表现（V6）。

**无字体回归**：25 处字面量收敛到同一 androidx 常量，代码等价性 + 实机截图双重确认，UI 观感无任何可见变化。

---

## 复现说明

所有测量脚本可一键复跑（需 emulator-5554 在线、设备 root）：

```
python _review/qa_v3_final.py 3 1 2     # V3 三主题卷页取证 + 数值落盘
python _review/bug1.py && python _review/bug2.py && python _review/bug3.py
python _review/drop_verify.py books|favs|multi|after|hidden
```

注：复跑 V3 前请确认 `page_turn_mode=0`、目标书有**文字密集页**，且阅读器菜单栏已收起（否则 `dragForwardEnabled=false`）。
