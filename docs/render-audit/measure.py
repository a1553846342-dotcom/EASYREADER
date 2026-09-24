# -*- coding: utf-8 -*-
"""
render-audit 像素测量脚本（纯标准库，无 PIL/numpy 依赖）。

用途：对比改前 / 改后截图在指定矩形区域内的平均亮度（0~255），
用于验证「elevation 阴影改自绘后观感不衰减」。

用法：
  python measure.py before_home.png after_home.png x0,y0,x1,y1 [x0,y0,x1,y1 ...]
"""
import sys
import struct
import zlib


def read_png(path):
    """极简 PNG 解码：只支持 8-bit RGB/RGBA/灰度，非隔行。返回 (w, h, rows[list[bytes RGB])"""
    with open(path, 'rb') as f:
        data = f.read()
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        raise ValueError('not a png: ' + path)
    pos = 8
    w = h = bitdepth = colortype = interlace = None
    idat = bytearray()
    palette = None
    trns = None
    while pos < len(data):
        length = struct.unpack('>I', data[pos:pos + 4])[0]
        ctype = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if ctype == b'IHDR':
            w, h, bitdepth, colortype, comp, filt, interlace = struct.unpack('>IIBBBBB', chunk)
        elif ctype == b'PLTE':
            palette = chunk
        elif ctype == b'tRNS':
            trns = chunk
        elif ctype == b'IDAT':
            idat.extend(chunk)
        elif ctype == b'IEND':
            break
    if bitdepth != 8:
        raise ValueError('unsupported bitdepth %s' % bitdepth)
    if interlace != 0:
        raise ValueError('unsupported interlace')
    raw = zlib.decompress(bytes(idat))
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[colortype]
    stride = w * channels
    # 反滤波
    out = bytearray(stride * h)
    prev = bytearray(stride)
    p = 0
    for y in range(h):
        ftype = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        if ftype == 1:  # Sub
            for i in range(channels, stride):
                line[i] = (line[i] + line[i - channels]) & 0xFF
        elif ftype == 2:  # Up
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif ftype == 3:  # Average
            for i in range(stride):
                a = line[i - channels] if i >= channels else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif ftype == 4:  # Paeth
            for i in range(stride):
                a = line[i - channels] if i >= channels else 0
                b = prev[i]
                c = prev[i - channels] if i >= channels else 0
                pa = abs(b - c)
                pb = abs(a - c)
                pc = abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        out[y * stride:(y + 1) * stride] = line
        prev = line

    def px(x, y):
        o = y * stride + x * channels
        if colortype == 2:
            return out[o], out[o + 1], out[o + 2]
        if colortype == 6:
            a = out[o + 3]
            if a == 255:
                return out[o], out[o + 1], out[o + 2]
            return tuple(int(v * a / 255.0) for v in out[o:o + 3])
        if colortype == 0:
            v = out[o]
            return v, v, v
        if colortype == 4:
            v = out[o]
            a = out[o + 1]
            return tuple(int(v * a / 255.0)) * 1 and (int(v * a / 255.0),) * 3
        if colortype == 3:
            idx = out[o]
            r, g, b = palette[idx * 3:idx * 3 + 3]
            return r, g, b
        raise ValueError('unsupported colortype %s' % colortype)

    return w, h, px


def luma(px):
    r, g, b = px
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def region_stats(px, w, h, rect):
    x0, y0, x1, y1 = rect
    x0 = max(0, min(w - 1, x0)); x1 = max(x0 + 1, min(w, x1))
    y0 = max(0, min(h - 1, y0)); y1 = max(y0 + 1, min(h, y1))
    total = 0.0
    n = 0
    mn, mx = 255.0, 0.0
    for y in range(y0, y1):
        for x in range(x0, x1):
            v = luma(px(x, y))
            total += v
            n += 1
            if v < mn:
                mn = v
            if v > mx:
                mx = v
    return total / n, mn, mx, n


def main():
    if len(sys.argv) < 4:
        print(__doc__)
        return 2
    before_path, after_path = sys.argv[1], sys.argv[2]
    rects = []
    for tok in sys.argv[3:]:
        x0, y0, x1, y1 = [int(v) for v in tok.split(',')]
        rects.append((x0, y0, x1, y1))
    wb, hb, pb = read_png(before_path)
    wa, ha, pa = read_png(after_path)
    print('before %dx%d  after %dx%d' % (wb, hb, wa, ha))
    print('%-24s %10s %10s %9s %9s' % ('rect', 'before', 'after', 'delta', 'delta%'))
    for r in rects:
        mb, _, _, _ = region_stats(pb, wb, hb, r)
        ma, _, _, _ = region_stats(pa, wa, ha, r)
        d = ma - mb
        pct = (d / mb * 100.0) if mb else 0.0
        print('%-24s %10.2f %10.2f %9.2f %8.2f%%' %
              ('%d,%d,%d,%d' % r, mb, ma, d, pct))
    return 0


if __name__ == '__main__':
    sys.exit(main())
