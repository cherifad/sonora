package com.adlen.jellyfinaudio.data.subsonic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubsonicEnvelope<T>(
    @SerialName("subsonic-response") val response: T,
)

@Serializable
data class SubsonicError(
    val code: Int,
    val message: String? = null,
)

@Serializable
data class PingResponse(
    val status: String,
    val version: String? = null,
    val error: SubsonicError? = null,
)

@Serializable
data class Album(
    val id: String,
    val name: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val created: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    val starred: String? = null,
    val song: List<Song> = emptyList(),
)

@Serializable
data class AlbumList(
    val album: List<Album> = emptyList(),
)

@Serializable
data class AlbumListResponse(
    val status: String,
    val version: String? = null,
    val error: SubsonicError? = null,
    val albumList2: AlbumList? = null,
)

@Serializable
data class Artist(
    val id: String,
    val name: String? = null,
    val albumCount: Int? = null,
    val coverArt: String? = null,
    val starred: String? = null,
)

@Serializable
data class ArtistIndex(
    val name: String? = null,
    val artist: List<Artist> = emptyList(),
)

@Serializable
data class Artists(
    val index: List<ArtistIndex> = emptyList(),
)

@Serializable
data class ArtistsResponse(
    val status: String,
    val version: String? = null,
    val error: SubsonicError? = null,
    val artists: Artists? = null,
)

@Serializable
data class Song(
    val id: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val duration: Int? = null,
    val track: Int? = null,
    val discNumber: Int? = null,
    val coverArt: String? = null,
    val starred: String? = null,
    val isDir: Boolean? = null,
    val suffix: String? = null,
    val contentType: String? = null,
)

@Serializable
data class SearchResult3(
    val artist: List<Artist> = emptyList(),
    val album: List<Album> = emptyList(),
    val song: List<Song> = emptyList(),
)

@Serializable
data class SearchResponse(
    val status: String,
    val version: String? = null,
    val error: SubsonicError? = null,
    val searchResult3: SearchResult3? = null,
)

@Serializable
data class Playlist(
    val id: String,
    val name: String? = null,
    val comment: String? = null,
    val owner: String? = null,
    val public: Boolean? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val coverArt: String? = null,
    val created: String? = null,
    val entry: List<Song> = emptyList(),
)

@Serializable
data class Playlists(
    val playlist: List<Playlist> = emptyList(),
)

@Serializable
data class PlaylistsResponse(
    val status: String,
    val version: String? = null,
    val error: SubsonicError? = null,
    val playlists: Playlists? = null,
)

@Serializable
data class Starred2(
    val artist: List<Artist> = emptyList(),
    val album: List<Album> = emptyList(),
    val song: List<Song> = emptyList(),
)

@Serializable
data class Starred2Response(
    val status: String,
    val version: String? = null,
    val error: SubsonicError? = null,
    val starred2: Starred2? = null,
)

@Serializable
data class Genre(
    val value: String? = null,
    val songCount: Int? = null,
    val albumCount: Int? = null,
)

@Serializable
data class Genres(
    val genre: List<Genre> = emptyList(),
)

@Serializable
data class GenresResponse(
    val status: String,
    val version: String? = null,
    val error: SubsonicError? = null,
    val genres: Genres? = null,
)

@Serializable
data class AlbumDetailResponse(
    val status: String,
    val version: String? = null,
    val error: SubsonicError? = null,
    val album: Album? = null,
)

@Serializable
data class PlaylistDetailResponse(
    val status: String,
    val version: String? = null,
    val error: SubsonicError? = null,
    val playlist: Playlist? = null,
)

@Serializable
data class MusicDirectory(
    val id: String? = null,
    val name: String? = null,
    val child: List<Song> = emptyList(),
)

@Serializable
data class MusicDirectoryResponse(
    val status: String,
    val version: String? = null,
    val error: SubsonicError? = null,
    val directory: MusicDirectory? = null,
)

@Serializable
data class PlayQueue(
    val entry: List<Song> = emptyList(),
    val current: String? = null,
    val position: Long? = null,
    val username: String? = null,
    val changed: String? = null,
    val changedBy: String? = null,
)

@Serializable
data class PlayQueueResponse(
    val status: String,
    val version: String? = null,
    val error: SubsonicError? = null,
    val playQueue: PlayQueue? = null,
)

@Serializable
data class Lyrics(
    val artist: String? = null,
    val title: String? = null,
    val value: String? = null,
)

@Serializable
data class LyricsLine(
    val start: Long? = null,
    val value: String? = null,
)

@Serializable
data class StructuredLyrics(
    val displayArtist: String? = null,
    val displayTitle: String? = null,
    val lang: String? = null,
    val synced: Boolean? = null,
    val line: List<LyricsLine> = emptyList(),
)

@Serializable
data class LyricsList(
    val structuredLyrics: List<StructuredLyrics> = emptyList(),
)

@Serializable
data class LyricsResponse(
    val status: String,
    val version: String? = null,
    val error: SubsonicError? = null,
    val lyrics: Lyrics? = null,
    val lyricsList: LyricsList? = null,
)
