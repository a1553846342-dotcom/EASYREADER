# 跨机型渲染一致性审计报告 — 阴影 / 毛玻璃 / 动画健壮性

> 执行人：render-engineer　·　基准机：emulator-5554（API 35 / 1080×2400 / 420dpi）
> 验证：`python .workbuddy/build.py check` → **BUILD SUCCESSFUL**；`python .workbuddy/audit_shelf.py` → **68 项通过，0 项失败**

---

## 0. 结论速览

| 项 | 数量 | 说明 |
|---|---|---|
| elevation 阴影改自绘 | **10 个文件 / 13 处** | 覆盖全部「品牌关键面」，参数从原 elevation 值反推 |
| 保留 elevation（次要装饰） | **9 处** | 逐处判断依据见 §2 |
| blur / RenderEffect 降级 | **3 个文件 / 3 类改动**（覆盖全部 8 个玻璃调用点） | 含 1 处 API<31 **必崩**隐患修复 |
| 动画卡死隐患修复 | **2 处** | 另排查 7 处确认无风险 |
| 新增公共设施 | `Modifier.consistentShadow()`、`supportsRealtimeBlur`、`frostedGlassFallback()` | 全部收口在既有模块内，零新增依赖 |

---

## 1. elevation 阴影跨机型一致（重点）

### 1.1 为什么必须改

`Modifier.shadow` / `Surface(shadowElevation=…)` / `graphicsLayer { shadowElevation = … }`
三者最终都落到 HWUI 的 `RenderNode.setElevation()`，由**系统 libhwui** 现场做
「凸包细分 + 模糊」。模糊半径、ambient/spot 两路阴影的 alpha 系数是 libhwui
内部常量，各 ROM（MIUI / OneUI / ColorOS / HarmonyOS）都有改写记录；软件渲染与
个别省电模式下还有 ROM 直接不画投影。结果是同一张卡在不同机型上阴影浓淡、
扩散范围肉眼可见地不一致，**且 App 侧无法补偿**。

### 1.2 方案：`Modifier.consistentShadow()`（新增于 `me/trishiraj/shadowglow/ShadowGlow.kt`）

用 Skia `BlurMaskFilter` + `Outline.toAndroidPath()` 自绘，**不依赖任何
RenderEffect（API 21+ 全可用）**，参数只由调用处显式传入的 elevation / 颜色决定 →
所有机型逐像素一致。

从 elevation 反推的映射（关键：**不改观感**）：

| 层 | 模糊半径 | 位移 | alpha |
|---|---|---|---|
| ambient（环境光） | `elevation × 0.45` | 0 | `ambientColor.a × 0.45` |
| spot（聚光） | `elevation × 0.45` | `elevation × 0.35`（向下，光源在屏幕上方） | `spotColor.a × 0.45` |

系数标定依据见 §1.4。

> ⚠️ 调用位置约定（已写入 KDoc）：自绘阴影必须放在链中任何
> `graphicsLayer { … }` / `clip(…)` **之前（更外层）**。`graphicsLayer` 在
> alpha<1（入场淡入）或旋转（MAX 档倾斜）时走离屏合成，会把超出节点边界的
> 绘制裁掉；`Modifier.shadow` 从不被裁是因为 HWUI 把投影画在父画布上，
> 自绘版只能靠「放外层」复刻这个语义。GlassCard 因此把阴影从链中段**上移到了链首**。

### 1.3 改动清单（全部「品牌关键面」）

