"""Сквозная проверка: синтетическая строка -> пайплайн -> текст -> ответ."""

import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))) + '/py')

import numpy as np
from PIL import Image

from mathai.charset import CLASSES
from mathai.layout import render_expression
from mathai.model import load_model, predict_proba
from mathai.pipeline import recognize_lines
from mathai.solver import solve, SolveError

MODEL = os.environ.get('MODEL', 'model/model.bin')


def _num(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


def same_result(a, b):
    """Сравнивает ответы по смыслу, а не по записи."""
    if a.get('mode') != b.get('mode'):
        return False
    if a.get('mode') == 'poly':
        return False
    if a.get('mode') == 'expression':
        x, y = _num(a.get('value_dec')), _num(b.get('value_dec'))
        return x is not None and y is not None and abs(x - y) < 1e-6
    return a.get('roots') == b.get('roots')


def canon(expr):
    """Приводит разные записи одного выражения к одной форме."""
    t = expr.replace('×', '*').replace('÷', '/').replace('π', 'pi')
    out = ''
    i = 0
    while i < len(t):
        if t.startswith('cbrt(', i):
            out += 'cbrt('
            i += 5
            continue
        if t[i] == '∛':
            out += 'cbrt('
            depth = 1
            j = i + 1
            start = j
            while j < len(t) and depth > 0:
                if t[j] in '([(':
                    depth += 1
                elif t[j] in ')]':
                    depth -= 1
                j += 1
            inner = t[start:j - 1] if depth == 0 else t[start:j]
            out += inner + ')'
            i = j if depth == 0 else j
            continue
        if t[i] == '√':
            out += 'sqrt('
            depth = 1
            j = i + 1
            start = j
            while j < len(t) and depth > 0:
                if t[j] in '([(':
                    depth += 1
                elif t[j] in ')]':
                    depth -= 1
                j += 1
            inner = t[start:j - 1] if depth == 0 else t[start:j]
            out += inner + ')'
            i = j if depth == 0 else j
            continue
        out += t[i]
        i += 1
    # '*' рядом с буквой, скобкой или функцией — это неявное умножение
    res = ''
    for k, ch in enumerate(out):
        if ch == '*':
            prev = out[k - 1] if k > 0 else ''
            nxt = out[k + 1] if k + 1 < len(out) else ''
            if (prev.isalpha() or prev in ')(π') or (nxt.isalpha() or nxt == '('):
                continue
        res += ch
    return res

CASES = [
    "7^2", "2^10", "4/4", "6/3", "x^2-5x+6", "sqrt(16)+5^2", "12^3", "722",
    "375", "9+3*4", "(3)+(13)+1/5", "(6^2)-(sqrt(25))", "sqrt(36)", "cbrt(64)",
    "cbrt(27)", "5^2", "1/6", "2/3+1/6", "8*7-9", "100/4", "sqrt(81)-4",
    "3^3-2^3", "7*8", "15-6+2", "(2+3)*4", "45/9", "sqrt(144)/2", "10^2/4",
    "6^2+8^2", "2^3*5", "9*9", "81/9", "x+5=12", "2x=8", "3x-4=11",
    "x^2=16", "5!-3", "sqrt(9)+sqrt(16)", "12/3/2", "7^2-3^2",
]


def render_case(expr, seed):
    np.random.seed(seed)
    return render_expression(expr, size=int(38 + seed % 12),
                             rotate_deg=float((seed % 7) - 3))


def main():
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 40
    outdir = sys.argv[2] if len(sys.argv) > 2 else None
    cases = CASES[:n]
    net = load_model(MODEL)

    ok_line = ok_val = 0
    bad = []
    blobs = []
    for i, e in enumerate(cases):
        img = render_case(e, 1000 + i)
        lines = recognize_lines(img, net)
        got = canon(" ".join(e for e, _ in lines).replace(" ", ""))
        want = canon(e.replace(" ", ""))
        try:
            r = solve(e)
        except SolveError:
            r = {'mode': 'poly'}
        try:
            r2 = solve(got)
            same_val = same_result(r, r2)
        except Exception:
            same_val = False
        if got == want:
            ok_line += 1
        elif len(bad) < 20:
            bad.append("  %-22s -> %-22s  %s" % (want, got, "значение ok" if same_val else "MISMATCH"))
        if same_val:
            ok_val += 1
        blobs.append((img, want))

    print("точность строки: %.1f%%  точность значения: %.1f%%  (n=%d)" % (
        100.0 * ok_line / len(cases), 100.0 * ok_val / len(cases), len(cases)))
    for b in bad:
        print(b)

    if outdir:
        os.makedirs(outdir, exist_ok=True)
        labels = []
        for i, (img, want) in enumerate(blobs):
            Image.fromarray((img * 255).astype(np.uint8)).save(
                os.path.join(outdir, 'case%03d.png' % i))
            labels.append({'file': 'case%03d.png' % i, 'expr': want})
        with open(os.path.join(outdir, 'labels.json'), 'w', encoding='utf-8') as f:
            json.dump(labels, f, ensure_ascii=False, indent=1)
        with open(os.path.join(outdir, 'labels.tsv'), 'w', encoding='utf-8') as f:
            for r in labels:
                f.write('%s\t%s\n' % (r['file'], r['expr']))
        with open(os.path.join(outdir, 'lines.bin'), 'wb') as f:
            f.write(b'MLN1')
            f.write(np.int32(len(blobs)).tobytes())
            for img, want in blobs:
                u8 = (img * 255).astype(np.uint8)
                name = want.encode('utf-8')
                f.write(np.int32(len(name)).tobytes())
                f.write(name)
                f.write(np.int32(u8.shape[1]).tobytes())
                f.write(np.int32(u8.shape[0]).tobytes())
                f.write(u8.tobytes())
        print("сохранено %d картинок и lines.bin в %s" % (len(blobs), outdir))


if __name__ == '__main__':
    main()
