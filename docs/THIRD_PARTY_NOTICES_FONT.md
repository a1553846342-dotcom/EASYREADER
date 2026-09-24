# 第三方字体声明（Third-Party Font Notices）

本 App 的**阅读正文字体**随 APK 一起分发，以保证所有安卓机型上的中文字形完全一致
（不依赖各厂商自带的 MiSans / HarmonyOS Sans / OneUI Sans 等系统字体）。

> 涉及的源文件只有两个，便于审计：
> - `app/src/main/res/font/`（打包的字体子集）
> - `app/src/main/java/com/example/ui/theme/AppFonts.kt`（接线点）

---

## 1. Noto Serif SC

| 项目 | 内容 |
| --- | --- |
| 字体名称 | Noto Serif SC |
| 字重 | Regular（400） |
| 版本 | notofonts/noto-cjk `main` 分支（SubsetOTF/SC） |
| 官方主页 | https://fonts.google.com/noto/specimen/Noto+Serif+SC |
| 许可证 | **SIL Open Font License 1.1** |
| 许可证全文 | https://scripts.sil.org/OFL |
| 许可证文件 | https://github.com/notofonts/noto-cjk/blob/main/Serif/LICENSE |
| 获取地址 | `https://cdn.jsdelivr.net/gh/notofonts/noto-cjk@main/Serif/SubsetOTF/SC/NotoSerifSC-Regular.otf` |

打包文件：`app/src/main/res/font/noto_serif_sc_regular.otf`

---

## 2. Noto Sans SC

| 项目 | 内容 |
| --- | --- |
| 字体名称 | Noto Sans SC |
| 字重 | Regular（400） |
| 版本 | notofonts/noto-cjk `main` 分支（SubsetOTF/SC） |
| 官方主页 | https://fonts.google.com/noto/specimen/Noto+Sans+SC |
| 许可证 | **SIL Open Font License 1.1** |
| 许可证全文 | https://scripts.sil.org/OFL |
| 许可证文件 | https://github.com/notofonts/noto-cjk/blob/main/Sans/LICENSE |
| 获取地址 | `https://cdn.jsdelivr.net/gh/notofonts/noto-cjk@main/Sans/SubsetOTF/SC/NotoSansSC-Regular.otf` |

打包文件：`app/src/main/res/font/noto_sans_sc_regular.otf`

---

## 3. 修改说明（子集化）

两个文件均为原始字体的**子集化**产物，由 `fontTools` 的 `pyftsubset` 生成，脚本见
`.workbuddy/gen_subset.py`（生成字符表）与 `.workbuddy/run_subset.py`（执行子集化）。

### 3.1 保留的字符集

| 类别 | 内容 | 数量 |
| --- | --- | --- |
| GB2312 一级汉字 | 0xB0A1–0xD7F9 区，覆盖率 100%（3755/3755） | 3755 |
| ASCII | U+0020–007E（拉丁字母、数字、半角标点） | 95 |
| Latin-1 补充 | U+00A0–00FF（é ü ° · « » 等） | — |
| Latin Extended-A | U+0100–017F（含 ā á Ǎ 等拼音声调字母） | — |
| 通用标点 | U+2010–2027、U+2030–205E（破折号、引号、省略号、千分号等） | — |
| 箭头 | U+2190–21FF | — |
| 带圈数字 | U+2460–24FF（①②③） | — |
| CJK 标点 | U+3000–303F（。、《》「」【】·～等） | — |
| 全角字符 | U+FF01–FF65（，．！？（）：； 及全角英数） | — |

合计请求码位 **4581** 个；子集实际输出 **4357** 个 cmap 码位，差值是源字体
（SC 子集）本身不含的少数 Latin-Extended 字母与箭头符号，阅读场景下由系统
fallback 补齐，不影响中文显示。

### 3.2 子集化参数

```
pyftsubset <src>.otf \
  --text-file=subset_chars.txt \
  --output-file=<out>.otf \
  --no-hinting \
  --layout-features=ccmp,locl,liga,clig,kern,palt,vert,vrt2,rlig,calt \
  --drop-tables+=DSIG,LTSH,VDMX,hdmx,PCLT,BASE \
  --glyph-names \
  --no-subset-tables+=name
```

- 保留 `GSUB` / `GPOS` 的常用排版特性：连字、字距调整、CJK 竖排标点替换
  （`vert` / `vrt2`）；实测保留它们只增加约 47 KB。
- 未做任何字形轮廓改动，未重命名字体（OF L 允许的修改均已按许可证要求保留
  版权声明，见 `name` 表 0 号记录）。
- 输出格式沿用原始字体的 **CFF/OTF**（相比转为 glyf/TTF 体积更小）。

### 3.3 体积与 APK 增量（实测）

| 项目 | 字节数 | 说明 |
| --- | --- | --- |
| `noto_serif_sc_regular.otf` | 1,202,520 B（1,147 KB） | 子集后（未压缩） |
| `noto_sans_sc_regular.otf` | 859,944 B（840 KB） | 子集后（未压缩） |
| 合计（未压缩） | 2,062,464 B（1.97 MB） | 源字体合计 19.29 MB |
| APK 内 `res/9h.otf`（= serif） | 985,296 B | aapt2 deflate 压缩后 |
| APK 内 `res/oN.otf`（= sans） | 735,643 B | aapt2 deflate 压缩后 |
| **字体在 APK 中的净占用** | **1,720,939 B（1.641 MB）** | 这是增量的主体 |

`python .workbuddy/build.py release` 前后对比：

| 指标 | 数值 |
| --- | --- |
| 基线 `app-release.apk` | 20,796,927 B（19.833 MB） |
| 新增后 `app-release.apk` | 22,533,879 B（21.490 MB） |
| **增量** | **1,736,952 B = 1.656 MB** |
| 预算上限 | 3.00 MB → **PASS**（余量 1.34 MB） |

增量归因（按 APK zip 条目逐项比对）：字体 1,720,939 B + `resources.arsc` 256 B +
其余条目合计 15,559 B = 1,736,952 B。

---

## 4. 覆盖范围与回退策略

- **只替换阅读正文字族**：`prefs.fontFamilyIndex` 的 0（默认）/ 1（衬线）/
  2（黑体）三档走上述打包字体；3（等宽）保持系统等宽族；4（自定义 TTF）
  原样走 `Typeface.createFromFile`，未被改动。
- **UI 界面字体未改动**，仍使用系统通用字族，避免全 App 中文观感的回归风险。
- **生僻字**：不在子集中的汉字由 Android 的 Typeface fallback 链自动补齐
  （自定义字体同样受 fallback 机制保护），**不会显示为豆腐块**。
- **加载兜底**：`AppFonts.bundledFontFamily()` 在资源 id 无效或构造异常时
  退回原系统通用字族，任何情况下都不会崩溃或空白。

---

## 5. 再分发合规性

- 两款字体均为 **SIL OFL 1.1**：允许自由使用、修改、再分发（含商业用途），
  也允许随应用捆绑分发。
- 许可证第 4 条要求：再分发时须保留版权声明与许可证全文。本文件即为该声明；
  `name` 表 0 号记录（Copyright）在子集化时被完整保留（`--no-subset-tables+=name`）。
- **未使用**任何不可再分发的系统字体（如 Microsoft YaHei / SimSun）。
