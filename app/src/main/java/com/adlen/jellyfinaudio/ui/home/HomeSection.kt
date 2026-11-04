package com.adlen.jellyfinaudio.ui.home

import com.adlen.jellyfinaudio.data.subsonic.Album
import com.adlen.jellyfinaudio.data.subsonic.Artist
import com.adlen.jellyfinaudio.data.subsonic.Genre
import com.adlen.jellyfinaudio.data.subsonic.Playlist

/**
 * A locally generated home feed. Subsonic has no server-driven recommendation
 * feed, so each section is built from an explicit endpoint.
 */
sealed interface HomeSection {
    val title: String

    data class Albums(
        override val title: String,
        val albums: List<Album>,
    ) : HomeSection

    data class Playlists(
        override val title: String,
        val playlists: List<Playlist>,
    ) : HomeSection

    data class Artists(
        override val title: String,
        val artists: List<Artist>,
    ) : HomeSection

    data class Genres(
        override val title: String,
        val genres: List<Genre>,
    ) : HomeSection
}
