// Sonora (https://github.com/cherifad/sonora), a fork of Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
package com.adlen.sonora.app.data.upload

import android.content.Context
import com.adlen.sonora.app.data.local.LocalTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File

enum class UploadState { QUEUED, UPLOADING, WAITING_SCAN, UPLOADED, NEEDS_REVIEW, SKIPPED, FAILED }

enum class UploadOrigin { NEW, DUPLICATE }

data class UploadEntry(
    val state: UploadState,
    val origin: UploadOrigin? = null,
    val serverTrackId: String? = null,
    val path: String? = null,
    val sha256: String? = null,
    val reason: String? = null,
    val uploadId: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * The state of each local file against the server: queued, uploading, uploaded (fresh or
 * duplicate), needs review, failed. Kept in one JSON file; the UI reads the flow.
 */
object LocalUploadIndex {
    private lateinit var file: File
    private val _entries = MutableStateFlow<Map<String, UploadEntry>>(emptyMap())
    val entries: StateFlow<Map<String, UploadEntry>> = _entries

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.filesDir, "upload/index.json")
        load()
    }

    fun key(track: LocalTrack): String = "local:${track.id}"
    fun key(id: Long): String = "local:$id"

    fun entry(key: String): UploadEntry? = _entries.value[key]

    fun set(key: String, entry: UploadEntry) {
        _entries.update { it + (key to entry) }
        save()
    }

    fun remove(key: String) {
        _entries.update { it - key }
        save()
    }

    fun clear() {
        _entries.value = emptyMap()
        save()
    }

    private fun load() {
        if (!file.isFile) return
        runCatching {
            val root = JSONObject(file.readText())
            val source = root.optJSONObject("entries") ?: JSONObject()
            val map = linkedMapOf<String, UploadEntry>()
            source.keys().forEach { key ->
                val o = source.getJSONObject(key)
                map[key] = UploadEntry(
                    state = runCatching { UploadState.valueOf(o.optString("state")) }.getOrDefault(UploadState.FAILED),
                    origin = o.optString("origin").takeIf { it.isNotBlank() }
                        ?.let { runCatching { UploadOrigin.valueOf(it) }.getOrNull() },
                    serverTrackId = o.optString("serverTrackId").takeIf { it.isNotBlank() },
                    path = o.optString("path").takeIf { it.isNotBlank() },
                    sha256 = o.optString("sha256").takeIf { it.isNotBlank() },
                    reason = o.optString("reason").takeIf { it.isNotBlank() },
                    uploadId = o.optString("uploadId").takeIf { it.isNotBlank() },
                    updatedAt = o.optLong("updatedAt"),
                )
            }
            _entries.value = map
        }
    }

    private fun save() {
        if (!::file.isInitialized) return
        runCatching {
            val source = JSONObject()
            _entries.value.forEach { (key, entry) ->
                source.put(key, JSONObject()
                    .put("state", entry.state.name)
                    .put("origin", entry.origin?.name.orEmpty())
                    .put("serverTrackId", entry.serverTrackId.orEmpty())
                    .put("path", entry.path.orEmpty())
                    .put("sha256", entry.sha256.orEmpty())
                    .put("reason", entry.reason.orEmpty())
                    .put("uploadId", entry.uploadId.orEmpty())
                    .put("updatedAt", entry.updatedAt))
            }
            file.parentFile?.mkdirs()
            file.writeText(JSONObject().put("entries", source).toString())
        }
    }
}
