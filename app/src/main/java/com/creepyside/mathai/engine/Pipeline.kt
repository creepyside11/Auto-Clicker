package com.creepyside.mathai.engine

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/** Изображение в оттенках серого, значения 0..1 (1 = белый). */
class Gray(val w: Int, val h: Int, val data: FloatArray)

class Comp(var x0: Int, var x1: Int, var y0: Int, var y1: Int, var area: Int) {
    val w: Int get() = x1 - x0 + 1
    val h: Int get() = y1 - y0 + 1
    val cx: Float get() = (x0 + x1) * 0.5f
    val cy: Float get() = (y0 + y1) * 0.5f
}

class Glyph(val box: Comp, val cls: String, val conf: Float)

class LineResult(val text: String, val confidence: Float)

class BinResult(val mask: ByteArray, val inverted: Boolean)

object Pipeline {

    const val MAX_SIDE = 1400
    const val DOT_MIN_H = 0.075f
    const val BAR_W_RATIO = 0.72f
    const val BAR_H_RATIO = 0.30f
    const val MERGE_XGAP = 0.11f
    const val MERGE_VGAP = 0.22f
    const val MERGE_OVERLAP = 0.30f
    const val SUP_RATIO = 0.74f
    const val SUP_SHIFT = 0.10f

    // ---------- базовые операции ----------

    fun otsu(g: ByteArray): Int {
        val hist = IntArray(256)
        for (b in g) hist[b.toInt() and 255]++
        val total = g.size.toDouble()
        if (total == 0.0) return 127
        val prob = DoubleArray(256)
        for (i in 0 until 256) prob[i] = hist[i] / total
        val omega = DoubleArray(256)
        val mu = DoubleArray(256)
        var acc = 0.0
        var muAcc = 0.0
        for (i in 0 until 256) {
            acc += prob[i]
            muAcc += prob[i] * i
            omega[i] = acc
            mu[i] = muAcc
        }
        val muT = mu[255]
        var best = 0
        var bestV = -1.0
        for (t in 0 until 256) {
            val den = omega[t] * (1.0 - omega[t])
            if (den <= 1e-12) continue
            val v = (muT * omega[t] - mu[t]) * (muT * omega[t] - mu[t]) / den
            if (v > bestV) { bestV = v; best = t }
        }
        return best
    }

    fun binarize(gray: Gray): BinResult {
        val h = gray.h
        val w = gray.w
        val n = h * w
        val g = ByteArray(n)
        for (i in 0 until n) {
            var v = (gray.data[i] * 255f).toInt()
            if (v < 0) v = 0
            if (v > 255) v = 255
            g[i] = v.toByte()
        }
        val t = otsu(g)
        var dark = 0
        for (i in 0 until n) if ((g[i].toInt() and 255) <= t) dark++
        val fracDark = dark.toDouble() / n
        val ink = ByteArray(n)
        var inverted = false
        if (fracDark <= 0.5) {
            for (i in 0 until n) ink[i] = if ((g[i].toInt() and 255) <= t) 1 else 0
        } else {
            inverted = true
            for (i in 0 until n) ink[i] = if ((g[i].toInt() and 255) > t) 1 else 0
        }
        var inkCount = 0
        for (b in ink) if (b.toInt() != 0) inkCount++
        val inkFrac = inkCount.toDouble() / n
        if (inkFrac < 0.002 || inkFrac > 0.45) {
            var win = max(9, min(h, w) / 24)
            if (win % 2 == 0) win++
            val half = win / 2
            val iw = w + 1
            val ig = FloatArray((h + 1) * iw)
            val ig2 = FloatArray((h + 1) * iw)
            for (y in 1..h) {
                var rowSum = 0f
                var rowSum2 = 0f
                val base = y * iw
                val prev = (y - 1) * iw
                for (x in 1..w) {
                    val v = gray.data[(y - 1) * w + (x - 1)]
                    rowSum += v
                    rowSum2 += v * v
                    ig[base + x] = ig[prev + x] + rowSum
                    ig2[base + x] = ig2[prev + x] + rowSum2
                }
            }
            val local = ByteArray(n)
            for (y in 0 until h) {
                val ya = max(0, y - half)
                val yb = min(h - 1, y + half)
                for (x in 0 until w) {
                    val xa = max(0, x - half)
                    val xb = min(w - 1, x + half)
                    val cnt = ((yb - ya + 1) * (xb - xa + 1)).toFloat()
                    val s = ig[(yb + 1) * iw + (xb + 1)] - ig[ya * iw + (xb + 1)] -
                            ig[(yb + 1) * iw + xa] + ig[ya * iw + xa]
                    val s2 = ig2[(yb + 1) * iw + (xb + 1)] - ig2[ya * iw + (xb + 1)] -
                            ig2[(yb + 1) * iw + xa] + ig2[ya * iw + xa]
                    val mean = s / cnt
                    var varr = s2 / cnt - mean * mean
                    if (varr < 0f) varr = 0f
                    val std = sqrt(varr.toDouble()).toFloat()
                    val thr = mean * (1f + 0.22f * (std / 0.5f - 1f))
                    local[y * w + x] = if (gray.data[y * w + x] < thr) 1 else 0
                }
            }
            var lc = 0
            for (b in local) if (b.toInt() != 0) lc++
            if (lc.toDouble() / n <= 0.5) {
                for (i in 0 until n) ink[i] = local[i]
                inverted = false
            } else {
                for (i in 0 until n) ink[i] = if (local[i].toInt() != 0) 0 else 1
                inverted = true
            }
        }
        return BinResult(ink, inverted)
    }

