"""Полный пайплайн распознавания: фото -> строка выражения.

Все шаги детерминированы и повторяются 1:1 в Kotlin:
  grayscale -> полярность -> бинаризация -> выравнивание наклона
  -> строки -> компоненты связности -> склейка в глифы
  -> классификация CNN -> структурная сборка (степени/дроби/корни).
"""

import numpy as np

from .charset import CLASSES, INPUT_SIZE, INNER_SIZE
from .render import normalize_glyph

# ---- параметры (те же константы в Kotlin) ----
MAX_SIDE = 1400
DOT_MIN_H = 0.055     # минимальная высота компоненты относительно строки
BAR_W_RATIO = 0.72    # ширина/высота глифа для "структурной" черты
BAR_H_RATIO = 0.30
MERGE_XGAP = 0.11     # зазор по x для склейки компонент в глиф
MERGE_VGAP = 0.22     # зазор по y
MERGE_OVERLAP = 0.30  # доля перекрытия по x
SUP_RATIO = 0.74      # высота надстрочного глифа относительно медианы
SUP_SHIFT = 0.10      # подъём центра надстрочника


class Comp:
    __slots__ = ('x0', 'x1', 'y0', 'y1', 'area')

    def __init__(self, x0, x1, y0, y1, area):
        self.x0, self.x1, self.y0, self.y1, self.area = x0, x1, y0, y1, area

    @property
    def w(self):
        return self.x1 - self.x0 + 1

    @property
    def h(self):
        return self.y1 - self.y0 + 1

    @property
    def cx(self):
        return (self.x0 + self.x1) * 0.5

    @property
    def cy(self):
        return (self.y0 + self.y1) * 0.5

    def __repr__(self):
        return "C(%d,%d,%d,%d,a=%d)" % (self.x0, self.x1, self.y0, self.y1, self.area)


def to_gray(rgb):
    """rgb: HxWx3 float 0..1 -> HxW float 0..1"""
    return 0.299 * rgb[:, :, 0] + 0.587 * rgb[:, :, 1] + 0.114 * rgb[:, :, 2]


def otsu(gray_u8):
    hist = np.bincount(gray_u8.ravel(), minlength=256).astype(np.float64)
    total = hist.sum()
    if total == 0:
        return 127
    prob = hist / total
    omega = np.cumsum(prob)
    mu = np.cumsum(prob * np.arange(256))
    mu_t = mu[-1]
    denom = omega * (1.0 - omega)
    denom[denom == 0] = 1e-12
    sigma = (mu_t * omega - mu) ** 2 / denom
    return int(np.argmax(sigma))


def integral(a):
    out = np.zeros((a.shape[0] + 1, a.shape[1] + 1), dtype=np.float64)
    out[1:, 1:] = a.cumsum(axis=0).cumsum(axis=1)
    return out


def box_mean(i, y0, y1, x0, x1):
    """Среднее по прямоугольнику через интегральное изображение (включительно)."""
    return (i[y1 + 1, x1 + 1] - i[y0, x1 + 1] - i[y1 + 1, x0] + i[y0, x0])


