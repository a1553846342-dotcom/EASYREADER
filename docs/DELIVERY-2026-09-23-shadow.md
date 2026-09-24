# 交付说明：阴影跨机型一致化（含用户报的"大块纯色框"修复）

日期：2026-09-23　包名：`com.aistudio.novelreader.kxmpzq`

产物：`app/build/outputs/apk/release/app-release.apk`
- 大小 **22,538,795 B**
- md5 **f7c3c623cdcc3b485ddc75eccef63439**
- 已确认 `SHADOW_PERF_LOG = false`（交付包不含性能调试日志）
- `consistentShadow` 调用点 **33 处**

---

## 一、用户报的问题与根因

现象：**卡片周围大块纯色框（品牌绿）、选中时方框**，观感廉价。

根因有**两个，互相独立**，而用户看到的绿框主要是**第二个**：

### 根因 A：`BlurMaskFilter` 在硬件加速画布上被静默忽略
旧自绘阴影用 `Paint.setMaskFilter(BlurMaskFilter) + nativeCanvas.drawPath`。
**Android 硬件加速画布不支持 `setMaskFilter`** ⇒ 模糊被丢弃 ⇒ 路径被画成**实心硬边形状**。
（同类坑：`ColorFilter.tint` 在本环境也会忽略 alpha，按满不透明着色 → 又是一块纯色。）

### 根因 B（真凶）：自绘丢了 `Modifier.shadow` 的**内容裁剪**语义
`Modifier.shadow(...)` 除画阴影外，还会**把内容裁到 shape**。
`GlassCard` 原链是 `.shadow(7A).shadow(7B).maxCardAura().clip(shape)` ——
**极致画质档**的 `maxCardAura`（虹彩呼吸光晕）**整圈都在卡片边界之外**，
原版正好被 shadow 的 clip **裁掉 ⇒ 默认不可见**。
改成 `drawBehind` 自绘后**丢了这层裁剪** ⇒ 光晕整圈外泄 ⇒ **大块绿色圆角光框**。

**归因证据**：把 `render_quality` 从 3 调到 2，绿块立刻消失（而自绘阴影两档都在跑）
⇒ 绿块与阴影本身无关，是极致档专属的 aura 外泄。

**同源问题**：原有的 `shadowGlow`（AppButton 极致档品牌色辉光）也用 MaskFilter，一并修好。

---

## 二、修复方案（不是退回 `Modifier.shadow`）

新增 `me/trishiraj/shadowglow/ShadowBlurEngine.kt`：
- 按 shape 轮廓生成**降采样 mask 位图**（0.5x / 0.25x）
- **只对 alpha 通道做三次盒式模糊**（≈高斯），RGB 常量重建（绕开预乘往返双乘 alpha）
- **双线性放大**画回画布
- `LruCache` 缓存；后续优化为 `drawWithCache`，**组合期算一次、绘制期零分配只 `drawImage`**

⇒ 纯 CPU 数学，**不依赖 `maskFilter` / `RenderEffect` / `Modifier.blur`**，
任何机型、任何渲染路径都得到同一份像素。

`consistentShadow` **复刻了 `Modifier.shadow` 的裁剪语义**：阴影 `drawBehind`（可正常外溢）
+ 内容 `.clip(shape)`。签名向后兼容：
`consistentShadow(elevation, shape, ambientColor, spotColor, offsetY, alpha)`。

### 覆盖范围
- 第一批 20 处（用户截图涉及的卡片、Tab 栏、按钮、弹层、提示条等）+ 第二批 13 处
  （亚克力面板、HomeScreen hero 与书封面、格式弹窗、漫画封面与操作条、下拉刷新 chip、
  图表选中柱、TTS 卡、引导页卡、FAB、空态卡）= **共 33 处**，参数一字未改。
- **刻意保留原生 elevation**（逐条有依据）：极致档选中胶囊（宽度连续动画 ⇒ 缓存键每帧失效）、
  各处 1dp 发丝线、3D 拖拽/翻页动效内部、第三方库内部、Canvas-only 书签飘带、mascot 庆祝弹窗。

---

## 三、验证状态（**如实区分"已验证"与"未验证"**）

### ✅ 已验证（第一批 20 处）

