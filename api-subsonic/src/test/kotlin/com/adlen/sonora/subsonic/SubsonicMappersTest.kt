// Subsonic/OpenSubsonic backend for Sonora. GPL-3.0.
package com.adlen.sonora.subsonic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

/** The Subsonic JSON → Sonora models mapping the whole UI reads. */
class SubsonicMappersTest {

    private val client = testClient { okEnvelope() }

    @Test
    fun `an album keeps its uri, artist, year, count and cover`() {
        val album = client.albumOf(
            JSONObject(
                """{"id":"17","name":"Golden Hour","artist":"Mira Sol","artistId":"ar1",
                   "coverArt":"al-17","year":2025,"songCount":6}""",
            ),
        )

        assertEquals("spotify:album:17", album.uri)
        assertEquals("Golden Hour", album.name)
        assertEquals("spotify:artist:ar1", album.artists.single().uri)
        assertEquals("Mira Sol", album.artists.single().name)
        assertEquals("2025-01-01", album.releaseDate)
        assertEquals(6, album.trackCount)
        val cover = album.images.single().url
        assertTrue(cover, cover.contains("id=al-17"))
        assertTrue(cover, cover.contains("size=300"))
    }

    @Test
    fun `an album without an artist or cover still maps`() {
        val album = client.albumOf(JSONObject("""{"id":"9","name":"Unknown Album"}"""))

        assertEquals("Unknown artist", album.artists.single().name)
        assertEquals("spotify:artist:Unknown artist", album.artists.single().uri)
        assertTrue(album.images.isEmpty())
        assertNull(album.releaseDate)
        assertEquals(0, album.trackCount)
    }

    @Test
    fun `a track turns seconds into milliseconds and keeps its album`() {
        val track = client.trackOf(
            JSONObject(
                """{"id":"s1","title":"Anywhere","artist":"Mira Sol","artistId":"ar1",
                   "album":"Golden Hour","albumId":"17","duration":22,"coverArt":"al-17"}""",
            ),
        )

        assertEquals("spotify:track:s1", track.uri)
        assertEquals("Anywhere", track.name)
        assertEquals(22_000L, track.durationMs)
        assertEquals("spotify:artist:ar1", track.artists.single().uri)
        assertEquals("spotify:album:17", track.album?.uri)
        assertEquals("Golden Hour", track.album?.name)
        assertTrue(track.playable)
        assertEquals("s1", track.uid)
    }

    @Test
    fun `the album a list belongs to overrides the track's own`() {
        val track = client.trackOf(
            JSONObject("""{"id":"s1","title":"Anywhere","duration":22}"""),
            albumId = "17",
            albumName = "Golden Hour",
        )

        assertEquals("Unknown artist", track.artists.single().name)
        assertEquals("spotify:album:17", track.album?.uri)
        assertEquals("Golden Hour", track.album?.name)
    }

    @Test
    fun `a track with no album has none`() {
        val track = client.trackOf(JSONObject("""{"id":"s1","title":"Single","duration":1}"""))

        assertNull(track.album)
    }

    @Test
    fun `a playlist maps its comment, owner and count`() {
        val playlist = client.playlistOf(
            JSONObject("""{"id":"p1","name":"Night Drive","comment":"late","owner":"admin","songCount":12}"""),
        )

        assertEquals("spotify:playlist:p1", playlist.uri)
        assertEquals("Night Drive", playlist.name)
        assertEquals("late", playlist.description)
        assertEquals("admin", playlist.owner)
        assertEquals(12, playlist.trackCount)
        assertTrue(playlist.editable)
    }
}