    fun rotate(gray: Gray, angleDeg: Float): Gray {
        if (abs(angleDeg) < 1e-6f) return gray
        val h = gray.h
        val w = gray.w
        val a = Math.toRadians(angleDeg.toDouble())
        val ca = cos(a)
        val sa = sin(a)
        val cx = (w - 1) * 0.5
        val cy = (h - 1) * 0.5
        val out = FloatArray(w * h)
        for (y in 0 until h) {
            val ys = y - cy
            for (x in 0 until w) {
                val xs = x - cx
                val sx = ca * xs + sa * ys + cx
                val sy = -sa * xs + ca * ys + cy
                if (sx < 0 || sy < 0 || sx >= w - 1 || sy >= h - 1) continue
                val x0 = floor(sx).toInt()
                val y0 = floor(sy).toInt()
                val fx = (sx - x0).toFloat()
                val fy = (sy - y0).toFloat()
                val i00 = y0 * w + x0
                val v = gray.data[i00] * (1 - fx) * (1 - fy) +
                        gray.data[i00 + 1] * fx * (1 - fy) +
                        gray.data[i00 + w] * (1 - fx) * fy +
                        gray.data[i00 + w + 1] * fx * fy
                out[y * w + x] = v
            }
        }
        return Gray(w, h, out)
    }

    fun deskew(mask: ByteArray, w: Int, h: Int): Float {
        var scale = 1.0f
        var mw = w
        var mh = h
        var small = mask
        if (max(h, w) > 600) {
            scale = 600f / max(h, w)
            mw = max(1, (w * scale).toInt())
            mh = max(1, (h * scale).toInt())
            small = ByteArray(mw * mh)
            for (y in 0 until mh) {
                val sy = min(h - 1, (y / scale).toInt())
                for (x in 0 until mw) {
                    val sx = min(w - 1, (x / scale).toInt())
                    small[y * mw + x] = mask[sy * w + sx]
                }
            }
        }
        var best = 0f
        var bestScore = -1.0
        var ang = -6
        while (ang <= 6) {
            val f = FloatArray(mw * mh)
            for (i in 0 until mw * mh) f[i] = if (small[i].toInt() != 0) 1f else 0f
            val rot = rotate(Gray(mw, mh, f), ang.toFloat())
            val proj = DoubleArray(mh)
            for (y in 0 until mh) {
                var s = 0.0
                for (x in 0 until mw) s += rot.data[y * mw + x]
                proj[y] = s
            }
            var mean = 0.0
            for (v in proj) mean += v
            mean /= mh
            var vr = 0.0
            for (v in proj) vr += (v - mean) * (v - mean)
            vr /= mh
            if (vr > bestScore) { bestScore = vr; best = ang.toFloat() }
            ang++
        }
        return best
    }

