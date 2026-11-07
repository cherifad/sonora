// Sonora (https://github.com/cherifad/sonora), a fork of Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
package com.adlen.sonora.app.data.upload

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

class UploadClientTest {

    private lateinit var server: HttpServer
    private var lastRange = ""
    private var lastPath = ""
    private var matchDecision = "new"

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val method = exchange.requestMethod
            lastPath = path
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            lastRange = exchange.requestHeaders.getFirst("Content-Range").orEmpty()
            val response = when {
                path == "/health" -> """{"ok":true}"""
                path == "/library/match" && matchDecision == "duplicate" ->
                    """{"decision":"duplicate","match":"hash","serverTrackId":"42"}"""
                path == "/library/match" -> """{"decision":"new"}"""
                path == "/uploads" && method == "POST" -> """{"uploadId":"u1","chunkSize":4}"""
                path == "/uploads/u1" && method == "PATCH" -> """{"offset":4}"""
                path == "/uploads/u1/complete" -> """{"id":"u1","status":"waiting_scan"}"""
                path == "/uploads/u1" && method == "GET" ->
                    """{"id":"u1","status":"done","serverTrackId":"42","path":"A/B.flac"}"""
                else -> """{"error":"unexpected $method $path"}"""
            }
            val bytes = response.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @After
    fun stop() {
        server.stop(0)
    }

    private fun client() = UploadClient(UploadTarget("http://127.0.0.1:${server.address.port}", "token"))

    private val metadata = UploadClient.Metadata(
        title = "Golden Hour", artist = "Mira Sol", album = "Golden Hour",
        albumArtist = "Mira Sol", track = 1, durationMs = 214000,
    )

    @Test
    fun `health, a new match, a session, a chunk and the final status`() = runBlocking {
        val client = client()
        assertTrue(client.health())
        assertEquals(UploadClient.Decision.New, client.match("sha", metadata))

        val created = client.create("track.flac", 4, "sha", metadata) as UploadClient.Created.Session
        assertEquals("u1", created.uploadId)
        assertEquals(4, created.chunkSize)

        val offset = client.patch("u1", 0, 4, byteArrayOf(1, 2, 3, 4))
        assertEquals(4L, offset)
        assertEquals("bytes 0-3/4", lastRange)
        assertEquals("/uploads/u1", lastPath)

        assertEquals("waiting_scan", client.complete("u1").status)
        val done = client.status("u1")
        assertEquals("done", done.status)
        assertEquals("42", done.serverTrackId)
        assertEquals("A/B.flac", done.path)
    }

    @Test
    fun `a duplicate match carries the server track id`() = runBlocking {
        matchDecision = "duplicate"
        val decision = client().match("sha", metadata)
        assertTrue(decision is UploadClient.Decision.Duplicate)
        assertEquals("42", (decision as UploadClient.Decision.Duplicate).serverTrackId)
    }
}