def binarize(gray):
    """Возвращает mask (uint8 0/1, 1 = чернила) и признак инверсии."""
    g = (np.clip(gray, 0, 1) * 255.0).astype(np.uint8)
    h, w = g.shape
    t = otsu(g)
    dark = g <= t
    frac_dark = dark.mean()
    # чернила — тот класс, которого меньше (текст обычно занимает < 50%)
    if frac_dark <= 0.5:
        ink = dark
        inverted = False
    else:
        ink = ~dark
        inverted = True
    ink_frac = ink.mean()
    if ink_frac < 0.002 or ink_frac > 0.45:
        # адаптивная бинаризация по локальному среднему (Sauvola-lite)
        win = max(9, int(min(h, w) / 24))
        if win % 2 == 0:
            win += 1
        half = win // 2
        gf = g.astype(np.float64) / 255.0
        ig = integral(gf)
        ig2 = integral(gf * gf)
        ys = np.arange(h)
        xs = np.arange(w)
        y0 = np.clip(ys - half, 0, h - 1)
        y1 = np.clip(ys + half, 0, h - 1)
        x0 = np.clip(xs - half, 0, w - 1)
        x1 = np.clip(xs + half, 0, w - 1)
        cnt = ((y1 - y0 + 1)[:, None] * (x1 - x0 + 1)[None, :]).astype(np.float64)
        s = (ig[y1 + 1][:, x1 + 1] - ig[y0][:, x1 + 1] - ig[y1 + 1][:, x0] + ig[y0][:, x0])
        s2 = (ig2[y1 + 1][:, x1 + 1] - ig2[y0][:, x1 + 1] - ig2[y1 + 1][:, x0] + ig2[y0][:, x0])
        mean = s / cnt
        var = np.maximum(s2 / cnt - mean * mean, 0.0)
        std = np.sqrt(var)
        thr = mean * (1.0 + 0.22 * (std / 0.5 - 1.0))
        local = gf < thr
        if local.mean() <= 0.5:
            ink = local
            inverted = False
        else:
            ink = ~local
            inverted = True
    return ink.astype(np.uint8), inverted


def rotate(gray, angle_deg):
    if abs(angle_deg) < 1e-6:
        return gray
    h, w = gray.shape
    a = np.deg2rad(angle_deg)
    ca, sa = np.cos(a), np.sin(a)
    cx, cy = (w - 1) * 0.5, (h - 1) * 0.5
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    xs = xs - cx
    ys = ys - cy
    sx = ca * xs + sa * ys + cx
    sy = -sa * xs + ca * ys + cy
    sx0 = np.floor(sx).astype(np.int32)
    sy0 = np.floor(sy).astype(np.int32)
    fx = sx - sx0
    fy = sy - sy0
    ok = (sx0 >= 0) & (sy0 >= 0) & (sx0 < w - 1) & (sy0 < h - 1)
    sx0c = np.clip(sx0, 0, w - 2)
    sy0c = np.clip(sy0, 0, h - 2)
    # фон за пределами кадра должен быть цветом бумаги, а не чёрным:
    # иначе бинаризация посчитает его чернилами
    out = np.full_like(gray, float(np.median(gray)))
    v = (gray[sy0c, sx0c] * (1 - fx) * (1 - fy)
         + gray[sy0c, sx0c + 1] * fx * (1 - fy)
         + gray[sy0c + 1, sx0c] * (1 - fx) * fy
         + gray[sy0c + 1, sx0c + 1] * fx * fy)
    out[ok] = v[ok]
    return out


def deskew(mask):
    """Оценка наклона по максимуму дисперсии проекции строк."""
    h, w = mask.shape
    best, best_score = 0.0, -1.0
    step = 1
    scale = 1.0
    small = mask
    if max(h, w) > 600:
        scale = 600.0 / max(h, w)
        ys = (np.arange(int(h * scale)) / scale).astype(np.int32)
        xs = (np.arange(int(w * scale)) / scale).astype(np.int32)
        small = mask[np.ix_(ys, xs)]
    for ang in range(-6, 7, step):
        rot = rotate(small.astype(np.float32), ang)
        proj = rot.sum(axis=1)
        score = float(np.var(proj))
        if score > best_score:
            best_score, best = score, float(ang)
    return best


def _raw_bands(mask, gap_tol=2):
    proj = mask.sum(axis=1)
    w = mask.shape[1]
    thr = max(1.0, 0.012 * w)
    rows = proj > thr
    bands = []
    y = 0
    h = len(rows)
    while y < h:
        if not rows[y]:
            y += 1
            continue
        y0 = y
        last = y
        while y < h:
            if rows[y]:
                last = y
                y += 1
            else:
                # считаем длину разрыва
                g = 0
                yy = y
                while yy < h and not rows[yy]:
                    g += 1
                    yy += 1
                if g > gap_tol or yy >= h:
                    break
                y = yy
        bands.append((y0, last))
        y = last + 1
    return bands


