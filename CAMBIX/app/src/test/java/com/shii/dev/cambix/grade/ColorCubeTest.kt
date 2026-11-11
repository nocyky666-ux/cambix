package com.shii.dev.cambix.grade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorCubeTest {
    @Test
    fun identitySamplesReturnTheirInput() {
        val c = ColorCube.identity(9)
        val out = FloatArray(3)
        c.sample(0.3f, 0.6f, 0.9f, out)
        assertEquals(0.3f, out[0], 0.01f)
        assertEquals(0.6f, out[1], 0.01f)
        assertEquals(0.9f, out[2], 0.01f)
    }

    @Test
    fun writeThenParseRoundTrips() {
        val original = ColorCube.generate(5) { r, g, b, out ->
            out[0] = g
            out[1] = b
            out[2] = r
        }
        val parsed = ColorCube.parseCube(original.toCubeText("tes"))
        assertEquals(5, parsed.size)
        val a = FloatArray(3)
        val b = FloatArray(3)
        original.sample(0.2f, 0.7f, 0.4f, a)
        parsed.sample(0.2f, 0.7f, 0.4f, b)
        for (i in 0..2) assertEquals(a[i], b[i], 0.001f)
    }

    @Test
    fun parserAcceptsCommentsAndDomain() {
        val text = "# komentar\nTITLE \"x\"\nLUT_3D_SIZE 2\nDOMAIN_MIN 0 0 0\nDOMAIN_MAX 1 1 1\n" +
            "0 0 0\n1 0 0\n0 1 0\n1 1 0\n0 0 1\n1 0 1\n0 1 1\n1 1 1\n"
        val c = ColorCube.parseCube(text)
        val out = FloatArray(3)
        c.sample(1f, 0f, 1f, out)
        assertEquals(1f, out[0], 0.001f)
        assertEquals(0f, out[1], 0.001f)
        assertEquals(1f, out[2], 0.001f)
    }

    @Test
    fun parserRejectsBrokenFiles() {
        assertFails("LUT_1D_SIZE 4\n0 0 0\n")
        assertFails("0 0 0\n")
        assertFails("LUT_3D_SIZE 2\n0 0 0\n1 1 1\n")
        assertFails("LUT_3D_SIZE 2\n" + "0 a 0\n".repeat(8))
        assertFails("LUT_3D_SIZE 1\n")
        assertFails("tidak ada apa apa")
    }

    @Test
    fun resamplingKeepsShapeOfAnIdentityCube() {
        val big = ColorCube.identity(33).resampled(17)
        assertEquals(17, big.size)
        val out = FloatArray(3)
        big.sample(0.5f, 0.25f, 0.75f, out)
        assertEquals(0.5f, out[0], 0.01f)
        assertEquals(0.25f, out[1], 0.01f)
        assertEquals(0.75f, out[2], 0.01f)
    }

    @Test
    fun everyBuiltInLookBuildsAValidCube() {
        val seen = HashSet<String>()
        for (look in BuiltIns.all) {
            assertTrue("id ganda ${look.id}", seen.add(look.id))
            val cube = look.recipe.build(9)
            for (v in cube.data) assertTrue(v in 0f..1f)
            assertTrue(look.look.lutId == "bi:${look.id}")
        }
        assertTrue(BuiltIns.all.size >= 20)
    }

    private fun assertFails(text: String) {
        var failed = false
        try {
            ColorCube.parseCube(text)
        } catch (e: IllegalArgumentException) {
            failed = true
        }
        assertTrue("seharusnya gagal: ${text.take(20)}", failed)
    }
}