    fun findLines(mask: ByteArray, w: Int, h: Int): List<IntArray> {
        val thr = max(1.0, 0.012 * w)
        val bands = ArrayList<IntArray>()
        var y = 0
        while (y < h) {
            var s = 0.0
            for (x in 0 until w) s += mask[y * w + x].toInt()
            if (!(s > thr)) { y++; continue }
            val y0 = y
            var last = y
            while (y < h) {
                var ss = 0.0
                for (x in 0 until w) ss += mask[y * w + x].toInt()
                if (ss > thr) {
                    last = y
                    y++
                } else {
                    var gap = 0
                    var yy = y
                    while (yy < h) {
                        var s2 = 0.0
                        for (x in 0 until w) s2 += mask[yy * w + x].toInt()
                        if (s2 > thr) break
                        gap++
                        yy++
                    }
                    if (gap > 2 || yy >= h) break
                    y = yy
                }
            }
            bands.add(intArrayOf(y0, last))
            y = last + 1
        }
        if (bands.isEmpty()) return emptyList()
        val heights = bands.map { it[1] - it[0] + 1 }.sorted()
        val med = heights[heights.size / 2].toFloat()
        val merged = ArrayList<IntArray>()
        for (b in bands) {
            if (merged.isNotEmpty()) {
                val prev = merged[merged.size - 1]
                val gap = b[0] - prev[1] - 1
                val combined = b[1] - prev[0] + 1
                if (gap <= 0.45f * med && combined <= 4.0f * med) {
                    merged[merged.size - 1] = intArrayOf(prev[0], b[1])
                    continue
                }
            }
            merged.add(intArrayOf(b[0], b[1]))
        }
        return merged
    }

    // ---------- компоненты связности ----------

    private fun find(parent: IntArray, a0: Int): Int {
        var a = a0
        while (parent[a] != a) {
            parent[a] = parent[parent[a]]
            a = parent[a]
        }
        return a
    }

    private fun union(parent: IntArray, a: Int, b: Int) {
        val ra = find(parent, a)
        val rb = find(parent, b)
        if (ra != rb) parent[rb] = ra
    }

    fun components(mask: ByteArray, w: Int, y0: Int, y1: Int): List<Comp> {
        val maxRuns = max(16, (y1 - y0 + 2) * (w / 2 + 2))
        val parent = IntArray(maxRuns)
        val rs = ArrayList<Int>()
        val re = ArrayList<Int>()
        val ry = ArrayList<Int>()
        var prevS = IntArray(0)
        var prevE = IntArray(0)
        var prevI = IntArray(0)
        var rid = 0
        for (y in y0..y1) {
            val cs = ArrayList<Int>()
            val ce = ArrayList<Int>()
            val ci = ArrayList<Int>()
            var x = 0
            while (x < w) {
                if (mask[y * w + x].toInt() != 0) {
                    val s = x
                    while (x < w && mask[y * w + x].toInt() != 0) x++
                    val e = x - 1
                    if (rid >= maxRuns) break
                    parent[rid] = rid
                    rs.add(s); re.add(e); ry.add(y)
                    for (k in prevS.indices) {
                        if (prevS[k] <= e + 1 && s <= prevE[k] + 1) union(parent, rid, prevI[k])
                    }
                    cs.add(s); ce.add(e); ci.add(rid)
                    rid++
                } else x++
            }
            prevS = cs.toIntArray()
            prevE = ce.toIntArray()
            prevI = ci.toIntArray()
        }
        val groups = HashMap<Int, IntArray>()
        for (k in 0 until rid) {
            val root = find(parent, k)
            val s = rs[k]
            val e = re[k]
            val y = ry[k]
            val g = groups[root]
            if (g == null) {
                groups[root] = intArrayOf(s, e, y, y, e - s + 1)
            } else {
                if (s < g[0]) g[0] = s
                if (e > g[1]) g[1] = e
                if (y > g[3]) g[3] = y
                g[4] += e - s + 1
            }
        }
        val out = ArrayList<Comp>()
        for (g in groups.values) out.add(Comp(g[0], g[1], g[2], g[3], g[4]))
        out.sortBy { it.x0 }
        return out
    }

