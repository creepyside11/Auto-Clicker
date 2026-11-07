package com.creepyside.liquidclick.core

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.provider.Settings
import com.creepyside.liquidclick.data.Repo

/** Два окна рядом: запуск приложений в заданных границах экрана. */
object Windows {

    const val LEFT = 0
    const val RIGHT = 1

    data class App(val packageName: String, val label: String)

    /** Список приложений с иконкой в лончере. */
    fun apps(ctx: Context): List<App> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val list = pm.queryIntentActivities(intent, 0)
        return list.mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
            App(pkg, label)
        }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** Поддержаны ли свободные окна (freeform). */
    fun freeformEnabled(ctx: Context): Boolean {
        val value = runCatching {
            Settings.Global.getInt(ctx.contentResolver, "enable_freeform_support", 0)
        }.getOrDefault(0)
        return value == 1
    }

    fun isLowRam(ctx: Context): Boolean =
        runCatching {
            (ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).isLowRamDevice
        }.getOrDefault(false)

    /** Границы окна для стороны экрана. */
    fun boundsFor(side: Int): Rect {
        val screen = Repo.screen
        val width = screen.w
        val height = screen.h
        val half = width / 2
        return if (side == LEFT) {
            Rect(0, 0, half, height)
        } else {
            Rect(half, 0, width, height)
        }
    }

    /** Открывает приложение в окне с заданными границами (или на весь экран). */
    fun open(ctx: Context, pkg: String, side: Int = LEFT): Boolean {
        val bounds = boundsFor(side)
        return openInBounds(ctx, pkg, bounds.left, bounds.top, bounds.width(), bounds.height())
    }

    fun openInBounds(
        ctx: Context,
        pkg: String,
        x: Int,
        y: Int,
        w: Int,
        h: Int
    ): Boolean {
        if (pkg.isBlank()) return false
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        )
        return runCatching {
            if (w > 0 && h > 0) {
                val options = ActivityOptions.makeBasic()
                options.setLaunchBounds(Rect(x, y, x + w, y + h))
                ctx.startActivity(intent, options.toBundle())
            } else {
                ctx.startActivity(intent)
            }
            true
        }.getOrDefault(
            runCatching { ctx.startActivity(intent); true }.getOrDefault(false)
        )
    }

    /** Пробует открыть два приложения бок о бок. Возвращает описание результата. */
    fun openPair(ctx: Context, leftPkg: String, rightPkg: String): String {
        if (leftPkg.isBlank() || rightPkg.isBlank()) return "Выберите приложения для обоих окон"
        val free = freeformEnabled(ctx)
        val leftOk = open(ctx, leftPkg, LEFT)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            open(ctx, rightPkg, RIGHT)
        }
        return when {
            !leftOk -> "Не удалось открыть " + leftPkg
            free -> "Открыто в свободных окнах"
            else -> "Открыто. Для окон рядом включите freeform (см. подсказку выше)"
        }
    }

    /** Перезапускает приложения в их окнах (сворачивание → открытие). */
    fun reopenPair(ctx: Context, leftPkg: String, rightPkg: String): String {
        A11y.instance?.pressHome()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            openPair(ctx, leftPkg, rightPkg)
        }, 450L)
        return "Свёрнуто и открыто заново"
    }

    fun close(ctx: Context, pkg: String) {
        // Остановить чужое приложение без системных прав нельзя; возвращаемся домой.
        A11y.instance?.pressHome()
    }

    fun label(ctx: Context, pkg: String): String {
        if (pkg.isBlank()) return "не выбрано"
        return runCatching {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }
}
