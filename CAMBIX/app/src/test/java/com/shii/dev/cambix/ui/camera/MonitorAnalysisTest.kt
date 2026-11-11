package com.shii.dev.cambix.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorAnalysisTest {
    private val w = 8
    private val h = 8

    @Test
    fun flatGreyFillsExactlyOneLumaBin() {
        val d = MonitorAnalysis.analyze(IntArray(w * h) { 0xFF808080.toInt() }, w, h)
        assertEquals(w * h, d.luma.sum())
        assertEquals(1, d.luma.count { it > 0 })
    }

    @Test
    fun whiteFrameCountsAsFullyClippedHighlights() {
        val d = MonitorAnalysis.analyze(IntArray(w * h) { 0xFFFFFFFF.toInt() }, w, h)
        assertEquals(1f, d.clippedHigh, 0.001f)
        assertEquals(0f, d.clippedLow, 0.001f)
    }

    @Test
    fun blackFrameCountsAsFullyClippedShadows() {
        val d = MonitorAnalysis.analyze(IntArray(w * h) { 0xFF000000.toInt() }, w, h)
        assertEquals(1f, d.clippedLow, 0.001f)
    }

    @Test
    fun neutralGreyHasNoChroma() {
        val d = MonitorAnalysis.analyze(IntArray(w * h) { 0xFF909090.toInt() }, w, h)
        assertTrue(d.chroma.all { Math.abs(it) < 0.001f })
    }

    @Test
    fun saturatedRedLandsOnThePositiveCrAxis() {
        val d = MonitorAnalysis.analyze(IntArray(w * h) { 0xFFFF0000.toInt() }, w, h)
        assertTrue(d.chroma[1] > 0.1f)
    }

    @Test
    fun waveformHasOneColumnPerPixelColumn() {
        val d = MonitorAnalysis.analyze(IntArray(w * h) { 0xFF404040.toInt() }, w, h)
        assertEquals(w, d.columns)
        assertEquals(w * HIST_BINS, d.waveform.size)
    }
}
