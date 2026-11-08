package com.adlen.sonora.app.data.offline

import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.adlen.sonora.app.data.entity.SongsModel
import com.adlen.sonora.app.data.preferences.isDownloadsEnabled
import com.adlen.sonora.app.di.Graph
import com.adlen.sonora.app.video.InterruptibleHttp
import com.adlen.sonora.app.video.YouTubeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import com.adlen.sonora.app.extensions.MediaHttp
import java.util.concurrent.ConcurrentHashMap

/**
 * Downloads songs for offline listening. Spotify only gives the track (title, artist,
 * duration, ISRC); where the audio file is, and that it is the right recording, is the job
 * of a `download-source` extension (docs/extensions/API.md §3), which answers the files it
 * has, best first. Sonora then:
 *
 * 1. fetches the first file that works (a FLAC, a DASH manifest of FLAC frames joined
 *    into one FLAC, or an M4A / MP3);
 * 2. checks what any file must pass, whoever sent it: real, decodable audio whose length
 *    is the song's (Spotify's duration), so a preview or another cut is refused;
 * 3. keeps it encrypted ([OfflineCrypto]), with its cover, in the Downloaded list.
 *
 * No extension giving download files: downloads say to install one. Nothing that fails the
 * check is kept: the song is marked unavailable instead.
 */
object OfflineDownloader {
    private const val TAG = "Offline"
    private const val WORKERS = 3
    /** A file may be this much shorter than the song (seconds): more is a preview or a cut. */
    private const val SHORTER_MAX = 5
    /** And this much longer (trailing silence, a longer intro): more is another version. */
    private const val LONGER_MAX = 15

    data class Request(
        val trackId: String,
        val title: String,
        val artist: String,
        val album: String,
        val albumId: String,
        val durationMs: Long,
        val coverUrl: String,
        /** The quality chosen for this download ([DownloadQuality.name]); empty = the setting. */
        val quality: String = "",
    ) {
        fun toJson(): JSONObject = JSONObject().put("id", trackId).put("title", title).put("artist", artist)
            .put("album", album).put("albumId", albumId).put("duration", durationMs).put("cover", coverUrl)
            .put("quality", quality)

        companion object {
            fun of(s: SongsModel) = Request(
                trackId = s.spotifyTrackId, title = s.title, artist = s.singer, album = s.album,
                albumId = s.albumId, durationMs = s.durationMs.toLong(), coverUrl = s.coverUri,
            )
            fun fromJson(o: JSONObject) = Request(
                o.getString("id"), o.optString("title"), o.optString("artist"), o.optString("album"),
                o.optString("albumId"), o.optLong("duration"), o.optString("cover"), o.optString("quality"),
            )
        }
    }

    private class Unavailable(message: String) : Exception(message)

private class StorageFull(message: String) : Exception(message)

/**
 * Whether another download fits: under the user's limit, and leaving the phone its reserve.
 * 0 means "no limit" for either bound.
 */
fun withinDownloadBudget(
    usedBytes: Long,
    freeBytes: Long,
    maxBytes: Long,
    keepFreeBytes: Long,
): Boolean = (maxBytes <= 0 || usedBytes < maxBytes) &&
    (keepFreeBytes <= 0 || freeBytes > keepFreeBytes)

    /** Two AAC encodes at once at most: the CPU stays free for the rest of the app. */
    private val encodeSlots = kotlinx.coroutines.sync.Semaphore(2)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Songs queued, downloading, or whose download did not succeed (for the Downloaded list). */
    val requests: SnapshotStateMap<String, Request> = mutableStateMapOf()
    private val queue = Channel<Request>(Channel.UNLIMITED)
    /** Requests not finished yet, in order (persisted: a restart resumes them). */
    private val pending = LinkedHashMap<String, Request>()
    private val running = ConcurrentHashMap<String, Job>()
    @Volatile private var started = false