def find_lines(mask):
    bands = _raw_bands(mask, gap_tol=3)
    if not bands:
        return []
    heights = [b - a + 1 for a, b in bands]
    # тонкие полосы (черта дроби, винкулум) не должны занижать медиану,
    # иначе высокая конструкция не склеится обратно в одну строку
    big = [h for h in heights if h >= 0.35 * max(heights)]
    med = float(np.median(big)) if big else float(np.median(heights))
    merged = []
    for a, b in bands:
        if merged:
            pa, pb = merged[-1]
            gap = a - pb - 1
            combined = b - pa + 1
            if gap <= 0.60 * med and combined <= 4.5 * med:
                merged[-1] = (pa, b)
                continue
        merged.append((a, b))
    return merged


def components(mask, y0, y1):
    """Двухпроходная маркировка по строкам (run-based union-find)."""
    parent = {}

    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a

    def union(a, b):
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[rb] = ra

    prev_runs = []
    all_runs = []
    rid = 0
    for y in range(y0, y1 + 1):
        row = mask[y]
        runs = []
        x = 0
        w = len(row)
        while x < w:
            if row[x]:
                s = x
                while x < w and row[x]:
                    x += 1
                e = x - 1
                parent[rid] = rid
                cur = (rid, s, e, y)
                runs.append(cur)
                all_runs.append(cur)
                for (prid, ps, pe, py) in prev_runs:
                    if ps <= e + 1 and s <= pe + 1:
                        union(rid, prid)
                rid += 1
            else:
                x += 1
        prev_runs = runs

    groups = {}
    for (r, s, e, y) in all_runs:
        root = find(r)
        g = groups.get(root)
        if g is None:
            groups[root] = [s, e, y, y, e - s + 1]
        else:
            if s < g[0]:
                g[0] = s
            if e > g[1]:
                g[1] = e
            if y > g[3]:
                g[3] = y
            g[4] += e - s + 1
    out = []
    for g in groups.values():
        out.append(Comp(g[0], g[1], g[2], g[3], g[4]))
    out.sort(key=lambda c: c.x0)
    return out


def is_thin(c, ref):
    """Тонкий горизонтальный штрих: минус, черта дроби, винкулум, часть '='."""
    return (c.h <= 0.20 * ref and c.w >= 0.25 * ref and
            c.area >= max(3.0, 0.45 * c.w * c.h))


def keep_comp(c, ref):
    """Оставляет глиф или тонкий штрих (фильтр по высоте убивал черты)."""
    if c.h >= max(2.0, 0.075 * ref) and c.area >= max(5, 0.004 * ref * ref):
        return True
    return is_thin(c, ref)


def is_bar(c, ref):
    """Черта дроби: длинная и тонкая. Минус короче — он не черта."""
    return is_thin(c, ref) and c.w >= 0.70 * ref


def merge_strokes(comps, ref):
    """Склеивает пары/тройки тонких штрихов в '=' и '÷'."""
    out = list(comps)
    changed = True
    while changed:
        changed = False
        for i in range(len(out)):
            for j in range(i + 1, len(out)):
                a, b = out[i], out[j]
                if not (is_thin(a, ref) and is_thin(b, ref)):
                    continue
                # '=' и '÷' — это два параллельных штриха одинаковой длины
                ov = min(a.x1, b.x1) - max(a.x0, b.x0) + 1
                if ov < 0.75 * min(a.w, b.w):
                    continue
                gap = max(0, max(a.y0, b.y0) - min(a.y1, b.y1))
                if gap > 0.45 * ref:
                    continue
                out[i] = Comp(min(a.x0, b.x0), max(a.x1, b.x1),
                              min(a.y0, b.y0), max(a.y1, b.y1), a.area + b.area)
                out.pop(j)
                changed = True
                break
            if changed:
                break
    return out


