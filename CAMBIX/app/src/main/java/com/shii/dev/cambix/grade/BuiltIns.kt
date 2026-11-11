package com.shii.dev.cambix.grade

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

enum class LookCategory(val label: String) {
    CINEMATIC("Sinematik"),
    FILM("Film"),
    NATURAL("Natural"),
    AESTHETIC("Estetik"),
    MONO("Hitam Putih"),
    IMPORTED("LUT saya"),
    PRESET("Preset saya"),
}

/**
 * A look described by a handful of grading moves instead of a copied film profile. Every built-in LUT
 * is generated from one of these, so the catalogue is original work and needs no licensed data.
 */
class LutRecipe(
    val gain: Float = 1f,
    val lift: Float = 0f,
    val gamma: Float = 1f,
    val contrast: Float = 0f,
    val saturation: Float = 1f,
    val shadowTint: FloatArray = floatArrayOf(0f, 0f, 0f),
    val highlightTint: FloatArray = floatArrayOf(0f, 0f, 0f),
    val mono: Boolean = false,
    val monoTint: FloatArray = floatArrayOf(1f, 1f, 1f),
) {
    fun apply(r: Float, g: Float, b: Float, out: FloatArray) {
        var c0 = r * gain
        var c1 = g * gain
        var c2 = b * gain
        c0 = lift + c0 * (1f - lift)
        c1 = lift + c1 * (1f - lift)
        c2 = lift + c2 * (1f - lift)
        val inv = 1f / gamma
        c0 = c0.coerceIn(0f, 1f).pow(inv)
        c1 = c1.coerceIn(0f, 1f).pow(inv)
        c2 = c2.coerceIn(0f, 1f).pow(inv)
        c0 = curve(c0)
        c1 = curve(c1)
        c2 = curve(c2)
        val luma = 0.2126f * c0 + 0.7152f * c1 + 0.0722f * c2
        if (mono) {
            c0 = luma * monoTint[0]
            c1 = luma * monoTint[1]
            c2 = luma * monoTint[2]
        } else {
            c0 = luma + (c0 - luma) * saturation
            c1 = luma + (c1 - luma) * saturation
            c2 = luma + (c2 - luma) * saturation
        }
        val sw = (1f - luma) * (1f - luma)
        val hw = luma * luma
        out[0] = (c0 + shadowTint[0] * sw + highlightTint[0] * hw).coerceIn(0f, 1f)
        out[1] = (c1 + shadowTint[1] * sw + highlightTint[1] * hw).coerceIn(0f, 1f)
        out[2] = (c2 + shadowTint[2] * sw + highlightTint[2] * hw).coerceIn(0f, 1f)
    }

    private fun curve(v: Float): Float =
        if (contrast >= 0f) v + (v * v * (3f - 2f * v) - v) * contrast else v + (0.5f + (v - 0.5f) * 0.5f - v) * -contrast

    fun build(size: Int = 33): ColorCube = ColorCube.generate(size) { r, g, b, out -> apply(r, g, b, out) }
}

class BuiltInLook(
    val id: String,
    val name: String,
    val category: LookCategory,
    val recipe: LutRecipe,
    val tweak: Look = Look(),
) {
    /** The look the camera and Studio apply: the recipe's LUT plus the small finishing tweak. */
    val look: Look get() = tweak.copy(lutId = "bi:$id", lutIntensity = 1f)
}

object BuiltIns {
    private fun t(r: Float, g: Float, b: Float) = floatArrayOf(r, g, b)