    fun isBar(c: Comp, ref: Float): Boolean {
        if (c.w <= BAR_W_RATIO * ref || c.h > BAR_H_RATIO * ref) return false
        return c.area >= 0.7 * c.w * c.h
    }

    fun groupGlyphs(comps: List<Comp>, ref: Float): Pair<List<Comp>, List<Comp>> {
        val items = ArrayList<Comp>()
        val bars = ArrayList<Comp>()
        for (c in comps) {
            if (isBar(c, ref)) bars.add(c) else items.add(c)
        }
        items.sortBy { it.x0 }
        var changed = true
        while (changed) {
            changed = false
            var i = 0
            while (i < items.size) {
                var j = i + 1
                while (j < items.size) {
                    val a = items[i]
                    val b = items[j]
                    val xgap = max(0, max(a.x0, b.x0) - min(a.x1, b.x1))
                    val vgap = max(0, max(a.y0, b.y0) - min(a.y1, b.y1))
                    val ov = min(a.x1, b.x1) - max(a.x0, b.x0) + 1
                    val ovr = ov.toFloat() / max(1, min(a.w, b.w))
                    val combinedW = max(a.x1, b.x1) - min(a.x0, b.x0) + 1
                    val close = (ovr >= MERGE_OVERLAP && vgap <= MERGE_VGAP * ref) ||
                            (xgap <= MERGE_XGAP * ref && vgap <= MERGE_VGAP * ref * 0.8f)
                    if (close && combinedW <= 1.1f * ref) {
                        val merged = Comp(
                            min(a.x0, b.x0), max(a.x1, b.x1),
                            min(a.y0, b.y0), max(a.y1, b.y1), a.area + b.area
                        )
                        items[i] = merged
                        items.removeAt(j)
                        changed = true
                        break
                    }
                    j++
                }
                i++
            }
        }
        items.sortBy { it.x0 }
        return Pair(items, bars)
    }

    // ---------- подготовка глифа ----------

    fun resizeGray(src: FloatArray, sw: Int, sh: Int, dw: Int, dh: Int): FloatArray {
        val out = FloatArray(dw * dh)
        if (dw <= 0 || dh <= 0) return out
        if (dw >= sw && dh >= sh) {
            val kx = if (dw > 1) (sw - 1).toDouble() / (dw - 1) else 0.0
            val ky = if (dh > 1) (sh - 1).toDouble() / (dh - 1) else 0.0
            for (y in 0 until dh) {
                val sy = y * ky
                val y0 = min(sh - 1, floor(sy).toInt())
                val y1 = min(sh - 1, y0 + 1)
                val fy = (sy - y0).toFloat()
                for (x in 0 until dw) {
                    val sx = x * kx
                    val x0 = min(sw - 1, floor(sx).toInt())
                    val x1 = min(sw - 1, x0 + 1)
                    val fx = (sx - x0).toFloat()
                    out[y * dw + x] = src[y0 * sw + x0] * (1 - fx) * (1 - fy) +
                            src[y0 * sw + x1] * fx * (1 - fy) +
                            src[y1 * sw + x0] * (1 - fx) * fy +
                            src[y1 * sw + x1] * fx * fy
                }
            }
            return out
        }
        val sxr = sw.toDouble() / dw
        val syr = sh.toDouble() / dh
        for (y in 0 until dh) {
            val ya = min(sh - 1, floor(y * syr).toInt())
            val yb = min(sh, max(ya + 1, ceil((y + 1) * syr).toInt()))
            for (x in 0 until dw) {
                val xa = min(sw - 1, floor(x * sxr).toInt())
                val xb = min(sw, max(xa + 1, ceil((x + 1) * sxr).toInt()))
                var sum = 0f
                var cnt = 0
                for (yy in ya until yb) for (xx in xa until xb) { sum += src[yy * sw + xx]; cnt++ }
                out[y * dw + x] = if (cnt > 0) sum / cnt else 0f
            }
        }
        return out
    }

