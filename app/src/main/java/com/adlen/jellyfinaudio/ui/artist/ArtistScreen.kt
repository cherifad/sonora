package com.adlen.jellyfinaudio.ui.artist

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adlen.jellyfinaudio.data.subsonic.Album
import com.adlen.jellyfinaudio.data.subsonic.SubsonicUrls
import com.adlen.jellyfinaudio.ui.ArtistUiState
import com.adlen.jellyfinaudio.ui.MainUiState
import com.adlen.jellyfinaudio.ui.components.AlbumArt
import com.adlen.jellyfinaudio.ui.components.MediaCard
import com.adlen.jellyfinaudio.ui.components.artworkGradient
import com.adlen.jellyfinaudio.ui.components.rememberArtworkColor
import com.adlen.jellyfinaudio.ui.theme.Negative
import com.adlen.jellyfinaudio.ui.theme.Spacing
import com.adlen.jellyfinaudio.ui.theme.TextPrimary
import com.adlen.jellyfinaudio.ui.theme.TextSubdued

@Composable
fun ArtistScreen(
    state: MainUiState,
    artist: ArtistUiState,
    onClose: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
) {
    val artworkModel = remember(state.config, artist.coverArtId) {
        state.config?.let { SubsonicUrls.coverArt(it, artist.coverArtId, 300) }
    }
    val headerColor = rememberArtworkColor(artworkModel, artist.coverArtId ?: artist.name)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(artworkGradient(headerColor)),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.m, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Rounded.MoreHoriz,
                            contentDescription = "More",
                            tint = TextPrimary,
                        )
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AlbumArt(
                        config = state.config,
                        coverArtId = artist.coverArtId,
                        modifier = Modifier.size(168.dp),
                        size = 500,
                        shape = CircleShape,
                    )
                    Spacer(Modifier.height(Spacing.xl))
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = albumCountLabel(artist.albumCount ?: artist.albums.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary.copy(alpha = 0.8f),
                    )
                }
            }

            if (artist.error != null) {
                item {
                    Text(
                        text = artist.error,
                        color = Negative,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.m),
                    )
                }
            }

            if (artist.loading && artist.albums.isEmpty()) {
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
            } else if (artist.albums.isNotEmpty()) {
                item {
                    Text(
                        text = "Albums",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(
                            start = Spacing.xl,
                            end = Spacing.xl,
                            top = Spacing.xxxl,
                            bottom = Spacing.m,
                        ),
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Spacing.xl),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
                    ) {
                        items(artist.albums, key = { it.id }) { album ->
                            MediaCard(
                                config = state.config,
                                coverArtId = album.coverArt,
                                title = album.name ?: "Unknown album",
                                subtitle = listOfNotNull(
                                    album.year?.toString(),
                                    album.songCount?.let { "$it songs" },
                                ).joinToString(" • "),
                                onClick = { onOpenAlbum(album) },
                            )
                        }
                    }
                }
            } else if (!artist.loading) {
                item {
                    Text(
                        text = "No albums found for this artist.",
                        color = TextSubdued,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xl),
                    )
                }
            }
        }
    }
}

private fun albumCountLabel(count: Int): String = when (count) {
    0 -> "Artist"
    1 -> "1 album"
    else -> "$count albums"
}