def group_glyphs(comps, ref):
    """Склеивает компоненты одного глифа ('=', '÷', '%', '!') в один бокс."""
    comps = merge_strokes(comps, ref)
    items = [c for c in comps if not is_bar(c, ref)]
    bars = [c for c in comps if is_bar(c, ref)]
    items.sort(key=lambda c: c.x0)
    changed = True
    while changed:
        changed = False
        i = 0
        while i < len(items):
            j = i + 1
            while j < len(items):
                a, b = items[i], items[j]
                xgap = max(0, max(a.x0, b.x0) - min(a.x1, b.x1))
                vgap = max(0, max(a.y0, b.y0) - min(a.y1, b.y1))
                ov = min(a.x1, b.x1) - max(a.x0, b.x0) + 1
                ovr = ov / float(max(1, min(a.w, b.w)))
                combined_w = max(a.x1, b.x1) - min(a.x0, b.x0) + 1
                close = (ovr >= MERGE_OVERLAP and vgap <= MERGE_VGAP * ref) or \
                        (xgap <= MERGE_XGAP * ref and vgap <= MERGE_VGAP * ref * 0.8)
                if close and combined_w <= 1.1 * ref:
                    merged = Comp(min(a.x0, b.x0), max(a.x1, b.x1),
                                  min(a.y0, b.y0), max(a.y1, b.y1), a.area + b.area)
                    items[i] = merged
                    items.pop(j)
                    changed = True
                    break
                j += 1
            i += 1
    items.sort(key=lambda c: c.x0)
    return items, bars


def crop_norm(mask, c):
    """Вырезает компоненту, бинаризует по 0.5, слегка размывает -> 32x32."""
    y0, y1 = max(0, c.y0), c.y1
    x0, x1 = max(0, c.x0), c.x1
    sub = mask[y0:y1 + 1, x0:x1 + 1].astype(np.float32)
    if sub.size == 0:
        return np.zeros((INPUT_SIZE, INPUT_SIZE), dtype=np.float32)
    # 3x3 гаусс, как при обучении (мягкие края вместо жёсткой маски)
    k = np.array([[1, 2, 1], [2, 4, 2], [1, 2, 1]], dtype=np.float32) / 16.0
    p = np.pad(sub, 1, mode='constant')
    sm = (p[:-2, :-2] * k[0, 0] + p[:-2, 1:-1] * k[0, 1] + p[:-2, 2:] * k[0, 2] +
          p[1:-1, :-2] * k[1, 0] + p[1:-1, 1:-1] * k[1, 1] + p[1:-1, 2:] * k[1, 2] +
          p[2:, :-2] * k[2, 0] + p[2:, 1:-1] * k[2, 1] + p[2:, 2:] * k[2, 2])
    return normalize_glyph(sm)


def _value_end(s):
    if not s:
        return False
    if s.endswith(')'):
        return True
    return s[-1] in '0123456789xyπ'


def _value_start(s):
    if not s:
        return False
    if s.startswith('sqrt') or s.startswith('cbrt'):
        return True
    return s[0] in '0123456789(xyπ'


def _is_number(s):
    return len(s) > 0 and all(ch in '0123456789.' for ch in s)


def is_sup(g, base):
    """Надстрочник: мельче базы, выше неё и заканчивается в её верхней части."""
    if g.box.h > 0.95 * base.box.h:
        return False
    if g.box.cy >= base.box.cy - 0.15 * base.box.h:
        return False
    return g.box.y1 <= base.box.y0 + 0.55 * base.box.h


def wrap_expr(s):
    """Скобки только там, где без них изменится смысл."""
    if s and all(ch not in s for ch in '+-*/^'):
        return s
    return '(' + s + ')'


def join_tokens(tokens):
    out = ''
    prev = None
    for t in tokens:
        if prev is not None and _value_end(prev) and _value_start(t):
            if not (_is_number(prev) and _is_number(t)):
                out += '*'
        out += t
        prev = t
    return out


ROOT_SIGNS = ('√', '∛')


def root_fn(cls):
    return 'cbrt' if cls == '∛' else 'sqrt'


class Glyph:
    __slots__ = ('box', 'cls', 'conf')

    def __init__(self, box, cls, conf):
        self.box, self.cls, self.conf = box, cls, conf


