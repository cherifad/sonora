package com.adlen.jellyfinaudio.ui.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adlen.jellyfinaudio.data.subsonic.Album
import com.adlen.jellyfinaudio.data.subsonic.Artist
import com.adlen.jellyfinaudio.data.subsonic.Genre
import com.adlen.jellyfinaudio.data.subsonic.Playlist
import com.adlen.jellyfinaudio.ui.MainUiState
import com.adlen.jellyfinaudio.ui.components.BrowseTile
import com.adlen.jellyfinaudio.ui.components.FilterChipRow
import com.adlen.jellyfinaudio.ui.components.MediaCard
import com.adlen.jellyfinaudio.ui.components.QuickPickTile
import com.adlen.jellyfinaudio.ui.theme.Dimens
import com.adlen.jellyfinaudio.ui.theme.Negative
import com.adlen.jellyfinaudio.ui.theme.Spacing
import com.adlen.jellyfinaudio.ui.theme.TextSubdued
import com.adlen.jellyfinaudio.ui.components.browseColorFor
import java.time.LocalTime

private val homeFilters = listOf("All", "Music", "Playlists")

@Composable
fun HomeScreen(
    state: MainUiState,
    onRetry: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onOpenArtist: (Artist) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }
    val greeting = remember { greetingFor(LocalTime.now().hour) }

    val quickPicks = remember(state.homeSections) {
        state.homeSections
            .filterIsInstance<HomeSection.Albums>()
            .flatMap { it.albums }
            .distinctBy { it.id }
            .take(8)
    }

    val visibleSections = when (filterIndex) {
        1 -> state.homeSections.filterNot { it is HomeSection.Playlists }
        2 -> state.homeSections.filterIsInstance<HomeSection.Playlists>()
        else -> state.homeSections
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = Spacing.m, bottom = Spacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = Spacing.xl)) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                )
                Spacer(Modifier.height(Spacing.l))
                FilterChipRow(
                    labels = homeFilters,
                    selectedIndex = filterIndex,
                    onSelect = { filterIndex = it },
                )
                Spacer(Modifier.height(Spacing.l))
            }
        }

        if (quickPicks.isNotEmpty()) {
            item {
                QuickPickGrid(
                    state = state,
                    albums = quickPicks,
                    onOpenAlbum = onOpenAlbum,
                )
            }
        }

        if (state.error != null) {
            item {
                Column(modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.m)) {
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
                        .padding(Spacing.massive),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        } else if (state.homeSections.isEmpty()) {
            item {
                Text(
                    text = "No library content found. Check that your server has music.",
                    color = TextSubdued,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = Spacing.xl),
                )
            }
        } else {
            items(visibleSections, key = { it.title }) { section ->
                HomeSectionView(
                    state = state,
                    section = section,
                    onOpenAlbum = onOpenAlbum,
                    onOpenPlaylist = onOpenPlaylist,
                    onOpenArtist = onOpenArtist,
                    onSearch = onSearch,
                )
            }
        }
    }
}

@Composable
private fun QuickPickGrid(
    state: MainUiState,
    albums: List<Album>,
    onOpenAlbum: (Album) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        albums.chunked(2).forEach { rowAlbums ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                rowAlbums.forEach { album ->
                    QuickPickTile(
                        config = state.config,
                        coverArtId = album.coverArt,
                        title = album.name ?: "Unknown album",
                        onClick = { onOpenAlbum(album) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowAlbums.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
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
    onOpenArtist: (Artist) -> Unit,
    onSearch: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(top = Spacing.l)) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.m),
        )

        when (section) {
            is HomeSection.Albums -> LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.xl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                items(section.albums, key = { it.id }) { album ->
                    MediaCard(
                        config = state.config,
                        coverArtId = album.coverArt,
                        title = album.name ?: "Unknown album",
                        subtitle = album.artist,
                        onClick = { onOpenAlbum(album) },
                    )
                }
            }

            is HomeSection.Playlists -> LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.xl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                items(section.playlists, key = { it.id }) { playlist ->
                    MediaCard(
                        config = state.config,
                        coverArtId = playlist.coverArt,
                        title = playlist.name ?: "Playlist",
                        subtitle = listOfNotNull(
                            playlist.owner,
                            playlist.songCount?.let { "$it songs" },
                        ).joinToString(" • "),
                        onClick = { onOpenPlaylist(playlist) },
                    )
                }
            }

            is HomeSection.Artists -> LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.xl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                items(section.artists, key = { it.id }) { artist ->
                    ArtistCard(
                        state = state,
                        artist = artist,
                        onClick = { onOpenArtist(artist) },
                    )
                }
            }

            is HomeSection.Genres -> LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.xl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                items(section.genres, key = { it.value.orEmpty() }) { genre ->
                    BrowseTile(
                        label = genre.value ?: "Genre",
                        color = browseColorFor(genre.value),
                        onClick = { genre.value?.let(onSearch) },
                        modifier = Modifier.width(Dimens.cardWidth),
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistCard(state: MainUiState, artist: Artist, onClick: () -> Unit) {
    MediaCard(
        config = state.config,
        coverArtId = artist.coverArt,
        title = artist.name ?: "Unknown artist",
        subtitle = "Artist",
        onClick = onClick,
        circular = true,
    )
}

private fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}
