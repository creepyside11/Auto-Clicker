"""Решатель выражений: точная арифметика, корни, степени, факториал,
проценты, тригонометрия, пошаговое решение и линейные/квадратные уравнения.

Это эталонная реализация: Kotlin-версия повторяет её логику.
"""

import math
from fractions import Fraction

PI = math.pi

FUNCS = {'sqrt', 'cbrt', 'sin', 'cos', 'tan', 'log', 'ln', 'abs', 'exp'}


class SolveError(Exception):
    pass


# ---------- лексер ----------

def tokenize(s):
    out = []
    i = 0
    n = len(s)
    while i < n:
        c = s[i]
        if c in ' \t\r\n':
            i += 1
            continue
        if c.isdigit() or (c == '.' and i + 1 < n and s[i + 1].isdigit()):
            j = i
            while j < n and (s[j].isdigit() or s[j] == '.'):
                j += 1
            txt = s[i:j]
            if txt.count('.') > 1:
                raise SolveError('плохое число: ' + txt)
            out.append(('num', txt))
            i = j
            continue
        if c == 'π':
            out.append(('num', 'pi'))
            i += 1
            continue
        if c.isalpha():
            j = i
            while j < n and s[j].isalpha():
                j += 1
            word = s[i:j]
            low = word.lower()
            if low in FUNCS:
                out.append(('func', low))
            elif low == 'pi':
                out.append(('num', 'pi'))
            else:
                for ch in word:
                    out.append(('var', ch.lower()))
            i = j
            continue
        if c in '+-*/^!%(),':
            out.append(('op', c))
            i += 1
            continue
        if c == '×':
            out.append(('op', '*'))
            i += 1
            continue
        if c == '÷':
            out.append(('op', '/'))
            i += 1
            continue
        if c == '−':
            out.append(('op', '-'))
            i += 1
            continue
        if c == '√':
            out.append(('func', 'sqrt'))
            i += 1
            continue
        if c == '∛':
            out.append(('func', 'cbrt'))
            i += 1
            continue
        raise SolveError('неизвестный символ: ' + c)
    return out


# ---------- AST ----------

class Node:
    pass


class Num(Node):
    def __init__(self, v, text=None):
        self.v = v
        self.text = text


class Var(Node):
    def __init__(self, name):
        self.name = name


class Un(Node):
    def __init__(self, op, a):
        self.op, self.a = op, a


class Bin(Node):
    def __init__(self, op, a, b):
        self.op, self.a, self.b = op, a, b


class Func(Node):
    def __init__(self, name, a):
        self.name, self.a = name, a


class Post(Node):
    def __init__(self, op, a):
        self.op, self.a = op, a


# ---------- парсер ----------

class Parser:
    def __init__(self, tokens):
        self.t = tokens
        self.i = 0

    def peek(self):
        return self.t[self.i] if self.i < len(self.t) else (None, None)

    def next(self):
        tok = self.peek()
        self.i += 1
        return tok

    def expect(self, ch):
        kind, val = self.next()
        if kind != 'op' or val != ch:
            raise SolveError('ожидалось "%s"' % ch)

    def parse(self):
        node = self.expr()
        if self.i != len(self.t):
            raise SolveError('лишние символы в конце')
        return node

    def expr(self):
        node = self.term()
        while True:
            kind, val = self.peek()
            if kind == 'op' and val in '+-':
                self.next()
                node = Bin(val, node, self.term())
            else:
                return node

    def term(self):
        node = self.unary()
        while True:
            kind, val = self.peek()
            if kind == 'op' and val in '*/':
                self.next()
                node = Bin(val, node, self.unary())
            elif self._implicit():
                node = Bin('*', node, self.unary())
            else:
                return node

    def _implicit(self):
        kind, val = self.peek()
        if kind in ('num', 'var', 'func'):
            return True
        if kind == 'op' and val == '(':
            return True
        return False

    def unary(self):
        kind, val = self.peek()
        if kind == 'op' and val in '+-':
            self.next()
            return Un(val, self.unary())
        return self.power()

    def power(self):
        base = self.postfix()
        kind, val = self.peek()
        if kind == 'op' and val == '^':
            self.next()
            return Bin('^', base, self.unary())
        return base

    def postfix(self):
        node = self.atom()
        while True:
            kind, val = self.peek()
            if kind == 'op' and val in '!%':
                self.next()
                node = Post(val, node)
            else:
                return node

    def atom(self):
        kind, val = self.next()
        if kind == 'num':
            if val == 'pi':
                return Num(PI, 'π')
            return Num(Fraction(val), val)
        if kind == 'var':
            return Var(val)
        if kind == 'func':
            if self.peek() == ('op', '('):
                self.next()
                arg = self.expr()
                self.expect(')')
                return Func(val, arg)
            return Func(val, self.unary())
        if kind == 'op' and val == '(':
            node = self.expr()
            self.expect(')')
            return node
        raise SolveError('неожиданный символ: %s' % (val,))


