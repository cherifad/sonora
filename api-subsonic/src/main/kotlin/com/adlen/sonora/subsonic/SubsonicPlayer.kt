// Subsonic/OpenSubsonic backend for Sonora. GPL-3.0.
package com.adlen.sonora.subsonic

import android.content.Context
import android.net.Uri as AndroidUri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player as Media3Player
import androidx.media3.exoplayer.ExoPlayer
import com.adlen.sonora.audio.AudioSettings
import com.adlen.sonora.audio.CrossfadeRamp
import com.adlen.sonora.audio.EqualizerProcessor
import com.adlen.sonora.audio.ReplayGainProcessor
import com.adlen.sonora.audio.replayGainLinear
import com.adlen.sonora.api.ItemKind
import com.adlen.sonora.api.NowPlaying
import com.adlen.sonora.api.Player
import com.adlen.sonora.api.PlayerRestrictions
import com.adlen.sonora.api.PlayerState
import com.adlen.sonora.api.Queue
import com.adlen.sonora.api.Repeat
import com.adlen.sonora.api.Track
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
 * Media3 player for Subsonic streams behind the Sonora [Player] contract:
 * - Audio focus, becoming-noisy handling and network wake lock.
 * - Subsonic scrobbling (`nowPlaying` on start, `submission=true` at 50% or 4 min).
 * - Server play-queue persistence (`savePlayQueue` / `getPlayQueue`).
 */
/** The default renderers with the same audio chain as the local player. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun subsonicRenderers(
    context: Context,
    replayGain: ReplayGainProcessor,
): androidx.media3.exoplayer.DefaultRenderersFactory =
    object : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean,
        ): androidx.media3.exoplayer.audio.AudioSink =
            androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf(EqualizerProcessor(), replayGain))
                .build()
    }

/** Long enough to be worth resuming, and not at either edge (where a bookmark means nothing). */
internal fun shouldBookmark(durationMs: Long, positionMs: Long): Boolean =
    durationMs >= BOOKMARK_MIN_MS &&
        positionMs >= BOOKMARK_EDGE_MS &&
        positionMs <= durationMs - BOOKMARK_EDGE_MS

private const val BOOKMARK_MIN_MS = 10 * 60_000L
private const val BOOKMARK_EDGE_MS = 30_000L

/** Shorter overlaps are not worth opening a second stream. */
private const val MIN_FADE_MS = 500L
/** How long the copy gets to open the stream before this song ends without a fade. */
private const val COPY_READY_TIMEOUT_MS = 1_500L
/** 40 ms steps: 25 volume updates a second, smooth enough and cheap. */
private const val RAMP_STEP_MS = 40L

