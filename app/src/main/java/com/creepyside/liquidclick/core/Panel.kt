package com.creepyside.liquidclick.core

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.creepyside.liquidclick.data.Repo
import com.creepyside.liquidclick.model.Watcher
import com.creepyside.liquidclick.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Плавающая стеклянная панель управления поверх других приложений. */
object Panel {

    private var root: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var wm: WindowManager? = null
    private var playLabel: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var ticker: Runnable? = null

    fun show(service: A11y) {
        if (root != null) return
        val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = windowManager

        val container = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(service, 8), dp(service, 8), dp(service, 8), dp(service, 8))
            background = GradientDrawable().apply {
                cornerRadius = dp(service, 30).toFloat()
                setColor(0xE60A1020.toInt())
                setStroke(dp(service, 1), 0x4DFFFFFF)
            }
            elevation = dp(service, 10).toFloat()
        }

        val handle = TextView(service).apply {
            text = "⠿"
            setTextColor(0x99FFFFFF.toInt())
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(service, 8), 0, dp(service, 8), 0)
            setOnTouchListener(dragListener(service, container))
        }

        val play = TextView(service).apply {
            text = if (Engine.running) "■" else "▶"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(dp(service, 14), dp(service, 10), dp(service, 14), dp(service, 10))
            background = GradientDrawable().apply {
                cornerRadius = dp(service, 22).toFloat()
                setColor(0x3322D3EE)
                setStroke(dp(service, 1), 0x667DF9FF)
            }
            setOnClickListener {
                if (Engine.running) {
                    Engine.stopScenario()
                    toast(service, "Остановлено")
                } else {
                    startLast(service)
                }
                refresh()
            }
        }
        playLabel = play

        val point = iconButton(service, "◎") {
            Picker.pickPoint { x, y ->
                Repo.click.x = x
                Repo.click.y = y
                Repo.save()
                toast(service, "Точка: $x, $y")
            }
        }

        val region = iconButton(service, "▭") {
            Picker.pickRegion { r ->
                val name = "Область " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                Repo.watchers.add(Watcher(name = name, region = r, enabled = false))
                Repo.save()
                toast(service, "Область сохранена — задайте текст в приложении")
            }
        }

        val open = iconButton(service, "⚙") {
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
        container.addView(point)
        container.addView(region)
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
        lp.x = dp(service, 12)
        lp.y = dp(service, 220)
        params = lp

        runCatching { windowManager.addView(container, lp) }
        root = container
        startTicker()
    }

    fun hide() {
        val view = root ?: return
        root = null
        stopTicker()
        runCatching { wm?.removeView(view) }
        playLabel = null
    }

    fun isShown(): Boolean = root != null

    private fun startTicker() {
        stopTicker()
        val runnable = object : Runnable {
            override fun run() {
                refresh()
                handler.postDelayed(this, 700)
            }
        }
        ticker = runnable
        handler.postDelayed(runnable, 700)
    }

    private fun stopTicker() {
        ticker?.let { handler.removeCallbacks(it) }
        ticker = null
    }

    private fun refresh() {
        playLabel?.text = if (Engine.running) "■" else "▶"
    }

    private fun startLast(service: A11y) {
        val scenario = Repo.findScenario(Repo.lastScenarioId) ?: Repo.scenarios.firstOrNull()
        if (scenario != null) {
            Engine.runScenario(service, scenario)
        } else {
            Engine.startClicker(service)
        }
    }

    private fun iconButton(service: A11y, symbol: String, action: () -> Unit): TextView {
        return TextView(service).apply {
            text = symbol
            setTextColor(0xE6FFFFFF.toInt())
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(service, 12), dp(service, 10), dp(service, 12), dp(service, 10))
            setOnClickListener { action() }
        }
    }

    private fun dragListener(service: A11y, container: View): View.OnTouchListener {
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

    private fun toast(context: Context, text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
