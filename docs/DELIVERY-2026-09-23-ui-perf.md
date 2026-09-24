# 交付说明 · 2026-09-23（UI 美化 + 极致档性能优化）

产物：`app/build/outputs/apk/release/app-release.apk`
大小 **22,542,035 B** · md5 `a60d976347ae83d35b4a7fbae33d840d`
门禁：`build.py release` **BUILD SUCCESSFUL**（3m36s，10 个 task 真实执行）· `audit_shelf.py` **68 通过 / 0 失败** · `SHADOW_PERF_LOG = false`

---

## 一、你提的三件事

| 你的原话 | 处理 |
|---|---|
| 「已选择 N 本」的栏太简陋 | 顶栏玻璃化重做（见二） |
| 下方纯色 tab 栏不好看 | 主 Tab 栏 + 多选操作栏一起玻璃化（见二） |
| 开极致变卡了，别改前端 UI | 极致档性能治理（见三） |

---

## 二、样式改动（只改样式，功能零改动）

### 1. 多选态两根栏 · `ui/shelf/ShelfSelectionHost.kt`
抽出一份 `multiSelectGlassBar()` 给顶栏和操作栏共用，五层：投影 → 玻璃底 → 竖向渐变 → 顶部 1.5dp 镜面高光 → 虹彩描边。

- **顶栏**：整条变成 56dp 悬浮玻璃胶囊；「已选择」降为次级文字，**数字 N 做成主色实底 + 白字药丸徽标**（数字滚动动画保留）；全选胶囊改成"空心玻璃 ↔ 主色实底"两态。
- **操作栏**：从 `surface 0.92` 的纯色条换成同一套玻璃配方——你看到的"纯色不好看"主要就是它。
- 功能锚点全部原样：`MultiSelectTopBar` 签名、`onClose`/`onToggleAll`、数字滚动、
  `"退出多选"` 无障碍语义、`enabled=false → alpha 0.35f`、`destructive`、进出场动画、
  `LocalAppBottomInsetNoTabBar`。

### 2. 底部主 Tab 栏 · `ui/components/AppBottomTabBar.kt`
- **"纯色"的根因**：玻璃之上还平铺了一层 `animatedPrimary.copy(alpha=0.30f)`，把 `liquidGlass`
  采到的真实内容层次整片盖住。改成 `tabBarGlassTint()` 的竖向渐变（顶高光 → 主色淡染 → 底部略深）
  \+ 顶部 1dp 内高光 + 外圈细描边。
- **选中指示**：从"底部一条 3dp 细线"升级为**选中项背后浮起的药丸高亮**（半透明主色染 + 细描边），
  弹簧手感（`DampingRatioMediumBouncy + StiffnessLow`）原样保留。
- 未选中 alpha 0.55 → 0.48；Badge 加与栏体同色描边、排版更圆润（数字格式化逻辑未动）。
- `RenderQuality.LOW` 档仍走最简分支（近实心底 + 白描边），不引入任何新开销。

---

## 三、极致档性能

### 定位方法：先筛"MAX 独有"
`consistentShadow`（39 处）、`liquidGlass`、`iridescentBorder` 都是**全档共用**，
不可能是"只有极致卡"的原因。筛完 MAX 独有只剩：`lens()` 折射、`maxCardAura`、`glassSheen`、
`shimmerPearl`、`shadowGlow`。

### 头号热点：`AppButton` 的 `shadowGlow`（MAX 档常驻）
它用 `drawBehind` 而非 `drawWithCache`，每帧重跑完整 CPU 模糊；**呼吸动画让模糊半径 σ 每帧变化
⇒ 缓存键每帧变 ⇒ 100% 未命中**。更关键的是它和 39 处静态阴影**共用同一个 12MB 缓存**，
一个呼吸周期就占掉约 8MB 且一直处于"最近使用"状态，**把静态卡片阴影的缓存全挤了出去**——
滚动时每张卡都要重做一遍模糊。**HIGH 档没有 `shadowGlow`，所以只有 MAX 卡。**

### 已做的 8 项优化
1. 掩码缓存**分桶**：STATIC 10MB / ANIMATED 4MB，互不驱逐
2. σ>32px 时降采样再加 0.125 档（**只对 ANIMATED 桶开放**，静态阴影一律停在 0.25 ⇒ 逐像素不变）
3. 临时 `IntArray` 缓冲池（每帧垃圾从推导值 ≈1.18MB 降到 ≈30KB）
4. `useCache=false` 时不再构造缓存键
5. 盒式模糊内层循环去掉 `coerceIn`（QA 用 16.38 万次仿真验证**逐像素等价**）
6. `shadowGlow` 光尾的 Shape 提到组合期
7. 删除 `GlassCard` 里**死掉的** aurora 无限动画（全工程零读取点）
8. 两个常量色标提到文件级

