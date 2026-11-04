// Subsonic/OpenSubsonic backend for Myspoti. GPL-3.0.
package app.myspoti.subsonic

import android.content.Context
import android.net.Uri as AndroidUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player as Media3Player
import androidx.media3.exoplayer.ExoPlayer
import app.myspoti.api.ItemKind
import app.myspoti.api.NowPlaying
import app.myspoti.api.Player
import app.myspoti.api.PlayerRestrictions
import app.myspoti.api.PlayerState
import app.myspoti.api.Queue
import app.myspoti.api.Repeat
import app.myspoti.api.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Media3 player for Subsonic streams behind the Myspoti [Player] contract:
 * - Audio focus, becoming-noisy handling and network wake lock.
 * - Subsonic scrobbling (`nowPlaying` on start, `submission=true` at 50% or 4 min).
 * - Server play-queue persistence (`savePlayQueue` / `getPlayQueue`).
 */
internal class SubsonicPlayer(
    context: Context,
    private val client: SubsonicClient,
    private val catalog: SubsonicCatalog,
    private val playlists: SubsonicPlaylists,
) : Player {

    private val exo = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<PlayerState?>(null)
    override val state: StateFlow<PlayerState?> = _state
    private val _queue = MutableStateFlow<Queue?>(null)
    override val queue: StateFlow<Queue?> = _queue

    private var tracks: List<Track> = emptyList()
    private var currentContext: String? = null
    private var currentContextName: String? = null
    private var muted = false

    private var nowPlayingScrobbledId: String? = null
    private var submittedScrobbleId: String? = null
    private var saveQueueJob: Job? = null
    private var lastSavedAtMs = 0L

    init {
        exo.addListener(object : Media3Player.Listener {
            override fun onEvents(player: Media3Player, events: Media3Player.Events) {
                publish()
                if (events.containsAny(
                        Media3Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Media3Player.EVENT_IS_PLAYING_CHANGED,
                    )
                ) {
                    scheduleSavePlayQueue()
                }
            }
        })
        scope.launch {
            while (isActive) {
                delay(500)
                publish()
                checkScrobbleAndPeriodicSave()
            }
        }
        restoreServerQueueIfIdle()
    }

    fun restoreServerQueueIfIdle() {
        if (tracks.isNotEmpty() || client.config.username.isBlank()) return
        scope.launch {
            val pq = runCatching {
                client.call("getPlayQueue").optJSONObject("playQueue")
            }.getOrNull() ?: return@launch
            val restored = pq.array("entry").map { client.trackOf(it) }
            if (restored.isEmpty() || tracks.isNotEmpty()) return@launch
            val currentId = pq.optString("current").takeIf { it.isNotBlank() }
            val posMs = pq.optLong("position", 0L).coerceAtLeast(0L)
            val startIndex = currentId?.let { id -> restored.indexOfFirst { it.id() == id } }
                ?.takeIf { it >= 0 } ?: 0
            tracks = restored
            currentContext = restored.getOrNull(startIndex)?.album?.uri
            currentContextName = restored.getOrNull(startIndex)?.album?.name
            exo.setMediaItems(restored.map { it.toMediaItem() })
            exo.seekTo(startIndex, posMs)
            exo.playWhenReady = false
            exo.prepare()
            publish()
        }
    }

    private fun checkScrobbleAndPeriodicSave() {
        if (tracks.isEmpty() || !exo.isPlaying) return
        val index = exo.currentMediaItemIndex
        val track = tracks.getOrNull(index) ?: return
        val songId = track.id()
        if (songId.isBlank()) return

        if (nowPlayingScrobbledId != songId) {
            nowPlayingScrobbledId = songId
            submittedScrobbleId = null
            scope.launch(Dispatchers.IO) {
                runCatching {
                    client.call("scrobble", mapOf("id" to songId, "submission" to "false"))
                }
            }
        }

        val posMs = exo.currentPosition.coerceAtLeast(0L)
        val durMs = track.durationMs.takeIf { it > 0L } ?: exo.duration.coerceAtLeast(0L)
        val thresholdMs = if (durMs > 0L) (durMs / 2L).coerceAtMost(240_000L).coerceAtLeast(10_000L) else 30_000L
        if (submittedScrobbleId != songId && posMs >= thresholdMs) {
            submittedScrobbleId = songId
            scope.launch(Dispatchers.IO) {
                runCatching {
                    client.call(
                        "scrobble",
                        mapOf(
                            "id" to songId,
                            "submission" to "true",
                            "time" to System.currentTimeMillis().toString(),
                        ),
                    )
                }
            }
        }

        val now = System.currentTimeMillis()
        if (now - lastSavedAtMs >= 15_000L) {
            scheduleSavePlayQueue()
        }
    }

    private fun scheduleSavePlayQueue() {
        if (tracks.isEmpty() || client.config.username.isBlank()) return
        val snapshot = tracks.toList()
        val currentId = snapshot.getOrNull(exo.currentMediaItemIndex)?.id()
        val positionMs = exo.currentPosition.coerceAtLeast(0L)
        saveQueueJob?.cancel()
        saveQueueJob = scope.launch(Dispatchers.IO) {
            delay(1_200L)
            lastSavedAtMs = System.currentTimeMillis()
            val pairs = buildList {
                snapshot.forEach { t ->
                    val id = t.id()
                    if (id.isNotBlank()) add("id" to id)
                }
                if (!currentId.isNullOrBlank()) add("current" to currentId)
                add("position" to positionMs.toString())
            }
            if (pairs.any { it.first == "id" }) {
                runCatching { client.callPairs("savePlayQueue", pairs) }
            }
        }
    }

    override suspend fun play(
        contextUri: String,
        trackUri: String?,
        index: Int?,
        positionMs: Long?,
        order: List<String>?,
    ) {
        val resolved = resolve(contextUri, trackUri, order)
        if (resolved.isEmpty()) return
        withContext(Dispatchers.Main) {
            tracks = resolved
            currentContext = contextUri
            currentContextName = runCatching { catalog.album(contextUri).name }.getOrNull()
                ?: resolved.firstOrNull()?.album?.name
            exo.setMediaItems(resolved.map { it.toMediaItem() })
            val byId = resolved.mapIndexed { i, track -> track.id() to i }.toMap()
            val start = (index ?: trackUri?.let { byId[trackIdOf(it)] } ?: 0).coerceIn(0, resolved.lastIndex)
            exo.seekTo(start, (positionMs ?: 0L).coerceAtLeast(0L))
            exo.prepare()
            exo.playWhenReady = true
            publish()
            scheduleSavePlayQueue()
        }
    }

    private suspend fun resolve(contextUri: String, trackUri: String?, order: List<String>?): List<Track> {
        val base = runCatching {
            when {
                contextUri.startsWith("spotify:album:") -> catalog.albumTracks(contextUri, 0, 500).items
                contextUri.startsWith("spotify:playlist:") -> playlists.tracks(contextUri, 0, 500).items
                contextUri.startsWith("spotify:track:") -> listOf(catalog.track(contextUri))
                trackUri != null -> listOf(catalog.track(trackUri))
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
        if (order.isNullOrEmpty()) return base
        val byId = base.associateBy { it.id() }
        return order.mapNotNull { byId[trackIdOf(it)] }.ifEmpty { base }
    }

    private fun trackIdOf(key: String): String =
        key.substringAfter("spotify:track:", key).substringBefore('|').substringAfterLast(':')

    override suspend fun pause() = withContext(Dispatchers.Main) {
        exo.pause()
        publish()
        scheduleSavePlayQueue()
    }

    override suspend fun resume() = withContext(Dispatchers.Main) {
        exo.play()
        publish()
    }

    override suspend fun seek(positionMs: Long) = withContext(Dispatchers.Main) {
        exo.seekTo(positionMs.coerceAtLeast(0L))
        publish()
        scheduleSavePlayQueue()
    }

    override suspend fun next() = withContext(Dispatchers.Main) {
        val index = exo.currentMediaItemIndex
        if (index in 0 until tracks.lastIndex) {
            exo.seekTo(index + 1, 0L)
            exo.playWhenReady = true
            publish()
        }
    }

    override suspend fun previous() = withContext(Dispatchers.Main) {
        val index = exo.currentMediaItemIndex
        if (exo.currentPosition > 3_000L) {
            exo.seekTo(index, 0L)
            publish()
        } else if (index > 0) {
            exo.seekTo(index - 1, 0L)
            exo.playWhenReady = true
            publish()
        }
    }

    override suspend fun setShuffle(on: Boolean) = withContext(Dispatchers.Main) {
        exo.shuffleModeEnabled = on
        publish()
    }

    override suspend fun setRepeat(mode: Repeat) = withContext(Dispatchers.Main) {
        exo.repeatMode = when (mode) {
            Repeat.TRACK -> Media3Player.REPEAT_MODE_ONE
            Repeat.CONTEXT -> Media3Player.REPEAT_MODE_ALL
            Repeat.OFF -> Media3Player.REPEAT_MODE_OFF
        }
        publish()
    }

    override suspend fun addToQueue(uris: List<String>) {
        val added = uris.mapNotNull { uri -> runCatching { catalog.track(uri) }.getOrNull() }
        if (added.isEmpty()) return
        withContext(Dispatchers.Main) {
            tracks = tracks + added
            exo.addMediaItems(added.map { it.toMediaItem() })
            publish()
            scheduleSavePlayQueue()
        }
    }

    override suspend fun removeFromQueue(items: List<NowPlaying>) = withContext(Dispatchers.Main) {
        items.forEach { item ->
            val index = tracks.indexOfFirst { it.uri == item.uri }
            if (index >= 0) {
                tracks = tracks.toMutableList().also { it.removeAt(index) }
                exo.removeMediaItem(index)
            }
        }
        publish()
        scheduleSavePlayQueue()
    }

    override suspend fun clearQueue() = withContext(Dispatchers.Main) {
        tracks = emptyList()
        exo.clearMediaItems()
        _state.value = null
        _queue.value = null
    }

    override suspend fun skipTo(item: NowPlaying) = withContext(Dispatchers.Main) {
        val index = tracks.indexOfFirst { it.uri == item.uri }
        if (index >= 0) {
            exo.seekTo(index, 0L)
            exo.playWhenReady = true
            publish()
        }
    }

    override suspend fun playNext(uris: List<String>) {
        val added = uris.mapNotNull { uri -> runCatching { catalog.track(uri) }.getOrNull() }
        if (added.isEmpty()) return
        withContext(Dispatchers.Main) {
            val at = (exo.currentMediaItemIndex + 1).coerceIn(0, tracks.size)
            tracks = tracks.toMutableList().also { it.addAll(at, added) }
            exo.addMediaItems(at, added.map { it.toMediaItem() })
            publish()
            scheduleSavePlayQueue()
        }
    }

    override suspend fun insertInQueue(uris: List<String>, before: NowPlaying) {
        val added = uris.mapNotNull { uri -> runCatching { catalog.track(uri) }.getOrNull() }
        val target = tracks.indexOfFirst { it.uri == before.uri }
        if (added.isEmpty() || target < 0) return
        withContext(Dispatchers.Main) {
            tracks = tracks.toMutableList().also { it.addAll(target, added) }
            exo.addMediaItems(target, added.map { it.toMediaItem() })
            publish()
            scheduleSavePlayQueue()
        }
    }

    override suspend fun moveInQueue(item: NowPlaying, target: NowPlaying, after: Boolean) = withContext(Dispatchers.Main) {
        val from = tracks.indexOfFirst { it.uri == item.uri }
        val targetIndex = tracks.indexOfFirst { it.uri == target.uri }
        if (from < 0 || targetIndex < 0) return@withContext
        val to = (targetIndex + if (after) 1 else 0).coerceIn(0, tracks.lastIndex)
        tracks = tracks.toMutableList().also { it.add(to, it.removeAt(from)) }
        exo.moveMediaItem(from, to)
        publish()
        scheduleSavePlayQueue()
    }

    override suspend fun volume(): Double = exo.volume.toDouble()

    override suspend fun setVolume(level: Double) = withContext(Dispatchers.Main) {
        exo.volume = level.toFloat().coerceIn(0f, 1f)
    }

    override suspend fun setSpeed(speed: Double) = withContext(Dispatchers.Main) {
        exo.setPlaybackSpeed(speed.toFloat())
    }

    override suspend fun setLocalMute(on: Boolean) {
        muted = on
    }

    override val localMute: Boolean get() = muted

    private fun publish() {
        if (tracks.isEmpty()) {
            _state.value = null
            _queue.value = null
            return
        }
        val index = exo.currentMediaItemIndex
        if (index !in tracks.indices) return
        val track = tracks[index]
        val item = track.toNowPlaying()
        _state.value = PlayerState(
            item = item,
            contextUri = currentContext,
            contextName = currentContextName,
            previous = tracks.getOrNull(index - 1)?.toNowPlaying(),
            next = tracks.getOrNull(index + 1)?.toNowPlaying(),
            isPaused = !exo.playWhenReady,
            isBuffering = exo.playbackState == Media3Player.STATE_BUFFERING,
            positionMs = exo.currentPosition.coerceAtLeast(0L),
            timestampMs = System.currentTimeMillis(),
            speed = exo.playbackParameters.speed.toDouble(),
            shuffle = exo.shuffleModeEnabled,
            repeat = when (exo.repeatMode) {
                Media3Player.REPEAT_MODE_ONE -> Repeat.TRACK
                Media3Player.REPEAT_MODE_ALL -> Repeat.CONTEXT
                else -> Repeat.OFF
            },
            restrictions = PlayerRestrictions(),
        )
        _queue.value = Queue(
            current = item,
            queued = tracks.drop(index + 1).map { it.toNowPlaying() },
            nextUp = emptyList(),
        )
    }

    private fun Track.toMediaItem(): MediaItem {
        val artwork = album?.images?.firstOrNull()?.url
        val metadata = MediaMetadata.Builder()
            .setTitle(name)
            .setArtist(artists.joinToString(", ") { it.name })
            .setAlbumTitle(album?.name)
            .apply { artwork?.let { setArtworkUri(AndroidUri.parse(it)) } }
            .build()
        return MediaItem.Builder()
            .setMediaId(uri)
            .setUri(client.streamUrl(id()))
            .setMediaMetadata(metadata)
            .build()
    }

    private fun Track.toNowPlaying(): NowPlaying = NowPlaying(
        kind = ItemKind.TRACK,
        uri = uri,
        uid = uid,
        name = name,
        artists = artists,
        album = album,
        imageUrl = album?.images?.firstOrNull()?.url,
        durationMs = durationMs,
    )

    private fun Track.id(): String = uri.substringAfterLast(':')
}
