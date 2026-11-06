// Subsonic/OpenSubsonic backend for Sonora. GPL-3.0.
package com.adlen.sonora.subsonic

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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

        val normalized = client.trackOf(
            JSONObject(
                """{"id":"s1","title":"A","duration":10,"replayGain":{"trackGain":-6.0,"albumGain":-7.0,"trackPeak":0.9,"albumPeak":0.95}}""",
            ),
        )
        assertEquals(-6f, normalized.replayGain?.trackGainDb ?: 0f, 0.001f)
        assertEquals(-7f, normalized.replayGain?.albumGainDb ?: 0f, 0.001f)
        assertEquals(0.9f, normalized.replayGain?.trackPeak ?: 0f, 0.001f)
    }

    @Test
    fun `a Jellyfin bridge is not asked to rate`() = runBlocking {
        var calls = 0
        val client = testClient { calls++; okEnvelope() }

        SubsonicExtras(client) { ServerCapabilities(type = "jellyfin") }
            .setRating("spotify:track:s1", 5)

        assertEquals(0, calls)
    }

    @Test
    fun `similar tracks come from getSimilarSongs2`() = runBlocking {
        var url: HttpUrl? = null
        val client = testClient { u ->
            url = u
            okEnvelope("\"similarSongs2\":{\"song\":[{\"id\":\"s2\",\"title\":\"Like it\",\"duration\":10,\"artist\":\"A\"}]}")
        }

        val tracks = SubsonicExtras(client).similarTracks("spotify:track:s1", 20)

        assertEquals("s1", url?.queryParameter("id"))
        assertEquals("20", url?.queryParameter("count"))
        assertEquals("spotify:track:s2", tracks.single().uri)
    }

    @Test
    fun `a Jellyfin bridge is not asked for similar songs`() = runBlocking {
        var calls = 0
        val client = testClient { calls++; okEnvelope() }

        val tracks = SubsonicExtras(client) { ServerCapabilities(type = "jellyfin") }
            .similarTracks("spotify:track:s1", 20)

        assertTrue(tracks.isEmpty())
        assertEquals(0, calls)
    }

    @Test
    fun `an album seed mixes from its first song`() = runBlocking {
        var similarId: String? = null
        val client = testClient { url ->
            when {
                url.encodedPath.endsWith("/getAlbum.view") ->
                    okEnvelope("\"album\":{\"id\":\"al1\",\"name\":\"A\",\"song\":[{\"id\":\"a1\",\"title\":\"First\",\"duration\":10}]}")
                url.encodedPath.endsWith("/getSimilarSongs2.view") -> {
                    similarId = url.queryParameter("id")
                    okEnvelope("\"similarSongs2\":{\"song\":[{\"id\":\"s2\",\"title\":\"Like\",\"duration\":10,\"artist\":\"A\"}]}")
                }
                else -> okEnvelope()
            }
        }

        val tracks = SubsonicExtras(client).similarTracks("spotify:album:al1", 10)

        assertEquals("a1", similarId)
        assertEquals("spotify:track:s2", tracks.single().uri)
    }

    @Test
    fun `an artist seed mixes from a top song`() = runBlocking {
        var similarId: String? = null
        val client = testClient { url ->
            when {
                url.encodedPath.endsWith("/getArtist.view") ->
                    okEnvelope("\"artist\":{\"id\":\"ar1\",\"name\":\"Mira\",\"album\":[]}")
                url.encodedPath.endsWith("/getTopSongs.view") ->
                    okEnvelope("\"topSongs\":{\"song\":[{\"id\":\"t1\",\"title\":\"Top\",\"duration\":10}]}")
                url.encodedPath.endsWith("/getSimilarSongs2.view") -> {
                    similarId = url.queryParameter("id")
                    okEnvelope("\"similarSongs2\":{\"song\":[{\"id\":\"s2\",\"title\":\"Like\",\"duration\":10}]}")
                }
                else -> okEnvelope()
            }
        }

        val tracks = SubsonicExtras(client).similarTracks("spotify:artist:ar1", 10)

        assertEquals("t1", similarId)
        assertEquals("spotify:track:s2", tracks.single().uri)
    }

    @Test
    fun `a track radio drops the seed itself`() = runBlocking {
        val client = testClient { url ->
            if (url.encodedPath.endsWith("/getSimilarSongs2.view")) {
                okEnvelope(
                    "\"similarSongs2\":{\"song\":[" +
                        "{\"id\":\"s1\",\"title\":\"Seed\",\"duration\":10}," +
                        "{\"id\":\"s2\",\"title\":\"Like\",\"duration\":10}]}",
                )
            } else {
                okEnvelope()
            }
        }

        val tracks = SubsonicExtras(client).similarTracks("spotify:track:s1", 10)

        assertEquals(listOf("spotify:track:s2"), tracks.map { it.uri })
    }

    @Test
    fun `similar albums are distinct and keep the row's album`() = runBlocking {
        val client = testClient { url ->
            if (url.encodedPath.endsWith("/getSimilarSongs2.view")) {
                okEnvelope(
                    "\"similarSongs2\":{\"song\":[" +
                        "{\"id\":\"s2\",\"title\":\"A\",\"duration\":10,\"albumId\":\"al1\",\"album\":\"First\",\"artist\":\"Mira\",\"coverArt\":\"c1\"}," +
                        "{\"id\":\"s3\",\"title\":\"B\",\"duration\":10,\"albumId\":\"al1\",\"album\":\"First\",\"artist\":\"Mira\",\"coverArt\":\"c1\"}," +
                        "{\"id\":\"s4\",\"title\":\"C\",\"duration\":10,\"albumId\":\"al2\",\"album\":\"Second\",\"artist\":\"Mira\",\"coverArt\":\"c2\"}]}",
                )
            } else {
                okEnvelope()
            }
        }

        val albums = SubsonicCatalog(client).similarAlbums("spotify:track:s1", 12)

        assertEquals(listOf("spotify:album:al1", "spotify:album:al2"), albums.map { it.uri })
    }

    @Test
    fun `an empty similar-songs answer is an empty mix`() = runBlocking {
        val client = testClient { url ->
            if (url.encodedPath.endsWith("/getSimilarSongs2.view")) {
                okEnvelope("\"similarSongs2\":{\"song\":[]}")
            } else {
                okEnvelope()
            }
        }

        assertTrue(SubsonicExtras(client).similarTracks("spotify:track:s1", 10).isEmpty())
    }

    @Test
    fun `random songs are read from getRandomSongs`() = runBlocking {
        val client = testClient { url ->
            if (url.encodedPath.endsWith("/getRandomSongs.view")) {
                okEnvelope("\"randomSongs\":{\"song\":[{\"id\":\"r1\",\"title\":\"Random\",\"duration\":10,\"artist\":\"A\"}]}")
            } else {
                okEnvelope()
            }
        }

        assertEquals("spotify:track:r1", client.randomSongs(20).single().uri)
    }

    @Test
    fun `a podcast stub is a typed unsupported failure`() {
        val error = assertThrows(SubsonicException.Unsupported::class.java) {
            runBlocking { SubsonicPodcasts().show("spotify:show:x") }
        }
        assertEquals("podcasts", error.feature)
    }
}
