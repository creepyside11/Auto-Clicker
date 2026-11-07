package com.creepyside.mathai.engine

import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.round

class SolveError(message: String) : Exception(message)

private val FUNCS = setOf("sqrt", "cbrt", "sin", "cos", "tan", "log", "ln", "abs", "exp")

class Tok(val kind: String, val text: String) {
    override fun toString(): String = "$kind:$text"
}

fun tokenize(src: String): List<Tok> {
    val out = ArrayList<Tok>()
    var i = 0
    val n = src.length
    while (i < n) {
        val c = src[i]
        if (c == ' ' || c == '\t' || c == '\n' || c == '\r') { i++; continue }
        if (c.isDigit() || (c == '.' && i + 1 < n && src[i + 1].isDigit())) {
            var j = i
            while (j < n && (src[j].isDigit() || src[j] == '.')) j++
            val txt = src.substring(i, j)
            if (txt.count { it == '.' } > 1) throw SolveError("плохое число: $txt")
            out.add(Tok("num", txt))
            i = j
            continue
        }
        if (c == 'π') { out.add(Tok("num", "pi")); i++; continue }
        if (c.isLetter()) {
            var j = i
            while (j < n && src[j].isLetter()) j++
            val word = src.substring(i, j)
            val low = word.lowercase()
            if (low in FUNCS) out.add(Tok("func", low))
            else if (low == "pi") out.add(Tok("num", "pi"))
            else for (ch in word) out.add(Tok("var", ch.lowercase()))
            i = j
            continue
        }
        when (c) {
            '+', '-', '*', '/', '^', '!', '%', '(', ')', ',' -> out.add(Tok("op", c.toString()))
            '×' -> out.add(Tok("op", "*"))
            '÷' -> out.add(Tok("op", "/"))
            '−' -> out.add(Tok("op", "-"))
            '√' -> out.add(Tok("func", "sqrt"))
            '∛' -> out.add(Tok("func", "cbrt"))
            else -> throw SolveError("неизвестный символ: $c")
        }
        i++
    }
    return out
}

sealed class Node

class NNum(val v: Num, val text: String?) : Node()
class NVar(val name: String) : Node()
class NUn(val op: String, val a: Node) : Node()
class NBin(val op: String, val a: Node, val b: Node) : Node()
class NFun(val name: String, val a: Node) : Node()
class NPost(val op: String, val a: Node) : Node()

class Parser(private val t: List<Tok>) {
    private var i = 0

    private fun peek(): Tok? = if (i < t.size) t[i] else null

    private fun isOp(tok: Tok?, ch: String): Boolean = tok != null && tok.kind == "op" && tok.text == ch

    fun parse(): Node {
        val node = expr()
        if (i != t.size) throw SolveError("лишние символы в конце")
        return node
    }

    private fun expr(): Node {
        var node = term()
        while (true) {
            val k = peek() ?: return node
            if (k.kind == "op" && (k.text == "+" || k.text == "-")) {
                i++
                node = NBin(k.text, node, term())
            } else return node
        }
    }

    private fun term(): Node {
        var node = unary()
        while (true) {
            val k = peek() ?: return node
            if (k.kind == "op" && (k.text == "*" || k.text == "/")) {
                i++
                node = NBin(k.text, node, unary())
            } else if (k.kind == "num" || k.kind == "var" || k.kind == "func" || isOp(k, "(")) {
                node = NBin("*", node, unary())
            } else return node
        }
    }

    private fun unary(): Node {
        val k = peek()
        if (k != null && k.kind == "op" && (k.text == "+" || k.text == "-")) {
            i++
            return NUn(k.text, unary())
        }
        return power()
    }

    private fun power(): Node {
        val base = postfix()
        val k = peek()
        if (isOp(k, "^")) {
            i++
            return NBin("^", base, unary())
        }
        return base
    }

    private fun postfix(): Node {
        var node = atom()
        while (true) {
            val k = peek() ?: return node
            if (k.kind == "op" && (k.text == "!" || k.text == "%")) {
                i++
                node = NPost(k.text, node)
            } else return node
        }
    }

