package com.creepyside.liquidclick.core

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.creepyside.liquidclick.data.Repo

/** Плавающее поле ввода: набрать текст и вставить его в активное поле приложения. */
object TextInput {

    private var root: View? = null
    private var wm: WindowManager? = null

    fun isShown(): Boolean = root != null

    fun show(service: A11y, initial: String = "") {
        close()
        val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = windowManager
        val density = service.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val card = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(0xF20A1424.toInt())
                setStroke(dp(1), 0x66FFFFFF)
            }
            elevation = dp(14).toFloat()
        }

        val title = TextView(service).apply {
            text = "Текст для вставки"
            setTextColor(0xFFEAF7FF.toInt())
            textSize = 15f
            setPadding(0, 0, 0, dp(8))
        }

        val field = EditText(service).apply {
            setText(initial)
            hint = "Введите текст, который нужно вставить"
            setHintTextColor(0x889FB6C9.toInt())
            setTextColor(0xFFEAF7FF.toInt())
            textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            maxLines = 6
            setBackgroundColor(0x1AFFFFFF)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setSelection(text?.length ?: 0)
        }

        val row = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(10), 0, 0)
        }

        fun button(label: String, accent: Boolean = false, action: () -> Unit): TextView =
            TextView(service).apply {
                text = label
                setTextColor(0xFFEAF7FF.toInt())
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(9), dp(14), dp(9))
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(if (accent) 0x5522D3EE else 0x1AFFFFFF)
                    setStroke(dp(1), if (accent) 0xAA7DF9FF.toInt() else 0x33FFFFFF)
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.marginStart = dp(6)
                layoutParams = lp
                setOnClickListener { action() }
            }

        row.addView(button("Отмена") { close() })
        row.addView(button("Сохранить") {
            val text = field.text.toString().trim()
            if (text.isNotEmpty()) {
                Repo.quickTexts.remove(text)
                Repo.quickTexts.add(0, text)
                while (Repo.quickTexts.size > 20) Repo.quickTexts.removeAt(Repo.quickTexts.lastIndex)
                Repo.touch()
                Toast.makeText(service, "Сохранено в быстрые тексты", Toast.LENGTH_SHORT).show()
            }
            close()
        })
        row.addView(button("Вставить", accent = true) {
            val text = field.text.toString()
            if (text.isBlank()) {
                Toast.makeText(service, "Введите текст", Toast.LENGTH_SHORT).show()
            } else {
                Repo.quickTexts.remove(text)
                Repo.quickTexts.add(0, text)
                while (Repo.quickTexts.size > 20) Repo.quickTexts.removeAt(Repo.quickTexts.lastIndex)
                Repo.touch()
                close()
                // ждём, пока фокус вернётся в приложение
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    val ok = A11y.instance?.typeText(text) ?: false
                    Toast.makeText(
                        service,
                        if (ok) "Текст вставлен" else "Поле ввода не найдено — текст скопирован",
                        Toast.LENGTH_SHORT
                    ).show()
                    if (!ok) {
                        val cm = service.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("liquidclick", text))
                    }
                }, 350L)
            }
        })

        card.addView(title)
        card.addView(field)
        card.addView(row)

        val scroll = ScrollView(service).apply {
            setBackgroundColor(0x00000000)
            addView(card)
        }

        val params = WindowManager.LayoutParams(
            (service.resources.displayMetrics.widthPixels * 0.92f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        runCatching { windowManager.addView(scroll, params) }
        root = scroll
        field.requestFocus()
        field.postDelayed({
            val imm = service.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            runCatching { imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT) }
        }, 250L)
    }

    fun close() {
        val view = root ?: return
        root = null
        runCatching { wm?.removeView(view) }
    }
}
