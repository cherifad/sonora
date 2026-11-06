// Sonora (https://github.com/cherifad/sonora), a fork of Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
package com.adlen.sonora.app.data.offline

import org.junit.Assert.assertEquals
import org.junit.Test

/** The on-disk format of downloaded lists: a release must not silently drop them. */
class OfflineCollectionsFormatTest {

    @Test
    fun `a v1 file keeps its lists, tracks, quality and singles`() {
        val text = """
            {"version":1,"lists":[
              {"uri":"spotify:playlist:p1","type":"playlist","name":"Mix","synced":123,
               "tracks":["a","b"],"quality":"CD"}
            ],"singles":["c"]}
        """.trimIndent()

        val (lists, singles) = readCollections(text)
        val mix = lists.getValue("spotify:playlist:p1")

        assertEquals("playlist", mix.type)
        assertEquals("Mix", mix.name)
        assertEquals(setOf("a", "b"), mix.trackIds)
        assertEquals(123L, mix.syncedAt)
        assertEquals("CD", mix.quality)
        assertEquals(setOf("c"), singles)
    }

    @Test
    fun `a file without lists or singles is empty`() {
        val (lists, singles) = readCollections("""{"version":1}""")

        assertEquals(0, lists.size)
        assertEquals(0, singles.size)
    }
}
