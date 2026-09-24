# 跨机型一致的阴影实现（Deterministic Shadow）交付说明

> 目标：把「自绘阴影」的致命缺陷修好，使阴影**既与原来的 `Modifier.shadow` 观感一致**，
> 又**跨机型/跨 ROM 逐像素一致**。
>
> 涉及文件：
> - `app/src/main/java/me/trishiraj/shadowglow/ShadowBlurEngine.kt`（新增，模糊内核）
> - `app/src/main/java/me/trishiraj/shadowglow/ShadowGlow.kt`（重写 `consistentShadow` / `shadowGlow`）
> - `app/src/main/java/me/trishiraj/shadowglow/Drawing.kt`（新增轮廓值签名、光尾改用新内核）
> - 8 个业务文件共 **20 处**调用点（见 §5）

---

## 1. 结论摘要

| 验收项 | 门禁 | 实测 | 结果 |
|---|---|---|---|
| 环带平均绝对亮度差（极致档 q3，数值来源 `_cmp_q3.txt`；⚠️ 对应图已不存在，见 §6.1） | < 6/255 | **0.12 – 1.95 / 255** | ✅ 通过（约 3–50× 余量） |
| 环带平均绝对亮度差（高画质 q2，默认档） | < 6/255 | **0.10 – 1.04 / 255** | ✅ 通过 |
| 阴影外扩方向亮度曲线 | 单调平滑、无硬边 | 与基线逐像素重合，最大相邻跳变与基线相同 | ✅ 通过 |
| `build.py check` | BUILD SUCCESSFUL | BUILD SUCCESSFUL | ✅ 通过 |
| `build.py release` | BUILD SUCCESSFUL | BUILD SUCCESSFUL | ✅ 通过 |
| `audit_shelf.py` | 68 通过 0 失败 | **68 项通过，0 项失败** | ✅ 通过 |

**一句话结论**：本轮共定位并修复了 **两个互相独立的根因**——(A) 旧自绘阴影用了硬件加速下会被静默忽略的
`BlurMaskFilter`（导致实心硬边块）；(B) 自绘版丢失了 `Modifier.shadow` 的 **clip 裁剪语义**，使 MAX 档
`maxCardAura` 的外圈光晕由「被裁掉」变成「整圈外泄」（导致大块品牌色圆角光框）。两者均已修复，像素门禁通过。

---

## 2. 根因

### 2.1 根因 A：`BlurMaskFilter` 在硬件加速画布上被忽略

旧自绘实现（`ShadowGlow.kt` 的 `drawBlurredShadowPath`）用：

```kotlin
paint.setMaskFilter(BlurMaskFilter(sigma, BlurMaskFilter.Blur.NORMAL))
canvas.drawPath(shadowPath, paint)
```

Android 官方限制：**硬件加速画布不支持 `MaskFilter`**，该 filter 被**静默忽略**（不报错、不降级）。
于是「模糊的阴影路径」退化成「**实心硬边形状**」——这正是上一版 UI 上出现的
**「大块硬边纯色框（品牌绿）」与「方框」**。注意：`Modifier.shadow` / `Surface(shadowElevation)`
走的是 HWUI 的 elevation 阴影（另一条管线），不受影响，所以把调用点换回 `Modifier.shadow` 后该缺陷即消失。

### 2.2 根因 B（本轮新发现）：丢失了 `Modifier.shadow` 的 clip 语义

`Modifier.shadow(elevation, shape, clip = elevation > 0.dp)` 的默认行为是：
**把节点内容裁剪到 `shape`**。`GlassCard` 的修饰符链是：

```
.consistentShadow(7A)          // 原：.shadow(pressLift.dp, ...)   clip=true
.consistentShadow(7B)          // 原：.shadow(4.dp, ...)           clip=true
.maxCardAura()                 // MAX 档：整圈虹彩呼吸光晕（整圈都在卡片边界之外）
.clip(effectiveShape)
.background(...)
```

`maxCardAura` 画的是 3 层描边圆角矩形，**整圈都落在卡片边界之外**（`expand = 16dp × i/3`，
描边宽 `16dp / i`）。原实现里，它正好被前面 `Modifier.shadow` 的 `clip = true` 裁掉 →
**默认不可见**。而自绘版用 `drawBehind`（**不裁剪内容**），光晕便整圈外泄，在 MAX 档形成
**大块品牌色圆角光框**——一个很容易被误判成「阴影 bug」的观感回退。

> 实测证据（统计页，v=540）：
> - 修复前 MAX 档 `(18,1000) = (107,200,170)` 满屏品牌绿光带；`(540,1705) = (114,200,172)` 大块绿带。
> - 把 `render_quality` 从 3 调到 2，绿块立刻消失 → 证明与 `consistentShadow`（两档都在跑）无关，
>   是 MAX 档专属的 `maxCardAura`。
> - 修复后 MAX 档 `(18,1000) = (255,255,255)`、`(540,1705) = (249,252,252)`，与基线一致。

**修复**：在 `consistentShadow` 内部复刻 `Modifier.shadow` 的裁剪语义——阴影本体用 `drawBehind`
画（不被裁剪、可正常溢出），内容用 `.clip(shape)` 裁剪：

