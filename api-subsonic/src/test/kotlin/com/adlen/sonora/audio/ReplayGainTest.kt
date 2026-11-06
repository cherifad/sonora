// Subsonic/OpenSubsonic backend for Sonora. GPL-3.0.
package com.adlen.sonora.audio

import com.adlen.sonora.api.ReplayGain
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

/** ReplayGain: track or album gain, capped by the declared peak. */
class ReplayGainTest {

    private fun assertClose(expected: Float, actual: Float) {
        assertEquals(expected, actual, 0.001f)
        assert(abs(expected - actual) < 0.001f)
    }

    @Test
    fun `no data leaves the audio untouched`() {
        assertClose(1f, replayGainLinear(null, album = false))
        assertClose(1f, replayGainLinear(ReplayGain(), album = false))
    }

    @Test
    fun `a negative track gain turns the track down`() {
        assertClose(0.501f, replayGainLinear(ReplayGain(trackGainDb = -6f), album = false))
    }

    @Test
    fun `a boost is capped by the peak`() {
        // +6 dB is 1.995, but a 0.8 peak cannot go past 1.25 without clipping.
        assertClose(1.25f, replayGainLinear(ReplayGain(trackGainDb = 6f, trackPeak = 0.8f), album = false))
    }

    @Test
    fun `album mode reads the album fields`() {
        val rg = ReplayGain(trackGainDb = -6f, albumGainDb = -3f, trackPeak = 0.5f, albumPeak = 0.9f)
        assertClose(0.501f, replayGainLinear(rg, album = false))
        assertClose(0.708f, replayGainLinear(rg, album = true))
    }
}
