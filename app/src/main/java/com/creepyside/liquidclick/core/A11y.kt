package com.creepyside.liquidclick.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.creepyside.liquidclick.data.Repo
import com.creepyside.liquidclick.model.ScreenSize

class A11y : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Repo.a11yReady = true
        val dm = resources.displayMetrics
        Repo.screen = ScreenSize(dm.widthPixels, dm.heightPixels)
        main.post {
            if (Repo.panelEnabled) Panel.show(this)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // не требуется
    }

    override fun onInterrupt() {
        // не требуется
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) {
            instance = null
            Repo.a11yReady = false
        }
        Panel.hide()
        Picker.close()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
            Repo.a11yReady = false
        }
        Panel.hide()
        Picker.close()
        super.onDestroy()
    }

    private fun dispatch(path: Path, duration: Long, done: (() -> Unit)? = null) {
        main.post {
            val stroke = GestureDescription.StrokeDescription(path, 0L, duration.coerceIn(1L, 29000L))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    done?.invoke()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    done?.invoke()
                }
            }
            runCatching { dispatchGesture(gesture, callback, null) }
        }
    }

    fun tap(x: Float, y: Float) {
        val p = Path()
        p.moveTo(x, y)
        p.lineTo(x, y)
        dispatch(p, 60L)
    }

    fun longPress(x: Float, y: Float, ms: Long) {
        val p = Path()
        p.moveTo(x, y)
        p.lineTo(x, y)
        dispatch(p, ms.coerceIn(120L, 20000L))
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long) {
        val p = Path()
        p.moveTo(x1, y1)
        p.lineTo(x2, y2)
        dispatch(p, ms.coerceIn(40L, 10000L))
    }

    fun pressBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun pressHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /** Вставляет текст в текущее сфокусированное поле ввода. */
    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findEditable(root) ?: return false
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        var ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!ok) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("liquidclick", text))
            ok = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }
        return ok
    }

    /** Есть ли на экране поле ввода. */
    fun hasEditable(): Boolean {
        val root = rootInActiveWindow ?: return false
        return findEditable(root) != null
    }

    private fun findEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            findEditable(child)?.let { return it }
        }
        return null
    }

    companion object {
        @Volatile
        var instance: A11y? = null

        fun ready(): Boolean = instance != null

        fun tapAt(x: Int, y: Int) {
            instance?.tap(x.toFloat(), y.toFloat())
        }
    }
}