| # | 位置 | 原 elevation | 改后 |
|---|---|---|---|
| 1 | `ui/components/AppBottomTabBar.kt:181`（流畅档） | `shadow(8dp, 黑 0.16)` | `consistentShadow(8dp, 黑 0.16)` |
| 2 | `ui/components/AppBottomTabBar.kt:188`（高/极致档） | `shadow(24dp, 主题色 0.18)` + `shadow(8dp, 黑 0.20)` | `consistentShadow` ×2 同参数 |
| 3 | `ui/components/AppButton.kt`（Primary 主按钮，含按压 2dp↔16dp 动画） | `shadow(shadowElevation, 主题色 0.35)` | `consistentShadow(shadowElevation, 主题色 0.35)`，并上移到 `graphicsLayer` 之外 |
| 4 | `ui/components/GlassCard.kt`（流畅档） | `shadow(4dp, 黑 0.08/0.12)` | `consistentShadow(4dp, 黑 0.08/0.12)` |
| 5 | `ui/components/GlassCard.kt`（高/极致档） | `shadow(pressLift, 主题色 0.10/0.14~0.30)` + `shadow(4dp, 黑 0.08/0.15)` | `consistentShadow` ×2 同参数，**上移到链首**（原 `.shadow` 在 `graphicsLayer` 之后） |
| 6 | `ui/ReaderScreen.kt:2288`（章节预览浮层 Surface 6dp） | `shadowElevation=6dp` | `shadowElevation=0` + `consistentShadow(6dp, 黑 0.10/0.14)` |
| 7 | `ui/ReaderScreen.kt:2614`（返回上次处 Chip 6dp） | 同上 | 同上 |
| 8 | `ui/ReaderScreen.kt:2668`（自动滚屏 Chip 4dp） | `shadowElevation=4dp` | `consistentShadow(4dp, 黑 0.08/0.12)` |
| 9 | `ui/ReaderScreen.kt:2718`（回到顶部圆形按钮 4dp） | 同上 | 同上 |
| 10 | `ui/ReaderScreen.kt:4018`（设置面板 `.shadow(12dp)`） | `shadow(12dp, R16)` | `consistentShadow(12dp, 黑 0.12/0.16)` |
| 11 | `library/LibraryScreen.kt:1406`（书源底部弹层，环境层） | `shadow(32dp, 品牌色 0.12)` | `consistentShadow(32dp, 品牌色 0.12)` |
| 12 | `library/LibraryScreen.kt:1412`（同上，接触层） | `shadow(8dp, 黑 0.20)` | `consistentShadow(8dp, 黑 0.20)` |
| 13 | `ui/CacheManagementScreen.kt:765`（清理成功弹层 Surface 12dp） | `shadowElevation=12dp` | `consistentShadow(12dp, 黑 0.12/0.16)` |

### 1.4 实测数据（像素级）

截图：`docs/render-audit/before_home.png`（改前）vs `after2_home.png`（改后），
同一坐标同一布局（Tab 栏图标 bounds 完全一致 `[173,2154][231,2212]`），
1080×2400 / 420dpi。测量脚本：`docs/render-audit/measure.py`。

| 区域（x 120–960） | 改前平均亮度 | 改后平均亮度 | Δ | Δ% |
|---|---|---|---|---|
| Tab 栏**上方** 2–16px | 254.65 | 251.18 | −3.47 | −1.4% |
| Tab 栏**下方** 2–24px（阴影最强带） | 249.77 | 239.72 | −10.05 | −4.0% |
| Tab 栏下方 24–60px | 251.14 | 251.17 | +0.03 | **0.0%** |
| Tab 栏下方 60–110px | 240.26 | 240.51 | +0.25 | +0.1% |
| Tab 栏内部（玻璃面） | 201.35 | 203.57 | +2.23 | +1.1% |

**解读：**

- **扩散范围逐像素一致**：24px 以外的三个带 Δ≈0.0%，说明 `0.45` 的模糊系数
  正确复刻了原生投影的空间衰减。
