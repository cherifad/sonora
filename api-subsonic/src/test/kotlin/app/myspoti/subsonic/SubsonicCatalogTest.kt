// Subsonic/OpenSubsonic backend for Myspoti. GPL-3.0.
package app.myspoti.subsonic

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** The catalog endpoints on a canned server answer: album, its tracks and their pages. */
class SubsonicCatalogTest {

    private val albumJson = """{"id":"17","name":"Golden Hour","artist":"Mira Sol","artistId":"ar1",
        "coverArt":"al-17","songCount":3,"song":[
          {"id":"a","title":"Anywhere","duration":22,"track":1},
          {"id":"b","title":"Daydream","duration":23,"track":2},
          {"id":"c","title":"Golden Hour","duration":24,"track":3}]}"""

    private val client = testClient { okEnvelope(""""album":$albumJson""") }
    private val catalog = SubsonicCatalog(client)

    @Test
    fun `an album page maps the album and all its songs`() = runBlocking {
        val album = catalog.album("spotify:album:17")
        assertEquals("spotify:album:17", album.uri)
        assertEquals("Golden Hour", album.name)
        assertEquals("Mira Sol", album.artists.single().name)

        val page = catalog.albumTracks("spotify:album:17", 0, 50)
        assertEquals(3, page.total)
        assertNull(page.nextOffset)
        assertEquals(listOf("spotify:track:a", "spotify:track:b", "spotify:track:c"), page.items.map { it.uri })
        // Each song inherits the album it was listed in (its name, id and cover).
        assertEquals("spotify:album:17", page.items.first().album?.uri)
        assertEquals("Golden Hour", page.items.first().album?.name)
        assertEquals(22_000L, page.items.first().durationMs)
    }

    @Test
    fun `album tracks page by offset`() = runBlocking {
        val page = catalog.albumTracks("spotify:album:17", 1, 1)

        assertEquals(1, page.items.size)
        assertEquals("spotify:track:b", page.items.single().uri)
        assertEquals(1, page.offset)
        assertEquals(3, page.total)
        assertEquals(2, page.nextOffset)
    }

    @Test
    fun `a missing album is an error`() {
        val empty = SubsonicCatalog(testClient { okEnvelope() })

        assertThrows(IllegalStateException::class.java) {
            runBlocking { empty.album("spotify:album:none") }
        }
    }

    @Test
    fun `an empty library is an empty page, not an error`() = runBlocking {
        val empty = SubsonicCatalog(testClient { okEnvelope(""""album":{"id":"9","name":"Empty","songCount":0}""") })

        val page = empty.albumTracks("spotify:album:9", 0, 50)

        assertEquals(0, page.items.size)
        assertEquals(0, page.total)
        assertNull(page.nextOffset)
    }
}
