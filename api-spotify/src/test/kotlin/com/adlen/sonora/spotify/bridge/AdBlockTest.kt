package com.adlen.sonora.spotify.bridge

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdBlockTest {
    @Test
    fun `blocks the ad audio host only`() {
        assertTrue(AdBlock.isBlocked("https://adstudio-assets.scdn.co/mp3/d0448345e619692e0b13626ad4994d4b824f7107.mp3"))
        assertFalse(AdBlock.isBlocked("https://audio-fa.scdn.co/audio/abc"))
        assertFalse(AdBlock.isBlocked("https://i.scdn.co/image/ab67616d0000b273"))
        assertFalse(AdBlock.isBlocked("https://evil.com/adstudio-assets.scdn.co/x.mp3"))
    }

    @Test
    fun `blocks foreign ad servers only`() {
        assertTrue(AdBlock.isBlocked("https://pubads.g.doubleclick.net/gampad/ads?x=1"))
        assertTrue(AdBlock.isBlocked("https://imasdk.googleapis.com/js/sdkloader/ima3.js"))
        assertTrue(AdBlock.isBlocked("https://s0.2mdn.net/ads/richmedia/studio/abc.mp4"))
        assertFalse(AdBlock.isBlocked("https://evil.com/s0.2mdn.net/x.mp4"))
        // Spotify's own ad endpoints stay reachable: a client that never calls them stands out.
        assertFalse(AdBlock.isBlocked("https://spclient.wg.spotify.com/ads/v2/config"))
        assertFalse(AdBlock.isBlocked("https://gew4-spclient.spotify.com/ad-logic/state/config?a=b"))
        assertFalse(AdBlock.isBlocked("https://adeventtracker.spotify.com/x"))
        assertFalse(AdBlock.isBlocked("https://sponsored-recommendations.spotify.com/x"))
        assertFalse(AdBlock.isBlocked("https://gew4-spclient.spotify.com/track-playback/v1/devices"))
        assertFalse(AdBlock.isBlocked("https://gew4-spclient.spotify.com/metadata/4/track/abc?ads=1"))
        assertFalse(AdBlock.isBlocked("https://api-partner.spotify.com/pathfinder/v2/query"))
        assertFalse(AdBlock.isBlocked("https://open.spotify.com/ads/"))
    }

    @Test
    fun `promotion shelves and sponsored cards are told apart`() {
        assertTrue(com.adlen.sonora.spotify.isPromotion("HomePromotionSectionData"))
        assertTrue(com.adlen.sonora.spotify.isPromotion("HomePromotionShelf"))
        assertTrue(com.adlen.sonora.spotify.isPromotion("SponsoredPlaylist"))
        assertTrue(com.adlen.sonora.spotify.isPromotion("EmbeddedAdVisual"))
        assertFalse(com.adlen.sonora.spotify.isPromotion("HomeGenericSectionData"))
        assertFalse(com.adlen.sonora.spotify.isPromotion("PlaylistResponseWrapper"))
        assertFalse(com.adlen.sonora.spotify.isPromotion("AlbumResponseWrapper"))
        assertFalse(com.adlen.sonora.spotify.isPromotion("Audiobook"))
    }

    @Test
    fun `base62 ids convert to metadata gids`() {
        // Pair checked against a captured metadata/4 request.
        assertTrue(com.adlen.sonora.spotify.base62ToHex("55vPnuXfuzXNW75JpuSczF") == "a734c1d4e4d84c4093c32d5bce9797a3")
    }

    @Test
    fun `bridge script is bundled`() {
        assertTrue(BridgeScript.source.contains("window.Sonora"))
    }
}