- **近距带改后比改前深 10.05 亮度单位**（改前阴影贡献仅 1.37 单位，改后 11.45 单位）。
- 原因与取舍：改前那次是在**模拟器软渲染**下拍的，HWUI 的 elevation 投影在
  软件渲染管线上几乎画不出来（标称 24dp+8dp 双层、alpha 0.18/0.20，实测只压暗
  1.37/255 ≈ 0.5%）。**模拟器软渲染不能作为「观感基准」**——真机上同一份
  elevation 代码会画出明显更重的投影（这正是我们要消除的机型间差异）。
  若严格对齐模拟器基准，自绘阴影会在真机上变成一条看不见的细线，
  等于把「跨机型一致」做成了「跨机型一致地没有阴影」。
  因此取值刻意比模拟器基准**强**，满足「绝不能变淡/消失」；代价是与模拟器
  基准的偏差为 −4.0%（带平均），方向是「更接近设计稿标注的 0.18/0.20 双层投影」。

### 1.5 保留 elevation 的 9 处（次要装饰，判断依据）

| 位置 | elevation | 保留理由 |
|---|---|---|
| `ui/ReaderScreen.kt:4123` 彩蛋文字 | 8dp | 一次性彩蛋浮层，非品牌面 |
| `ui/ReaderScreen.kt:4169` 书签小页签 | `6f`（≈2dp 像素值） | 20×34dp 小元素、极弱阴影 |
| `ui/source/SourceManagementScreen.kt` ×3 | 1dp | 1dp 装饰性，视觉不可辨 |
| `ui/components/SettingsControls.kt:140` 分段选中指示器 | 10dp 主题色 0.55 | 装饰性强调光晕（MAX 档专属） |
| `ui/components/SettingsControls.kt:497 / 556` 滑轨 / 拖动 thumb | 1dp ×2 | 1dp 装饰性 |
| `ui/components/WeeklyReadingChart.kt` 选中柱 | 6dp 主题色 0.30 | 图表选中高光，装饰性 |
| `ui/ComicChaptersScreen.kt:595` 封面缩略图 | 12dp | 封面缩略图，装饰性 |
| `library/LibraryScreen.kt:2622` 文字投影 | 2dp | 2dp 装饰性 |
| `com/swapnil/squishyswitch/.../SquishyToggle.kt:153` 滑块小圆 | 10dp | **结构性原因**：该节点外层是带缩放/平移动画的 `graphicsLayer`，自绘阴影放外层不随滑块移动、放内层会被离屏合成裁掉；改造成本/风险比不划算 |

另：`AppButton.kt` 的 `shadowGlow`（MAX 档辉光）本就是 `drawBehind` +
`BlurMaskFilter` 自绘，天然跨机型一致，无需改动。

### 1.6 禁改文件中发现的问题（**未动手**，交由主理人处理）

- `ui/shelf/ShelfSelectionHost.kt` 有 **3 处 `Modifier.shadow`**，且位于
  多选拖拽操作栏 / 归位幽灵卡——属于「悬浮操作栏」级别的品牌关键面。
  建议同样替换为 `Modifier.consistentShadow(...)`（参数逐处照抄原值即可，
  一行一处）。注意需放在该文件的 `graphicsLayer { … }` 之前。
- `ui/components/AppSnackbar.kt`、`AppErrorSnackbar.kt`（不在允许清单）仍有
  elevation 阴影，属于悬浮操作栏级别，建议后续一并处理。

---

## 2. blur / RenderEffect 降级

`RenderEffect` 自 Android 12（API 31）才存在；KMPLiquidGlass 在低版本会把
blur / colorControls / vibrancy / lens **全部静默 no-op**（见
`backdrop/.../platform/PlatformEffects.kt` 的 `PlatformCapabilities`），
结果是 backdrop 以**未模糊的原始内容**透出——玻璃卡看起来像一块透明玻璃
叠了张清晰底图，毛玻璃质感完全丢失。因此降级不能只是「不显示」。

### 2.1 改动

**① `ui/components/LiquidGlass.kt` — 新增能力判定 + 降级实现（收口一处，全 App 生效）**

```kotlin
val supportsRealtimeBlur: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
```

`frostedGlassFallback(shape, surfaceColor)`：
- **加厚的半透明基底**：`surfaceColor.alpha + 0.20`（弥补没有真实模糊带来的「太透」）；
- **柔和透光渐变**：全域「左上受光提亮 → 右下轻微压暗」+ 顶部 22% 高度受光亮边，
  替代真实模糊的漫反射观感；