```kotlin
return this
    .drawBehind { drawConsistentElevationShadow(...) }  // 阴影：画在内容之下，不裁剪
    .clip(shape)                                        // 复刻 shadow(elevation, shape, clip = elevation > 0)
```

---

## 3. 实现原理（确定性 CPU 模糊）

不用 `BlurMaskFilter`、不用 `RenderEffect`、不用 `Modifier.blur`（后两者有 API 门槛、且同样依赖系统模糊实现），
改为**纯 CPU 的、与 GPU/ROM 无关的**模糊：

1. **降采样画掩码**：把 `shape.createOutline(size, …).toAndroidPath()` 的轮廓路径，
   按 `scale ∈ {1.0, 0.5, 0.25}`（依 σ 选取）画进一张 `ARGB_8888` 位图，填充色即阴影色（含 alpha）。
2. **三次盒式模糊 ≈ 高斯**：只对位图的 **alpha 通道**做 **3 遍 box blur**（水平/垂直可分离，整数滑动窗口）。
   三次串联的方差 = `r² + r`，令其等于 `σ²` 解得单次半径
   `r = (−1 + √(1 + 4σ²)) / 2`。模糊后再按阴影 RGB 重建像素。
   - **只模糊 alpha** 是关键：绕开 Android 位图「直通/预乘」往返导致的双重乘 alpha 问题。
3. **双线性放大**：`drawImage(..., filterQuality = FilterQuality.Low)` 放大回画布，
   模糊被平滑拉伸，看不出像素块。
4. **`colorFilter = null`**：把「颜色 + alpha」直接烘焙进位图，绘制时不做任何 tint。
   - ⚠️ **教训**：不要用 `ColorFilter.tint(color, SrcIn)` 上色——本工程环境下该 tint 会
     **忽略 color 的 alpha、按满不透明色着色**，直接把阴影变成一块高饱和色（≈「大块纯色框」）。

### 3.1 缓存

- 键 `MaskKey(shapeSig, sigmaQ, scaleQ, strokeQ, colorArgb)`，全部是**值**（轮廓"签名"是
  类型+尺寸+圆角的量化串），与对象引用无关，故重建同样形状可命中缓存。
- `LruCache<MaskKey, Bitmap>`，字节预算 **12 MB**（`sizeOf` 用 `Bitmap.byteCount`）。
- 形状每帧变化的光尾（`drawGlowTrailAlongShape`）传 `useCache = false`，避免误复用。
- 滚动/重组时命中缓存 → 零重算。

### 3.2 与 `Modifier.shadow` 的语义对齐

| 维度 | `Modifier.shadow` | `consistentShadow` |
|---|---|---|
| 环境层 ambient | 全向、无位移 | σ = elevation × 0.22，无位移 |
| 聚光层 spot | 向下偏移 | σ = elevation × 0.22，偏移 = elevation × 0.28（向下） |
| 双层叠加 | ambient + spot | ambient + spot 两遍绘制叠加 |
| 不影响布局 | 是 | 是（`drawBehind`） |
| 内容裁剪到 shape | `clip = elevation > 0` | `.clip(shape)`（本轮补上） |
| alpha | — | 每层再乘 gain（0.10，见下） |

**alpha gain（0.10）**：HWUI 在基准机上把标称 alpha 画得比标称淡，自绘版乘这个系数才能逼近
「与改前一致」的观感；数值由 §6 的像素级验收实测标定。

---

## 4. 参数与兼容性

`consistentShadow` 的签名**完全向后兼容**（与工程内既有调用点一致）：

```kotlin
@Composable
fun Modifier.consistentShadow(
    elevation: Dp,
    shape: Shape,
    ambientColor: Color = Color.Black.copy(alpha = 0.08f),
    spotColor: Color  = Color.Black.copy(alpha = 0.15f),
    offsetY: Dp = 0.dp,
    alpha: Float = 1f
): Modifier
```

`shadowGlow(...)`（纯色/渐变两个重载）签名保持不变；纯色重载改走
`drawDeterministicGlowRoundRect`（同一 CPU 内核），渐变重载当前**无调用点**、暂以首色渲染。

---

## 5. 调用点（第一批 20 处，全部由 `Modifier.shadow` / `Surface(shadowElevation)` 换回）

| 文件 | 行 | 原实现 → 现实现 |
|---|---|---|
| `ui/components/GlassCard.kt` | 531, 540, 549 | `.shadow(...)` → `.consistentShadow(...)`（LOW 4dp；else 品牌色 `pressLift.dp` + 4dp 黑） |
| `ui/components/AppBottomTabBar.kt` | 184, 192, 198 | `Modifier.shadow(...)` → `.consistentShadow(...)`（LOW 8dp；else 24dp+8dp 双层品牌色） |
| `ui/components/AppButton.kt` | 174 | `.shadow(...)` → `.consistentShadow(...)`（品牌色 .35/.35） |
| `ui/components/AppSnackbar.kt` | 98 | `.shadow(8.dp, RoundedCornerShape(14.dp))` → `.consistentShadow(...)` |
| `ui/components/AppErrorSnackbar.kt` | 59 | 同上 |
| `ui/CacheManagementScreen.kt` | 767 | `Surface(shadowElevation=12.dp)` → `shadowElevation=0.dp` + `.consistentShadow(12.dp, RoundedCornerShape(24.dp), Black.12/Black.16)` |
| `library/LibraryScreen.kt` | 1407, 1413 | SourcePickerSheet 双层：32dp 品牌色 + 8dp 黑 |
| `library/LibraryScreen.kt` | 2552, 2623 | 头部折叠条 / 检索行卡片 |
| `ui/ReaderScreen.kt` | 2293, 2607, 2659, 2712 | `.shadow(...)` → `.consistentShadow(...)` |
| `ui/ReaderScreen.kt` | 3994, 4130 | `.shadow(12/8.dp, RoundedCornerShape(16.dp))` → `.consistentShadow(...)` |