    val all: List<BuiltInLook> = listOf(
        // Sinematik
        BuiltInLook("teal_orange", "Teal & Orange", LookCategory.CINEMATIC,
            LutRecipe(contrast = 0.25f, saturation = 1.1f, shadowTint = t(-0.04f, 0.01f, 0.06f), highlightTint = t(0.07f, 0.02f, -0.05f))),
        BuiltInLook("dark_cinema", "Sinema Gelap", LookCategory.CINEMATIC,
            LutRecipe(gamma = 0.9f, contrast = 0.3f, saturation = 0.85f, shadowTint = t(-0.02f, 0.01f, 0.04f)), Look(vignette = 0.25f)),
        BuiltInLook("moody_film", "Moody", LookCategory.CINEMATIC,
            LutRecipe(lift = 0.05f, contrast = 0.15f, saturation = 0.75f, shadowTint = t(-0.02f, 0.03f, 0.04f)), Look(grain = 0.2f, vignette = 0.15f)),
        BuiltInLook("warm_cinema", "Sinema Hangat", LookCategory.CINEMATIC,
            LutRecipe(contrast = 0.2f, saturation = 1.05f, highlightTint = t(0.06f, 0.025f, -0.04f), shadowTint = t(0.01f, 0f, -0.01f))),
        BuiltInLook("cool_cinema", "Sinema Sejuk", LookCategory.CINEMATIC,
            LutRecipe(contrast = 0.2f, saturation = 0.95f, shadowTint = t(-0.03f, 0f, 0.05f), highlightTint = t(-0.02f, 0.02f, 0.04f))),
        // Film
        BuiltInLook("film_warm", "Film Hangat", LookCategory.FILM,
            LutRecipe(lift = 0.04f, contrast = 0.12f, saturation = 1.05f, highlightTint = t(0.05f, 0.02f, -0.03f)), Look(grain = 0.3f)),
        BuiltInLook("film_cool", "Film Sejuk", LookCategory.FILM,
            LutRecipe(lift = 0.04f, contrast = 0.12f, saturation = 0.95f, shadowTint = t(-0.02f, 0.02f, 0.04f)), Look(grain = 0.3f)),
        BuiltInLook("analog_negative", "Negatif Analog", LookCategory.FILM,
            LutRecipe(lift = 0.05f, gamma = 1.05f, contrast = -0.05f, saturation = 1.1f, highlightTint = t(0.04f, 0.02f, -0.02f), shadowTint = t(0f, 0.02f, 0.01f)), Look(grain = 0.35f, fade = 0.15f)),
        BuiltInLook("vintage_fade", "Vintage Pudar", LookCategory.FILM,
            LutRecipe(lift = 0.10f, contrast = -0.1f, saturation = 0.8f, highlightTint = t(0.05f, 0.03f, -0.02f)), Look(grain = 0.25f, vignette = 0.2f)),
        BuiltInLook("cine_film", "Sinema Film", LookCategory.FILM,
            LutRecipe(contrast = 0.3f, saturation = 0.9f, shadowTint = t(-0.01f, 0.01f, 0.03f), highlightTint = t(0.03f, 0.01f, -0.02f)), Look(grain = 0.2f)),
        // Natural
        BuiltInLook("natural_skin", "Kulit Natural", LookCategory.NATURAL,
            LutRecipe(contrast = 0.08f, saturation = 1.03f, highlightTint = t(0.02f, 0.01f, -0.01f))),
        BuiltInLook("soft_portrait", "Potret Lembut", LookCategory.NATURAL,
            LutRecipe(lift = 0.03f, gamma = 1.08f, contrast = -0.05f, saturation = 0.95f, highlightTint = t(0.015f, 0.005f, 0f))),
        BuiltInLook("clean_day", "Siang Bersih", LookCategory.NATURAL,
            LutRecipe(contrast = 0.1f, saturation = 1.08f)),
        BuiltInLook("natural_green", "Hijau Natural", LookCategory.NATURAL,
            LutRecipe(contrast = 0.1f, saturation = 1.05f, shadowTint = t(-0.01f, 0.02f, 0f))),
        BuiltInLook("warm_skin", "Kulit Hangat", LookCategory.NATURAL,
            LutRecipe(contrast = 0.1f, saturation = 1.05f, highlightTint = t(0.04f, 0.015f, -0.02f), shadowTint = t(0.01f, 0.005f, 0f))),
        // Estetik
        BuiltInLook("golden_hour", "Golden Hour", LookCategory.AESTHETIC,
            LutRecipe(gamma = 1.06f, contrast = 0.12f, saturation = 1.15f, highlightTint = t(0.09f, 0.04f, -0.05f), shadowTint = t(0.02f, 0f, -0.02f))),
        BuiltInLook("dreamy", "Dreamy", LookCategory.AESTHETIC,
            LutRecipe(lift = 0.08f, gamma = 1.1f, contrast = -0.1f, saturation = 0.9f, highlightTint = t(0.03f, 0.01f, 0.03f)), Look(glow = 0.4f)),
        BuiltInLook("pastel", "Pastel", LookCategory.AESTHETIC,
            LutRecipe(gain = 1.04f, lift = 0.10f, contrast = -0.1f, saturation = 0.7f)),
        BuiltInLook("soft_vintage", "Vintage Lembut", LookCategory.AESTHETIC,
            LutRecipe(lift = 0.07f, contrast = -0.05f, saturation = 0.85f, highlightTint = t(0.04f, 0.02f, -0.02f)), Look(grain = 0.15f)),
        BuiltInLook("urban_night", "Malam Kota", LookCategory.AESTHETIC,
            LutRecipe(gamma = 0.92f, contrast = 0.3f, saturation = 1.1f, shadowTint = t(-0.03f, 0f, 0.06f), highlightTint = t(0.03f, 0f, 0.02f)), Look(vignette = 0.2f)),
        // Hitam putih
        BuiltInLook("mono_classic", "Monokrom Klasik", LookCategory.MONO,
            LutRecipe(contrast = 0.2f, mono = true)),
        BuiltInLook("mono_high", "Kontras Tinggi", LookCategory.MONO,
            LutRecipe(contrast = 0.6f, mono = true), Look(grain = 0.2f)),
        BuiltInLook("mono_soft", "Monokrom Lembut", LookCategory.MONO,
            LutRecipe(lift = 0.04f, contrast = -0.1f, mono = true)),
        BuiltInLook("film_noir", "Film Noir", LookCategory.MONO,
            LutRecipe(gamma = 0.85f, contrast = 0.7f, mono = true, monoTint = t(1f, 0.99f, 0.96f)), Look(grain = 0.3f, vignette = 0.35f)),
    )