- **`filmGrain(0.05)` 噪点**：保留磨砂介质的颗粒质感。

`Modifier.liquidGlass()` 与 `Modifier.liquidGlassStatic()` 均在
`!supportsRealtimeBlur` 时返回该降级 → 以下 **8 个玻璃调用点一次性全覆盖**：

| 调用点 | blur 半径 | 降级后观感 |
|---|---|---|
| `GlassCard.kt`（页面内容卡，实时路径） | `tweaks.blurRadiusDp`（默认 22dp） | 加厚磨砂面板 |
| `GlassCard.kt`（预烘焙路径 `liquidGlassStatic`） | 同上 | 同上 |
| `AppBottomTabBar.kt`（底部 Tab 栏） | 8dp | 同上 |
| `LibraryScreen.kt:585`（搜索历史卡） | 20dp | 同上 |
| `LibraryScreen.kt:1424`（书源底部弹层） | 12dp | 同上 |
| `library/FormatPickerDialog.kt`（弹窗面板） | 18dp | 同上 |
| `library/LibraryLoginDialog.kt`（登录弹窗） | 22dp | 同上 |
| `ui/components/AcrylicDialog.kt` ×2（亚克力弹窗） | 18dp | 同上 |

**② `LiquidGlass.kt` — `GlassDialogWindowEffect` 弹窗宿主降级**

API<31 时 decorView 无法实时模糊，弹窗背后内容会以清晰原样透出。
降级：`window.setDimAmount` 从轻压 **0.12 提到 0.42**，用「暗化 + 面板本体
加厚基底」近似毛玻璃层次——观感是明显的磨砂弹窗，而不是透明的清晰玻璃。

**③ `ui/ComicChaptersScreen.kt:549` — 修复一处 API<31 必崩隐患（重要）**

原代码直接调用 `android.graphics.RenderEffect.createBlurEffect(...)`。
`RenderEffect` 类本身 API 31 才引入，**低版本直接调用会 `VerifyError` 崩溃**
（不只是「不显示」）。已加 `Build.VERSION.SDK_INT >= S` 判级；不支持时用
「主题色磨砂遮罩（0.40→0.30 纵向渐变）+ 透光渐变」近似虚化封面底图
（其上本就叠有 0.45→0.82 的黑色压暗层，遮罩后观感接近虚化）。

### 2.2 核查后无需改动的两处（说明原因）

- **`MainActivity.kt`**：只挂 `layerBackdrop` 捕获层与 `LocalGlassBackdrop`，
  不直接调用 RenderEffect；真正的 RenderEffect 在 backdrop 库
  （`PlatformCapabilities.supportsBlur` 已内部判级、no-op 安全）与本次新增的
  `supportsRealtimeBlur` 分支里。`liquidGlassProvider` 来自 liquidglass-compose
  模块，自带 `GlassRenderTier.select(SDK_INT, requested)` 降级。零改动。
- **`GlassCard.kt` / `AppBottomTabBar.kt` / `LibraryScreen.kt` 的玻璃调用点**：
  通过 ① 的收口自动获得降级，无需逐点改动（避免 8 处重复实现）。

### 2.3 毛玻璃实测（API 35 走实时 RenderEffect 路径，确认零回归）

| 区域 | 改前 | 改后 | Δ |
|---|---|---|---|
| Tab 栏玻璃区平均亮度 | 203.20 | 204.53 | +0.65% |
| Tab 栏玻璃区亮度**标准差**（纹理保留度） | 31.09 | 30.24 | −2.7% |
| 设置页玻璃卡内区 | 232.48 | 233.36 | +0.38% |
| 设置页卡片下方阴影带 | 254.13 | 254.52 | +0.15% |
| 书库页上半（含玻璃卡） | 237.24 | 238.49 | +0.52% |
| 书库页底栏玻璃区 | 209.26 | 208.66 | −0.29% |