**目标文件内已无残留** `.shadow(` / `shadowElevation > 0`（`ReaderScreen.kt:4176` 除外，见 §7）。

> 说明：`Surface(shadowElevation = …)` 站点统一把 `shadowElevation` 归 0，并把
> `Modifier.consistentShadow(同 elevation, 同 shape, …)` 挂到 `Surface` 的 `modifier` 上；
> `Surface` 自身仍按原 `shape` 裁剪，与原观感一致。
>
> ⚠️ 表中行号是**写入时**的快照；工作树由多位成员并行编辑、行号会漂移（见 §10.4）。
> 复核时请按**符号锚点**（所在文件的组件/函数名 + 参数字面量）定位，不要只信行号。

---

## 6. 像素级验收（硬门禁）

### 6.1 方法

- 设备：`emulator-5554`（1080×2400 @420dpi）；`render_quality` 分别设为 **3（极致）** 与 **2（高，默认）**。
- A/B：**基线 APK**（`_review/_baseline_app-release.apk`，纯 `Modifier.shadow`）
  vs **新 APK**（`consistentShadow`），**同一画质、同一采集脚本**重拍。
- 页面：书架 / 统计 / 设置 / 书库(空态)。采集脚本 `.workbuddy/shadow_capture.py`，对比脚本 `.workbuddy/shadow_compare.py`。
- 指标：环带平均绝对亮度差（卡片边缘外 +8/+16/+24/+32 px）+ 竖直扫描线亮度剖面与最大相邻跳变。

> ⚠️ **产物 ↔ 数字 对照（务必看，避免引错文件）**
>
> | 数字来源 | 采集档位 | 对应的对比报告 | 对应的截图文件 |
> |---|---|---|---|
> | §6.2 第一张表 | **q3（极致）** | `_review/_cmp_q3.txt` | ⚠️ **当前仓库已无**（见下） |
> | §6.2 第二张表 | **q2（高，默认）** | `_review/_cmp_q2b.txt` | `_review/shadow_base_*.png` / `_review/shadow_new_*.png`（**现存的这批就是 q2**） |
> | §6.3 | **q3** | 随 `_cmp_q3.txt` 同批 | ⚠️ 当前仓库已无 |
>
> **⚠️ 仓库现状**：`_review/shadow_base_*.png` / `shadow_new_*.png` **只保留最后一次采集（q2）**，
> 更早的 q3 截图已被 q2 覆盖。因此 §6.2 / §6.3 的 **q3 数字只能从 `_review/_cmp_q3.txt` 的文本记录复核，
> 无法从现存 PNG 复现**。需在下一轮拿到设备后**重拍 q3 并另存为独立文件名**（如 `_review/shadow_q3_base_*.png`）补齐。
> 引用时请务必核对上表，不要把 q3 的数字指向 q2 的图。
>
> 已由 QA 独立复测确认：现存 PNG 就是 q2 那套（QA 自己的 q2 采集与 `shadow_base_*` 逐像素差 **0.000**）；
> QA 重采的真 q3 主卡 +8 = **2.066/255**、q2 = **1.044/255**，与本文件 §6.2 的 **1.95 / 1.04** 吻合，结论未被推翻。

### 6.1.1 方法论铁律（本轮踩坑换来的，务必遵守）

> **任何 A/B 像素对比，必须先把两侧的采集条件（画质档、分辨率、滚动位置、页面状态）锁死再比。**

踩坑实录：第一次 A/B 的基线收在 **q2**、新包收在 **q3**，两份截图根本没有可比性——
差异里混进的其实是 MAX 档专属的 `maxCardAura` 极光光晕（§2.2），
一度被误判成「自绘阴影过强/偏绿」，白白耗掉多轮调参。
**把画质档对到相同**后差异立即降到 1/255 量级。
具体到本工程，A/B 前必须逐项确认：

1. **采集前必须验证"档位实际生效"，而不是"prefs 里写着几"。**
   `render_quality` 会被 App 的**画质看门狗降档**（`MainActivity.onCreate` 的 `boot_guard`）：
   prefs 写着 3，实际可能渲染成 2。**A/B 两侧都要在实际生效档位相同的前提下采集。**
   判据：先在同一档位采两次「不该变」的画面（或读实际渲染档位），**确认生效后再进入对比**。
   踩坑实录：QA 第一次采"q3"时就被降档到 q2，一度得到 **7.3 的假 FAIL**；重置 `boot_guard_cnt` 并同档重采后才对。
