// Subsonic/OpenSubsonic backend for Myspoti. GPL-3.0.
package app.myspoti.subsonic

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.security.MessageDigest

/** The client's contract with the server: URL shape, token auth, parsing and errors. */
class SubsonicClientTest {

    private fun md5(value: String): String =
        MessageDigest.getInstance("MD5").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    @Test
    fun `a call carries a salted token, never the password`() = runBlocking {
        var url: HttpUrl? = null
        val client = testClient { u -> url = u; okEnvelope() }

        client.call("ping")

        val seen = requireNotNull(url)
        assertEquals("/rest/ping.view", seen.encodedPath)
        assertEquals("me", seen.queryParameter("u"))
        assertNull("the password must never travel", seen.queryParameter("p"))
        assertEquals("1.16.1", seen.queryParameter("v"))
        assertEquals("JellyfinAudio", seen.queryParameter("c"))
        assertEquals("json", seen.queryParameter("f"))
        val salt = requireNotNull(seen.queryParameter("s"))
        assertEquals(md5(TEST_PASSWORD + salt), seen.queryParameter("t"))
    }

    @Test
    fun `a server without a scheme gets http and its path keeps the prefix`() = runBlocking {
        var url: HttpUrl? = null
        val client = testClient(
            SubsonicConfig("music.example/subfin/", "me", TEST_PASSWORD),
        ) { u -> url = u; okEnvelope() }

        client.call("ping")

        val seen = requireNotNull(url)
        assertEquals("http", seen.scheme)
        assertEquals("music.example", seen.host)
        assertEquals("/subfin/rest/ping.view", seen.encodedPath)
    }

    @Test
    fun `parameters and repeated keys reach the server`() = runBlocking {
        var url: HttpUrl? = null
        val client = testClient { u -> url = u; okEnvelope() }

        client.callPairs("search3", listOf("query" to "mira sol", "songCount" to "2"))

        val seen = requireNotNull(url)
        assertEquals("/rest/search3.view", seen.encodedPath)
        assertEquals("mira sol", seen.queryParameter("query"))
        assertEquals("2", seen.queryParameter("songCount"))
    }

    @Test
    fun `the envelope is unwrapped and a failed status is an error with its message`() {
        val ok = runBlocking { testClient { okEnvelope("\"openSubsonic\":true") }.call("ping") }
        assertEquals("ok", ok.optString("status"))
        assertTrue(ok.optBoolean("openSubsonic"))

        val failed = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                testClient {
                    """{"subsonic-response":{"status":"failed","error":{"code":40,"message":"Wrong username or password"}}}"""
                }.call("ping")
            }
        }
        assertEquals("Wrong username or password", failed.message)
    }

    @Test
    fun `an HTTP error is an error`() {
        val client = SubsonicClient(
            SubsonicConfig("music.example", "me", TEST_PASSWORD),
            fakeHttpStatus { 500 to "" },
        )
        val error = assertThrows(IllegalStateException::class.java) { runBlocking { client.call("ping") } }
        assertTrue(error.message.orEmpty().contains("500"))
    }

    @Test
    fun `the client reports whether the server answered`() {
        val answers = mutableListOf<Boolean>()
        val previous = SubsonicClient.Reach.onReachable
        SubsonicClient.Reach.onReachable = { answers += it }
        try {
            runBlocking { testClient { okEnvelope() }.call("ping") }
            val refused = SubsonicClient(
                SubsonicConfig("music.example", "me", TEST_PASSWORD),
                offlineHttp(),
            )
            assertThrows(IOException::class.java) { runBlocking { refused.call("ping") } }
        } finally {
            SubsonicClient.Reach.onReachable = previous
        }
        assertEquals(listOf(true, false), answers)
    }

    @Test
    fun `a download URL asks for the quality, or the original file`() {
        val client = testClient { okEnvelope() }

        val original = client.downloadUrl("42").toHttpUrl()
        assertEquals("/rest/stream.view", original.encodedPath)
        assertEquals("42", original.queryParameter("id"))
        assertNull(original.queryParameter("format"))
        assertNull(original.queryParameter("maxBitRate"))

        val cd = client.downloadUrl("42", maxBitRateKbps = 320).toHttpUrl()
        assertEquals("320", cd.queryParameter("maxBitRate"))
        assertNull(cd.queryParameter("format"))

        val small = client.downloadUrl("42", format = "mp3", maxBitRateKbps = 128).toHttpUrl()
        assertEquals("mp3", small.queryParameter("format"))
        assertEquals("128", small.queryParameter("maxBitRate"))
    }

    @Test
    fun `cover art URLs are built for a real id only`() {
        val client = testClient { okEnvelope() }

        assertNull(client.coverUrl(null))
        assertNull(client.coverUrl(""))
        val url = requireNotNull(client.coverUrl("al-17", 600)).toHttpUrl()
        assertEquals("/rest/getCoverArt.view", url.encodedPath)
        assertEquals("al-17", url.queryParameter("id"))
        assertEquals("600", url.queryParameter("size"))
    }

    @Test
    fun `a new server replaces the base URL`() = runBlocking {
        var url: HttpUrl? = null
        val client = testClient { u -> url = u; okEnvelope() }
        client.updateConfig(SubsonicConfig("other.example:4533", "me", TEST_PASSWORD))

        client.call("ping")

        assertEquals("other.example", requireNotNull(url).host)
        assertEquals(4533, requireNotNull(url).port)
        // The token follows the (same) password of the new configuration.
        assertFalse(requireNotNull(url).queryParameter("t").isNullOrBlank())
    }
}
