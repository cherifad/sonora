// Subsonic/OpenSubsonic backend for Sonora. GPL-3.0.
package com.adlen.sonora.subsonic

import org.junit.Assert.assertEquals
import org.junit.Test

/** The fingerprint is the SHA-256 of the certificate, in lower-case hex. */
class SubsonicTlsTest {

    @Test
    fun `an empty certificate hashes to the SHA-256 of empty bytes`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            certificateFingerprint(byteArrayOf()),
        )
    }

    @Test
    fun `the same bytes give the same fingerprint`() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        assertEquals(certificateFingerprint(bytes), certificateFingerprint(bytes.copyOf()))
    }
}
