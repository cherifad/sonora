package com.adlen.jellyfinaudio.data.subsonic

import com.adlen.jellyfinaudio.data.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsonicUrlsTest {

    private val config = ServerConfig(
        baseUrl = "http://192.168.1.10:8096/subfin",
        username = "listener@example.com",
        password = "secret",
    )

    @Test
    fun streamUrlUsesNormalizedBasePathAndAuthToken() {
        val url = SubsonicUrls.stream(config, "song id/1")

        assertTrue(
            url.startsWith(
                "http://192.168.1.10:8096/subfin/rest/stream.view?id=song+id%2F1",
            ),
        )
        assertTrue(url.contains("&u=listener%40example.com"))
        assertTrue(url.contains("&v=1.16.1"))
        assertTrue(url.contains("&c=JellyfinAudio"))

        val salt = queryValue(url, "s")
        val token = queryValue(url, "t")
        assertEquals(SubsonicAuthInterceptor.md5("secret$salt"), token)
    }

    @Test
    fun streamUrlSupportsFormatFallback() {
        val url = SubsonicUrls.stream(config, "42", format = "mp3")
        assertTrue(url.contains("&format=mp3"))
    }

    @Test
    fun coverArtUrlIncludesSize() {
        val url = SubsonicUrls.coverArt(config, "cover/1", size = 512)
        assertTrue(
            url!!.startsWith(
                "http://192.168.1.10:8096/subfin/rest/getCoverArt.view?id=cover%2F1&size=512",
            ),
        )
    }

    @Test
    fun coverArtUrlIsStableSoCoilCanCacheIt() {
        val first = SubsonicUrls.coverArt(config, "cover/1", size = 300)!!
        val second = SubsonicUrls.coverArt(config, "cover/1", size = 300)!!

        assertEquals(first, second)
        val salt = queryValue(first, "s")
        assertEquals(SubsonicAuthInterceptor.md5("secret$salt"), queryValue(first, "t"))
    }

    private fun queryValue(url: String, key: String): String =
        Regex("[?&]$key=([^&]+)").find(url)?.groupValues?.get(1)
            ?: error("Missing $key in $url")
}