    fun normalizeGlyph(src: FloatArray, sw: Int, sh: Int): FloatArray {
        val size = Classifier.INPUT
        val inner = Classifier.INNER
        val out = FloatArray(size * size)
        if (sw <= 0 || sh <= 0) return out
        val scale = inner.toFloat() / max(sw, sh)
        val nw = max(1, round(sw * scale).toInt())
        val nh = max(1, round(sh * scale).toInt())
        val small = resizeGray(src, sw, sh, nw, nh)
        val y0 = (size - nh) / 2
        val x0 = (size - nw) / 2
        for (y in 0 until nh) for (x in 0 until nw) out[(y0 + y) * size + (x0 + x)] = small[y * nw + x]
        return out
    }

    fun cropNorm(mask: ByteArray, w: Int, c: Comp): FloatArray {
        val y0 = max(0, c.y0)
        val y1 = c.y1
        val x0 = max(0, c.x0)
        val x1 = c.x1
        val sw = x1 - x0 + 1
        val sh = y1 - y0 + 1
        if (sw <= 0 || sh <= 0) return FloatArray(Classifier.INPUT * Classifier.INPUT)
        val sub = FloatArray(sw * sh)
        for (y in 0 until sh) for (x in 0 until sw) sub[y * sw + x] = mask[(y0 + y) * w + x0 + x].toFloat()
        // сглаживание 3x3 гауссом — как при обучении
        val p = FloatArray((sw + 2) * (sh + 2))
        for (y in 0 until sh) for (x in 0 until sw) p[(y + 1) * (sw + 2) + (x + 1)] = sub[y * sw + x]
        val sm = FloatArray(sw * sh)
        val kw = floatArrayOf(1f, 2f, 1f, 2f, 4f, 2f, 1f, 2f, 1f)
        for (y in 0 until sh) {
            for (x in 0 until sw) {
                var acc = 0f
                for (ky in 0..2) for (kx in 0..2) {
                    acc += p[(y + ky) * (sw + 2) + (x + kx)] * kw[ky * 3 + kx]
                }
                sm[y * sw + x] = acc / 16f
            }
        }
        return normalizeGlyph(sm, sw, sh)
    }

    // ---------- сборка выражения ----------

    private fun valueEnd(s: String): Boolean {
        if (s.isEmpty()) return false
        if (s.endsWith(")")) return true
        val c = s[s.length - 1]
        return c.isDigit() || c == 'x' || c == 'y' || c == 'π'
    }

    private fun valueStart(s: String): Boolean {
        if (s.isEmpty()) return false
        if (s.startsWith("sqrt") || s.startsWith("cbrt")) return true
        val c = s[0]
        return c.isDigit() || c == '(' || c == 'x' || c == 'y' || c == 'π'
    }

    private fun isNumber(s: String): Boolean {
        if (s.isEmpty()) return false
        for (c in s) if (!c.isDigit() && c != '.') return false
        return true
    }

    fun joinTokens(tokens: List<String>): String {
        val sb = StringBuilder()
        var prev: String? = null
        for (t in tokens) {
            val p = prev
            if (p != null && valueEnd(p) && valueStart(t)) {
                if (!(isNumber(p) && isNumber(t))) sb.append('*')
            }
            sb.append(t)
            prev = t
        }
        return sb.toString()
    }

    private class PTok(val x: Int, val text: String)