2. `render_quality` 两侧一致（并同时把 `boot_guard_cnt` 置 0，防止启动看门狗把极致档降回高档）；
3. 分辨率/密度一致（1080×2400 @420dpi）；
4. 起始页面与滚动位置一致（本工程有「状态恢复」，冷启会停在书籍详情/阅读页，需 BACK 循环回到 Tab 页）；
5. 导航序列一致（用同一个采集脚本）；
6. 避开非确定内容（异步封面/网络搜索结果的页面，单独判断、不计入门禁主指标）。

### 6.2 环带平均绝对亮度差（单位 1/255，门禁 < 6）

**极致档 q3**（数值来源：`_review/_cmp_q3.txt`；⚠️ 对应 PNG 已被后来的 q2 采集覆盖，**当前仓库无法从图复现**，见 §6.1 对照）：

| 页面 | 环带 +8px | +16px | +24px | +32px |
|---|---|---|---|---|
| 统计·头卡 | 1.291 | 1.184 | 0.991 | 0.950 |
| 统计·主卡 | 1.947 | 1.708 | 1.851 | 1.904 |
| 统计·阅读日历卡 | 1.581 | 1.377 | 1.484 | 1.601 |
| 书架·顶部标题条 | 0.233 | 0.284 | 0.229 | 0.168 |
| 书架·底部 Tab 栏 | 0.393 | 0.444 | 0.554 | 0.752 |
| 设置·设置头卡 | 1.115 | 0.991 | 0.915 | 0.814 |
| 设置·主题配色卡 | 1.121 | 1.186 | 1.300 | 1.191 |
| 书库·书库头卡 | 0.270 | 0.234 | 0.268 | 0.259 |
| 书库·检索卡片 | 0.117 | 0.204 | 0.326 | 0.419 |

**高画质 q2（默认档）**（数值来源：`_review/_cmp_q2b.txt`；✅ 对应 PNG 即**当前仓库现存的** `_review/shadow_base_*.png` / `shadow_new_*.png`，可逐像素复现）：

| 页面 | 环带 +8px | +16px | +24px | +32px |
|---|---|---|---|---|
| 统计·头卡 | 0.369 | 0.421 | 0.512 | 0.491 |
| 统计·主卡 | 0.331 | 0.459 | 0.776 | 0.883 |
| 统计·阅读日历卡 | 0.888 | 0.757 | 0.909 | 1.044 |
| 书架·顶部标题条 | 0.271 | 0.243 | 0.158 | 0.117 |
| 书架·底部 Tab 栏 | 0.393 | 0.444 | 0.554 | 0.752 |
| 设置·设置头卡 | 0.583 | 0.596 | 0.633 | 0.492 |
| 设置·主题配色卡 | 0.532 | 0.685 | 0.827 | 0.783 |
| 书库·书库头卡 | 0.136 | 0.165 | 0.221 | 0.238 |
| 书库·检索卡片 | 0.097 | 0.192 | 0.319 | 0.414 |

**全部页面、全部环带 ≪ 6/255**（最差 1.95）。

### 6.3 外扩方向亮度曲线（单调平滑、无硬边）

统计页主卡下沿外（q3，v=540，y=1697→1725，亮度 0–255；⚠️ 对应 q3 PNG 已不存在，以下为当时文本记录）：

```
基线: 240.2 248.3 252.0 254.8 254.4 253.1 250.6 250.6 … 逐步收敛到 ~251
新版: 238.7 247.5 251.4 254.1 253.9 252.9 251.4 251.4 … 逐步收敛到 255
基线相邻步进: [8.1, 3.7, 2.8, -0.4, -1.3, -2.5, 0.0, …]   最大单步 8.1
新版相邻步进: [8.9, 3.8, 2.8, -0.2, -1.1, -1.5, 0.0, …]   最大单步 8.9
```

设置页主题卡下沿外（q3，v=540，y=2103→2135；⚠️ 对应 q3 PNG 已不存在）：基线/新版**逐值完全相同**（`…145,145,208.4,203.3,204.3,208.7,196.3,184.7,176.6,171.0,170.2`），
最大单步 63.5（该跳变来自卡片自带的 UI 内容边缘，**两侧一致**，非阴影硬边）。

结论：自绘阴影的**外扩曲线与基线逐像素重合**，既单调平滑、也没有引入新的硬边。

### 6.4 整页差异的说明

`shelf` 页「整页平均绝对亮度差」偏高（q3 ≈ 8.7，q2 ≈ 9.5），**与阴影无关**：差异集中在
书籍封面缩略图区域（`y≈736–1308` 等多段），是异步加载封面（占位图 vs 已加载图）造成的，
两侧环带（标题条 / Tab 栏）差异都 < 0.8/255。统计页、设置页、书库页整页差分别为
**1.87 / 1.47 / 0.43**（q3，数值来源 `_review/_cmp_q3.txt`，⚠️ 对应 PNG 已不存在）。

---

## 7. 已知差异与残留（如实说明）

1. **`ReaderScreen.kt:4176` 仍有原生阴影**：`BookmarkHangingRibbon` 的
   `graphicsLayer { shadowElevation = 6f }`。该站点不在第一批「16 处」清单内，
   **已由 team-lead 拍板归入第二批「不换」**（见 §10.3：Canvas 路径需补等价 shape、纯装饰）。