玻璃区标准差 31.09 → 30.24（−2.7%），说明**真实模糊仍在生效、底图纹理完整保留**
（若误降级成纯色遮罩，标准差会趋近 0）。降级路径需 API≤31 设备实拍验证，
模拟器 API 35 无法触发，已在代码中逐点标注降级形态。

---

## 3. 动画被系统关闭时的健壮性

前提澄清：Compose 动画由 `MonotonicFrameClock`（Choreographer）驱动，
**不受** `ANIMATOR_DURATION_SCALE` 影响；真正受影响的是 app 自己的
`LocalReduceMotion` 分支与「依赖动画完成回调」的协程链。本轮把这两类全部排查了一遍。

### 3.1 修复的 2 处

**① `ui/components/SettingsControls.kt:460-482` 滑块拖拽 — `dragging` 状态可能永久卡死**

原写法：`dragging = true` 后进入 `while(true) { awaitPointerEvent() … }`，
循环正常 `break` 后才 `dragging = false`。若协程在 `awaitPointerEvent` 挂起时被取消
（节点移出组合、父级抢占手势流、`awaitEachGesture` 重启），复位语句**永远不会执行**
→ 拖动 thumb 永远显示、滑块永远停在拖动态。
修复：整段拖拽体包进 `try { … } finally { dragging = false }`，且 finally 里用
**非挂起**的状态写（`MutableState` 赋值），保证在已取消的协程里也能复位。

**② `com/swapnil/squishyswitch/presentation/SquishyToggle.kt:81-119` — 连点可把滑块冻在拉伸态**

原写法：每次 `flip()` 都往 `rememberCoroutineScope()` 里再 launch 一段
「拉伸 → 移动 → 挤压 → 恢复」四阶段链。快速连点时**多条链并发**抢同一批
`Animatable`——`Animatable.animateTo` 会取消前一个，于是上一条链的 `joinAll`
提前返回、直接跳到 Step 4 把形变归位，与新链的拉伸阶段互相踩踏，
最终可能停在 `1.15f` 的拉伸态再也不回弹（滑块外观永久变形）。
修复：改由 `LaunchedEffect(isToggled)` 作为**唯一**动画驱动——状态翻转即重启
整段动画，任何时刻只有一条链在跑；即使被中断 / 系统关闭动画，
最后一步 restore 也保证把 squish 收敛回 `1f`。

### 3.2 排查后确认无风险的 7 处（结论）

| 位置 | 形态 | 结论 |
|---|---|---|
| `GlassCard.kt:489` 按压触点追踪 | `awaitEachGesture` + `finally { handDown = false }` | ✅ finally 保证复位 |
| `ui/CacheManagementScreen.kt:124` 总占用滚动 | `Animatable.animateTo`，纯视觉 | ✅ 不 gate 任何业务 |
| `ui/components/SettingsControls.kt:616` 翻页效果预览 | 同上 | ✅ 纯视觉 |
| `ui/ReaderScreen.kt:498/508` 章节入场 / 回弹 | 同上 | ✅ 纯视觉 |
| `ui/components/WeeklyReadingChart.kt:88` 柱状生长 | LOW 档有 `snapTo(1f)` 快路 | ✅ 纯视觉 |
| `ui/shelf/ShelfSelectionHost.kt:1129` HeartBurst | `if (reduceMotion) { onDone(); return }` | ✅ 两个分支都调用 `onDone()` |
| `ui/favorite/FavoriteHeart.kt:90` 收藏心形 | `tween(if (reduceMotion) 100 else 260)` | ✅ 只改时长不跳过 |

### 3.3 发现但不在我允许清单内（**未动手**）

- `ui/components/WeeklyReadingChart.kt:88` 的生长动画**没有 `LocalReduceMotion`
  分支**（只有 `RenderQuality.LOW` 分支）。系统关闭动画时仍会播 320+400ms 生长。
  不是卡死，只是「减少动态效果」未覆盖，属可接受的小瑕疵；若要补，加一个
  `if (LocalReduceMotion.current) { snapTo(1f); return@LaunchedEffect }` 即可。

