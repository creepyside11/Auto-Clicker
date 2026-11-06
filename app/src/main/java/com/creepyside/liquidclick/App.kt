package com.creepyside.liquidclick

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_RUN, "Автоматизация", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_CAPTURE, "Захват экрана", NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        const val CH_RUN = "liquidclick_run"
        const val CH_CAPTURE = "liquidclick_capture"
    }
}
