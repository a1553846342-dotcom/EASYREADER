# Ambient audio credits

## 新反馈第 7 条分层混音版（2026-09-01）

七个场景音全部重制为**分层混音**（多 CC0 轨叠加 + 等功率循环交叉淡化 +
首尾响度均衡选段），由 `mix_one.py`（仓库外工程脚本）产出。层次结构：

| file | layers |
| --- | --- |
| `scene_rain.ogg` | Ylmir rain 变体3 ×1.0 + 变体1 ×0.40（瓦片） + Luke.RUSTLTD wind ×0.22 + rubberduck thunder ×3（0.30/0.20/0.12 稀疏单次） |
| `scene_snow.ogg` | TinyWorlds forest ×1.0 + wind ×0.20 |
| `scene_sakura.ogg` | isaiah658 birds（原始 48kHz 源，瓦片） ×1.0 + wind ×0.10 |
| `scene_firefly.ogg` | Wolfgang_ crickets ×1.0 + wind ×0.07（mp3 → ogg 升级） |
| `scene_ocean.ogg` | RandomMind Vistula 河海浪 320kbps 全新主床 ×1.0 + wind ×0.15 |
| `scene_campfire.ogg` | PagDev fireplace ×1.0 + crickets ×0.20（瓦片） + wind ×0.08 |
| `scene_night.ogg` | Siobhan Leachman cicada（Wikimedia 原始源，瓦片） ×1.0 + crickets ×0.35（瓦片） |

量化验收（`ambient_src/mix_report` + 编码后复验）：循环点首尾 0.5s 响度比
0.75–1.21（无音量突变），接缝样本跳变 ≤0.16（环境噪声自然样本差量级），
各场景 RMS −17.5 ~ −18.5dB（响度一致）。BBC Sound Effects 库经核实为
RemArc 个人/教育用途许可，**不适用**于应用分发，故未采用；全部素材维持 CC0。

---
（以下为原始素材来源记录）

Every file in this folder is **CC0 1.0 (public domain dedication)**. CC0 asks
for no attribution and imposes no share-alike, so nothing here puts a
condition on this mod or on anything built from it -- but the people who
recorded them are named anyway, because that is the decent thing and because
anyone replacing a file should know what they are replacing.

Nothing here is CC-BY or CC-BY-SA. That was a deliberate filter: a
share-alike recording would reach out and set terms on the rest of the mod,
and an attribution-only one would put a condition on every fork. Several
otherwise-good cricket and bird recordings on Wikimedia Commons were passed
over for exactly that reason. Pixabay, ZapSplat, Sonniss GDC bundles, Envato,
Adobe Stock and the YouTube Audio Library were also passed over -- they are
"royalty free" under their own terms, not CC0.

Verified on each asset's own page (not a search-filter result). Check date
for the added Tier-1 beds: 2026-08-09. Check date for the Tier-2 beds
(`cicadas.ogg`, `fire.ogg`): 2026-08-14. Check date for `grass.ogg`:
2026-08-14, on Fantozzi's own OGA page (author named, licence CC0 — the
page also points at Freesound pack 10338, also CC0). The OGA note says
the "Sand" steps read as grass; `Fantozzi-SandL1` was re-encoded to mono
Vorbis ~q5, 44.1 kHz, 0.44 s (one-shot, no loop seam).

