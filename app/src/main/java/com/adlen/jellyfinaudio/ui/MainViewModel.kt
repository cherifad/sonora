package com.adlen.jellyfinaudio.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adlen.jellyfinaudio.data.ServerConfig
import com.adlen.jellyfinaudio.data.ServerRepository
import com.adlen.jellyfinaudio.data.subsonic.Album
import com.adlen.jellyfinaudio.data.subsonic.Artist
import com.adlen.jellyfinaudio.data.subsonic.Genre
import com.adlen.jellyfinaudio.data.subsonic.LyricsLine
import com.adlen.jellyfinaudio.data.subsonic.Playlist
import com.adlen.jellyfinaudio.data.subsonic.SearchResult3
import com.adlen.jellyfinaudio.data.subsonic.Song
import com.adlen.jellyfinaudio.data.subsonic.SubsonicClientFactory
import com.adlen.jellyfinaudio.playback.PlaybackConnection
import com.adlen.jellyfinaudio.ui.home.HomeSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val title: String,
    val subtitle: String,
    val coverArtId: String?,
    val songs: List<Song>,
    val typeLabel: String = "Album",
    val meta: String = "",
    val error: String? = null,
)

data class ArtistUiState(
    val id: String,
    val name: String,
    val coverArtId: String?,
    val albumCount: Int? = null,
    val albums: List<Album> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

data class LyricsUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val plain: String? = null,
    val lines: List<LyricsLine> = emptyList(),
    val synced: Boolean = false,
)

