package com.creepyside.mathai

import com.creepyside.mathai.engine.SolveError
import com.creepyside.mathai.engine.solve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SolverTest {

    private fun value(expr: String): String = solve(expr).value ?: ""

    @Test
    fun arithmetic() {
        assertEquals("6", value("2+2*2"))
        assertEquals("27", value("3*(4+5)"))
        assertEquals("21", value("(1+2)*(3+4)"))
        assertEquals("5", value("1+2*3-4/2"))
        assertEquals("-9", value("-3^2"))
        assertEquals("512", value("2^3^2"))
        assertEquals("18", value("2*3^2"))
    }

    @Test
    fun rootsAndPowers() {
        assertEquals("4", value("sqrt(16)"))
        assertEquals("29", value("√(16)+5^2"))
        assertEquals("3", value("cbrt(27)"))
        assertEquals("8", value("2^3"))
        assertEquals("1/2", value("2^-1"))
        assertEquals("1024", value("2^10"))
    }

    @Test
    fun fractionsExact() {
        assertEquals("1/2", value("(1/3)+(1/6)"))
        assertEquals("5/2", value("10/4"))
        assertEquals("7/6", value("1/2+2/3"))
    }

    @Test
    fun extras() {
        assertEquals("120", value("5!"))
        assertEquals("100", value("50%*200"))
        assertEquals("3", value("log(1000)"))
        assertEquals("0", value("sin(0)"))
    }

    @Test
    fun irrational() {
        val v = solve("sqrt(2)").valueDec ?: ""
        assertTrue(v.startsWith("1.4142135"))
    }

    @Test
    fun equations() {
        assertEquals(listOf("2"), solve("2x+3=7").roots)
        assertEquals(listOf("3", "2"), solve("x^2-5x+6=0").roots)
        assertEquals(listOf("7"), solve("3(x-1)=2x+4").roots)
    }

    @Test
    fun errors() {
        try {
            solve("1/0")
            fail("ожидали ошибку деления на ноль")
        } catch (e: SolveError) {
            assertTrue(e.message!!.contains("ноль"))
        }
    }

    @Test
    fun stepsPresent() {
        val r = solve("√(16)+5^2")
        assertTrue(r.steps.size >= 2)
        assertEquals("29", r.value)
    }
}
