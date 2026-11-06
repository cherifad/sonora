// Subsonic/OpenSubsonic backend for Sonora. GPL-3.0.
package com.adlen.sonora.subsonic

/**
 * What the server said about itself, probed once per session and cached. The optional calls
 * (lyrics, artist biography, API-key auth) consult this instead of trying and swallowing.
 */
data class ServerCapabilities(
    val type: String? = null,
    val serverVersion: String? = null,
    val apiVersion: String? = null,
    val openSubsonic: Boolean = false,
    val extensions: Set<String> = emptySet(),
) {
    enum class Feature {
        API_KEY_AUTH,
        FORM_POST,
        SONG_LYRICS,
        TRANSCODE_OFFSET,
        RATINGS,
        SHARES,
        BOOKMARKS,
        SCAN,
        ARTIST_INFO,
        TOP_SONGS,
        SIMILAR_SONGS,
        PODCASTS,
    }

    /**
     * Extensions are advertised; core Subsonic endpoints are not. For those we use a small
     * matrix of what the known bridges actually answer, and `callOptional` can still mark one
     * unsupported at runtime. Being conservative here hides a button rather than showing one
     * that always fails.
     */
    fun supports(feature: Feature): Boolean = when (feature) {
        Feature.API_KEY_AUTH -> "apiKeyAuthentication" in extensions
        Feature.FORM_POST -> "formPost" in extensions
        Feature.SONG_LYRICS -> openSubsonic || "songLyrics" in extensions
        Feature.TRANSCODE_OFFSET -> "transcodeOffset" in extensions
        // Jellyfin's Subsonic bridge is read-mostly: no shares, scan, Last.fm biography,
        // popular tracks or bookmarks. Ratings are exposed by Navidrome/Gonic/Subsonic.
        Feature.RATINGS -> !isJellyfin
        Feature.SHARES -> !isJellyfin
        Feature.BOOKMARKS -> !isJellyfin
        Feature.SCAN -> !isJellyfin
        Feature.ARTIST_INFO -> !isJellyfin
        Feature.TOP_SONGS -> !isJellyfin
        Feature.SIMILAR_SONGS -> !isJellyfin
        // Podcasts are a Jellyfin feature; Navidrome has no podcast support.
        Feature.PODCASTS -> isJellyfin
    }

    val isJellyfin: Boolean get() = type?.contains("jellyfin", ignoreCase = true) == true
    val isNavidrome: Boolean get() = type?.contains("navidrome", ignoreCase = true) == true

    /** A one-line label for Settings / diagnostics. */
    val label: String
        get() = buildString {
            append(type?.ifBlank { null } ?: "Subsonic")
            serverVersion?.takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
            if (openSubsonic) append(" · OpenSubsonic")
        }
}
