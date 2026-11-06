// Subsonic/OpenSubsonic backend for Sonora. GPL-3.0.
package com.adlen.sonora.subsonic

import com.adlen.sonora.api.LibraryFilter
import com.adlen.sonora.api.LibrarySort
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Paging: albums and search go to the server; full-list endpoints are fetched once. */
class SubsonicPagingTest {

    private val albumCount = 250
    private val songCount = 250

    private fun albumJson(i: Int) =
        """{"id":"al-$i","name":"Album $i","artist":"A","songCount":1}"""

    private fun songJson(prefix: String, i: Int) =
        """{"id":"$prefix-$i","title":"Song $i","duration":10,"artist":"A"}"""

    @Test
    fun `an album page asks the server for its offset and size`() = runBlocking {
        var seen: HttpUrl? = null
        val client = testClient { url ->
            seen = url
            val offset = url.queryParameter("offset")!!.toInt()
            val size = url.queryParameter("size")!!.toInt()
            val albums = (offset until (offset + size).coerceAtMost(albumCount)).map(::albumJson)
            okEnvelope("\"albumList2\":{\"album\":[${albums.joinToString(",")}]}")
        }

        val page = SubsonicLibrary(client)
            .items(LibraryFilter.ALBUMS, LibrarySort.ALPHABETICAL, null, offset = 100, limit = 50)

        assertEquals("100", seen?.queryParameter("offset"))
        assertEquals("50", seen?.queryParameter("size"))
        assertEquals(50, page.items.size)
        assertEquals("spotify:album:al-100", page.items.first().uri)
        assertEquals("spotify:album:al-149", page.items.last().uri)
        assertEquals(100, page.offset)
        assertEquals(150, page.total)
        assertEquals(150, page.nextOffset)
    }

    @Test
    fun `a search page asks the server for its song offset`() = runBlocking {
        var seen: HttpUrl? = null
        val client = testClient { url ->
            seen = url
            val offset = url.queryParameter("songOffset")!!.toInt()
            val count = url.queryParameter("songCount")!!.toInt()
            val songs = (offset until (offset + count).coerceAtMost(songCount)).map { songJson("s", it) }
            okEnvelope("\"searchResult3\":{\"song\":[${songs.joinToString(",")}]}")
        }

        val page = SubsonicSearch(client).tracks("song", offset = 100, limit = 50)

        assertEquals("100", seen?.queryParameter("songOffset"))
        assertEquals("50", seen?.queryParameter("songCount"))
        assertEquals(50, page.items.size)
        assertEquals("spotify:track:s-100", page.items.first().uri)
        assertEquals(150, page.nextOffset)
    }

    @Test
    fun `a playlist over 100 songs pages locally after one fetch`() = runBlocking {
        var fetches = 0
        val entries = (0 until songCount).map { songJson("p", it) }
        val client = testClient { url ->
            if (url.encodedPath.endsWith("/getPlaylist.view")) fetches++
            okEnvelope("\"playlist\":{\"id\":\"pl1\",\"name\":\"List\",\"entry\":[${entries.joinToString(",")}]}")
        }
        val playlists = SubsonicPlaylists(client)

        val first = playlists.tracks("spotify:playlist:pl1", 0, 50)
        val second = playlists.tracks("spotify:playlist:pl1", 100, 50)

        assertEquals(1, fetches)
        assertEquals("spotify:track:p-0", first.items.first().uri)
        assertEquals("spotify:track:p-100", second.items.first().uri)
        assertEquals(250, second.total)
        assertEquals(150, second.nextOffset)
    }

    @Test
    fun `liked states and liked songs share one starred fetch`() = runBlocking {
        var fetches = 0
        val client = testClient { url ->
            if (url.encodedPath.endsWith("/getStarred2.view")) fetches++
            okEnvelope("\"starred2\":{\"song\":[${songJson("a", 0)}]}")
        }
        val library = SubsonicLibrary(client)

        assertTrue(library.contains(listOf("spotify:track:a-0")).single())
        assertEquals(1, library.likedTracks(0, 10).items.size)
        assertEquals(1, fetches)

        // A mutation drops the cache: the next read asks again.
        library.add(listOf("spotify:track:a-0"))
        library.contains(listOf("spotify:track:a-0"))
        assertEquals(2, fetches)
    }
}
