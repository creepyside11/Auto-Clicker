"""Синтетическая генерация изображений глифов для обучения.

Нормализация глифа при обучении и при инференсе описана одной
функцией `normalize_glyph`, поэтому модель видит одинаково
устроенные картинки и там, и там.
"""

import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

from .charset import CLASSES, INNER_SIZE, INPUT_SIZE

PREFIX = "/data/data/com.termux/files/usr"
FONT_CANDIDATES = [
    PREFIX + "/share/fonts/TTF/DejaVuSans.ttf",
    PREFIX + "/share/fonts/TTF/DejaVuSans-Bold.ttf",
    PREFIX + "/share/fonts/TTF/DejaVuSerif.ttf",
    PREFIX + "/share/fonts/TTF/DejaVuSerif-Bold.ttf",
    PREFIX + "/share/fonts/TTF/DejaVuSansMono.ttf",
    PREFIX + "/share/fonts/TTF/DejaVuSansCondensed.ttf",
    PREFIX + "/share/fonts/TTF/DejaVuMathTeXGyre.ttf",
    "/system/fonts/DroidSans.ttf",
    "/system/fonts/DroidSans-Bold.ttf",
    "/system/fonts/DroidSansMono.ttf",
    "/system/fonts/CutiveMono.ttf",
    "/system/fonts/CarroisGothicSC-Regular.ttf",
    "/system/fonts/ComingSoon.ttf",
    "/system/fonts/DancingScript-Regular.ttf",
]


def available_fonts():
    out = []
    for p in FONT_CANDIDATES:
        if os.path.exists(p):
            try:
                ImageFont.truetype(p, 48)
                out.append(p)
            except Exception:
                pass
    return out


def normalize_glyph(gray, size=INPUT_SIZE, inner=INNER_SIZE):
    """gray: 2D float array (0..1, 1 = чернила), обрезанный по bbox.

    Вписывает глиф в inner x inner с сохранением пропорций и центрирует
    в size x size. Та же логика реализована в Kotlin.
    """
    h, w = gray.shape
    if h == 0 or w == 0:
        return np.zeros((size, size), dtype=np.float32)
    scale = inner / float(max(h, w))
    nh = max(1, int(round(h * scale)))
    nw = max(1, int(round(w * scale)))
    img = Image.fromarray((gray * 255.0).clip(0, 255).astype(np.uint8), mode="L")
    img = img.resize((nw, nh), Image.BILINEAR)
    arr = np.asarray(img, dtype=np.float32) / 255.0
    canvas = np.zeros((size, size), dtype=np.float32)
    y0 = (size - nh) // 2
    x0 = (size - nw) // 2
    canvas[y0:y0 + nh, x0:x0 + nw] = arr
    return canvas


def bbox_of(gray, thresh=0.5):
    mask = gray > thresh
    if not mask.any():
        return None
    rows = np.where(mask.any(axis=1))[0]
    cols = np.where(mask.any(axis=0))[0]
    return rows[0], rows[-1] + 1, cols[0], cols[-1] + 1


def _dilate(b, k):
    out = b
    for _ in range(k):
        p = np.pad(out, 1, mode="constant")
        out = np.maximum.reduce([
            p[:-2, :-2], p[:-2, 1:-1], p[:-2, 2:],
            p[1:-1, :-2], p[1:-1, 1:-1], p[1:-1, 2:],
            p[2:, :-2], p[2:, 1:-1], p[2:, 2:],
        ])
    return out


def _erode(b):
    p = np.pad(b, 1, mode="constant", constant_values=1)
    return np.minimum.reduce([
        p[:-2, :-2], p[:-2, 1:-1], p[:-2, 2:],
        p[1:-1, :-2], p[1:-1, 1:-1], p[1:-1, 2:],
        p[2:, :-2], p[2:, 1:-1], p[2:, 2:],
    ])


