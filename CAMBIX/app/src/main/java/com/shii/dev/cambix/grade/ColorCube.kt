package com.shii.dev.cambix.grade

import java.util.Locale

/** data holds r,g,b triples with red varying fastest, then green, then blue (the .cube order). */
class ColorCube(val size: Int, val data: FloatArray) {
    init {
        require(size in 2..MAX_SIZE) { "Ukuran LUT harus 2 sampai $MAX_SIZE" }
        require(data.size == size * size * size * 3) { "Data LUT tidak sesuai ukuran" }
    }

    fun sample(r: Float, g: Float, b: Float, out: FloatArray) {
        val n1 = size - 1
        val fr = r.coerceIn(0f, 1f) * n1
        val fg = g.coerceIn(0f, 1f) * n1
        val fb = b.coerceIn(0f, 1f) * n1
        val r0 = minOf(fr.toInt(), n1 - 1)
        val g0 = minOf(fg.toInt(), n1 - 1)
        val b0 = minOf(fb.toInt(), n1 - 1)
        val tr = fr - r0
        val tg = fg - g0
        val tb = fb - b0
        val stepG = 3 * size
        val stepB = 3 * size * size
        val base = (b0 * size * size + g0 * size + r0) * 3
        for (c in 0..2) {
            val i = base + c
            val c000 = data[i]
            val c100 = data[i + 3]
            val c010 = data[i + stepG]
            val c110 = data[i + stepG + 3]
            val c001 = data[i + stepB]
            val c101 = data[i + stepB + 3]
            val c011 = data[i + stepB + stepG]
            val c111 = data[i + stepB + stepG + 3]
            val x00 = c000 + (c100 - c000) * tr
            val x10 = c010 + (c110 - c010) * tr
            val x01 = c001 + (c101 - c001) * tr
            val x11 = c011 + (c111 - c011) * tr
            val y0 = x00 + (x10 - x00) * tg
            val y1 = x01 + (x11 - x01) * tg
            out[c] = y0 + (y1 - y0) * tb
        }
    }

    fun resampled(newSize: Int): ColorCube {
        if (newSize == size) return this
        val out = FloatArray(newSize * newSize * newSize * 3)
        val tmp = FloatArray(3)
        val d = (newSize - 1).toFloat()
        var i = 0
        for (b in 0 until newSize) for (g in 0 until newSize) for (r in 0 until newSize) {
            sample(r / d, g / d, b / d, tmp)
            out[i++] = tmp[0]
            out[i++] = tmp[1]
            out[i++] = tmp[2]
        }
        return ColorCube(newSize, out)
    }

    fun toCubeText(title: String): String {
        val sb = StringBuilder(size * size * size * 24 + 64)
        sb.append("TITLE \"").append(title.replace('"', '\'')).append("\"\n")
        sb.append("LUT_3D_SIZE ").append(size).append("\n")
        sb.append("DOMAIN_MIN 0.0 0.0 0.0\nDOMAIN_MAX 1.0 1.0 1.0\n")
        var i = 0
        while (i < data.size) {
            sb.append(String.format(Locale.US, "%.6f %.6f %.6f\n", data[i], data[i + 1], data[i + 2]))
            i += 3
        }
        return sb.toString()
    }

    companion object {
        const val MAX_SIZE = 65

        fun identity(size: Int): ColorCube = generate(size) { r, g, b, out ->
            out[0] = r
            out[1] = g
            out[2] = b
        }

        fun generate(size: Int, fn: (Float, Float, Float, FloatArray) -> Unit): ColorCube {
            val data = FloatArray(size * size * size * 3)
            val tmp = FloatArray(3)
            val d = (size - 1).toFloat()
            var i = 0
            for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
                fn(r / d, g / d, b / d, tmp)
                data[i++] = tmp[0]
                data[i++] = tmp[1]
                data[i++] = tmp[2]
            }
            return ColorCube(size, data)
        }

        /** Throws IllegalArgumentException with a message that is safe to show the user. */
        fun parseCube(text: String): ColorCube {
            var size = 0
            var data: FloatArray? = null
            var count = 0
            val domMin = floatArrayOf(0f, 0f, 0f)
            val domMax = floatArrayOf(1f, 1f, 1f)
            val splitter = Regex("\\s+")
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val parts = line.split(splitter)
                val head = parts[0].uppercase(Locale.US)
                when {
                    head == "TITLE" -> {}
                    head == "LUT_1D_SIZE" -> throw IllegalArgumentException("LUT 1D belum didukung. Pakai LUT 3D (LUT_3D_SIZE).")
                    head == "LUT_3D_SIZE" -> {
                        size = parts.getOrNull(1)?.toIntOrNull() ?: throw IllegalArgumentException("LUT_3D_SIZE tidak valid")
                        if (size !in 2..MAX_SIZE) throw IllegalArgumentException("Ukuran LUT $size tidak didukung (2 sampai $MAX_SIZE)")
                        data = FloatArray(size * size * size * 3)
                    }
                    head == "DOMAIN_MIN" -> readTriple(parts, domMin)
                    head == "DOMAIN_MAX" -> readTriple(parts, domMax)
                    head[0].isDigit() || head[0] == '-' || head[0] == '+' || head[0] == '.' -> {
                        val d = data ?: throw IllegalArgumentException("Baris data muncul sebelum LUT_3D_SIZE")
                        if (parts.size < 3) throw IllegalArgumentException("Baris data LUT rusak")
                        if (count >= size * size * size) throw IllegalArgumentException("Data LUT lebih banyak dari ukurannya")
                        for (c in 0..2) {
                            val v = parts[c].toFloatOrNull() ?: throw IllegalArgumentException("Angka LUT tidak valid: ${parts[c]}")
                            val span = (domMax[c] - domMin[c]).takeIf { it > 1e-6f } ?: 1f
                            d[count * 3 + c] = (v - domMin[c]) / span
                        }
                        count++
                    }
                }
            }
            val d = data ?: throw IllegalArgumentException("File bukan LUT .cube 3D (LUT_3D_SIZE tidak ada)")
            val expected = size * size * size
            if (count != expected) throw IllegalArgumentException("LUT tidak lengkap: $count dari $expected baris")
            return ColorCube(size, d)
        }

        private fun readTriple(parts: List<String>, out: FloatArray) {
            if (parts.size < 4) throw IllegalArgumentException("Baris DOMAIN rusak")
            for (c in 0..2) out[c] = parts[c + 1].toFloatOrNull() ?: throw IllegalArgumentException("Angka DOMAIN tidak valid")
        }
    }
}
