package com.adlen.sonora.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OffloadTest {
    private fun wanted(
        preference: Boolean = true,
        sdkInt: Int = 33,
        equalizerActive: Boolean = false,
        replayGain: Boolean = false,
        skipSilence: Boolean = false,
        speed: Float = 1f,
    ) = Offload.wanted(preference, sdkInt, equalizerActive, replayGain, skipSilence, speed)

    @Test fun offloadRunsWhenNothingIsProcessing() {
        assertTrue(wanted())
    }

    @Test fun everyProcessorKeepsItOff() {
        assertFalse(wanted(equalizerActive = true))
        assertFalse(wanted(replayGain = true))
        assertFalse(wanted(skipSilence = true))
        assertFalse(wanted(speed = 1.25f))
        assertFalse(wanted(speed = 0.75f))
    }

    @Test fun theUserAndThePlatformCanRefuse() {
        assertFalse(wanted(preference = false))
        assertFalse(wanted(sdkInt = 28))
    }
}