def parse(s):
    return Parser(tokenize(s)).parse()


# ---------- значения ----------

def is_float(v):
    return isinstance(v, float)


def fmt_num(v):
    if is_float(v):
        if math.isnan(v) or math.isinf(v):
            return str(v)
        if abs(v - round(v)) < 1e-12 and abs(v) < 1e15:
            return str(int(round(v)))
        return ('%.10g' % v)
    v = Fraction(v)
    if v.denominator == 1:
        return str(v.numerator)
    return '%d/%d' % (v.numerator, v.denominator)


def to_float(v):
    return float(v)


def _exact(v):
    return not is_float(v)


def num_add(a, b):
    if _exact(a) and _exact(b):
        return Fraction(a) + Fraction(b)
    return float(a) + float(b)


def num_sub(a, b):
    if _exact(a) and _exact(b):
        return Fraction(a) - Fraction(b)
    return float(a) - float(b)


def num_mul(a, b):
    if _exact(a) and _exact(b):
        return Fraction(a) * Fraction(b)
    return float(a) * float(b)


def num_div(a, b):
    if (not is_float(b)) and Fraction(b) == 0:
        raise SolveError('деление на ноль')
    if _exact(a) and _exact(b):
        return Fraction(a) / Fraction(b)
    return float(a) / float(b)


def perfect_root(x, k):
    """Точный корень k-й степени из Fraction, если он существует."""
    if is_float(x):
        return None
    f = Fraction(x)
    if f < 0 and k % 2 == 0:
        return None
    sign = -1 if f < 0 else 1
    f = abs(f)
    rn = int(round(f.numerator ** (1.0 / k)))
    rd = int(round(f.denominator ** (1.0 / k)))
    for cand_n in (rn - 1, rn, rn + 1):
        for cand_d in (rd - 1, rd, rd + 1):
            if cand_n > 0 and cand_d > 0 and cand_n ** k == f.numerator and cand_d ** k == f.denominator:
                return Fraction(sign * cand_n, cand_d)
    return None


def num_sqrt(a):
    if _exact(a):
        if Fraction(a) < 0:
            raise SolveError('корень из отрицательного числа')
        r = perfect_root(a, 2)
        if r is not None:
            return r
    v = float(a)
    if v < 0:
        raise SolveError('корень из отрицательного числа')
    return math.sqrt(v)


def num_cbrt(a):
    if _exact(a):
        r = perfect_root(a, 3)
        if r is not None:
            return r
    v = float(a)
    return math.copysign(abs(v) ** (1.0 / 3.0), v)


def num_pow(a, b):
    if _exact(a) and _exact(b) and Fraction(b).denominator == 1:
        e = int(Fraction(b))
        if abs(e) <= 512:
            if Fraction(a) == 0 and e < 0:
                raise SolveError('деление на ноль')
            return Fraction(a) ** e
    if _exact(b) and Fraction(b) == Fraction(1, 2):
        return num_sqrt(a)
    if _exact(b) and Fraction(b) == Fraction(1, 3):
        return num_cbrt(a)
    base = float(a)
    exp = float(b)
    if base < 0:
        # отрицательное основание в дробной степени
        if abs(exp - round(exp)) < 1e-12:
            return float(base) ** float(round(exp))
        raise SolveError('отрицательное основание в дробной степени')
    if base == 0 and exp < 0:
        raise SolveError('деление на ноль')
    try:
        return base ** exp
    except OverflowError:
        raise SolveError('слишком большое число')


