// Subsonic/OpenSubsonic backend for Sonora. GPL-3.0.
package com.adlen.sonora.audio

/**
 * Preferences the app pushes to the backend's player. The audio thread reads them; a change
 * takes effect on the next buffer, without rebuilding the player.
 */
object AudioSettings {
    @Volatile var skipSilence: Boolean = false
    @Volatile var replayGainEnabled: Boolean = true
    @Volatile var replayGainAlbum: Boolean = false
}
