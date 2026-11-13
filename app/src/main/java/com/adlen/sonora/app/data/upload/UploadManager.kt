// Sonora (https://github.com/cherifad/sonora), a fork of Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
package com.adlen.sonora.app.data.upload

import android.content.Context
import com.adlen.sonora.app.data.DataSaver
import com.adlen.sonora.app.data.local.LocalMusic
import com.adlen.sonora.app.data.local.LocalTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Uploads the local library to the companion ingest service. A duplicate is a success:
 * the local file is marked UPLOADED with origin DUPLICATE, exactly like a fresh upload.
 */
object UploadManager {
    data class Progress(val done: Int, val total: Int, val title: String)

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress
    private val running = AtomicBoolean(false)

    private val deniedFolders = listOf(
        "whatsapp", "telegram", "recordings", "voice recorder", "call recordings",
        "notifications", "ringtones", "alarms", "android/media", "download",
    )

    /**
     * Why a file is not worth uploading, or null when it is. The app is strict on purpose:
     * a file without title, artist and album tags never leaves the phone.
     */
    fun skipReason(track: LocalTrack): String? {
        if (track.title.isBlank() || track.artist.isBlank() || track.album.isBlank()) {
            return "missing title, artist or album tags"
        }
        if (track.durationMs in 1 until 30_000) return "shorter than 30 seconds"
        val folder = track.folder.lowercase()
        if (deniedFolders.any { folder.contains(it) }) return "voice note or system folder"
        return null
    }

    /** The files the automatic sync may send: tagged music, not voice notes or system sounds. */
    fun isLikelyMusic(track: LocalTrack): Boolean = skipReason(track) == null

    /** True when the server still returns the track the ingest registered for this file. */
    suspend fun isOnServer(serverTrackId: String?): Boolean {
        if (serverTrackId.isNullOrBlank()) return false
        val api = com.adlen.sonora.host.android.SonoraRuntime.subsonicApi ?: return false
        return runCatching {
            api.catalog.track("spotify:track:$serverTrackId")
        }.isSuccess
    }

    fun enqueue(context: Context, tracks: List<LocalTrack>) {
        if (UploadSettings.load(context) == null) return
        tracks.forEach { track ->
            val key = LocalUploadIndex.key(track)
            val current = LocalUploadIndex.entry(key)
            val reason = skipReason(track)
            if (reason != null) {
                // Never overwrite a file the server already confirmed, or one waiting for its scan.
                if (current?.state != UploadState.UPLOADED && current?.state != UploadState.WAITING_SCAN) {
                    LocalUploadIndex.set(key, UploadEntry(UploadState.SKIPPED, reason = reason))
                }
            } else if (current == null || current.state == UploadState.FAILED || current.state == UploadState.SKIPPED) {
                // Pressing Upload again must not re-send a file the server already confirmed, or
                // one waiting for its scan: keep those states and let the worker poll them.
                LocalUploadIndex.set(key, UploadEntry(UploadState.QUEUED))
            }
        }
        UploadWork.enqueue(context)
    }

    suspend fun run(context: Context) {
        if (!running.compareAndSet(false, true)) return
        try {
            val target = UploadSettings.load(context) ?: return
            if (UploadSettings.isWifiOnly(context) && DataSaver.metered) return
            var client = UploadClient(target)
            var refreshed = false
            val tracks = LocalMusic.tracks(context)
            val queued = LocalUploadIndex.entries.value
                .filterValues { it.state == UploadState.QUEUED || it.state == UploadState.WAITING_SCAN }
            var done = 0
            for ((key, entry) in queued) {
                val track = tracks.firstOrNull { LocalUploadIndex.key(it) == key } ?: continue
                _progress.value = Progress(done, queued.size, track.title)
                try {
                    process(context, client, key, entry, track)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: UploadUnauthorized) {
                    // The token expired (or the ingest secret changed): exchange once more with
                    // the Subsonic credentials the app already holds, then retry this file.
                    val renewed = if (refreshed) null else UploadAuth.refresh(context)
                    if (renewed == null) {
                        mark(key, UploadState.FAILED, reason = e.message ?: "unauthorized")
                    } else {
                        refreshed = true
                        client = UploadClient(renewed)
                        try {
                            process(context, client, key, entry, track)
                        } catch (retry: kotlinx.coroutines.CancellationException) {
                            throw retry
                        } catch (retry: Throwable) {
                            mark(key, UploadState.FAILED, reason = retry.message ?: retry.javaClass.simpleName)
                        }
                    }
                } catch (e: Throwable) {
                    mark(key, UploadState.FAILED, reason = e.message ?: e.javaClass.simpleName)
                }
                done++
            }
            _progress.value = null
        } finally {
            running.set(false)
        }
    }