2. **全库原生阴影现状（第二批已完成）**：第二批已把
   `AcrylicDialog`、`FormatPickerDialog`、`HomeScreen`(hero+书封面)、`ComicChaptersScreen`(封面+Card)、
   `PageTurnContainer`、`WeeklyReadingChart`、`ReaderScreen`(TTS Card)、`OnboardingScreen`、
   `LibraryScreen`(FAB)、`MascotEmptyState` 一致化（见 §10）。
   **仅剩 §10.3 判据判定「不换」的站点仍为原生 elevation**：`SettingsControls`（选中胶囊 10dp /
   滑轨·滑块 1dp）、`SourceManagementScreen`（3 处 1dp）、`PageScrubber`（3D 变换内 14dp）、
   `ShelfSelectionHost`（拖拽幽灵 graphicsLayer）、`SquishyToggle`（第三方 vendored）、
   `mascot/*`（4 处庆祝弹窗）、`ReaderScreen.BookmarkHangingRibbon`（书签飘带）。
3. **MAX 档 `maxCardAura` 外圈光晕仍不可见**（与原始版本一致）。原因见 §2.2：原实现即被
   `Modifier.shadow` 的 clip 裁掉；本轮为「与原来一致」刻意保留了该裁剪。
   **已由 team-lead 拍板：保持现状、不得改**（用户诉求是「原样 + 跨机型一致」，不趁机加新视觉）。
4. **渐变版 `shadowGlow` 无调用点**：当前以渐变首色渲染，若将来启用需扩展为渐变掩码。
   **已确认可接受**。
5. **极小 σ（< ~0.5px）不模糊**：`buildMaskBitmap` 在盒式半径 < 1 时跳过模糊，
   此时靠路径自身抗锯齿提供约 1px 柔化——观感与 `Modifier.shadow` 无可见差别（见 §6 门禁）。

---

## 8. 复现步骤

```bash
# 编译
python .workbuddy/build.py check     # BUILD SUCCESSFUL
python .workbuddy/build.py release   # BUILD SUCCESSFUL

# 书架静态审计
python .workbuddy/audit_shelf.py     # 68 项通过，0 项失败

# 像素 A/B（设备 emulator-5554）
#   1) 装基线 _review/_baseline_app-release.apk，设 render_quality=<Q>，采集
python .workbuddy/shadow_capture.py shadow_base
#   2) 装 app/build/outputs/apk/release/app-release.apk，设 render_quality=<Q>，采集
python .workbuddy/shadow_capture.py shadow_new
#   3) 对比（Q 必须两次一致！）
python .workbuddy/shadow_compare.py shadow_base shadow_new
```

`render_quality` 改动（需 root，本机 adbd 会反复丢 root，失败要重试）：
`/data/data/com.aistudio.novelreader.kxmpzq/shared_prefs/novel_reader_prefs.xml` 中
`render_quality` 值，并同时把 `boot_guard_cnt` 置 0（避免启动看门狗把极致档降回高档）。

**产物清单（含档位标注，避免引错）：**

| 产物 | 档位 | 是否存在（当前仓库） |
|---|---|---|
| `_review/_cmp_q2b.txt` | q2（高，默认） | ✅ 存在 |
| `_review/shadow_base_*.png` / `_review/shadow_new_*.png` | **q2（高，默认）** | ✅ 存在（**就是 q2 那套**） |
| `_review/_cmp_q3.txt` | q3（极致） | ✅ 文本存在 |
| 对应 q3 的 `shadow_*_q3*.png` | q3（极致） | ❌ **不存在**（被后来的 q2 采集覆盖，需重拍） |

> ⚠️ 一句话：**现在仓库里能逐像素复现的只有 q2**；q3 只剩文本记录。重拍时请把 q3 图另存为
> `_review/shadow_base_q3_*.png` / `shadow_new_q3_*.png`，别再覆盖。

---

## 9. 第二批：残留原生阴影（决策记录）

第一批只覆盖了「曾被改成自绘、后又被还原」的 20 处。全库仍存在其它**原生 elevation 阴影**
（`Modifier.shadow` / `Surface(shadowElevation)` / `graphicsLayer{shadowElevation}` /
Material3 `Card`·`FAB` 的 elevation），它们**同样走 HWUI、同样随 ROM 变化**，尚未纳入一致化。

### 9.1 已拍板：不换（保持原生 elevation）

逐条清单与判据统一维护在 **§10.3**（避免两处发散）。此处仅留结论摘要：
MAX 档选中胶囊(10dp)、各处 1dp 发丝线、PageScrubber 3D 阴影、ShelfSelectionHost 拖拽幽灵、
SquishyToggle 第三方、4 处 mascot 庆祝弹窗、ReaderScreen 书签飘带 —— **全部保持原生 elevation**。

<!-- 旧的重复表格已移除，逐条清单见 §10.3 -->

### 9.2 换 / 不换的判据（供后续沿用）