    private fun atom(): Node {
        val tok = if (i < t.size) t[i++] else throw SolveError("неожиданный конец выражения")
        when (tok.kind) {
            "num" -> {
                if (tok.text == "pi") return NNum(Num.Ap(Math.PI), "π")
                return NNum(Num.Ex(Frac.parse(tok.text)), tok.text)
            }
            "var" -> return NVar(tok.text)
            "func" -> {
                if (isOp(peek(), "(")) {
                    i++
                    val arg = expr()
                    if (!isOp(peek(), ")")) throw SolveError("ожидалось \")\"")
                    i++
                    return NFun(tok.text, arg)
                }
                return NFun(tok.text, unary())
            }
            "op" -> if (tok.text == "(") {
                val node = expr()
                if (!isOp(peek(), ")")) throw SolveError("ожидалось \")\"")
                i++
                return node
            }
        }
        throw SolveError("неожиданный символ: ${tok.text}")
    }
}

fun parseExpr(s: String): Node = Parser(tokenize(s)).parse()

fun negNum(v: Num): Num = when (v) {
    is Num.Ex -> Num.Ex(-v.v)
    is Num.Ap -> Num.Ap(-v.v)
}

fun evalNode(node: Node, env: Map<String, Num>?): Num = when (node) {
    is NNum -> node.v
    is NVar -> env?.get(node.name) ?: throw SolveError("неизвестная переменная: " + node.name)
    is NUn -> {
        val v = evalNode(node.a, env)
        if (node.op == "-") negNum(v) else v
    }
    is NPost -> {
        val v = evalNode(node.a, env)
        if (node.op == "!") Nums.fact(v) else Nums.div(v, Num.Ex(Frac.of(100)))
    }
    is NBin -> {
        val a = evalNode(node.a, env)
        val b = evalNode(node.b, env)
        when (node.op) {
            "+" -> Nums.add(a, b)
            "-" -> Nums.sub(a, b)
            "*" -> Nums.mul(a, b)
            "/" -> Nums.div(a, b)
            "^" -> Nums.pow(a, b)
            else -> throw SolveError("плохая операция " + node.op)
        }
    }
    is NFun -> {
        val a = evalNode(node.a, env)
        when (node.name) {
            "sqrt" -> Nums.sqrt(a)
            "cbrt" -> Nums.cbrt(a)
            "sin", "cos", "tan" -> Nums.trig(node.name, a)
            "log" -> Nums.log10(a)
            "ln" -> Nums.ln(a)
            "abs" -> Nums.abs(a)
            "exp" -> Nums.exp(a)
            else -> throw SolveError("неизвестная функция " + node.name)
        }
    }
}

fun precOf(node: Node): Int = when (node) {
    is NBin -> when (node.op) {
        "+", "-" -> 1
        "*", "/" -> 2
        else -> 3
    }
    is NUn -> 4
    is NPost -> 5
    else -> 6
}

fun render(node: Node, parentPrec: Int = 0, isRight: Boolean = false, parentOp: String? = null): String {
    val p = precOf(node)
    val s: String = when (node) {
        is NNum -> node.text ?: Nums.fmt(node.v)
        is NVar -> node.name
        is NUn -> node.op + render(node.a, 4, true, node.op)
        is NPost -> render(node.a, 5, false, node.op) + node.op
        is NFun -> node.name + "(" + render(node.a) + ")"
        is NBin -> render(node.a, p, false, node.op) + " " + node.op + " " + render(node.b, p, true, node.op)
    }
    var need = p < parentPrec
    if (p == parentPrec && isRight && (parentOp == "-" || parentOp == "/" || parentOp == "^")) need = true
    if (node is NUn && parentOp == "^") need = true
    return if (need) "($s)" else s
}

fun isConst(node: Node): Boolean = when (node) {
    is NNum -> true
    is NVar -> false
    is NUn -> isConst(node.a)
    is NPost -> isConst(node.a)
    is NFun -> isConst(node.a)
    is NBin -> isConst(node.a) && isConst(node.b)
}

fun findStep(node: Node): Node? = when (node) {
    is NUn -> findStep(node.a) ?: (if (isConst(node)) node else null)
    is NPost -> findStep(node.a) ?: (if (isConst(node)) node else null)
    is NFun -> findStep(node.a) ?: (if (isConst(node)) node else null)
    is NBin -> findStep(node.a) ?: findStep(node.b) ?: (if (isConst(node)) node else null)
    else -> null
}

fun replaceNode(node: Node, target: Node, repl: Node): Node {
    if (node === target) return repl
    return when (node) {
        is NUn -> NUn(node.op, replaceNode(node.a, target, repl))
        is NPost -> NPost(node.op, replaceNode(node.a, target, repl))
        is NFun -> NFun(node.name, replaceNode(node.a, target, repl))
        is NBin -> NBin(node.op, replaceNode(node.a, target, repl), replaceNode(node.b, target, repl))
        else -> node
    }
}

