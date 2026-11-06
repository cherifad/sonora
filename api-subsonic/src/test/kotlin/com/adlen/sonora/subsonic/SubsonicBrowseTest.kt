// Subsonic/OpenSubsonic backend for Sonora. GPL-3.0.
package com.adlen.sonora.subsonic

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Browse: a genre page and the playlists tab of search. */
class SubsonicBrowseTest {

    @Test
    fun `a genre page asks for its albums and songs`() = runBlocking {
        var albumUrl: HttpUrl? = null
        var songUrl: HttpUrl? = null
        val client = testClient { url ->
            when {
                url.encodedPath.endsWith("/getAlbumList2.view") -> {
                    albumUrl = url
                    okEnvelope("\"albumList2\":{\"album\":[{\"id\":\"al1\",\"name\":\"Rock 1\",\"artist\":\"A\",\"songCount\":1}]}")
                }
                url.encodedPath.endsWith("/getSongsByGenre.view") -> {
                    songUrl = url
                    okEnvelope("\"songsByGenre\":{\"song\":[{\"id\":\"s1\",\"title\":\"Rock Song\",\"duration\":10,\"artist\":\"A\"}]}")
                }
                else -> okEnvelope()
            }
        }

        val albums = client.genreAlbums("Rock", 50)
        val songs = client.genreSongs("Rock", 50)

        assertEquals("byGenre", albumUrl?.queryParameter("type"))
        assertEquals("Rock", albumUrl?.queryParameter("genre"))
        assertEquals("Rock", songUrl?.queryParameter("genre"))
        assertEquals("spotify:album:al1", albums.single().uri)
        assertEquals("spotify:track:s1", songs.single().uri)
    }

    @Test
    fun `playlist search filters the server's list by name`() = runBlocking {
        val client = testClient { url ->
            if (url.encodedPath.endsWith("/getPlaylists.view")) {
                okEnvelope("\"playlists\":{\"playlist\":[{\"id\":\"p1\",\"name\":\"Road Trip\"},{\"id\":\"p2\",\"name\":\"Focus\"}]}")
            } else {
                okEnvelope()
            }
        }

        val page = SubsonicSearch(client).playlists("road", 0, 10)

        assertEquals(listOf("spotify:playlist:p1"), page.items.map { it.uri })
    }

    @Test
    fun `a search result includes matching playlists`() = runBlocking {
        val client = testClient { url ->
            when {
                url.encodedPath.endsWith("/search3.view") ->
                    okEnvelope("\"searchResult3\":{\"song\":[],\"album\":[],\"artist\":[]}")
                url.encodedPath.endsWith("/getPlaylists.view") ->
                    okEnvelope("\"playlists\":{\"playlist\":[{\"id\":\"p1\",\"name\":\"Road Trip\"}]}")
                else -> okEnvelope()
            }
        }

        val results = SubsonicSearch(client).all("road", 10)

        assertEquals(1, results.playlists.items.size)
    }

    @Test
    fun `a rating is sent to setRating and read back from userRating`() = runBlocking {
        var url: HttpUrl? = null
        val client = testClient { u ->
            url = u
            okEnvelope()
        }

        SubsonicExtras(client).setRating("spotify:track:s1", 4)

        assertEquals("/rest/setRating.view", url?.encodedPath)
        assertEquals("s1", url?.queryParameter("id"))
        assertEquals("4", url?.queryParameter("rating"))

        val track = client.trackOf(
            JSONObject("""{"id":"s1","title":"A","duration":10,"userRating":4}"""),
        )
        assertEquals(4, track.rating ?: 0)
    }

    @Test
    fun `a Jellyfin bridge is not asked to rate`() = runBlocking {
        var calls = 0
        val client = testClient { calls++; okEnvelope() }

        SubsonicExtras(client) { ServerCapabilities(type = "jellyfin") }
            .setRating("spotify:track:s1", 5)

        assertEquals(0, calls)
    }
}
