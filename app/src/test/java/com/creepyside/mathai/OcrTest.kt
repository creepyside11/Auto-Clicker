package com.creepyside.mathai

import com.creepyside.mathai.engine.Classifier
import com.creepyside.mathai.engine.Gray
import com.creepyside.mathai.engine.Pipeline
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/** Прогоняет пайплайн по синтетическим картинкам с известными ответами. */
class OcrTest {

    private fun moduleDir(): File {
        var d = File(System.getProperty("user.dir") ?: ".")
        if (File(d, "src/main/assets").exists()) return d
        if (File(d, "app/src/main/assets").exists()) return File(d, "app")
        return d
    }

    private fun loadGray(file: File): Gray {
        val img = ImageIO.read(file)
        val w = img.width
        val h = img.height
        val data = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val rgb = img.getRGB(x, y)
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                data[y * w + x] = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            }
        }
        return Gray(w, h, data)
    }

    @Test
    fun recognizesSyntheticLines() {
        val dir = moduleDir()
        val model = File(dir, "src/main/assets/model.bin")
        if (!model.exists()) {
            println("model.bin отсутствует — тест пропущен")
            return
        }
        val clf = Classifier(model.readBytes())
        val linesDir = File(dir, "src/test/resources/lines")
        val labels = File(linesDir, "labels.tsv")
        if (!labels.exists()) {
            println("labels.tsv отсутствует — тест пропущен")
            return
        }
        var total = 0
        var exact = 0
        val bad = ArrayList<String>()
        labels.forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            val parts = line.split('\t')
            if (parts.size < 2) return@forEachLine
            val file = File(linesDir, parts[0])
            if (!file.exists()) return@forEachLine
            val expected = parts[1].replace(" ", "")
            val gray = loadGray(file)
            val res = Pipeline.recognize(gray, clf)
            val got = res.joinToString(" ") { it.text }.replace(" ", "")
            total++
            if (got == expected) {
                exact++
            } else {
                if (bad.size < 12) bad.add("${parts[0]}: ожидали [$expected], получили [$got]")
            }
        }
        val acc = exact.toFloat() / total
        println("OCR: $exact/$total = " + String.format(java.util.Locale.US, "%.1f%%", acc * 100))
        for (b in bad) println("  " + b)
        assertTrue("нет тестовых картинок", total >= 10)
        assertTrue("точность распознавания слишком низкая: $acc", acc >= 0.50f)
    }
}
