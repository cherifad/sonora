// Subsonic/OpenSubsonic backend for Myspoti. GPL-3.0.
package app.myspoti.subsonic

import app.myspoti.api.Album
import app.myspoti.api.AlbumRef
import app.myspoti.api.Artist
import app.myspoti.api.ArtistRef
import app.myspoti.api.Image
import app.myspoti.api.MediaCard
import app.myspoti.api.MediaKind
import app.myspoti.api.Playlist
import app.myspoti.api.Track
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