class GlyphRenderer:
    def __init__(self, seed=0):
        self.rng = np.random.RandomState(seed)
        self.fonts = available_fonts()
        if not self.fonts:
            raise RuntimeError("не найдено ни одного шрифта")
        self.font_glyphs = {}
        for path in self.fonts:
            font = ImageFont.truetype(path, 48)
            ok = []
            for c in CLASSES:
                try:
                    mask = font.getmask(c)
                    bb = mask.getbbox()
                    if bb is None:
                        continue
                    if (bb[2] - bb[0]) < 6 or (bb[3] - bb[1]) < 8:
                        continue
                    if sum(1 for v in mask if v > 96) < 40:
                        continue
                    ok.append(c)
                except Exception:
                    pass
            self.font_glyphs[path] = ok

    def _draw(self, ch, font, size):
        canvas = Image.new("L", (size * 2, size * 2), 0)
        d = ImageDraw.Draw(canvas)
        ox = self.rng.uniform(0, size)
        oy = self.rng.uniform(0, size)
        d.text((ox, oy), ch, fill=255, font=font)
        return canvas

    def _pick_font(self, ch):
        idx = int(self.rng.randint(len(self.fonts)))
        for _ in range(24):
            path = self.fonts[idx]
            if ch in self.font_glyphs[path]:
                return path
            idx = int(self.rng.randint(len(self.fonts)))
        for i, path in enumerate(self.fonts):
            if ch in self.font_glyphs[path]:
                return path
        return self.fonts[0]

    def sample(self, ch):
        path = self._pick_font(ch)
        fsize = int(self.rng.randint(34, 62))
        font = ImageFont.truetype(path, fsize)
        img = self._draw(ch, font, 64)
        angle = float(self.rng.uniform(-13, 13))
        img = img.rotate(angle, resample=Image.BILINEAR, expand=True, fillcolor=0)
        if self.rng.rand() < 0.6:
            shear = float(self.rng.uniform(-0.22, 0.22))
            img = img.transform(
                img.size, Image.AFFINE,
                (1, shear, -shear * img.size[1] / 2.0, 0, 1, 0),
                resample=Image.BILINEAR, fillcolor=0,
            )
        arr = np.asarray(img, dtype=np.float32) / 255.0
        binar = (arr > 0.45).astype(np.uint8)
        t = self.rng.rand()
        ink_before = int(binar.sum())
        if t < 0.24:
            binar = _dilate(binar, 1 if self.rng.rand() < 0.75 else 2)
        elif t < 0.52:
            thin = _erode(binar)
            # если эрозия почти стёрла глиф — оставляем исходный вариант
            if thin.sum() >= 0.55 * ink_before and thin.sum() > 30:
                binar = thin
        arr = np.asarray(
            Image.fromarray((binar * 255).astype(np.uint8), "L").filter(
                ImageFilter.GaussianBlur(float(self.rng.uniform(0.3, 1.1)))
            ),
            dtype=np.float32,
        ) / 255.0
        bb = bbox_of(arr, 0.35)
        if bb is None:
            return np.zeros((INPUT_SIZE, INPUT_SIZE), dtype=np.float32)
        # у знака корня сверху часто пририсована черта над подкоренным
        if ch == '√' and self.rng.rand() < 0.45:
            y0, y1, x0, x1 = bb
            gh = max(4, y1 - y0)
            length = int(gh * self.rng.uniform(0.6, 1.8))
            barh = max(1, int(gh * 0.07))
            xx0 = max(0, x1 - 2)
            arr[y0:y0 + barh, xx0:xx0 + length] = 1.0
            bb = bbox_of(arr, 0.35)
            if bb is None:
                return np.zeros((INPUT_SIZE, INPUT_SIZE), dtype=np.float32)
        y0, y1, x0, x1 = bb
        if (y1 - y0) < 5 or (x1 - x0) < 2 or int((arr > 0.4).sum()) < 25:
            return self.sample(ch)
        out = normalize_glyph(arr[y0:y1, x0:x1])
        # отбраковка мусора: перераздутый или почти пустой глиф только мешает
        ink = float((out > 0.4).mean())
        lo, hi = (0.02, 0.80) if ch == '.' else (0.015, 0.42)
        if not (lo <= ink <= hi):
            return self.sample(ch)
        if self.rng.rand() < 0.5:
            noise = self.rng.randn(*out.shape).astype(np.float32) * self.rng.uniform(0.02, 0.09)
            out = np.clip(out + noise, 0.0, 1.0)
        if self.rng.rand() < 0.3:
            out = np.clip(out * self.rng.uniform(0.75, 1.25), 0.0, 1.0)
        if self.rng.rand() < 0.12:
            ys, xs = np.nonzero(out > 0.4)
            if len(ys) > 10:
                n = max(1, int(len(ys) * self.rng.uniform(0.02, 0.12)))
                pick = self.rng.choice(len(ys), n, replace=False)
                out[ys[pick], xs[pick]] *= 0.15
        return out.astype(np.float32)


def build_dataset(per_class=2200, seed=1234, verbose=True):
    rng = np.random.RandomState(seed)
    renderer = GlyphRenderer(seed=seed)
    n = len(CLASSES) * per_class
    X = np.zeros((n, INPUT_SIZE, INPUT_SIZE), dtype=np.uint8)
    y = np.zeros((n,), dtype=np.int64)
    k = 0
    for ci, ch in enumerate(CLASSES):
        for _ in range(per_class):
            img = renderer.sample(ch)
            X[k] = (img * 255).astype(np.uint8)
            y[k] = ci
            k += 1
        if verbose:
            print("  %s: %d" % (ch, per_class), flush=True)
    perm = rng.permutation(n)
    return X[perm], y[perm]