    fun assemble(glyphs: List<Glyph>, bars: List<Comp>, ref: Float, lineCy: Float): String {
        if (glyphs.isEmpty() && bars.isEmpty()) return ""
        val tokens = ArrayList<PTok>()
        val usedBars = HashSet<Int>()
        val usedGlyphs = HashSet<Int>()

        // 1) корень, у которого черта слилась со знаком (широкий бокс)
        for (gi in glyphs.indices) {
            val g = glyphs[gi]
            if (g.cls != "√" || gi in usedGlyphs) continue
            if (g.box.w <= 1.15f * ref) continue
            usedGlyphs.add(gi)
            val rad = ArrayList<Glyph>()
            for (j in glyphs.indices) {
                if (j in usedGlyphs) continue
                val hbox = glyphs[j].box
                if (hbox.cx >= g.box.x0 - 0.12f * ref && hbox.cx <= g.box.x1 + 0.12f * ref &&
                    hbox.cy > g.box.y0 - 0.15f * ref
                ) {
                    rad.add(glyphs[j])
                    usedGlyphs.add(j)
                }
            }
            val inner = assemble(rad, emptyList(), ref, lineCy)
            tokens.add(PTok(g.box.x0, "sqrt(" + inner + ")"))
        }

        // 2) корень с отдельной чертой
        for (gi in glyphs.indices) {
            val g = glyphs[gi]
            if (g.cls != "√" || gi in usedGlyphs) continue
            var best = -1
            for (bi in bars.indices) {
                if (bi in usedBars) continue
                val b = bars[bi]
                if (b.x0 - g.box.x1 <= 0.40f * ref && abs(b.y0 - g.box.y0) <= 0.45f * ref) {
                    if (best < 0 || b.w > bars[best].w) best = bi
                }
            }
            if (best < 0) continue
            val bar = bars[best]
            usedBars.add(best)
            usedGlyphs.add(gi)
            val rad = ArrayList<Glyph>()
            for (j in glyphs.indices) {
                if (j in usedGlyphs) continue
                val hbox = glyphs[j].box
                if (hbox.cx >= bar.x0 - 0.12f * ref && hbox.cx <= bar.x1 + 0.12f * ref &&
                    hbox.cy > bar.y0 - 0.15f * ref
                ) {
                    rad.add(glyphs[j])
                    usedGlyphs.add(j)
                }
            }
            val inner = assemble(rad, emptyList(), ref, lineCy)
            tokens.add(PTok(g.box.x0, "sqrt(" + inner + ")"))
        }

        // 3) дроби
        for (bi in bars.indices) {
            if (bi in usedBars) continue
            val b = bars[bi]
            val num = ArrayList<Glyph>()
            val den = ArrayList<Glyph>()
            for (j in glyphs.indices) {
                if (j in usedGlyphs) continue
                val hbox = glyphs[j].box
                if (hbox.cx < b.x0 - 0.14f * ref || hbox.cx > b.x1 + 0.14f * ref) continue
                if (hbox.cy < b.y0) num.add(glyphs[j]) else if (hbox.cy > b.y1) den.add(glyphs[j])
            }
            if (num.isEmpty() || den.isEmpty()) continue
            usedBars.add(bi)
            for (g in num) usedGlyphs.add(glyphs.indexOf(g))
            for (g in den) usedGlyphs.add(glyphs.indexOf(g))
            val ns = assemble(num, emptyList(), ref, lineCy)
            val ds = assemble(den, emptyList(), ref, lineCy)
            if (ns.isNotEmpty() && ds.isNotEmpty()) tokens.add(PTok(b.x0, "(" + ns + ")/(" + ds + ")"))
        }

        // 4) остальные глифы + надстрочники (степени)
        val rest = ArrayList<Int>()
        for (j in glyphs.indices) if (j !in usedGlyphs) rest.add(j)
        rest.sortBy { glyphs[it].box.x0 }
        var base: Glyph? = null
        var i = 0
        while (i < rest.size) {
            val g = glyphs[rest[i]]
            val b = base
            if (b != null && g.box.h <= 0.95f * b.box.h && g.box.cy < b.box.cy - 0.12f * b.box.h) {
                val sup = ArrayList<Glyph>()
                while (i < rest.size) {
                    val g2 = glyphs[rest[i]]
                    if (g2.box.h <= 0.95f * b.box.h && g2.box.cy < b.box.cy - 0.12f * b.box.h) {
                        sup.add(g2)
                        i++
                    } else break
                }
                var clsList = sup.map { Classes.token(it.cls) }.toMutableList()
                if (clsList.isNotEmpty() && clsList[0] == "^") clsList = clsList.drop(1).toMutableList()
                val inner = joinTokens(clsList)
                if (inner.isNotEmpty()) tokens.add(PTok(sup[0].box.x0, "^(" + inner + ")"))
                continue
            }
            tokens.add(PTok(g.box.x0, Classes.token(g.cls)))
            base = g
            i++
        }

        // неиспользованные черты — это минусы
        for (bi in bars.indices) {
            if (bi in usedBars) continue
            tokens.add(PTok(bars[bi].x0, "-"))
        }

        tokens.sortBy { it.x }
        return joinTokens(tokens.map { it.text })
    }

