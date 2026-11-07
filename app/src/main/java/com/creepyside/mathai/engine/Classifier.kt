package com.creepyside.mathai.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Инференс обученной CNN на чистом Kotlin.
 *
 * Архитектура (совпадает с обучением):
 * 32x32x1 -> conv3x3(16)+relu -> maxpool2 -> 16x16x16
 *         -> conv3x3(32)+relu -> maxpool2 -> 8x8x32
 *         -> conv3x3(64)+relu -> maxpool2 -> 4x4x64
 *         -> flatten 1024 -> dense 128 + relu -> dense N
 */
class Classifier(data: ByteArray) {

    companion object {
        const val INPUT = 32
        const val INNER = 24
    }

    private val c1w: FloatArray
    private val c1b: FloatArray
    private val c2w: FloatArray
    private val c2b: FloatArray
    private val c3w: FloatArray
    private val c3b: FloatArray
    private val f1w: FloatArray
    private val f1b: FloatArray
    private val f2w: FloatArray
    private val f2b: FloatArray
    val numClasses: Int

    init {
        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(4)
        bb.get(magic)
        val ok = magic[0] == 'M'.code.toByte() && magic[1] == 'A'.code.toByte() &&
                magic[2] == 'I'.code.toByte() && magic[3] == '1'.code.toByte()
        if (!ok) throw IllegalArgumentException("плохой файл модели")
        val count = bb.int
        if (count < 10) throw IllegalArgumentException("мало тензоров в модели: $count")
        val tensors = arrayOfNulls<FloatArray>(count)
        for (k in 0 until count) {
            val size = bb.int
            val arr = FloatArray(size)
            for (i in 0 until size) arr[i] = bb.float
            tensors[k] = arr
        }
        c1w = tensors[0]!!
        c1b = tensors[1]!!
        c2w = tensors[2]!!
        c2b = tensors[3]!!
        c3w = tensors[4]!!
        c3b = tensors[5]!!
        f1w = tensors[6]!!
        f1b = tensors[7]!!
        f2w = tensors[8]!!
        f2b = tensors[9]!!
        numClasses = f2b.size
    }

    private fun conv(inp: FloatArray, inC: Int, size: Int, outC: Int, w: FloatArray, b: FloatArray): FloatArray {
        val plane = size * size
        val out = FloatArray(outC * plane)
        for (o in 0 until outC) {
            val wBase = o * inC * 9
            val bv = b[o]
            val oBase = o * plane
            for (y in 0 until size) {
                for (x in 0 until size) {
                    var sum = bv
                    for (ci in 0 until inC) {
                        val iBase = ci * plane
                        val kBase = wBase + ci * 9
                        for (ky in -1..1) {
                            val yy = y + ky
                            if (yy < 0 || yy >= size) continue
                            for (kx in -1..1) {
                                val xx = x + kx
                                if (xx < 0 || xx >= size) continue
                                val v = inp[iBase + yy * size + xx]
                                if (v == 0f) continue
                                sum += v * w[kBase + (ky + 1) * 3 + (kx + 1)]
                            }
                        }
                    }
                    out[oBase + y * size + x] = if (sum > 0f) sum else 0f
                }
            }
        }
        return out
    }

    private fun pool(inp: FloatArray, c: Int, size: Int): FloatArray {
        val oh = size / 2
        val out = FloatArray(c * oh * oh)
        for (ci in 0 until c) {
            val iBase = ci * size * size
            val oBase = ci * oh * oh
            for (y in 0 until oh) {
                for (x in 0 until oh) {
                    val y0 = y * 2
                    val x0 = x * 2
                    var m = inp[iBase + y0 * size + x0]
                    val v1 = inp[iBase + y0 * size + x0 + 1]
                    if (v1 > m) m = v1
                    val v2 = inp[iBase + (y0 + 1) * size + x0]
                    if (v2 > m) m = v2
                    val v3 = inp[iBase + (y0 + 1) * size + x0 + 1]
                    if (v3 > m) m = v3
                    out[oBase + y * oh + x] = m
                }
            }
        }
        return out
    }

    /** input: 32x32 значения 0..1, построчно. Возвращает вероятности классов. */
    fun predict(input: FloatArray): FloatArray {
        var h = conv(input, 1, INPUT, 16, c1w, c1b)
        h = pool(h, 16, INPUT)
        h = conv(h, 16, 16, 32, c2w, c2b)
        h = pool(h, 32, 16)
        h = conv(h, 32, 8, 64, c3w, c3b)
        h = pool(h, 64, 8)
        val f1 = FloatArray(128)
        for (o in 0 until 128) {
            var sum = f1b[o]
            for (i in 0 until 1024) {
                val v = h[i]
                if (v == 0f) continue
                sum += v * f1w[i * 128 + o]
            }
            f1[o] = if (sum > 0f) sum else 0f
        }
        val logits = FloatArray(numClasses)
        var maxL = -1e30f
        for (o in 0 until numClasses) {
            var sum = f2b[o]
            for (i in 0 until 128) sum += f1[i] * f2w[i * numClasses + o]
            logits[o] = sum
            if (sum > maxL) maxL = sum
        }
        var sum = 0f
        for (o in 0 until numClasses) {
            logits[o] = Math.exp((logits[o] - maxL).toDouble()).toFloat()
            sum += logits[o]
        }
        if (sum > 0f) for (o in 0 until numClasses) logits[o] /= sum
        return logits
    }

    fun argmax(p: FloatArray): Int {
        var bi = 0
        var bv = p[0]
        for (i in 1 until p.size) if (p[i] > bv) { bv = p[i]; bi = i }
        return bi
    }
}
