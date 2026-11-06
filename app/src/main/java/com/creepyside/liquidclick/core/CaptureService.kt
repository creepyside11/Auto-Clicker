package com.creepyside.liquidclick.core

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import com.creepyside.liquidclick.App
import com.creepyside.liquidclick.data.Repo

class CaptureService : Service() {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var width = 0
    private var height = 0
    private var density = 320
    private var thread: HandlerThread? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) {
                Handler(Looper.getMainLooper()).post { setup() }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (pendingData == null) {
            // Токен захвата потерян (перезапуск процесса) — служба не нужна.
            stopSelf()
            return
        }
        instance = this
        val started = runCatching { startNotification() }.isSuccess
        if (!started) {
            instance = null
            stopSelf()
            return
        }
        thread = HandlerThread("liquidclick-capture").also { it.start() }
        val dm = getSystemService(DisplayManager::class.java)
        dm.registerDisplayListener(displayListener, Handler(thread!!.looper))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (pendingData == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (projection == null) {
            startProjection()
        }
        Repo.captureReady = projection != null
        return START_NOT_STICKY
    }

    private fun startNotification() {
        val builder = Notification.Builder(this, App.CH_CAPTURE)
            .setContentTitle("LiquidClick")
            .setContentText("Распознавание экрана включено")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
        val notification: Notification = builder.build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun startProjection() {
        val data = pendingData ?: return
        val code = pendingResultCode
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val p = runCatching { manager.getMediaProjection(code, data) }.getOrNull() ?: return
        projection = p
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                releaseAll()
            }
        }, Handler(Looper.getMainLooper()))
        setup()
    }

    private fun setup() {
        val p = projection ?: return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        val d = metrics.densityDpi
        if (w == width && h == height && reader != null) return
        width = w
        height = h
        density = d
        runCatching { virtualDisplay?.release() }
        runCatching { reader?.close() }
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader = r
        virtualDisplay = p.createVirtualDisplay(
            "liquidclick",
            w,
            h,
            d,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            r.surface,
            null,
            null
        )
        Repo.captureReady = true
    }

    private fun grab(): Bitmap? {
        val r = reader ?: return null
        var image: android.media.Image? = null
        var i = 0
        while (i < 30) {
            image = runCatching { r.acquireLatestImage() }.getOrNull()
            if (image != null) break
            runCatching { Thread.sleep(16) }
            i++
        }
        val img = image ?: return null
        return try {
            val plane = img.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width
            val bmp = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(buffer)
            if (rowPadding == 0) {
                bmp
            } else {
                val cropped = Bitmap.createBitmap(bmp, 0, 0, width, height)
                bmp.recycle()
                cropped
            }
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { img.close() }
        }
    }

    private fun releaseAll() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { reader?.close() }
        reader = null
        runCatching { projection?.stop() }
        projection = null
        Repo.captureReady = false
        instance = null
    }

    override fun onDestroy() {
        runCatching {
            getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        }
        releaseAll()
        runCatching { thread?.quitSafely() }
        thread = null
        stopForegroundCompat()
        super.onDestroy()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        private const val NOTIF_ID = 4211

        @Volatile
        var instance: CaptureService? = null

        @Volatile
        var pendingResultCode: Int = 0

        @Volatile
        var pendingData: Intent? = null

        fun capture(): Bitmap? = instance?.grab()

        fun isReady(): Boolean = instance != null

        fun start(ctx: Context) {
            val intent = Intent(ctx, CaptureService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, CaptureService::class.java))
        }
    }
}