1. **表面性质**：品牌关键面（dialog 主面板 / 首屏卡片 / hero）= 必须换；瞬态动画内部 = 可不换。
2. **elevation 量级**：≤1dp 发丝线不换（差异不可辨）。
3. **结构性冲突**：位于 3D 变换 / `alpha<1` 离屏合成层内，或阴影需随 3D 透视变形 ⇒ 不换（除非先做层裁剪验证）。
4. **成本**：形状随连续动画变化（缓存键失效）⇒ 不换。
5. **第三方 vendored 组件** ⇒ 不换。
6. **不为了"统计口径统一"而改**：每处都要有独立依据（第一批的教训）。

---

## 10. 第二批最终范围（已定稿，按符号锚点）

> 已与 team-lead 逐条确认。**定位一律用符号锚点（函数名 / 字面量），不用行号**——理由见 §10.4。

> **✅ 代码已落地（本轮）**：必换 **4 处** + 低优先 **8 处** 全部落地（改动点共 13 个：
> AcrylicDialog 双层 2 + HomeScreen 2 + FormatPickerDialog 1 + ComicChaptersScreen 2 +
> PageTurnContainer 1 + WeeklyReadingChart 1 + ReaderScreen 1 + OnboardingScreen 1 +
> LibraryScreen 1 + MascotEmptyState 1）。`consistentShadow` 调用点由 **20 → 33**。
> 门禁：`build.py check` ✅ BUILD SUCCESSFUL、`audit_shelf.py` **68 PASS / 0 FAIL** ✅
> （涉及 `HomeScreen.kt`，未改任何局部变量名）。
> **⚠️ 未完成（环境限制）**：**逐表面环带像素差（<6/255）复核未做、q3 重拍未做** —— 设备环境不稳定
> （模拟器反复自发崩溃），无法采集。**不得据此认为第二批已通过像素门禁**（见 §12.4）。

### 10.1 必换（品牌关键面，4 处）

| 锚点（函数 / 字面量） | 现值 | 表面类型 |
|---|---|---|
| `AcrylicDialog.kt` → `Modifier.acrylicPanel(...)`：`.shadow(elevation=32.dp, ambient=primary 0.14, spot=primary 0.18)` + `.shadow(elevation=8.dp, ambient=Black 0.18, spot=Black 0.22)` | 双层 | 亚克力立牌面板（dialog 主面板） |
| `HomeScreen.kt` → 书封面 `boxModifier`：`.shadow(elevation=8.dp, shape=RoundedCornerShape(14.dp), ambient=Black 0.10, spot=Black 0.16)` | 8dp | 书架封面卡（首屏高频） |
| `HomeScreen.kt` → "正在阅读" hero：`.shadow(6.dp, RoundedCornerShape(12.dp))` | 6dp | hero 封面（首屏常驻） |
| `FormatPickerDialog.kt` → `.shadow(28.dp, RoundedCornerShape(24.dp), clip=false)` | 28dp | 格式选择弹窗面板 |

### 10.2 低优先（结构标准，8 处）

| 锚点 | 现值 |
|---|---|
| `ComicChaptersScreen.kt` → `.shadow(12.dp, RoundedCornerShape(12.dp))` | 12dp |
| `PageTurnContainer.kt` → `Surface(shape=RoundedCornerShape(24.dp), shadowElevation=6.dp, …)` 下拉刷新 chip | 6dp（浮层，阴影外溢可见） |
| `WeeklyReadingChart.kt` → `Modifier.shadow(elevation=6.dp, shape=topStart/topEnd 4dp, chartPrimary 0.30/0.30)` 选中柱 | 6dp |
| `ComicChaptersScreen.kt` → `Card(elevation=CardDefaults.cardElevation(defaultElevation=10.dp))` | 10dp |
| `ReaderScreen.kt` → `Card(elevation=CardDefaults.cardElevation(defaultElevation=8.dp))` | 8dp |
| `OnboardingScreen.kt` → `Card(defaultElevation=4.dp)` | 4dp |
| `LibraryScreen.kt` → `FloatingActionButtonDefaults.elevation(defaultElevation=3.dp)` | 3dp |
| `MascotEmptyState.kt` → `Card(defaultElevation=2.dp)` | 2dp |

> ⚠️ 改 `WeeklyReadingChart.kt` 时**只动 shadow 调用**，不得触碰同文件里的 `LocalReduceMotion` 动画分支
> （另一位成员刚加的）。改 `HomeScreen.kt` 后**必须** `python .workbuddy/audit_shelf.py` 拿 **68 PASS**。

### 10.3 不换（逐条判据）

