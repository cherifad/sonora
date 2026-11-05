// Subsonic/OpenSubsonic backend for Myspoti. GPL-3.0.
package app.myspoti.subsonic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The download quality names the app stores → the parameters the server understands. */
class DownloadParamsTest {

    @Test
    fun `small downloads ask for mp3 128`() {
        assertEquals("mp3" to 128, DownloadParams.of("SMALL"))
    }

    @Test
    fun `cd downloads only cap the bitrate`() {
        assertEquals(null to 320, DownloadParams.of("CD"))
    }

    @Test
    fun `hi-res keeps the original file`() {
        assertEquals(null to null, DownloadParams.of("HI_RES"))
    }

    @Test
    fun `an unknown or missing quality keeps the original file`() {
        assertNull(DownloadParams.of(null).first)
        assertNull(DownloadParams.of("").first)
        assertNull(DownloadParams.of("off").second)
        assertNull(DownloadParams.of("ASK").second)
    }
}
