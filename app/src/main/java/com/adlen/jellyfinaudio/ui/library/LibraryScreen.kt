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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adlen.jellyfinaudio.data.subsonic.Artist
import com.adlen.jellyfinaudio.ui.MainUiState
import com.adlen.jellyfinaudio.ui.components.AlbumArt
import com.adlen.jellyfinaudio.ui.theme.Negative
import com.adlen.jellyfinaudio.ui.theme.TextSubdued

@Composable
fun LibraryScreen(
    state: MainUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        if (state.artists.isEmpty() && !state.libraryLoading) onRetry()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Column {
                Text(
                    text = "Your Library",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Artists",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSubdued,
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        if (state.error != null) {
            item {
                Column {
                    Text(
                        text = state.error.orEmpty(),
                        color = Negative,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            }
        }

        if (state.libraryLoading && state.artists.isEmpty()) {
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
        } else if (state.artists.isEmpty() && !state.libraryLoading) {
            item {
                Text(
                    text = "No artists found.",
                    color = TextSubdued,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(state.artists, key = { it.id }) { artist ->
                ArtistRow(state = state, artist = artist)
            }
        }
    }
}

@Composable
private fun ArtistRow(state: MainUiState, artist: Artist) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArt(
            config = state.config,
            coverArtId = artist.coverArt,
            modifier = Modifier.size(52.dp),
            size = 200,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = artist.name ?: "Unknown artist",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = albumCountLabel(artist.albumCount),
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
