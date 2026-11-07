package com.creepyside.liquidclick.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Время в московском часовом поясе (МСК, UTC+3). */
object Times {
    val moscow: TimeZone = TimeZone.getTimeZone("Europe/Moscow")

    fun msk(millis: Long = System.currentTimeMillis(), withSeconds: Boolean = true): String {
        val pattern = if (withSeconds) "dd.MM.yyyy HH:mm:ss" else "dd.MM.yyyy HH:mm"
        val format = SimpleDateFormat(pattern, Locale.US)
        format.timeZone = moscow
        return format.format(Date(millis)) + " МСК"
    }

    fun shortMsk(millis: Long): String {
        val format = SimpleDateFormat("HH:mm", Locale.US)
        format.timeZone = moscow
        return format.format(Date(millis))
    }
}
