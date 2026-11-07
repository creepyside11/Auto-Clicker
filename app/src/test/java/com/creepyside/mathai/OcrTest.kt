package com.creepyside.mathai

import com.creepyside.mathai.engine.Classifier
import com.creepyside.mathai.engine.Gray
import com.creepyside.mathai.engine.Pipeline
import com.creepyside.mathai.engine.solve
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.File
import java.util.Locale

/**
 * Прогоняет пайплайн по синтетическим картинкам с известными ответами.
 * Картинки лежат в src/test/resources/lines/lines.bin (свой формат,
 * чтобы не зависеть от javax.imageio в Android unit-тестах).
 */
class OcrTest {

    private fun moduleDir(): File {
        val d = File(System.getProperty("user.dir") ?: ".")
        if (File(d, "src/main/assets").exists()) return d
        if (File(d, "app/src/main/assets").exists()) return File(d, "app")
        return d
    }

    private class Case(val expr: String, val gray: Gray)

    private fun readLeInt(inp: DataInputStream): Int = Integer.reverseBytes(inp.readInt())

    private fun readCases(file: File): List<Case> {
        val out = ArrayList<Case>()
        DataInputStream(file.inputStream().buffered()).use { inp ->
            val magic = ByteArray(4)
            inp.readFully(magic)
            require(String(magic) == "MLN1") { "плохой файл lines.bin" }
            val count = readLeInt(inp)
            for (k in 0 until count) {
                val nlen = readLeInt(inp)
                val name = ByteArray(nlen)
                inp.readFully(name)
                val w = readLeInt(inp)
                val h = readLeInt(inp)
                val data = ByteArray(w * h)
                inp.readFully(data)
                val floats = FloatArray(w * h)
                for (i in floats.indices) floats[i] = (data[i].toInt() and 255) / 255f
                out.add(Case(String(name, Charsets.UTF_8), Gray(w, h, floats)))
            }
        }
        return out
    }

    /** Приводит разные записи одного выражения к одной форме. */
    private fun canon(expr: String): String {
        val t = expr.replace("×", "*").replace("÷", "/").replace(" ", "")
        val sb = StringBuilder()
        var i = 0
        while (i < t.length) {
            if (t.startsWith("cbrt(", i)) { sb.append("cbrt("); i += 5; continue }
            if (t[i] == '∛' || t[i] == '√') {
                val fn = if (t[i] == '∛') "cbrt(" else "sqrt("
                sb.append(fn)
                var depth = 1
                var j = i + 1
                val start = j
                while (j < t.length && depth > 0) {
                    if (t[j] == '(' || t[j] == '[') depth++
                    else if (t[j] == ')' || t[j] == ']') depth--
                    j++
                }
                val inner = if (depth == 0) t.substring(start, j - 1) else t.substring(start, j)
                sb.append(inner).append(')')
                i = j
                continue
            }
            sb.append(t[i])
            i++
        }
        // '*' рядом с буквой, скобкой или функцией — это неявное умножение
        val res = StringBuilder()
        for (k in sb.indices) {
            val ch = sb[k]
            if (ch == '*') {
                val prev = if (k > 0) sb[k - 1] else ' '
                val nxt = if (k + 1 < sb.length) sb[k + 1] else ' '
                if (prev.isLetter() || prev == ')' || prev == '(' ||
                    nxt.isLetter() || nxt == '('
                ) continue
            }
            res.append(ch)
        }
        return res.toString()
    }

    private fun sameValue(a: String, b: String): Boolean = try {
        val ra = solve(a)
        val rb = solve(b)
        if (ra.mode != rb.mode) false
        else if (ra.mode == "expression") {
            val x = ra.valueDec?.toDoubleOrNull()
            val y = rb.valueDec?.toDoubleOrNull()
            x != null && y != null && Math.abs(x - y) < 1e-6
        } else ra.roots == rb.roots
    } catch (t: Throwable) {
        false
    }

    @Test
    fun recognizesSyntheticLines() {
        val dir = moduleDir()
        val model = File(dir, "src/main/assets/model.bin")
        if (!model.exists()) {
            println("model.bin отсутствует — тест пропущен")
            return
        }
        val linesFile = File(dir, "src/test/resources/lines/lines.bin")
        if (!linesFile.exists()) {
            println("lines.bin отсутствует — тест пропущен")
            return
        }
        val clf = Classifier(model.readBytes())
        val cases = readCases(linesFile)
        var exact = 0
        var valueOk = 0
        val bad = ArrayList<String>()
        for (c in cases) {
            val res = Pipeline.recognize(c.gray, clf)
            val got = canon(res.joinToString("") { it.text })
            val expected = canon(c.expr)
            if (got == expected) exact++
            else if (bad.size < 15) bad.add("ожидали [$expected], получили [$got]")
            if (sameValue(expected, got)) valueOk++
        }
        val acc = exact.toFloat() / cases.size
        val accVal = valueOk.toFloat() / cases.size
        println("OCR строка: $exact/${cases.size} = " + String.format(Locale.US, "%.1f%%", acc * 100))
        println("OCR значение: $valueOk/${cases.size} = " + String.format(Locale.US, "%.1f%%", accVal * 100))
        for (b in bad) println("  " + b)
        assertTrue("нет тестовых картинок", cases.size >= 10)
        assertTrue("точность записи слишком низкая: $acc", acc >= 0.70f)
        assertTrue("точность по значению слишком низкая: $accVal", accVal >= 0.80f)
    }
}
