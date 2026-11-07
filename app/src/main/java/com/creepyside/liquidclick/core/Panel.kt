package com.creepyside.liquidclick.core

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.creepyside.liquidclick.data.Repo
import com.creepyside.liquidclick.model.StepType
import com.creepyside.liquidclick.model.Watcher
import com.creepyside.liquidclick.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Плавающая стеклянная панель у края экрана + раскрывающиеся настройки. */
object Panel {

    private var root: View? = null
    private var settingsRoot: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var settingsParams: WindowManager.LayoutParams? = null
    private var wm: WindowManager? = null
    private var playLabel: TextView? = null
    private var expanded = false
    private val handler = Handler(Looper.getMainLooper())
    private var ticker: Runnable? = null

    fun show(service: A11y) {
        if (root != null) return
        val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = windowManager
        val density = service.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val container = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(6), dp(8), dp(6), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(30).toFloat()
                setColor(withAlpha(0x0A1020, Repo.panelAlpha))
                setStroke(dp(1), 0x4DFFFFFF)
            }
            elevation = dp(10).toFloat()
        }

        val handle = TextView(service).apply {
            text = "⠿"
            setTextColor(0x99FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(2))
            setOnTouchListener(dragListener(container))
        }

        val play = iconButton(service, "▶", accent = true) {
            if (Engine.running) {
                Engine.stopScenario()
                toast(service, "Остановлено")
            } else {
                startSelected(service)
            }
            refresh()
        }
        playLabel = play

        val gear = iconButton(service, "⚙") {
            expanded = !expanded
            if (expanded) showSettings(service) else hideSettings()
            refresh()
        }

        val add = iconButton(service, "✚") {
            if (Bubbles.addBubble() == null) {
                toast(service, "Сначала выберите сценарий")
            } else {
                toast(service, "Кнопка добавлена — перетащите")
            }
            refresh()
        }

        val open = iconButton(service, "☰") {
            val intent = Intent(service, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            runCatching { service.startActivity(intent) }
        }

        val close = iconButton(service, "✕") {
            Repo.panelEnabled = false
            Repo.save()
            hide()
            toast(service, "Панель скрыта")
        }

        container.addView(handle)
        container.addView(play)
        container.addView(gear)
        container.addView(add)
        container.addView(open)
        container.addView(close)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = if (Repo.panelSide == 1) dp(8) else dp(6)
        lp.y = dp(170)
        params = lp

        runCatching { windowManager.addView(container, lp) }
        root = container
        if (Repo.panelSide == 1) {
            root?.post { snapToSide() }
        }
        startTicker()
    }

    fun hide() {
        hideSettings()
        val view = root ?: return
        root = null
        stopTicker()
        runCatching { wm?.removeView(view) }
        playLabel = null
    }

    fun isShown(): Boolean = root != null

    // ---------- Раскрытые настройки ----------

    private fun showSettings(service: A11y) {
        hideSettings()
        val windowManager = wm ?: return
        val density = service.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val card = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(withAlpha(0x0A1424, Repo.panelAlpha))
                setStroke(dp(1), 0x66FFFFFF)
            }
            elevation = dp(14).toFloat()
        }

        val scroll = ScrollView(service).apply {
            setBackgroundColor(0x00000000)
            addView(card)
        }

        val widthPx = (service.resources.displayMetrics.widthPixels * 0.78f).toInt()
        val lp = WindowManager.LayoutParams(
            widthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (Repo.panelSide == 1) dp(8) else dp(66)
            y = dp(170)
        }
        settingsParams = lp
        runCatching { windowManager.addView(scroll, lp) }
        settingsRoot = scroll
        buildSettings(service, card)
    }

    private fun hideSettings() {
        val view = settingsRoot ?: return
        settingsRoot = null
        runCatching { wm?.removeView(view) }
    }

    private fun buildSettings(service: A11y, card: LinearLayout) {
        val density = service.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        card.removeAllViews()

        fun title(text: String) {
            card.addView(TextView(service).apply {
                this.text = text.uppercase()
                setTextColor(0x889FB6C9.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
                letterSpacing = 0.14f
                setPadding(0, dp(10), 0, dp(4))
            })
        }

        fun row(vararg views: View) {
            val row = LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            views.forEach { v ->
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                lp.marginEnd = dp(5)
                row.addView(v, lp)
            }
            card.addView(row)
        }

        fun text(value: String, color: Int = 0xFFEAF7FF.toInt(), size: Float = 12f): TextView =
            TextView(service).apply {
                text = value
                setTextColor(color)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
                gravity = Gravity.CENTER
                maxLines = 2
            }

        fun action(label: String, accent: Boolean = false, onClick: () -> Unit): TextView =
            TextView(service).apply {
                text = label
                setTextColor(0xFFEAF7FF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(9), dp(8), dp(9))
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(if (accent) 0x5522D3EE else 0x1AFFFFFF)
                    setStroke(dp(1), if (accent) 0xAA7DF9FF.toInt() else 0x33FFFFFF)
                }
                setOnClickListener {
                    onClick()
                    buildSettings(service, card)
                    refresh()
                }
            }

        // --- Сценарий ---
        title("Сценарий")
        val scenarios = Repo.scenarios
        val index = scenarios.indexOfFirst { it.id == Repo.lastScenarioId }.coerceAtLeast(0)
        val current = scenarios.getOrNull(index)
        row(
            action("◀") {
                if (scenarios.isNotEmpty()) {
                    val next = (index - 1 + scenarios.size) % scenarios.size
                    Repo.lastScenarioId = scenarios[next].id
                    Repo.touch()
                }
            },
            text(current?.name ?: "нет сценариев", if (current != null) 0xFF7DF9FF.toInt() else 0xFF9FB6C9.toInt()),
            action("▶") {
                if (scenarios.isNotEmpty()) {
                    val next = (index + 1) % scenarios.size
                    Repo.lastScenarioId = scenarios[next].id
                    Repo.touch()
                }
            }
        )
        row(
            action(if (Engine.running) "■ Стоп" else "▶ Запуск", accent = !Engine.running) {
                if (Engine.running) Engine.stopScenario() else startSelected(service)
            },
            action("✚ Кнопка на экран") {
                Bubbles.showFor(Repo.lastScenarioId)
                Bubbles.addBubble()
            }
        )
        row(
            action(if (Bubbles.isShown()) "◉ Кнопки видны" else "◌ Показать кнопки") {
                Bubbles.toggle()
            },
            action("✕ Скрыть и удалить все") {
                Bubbles.hideAll()
                Bubbles.clearSteps()
            }
        )

        // --- Кликер ---
        title("Кликер")
        val cfg = Repo.click
        row(
            action("Тап", cfg.mode == StepType.TAP) {
                Repo.click = Repo.click.copy(mode = StepType.TAP); Repo.touch()
            },
            action("Зажатие", cfg.mode == StepType.LONG) {
                Repo.click = Repo.click.copy(mode = StepType.LONG); Repo.touch()
            },
            action("Свайп", cfg.mode == StepType.SWIPE) {
                Repo.click = Repo.click.copy(mode = StepType.SWIPE); Repo.touch()
            }
        )
        row(
            action("−") {
                Repo.click = Repo.click.copy(
                    intervalMs = (Repo.click.intervalMs - 50).coerceAtLeast(10)
                ); Repo.touch()
            },
            text("интервал " + formatMs(cfg.intervalMs)),
            action("＋") {
                Repo.click = Repo.click.copy(
                    intervalMs = (Repo.click.intervalMs + 50).coerceAtMost(3000)
                ); Repo.touch()
            }
        )
        row(
            action("−") {
                Repo.click = Repo.click.copy(count = (Repo.click.count - 10).coerceAtLeast(0)); Repo.touch()
            },
            text(if (cfg.count == 0) "повторы ∞" else "повторы " + cfg.count),
            action("＋") {
                Repo.click = Repo.click.copy(count = (Repo.click.count + 10).coerceAtMost(5000)); Repo.touch()
            }
        )
        row(
            action("◎ Точка") { Picker.pickPoint { x, y ->
                Repo.click = Repo.click.copy(x = x, y = y); Repo.touch()
            } },
            action("▭ Область") { Picker.pickRegion { r ->
                Repo.watchers.add(
                    Watcher(
                        name = "Область " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                        region = r,
                        enabled = false
                    )
                )
                Repo.touch()
                toast(service, "Область сохранена")
            } }
        )
        row(
            action(if (Engine.running) "■ Кликер идёт" else "▶ Запустить кликер", accent = !Engine.running) {
                if (Engine.running) Engine.stopScenario() else Engine.startClicker(service)
            },
            action("◎ Задать точку клика") { Picker.pickPoint { x, y ->
                Repo.click = Repo.click.copy(x = x, y = y); Repo.touch()
            } }
        )

        // --- Текст ---
        title("Текст")
        val lastText = Repo.quickTexts.firstOrNull() ?: "нет"
        row(
            action("✎ Ввести текст", accent = true) { TextInput.show(service) },
            action("Вставить") {
                val text = Repo.quickTexts.firstOrNull()
                if (text.isNullOrBlank()) {
                    toast(service, "Нет сохранённого текста")
                } else {
                    val ok = A11y.instance?.typeText(text) ?: false
                    toast(service, if (ok) "Вставлено" else "Поле ввода не найдено")
                }
            }
        )
        card.addView(TextView(service).apply {
            this.text = "последний: " + lastText.take(40)
            setTextColor(0x889FB6C9.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setPadding(0, dp(4), 0, 0)
        })
        Repo.quickTexts.take(3).forEach { quick ->
            card.addView(TextView(service).apply {
                text = "↳ " + quick.take(34)
                setTextColor(0xFF7DF9FF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(dp(4), dp(5), dp(4), dp(5))
                setOnClickListener {
                    val ok = A11y.instance?.typeText(quick) ?: false
                    toast(service, if (ok) "Вставлено" else "Поле ввода не найдено")
                    buildSettings(service, card)
                }
            })
        }

        // --- Наблюдение ---
        title("Наблюдение")
        row(
            action(if (Engine.watchersLive) "■ Остановить" else "▶ Запустить", Engine.watchersLive) {
                if (!CaptureService.isReady()) {
                    toast(service, "Разрешите захват экрана в приложении")
                } else {
                    Engine.toggleWatchers(service)
                }
            },
            action("▭ Новая область") { Picker.pickRegion { r ->
                Repo.watchers.add(
                    Watcher(
                        name = "Область " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                        region = r,
                        enabled = false
                    )
                )
                Repo.touch()
                toast(service, "Область сохранена")
            } }
        )
        card.addView(TextView(service).apply {
            text = "наблюдений: " + Repo.watchers.size + " · включено: " + Repo.watchers.count { it.enabled }
            setTextColor(0x889FB6C9.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setPadding(0, dp(4), 0, 0)
        })

        // --- Окна ---
        title("Два окна")
        card.addView(TextView(service).apply {
            text = Windows.label(service, Repo.leftPkg) + "  |  " + Windows.label(service, Repo.rightPkg)
            setTextColor(0xFF7DF9FF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setPadding(0, dp(2), 0, dp(2))
        })
        row(
            action("Открыть оба") {
                Windows.openPair(service, Repo.leftPkg, Repo.rightPkg)
            },
            action("Свернуть") {
                A11y.instance?.pressHome()
            }
        )
        row(
            action(if (Engine.running) "■ Стоп" else "▶ Цикл окон", accent = !Engine.running) {
                if (Engine.running) {
                    Engine.stopScenario()
                } else {
                    Engine.runPair(
                        ctx = service,
                        leftPkg = Repo.leftPkg,
                        rightPkg = Repo.rightPkg,
                        leftScenario = Repo.findScenario(Repo.leftScenarioId),
                        rightScenario = Repo.findScenario(Repo.rightScenarioId),
                        loops = Repo.pairLoops,
                        forever = Repo.pairForever,
                        collapseEachLoop = Repo.pairCollapse
                    )
                }
            },
            action("Открыть заново") {
                Windows.reopenPair(service, Repo.leftPkg, Repo.rightPkg)
            }
        )

        // --- Панель ---
        title("Панель")
        row(
            action("Слева", Repo.panelSide == 0) { Repo.panelSide = 0; Repo.touch(); moveToSide(service) },
            action("Справа", Repo.panelSide == 1) { Repo.panelSide = 1; Repo.touch(); moveToSide(service) }
        )
        row(
            action("− прозрачность") {
                Repo.panelAlpha = (Repo.panelAlpha - 10).coerceIn(30, 100); Repo.touch()
            },
            text(Repo.panelAlpha.toString() + "%"),
            action("＋") {
                Repo.panelAlpha = (Repo.panelAlpha + 10).coerceIn(30, 100); Repo.touch()
            }
        )
        row(
            action("Открыть приложение") {
                val intent = Intent(service, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                runCatching { service.startActivity(intent) }
            },
            action("Скрыть панель") {
                Repo.panelEnabled = false
                Repo.save()
                hide()
            }
        )
        card.addView(TextView(service).apply {
            text = "Сценариев: " + Repo.scenarios.size + " · шагов: " + (Repo.findScenario(Repo.lastScenarioId)?.steps?.size ?: 0)
            setTextColor(0x889FB6C9.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setPadding(0, dp(8), 0, dp(2))
        })
    }

    private fun moveToSide(service: A11y) {
        val lp = params ?: return
        val width = service.resources.displayMetrics.widthPixels
        val density = service.resources.displayMetrics.density
        lp.x = if (Repo.panelSide == 1) width - (60 * density).toInt() else (6 * density).toInt()
        runCatching { wm?.updateViewLayout(root, lp) }
        settingsParams?.let { sp ->
            sp.x = if (Repo.panelSide == 1) (8 * density).toInt() else (66 * density).toInt()
            runCatching { wm?.updateViewLayout(settingsRoot, sp) }
        }
    }

    private fun snapToSide() {
        val service = A11y.instance ?: return
        moveToSide(service)
    }

    // ---------- Служебное ----------

    private fun startTicker() {
        stopTicker()
        val runnable = object : Runnable {
            override fun run() {
                refresh()
                handler.postDelayed(this, 900)
            }
        }
        ticker = runnable
        handler.postDelayed(runnable, 900)
    }

    private fun stopTicker() {
        ticker?.let { handler.removeCallbacks(it) }
        ticker = null
    }

    private fun refresh() {
        playLabel?.text = if (Engine.running) "■" else "▶"
    }

    private fun startSelected(service: A11y) {
        val scenario = Repo.findScenario(Repo.lastScenarioId) ?: Repo.scenarios.firstOrNull()
        if (scenario != null) {
            Engine.runScenario(service, scenario)
        } else {
            Engine.startClicker(service)
        }
    }

    private fun iconButton(
        service: A11y,
        symbol: String,
        accent: Boolean = false,
        action: () -> Unit
    ): TextView {
        val density = service.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        return TextView(service).apply {
            text = symbol
            setTextColor(0xF2FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(9), dp(8), dp(9))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (accent) {
                    setColor(0x4022D3EE)
                    setStroke(dp(1), 0x807DF9FF.toInt())
                } else {
                    setColor(0x14FFFFFF)
                }
            }
            val lp = LinearLayout.LayoutParams(dp(40), dp(40))
            lp.topMargin = dp(5)
            layoutParams = lp
            setOnClickListener { action() }
        }
    }

    private fun dragListener(container: View): View.OnTouchListener {
        var startRawX = 0f
        var startRawY = 0f
        var startX = 0
        var startY = 0
        return View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    startX = params?.x ?: 0
                    startY = params?.y ?: 0
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val lp = params
                    if (lp != null) {
                        lp.x = startX + (event.rawX - startRawX).toInt()
                        lp.y = startY + (event.rawY - startRawY).toInt()
                        runCatching { wm?.updateViewLayout(container, lp) }
                    }
                    true
                }

                else -> true
            }
        }
    }

    private fun withAlpha(color: Int, percent: Int): Int {
        val alpha = (255 * percent.coerceIn(0, 100) / 100)
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }

    private fun formatMs(value: Long): String = when {
        value >= 60000L -> (value / 60000L).toString() + " мин"
        value >= 1000L -> String.format(Locale.US, "%.1f", value / 1000f) + " с"
        else -> value.toString() + " мс"
    }

    private fun toast(context: Context, text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}