    // ---------- главный вход ----------

    fun recognize(grayIn: Gray, clf: Classifier): List<LineResult> {
        var gray = grayIn
        if (max(gray.h, gray.w) > MAX_SIDE) {
            val scale = MAX_SIDE.toFloat() / max(gray.h, gray.w)
            val nw = max(1, (gray.w * scale).toInt())
            val nh = max(1, (gray.h * scale).toInt())
            gray = Gray(nw, nh, resizeGray(gray.data, gray.w, gray.h, nw, nh))
        }
        var bin = binarize(gray)
        var mask = bin.mask
        var inkCount = 0
        for (b in mask) if (b.toInt() != 0) inkCount++
        if (inkCount == 0) return emptyList()
        val ang = deskew(mask, gray.w, gray.h)
        if (abs(ang) > 0.4f) {
            gray = rotate(gray, ang)
            bin = binarize(gray)
            mask = bin.mask
        }
        val w = gray.w
        val lines = findLines(mask, w, gray.h)
        val results = ArrayList<LineResult>()
        for (line in lines) {
            val y0 = line[0]
            val y1 = line[1]
            val lineH = y1 - y0 + 1
            if (lineH < 8) continue
            var comps = components(mask, w, y0, y1)
            if (comps.isEmpty()) continue
            val tall = comps.filter { it.h >= 0.18f * lineH }.map { it.h }.sorted()
            var ref = if (tall.isNotEmpty()) tall[tall.size / 2].toFloat() else lineH.toFloat()
            if (ref < 8f) ref = 8f
            comps = comps.filter { it.h >= max(2f, DOT_MIN_H * ref) && it.area >= 3 }
            if (comps.isEmpty()) continue
            val grouped = groupGlyphs(comps, ref)
            val boxes = grouped.first
            val bars = grouped.second
            if (boxes.isEmpty() && bars.isEmpty()) continue
            val glyphs = ArrayList<Glyph>()
            var confSum = 0f
            for (c in boxes) {
                val crop = cropNorm(mask, w, c)
                val probs = clf.predict(crop)
                val idx = clf.argmax(probs)
                val cls = if (idx < Classes.LIST.size) Classes.LIST[idx] else "?"
                glyphs.add(Glyph(c, cls, probs[idx]))
                confSum += probs[idx]
            }
            var cy = (y0 + y1) * 0.5f
            if (boxes.isNotEmpty()) {
                var s = 0f
                for (c in boxes) s += c.cy
                cy = s / boxes.size
            }
            val expr = assemble(glyphs, bars, ref, cy)
            val conf = if (boxes.isEmpty()) 0f else confSum / boxes.size
            results.add(LineResult(expr, conf))
        }
        return results
    }
}