data class MainUiState(
    val config: ServerConfig? = null,
    val homeLoading: Boolean = false,
    val homeSections: List<HomeSection> = emptyList(),
    val libraryLoading: Boolean = false,
    val artists: List<Artist> = emptyList(),
    val libraryAlbums: List<Album> = emptyList(),
    val searchLoading: Boolean = false,
    val searchResults: SearchResult3? = null,
    val detail: DetailUiState? = null,
    val artistDetail: ArtistUiState? = null,
    val detailLoading: Boolean = false,
    val lyrics: LyricsUiState = LyricsUiState(),
    val playlists: List<Playlist> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val userMessage: String? = null,
    val error: String? = null,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: ServerRepository,
    private val playback: PlaybackConnection,
) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState())
    val state = _state.asStateFlow()

    val playbackState = playback.state
    val queue = playback.queue

    init {
        viewModelScope.launch {
            repository.config.collect { config ->
                if (config != null && _state.value.config == null) {
                    _state.update { it.copy(config = config) }
                    loadHome()
                    playback.restoreQueue(config)
                } else {
                    _state.update { it.copy(config = config) }
                }
            }
        }
    }

    fun loadHome() {
        val config = _state.value.config ?: return
        viewModelScope.launch {
            _state.update { it.copy(homeLoading = true, error = null) }
            val api = SubsonicClientFactory.create(config)

            val newest = runCatching {
                api.getAlbumList2(type = "newest", size = 20).response.albumList2?.album.orEmpty()
            }.getOrDefault(emptyList())

            val frequent = runCatching {
                api.getAlbumList2(type = "frequent", size = 20).response.albumList2?.album.orEmpty()
            }.getOrDefault(emptyList())

            val playlists = runCatching {
                api.getPlaylists().response.playlists?.playlist.orEmpty()
            }.getOrDefault(emptyList())

            val starred = runCatching {
                api.getStarred2().response.starred2
            }.getOrNull()

            val genres = runCatching {
                api.getGenres().response.genres?.genre.orEmpty()
            }.getOrDefault(emptyList())

            val sections = buildList {
                if (newest.isNotEmpty()) add(HomeSection.Albums("New releases", newest))
                if (frequent.isNotEmpty()) add(HomeSection.Albums("Most played", frequent))
                if (playlists.isNotEmpty()) add(HomeSection.Playlists("Your playlists", playlists))
                starred?.album?.takeIf { it.isNotEmpty() }?.let {
                    add(HomeSection.Albums("Favorite albums", it))
                }
                starred?.artist?.takeIf { it.isNotEmpty() }?.let {
                    add(HomeSection.Artists("Favorite artists", it))
                }
                if (genres.isNotEmpty()) add(HomeSection.Genres("Genres", genres))
            }

            _state.update {
                it.copy(
                    homeLoading = false,
                    homeSections = sections,
                    playlists = playlists,
                    genres = genres,
                    error = if (sections.isEmpty()) {
                        "No library content found. Check that your server has music."
                    } else {
                        null
                    },
                )
            }
        }
    }

    fun loadLibrary() {
        val config = _state.value.config ?: return
        if (_state.value.artists.isNotEmpty() && _state.value.libraryAlbums.isNotEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(libraryLoading = true, error = null) }
            val api = SubsonicClientFactory.create(config)

            val artistsResult = runCatching { api.getArtists().response }
            val albumsResult = runCatching {
                api.getAlbumList2(type = "alphabeticalByName", size = 100)
                    .response.albumList2?.album.orEmpty()
            }
            val playlistsResult = runCatching {
                api.getPlaylists().response.playlists?.playlist.orEmpty()
            }

            val response = artistsResult.getOrNull()
            if (response?.status == "ok") {
                val artists = response.artists?.index
                    ?.flatMap { it.artist }
                    .orEmpty()
                _state.update {
                    it.copy(
                        libraryLoading = false,
                        artists = artists,
                        libraryAlbums = albumsResult.getOrDefault(emptyList()),
                        playlists = playlistsResult.getOrDefault(it.playlists),
                        error = null,
                    )
                }
            } else {
                val message = response?.error?.message
                    ?: artistsResult.exceptionOrNull()?.message
                    ?: "Could not load library"
                _state.update {
                    it.copy(libraryLoading = false, error = message)
                }
            }
        }
    }

    fun search(query: String) {
        val config = _state.value.config ?: return
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(searchResults = null) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(searchLoading = true, error = null) }
            runCatching {
                SubsonicClientFactory.create(config)
                    .search3(query = trimmed)
                    .response
            }.onSuccess { response ->
                if (response.status == "ok") {
                    _state.update {
                        it.copy(
                            searchLoading = false,
                            searchResults = response.searchResult3 ?: SearchResult3(),
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            searchLoading = false,
                            error = response.error?.message ?: "Search failed",
                        )
                    }
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        searchLoading = false,
                        error = error.message ?: "Search failed",
                    )
                }
            }
        }
    }

    fun openAlbum(album: Album) {
        val config = _state.value.config ?: return
        _state.update {
            it.copy(
                detailLoading = true,
                error = null,
                detail = DetailUiState(
                    title = album.name ?: "Album",
                    subtitle = album.artist.orEmpty(),
                    coverArtId = album.coverArt,
                    songs = emptyList(),
                    typeLabel = "Album",
                    meta = albumMeta(album),
                ),
            )
        }
        viewModelScope.launch {
            _state.update { it.copy(detailLoading = true, error = null) }
            val api = SubsonicClientFactory.create(config)
            val result = runCatching {
                api.getAlbum(album.id).response
            }
            val response = result.getOrNull()
            val detailAlbum = response?.album
            Log.d(
                TAG,
                "openAlbum id=${album.id} status=${response?.status} songs=${detailAlbum?.song?.size}",
            )
            if (response?.status == "ok" && detailAlbum != null) {
                var songs = detailAlbum.song
                if (songs.isEmpty()) {
                    songs = runCatching {
                        api.getMusicDirectory(album.id).response.directory
                            ?.child
                            ?.filter { it.isDir != true }
                            .orEmpty()
                    }.getOrDefault(emptyList())
                    Log.d(TAG, "openAlbum fallback getMusicDirectory songs=${songs.size}")
                }
                _state.update {
                    it.copy(
                        detailLoading = false,
                        detail = DetailUiState(
                            title = detailAlbum.name ?: album.name ?: "Album",
                            subtitle = detailAlbum.artist ?: album.artist.orEmpty(),
                            coverArtId = detailAlbum.coverArt ?: album.coverArt,
                            songs = songs,
                            typeLabel = "Album",
                            meta = albumMeta(detailAlbum).ifBlank { albumMeta(album) },
                            error = if (songs.isEmpty()) {
                                "The server returned no tracks for this album."
                            } else {
                                null
                            },
                        ),
                    )
                }
            } else {
                val message = response?.error?.message
                    ?: result.exceptionOrNull()?.message
                    ?: "Could not load album"
                Log.e(TAG, "openAlbum failed: $message", result.exceptionOrNull())
                _state.update {
                    it.copy(
                        detailLoading = false,
                        error = message,
                        detail = it.detail?.copy(error = message),
                    )
                }
            }
        }
    }

    fun openPlaylist(playlist: Playlist) {
        val config = _state.value.config ?: return
        _state.update {
            it.copy(
                detailLoading = true,
                error = null,
                detail = DetailUiState(
                    title = playlist.name ?: "Playlist",
                    subtitle = listOfNotNull(
                        playlist.owner,
                        playlist.songCount?.let { "$it songs" },
                    ).joinToString(" • "),
                    coverArtId = playlist.coverArt,
                    songs = emptyList(),
                    typeLabel = "Playlist",
                    meta = playlistMeta(playlist),
                ),
            )
        }
        viewModelScope.launch {
            _state.update { it.copy(detailLoading = true, error = null) }
            val result = runCatching {
                SubsonicClientFactory.create(config).getPlaylist(playlist.id).response
            }
            val response = result.getOrNull()
            val detailPlaylist = response?.playlist
            Log.d(
                TAG,
                "openPlaylist id=${playlist.id} status=${response?.status} entries=${detailPlaylist?.entry?.size}",
            )
            if (response?.status == "ok" && detailPlaylist != null) {
                _state.update {
                    it.copy(
                        detailLoading = false,
                        detail = DetailUiState(
                            title = detailPlaylist.name ?: playlist.name ?: "Playlist",
                            subtitle = detailPlaylist.owner ?: playlist.owner.orEmpty(),
                            coverArtId = detailPlaylist.coverArt ?: playlist.coverArt,
                            songs = detailPlaylist.entry,
                            typeLabel = "Playlist",
                            meta = playlistMeta(detailPlaylist).ifBlank { playlistMeta(playlist) },
                            error = if (detailPlaylist.entry.isEmpty()) {
                                "The server returned no tracks for this playlist."
                            } else {
                                null
                            },
                        ),
                    )
                }
            } else {
                val message = response?.error?.message
                    ?: result.exceptionOrNull()?.message
                    ?: "Could not load playlist"
                Log.e(TAG, "openPlaylist failed: $message", result.exceptionOrNull())
                _state.update {
                    it.copy(
                        detailLoading = false,
                        error = message,
                        detail = it.detail?.copy(error = message),
                    )
                }
            }
        }
    }

    fun closeDetail() {
        _state.update { it.copy(detail = null) }
    }

    fun openArtist(artist: Artist) {
        val config = _state.value.config ?: return
        _state.update {
            it.copy(
                artistDetail = ArtistUiState(
                    id = artist.id,
                    name = artist.name ?: "Artist",
                    coverArtId = artist.coverArt,
                    albumCount = artist.albumCount,
                    loading = true,
                ),
            )
        }
        viewModelScope.launch {
            val result = runCatching {
                SubsonicClientFactory.create(config).getArtist(artist.id).response
            }
            val response = result.getOrNull()
            val artistDetail = response?.artist
            if (response?.status == "ok" && artistDetail != null) {
                _state.update {
                    it.copy(
                        artistDetail = ArtistUiState(
                            id = artistDetail.id,
                            name = artistDetail.name ?: artist.name ?: "Artist",
                            coverArtId = artistDetail.coverArt ?: artist.coverArt,
                            albumCount = artistDetail.albumCount ?: artist.albumCount,
                            albums = artistDetail.album.sortedByDescending { album ->
                                album.year ?: 0
                            },
                            loading = false,
                        ),
                    )
                }
            } else {
                val message = response?.error?.message
                    ?: result.exceptionOrNull()?.message
                    ?: "Could not load artist"
                _state.update {
                    it.copy(
                        artistDetail = it.artistDetail?.copy(loading = false, error = message),
                    )
                }
            }
        }
    }

    fun closeArtist() {
        _state.update { it.copy(artistDetail = null) }
    }

    fun playSongs(songs: List<Song>, startIndex: Int) {
        val config = _state.value.config ?: return
        playback.playSongs(config, songs, startIndex)
    }

    fun togglePlayPause() = playback.togglePlayPause()

    fun next() = playback.next()

    fun previous() = playback.previous()

    fun seekTo(positionMs: Long) = playback.seekTo(positionMs)

    fun skipToQueueItem(index: Int) = playback.skipTo(index)

    fun toggleShuffle() = playback.toggleShuffle()

    fun cycleRepeat() = playback.cycleRepeat()

    fun clearQueue() = playback.clearQueue()

    fun loadLyrics() {
        val config = _state.value.config ?: return
        val current = playback.state.value
        val id = current.currentMediaId ?: return
        val artist = current.artist
        val title = current.title

        viewModelScope.launch {
            _state.update { it.copy(lyrics = LyricsUiState(loading = true)) }
            val api = SubsonicClientFactory.create(config)

            val structured = runCatching {
                api.getLyricsBySongId(id).response.lyricsList?.structuredLyrics
                    ?.firstOrNull { it.line.isNotEmpty() }
            }.getOrNull()

            if (structured != null) {
                _state.update {
                    it.copy(
                        lyrics = LyricsUiState(
                            lines = structured.line,
                            synced = structured.synced == true,
                        ),
                    )
                }
                return@launch
            }

            val plain = runCatching {
                api.getLyrics(artist = artist, title = title).response.lyrics?.value
            }.getOrNull()

            _state.update {
                it.copy(
                    lyrics = if (!plain.isNullOrBlank()) {
                        LyricsUiState(plain = plain)
                    } else {
                        LyricsUiState(error = "No lyrics found for this track.")
                    },
                )
            }
        }
    }

    fun clearLyrics() {
        _state.update { it.copy(lyrics = LyricsUiState()) }
    }

    fun toggleStar() {
        val config = _state.value.config ?: return
        val current = playback.state.value
        val id = current.currentMediaId ?: return
        viewModelScope.launch {
            val result = runCatching {
                val api = SubsonicClientFactory.create(config)
                val response = if (current.isStarred) {
                    api.unstar(id).response
                } else {
                    api.star(id).response
                }
                check(response.status == "ok") {
                    response.error?.message ?: "Could not update favorite"
                }
            }
            result.onSuccess {
                playback.setStarred(id, !current.isStarred)
            }.onFailure { error ->
                _state.update {
                    it.copy(error = error.message ?: "Could not update favorite")
                }
            }
        }
    }

    fun addCurrentSongToPlaylist(playlistId: String) {
        val config = _state.value.config ?: return
        val songId = playback.state.value.currentMediaId ?: return
        viewModelScope.launch {
            val result = runCatching {
                val response = SubsonicClientFactory.create(config)
                    .updatePlaylist(playlistId = playlistId, songIdToAdd = listOf(songId))
                    .response
                check(response.status == "ok") {
                    response.error?.message ?: "Could not add to playlist"
                }
            }
            result.onSuccess {
                _state.update { it.copy(userMessage = "Added to playlist.") }
            }.onFailure { error ->
                _state.update {
                    it.copy(error = error.message ?: "Could not add to playlist")
                }
            }
        }
    }

    fun createPlaylistWithCurrentSong(name: String) {
        val config = _state.value.config ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(error = "Enter a playlist name.") }
            return
        }
        val songId = playback.state.value.currentMediaId
        viewModelScope.launch {
            val result = runCatching {
                val response = SubsonicClientFactory.create(config)
                    .createPlaylist(
                        name = trimmed,
                        songId = songId?.let { listOf(it) },
                    )
                    .response
                check(response.status == "ok") {
                    response.error?.message ?: "Could not create playlist"
                }
            }
            result.onSuccess {
                _state.update { it.copy(userMessage = "Playlist created.") }
                loadHome()
            }.onFailure { error ->
                _state.update {
                    it.copy(error = error.message ?: "Could not create playlist")
                }
            }
        }
    }

    fun clearUserMessage() {
        _state.update { it.copy(userMessage = null) }
    }

    private companion object {
        const val TAG = "JellyfinAudio"
    }
}

private fun albumMeta(album: Album): String = listOfNotNull(
    album.year?.toString(),
    album.songCount?.let { if (it == 1) "1 song" else "$it songs" },
    album.duration?.takeIf { it > 0 }?.let { "${it / 60} min" },
).joinToString(" • ")

private fun playlistMeta(playlist: Playlist): String = listOfNotNull(
    playlist.songCount?.let { if (it == 1) "1 song" else "$it songs" },
    playlist.duration?.takeIf { it > 0 }?.let { "${it / 60} min" },
).joinToString(" • ")