class Step(val before: String, val after: String)

class SolveResult(
    val mode: String,
    val value: String? = null,
    val valueDec: String? = null,
    val roots: List<String> = emptyList(),
    val rootsDec: List<String> = emptyList(),
    val steps: List<Step> = emptyList(),
    val expression: String = "",
    val simplified: String = "",
)

fun solveSteps(expr: String, env: Map<String, Num>?, maxSteps: Int = 60): Triple<Num, List<Step>, Node> {
    var cur: Node = parseExpr(expr)
    val steps = ArrayList<Step>()
    for (k in 0 until maxSteps) {
        if (cur is NNum) break
        val target = findStep(cur) ?: break
        val v = evalNode(target, env)
        val before = render(cur)
        cur = replaceNode(cur, target, NNum(v, null))
        val after = render(cur)
        if (before != after) steps.add(Step(before, after))
        if (cur is NNum) break
    }
    if (cur is NNum) return Triple(cur.v, steps, cur)
    val v = evalNode(cur, env)
    return Triple(v, steps, cur)
}

// ---------- многочлены ----------

class Poly(coeffs: List<Frac>) {
    val c: MutableList<Frac> = ArrayList(coeffs)
    init {
        while (c.size > 1 && c[c.size - 1].n.signum() == 0) c.removeAt(c.size - 1)
        if (c.isEmpty()) c.add(Frac.ZERO)
    }

    fun deg(): Int = c.size - 1

    fun at(i: Int): Frac = if (i < c.size) c[i] else Frac.ZERO

    companion object {
        fun const(v: Frac): Poly = Poly(listOf(v))
        fun varPoly(): Poly = Poly(listOf(Frac.ZERO, Frac.ONE))
    }

    fun add(o: Poly): Poly {
        val n = maxOf(c.size, o.c.size)
        val out = ArrayList<Frac>()
        for (i in 0 until n) out.add(at(i) + o.at(i))
        return Poly(out)
    }

    fun scale(k: Frac): Poly {
        val out = ArrayList<Frac>()
        for (v in c) out.add(v * k)
        return Poly(out)
    }

    fun sub(o: Poly): Poly = add(o.scale(Frac.of(-1)))

    fun mul(o: Poly): Poly {
        val out = ArrayList<Frac>()
        for (i in 0 until c.size + o.c.size - 1) out.add(Frac.ZERO)
        for (i in c.indices) {
            if (c[i].n.signum() == 0) continue
            for (j in o.c.indices) {
                out[i + j] = out[i + j] + c[i] * o.c[j]
            }
        }
        return Poly(out)
    }

    fun powInt(e: Int): Poly {
        var out = const(Frac.ONE)
        for (k in 0 until e) out = out.mul(this)
        return out
    }
}

fun toPoly(node: Node, name: String = "x"): Poly {
    when (node) {
        is NNum -> {
            if (node.v !is Num.Ex) throw SolveError("приближённое число в уравнении")
            return Poly.const((node.v as Num.Ex).v)
        }
        is NVar -> {
            if (node.name != name) throw SolveError("уравнение только с одной переменной")
            return Poly.varPoly()
        }
        is NUn -> {
            val p = toPoly(node.a, name)
            return if (node.op == "-") p.scale(Frac.of(-1)) else p
        }
        is NPost -> throw SolveError("факториал/процент в уравнении не поддерживается")
        is NFun -> {
            if (node.name == "sqrt" || node.name == "cbrt") {
                val inner = toPoly(node.a, name)
                if (inner.deg() == 0) {
                    val v = Num.Ex(inner.c[0])
                    val r = if (node.name == "sqrt") Nums.sqrt(v) else Nums.cbrt(v)
                    if (r is Num.Ex) return Poly.const(r.v)
                    throw SolveError("иррациональный коэффициент")
                }
                throw SolveError("корень от переменной не поддерживается в уравнении")
            }
            if (node.name == "abs") {
                val inner = toPoly(node.a, name)
                if (inner.deg() == 0) {
                    val v = inner.c[0]
                    return Poly.const(if (v.n.signum() < 0) -v else v)
                }
                throw SolveError("модуль от переменной не поддерживается")
            }
            throw SolveError("функция ${node.name} в уравнении не поддерживается")
        }
        is NBin -> {
            val a = toPoly(node.a, name)
            val b = toPoly(node.b, name)
            when (node.op) {
                "+" -> return a.add(b)
                "-" -> return a.sub(b)
                "*" -> return a.mul(b)
                "/" -> {
                    if (b.deg() != 0) throw SolveError("переменная в знаменателе не поддерживается")
                    if (b.c[0].n.signum() == 0) throw SolveError("деление на ноль")
                    return a.scale(Frac.ONE / b.c[0])
                }
                "^" -> {
                    if (b.deg() != 0) throw SolveError("переменная в показателе степени не поддерживается")
                    val e = b.c[0]
                    if (!e.isInteger() || e.n.signum() < 0 || e.n.toInt() > 6) {
                        throw SolveError("степень должна быть целой от 0 до 6")
                    }
                    return a.powInt(e.n.toInt())
                }
            }
        }
    }
    throw SolveError("плохой узел в уравнении")
}