def num_fact(a):
    if is_float(a) or Fraction(a).denominator != 1:
        raise SolveError('факториал только для целых')
    n = int(Fraction(a))
    if n < 0 or n > 170:
        raise SolveError('факториал вне диапазона 0..170')
    return Fraction(math.factorial(n))


def _trig_exact(name, arg):
    """Точные значения для очевидных случаев (аргумент 0)."""
    if is_float(arg):
        return None
    if Fraction(arg) == 0:
        return Fraction(0) if name in ('sin', 'tan') else Fraction(1)
    return None


def num_trig(name, a):
    v = to_float(a)
    ex = _trig_exact(name, a)
    if ex is not None:
        return ex
    if name == 'sin':
        return math.sin(v)
    if name == 'cos':
        return math.cos(v)
    if name == 'tan':
        c = math.cos(v)
        if abs(c) < 1e-15:
            raise SolveError('тангенс не определён')
        return math.tan(v)


def eval_node(node, env=None):
    """Вычисляет узел. env: словарь значений переменных."""
    if isinstance(node, Num):
        return node.v
    if isinstance(node, Var):
        if env is None or node.name not in env:
            raise SolveError('неизвестная переменная: ' + node.name)
        return env[node.name]
    if isinstance(node, Un):
        v = eval_node(node.a, env)
        return -v if node.op == '-' else v
    if isinstance(node, Post):
        v = eval_node(node.a, env)
        if node.op == '!':
            return num_fact(v)
        return num_div(v, Fraction(100))
    if isinstance(node, Bin):
        a = eval_node(node.a, env)
        b = eval_node(node.b, env)
        if node.op == '+':
            return num_add(a, b)
        if node.op == '-':
            return num_sub(a, b)
        if node.op == '*':
            return num_mul(a, b)
        if node.op == '/':
            return num_div(a, b)
        if node.op == '^':
            return num_pow(a, b)
        raise SolveError('плохая операция ' + node.op)
    if isinstance(node, Func):
        a = eval_node(node.a, env)
        if node.name == 'sqrt':
            return num_sqrt(a)
        if node.name == 'cbrt':
            return num_cbrt(a)
        if node.name in ('sin', 'cos', 'tan'):
            return num_trig(node.name, a)
        if node.name == 'log':
            if to_float(a) <= 0:
                raise SolveError('логарифм неположительного числа')
            r = math.log10(to_float(a))
            return int(round(r)) if abs(r - round(r)) < 1e-12 else r
        if node.name == 'ln':
            if to_float(a) <= 0:
                raise SolveError('логарифм неположительного числа')
            return math.log(to_float(a))
        if node.name == 'abs':
            return abs(a)
        if node.name == 'exp':
            return math.exp(to_float(a))
        raise SolveError('неизвестная функция ' + node.name)
    raise SolveError('плохой узел')


# ---------- печать выражений ----------

def _prec(node):
    if isinstance(node, Bin):
        return {'+': 1, '-': 1, '*': 2, '/': 2, '^': 3}[node.op]
    if isinstance(node, Un):
        return 4
    if isinstance(node, Post):
        return 5
    return 6


def render(node, parent_prec=0, is_right=False, parent_op=None):
    p = _prec(node)
    if isinstance(node, Num):
        s = node.text if node.text is not None else fmt_num(node.v)
    elif isinstance(node, Var):
        s = node.name
    elif isinstance(node, Un):
        s = node.op + render(node.a, 4, True, node.op)
    elif isinstance(node, Post):
        s = render(node.a, 5, False, node.op) + node.op
    elif isinstance(node, Func):
        s = node.name + '(' + render(node.a) + ')'
    elif isinstance(node, Bin):
        s = render(node.a, p, False, node.op) + ' ' + node.op + ' ' + render(node.b, p, True, node.op)
    else:
        return '?'
    need = p < parent_prec
    if p == parent_prec and is_right and parent_op in ('-', '/', '^'):
        need = True
    if isinstance(node, Un) and parent_op == '^':
        need = True
    return '(' + s + ')' if need else s


# ---------- пошаговое решение ----------

