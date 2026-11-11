package com.shii.dev.cambix.camera

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionPhotoTest {
    private fun bytes(vararg v: Int): List<Byte> = v.map { it.toByte() }

    private fun fakeJpeg(withExif: Boolean): ByteArray {
        val body = ArrayList<Byte>()
        body.addAll(bytes(0xFF, 0xD8))
        if (withExif) {
            val payload = "Exif\u0000\u0000II".toByteArray(Charsets.ISO_8859_1)
            val len = payload.size + 2
            body.addAll(bytes(0xFF, 0xE1, len ushr 8, len and 0xFF))
            body.addAll(payload.toList())
        }
        body.addAll(bytes(0xFF, 0xDB, 0x00, 0x04, 0x01, 0x02))
        body.addAll(bytes(0xFF, 0xD9))
        return body.toByteArray()
    }

    private fun fakeMp4(size: Int): ByteArray {
        val b = ByteArray(size)
        val head = bytes(0, 0, 0, 0x18, 'f'.code, 't'.code, 'y'.code, 'p'.code).toByteArray()
        head.copyInto(b)
        for (i in head.size until size) b[i] = (i % 251).toByte()
        return b
    }

    @Test
    fun roundTripReturnsTheSameVideo() {
        val mp4 = fakeMp4(5000)
        val combined = MotionPhoto.build(fakeJpeg(true), mp4, 1_500_000L)
        assertArrayEquals(mp4, MotionPhoto.extractMp4(combined))
    }

    @Test
    fun xmpDeclaresTheExactVideoLengthAndTimestamp() {
        val xmp = MotionPhoto.buildXmp(5000, 1_500_000L)
        assertTrue(xmp.contains("Item:Length=\"5000\""))
        assertTrue(xmp.contains("MotionPhotoPresentationTimestampUs=\"1500000\""))
        assertTrue(xmp.contains("GCamera:MotionPhoto=\"1\""))
    }

    @Test
    fun outputStillStartsWithJpegMarkerAndKeepsExifFirst() {
        val combined = MotionPhoto.build(fakeJpeg(true), fakeMp4(100), 0L)
        assertEquals(0xFF, combined[0].toInt() and 0xFF)
        assertEquals(0xD8, combined[1].toInt() and 0xFF)
        assertEquals(0xE1, combined[3].toInt() and 0xFF)
        assertEquals('E'.code, combined[6].toInt())
    }

    @Test
    fun worksWhenTheJpegHasNoExif() {
        val mp4 = fakeMp4(300)
        val combined = MotionPhoto.build(fakeJpeg(false), mp4, 0L)
        assertArrayEquals(mp4, MotionPhoto.extractMp4(combined))
    }

    @Test
    fun plainJpegHasNoVideo() {
        assertNull(MotionPhoto.extractMp4(fakeJpeg(true)))
    }

    @Test
    fun rejectsNonJpegInput() {
        var failed = false
        try {
            MotionPhoto.build(byteArrayOf(1, 2, 3, 4, 5, 6), fakeMp4(100), 0L)
        } catch (e: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
        assertNotNull(MotionPhoto.buildXmp(1, 0L))
    }
}
