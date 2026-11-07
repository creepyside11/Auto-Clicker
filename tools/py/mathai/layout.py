"""Синтетический рендер целых выражений: дроби, корни, степени, шум фото."""

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

from .render import available_fonts


class Node:
    def metrics(self):
        """(w, ascent, descent)"""
        raise NotImplementedError

    def draw(self, img, x, baseline):
        raise NotImplementedError


class Glyph(Node):
    def __init__(self, ch, font):
        self.ch = ch
        self.font = font
        b = font.getbbox(ch, anchor='ls')
        self._w = max(1, int(round(font.getlength(ch))))
        self._asc = max(1, int(-b[1]))
        self._desc = max(0, int(b[3]))

    def metrics(self):
        return self._w, self._asc, self._desc

    def draw(self, img, x, baseline):
        ImageDraw.Draw(img).text((x, baseline), self.ch, fill=255, font=self.font, anchor='ls')
        return self._w


class HBox(Node):
    def __init__(self, children, gap=1):
        self.children = children
        self.gap = gap

    def metrics(self):
        ms = [c.metrics() for c in self.children]
        w = sum(m[0] for m in ms) + self.gap * max(0, len(ms) - 1)
        asc = max([m[1] for m in ms] or [0])
        desc = max([m[2] for m in ms] or [0])
        return w, asc, desc

    def draw(self, img, x, baseline):
        cx = x
        for c in self.children:
            w, _, _ = c.metrics()
            c.draw(img, cx, baseline)
            cx += w + self.gap
        return self.metrics()[0]


