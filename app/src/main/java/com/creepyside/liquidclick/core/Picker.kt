package com.creepyside.liquidclick.core

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.creepyside.liquidclick.model.Region
import kotlin.math.max
import kotlin.math.min

/** Полноэкранный выбор точки или прямоугольной области поверх любых приложений. */
object Picker {

    private var overlay: View? = null

    fun pickRegion(callback: (Region) -> Unit) {
        val service = A11y.instance ?: return
        close()
        val view = PickView(service, true, callback, null) { close() }
        show(service, view)
    }

    fun pickPoint(callback: (Int, Int) -> Unit) {
        val service = A11y.instance ?: return
        close()
        val view = PickView(service, false, null, callback) { close() }
        show(service, view)
    }

    /** Если открыт наш экран — свернуть его, чтобы видеть целевое приложение. */
    private fun show(service: A11y, view: View) {
        if (com.creepyside.liquidclick.ui.MainActivity.visible) {
            service.pressHome()
            Handler(Looper.getMainLooper()).postDelayed({ attach(service, view) }, 320L)
        } else {
            attach(service, view)
        }
    }

    fun close() {
        val view = overlay ?: return
        overlay = null
        val service = A11y.instance
        if (service != null) {
            runCatching {
                (service.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view)
            }
        }
    }

    private fun attach(service: A11y, view: View) {
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        runCatching { wm.addView(view, params) }
        overlay = view
        view.postDelayed({ close() }, 90000L)
    }

    private class PickView(
        context: Context,
        private val regionMode: Boolean,
        private val onRegion: ((Region) -> Unit)?,
        private val onPoint: ((Int, Int) -> Unit)?,
        private val onDone: () -> Unit
    ) : View(context) {

        private var startX = 0f
        private var startY = 0f
        private var endX = 0f
        private var endY = 0f
        private var active = false
        private val density = resources.displayMetrics.density

        private val dim = Paint().apply { color = 0x8C05070D.toInt() }
        private val clear = Paint().apply { color = 0x00000000; xfermode = null }
        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
            color = 0xFF7DF9FF.toInt()
        }
        private val fillRect = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x227DF9FF
            style = Paint.Style.FILL
        }
        private val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
            color = 0xFFFFFFFF.toInt()
        }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 15f * density
            isFakeBoldText = true
        }
        private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCFFFFFF.toInt()
            textSize = 14f * density
        }

        init {
            isClickable = true
            isFocusable = false
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    endX = startX
                    endY = startY
                    active = true
                    invalidate()
                }

                MotionEvent.ACTION_MOVE -> {
                    endX = event.rawX
                    endY = event.rawY
                    invalidate()
                }

                MotionEvent.ACTION_UP -> {
                    endX = event.rawX
                    endY = event.rawY
                    active = false
                    if (regionMode) {
                        val region = Region(
                            min(startX, endX).toInt(),
                            min(startY, endY).toInt(),
                            max(startX, endX).toInt(),
                            max(startY, endY).toInt()
                        )
                        if (region.valid) onRegion?.invoke(region)
                    } else {
                        onPoint?.invoke(endX.toInt(), endY.toInt())
                    }
                    onDone()
                }

                MotionEvent.ACTION_CANCEL -> {
                    active = false
                    onDone()
                }
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dim)
            if (regionMode) {
                val rect = RectF(
                    min(startX, endX),
                    min(startY, endY),
                    max(startX, endX),
                    max(startY, endY)
                )
                canvas.drawRect(rect, fillRect)
                canvas.drawRect(rect, border)
                val text = rect.width().toInt().toString() + " × " + rect.height().toInt()
                canvas.drawText(text, rect.left + 12f * density, (rect.top - 12f * density).coerceAtLeast(40f), label)
                drawCorners(canvas, rect)
                canvas.drawText(
                    "Проведите по области · отпустите для выбора",
                    24f * density,
                    64f * density,
                    hint
                )
            } else {
                canvas.drawLine(startX - 40f * density, startY, startX + 40f * density, startY, cross)
                canvas.drawLine(startX, startY - 40f * density, startX, startY + 40f * density, cross)
                canvas.drawCircle(startX, startY, 14f * density, cross)
                canvas.drawText(
                    "Нажмите в нужной точке · " + startX.toInt() + ", " + startY.toInt(),
                    24f * density,
                    64f * density,
                    hint
                )
            }
        }

        private fun drawCorners(canvas: Canvas, rect: RectF) {
            val len = 18f * density
            val w = 4f * density
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = w
                color = 0xFFFFFFFF.toInt()
            }
            canvas.drawLine(rect.left, rect.top, rect.left + len, rect.top, paint)
            canvas.drawLine(rect.left, rect.top, rect.left, rect.top + len, paint)
            canvas.drawLine(rect.right, rect.top, rect.right - len, rect.top, paint)
            canvas.drawLine(rect.right, rect.top, rect.right, rect.top + len, paint)
            canvas.drawLine(rect.left, rect.bottom, rect.left + len, rect.bottom, paint)
            canvas.drawLine(rect.left, rect.bottom, rect.left, rect.bottom - len, paint)
            canvas.drawLine(rect.right, rect.bottom, rect.right - len, rect.bottom, paint)
            canvas.drawLine(rect.right, rect.bottom, rect.right, rect.bottom - len, paint)
        }
    }
}
