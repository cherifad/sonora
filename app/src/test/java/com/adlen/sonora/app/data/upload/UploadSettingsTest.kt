// Sonora (https://github.com/cherifad/sonora), a fork of Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
package com.adlen.sonora.app.data.upload

import org.junit.Assert.assertEquals
import org.junit.Test

class UploadSettingsTest {

    @Test
    fun `a URL without a scheme gets http and no trailing slash`() {
        assertEquals("http://music.local:8088", UploadSettings.normalize("music.local:8088/"))
    }

    @Test
    fun `an https URL is kept`() {
        assertEquals("https://ingest.example", UploadSettings.normalize(" https://ingest.example/ "))
    }

    @Test
    fun `the suggested ingest name adds upload to the server host`() {
        assertEquals("https://music-upload.example.com", UploadSettings.suggestUrl("https://music.example.com/"))
        assertEquals("http://navidrome-upload.home.example.org", UploadSettings.suggestUrl("navidrome.home.example.org:4533"))
    }

    @Test
    fun `an IP server keeps the host and the ingest port`() {
        assertEquals("http://192.168.1.20:8088", UploadSettings.suggestUrl("http://192.168.1.20:4533/"))
        assertEquals("http://localhost:8088", UploadSettings.suggestUrl("http://localhost:4533"))
    }
}