### `maxCardAura` 重做（按你选的"保留效果 + 优化 + 做得更好看"）
原实现画 3 圈 sweep 渐变描边，但其中 **2 圈整圈在卡片外、被阴影裁剪完全裁掉，一个像素都不可见**；
唯一可见的是最内圈**意外**落在卡内的 2.67dp（`expand` 最小却配了最宽的描边），
还要被 `liquidGlass` 压掉 70%，等效透明度只剩 ≈0.14 —— 花 3 份钱买到一条几乎看不见的脏边。

改为：**只画 1 圈，刻意画在卡内**。sweep 数量 3 → 1（成本约 1/3），
且不再被裁、不再被压暗，透明度直接由常量给定 ⇒ 从"看不见的脏边"变成**清晰的虹彩描边**。
呼吸（3s）与色相旋转（8s）两个动效保留。

**装包后一眼可调**（`ui/components/MaxFx.kt` 顶部）：
```
AURA_INSET_DP   = 1.0f   // 细边中心向内缩进（dp），越大越靠内
AURA_WIDTH_DP   = 1.8f   // 细边宽度（dp）
AURA_ALPHA_MIN  = 0.22f  // 呼吸最淡端
AURA_ALPHA_MAX  = 0.34f  // 呼吸最浓端
```
⚠️ 约束：`inset − width/2 > 0`，否则细边会被卡片边界裁掉。

---

## 四、验证状态（严格区分"验过的"和"没验的"）

### ✅ 已验证
| 项 | 结果 |
|---|---|
| `build.py release` | BUILD SUCCESSFUL（真编译，10 个 task 执行） |
| `audit_shelf.py` | **68 通过 / 0 失败** |
| 功能锚点（多选栏 8 项 / Tab 栏 5 项） | 全部在位，QA 用 `git show HEAD:` 逐条比过基线 |
| 切 Tab 不再重组整条栏 | 代码级证实：`Animatable.value` 在 `onDrawWithContent` 内读取 |
| 盒式模糊去 `coerceIn` 的等价性 | 16.38 万次仿真，零差异 |
| 装箱 / 4 个 Tab 切换 / 四档画质切换 | 零崩溃（logcat 无 FATAL EXCEPTION） |
| 缓存分桶实际生效 | 埋点实测 **ANIMATED 桶命中率 95.6%** |

### ⚠️ 未验证（环境限制，**不等于"通过"**）
| 项 | 状态 |
|---|---|
| **极致档"变快了"的设备级证据** | **未取得**。QA 与工程师两轮 gfxinfo 都判定**数据不可信**：同档两次重复 25.5% vs 1.6%（差 16 倍），流畅档甚至测出 100% janky（物理上说不通）。这台软件渲染模拟器噪声远大于信号。 |
| 书架滚动 before/after | **实测无差异**。原因已查明：书架页没有 Primary AppButton，`shadowGlow` 根本没跑 —— 优化有效，但**只对有 Primary AppButton 的页面有效**（设置页/书库页/首页），标准测量页（书架）不在其中。 |
| 所有改动的目视验收 | **未做**。这台机器 `screencap` 会把 App 打成 Segmentation fault，无法截图。 |

**建议**：装包后自己看两处 —— ① 多选顶栏与底栏、主 Tab 栏的玻璃观感；
② 极致档下**设置页/书库页**（有主按钮的页面）是否比之前顺。若虹彩细边太浓/太淡，改上面四个常量即可。

---

## 五、过程中被 QA 抓出并已修正的两处
1. **OPT-2 的论据是错的**：原注释写"33 处调用点、最大 elevation 12dp"，实际是 **39 处、
   静态最大 32.dp**；且 `GlassCard` 的 `pressLift` 是动态值，倾斜滑杆拉满时上限 **48.dp**。
   已把 0.125 档限定为 ANIMATED 桶专用，静态侧"逐像素不变"从侥幸成立变成定义上成立。
2. **`maxCardAura` "整圈不可见"的说法不成立**：最内圈有 2.67dp 落在卡内。已改为按正确几何
   重新设计（见三）。
