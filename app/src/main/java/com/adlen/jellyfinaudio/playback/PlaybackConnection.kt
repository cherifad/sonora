package com.adlen.jellyfinaudio.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.adlen.jellyfinaudio.data.ServerConfig
import com.adlen.jellyfinaudio.data.subsonic.Song
import com.adlen.jellyfinaudio.data.subsonic.SubsonicApi
import com.adlen.jellyfinaudio.data.subsonic.SubsonicClientFactory
import com.adlen.jellyfinaudio.data.subsonic.SubsonicUrls
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the MediaController connection used by the UI. Playback itself lives in
 * [PlaybackService] so it continues when the activity is backgrounded.
 */
@Singleton
class PlaybackConnection @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pendingAction: (() -> Unit)? = null

    private var lastConfig: ServerConfig? = null
    private var lastSongs: List<Song> = emptyList()
    private var lastIndex: Int = 0
    private var fallbackAttempted: Boolean = false
    private var connectionGeneration: Int = 0

    private val starredById = mutableMapOf<String, Boolean>()
    private var lastScrobbledId: String? = null
    private var scrobbleSubmitted: Boolean = false
    private var lastQueueSaveAt: Long = 0L
    private var api: SubsonicApi? = null
    private var apiConfig: ServerConfig? = null

    private val _state = MutableStateFlow(PlaybackUiState())
    val state = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue = _queue.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            update()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Player error: ${error.describe()}", error)
            if (!fallbackAttempted && error.isFormatError() && lastSongs.isNotEmpty()) {
                fallbackAttempted = true
                retryWithMp3()
            } else {
                _state.update { it.copy(playerError = error.describe()) }
            }
        }

        override fun onPlayerErrorChanged(error: PlaybackException?) {
            if (error == null) {
                _state.update { it.copy(playerError = null) }
            }
        }
    }

    init {
        scope.launch {
            while (isActive) {
                if (controller?.isPlaying == true) update()
                delay(500)
            }
        }
    }

    private fun connect() {
        if (controllerFuture != null) return
        val generation = ++connectionGeneration
        Log.d(TAG, "Connecting MediaController to PlaybackService")
        val token = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java),
        )
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { mediaController ->
                        Log.d(TAG, "MediaController connected")
                        controller = mediaController
                        mediaController.addListener(listener)
                        pendingAction?.invoke()
                        pendingAction = null
                        _state.update { it.copy(connectionError = null) }
                        update()
                    }
                    .onFailure { error ->
                        Log.e(TAG, "MediaController connection failed", error)
                        controllerFuture = null
                        _state.update {
                            it.copy(
                                connectionError = error.message
                                    ?: "Playback service unavailable",
                            )
                        }
                    }
            },
            ContextCompat.getMainExecutor(context),
        )

        scope.launch {
            delay(15_000)
            if (generation == connectionGeneration && controller == null) {
                _state.update {
                    it.copy(
                        connectionError = it.connectionError
                            ?: "Playback service did not connect within 15s",
                    )
                }
            }
        }
    }

    fun playSongs(config: ServerConfig, songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) {
            Log.w(TAG, "playSongs called with an empty song list")
            return
        }
        Log.d(TAG, "playSongs: ${songs.size} item(s), startIndex=$startIndex")
        _state.update { it.copy(playerError = null) }

        val index = startIndex.coerceIn(0, songs.lastIndex)
        lastConfig = config
        lastSongs = songs
        lastIndex = index
        fallbackAttempted = false
        lastScrobbledId = null
        scrobbleSubmitted = false
        lastQueueSaveAt = 0L

        enqueuePlayback(config = config, songs = songs, index = index, format = null)
    }

    private fun enqueuePlayback(
        config: ServerConfig,
        songs: List<Song>,
        index: Int,
        format: String?,
    ) {
        val items = buildItems(config, songs, format)
        val action: () -> Unit = {
            controller?.setMediaItems(items, index, 0L)
            controller?.prepare()
            controller?.play()
        }
        if (controller != null) {
            Log.d(
                TAG,
                "controller connected=${controller?.isConnected} commands=${controller?.availableCommands}",
            )
            action()
            Log.d(
                TAG,
                "after play request: count=${controller?.mediaItemCount} state=${controller?.playbackState}",
            )
        } else {
            Log.d(TAG, "Controller not connected yet; queueing playback")
            _state.update { it.copy(connectionError = "Connecting to playback service…") }
            pendingAction = action
            if (controllerFuture == null) connect()
        }
    }

    private fun buildItems(
        config: ServerConfig,
        songs: List<Song>,
        format: String?,
    ): List<MediaItem> = songs.map { song ->
        Log.d(
            TAG,
            "Queueing song id=${song.id} suffix=${song.suffix} contentType=${song.contentType}",
        )
        starredById[song.id] = !song.starred.isNullOrBlank()
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title ?: "Unknown track")
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .apply {
                SubsonicUrls.coverArt(config, song.coverArt, 512)
                    ?.let { setArtworkUri(Uri.parse(it)) }
            }
            .build()

        MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(SubsonicUrls.stream(config, song.id, format = format))
            .setMediaMetadata(metadata)
            .build()
    }

    private fun retryWithMp3() {
        val config = lastConfig ?: return
        if (lastSongs.isEmpty()) return
        val index = controller?.currentMediaItemIndex
            ?.takeIf { it in lastSongs.indices }
            ?: lastIndex
        Log.w(TAG, "Retrying playback with MP3 transcoding at index=$index")
        enqueuePlayback(config = config, songs = lastSongs, index = index, format = "mp3")
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun skipTo(index: Int) {
        controller?.seekTo(index, 0L)
    }

    fun toggleShuffle() {
        val player = controller ?: return
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val player = controller ?: return
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun clearQueue() {
        controller?.clearMediaItems()
    }

    private fun update() {
        val player = controller ?: return
        val item = player.currentMediaItem
        val metadata = item?.mediaMetadata

        val previous = _state.value
        _state.value = previous.copy(
            connected = true,
            currentMediaId = item?.mediaId,
            title = metadata?.title?.toString(),
            artist = metadata?.artist?.toString(),
            album = metadata?.albumTitle?.toString(),
            artworkUri = metadata?.artworkUri?.toString(),
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0L } ?: 0L,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            hasNext = player.hasNextMediaItem(),
            hasPrevious = player.hasPreviousMediaItem(),
            isStarred = item?.mediaId?.let { starredById[it] } ?: false,
        )

        _queue.value = (0 until player.mediaItemCount).map { index ->
            val mediaItem = player.getMediaItemAt(index)
            QueueItem(
                mediaId = mediaItem.mediaId,
                title = mediaItem.mediaMetadata.title?.toString().orEmpty(),
                artist = mediaItem.mediaMetadata.artist?.toString().orEmpty(),
                artworkUri = mediaItem.mediaMetadata.artworkUri?.toString(),
                isCurrent = index == player.currentMediaItemIndex,
            )
        }

        maybeScrobble(player)
        maybeSaveQueue(player)
    }

    fun setStarred(id: String, starred: Boolean) {
        starredById[id] = starred
        _state.update { current ->
            if (current.currentMediaId == id) current.copy(isStarred = starred) else current
        }
    }

    fun restoreQueue(config: ServerConfig) {
        if ((controller?.mediaItemCount ?: 0) > 0 || lastSongs.isNotEmpty()) return
        scope.launch {
            val response = runCatching {
                subsonicApi(config).getPlayQueue().response
            }.getOrNull()
            val queue = response?.playQueue
            if (queue == null || queue.entry.isEmpty()) return@launch

            Log.d(TAG, "Restoring play queue: ${queue.entry.size} item(s)")
            lastConfig = config
            lastSongs = queue.entry
            val index = queue.entry.indexOfFirst { it.id == queue.current }
                .takeIf { it >= 0 }
                ?: 0
            lastIndex = index
            fallbackAttempted = false
            lastScrobbledId = null
            scrobbleSubmitted = false

            val items = buildItems(config, queue.entry, format = null)
            val position = queue.position?.coerceAtLeast(0L) ?: 0L
            val action: () -> Unit = {
                controller?.setMediaItems(items, index, position)
                controller?.prepare()
                update()
            }
            if (controller != null) {
                action()
            } else {
                pendingAction = action
                _state.update { it.copy(connectionError = "Connecting to playback service…") }
                if (controllerFuture == null) connect()
            }
        }
    }

    private fun maybeScrobble(player: Player) {
        if (!player.isPlaying) return
        val config = lastConfig ?: return
        val id = player.currentMediaItem?.mediaId ?: return
        if (id != lastScrobbledId) {
            lastScrobbledId = id
            scrobbleSubmitted = false
            submitScrobble(config, id, submission = false)
        }
        if (scrobbleSubmitted) return
        val duration = player.duration
        val position = player.currentPosition
        val half = if (duration > 0) duration / 2 else Long.MAX_VALUE
        if (position >= half || position >= 240_000L) {
            scrobbleSubmitted = true
            submitScrobble(config, id, submission = true)
        }
    }

    private fun submitScrobble(config: ServerConfig, id: String, submission: Boolean) {
        scope.launch {
            runCatching {
                subsonicApi(config).scrobble(
                    id = id,
                    submission = submission,
                    time = System.currentTimeMillis(),
                )
            }.onFailure { Log.w(TAG, "scrobble failed", it) }
        }
    }

    private fun maybeSaveQueue(player: Player) {
        val config = lastConfig ?: return
        if (player.mediaItemCount == 0) return
        val now = System.currentTimeMillis()
        if (now - lastQueueSaveAt < 5_000L) return
        lastQueueSaveAt = now

        val ids = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        val current = player.currentMediaItem?.mediaId ?: return
        val position = player.currentPosition.coerceAtLeast(0L)
        scope.launch {
            runCatching {
                subsonicApi(config).savePlayQueue(ids, current, position)
            }.onFailure { Log.w(TAG, "savePlayQueue failed", it) }
        }
    }

    private fun subsonicApi(config: ServerConfig): SubsonicApi {
        val cached = api
        if (cached != null && apiConfig == config) return cached
        return SubsonicClientFactory.create(config).also {
            api = it
            apiConfig = config
        }
    }

    private fun PlaybackException.isFormatError(): Boolean = when (errorCode) {
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FAILED -> true
        else -> false
    }

    private fun PlaybackException.describe(): String {
        val parts = linkedSetOf<String>()
        var current: Throwable? = this
        while (current != null) {
            current.message?.takeIf { it.isNotBlank() }?.let(parts::add)
            current = current.cause
        }
        return (listOf(errorCodeName) + parts).joinToString(" | ")
    }

    private companion object {
        const val TAG = "JellyfinAudio"
    }
}
