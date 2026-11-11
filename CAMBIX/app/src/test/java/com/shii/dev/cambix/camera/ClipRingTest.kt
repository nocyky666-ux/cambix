package com.shii.dev.cambix.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipRingTest {
    private val frameUs = 33_333L

    private fun feed(ring: ClipRing, from: Int, count: Int, keyEvery: Int = 30): List<RawClip> {
        val done = ArrayList<RawClip>()
        for (i in from until from + count) {
            done += ring.add(EncodedSample(ByteArray(4), i * frameUs, i % keyEvery == 0))
        }
        return done
    }

    @Test
    fun clipStartsOnAKeyframeAtOrBeforeThePreRoll() {
        val ring = ClipRing(3_500_000L)
        feed(ring, 0, 150)
        assertTrue(ring.begin(1, 1_500_000L, 1_500_000L))
        val clips = feed(ring, 150, 60)
        assertEquals(1, clips.size)
        val clip = clips[0]
        assertTrue(clip.samples.first().isKey)
        assertTrue(clip.samples.first().ptsUs <= clip.anchorUs - 1_500_000L)
        assertTrue(clip.samples.last().ptsUs >= clip.anchorUs + 1_500_000L)
    }

    @Test
    fun clipWithoutEnoughHistoryStartsAtTheEarliestKeyframe() {
        val ring = ClipRing(3_500_000L)
        feed(ring, 0, 10)
        assertTrue(ring.begin(1, 1_500_000L, 500_000L))
        val clip = feed(ring, 10, 30).single()
        assertEquals(0L, clip.samples.first().ptsUs)
    }

    @Test
    fun beginFailsUntilAKeyframeExists() {
        val ring = ClipRing(3_500_000L)
        assertFalse(ring.begin(1, 1_000_000L, 1_000_000L))
        ring.add(EncodedSample(ByteArray(4), 0L, false))
        assertFalse(ring.begin(2, 1_000_000L, 1_000_000L))
    }

    @Test
    fun ringDropsOldSamples() {
        val ring = ClipRing(1_000_000L)
        feed(ring, 0, 300)
        assertTrue(ring.begin(1, 500_000L, 100_000L))
        val clip = feed(ring, 300, 10).single()
        assertTrue(clip.samples.first().ptsUs >= 300 * frameUs - 1_000_000L - 1_000_000L)
    }

    @Test
    fun samplesStayInOrder() {
        val ring = ClipRing(3_500_000L)
        feed(ring, 0, 100)
        ring.begin(1, 1_000_000L, 1_000_000L)
        val clip = feed(ring, 100, 60).single()
        val times = clip.samples.map { it.ptsUs }
        assertEquals(times.sorted(), times)
    }
}