    private fun start() {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
            repeat(WORKERS) { scope.launch { for (r in queue) work(r) } }
        }
    }

    /** Re-queues what was waiting when the app last stopped. */
    fun resumePending() {
        // Read off the main thread (the app is already drawing): the queue fills a moment later.
        scope.launch { resumePendingNow() }
    }

    /** The saved queue read and enqueued. */
    internal suspend fun resumePendingNow() {
        val saved = runCatching {
            val a = JSONArray(OfflineStore.pendingFile.readText())
            (0 until a.length()).map { Request.fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        if (saved.isNotEmpty()) enqueueAll(saved)
    }

    /**
     * Resumes the saved queue and waits until it is empty (WorkManager's worker). [onProgress]
     * gets the requests left and the average progress (-1 while unknown).
     */
    suspend fun awaitPending(onProgress: (remaining: Int, progress: Float) -> Unit) {
        resumePendingNow()
        while (true) {
            val remaining = synchronized(pending) { pending.size }
            if (remaining == 0) return
            val states = synchronized(pending) { pending.keys.toList() }.mapNotNull { OfflineStore.status[it] }
            val known = states.mapNotNull { (it as? OfflineStatus.Downloading)?.progress?.takeIf { p -> p >= 0f } }
            onProgress(remaining, if (known.isEmpty()) -1f else known.average().toFloat())
            delay(1_000)
        }
    }

    /**
     * Downloads [songs] in [quality] (asked when the setting is "ask each time"), else in
     * the quality chosen in Settings.
     */
    /**
     * Whether a download can start now, said in a toast when not: downloads switched off in
     * Settings (instead of a list of failed songs), or no extension giving download files (where to
     * get one, for a download the user asked for). A list is only marked downloaded when this holds.
     */
    fun ready(auto: Boolean = false): Boolean {
        if (!isDownloadsEnabled(Graph.app)) {
            scope.launch(Dispatchers.Main) {
                android.widget.Toast.makeText(Graph.app, com.adlen.sonora.engine.R.string.downloads_off_toast, android.widget.Toast.LENGTH_LONG).show()
            }
            return false
        }
        // Subsonic downloads come from the server itself: no download-source extension needed.
        if (com.adlen.sonora.host.android.SonoraRuntime.isSubsonic) return true
        if (!com.adlen.sonora.app.extensions.Extensions.hasDownloadSource()) {
            if (!auto) scope.launch(Dispatchers.Main) {
                android.widget.Toast.makeText(Graph.app, com.adlen.sonora.engine.R.string.downloads_need_source, android.widget.Toast.LENGTH_LONG).show()
            }
            return false
        }
        return true
    }

    fun enqueue(
        songs: List<SongsModel>,
        quality: com.adlen.sonora.app.data.preferences.DownloadQuality? = null,
        /** Started by the app (a downloaded list keeping in step), not by a tap. */
        auto: Boolean = false,
    ) {
        if (!ready(auto)) return
        val wifiOnly = com.adlen.sonora.app.data.preferences.isWifiOnly(Graph.app)
        if ((auto || wifiOnly) && (com.adlen.sonora.app.data.DataSaver.metered || !OfflineStore.isOnline(Graph.app))) {
            // Automatic downloads always wait for Wi-Fi; a user download does too when asked.
            OfflineCollections.waitForWifi()
            if (!auto) scope.launch(Dispatchers.Main) {
                android.widget.Toast.makeText(Graph.app, com.adlen.sonora.engine.R.string.download_waiting_wifi, android.widget.Toast.LENGTH_LONG).show()
            }
            return
        }
        if (!auto && com.adlen.sonora.app.data.DataSaver.metered) {
            // Asked for by the user on mobile data: it goes ahead, but they are told.
            scope.launch(Dispatchers.Main) {
                android.widget.Toast.makeText(Graph.app, com.adlen.sonora.engine.R.string.download_on_mobile_data, android.widget.Toast.LENGTH_LONG).show()
            }
        }
        val q = quality?.let { com.adlen.sonora.app.data.preferences.DownloadQuality.saved(it.name) }?.name.orEmpty()
        // Episodes go to PodcastDownloader: their id is no track id, so the download-source lookup
        // would 404 (or worse, match an unrelated recording by title).
        enqueueAll(songs.filter { it.spotifyTrackId.isNotBlank() && !isEpisodeKey(it.spotifyTrackId) }.map { Request.of(it).copy(quality = q) })
    }

    private fun enqueueAll(list: List<Request>) {
        val added = synchronized(pending) {
            list.filter { r ->
                // Last guard before the track download path, whatever the caller (resume, retry, list).
                !isEpisodeKey(r.trackId) &&
                    !OfflineStore.isDownloaded(r.trackId) && pending.putIfAbsent(r.trackId, r) == null
            }.also { if (it.isNotEmpty()) savePending() }
        }
        if (added.isEmpty()) return
        scope.launch(Dispatchers.Main) {
            added.forEach { requests[it.trackId] = it; OfflineStore.status[it.trackId] = OfflineStatus.Queued }
        }
        start()
        added.forEach { queue.trySend(it) }
        // Keep going with the app in the background (or killed): the worker resumes the queue
        // and shows its progress in a notification.
        DownloadsWork.enqueue(Graph.app)
    }

    /**
     * The account's downloads are put away (sign-out, another account signing in): the queue is
     * dropped from memory only (its file goes with the account's folder) and the running downloads
     * stop, waited for a moment so none finishes into the next account's folder.
     */
    internal suspend fun forgetSession() {
        synchronized(pending) { pending.clear() }
        val jobs = running.values.toList()
        running.clear()
        jobs.forEach { it.cancel() }
        kotlinx.coroutines.withTimeoutOrNull(5_000) { jobs.forEach { it.join() } }
        withContext(Dispatchers.Main) { requests.clear() }
    }

    /** Stops a queued or running download (and forgets a failed one). */
    fun cancel(trackId: String) {
        synchronized(pending) { if (pending.remove(trackId) != null) savePending() }
        running.remove(trackId)?.cancel()
        OfflineStore.status.remove(trackId)
        requests.remove(trackId)
    }

    /**
     * Stops every running and queued download and empties the queue. Unlike [removeAll], it
     * deletes nothing: finished songs stay in [OfflineStore.tracks] and in the index, and a
     * cancelled download only drops its own partial file in the temp folder.
     */
    fun stopAll() {
        // The queue survives a restart through pending.json: clearing it here is what makes the
        // stop stick, instead of the downloads resuming at the next launch.
        val ids = synchronized(pending) { pending.keys.toList().also { pending.clear(); savePending() } }
        // Running jobs may not all be in `pending` any more; cancel them by their own handle.
        running.values.forEach { it.cancel() }
        running.clear()
        // Snapshot state: the badges and the list are read from the UI, so write them there.
        scope.launch(Dispatchers.Main) {
            ids.forEach { OfflineStore.status.remove(it); requests.remove(it) }
        }
    }

    /** Tries a failed song again. */
    fun retry(trackId: String) {
        val r = requests[trackId] ?: return
        enqueueAll(listOf(r))
    }

    /** Removes the song: the file if downloaded, else its download. */
    fun remove(trackId: String) {
        cancel(trackId)
        // A removed song takes its resumable partial with it.
        val name = safeFileName(trackId)
        File(OfflineStore.tmpDir, "$name.part").delete()
        File(OfflineStore.tmpDir, "$name.part.src").delete()
        // File deletion on the IO scope, not the (UI) caller's thread.
        scope.launch { OfflineStore.remove(trackId) }
    }

    fun removeAll() {
        val ids = synchronized(pending) { pending.keys.toList().also { pending.clear(); savePending() } }
        ids.forEach { running.remove(it)?.cancel() }
        running.values.forEach { it.cancel() }
        requests.clear()
        // Deleting a large library can take a while: IO scope, not the caller.
        scope.launch { OfflineCollections.removeAll(); OfflineStore.removeAll() }
    }

    private fun savePending() {
        runCatching {
            val a = JSONArray()
            pending.values.forEach { a.put(it.toJson()) }
            com.adlen.sonora.app.data.writeTextSafely(OfflineStore.pendingFile, a.toString())
        }
    }

    private suspend fun setStatus(id: String, s: OfflineStatus?) = withContext(Dispatchers.Main) {
        if (s == null) OfflineStore.status.remove(id) else OfflineStore.status[id] = s
    }

    private suspend fun work(r: Request) {
        // Cancelled while it waited in the queue.
        if (synchronized(pending) { !pending.containsKey(r.trackId) }) return
        // Data saver on mobile data: downloads wait for Wi-Fi.
        while (com.adlen.sonora.app.data.DataSaver.active) {
            kotlinx.coroutines.delay(5_000)
            if (synchronized(pending) { !pending.containsKey(r.trackId) }) return
        }
        val job = scope.launch {
            val outcome: OfflineStatus? = try {
                setStatus(r.trackId, OfflineStatus.Downloading(-1f))
                checkBudget()
                val t = retrying(r)
                // Already on the IO scope: the index rewrite stays off the main thread.
                OfflineStore.add(t)
                withContext(Dispatchers.Main) { requests.remove(r.trackId) }
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: StorageFull) {
                Log.i(TAG, "${r.title}: ${e.message}")
                scope.launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        Graph.app,
                        com.adlen.sonora.engine.R.string.download_storage_full,
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
                OfflineStatus.Failed
            } catch (e: Unavailable) {
                Log.i(TAG, "${r.title}: ${e.message}")
                OfflineStatus.Unavailable
            } catch (e: com.adlen.sonora.app.extensions.Extensions.NotAnswering) {
                // Failed, not unavailable: the source could not answer, so a tap on the song tries it again.
                Log.i(TAG, "${r.title}: download source not answering: ${e.message}")
                OfflineStatus.Failed
            } catch (t: Throwable) {
                Log.w(TAG, "${r.title} failed", t)
                OfflineStatus.Failed
            }
            synchronized(pending) { pending.remove(r.trackId); savePending() }
            if (outcome != null) setStatus(r.trackId, outcome)
        }
        running[r.trackId] = job
        job.join()
        running.remove(r.trackId, job)
    }

    private fun checkBudget() {
        val context = Graph.app
        val used = OfflineStore.totalBytes()
        val max = com.adlen.sonora.app.data.preferences.maxDownloadBytes(context)
        val free = context.filesDir.usableSpace
        val keep = com.adlen.sonora.app.data.preferences.keepFreeBytes(context)
        if (!withinDownloadBudget(used, free, max, keep)) {
            throw StorageFull(if (max > 0 && used >= max) "download limit reached" else "not enough free space")
        }
    }

    /** A transient failure gets two more tries, with a short backoff. */
    private suspend fun retrying(r: Request): OfflineTrack {
        var last: Throwable? = null
        repeat(3) { attempt ->
            try {
                return download(r)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                last = e
                if (attempt < 2) kotlinx.coroutines.delay(1_000L * (attempt + 1))
            }
        }
        throw last ?: IOException("download failed")
    }

    private suspend fun download(r: Request): OfflineTrack {
        if (!isDownloadsEnabled(Graph.app)) throw Unavailable("downloads are off")
        // Subsonic: the server itself serves the file (original or transcoded), no extension.
        if (com.adlen.sonora.host.android.SonoraRuntime.isSubsonic) return downloadSubsonic(r)
        val quality = qualityOf(r)
        val tmp = OfflineStore.tmpDir
        val audioTmp = File(tmp, "${r.trackId}.part")
        try {
            val meta = spotifyMeta(r)
            Log.i(TAG, "${meta.title}: isrc=${meta.isrc ?: "-"}")

            // ── The files the download-source extensions give, tried in their order; each one
            // is checked (real audio, the song's length) before it is taken.
            val file = resolveAndFetch(r, meta, audioTmp, quality)
            currentCoroutineContext().ensureActive()

            val out: File
            if (file.lossy != null) {
                // A small file as the source served it (M4A, MP3): kept as it is.
                out = File(OfflineStore.audioDir, "${r.trackId}.${file.lossy}")
                if (!audioTmp.renameTo(out)) throw IOException("could not move the file")
            } else if (quality == com.adlen.sonora.app.data.preferences.DownloadQuality.SMALL) {
                // "Smallest": the checked FLAC becomes AAC, encoded on the phone.
                out = File(OfflineStore.audioDir, "${r.trackId}.m4a")
                val aacTmp = File(OfflineStore.tmpDir, "${r.trackId}.m4apart")
                try {
                    // Encoding is long: a few at a time, the other downloads go on meanwhile.
                    encodeSlots.withPermit { runInterruptible(Dispatchers.Default) { AacTranscoder.transcode(audioTmp, aacTmp) } }
                    if (aacTmp.length() < 10_000 || !aacTmp.renameTo(out)) throw IOException("could not encode the small file")
                } finally {
                    aacTmp.delete()
                }
            } else {
                out = File(OfflineStore.audioDir, "${r.trackId}.flac")
                if (!audioTmp.renameTo(out)) throw IOException("could not move the file")
            }
            // The audio is complete on disk from here on: "stop all" must not interrupt this last
            // part, or the file would stay out of the index and be swept as a stray at startup.
            return withContext(NonCancellable) {
                // Kept encrypted: the file only plays in Sonora, on this phone.
                runInterruptible(Dispatchers.IO) { OfflineCrypto.encryptInPlace(Graph.app, out) }
                val cover = runCatching {
                    val c = File(OfflineStore.coverDir, "${r.trackId}.jpg")
                    httpGet(com.adlen.sonora.host.android.Covers.large(meta.coverUrl), c); c.name
                }.getOrDefault("")
                OfflineTrack(
                    trackId = r.trackId, title = meta.title, artist = meta.artist, album = meta.album,
                    albumId = meta.albumId, durationMs = meta.durationMs, coverUrl = meta.coverUrl,
                    file = out.name, cover = cover, source = file.provider, verified = file.isrcMatched,
                    bytes = out.length(), addedAt = System.currentTimeMillis(), providerId = file.extensionId,
                    extensionQuality = com.adlen.sonora.app.extensions.Extensions.qualityLine(file.extensionId, com.adlen.sonora.extensions.SettingSection.DOWNLOADS),
                )
            }
        } finally {
            audioTmp.delete()
        }
    }

    /**
     * The Subsonic download: the server's own `stream` endpoint (original file, or transcoded to
     * the chosen quality), kept encrypted like every other download.
     */
    private suspend fun downloadSubsonic(r: Request): OfflineTrack {
        val api = com.adlen.sonora.host.android.SonoraRuntime.subsonicApi
            ?: throw Unavailable("no Subsonic server configured")
        val quality = qualityOf(r)
        val name = safeFileName(r.trackId)
        val tmp = File(OfflineStore.tmpDir, "$name.part")
        val sourceFile = File(OfflineStore.tmpDir, "$name.part.src")
        try {
            setStatus(r.trackId, OfflineStatus.Downloading(-1f))
            // The quality asked for first, then the original file: a server without a
            // transcoder (or one that refuses the format) still gives its own audio.
            val urls = runCatching {
                listOf(api.downloadUrl(r.trackId, quality.name), api.downloadUrl(r.trackId, null)).distinct()
            }.getOrElse { throw Unavailable("no download URL: ${it.message}") }
            var fetched = false
            var asOriginal = false
            var lastError = "the server sent no audio"
            for ((index, url) in urls.withIndex()) {
                // A retry of the same request resumes its partial; another quality starts over.
                if (index > 0) {
                    tmp.delete()
                    sourceFile.delete()
                }
                if (sourceFile.exists() && sourceFile.readText() != url) {
                    tmp.delete()
                    sourceFile.delete()
                }
                sourceFile.writeText(url)
                val ok = runCatching { fetchServerFile(r.trackId, url, tmp) }.getOrElse { e ->
                    lastError = e.message ?: "the server did not send the file"
                    false
                }
                if (!ok || tmp.length() < 10_000) {
                    lastError = if (ok) "the server sent no audio (${tmp.length()} bytes)" else lastError
                    continue
                }
                val audio = runCatching { checkServerAudio(tmp); true }.getOrElse { e ->
                    lastError = e.message ?: "the server did not send an audio file"
                    false
                }
                if (audio) { fetched = true; asOriginal = index > 0; break }
            }
            if (!fetched) throw Unavailable(lastError)
            currentCoroutineContext().ensureActive()
            val out = File(OfflineStore.audioDir, "$name.${sniffAudioExtension(tmp)}")
            if (!tmp.renameTo(out)) throw IOException("could not move the file")
            // The audio is complete on disk: "stop all" must not leave it out of the index.
            return withContext(NonCancellable) {
                runInterruptible(Dispatchers.IO) { OfflineCrypto.encryptInPlace(Graph.app, out) }
                val coverUrl = api.coverUrl(r.trackId, 600) ?: r.coverUrl.takeIf { it.isNotBlank() }
                val cover = runCatching {
                    val c = File(OfflineStore.coverDir, "$name.jpg")
                    if (coverUrl == null) "" else { httpGet(coverUrl, c); c.name }
                }.getOrDefault("")
                OfflineTrack(
                    trackId = r.trackId, title = r.title, artist = r.artist, album = r.album,
                    albumId = r.albumId, durationMs = r.durationMs, coverUrl = coverUrl.orEmpty(),
                    file = out.name, cover = cover, source = "Subsonic", verified = true,
                    bytes = out.length(), addedAt = System.currentTimeMillis(),
                    providerId = null, extensionQuality = if (asOriginal) "Original" else qualityLabel(quality),
                )
            }
        } finally {
            // A complete file was moved out; a failed attempt keeps its partial for a retry.
            if (!tmp.exists()) sourceFile.delete()
        }
    }

    /**
     * The server file streamed straight to [out], reporting progress while it goes. A plain
     * OkHttp call, not the extensions' safe HTTP: the Subsonic server is the user's own,
     * often on the LAN or localhost (adb reverse), which that client refuses as a private
     * address.
     */
    private suspend fun fetchServerFile(trackId: String, url: String, out: File): Boolean {
        val copied = java.util.concurrent.atomic.AtomicLong(0)
        val total = java.util.concurrent.atomic.AtomicLong(-1)
        val watcher = scope.launch {
            while (isActive) {
                val done = copied.get()
                val all = total.get()
                setStatus(
                    trackId,
                    OfflineStatus.Downloading(if (all > 0) (done.toFloat() / all).coerceIn(0f, 1f) else -1f),
                )
                delay(400)
            }
        }
        try {
            return runInterruptible(Dispatchers.IO) {
                val start = if (out.exists()) out.length() else 0L
                val request = okhttp3.Request.Builder().url(url).apply {
                    if (start > 0) header("Range", "bytes=$start-")
                }.build()
                serverHttp().newCall(request).execute().use { res ->
                    if (res.code == 416) {
                        // The partial is not a prefix the server can continue: start over.
                        out.delete()
                        throw IOException("range not satisfiable")
                    }
                    if (!res.isSuccessful) throw IOException("stream HTTP ${res.code}")
                    val body = res.body ?: throw IOException("empty body")
                    // 206 continues the partial; 200 (a server without Range) starts over.
                    val append = start > 0 && res.code == 206
                    if (!append && start > 0) out.delete()
                    val already = if (append) start else 0L
                    copied.set(already)
                    total.set(if (body.contentLength() > 0) already + body.contentLength() else -1)
                    java.io.FileOutputStream(out, append).use { output ->
                        body.byteStream().use { input ->
                            val buf = ByteArray(256 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                copied.addAndGet(n.toLong())
                            }
                        }
                    }
                }
                true
            }
        } finally {
            watcher.cancel()
            setStatus(trackId, OfflineStatus.Downloading(-1f))
        }
    }

    @Volatile private var serverHttpKey: String? = null
    @Volatile private var serverHttpClient: okhttp3.OkHttpClient? = null
    private val plainHttp by lazy { okhttp3.OkHttpClient.Builder().build() }

    /** The server's own HTTP client, rebuilt when the configured URL changes (TOFU TLS). */
    private fun serverHttp(): okhttp3.OkHttpClient {
        val config = com.adlen.sonora.host.android.SonoraRuntime.subsonicApi?.config ?: return plainHttp
        if (serverHttpKey != config.baseUrl) {
            serverHttpKey = config.baseUrl
            serverHttpClient = com.adlen.sonora.subsonic.SubsonicTls.client(Graph.app, config)
        }
        return serverHttpClient ?: plainHttp
    }

    /** A server file the phone's own extractor must read as audio (a JSON/XML error page would not). */
    private fun checkServerAudio(file: File) {
        val extractor = android.media.MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val audio = (0 until extractor.trackCount).any {
                extractor.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            if (!audio) throw Unavailable("the server did not send an audio file")
        } catch (e: IOException) {
            throw Unavailable("the file could not be read: ${e.message}")
        } finally {
            extractor.release()
        }
    }

    /** The audio container from the file's first bytes (Subsonic does not say what it transcoded to). */
    private fun sniffAudioExtension(file: File): String {
        val head = ByteArray(12)
        val n = runCatching { file.inputStream().use { it.read(head) } }.getOrDefault(0)
        fun ascii(at: Int, len: Int) = String(head, at, len, Charsets.US_ASCII)
        return when {
            n >= 4 && ascii(0, 4) == "fLaC" -> "flac"
            n >= 4 && ascii(0, 4) == "OggS" -> "ogg"
            n >= 8 && ascii(4, 4) == "ftyp" -> "m4a"
            n >= 4 && ascii(0, 4) == "RIFF" -> "wav"
            n >= 2 && head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() -> "mp3"
            n >= 2 && (head[0].toInt() and 0xFF) == 0xFF && (head[1].toInt() and 0xE0) == 0xE0 -> "mp3"
            else -> "mp3"
        }
    }

    private fun qualityLabel(q: com.adlen.sonora.app.data.preferences.DownloadQuality): String = when (q) {
        com.adlen.sonora.app.data.preferences.DownloadQuality.SMALL -> "MP3 128 kbps"
        com.adlen.sonora.app.data.preferences.DownloadQuality.CD -> "≤ 320 kbps"
        else -> "Original"
    }

    private fun safeFileName(id: String): String =
        id.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "track" }

    private class Fetched(
        val provider: String,
        val isrcMatched: Boolean,
        /** The id of the download-source extension that supplied it. */
        val extensionId: String,
        /** The extension of a lossy file kept as served (m4a, mp3); null for a FLAC. */
        val lossy: String? = null,
    )

    /** This download's quality: the one chosen when it started, else Settings (CD when Settings says "ask"). */
    private fun qualityOf(r: Request): com.adlen.sonora.app.data.preferences.DownloadQuality =
        com.adlen.sonora.app.data.preferences.DownloadQuality.saved(r.quality)
            ?: com.adlen.sonora.app.data.preferences.DownloadQuality.saved(com.adlen.sonora.app.data.preferences.getDownloadQuality(Graph.app).name)
            ?: com.adlen.sonora.app.data.preferences.DownloadQuality.CD

    /** The quality as a download-source extension is asked for it. */
    internal fun queryQuality(q: com.adlen.sonora.app.data.preferences.DownloadQuality): String = when (q) {
        com.adlen.sonora.app.data.preferences.DownloadQuality.HI_RES -> "hi-res"
        com.adlen.sonora.app.data.preferences.DownloadQuality.SMALL -> "small"
        else -> "cd"
    }

    /**
     * Asks the download-source extensions for [meta]'s files and saves the first one that works
     * into [out]. Unavailable when no extension has a file, or none of them could be used.
     */
    private suspend fun resolveAndFetch(r: Request, meta: Meta, out: File, quality: com.adlen.sonora.app.data.preferences.DownloadQuality): Fetched {
        val track = com.adlen.sonora.app.extensions.Extensions.trackInfo(r.trackId, meta.title, meta.artist, meta.album, meta.durationMs, meta.coverUrl)
            .copy(isrc = meta.isrc)
        val (extensionId, extension, files) = com.adlen.sonora.app.extensions.Extensions.downloadFiles(track, queryQuality(quality))
            ?: throw Unavailable("no download source has this song")
        // The last error of a file that could not be fetched (not refused or failed a check): the song is then
        // a download that failed and can be tried again, not a recording that was not found.
        var fetchError: Exception? = null
        for (file in files) {
            currentCoroutineContext().ensureActive()
            setStatus(r.trackId, OfflineStatus.Downloading(-1f))
            val label = listOfNotNull(file.provider ?: extension, file.bitDepth?.takeIf { file.lossless }?.let { "$it-bit" }).joinToString(" ")
            val ok = try {
                when {
                    file.format == "dash-flac" -> runInterruptible(Dispatchers.IO) {
                        DashFlacRemux.download("data:application/dash+xml;base64,${file.manifest}", out, file.headers)
                    }
                    else -> fetchWithProgress(r.trackId, file.url!!, out, file.headers)
                } && out.length() >= 10_000 && runInterruptible(Dispatchers.IO) {
                    file.transform?.let { spec ->
                        val staged = File.createTempFile("download-transform-", ".partial", out.parentFile)
                        try {
                            out.inputStream().use { input -> staged.outputStream().use { output ->
                                com.adlen.sonora.extensions.PatternedBlowfish.transform(input, output, spec)
                            } }
                            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException("transform cancelled")
                            java.nio.file.Files.move(staged.toPath(), out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        } finally { staged.delete() }
                    }
                    true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "${meta.title}: $label ${file.format}: ${e.message}")
                if (e !is com.adlen.sonora.extensions.ExtensionMediaHttp.Refused && !com.adlen.sonora.extensions.SafeNet.isRefusal(e)) fetchError = e
                false
            }
            if (!ok) continue
            val refused = runCatching {
                if (file.lossless) checkFlacHeader(out, meta, file.bitDepth) else checkAudio(out, meta)
            }.exceptionOrNull()
            if (refused != null) { Log.i(TAG, "${meta.title}: $label: ${refused.message}"); continue }
            if (!file.lossless) {
                Log.i(TAG, "${meta.title}: saved $label ${file.format}")
                return Fetched(label, file.isrcMatched, extensionId, lossy = file.format)
            }
            Log.i(TAG, "${meta.title}: saved FLAC from $label")
            return Fetched(label, file.isrcMatched, extensionId)
        }
        fetchError?.let { throw IOException("none of the ${files.size} files from $extension could be fetched: ${it.message}", it) }
        throw Unavailable("none of the ${files.size} files from $extension could be used")
    }

    /**
     * A STREAMINFO read on the saved file: anything that is not a FLAC file (an encrypted or
     * AAC stream) is refused, a preview or another cut is caught (the header states the real
     * length), and a misplaced 24-bit label shows up in the log.
     */
    private fun checkFlacHeader(file: File, meta: Meta, bitDepth: Int?) {
        val seconds = runCatching {
            file.inputStream().use { s ->
                val head = ByteArray(42); var off = 0
                while (off < head.size) { val n = s.read(head, off, head.size - off); if (n < 0) break; off += n }
                flacDurationSec(head)
            }
        }.getOrNull() ?: throw Unavailable("the source did not send a FLAC file")
        checkLength(seconds.toLong(), meta)
        Log.d(TAG, "${meta.title}: FLAC ${seconds}s header check, labelled ${bitDepth ?: "-"}-bit")
    }

    /** An M4A / MP3 as served: the phone's own extractor must find an audio track of the song's length. */
    private fun checkAudio(file: File, meta: Meta) {
        val extractor = android.media.MediaExtractor()
        val seconds = try {
            extractor.setDataSource(file.path)
            val format = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
                .firstOrNull { it.getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: throw Unavailable("the source did not send an audio file")
            if (format.containsKey(android.media.MediaFormat.KEY_DURATION)) format.getLong(android.media.MediaFormat.KEY_DURATION) / 1_000_000 else -1L
        } catch (e: IOException) {
            throw Unavailable("the file could not be read: ${e.message}")
        } finally {
            extractor.release()
        }
        // No length in the track's header (some MP3 streams): the file's own metadata is asked next.
        val known = seconds.takeIf { it > 0 } ?: runCatching {
            android.media.MediaMetadataRetriever().let { r ->
                try {
                    r.setDataSource(file.path)
                    r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.div(1000)
                } finally { r.release() }
            }
        }.getOrNull()?.takeIf { it > 0 }
        if (known != null) checkLength(known, meta)
        else Log.w(TAG, "${meta.title}: length of the file unknown, not compared with the song's")
    }

    private fun checkLength(seconds: Long, meta: Meta) {
        lengthMismatch(seconds, meta.durationMs)?.let { throw Unavailable("backed out: $it") }
    }

    private data class Meta(
        val title: String, val artist: String, val album: String, val albumId: String,
        val durationMs: Long, val coverUrl: String, val isrc: String?,
    )

    /** The request, completed by Spotify's track (ISRC, and anything the list lacked). */
    private suspend fun spotifyMeta(r: Request): Meta {
        val api = com.adlen.sonora.host.android.SonoraRuntime.api
        val uri = "spotify:track:${r.trackId}"
        val t = runCatching { api.catalog.track(uri) }.onFailure { Log.w(TAG, "${r.title}: Spotify track: $it") }.getOrNull()
        val isrc = runCatching { api.catalog.isrc(uri) }.onFailure { Log.w(TAG, "${r.title}: ISRC: $it") }.getOrNull()
        val cover = r.coverUrl.ifBlank { t?.album?.images?.maxByOrNull { it.width ?: 0 }?.url.orEmpty() }
        return Meta(
            title = r.title.ifBlank { t?.name.orEmpty() },
            artist = r.artist.ifBlank { t?.artists?.joinToString(", ") { it.name }.orEmpty() },
            album = r.album.ifBlank { t?.album?.name.orEmpty() },
            albumId = r.albumId.ifBlank { t?.album?.uri?.substringAfterLast(':').orEmpty() },
            durationMs = r.durationMs.takeIf { it > 0 } ?: t?.durationMs ?: 0L,
            coverUrl = cover,
            isrc = isrc?.let(::normalizeIsrc),
        ).also { if (it.title.isBlank() || it.artist.isBlank()) throw Unavailable("no track metadata") }
    }

    private suspend fun httpGet(url: String, out: File) = runInterruptible {
        InterruptibleHttp.read(YouTubeSource.http.newCall(okhttp3.Request.Builder().url(url).build())) { res ->
            if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
            val body = res.body ?: throw IOException("empty body")
            out.outputStream().use { body.byteStream().copyTo(it) }
        }
    }

    /**
     * Downloads the file URL into [out] with HTTP **Range** requests in chunks,
     * reporting progress: a plain streaming GET with a browser User-Agent and the
     * extension's headers, retried per chunk on resets.
     */
    private suspend fun fetchWithProgress(trackId: String, url: String, out: File, headers: Map<String, String> = emptyMap()): Boolean {
        // A quick HEAD for the size: the progress bar needs it (-1 when the server says nothing).
        val total = runInterruptible(Dispatchers.IO) { contentLength(url, headers) }
        val watcher = if (total > 0) scope.launch {
            while (isActive) {
                setStatus(trackId, OfflineStatus.Downloading((out.length().toFloat() / total).coerceIn(0f, 1f)))
                delay(400)
            }
        } else null
        try {
            return runInterruptible(Dispatchers.IO) { rangedDownload(url, out, total, headers) }
        } finally {
            watcher?.cancel()
            setStatus(trackId, OfflineStatus.Downloading(-1f))
        }
    }

    /**
     * The byte range loop, shared with [PodcastDownloader]: a publisher's MP3 is fetched the
     * same way (follows the 302s to podtrac/radiofrance/simplecast, restarts from zero when a
     * server ignores Range, retries each chunk three times).
     */
    internal fun rangedDownload(url: String, out: File, knownTotal: Long, headers: Map<String, String> = emptyMap()): Boolean {
        val chunk = 4L * 1024 * 1024 // 4 MB: the stream gets its turn between two chunks
        var total = knownTotal
        java.io.RandomAccessFile(out, "rw").use { output ->
            output.setLength(0)
            while (true) {
                // A chunk is [start, end]; a retry resumes where the bytes stopped, never re-appends.
                val end = if (total > 0) minOf(output.length() + chunk - 1, total - 1) else output.length() + chunk - 1
                var done = false
                var attempt = 0
                yieldToPlayer()
                while (true) {
                    attempt++
                    val position = output.length()
                    // The safe client (see MediaHttp): redirects followed by hand and re-checked, the headers kept on their own host only.
                    try {
                        MediaHttp.open(url, headers = headers, fixed = mapOf("User-Agent" to DownloadUa, "Range" to "bytes=$position-$end"), timeoutMs = 60_000).use { conn ->
                            val code = conn.response.code
                            // Asked past the end of a file whose size was unknown: it is complete.
                            if (code == 416 && total <= 0 && position > 0) { done = true; return@use }
                            if (code !in 200..299) throw IOException("stream HTTP $code")
                            val full = code != 206 // server ignored Range → the whole body, from byte 0
                            if (full) output.setLength(0)
                            // A part must be the one asked for: bytes from elsewhere in the file would be
                            // written at the wrong place and make a file that plays wrong.
                            if (!full) {
                                val span = contentRangeSpan(conn.response.header("Content-Range"))
                                if (span == null || span.first != position || span.last > end)
                                    throw IOException("stream sent ${conn.response.header("Content-Range") ?: "no Content-Range"} for bytes $position-$end")
                            }
                            if (total <= 0) total = contentRangeTotal(conn.response.header("Content-Range")) ?: -1L
                            output.seek(output.length())
                            val expected = if (full) -1L else end - position + 1
                            var got = 0L
                            conn.response.body.byteStream().use { input ->
                                val buf = ByteArray(256 * 1024)
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    // Never more than the part asked for, whatever the server sends after it.
                                    val keep = if (expected >= 0) minOf(n.toLong(), expected - got).toInt() else n
                                    output.write(buf, 0, keep)
                                    got += keep
                                    if (expected in 0..got) break
                                }
                            }
                            if (full) done = true
                            // A short chunk with the size still unknown is the end of the stream.
                            else if (total <= 0 && got < expected) done = true
                        }
                        break
                    } catch (e: IOException) {
                        if (attempt >= 3 || e is com.adlen.sonora.extensions.ExtensionMediaHttp.Refused || com.adlen.sonora.extensions.SafeNet.isRefusal(e)) throw e
                        Log.w(TAG, "chunk at ${output.length()} failed (attempt $attempt): ${e.message}")
                    }
                }
                if (done) break
                if (total in 1..output.length()) break
            }
        }
        return true
    }

    /**
     * The song playing here is loading: downloads wait (up to 30 s) so they do not starve
     * it, or the player's stall watchdog would take it for stuck and skip it.
     */
    private fun yieldToPlayer() {
        var waited = 0
        while (waited < 30_000 && playerStarving()) {
            Thread.sleep(500); waited += 500
        }
    }

    private fun playerStarving(): Boolean = runCatching {
        com.adlen.sonora.app.di.SongPlayer.isPlaying() && com.adlen.sonora.app.di.SongPlayer.isBuffering &&
            com.adlen.sonora.app.di.SongPlayer.activeDevice.value == null && !com.adlen.sonora.app.di.LocalPlayer.active.value
    }.getOrDefault(false)

    private fun contentLength(url: String, headers: Map<String, String> = emptyMap()): Long =
        runCatching {
            MediaHttp.open(url, "HEAD", headers, mapOf("User-Agent" to DownloadUa)).use { conn ->
                conn.response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 } ?: -1L
            }
        }.getOrDefault(-1L)

    // ── Pure helpers (unit-tested) ──

    /** File hosts expect a browser-ish User-Agent on downloads. */
    internal const val DownloadUa =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /** Length from a FLAC STREAMINFO header, or null if [head] is not one. */
    internal fun flacDurationSec(head: ByteArray): Int? {
        if (head.size < 26 || String(head, 0, 4, Charsets.US_ASCII) != "fLaC") return null
        // Bytes 18..25: sample rate (20 bits), channels (3), bits/sample (5), total samples (36).
        var bits = 0L
        for (i in 18..25) bits = (bits shl 8) or (head[i].toLong() and 0xFF)
        val sampleRate = (bits ushr 44).toInt()
        val totalSamples = bits and 0xFFFFFFFFFL
        if (sampleRate <= 0 || totalSamples <= 0) return null
        return (totalSamples / sampleRate).toInt()
    }

    /** Why a file of [seconds] is not the song of [durationMs], or null when it is (or the song's length is unknown). */
    internal fun lengthMismatch(seconds: Long, durationMs: Long): String? {
        val expected = durationMs / 1000
        return when {
            expected <= 0 -> null
            seconds < expected - SHORTER_MAX -> "the file is ${seconds}s, the song ${expected}s (a preview)"
            seconds > expected + LONGER_MAX -> "the file is ${seconds}s, the song ${expected}s (another version)"
            else -> null
        }
    }

    /** "bytes 0-99/1234" → 1234; null when absent or "*". */
    /** "bytes 100-199/1234" → 100..199; null when absent or not that shape. */
    fun contentRangeSpan(header: String?): LongRange? {
        val m = Regex("""^\s*bytes\s+(\d+)-(\d+)/(\d+|\*)\s*$""", RegexOption.IGNORE_CASE).find(header ?: return null) ?: return null
        val first = m.groupValues[1].toLongOrNull() ?: return null
        val last = m.groupValues[2].toLongOrNull() ?: return null
        return if (last >= first) first..last else null
    }

    fun contentRangeTotal(header: String?): Long? =
        header?.substringAfterLast('/', "")?.trim()?.toLongOrNull()?.takeIf { it > 0 }

    /** "US-UM7-17-03861" / "usum71703861" → "USUM71703861"; null when not an ISRC. */
    fun normalizeIsrc(raw: String): String? =
        raw.uppercase().filter { it.isLetterOrDigit() }.takeIf { Regex("[A-Z]{2}[A-Z0-9]{3}\\d{7}").matches(it) }
}
