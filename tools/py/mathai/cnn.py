"""Крошечная CNN на чистом numpy: свёртки, пулинг, dense, Adam.

Раскладка внутри обучения — NHWC (быстро через GEMM).
На экспорт веса уходят в порядке (out_channel, in_channel, ky, kx),
поэтому Kotlin-ядро не зависит от внутренней раскладки.

Архитектура:
  32x32x1 -> conv3x3(16) -> relu -> maxpool2 -> 16x16x16
          -> conv3x3(32) -> relu -> maxpool2 -> 8x8x32
          -> conv3x3(64) -> relu -> maxpool2 -> 4x4x64
          -> flatten(1024) -> dense(128) -> relu -> dense(28)
"""

import numpy as np


def _strided_view(xp, n, ho, wo, c, k):
    """im2col без копирования: (n, ho, wo, k, k, c)."""
    s = xp.strides
    shape = (n, ho, wo, k, k, c)
    strides = (s[0], s[1], s[2], s[1], s[2], s[3])
    return np.lib.stride_tricks.as_strided(xp, shape=shape, strides=strides, writeable=False)


def relu(x):
    return np.maximum(x, 0.0)


class Conv:
    """Свёртка 3x3 (или kxk) со stride 1, NHWC."""

    def __init__(self, cin, cout, k, pad, rng):
        self.cin, self.cout, self.k, self.pad = cin, cout, k, pad
        self.w = (rng.randn(cin * k * k, cout) * np.sqrt(2.0 / (cin * k * k))).astype(np.float32)
        self.b = np.zeros(cout, dtype=np.float32)
        self.gw = np.zeros_like(self.w)
        self.gb = np.zeros_like(self.b)
        self.cache = None

    def forward(self, x, train=False):
        n, h, w, c = x.shape
        k, pad = self.k, self.pad
        xp = np.pad(x, ((0, 0), (pad, pad), (pad, pad), (0, 0))) if pad else x
        ho = h + 2 * pad - k + 1
        wo = w + 2 * pad - k + 1
        view = _strided_view(xp, n, ho, wo, c, k)
        cols = view.reshape(n * ho * wo, c * k * k)
        out = cols @ self.w + self.b
        out = out.reshape(n, ho, wo, self.cout)
        if train:
            self.cache = (cols, x.shape, xp.shape, ho, wo)
        return out

    def backward(self, dout):
        cols, xshape, xpshape, ho, wo = self.cache
        n, h, w, c = xshape
        k, pad = self.k, self.pad
        d = dout.reshape(-1, self.cout)
        self.gw = (cols.T @ d).astype(np.float32)
        self.gb = d.sum(axis=0).astype(np.float32)
        dcols = (d @ self.w.T).reshape(n, ho, wo, k, k, c)
        dxp = np.zeros(xpshape, dtype=np.float32)
        for ki in range(k):
            for kj in range(k):
                dxp[:, ki:ki + ho, kj:kj + wo, :] += dcols[:, :, :, ki, kj, :]
        if pad:
            return dxp[:, pad:-pad, pad:-pad, :]
        return dxp


class MaxPool:
    """Макспулинг 2x2 без argmax/take_along_axis — в разы быстрее."""

    def __init__(self):
        self.cache = None

    @staticmethod
    def _quad(x, ho, wo):
        return (
            x[:, 0:ho * 2:2, 0:wo * 2:2, :],
            x[:, 0:ho * 2:2, 1:wo * 2:2, :],
            x[:, 1:ho * 2:2, 0:wo * 2:2, :],
            x[:, 1:ho * 2:2, 1:wo * 2:2, :],
        )

    def forward(self, x, train=False):
        n, h, w, c = x.shape
        ho, wo = h // 2, w // 2
        a, b, cc, d = self._quad(x, ho, wo)
        mx = np.maximum(np.maximum(a, b), np.maximum(cc, d))
        if train:
            m0 = a >= mx
            m1 = (~m0) & (b >= mx)
            m2 = (~m0) & (~m1) & (cc >= mx)
            m3 = (~m0) & (~m1) & (~m2)
            self.cache = (x.shape, m0, m1, m2, m3)
        return mx

    def backward(self, dout):
        xshape, m0, m1, m2, m3 = self.cache
        n, h, w, c = xshape
        ho, wo = h // 2, w // 2
        out = np.zeros(xshape, dtype=np.float32)
        out[:, 0:ho * 2:2, 0:wo * 2:2, :] = dout * m0
        out[:, 0:ho * 2:2, 1:wo * 2:2, :] = dout * m1
        out[:, 1:ho * 2:2, 0:wo * 2:2, :] = dout * m2
        out[:, 1:ho * 2:2, 1:wo * 2:2, :] = dout * m3
        return out