    private suspend fun process(
        context: Context,
        client: UploadClient,
        key: String,
        entry: UploadEntry,
        track: LocalTrack,
    ) {
        if (entry.state == UploadState.WAITING_SCAN && entry.uploadId != null) {
            poll(context, client, key, entry.uploadId, complete = false)
        } else {
            uploadOne(context, client, track)
        }
    }

    private suspend fun uploadOne(context: Context, client: UploadClient, track: LocalTrack) {
        val key = LocalUploadIndex.key(track)
        mark(key, UploadState.UPLOADING)
        val bytes = readBytes(context, track)
        val sha = sha256(bytes)
        val metadata = UploadClient.Metadata(
            title = track.title,
            artist = track.artist,
            album = track.album,
            albumArtist = track.artist,
            track = 0,
            durationMs = track.durationMs,
        )
        when (val decision = client.match(sha, metadata)) {
            is UploadClient.Decision.Duplicate -> {
                mark(key, UploadState.UPLOADED, UploadOrigin.DUPLICATE, decision.serverTrackId, sha256 = sha)
                return
            }
            is UploadClient.Decision.Review -> {
                mark(key, UploadState.NEEDS_REVIEW, reason = decision.reason)
                return
            }
            UploadClient.Decision.New -> Unit
        }
        val filename = track.displayName.ifBlank { track.title + extensionFor(track.mimeType) }
        when (val created = client.create(filename, bytes.size.toLong(), sha, metadata)) {
            is UploadClient.Created.Duplicate -> {
                mark(key, UploadState.UPLOADED, UploadOrigin.DUPLICATE, created.serverTrackId, sha256 = sha)
            }
            is UploadClient.Created.Review -> {
                mark(key, UploadState.NEEDS_REVIEW, reason = created.reason)
            }
            is UploadClient.Created.Session -> {
                var offset = 0L
                while (offset < bytes.size) {
                    val end = minOf(offset + created.chunkSize, bytes.size.toLong())
                    val chunk = bytes.copyOfRange(offset.toInt(), end.toInt())
                    offset = client.patch(created.uploadId, offset, bytes.size.toLong(), chunk)
                }
                mark(key, UploadState.UPLOADING, uploadId = created.uploadId, sha256 = sha)
                poll(context, client, key, created.uploadId, complete = true)
            }
        }
    }

    private suspend fun poll(
        context: Context,
        client: UploadClient,
        key: String,
        uploadId: String,
        complete: Boolean,
    ) {
        var status = if (complete) client.complete(uploadId) else client.status(uploadId)
        for (attempt in 0 until 30) {
            if (status.status == "done") break
            delay(2_000)
            status = client.status(uploadId)
        }
        when (status.status) {
            "done" -> mark(key, UploadState.UPLOADED, UploadOrigin.NEW, status.serverTrackId, status.path, uploadId = uploadId)
            "waiting_scan" -> mark(key, UploadState.WAITING_SCAN, path = status.path, reason = status.error, uploadId = uploadId)
            else -> mark(key, UploadState.FAILED, reason = status.error ?: status.status, uploadId = uploadId)
        }
    }

    private fun mark(
        key: String,
        state: UploadState,
        origin: UploadOrigin? = null,
        serverTrackId: String? = null,
        path: String? = null,
        sha256: String? = null,
        reason: String? = null,
        uploadId: String? = null,
    ) {
        LocalUploadIndex.set(key, UploadEntry(
            state = state,
            origin = origin,
            serverTrackId = serverTrackId,
            path = path,
            sha256 = sha256,
            reason = reason,
            uploadId = uploadId,
        ))
    }

    private suspend fun readBytes(context: Context, track: LocalTrack): ByteArray =
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(track.uri)?.use { it.readBytes() }
                ?: throw IOException("cannot read ${track.title}")
        }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun extensionFor(mime: String): String = when {
        mime.contains("flac") -> ".flac"
        mime.contains("mpeg") || mime.contains("mp3") -> ".mp3"
        mime.contains("mp4") || mime.contains("m4a") -> ".m4a"
        mime.contains("ogg") -> ".ogg"
        mime.contains("opus") -> ".opus"
        mime.contains("wav") -> ".wav"
        else -> ".flac"
    }
}
