package com.adlen.jellyfinaudio.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adlen.jellyfinaudio.ui.setup.ServerSetupScreen
import com.adlen.jellyfinaudio.ui.setup.ServerSetupViewModel

@Composable
fun AppRoot(
    mainViewModel: MainViewModel = hiltViewModel(),
    setupViewModel: ServerSetupViewModel = hiltViewModel(),
) {
    val state by mainViewModel.state.collectAsStateWithLifecycle()
    val setupState by setupViewModel.state.collectAsStateWithLifecycle()
    val playbackState by mainViewModel.playbackState.collectAsStateWithLifecycle()
    val queue by mainViewModel.queue.collectAsStateWithLifecycle()

    if (state.config == null) {
        ServerSetupScreen(
            state = setupState,
            onConnect = setupViewModel::connect,
        )
    } else {
        MainScaffold(
            state = state,
            playbackState = playbackState,
            queue = queue,
            onLoadHome = mainViewModel::loadHome,
            onLoadLibrary = mainViewModel::loadLibrary,
            onSearch = mainViewModel::search,
            onOpenAlbum = mainViewModel::openAlbum,
            onOpenPlaylist = mainViewModel::openPlaylist,
            onPlaySongs = mainViewModel::playSongs,
            onCloseDetail = mainViewModel::closeDetail,
            onPlayPause = mainViewModel::togglePlayPause,
            onNext = mainViewModel::next,
            onPrevious = mainViewModel::previous,
            onSeek = mainViewModel::seekTo,
            onShuffle = mainViewModel::toggleShuffle,
            onRepeat = mainViewModel::cycleRepeat,
            onSkipToQueueItem = mainViewModel::skipToQueueItem,
            onClearQueue = mainViewModel::clearQueue,
            onToggleStar = mainViewModel::toggleStar,
            onLoadLyrics = mainViewModel::loadLyrics,
            onClearLyrics = mainViewModel::clearLyrics,
            onAddCurrentToPlaylist = mainViewModel::addCurrentSongToPlaylist,
            onCreatePlaylistWithCurrent = mainViewModel::createPlaylistWithCurrentSong,
            onClearUserMessage = mainViewModel::clearUserMessage,
        )
    }
}