class Dense:
    def __init__(self, cin, cout, rng):
        self.w = (rng.randn(cin, cout) * np.sqrt(2.0 / cin)).astype(np.float32)
        self.b = np.zeros(cout, dtype=np.float32)
        self.gw = np.zeros_like(self.w)
        self.gb = np.zeros_like(self.b)
        self.cache = None

    def forward(self, x, train=False):
        if train:
            self.cache = x
        return x @ self.w + self.b

    def backward(self, dout):
        x = self.cache
        self.gw = (x.T @ dout).astype(np.float32)
        self.gb = dout.sum(axis=0).astype(np.float32)
        return (dout @ self.w.T).astype(np.float32)


class Net:
    def __init__(self, num_classes=28, seed=0):
        rng = np.random.RandomState(seed)
        self.c1 = Conv(1, 16, 3, 1, rng)
        self.p1 = MaxPool()
        self.c2 = Conv(16, 32, 3, 1, rng)
        self.p2 = MaxPool()
        self.c3 = Conv(32, 64, 3, 1, rng)
        self.p3 = MaxPool()
        self.f1 = Dense(1024, 128, rng)
        self.f2 = Dense(128, num_classes, rng)
        self.num_classes = num_classes
        self.acts = None

    def layers(self):
        return [self.c1, self.c2, self.c3, self.f1, self.f2]

    def forward(self, x, train=False):
        """x: (n,32,32,1) NHWC."""
        a1 = self.c1.forward(x, train)
        r1 = relu(a1)
        p1 = self.p1.forward(r1, train)
        a2 = self.c2.forward(p1, train)
        r2 = relu(a2)
        p2 = self.p2.forward(r2, train)
        a3 = self.c3.forward(p2, train)
        r3 = relu(a3)
        p3 = self.p3.forward(r3, train)
        # каналы-первыми: тот же порядок, что во flatten у Kotlin-ядра
        flat = p3.transpose(0, 3, 1, 2).reshape(p3.shape[0], -1)
        a4 = self.f1.forward(flat, train)
        r4 = relu(a4)
        logits = self.f2.forward(r4, train)
        if train:
            self.acts = (a1, a2, a3, p3, a4)
        return logits

    def backward(self, dlogits):
        a1, a2, a3, p3, a4 = self.acts
        da4 = self.f2.backward(dlogits) * (a4 > 0)
        dflat = self.f1.backward(da4)
        n, h3, w3, c3 = p3.shape
        dr3 = self.p3.backward(dflat.reshape(n, c3, h3, w3).transpose(0, 2, 3, 1))
        da3 = dr3 * (a3 > 0)
        dr2 = self.c3.backward(da3)
        da2 = self.p2.backward(dr2) * (a2 > 0)
        dr1 = self.c2.backward(da2)
        da1 = self.p1.backward(dr1) * (a1 > 0)
        self.c1.backward(da1)

    def grads(self):
        return ([l.gw for l in self.layers()], [l.gb for l in self.layers()])

    def export_weights(self):
        """Вес свёрток -> (cout, cin, ky, kx) для Kotlin-ядра."""
        out = []
        for l in self.layers():
            if isinstance(l, Conv):
                k = l.k
                # l.w строки упорядочены как (ky, kx, ci); для Kotlin нужно
                # (cout, ci, ky, kx)
                w = l.w.reshape(k, k, l.cin, l.cout).transpose(3, 2, 0, 1)
                out.append(np.ascontiguousarray(w).reshape(l.cout, l.cin * k * k).copy())
            else:
                out.append(l.w.copy())
            out.append(l.b.copy())
        return out


def softmax(z):
    z = z - z.max(axis=1, keepdims=True)
    e = np.exp(z)
    return e / e.sum(axis=1, keepdims=True)


class Adam:
    def __init__(self, params, lr=1e-3):
        self.params = params
        self.lr = lr
        self.m = [np.zeros_like(p.w) for p in params] + [np.zeros_like(p.b) for p in params]
        self.v = [np.zeros_like(p.w) for p in params] + [np.zeros_like(p.b) for p in params]
        self.t = 0

    def step(self, grads_w, grads_b):
        self.t += 1
        b1, b2, eps = 0.9, 0.999, 1e-8
        n = len(self.params)
        allg = list(grads_w) + list(grads_b)
        allp = [p.w for p in self.params] + [p.b for p in self.params]
        for i in range(2 * n):
            g = allg[i]
            self.m[i] = b1 * self.m[i] + (1 - b1) * g
            self.v[i] = b2 * self.v[i] + (1 - b2) * (g * g)
            mh = self.m[i] / (1 - b1 ** self.t)
            vh = self.v[i] / (1 - b2 ** self.t)
            allp[i] -= self.lr * mh / (np.sqrt(vh) + eps)
