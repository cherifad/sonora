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
}
