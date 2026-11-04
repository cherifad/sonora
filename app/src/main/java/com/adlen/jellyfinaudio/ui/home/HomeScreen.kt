package com.adlen.jellyfinaudio.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adlen.jellyfinaudio.data.subsonic.Album
import com.adlen.jellyfinaudio.data.subsonic.Artist
import com.adlen.jellyfinaudio.data.subsonic.Genre
import com.adlen.jellyfinaudio.data.subsonic.Playlist
import com.adlen.jellyfinaudio.ui.MainUiState
import com.adlen.jellyfinaudio.ui.components.AlbumArt
import com.adlen.jellyfinaudio.ui.theme.Negative
import com.adlen.jellyfinaudio.ui.theme.TextSubdued

@Composable
fun HomeScreen(
    state: MainUiState,
    onRetry: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "Good evening",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = true,
                        onClick = {},
                        label = { Text("All") },
                    )
                    FilterChip(
                        selected = false,
                        onClick = {},
                        label = { Text("Music") },
                    )
                    FilterChip(
                        selected = false,
                        onClick = {},
                        label = { Text("Playlists") },
                    )
                }
            }
        }

        if (state.error != null) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = state.error,
                        color = Negative,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            }
        }

        if (state.homeLoading && state.homeSections.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (state.homeSections.isEmpty()) {
            item {
                Text(
                    text = "No library content found. Check that your server has music.",
                    color = TextSubdued,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        } else {
            items(state.homeSections, key = { it.title }) { section ->
                HomeSectionView(
                    state = state,
                    section = section,
                    onOpenAlbum = onOpenAlbum,
                    onOpenPlaylist = onOpenPlaylist,
                )
            }
        }
    }
}

@Composable
private fun HomeSectionView(
    state: MainUiState,
    section: HomeSection,
    onOpenAlbum: (Album) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
) {
    Column {
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        )

        when (section) {
            is HomeSection.Albums -> LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(section.albums, key = { it.id }) { album ->
                    AlbumCard(
                        state = state,
                        album = album,
                        onClick = { onOpenAlbum(album) },
                    )
                }
            }

            is HomeSection.Playlists -> LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(section.playlists, key = { it.id }) { playlist ->
                    PlaylistCard(
                        state = state,
                        playlist = playlist,
                        onClick = { onOpenPlaylist(playlist) },
                    )
                }
            }

            is HomeSection.Artists -> LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(section.artists, key = { it.id }) { artist ->
                    ArtistCard(state = state, artist = artist)
                }
            }

            is HomeSection.Genres -> LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(section.genres, key = { it.value.orEmpty() }) { genre ->
                    GenreChip(genre)
                }
            }
        }
    }
}

@Composable
private fun AlbumCard(
    state: MainUiState,
    album: Album,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
    ) {
        AlbumArt(
            config = state.config,
            coverArtId = album.coverArt,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            size = 400,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = album.name ?: "Unknown album",
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = album.artist.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSubdued,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlaylistCard(
    state: MainUiState,
    playlist: Playlist,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
    ) {
        AlbumArt(
            config = state.config,
            coverArtId = playlist.coverArt,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            size = 400,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = playlist.name ?: "Playlist",
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = listOfNotNull(playlist.owner, playlist.songCount?.let { "$it songs" })
                .joinToString(" • "),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSubdued,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ArtistCard(state: MainUiState, artist: Artist) {
    Column(
        modifier = Modifier.width(140.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AlbumArt(
            config = state.config,
            coverArtId = artist.coverArt,
            modifier = Modifier.size(140.dp),
            size = 400,
            shape = CircleShape,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = artist.name ?: "Unknown artist",
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Artist",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSubdued,
            maxLines = 1,
        )
    }
}

@Composable
private fun GenreChip(genre: Genre) {
    AssistChip(
        onClick = {},
        label = { Text(genre.value ?: "Genre") },
    )
}