| 项 | 结果 | 来源 |
|---|---|---|
| `build.py check` / `release` | BUILD SUCCESSFUL | QA 独立重跑 |
| `audit_shelf.py` | **68 项通过 / 0 失败** | QA 独立重跑 |
| 装机包一致性 | 从设备 pull 出 `base.apk` 算 md5，与构建产物**逐字节一致** | QA 独立复测 |
| 环带平均亮度差 | 极致档 q3 最差 **2.066/255**；默认档 q2 最差 **1.044/255**（门槛 6） | QA 用基线包做**同档 A/B** |
| 根因 B 已修 | 采样点 q3/q2/基线差 **≤2/255**，无绿残留 | QA 自采 |
| 裁剪语义真复刻 | 卡外 60px 环带品牌绿像素 新 0.844% vs 旧基线 0.780%，同量级 | QA 实测（非看注释） |
| 判据灵敏度自证 | 注入 8–10px 硬边绿框 → 判据响应 **94.85/255**（门槛 6） | QA 注入实验 |
| 功能回归 | `bug1`/`bug2`/`bug3`/`drop_verify` books·favs·multi·after·hidden **8/8 通过** | QA 逐项单独跑 |

### ⚠️ 未完成验证（环境限制，**不是"通过"**）

| 项 | 状态 | 原因 |
|---|---|---|
| 第二批 13 处**逐表面**环带像素差 | **未完成** | 模拟器每轮只能出 1 张截图即崩溃 |
| 第二批目视检查 | **仅 1 个样本**：书架页（书封面卡 + 底部 Tab 栏）**无硬边色块** | 同上 |
| CPU 模糊性能 | **未测量** | 同上 |
| 真机 ROM 行为（MIUI/OneUI 强制深色、原生 Toast/分享面板） | 未覆盖 | 模拟器无法复现 |

**环境障碍（已穷举 4 种组合，全部失败）**：
`-gpu host` 崩溃；`-gpu swiftshader_indirect` 报 `Failed to find ColorBuffer: 48`；
swiftshader + `-feature -Vulkan,-GLDirectMem,-GLAsyncSwap` **段错误**；
设备端 `screencap -p` 落盘再 `pull` 同样崩溃。**均在第一次截图时挂掉。**

**性能的唯一一组数据**（来自 QA 第一阶段，**模拟器软渲染，绝对值不可信**）：
p99 由基线 250–350 升到 950–1000（≈3×），同窗口吞吐 −28%，p50 无回退。
**真机必须复测**才能判断是否构成实际影响。

---

## 四、过程中的两个产品级发现（值得单独跟进）

1. **"画质看门狗"其实是崩溃循环保护**（`MainActivity.kt:107-127`）：
   `render_quality == 3` 时若 **20 秒内连续冷启动两次**，会**永久降回 2**（防止实验性着色器崩到打不开）。
   —— 自动化测试反复 `force-stop` + 重启，正好会触发它，导致"prefs 写 3、实际渲染 2"。
   采集前需同时置 `render_quality=3`、`boot_guard_cnt=0`、`boot_guard_last=0`。
2. **`adb kill-server` 只能在模拟器开机前用**。运行期执行会让模拟器丢连接并崩溃
   （日志实证 `Unable to connect to adb daemon on port: 5037` → `emu-crash-*.db`）。
   运行期判断：`adb devices` 为空时**先 `tasklist | grep -i emulator`**，
   进程在 → 等它自愈，**不要 kill-server**。

---

## 五、后续建议（按优先级）

1. **真机复测性能**（唯一有实际风险的未验项）：连续快滚书架，取 p50/p90/p99。
   若确有回退，优化方向：更激进的降采样（0.25x）、按需缩小 `drawImage` 目标尺寸、提高缓存命中率。
2. **真机截一批图**覆盖第二批 13 处 + 6 套阅读主题 + 极致/默认两档，补齐环带像素差。
3. **云真机**跑 MIUI / OneUI / HarmonyOS 的强制深色与原生控件皮肤化。
4. 顺手项：`ReaderPagination.kt:92` 行距下限与实际渲染的 `lineHeight.sp` 不一致（分页偏差 ±1 行）。

---

## 六、附：相关文档与脚本

- `docs/shadow-consistency.md`（原理 / 缓存 / 33 处调用点 / 像素数字 / 决策记录 / 复现步骤）
- `docs/qa-shadow-verify-2026-09-23.md`（QA 独立复验报告）
- `docs/cross-device-ui-audit.md`、`docs/adaptive-screen-audit.md`、`docs/final-acceptance-2026-09-23.md`
- 复现脚本：`_review/qa_*.py`、`_review/final_visual*.sh`、`.workbuddy/shadow_perf_ab.py`