internal class SubsonicPlayer(
    private val context: Context,
    private val client: SubsonicClient,
    private val catalog: SubsonicCatalog,
    private val playlists: SubsonicPlaylists,
) : Player {

    private val replayGain = ReplayGainProcessor()

    private val attributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private val exo = ExoPlayer.Builder(context, subsonicRenderers(context, replayGain))
        .setAudioAttributes(attributes, /* handleAudioFocus = */ true)
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

    // ── Crossfade ──
    // Near the end of a song a second player opens the same stream at the same position and
    // carries the tail (fading out) while the main player moves on to the next item at once
    // (fading in). The queue, the session and the scrobbles only ever see the main player.
    private var outgoing: ExoPlayer? = null
    private var fadeMs = 0L
    private var preparingFade = false
    private var startingFade = false
    /** The item index whose end must not fade (the user sought into its tail, or the fade failed). */
    private var noFadeFor = -1
    /** Identifies the fade in flight, so a seek or a pause can drop the copy still being prepared. */
    private var fadeToken: Any? = null
    /** The user's own volume, which the ramp scales while it runs. */
    private var userVolume = 1f
    private val handler = Handler(Looper.getMainLooper())

    init {
        exo.skipSilenceEnabled = AudioSettings.skipSilence
        exo.addListener(object : Media3Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                applyReplayGain()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // The copy is a second player: a pause (the user's, an audio focus loss, the
                // headset unplugged) must end the fade, or the tail would keep singing alone.
                if (!playWhenReady) endCrossfade()
            }

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
                if (exo.skipSilenceEnabled != AudioSettings.skipSilence) exo.skipSilenceEnabled = AudioSettings.skipSilence
                applyReplayGain()
                publish()
                maybeBeginCrossfade()
                checkScrobbleAndPeriodicSave()
            }
        }
        restoreServerQueueIfIdle()
    }

    /**
     * A long track (a DJ set, an audiobook chapter) remembers where it was paused, so another
     * client can resume it. Short songs do not create bookmark noise.
     */
    private fun saveBookmark() {
        val track = tracks.getOrNull(exo.currentMediaItemIndex) ?: return
        val position = exo.currentPosition
        if (!shouldBookmark(track.durationMs, position)) return
        if (client.capabilities?.supports(ServerCapabilities.Feature.BOOKMARKS) == false) return
        val id = track.uri.substringAfterLast(':').substringBefore('|')
        if (id.isBlank()) return
        scope.launch {
            runCatching { client.call("createBookmark", mapOf("id" to id, "position" to position.toString())) }
                .onSuccess { bookmarkCache = bookmarkCache?.plus(id to position) }
        }
    }

    @Volatile
    private var bookmarkCache: Map<String, Long>? = null

    /** The server's bookmarks, read once per session (or after a config change). */
    private suspend fun bookmarks(): Map<String, Long> {
        bookmarkCache?.let { return it }
        if (client.capabilities?.supports(ServerCapabilities.Feature.BOOKMARKS) == false) return emptyMap()
        val root = client.callOptional("getBookmarks")?.optJSONObject("bookmarks") ?: return emptyMap()
        val map = root.array("bookmark").mapNotNull { bookmark ->
            val id = bookmark.optJSONObject("entry")?.optString("id")?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            id to bookmark.optLong("position", 0L)
        }.toMap()
        bookmarkCache = map
        return map
    }

    private suspend fun maybeResumeBookmark(track: Track) {
        if (track.durationMs < BOOKMARK_MIN_MS) return
        val position = bookmarks()[track.id()] ?: return
        if (!shouldBookmark(track.durationMs, position)) return
        withContext(Dispatchers.Main) {
            if (tracks.getOrNull(exo.currentMediaItemIndex)?.id() == track.id()) {
                exo.seekTo(position)
                publish()
            }
        }
    }

    internal fun invalidateBookmarks() {
        bookmarkCache = null
    }

    /** Track or album gain, capped by the declared peak; 1 when normalization is off. */
    private fun applyReplayGain() {
        val track = tracks.getOrNull(exo.currentMediaItemIndex)
        replayGain.gain = if (AudioSettings.replayGainEnabled) {
            replayGainLinear(track?.replayGain, AudioSettings.replayGainAlbum)
        } else {
            1f
        }
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
            endCrossfade()
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
            // An explicit position wins; otherwise a long track picks up its server bookmark.
            if (positionMs == null) {
                val started = resolved[start]
                scope.launch { maybeResumeBookmark(started) }
            }
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
        endCrossfade()
        saveBookmark()
        exo.pause()
        publish()
        scheduleSavePlayQueue()
    }

    override suspend fun resume() = withContext(Dispatchers.Main) {
        exo.play()
        publish()
    }

    override suspend fun seek(positionMs: Long) = withContext(Dispatchers.Main) {
        endCrossfade()
        val to = positionMs.coerceAtLeast(0L)
        // Jumping into a song's last seconds must not start a fade it never earned.
        if (CrossfadeRamp.shouldStart(AudioSettings.crossfadeSeconds, exo.duration.takeIf { it > 0 } ?: 0L, to)) {
            noFadeFor = exo.currentMediaItemIndex
        }
        exo.seekTo(to)
        publish()
        scheduleSavePlayQueue()
    }

    override suspend fun next() = withContext(Dispatchers.Main) {
        // Mid-fade the main player is already on the incoming song while the ear still hears the
        // one going out: a skip there would go two songs on. It lands on the incoming one.
        if (outgoing != null && CrossfadeRamp.progress(exo.currentPosition, fadeMs) < 0.5f) {
            return@withContext endCrossfade()
        }
        endCrossfade()
        val index = exo.currentMediaItemIndex
        if (index in 0 until tracks.lastIndex) {
            exo.seekTo(index + 1, 0L)
            exo.playWhenReady = true
            publish()
        }
    }

    override suspend fun previous() = withContext(Dispatchers.Main) {
        endCrossfade()
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
        endCrossfade()
        tracks = emptyList()
        exo.clearMediaItems()
        _state.value = null
        _queue.value = null
    }

    override suspend fun skipTo(item: NowPlaying) = withContext(Dispatchers.Main) {
        endCrossfade()
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

    /** The user's own level: during a fade the ramp scales it, [exo] holds the scaled value. */
    override suspend fun volume(): Double = userVolume.toDouble()

    override suspend fun setVolume(level: Double) = withContext(Dispatchers.Main) {
        userVolume = level.toFloat().coerceIn(0f, 1f)
        if (outgoing == null) exo.volume = userVolume
    }

    override suspend fun setSpeed(speed: Double) = withContext(Dispatchers.Main) {
        exo.setPlaybackSpeed(speed.toFloat())
    }

    override suspend fun setLocalMute(on: Boolean) {
        muted = on
    }

    override val localMute: Boolean get() = muted

    // ── Crossfade ──

    /** A player for the outgoing tail: same renderers, no audio focus and no noisy pause of its own. */
    private fun fadeCopy(gain: Float): ExoPlayer {
        val trackGain = ReplayGainProcessor().apply { this.gain = gain }
        return ExoPlayer.Builder(context, subsonicRenderers(context, trackGain))
            .setAudioAttributes(attributes, /* handleAudioFocus = */ false)
            .setHandleAudioBecomingNoisy(false)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    private fun canCrossfade(seconds: Int): Boolean =
        seconds > 0 && !startingFade && !preparingFade && outgoing == null &&
            exo.playWhenReady && exo.isPlaying &&
            // A song on repeat never hands over, and a queue of one has nothing to hand over to.
            exo.repeatMode != Media3Player.REPEAT_MODE_ONE &&
            exo.mediaItemCount > 1 && exo.nextMediaItemIndex != C.INDEX_UNSET &&
            exo.currentMediaItem != null && exo.currentMediaItemIndex != noFadeFor

    private fun maybeBeginCrossfade() {
        val seconds = AudioSettings.crossfadeSeconds
        if (!canCrossfade(seconds)) return
        val duration = exo.duration.takeIf { it > 0 } ?: return
        if (!CrossfadeRamp.shouldStart(seconds, duration, exo.currentPosition)) return
        beginCrossfade(seconds, duration)
    }

    private fun beginCrossfade(seconds: Int, duration: Long) {
        val item = exo.currentMediaItem ?: return
        val index = exo.currentMediaItemIndex
        val remaining = (duration - exo.currentPosition).coerceAtLeast(0)
        val fade = minOf(CrossfadeRamp.fadeMs(seconds, duration), remaining)
        if (fade < MIN_FADE_MS) return
        val token = Any()
        fadeToken = token
        preparingFade = true
        val copy = fadeCopy(replayGain.gain)
        copy.volume = 1f
        // The copy starts a little ahead of the main player: it is heard once it is ready.
        copy.setMediaItem(item, exo.currentPosition + 300)
        copy.addListener(object : Media3Player.Listener {
            private var aligned = false

            override fun onPlaybackStateChanged(state: Int) {
                if (state != Media3Player.STATE_READY) return
                // Ready later than its lead (a stream opened at an offset can take a second):
                // started there, the copy would play again what was just heard. Moved once to
                // where the main player is, inside what it has loaded, then started.
                if (!aligned && fadeToken === token && exo.currentMediaItemIndex == index &&
                    exo.currentPosition + 50 > copy.currentPosition
                ) {
                    aligned = true
                    copy.seekTo(exo.currentPosition + 100)
                    return
                }
                copy.removeListener(this)
                if (fadeToken !== token) return copy.release()
                preparingFade = false
                if (exo.currentMediaItemIndex != index || !exo.playWhenReady || !exo.isPlaying) {
                    fadeToken = null
                    return copy.release()
                }
                // What is left of the song now, not when the copy was asked for: a longer fade
                // would run past the copy's end, the next song never reaching full volume.
                val length = minOf(fade, (exo.duration - exo.currentPosition).coerceAtLeast(0))
                if (length < MIN_FADE_MS) {
                    noFadeFor = index
                    fadeToken = null
                    return copy.release()
                }
                outgoing = copy
                fadeMs = length
                copy.play()
                startingFade = true
                exo.volume = 0f
                exo.seekToNextMediaItem()
                startingFade = false
                rampTick.run()
            }

            override fun onPlayerError(error: PlaybackException) {
                // Could not open the stream: this song ends as usual.
                if (fadeToken === token) {
                    fadeToken = null
                    preparingFade = false
                    noFadeFor = index
                }
                copy.release()
            }
        })
        copy.prepare()
        // Not ready in time: no fade for this song.
        handler.postDelayed({
            if (fadeToken === token && outgoing == null) {
                fadeToken = null
                preparingFade = false
                noFadeFor = index
                copy.release()
            }
        }, COPY_READY_TIMEOUT_MS)
    }

    /** Drives the two gains from the incoming song's own position, as the local player does. */
    private val rampTick = object : Runnable {
        override fun run() {
            handler.removeCallbacks(this)
            val copy = outgoing ?: return
            val t = CrossfadeRamp.progress(exo.currentPosition, fadeMs)
            exo.volume = userVolume * CrossfadeRamp.incomingGain(t)
            copy.volume = CrossfadeRamp.outgoingGain(t)
            if (t >= 1f) endCrossfade() else handler.postDelayed(this, RAMP_STEP_MS)
        }
    }

    /** Ends a fade (done, or interrupted by a seek/skip/pause): the copy goes, the main player is at full volume. */
    private fun endCrossfade() {
        handler.removeCallbacks(rampTick)
        fadeToken = null
        preparingFade = false
        val copy = outgoing
        outgoing = null
        copy?.let { it.stop(); it.release() }
        exo.volume = userVolume
    }

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
