// Sonora (https://github.com/cherifad/sonora), a fork of Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
package com.adlen.sonora.app.data.upload

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The ingest refused the token; the caller should exchange again and retry. */
class UploadUnauthorized(message: String) : IOException(message)

/** The ingest API (`ingest/`), as the app uses it. */
class UploadClient(
    private val target: UploadTarget,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(120, TimeUnit.SECONDS)
        .build(),
) {
    data class Metadata(
        val title: String,
        val artist: String,
        val album: String,
        val albumArtist: String,
        val track: Int,
        val durationMs: Long,
    )

    sealed interface Decision {
        data object New : Decision
        data class Duplicate(val match: String, val serverTrackId: String?) : Decision
        data class Review(val reason: String) : Decision
    }

    sealed interface Created {
        data class Session(val uploadId: String, val chunkSize: Int) : Created
        data class Duplicate(val match: String, val serverTrackId: String?) : Created
        data class Review(val reason: String) : Created
    }

    data class UploadStatus(
        val id: String,
        val status: String,
        val serverTrackId: String?,
        val path: String?,
        val origin: String?,
        val error: String?,
    )

    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(request("GET", "/health")).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    suspend fun match(sha256: String, metadata: Metadata): Decision = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("sha256", sha256)
            .put("metadata", metadataJson(metadata))
            .toString()
        val json = call("POST", "/library/match", body)
        when (json.optString("decision")) {
            "duplicate" -> Decision.Duplicate(json.optString("match"), json.optString("serverTrackId").takeIf { it.isNotBlank() })
            "needs_review" -> Decision.Review(json.optString("reason"))
            else -> Decision.New
        }
    }

    suspend fun create(filename: String, size: Long, sha256: String, metadata: Metadata): Created = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("filename", filename)
            .put("size", size)
            .put("sha256", sha256)
            .put("metadata", metadataJson(metadata))
            .toString()
        val json = call("POST", "/uploads", body)
        when {
            json.optBoolean("duplicate") -> Created.Duplicate(
                json.optString("match"),
                json.optString("serverTrackId").takeIf { it.isNotBlank() },
            )
            json.optString("decision") == "needs_review" -> Created.Review(json.optString("reason"))
            else -> Created.Session(json.getString("uploadId"), json.optInt("chunkSize", 8 shl 20))
        }
    }

    suspend fun patch(uploadId: String, offset: Long, total: Long, data: ByteArray): Long = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(target.baseUrl + "/uploads/" + uploadId)
            .header("Authorization", "Bearer " + target.token)
            .header("Content-Range", "bytes $offset-${offset + data.size - 1}/$total")
            .patch(data.toRequestBody("application/octet-stream".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401 || response.code == 403) throw UploadUnauthorized(text)
            if (!response.isSuccessful) throw IOException("upload HTTP ${response.code}: $text")
            JSONObject(text).getLong("offset")
        }
    }

    suspend fun complete(uploadId: String): UploadStatus = withContext(Dispatchers.IO) {
        statusOf(call("POST", "/uploads/$uploadId/complete", ""))
    }

    suspend fun status(uploadId: String): UploadStatus = withContext(Dispatchers.IO) {
        statusOf(call("GET", "/uploads/$uploadId", null))
    }

    private fun request(method: String, path: String, body: String? = null): Request {
        val builder = Request.Builder()
            .url(target.baseUrl + path)
            .header("Authorization", "Bearer " + target.token)
        return when (method) {
            "POST" -> builder.post((body ?: "").toRequestBody("application/json".toMediaType())).build()
            else -> builder.get().build()
        }
    }

    private fun call(method: String, path: String, body: String?): JSONObject {
        http.newCall(request(method, path, body)).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401 || response.code == 403) throw UploadUnauthorized(text)
            if (!response.isSuccessful) throw IOException("upload HTTP ${response.code}: $text")
            return JSONObject(text)
        }
    }

    private fun statusOf(json: JSONObject) = UploadStatus(
        id = json.optString("id"),
        status = json.optString("status"),
        serverTrackId = json.optString("serverTrackId").takeIf { it.isNotBlank() },
        path = json.optString("path").takeIf { it.isNotBlank() },
        origin = json.optString("origin").takeIf { it.isNotBlank() },
        error = json.optString("error").takeIf { it.isNotBlank() },
    )

    private fun metadataJson(metadata: Metadata) = JSONObject()
        .put("title", metadata.title)
        .put("artist", metadata.artist)
        .put("album", metadata.album)
        .put("albumArtist", metadata.albumArtist)
        .put("track", metadata.track)
        .put("durationMs", metadata.durationMs)
}
