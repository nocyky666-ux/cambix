package com.shii.dev.cambix.grade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraderTest {
    private fun px(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun r(p: Int) = (p shr 16) and 255
    private fun g(p: Int) = (p shr 8) and 255
    private fun b(p: Int) = p and 255

    private val ramp = IntArray(16) { px(it * 17, it * 17, it * 17) }

    @Test
    fun neutralLookKeepsPixelsWithinOneStep() {
        val src = intArrayOf(px(10, 200, 90), px(255, 0, 128), px(0, 0, 0), px(255, 255, 255))
        val out = Grader.render(src, 4, 1, Look(), null)
        for (i in src.indices) {
            assertTrue(Math.abs(r(src[i]) - r(out[i])) <= 1)
            assertTrue(Math.abs(g(src[i]) - g(out[i])) <= 1)
            assertTrue(Math.abs(b(src[i]) - b(out[i])) <= 1)
        }
    }

    @Test
    fun positiveExposureBrightensAndNegativeDarkens() {
        val src = intArrayOf(px(100, 100, 100))
        assertTrue(r(Grader.render(src, 1, 1, Look(exposure = 1f), null)[0]) > 100)
        assertTrue(r(Grader.render(src, 1, 1, Look(exposure = -1f), null)[0]) < 100)
    }

    @Test
    fun contrastSpreadsTonesAroundTheMiddle() {
        val src = intArrayOf(px(64, 64, 64), px(192, 192, 192))
        val out = Grader.render(src, 2, 1, Look(contrast = 1f), null)
        assertTrue(r(out[0]) < 64)
        assertTrue(r(out[1]) > 192)
    }

    @Test
    fun saturationMinusOneProducesGrey() {
        val out = Grader.render(intArrayOf(px(200, 40, 90)), 1, 1, Look(saturation = -1f), null)[0]
        assertTrue(Math.abs(r(out) - g(out)) <= 1 && Math.abs(g(out) - b(out)) <= 1)
    }

    @Test
    fun warmTemperatureRaisesRedOverBlue() {
        val out = Grader.render(intArrayOf(px(128, 128, 128)), 1, 1, Look(temperature = 1f), null)[0]
        assertTrue(r(out) > b(out))
    }

    @Test
    fun identityLutChangesNothingAndCustomLutIsApplied() {
        val src = intArrayOf(px(30, 120, 220))
        val same = Grader.render(src, 1, 1, Look(lutId = "x"), ColorCube.identity(17))[0]
        assertTrue(Math.abs(r(same) - 30) <= 1 && Math.abs(b(same) - 220) <= 1)
        val invert = ColorCube.generate(17) { rr, gg, bb, out ->
            out[0] = 1f - rr
            out[1] = 1f - gg
            out[2] = 1f - bb
        }
        val inv = Grader.render(src, 1, 1, Look(lutId = "x"), invert)[0]
        assertTrue(Math.abs(r(inv) - 225) <= 2)
        val half = Grader.render(src, 1, 1, Look(lutId = "x", lutIntensity = 0.5f), invert)[0]
        assertTrue(Math.abs(r(half) - 128) <= 3)
    }

    @Test
    fun vignetteDarkensCornersMoreThanCentre() {
        val w = 9
        val src = IntArray(w * w) { px(180, 180, 180) }
        val out = Grader.render(src, w, w, Look(vignette = 1f), null)
        assertTrue(r(out[0]) < r(out[(w / 2) * w + w / 2]))
    }

    @Test
    fun outputSizeMatchesAndSourceIsUntouched() {
        val src = IntArray(6) { px(it * 20, 10, 10) }
        val copy = src.copyOf()
        val out = Grader.render(src, 3, 2, Look(exposure = 0.5f, sharpen = 0.5f, clarity = 0.3f, glow = 0.3f), null)
        assertEquals(6, out.size)
        assertTrue(src.contentEquals(copy))
    }

    @Test
    fun hslSaturationOnReddestBandChangesRedsOnly() {
        val bands = List(8) { if (it == 0) HslBand(sat = -1f) else HslBand() }
        val src = intArrayOf(px(220, 30, 30), px(30, 30, 220))
        val out = Grader.render(src, 2, 1, Look(hsl = bands), null)
        assertTrue(Math.abs(r(out[0]) - g(out[0])) < Math.abs(220 - 30))
        assertTrue(b(out[1]) > 200)
    }

    @Test
    fun rampStaysMonotonicUnderStrongLook() {
        val out = Grader.render(ramp, 16, 1, Look(contrast = 0.6f, shadows = 0.4f, highlights = -0.4f, fade = 0.2f), null)
        for (i in 1 until out.size) assertTrue(r(out[i]) >= r(out[i - 1]))
    }
}