fun polyStr(p: Poly): String {
    val parts = ArrayList<String>()
    for (i in p.c.size - 1 downTo 0) {
        val v = p.c[i]
        if (v.n.signum() == 0) continue
        if (i == 0) parts.add(Nums.fmt(Num.Ex(v)))
        else if (i == 1) parts.add(if (v == Frac.ONE) "x" else Nums.fmt(Num.Ex(v)) + "x")
        else parts.add(if (v == Frac.ONE) "x^$i" else Nums.fmt(Num.Ex(v)) + "x^$i")
    }
    if (parts.isEmpty()) return "0"
    return parts.joinToString(" + ").replace("+ -", "- ")
}

fun solveEquation(lhs: String, rhs: String, name: String = "x"): Pair<List<Num>, List<Step>> {
    val left = toPoly(parseExpr(lhs), name)
    val right = toPoly(parseExpr(rhs), name)
    val p = left.sub(right)
    if (p.deg() > 2) throw SolveError("поддерживаются уравнения не выше квадратного")
    val steps = ArrayList<Step>()
    steps.add(Step("переносим всё влево", polyStr(p) + " = 0"))
    val roots = ArrayList<Num>()
    if (p.deg() == 0) {
        if (p.c[0].n.signum() == 0) return Pair(roots, steps)
        throw SolveError("уравнение не имеет решений")
    }
    if (p.deg() == 1) {
        val b = p.c[0]
        val a = p.c[1]
        steps.add(Step("выражаем x", "x = -(${Nums.fmt(Num.Ex(b))})/(${Nums.fmt(Num.Ex(a))})"))
        roots.add(Num.Ex(-b / a))
    } else {
        val cc = p.c[0]
        val bb = p.c[1]
        val aa = p.c[2]
        val d = bb * bb - Frac.of(4) * aa * cc
        steps.add(Step("коэффициенты", "a = ${Nums.fmt(Num.Ex(aa))}, b = ${Nums.fmt(Num.Ex(bb))}, c = ${Nums.fmt(Num.Ex(cc))}"))
        steps.add(Step("дискриминант", "D = b² - 4ac = ${Nums.fmt(Num.Ex(d))}"))
        if (d.n.signum() < 0) throw SolveError("действительных корней нет (D < 0)")
        val sd = Nums.sqrt(Num.Ex(d))
        steps.add(Step("корни", "x = (-b ± √D) / (2a)"))
        val twoA = Num.Ex(Frac.of(2) * aa)
        val minusB = Num.Ex(-bb)
        roots.add(Nums.div(Nums.add(minusB, sd), twoA))
        if (d.n.signum() != 0) {
            roots.add(Nums.div(Nums.sub(minusB, sd), twoA))
        }
    }
    return Pair(roots, steps)
}

fun solve(expr: String, env: Map<String, Num>? = null): SolveResult {
    val e = expr.trim()
    if (e.isEmpty()) throw SolveError("пустое выражение")
    if (e.contains('=')) {
        val parts = e.split('=')
        if (parts.size != 2) throw SolveError("слишком много знаков \"=\"")
        val (roots, steps) = solveEquation(parts[0], parts[1])
        return SolveResult(
            mode = "equation",
            roots = roots.map { Nums.fmt(it) },
            rootsDec = roots.map { String.format(java.util.Locale.US, "%.10g", it.asDouble()) },
            steps = steps,
            expression = e,
        )
    }
    val (val0, steps, final) = solveSteps(e, env)
    return SolveResult(
        mode = "expression",
        value = Nums.fmt(val0),
        valueDec = String.format(java.util.Locale.US, "%.10g", val0.asDouble()),
        steps = steps,
        expression = e,
        simplified = render(final),
    )
}
