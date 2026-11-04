package com.adlen.jellyfinaudio.ui.detail

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adlen.jellyfinaudio.data.subsonic.Song
import com.adlen.jellyfinaudio.data.subsonic.SubsonicUrls
import com.adlen.jellyfinaudio.playback.PlaybackUiState
import com.adlen.jellyfinaudio.ui.DetailUiState
import com.adlen.jellyfinaudio.ui.MainUiState
import com.adlen.jellyfinaudio.ui.components.AlbumArt
import com.adlen.jellyfinaudio.ui.components.PlayButton
import com.adlen.jellyfinaudio.ui.components.TrackRow
import com.adlen.jellyfinaudio.ui.components.artworkGradient
import com.adlen.jellyfinaudio.ui.components.rememberArtworkColor
import com.adlen.jellyfinaudio.ui.theme.Negative
import com.adlen.jellyfinaudio.ui.theme.Spacing
import com.adlen.jellyfinaudio.ui.theme.TextPrimary
import com.adlen.jellyfinaudio.ui.theme.TextSubdued
import com.adlen.jellyfinaudio.ui.util.formatSeconds

@Composable
fun DetailScreen(
    state: MainUiState,
    playbackState: PlaybackUiState,
    onClose: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
) {
    val detail = state.detail ?: return
    val artworkModel = state.config?.let { SubsonicUrls.coverArt(it, detail.coverArtId, 300) }
    val headerColor = rememberArtworkColor(artworkModel, detail.coverArtId ?: detail.title)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(artworkGradient(headerColor)),
    ) {
        DetailContent(
            state = state,
            detail = detail,
            playbackState = playbackState,
            onClose = onClose,
            onPlaySongs = onPlaySongs,
        )
    }
}

@Composable
private fun DetailContent(
    state: MainUiState,
    detail: DetailUiState,
    playbackState: PlaybackUiState,
    onClose: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        contentPadding = PaddingValues(bottom = Spacing.massive),
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
                    coverArtId = detail.coverArtId,
                    modifier = Modifier.size(232.dp),
                    size = 500,
                )
                Spacer(Modifier.height(Spacing.xxxl))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = detail.typeLabel.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = detail.title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (detail.subtitle.isNotBlank()) {
                        Spacer(Modifier.height(Spacing.m))
                        Text(
                            text = detail.subtitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (detail.meta.isNotBlank()) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = detail.meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSubdued,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.xl))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
                ) {
                    PlayButton(
                        playing = playbackState.isPlaying &&
                            detail.songs.any { it.id == playbackState.currentMediaId },
                        onClick = {
                            if (detail.songs.isNotEmpty()) onPlaySongs(detail.songs, 0)
                        },
                    )
                    IconButton(onClick = {
                        if (detail.songs.isNotEmpty()) {
                            onPlaySongs(detail.songs.shuffled(), 0)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Shuffle",
                            tint = TextPrimary,
                        )
                    }
                }
            }
        }

        if (detail.error != null) {
            item {
                Text(
                    text = detail.error,
                    color = Negative,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.m),
                )
            }
        }

        if (state.detailLoading && detail.songs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.massive),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (detail.songs.isEmpty()) {
            item {
                Text(
                    text = "No tracks found.",
                    color = TextSubdued,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xl),
                )
            }
        } else {
            itemsIndexed(detail.songs, key = { _, song -> song.id }) { index, song ->
                TrackRow(
                    index = index + 1,
                    title = song.title ?: "Unknown track",
                    subtitle = song.artist ?: song.album,
                    durationLabel = formatSeconds(song.duration),
                    isPlaying = playbackState.currentMediaId == song.id,
                    onClick = { onPlaySongs(detail.songs, index) },
                )
            }
        }
    }
}
