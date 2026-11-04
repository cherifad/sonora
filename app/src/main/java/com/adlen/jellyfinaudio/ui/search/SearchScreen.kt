package com.adlen.jellyfinaudio.ui.search

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adlen.jellyfinaudio.data.subsonic.Album
import com.adlen.jellyfinaudio.data.subsonic.Artist
import com.adlen.jellyfinaudio.data.subsonic.Song
import com.adlen.jellyfinaudio.ui.MainUiState
import com.adlen.jellyfinaudio.ui.components.AlbumArt
import com.adlen.jellyfinaudio.ui.theme.TextSubdued

@Composable
fun SearchScreen(
    state: MainUiState,
    onSearch: (String) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onPlaySong: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Column {
                Text(
                    text = "Search",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("What do you want to listen to?") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        IconButton(onClick = { onSearch(query) }) {
                            Icon(Icons.Rounded.Search, contentDescription = "Search")
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        if (state.searchLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        val results = state.searchResults
        if (results == null) {
            item {
                Text(
                    text = "Search your library",
                    color = TextSubdued,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            if (results.artist.isNotEmpty()) {
                item { SectionHeader("Artists") }
                items(results.artist, key = { "artist-${it.id}" }) { artist ->
                    ArtistResult(state = state, artist = artist)
                }
            }
            if (results.album.isNotEmpty()) {
                item { SectionHeader("Albums") }
                items(results.album, key = { "album-${it.id}" }) { album ->
                    AlbumResult(
                        state = state,
                        album = album,
                        onClick = { onOpenAlbum(album) },
                    )
                }
            }
            if (results.song.isNotEmpty()) {
                item { SectionHeader("Songs") }
                items(results.song, key = { "song-${it.id}" }) { song ->
                    SongResult(
                        song = song,
                        onClick = { onPlaySong(song) },
                    )
                }
            }
            if (results.artist.isEmpty() && results.album.isEmpty() && results.song.isEmpty()) {
                item {
                    Text(
                        text = "No results.",
                        color = TextSubdued,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun ArtistResult(state: MainUiState, artist: Artist) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AlbumArt(
            config = state.config,
            coverArtId = artist.coverArt,
            modifier = Modifier.size(48.dp),
            size = 200,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Text(
                text = artist.name ?: "Unknown artist",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Artist",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSubdued,
            )
        }
    }
}

@Composable
private fun AlbumResult(
    state: MainUiState,
    album: Album,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArt(
            config = state.config,
            coverArtId = album.coverArt,
            modifier = Modifier.size(48.dp),
            size = 200,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
        ) {
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
}

@Composable
private fun SongResult(song: Song, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = TextSubdued,
            modifier = Modifier.size(24.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = song.title ?: "Unknown track",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(song.artist, song.album).joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSubdued,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
