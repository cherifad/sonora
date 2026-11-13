package com.adlen.sonora.subsonic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrecacheTest {
    @Test fun warmsOnlyTheNextSongOfAPlayingUnmeteredQueue() {
        assertTrue(Precache.wanted(unmetered = true, hasNext = true, cached = false, playing = true, loading = false))
    }

    @Test fun neverOnAMeteredNetwork() {
        assertFalse(Precache.wanted(unmetered = false, hasNext = true, cached = false, playing = true, loading = false))
    }

    @Test fun nothingToWarmWithoutANextSongOrWithItAlreadyCached() {
        assertFalse(Precache.wanted(unmetered = true, hasNext = false, cached = false, playing = true, loading = false))
        assertFalse(Precache.wanted(unmetered = true, hasNext = true, cached = true, playing = true, loading = false))
    }

    @Test fun notWhilePausedOrWhileTheCurrentSongStillLoads() {
        assertFalse(Precache.wanted(unmetered = true, hasNext = true, cached = false, playing = false, loading = false))
        assertFalse(Precache.wanted(unmetered = true, hasNext = true, cached = false, playing = true, loading = true))
    }
}