class Frac(Node):
    def __init__(self, num, den, font):
        self.num, self.den, self.font = num, den, font

    def _p(self):
        size = self.font.size
        return (max(3, int(size * 0.16)), max(1, int(size * 0.06)), max(4, int(size * 0.26)))

    def metrics(self):
        pad, bar, gap = self._p()
        nw, na, nd = self.num.metrics()
        dw, da, dd = self.den.metrics()
        w = max(nw, dw) + 2 * pad
        asc = na + nd + gap + bar + gap + da
        desc = dd
        return w, asc, desc

    def draw(self, img, x, baseline):
        pad, bar, gap = self._p()
        nw, na, nd = self.num.metrics()
        dw, da, dd = self.den.metrics()
        w, asc, desc = self.metrics()
        bar_y = baseline - da - gap - bar
        ImageDraw.Draw(img).rectangle(
            [x + pad // 2, bar_y, x + w - pad // 2, bar_y + bar - 1], fill=255)
        self.num.draw(img, x + (w - nw) // 2, bar_y - gap - nd)
        self.den.draw(img, x + (w - dw) // 2, baseline)
        return w


class Sqrt(Node):
    def __init__(self, inner, font):
        self.inner, self.font = inner, font
        sign_size = max(10, int(font.size * 0.95))
        self.sign = Glyph('√', ImageFont.truetype(font.path, sign_size))

    def metrics(self):
        iw, ia, idesc = self.inner.metrics()
        sw, sasc, sdesc = self.sign.metrics()
        pad = max(3, int(self.font.size * 0.16))
        bar = max(1, int(self.font.size * 0.07))
        w = sw + pad + iw + pad
        asc = max(sasc, ia + bar + max(2, int(self.font.size * 0.14)))
        desc = max(sdesc, idesc)
        return w, asc, desc

    def draw(self, img, x, baseline):
        sw, sasc, sdesc = self.sign.metrics()
        iw, ia, idesc = self.inner.metrics()
        pad = max(3, int(self.font.size * 0.16))
        bar = max(1, int(self.font.size * 0.07))
        w, asc, desc = self.metrics()
        self.sign.draw(img, x, baseline)
        bar_y = baseline - asc + 1
        x0 = x + sw - 2
        x1 = x + w - 1
        ImageDraw.Draw(img).rectangle([x0, bar_y, x1, bar_y + bar - 1], fill=255)
        self.inner.draw(img, x + sw + pad, baseline)
        return w


class Sup(Node):
    def __init__(self, base, exp, font):
        self.base, self.exp, self.font = base, exp, font
        self.lift = int(font.size * 0.46)

    def metrics(self):
        bw, ba, bd = self.base.metrics()
        ew, ea, ed = self.exp.metrics()
        asc = max(ba, self.lift + ea)
        return bw + ew, asc, max(bd, ed)

    def draw(self, img, x, baseline):
        bw, ba, bd = self.base.metrics()
        self.base.draw(img, x, baseline)
        self.exp.draw(img, x + bw, baseline - self.lift)
        return self.metrics()[0]


def make_font(size):
    fonts = available_fonts()
    path = fonts[np.random.randint(len(fonts))]
    return ImageFont.truetype(path, size)


def parse_layout(expr, font):
    expr = expr.replace('*', '×')
    """Рекурсивный разбор: sqrt(...), cbrt(...), (...), a^b (степень), a/b (дробь)."""
    nodes, _ = _parse(expr, 0, font, '')
    return HBox(nodes, 2)


def _is_num_tail(node):
    if isinstance(node, Glyph):
        return node.ch in '0123456789.,'
    if isinstance(node, Sup):
        return _is_num_tail(node.base)
    return False


def _sub_size(font, k):
    path = font.path
    return ImageFont.truetype(path, max(9, int(round(font.size * k))))


def _parse(s, i, font, stop):
    nodes = []
    while i < len(s):
        c = s[i]
        if c in stop:
            return nodes, i
        if s.startswith('sqrt(', i):
            inner, j = _parse(s, i + 5, font, ')')
            nodes.append(Sqrt(HBox(inner, 2), font))
            i = j + 1 if j < len(s) else j
            continue
        if s.startswith('cbrt(', i):
            inner, j = _parse(s, i + 5, font, ')')
            nodes.append(HBox([Glyph('∛', font), HBox(inner, 2)], 1))
            i = j + 1 if j < len(s) else j
            continue
        if c == '(':
            inner, j = _parse(s, i + 1, font, ')')
            nodes.append(HBox([Glyph('(', font), HBox(inner, 2), Glyph(')', font)], 0))
            i = j + 1 if j < len(s) else j
            continue
        if c == '^':
            j = i + 1
            efont = _sub_size(font, 0.66)
            if j < len(s) and s[j] == '(':
                inner, k = _parse(s, j + 1, efont, ')')
                k = k + 1 if k < len(s) else k
            else:
                inner, k = _parse(s, j, efont, '^*/+-)')
            base = nodes.pop() if nodes else Glyph('?', font)
            nodes.append(Sup(base, HBox(inner, 0), font))
            i = k
            continue
        if c == '/' and nodes:
            j = i + 1
            if j < len(s) and s[j] == '(':
                inner, k = _parse(s, j + 1, font, ')')
                k = k + 1 if k < len(s) else k
            else:
                inner, k = _parse(s, j, font, '^*/+-)')
            num = [nodes.pop()]
            # в числитель входит весь хвост числа: '100/4' -> 100/4, а не 1,0,(0/4)
            while nodes and _is_num_tail(nodes[-1]):
                num.insert(0, nodes.pop())
            nodes.append(Frac(HBox(num, 0), HBox(inner, 0), font))
            i = k
            continue
        nodes.append(Glyph(c, font))
        i += 1
    return nodes, i


def render_expression(expr, size=None, noise_level=0.0, rotate_deg=None, dark=False):
    size = size or int(np.random.randint(34, 58))
    font = make_font(size)
    root = parse_layout(expr, font)
    w, asc, desc = root.metrics()
    pad = int(size * 0.6)
    W = max(8, w + 2 * pad)
    H = max(8, asc + desc + 2 * pad)
    img = Image.new('L', (W, H), 0)
    root.draw(img, pad, pad + asc)
    arr = np.asarray(img, dtype=np.float32) / 255.0
    if noise_level > 0:
        arr = arr * (1.0 - noise_level) + np.random.rand(*arr.shape).astype(np.float32) * noise_level
        arr = np.asarray(Image.fromarray((arr * 255).astype(np.uint8), 'L').filter(
            ImageFilter.GaussianBlur(np.random.uniform(0.4, 1.2))), dtype=np.float32) / 255.0
    if rotate_deg is None:
        rotate_deg = float(np.random.uniform(-2.5, 2.5))
    if abs(rotate_deg) > 0.05:
        im = Image.fromarray((arr * 255).astype(np.uint8), 'L')
        im = im.rotate(rotate_deg, resample=Image.BILINEAR, expand=True, fillcolor=0)
        arr = np.asarray(im, dtype=np.float32) / 255.0
    if dark:
        return 1.0 - arr * 0.92
    return 1.0 - arr * 0.93
