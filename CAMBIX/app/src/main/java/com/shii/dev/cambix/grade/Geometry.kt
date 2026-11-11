package com.shii.dev.cambix.grade

enum class Aspect(val label: String, val ratio: Float?) {
    ORIGINAL("Asli", null),
    SQUARE("1:1", 1f),
    R4_5("4:5", 4f / 5f),
    R3_4("3:4", 3f / 4f),
    R9_16("9:16", 9f / 16f),
    R16_9("16:9", 16f / 9f),
}

class Frame(val px: IntArray, val w: Int, val h: Int)

object Geometry {
    /** Order: rotate (clockwise quarter turns), mirror, then centre-crop to the aspect. */
    fun transform(src: Frame, quarterTurns: Int, flipH: Boolean, aspect: Aspect): Frame {
        var f = src
        val turns = ((quarterTurns % 4) + 4) % 4
        repeat(turns) { f = rotate90(f) }
        if (flipH) f = mirror(f)
        val ratio = aspect.ratio
        if (ratio != null) f = cropToRatio(f, ratio)
        return f
    }

    private fun rotate90(s: Frame): Frame {
        val out = IntArray(s.px.size)
        val nw = s.h
        val nh = s.w
        for (y in 0 until s.h) for (x in 0 until s.w) {
            out[x * nw + (s.h - 1 - y)] = s.px[y * s.w + x]
        }
        return Frame(out, nw, nh)
    }

    private fun mirror(s: Frame): Frame {
        val out = IntArray(s.px.size)
        for (y in 0 until s.h) for (x in 0 until s.w) {
            out[y * s.w + (s.w - 1 - x)] = s.px[y * s.w + x]
        }
        return Frame(out, s.w, s.h)
    }

    fun cropToRatio(s: Frame, ratio: Float): Frame {
        val current = s.w.toFloat() / s.h
        var cw = s.w
        var ch = s.h
        if (current > ratio) cw = (s.h * ratio).toInt().coerceIn(1, s.w) else ch = (s.w / ratio).toInt().coerceIn(1, s.h)
        if (cw == s.w && ch == s.h) return s
        val x0 = (s.w - cw) / 2
        val y0 = (s.h - ch) / 2
        val out = IntArray(cw * ch)
        for (y in 0 until ch) System.arraycopy(s.px, (y + y0) * s.w + x0, out, y * cw, cw)
        return Frame(out, cw, ch)
    }
}
