// Subsonic/OpenSubsonic backend for Sonora. GPL-3.0.
package com.adlen.sonora.subsonic

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bookmarks, shares, scan status and the server's now-playing list. */
class SubsonicServerExtrasTest {

    private fun song(id: String) =
        """{"id":"$id","title":"Song $id","duration":10,"artist":"A"}"""

    @Test
    fun `bookmarks keep their track and position`() = runBlocking {
        val client = testClient { url ->
            if (url.encodedPath.endsWith("/getBookmarks.view")) {
                okEnvelope(
                    "\"bookmarks\":{\"bookmark\":[{\"position\":42000,\"changed\":\"2026-01-01T00:00:00Z\"," +
                        "\"entry\":${song("s1")}}]}",
                )
            } else {
                okEnvelope()
            }
        }

        val bookmarks = SubsonicServerExtras(client).bookmarks()

        assertEquals(1, bookmarks.size)
        assertEquals("spotify:track:s1", bookmarks.single().track.uri)
        assertEquals(42_000L, bookmarks.single().positionMs)
        assertNotNull(bookmarks.single().changedAt)
    }

    @Test
    fun `a bookmark is written with its id and position`() = runBlocking {
        var url: HttpUrl? = null
        val client = testClient { u ->
            url = u
            okEnvelope()
        }

        SubsonicServerExtras(client).createBookmark("spotify:track:s1", 1_234)

        assertEquals("/rest/createBookmark.view", url?.encodedPath)
        assertEquals("s1", url?.queryParameter("id"))
        assertEquals("1234", url?.queryParameter("position"))
    }

    @Test
    fun `shares map their url, expiry and tracks`() = runBlocking {
        val client = testClient { url ->
            if (url.encodedPath.endsWith("/getShares.view")) {
                okEnvelope(
                    "\"shares\":{\"share\":[{\"id\":\"sh1\",\"url\":\"https://music.example/s/sh1\"," +
                        "\"description\":\"Listen\",\"expires\":\"2026-01-01T00:00:00Z\"," +
                        "\"entry\":[${song("s1")}]}]}",
                )
            } else {
                okEnvelope()
            }
        }

        val share = SubsonicServerExtras(client).shares().single()

        assertEquals("sh1", share.id)
        assertEquals("https://music.example/s/sh1", share.url)
        assertEquals("Listen", share.description)
        assertNotNull(share.expiresAt)
        assertEquals("spotify:track:s1", share.tracks.single().uri)
    }

    @Test
    fun `scan status is read and a scan can be started`() = runBlocking {
        var started = false
        val client = testClient { url ->
            when {
                url.encodedPath.endsWith("/getScanStatus.view") ->
                    okEnvelope("\"scanStatus\":{\"scanning\":true,\"count\":42,\"lastScan\":1700000000000}")
                url.encodedPath.endsWith("/startScan.view") -> {
                    started = true
                    okEnvelope()
                }
                else -> okEnvelope()
            }
        }
        val extras = SubsonicServerExtras(client)

        val status = extras.scanStatus()
        extras.startScan()

        assertTrue(status?.scanning == true)
        assertEquals(42, status?.count ?: -1)
        assertEquals(1_700_000_000_000L, status?.lastScan ?: 0L)
        assertTrue(started)
    }

    @Test
    fun `now playing lists the other clients' tracks`() = runBlocking {
        val client = testClient { url ->
            if (url.encodedPath.endsWith("/getNowPlaying.view")) {
                okEnvelope(
                    "\"nowPlaying\":{\"entry\":[{\"username\":\"me\",\"playerId\":\"p1\",\"minutesAgo\":2," +
                        "\"id\":\"s1\",\"title\":\"Song s1\",\"duration\":10}]}",
                )
            } else {
                okEnvelope()
            }
        }

        val playing = SubsonicServerExtras(client).nowPlaying().single()

        assertEquals("me", playing.username)
        assertEquals("p1", playing.playerId)
        assertEquals(2, playing.minutesAgo)
        assertEquals("spotify:track:s1", playing.track.uri)
    }

    @Test
    fun `a Jellyfin bridge is not asked for bookmarks`() = runBlocking {
        var calls = 0
        val client = testClient { url ->
            calls++
            if (url.encodedPath.endsWith("/ping.view")) {
                """{"subsonic-response":{"status":"ok","version":"1.16.1","type":"jellyfin"}}"""
            } else {
                okEnvelope()
            }
        }
        client.probeCapabilities()

        assertTrue(SubsonicServerExtras(client).bookmarks().isEmpty())
        assertEquals(1, calls)
    }
}
