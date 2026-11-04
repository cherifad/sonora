package com.adlen.jellyfinaudio.ui.search

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adlen.jellyfinaudio.data.subsonic.Album
import com.adlen.jellyfinaudio.data.subsonic.Artist
import com.adlen.jellyfinaudio.data.subsonic.Song
import com.adlen.jellyfinaudio.ui.MainUiState
import com.adlen.jellyfinaudio.ui.components.AlbumArt
import com.adlen.jellyfinaudio.ui.components.BrowseTile
import com.adlen.jellyfinaudio.ui.components.TrackRow
import com.adlen.jellyfinaudio.ui.components.browseColorFor
import com.adlen.jellyfinaudio.ui.theme.Negative
import com.adlen.jellyfinaudio.ui.theme.Spacing
import com.adlen.jellyfinaudio.ui.theme.SpotifyShapes
import com.adlen.jellyfinaudio.ui.theme.TextPrimary
import com.adlen.jellyfinaudio.ui.theme.TextSubdued
import com.adlen.jellyfinaudio.ui.theme.White
import com.adlen.jellyfinaudio.ui.util.formatSeconds
import kotlinx.coroutines.delay

private val searchFilters = listOf("All", "Songs", "Albums", "Artists")

@Composable
fun SearchScreen(
    state: MainUiState,
    onSearch: (String) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenArtist: (Artist) -> Unit,
    onPlaySong: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            onSearch("")
            return@LaunchedEffect
        }
        delay(300)
        onSearch(query)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.xxxl),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = Spacing.xl)) {
                Spacer(Modifier.height(Spacing.m))
                Text(
                    text = "Search",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(Spacing.l))
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    onSubmit = { onSearch(query) },
                )
                Spacer(Modifier.height(Spacing.l))
            }
        }

        if (query.isBlank()) {
            item {
                Text(
                    text = "Browse all",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(
                        start = Spacing.xl,
                        end = Spacing.xl,
                        top = Spacing.m,
                        bottom = Spacing.l,
                    ),
                )
            }
            if (state.genres.isEmpty()) {
                item {
                    Text(
                        text = "Genres appear here once your library has loaded.",
                        color = TextSubdued,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = Spacing.xl),
                    )
                }
            } else {
                items(state.genres.chunked(2), key = { row ->
                    row.joinToString("|") { it.value.orEmpty() }
                }) { rowGenres ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.xl, vertical = Spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
                    ) {
                        rowGenres.forEach { genre ->
                            BrowseTile(
                                label = genre.value ?: "Genre",
                                color = browseColorFor(genre.value),
                                onClick = {
                                    query = genre.value.orEmpty()
                                    onSearch(query)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowGenres.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            return@LazyColumn
        }

        if (state.searchLoading) {
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

        val results = state.searchResults
        if (state.error != null) {
            item {
                Text(
                    text = state.error,
                    color = Negative,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = Spacing.xl),
                )
            }
        }

        if (results == null) {
            if (!state.searchLoading) {
                item {
                    Text(
                        text = "Search your library",
                        color = TextSubdued,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = Spacing.xl),
                    )
                }
            }
        } else {
            val topAlbum = results.album.firstOrNull()
            val topArtist = results.artist.firstOrNull()
            if (filterIndex == 0 && (topAlbum != null || topArtist != null)) {
                item {
                    Text(
                        text = "Top result",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.m),
                    )
                }
                item {
                    if (topAlbum != null) {
                        TopResultCard(
                            state = state,
                            title = topAlbum.name ?: "Album",
                            subtitle = topAlbum.artist ?: "Album",
                            coverArtId = topAlbum.coverArt,
                            onClick = { onOpenAlbum(topAlbum) },
                        )
                    } else if (topArtist != null) {
                        TopResultCard(
                            state = state,
                            title = topArtist.name ?: "Artist",
                            subtitle = "Artist",
                            coverArtId = topArtist.coverArt,
                            onClick = { onOpenArtist(topArtist) },
                        )
                    }
                }
            }

            item {
                com.adlen.jellyfinaudio.ui.components.FilterChipRow(
                    labels = searchFilters,
                    selectedIndex = filterIndex,
                    onSelect = { filterIndex = it },
                    modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.m),
                )
            }

            val showSongs = filterIndex == 0 || filterIndex == 1
            val showAlbums = filterIndex == 0 || filterIndex == 2
            val showArtists = filterIndex == 0 || filterIndex == 3

            if (showSongs && results.song.isNotEmpty()) {
                item { ResultHeader("Songs") }
                items(results.song, key = { "song-${it.id}" }) { song ->
                    TrackRow(
                        index = results.song.indexOf(song) + 1,
                        title = song.title ?: "Unknown track",
                        subtitle = listOfNotNull(song.artist, song.album)
                            .joinToString(" • "),
                        durationLabel = formatSeconds(song.duration),
                        onClick = { onPlaySong(song) },
                    )
                }
            }

            if (showAlbums && results.album.isNotEmpty()) {
                item { ResultHeader("Albums") }
                items(results.album, key = { "album-${it.id}" }) { album ->
                    MediaListRow(
                        state = state,
                        title = album.name ?: "Unknown album",
                        subtitle = album.artist.orEmpty(),
                        coverArtId = album.coverArt,
                        onClick = { onOpenAlbum(album) },
                    )
                }
            }

            if (showArtists && results.artist.isNotEmpty()) {
                item { ResultHeader("Artists") }
                items(results.artist, key = { "artist-${it.id}" }) { artist ->
                    MediaListRow(
                        state = state,
                        title = artist.name ?: "Unknown artist",
                        subtitle = "Artist",
                        coverArtId = artist.coverArt,
                        circular = true,
                        onClick = { onOpenArtist(artist) },
                    )
                }
            }

            val nothing = results.artist.isEmpty() && results.album.isEmpty() && results.song.isEmpty()
            if (nothing && !state.searchLoading) {
                item {
                    Text(
                        text = "No results found for ‘$query’",
                        color = TextSubdued,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xl),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(White.copy(alpha = 0.10f))
            .padding(horizontal = Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = TextSubdued,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(Spacing.m))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "What do you want to listen to?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSubdued,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = TextPrimary,
                ),
                cursorBrush = SolidColor(White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Clear",
                    tint = TextSubdued,
                )
            }
        }
    }
}

@Composable
private fun ResultHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.l, bottom = Spacing.xs),
    )
}

@Composable
private fun TopResultCard(
    state: MainUiState,
    title: String,
    subtitle: String,
    coverArtId: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl)
            .clip(SpotifyShapes.card)
            .background(White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(Spacing.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArt(
            config = state.config,
            coverArtId = coverArtId,
            modifier = Modifier.size(92.dp),
            size = 300,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.xl),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.xs))
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

@Composable
private fun MediaListRow(
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
            modifier = Modifier.size(52.dp),
            size = 200,
            shape = if (circular) androidx.compose.foundation.shape.CircleShape else SpotifyShapes.card,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.l),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
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
