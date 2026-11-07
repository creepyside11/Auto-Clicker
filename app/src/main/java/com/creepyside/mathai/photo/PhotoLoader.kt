package com.creepyside.mathai.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.creepyside.mathai.engine.Gray
import java.io.File
import java.io.InputStream

/** Загрузка фото -> оттенки серого (0..1, 1 = белый). */
object PhotoLoader {

    private const val MAX_SIDE = 1600

    fun fromUri(context: Context, uri: Uri): Gray {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val sample = sampleSize(bounds.outWidth, bounds.outHeight)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bmp: Bitmap? = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (bmp == null) throw IllegalStateException("не удалось открыть изображение")
        val rotation = readRotation(context, uri)
        val fixed = applyRotation(bmp, rotation)
        return toGray(fixed)
    }

    fun fromFile(context: Context, file: File): Gray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bmp = BitmapFactory.decodeFile(file.absolutePath, opts)
            ?: throw IllegalStateException("не удалось открыть изображение")
        var rotation = 0
        try {
            val exif = ExifInterface(file.absolutePath)
            rotation = exifRotation(exif)
        } catch (ignored: Throwable) {
        }
        val fixed = applyRotation(bmp, rotation)
        return toGray(fixed)
    }

    private fun readRotation(context: Context, uri: Uri): Int {
        return try {
            val stream: InputStream? = context.contentResolver.openInputStream(uri)
            if (stream == null) 0 else stream.use { exifRotation(ExifInterface(it)) }
        } catch (t: Throwable) {
            0
        }
    }

    private fun exifRotation(exif: ExifInterface): Int {
        return when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }

    private fun applyRotation(src: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return src
        val m = Matrix()
        m.postRotate(degrees.toFloat())
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (out != src) src.recycle()
        return out
    }

    private fun sampleSize(w: Int, h: Int): Int {
        var sample = 1
        var m = maxOf(w, h)
        while (m / sample > MAX_SIDE) sample *= 2
        return sample
    }

    private fun toGray(bmp: Bitmap): Gray {
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        val data = FloatArray(w * h)
        for (i in 0 until w * h) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            data[i] = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        }
        return Gray(w, h, data)
    }
}
