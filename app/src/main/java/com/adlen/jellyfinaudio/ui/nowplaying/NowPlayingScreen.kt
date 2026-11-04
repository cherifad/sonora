package com.adlen.jellyfinaudio.ui.nowplaying

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.adlen.jellyfinaudio.playback.PlaybackUiState
import com.adlen.jellyfinaudio.ui.components.artworkGradient
import com.adlen.jellyfinaudio.ui.theme.Accent
import com.adlen.jellyfinaudio.ui.theme.Background
import com.adlen.jellyfinaudio.ui.theme.Black
import com.adlen.jellyfinaudio.ui.theme.Card
import com.adlen.jellyfinaudio.ui.theme.Negative
import com.adlen.jellyfinaudio.ui.theme.Spacing
import com.adlen.jellyfinaudio.ui.theme.TextPrimary
import com.adlen.jellyfinaudio.ui.theme.TextSubdued
import com.adlen.jellyfinaudio.ui.theme.White
import com.adlen.jellyfinaudio.ui.util.formatMs
import kotlinx.coroutines.launch

@Composable
fun NowPlayingScreen(
    state: PlaybackUiState,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onQueue: () -> Unit,
    onLyrics: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onLike: () -> Unit,
) {
    val headerColor = rememberArtworkColorForNowPlaying(state)
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val dismissThresholdPx = with(LocalDensity.current) { 160.dp.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = offsetY.value }
                .background(artworkGradient(headerColor))
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (offsetY.value > dismissThresholdPx) {
                                scope.launch {
                                    offsetY.animateTo(
                                        targetValue = screenHeightPx,
                                        animationSpec = tween(180),
                                    )
                                    onClose()
                                    offsetY.snapTo(0f)
                                }
                            } else {
                                scope.launch { offsetY.animateTo(0f, spring()) }
                            }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            offsetY.snapTo((offsetY.value + dragAmount).coerceAtLeast(0f))
                        }
                    }
                },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(top = Spacing.m, bottom = Spacing.xl),
            ) {
                NowPlayingTopBar(state = state, onClose = onClose)

                Spacer(Modifier.height(Spacing.l))

                NowPlayingArtwork(state = state)

                Spacer(Modifier.height(Spacing.xxxl))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xxxl),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.title ?: "Nothing playing",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = state.artist.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSubdued,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val error = state.playerError ?: state.connectionError
                        if (error != null) {
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = Negative,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(onClick = onLike) {
                        Icon(
                            imageVector = if (state.isStarred) {
                                Icons.Rounded.Favorite
                            } else {
                                Icons.Rounded.FavoriteBorder
                            },
                            contentDescription = if (state.isStarred) {
                                "Remove favorite"
                            } else {
                                "Favorite"
                            },
                            tint = if (state.isStarred) Accent else TextPrimary,
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.m))

                NowPlayingSeekBar(state = state, onSeek = onSeek)

                Spacer(Modifier.height(Spacing.m))

                TransportControls(
                    state = state,
                    onShuffle = onShuffle,
                    onPrevious = onPrevious,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onRepeat = onRepeat,
                )

                Spacer(Modifier.weight(1f))

                BottomActions(
                    onLyrics = onLyrics,
                    onAddToPlaylist = onAddToPlaylist,
                    onQueue = onQueue,
                )
            }
        }
    }
}

@Composable
private fun NowPlayingTopBar(state: PlaybackUiState, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Close player",
                tint = TextPrimary,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Playing from",
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
            Text(
                text = state.album ?: "Your library",
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        IconButton(onClick = {}) {
            Icon(
                imageVector = Icons.Rounded.MoreHoriz,
                contentDescription = "More",
                tint = TextPrimary,
            )
        }
    }
}

@Composable
private fun NowPlayingArtwork(state: PlaybackUiState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xxxl),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(24.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(Card),
        ) {
            if (state.artworkUri != null) {
                AsyncImage(
                    model = state.artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = TextSubdued,
                    modifier = Modifier
                        .size(96.dp)
                        .align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun NowPlayingSeekBar(state: PlaybackUiState, onSeek: (Long) -> Unit) {
    var draggedValue by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1L)
    val value = (draggedValue ?: state.positionMs.toFloat()).coerceIn(0f, duration.toFloat())

    Column(modifier = Modifier.padding(horizontal = Spacing.xxxl)) {
        Slider(
            value = value,
            onValueChange = { draggedValue = it },
            onValueChangeFinished = {
                draggedValue?.let { onSeek(it.toLong()) }
                draggedValue = null
            },
            valueRange = 0f..duration.toFloat(),
            enabled = state.durationMs > 0,
            colors = SliderDefaults.colors(
                thumbColor = TextPrimary,
                activeTrackColor = TextPrimary,
                inactiveTrackColor = TextPrimary.copy(alpha = 0.30f),
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatMs(value.toLong()),
                style = MaterialTheme.typography.bodySmall,
                color = TextSubdued,
            )
            Text(
                text = formatMs(state.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = TextSubdued,
            )
        }
    }
}

@Composable
private fun TransportControls(
    state: PlaybackUiState,
    onShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xxl),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onShuffle) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = "Shuffle",
                tint = if (state.shuffle) Accent else TextPrimary,
            )
        }
        IconButton(onClick = onPrevious, enabled = state.hasPrevious) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous",
                tint = if (state.hasPrevious) TextPrimary else TextSubdued,
                modifier = Modifier.size(36.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(White)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onPlayPause() })
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (state.isPlaying) "Pause" else "Play",
                tint = Black,
                modifier = Modifier.size(34.dp),
            )
        }
        IconButton(onClick = onNext, enabled = state.hasNext) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next",
                tint = if (state.hasNext) TextPrimary else TextSubdued,
                modifier = Modifier.size(36.dp),
            )
        }
        IconButton(onClick = onRepeat) {
            Icon(
                imageVector = if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                    Icons.Rounded.RepeatOne
                } else {
                    Icons.Rounded.Repeat
                },
                contentDescription = "Repeat",
                tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) TextPrimary else Accent,
            )
        }
    }
}

@Composable
private fun BottomActions(
    onLyrics: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onQueue: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xxxl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Devices,
                contentDescription = null,
                tint = Accent,
            )
            Spacer(Modifier.width(Spacing.m))
            Text(
                text = "This phone",
                style = MaterialTheme.typography.labelMedium,
                color = Accent,
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onLyrics) {
            Icon(
                imageVector = Icons.Rounded.Lyrics,
                contentDescription = "Lyrics",
                tint = TextSubdued,
            )
        }
        IconButton(onClick = onAddToPlaylist) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                contentDescription = "Add to playlist",
                tint = TextSubdued,
            )
        }
        IconButton(onClick = onQueue) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = "Queue",
                tint = TextSubdued,
            )
        }
    }
}

@Composable
private fun rememberArtworkColorForNowPlaying(state: PlaybackUiState) =
    com.adlen.jellyfinaudio.ui.components.rememberArtworkColor(
        model = state.artworkUri,
        seed = state.album ?: state.title,
    )