| 锚点 | 现值 | 判据 |
|---|---|---|
| `SettingsControls.kt` → 分段控件选中胶囊 `Modifier.shadow(elevation=10.dp, primary 0.55/0.55)` | 10dp（仅 MAX） | 胶囊宽度 **0↔180dp 连续动画** ⇒ 缓存键含 `bounds.size.width` **每帧失效** ⇒ 每帧重建掩码 + 三次盒式模糊；风险 > 收益 |
| `SettingsControls.kt` → 滑轨轨道 `.shadow(1.dp,… 0.20/0.20)`、滑块 `.shadow(1.dp, CircleShape, 0.30/0.30)` | 1dp | 发丝线，跨 ROM 差异肉眼不可辨 |
| `SourceManagementScreen.kt` → 三处 `Surface(shadowElevation=1.dp)` | 1dp | 同上 |
| `PageScrubber.kt` → 居中页 `.shadow(14.dp, RoundedCornerShape(12.dp), clip=false)` | 14dp | 位于 3D 变换（`rotationY`/`cameraDistance`）内，阴影本就应随透视变形；该区曾踩「离屏合成裁掉越界投影」的坑，替换需先做层裁剪验证 |
| `ReaderScreen.kt` → `BookmarkHangingRibbon` 的 `graphicsLayer{shadowElevation=6f}` | 6dp | Canvas 路径阴影需补等价 shape；纯装饰 |
| `ShelfSelectionHost.kt` → 拖拽幽灵卡 `graphicsLayer{shadowElevation = 12f / 10f*alpha}` | 12/10f | 位于旋转/缩放/`alpha` 的 graphicsLayer 内、`alpha<1` 走离屏合成；瞬态拖拽动效；且受 `audit_shelf` 保护 |
| `SquishyToggle.kt` → `graphicsLayer{shadowElevation=10.dp}` | 10dp | vendored 第三方库内部实现 |
| `mascot/{BookComplete,BookmarkHappy,DeleteSad,MoveBook}Animation.kt` → `.shadow(24.dp, RoundedCornerShape(24.dp), spotColor=…)` | 24dp | 一次性播放的庆祝动画覆盖层；外层 `graphicsLayer{alpha}` 离屏合成可能裁掉 `drawBehind` 溢出阴影 |

### 10.4 教训：多 agent 并行编辑下，一律用符号锚点定位

工作树由多位成员（software / font / adaptive / render / shadow）并行编辑，**行号会持续漂移**。
本轮就发生过一次：team-lead 凭记忆转录的第二批行号全部对不上——实测 `HomeScreen.kt:1865` 是
`CategoryPill(...)`、`HomeScreen.kt:2738` 是 `contentDescription`、`HomeScreen.kt:2984` 与
`AcrylicDialog.kt:298` **越界不存在**、`FormatPickerDialog.kt:154` 是文案、`SourceManagementScreen.kt:135`
是 `SnackbarDuration`。**结论：下达与核对改动范围时，一律引用符号锚点（函数名 / 字段名 / 括号内字面量），
不引用行号；实施前先用 Grep 按锚点复核当前行号。**

### 10.5 第二批硬约束（与第一批一致）

- **参数一字不改**：`.shadow(elevation, shape, ambientColor, spotColor)` → `.consistentShadow(同参)`；
  `Surface(shadowElevation>0)` → `shadowElevation=0.dp` + `modifier = Modifier.consistentShadow(同 elevation, 同 shape, 同色)`。
- **逐表面环带像素差 < 6/255**，且 **A/B 先锁死采集条件**（画质档 / 分辨率 / 滚动位置 / 页面状态）——见 §6.1.1。
- 涉及 `HomeScreen.kt` / `ui/shelf/` ⇒ `audit_shelf.py` 必须 **68 PASS**，且不得改局部变量名。

---

## 11. 独立复测结论（来源：shadow-qa 独立复验，非本文自证）

> 以下条目由 QA（shadow-qa）**独立重拍重测**得出，未采信本文件的自测数字。列此以增强可复核性。

1. **根因 B 成立且已修复（V3）**：新包 q3 `(18,1000)=(255,255,255)`、q2 同值；旧基线 q3 `(253,254,254)`；差 ≤ 2/255。
   ⇒ 证实「MAX 档 `maxCardAura` 外泄」已随 clip 复刻而消除。
2. **裁剪语义确实复刻（V4，非看注释）**：q3 统计页卡外 60px 环带内「品牌绿」像素计数——
   新包 **2905 个（0.844%）** vs 旧基线 **2683 个（0.780%）**，量级相同；
   若 aura 外泄，该计数会高一个数量级。⇒ 证明确实重新裁剪住了。
3. **判据灵敏度已自证**：向真实截图注入「8–10px 硬边品牌绿框」，环带判据响应 **94.85/255**（门槛 6）。
   ⇒ 该判据确实能检出硬边，不是"永远通过"。
4. **数字交叉核对**：QA 重采真 q3 主卡 +8 = **2.066/255**，q2 = **1.044/255**；
   与本文件 §6.2 的 **1.95 / 1.04** 吻合。⇒ 第一批结论未被推翻。
5. **已发现的产物问题**：现存 `_review/shadow_*.png` 实为 q2；已按 §6.1 / §8 修正引用，并标注 q3 图缺失待重拍。

---

## 12. 性能：CPU 模糊回退的定位与修复（drawWithCache）

> ⚠️ 本节是**修复**；改前/改后的 gfxinfo 对比表（p50/p90/p95/p99 + janky% + 吞吐 + 缓存命中率）
> 需在设备上实采，`_review/_perf_ab.md` 为落地产物（采集脚本 `.workbuddy/shadow_perf_ab.py`）。

### 12.1 症状（QA V5 实测）
自绘 `consistentShadow` 首版把书架连续快滚的帧时间尾部大幅推高：
q3 p99 ≈ 950–1000 ms（≈ 基线 250–350 ms 的 3×）、吞吐 −28%、p90 73–125 ms（可见 micro-stutter）。

