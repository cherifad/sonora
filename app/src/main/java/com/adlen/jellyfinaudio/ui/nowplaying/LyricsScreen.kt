package com.adlen.jellyfinaudio.ui.nowplaying

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adlen.jellyfinaudio.playback.PlaybackUiState
import com.adlen.jellyfinaudio.ui.LyricsUiState
import com.adlen.jellyfinaudio.ui.theme.Accent
import com.adlen.jellyfinaudio.ui.theme.Background
import com.adlen.jellyfinaudio.ui.theme.Negative
import com.adlen.jellyfinaudio.ui.theme.TextPrimary
import com.adlen.jellyfinaudio.ui.theme.TextSubdued

@Composable
fun LyricsScreen(
    state: PlaybackUiState,
    lyrics: LyricsUiState,
    onClose: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close lyrics",
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title ?: "Lyrics",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    Text(
                        text = state.artist.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSubdued,
                        maxLines = 1,
                    )
                }
            }

            when {
                lyrics.loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                lyrics.error != null -> Text(
                    text = lyrics.error,
                    color = Negative,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(24.dp),
                )

                lyrics.plain != null -> Text(
                    text = lyrics.plain,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                )

                lyrics.lines.isNotEmpty() -> SyncedLyrics(
                    lyrics = lyrics,
                    positionMs = state.positionMs,
                )

                else -> Text(
                    text = "No lyrics found for this track.",
                    color = TextSubdued,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
    }
}

@Composable
private fun SyncedLyrics(
    lyrics: LyricsUiState,
    positionMs: Long,
) {
    val listState = rememberLazyListState()
    val currentIndex = if (lyrics.synced) {
        lyrics.lines.indexOfLast { (it.start ?: 0L) <= positionMs }
    } else {
        -1
    }

    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            val isCurrent = index == currentIndex && lyrics.synced
            Text(
                text = line.value.orEmpty(),
                style = if (isCurrent) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                color = if (isCurrent) Accent else TextPrimary,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}