def _is_const(node):
    if isinstance(node, Num):
        return True
    if isinstance(node, Var):
        return False
    if isinstance(node, Un):
        return _is_const(node.a)
    if isinstance(node, Post):
        return _is_const(node.a)
    if isinstance(node, Func):
        return _is_const(node.a)
    if isinstance(node, Bin):
        return _is_const(node.a) and _is_const(node.b)
    return False


def _replace(node, target, repl):
    """Возвращает копию дерева с заменой поддерева target на repl."""
    if node is target:
        return repl
    if isinstance(node, Un):
        return Un(node.op, _replace(node.a, target, repl))
    if isinstance(node, Post):
        return Post(node.op, _replace(node.a, target, repl))
    if isinstance(node, Func):
        return Func(node.name, _replace(node.a, target, repl))
    if isinstance(node, Bin):
        return Bin(node.op, _replace(node.a, target, repl), _replace(node.b, target, repl))
    return node


def _find_step(node):
    """Ищет самый глубокий узел, который можно вычислить прямо сейчас."""
    if isinstance(node, (Un, Post, Func)):
        inner = _find_step(node.a)
        if inner is not None:
            return inner
        return node if _is_const(node) else None
    if isinstance(node, Bin):
        for side in (node.a, node.b):
            inner = _find_step(side)
            if inner is not None:
                return inner
        return node if _is_const(node) else None
    return None


def solve_steps(expr, env=None, max_steps=60):
    """Возвращает (значение, список шагов, финальное дерево)."""
    node = parse(expr)
    steps = []
    cur = node
    for _ in range(max_steps):
        if isinstance(cur, Num):
            break
        target = _find_step(cur)
        if target is None:
            break
        val = eval_node(target, env)
        before = render(cur)
        repl = Num(val)
        cur = _replace(cur, target, repl)
        after = render(cur)
        if before != after:
            steps.append((before, after))
        if isinstance(cur, Num):
            break
    if isinstance(cur, Num):
        return cur.v, steps, cur
    val = eval_node(cur, env)
    return val, steps, cur


# ---------- многочлены (для уравнений) ----------

class Poly:
    """c[0] + c[1]*x + c[2]*x^2 ... коэффициенты — Fraction."""

    def __init__(self, coeffs):
        c = [Fraction(x) for x in coeffs]
        while len(c) > 1 and c[-1] == 0:
            c.pop()
        self.c = c

    @staticmethod
    def const(v):
        return Poly([Fraction(v)])

    @staticmethod
    def var():
        return Poly([Fraction(0), Fraction(1)])

    def deg(self):
        return len(self.c) - 1

    def add(self, o):
        n = max(len(self.c), len(o.c))
        out = [Fraction(0)] * n
        for i, v in enumerate(self.c):
            out[i] += v
        for i, v in enumerate(o.c):
            out[i] += v
        return Poly(out)

    def sub(self, o):
        return self.add(o.scale(Fraction(-1)))

    def scale(self, k):
        return Poly([v * Fraction(k) for v in self.c])

    def mul(self, o):
        out = [Fraction(0)] * (len(self.c) + len(o.c) - 1)
        for i, a in enumerate(self.c):
            if a == 0:
                continue
            for j, b in enumerate(o.c):
                out[i + j] += a * b
        return Poly(out)

    def pow_int(self, e):
        out = Poly.const(1)
        for _ in range(e):
            out = out.mul(self)
        return out