def _pick_vinculums(glyphs, bars, ref):
    """Сопоставляет каждому знаку корня его черту (винкулум)."""
    vin = {}
    taken = set()
    for gi, g in enumerate(glyphs):
        if g.cls not in ROOT_SIGNS:
            continue
        best, best_d = None, None
        for bi, b in enumerate(bars):
            if bi in taken:
                continue
            if b.x0 - g.box.x1 > 0.45 * ref or b.x1 < g.box.x1 or b.y0 > g.box.cy:
                continue
            d = abs(b.y0 - g.box.y0)
            if best is None or d < best_d:
                best, best_d = bi, d
        if best is not None:
            vin[gi] = best
            taken.add(best)
    return vin


def assemble(glyphs, bars, ref, line_cy):
    """Собирает выражение из глифов и структурных черт."""
    if not glyphs and not bars:
        return ''
    tokens = []
    used_bars = set()
    used_glyphs = set()

    # 0) корень, у которого черта слилась со знаком (широкий бокс)
    for gi, g in enumerate(glyphs):
        if g.cls not in ROOT_SIGNS or gi in used_glyphs:
            continue
        if g.box.w <= 1.15 * ref:
            continue
        used_glyphs.add(gi)
        rad = []
        for j, h in enumerate(glyphs):
            if j in used_glyphs:
                continue
            if h.box.cx >= g.box.x0 - 0.12 * ref and h.box.cx <= g.box.x1 + 0.12 * ref \
                    and h.box.cy > g.box.y0 - 0.15 * ref:
                rad.append((j, h))
        for j, _ in rad:
            used_glyphs.add(j)
        inner = assemble([h for _, h in rad], [], ref, line_cy)
        tokens.append(('x', g.box.x0, root_fn(g.cls) + '(' + inner + ')'))

    vin = _pick_vinculums(glyphs, bars, ref)

    # 1) дроби (черты, не занятые корнями)
    for bi, b in enumerate(bars):
        if bi in used_bars or bi in vin.values():
            continue
        num, den = [], []
        for j, h in enumerate(glyphs):
            if j in used_glyphs:
                continue
            if h.box.cx < b.x0 - 0.14 * ref or h.box.cx > b.x1 + 0.14 * ref:
                continue
            if h.box.cy < b.y0:
                num.append((j, h))
            elif h.box.cy > b.y1:
                den.append((j, h))
        if not num or not den:
            continue
        used_bars.add(bi)
        for j, _ in num + den:
            used_glyphs.add(j)
        # винкулумы корней внутри числителя/знаменателя уже израсходованы —
        # иначе они выйдут наружу как лишние минусы
        for j, _ in num + den:
            if j in vin:
                used_bars.add(vin[j])
        # вложенные дроби: черты внутри числителя/знаменателя уходят в рекурсию
        nb = [bars[vin[j]] for j, _ in num if j in vin]
        db = [bars[vin[j]] for j, _ in den if j in vin]
        for bj, b2 in enumerate(bars):
            if bj == bi or bj in used_bars:
                continue
            if b2.x1 < b.x0 or b2.x0 > b.x1:
                continue
            if b2.y1 < b.y0:
                nb.append(b2)
            elif b2.y0 > b.y1:
                db.append(b2)
        for b2 in nb + db:
            for bj, b3 in enumerate(bars):
                if b3 is b2:
                    used_bars.add(bj)
        n = assemble([h for _, h in num], nb, ref, line_cy)
        d = assemble([h for _, h in den], db, ref, line_cy)
        if n and d:
            tokens.append(('x', b.x0, wrap_expr(n) + '/' + wrap_expr(d)))

    # 2) корни со своей чертой
    for gi, bi in vin.items():
        g = glyphs[gi]
        if gi in used_glyphs or bi in used_bars:
            continue
        b = bars[bi]
        used_bars.add(bi)
        used_glyphs.add(gi)
        rad = []
        for j, h in enumerate(glyphs):
            if j in used_glyphs:
                continue
            if h.box.cx >= b.x0 - 0.12 * ref and h.box.cx <= b.x1 + 0.12 * ref \
                    and h.box.y0 <= b.y1 + 1.2 * ref and h.box.cy > b.y0 - 0.15 * ref:
                rad.append((j, h))
        for j, _ in rad:
            used_glyphs.add(j)
        inner = assemble([h for _, h in rad], [], ref, line_cy)
        tokens.append(('x', g.box.x0, root_fn(g.cls) + '(' + inner + ')'))

    # 3) обычные глифы + надстрочники (степени)
    rest = [(j, g) for j, g in enumerate(glyphs) if j not in used_glyphs]
    rest.sort(key=lambda p: p[1].box.x0)
    base = None
    i = 0
    while i < len(rest):
        j, g = rest[i]
        if base is not None and is_sup(g, base):
            sup = []
            while i < len(rest):
                j2, g2 = rest[i]
                if is_sup(g2, base):
                    sup.append(g2)
                    i += 1
                else:
                    break
            cls_list = [('.' if gg.cls == ',' else gg.cls) for gg in sup]
            if cls_list and cls_list[0] == '^':
                cls_list = cls_list[1:]
            inner = join_tokens(cls_list)
            if inner:
                tokens.append(('x', sup[0].box.x0, '^' + wrap_expr(inner)))
            continue
        tokens.append(('x', g.box.x0, '.' if g.cls == ',' else g.cls))
        base = g
        i += 1

    # неиспользованные черты — это минусы
    for bi, b in enumerate(bars):
        if bi in used_bars:
            continue
        tokens.append(('x', b.x0, '-'))

    tokens.sort(key=lambda t: t[1])
    return join_tokens([t[2] for t in tokens])


