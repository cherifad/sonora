// Subsonic/OpenSubsonic backend for Sonora. GPL-3.0.
package com.adlen.sonora.subsonic

/**
 * How the server makes a download file: a container and/or a bitrate cap, or nothing for the
 * original. `SMALL` asks for MP3 128 kbps, `CD` caps whatever the file is at 320 kbps;
 * anything else (HI_RES, ASK, OFF, unknown) keeps the server's own file.
 */
internal object DownloadParams {
    /** `format` (or null) and `maxBitRate` in kbps (or null). */
    fun of(quality: String?): Pair<String?, Int?> = when (quality) {
        "SMALL" -> "mp3" to 128
        "CD" -> null to 320
        else -> null to null
    }
}
