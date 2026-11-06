// Subsonic/OpenSubsonic backend for Sonora. GPL-3.0.
package com.adlen.sonora.subsonic

import com.adlen.sonora.api.Album
import com.adlen.sonora.api.AlbumRef
import com.adlen.sonora.api.Artist
import com.adlen.sonora.api.ArtistRef
import com.adlen.sonora.api.Image
import com.adlen.sonora.api.MediaCard
import com.adlen.sonora.api.MediaKind
import com.adlen.sonora.api.Playlist
import com.adlen.sonora.api.Track
import org.json.JSONObject

internal fun SubsonicClient.imageOf(coverArt: String?, size: Int = 300): List<Image> =
    coverUrl(coverArt, size)?.let { listOf(Image(it)) }.orEmpty()

internal fun SubsonicClient.albumOf(json: JSONObject): Album {
    val id = json.optString("id")
    val artist = json.optString("artist").ifBlank { "Unknown artist" }
    val artistId = json.optString("artistId").takeIf { it.isNotBlank() } ?: artist
    return Album(
        uri = "spotify:album:$id",
        name = json.optString("name").ifBlank { "Unknown album" },
        artists = listOf(ArtistRef("spotify:artist:$artistId", artist)),
        images = imageOf(json.optString("coverArt").takeIf { it.isNotBlank() }, 300),
        type = "ALBUM",
        releaseDate = json.optInt("year", 0).takeIf { it > 0 }?.let { "$it-01-01" },
        trackCount = json.optInt("songCount", 0),
    )
}

internal fun SubsonicClient.trackOf(
    json: JSONObject,
    albumId: String? = null,
    albumName: String? = null,
    albumImages: List<Image> = emptyList(),
): Track {
    val id = json.optString("id")
    val artist = json.optString("artist").ifBlank { "Unknown artist" }
    val artistId = json.optString("artistId").takeIf { it.isNotBlank() } ?: artist
    val resolvedAlbumId = albumId ?: json.optString("albumId").takeIf { it.isNotBlank() }
    val resolvedAlbumName = albumName ?: json.optString("album").takeIf { it.isNotBlank() }
    val images = albumImages.ifEmpty {
        imageOf(json.optString("coverArt").takeIf { it.isNotBlank() }, 300)
    }
    return Track(
        uri = "spotify:track:$id",
        name = json.optString("title").ifBlank { "Unknown track" },
        durationMs = json.optLong("duration", 0L) * 1000L,
        artists = listOf(ArtistRef("spotify:artist:$artistId", artist)),
        album = resolvedAlbumName?.let {
            AlbumRef(
                uri = resolvedAlbumId?.let { album -> "spotify:album:$album" }.orEmpty(),
                name = it,
                images = images,
            )
        },
        playable = true,
        uid = id,
        rating = json.optInt("userRating", 0).takeIf { it > 0 },
    )
}

internal fun SubsonicClient.artistOf(json: JSONObject): Artist {
    val id = json.optString("id")
    val name = json.optString("name").ifBlank { "Unknown artist" }
    val image = json.optString("artistImageUrl").takeIf { it.isNotBlank() }
        ?: json.optString("coverArt").takeIf { it.isNotBlank() }
    return Artist(
        uri = "spotify:artist:$id",
        name = name,
        images = image?.let { listOf(Image(it)) }.orEmpty(),
    )
}

internal fun SubsonicClient.playlistOf(json: JSONObject): Playlist {
    val id = json.optString("id")
    return Playlist(
        uri = "spotify:playlist:$id",
        name = json.optString("name").ifBlank { "Playlist" },
        description = json.optString("comment").takeIf { it.isNotBlank() },
        owner = json.optString("owner").takeIf { it.isNotBlank() },
        images = imageOf(json.optString("coverArt").takeIf { it.isNotBlank() }, 300),
        trackCount = json.optInt("songCount", 0),
        editable = true,
    )
}

internal fun Album.toCard(): MediaCard = MediaCard(
    uri = uri,
    kind = MediaKind.ALBUM,
    title = name,
    subtitle = artists.joinToString(", ") { it.name },
    images = images,
)

internal fun Playlist.toCard(): MediaCard = MediaCard(
    uri = uri,
    kind = MediaKind.PLAYLIST,
    title = name,
    subtitle = owner,
    images = images,
)

internal fun Track.toCard(): MediaCard = MediaCard(
    uri = uri,
    kind = MediaKind.TRACK,
    title = name,
    subtitle = artists.joinToString(", ") { it.name },
    images = album?.images.orEmpty(),
)
