package com.music.spotui.data.offline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the app behaves offline: no network, no server, or the data saver on mobile data. */
class OfflineModeTest {

    @Test
    fun `online with the server answering plays from the stream`() {
        assertFalse(offlineNow(network = true, server = true, dataSaver = false))
    }

    @Test
    fun `no network is offline`() {
        assertTrue(offlineNow(network = false, server = true, dataSaver = false))
    }

    @Test
    fun `a server out of reach is offline, even with mobile data on`() {
        assertTrue(offlineNow(network = true, server = false, dataSaver = false))
    }

    @Test
    fun `the data saver on mobile data is offline`() {
        assertTrue(offlineNow(network = true, server = true, dataSaver = true))
    }

    @Test
    fun `one answer is enough to come back online`() {
        assertFalse(offlineNow(network = true, server = true, dataSaver = false))
        assertTrue(offlineNow(network = true, server = false, dataSaver = false))
        assertFalse(offlineNow(network = true, server = true, dataSaver = false))
    }
}
