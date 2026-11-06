package com.creepyside.liquidclick.core

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.creepyside.liquidclick.data.Repo
import com.creepyside.liquidclick.model.Region
import com.creepyside.liquidclick.model.Scenario
import com.creepyside.liquidclick.model.Step
import com.creepyside.liquidclick.model.StepType
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Второй способ создания сценария: перетаскиваемые кнопки-«капли» прямо на экране.
 * Каждая кнопка — шаг сценария, её положение и есть точка нажатия.
 */
object Bubbles {

    private val windows = LinkedHashMap<String, View>()
    private var activeScenarioId: String = ""
    private var shown = false
    private var hiddenForRun = false

    fun isShown(): Boolean = shown

    fun activeScenario(): Scenario? =
        Repo.findScenario(activeScenarioId) ?: Repo.findScenario(Repo.lastScenarioId)

    fun showFor(scenarioId: String) {
        val scenario = Repo.findScenario(scenarioId) ?: return
        activeScenarioId = scenario.id
        Repo.lastScenarioId = scenario.id
        Repo.save()
        shown = true
        Repo.bubblesEnabled = true
        Repo.save()
        rebuild()
    }

    fun showActive() {
        val id = activeScenarioId.ifBlank { Repo.lastScenarioId }
        val scenario = Repo.findScenario(id) ?: Repo.scenarios.firstOrNull() ?: return
        showFor(scenario.id)
    }

    fun hideAll() {
        shown = false
        Repo.bubblesEnabled = false
        Repo.save()
        detachWindows()
    }

    private fun detachWindows() {
        val service = A11y.instance ?: run {
            windows.clear()
            return
        }
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windows.values.forEach { view -> runCatching { wm.removeView(view) } }
        windows.clear()
    }

    fun toggle(): Boolean {
        if (shown) {
            hideAll()
        } else {
            showActive()
        }
        return shown
    }

    fun hideForRun() {
        if (!shown) return
        hiddenForRun = true
        shown = false
        detachWindows()
    }

    fun restoreAfterRun() {
        if (!hiddenForRun) return
        hiddenForRun = false
        showActive()
    }

    fun rebuild() {
        val service = A11y.instance ?: return
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windows.values.forEach { runCatching { wm.removeView(it) } }
        windows.clear()
        if (!shown) return
        val scenario = activeScenario() ?: return
        scenario.steps.forEachIndexed { index, step ->
            if (!hasPoint(step)) return@forEachIndexed
            addWindow(service, wm, scenario, step, index)
        }
    }

    /** Создаёт новый шаг-кнопку в центре экрана и сразу показывает её. */
    fun addBubble(): Step? {
        val scenario = activeScenario() ?: run {
            val created = Scenario(name = "Быстрый сценарий")
            Repo.scenarios.add(created)
            activeScenarioId = created.id
            Repo.lastScenarioId = created.id
            Repo.save()
            created
        }
        val offset = (windows.size % 5) * 24
        val step = Step(
            type = StepType.TAP,
            x = Repo.screen.w / 2 + offset,
            y = Repo.screen.h / 2 + offset
        )
        scenario.steps.add(step)
        Repo.touch()
        shown = true
        Repo.bubblesEnabled = true
        Repo.save()
        rebuild()
        val context = A11y.instance
        if (context != null) {
            Toast.makeText(context, "Кнопка добавлена — перетащите её", Toast.LENGTH_SHORT).show()
        }
        return step
    }

    fun deleteStep(scenario: Scenario, step: Step) {
        scenario.steps.removeAll { it.id == step.id }
        Repo.touch()
        rebuild()
    }

    private fun hasPoint(step: Step): Boolean = when (step.type) {
        StepType.TAP, StepType.LONG, StepType.TEXT, StepType.CHECK -> step.x > 0 && step.y > 0
        StepType.SWIPE -> step.x > 0 && step.y > 0
        else -> false
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addWindow(
        service: A11y,
        wm: WindowManager,
        scenario: Scenario,
        step: Step,
        index: Int
    ) {
        val density = service.resources.displayMetrics.density
        val size = (58 * density).roundToInt()

        val container = FrameLayout(service)
        val circle = TextView(service).apply {
            text = (index + 1).toString()
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xCC0B1B2E.toInt())
                setStroke((1.5f * density).roundToInt(), 0xCC7DF9FF.toInt())
            }
        }
        val badge = TextView(service).apply {
            text = "✕"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 10f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xE6FF5C7A.toInt())
            }
        }
        val badgeSize = (18 * density).roundToInt()
        container.addView(circle, FrameLayout.LayoutParams(size, size))
        container.addView(
            badge,
            FrameLayout.LayoutParams(badgeSize, badgeSize).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = -badgeSize / 4
                marginEnd = -badgeSize / 4
            }
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = step.x - size / 2
            y = step.y - size / 2
        }

        badge.setOnClickListener {
            deleteStep(scenario, step)
            Toast.makeText(service, "Шаг удалён", Toast.LENGTH_SHORT).show()
        }

        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        container.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (abs(dx) > 6f || abs(dy) > 6f) moved = true
                    if (moved) {
                        params.x = startX + dx.roundToInt()
                        params.y = startY + dy.roundToInt()
                        runCatching { wm.updateViewLayout(container, params) }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        step.x = (params.x + size / 2).coerceAtLeast(0)
                        step.y = (params.y + size / 2).coerceAtLeast(0)
                        Repo.touch()
                        Toast.makeText(
                            service,
                            "Шаг ${index + 1}: ${step.x}, ${step.y}",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            service,
                            "${StepType.title(step.type)} · ${step.x}, ${step.y}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    true
                }

                else -> true
            }
        }

        runCatching { wm.addView(container, params) }
        windows[step.id] = container
    }

    /** Прямоугольник области для шага-условия (если задан) — для подсветки. */
    fun regionOf(step: Step): Region? = step.region
}