def to_poly(node, name='x'):
    if isinstance(node, Num):
        return Poly.const(node.v)
    if isinstance(node, Var):
        if node.name != name:
            raise SolveError('уравнение только с одной переменной')
        return Poly.var()
    if isinstance(node, Un):
        p = to_poly(node.a, name)
        return p.scale(Fraction(-1)) if node.op == '-' else p
    if isinstance(node, Post):
        raise SolveError('факториал/процент в уравнении не поддерживается')
    if isinstance(node, Func):
        if node.name in ('sqrt', 'cbrt'):
            inner = to_poly(node.a, name)
            if inner.deg() == 0:
                v = inner.c[0]
                return Poly.const(num_sqrt(v) if node.name == 'sqrt' else num_cbrt(v))
            raise SolveError('корень от переменной не поддерживается в уравнении')
        if node.name == 'abs':
            inner = to_poly(node.a, name)
            if inner.deg() == 0:
                return Poly.const(abs(inner.c[0]))
            raise SolveError('модуль от переменной не поддерживается')
        raise SolveError('функция %s в уравнении не поддерживается' % node.name)
    if isinstance(node, Bin):
        a = to_poly(node.a, name)
        b = to_poly(node.b, name)
        if node.op == '+':
            return a.add(b)
        if node.op == '-':
            return a.sub(b)
        if node.op == '*':
            return a.mul(b)
        if node.op == '/':
            if b.deg() != 0:
                raise SolveError('переменная в знаменателе не поддерживается')
            if b.c[0] == 0:
                raise SolveError('деление на ноль')
            return a.scale(Fraction(1) / b.c[0])
        if node.op == '^':
            if b.deg() != 0:
                raise SolveError('переменная в показателе степени не поддерживается')
            e = b.c[0]
            if e.denominator != 1 or e < 0 or e > 6:
                raise SolveError('степень должна быть целой от 0 до 6')
            return a.pow_int(int(e))
    raise SolveError('плохой узел в уравнении')


def solve_equation(lhs, rhs, name='x'):
    """Возвращает (корни, шаги)."""
    left = to_poly(parse(lhs), name)
    right = to_poly(parse(rhs), name)
    p = left.sub(right)
    if p.deg() > 2:
        raise SolveError('поддерживаются уравнения не выше квадратного')
    steps = []
    steps.append(('переносим всё влево', '%s = 0' % poly_str(p)))
    roots = []
    if p.deg() == 0:
        if p.c[0] == 0:
            return [], steps
        raise SolveError('уравнение не имеет решений')
    if p.deg() == 1:
        b, a = p.c[0], p.c[1]
        steps.append(('выражаем x', 'x = -(%s)/(%s)' % (fmt_num(b), fmt_num(a))))
        roots.append(-b / a)
    else:
        c, b, a = p.c[0], p.c[1], p.c[2]
        D = b * b - Fraction(4) * a * c
        steps.append(('коэффициенты', 'a = %s, b = %s, c = %s' % (fmt_num(a), fmt_num(b), fmt_num(c))))
        steps.append(('дискриминант', 'D = b² - 4ac = %s' % fmt_num(D)))
        if D < 0:
            raise SolveError('действительных корней нет (D < 0)')
        sd = num_sqrt(D)
        steps.append(('корни', 'x = (-b ± √D) / (2a)'))
        r1 = num_div(num_add(-b, sd), Fraction(2) * a)
        roots.append(r1)
        if D != 0:
            r2 = num_div(num_sub(-b, sd), Fraction(2) * a)
            roots.append(r2)
    return roots, steps


def poly_str(p):
    parts = []
    for i in range(len(p.c) - 1, -1, -1):
        v = p.c[i]
        if v == 0:
            continue
        if i == 0:
            parts.append(fmt_num(v))
        elif i == 1:
            parts.append(('%sx' % fmt_num(v)) if v != 1 else 'x')
        else:
            parts.append(('%sx^%d' % (fmt_num(v), i)) if v != 1 else ('x^%d' % i))
    return ' + '.join(parts).replace('+ -', '- ') if parts else '0'


def solve(expr, env=None):
    """Главная точка входа: строка -> словарь с результатом."""
    expr = expr.strip()
    if not expr:
        raise SolveError('пустое выражение')
    if '=' in expr:
        parts = expr.split('=')
        if len(parts) != 2:
            raise SolveError('слишком много знаков "="')
        roots, steps = solve_equation(parts[0], parts[1])
        return {
            'mode': 'equation',
            'roots': [fmt_num(r) for r in roots],
            'roots_dec': [('%.10g' % to_float(r)) for r in roots],
            'steps': steps,
            'expression': expr,
        }
    val, steps, final = solve_steps(expr, env)
    return {
        'mode': 'expression',
        'value': fmt_num(val),
        'value_dec': ('%.10g' % to_float(val)),
        'steps': steps,
        'expression': expr,
        'simplified': render(final),
    }
