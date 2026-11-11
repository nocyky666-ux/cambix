package com.shii.dev.cambix.grade

import kotlin.math.pow
import kotlin.math.sqrt

data class HslBand(val hue: Float = 0f, val sat: Float = 0f, val lum: Float = 0f) {
    val neutral: Boolean get() = hue == 0f && sat == 0f && lum == 0f
}

/**
 * One complete edit of colour and effects. Ranges are -1..1 unless noted. The first block (through
 * [vignette] and [grain]) is also implemented in the live GPU shader, so the viewfinder matches what
 * the CPU grader writes to the file. The rest only exists in the CPU grader (Studio and save-time).
 */
data class Look(
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val saturation: Float = 0f,
    val vibrance: Float = 0f,
    val fade: Float = 0f,
    val vignette: Float = 0f,
    val grain: Float = 0f,
    val sharpen: Float = 0f,
    val clarity: Float = 0f,
    val glow: Float = 0f,
    val aberration: Float = 0f,
    val sunStrength: Float = 0f,
    val sunX: Float = 0.8f,
    val sunY: Float = 0.2f,
    val sunRadius: Float = 0.5f,
    val sunWarmth: Float = 0.7f,
    val hsl: List<HslBand> = List(8) { HslBand() },
    val lutId: String? = null,
    val lutIntensity: Float = 1f,
) {
    val isNeutral: Boolean
        get() = lutId == null && copy(lutIntensity = 1f) == NEUTRAL

    /** True when the live shader alone cannot reproduce this look. */
    val needsCpuOnly: Boolean
        get() = sharpen != 0f || clarity != 0f || glow != 0f || aberration != 0f || sunStrength != 0f || hsl.any { !it.neutral }

    companion object {
        val NEUTRAL = Look()
    }
}

/**
 * Per-channel tone curve. The GLSL in GlPipeline repeats these formulas line for line; if one changes,
 * change the other.
 */
class ToneParams(look: Look) {
    private val expGain = 2f.pow(look.exposure)
    val wbR = (1f + 0.30f * look.temperature + 0.05f * look.tint).coerceAtLeast(0.2f)
    val wbG = (1f - 0.20f * look.tint).coerceAtLeast(0.2f)
    val wbB = (1f - 0.30f * look.temperature + 0.05f * look.tint).coerceAtLeast(0.2f)
    val black = -look.blacks * 0.10f
    val white = 1f - look.whites * 0.10f
    val shadows = look.shadows
    val highlights = look.highlights
    val contrast = look.contrast
    val fade = look.fade

    val exposureGain: Float get() = expGain

    fun channel(x: Float, wb: Float): Float {
        val lin = x.coerceAtLeast(0f).pow(2.2f) * expGain * wb
        var v = lin.coerceAtLeast(0f).pow(1f / 2.2f).coerceIn(0f, 1f)
        v = ((v - black) / (white - black)).coerceIn(0f, 1f)
        v += shadows * 0.6f * (1f - v) * (1f - v) * sqrt(v)
        v += highlights * 0.6f * v * v * sqrt(1f - v)
        v = v.coerceIn(0f, 1f)
        v = if (contrast >= 0f) {
            val s = v * v * (3f - 2f * v)
            v + (s - v) * contrast
        } else {
            val flat = 0.5f + (v - 0.5f) * 0.5f
            v + (flat - v) * -contrast
        }
        v = fade * 0.14f + v * (1f - fade * 0.18f)
        return v.coerceIn(0f, 1f)
    }

    fun table(wb: Float): FloatArray = FloatArray(256) { channel(it / 255f, wb) }
}
