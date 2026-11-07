package com.creepyside.mathai.engine

import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Значение: либо точное (дробь), либо приближённое (double). */
sealed class Num {
    class Ex(val v: Frac) : Num()
    class Ap(val v: Double) : Num()

    fun asDouble(): Double = when (this) {
        is Ex -> v.toDouble()
        is Ap -> v
    }

    fun isExact(): Boolean = this is Ex

    companion object {
        fun of(v: Long): Num = Ex(Frac.of(v))
        fun of(v: Frac): Num = Ex(v)
        fun of(v: Double): Num = Ap(v)
    }
}

object Nums {

    fun fmt(v: Num): String = when (v) {
        is Num.Ex -> if (v.v.isInteger()) v.v.n.toString() else "${v.v.n}/${v.v.d}"
        is Num.Ap -> {
            val d = v.v
            if (d.isNaN() || d.isInfinite()) d.toString()
            else if (abs(d - round(d)) < 1e-12 && abs(d) < 1e15) round(d).toLong().toString()
            else String.format(java.util.Locale.US, "%.10g", d)
        }
    }

    fun add(a: Num, b: Num): Num {
        if (a is Num.Ex && b is Num.Ex) return Num.Ex(a.v + b.v)
        return Num.Ap(a.asDouble() + b.asDouble())
    }

    fun sub(a: Num, b: Num): Num {
        if (a is Num.Ex && b is Num.Ex) return Num.Ex(a.v - b.v)
        return Num.Ap(a.asDouble() - b.asDouble())
    }

    fun mul(a: Num, b: Num): Num {
        if (a is Num.Ex && b is Num.Ex) return Num.Ex(a.v * b.v)
        return Num.Ap(a.asDouble() * b.asDouble())
    }

    fun div(a: Num, b: Num): Num {
        if (b is Num.Ex && b.v.n.signum() == 0) throw SolveError("деление на ноль")
        if (a is Num.Ex && b is Num.Ex) return Num.Ex(a.v / b.v)
        val d = b.asDouble()
        if (d == 0.0) throw SolveError("деление на ноль")
        return Num.Ap(a.asDouble() / d)
    }

    fun perfectRoot(x: Frac, k: Int): Frac? {
        if (x.n.signum() < 0 && k % 2 == 0) return null
        val sign = if (x.n.signum() < 0) -1L else 1L
        val an = x.n.abs()
        val ad = x.d
        val rn = round(an.toDouble().pow(1.0 / k)).toLong()
        val rd = round(ad.toDouble().pow(1.0 / k)).toLong()
        for (cn in longArrayOf(rn - 1, rn, rn + 1)) {
            for (cd in longArrayOf(rd - 1, rd, rd + 1)) {
                if (cn > 0 && cd > 0 &&
                    BigInteger.valueOf(cn).pow(k) == an &&
                    BigInteger.valueOf(cd).pow(k) == ad
                ) {
                    return Frac.of(sign * cn, cd)
                }
            }
        }
        return null
    }

    fun sqrt(a: Num): Num {
        if (a is Num.Ex) {
            if (a.v.n.signum() < 0) throw SolveError("корень из отрицательного числа")
            val r = perfectRoot(a.v, 2)
            if (r != null) return Num.Ex(r)
        }
        val v = a.asDouble()
        if (v < 0) throw SolveError("корень из отрицательного числа")
        return Num.Ap(sqrt(v))
    }

    fun cbrt(a: Num): Num {
        if (a is Num.Ex) {
            val r = perfectRoot(a.v, 3)
            if (r != null) return Num.Ex(r)
        }
        val v = a.asDouble()
        return Num.Ap(Math.cbrt(v))
    }

    fun pow(a: Num, b: Num): Num {
        if (a is Num.Ex && b is Num.Ex && b.v.isInteger()) {
            val e = b.v.n.toInt()
            if (abs(e) <= 512) {
                if (a.v.n.signum() == 0 && e < 0) throw SolveError("деление на ноль")
                return Num.Ex(a.v.pow(e))
            }
        }
        if (b is Num.Ex) {
            if (b.v == Frac.of(1, 2)) return sqrt(a)
            if (b.v == Frac.of(1, 3)) return cbrt(a)
        }
        val base = a.asDouble()
        val e = b.asDouble()
        if (base < 0) {
            val r = round(e)
            if (abs(e - r) < 1e-12) return Num.Ap(base.pow(r.toInt()))
            throw SolveError("отрицательное основание в дробной степени")
        }
        if (base == 0.0 && e < 0) throw SolveError("деление на ноль")
        val r = base.pow(e)
        if (r.isInfinite()) throw SolveError("слишком большое число")
        return Num.Ap(r)
    }

    fun fact(a: Num): Num {
        if (a !is Num.Ex || !a.v.isInteger()) throw SolveError("факториал только для целых")
        val n = a.v.n.toInt()
        if (n < 0 || n > 170) throw SolveError("факториал вне диапазона 0..170")
        var acc = BigInteger.ONE
        for (i in 2..n) acc = acc.multiply(BigInteger.valueOf(i.toLong()))
        return Num.Ex(Frac.create(acc, BigInteger.ONE))
    }

    fun trig(name: String, a: Num): Num {
        if (a is Num.Ex && a.v.n.signum() == 0) {
            return when (name) {
                "sin", "tan" -> Num.Ex(Frac.ZERO)
                else -> Num.Ex(Frac.ONE)
            }
        }
        val v = a.asDouble()
        return when (name) {
            "sin" -> Num.Ap(sin(v))
            "cos" -> Num.Ap(cos(v))
            else -> {
                if (abs(cos(v)) < 1e-15) throw SolveError("тангенс не определён")
                Num.Ap(tan(v))
            }
        }
    }

    fun log10(a: Num): Num {
        val v = a.asDouble()
        if (v <= 0) throw SolveError("логарифм неположительного числа")
        val r = log10(v)
        val rr = round(r)
        return if (abs(r - rr) < 1e-12) Num.Ex(Frac.of(rr.toLong())) else Num.Ap(r)
    }

    fun ln(a: Num): Num {
        val v = a.asDouble()
        if (v <= 0) throw SolveError("логарифм неположительного числа")
        return Num.Ap(ln(v))
    }

    fun abs(a: Num): Num = when (a) {
        is Num.Ex -> if (a.v.n.signum() < 0) Num.Ex(-a.v) else a
        is Num.Ap -> Num.Ap(abs(a.v))
    }

    fun exp(a: Num): Num = Num.Ap(exp(a.asDouble()))
}
