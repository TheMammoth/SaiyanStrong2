package com.saiyanstrong.presentation.screens.barpath

import com.saiyanstrong.domain.model.BarPathSample
import org.junit.Assert.assertEquals
import org.junit.Test

class BarPathTrackPlaybackContentTest {

    private fun samples(vararg ts: Long): List<BarPathSample> =
        ts.map { BarPathSample(timestampMs = it, xPx = 0.0, yPx = 0.0) }

    @Test
    fun `empty samples returns -1`() {
        assertEquals(-1, currentSampleIndex(emptyList(), 100L))
    }

    @Test
    fun `before the first sample clamps to 0`() {
        val s = samples(100L, 200L, 300L)
        assertEquals(0, currentSampleIndex(s, 0L))
        assertEquals(0, currentSampleIndex(s, 50L))
    }

    @Test
    fun `picks the latest sample at or before the playback time`() {
        val s = samples(0L, 100L, 200L, 300L)
        assertEquals(0, currentSampleIndex(s, 0L))
        assertEquals(1, currentSampleIndex(s, 150L))
        assertEquals(2, currentSampleIndex(s, 200L)) // exact boundary lands on that sample
        assertEquals(2, currentSampleIndex(s, 299L))
    }

    @Test
    fun `after the last sample returns the last index`() {
        val s = samples(0L, 100L, 200L)
        assertEquals(2, currentSampleIndex(s, 999L))
    }
}
