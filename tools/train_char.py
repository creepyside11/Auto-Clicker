"""Обучение классификатора глифов и экспорт весов в model/model.bin."""

import json
import os
import struct
import sys
import time

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))) + '/py')

from mathai.charset import CLASSES, NUM_CLASSES
from mathai.cnn import Net, Adam, softmax
from mathai.render import build_dataset

DATA = os.environ.get('DATA', 'data/glyphs.npy')
MODEL_DIR = os.environ.get('MODEL_DIR', 'model')
EPOCHS = int(os.environ.get('EPOCHS', '12'))
BATCH = int(os.environ.get('BATCH', '64'))
LIMIT = int(os.environ.get('LIMIT', '0'))
LR = float(os.environ.get('LR', '1e-3'))
CLIP = float(os.environ.get('CLIP', '5.0'))
WARMUP = int(os.environ.get('WARMUP', '150'))


def batches(n, batch, rng):
    idx = rng.permutation(n)
    for i in range(0, n - batch + 1, batch):
        yield idx[i:i + batch]


def shift_aug(xb, rng):
    """Сдвиг без заворота краёв (roll переносил бы чернила на другую сторону)."""
    n, h, w, c = xb.shape
    out = np.zeros_like(xb)
    for i in range(n):
        dy = rng.randint(-2, 3)
        dx = rng.randint(-2, 3)
        ys0, ys1 = max(0, dy), min(h, h + dy)
        xs0, xs1 = max(0, dx), min(w, w + dx)
        yd0, yd1 = max(0, -dy), min(h, h - dy)
        xd0, xd1 = max(0, -dx), min(w, w - dx)
        out[i, yd0:yd1, xd0:xd1, :] = xb[i, ys0:ys1, xs0:xs1, :]
    return out


def evaluate(net, X, y):
    correct = 0
    for i in range(0, len(X), 256):
        xb = X[i:i + 256].astype(np.float32) / 255.0
        xb = xb[:, :, :, None]
        pred = net.forward(xb, train=False).argmax(axis=1)
        correct += int((pred == y[i:i + 256]).sum())
    return correct / float(len(X))


def per_class_report(net, X, y):
    preds = []
    for i in range(0, len(X), 256):
        xb = X[i:i + 256].astype(np.float32) / 255.0
        xb = xb[:, :, :, None]
        preds.append(net.forward(xb, train=False).argmax(axis=1))
    pred = np.concatenate(preds)
    rep = {}
    for ci, ch in enumerate(CLASSES):
        m = y == ci
        if m.sum():
            rep[ch] = round(float((pred[m] == ci).mean()), 4)
    return rep


def save_model(net, model_dir, val, test_acc, rep):
    os.makedirs(model_dir, exist_ok=True)
    tensors = net.export_weights()
    with open(os.path.join(model_dir, 'model.bin'), 'wb') as f:
        f.write(b'MAI1')
        f.write(struct.pack('<i', len(tensors)))
        for t in tensors:
            f.write(struct.pack('<i', t.size))
            f.write(np.asarray(t, dtype='<f4').tobytes())
    with open(os.path.join(model_dir, 'classes.txt'), 'w', encoding='utf-8') as f:
        f.write("\n".join(CLASSES) + "\n")
    size = os.path.getsize(os.path.join(model_dir, 'model.bin'))
    meta = {
        "classes": CLASSES,
        "input_size": 32,
        "inner_size": 24,
        "arch": "conv16-conv32-conv64-fc128-fc%d" % NUM_CLASSES,
        "params": int(sum(l.w.size + l.b.size for l in net.layers())),
        "model_bytes": size,
        "val_accuracy": round(float(val), 4),
        "test_accuracy": None if test_acc is None else round(float(test_acc), 4),
        "per_class_test": rep,
        "trained_at": time.strftime("%Y-%m-%d %H:%M:%S"),
    }
    with open(os.path.join(model_dir, 'meta.json'), 'w', encoding='utf-8') as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)
    return size


def main():
    t0 = time.time()
    X = np.load(DATA)
    y = np.load(DATA.replace('.npy', '_y.npy'))
    if LIMIT > 0 and LIMIT < len(X):
        X, y = X[:LIMIT], y[:LIMIT]
    n = len(X)
    rng = np.random.RandomState(7)
    perm = rng.permutation(n)
    X, y = X[perm], y[perm]
    n_val = max(2000, n // 12)
    Xval, yval = X[:n_val], y[:n_val]
    Xtr, ytr = X[n_val:], y[n_val:]
    print("train %d val %d" % (len(Xtr), len(Xval)), flush=True)

    net = Net(NUM_CLASSES, seed=11)
    opt = Adam(net.layers(), lr=LR)
    best = -1.0
    best_state = None
    ntr = len(Xtr)
    step_no = 0
    for ep in range(EPOCHS):
        opt.lr = LR * (0.5 * (1 + np.cos(np.pi * ep / EPOCHS)))
        tot = 0.0
        seen = 0
        nb = 0
        for bi in batches(ntr, BATCH, rng):
            xb = Xtr[bi].astype(np.float32) / 255.0
            xb = xb[:, :, :, None]
            xb = shift_aug(xb, rng)
            yb = ytr[bi]
            if step_no < WARMUP:
                opt.lr = LR * (step_no + 1) / float(WARMUP) * (0.5 * (1 + np.cos(np.pi * ep / EPOCHS)))
            logits = net.forward(xb, train=True)
            p = softmax(logits)
            # label smoothing
            eps = 0.05
            d = p.copy()
            d[np.arange(len(bi)), yb] -= (1 - eps)
            d -= eps / NUM_CLASSES
            d /= len(bi)
            loss = -np.log(np.clip(p[np.arange(len(bi)), yb], 1e-9, 1.0)).mean()
            net.backward(d.astype(np.float32))
            gw, gb = net.grads()
            norm = 0.0
            for g in list(gw) + list(gb):
                norm += float(np.dot(g.ravel(), g.ravel()))
            norm = norm ** 0.5
            if norm > CLIP:
                k = CLIP / (norm + 1e-9)
                for g in list(gw) + list(gb):
                    g *= k
            opt.step(gw, gb)
            step_no += 1
            nb += 1
            tot += float(loss) * len(bi)
            seen += len(bi)
            if nb % 150 == 0:
                print('   ep %d batch %4d loss %.3f lr %.2e' % (ep + 1, nb, float(loss), opt.lr), flush=True)
        val = evaluate(net, Xval, yval)
        print("epoch %2d  loss %.4f  val %.4f  (%.1fs)" % (
            ep + 1, tot / max(seen, 1), val, time.time() - t0), flush=True)
        if val > best:
            best = val
            best_state = [(l.w.copy(), l.b.copy()) for l in net.layers()]
            save_model(net, MODEL_DIR, best, None, None)
            print("   сохранён чекпоинт (val %.4f)" % best, flush=True)

    for l, (w, b) in zip(net.layers(), best_state):
        l.w = w
        l.b = b
    print("best val %.4f" % best, flush=True)

    # независимый тест: другие шрифты/аугментации (другой seed)
    Xt, yt = build_dataset(per_class=120, seed=999, verbose=False)
    test_acc = evaluate(net, Xt, yt)
    print("independent test %.4f" % test_acc, flush=True)
    rep = per_class_report(net, Xt, yt)
    print("per class:", json.dumps(rep, ensure_ascii=False), flush=True)

    size = save_model(net, MODEL_DIR, best, test_acc, rep)
    print("model.bin %.1f KB  total %.1fs" % (size / 1024.0, time.time() - t0), flush=True)


if __name__ == '__main__':
    main()
