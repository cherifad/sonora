// Subsonic/OpenSubsonic backend for Sonora. GPL-3.0.
package com.adlen.sonora.subsonic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When pausing a track is worth a server bookmark. */
class BookmarkTest {

    private val tenMinutes = 10 * 60_000L

    @Test
    fun `a short song is not bookmarked`() {
        assertFalse(shouldBookmark(4 * 60_000L, 2 * 60_000L))
    }

    @Test
    fun `a long track away from its edges is bookmarked`() {
        assertTrue(shouldBookmark(tenMinutes, 5 * 60_000L))
    }

    @Test
    fun `the first and last thirty seconds are not bookmarked`() {
        assertFalse(shouldBookmark(tenMinutes, 10_000L))
        assertFalse(shouldBookmark(tenMinutes, tenMinutes - 10_000L))
    }
}
