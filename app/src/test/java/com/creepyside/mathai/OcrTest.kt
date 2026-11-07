package com.creepyside.mathai

import com.creepyside.mathai.engine.Classifier
import com.creepyside.mathai.engine.Gray
import com.creepyside.mathai.engine.Pipeline
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
            val got = res.joinToString(" ") { it.text }.replace(" ", "")
            val expected = c.expr.replace(" ", "")
            if (got == expected) exact++ else if (bad.size < 15) bad.add("ожидали [$expected], получили [$got]")
            val okVal = try {
                val a = com.creepyside.mathai.engine.solve(expected)
                val b = com.creepyside.mathai.engine.solve(got)
                if (a.mode != b.mode) false
                else if (a.mode == "expression") Math.abs(a.valueDec!!.toDouble() - b.valueDec!!.toDouble()) < 1e-6
                else a.roots == b.roots
            } catch (t: Throwable) {
                false
            }
            if (okVal) valueOk++
        }
        val acc = exact.toFloat() / cases.size
        val accVal = valueOk.toFloat() / cases.size
        println("OCR строка: $exact/${cases.size} = " + String.format(Locale.US, "%.1f%%", acc * 100))
        println("OCR значение: $valueOk/${cases.size} = " + String.format(Locale.US, "%.1f%%", accVal * 100))
        for (b in bad) println("  " + b)
        assertTrue("нет тестовых картинок", cases.size >= 10)
        assertTrue("точность распознавания слишком низкая: $acc", acc >= 0.35f)
        assertTrue("точность по значению слишком низкая: $accVal", accVal >= 0.35f)
    }
}
