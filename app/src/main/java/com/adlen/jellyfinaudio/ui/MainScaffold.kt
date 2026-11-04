package com.adlen.jellyfinaudio.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adlen.jellyfinaudio.R
import com.adlen.jellyfinaudio.data.subsonic.Album
import com.adlen.jellyfinaudio.data.subsonic.Playlist
import com.adlen.jellyfinaudio.data.subsonic.Song
import com.adlen.jellyfinaudio.playback.PlaybackUiState
import com.adlen.jellyfinaudio.playback.QueueItem
import com.adlen.jellyfinaudio.ui.components.MiniPlayer
import com.adlen.jellyfinaudio.ui.detail.DetailScreen
import com.adlen.jellyfinaudio.ui.home.HomeScreen
import com.adlen.jellyfinaudio.ui.library.LibraryScreen
import com.adlen.jellyfinaudio.ui.nowplaying.AddToPlaylistSheet
import com.adlen.jellyfinaudio.ui.nowplaying.LyricsScreen
import com.adlen.jellyfinaudio.ui.nowplaying.NowPlayingScreen
import com.adlen.jellyfinaudio.ui.nowplaying.QueueSheet
import com.adlen.jellyfinaudio.ui.search.SearchScreen
import com.adlen.jellyfinaudio.ui.theme.Black

private enum class MainTab(
    val labelRes: Int,
    val icon: ImageVector,
) {
    Home(R.string.tab_home, Icons.Rounded.Home),
    Search(R.string.tab_search, Icons.Rounded.Search),
    Library(R.string.tab_library, Icons.Rounded.LibraryMusic),
}

@Composable
fun MainScaffold(
    state: MainUiState,
    playbackState: PlaybackUiState,
    queue: List<QueueItem>,
    onLoadHome: () -> Unit,
    onLoadLibrary: () -> Unit,
    onSearch: (String) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onCloseDetail: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onSkipToQueueItem: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onToggleStar: () -> Unit,
    onLoadLyrics: () -> Unit,
    onClearLyrics: () -> Unit,
    onAddCurrentToPlaylist: (String) -> Unit,
    onCreatePlaylistWithCurrent: (String) -> Unit,
    onClearUserMessage: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(MainTab.Home) }
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var showAddToPlaylist by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val playbackError = playbackState.playerError ?: playbackState.connectionError

    LaunchedEffect(tab) {
        if (tab == MainTab.Library) onLoadLibrary()
    }

    LaunchedEffect(playbackError) {
        playbackError?.let { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(state.userMessage) {
        state.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearUserMessage()
        }
    }

    LaunchedEffect(playbackState.currentMediaId) {
        if (showLyrics) onLoadLyrics()
    }

    BackHandler(enabled = state.detail != null && !showNowPlaying) { onCloseDetail() }
    BackHandler(enabled = showNowPlaying && !showQueue) { showNowPlaying = false }
    BackHandler(enabled = showQueue) { showQueue = false }
    BackHandler(enabled = showLyrics) {
        showLyrics = false
        onClearLyrics()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                Column {
                    MiniPlayer(
                        state = playbackState,
                        onClick = { showNowPlaying = true },
                        onPlayPause = onPlayPause,
                    )
                    NavigationBar(containerColor = Black, tonalElevation = 0.dp) {
                        MainTab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(stringResource(item.labelRes)) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            val modifier = Modifier.padding(innerPadding)
            when (tab) {
                MainTab.Home -> HomeScreen(
                    state = state,
                    onRetry = onLoadHome,
                    onOpenAlbum = onOpenAlbum,
                    onOpenPlaylist = onOpenPlaylist,
                    modifier = modifier,
                )

                MainTab.Search -> SearchScreen(
                    state = state,
                    onSearch = onSearch,
                    onOpenAlbum = onOpenAlbum,
                    onPlaySong = { song -> onPlaySongs(listOf(song), 0) },
                    modifier = modifier,
                )

                MainTab.Library -> LibraryScreen(
                    state = state,
                    onRetry = onLoadLibrary,
                    modifier = modifier,
                )
            }
        }

        if (state.detail != null && !showNowPlaying) {
            DetailScreen(
                state = state,
                onClose = onCloseDetail,
                onPlaySongs = onPlaySongs,
            )
        }

        if (showNowPlaying) {
            NowPlayingScreen(
                state = playbackState,
                onClose = { showNowPlaying = false },
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onSeek = onSeek,
                onShuffle = onShuffle,
                onRepeat = onRepeat,
                onQueue = { showQueue = true },
                onLyrics = {
                    onLoadLyrics()
                    showLyrics = true
                },
                onAddToPlaylist = { showAddToPlaylist = true },
                onLike = onToggleStar,
            )
        }

        if (showAddToPlaylist) {
            AddToPlaylistSheet(
                playlists = state.playlists,
                onDismiss = { showAddToPlaylist = false },
                onAdd = { onAddCurrentToPlaylist(it.id) },
                onCreate = onCreatePlaylistWithCurrent,
            )
        }

        if (showLyrics) {
            LyricsScreen(
                state = playbackState,
                lyrics = state.lyrics,
                onClose = {
                    showLyrics = false
                    onClearLyrics()
                },
            )
        }

        if (showQueue) {
            QueueSheet(
                queue = queue,
                onDismiss = { showQueue = false },
                onSelect = onSkipToQueueItem,
                onClear = onClearQueue,
            )
        }
    }
}
