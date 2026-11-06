// Sonora (https://github.com/cherifad/sonora), a fork of Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
package com.adlen.sonora.app.data.offline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The download budget: the user's limit and the phone's reserve. */
class DownloadBudgetTest {

    @Test
    fun `under the limit and above the reserve`() {
        assertTrue(OfflineDownloader.withinDownloadBudget(usedBytes = 1_000L, freeBytes = 10_000L, maxBytes = 8_000L, keepFreeBytes = 500L))
    }

    @Test
    fun `at the limit is refused`() {
        assertFalse(OfflineDownloader.withinDownloadBudget(usedBytes = 8_000L, freeBytes = 10_000L, maxBytes = 8_000L, keepFreeBytes = 500L))
    }

    @Test
    fun `the free-space reserve is kept`() {
        assertFalse(OfflineDownloader.withinDownloadBudget(usedBytes = 1_000L, freeBytes = 400L, maxBytes = 8_000L, keepFreeBytes = 500L))
    }

    @Test
    fun `zero means no bound`() {
        assertTrue(OfflineDownloader.withinDownloadBudget(usedBytes = 9_000L, freeBytes = 10_000L, maxBytes = 0L, keepFreeBytes = 0L))
    }
}
