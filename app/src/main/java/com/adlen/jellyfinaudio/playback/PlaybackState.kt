package com.adlen.jellyfinaudio.playback

import androidx.media3.common.Player

data class PlaybackUiState(
    val connected: Boolean = false,
    val currentMediaId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val artworkUri: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val isStarred: Boolean = false,
    val connectionError: String? = null,
    val playerError: String? = null,
)

data class QueueItem(
    val mediaId: String,
    val title: String,
    val artist: String,
    val artworkUri: String?,
    val isCurrent: Boolean,
)