    fun byId(id: String): BuiltInLook? = all.firstOrNull { it.id == id }
}

/** A small invented scene (sky, sun, hills, a skin-tone disc, a grey ramp) so LUT thumbnails mean something offline. */
object SampleScene {
    fun create(w: Int, h: Int): IntArray {
        val px = IntArray(w * h)
        val sunX = w * 0.72f
        val sunY = h * 0.30f
        val faceX = w * 0.30f
        val faceY = h * 0.52f
        val faceR = h * 0.17f
        for (y in 0 until h) for (x in 0 until w) {
            val v = y / h.toFloat()
            var r: Float
            var g: Float
            var b: Float
            if (v < 0.62f) {
                val k = v / 0.62f
                r = 0.25f + 0.65f * k
                g = 0.45f + 0.30f * k
                b = 0.85f - 0.25f * k
                val dx = x - sunX
                val dy = y - sunY
                val d = sqrt(dx * dx + dy * dy) / (h * 0.28f)
                val s = max(0f, 1f - d)
                r = min(1f, r + s * s * 0.9f)
                g = min(1f, g + s * s * 0.8f)
                b = min(1f, b + s * s * 0.5f)
            } else {
                val ridge = 0.62f + 0.05f * kotlin.math.sin(x / w.toFloat() * 9f)
                if (v > ridge) {
                    r = 0.18f
                    g = 0.42f - (v - ridge) * 0.4f
                    b = 0.16f
                } else {
                    r = 0.9f
                    g = 0.7f
                    b = 0.5f
                }
            }
            val fx = x - faceX
            val fy = y - faceY
            if (fx * fx + fy * fy < faceR * faceR) {
                r = 0.86f
                g = 0.64f
                b = 0.52f
            }
            if (v > 0.9f) {
                val gray = x / w.toFloat()
                r = gray
                g = gray
                b = gray
            }
            px[y * w + x] = (0xFF shl 24) or ((r * 255f).toInt().coerceIn(0, 255) shl 16) or
                ((g * 255f).toInt().coerceIn(0, 255) shl 8) or (b * 255f).toInt().coerceIn(0, 255)
        }
        return px
    }
}