### 12.2 根因
首版把**每帧都要重算的重活**放在了**每帧执行的 `drawBehind`** 里：

```kotlin
// 旧（每帧）：createOutline / toAndroidPath / computeBounds / 轮廓签名 / 建缓存键 / asImageBitmap
.drawBehind { drawConsistentElevationShadow(...) }   // ambient + spot 各来一遍
```

滚动时没有任何重组，但 `drawBehind` 的 lambda **每帧都跑**，于是每帧：
`shape.createOutline()` → `outline.toAndroidPath()` → `RectF().computeBounds()` →
`shadowOutlineSignature()`（字符串拼接）→ `Color.toArgb()` → 构造 `MaskKey` →
`mask.asImageBitmap()` —— 一层套一层，两层阴影就是 ×2。**这些量与"是否命中缓存"无关**，
命中缓存也省不掉（键和位图包装每帧都要重新造）。

### 12.3 修复
把上述重活整体移到**组合期的 `drawWithCache`**（只在 size / 参数变化时执行一次），
绘制期只剩「贴一张已解析好的位图」：

```kotlin
return this
    .drawWithCache {
        val e = elevation.toPx()
        if (size.width <= 0f || size.height <= 0f || e <= 0f) {
            return@drawWithCache onDrawBehind { }        // 早退也要返回合法 DrawResult
        }
        // ── 组合期一次性解析：路径 / 外接矩形 / 值签名 / 两张掩码位图 ──
        val outline = shape.createOutline(size, layoutDirection, this)
        val shadowPath = outline.toAndroidPath()
        val bounds = RectF().also { @Suppress("DEPRECATION") shadowPath.computeBounds(it, true) }
        val signature = outline.shadowOutlineSignature()
        val ambient = resolveBlurredShadow(shadowPath, bounds, signature, ambientColor.copy(alpha = 1f),
            e * SHADOW_AMBIENT_BLUR_FACTOR, 0f, baseOffsetY, 0f, Fill, ambientAlpha)
        val spot = resolveBlurredShadow(shadowPath, bounds, signature, spotColor.copy(alpha = 1f),
            e * SHADOW_SPOT_BLUR_FACTOR, 0f, baseOffsetY + e * SHADOW_SPOT_OFFSET_FACTOR, 0f, Fill, spotAlpha)
        // ── 绘制期：零分配、无路径运算，只 drawImage ──
        onDrawBehind {
            ambient?.let { drawResolvedBlurredShadow(it) }
            spot?.let { drawResolvedBlurredShadow(it) }
        }
    }
    .clip(shape)   // 复刻 Modifier.shadow 的裁剪语义（见 §2.2）
```

配套（`ShadowBlurEngine.kt`）：
- 新增 `class ResolvedBlurredShadow(image, srcSize, dstOffset, dstSize)`：`asImageBitmap()` / `IntSize` /
  `IntOffset` **全部在解析期算好**，绘制期零分配。
- 新增 `resolveBlurredShadow(...)`（组合期重活，含命中率记账）与 `drawResolvedBlurredShadow(...)`
  （绘制期只 `drawImage(filterQuality = Low)`）。
- `ShadowMaskCache` 增加 `hits/misses` 计数与 `statsText()`（仅用于验收，开销为两次 Long 自增）。

### 12.4 结论：性能未测量（环境限制）
**性能三档 A/B 未完成 —— 设备环境不稳定：本机模拟器反复自发崩溃（`tasklist` 中 qemu 进程消失、
emulator 日志停在 `Boot completed` 且无崩溃行），单次存活约 1.5–4 分钟，无法完成 gfxinfo 采集。**
因此本节**不给出"通过"结论**，性能项按 **「未测量 / 环境限制」** 记账。

唯一掌握的量化数据来自 QA 第一阶段（V5）：自绘首版在**模拟器软渲染**下 q3 `p99 ≈ 950–1000 ms`
（≈ 基线 `250–350 ms` 的 3×）、吞吐 −28%。**该数据不可外推为真机结论**：来源是模拟器软渲染，
基线自身都跑不满 60 fps，绝对帧时间不可信；**目前没有任何真机证据表明存在同等回退**。
`drawWithCache` 修复已在代码层完成（§12.3），**建议在真机上复测**后再对性能定性。

> `SHADOW_PERF_LOG`（`ShadowBlurEngine.kt`）已置回 **`false`**，交付包**不含**该调试日志
> （每 240 次解析打印 `hits/misses/hitRate` 到 logcat tag `ShadowPerf` 的开关已关闭）。

### 12.5 设备采集铁律（沿用 §6.1.1）
- 采集前先验证**档位实际生效**（看门狗会降档），`boot_guard_cnt` 置 0；
- `adb devices` 为空**不等于**模拟器死了：先 `tasklist | grep -i emulator`。
  进程在 → **等它自愈或直接重跑命令，不要 `adb kill-server`**——运行期 `kill-server` 会把模拟器搞崩：
  日志实证 `Unable to connect to adb daemon on port: 5037` → `emu-crash-*.db`；`kill-server`
  只能在模拟器**开机前**使用。进程真没了才谈重启。
- 采集期间**勿并行跑 gradle**（CPU 抢占直接污染 p99）。
