package com.creepyside.liquidclick.core

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object Ocr {

    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun read(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        client.process(image)
            .addOnSuccessListener { result -> if (cont.isActive) cont.resume(result.text) }
            .addOnFailureListener { if (cont.isActive) cont.resume("") }
            .addOnCanceledListener { if (cont.isActive) cont.resume("") }
    }
}
