"""Загрузка экспортированных весов и инференс (numpy)."""

import struct

import numpy as np

from .cnn import Conv, Net, softmax
from .charset import NUM_CLASSES


def load_model(path):
    with open(path, 'rb') as f:
        magic = f.read(4)
        if magic != b'MAI1':
            raise ValueError('плохой файл модели')
        count = struct.unpack('<i', f.read(4))[0]
        tensors = []
        for _ in range(count):
            size = struct.unpack('<i', f.read(4))[0]
            arr = np.frombuffer(f.read(size * 4), dtype='<f4').astype(np.float32)
            tensors.append(arr)
    net = Net(NUM_CLASSES, seed=0)
    layers = net.layers()
    for i, l in enumerate(layers):
        if isinstance(l, Conv):
            # экспорт: (cout, cin, ky*kx) -> рабочая раскладка (cin*ky*kx, cout)
            w = tensors[2 * i].reshape(l.cout, l.cin, l.k, l.k)
            w = w.transpose(2, 3, 1, 0).reshape(l.k * l.k * l.cin, l.cout)
        else:
            w = tensors[2 * i].reshape(l.w.shape)
        b = tensors[2 * i + 1].reshape(l.b.shape)
        l.w = np.ascontiguousarray(w, dtype=np.float32)
        l.b = np.ascontiguousarray(b, dtype=np.float32)
    return net


def predict_proba(net, X):
    """X: (n,32,32) float 0..1"""
    out = []
    for i in range(0, len(X), 256):
        xb = np.asarray(X[i:i + 256], dtype=np.float32)[:, :, :, None]
        out.append(softmax(net.forward(xb, train=False)))
    return np.concatenate(out) if out else np.zeros((0, NUM_CLASSES), dtype=np.float32)