def recognize_lines(gray, net):
    """Возвращает список распознанных строк (текст) с уверенностью."""
    h, w = gray.shape
    scale = 1.0
    if max(h, w) > MAX_SIDE:
        scale = MAX_SIDE / float(max(h, w))
        nh, nw = int(h * scale), int(w * scale)
        yi = (np.arange(nh) / scale).astype(np.int32).clip(0, h - 1)
        xi = (np.arange(nw) / scale).astype(np.int32).clip(0, w - 1)
        gray = gray[np.ix_(yi, xi)]
    mask, inverted = binarize(gray)
    if mask.sum() == 0:
        return []
    ang = deskew(mask)
    if abs(ang) > 0.4:
        gray = rotate(gray, ang)
        mask, _ = binarize(gray)
    lines = find_lines(mask)
    results = []
    for (y0, y1) in lines:
        line_h = y1 - y0 + 1
        if line_h < 8:
            continue
        comps = components(mask, y0, y1)
        if not comps:
            continue
        tall = [c.h for c in comps if c.h >= 0.18 * line_h]
        ref = float(np.median(tall)) if tall else float(line_h)
        ref = max(8.0, ref)
        comps = [c for c in comps if keep_comp(c, ref)]
        if not comps:
            continue
        glyph_boxes, bars = group_glyphs(comps, ref)
        if not glyph_boxes and not bars:
            continue
        crops = [crop_norm(mask, c) for c in glyph_boxes]
        probs = predict_proba_safe(net, crops)
        glyphs = []
        conf_sum = 0.0
        for i, c in enumerate(glyph_boxes):
            idx = int(probs[i].argmax())
            glyphs.append(Glyph(c, CLASSES[idx], float(probs[i][idx])))
            conf_sum += float(probs[i][idx])
        cy = float(np.mean([(c.y0 + c.y1) * 0.5 for c in glyph_boxes])) if glyph_boxes else (y0 + y1) * 0.5
        expr = assemble(glyphs, bars, ref, cy)
        conf = conf_sum / max(1, len(glyph_boxes))
        results.append((expr, conf))
    return results


_PRED = None


def predict_proba_safe(net, crops):
    from .model import predict_proba
    if len(crops) == 0:
        return np.zeros((0, len(CLASSES)), dtype=np.float32)
    return predict_proba(net, np.asarray(crops, dtype=np.float32))
