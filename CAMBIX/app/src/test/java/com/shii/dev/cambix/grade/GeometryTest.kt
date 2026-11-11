package com.shii.dev.cambix.grade

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class GeometryTest {
    private val frame = Frame(intArrayOf(1, 2, 3, 4, 5, 6), 3, 2)

    @Test
    fun fourQuarterTurnsReturnTheOriginal() {
        val f = Geometry.transform(frame, 4, false, Aspect.ORIGINAL)
        assertArrayEquals(frame.px, f.px)
    }

    @Test
    fun oneQuarterTurnSwapsSizeAndRotatesClockwise() {
        val f = Geometry.transform(frame, 1, false, Aspect.ORIGINAL)
        assertEquals(2, f.w)
        assertEquals(3, f.h)
        assertArrayEquals(intArrayOf(4, 1, 5, 2, 6, 3), f.px)
    }

    @Test
    fun mirrorFlipsEachRow() {
        val f = Geometry.transform(frame, 0, true, Aspect.ORIGINAL)
        assertArrayEquals(intArrayOf(3, 2, 1, 6, 5, 4), f.px)
    }

    @Test
    fun squareCropIsCentredAndSized() {
        val wide = Frame(IntArray(8 * 4) { it }, 8, 4)
        val f = Geometry.transform(wide, 0, false, Aspect.SQUARE)
        assertEquals(4, f.w)
        assertEquals(4, f.h)
        assertEquals(2, f.px[0])
    }
}
