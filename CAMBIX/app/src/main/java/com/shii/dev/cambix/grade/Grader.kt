package com.shii.dev.cambix.grade

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object Grader {
    private val BAND_CENTERS = floatArrayOf(0f, 30f, 60f, 120f, 180f, 240f, 270f, 300f)

    /** src is untouched; the graded pixels come back in a new array of the same size. */
    fun render(src: IntArray, w: Int, h: Int, look: Look, cube: ColorCube?, seed: Int = 7): IntArray {
        val out = IntArray(w * h)
        val tone = ToneParams(look)
        val tr = tone.table(tone.wbR)
        val tg = tone.table(tone.wbG)
        val tb = tone.table(tone.wbB)

        val sat = look.saturation
        val vib = look.vibrance
        val lutMix = look.lutIntensity.coerceIn(0f, 1f)
        val useLut = cube != null && lutMix > 0.001f
        val hslActive = look.hsl.any { !it.neutral }
        val hueBand = FloatArray(8) { look.hsl.getOrNull(it)?.hue ?: 0f }
        val satBand = FloatArray(8) { look.hsl.getOrNull(it)?.sat ?: 0f }
        val lumBand = FloatArray(8) { look.hsl.getOrNull(it)?.lum ?: 0f }
        val sharpen = look.sharpen
        val aberration = look.aberration
        val clarity = look.clarity
        val glow = look.glow
        val sunOn = look.sunStrength > 0.001f
        val vignette = look.vignette
        val grain = look.grain
        val low = if (clarity != 0f || glow > 0f) LowRes(src, w, h) else null

        val cx = (w - 1) / 2f
        val cy = (h - 1) / 2f
        val shift = aberration * 0.012f
        val rgb = FloatArray(3)
        val hs = FloatArray(3)
        val lowOut = FloatArray(3)
        val sunWarm = floatArrayOf(1f, 0.72f, 0.35f)
        val sunColor = FloatArray(3) { 1f + (sunWarm[it] - 1f) * look.sunWarmth }

        for (y in 0 until h) {
            val row = y * w
            val v = y / h.toFloat()
            for (x in 0 until w) {
                var r: Int
                var g: Int
                var b: Int
                if (aberration > 0f) {
                    r = channel(src, w, h, x + ((x - cx) * shift).toInt(), y + ((y - cy) * shift).toInt(), 16)
                    g = (src[row + x] shr 8) and 255
                    b = channel(src, w, h, x - ((x - cx) * shift).toInt(), y - ((y - cy) * shift).toInt(), 0)
                } else {
                    val p = src[row + x]
                    r = (p shr 16) and 255
                    g = (p shr 8) and 255
                    b = p and 255
                }
                if (sharpen > 0f) {
                    val lc = luma8(src[row + x])
                    val ln = luma8(src[max(y - 1, 0) * w + x])
                    val ls = luma8(src[min(y + 1, h - 1) * w + x])
                    val lw = luma8(src[row + max(x - 1, 0)])
                    val le = luma8(src[row + min(x + 1, w - 1)])
                    val d = ((4 * lc - ln - ls - lw - le) * sharpen * 0.5f).toInt()
                    r = (r + d).coerceIn(0, 255)
                    g = (g + d).coerceIn(0, 255)
                    b = (b + d).coerceIn(0, 255)
                }

                var rf = tr[r]
                var gf = tg[g]
                var bf = tb[b]
                val luma = 0.2126f * rf + 0.7152f * gf + 0.0722f * bf

                if (sat != 0f || vib != 0f) {
                    val mx = max(rf, max(gf, bf))
                    val mn = min(rf, min(gf, bf))
                    val sl = (mx - mn) / (mx + 0.0001f)
                    val vibAmt = if (vib >= 0f) vib * (1f - sl) else vib
                    val f = (1f + sat) * (1f + vibAmt)
                    rf = (luma + (rf - luma) * f).coerceIn(0f, 1f)
                    gf = (luma + (gf - luma) * f).coerceIn(0f, 1f)
                    bf = (luma + (bf - luma) * f).coerceIn(0f, 1f)
                }

                if (hslActive) {
                    rgbToHsl(rf, gf, bf, hs)
                    var hueShift = 0f
                    var satMul = 0f
                    var lumShift = 0f
                    for (i in 0 until 8) {
                        var dist = abs(hs[0] - BAND_CENTERS[i])
                        if (dist > 180f) dist = 360f - dist
                        val wgt = 1f - dist / 40f
                        if (wgt > 0f) {
                            hueShift += wgt * hueBand[i]
                            satMul += wgt * satBand[i]
                            lumShift += wgt * lumBand[i]
                        }
                    }
                    val chroma = min(1f, hs[1] * 3f)
                    val hue = (hs[0] + hueShift * 30f * chroma) % 360f
                    hs[0] = if (hue < 0f) hue + 360f else hue
                    hs[1] = (hs[1] * (1f + satMul * chroma)).coerceIn(0f, 1f)
                    hs[2] = (hs[2] + lumShift * 0.25f * chroma).coerceIn(0f, 1f)
                    hslToRgb(hs[0], hs[1], hs[2], rgb)
                    rf = rgb[0]
                    gf = rgb[1]
                    bf = rgb[2]
                }

                if (useLut) {
                    cube!!.sample(rf, gf, bf, rgb)
                    rf += (rgb[0].coerceIn(0f, 1f) - rf) * lutMix
                    gf += (rgb[1].coerceIn(0f, 1f) - gf) * lutMix
                    bf += (rgb[2].coerceIn(0f, 1f) - bf) * lutMix
                }

                if (low != null) {
                    low.sample(x, y, lowOut)
                    if (clarity != 0f) {
                        val lowLuma = 0.2126f * lowOut[0] + 0.7152f * lowOut[1] + 0.0722f * lowOut[2]
                        val cur = 0.2126f * rf + 0.7152f * gf + 0.0722f * bf
                        val d = (cur - lowLuma) * clarity * 1.2f
                        rf += d
                        gf += d
                        bf += d
                    }
                    if (glow > 0f) {
                        val lowLuma = 0.2126f * lowOut[0] + 0.7152f * lowOut[1] + 0.0722f * lowOut[2]
                        val bright = max(0f, lowLuma - 0.55f) * 2.2f * glow * 0.8f
                        rf += lowOut[0] * bright
                        gf += lowOut[1] * bright
                        bf += lowOut[2] * bright
                    }
                }

                val u = x / w.toFloat()
                if (sunOn) {
                    val dx = (u - look.sunX) * (w / h.toFloat())
                    val dy = v - look.sunY
                    val dist = sqrt(dx * dx + dy * dy) / look.sunRadius.coerceAtLeast(0.05f)
                    var a = max(0f, 1f - dist)
                    a *= a
                    val rays = 0.8f + 0.2f * sin(atan2(dy, dx) * 14f)
                    a *= look.sunStrength * rays
                    rf = 1f - (1f - rf.coerceIn(0f, 1f)) * (1f - a * sunColor[0])
                    gf = 1f - (1f - gf.coerceIn(0f, 1f)) * (1f - a * sunColor[1])
                    bf = 1f - (1f - bf.coerceIn(0f, 1f)) * (1f - a * sunColor[2])
                }

                if (vignette != 0f) {
                    val qx = (u - 0.5f) * 2f
                    val qy = (v - 0.5f) * 2f
                    val d = sqrt(qx * qx + qy * qy) / 1.4142f
                    val t = ((d - 0.35f) / 0.65f).coerceIn(0f, 1f)
                    val vv = t * t * (3f - 2f * t)
                    val k = if (vignette >= 0f) 1f - vignette * vv * 0.8f else 1f + -vignette * vv * 0.8f
                    rf *= k
                    gf *= k
                    bf *= k
                }

                if (grain > 0f) {
                    val n = noise(x, y, seed) - 0.5f
                    val lm = 0.2126f * rf + 0.7152f * gf + 0.0722f * bf
                    val amt = n * grain * 0.12f * (1f - abs(2f * lm - 1f) * 0.6f)
                    rf += amt
                    gf += amt
                    bf += amt
                }

                val ri = (rf.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                val gi = (gf.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                val bi = (bf.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                out[row + x] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
            }
        }
        return out
    }

    private fun channel(src: IntArray, w: Int, h: Int, x: Int, y: Int, shiftBits: Int): Int =
        (src[y.coerceIn(0, h - 1) * w + x.coerceIn(0, w - 1)] shr shiftBits) and 255

    private fun luma8(p: Int): Int = (((p shr 16) and 255) * 54 + ((p shr 8) and 255) * 183 + (p and 255) * 19) shr 8

    /** Integer hash so grain differs per pixel but is repeatable for a given seed. */
    private fun noise(x: Int, y: Int, seed: Int): Float {
        var n = x * 374761393 + y * 668265263 + seed * 362437
        n = (n xor (n ushr 13)) * 1274126177
        n = n xor (n ushr 16)
        return (n and 0xFFFF) / 65535f
    }

    private fun rgbToHsl(r: Float, g: Float, b: Float, out: FloatArray) {
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val l = (mx + mn) / 2f
        var h = 0f
        var s = 0f
        val d = mx - mn
        if (d > 1e-5f) {
            s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
            h = when (mx) {
                r -> (g - b) / d + (if (g < b) 6f else 0f)
                g -> (b - r) / d + 2f
                else -> (r - g) / d + 4f
            } * 60f
        }
        out[0] = h
        out[1] = s
        out[2] = l
    }

    private fun hueToRgb(p: Float, q: Float, t0: Float): Float {
        var t = t0
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 0.5f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }

    private fun hslToRgb(h: Float, s: Float, l: Float, out: FloatArray) {
        if (s <= 1e-5f) {
            out[0] = l
            out[1] = l
            out[2] = l
            return
        }
        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q
        val hh = h / 360f
        out[0] = hueToRgb(p, q, hh + 1f / 3f)
        out[1] = hueToRgb(p, q, hh)
        out[2] = hueToRgb(p, q, hh - 1f / 3f)
    }

    /** A blurred, much smaller copy that clarity and glow read from with bilinear sampling. */
    private class LowRes(src: IntArray, val w: Int, val h: Int) {
        private val f = max(1, max(w, h) / 160)
        private val lw = (w + f - 1) / f
        private val lh = (h + f - 1) / f
        private val r = FloatArray(lw * lh)
        private val g = FloatArray(lw * lh)
        private val b = FloatArray(lw * lh)

        init {
            val count = IntArray(lw * lh)
            for (y in 0 until h) {
                val ly = (y / f) * lw
                for (x in 0 until w) {
                    val p = src[y * w + x]
                    val i = ly + x / f
                    r[i] += ((p shr 16) and 255).toFloat()
                    g[i] += ((p shr 8) and 255).toFloat()
                    b[i] += (p and 255).toFloat()
                    count[i]++
                }
            }
            for (i in r.indices) {
                val c = max(1, count[i]) * 255f
                r[i] /= c
                g[i] /= c
                b[i] /= c
            }
            blur(r)
            blur(g)
            blur(b)
        }

        private fun blur(a: FloatArray) {
            val tmp = FloatArray(a.size)
            repeat(2) {
                for (y in 0 until lh) for (x in 0 until lw) {
                    var s = 0f
                    for (k in -2..2) s += a[y * lw + (x + k).coerceIn(0, lw - 1)]
                    tmp[y * lw + x] = s / 5f
                }
                for (y in 0 until lh) for (x in 0 until lw) {
                    var s = 0f
                    for (k in -2..2) s += tmp[(y + k).coerceIn(0, lh - 1) * lw + x]
                    a[y * lw + x] = s / 5f
                }
            }
        }

        fun sample(x: Int, y: Int, out: FloatArray) {
            val fx = ((x + 0.5f) / f - 0.5f).coerceIn(0f, (lw - 1).toFloat())
            val fy = ((y + 0.5f) / f - 0.5f).coerceIn(0f, (lh - 1).toFloat())
            val x0 = fx.toInt()
            val y0 = fy.toInt()
            val x1 = min(x0 + 1, lw - 1)
            val y1 = min(y0 + 1, lh - 1)
            val tx = fx - x0
            val ty = fy - y0
            out[0] = mix2(r, x0, y0, x1, y1, tx, ty)
            out[1] = mix2(g, x0, y0, x1, y1, tx, ty)
            out[2] = mix2(b, x0, y0, x1, y1, tx, ty)
        }

        private fun mix2(a: FloatArray, x0: Int, y0: Int, x1: Int, y1: Int, tx: Float, ty: Float): Float {
            val top = a[y0 * lw + x0] + (a[y0 * lw + x1] - a[y0 * lw + x0]) * tx
            val bottom = a[y1 * lw + x0] + (a[y1 * lw + x1] - a[y1 * lw + x0]) * tx
            return top + (bottom - top) * ty
        }
    }
}
