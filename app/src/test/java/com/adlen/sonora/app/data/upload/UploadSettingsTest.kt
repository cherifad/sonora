// Sonora (https://github.com/cherifad/sonora), a fork of Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
package com.adlen.sonora.app.data.upload

import org.junit.Assert.assertEquals
import org.junit.Test

class UploadSettingsTest {

    @Test
    fun `a scheme-less name is served over https and loses the trailing slash`() {
        assertEquals("https://ingest.example.com", UploadSettings.normalize("ingest.example.com/"))
        assertEquals("https://ingest.example.com", UploadSettings.normalize("  ingest.example.com/  "))
    }

    @Test
    fun `an address that only works on the LAN keeps plain http`() {
        assertEquals("http://music.local:8088", UploadSettings.normalize("music.local:8088/"))
        assertEquals("http://192.168.1.20:8088", UploadSettings.normalize("192.168.1.20:8088"))
        assertEquals("http://localhost:8088", UploadSettings.normalize("localhost:8088"))
    }

    @Test
    fun `an https URL is kept`() {
        assertEquals("https://ingest.example", UploadSettings.normalize(" https://ingest.example/ "))
    }

    @Test
    fun `the suggested ingest name is navidrome-ingest on the server's domain`() {
        assertEquals("https://navidrome-ingest.example.com", UploadSettings.suggestUrl("https://music.example.com/"))
        assertEquals("https://navidrome-ingest.example.com", UploadSettings.suggestUrl("https://jellyfin.example.com/"))
        assertEquals("https://navidrome-ingest.example.org", UploadSettings.suggestUrl("navidrome.home.example.org:4533"))
        assertEquals("https://navidrome-ingest.servperso.me", UploadSettings.suggestUrl("https://musics.servperso.me/"))
    }

    @Test
    fun `a country domain is kept whole`() {
        assertEquals("https://navidrome-ingest.example.co.uk", UploadSettings.suggestUrl("https://music.example.co.uk"))
    }

    @Test
    fun `an IP server keeps the host and the ingest port`() {
        assertEquals("http://192.168.1.20:8088", UploadSettings.suggestUrl("http://192.168.1.20:4533/"))
        assertEquals("http://localhost:8088", UploadSettings.suggestUrl("http://localhost:4533"))
        assertEquals("http://<ingest-host>:8088", UploadSettings.suggestUrl(""))
    }
}
