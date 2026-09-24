# -*- coding: utf-8 -*-
"""
阴影剖面分析器（纯标准库）。

不依赖截图里元素的绝对位置（改前/改后 Tab 栏可能因滚动折叠而位置不同），
改为「先定位面板边缘，再按相对偏移采样」，把背景基准与阴影贡献分离出来：

    shadow_contribution(off) = bg_reference - luma(edge ± off)

用法：
  python profile.py before_home.png after_home.png [x0 x1]
"""
import sys
from measure import read_png, luma

OFFSETS = (2, 6, 12, 24, 40, 70, 110, 160)


def row_luma(px, y, x0, x1):
    tot = 0.0
    n = 0
    for x in range(x0, x1, 3):
        tot += luma(px(x, y))
        n += 1
    return tot / n


def analyse(path, x0=120, x1=960):
    w, h, px = read_png(path)
    # 在底部 500px 内逐行求平均
    rows = {}
    for y in range(h - 500, h):
        rows[y] = row_luma(px, y, x0, x1)
    ys = sorted(rows)
    # 背景基准：底部区域的最亮 5% 行的均值（远离面板的纯背景）
    top5 = sorted(rows.values())[-max(1, len(ys) // 20):]
    bg = sum(top5) / len(top5)
    # 面板：连续低于 bg-25 的行区间
    panel = [y for y in ys if rows[y] < bg - 25]
    if not panel:
        return None
    ptop, pbot = min(panel), max(panel)
    prof_above = {}
    prof_below = {}
    for off in OFFSETS:
        ya = ptop - off
        yb = pbot + off
        prof_above[off] = rows[ya] if ya in rows else float('nan')
        prof_below[off] = rows[yb] if yb in rows else float('nan')
    return dict(size=(w, h), bg=bg, ptop=ptop, pbot=pbot,
                above=prof_above, below=prof_below)


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    a, b = sys.argv[1], sys.argv[2]
    x0, x1 = 120, 960
    if len(sys.argv) >= 5:
        x0, x1 = int(sys.argv[3]), int(sys.argv[4])
    ra, rb = analyse(a, x0, x1), analyse(b, x0, x1)
    for tag, r in (('before', ra), ('after', rb)):
        if r is None:
            print(tag, '未定位到面板')
            continue
        print('%s  %dx%d  bg=%.2f  面板 y=[%d,%d] (高 %dpx)' %
              (tag, r['size'][0], r['size'][1], r['bg'], r['ptop'], r['pbot'],
               r['pbot'] - r['ptop']))
    print()
    print('%-6s | %-22s | %-22s' % ('off', 'before 阴影强度(亮度压低)', 'after 阴影强度(亮度压低)'))
    print('%-6s | %10s %10s | %10s %10s' % ('', '上方', '下方', '上方', '下方'))
    for off in OFFSETS:
        ca = ra['bg'] - ra['above'][off]
        cb = ra['bg'] - ra['below'][off]
        da = rb['bg'] - rb['above'][off]
        db = rb['bg'] - rb['below'][off]
        print('%-6d | %10.2f %10.2f | %10.2f %10.2f' % (off, ca, cb, da, db))
    return 0


if __name__ == '__main__':
    sys.exit(main())