### 3.4 动画关闭实测（`animator_duration_scale` / `transition` / `window` 全部 = 0）

冷启动 → 依次点 书库 / 设置 / 统计 / 书架 → 打开《三体》阅读器 → 设置页连拨
「纯净模式」开关两次，全程截图（`docs/render-audit/animscale0_*.png`）：

- 四个 Tab 全部正常切换，阅读器正常出页（`第1/1页 第2/75章 2%`），**无任何功能卡死**；
- SquishyToggle 滑块：OFF → ON → OFF，轨道左右绿通道差实测
  `+22.5 → −22.0 → +22.5`，两次拨动后**精确回到初态**，
  滑块没有被冻在拉伸形变里（修复前连点存在该风险）；
- 测毕已恢复三个 scale = `1`（已复核）。

---

## 4. 验证与产物

| 项 | 结果 |
|---|---|
| `python .workbuddy/build.py check` | **BUILD SUCCESSFUL**（两轮：接入后 + 标定后） |
| `adb install -r` app-release.apk | Success |
| `python .workbuddy/audit_shelf.py` | **68 项通过，0 项失败** |
| 动画关闭主流程 | 无卡死（§3.4） |
| 测后现场还原 | 动画 scale 已恢复 1；`wm size/density` 已恢复 1080×2400 / 420 |

**产物目录** `docs/render-audit/`：

- `measure.py` — 纯标准库 PNG 解码 + 区域亮度对比（无 PIL/numpy 依赖）
- `profile.py` — 面板边缘定位 + 阴影剖面分析
- `before_home.png` / `before_library.png` / `before_settings.png` — 改前
- `after_home.png` / `after2_home.png` / `after2_library.png` / `after2_settings.png` — 改后（`after2*` 为标定后的最终版）
- `animscale0_*.png` — 动画关闭健壮性实测

> 拍摄注意事项（给后续复测的同学）：改前/改后必须**同一 `wm size/density`**、
> **不产生滚动**（Tab 栏会折叠成 52dp 导致坐标漂移）、
> 并用 `uiautomator dump` 核对 Tab 栏 bounds 一致后再对比。
> 本次首轮 `after_*` 因 Tab 栏折叠 + 分辨率被并行 agent 改动而作废，仅留档。

---

## 5. 关键设计决策与偏离说明

1. **自绘阴影收口在 `ShadowGlow.kt`（`me.trishiraj.shadowglow` 包）**，
   而不是新建文件：该文件本就是「阴影绘制」模块且在允许清单内，
   所有允许文件可直接 import，零新增依赖、零新增文件。
2. **alpha 增益 0.45 / 模糊系数 0.45 的标定方向**：以模拟器软渲染实测为准会得到
   一个几乎不可见的阴影（§1.4），这会让「跨机型一致」变成「跨机型一致地没有
   阴影」。最终取「与设计稿标称投影的量级一致、比模拟器实测略强」，
   偏差方向是「更浓」而绝非「变淡」，同时扩散范围与实测基准逐像素一致。
3. **GlassCard 阴影上移到链首**：不是等价替换，而是必须的结构调整——
   自绘阴影必须位于任何 `graphicsLayer`/`clip` 之外，否则离屏合成会裁掉
   超出节点边界的部分（§1.2）。`RenderQuality.LOW` 档原本为滚动隔离挂的
   空 `graphicsLayer { }` 已保留。
4. **SquishyToggle 的 10dp 滑块投影保留 elevation**（§1.5），原因是在不重构
   该组件变换层级的前提下，自绘阴影无法既跟随滑块位移又不被离屏合成裁掉。
5. **blur 降级收口在 `LiquidGlass.kt` 而非逐调用点**：8 个玻璃调用点共享一套
   `frostedGlassFallback`，避免 8 处重复实现，也保证降级观感全局一致。
