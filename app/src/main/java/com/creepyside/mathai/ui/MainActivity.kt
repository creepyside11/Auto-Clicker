package com.creepyside.mathai.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.creepyside.mathai.R
import com.creepyside.mathai.engine.Classifier
import com.creepyside.mathai.engine.LineResult
import com.creepyside.mathai.engine.Pipeline
import com.creepyside.mathai.engine.SolveError
import com.creepyside.mathai.engine.SolveResult
import com.creepyside.mathai.engine.solve
import com.creepyside.mathai.photo.PhotoLoader
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var recognized: TextView
    private lateinit var result: TextView
    private lateinit var resultSub: TextView
    private lateinit var steps: TextView
    private lateinit var info: TextView
    private lateinit var cardRecognized: View
    private lateinit var cardResult: View
    private lateinit var cardSteps: View

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var classifier: Classifier? = null
    private var modelError: String? = null
    private var pendingFile: File? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) runPhoto(uri)
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok: Boolean ->
        val f = pendingFile
        if (ok && f != null) runPhotoFile(f)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        input = findViewById(R.id.input)
        status = findViewById(R.id.status)
        recognized = findViewById(R.id.recognized)
        result = findViewById(R.id.result)
        resultSub = findViewById(R.id.resultSub)
        steps = findViewById(R.id.steps)
        info = findViewById(R.id.info)
        cardRecognized = findViewById(R.id.cardRecognized)
        cardResult = findViewById(R.id.cardResult)
        cardSteps = findViewById(R.id.cardSteps)

        findViewById<Button>(R.id.btnSolve).setOnClickListener { solveNow() }
        findViewById<Button>(R.id.btnPhoto).setOnClickListener { pickPhoto() }
        findViewById<Button>(R.id.btnCamera).setOnClickListener { launchCamera() }

        bindKey(R.id.kSqrt, "√(")
        bindKey(R.id.kCbrt, "∛(")
        bindKey(R.id.kPow, "^")
        bindKey(R.id.kPi, "π")
        bindKey(R.id.kMul, "×")
        bindKey(R.id.kDiv, "÷")
        bindKey(R.id.kLp, "(")
        bindKey(R.id.kRp, ")")

        input.setOnEditorActionListener { _, _, _ ->
            solveNow()
            true
        }

        info.text = buildInfo()
        loadModelAsync()
        handleShare(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent == null) return
        if (intent.action != Intent.ACTION_SEND) return
        val type = intent.type ?: return
        if (!type.startsWith("image/")) return
        val uri: Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)
        if (uri != null) runPhoto(uri)
    }

    private fun bindKey(id: Int, text: String) {
        findViewById<Button>(id).setOnClickListener {
            val start = input.selectionStart.coerceAtLeast(0)
            val end = input.selectionEnd.coerceAtLeast(0)
            val lo = minOf(start, end)
            val hi = maxOf(start, end)
            val cur = input.text.toString()
            val next = cur.substring(0, lo) + text + cur.substring(hi)
            input.setText(next)
            input.setSelection(lo + text.length)
        }
    }

    // ---------- модель ----------

    private fun loadModelAsync() {
        io.execute {
            try {
                ensureClassifier()
            } catch (t: Throwable) {
                modelError = t.message ?: t.javaClass.simpleName
            }
        }
    }

    private fun ensureClassifier(): Classifier {
        val cur = classifier
        if (cur != null) return cur
        val bytes = assets.open("model.bin").use { it.readBytes() }
        val clf = Classifier(bytes)
        classifier = clf
        return clf
    }

    private fun buildInfo(): String {
        var size = "—"
        var acc = "—"
        try {
            val bytes = assets.open("model.bin").use { it.readBytes() }
            size = String.format(java.util.Locale.US, "%.0f КБ", bytes.size / 1024.0)
        } catch (ignored: Throwable) {
        }
        try {
            val text = assets.open("meta.json").use { it.readBytes().toString(Charsets.UTF_8) }
            val o = JSONObject(text)
            acc = String.format(java.util.Locale.US, "%.2f%%", o.optDouble("test_accuracy", 0.0) * 100.0)
        } catch (ignored: Throwable) {
        }
        return getString(R.string.model_info, size, 28, acc) +
                "\nРаботает полностью на устройстве, без интернета."
    }

    // ---------- действия ----------

    private fun pickPhoto() {
        try {
            pickImage.launch("image/*")
        } catch (t: Throwable) {
            toast("Не удалось открыть галерею")
        }
    }

    private fun launchCamera() {
        try {
            val dir = File(cacheDir, "images")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "shot_" + System.currentTimeMillis() + ".jpg")
            pendingFile = file
            val uri = FileProvider.getUriForFile(this, packageName + ".fileprovider", file)
            takePicture.launch(uri)
        } catch (t: Throwable) {
            toast("Камера недоступна")
        }
    }

    private fun runPhoto(uri: Uri) {
        status.text = getString(R.string.recognizing)
        io.execute {
            try {
                val gray = PhotoLoader.fromUri(this, uri)
                recognize(gray)
            } catch (t: Throwable) {
                main.post { fail("Не удалось прочитать изображение: " + (t.message ?: "")) }
            }
        }
    }

    private fun runPhotoFile(file: File) {
        status.text = getString(R.string.recognizing)
        io.execute {
            try {
                val gray = PhotoLoader.fromFile(this, file)
                recognize(gray)
            } catch (t: Throwable) {
                main.post { fail("Не удалось прочитать изображение: " + (t.message ?: "")) }
            }
        }
    }

    private fun recognize(gray: com.creepyside.mathai.engine.Gray) {
        try {
            val clf = ensureClassifier()
            val lines = Pipeline.recognize(gray, clf)
            main.post { onRecognized(lines) }
        } catch (t: Throwable) {
            main.post { fail("Ошибка распознавания: " + (t.message ?: t.javaClass.simpleName)) }
        }
    }

    private fun onRecognized(lines: List<LineResult>) {
        val good = lines.filter { it.text.isNotBlank() }
        if (good.isEmpty()) {
            status.text = ""
            toast(getString(R.string.nothing_found))
            return
        }
        val best = good.maxByOrNull { it.text.length }!!
        status.text = ""
        cardRecognized.visibility = View.VISIBLE
        recognized.text = best.text
        input.setText(best.text)
        input.setSelection(best.text.length)
        solveNow()
    }

    private fun solveNow() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) {
            toast(getString(R.string.empty_input))
            return
        }
        try {
            val r = solve(text)
            showResult(r)
        } catch (e: SolveError) {
            fail(e.message ?: "не удалось решить")
        } catch (t: Throwable) {
            fail("Не удалось разобрать выражение")
        }
    }

    private fun showResult(r: SolveResult) {
        cardResult.visibility = View.VISIBLE
        if (r.mode == "equation") {
            result.text = if (r.roots.isEmpty()) "нет решений"
            else "x = " + r.roots.joinToString(",  ")
            resultSub.text = if (r.rootsDec.isEmpty()) "" else "≈ " + r.rootsDec.joinToString(",  ")
        } else {
            result.text = r.value ?: "?"
            resultSub.text = if (r.valueDec != null && r.valueDec != r.value) "≈ " + r.valueDec else ""
        }
        if (r.steps.isEmpty()) {
            cardSteps.visibility = View.GONE
        } else {
            cardSteps.visibility = View.VISIBLE
            val sb = StringBuilder()
            r.steps.forEachIndexed { i, s ->
                sb.append(i + 1).append(". ").append(s.before).append("\n   → ").append(s.after).append("\n")
            }
            steps.text = sb.toString().trim()
        }
        result.setOnLongClickListener {
            copy(result.text.toString())
            true
        }
    }

    private fun fail(message: String) {
        cardResult.visibility = View.VISIBLE
        cardSteps.visibility = View.GONE
        result.text = "—"
        resultSub.text = message
        status.text = ""
    }

    private fun copy(text: String) {
        try {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("MathAI", text))
            toast(getString(R.string.copy_done))
        } catch (ignored: Throwable) {
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }
}
