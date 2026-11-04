package com.adlen.jellyfinaudio.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adlen.jellyfinaudio.data.subsonic.Album
import com.adlen.jellyfinaudio.data.subsonic.Artist
import com.adlen.jellyfinaudio.data.subsonic.Playlist
import com.adlen.jellyfinaudio.ui.MainUiState
import com.adlen.jellyfinaudio.ui.components.AlbumArt
import com.adlen.jellyfinaudio.ui.components.FilterChipRow
import com.adlen.jellyfinaudio.ui.theme.Negative
import com.adlen.jellyfinaudio.ui.theme.Spacing
import com.adlen.jellyfinaudio.ui.theme.SpotifyShapes
import com.adlen.jellyfinaudio.ui.theme.TextSubdued

private val libraryTabs = listOf("Playlists", "Artists", "Albums")

@Composable
fun LibraryScreen(
    state: MainUiState,
    onRetry: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onOpenArtist: (Artist) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tabIndex by rememberSaveable { mutableIntStateOf(1) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = Spacing.xl)) {
                Spacer(Modifier.height(Spacing.m))
                Text(
                    text = "Your Library",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(Spacing.l))
                FilterChipRow(
                    labels = libraryTabs,
                    selectedIndex = tabIndex,
                    onSelect = { tabIndex = it },
                )
                Spacer(Modifier.height(Spacing.l))
            }
        }

        if (state.error != null) {
            item {
                Column(modifier = Modifier.padding(horizontal = Spacing.xl)) {
                    Text(
                        text = state.error,
                        color = Negative,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            }
        }

        if (state.libraryLoading && state.artists.isEmpty() && state.libraryAlbums.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.huge),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        when (tabIndex) {
            0 -> {
                if (state.playlists.isEmpty() && !state.libraryLoading) {
                    item { EmptyLabel("No playlists yet.") }
                }
                items(state.playlists, key = { "playlist-${it.id}" }) { playlist ->
                    LibraryRow(
                        state = state,
                        title = playlist.name ?: "Playlist",
                        subtitle = listOfNotNull(
                            playlist.owner,
                            playlist.songCount?.let { "$it songs" },
                        ).joinToString(" • "),
                        coverArtId = playlist.coverArt,
                        onClick = { onOpenPlaylist(playlist) },
                    )
                }
            }

            1 -> {
                if (state.artists.isEmpty() && !state.libraryLoading) {
                    item { EmptyLabel("No artists found.") }
                }
                items(state.artists, key = { "artist-${it.id}" }) { artist ->
                    LibraryRow(
                        state = state,
                        title = artist.name ?: "Unknown artist",
                        subtitle = albumCountLabel(artist.albumCount),
                        coverArtId = artist.coverArt,
                        circular = true,
                        onClick = { onOpenArtist(artist) },
                    )
                }
            }

            else -> {
                if (state.libraryAlbums.isEmpty() && !state.libraryLoading) {
                    item { EmptyLabel("No albums found.") }
                }
                items(state.libraryAlbums, key = { "album-${it.id}" }) { album ->
                    LibraryRow(
                        state = state,
                        title = album.name ?: "Unknown album",
                        subtitle = listOfNotNull(
                            album.artist,
                            album.year?.toString(),
                        ).joinToString(" • "),
                        coverArtId = album.coverArt,
                        onClick = { onOpenAlbum(album) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLabel(text: String) {
    Text(
        text = text,
        color = TextSubdued,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xl),
    )
}

@Composable
private fun LibraryRow(
    state: MainUiState,
    title: String,
    subtitle: String,
    coverArtId: String?,
    circular: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.xl, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArt(
            config = state.config,
            coverArtId = coverArtId,
            modifier = Modifier.size(56.dp),
            size = 200,
            shape = if (circular) CircleShape else SpotifyShapes.small,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.l),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSubdued,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun albumCountLabel(count: Int?): String = when (count) {
    null -> "Artist"
    1 -> "1 album"
    else -> "$count albums"
}
