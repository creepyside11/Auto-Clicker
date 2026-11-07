package com.creepyside.mathai.engine

import java.math.BigInteger

/** Точная рациональная дробь на BigInteger. */
class Frac private constructor(val n: BigInteger, val d: BigInteger) : Comparable<Frac> {

    companion object {
        val ZERO = Frac(BigInteger.ZERO, BigInteger.ONE)
        val ONE = Frac(BigInteger.ONE, BigInteger.ONE)

        fun of(v: Long): Frac = Frac(BigInteger.valueOf(v), BigInteger.ONE)

        fun of(n: Long, d: Long): Frac = create(BigInteger.valueOf(n), BigInteger.valueOf(d))

        fun create(n: BigInteger, d: BigInteger): Frac {
            if (d.signum() == 0) throw SolveError("деление на ноль")
            var nn = n
            var dd = d
            if (dd.signum() < 0) {
                nn = nn.negate()
                dd = dd.negate()
            }
            val g = nn.gcd(dd)
            if (g.signum() != 0 && g != BigInteger.ONE) {
                nn = nn.divide(g)
                dd = dd.divide(g)
            }
            return Frac(nn, dd)
        }

        /** "12", "12.5", "-3" -> дробь */
        fun parse(s: String): Frac {
            val t = s.trim()
            val dot = t.indexOf('.')
            if (dot < 0) return create(BigInteger(t), BigInteger.ONE)
            val ip = if (dot == 0) "0" else t.substring(0, dot)
            val fp = t.substring(dot + 1)
            val den = BigInteger.TEN.pow(fp.length)
            val num = BigInteger(ip).multiply(den).add(if (fp.isEmpty()) BigInteger.ZERO else BigInteger(fp))
            return create(num, den)
        }
    }

    operator fun plus(o: Frac): Frac = create(n.multiply(o.d).add(o.n.multiply(d)), d.multiply(o.d))
    operator fun minus(o: Frac): Frac = create(n.multiply(o.d).subtract(o.n.multiply(d)), d.multiply(o.d))
    operator fun times(o: Frac): Frac = create(n.multiply(o.n), d.multiply(o.d))
    operator fun div(o: Frac): Frac = create(n.multiply(o.d), d.multiply(o.n))
    operator fun unaryMinus(): Frac = Frac(n.negate(), d)

    fun isInteger(): Boolean = d == BigInteger.ONE

    fun toIntExact(): Int = n.divide(d).intValueExact()

    fun toDouble(): Double = n.toDouble() / d.toDouble()

    fun pow(e: Int): Frac {
        if (e == 0) return ONE
        if (e > 0) return create(n.pow(e), d.pow(e))
        return create(d.pow(-e), n.pow(-e))
    }

    override fun compareTo(other: Frac): Int =
        n.multiply(other.d).compareTo(other.n.multiply(d))

    override fun equals(other: Any?): Boolean {
        if (other !is Frac) return false
        return n == other.n && d == other.d
    }

    override fun hashCode(): Int = n.hashCode() * 31 + d.hashCode()

    override fun toString(): String = if (d == BigInteger.ONE) n.toString() else "$n/$d"
}
