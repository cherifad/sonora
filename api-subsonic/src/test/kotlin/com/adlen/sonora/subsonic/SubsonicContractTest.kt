// Subsonic/OpenSubsonic backend for Sonora. GPL-3.0.
package com.adlen.sonora.subsonic

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The endpoint and parameter shapes the app relies on, beyond the read paths. */
class SubsonicContractTest {

    @Test
    fun `star uses the id field that matches the item kind`() = runBlocking {
        val urls = mutableListOf<HttpUrl>()
        val client = testClient { url ->
            urls += url
            okEnvelope("\"starred2\":{\"song\":[]}")
        }

        SubsonicLibrary(client).add(
            listOf("spotify:track:t1", "spotify:album:a1", "spotify:artist:ar1"),
        )

        assertEquals("/rest/star.view", urls[0].encodedPath)
        assertEquals("t1", urls[0].queryParameter("id"))
        assertEquals("a1", urls[1].queryParameter("albumId"))
        assertEquals("ar1", urls[2].queryParameter("artistId"))
    }

    @Test
    fun `playlist mutations use create, update and delete`() = runBlocking {
        val paths = mutableListOf<String>()
        val client = testClient { url ->
            paths += url.encodedPath
            if (url.encodedPath.endsWith("/getPlaylists.view")) {
                okEnvelope("\"playlists\":{\"playlist\":[{\"id\":\"p1\",\"name\":\"Mix\"}]}")
            } else {
                okEnvelope()
            }
        }
        val playlists = SubsonicPlaylists(client)

        assertEquals("spotify:playlist:p1", playlists.create("Mix", null))
        playlists.rename("spotify:playlist:p1", "New")
        playlists.setDescription("spotify:playlist:p1", "Desc")
        playlists.delete("spotify:playlist:p1")

        assertEquals(
            listOf(
                "/rest/createPlaylist.view",
                "/rest/getPlaylists.view",
                "/rest/updatePlaylist.view",
                "/rest/updatePlaylist.view",
                "/rest/deletePlaylist.view",
            ),
            paths,
        )
    }

    @Test
    fun `scrobble and the play queue use their documented parameters`() = runBlocking {
        val urls = mutableListOf<HttpUrl>()
        val client = testClient { url ->
            urls += url
            okEnvelope()
        }

        client.call("scrobble", mapOf("id" to "s1", "submission" to "true"))
        client.callPairs("savePlayQueue", listOf("id" to "s1", "current" to "s1", "position" to "1000"))
        client.call("getPlayQueue")

        assertEquals("/rest/scrobble.view", urls[0].encodedPath)
        assertEquals("s1", urls[0].queryParameter("id"))
        assertEquals("true", urls[0].queryParameter("submission"))
        assertEquals("/rest/savePlayQueue.view", urls[1].encodedPath)
        assertEquals("1000", urls[1].queryParameter("position"))
        assertEquals("/rest/getPlayQueue.view", urls[2].encodedPath)
    }

    @Test
    fun `the download and cover URLs keep their parameters`() {
        val client = testClient { okEnvelope() }

        val download = client.downloadUrl("s1", "mp3", 128)
        assertTrue(download.startsWith("http://music.example/rest/stream.view?"))
        assertTrue(download.contains("format=mp3"))
        assertTrue(download.contains("maxBitRate=128"))

        val cover = client.coverUrl("al-1", 600)
        assertTrue(cover!!.contains("/rest/getCoverArt.view?"))
        assertTrue(cover.contains("size=600"))
    }
}
