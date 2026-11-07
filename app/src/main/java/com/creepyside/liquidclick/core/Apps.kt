package com.creepyside.liquidclick.core

import android.content.Context
import android.content.Intent

/** Список установленных приложений с иконкой в лончере. */
object Apps {

    data class Entry(val packageName: String, val label: String)

    fun list(ctx: Context): List<Entry> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val found = pm.queryIntentActivities(intent, 0)
        return found.mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
            Entry(pkg, label)
        }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun label(ctx: Context, pkg: String): String {
        if (pkg.isBlank()) return pkg
        return runCatching {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    /** Открывает приложение обычным способом. */
    fun open(ctx: Context, pkg: String): Boolean {
        if (pkg.isBlank()) return false
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { ctx.startActivity(intent); true }.getOrDefault(false)
    }
}