| file | source | author | licence |
| --- | --- | --- | --- |
| `crickets.mp3` | [Crickets Ambient Noise - loopable](https://opengameart.org/content/crickets-ambient-noise-loopable) | Wolfgang_ | CC0 1.0 |
| `birds.ogg` | [Ambient Bird Sounds](https://opengameart.org/content/ambient-bird-sounds) | isaiah658 | CC0 1.0 |
| `rain.ogg` | [Rain (loopable)](https://opengameart.org/content/rain-loopable) | Ylmir | CC0 1.0 |
| `water.ogg` | [100 CC0 SFX #2](https://opengameart.org/content/100-cc0-sfx-2) | rubberduck | CC0 1.0 |
| `thunder.ogg` | [100 CC0 SFX #2](https://opengameart.org/content/100-cc0-sfx-2) | rubberduck | CC0 1.0 |
| `wind.ogg` | [wind1](https://opengameart.org/content/wind1) | Luke.RUSTLTD | CC0 1.0 |
| `forest.ogg` | [Forest Ambience](https://opengameart.org/content/forest-ambience) | TinyWorlds | CC0 1.0 |
| `cave.ogg` | [Loopable Dungeon Ambience](https://opengameart.org/content/loopable-dungeon-ambience) | JaggedStone | CC0 1.0 |
| `waves.ogg` | [Sea and river wave sounds](https://opengameart.org/content/sea-and-river-wave-sounds) | RandomMind | CC0 1.0 |
| `town.ogg` | [S13-05 Light cafe background walla](https://freesound.org/people/craigsmith/sounds/675073/) | craigsmith | CC0 1.0 |
| `indoor.ogg` | [Room white noise - Room Ambience](https://freesound.org/people/Littleboot/sounds/147300/) | Littleboot | CC0 1.0 |
| `cicadas.ogg` | [Chorus Cicada singing](https://commons.wikimedia.org/wiki/File:Chorus_Cicada_singing.ogg) | Siobhan Leachman (Ambrosia10) | CC0 1.0 |
| `fire.ogg` | [Fireplace Sound loop](https://opengameart.org/content/fireplace-sound-loop) | PagDev | CC0 1.0 |
| `grass.ogg` | [Fantozzi's Footsteps (Grass/Sand & Stone)](https://opengameart.org/content/fantozzis-footsteps-grasssand-stone) (`Fantozzi-SandL1`, mono Vorbis ~q5) | Fantozzi (submitted by qubodup) | CC0 1.0 |

`rain.ogg` is track 2 of the four in that pack; `water.ogg` is
`sfx100v2_loop_water_02.ogg` and `thunder.ogg` is `sfx100v2_thunder_01.ogg`,
both taken unedited out of the hundred-sound pack.

`wind.ogg` is a mono loop cut from `wind1.wav` (one of the five PureData
wind beds in that set). `forest.ogg` is a mono loop cut from
`Forest_Ambience.mp3`. `cave.ogg` is a mono loop cut from
`dungeon_ambient_1.ogg`. `waves.ogg` is a mono loop cut from the short
`VistulaShort.mp3` river/wave recording (not the 180 MB full hour).
`town.ogg` is a mono loop from craigsmith's vintage Hollywood walla transfer
(unintelligible murmur -- not spoken dialogue). `indoor.ogg` is a mono loop
from Littleboot's room-tone recording.

All six added beds were re-encoded to mono Ogg Vorbis (~q5, 44.1 kHz),
level-matched, and given a short crossfade seam so a looping Source does not
click on the wrap.

`cicadas.ogg` is a mono loop cut from Siobhan Leachman's 24 s recording of
*Amphipsalta zelandica* (Chorus Cicada) on Wikimedia Commons, page verified
CC0 1.0 Universal on 2026-08-14. `fire.ogg` is a mono loop cut from PagDev's
`fire.wav` fireplace recording (29 s, titled "Feuer" in the file metadata).
Both were re-encoded the same way as the Tier-1 beds (mono Vorbis ~q5,
44.1 kHz, loudness matched, crossfade seam).

Tier-2 keys that were searched and **not** shipped, because no CC0 file of
acceptable length and quality was on the asset's own page:

- `stream` — rubberduck `water_flowing.ogg` is 1.9 s (too short to loop as a
  bed). Freesound CC0 creek recordings (easy_thunder 264180 and others) are
  the right licence, but the preview CDN did not finish transferring a
  complete file this session. Wikimedia "Mountain Flowing Stream" /
  "Smooth Mountain Stream" were **deleted** in June 2026 for a copyright
  claim. Skipped rather than loop a 2 s sample.
- `waterfall` — Gen 1 has no waterfall clock or tile the mod already
  measures. No bed without a rule.
- `frogs` — several CC0 candidates (yaanick 570306, felix.blume 135561,
  gtjuks 348160) verified on their Freesound pages; Wikimedia
  `Frogs croak calling chorus at night.ogg` is **CC-BY-SA 4.0** and was
  discarded. Same CDN transfer problem as `stream`. Skipped.
- `owl` — Extx 277323 is a hunting call, not a bird; Wikimedia `Kcg-a̱kuluu
  (owl).ogg` is a spoken word. simongray's Ruru is CC0 but the file did not
  arrive complete. Skipped.
- `snow_wind` — craigsmith G56-21 Winter Wind is CC0 (44 s) and was the
  pick; file did not arrive complete. Snow already reuses the rain bed
  pitched down and boosts `wind`, so the gap is covered rather than silent.
- `shop` — no CC0 indoor-commercial walla that is unintelligible and
  distinct from the cafe walla already used as `town.ogg`. Skipped.

Also discarded on licence, even when the sound itself was good:

- qubodup [Fire Loop](https://opengameart.org/content/fire-loop) — **CC-BY 3.0**
- AntumDeluge [Fire Crackling](https://opengameart.org/content/fire-crackling) — CC0, but 2.9 s (one-shot, not a bed)
- Wikimedia `Frogs croak calling chorus at night.ogg` — **CC-BY-SA 4.0**

Tall-grass rustle search (2026-08-14), discarded rather than shipped:

- Pixabay / ZapSplat / Sonniss / Envato / Adobe Stock / YouTube Audio
  Library — rejected by name ("royalty free" is not CC0).
- Kenney Impact Sounds and similar packs — not a grass-in-meadow rustle
  (impacts / UI), skipped on fitness, not licence.
- [20 Rustles of dry leaves](https://opengameart.org/content/20-rustles-dry-leaves)
  — considered; the asset page fetch this session did not return a named
  author + CC0 line I could quote, so it was not used. Collection-page
  claims are not a licence.
- rubberduck `100 CC0 SFX #2` footsteps — already trusted CC0 (water /
  thunder came from it) but they are hard-surface steps, not meadow.
- Fantozzi Stone* files from the same pack — CC0, kept as unused siblings
  of the Sand step that shipped.

## Replacing one

Drop a file with the same name here and it is used instead -- the paths are
resolved at play time, not baked. Ogg Vorbis or MP3, either is fine.

Four of the original five loop, so **dead air on the ends matters**: a looping
source repeats its buffer with no gap, and a beat of silence at the tail
becomes a hole you hear every time round. `stripSilence` in
`lib/AmbientSound.lua` measures it off whatever it is given, so a replacement
file does not have to be tightly topped and tailed. What it actually found in
the original set is worth knowing, because it is not the file format you would
expect:

| file | head | tail |
| --- | --- | --- |
| `crickets.mp3` | 0 | 0 |
| `rain.ogg` | 0 | 0 |
| `water.ogg` | 0 | 0 |
| `birds.ogg` | 65 ms | 767 ms |
| `thunder.ogg` | 40 ms | 829 ms |

The MP3's encoder padding is already handled by the decoder; the two files
that needed trimming are Oggs whose recordist simply left the tape running.

Decoding a file costs about 100 ms for the longest one here, once per session,
on the frame that bed first comes up.

The original five also have a **synthesized fallback** -- a Game Boy channel
program in `lib/AmbientSound.lua` -- used when a file here is missing or will
not decode. The beds added later have **no chip fallback** (`chip = nil`):
if their file is missing they stay silent. Degrade quiet is the house default
for anything that never had a synth version. So deleting this folder costs the
quality of the original ambience and removes the new layers entirely, never
the feature that was already there.
