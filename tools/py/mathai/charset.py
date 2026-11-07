"""Набор классов символов, которые распознаёт модель.

Модель предсказывает ровно один класс на один вырезанный глиф.
Порядок классов фиксирован и одинаков в Python и в Kotlin.
"""

CLASSES = [
    "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
    "+", "-", "×", "÷", "=",
    "(", ")", "^", ".", ",",
    "/", "%", "!", "x", "y",
    "π", "√", "∛",
]

NUM_CLASSES = len(CLASSES)
CLASS_TO_IDX = {c: i for i, c in enumerate(CLASSES)}

# Глиф -> как он попадает в текстовое выражение
GLYPH_TO_TOKEN = {
    "0": "0", "1": "1", "2": "2", "3": "3", "4": "4",
    "5": "5", "6": "6", "7": "7", "8": "8", "9": "9",
    "+": "+", "-": "-", "×": "*", "÷": "/", "=": "=",
    "(": "(", ")": ")", "^": "^", ".": ".", ",": ".",
    "/": "/", "%": "%", "!": "!", "x": "x", "y": "y",
    "π": "pi", "√": "sqrt", "∛": "cbrt",
}

# Глифы, которые пишутся как функция/идентификатор, а не как одиночный символ
MULTI_CHAR_GLYPHS = {"π", "√", "∛"}

# Размер входа модели
INPUT_SIZE = 32
INNER_SIZE = 24  # глиф вписывается в 24x24 и центрируется в 32x32


def _implicit_mul(a, b):
    """Нужен ли знак умножения между соседними глифами."""
    left_value = a in "0123456789.,)!" or a in ("x", "y", "π")
    right_value = b in "0123456789(xy" or b in ("π", "√", "∛")
    if not (left_value and right_value):
        return False
    if a == ")" and b == "(":
        return True
    if a in ("x", "y") and b in ("x", "y"):
        return True
    if a in "0123456789" and b in "0123456789":
        return False
    return True


def to_expression(glyphs):
    """Склеивает последовательность глифов в ASCII-выражение."""
    out = []
    prev = None
    for g in glyphs:
        tok = GLYPH_TO_TOKEN[g]
        if prev is not None and _implicit_mul(prev, g):
            out.append("*")
        out.append(tok)
        prev = g
    return "".join(out)
